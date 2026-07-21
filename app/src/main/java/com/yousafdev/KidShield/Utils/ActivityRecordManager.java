package com.yousafdev.KidShield.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Activity 使用记录管理器
 * 无第三方依赖，纯 SharedPreferences + JSON 实现
 * 用于学习模式下的 Activity 采集和展示
 */
public class ActivityRecordManager {
    private static final String TAG = "ActivityRecordManager";
    private static final String PREFS_NAME = "activity_records";
    private static final String KEY_RECORDS = "records_json";
    private static final String KEY_LAST_PACKAGE = "last_package";
    private static final String KEY_LAST_CLASS = "last_class";
    private static final String KEY_LAST_TIME = "last_time";
    private static final String KEY_SESSION_ID = "session_id";
    private static final int MAX_RECORDS = 1000;

    private final Context context;
    private final SharedPreferences prefs;

    public ActivityRecordManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 记录一次 Activity 切换
     */
    public void recordActivity(String packageName, String className) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(className)) return;

        long now = System.currentTimeMillis();
        String lastPackage = prefs.getString(KEY_LAST_PACKAGE, "");
        String lastClass = prefs.getString(KEY_LAST_CLASS, "");
        long lastTime = prefs.getLong(KEY_LAST_TIME, 0L);

        // 更新上一个 Activity 的停留时长
        if (!TextUtils.isEmpty(lastPackage) && !TextUtils.isEmpty(lastClass) && lastTime > 0) {
            long duration = now - lastTime;
            if (duration > 500) { // 超过500ms才算有效停留
                updateDuration(lastPackage, lastClass, duration);
            }
        }

        // 记录当前 Activity（避免重复记录同一个页面）
        if (packageName.equals(lastPackage) && className.equals(lastClass)) {
            prefs.edit().putLong(KEY_LAST_TIME, now).apply();
            return;
        }

        // 获取App名称
        String appName = getAppName(packageName);
        boolean isSystem = isSystemApp(packageName);
        String sessionId = getOrCreateSessionId();

        // 创建新记录
        try {
            JSONObject record = new JSONObject();
            record.put("packageName", packageName);
            record.put("className", className);
            record.put("appName", appName);
            record.put("time", now);
            record.put("durationMs", 0);
            record.put("sessionId", sessionId);
            record.put("isBlocked", false);
            record.put("isSystemApp", isSystem);

            JSONArray records = getRecordsArray();
            records.put(record);

            // 限制最大记录数
            while (records.length() > MAX_RECORDS) {
                records.remove(0);
            }

            prefs.edit()
                .putString(KEY_RECORDS, records.toString())
                .putString(KEY_LAST_PACKAGE, packageName)
                .putString(KEY_LAST_CLASS, className)
                .putLong(KEY_LAST_TIME, now)
                .apply();

            Log.d(TAG, "记录Activity: " + appName + "/" + className);
        } catch (Exception e) {
            Log.e(TAG, "记录Activity失败", e);
        }
    }

    /**
     * 标记某个 Activity 被拦截
     */
    public void markBlocked(String packageName, String className) {
        try {
            JSONArray records = getRecordsArray();
            for (int i = records.length() - 1; i >= 0; i--) {
                JSONObject record = records.getJSONObject(i);
                if (record.getString("packageName").equals(packageName)
                    && record.getString("className").equals(className)) {
                    record.put("isBlocked", true);
                    break;
                }
            }
            prefs.edit().putString(KEY_RECORDS, records.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "标记拦截状态失败", e);
        }
    }

    /**
     * 更新某个 Activity 的停留时长
     */
    private void updateDuration(String packageName, String className, long extraMs) {
        try {
            JSONArray records = getRecordsArray();
            for (int i = records.length() - 1; i >= 0; i--) {
                JSONObject record = records.getJSONObject(i);
                if (record.getString("packageName").equals(packageName)
                    && record.getString("className").equals(className)) {
                    long currentDuration = record.optLong("durationMs", 0);
                    record.put("durationMs", currentDuration + extraMs);
                    break;
                }
            }
            prefs.edit().putString(KEY_RECORDS, records.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "更新时长失败", e);
        }
    }

    /**
     * 获取 App 使用排行（按总时长降序）
     */
    public List<AppUsageSummary> getAppSummary() {
        List<AppUsageSummary> summaryList = new ArrayList<>();
        try {
            JSONArray records = getRecordsArray();

            // 按包名聚合
            java.util.Map<String, AppUsageSummary> map = new java.util.LinkedHashMap<>();
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.getJSONObject(i);
                String pkg = record.getString("packageName");
                String appName = record.optString("appName", pkg);
                long duration = record.optLong("durationMs", 0);

                AppUsageSummary summary = map.get(pkg);
                if (summary == null) {
                    summary = new AppUsageSummary(pkg, appName, 0, 0);
                    map.put(pkg, summary);
                }
                summary.visitCount++;
                summary.totalDuration += duration;
            }

            summaryList.addAll(map.values());
            // 按总时长降序
            Collections.sort(summaryList, (a, b) -> Long.compare(b.totalDuration, a.totalDuration));
        } catch (Exception e) {
            Log.e(TAG, "获取App排行失败", e);
        }
        return summaryList;
    }

    /**
     * 获取某个 App 的 Activity 详情
     */
    public List<ActivityDetail> getActivityDetail(String packageName) {
        List<ActivityDetail> detailList = new ArrayList<>();
        try {
            JSONArray records = getRecordsArray();
            java.util.Map<String, ActivityDetail> map = new java.util.LinkedHashMap<>();

            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.getJSONObject(i);
                if (!record.getString("packageName").equals(packageName)) continue;

                String cls = record.getString("className");
                long duration = record.optLong("durationMs", 0);

                ActivityDetail detail = map.get(cls);
                if (detail == null) {
                    detail = new ActivityDetail(cls, 0, 0);
                    map.put(cls, detail);
                }
                detail.visitCount++;
                detail.totalDuration += duration;
            }

            detailList.addAll(map.values());
            Collections.sort(detailList, (a, b) -> Long.compare(b.totalDuration, a.totalDuration));
        } catch (Exception e) {
            Log.e(TAG, "获取Activity详情失败", e);
        }
        return detailList;
    }

    /**
     * 获取最近的N条记录
     */
    public List<JSONObject> getRecentRecords(int limit) {
        List<JSONObject> list = new ArrayList<>();
        try {
            JSONArray records = getRecordsArray();
            int start = Math.max(0, records.length() - limit);
            for (int i = records.length() - 1; i >= start; i--) {
                list.add(records.getJSONObject(i));
            }
        } catch (Exception e) {
            Log.e(TAG, "获取最近记录失败", e);
        }
        return list;
    }

    /** 清空所有记录 */
    public void clearAll() {
        prefs.edit()
            .remove(KEY_RECORDS)
            .remove(KEY_LAST_PACKAGE)
            .remove(KEY_LAST_CLASS)
            .remove(KEY_LAST_TIME)
            .apply();
        Log.d(TAG, "Activity记录已清空");
    }

    // ========== 内部方法 ==========

    private JSONArray getRecordsArray() {
        try {
            String json = prefs.getString(KEY_RECORDS, "[]");
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private String getOrCreateSessionId() {
        String sid = prefs.getString(KEY_SESSION_ID, "");
        if (TextUtils.isEmpty(sid)) {
            sid = String.valueOf(System.currentTimeMillis());
            prefs.edit().putString(KEY_SESSION_ID, sid).apply();
        }
        return sid;
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private boolean isSystemApp(String packageName) {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(packageName, 0);
            return (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 数据类 ==========

    public static class AppUsageSummary {
        public final String packageName;
        public final String appName;
        public int visitCount;
        public long totalDuration;

        public AppUsageSummary(String packageName, String appName, int visitCount, long totalDuration) {
            this.packageName = packageName;
            this.appName = appName;
            this.visitCount = visitCount;
            this.totalDuration = totalDuration;
        }

        public String getFormattedDuration() {
            long totalSec = totalDuration / 1000;
            if (totalSec > 3600) {
                return (totalSec / 3600) + "h" + ((totalSec % 3600) / 60) + "m";
            }
            return (totalSec / 60) + "m";
        }
    }

    public static class ActivityDetail {
        public final String className;
        public int visitCount;
        public long totalDuration;

        public ActivityDetail(String className, int visitCount, long totalDuration) {
            this.className = className;
            this.visitCount = visitCount;
            this.totalDuration = totalDuration;
        }

        public String getSimpleName() {
            String[] parts = className.split("\\.");
            return parts.length > 0 ? parts[parts.length - 1] : className;
        }

        public String getFormattedDuration() {
            long totalSec = totalDuration / 1000;
            if (totalSec > 3600) {
                return (totalSec / 3600) + "h" + ((totalSec % 3600) / 60) + "m";
            }
            return (totalSec / 60) + "m";
        }
    }
}
