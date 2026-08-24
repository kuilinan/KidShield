package com.yousafdev.KidShield.Network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import com.yousafdev.KidShield.Models.KidPolicy;
import com.yousafdev.KidShield.Receivers.AlarmSelfHealReceiver;

/**
 * KidCommandStore - 指令持久化仓库（核心机制）
 *
 * 用户设计原则（重要）：
 * "所有管控指令发送过来后全部长存储在孩子端本地，
 *  无论怎么样，只要指令还在就可以正常执行。"
 *
 * 实现：
 * - 策略（白名单/黑名单/限额/就寝）→ JSON 落盘 SharedPreferences
 * - 锁屏指令 → lockUntil 用 elapsedRealtime（防改时间作弊）落盘
 * - 重启 → BootReceiver 重新加载，指令继续生效
 * - 断网 → 本地执行，不依赖后端
 * - 到期 → AlarmManager 自动解除（即使 App 被杀也能触发）
 */
public class KidCommandStore {

    private static final String PREFS = "kid_policy_store";
    private static final String KEY_POLICY_JSON = "policy_json";
    private static final String KEY_POLICY_SIGNATURE = "policy_signature";

    private final Context context;
    private final SharedPreferences prefs;

    public KidCommandStore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ========== 策略持久化 ==========

    /** 保存策略（覆盖式：新指令替换旧指令；未下发的指令保持原样由上层处理） */
    public synchronized void savePolicy(KidPolicy policy) {
        prefs.edit()
                .putString(KEY_POLICY_JSON, policy.toJson().toString())
                .putLong("updated_at", System.currentTimeMillis())
                .apply();
    }

    /** 加载策略（任何情况下调用都返回可用策略——默认策略也有效） */
    public synchronized KidPolicy loadPolicy() {
        String json = prefs.getString(KEY_POLICY_JSON, "");
        return KidPolicy.fromJson(json);
    }

    /** 是否有已保存的策略 */
    public boolean hasPolicy() {
        return prefs.contains(KEY_POLICY_JSON);
    }

    // ========== 锁屏指令（长存储核心） ==========

    /**
     * 应用锁屏指令（长存储）：
     * 家长下发"锁屏N分钟" → 计算绝对截止时间（elapsedRealtime）→ 落盘 → 设置闹钟
     * 之后无论重启/断网/杀进程，只要没到时间，锁屏就一直有效！
     */
    public synchronized void applyLock(long durationMs, String reason) {
        long lockUntil = SystemClock.elapsedRealtime() + durationMs;
        KidPolicy policy = loadPolicy();
        policy.lockUntil = lockUntil;
        policy.lockReason = reason == null ? "" : reason;
        savePolicy(policy);
        scheduleLockAlarm(lockUntil);
    }

    /** 解除锁屏（家长下发解锁指令才清除；到期由闹钟自动清除） */
    public synchronized void clearLock() {
        KidPolicy policy = loadPolicy();
        policy.lockUntil = 0;
        policy.lockReason = "";
        savePolicy(policy);
        cancelLockAlarm();
    }

    /** 当前锁屏剩余毫秒（elapsedRealtime 基准，改系统时间无效！） */
    public long getRemainingLockMs() {
        KidPolicy policy = loadPolicy();
        return policy.getRemainingLockMs(SystemClock.elapsedRealtime());
    }

    /** 是否处于锁屏中 */
    public boolean isLocked() {
        return getRemainingLockMs() > 0;
    }

    // ========== 闹钟调度（到期自动解除，进程被杀也能触发） ==========

    private void scheduleLockAlarm(long lockUntilElapsed) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // elapsedRealtime 基准的闹钟：不受系统时间修改影响
        Intent intent = new Intent(context, AlarmSelfHealReceiver.class);
        intent.setAction("com.yousafdev.KidShield.ACTION_LOCK_EXPIRED");
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0x51, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, lockUntilElapsed, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, lockUntilElapsed, pi);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, lockUntilElapsed, pi);
        }
    }

    private void cancelLockAlarm() {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(context, AlarmSelfHealReceiver.class);
        intent.setAction("com.yousafdev.KidShield.ACTION_LOCK_EXPIRED");
        PendingIntent pi = PendingIntent.getBroadcast(
                context, 0x51, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    /** 开机/自愈时调用：检查锁屏指令是否仍然有效（指令在=继续执行！） */
    public synchronized void restoreLockIfNeeded() {
        KidPolicy policy = loadPolicy();
        long remain = policy.getRemainingLockMs(SystemClock.elapsedRealtime());
        if (remain > 0) {
            // 指令还在且未到期 → 重新调度闹钟，锁屏继续生效
            scheduleLockAlarm(policy.lockUntil);
        } else if (policy.lockUntil > 0) {
            // 已到期 → 自动清除
            policy.lockUntil = 0;
            policy.lockReason = "";
            savePolicy(policy);
        }
    }

    // ========== 指令签名/完整性（可选扩展） ==========

    /** 保存策略时附带家长端签名（防止孩子篡改本地策略） */
    public void saveSignature(String signature) {
        prefs.edit().putString(KEY_POLICY_SIGNATURE, signature).apply();
    }

    public String getSignature() {
        return prefs.getString(KEY_POLICY_SIGNATURE, "");
    }

    // ========== 管控密码（防孩子修改管控设置！参考vivo健康使用设备密码） ==========

    /** 设置/修改管控密码（家长操作） */
    public synchronized void setLockPassword(String password) {
        KidPolicy policy = loadPolicy();
        // 存储SHA-256哈希，不存明文
        policy.lockPassword = sha256(password);
        savePolicy(policy);
    }

    /** 验证管控密码 */
    public synchronized boolean verifyLockPassword(String input) {
        KidPolicy policy = loadPolicy();
        if (policy.lockPassword == null || policy.lockPassword.isEmpty()) return true; // 未设置密码=不锁定
        return sha256(input).equals(policy.lockPassword);
    }

    /** 是否已设置管控密码 */
    public synchronized boolean hasLockPassword() {
        KidPolicy policy = loadPolicy();
        return policy.lockPassword != null && !policy.lockPassword.isEmpty();
    }

    private String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return input; // 极端情况兜底
        }
    }

    // ========== 临时延长（超时后+15分钟，限1次/天，参考vivo健康使用设备） ==========

    private static final String KEY_LAST_EXTEND_DATE = "last_extend_date";
    private static final long EXTEND_DURATION_MS = 15 * 60 * 1000; // 15分钟

    /** 尝试临时延长15分钟。成功返回true，当天已用过返回false */
    public synchronized boolean tryExtendLockOnce() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(new java.util.Date());
        String lastDate = prefs.getString(KEY_LAST_EXTEND_DATE, "");
        if (today.equals(lastDate)) {
            return false; // 今天已经用过延长机会
        }
        // 记录今天已用 + 延长锁屏
        prefs.edit().putString(KEY_LAST_EXTEND_DATE, today).apply();
        KidPolicy policy = loadPolicy();
        if (policy.lockUntil > 0) {
            policy.lockUntil += EXTEND_DURATION_MS; // 在原截止时间上+15分钟
            savePolicy(policy);
            scheduleLockAlarm(policy.lockUntil);
        }
        return true;
    }

    /** 今天还能否延长 */
    public synchronized boolean canExtendToday() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(new java.util.Date());
        return !today.equals(prefs.getString(KEY_LAST_EXTEND_DATE, ""));
    }
}