package com.yousafdev.KidShield.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.yousafdev.KidShield.R;
import com.yousafdev.KidShield.Network.ApiClient;

import org.json.JSONObject;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText editTextEmail, editTextPassword, editTextParentEmail;
    private TextInputLayout textInputLayoutParentEmail;
    private Button buttonRegister;
    private TextView textViewLoginPrompt;
    private RadioGroup radioGroupRole;
    private ProgressBar progressBar;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // 使用自建 API

        // Initialize UI components
        editTextEmail = findViewById(R.id.editText_email_register);
        editTextPassword = findViewById(R.id.editText_password_register);
        editTextParentEmail = findViewById(R.id.editText_parent_email);
        textInputLayoutParentEmail = findViewById(R.id.textInputLayout_parent_email);
        buttonRegister = findViewById(R.id.button_register);
        textViewLoginPrompt = findViewById(R.id.textView_login_prompt);
        radioGroupRole = findViewById(R.id.radioGroup_role);
        progressBar = findViewById(R.id.progressBar_register);

        // Listener for role selection to show/hide the parent email field
        radioGroupRole.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioButton_child) {
                textInputLayoutParentEmail.setVisibility(View.VISIBLE);
            } else {
                textInputLayoutParentEmail.setVisibility(View.GONE);
            }
        });

        // Listener for the register button
        buttonRegister.setOnClickListener(v -> registerUser());

        // Listener to switch back to login screen
        textViewLoginPrompt.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void registerUser() {
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String parentEmail = editTextParentEmail.getText().toString().trim();
        int selectedRoleId = radioGroupRole.getCheckedRadioButtonId();

        // --- Input Validation ---
        if (TextUtils.isEmpty(email)) {
            Toast.makeText(this, "请输入邮箱地址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "请输入密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "密码至少需要6个字符", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedRoleId == -1) {
            Toast.makeText(this, "请选择身份（家长/孩子）", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedRoleId == R.id.radioButton_child && TextUtils.isEmpty(parentEmail)) {
            Toast.makeText(this, "请输入家长邮箱地址", Toast.LENGTH_SHORT).show();
            return;
        }
        // --- End Validation ---

        progressBar.setVisibility(View.VISIBLE);

        // 使用自建 API 注册
        new Thread(() -> {
            try {
                ApiClient apiClient = new ApiClient();
                RadioButton selectedRadioButton = findViewById(selectedRoleId);
                String role = selectedRadioButton.getText().toString().toLowerCase();
                String nickname = role.equals("parent") ? "家长" : "孩子";

                JSONObject result = apiClient.register(email, password, role, nickname);
                String token = result.optString("token", "");

                // 保存 Token 到本地
                if (!token.isEmpty()) {
                    SharedPreferences prefs = getSharedPreferences("kidshield", MODE_PRIVATE);
                    prefs.edit()
                        .putString("token", token)
                        .putString("email", email)
                        .putString("role", role)
                        .putString("user_id", result.optString("uid", ""))
                        .apply();
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(RegisterActivity.this, "注册成功！", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(RegisterActivity.this, "注册失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}