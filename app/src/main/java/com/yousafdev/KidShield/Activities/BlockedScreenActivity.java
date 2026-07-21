package com.yousafdev.KidShield.Activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.yousafdev.KidShield.R;

/**
 * 应用被封锁时显示的提示页面
 * 孩子需要长按"长按返回桌面"按钮才能返回（防止误触绕过）
 * 显示被禁应用名称和原因
 */
public class BlockedScreenActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_screen);

        // 读取传入的包名和原因
        String blockedPackage = getIntent().getStringExtra("blocked_package");
        String reason = getIntent().getStringExtra("reason");

        // 显示被禁应用的名称
        TextView tvAppName = findViewById(R.id.tv_blocked_app_name);
        if (blockedPackage != null && !blockedPackage.isEmpty()) {
            try {
                PackageManager pm = getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(blockedPackage, 0);
                String appName = pm.getApplicationLabel(ai).toString();
                tvAppName.setText(appName);
            } catch (Exception e) {
                tvAppName.setText(blockedPackage);
            }
            tvAppName.setVisibility(android.view.View.VISIBLE);
        }

        // 显示原因
        TextView tvReason = findViewById(R.id.tv_block_reason);
        if (reason != null && !reason.isEmpty()) {
            tvReason.setText(reason);
        }

        Button buttonOk = findViewById(R.id.button_ok);
        // 长按才能返回桌面（防止儿童误触）
        buttonOk.setOnLongClickListener(v -> {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
            Toast.makeText(this, "已返回桌面", Toast.LENGTH_SHORT).show();
            return true;
        });
        buttonOk.setOnClickListener(v -> {
            Toast.makeText(this, "需要长按才能返回桌面", Toast.LENGTH_SHORT).show();
        });

        Button buttonRequestUnlock = findViewById(R.id.button_request_unlock);
        buttonRequestUnlock.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChildDashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // 按返回键也回桌面，不关闭封锁页面
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
