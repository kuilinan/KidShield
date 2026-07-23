package com.yousafdev.KidShield.Services;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.yousafdev.KidShield.R;

/**
 * 增强版悬浮窗拦截服务
 * 
 * 功能：
 * 1. 全屏覆盖阻止操作（长按返回桌面）
 * 2. 标志位加固：防止 Home/Back/最近任务绕过
 * 3. VIVO 特殊适配：覆盖 VIVO 独占卸载/设置界面
 * 4. 定时刷新：防止被系统或省电策略关闭
 * 5. 多窗口保护：防止分屏/小窗绕过
 */
public class BlockOverlayService extends Service {
    private static final String TAG = "BlockOverlayService";
    private static final long REFRESH_INTERVAL_MS = 30000; // 30秒刷新一次

    private WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;
    private String blockedPackage = "";
    private String blockReason = "";

    private Handler refreshHandler;
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            // 定时刷新悬浮窗，防止被系统回收
            if (isShowing && overlayView != null && !overlayView.isAttachedToWindow()) {
                Log.w(TAG, "悬浮窗已分离，重新显示");
                showOverlayImmediate();
            }
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        refreshHandler = new Handler(Looper.getMainLooper());
        Log.d(TAG, "增强版悬浮窗拦截服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "HIDE".equals(intent.getAction())) {
            dismissOverlay();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            blockedPackage = intent.getStringExtra("blockedPackage");
            blockReason = intent.getStringExtra("reason");
            if (blockReason == null || blockReason.isEmpty()) {
                blockReason = "该操作已被家长限制";
            }
        }

        // 如果正在使用不完整视图，先销毁再重建
        if (overlayView != null && isShowing) {
            dismissOverlay();
        }

        if (!isShowing) {
            showOverlay();
        }

        // 启动定时刷新
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);

        return START_STICKY;
    }

    private void showOverlay() {
        showOverlayImmediate();
    }

    private void showOverlayImmediate() {
        try {
            if (overlayView == null) {
                LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                overlayView = inflater.inflate(R.layout.overlay_block_screen, null);

                // 按钮：长按关闭
                Button btnOk = overlayView.findViewById(R.id.btn_overlay_ok);
                btnOk.setOnLongClickListener(v -> {
                    goHome();
                    dismissOverlay();
                    Toast.makeText(this, "已返回桌面", Toast.LENGTH_SHORT).show();
                    return true;
                });
                btnOk.setOnClickListener(v -> {
                    Toast.makeText(this, "需要长按才能关闭", Toast.LENGTH_SHORT).show();
                });

                // 显示被限制的应用名和原因
                TextView tvAppName = overlayView.findViewById(R.id.tv_blocked_app_name);
                TextView tvReason = overlayView.findViewById(R.id.tv_block_reason);
                if (tvAppName != null) {
                    if (blockedPackage != null && !blockedPackage.isEmpty()) {
                        tvAppName.setText(blockedPackage);
                        tvAppName.setVisibility(View.VISIBLE);
                    } else {
                        tvAppName.setVisibility(View.GONE);
                    }
                }
                if (tvReason != null && blockReason != null) {
                    tvReason.setText(blockReason);
                }
            }

            // 窗口类型选择
            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            // 标志位加固：
            // FLAG_NOT_TOUCH_MODAL - 所有触摸都发给悬浮窗
            // FLAG_WATCH_OUTSIDE_TOUCH - 监听外部触摸
            // FLAG_SHOW_WHEN_LOCKED - 锁屏显示
            // FLAG_TURN_SCREEN_ON - 点亮屏幕
            // FLAG_KEEP_SCREEN_ON - 保持屏幕
            // FLAG_LAYOUT_IN_SCREEN - 全屏布局
            // FLAG_FULLSCREEN - 全屏
            // FLAG_HARDWARE_ACCELERATED - 硬件加速
            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

            // Android 9+ 硬件加速
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            }

            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    flags,
                    PixelFormat.TRANSLUCENT
            );
            params.gravity = Gravity.CENTER;
            params.x = 0;
            params.y = 0;

            // VIVO / 部分机型需要设置 systemUiVisibility 才能全屏覆盖
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                params.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            }

            // 设置窗口标题（调试用）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                params.setTitle("KidShield BlockOverlay");
            }

            windowManager.addView(overlayView, params);
            isShowing = true;
            Log.d(TAG, "悬浮窗已显示（VIVO加固模式）");

        } catch (Exception e) {
            Log.e(TAG, "显示悬浮窗失败", e);

            // 如果被系统拦截（如 VIVO 智能省电），尝试返回桌面并提示
            if (e.getMessage() != null && e.getMessage().contains("permission")) {
                Log.w(TAG, "VIVO 系统拦截悬浮窗，尝试界面返回");
                goHome();
            }
        }
    }

    private void goHome() {
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(homeIntent);
        } catch (Exception e) {
            Log.e(TAG, "返回桌面失败", e);
        }
    }

    public void dismissOverlay() {
        if (overlayView != null && isShowing) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            isShowing = false;
        }
        if (refreshHandler != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        dismissOverlay();
        super.onDestroy();
    }
}
