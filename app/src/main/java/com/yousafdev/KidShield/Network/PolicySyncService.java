package com.yousafdev.KidShield.Network;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ⭐⭐⭐ 核心服务：指令同步引擎 ⭐⭐⭐
 * 
 * 实现你的想法：家长发指令 → 同步到本地文件 → 断网/重启都不怕！
 * 
 * 工作流程：
 * ┌──────────────┐    ┌────────────────┐    ┌───────────────┐
 * │  家长端发指令 │ →  │ Vercel API     │ →  │ 孩子端拉取    │
 * │  (锁应用/任务)│    │ (kid-shield)   │    │ (定时轮询)    │
 * └──────────────┘    └────────────────┘    └──────┬────────┘
 *                                                   ↓
 * ┌──────────────┐    ┌───────────────┐    ┌───────────────┐
 * │ 无障碍服务   │ ←  │ CommandStore  │ ←  │ 写入本地文件  │
 * │ 读取黑名单   │    │ (本地JSON文件) │    │ policies/     │
 * └──────────────┘    └───────────────┘    └───────────────┘
 */
public class PolicySyncService extends Service {
    private static final String TAG = "PolicySync";
    private static final long SYNC_INTERVAL_MS = 30 * 1000; // 30秒同步一次
    
    private CommandStore commandStore;
    private ScheduledExecutorService scheduler;
    private ConnectivityManager connectivityManager;
    private NetworkCallback networkCallback;
    
    @Override
    public void onCreate() {
        super.onCreate();
        commandStore = new CommandStore(this);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        // 创建通知渠道（Android 8+ 前台服务必须）
        String channelId = "policy_sync_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "指令同步",
                NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("后台同步家长端的管控指令");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
            
            Notification notification = new Notification.Builder(this, channelId)
                .setContentTitle("KidShield 儿童守护")
                .setContentText("正在同步管控指令...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .build();
            startForeground(1, notification);
        }
        
        Log.d(TAG, "🚀 策略同步服务已启动");
        Log.d(TAG, "📁 策略存储目录: " + getFilesDir() + "/policies/");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 注册网络状态监听（网络恢复时立即同步）
        networkCallback = new NetworkCallback();
        NetworkRequest request = new NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
        
        // 立即同步一次
        syncPolicies();
        
        // 定时同步（每30秒）
        scheduler.scheduleAtFixedRate(
            this::syncPolicies,
            SYNC_INTERVAL_MS,
            SYNC_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        // 设为粘性服务，被杀后自动重启
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduler != null) scheduler.shutdown();
        if (networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        Log.d(TAG, "🛑 策略同步服务已停止");
    }
    
    @Override
    public IBinder onBind(Intent intent) { return null; }
    
    // ===================== 核心：同步策略到本地文件 =====================
    
    private void syncPolicies() {
        ApiClient api = ApiClient.getInstance();
        if (!api.isLoggedIn()) {
            Log.d(TAG, "⏳ 未登录，跳过同步");
            return;
        }
        
        try {
            Log.d(TAG, "🔄 开始同步策略...");
            
            // 1. 先获取用户信息，判断是家长还是孩子
            JSONObject userInfo = api.getUserMe();
            String role = userInfo.optString("role", "");
            
            if ("child".equals(role)) {
                // ⭐ 孩子端：拉取家长设置的策略
                String childId = userInfo.getString("id");
                
                // 1️⃣ 拉取白名单/黑名单
                try {
                    JSONObject whitelistResp = api.getWhitelist(childId);
                    JSONArray apps = whitelistResp.optJSONArray("apps");
                    if (apps != null) {
                        // 把白名单保存到本地
                        List<Map<String, String>> appList = new ArrayList<>();
                        List<String> blockedList = new ArrayList<>();
                        for (int i = 0; i < apps.length(); i++) {
                            JSONObject app = apps.getJSONObject(i);
                            Map<String, String> map = new HashMap<>();
                            map.put("package_name", app.optString("package_name"));
                            map.put("app_name", app.optString("app_name"));
                            appList.add(map);
                        }
                        commandStore.saveWhitelistApps(appList);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "同步白名单失败", e);
                }
                
                // 2️⃣ 拉取任务列表
                try {
                    JSONObject missionsResp = api.getMissions(childId);
                    JSONArray missions = missionsResp.optJSONArray("missions");
                    if (missions != null) {
                        commandStore.saveMissions(missions);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "同步任务失败", e);
                }
                
                Log.d(TAG, "✅ 策略同步完成 (角色: 孩子)");
            }
            
            // 更新版本号
            commandStore.updatePolicyVersion(System.currentTimeMillis());
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 同步失败: " + e.getMessage() + " (断网或API不可用)");
            // ⭐ 断网时静默失败——本地文件依然存在，管控照常运行！
        }
    }
    
    // ===================== 网络恢复自动同步 =====================
    
    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(Network network) {
            Log.d(TAG, "🌐 网络已恢复，立即同步策略");
            syncPolicies();
        }
    }
    
    // ===================== 启动/停止服务（工具方法） =====================
    
    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, PolicySyncService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
            Log.d(TAG, "▶️ 启动策略同步服务");
        } catch (Exception e) {
            Log.e(TAG, "启动同步服务失败（SDK " + Build.VERSION.SDK_INT + "）: " + e.getMessage());
            // 安全降级：服务不可用不影响主流程
        }
    }

    public static void stop(Context context) {
        try {
            Intent intent = new Intent(context, PolicySyncService.class);
            context.stopService(intent);
            Log.d(TAG, "⏹ 停止策略同步服务");
        } catch (Exception e) {
            Log.e(TAG, "停止同步服务失败: " + e.getMessage());
        }
    }
}
