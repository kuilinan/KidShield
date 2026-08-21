package com.yousafdev.KidShield.Services;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.Context;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * KidInterceptor - 应用拦截判定引擎
 *
 * 拦截优先级：黑名单 > 锁屏/超时 > 就寝时段 > 分类限额 > 白名单豁免
 * 纯判定逻辑（decide），可单测。
 *
 * 参考：xiaopacai AppInterceptor.kt (Apache 2.0)
 */
public class KidInterceptor {

    /** 系统关键应用（电话/短信/设置等，永远不拦截，保证紧急功能可用） */
    public static final Set<String> SYSTEM_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.phone", "com.android.contacts", "com.android.mms",
            "com.android.dialer", "com.android.incallui", "com.android.server.telecom",
            "com.android.settings", "com.android.systemui", "android",
            "com.google.android.gms", "com.google.android.gsf",
            "com.android.providers.contacts", "com.android.providers.media.module"
    ));

    /** 桌面启动器（免拦截，避免"返回桌面后被拦截"的死循环） */
    public static final Set<String> LAUNCHER_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.apps.nexuslauncher", "com.android.launcher",
            "com.android.launcher2", "com.android.launcher3", "com.android.launcher4",
            "com.miui.home", "com.sec.android.app.launcher", "com.oppo.launcher",
            "com.huawei.android.launcher", "com.vivo.launcher", "com.bbk.launcher2",
            "com.coloros.launcher", "com.oneplus.launcher", "com.android.settings"
    ));

    /** 应用分类（用于分类限额） */
    public static final String CATEGORY_GAME = "game";
    public static final String CATEGORY_SOCIAL = "social";
    public static final String CATEGORY_VIDEO = "video";
    public static final String CATEGORY_LEARNING = "learning";

    /** 拦截判定结果 */
    public static class Decision {
        public final boolean intercept;
        public final String reason; // system/launcher/blacklist/whitelist/lock/sleep/category_limit/default
        public final String displayReason; // 展示给孩子的原因

        public Decision(boolean intercept, String reason, String displayReason) {
            this.intercept = intercept;
            this.reason = reason;
            this.displayReason = displayReason;
        }
    }

    public static Decision allow(String reason) {
        return new Decision(false, reason, "");
    }

    public static Decision block(String reason, String display) {
        return new Decision(true, reason, display);
    }

    /**
     * 核心判定（纯逻辑，可单测）
     *
     * @param packageName      当前前台应用包名
     * @param isBlacklisted    是否在黑名单
     * @param isWhitelisted    是否在白名单
     * @param whitelistMode    是否白名单模式（true=非白名单一律拦截）
     * @param isLocked         是否锁屏/超时停用
     * @param isSleepTime      是否就寝时段
     * @param category         应用分类（game/social/video/learning/other）
     * @param categoryExceeded 该分类限额是否已用完
     * @param blockUnknown     白名单模式下未知应用是否拦截
     */
    public static Decision decide(
            String packageName,
            boolean isBlacklisted,
            boolean isWhitelisted,
            boolean whitelistMode,
            boolean isLocked,
            boolean isSleepTime,
            String category,
            boolean categoryExceeded,
            boolean blockUnknown) {

        if (packageName == null) return allow("null");

        // 1. 系统关键应用永远放行（电话/短信/SOS）
        if (SYSTEM_PACKAGES.contains(packageName)) {
            return allow("system");
        }
        // 2. 桌面启动器放行（防死循环）
        if (LAUNCHER_PACKAGES.contains(packageName)) {
            return allow("launcher");
        }
        // 3. 黑名单优先拦截
        if (isBlacklisted) {
            return block("blacklist", "这个应用被家长禁止了哦");
        }
        // 4. 锁屏/超时停用：全部拦截（除白名单）
        if (isLocked) {
            if (isWhitelisted && !whitelistMode) {
                return allow("whitelist");
            }
            return block("lock", "休息时间到啦，起来活动一下吧 🌈");
        }
        // 5. 就寝时段
        if (isSleepTime) {
            if (isWhitelisted && !whitelistMode) {
                return allow("whitelist");
            }
            return block("sleep", "睡觉时间到啦，晚安 🌙");
        }
        // 6. 白名单模式：非白名单一律拦
        if (whitelistMode && !isWhitelisted) {
            return block("whitelist-mode", "这个应用要问过家长才能用哦 🌟");
        }
        // 7. 分类限额用完
        if (categoryExceeded && !"learning".equals(category)) {
            return block("category-limit", "今天这个类型的应用玩够啦，明天再来吧 ⏰");
        }
        // 8. 学习类应用特殊放行
        if (CATEGORY_LEARNING.equals(category)) {
            return allow("study");
        }
        // 9. 未知应用：白名单模式下由调用方决定
        if (blockUnknown) {
            return block("default", "这个要问过家长才能用哦 🌟");
        }
        return allow("default");
    }

    /** 获取应用分类（通过包名启发式判断，参考小趴菜 CategoryTaxonomy） */
    public static String guessCategory(String packageName, String appName, Context context) {
        if (packageName == null) return "other";
        String pkg = packageName.toLowerCase();
        String name = appName == null ? "" : appName.toLowerCase();

        // 学习类
        if (pkg.contains("school") || pkg.contains("study") || pkg.contains("edu")
                || pkg.contains("class") || pkg.contains("homework") || pkg.contains("dict")
                || name.contains("学习") || name.contains("作业") || name.contains("课堂")
                || name.contains("词典") || name.contains("阅读") || name.contains("课程")
                || name.contains("英语") || name.contains("数学")) {
            return CATEGORY_LEARNING;
        }
        // 游戏类
        if (pkg.contains("game") || pkg.contains("unity") || name.contains("游戏")
                || name.contains("王者") || name.contains("吃鸡") || name.contains("我的世界")
                || name.contains("原神") || name.contains("蛋仔")) {
            return CATEGORY_GAME;
        }
        // 社交类
        if (pkg.contains("weixin") || pkg.contains("wechat") || pkg.contains("qq")
                || pkg.contains("tim") || pkg.contains("whatsapp") || pkg.contains("telegram")
                || pkg.contains("instagram") || pkg.contains("tiktok") || pkg.contains("douyin")
                || name.contains("微信") || name.contains("抖音") || name.contains("快手")
                || name.contains("微博") || name.contains("QQ") || name.contains("钉钉")) {
            return CATEGORY_SOCIAL;
        }
        // 视频类
        if (pkg.contains("video") || pkg.contains("bilibili") || pkg.contains("youtube")
                || pkg.contains("iqiyi") || pkg.contains("tencentvideo") || pkg.contains("youku")
                || name.contains("哔哩哔哩") || name.contains("爱奇艺") || name.contains("优酷")
                || name.contains("腾讯视频") || name.contains("视频")) {
            return CATEGORY_VIDEO;
        }
        return "other";
    }
}