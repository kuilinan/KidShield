package com.yousafdev.KidShield.Activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.yousafdev.KidShield.Network.ApiClient;
import com.yousafdev.KidShield.Network.PolicySyncService;
import com.yousafdev.KidShield.R;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editTextEmail, editTextPassword;
    private Button buttonLogin;
    private TextView textViewRegisterPrompt;
    private ProgressBar progressBar;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = getSharedPreferences("kidshield", Context.MODE_PRIVATE);

        editTextEmail = findViewById(R.id.editText_email);
        editTextPassword = findViewById(R.id.editText_password);
        buttonLogin = findViewById(R.id.button_login);
        textViewRegisterPrompt = findViewById(R.id.textView_register_prompt);
        progressBar = findViewById(R.id.progressBar);

        buttonLogin.setOnClickListener(v -> loginUser());
        textViewRegisterPrompt.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        // 添加"跳过登录"按钮 - 离线Demo模式
        Button skipBtn = new Button(this);
        skipBtn.setText("跳过登录 · 离线Demo模式");
        skipBtn.setTextSize(14);
        skipBtn.setAlpha(0.8f);
        skipBtn.setBackgroundColor(0x33FFFFFF);
        skipBtn.setTextColor(0xFFB0BEC5);
        skipBtn.setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            Intent intent = new Intent(LoginActivity.this, ChildDashboardActivity.class);
            intent.putExtra("OFFLINE_MODE", true);
            startActivity(intent);
            finish();
        });

        // 把按钮加到底部
        runOnUiThread(() -> {
            try {
                android.widget.LinearLayout root = findViewById(android.R.id.content);
                if (root != null && root.getChildCount() > 0 && root.getChildAt(0) instanceof android.widget.ScrollView) {
                    android.widget.ScrollView sv = (android.widget.ScrollView) root.getChildAt(0);
                    if (sv.getChildCount() > 0 && sv.getChildAt(0) instanceof androidx.constraintlayout.widget.ConstraintLayout) {
                        androidx.constraintlayout.widget.ConstraintLayout cl = (androidx.constraintlayout.widget.ConstraintLayout) sv.getChildAt(0);
                        skipBtn.setId(android.view.View.generateViewId());
                        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams params =
                                new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                                        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT,
                                        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
                                );
                        params.topToBottom = R.id.textView_register_prompt;
                        params.leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
                        params.rightToRight = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
                        params.topMargin = 180;
                        params.leftMargin = 32;
                        params.rightMargin = 32;
                        cl.addView(skipBtn, params);
                    }
                }
            } catch (Exception e) {
                // 忽略
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 检查是否已登录（有保存的token）
        String savedToken = prefs.getString("auth_token", null);
        String savedRole = prefs.getString("user_role", null);
        if (savedToken != null && savedRole != null) {
            progressBar.setVisibility(View.VISIBLE);
            ApiClient.getInstance().setToken(savedToken);
            navigateByRole(savedRole);
        }
    }

    private void loginUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "请输入邮箱地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);

        new Thread(() -> {
            try {
                JSONObject result = ApiClient.getInstance().login(email, password);
                // 检查后端是否返回了错误信息（如账号不存在/密码错误）
                if (result.has("error")) {
                    String errMsg = result.optString("error", "未知错误");
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(LoginActivity.this, "登录失败: " + errMsg, Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                // 安全获取 token，避免 JSONException
                if (!result.has("token") || !result.has("user")) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(LoginActivity.this, "服务器响应异常，请稍后重试", Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                String token = result.getString("token");
                JSONObject user = result.getJSONObject("user");
                String role = user.getString("role");
                String id = user.getString("id");

                // 保存登录信息（注意 key 名要和其他 Activity 一致）
                prefs.edit()
                    .putString("token", token)
                    .putString("auth_token", token)
                    .putString("user_role", role)
                    .putString("user_id", id)
                    .putString("user_email", email)
                    .apply();

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(LoginActivity.this, "✅ 登录成功", Toast.LENGTH_SHORT).show();
                    // 启动策略同步服务（孩子端自动拉取家长指令）
                    PolicySyncService.start(LoginActivity.this);
                    navigateByRole(role);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("401")) {
                        Toast.makeText(LoginActivity.this, "邮箱或密码错误", Toast.LENGTH_LONG).show();
                    } else if (msg != null && msg.contains("Failed to connect")) {
                        Toast.makeText(LoginActivity.this, "网络连接失败，请检查网络", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(LoginActivity.this, "登录失败：" + (msg != null ? msg : "未知错误"),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void navigateByRole(String role) {
        Intent intent;
        if ("child".equals(role)) {
            intent = new Intent(LoginActivity.this, ChildSetupActivity.class);
        } else if ("parent".equals(role)) {
            intent = new Intent(LoginActivity.this, ParentDashboardActivity.class);
        } else {
            Toast.makeText(LoginActivity.this, "未知的用户角色", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
        finish();
    }
}
