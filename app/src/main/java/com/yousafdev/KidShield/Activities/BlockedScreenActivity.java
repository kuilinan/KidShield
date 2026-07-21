package com.yousafdev.KidShield.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.yousafdev.KidShield.R;

/**
 * 应用被封锁时显示的提示页面
 * 点击"我知道了"返回桌面
 * 点击"申请解封"进入孩子端申请页面
 */
public class BlockedScreenActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked_screen);

        Button buttonOk = findViewById(R.id.button_ok);
        buttonOk.setOnClickListener(v -> {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
            // finish() 注释掉，让 Activity 保留在栈中但被桌面覆盖
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
