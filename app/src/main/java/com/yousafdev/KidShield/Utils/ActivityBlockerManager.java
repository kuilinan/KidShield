package com.yousafdev.KidShield.Utils;

import android.content.Context;
import com.yousafdev.KidShield.Network.CommandStore;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity 级拦截 + URL 拦截管理器
 * 支持：
 * 1. 精细到 Activity 级别的 App 功能拦截（如拦截抖音的"直播"Activity）
 * 2. URL 黑名单（家长端添加网址后，浏览器访问该网址时自动拦截）
 * 
 * 纯 SharedPreferences + JSON 实现，无第三方依赖
 */
public class ActivityBlockerManager {
    private static final String TAG = "ActivityBlockerManager";

    private final CommandStore commandStore;

    public ActivityBlockerManager(Context context) {
        this.commandStore = new CommandStore(context);
    }

    // ==================== Activity 级拦截规则 ====================

    /**
     * 检查某个 Activity 是否应该被拦截
     */
    public boolean shouldBlockActivity(String packageName, String className) {
        if (TextUtils.isEmpty(packageName) || TextUtils.isEmpty(className)) return false;

        try {
            JSONArray rules = getActivityRules();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                if (!rule.optBoolean("enabled", true)) continue;

                String rulePkg = rule.optString("packageName", "");
                String ruleCls = rule.optString("activityClassName", "");

                // 包名精确匹配，类名模糊匹配（支持部分匹配）
                if (packageName.equals(rulePkg) && className.contains(ruleCls)) {
                    Log.d(TAG, "Activity拦截命中: " + packageName + "/" + className);
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查Activity拦截失败", e);
        }
        return false;
    }

    /**
     * 获取拦截原因的文案
     */
    public String getBlockReason(String packageName, String className) {
        try {
            JSONArray rules = getActivityRules();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                if (!rule.optBoolean("enabled", true)) continue;
                if (packageName.equals(rule.optString("packageName", ""))
                    && className.contains(rule.optString("activityClassName", ""))) {
                    return rule.optString("reason", "此功能已被家长禁止");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取拦截原因失败", e);
        }
        return "此功能已被家长禁止";
    }

    /**
     * 添加一条 Activity 拦截规则
     */
    public void addActivityRule(String packageName, String activityClassName, String appName, String reason) {
        try {
            JSONArray rules = getActivityRules();

            // 如果已存在相同规则，更新
            for (int i = 0; i < rules.length(); i++) {
                JSONObject existing = rules.getJSONObject(i);
                if (packageName.equals(existing.optString("packageName", ""))
                    && activityClassName.equals(existing.optString("activityClassName", ""))) {
                    existing.put("appName", appName != null ? appName : "");
                    existing.put("reason", reason != null ? reason : "此功能已被家长禁止");
                    existing.put("enabled", true);
                    saveActivityRules(rules);
                    Log.d(TAG, "Activity拦截规则已更新: " + packageName + "/" + activityClassName);
                    return;
                }
            }

            JSONObject rule = new JSONObject();
            rule.put("packageName", packageName);
            rule.put("activityClassName", activityClassName);
            rule.put("appName", appName != null ? appName : "");
            rule.put("reason", reason != null ? reason : "此功能已被家长禁止");
            rule.put("enabled", true);
            rules.put(rule);
            saveActivityRules(rules);
            Log.d(TAG, "Activity拦截规则已添加: " + packageName + "/" + activityClassName);
        } catch (Exception e) {
            Log.e(TAG, "添加Activity拦截规则失败", e);
        }
    }

    /**
     * 移除一条 Activity 拦截规则
     */
    public void removeActivityRule(String packageName, String activityClassName) {
        try {
            JSONArray rules = getActivityRules();
            JSONArray newRules = new JSONArray();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                if (!(packageName.equals(rule.optString("packageName", ""))
                    && activityClassName.equals(rule.optString("activityClassName", "")))) {
                    newRules.put(rule);
                }
            }
            saveActivityRules(newRules);
            Log.d(TAG, "Activity拦截规则已移除: " + packageName + "/" + activityClassName);
        } catch (Exception e) {
            Log.e(TAG, "移除Activity拦截规则失败", e);
        }
    }

    /**
     * 获取某个 App 的所有拦截规则
     */
    public List<ActivityBlockRule> getRulesForApp(String packageName) {
        List<ActivityBlockRule> list = new ArrayList<>();
        try {
            JSONArray rules = getActivityRules();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                if (packageName.equals(rule.optString("packageName", ""))) {
                    list.add(new ActivityBlockRule(
                        rule.optString("packageName", ""),
                        rule.optString("activityClassName", ""),
                        rule.optString("appName", ""),
                        rule.optString("reason", ""),
                        rule.optBoolean("enabled", true)
                    ));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取App拦截规则失败", e);
        }
        return list;
    }

    /**
     * 获取所有拦截规则
     */
    public List<ActivityBlockRule> getAllActivityRules() {
        List<ActivityBlockRule> list = new ArrayList<>();
        try {
            JSONArray rules = getActivityRules();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                list.add(new ActivityBlockRule(
                    rule.optString("packageName", ""),
                    rule.optString("activityClassName", ""),
                    rule.optString("appName", ""),
                    rule.optString("reason", ""),
                    rule.optBoolean("enabled", true)
                ));
            }
        } catch (Exception e) {
            Log.e(TAG, "获取所有拦截规则失败", e);
        }
        return list;
    }

    // ==================== URL 黑名单 / 网址拦截 ====================

    /**
     * 检查某个 URL 是否在黑名单中
     * @param url 完整的网址
     * @return true=应该被拦截
     */
    public boolean isUrlBlocked(String url) {
        if (TextUtils.isEmpty(url)) return false;

        try {
            JSONArray urls = getUrlBlacklist();
            String lowerUrl = url.toLowerCase();

            for (int i = 0; i < urls.length(); i++) {
                String blockedUrl = urls.getString(i).toLowerCase().trim();
                if (TextUtils.isEmpty(blockedUrl)) continue;

                // 支持精确匹配和域名匹配
                if (lowerUrl.contains(blockedUrl) || blockedUrl.contains(lowerUrl)) {
                    Log.d(TAG, "URL拦截命中: " + url + " (规则: " + blockedUrl + ")");
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查URL黑名单失败", e);
        }
        return false;
    }

    /**
     * 检查指定包名+类名是否可能是浏览器页面
     * 若是，则提取URL并检查黑名单
     */
    public boolean isBrowserUrlBlocked(String packageName, String className) {
        // 常见的浏览器包名
        String[] browserPackages = {
            "com.android.chrome",           // Chrome
            "com.android.browser",           // 原生浏览器
            "com.miui.browser",              // 小米浏览器
            "com.huawei.browser",            // 华为浏览器
            "com.heytap.browser",            // OPPO浏览器
            "com.vivo.browser",              // vivo浏览器
            "com.tencent.mtt",               // QQ浏览器
            "com.UCMobile",                  // UC浏览器
            "com.baidu.browser",             // 百度浏览器
            "com.samsung.android.browser",   // 三星浏览器
            "org.mozilla.firefox",           // Firefox
            "org.mozilla.firefox_beta",      // Firefox Beta
            "com.microsoft.emmx",            // Edge
            "com.duckduckgo.mobile.android", // DuckDuckGo
            "com.opera.browser",             // Opera
            "com.opera.mini.android"         // Opera Mini
        };

        // 检查是否浏览器
        boolean isBrowser = false;
        for (String bp : browserPackages) {
            if (bp.equals(packageName)) {
                isBrowser = true;
                break;
            }
        }
        // 类名包含 webview/browser 也视为浏览器
        String lowerCls = className.toLowerCase();
        if (lowerCls.contains("webview") || lowerCls.contains("browser")
            || lowerCls.contains("chrome") || className.contains(".WebView")) {
            isBrowser = true;
        }

        return isBrowser;
    }

    /**
     * 添加一个 URL 到黑名单
     */
    public void addBlockedUrl(String url) {
        if (TextUtils.isEmpty(url)) return;
        try {
            JSONArray urls = getUrlBlacklist();
            // 检查是否已存在
            for (int i = 0; i < urls.length(); i++) {
                if (url.equalsIgnoreCase(urls.getString(i))) {
                    Log.d(TAG, "URL已在黑名单中: " + url);
                    return;
                }
            }
            urls.put(url.trim());
            commandStore.saveUrlBlacklist(urls);
            Log.d(TAG, "URL已加入黑名单: " + url);
        } catch (Exception e) {
            Log.e(TAG, "添加URL黑名单失败", e);
        }
    }

    /**
     * 从黑名单移除一个 URL
     */
    public void removeBlockedUrl(String url) {
        try {
            JSONArray urls = getUrlBlacklist();
            JSONArray newUrls = new JSONArray();
            for (int i = 0; i < urls.length(); i++) {
                if (!url.equalsIgnoreCase(urls.getString(i))) {
                    newUrls.put(urls.getString(i));
                }
            }
            commandStore.saveUrlBlacklist(newUrls);
            Log.d(TAG, "URL已从黑名单移除: " + url);
        } catch (Exception e) {
            Log.e(TAG, "移除URL黑名单失败", e);
        }
    }

    /**
     * 获取所有黑名单 URL
     */
    /**
     * 获取浏览器拦截原因的文案
     */
    public String getUrlBlockReason(String packageName) {
        if (TextUtils.isEmpty(packageName)) return null;
        // 检查是否为浏览器应用
        String[] browserPackages = {
            "com.android.chrome", "com.android.browser", "com.miui.browser",
            "com.huawei.browser", "com.heytap.browser", "com.vivo.browser",
            "com.tencent.mtt", "com.UCMobile", "com.baidu.browser",
            "com.samsung.android.browser", "org.mozilla.firefox",
            "org.mozilla.firefox_beta", "com.microsoft.emmx",
            "com.duckduckgo.mobile.android", "com.opera.browser",
            "com.opera.mini.android"
        };
        for (String bp : browserPackages) {
            if (bp.equals(packageName)) {
                return "该浏览器访问的网页已被家长禁止";
            }
        }
        return "此网页已被家长禁止访问";
    }

    public List<String> getBlockedUrls() {
        List<String> list = new ArrayList<>();
        try {
            JSONArray urls = getUrlBlacklist();
            for (int i = 0; i < urls.length(); i++) {
                list.add(urls.getString(i));
            }
        } catch (Exception e) {
            Log.e(TAG, "获取URL黑名单失败", e);
        }
        return list;
    }

    // ==================== 内部方法 ====================

    private JSONArray getActivityRules() {
        try {
        return commandStore.getActivityRules();
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveActivityRules(JSONArray rules) {
        commandStore.saveActivityRules(rules);
    }

    private JSONArray getUrlBlacklist() {
        try {
        return commandStore.getActivityRules();
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    // ==================== 数据类 ====================

    public static class ActivityBlockRule {
        public final String packageName;
        public final String activityClassName;
        public final String appName;
        public final String reason;
        public final boolean enabled;

        public ActivityBlockRule(String packageName, String activityClassName,
                                 String appName, String reason, boolean enabled) {
            this.packageName = packageName;
            this.activityClassName = activityClassName;
            this.appName = appName;
            this.reason = reason;
            this.enabled = enabled;
        }

        public String getSimpleClassName() {
            String[] parts = activityClassName.split("\\.");
            return parts.length > 0 ? parts[parts.length - 1] : activityClassName;
        }
    }
}
