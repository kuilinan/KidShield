package com.yousafdev.KidShield.Utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

/**
 * VIVO 系统保活适配助手
 * 
 * VIVO OriginOS / Funtouch OS 杀后台机制：
 * 1. 智能省电引擎 — 自动清理后台高频唤醒应用
 * 2. 锁屏清理 — 默认关闭屏幕后清理所有非白名单应用
 * 3. 自启动管理 — 禁止应用开机自启和后台启动
 * 4. 后台高耗电管理 — 限制后台活动
 * 5. 通知权限管理 — 限制通知展示（间接影响前台服务优先级）
 * 
 * 本类提供：引导跳转 + 自动配置建议 + 状态检测
 */
public class VivoKeepAliveHelper {
    private static final String TAG = "VivoKeepAliveHelper";
    private static final String PREF_NAME = "vivo_keepalive";
    
    // VIVO 系统包名
    private static final String VIVO_SETTINGS_PKG = "com.android.settings";
    private static final String VIVO_SECURITY_CENTER = "com.iqoo.secure";
    private static final String VIVO_BACKGROUND_MANAGER = "com.vivo.background";
    
    /**
     * 检测当前设备是否为 VIVO 品牌
     */
    public static boolean isVivoDevice() {
        String brand = Build.BRAND.toLowerCase();
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        return brand.contains("vivo") || 
               brand.contains("bbk") ||
               manufacturer.contains("vivo") ||
               manufacturer.contains("bbk");
    }
    
    /**
     * 判断 VIVO 系统版本
     */
    public static boolean isOriginOS() {
        // OriginOS 通常基于 Android 11+
        return isVivoDevice() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }
    
    /**
     * 应用保活策略入口
     * 在 GuardService 启动时调用
     */
    public static void applyKeepAlive(Context context) {
        if (!isVivoDevice()) {
            Log.d(TAG, "非VIVO设备，跳过VIVO保活策略");
            return;
        }
        
        Log.d(TAG, "✦ VIVO设备检测到，应用强保活策略 ✦");
        
        // 记录保活状态
        recordKeepAliveAttempt(context);
        
        // 不需要跳转 UI 的后台操作
        try {
            // 1. 请求忽略电池优化（白名单）
            requestIgnoreBatteryOptimization(context);
            
            // 2. 检查省电策略状态
            checkPowerSaveStatus(context);
        } catch (Exception e) {
            Log.e(TAG, "保活策略异常", e);
        }
    }
    
    /**
     * 请求忽略电池优化（系统白名单）
     * 这是所有 Android 通用的，但在 VIVO 上至关重要
     */
    private static void requestIgnoreBatteryOptimization(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;
        
        String packageName = context.getPackageName();
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            Log.w(TAG, "⚠ 应用未被加入电池白名单，需要引导用户开启");
            // 记录状态，由引导界面提示用户开启
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("need_battery_whitelist", true)
                    .apply();
        } else {
            Log.d(TAG, "✅ 已在电池白名单中");
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("need_battery_whitelist", false)
                    .apply();
        }
    }
    
    /**
     * 检查 VIVO 省电策略状态
     */
    private static void checkPowerSaveStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean needGuide = false;
        
        // 检测 VIVO 智能省电引擎
        if (isVivoSmartPowerSaveEnabled(context)) {
            Log.w(TAG, "⚠ VIVO 智能省电引擎可能限制后台运行");
            needGuide = true;
        }
        
        prefs.edit().putBoolean("vivo_power_save_issue", needGuide).apply();
    }
    
    /**
     * 检测 VIVO 是否开启了智能省电
     */
    private static boolean isVivoSmartPowerSaveEnabled(Context context) {
        // VIVO 的智能省电开关无法通过 API 直接读取
        // 通过 SharedPreference 缓存用户反馈的结果
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean("vivo_power_save_enabled", true); // 默认认为开启了
    }
    
    // ==================== 引导跳转方法 ====================
    
    /**
     * 打开 VIVO 电池优化白名单设置页
     */
    public static Intent getBatteryOptimizationIntent(Context context) {
        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        return intent;
    }
    
    /**
     * 打开 VIVO 自启动管理页
     * VIVO 的自启动管理在不同版本路径不同
     */
    public static Intent getAutoStartIntent(Context context) {
        Intent intent = new Intent();
        intent.setClassName("com.iqoo.secure", 
                "com.iqoo.secure.ui.phoneoptimize.addwhite.AdWhiteListActivity");
        intent.putExtra("packagename", context.getPackageName());
        
        // 如果上面那个不行，试试这个（OriginOS 不同版本）
        if (!isIntentAvailable(context, intent)) {
            intent = new Intent();
            intent.setClassName("com.iqoo.secure",
                    "com.iqoo.secure.MainActivity");
            intent.putExtra("from", "autostart");
        }
        
        return intent;
    }
    
    /**
     * 打开 VIVO 锁屏清理白名单设置
     */
    public static Intent getLockScreenCleanIntent(Context context) {
        Intent intent = new Intent();
        intent.setClassName("com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.addwhite.AdWhiteListActivity");
        intent.putExtra("type", "lockscreen");
        intent.putExtra("packagename", context.getPackageName());
        return intent;
    }
    
    /**
     * 打开 VIVO 后台高耗电管理
     */
    public static Intent getHighPowerIntent(Context context) {
        Intent intent = new Intent();
        intent.setClassName("com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.addwhite.AdWhiteListActivity");
        intent.putExtra("type", "power");
        intent.putExtra("packagename", context.getPackageName());
        return intent;
    }
    
    /**
     * 打开 VIVO 通知管理设置
     */
    public static Intent getNotificationSettingsIntent(Context context) {
        Intent intent = new Intent();
        intent.setClassName("com.android.settings",
                "com.android.settings.Settings$AppNotificationSettingsActivity");
        intent.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
        return intent;
    }
    
    /**
     * 锁定多任务卡片 - 引导用户操作
     * VIVO 的多任务锁定方式是：进入多任务界面 → 下拉应用卡片 → 出现锁图标
     * 这部分只能引导用户手动操作
     */
    public static String getLockTaskGuideText() {
        return "🔒 VIVO 多任务锁定方法：\n" +
               "1. 按最近任务键进入多任务界面\n" +
               "2. 找到 KidShield 卡片\n" +
               "3. 向下拖动卡片直到出现 🔒 图标\n" +
               "4. 松开即锁定，之后清理后台不会关闭该应用";
    }
    
    /**
     * 获取 VIVO 保活引导界面需要展示的信息
     */
    public static String[] getKeepAliveGuideItems(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean needBattery = prefs.getBoolean("need_battery_whitelist", true);
        boolean powerSaveIssue = prefs.getBoolean("vivo_power_save_issue", true);
        
        if (isOriginOS()) {
            return new String[]{
                    (needBattery ? "❌ " : "✅ ") + "电池白名单（必须）",
                    (powerSaveIssue ? "❌ " : "✅ ") + "智能省电排除",
                    "❌ 自启动管理",
                    "❌ 锁屏清理白名单",
                    "❌ 后台高耗电白名单",
                    "❌ 多任务锁定（手动）"
            };
        } else {
            return new String[]{
                    (needBattery ? "❌ " : "✅ ") + "电池白名单（必须）",
                    "❌ 自启动管理",
                    "❌ 锁屏清理白名单",
                    "❌ 后台高耗电白名单"
            };
        }
    }
    
    private static boolean isIntentAvailable(Context context, Intent intent) {
        return context.getPackageManager()
                .queryIntentActivities(intent, 0)
                .size() > 0;
    }
    
    private static void recordKeepAliveAttempt(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong("last_keepalive_time", System.currentTimeMillis())
                .putInt("keepalive_count", 
                    context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .getInt("keepalive_count", 0) + 1)
                .apply();
    }
    
    /**
     * 检测保活状态是否全部配置完成
     */
    public static boolean isFullyConfigured(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (pm == null) return false;
        
        // 至少电池白名单要开启
        return pm.isIgnoringBatteryOptimizations(context.getPackageName());
    }
}
