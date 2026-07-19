package com.yousafdev.KidShield.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.yousafdev.KidShield.R;

public class LocationGuardService extends Service {
    private static final String TAG = "LocationGuard";
    private static final int NOTIFICATION_ID = 1002;
    private static final String CHANNEL_ID = "KidShieldLocation";
    
    private Handler handler;
    private Runnable locationCheckRunnable;
    private boolean isChecking = false;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        
        // 开始检测定位权限
        startLocationGuard();
        Log.d(TAG, "定位守护服务已启动");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "位置权限守护",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("守护位置权限不被关闭");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("位置权限守护中")
                .setContentText("家长控制正在守护位置权限")
                .setSmallIcon(R.drawable.ic_check)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void startLocationGuard() {
        locationCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkLocationPermission();
                handler.postDelayed(this, 10000); // 每10秒检查一次
            }
        };
        handler.postDelayed(locationCheckRunnable, 5000);
    }

    private void checkLocationPermission() {
        try {
            // 检查GPS是否开启
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            boolean isGpsEnabled = locationManager != null && 
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            
            // 检查位置权限
            boolean hasFineLocation = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean hasCoarseLocation = ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            
            // 如果位置权限被关闭或GPS被关闭，弹出警告
            if (!hasFineLocation || !hasCoarseLocation) {
                Log.w(TAG, "位置权限被关闭！正在尝试重新请求...");
                showLocationWarning();
            }
            
            if (!isGpsEnabled) {
                Log.w(TAG, "GPS已关闭，尝试重新开启...");
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ 无法直接开关GPS，提示用户
                        showLocationWarning();
                    } else {
                        // 低版本尝试直接开启
                        Settings.Secure.putInt(getContentResolver(), 
                                Settings.Secure.LOCATION_MODE, 
                                Settings.Secure.LOCATION_MODE_HIGH_ACCURACY);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "无法自动开启GPS", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "定位检查异常", e);
        }
    }

    private void showLocationWarning() {
        // 发送广播让前台Activity显示警告
        Intent intent = new Intent("com.yousafdev.KidShield.LOCATION_WARNING");
        sendBroadcast(intent);
        
        // 显示Toast提示
        Toast.makeText(this, R.string.location_disabled_alert, Toast.LENGTH_LONG).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (handler != null && locationCheckRunnable != null) {
            handler.removeCallbacks(locationCheckRunnable);
        }
        // 自动重启服务
        Intent restartIntent = new Intent(this, LocationGuardService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }
        super.onDestroy();
    }
}
