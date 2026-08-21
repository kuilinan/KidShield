package com.yousafdev.KidShield.Services;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.yousafdev.KidShield.R;

import java.util.List;

/**
 * AntiBypassService - 防绕过检测（参考 xiaopacai AntiBypassService.kt, Apache 2.0）
 *
 * 检测孩子可能绕过管控的方式：
 * 1. 无障碍服务被关闭（拦截失效！）
 * 2. 使用情况访问权限被关闭（统计失效）
 * 3. 电池优化被开启（后台被杀，守护中断）
 * 4. 孩子打开"应用信息页"（可能想停用/卸载 KidShield）
 * 5. 定时自检 + 开机自检
 *
 * 检测到问题 → 本地通知提醒家长 + 拉起权限引导
 */
public class AntiBypassService {

    private static final String TAG = "AntiBypass";
    private static final String CHANNEL_SECURITY = "kidshield_security";

    /** 无障碍服务是否开启（KidShield 的 AppAccessibilityService） */
    public static boolean isAccessibilityServiceEnabled(Context context) {
        try {
            String expected = context.getPackageName() + "/" + AppAccessibilityService.class.getName();
            String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabledServices == null) return false;
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
            splitter.setString(enabledServices);
            while (splitter.hasNext()) {
                if (splitter.next().equalsIgnoreCase(expected)) return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "检查无障碍失败", e);
        }
        return false;
    }

    /** 使用情况访问权限是否开启 */
    public static boolean isUsageStatsPermissionGranted(Context context) {
        try {
            AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    /** 电池优化是否被豁免（豁免=不被杀后台） */
    public static boolean isIgnoringBatteryOptimizations(Context context) {
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(context.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    /** 设备管理员是否激活（防卸载） */
    public static boolean isDeviceAdminActive(Context context) {
        try {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, com.yousafdev.KidShield.DeviceAdmin.AdminReceiver.class);
            return dpm != null && dpm.isAdminActive(admin);
        } catch (Exception e) {
            return false;
        }
    }

    /** 检查孩子是否打开"应用信息页"（想停用/卸载 KidShield） */
    public static boolean isAppInfoPage(Context context, String packageName) {
        if (packageName == null) return false;
        // 系统设置的应用详情页
        if (packageName.equals("com.android.settings")) {
            // 由上层通过 ActivityRecordManager 判断具体页面
            return false;
        }
        return false;
    }

    /**
     * 全量检查所有绕过向量，返回问题列表（空=一切正常）
     * 参考 xiaopacai checkAllBypassVectors()
     */
    public static List<String> checkAllBypassVectors(Context context) {
        java.util.List<String> issues = new java.util.ArrayList<>();

        if (!isAccessibilityServiceEnabled(context)) {
            issues.add("无障碍服务被关闭！应用拦截已失效");
        }
        if (!isUsageStatsPermissionGranted(context)) {
            issues.add("使用情况访问权限被关闭！使用统计已失效");
        }
        if (!isIgnoringBatteryOptimizations(context)) {
            issues.add("电池优化未豁免！守护服务可能被杀");
        }
        if (!isDeviceAdminActive(context)) {
            issues.add("设备管理员未激活！应用可能被卸载");
        }
        return issues;
    }

    /** 发送安全告警通知（本地通知，提醒家长） */
    public static void notifySecurityIssue(Context context, String title, String message) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_SECURITY, "安全告警", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(channel);
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SECURITY)
                    .setSmallIcon(R.drawable.ic_pending)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);
            nm.notify((int) System.currentTimeMillis() % 10000, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "发送告警通知失败", e);
        }
    }

    /** 定时自检入口（由 GuardService 定时调用） */
    public static void periodicSelfCheck(Context context) {
        List<String> issues = checkAllBypassVectors(context);
        if (!issues.isEmpty()) {
            Log.w(TAG, "⚠ 检测到安全风险: " + issues);
            notifySecurityIssue(context, "⚠️ KidShield 守护异常", issues.get(0));
        }
    }
}