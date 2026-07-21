package com.yousafdev.KidShield.Activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.yousafdev.KidShield.Network.ApiClient;
import com.yousafdev.KidShield.R;

import org.json.JSONObject;

public class BindChildActivity extends AppCompatActivity {
    private EditText editTextChildEmail;
    private EditText editTextParentCode;
    private Button buttonBind;
    private ProgressBar progressBar;
    private ApiClient apiClient;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bind_child);

        editTextChildEmail = findViewById(R.id.editText_child_email);
        editTextParentCode = findViewById(R.id.editText_parent_code);
        buttonBind = findViewById(R.id.button_bind);
        progressBar = findViewById(R.id.progressBar_bind);

        apiClient = new ApiClient();
        SharedPreferences prefs = getSharedPreferences("kidshield", MODE_PRIVATE);
        token = prefs.getString("token", "");

        buttonBind.setOnClickListener(v -> bindChild());
    }

    private void bindChild() {
        String childEmail = editTextChildEmail.getText().toString().trim();
        String parentCode = editTextParentCode.getText().toString().trim();
        if (childEmail.isEmpty()) {
            Toast.makeText(this, "请填写孩子邮箱", Toast.LENGTH_SHORT).show();
            return;
        }
        if (parentCode.isEmpty()) {
            Toast.makeText(this, "请在家长端查看并输入家长码", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        buttonBind.setEnabled(false);
        new Thread(() -> {
            try {
                JSONObject result = apiClient.parentBindChild(childEmail, parentCode);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    buttonBind.setEnabled(true);
                    try {
                        boolean success = result.optBoolean("success", false);
                        if (success || result.has("id") || result.optString("message","").contains("成功")) {
                            Toast.makeText(BindChildActivity.this, "🎉 绑定成功！", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            String msg = result.optString("message", "绑定失败，请检查邮箱和家长码是否正确");
                            Toast.makeText(BindChildActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(BindChildActivity.this, "绑定失败，请检查邮箱和家长码", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    buttonBind.setEnabled(true);
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("400")) {
                        Toast.makeText(BindChildActivity.this, "绑定失败：请检查孩子邮箱是否已注册", Toast.LENGTH_SHORT).show();
                    } else if (msg != null && msg.contains("401")) {
                        Toast.makeText(BindChildActivity.this, "绑定失败：家长码错误或已过期", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(BindChildActivity.this, "网络错误: " + (msg != null ? msg : "未知错误"), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
}
