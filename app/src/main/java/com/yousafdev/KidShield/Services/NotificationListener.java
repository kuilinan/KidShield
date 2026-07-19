package com.yousafdev.KidShield.Services;

import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class NotificationListener extends NotificationListenerService {
    private static final String TAG = "NotificationListener";
    private String currentUid;

    @Override
    public void onCreate() {
        super.onCreate();
        // Firebase已移除
        String savedUid = getSharedPreferences("kidshield", MODE_PRIVATE).getString("user_id", null); if (savedUid != null) {
            currentUid = savedUid;
        }
        Log.d(TAG, "通知监听服务已启动");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        String key = sbn.getKey();
        long postTime = sbn.getPostTime();

        String title = "";
        String text = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Bundle extras = sbn.getNotification().extras;
            if (extras != null) {
                title = extras.getString("android.title", "");
                text = extras.getString("android.text", "");
            }
        }

        Log.d(TAG, "通知来自: " + packageName + " | 标题: " + title);

        if (currentUid != null && !packageName.contains("yousafdev")) {
            String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                    .format(new Date(postTime));

            Map<String, Object> data = new HashMap<>();
            data.put("packageName", packageName);
            data.put("title", title != null ? title : "");
            data.put("text", text != null ? text.substring(0, Math.min(text.length(), 200)) : "");
            data.put("time", timeStr);
            data.put("timestamp", postTime);

            mDatabase.child("notifications").child(currentUid)
                    .child(key.replace(".", "_"))
                    .setValue(data);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "通知已移除: " + sbn.getPackageName());
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "通知监听服务已连接");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "通知监听服务断开，尝试重新连接");
        try {
            requestRebind(new ComponentName(this, NotificationListener.class));
        } catch (Exception e) {
            Log.e(TAG, "重新绑定失败", e);
        }
    }
}
