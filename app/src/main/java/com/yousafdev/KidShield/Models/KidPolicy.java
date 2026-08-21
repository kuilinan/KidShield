package com.yousafdev.KidShield.Models;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * KidPolicy - 管控策略模型（指令持久化的核心数据结构）
 *
 * 设计原则：所有指令长存储在孩子端本地，只要指令存在就持续生效。
 * - 重启手机：从本地重新加载，指令继续执行
 * - 断网离线：不依赖后端，本地直接执行
 * - 只有家长明确下发"解除指令"才会删除
 *
 * 参考：xiaopacai PolicyConfig (Apache 2.0) + KidShield CommandStore
 */
public class KidPolicy {

    // ========== 白名单/黑名单模式 ==========
    public boolean whitelistMode = false;          // true=仅白名单可用
    public Set<String> whitelist = new HashSet<>(); // 白名单包名
    public Set<String> blacklist = new HashSet<>(); // 黑名单包名

    // ========== 时间限额 ==========
    public int dailyLimitMinutes = 120;            // 每日总时长限额（分钟），0=不限
    public Map<String, Integer> categoryLimits = new HashMap<>(); // 分类限额，如 game:30, social:60

    // ========== 就寝时段 ==========
    public String sleepStart = "21:00";            // 就寝开始（24h）
    public String sleepEnd = "07:00";              // 就寝结束（24h）
    public boolean sleepEnabled = true;

    // ========== 锁屏指令（关键：长存储，到期自动解除） ==========
    public long lockUntil = 0;                     // 锁屏截止时间（elapsedRealtime 毫秒，防改系统时间！）
    public String lockReason = "";                 // 锁屏原因（展示给孩子）

    // ========== 版本 ==========
    public long updatedAt = System.currentTimeMillis();

    /** 当前是否处于锁屏状态（用 elapsedRealtime 防改时间作弊） */
    public boolean isLocked(long nowElapsedRealtime) {
        return lockUntil > 0 && nowElapsedRealtime < lockUntil;
    }

    /** 剩余锁屏毫秒数 */
    public long getRemainingLockMs(long nowElapsedRealtime) {
        if (lockUntil <= 0) return 0;
        long remain = lockUntil - nowElapsedRealtime;
        return Math.max(remain, 0);
    }

    /** 当前是否处于就寝时段 */
    public boolean isSleepTime(long nowMillis) {
        if (!sleepEnabled || sleepStart == null || sleepEnd == null) return false;
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(nowMillis);
        int nowMin = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
        int startMin = parseHHMM(sleepStart);
        int endMin = parseHHMM(sleepEnd);
        if (startMin <= endMin) {
            return nowMin >= startMin && nowMin < endMin;
        } else {
            // 跨天：如 22:00 - 07:00
            return nowMin >= startMin || nowMin < endMin;
        }
    }

    private int parseHHMM(String hhmm) {
        try {
            String[] parts = hhmm.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    // ========== 序列化（JSON 落盘） ==========
    public JSONObject toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("whitelistMode", whitelistMode);
            obj.put("whitelist", new JSONArray(whitelist));
            obj.put("blacklist", new JSONArray(blacklist));
            obj.put("dailyLimitMinutes", dailyLimitMinutes);
            obj.put("categoryLimits", new JSONObject(categoryLimits));
            obj.put("sleepStart", sleepStart);
            obj.put("sleepEnd", sleepEnd);
            obj.put("sleepEnabled", sleepEnabled);
            obj.put("lockUntil", lockUntil);
            obj.put("lockReason", lockReason);
            obj.put("updatedAt", updatedAt);
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static KidPolicy fromJson(String json) {
        KidPolicy p = new KidPolicy();
        if (json == null || json.isEmpty()) return p;
        try {
            JSONObject obj = new JSONObject(json);
            p.whitelistMode = obj.optBoolean("whitelistMode", false);
            p.whitelist = jsonArrayToSet(obj.optJSONArray("whitelist"));
            p.blacklist = jsonArrayToSet(obj.optJSONArray("blacklist"));
            p.dailyLimitMinutes = obj.optInt("dailyLimitMinutes", 120);
            JSONObject cat = obj.optJSONObject("categoryLimits");
            if (cat != null) {
                java.util.Iterator<String> keys = cat.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    p.categoryLimits.put(k, cat.optInt(k, 0));
                }
            }
            p.sleepStart = obj.optString("sleepStart", "21:00");
            p.sleepEnd = obj.optString("sleepEnd", "07:00");
            p.sleepEnabled = obj.optBoolean("sleepEnabled", true);
            p.lockUntil = obj.optLong("lockUntil", 0);
            p.lockReason = obj.optString("lockReason", "");
            p.updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
        } catch (Exception ignored) {
        }
        return p;
    }

    private static Set<String> jsonArrayToSet(JSONArray arr) {
        Set<String> set = new HashSet<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                set.add(arr.optString(i));
            }
        }
        return set;
    }
}
