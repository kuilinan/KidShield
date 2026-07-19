package com.yousafdev.KidShield.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yousafdev.KidShield.R;

public class MonitoringService extends Service {
    private static final String TAG = "MonitoringService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "KidShieldMonitor";
    public static final String ACTION_SYNC_DATA = "com.yousafdev.KidShield.ACTION_SYNC_DATA";

    private DatabaseReference mDatabase;
    private String currentUid;
    private boolean devModeBlocked = false;
    private Handler handler;
    private Runnable devModeCheck;

    @Override
    public void onCreate() {
        super.onCreate();
        mDatabase = FirebaseDatabase.getInstance().getReference();
        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();

        // 获取当前用户
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            listenForDevModeSetting();
            startDevModeGuard();
        }

        startForeground(NOTIFICATION_ID, createNotification());
        // 启动位置守护服务
        startLocationGuardService();
        Log.d(TAG, "监控服务已启动");
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

    private void listenForDevModeSetting() {
        if (currentUid == null) return;
        mDatabase.child("users").child(currentUid).child("settings").child("blockDeveloperMode")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        devModeBlocked = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                    }
                    @Override public void onCancelled(DatabaseError error) {}
                });
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
                handler.postDelayed(this, 5000); // 每5秒检查一次
            }
        };
        handler.postDelayed(devModeCheck, 5000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (handler != null && devModeCheck != null) {
            handler.removeCallbacks(devModeCheck);
        }
        super.onDestroy();
    }

    private void startLocationGuardService() {
        Intent intent = new Intent(this, LocationGuardService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Log.d(TAG, "位置守护服务已启动");
    }

}