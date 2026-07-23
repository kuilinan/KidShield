package com.yousafdev.KidShield.Services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.yousafdev.KidShield.DeviceAdmin.AdminReceiver;
import com.yousafdev.KidShield.R;
import com.yousafdev.KidShield.Utils.VivoKeepAliveHelper;
import com.yousafdev.KidShield.Receivers.AlarmSelfHealReceiver;

/**
 * 升级版综合守护服务（VIVO 强保活引擎）
 * 功能：前台服务加固 + 高频心跳 + 双进程守护 + VIVO省电策略对抗 + 开发者封锁
 */
public class GuardService extends Service {
    private static final String TAG = "GuardService";
    private static final int NOTIFICATION_ID = 1003;
    private static final String CHANNEL_ID = "KidShieldGuard";
    private static final String CHANNEL_NAME = "KidShield 守护";

    // 心跳间隔（VIVO 激进杀后台，用更短间隔）
    private static final long HEARTBEAT_INTERVAL_MS = 5000; // 5秒

    // AlarmManager 自愈间隔
    private static final long ALARM_SELF_HEAL_INTERVAL_MS = 60000; // 60秒

    private Handler handler;
    private PowerManager.WakeLock wakeLock;
    private boolean devModeBlocked = false;

    // 屏幕状态监测
    private boolean screenOn = true;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());

        // 创建高优先级通知频道
        createNotificationChannels();

        // 获取 WakeLock（防止 CPU 休眠）
        acquireWakeLock();

        // 启动前台服务（VIVO 对前台服务容忍度更高）
        startForeground(NOTIFICATION_ID, createNotification());

        // 注册屏幕状态监听
        registerScreenStateReceiver();

        // 启动保活心跳
        startHeartbeat();

        // 启动 AlarmManager 自愈（应对极端杀进程）
        scheduleAlarmSelfHeal();

        // 启动 VIVO 特定保活策略
        VivoKeepAliveHelper.applyKeepAlive(this);

        Log.d(TAG, "✦ KidShield 强保活引擎已启动 ✦");
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 主频道 - 高重要性，确保锁屏显示
            NotificationChannel mainChannel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            mainChannel.setDescription("KidShield 核心守护通知");
            mainChannel.setShowBadge(false);
            mainChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(mainChannel);

            // 辅助频道 - 用于 AlarmManager 自愈的透明通知
            NotificationChannel healChannel = new NotificationChannel(
                    CHANNEL_ID + "_heal", CHANNEL_NAME + "(自愈)",
                    NotificationManager.IMPORTANCE_MIN);
            healChannel.setDescription("自愈服务");
            healChannel.setShowBadge(false);
            if (nm != null) nm.createNotificationChannel(healChannel);
        }
    }

    private Notification createNotification() {
        // VIVO 上，通知文案要有说服力，降低用户关闭的欲望
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("⚡ 孩子守护中")
                .setContentText("KidShield 正在保护孩子安全 · 请保持运行")
                .setSmallIcon(R.drawable.ic_check)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setSilent(true) // 不发出声音，但保持高优先级
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "KidShield:GuardWakeLock");
                wakeLock.acquire(10 * 60 * 1000L); // 10分钟自动释放，防止系统强制回收
                Log.d(TAG, "WakeLock acquired");
            }
        } catch (Exception e) {
            Log.e(TAG, "WakeLock acquisition failed", e);
        }
    }

    private void registerScreenStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenStateReceiver, filter);
    }

    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action) ||
                Intent.ACTION_USER_PRESENT.equals(action)) {
                screenOn = true;
                // 亮屏时加强心跳
                handler.removeCallbacks(heartbeatRunnable);
                handler.post(heartbeatRunnable);
                Log.d(TAG, "屏幕已点亮，心跳恢复");
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                screenOn = false;
                Log.d(TAG, "屏幕已关闭，心跳继续（防杀）");
            }
        }
    };

    // ==================== 保活心跳 ====================

    private void startHeartbeat() {
        handler.post(heartbeatRunnable);
    }

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // 1. 刷新 WakeLock
                refreshWakeLock();

                // 2. 开发者模式封锁（如果已启用）
                enforceDevModeBlock();

                // 3. 检查设备管理员状态
                checkDeviceAdmin();

                // 4. 重新获取前台服务通知（VIVO 上防止通知被系统清除后降权）
                refreshForegroundNotification();

                // 5. 检查无障碍服务运行状态（仅日志）
                checkAccessibilityService();
            } catch (Exception e) {
                Log.e(TAG, "Heartbeat error", e);
            }

            // 无论亮屏灭屏，都保持心跳
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    private void refreshWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            acquireWakeLock();
        } catch (Exception ignored) {}
    }

    private void refreshForegroundNotification() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                // 重发通知，防止被 VIVO 系统清除
                nm.notify(NOTIFICATION_ID, createNotification());
            }
        } catch (Exception ignored) {}
    }

    // ==================== AlarmManager 自愈（双进程守护） ====================

    private void scheduleAlarmSelfHeal() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(this, AlarmSelfHealReceiver.class);
        intent.setAction("com.yousafdev.KidShield.ALARM_SELF_HEAL");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 使用精准闹钟，每60秒触发一次自愈检查
        alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + ALARM_SELF_HEAL_INTERVAL_MS,
                ALARM_SELF_HEAL_INTERVAL_MS,
                pendingIntent);
    }


    private void enforceDevModeBlock() {
        if (!devModeBlocked) return;
        try {
            // 关闭开发者模式
            if (Settings.Global.getInt(getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1) {
                Settings.Global.putInt(getContentResolver(),
                        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
                Log.d(TAG, "⛔ 开发者模式已强制关闭");
            }

            // 关闭 ADB
            if (Settings.Global.getInt(getContentResolver(),
                    Settings.Global.ADB_ENABLED, 0) == 1) {
                Settings.Global.putInt(getContentResolver(),
                        Settings.Global.ADB_ENABLED, 0);
                Log.d(TAG, "⛔ ADB 已强制关闭");
            }

            // 关闭无线调试 (Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Settings.Global.getInt(getContentResolver(),
                        "adb_wifi_enabled", 0) == 1) {
                    Settings.Global.putInt(getContentResolver(),
                            "adb_wifi_enabled", 0);
                    Log.d(TAG, "⛔ 无线ADB已强制关闭");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "开发者模式封锁失败", e);
        }
    }

    private void checkDeviceAdmin() {
        if (!AdminReceiver.isActive(this)) {
            Log.w(TAG, "⚠ 设备管理员未激活");
            try {
                AdminReceiver.requestActivate(this);
            } catch (Exception e) {
                Log.e(TAG, "无法自动激活设备管理员", e);
            }
        }
    }

    private void checkAccessibilityService() {
        // 无障碍服务状态检测（通过日志）
        boolean accRunning = AppAccessibilityService.isRunning();
        if (!accRunning) {
            Log.w(TAG, "⚠ 无障碍服务未运行");
        }
    }

    // ==================== 开关控制 ====================

    public static void enableDevModeBlock(Context context, boolean enable) {
        context.getSharedPreferences("guard_config", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("dev_mode_block", enable)
                .apply();
    }

    public static boolean isDevModeBlockEnabled(Context context) {
        return context.getSharedPreferences("guard_config", Context.MODE_PRIVATE)
                .getBoolean("dev_mode_block", false);
    }

    // ==================== 生命周期 ====================

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("dev_mode_block")) {
            devModeBlocked = intent.getBooleanExtra("dev_mode_block", false);
        }

        // 记录心跳
        getSharedPreferences("guard_health", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_heartbeat", System.currentTimeMillis())
                .apply();

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "⚠ GuardService 被销毁！立即尝试重启...");

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        try {
            unregisterReceiver(screenStateReceiver);
        } catch (Exception ignored) {}

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // 自杀式重启
        Intent restart = new Intent(this, GuardService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }

        super.onDestroy();
    }
}
