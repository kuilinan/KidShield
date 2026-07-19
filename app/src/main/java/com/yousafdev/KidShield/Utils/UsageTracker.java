package com.yousafdev.KidShield.Utils;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UsageTracker {
    private static final String TAG = "UsageTracker";
    private final Context context;
    private final PackageManager packageManager;
    private final UsageStatsManager usageStatsManager;

    public UsageTracker(Context context) {
        this.context = context;
        this.packageManager = context.getPackageManager();
        this.usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    /**
     * 检查是否有使用权限
     */
    public static boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * 打开使用权限设置页面
     */
    public static Intent getUsageSettingsIntent() {
        return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    /**
     * 获取今天的总屏幕使用时间（毫秒）
     */
    public long getTodayTotalUsage() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        Map<String, UsageStats> stats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
        long total = 0;
        if (stats != null) {
            for (UsageStats stat : stats.values()) {
                total += stat.getTotalTimeInForeground();
            }
        }
        return total;
    }

    /**
     * 获取各应用使用时间列表（按时间降序）
     */
    public List<AppUsageInfo> getAppUsageList() {
        List<AppUsageInfo> list = new ArrayList<>();
        
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        try {
            Map<String, UsageStats> statsMap = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime);
            if (statsMap != null) {
                for (Map.Entry<String, UsageStats> entry : statsMap.entrySet()) {
                    String packageName = entry.getKey();
                    UsageStats stats = entry.getValue();
                    long timeInForeground = stats.getTotalTimeInForeground();
                    
                    if (timeInForeground > 1000) { // 超过1秒才统计
                        String appName = getAppName(packageName);
                        list.add(new AppUsageInfo(packageName, appName, timeInForeground));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取使用统计失败", e);
        }

        // 按使用时间降序
        Collections.sort(list, (a, b) -> Long.compare(b.usageTime, a.usageTime));
        return list;
    }

    private String getAppName(String packageName) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    /**
     * 获取某个应用的今日使用时间
     */
    public long getAppUsageTime(String packageName) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        Map<String, UsageStats> stats = usageStatsManager.queryAndAggregateUsageStats(startTime, System.currentTimeMillis());
        if (stats != null && stats.containsKey(packageName)) {
            return stats.get(packageName).getTotalTimeInForeground();
        }
        return 0;
    }

    /**
     * 格式化时间显示
     */
    public static String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        minutes = minutes % 60;
        
        if (hours > 0) {
            return String.format(Locale.CHINA, "%d小时%d分钟", hours, minutes);
        } else {
            return String.format(Locale.CHINA, "%d分钟", minutes);
        }
    }

    /**
     * 应用使用信息数据类
     */
    public static class AppUsageInfo {
        public final String packageName;
        public final String appName;
        public final long usageTime; // 毫秒
        public final String formattedTime;

        public AppUsageInfo(String packageName, String appName, long usageTime) {
            this.packageName = packageName;
            this.appName = appName;
            this.usageTime = usageTime;
            this.formattedTime = formatDuration(usageTime);
        }
    }
}
