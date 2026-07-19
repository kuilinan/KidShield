package com.yousafdev.KidShield.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yousafdev.KidShield.R;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editTextEmail, editTextPassword;
    private Button buttonLogin;
    private TextView textViewRegisterPrompt;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private boolean firebaseTimeout = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        mAuth = FirebaseAuth.getInstance();
        editTextEmail = findViewById(R.id.editText_email);
        editTextPassword = findViewById(R.id.editText_password);
        buttonLogin = findViewById(R.id.button_login);
        textViewRegisterPrompt = findViewById(R.id.textView_register_prompt);
        progressBar = findViewById(R.id.progressBar);
        buttonLogin.setOnClickListener(v -> loginUser());
        textViewRegisterPrompt.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        // 添加"跳过登录"按钮 - 解决国内网络连不上 Firebase 的问题
        Button skipBtn = new Button(this);
        skipBtn.setText("跳过登录 · 离线Demo模式");
        skipBtn.setTextSize(14);
        skipBtn.setAlpha(0.8f);
        skipBtn.setBackgroundColor(0x33FFFFFF);
        skipBtn.setTextColor(0xFFB0BEC5);
        skipBtn.setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            // 直接进入孩子端主界面用于测试
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
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            progressBar.setVisibility(View.VISIBLE);
            checkUserRoleAndNavigate(currentUser.getUid());
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
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        checkUserRoleAndNavigate(task.getResult().getUser().getUid());
                    } else {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(LoginActivity.this, "登录失败：" + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void checkUserRoleAndNavigate(String uid) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(uid);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                if (snapshot.exists()) {
                    String role = snapshot.child("role").getValue(String.class);
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
                } else {
                    Toast.makeText(LoginActivity.this, "未找到用户数据，请重新注册", Toast.LENGTH_SHORT).show();
                    mAuth.signOut();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, "数据库错误：" + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}