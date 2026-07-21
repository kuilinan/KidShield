package com.yousafdev.KidShield.Network;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ⭐ 本地指令存储引擎 ⭐
 * 
 * 家长发送的指令不依赖实时网络！流程：
 * 1. 有网时：从 API 拉取最新指令 → 写入本地文件 → 生效
 * 2. 断网时：直接从本地文件读取 → 照常生效
 * 3. 重启时：开机启动 → 读取本地文件 → 继续生效
 * 
 * 存储位置：Context.getFilesDir() + "/policies/"
 *   ├── blocked_apps.json      ← 被锁定的应用列表
 *   ├── whitelist_apps.json    ← 白名单(仅允许的应用)
 *   ├── time_limits.json       ← 使用时间限制
 *   ├── missions.json          ← 任务列表
 *   ├── lockdown.json          ← 锁定模式(全局锁)
 *   ├── time_requests.json     ← 加时长申请(缓存)
 *   └── policy_version.json    ← 版本号(用于增量更新)
 */
public class CommandStore {
    private static final String TAG = "CommandStore";
    private static final String POLICIES_DIR = "policies";
    
    private final Context context;
    private final File policiesDir;
    
    public CommandStore(Context context) {
        this.context = context;
        this.policiesDir = new File(context.getFilesDir(), POLICIES_DIR);
        if (!policiesDir.exists()) {
            policiesDir.mkdirs();
            Log.d(TAG, "📁 创建策略目录: " + policiesDir.getAbsolutePath());
        }
    }
    
    // ===================== 存储文件路径 =====================
    private File getBlockedAppsFile() { return new File(policiesDir, "blocked_apps.json"); }
    private File getWhitelistFile() { return new File(policiesDir, "whitelist_apps.json"); }
    private File getTimeLimitsFile() { return new File(policiesDir, "time_limits.json"); }
    private File getMissionsFile() { return new File(policiesDir, "missions.json"); }
    private File getLockdownFile() { return new File(policiesDir, "lockdown.json"); }
    private File getTimeRequestsFile() { return new File(policiesDir, "time_requests.json"); }
    private File getPolicyVersionFile() { return new File(policiesDir, "policy_version.json"); }
    
    // ========== 1. 被锁定的应用（黑名单） ==========
    
    /**
     * 保存被锁定的应用列表
     * @param packageNames 包名列表，如 ["com.example.game", "com.example.tiktok"]
     */
    public void saveBlockedApps(List<String> packageNames) {
        try {
            JSONArray arr = new JSONArray();
            for (String pkg : packageNames) arr.put(pkg);
            writeFile(getBlockedAppsFile(), arr.toString(2));
            Log.d(TAG, "🔒 已保存 " + packageNames.size() + " 个被锁定应用");
        } catch (Exception e) {
            Log.e(TAG, "保存被封应用失败", e);
        }
    }
    
    /**
     * 读取被锁定的应用列表
     */
    public List<String> getBlockedApps() {
        List<String> list = new ArrayList<>();
        try {
            String content = readFile(getBlockedAppsFile());
            if (content != null) {
                JSONArray arr = new JSONArray(content);
                for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
            }
        } catch (Exception e) {
            Log.e(TAG, "读取被封应用失败", e);
        }
        return list;
    }
    
    /**
     * 检查某个应用是否被锁定
     */
    public boolean isAppBlocked(String packageName) {
        return getBlockedApps().contains(packageName);
    }
    
    // ========== 2. 白名单（仅允许的应用） ==========
    
    /**
     * 保存白名单应用（白名单模式下，只有这些应用能用）
     */
    public void saveWhitelistApps(List<Map<String, String>> apps) {
        try {
            JSONArray arr = new JSONArray();
            for (Map<String, String> app : apps) {
                JSONObject obj = new JSONObject();
                obj.put("package_name", app.get("package_name"));
                obj.put("app_name", app.get("app_name"));
                arr.put(obj);
            }
            writeFile(getWhitelistFile(), arr.toString(2));
            Log.d(TAG, "✅ 已保存 " + apps.size() + " 个白名单应用");
        } catch (Exception e) {
            Log.e(TAG, "保存白名单失败", e);
        }
    }
    
    public List<Map<String, String>> getWhitelistApps() {
        List<Map<String, String>> list = new ArrayList<>();
        try {
            String content = readFile(getWhitelistFile());
            if (content != null) {
                JSONArray arr = new JSONArray(content);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    Map<String, String> map = new HashMap<>();
                    map.put("package_name", obj.optString("package_name"));
                    map.put("app_name", obj.optString("app_name"));
                    list.add(map);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "读取白名单失败", e);
        }
        return list;
    }
    
    /**
     * 保存白名单模式开关
     */
    public void saveWhitelistMode(boolean enabled) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("enabled", enabled);
            writeFile(new File(policiesDir, "whitelist_mode.json"), obj.toString(2));
            Log.d(TAG, "🔲 白名单模式: " + (enabled ? "已开启" : "已关闭"));
        } catch (Exception e) {
            Log.e(TAG, "保存白名单模式失败", e);
        }
    }
    
    public boolean getWhitelistMode() {
        try {
            String content = readFile(new File(policiesDir, "whitelist_mode.json"));
            if (content != null) {
                return new JSONObject(content).optBoolean("enabled", false);
            }
        } catch (Exception e) {}
        return false;
    }
    
    /**
     * 获取白名单包名列表（简化版）
     */
    public List<String> getWhitelistPackageNames() {
        List<String> list = new ArrayList<>();
        List<Map<String, String>> apps = getWhitelistApps();
        for (Map<String, String> app : apps) {
            list.add(app.get("package_name"));
        }
        return list;
    }
    
    /**
     * 保存开发者模式封锁状态
     */
    public void saveDevModeBlocked(boolean blocked) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("blocked", blocked);
            writeFile(new File(policiesDir, "dev_mode_block.json"), obj.toString(2));
            Log.d(TAG, "🚫 开发者模式封锁: " + (blocked ? "已开启" : "已关闭"));
        } catch (Exception e) {
            Log.e(TAG, "保存开发者封锁状态失败", e);
        }
    }
    
    public boolean isDevModeBlocked() {
        try {
            String content = readFile(new File(policiesDir, "dev_mode_block.json"));
            if (content != null) {
                return new JSONObject(content).optBoolean("blocked", false);
            }
        } catch (Exception e) {}
        return false;
    }
    
    // ========== 3. 锁定模式（全局锁机） ==========
    
    /**
     * 保存锁定模式状态
     * @param locked true = 锁定手机，孩子无法使用任何应用
     * @param reason 锁定原因（如"作业没做完"）
     */
    public void saveLockdown(boolean locked, String reason) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("locked", locked);
            obj.put("reason", reason != null ? reason : "");
            obj.put("updated_at", System.currentTimeMillis());
            writeFile(getLockdownFile(), obj.toString(2));
            Log.d(TAG, "🔐 锁定模式: " + (locked ? "已锁定" : "已解锁") + " 原因: " + reason);
        } catch (Exception e) {
            Log.e(TAG, "保存锁定状态失败", e);
        }
    }
    
    public boolean isLockdown() {
        try {
            String content = readFile(getLockdownFile());
            if (content != null) {
                JSONObject obj = new JSONObject(content);
                return obj.optBoolean("locked", false);
            }
        } catch (Exception e) {
            Log.e(TAG, "读取锁定状态失败", e);
        }
        return false;
    }
    
    public String getLockdownReason() {
        try {
            String content = readFile(getLockdownFile());
            if (content != null) {
                JSONObject obj = new JSONObject(content);
                return obj.optString("reason", "");
            }
        } catch (Exception e) {
            Log.e(TAG, "读取锁定原因失败", e);
        }
        return "";
    }
    
    // ========== 4. 使用时间限制 ==========
    
    /**
     * 保存每日使用时间限制
     * @param dailyMinutes 每天最多能用多少分钟
     * @param appLimits 每个应用的时间限制 {"com.example.game": 30}
     */
    public void saveTimeLimits(int dailyMinutes, Map<String, Integer> appLimits) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("daily_minutes", dailyMinutes);
            JSONObject apps = new JSONObject();
            if (appLimits != null) {
                for (Map.Entry<String, Integer> entry : appLimits.entrySet()) {
                    apps.put(entry.getKey(), entry.getValue());
                }
            }
            obj.put("app_limits", apps);
            writeFile(getTimeLimitsFile(), obj.toString(2));
            Log.d(TAG, "⏱ 已保存时间限制: 每日" + dailyMinutes + "分钟");
        } catch (Exception e) {
            Log.e(TAG, "保存时间限制失败", e);
        }
    }
    
    public int getDailyTimeLimit() {
        try {
            String content = readFile(getTimeLimitsFile());
            if (content != null) {
                JSONObject obj = new JSONObject(content);
                return obj.optInt("daily_minutes", 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "读取时间限制失败", e);
        }
        return 0;
    }
    
    // ========== 5. 任务列表 ==========
    
    /**
     * 保存任务列表
     */
    public void saveMissions(JSONArray missions) {
        try {
            writeFile(getMissionsFile(), missions.toString(2));
            Log.d(TAG, "📋 已保存 " + missions.length() + " 个任务");
        } catch (Exception e) {
            Log.e(TAG, "保存任务失败", e);
        }
    }
    
    public JSONArray getMissions() {
        try {
            String content = readFile(getMissionsFile());
            if (content != null) return new JSONArray(content);
        } catch (Exception e) {
            Log.e(TAG, "读取任务失败", e);
        }
        return new JSONArray();
    }
    
    // ========== 6. 通用存储接口 ==========
    
    /**
     * 保存任何策略数据到指定文件名
     * @param fileName 文件名（如 "homework_schedule.json"）
     * @param data JSON 字符串
     */
    public void savePolicyData(String fileName, String data) {
        try {
            File file = new File(policiesDir, fileName);
            writeFile(file, data);
            Log.d(TAG, "💾 已保存策略文件: " + fileName);
        } catch (Exception e) {
            Log.e(TAG, "保存策略文件失败: " + fileName, e);
        }
    }
    
    /**
     * 读取任何策略数据
     */
    public String readPolicyData(String fileName) {
        try {
            File file = new File(policiesDir, fileName);
            return readFile(file);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取策略版本号，用于增量同步
     */
    public long getPolicyVersion() {
        try {
            String content = readFile(getPolicyVersionFile());
            if (content != null) {
                return Long.parseLong(content.trim());
            }
        } catch (Exception e) {}
        return 0;
    }
    
    /**
     * 更新策略版本号
     */
    public void updatePolicyVersion(long version) {
        try {
            writeFile(getPolicyVersionFile(), String.valueOf(version));
        } catch (Exception e) {}
    }
    
    /**
     * 获取所有策略文件列表（用于调试）
     */
    public String[] listPolicyFiles() {
        File[] files = policiesDir.listFiles();
        if (files == null) return new String[0];
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName() + " (" + files[i].length() + "B)";
        }
        return names;
    }
    
    /**
     * 清除所有本地策略（解绑时调用）
     */
    public void clearAll() {
        File[] files = policiesDir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        Log.d(TAG, "🗑 已清除所有本地策略");
    }
    
    // ===================== 文件 I/O =====================
    
    private void writeFile(File file, String content) throws Exception {
        FileWriter writer = new FileWriter(file);
        writer.write(content);
        writer.flush();
        writer.close();
    }
    
    private String readFile(File file) throws Exception {
        if (!file.exists()) return null;
        FileReader reader = new FileReader(file);
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int len;
        while ((len = reader.read(buf)) != -1) {
            sb.append(buf, 0, len);
        }
        reader.close();
        return sb.toString();
    }


    // ===================== 禁令持久化（本地存储，断网有效，重启不丢） =====================

    /**
     * 保存单条禁令规则
     * @param packageName 应用包名
     * @param durationMinutes 禁止时长（分钟），0=永久
     * @param reason 原因
     */
    public void saveBlockRule(String packageName, int durationMinutes, String reason) {
        try {
            editor = prefs.edit();
            JSONObject rule = new JSONObject();
            rule.put("packageName", packageName);
            rule.put("durationMinutes", durationMinutes);
            rule.put("reason", reason != null ? reason : "");
            rule.put("timestamp", System.currentTimeMillis());
            rule.put("expireAt", durationMinutes > 0 ? System.currentTimeMillis() + (durationMinutes * 60 * 1000L) : 0);

            // 读取已有禁令列表
            JSONArray rules = getBlockRules();
            // 如果已存在同名包名，更新
            boolean updated = false;
            for (int i = 0; i < rules.length(); i++) {
                JSONObject existing = rules.getJSONObject(i);
                if (existing.getString("packageName").equals(packageName)) {
                    rules.put(i, rule);
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                rules.put(rule);
            }
            editor.putString("block_rules", rules.toString());
            editor.apply();

            // 发送广播通知无障碍服务更新
            Intent intent = new Intent("com.yousafdev.KidShield.UPDATE_WHITELIST");
            context.sendBroadcast(intent);

            Log.d(TAG, "禁令已保存: " + packageName + " 时长=" + durationMinutes + "min");
        } catch (Exception e) {
            Log.e(TAG, "保存禁令失败", e);
        }
    }

    /**
     * 获取所有禁令规则
     */
    public JSONArray getBlockRules() {
        try {
            String json = prefs.getString("block_rules", "[]");
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /**
     * 检查某个应用当前是否被禁止
     * @param packageName 应用包名
     * @return true=被禁止，false=可正常使用
     */
    public boolean isAppBlocked(String packageName) {
        try {
            JSONArray rules = getBlockRules();
            long now = System.currentTimeMillis();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                if (rule.getString("packageName").equals(packageName)) {
                    long expireAt = rule.optLong("expireAt", 0);
                    // expireAt=0 表示永久禁止
                    if (expireAt == 0 || expireAt > now) {
                        return true;
                    } else {
                        // 已过期，自动移除
                        rules.remove(i);
                        editor = prefs.edit();
                        editor.putString("block_rules", rules.toString());
                        editor.apply();
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "检查禁令状态失败", e);
        }
        return false;
    }

    /**
     * 移除禁令（解禁）
     * @param packageName 应用包名，传"*"表示清空所有
     */
    public void removeBlockRule(String packageName) {
        try {
            JSONArray rules = getBlockRules();
            JSONArray newRules = new JSONArray();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                if ("*".equals(packageName) || !rule.getString("packageName").equals(packageName)) {
                    newRules.put(rule);
                }
            }
            editor = prefs.edit();
            editor.putString("block_rules", newRules.toString());
            editor.apply();

            Intent intent = new Intent("com.yousafdev.KidShield.UPDATE_WHITELIST");
            context.sendBroadcast(intent);

            Log.d(TAG, "禁令已移除: " + packageName);
        } catch (Exception e) {
            Log.e(TAG, "移除禁令失败", e);
        }
    }

    /**
     * 获取所有被禁止应用的包名列表（不含已过期的）
     */
    public List<String> getBlockedPackageNames() {
        List<String> blocked = new ArrayList<>();
        try {
            JSONArray rules = getBlockRules();
            long now = System.currentTimeMillis();
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                long expireAt = rule.optLong("expireAt", 0);
                if (expireAt == 0 || expireAt > now) {
                    blocked.add(rule.getString("packageName"));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取被禁包名列表失败", e);
        }
        return blocked;
    }

}
