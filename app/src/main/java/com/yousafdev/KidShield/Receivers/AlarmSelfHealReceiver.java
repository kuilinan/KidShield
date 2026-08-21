package com.yousafdev.KidShield.Receivers;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.yousafdev.KidShield.Network.KidCommandStore;
import com.yousafdev.KidShield.Services.GuardService;
/**
 * Alarm 自愈接收器：
 * 1. 当 GuardService 被杀死时，通过 AlarmManager 重新拉起
 * 2. 锁屏指令到期时（ACTION_LOCK_EXPIRED）自动解除锁屏
 */
public class AlarmSelfHealReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmSelfHealReceiver";
    public static final String ACTION_LOCK_EXPIRED = "com.yousafdev.KidShield.ACTION_LOCK_EXPIRED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;

        // 锁屏指令到期：清除锁屏状态（指令生命周期结束才失效！）
        if (ACTION_LOCK_EXPIRED.equals(action)) {
            Log.i(TAG, "🔓 锁屏指令到期，自动解除");
            try {
                KidCommandStore store = new KidCommandStore(context);
                store.clearLock();
            } catch (Exception e) {
                Log.e(TAG, "解除锁屏失败", e);
            }
            return;
        }

        // 原有逻辑：GuardService 自愈
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
