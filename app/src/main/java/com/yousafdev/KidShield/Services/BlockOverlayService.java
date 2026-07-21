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

import com.yousafdev.KidShield.R;

/**
 * 悬浮窗拦截服务
 * 当检测到卸载/设置页面时，全屏覆盖阻止操作
 * 孩子点击"我知道了"返回桌面
 */
public class BlockOverlayService extends Service {

    private static final String TAG = "BlockOverlayService";
    private WindowManager windowManager;
    private View overlayView;
    private boolean isShowing = false;

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
            btnOk.setOnClickListener(v -> {
                goHome();
                dismissOverlay();
            });
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
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
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
