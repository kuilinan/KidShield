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

import com.yousafdev.KidShield.Activities.BlockedScreenActivity;
import com.yousafdev.KidShield.Network.CommandStore;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppAccessibilityService";
    public static final String ACTION_FOREGROUND_APP = "com.yousafdev.KidShield.ACTION_FOREGROUND_APP";
    public static final String EXTRA_PACKAGE_NAME = "packageName";

    private CommandStore commandStore;
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
        Log.d(TAG, "AppAccessibilityService 创建，使用本地 CommandStore");

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
                Log.d(TAG, "前台应用: " + packageName);

                // 发送广播
                Intent intent = new Intent(ACTION_FOREGROUND_APP);
                intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
                sendBroadcast(intent);

                // 开发者模式封锁检查
                if (devModeBlocked) {
                    enforceDevModeBlock();
                    if (isDeveloperSettingsPackage(packageName)) {
                        blockApp(packageName, "开发者模式已被家长禁用");
                        return;
                    }
                }

                // 白名单检查
                if (whitelistMode) {
                    if (!systemApps.contains(packageName) && !whitelistApps.contains(packageName)) {
                        blockApp(packageName, "此应用已被家长限制");
                    }
                }
            }
        }
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

        // 显示封锁提示
        Intent blockIntent = new Intent(this, BlockedScreenActivity.class);
        blockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        blockIntent.putExtra("blocked_package", packageName);
        blockIntent.putExtra("reason", reason);
        startActivity(blockIntent);
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "无障碍服务中断");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Log.d(TAG, "无障碍服务已连接（白名单+开发者封锁模式）");
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
