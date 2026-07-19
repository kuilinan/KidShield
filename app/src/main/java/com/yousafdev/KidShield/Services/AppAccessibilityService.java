package com.yousafdev.KidShield.Services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.yousafdev.KidShield.Activities.BlockedScreenActivity;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class AppAccessibilityService extends AccessibilityService {
    private static final String TAG = "AppAccessibilityService";
    public static final String ACTION_FOREGROUND_APP = "com.yousafdev.KidShield.ACTION_FOREGROUND_APP";
    public static final String EXTRA_PACKAGE_NAME = "packageName";

    private CommandStore commandStore;
    private String currentChildUid;
    private Set<String> whitelistApps = new HashSet<>();
    private Set<String> systemApps = new HashSet<>();
    private boolean whitelistMode = true;
    private boolean devModeBlocked = false;
// ⚠️ REMOVED FIREBASE: private ValueEventListener whitelistListener;
    private String lastBlockedPackage = "";
    private long lastBlockedTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        commandStore = new CommandStore(this);
        // 改用本地文件存储的策略

        // 加载系统应用列表
        loadSystemApps();

        // 监听当前用户UID
        detectCurrentUser();

        // 注册广播接收器
        IntentFilter filter = new IntentFilter("com.yousafdev.KidShield.UPDATE_WHITELIST");
        registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void loadSystemApps() {
        PackageManager pm = getPackageManager();
        java.util.List<android.content.pm.PackageInfo> packages = pm.getInstalledPackages(0);
        for (android.content.pm.PackageInfo pkg : packages) {
            if ((pkg.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                systemApps.add(pkg.packageName);
            }
        }
        Log.d(TAG, "加载了 " + systemApps.size() + " 个系统应用");
    }

    private void detectCurrentUser() {
        // 从本地存储读取策略（家长通过指令同步存储到文件）
        // 设备绑定的孩子UID由登录时保存
// ⚠️ REMOVED FIREBASE: deviceRef.child("childUid").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
// ⚠️ REMOVED FIREBASE: public void onDataChange(DataSnapshot snapshot) {
                String uid = snapshot.getValue(String.class);
                if (uid != null) {
                    currentChildUid = uid;
                    startListeningForWhitelist();
                    startListeningForDevMode();
                }
            }
// ⚠️ REMOVED FIREBASE: @Override public void onCancelled(DatabaseError error) {}
        });
    }

    private void startListeningForWhitelist() {
        if (currentChildUid == null) return;

        // 监听白名单模式开关
        mDatabase.child("users").child(currentChildUid).child("settings").child("whitelistMode")
// ⚠️ REMOVED FIREBASE: .addValueEventListener(new ValueEventListener() {
                    @Override
// ⚠️ REMOVED FIREBASE: public void onDataChange(DataSnapshot snapshot) {
                        whitelistMode = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                        Log.d(TAG, "白名单模式: " + whitelistMode);
                    }
// ⚠️ REMOVED FIREBASE: @Override public void onCancelled(DatabaseError error) {}
                });

        // 监听白名单应用
        whitelistListener = mDatabase.child("users").child(currentChildUid).child("whitelist_apps")
// ⚠️ REMOVED FIREBASE: .addValueEventListener(new ValueEventListener() {
                    @Override
// ⚠️ REMOVED FIREBASE: public void onDataChange(DataSnapshot snapshot) {
                        whitelistApps.clear();
// ⚠️ REMOVED FIREBASE: for (DataSnapshot appSnapshot : snapshot.getChildren()) {
                            Boolean allowed = appSnapshot.getValue(Boolean.class);
                            if (allowed != null && allowed) {
                                String pkg = appSnapshot.getKey().replace("_", ".");
                                whitelistApps.add(pkg);
                            }
                        }
                        Log.d(TAG, "白名单更新: " + whitelistApps.size() + " 个应用");
                    }
// ⚠️ REMOVED FIREBASE: @Override public void onCancelled(DatabaseError error) {}
                });
    }

    private void startListeningForDevMode() {
        if (currentChildUid == null) return;
        mDatabase.child("users").child(currentChildUid).child("settings").child("blockDeveloperMode")
// ⚠️ REMOVED FIREBASE: .addValueEventListener(new ValueEventListener() {
                    @Override
// ⚠️ REMOVED FIREBASE: public void onDataChange(DataSnapshot snapshot) {
                        devModeBlocked = snapshot.getValue(Boolean.class) != null && snapshot.getValue(Boolean.class);
                        Log.d(TAG, "开发者模式封锁: " + devModeBlocked);
                        if (devModeBlocked) {
                            enforceDevModeBlock();
                        }
                    }
// ⚠️ REMOVED FIREBASE: @Override public void onCancelled(DatabaseError error) {}
                });
    }

    private void enforceDevModeBlock() {
        try {
            // 关闭开发者选项
            Settings.Global.putInt(getContentResolver(), "development_settings_enabled", 0);
            // 关闭USB调试
            Settings.Global.putInt(getContentResolver(), "adb_enabled", 0);
            // 关闭无线调试
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
                if (whitelistMode && currentChildUid != null) {
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
        // 防止频繁弹窗
        long now = System.currentTimeMillis();
        if (packageName.equals(lastBlockedPackage) && (now - lastBlockedTime) < 3000) {
            return;
        }
        lastBlockedPackage = packageName;
        lastBlockedTime = now;

        // 回到桌面并弹出锁定提示
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
                if (whitelistListener != null && currentChildUid != null) {
                    // 从本地CommandStore读取
                            .removeEventListener(whitelistListener);
                }
                detectCurrentUser();
            }
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(updateReceiver);
        } catch (Exception e) {}
    }
}
