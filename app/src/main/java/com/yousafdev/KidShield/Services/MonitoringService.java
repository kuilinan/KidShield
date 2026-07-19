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

import androidx.core.app.NotificationCompat;

import com.yousafdev.KidShield.Network.CommandStore;
import com.yousafdev.KidShield.R;

public class MonitoringService extends Service {
    private static final String TAG = "MonitoringService";
    private static final String CHANNEL_ID = "kidshield_monitor";
    private static final int NOTIFICATION_ID = 1002;
    private CommandStore commandStore;
    private String currentUid;
    private boolean devModeBlocked = false;
    private Handler handler;
    private Runnable devModeCheck;

    @Override
    public void onCreate() {
        super.onCreate();
        commandStore = new CommandStore(this);
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();

        // 从本地存储获取用户ID
        currentUid = getSharedPreferences("kidshield", MODE_PRIVATE).getString("user_id", null);
        if (currentUid != null) {
            // 从本地CommandStore读取开发者模式设置
            String devMode = commandStore.readPolicyData("block_dev_mode");
            devModeBlocked = devMode != null && devMode.contains("true");
            startDevModeGuard();
        }
        startForeground(NOTIFICATION_ID, createNotification());
        startLocationGuardService();
        Log.d(TAG, "监控服务已启动 (本地模式)");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "KidShield 监控",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("KidShield 保护中")
                .setContentText("家长控制服务正在运行")
                .setSmallIcon(R.drawable.ic_check)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void startDevModeGuard() {
        devModeCheck = new Runnable() {
            @Override
            public void run() {
                if (devModeBlocked) {
                    try {
                        if (Settings.Global.getInt(getContentResolver(), "development_settings_enabled", 0) == 1) {
                            Settings.Global.putInt(getContentResolver(), "development_settings_enabled", 0);
                            Log.d(TAG, "开发者模式已被守护进程关闭");
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
                        Log.e(TAG, "守护检查失败", e);
                    }
                }
                handler.postDelayed(this, 5000);
            }
        };
        handler.postDelayed(devModeCheck, 5000);
    }

    private void startLocationGuardService() {
        Intent intent = new Intent(this, LocationGuardService.class);
        startService(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null && devModeCheck != null) {
            handler.removeCallbacks(devModeCheck);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
