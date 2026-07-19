package com.yousafdev.KidShield.Utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.yousafdev.KidShield.Services.GuardService;
import com.yousafdev.KidShield.Services.LocationGuardService;
import com.yousafdev.KidShield.Services.MonitoringService;

import java.util.concurrent.TimeUnit;

/**
 * 开机/解锁自启接收器
 * 监听 BOOT_COMPLETED（开机） 和 USER_PRESENT（解锁屏幕）
 * 启动所有守护服务防止管控被绕过
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final long SYNC_INTERVAL = TimeUnit.MINUTES.toMillis(1);

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "📱 手机开机完成 → 启动所有守护服务");
        } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
            Log.d(TAG, "🔓 屏幕解锁 → 确保所有守护服务运行");
        } else {
            return;
        }

        startAllGuardServices(context);
        scheduleDataSync(context);
    }

    private void startAllGuardServices(Context context) {
        try {
            // 1. 启动监控服务（白名单拦截 + 开发者封锁）
            Intent monitoringIntent = new Intent(context, MonitoringService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(monitoringIntent);
            } else {
                context.startService(monitoringIntent);
            }
            Log.d(TAG, "✅ MonitoringService 已启动");

            // 2. 启动综合守护服务（设备管理员检查 + 开发者封锁 + 保活）
            Intent guardIntent = new Intent(context, GuardService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(guardIntent);
            } else {
                context.startService(guardIntent);
            }
            Log.d(TAG, "✅ GuardService 已启动");

            // 3. 启动位置守护服务
            try {
                Intent locationIntent = new Intent(context, LocationGuardService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(locationIntent);
                } else {
                    context.startService(locationIntent);
                }
                Log.d(TAG, "✅ LocationGuardService 已启动");
            } catch (Exception e) {
                Log.e(TAG, "启动位置守护失败（可忽略）", e);
            }

        } catch (Exception e) {
            Log.e(TAG, "启动守护服务失败", e);
        }
    }

    public static void scheduleDataSync(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M 
            ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT 
            : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, flags);
        
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + SYNC_INTERVAL,
                    SYNC_INTERVAL,
                    pendingIntent
            );
            Log.d(TAG, "数据同步闹钟已设置，每 " + TimeUnit.MILLISECONDS.toMinutes(SYNC_INTERVAL) + " 分钟一次");
        }
    }
}
