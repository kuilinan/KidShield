package com.yousafdev.KidShield.Receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.yousafdev.KidShield.Services.GuardService;

/**
 * Alarm 自愈接收器：当 GuardService 被杀死时，通过 AlarmManager 重新拉起
 * 从 GuardService 内部类提取为独立类（解决 Manifest 注册兼容性问题）
 */
public class AlarmSelfHealReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmSelfHealReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isServiceRunning(context)) {
            Log.w(TAG, "⚠ GuardService 已被杀！Alarm 自愈机制重新拉起...");
            Intent serviceIntent = new Intent(context, GuardService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }

    private static boolean isServiceRunning(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("guard_health", Context.MODE_PRIVATE);
            long lastHeartbeat = prefs.getLong("last_heartbeat", 0);
            return (System.currentTimeMillis() - lastHeartbeat) < 30000;
        } catch (Exception e) {
            return false;
        }
    }
}
