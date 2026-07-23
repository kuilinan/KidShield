package com.yousafdev.KidShield.Services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.graphics.Path;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.yousafdev.KidShield.Network.CommandStore;
import com.yousafdev.KidShield.Utils.LearningModeManager;
import com.yousafdev.KidShield.Utils.ActivityRecordManager;
import com.yousafdev.KidShield.Utils.ActivityBlockerManager;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppAccessibilityService";
    public static final String ACTION_FOREGROUND_APP = "com.yousafdev.KidShield.ACTION_FOREGROUND_APP";
    public static final String EXTRA_PACKAGE_NAME = "packageName";

    // 运行状态标记
    private static boolean sRunning = false;

    private CommandStore commandStore;
    private LearningModeManager learningModeManager;
    private ActivityRecordManager activityRecordManager;
    private ActivityBlockerManager activityBlockerManager;
    private String currentChildUid;
    private Set<String> whitelistApps = new HashSet<>();
    private Set<String> systemApps = new HashSet<>();
    private boolean whitelistMode = false;
    private boolean devModeBlocked = false;
    private String lastBlockedPackage = "";
    private long lastBlockedTime = 0;

    // 防抖：上次多任务拦截时间
    private long lastRecentAppsBlockTime = 0;
    // USB/ADB 检测
    private boolean adbWarningShown = false;

    @Override
    public void onCreate() {
        super.onCreate();
        sRunning = true;
        commandStore = new CommandStore(this);
        learningModeManager = new LearningModeManager(this);
        activityRecordManager = new ActivityRecordManager(this);
        activityBlockerManager = new ActivityBlockerManager(this);
        Log.d(TAG, "AppAccessibilityService 创建，使用本地 CommandStore + 学习模式/Activity拦截/URL黑名单");
        loadSystemApps();
        loadPoliciesFromStore();
        IntentFilter filter = new IntentFilter("com.yousafdev.KidShield.UPDATE_WHITELIST");
        registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    /** 供外部查询服务是否运行 */
    public static boolean isRunning() {
        return sRunning;
    }

    private void loadSystemApps() {
        PackageManager pm = getPackageManager();
        List<android.content.pm.PackageInfo> packages = pm.getInstalledPackages(0);
        for (android.content.pm.PackageInfo pkg : packages) {
            if ((pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                systemApps.add(pkg.packageName);
            }
        }
        Log.d(TAG, "加载了 " + systemApps.size() + " 个系统应用");
    }

    private void loadPoliciesFromStore() {
        whitelistMode = commandStore.getWhitelistMode();
        Log.d(TAG, "白名单模式: " + whitelistMode);
        List<String> storedWhitelist = commandStore.getWhitelistPackageNames();
        whitelistApps.clear();
        if (storedWhitelist != null) whitelistApps.addAll(storedWhitelist);
        Log.d(TAG, "白名单应用: " + whitelistApps.size() + " 个");
        devModeBlocked = commandStore.isDevModeBlocked();
        if (devModeBlocked) enforceDevModeBlock();
        Log.d(TAG, "开发者模式封锁: " + devModeBlocked);
    }

    private void enforceDevModeBlock() {
        try {
            Settings.Global.putInt(getContentResolver(), "development_settings_enabled", 0);
            Settings.Global.putInt(getContentResolver(), "adb_enabled", 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 0);
            }
            Log.d(TAG, "开发者模式已强制关闭");
        } catch (Exception e) {
            Log.e(TAG, "关闭开发者模式失败", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();

        // ===== 多任务键拦截（TYPE_WINDOWS_CHANGED 可捕捉多任务界面） =====
        if (eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED || 
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            // 检测是否进入了多任务/最近任务界面
            if (event.getPackageName() != null && event.getClassName() != null) {
                String pkg = event.getPackageName().toString();
                String cls = event.getClassName().toString();

                // 多任务界面特征
                if (isRecentAppsScreen(pkg, cls)) {
                    long now = System.currentTimeMillis();
                    // 500ms 防抖
                    if (now - lastRecentAppsBlockTime > 500) {
                        lastRecentAppsBlockTime = now;
                        Log.w(TAG, "⛔ 检测到多任务界面，强制返回桌面: " + cls);
                        // 先返回到桌面
                        performGlobalAction(GLOBAL_ACTION_HOME);
                    }
                    return; // 不再处理其他逻辑
                }
            }
        }

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (event.getPackageName() != null && event.getClassName() != null) {
                String packageName = event.getPackageName().toString();
                String className = event.getClassName().toString();
                Log.d(TAG, "前台应用: " + packageName + " 类: " + className);

                // 发送前台广播
                Intent intent = new Intent(ACTION_FOREGROUND_APP);
                intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
                sendBroadcast(intent);

                // ===== USB/ADB 连接检测（每次前台切换都检查） =====
                checkAdbConnection();

                // ===== 防卸载拦截 =====
                if (isUninstallOrSettingsPage(packageName, className)) {
                    Log.w(TAG, "检测到卸载/设置页面，自动返回: " + packageName + "/" + className);
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    try {
                        Intent overlayIntent = new Intent(this, BlockOverlayService.class);
                        overlayIntent.putExtra("blockedPackage", packageName);
                        overlayIntent.putExtra("reason", "家长已禁用应用卸载");
                        startService(overlayIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "启动悬浮窗失败", e);
                    }
                    return;
                }

                // 开发者模式封锁检查
                if (devModeBlocked) {
                    enforceDevModeBlock();
                    if (isDeveloperSettingsPackage(packageName)) {
                        blockApp(packageName, "开发者模式已被家长禁用");
                        return;
                    }
                }

                // ===== 学习模式采集 =====
                if (learningModeManager.isLearningMode()) {
                    activityRecordManager.recordActivity(packageName, className);
                }

                // ===== Activity 级细粒度拦截 =====
                if (activityBlockerManager.shouldBlockActivity(packageName, className)) {
                    String reason = activityBlockerManager.getBlockReason(packageName, className);
                    blockApp(packageName, reason);
                    activityRecordManager.markBlocked(packageName, className);
                    return;
                }

                // 白名单检查
                if (whitelistMode) {
                    if (!systemApps.contains(packageName) && !whitelistApps.contains(packageName)) {
                        blockApp(packageName, "此应用已被家长限制");
                    }
                }

                // ===== URL 黑名单检测 =====
                if (activityBlockerManager.isBrowserUrlBlocked(packageName, className)) {
                    String blockReason = activityBlockerManager.getUrlBlockReason(packageName);
                    if (blockReason != null) {
                        Log.w(TAG, "浏览器访问黑名单网页: " + packageName + " - " + blockReason);
                        blockApp(packageName, blockReason);
                    }
                }
            }
        }
    }

    /**
     * 检测是否是多任务/最近任务界面
     * 覆盖主流厂商 + VIVO OriginOS
     */
    private boolean isRecentAppsScreen(String packageName, String className) {
        // 原生 Android / AOSP
        if ("com.android.systemui".equals(packageName) && 
            (className.contains("Recent") || className.contains("Overview"))) {
            return true;
        }
        // VIVO OriginOS（不同版本路径不同）
        if ("com.android.systemui".equals(packageName) &&
            (className.contains("RecentTask") || 
             className.contains("RecentApps") || 
             className.contains("MultiTask") ||
             className.contains("RecentsActivity"))) {
            return true;
        }
        // 通用：类名包含 Recent/Overview/MultiTask
        if (className.contains("RecentApps") || 
            className.contains("RecentPanel") ||
            className.contains("Overview") ||
            className.contains("MultiTasking") ||
            className.contains("RecentTasks")) {
            return true;
        }
        return false;
    }

    /**
     * 检测 USB/ADB 连接
     */
    private void checkAdbConnection() {
        try {
            boolean adbEnabled = Settings.Global.getInt(getContentResolver(), "adb_enabled", 0) == 1;
            if (adbEnabled && devModeBlocked) {
                // 如果 ADB 开启了但 devModeBlock 没关掉它，说明可能被手动开启
                enforceDevModeBlock();
                if (!adbWarningShown) {
                    adbWarningShown = true;
                    Log.w(TAG, "⚠ 检测到 ADB 连接！已强制关闭");
                }
            } else {
                adbWarningShown = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "ADB检查异常", e);
        }
    }

    /**
     * 检测是否进入卸载/设置相关页面
     * 增强：覆盖 VIVO 更多卸载路径
     */
    private boolean isUninstallOrSettingsPage(String packageName, String className) {
        // ── 方案A: 包名精确匹配 ──
        if ("com.android.packageinstaller".equals(packageName) ||
            "com.google.android.packageinstaller".equals(packageName) ||
            "com.miui.packageinstaller".equals(packageName) ||
            "com.huawei.packageinstaller".equals(packageName) ||
            "com.samsung.android.packageinstaller".equals(packageName) ||
            "com.oplus.packageinstaller".equals(packageName) ||
            "com.vivo.packageinstaller".equals(packageName)) {
            if (className.contains("Uninstall") || className.contains("uninstall")) {
                return true;
            }
        }
        // 通用 Settings
        if ("com.android.settings".equals(packageName)) {
            if (className.contains("Uninstall") &&
                !className.contains("InstalledAppDetails") &&
                !className.contains("ApplicationDetail")) {
                return true;
            }
        }
        // VIVO 安全中心（多版本）
        if ("com.vivo.secime.service".equals(packageName) ||
            "com.vivo.secime".equals(packageName)) {
            if (className.contains("Uninstall") || 
                className.contains("AppUninstall") ||
                className.contains("ApplicationManage")) {
                return true;
            }
        }
        // VIVO 智能引擎（OriginOS 卸载路径）
        if ("com.vivo.assistant".equals(packageName) ||
            "com.vivo.engine".equals(packageName)) {
            if (className.contains("Uninstall")) {
                return true;
            }
        }
        // VIVO 桌面启动器长按卸载入口
        if ("com.android.launcher3".equals(packageName) ||
            "com.vivo.launcher".equals(packageName)) {
            if (className.contains("Uninstall")) {
                return true;
            }
        }
        // 小米安全中心
        if ("com.miui.securitycenter".equals(packageName)) {
            if (className.contains("Uninstall") || className.contains("appmanager")) return true;
        }
        // 华为系统管理器
        if ("com.huawei.systemmanager".equals(packageName)) {
            if (className.contains("Uninstall") || className.contains("AppDetal") || className.contains("AppDetail")) return true;
        }
        // OPPO/ColorOS
        if ("com.coloros.safecenter".equals(packageName) || "com.oppo.safe".equals(packageName)) {
            if (className.contains("Uninstall") || className.contains("AppDetail")) return true;
        }
        // ── 方案B: 类名关键词匹配 ──
        String[] uninstallPatterns = {
            "UninstallerActivity", "UninstallAlertDialog",
            "UninstallConfirmActivity", "UninstallFinishActivity",
            "uninstall", "Uninstall"
        };
        for (String pattern : uninstallPatterns) {
            if (className.contains(pattern)) return true;
        }
        return false;
    }

    private boolean isDeveloperSettingsPackage(String packageName) {
        return packageName.equals("com.android.settings") ||
               packageName.equals("com.android.systemui") ||
               packageName.equals("com.android.development") ||
               packageName.contains("developer");
    }

    private void blockApp(String packageName, String reason) {
        long now = System.currentTimeMillis();
        if (packageName.equals(lastBlockedPackage) && (now - lastBlockedTime) < 3000) return;
        lastBlockedPackage = packageName;
        lastBlockedTime = now;

        // 返回桌面
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);

        // 悬浮窗覆盖
        Intent overlayIntent = new Intent(this, BlockOverlayService.class);
        overlayIntent.putExtra("blockedPackage", packageName);
        overlayIntent.putExtra("reason", reason);
        startService(overlayIntent);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "无障碍服务中断");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sRunning = true;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                        | AccessibilityEvent.TYPE_WINDOWS_CHANGED; // 加这个才能捕捉多任务
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                   | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Log.d(TAG, "无障碍服务已连接（含多任务拦截+VIVO卸载路径+TYPE_WINDOWS_CHANGED）");
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        super.onDestroy();
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception ignored) {}
    }

    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.yousafdev.KidShield.UPDATE_WHITELIST".equals(intent.getAction())) {
                Log.d(TAG, "收到策略更新广播，重新读取本地策略");
                loadPoliciesFromStore();
            }
        }
    };
}
