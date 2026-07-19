package com.yousafdev.KidShield.Utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.yousafdev.KidShield.Services.GuardService;
import com.yousafdev.KidShield.Services.LocationGuardService;
import com.yousafdev.KidShield.Services.MonitoringService;

/**
 * 电源状态监听器（阳光守护保活机制）
 * 当手机充电/断电时自动重启守护服务，防止被系统杀死
 */
public class PowerConnectionReceiver extends BroadcastReceiver {
    private static final String TAG = "PowerConnectionReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            Log.d(TAG, "电源已连接 → 重新启动守护服务");
            restartGuardServices(context);
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            Log.d(TAG, "电源已断开 → 重新启动守护服务");
            restartGuardServices(context);
        }
    }

    private void restartGuardServices(Context context) {
        try {
            Intent monitoringIntent = new Intent(context, MonitoringService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(monitoringIntent);
            } else {
                context.startService(monitoringIntent);
            }

            Intent guardIntent = new Intent(context, GuardService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(guardIntent);
            } else {
                context.startService(guardIntent);
            }

            try {
                Intent locationIntent = new Intent(context, LocationGuardService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(locationIntent);
                } else {
                    context.startService(locationIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "启动位置守护失败", e);
            }

            Log.d(TAG, "所有守护服务已重新启动");
        } catch (Exception e) {
            Log.e(TAG, "重启守护服务失败", e);
        }
    }
}
