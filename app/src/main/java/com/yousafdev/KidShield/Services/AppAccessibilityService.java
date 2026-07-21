package com.yousafdev.KidShield.Services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
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

    @Override
    public void onCreate() {
        super.onCreate();
        commandStore = new CommandStore(this);
        learningModeManager = new LearningModeManager(this);
        activityRecordManager = new ActivityRecordManager(this);
        activityBlockerManager = new ActivityBlockerManager(this);
        Log.d(TAG, "AppAccessibilityService 创建，使用本地 CommandStore + 学习模式/Activity拦截/URL黑名单");

        // 加载系统应用列表
        loadSystemApps();

        // 读取本地存储的策略
        loadPoliciesFromStore();

        // 注册广播接收器（用于策略更新通知）
        IntentFilter filter = new IntentFilter("com.yousafdev.KidShield.UPDATE_WHITELIST");
        registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
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
        // 从本地 CommandStore 读取当前策略
        // 白名单模式
        whitelistMode = commandStore.getWhitelistMode();
        Log.d(TAG, "白名单模式: " + whitelistMode);

        // 白名单应用列表
        List<String> storedWhitelist = commandStore.getWhitelistPackageNames();
        whitelistApps.clear();
        if (storedWhitelist != null) {
            whitelistApps.addAll(storedWhitelist);
        }
        Log.d(TAG, "白名单应用: " + whitelistApps.size() + " 个");

        // 开发者模式封锁
        devModeBlocked = commandStore.isDevModeBlocked();
        if (devModeBlocked) {
            enforceDevModeBlock();
        }
        Log.d(TAG, "开发者模式封锁: " + devModeBlocked);
    }

    private void enforceDevModeBlock() {
        try {
            Settings.Global.putInt(getContentResolver(), "development_settings_enabled", 0);
            Settings.Global.putInt(getContentResolver(), "adb_enabled", 0);
            Settings.Global.putInt(getContentResolver(), "adb_wifi_enabled", 0);
            Log.d(TAG, "开发者模式已强制关闭");
        } catch (Exception e) {
            Log.e(TAG, "关闭开发者模式失败", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null && event.getClassName() != null) {
                String packageName = event.getPackageName().toString();
                String className = event.getClassName().toString();
                Log.d(TAG, "前台应用: " + packageName + " 类: " + className);

                // 发送广播
                Intent intent = new Intent(ACTION_FOREGROUND_APP);
                intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
                sendBroadcast(intent);

                // ===== 防卸载拦截：检测卸载/设置页面 =====
                if (isUninstallOrSettingsPage(packageName, className)) {
                    // 检测到卸载相关界面，自动按返回键
                    Log.w(TAG, "检测到卸载/设置页面，自动返回: " + packageName + "/" + className);
                    performGlobalAction(GLOBAL_ACTION_BACK);

                    // 同时启动悬浮窗覆盖
                    try {
                        Intent overlayIntent = new Intent(this, BlockOverlayService.class);
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

                // ===== URL 黑名单检测（浏览器浏览拦截入口） =====
                if (activityBlockerManager.isBrowserUrlBlocked(packageName, className)) {
                    // 检测到浏览器访问黑名单中的网页，使用回桌面 + 悬浮窗提示
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
     * 检测是否进入卸载/设置相关页面
     * 全面支持各大手机厂商（原生/小米/华为/OPPO/vivo/三星等）
     * 只拦截真正的卸载确认弹窗，不拦截"应用信息"和"管理应用"列表
     */
    private boolean isUninstallOrSettingsPage(String packageName, String className) {
        // ── 方案A: 包名精确匹配 ──
        // 通用 PackageInstaller
        if ("com.android.packageinstaller".equals(packageName) ||
            "com.google.android.packageinstaller".equals(packageName) ||
            "com.miui.packageinstaller".equals(packageName) ||
            "com.huawei.packageinstaller".equals(packageName) ||
            "com.samsung.android.packageinstaller".equals(packageName) ||
            "com.oplus.packageinstaller".equals(packageName) ||
            "com.vivo.packageinstaller".equals(packageName)) {
            // 包安装器中任何包含 Uninstall 的页面都要拦截
            if (className.contains("Uninstall")) {
                return true;
            }
        }
        // 通用 Settings
        if ("com.android.settings".equals(packageName)) {
            // 只拦截包含 Uninstall 的页面，放行 InstalledAppDetails/ApplicationDetail（应用信息页）
            if (className.contains("Uninstall") &&
                !className.contains("InstalledAppDetails") &&
                !className.contains("ApplicationDetail")) {
                return true;
            }
        }
        // 小米安全中心
        if ("com.miui.securitycenter".equals(packageName)) {
            if (className.contains("Uninstall") ||
                className.contains("appmanager")) {
                return true;
            }
        }
        // 华为系统管理器
        if ("com.huawei.systemmanager".equals(packageName)) {
            if (className.contains("Uninstall") ||
                className.contains("AppDetal") ||
                className.contains("AppDetail")) {
                return true;
            }
        }
        // OPPO/ColorOS 安全中心
        if ("com.coloros.safecenter".equals(packageName) ||
            "com.oppo.safe".equals(packageName)) {
            if (className.contains("Uninstall") ||
                className.contains("AppDetail")) {
                return true;
            }
        }
        // vivo 安全管理
        if ("com.vivo.secime.service".equals(packageName)) {
            if (className.contains("Uninstall")) {
                return true;
            }
        }
        // ── 方案B: 类名关键词匹配（兜底，不依赖包名） ──
        String[] uninstallPatterns = {
            "UninstallerActivity",
            "UninstallAlertDialog",
            "UninstallConfirmActivity",
            "UninstallFinishActivity"
        };
        for (String pattern : uninstallPatterns) {
            if (className.contains(pattern)) {
                return true;
            }
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
        if (packageName.equals(lastBlockedPackage) && (now - lastBlockedTime) < 3000) {
            return;
        }
        lastBlockedPackage = packageName;
        lastBlockedTime = now;

        // 回到桌面
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);

        // 使用悬浮窗覆盖（可防止Home键绕过）
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
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                        | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                   | AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Log.d(TAG, "无障碍服务已连接（白名单+开发者封锁模式 + 内容变化监听）");
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {
            // ignore
        }
    }
}
