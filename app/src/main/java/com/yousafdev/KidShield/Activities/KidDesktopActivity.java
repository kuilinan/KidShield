package com.yousafdev.KidShield.Activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yousafdev.KidShield.Models.KidPolicy;
import com.yousafdev.KidShield.Network.KidCommandStore;
import com.yousafdev.KidShield.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * KidDesktopActivity - 假桌面（安全桌面）
 *
 * 强制锁屏时启动的可爱安全环境：
 * - 显示锁屏倒计时（elapsedRealtime 基准，改时间无效）
 * - 只显示白名单应用（点开即用）
 * - 呼叫家长按钮（一键拨号）
 * - 防逃逸：返回键无效、onStop 检查锁屏状态立即拉回、不出现在最近任务
 * - 到点自动退出（由 AlarmManager 触发，本页轮询倒计时并自退）
 *
 * 参考：tuEagles Kiosk 桌面设计（技术参考）
 */
public class KidDesktopActivity extends Activity {

    private static final long COUNTDOWN_TICK_MS = 1000;

    private TextView textViewCountdown;
    private TextView textViewLockReason;
    private RecyclerView recyclerViewApps;
    private Button buttonCallParent;
    private Button buttonExit;

    private KidCommandStore commandStore;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kid_desktop);

        commandStore = new KidCommandStore(this);

        textViewCountdown = findViewById(R.id.textView_lock_countdown);
        textViewLockReason = findViewById(R.id.textView_lock_reason);
        recyclerViewApps = findViewById(R.id.recyclerView_desktop_apps);
        buttonCallParent = findViewById(R.id.button_call_parent);
        buttonExit = findViewById(R.id.button_desktop_exit);

        // 锁屏原因展示
        KidPolicy policy = commandStore.loadPolicy();
        if (policy.lockReason != null && !policy.lockReason.isEmpty()) {
            textViewLockReason.setText(policy.lockReason);
        }

        // 白名单应用网格
        recyclerViewApps.setLayoutManager(new GridLayoutManager(this, 4));
        recyclerViewApps.setAdapter(new DesktopAppAdapter(loadWhitelistApps()));

        // 呼叫家长（电话白名单：先打开拨号界面）
        buttonCallParent.setOnClickListener(v -> {
            try {
                Intent dial = new Intent(Intent.ACTION_DIAL);
                dial.setData(Uri.parse("tel:"));
                startActivity(dial);
            } catch (Exception e) {
                Toast.makeText(this, "无法打开拨号", Toast.LENGTH_SHORT).show();
            }
        });

        // 到点自动解锁按钮（仅提示，真正解锁靠闹钟）
        buttonExit.setOnClickListener(v ->
                Toast.makeText(this, "到点会自动解锁哦，耐心等待 ⏰", Toast.LENGTH_SHORT).show());

        // 防逃逸：不显示在最近任务
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
    }

    /** 加载白名单应用（带图标） */
    private List<DesktopApp> loadWhitelistApps() {
        List<DesktopApp> apps = new ArrayList<>();
        try {
            KidPolicy policy = commandStore.loadPolicy();
            PackageManager pm = getPackageManager();

            if (policy.whitelist != null) {
                for (String pkg : policy.whitelist) {
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                        Drawable icon = pm.getApplicationIcon(ai);
                        String name = pm.getApplicationLabel(ai).toString();
                        apps.add(new DesktopApp(name, pkg, icon));
                    } catch (Exception ignored) {
                    }
                }
            }
            Collections.sort(apps, Comparator.comparing(a -> a.name));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return apps;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 启动倒计时轮询
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                long remainMs = commandStore.getRemainingLockMs();
                if (remainMs <= 0) {
                    // 锁屏指令已到期 → 自动退出假桌面
                    finish();
                    return;
                }
                textViewCountdown.setText(formatCountdown(remainMs));
                handler.postDelayed(this, COUNTDOWN_TICK_MS);
            }
        };
        handler.post(countdownRunnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 防逃逸：如果锁屏指令还在，离开假桌面就立刻拉回！
        if (!isFinishing() && commandStore.isLocked()) {
            handler.postDelayed(() -> {
                if (commandStore.isLocked()) {
                    Intent intent = new Intent(this, KidDesktopActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }
            }, 300);
        }
        if (handler != null && countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
        }
    }

    @Override
    public void onBackPressed() {
        // 锁屏中：返回键无效（防逃逸）
        Toast.makeText(this, "安全小天地里不能后退哦 🏠", Toast.LENGTH_SHORT).show();
    }

    /** 倒计时格式化 mm:ss */
    private String formatCountdown(long ms) {
        long totalSec = ms / 1000;
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format(Locale.CHINA, "剩余 %02d:%02d", min, sec);
    }

    /** 桌面应用数据 */
    private static class DesktopApp {
        String name;
        String packageName;
        Drawable icon;

        DesktopApp(String name, String packageName, Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    /** 应用网格适配器 */
    private class DesktopAppAdapter extends RecyclerView.Adapter<DesktopAppAdapter.VH> {
        private List<DesktopApp> apps;

        DesktopAppAdapter(List<DesktopApp> apps) {
            this.apps = apps;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            // 简易网格项：竖向布局（图标+名字）
            android.widget.LinearLayout item = new android.widget.LinearLayout(KidDesktopActivity.this);
            item.setOrientation(android.widget.LinearLayout.VERTICAL);
            item.setGravity(android.view.Gravity.CENTER);
            int pad = dp(8);
            item.setPadding(pad, pad, pad, pad);

            android.widget.ImageView iv = new android.widget.ImageView(KidDesktopActivity.this);
            iv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(52), dp(52)));
            item.addView(iv);

            android.widget.TextView tv = new android.widget.TextView(KidDesktopActivity.this);
            tv.setTextColor(getColor(R.color.kid_clay_text));
            tv.setTextSize(11);
            tv.setMaxLines(1);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
            item.addView(tv);

            return new VH(item, iv, tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            DesktopApp app = apps.get(position);
            holder.icon.setImageDrawable(app.icon);
            holder.name.setText(app.name);
            holder.itemView.setOnClickListener(v -> {
                try {
                    Intent launch = getPackageManager().getLaunchIntentForPackage(app.packageName);
                    if (launch != null) startActivity(launch);
                } catch (Exception e) {
                    Toast.makeText(KidDesktopActivity.this, "打不开啦", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }

        class VH extends RecyclerView.ViewHolder {
            android.widget.ImageView icon;
            android.widget.TextView name;

            VH(View itemView, android.widget.ImageView icon, android.widget.TextView name) {
                super(itemView);
                this.icon = icon;
                this.name = name;
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}