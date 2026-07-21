package com.yousafdev.KidShield.Services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.yousafdev.KidShield.R;

/**
 * 悬浮窗拦截服务
 * 当检测到卸载/设置页面时，全屏覆盖阻止操作
 * 孩子需要长按"我知道了"按钮才能返回桌面（防止误触绕过）
 * 增强：显示被禁应用名、原因，支持联系家长按钮
 */
public class BlockOverlayService extends Service {

    private static final String TAG = "BlockOverlayService";
    private WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;
    private String blockedPackage = "";
    private String blockReason = "";

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "HIDE".equals(intent.getAction())) {
            dismissOverlay();
            return START_NOT_STICKY;
        }
        // 读取传入的包名和原因
        if (intent != null) {
            blockedPackage = intent.getStringExtra("blockedPackage");
            blockReason = intent.getStringExtra("reason");
            if (blockReason == null) blockReason = "该应用已被家长限制";
        }
        if (!isShowing) {
            showOverlay();
        }
        return START_STICKY;
    }

    private void showOverlay() {
        if (overlayView == null) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
            overlayView = inflater.inflate(R.layout.overlay_block_screen, null);

            Button btnOk = overlayView.findViewById(R.id.btn_overlay_ok);
            // 长按才能关闭（防止儿童误触）
            btnOk.setOnLongClickListener(v -> {
                goHome();
                dismissOverlay();
                Toast.makeText(this, "已返回桌面", Toast.LENGTH_SHORT).show();
                return true;
            });
            btnOk.setOnClickListener(v -> {
                Toast.makeText(this, "需要长按才能关闭", Toast.LENGTH_SHORT).show();
            });

            // 设置应用名和原因
            TextView tvAppName = overlayView.findViewById(R.id.tv_blocked_app_name);
            TextView tvReason = overlayView.findViewById(R.id.tv_block_reason);
            if (tvAppName != null && blockedPackage != null && !blockedPackage.isEmpty()) {
                tvAppName.setText(blockedPackage);
                tvAppName.setVisibility(View.VISIBLE);
            }
            if (tvReason != null && blockReason != null) {
                tvReason.setText(blockReason);
            }
        }

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_FULLSCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.CENTER;
        params.x = 0;
        params.y = 0;

        try {
            windowManager.addView(overlayView, params);
            isShowing = true;
        } catch (Exception e) {
            android.util.Log.e(TAG, "显示悬浮窗失败", e);
            stopSelf();
        }
    }

    private void goHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
    }

    public void dismissOverlay() {
        if (overlayView != null && isShowing) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception e) {
                // ignore
            }
            isShowing = false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        dismissOverlay();
        super.onDestroy();
    }
}
