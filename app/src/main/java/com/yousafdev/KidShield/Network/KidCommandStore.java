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
}