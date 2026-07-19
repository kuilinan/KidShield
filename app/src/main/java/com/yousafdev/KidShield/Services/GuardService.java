package com.yousafdev.KidShield.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.yousafdev.KidShield.DeviceAdmin.AdminReceiver;
import com.yousafdev.KidShield.R;

/**
 * 综合守护服务（阳光守护核心保活引擎）
 * 功能：开发者模式封锁 + 位置守护 + 无障碍保活 + 设备管理员检查 + 多维度保活
 */
public class GuardService extends Service {
    private static final String TAG = "GuardService";
    private static final int NOTIFICATION_ID = 1003;
    private static final String CHANNEL_ID = "KidShieldGuard";
    
    private Handler handler;
    private boolean devModeBlocked = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        startGuardLoop();
        Log.d(TAG, "综合守护服务已启动");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "KidShield 守护",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("守护应用正常运行");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("KidShield 守护中")
                .setContentText("正在保护孩子的安全")
                .setSmallIcon(R.drawable.ic_check)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void startGuardLoop() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. 开发者模式封锁
                    enforceDevModeBlock();
                    // 2. 检查设备管理员状态
                    checkDeviceAdmin();
                    // 3. 检查无障碍服务运行
                    checkAccessibilityService();
                } catch (Exception e) {
                    Log.e(TAG, "守护循环异常", e);
                }
                // 每8秒执行一次
                handler.postDelayed(this, 8000);
            }
        });
    }

    private void enforceDevModeBlock() {
        if (!devModeBlocked) return;
        try {
            if (Settings.Global.getInt(getContentResolver(), "development_settings_enabled", 0) == 1) {
                Settings.Global.putInt(getContentResolver(), "development_settings_enabled", 0);
                Log.d(TAG, "开发者模式已强制关闭");
            }
            if (Settings.Global.getInt(getContentResolver(), "adb_enabled", 0) == 1) {
                Settings.Global.putInt(getContentResolver(), "adb_enabled", 0);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (Settings.Global.getInt(getContentResolver(), "adb_wifi_enabled", 0) == 1) {
                    Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 0);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "开发者模式封锁失败", e);
        }
    }

    private void checkDeviceAdmin() {
        if (!AdminReceiver.isActive(this)) {
            Log.w(TAG, "设备管理员未激活，尝试激活...");
            try {
                AdminReceiver.requestActivate(this);
            } catch (Exception e) {
                Log.e(TAG, "无法自动激活设备管理员（需要用户手动确认）", e);
            }
        }
    }

    private void checkAccessibilityService() {
        // 无障碍服务无法从外部检查状态，这需要用户手动开启
        // 我们确保前台服务一直在运行，为无障碍提供基础保障
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // 被杀后自动重启
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        // 自动重启
        Intent restart = new Intent(this, GuardService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart);
        } else {
            startService(restart);
        }
        super.onDestroy();
    }
}
