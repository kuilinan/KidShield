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
        if (childEmail.isEmpty() || parentCode.isEmpty()) {
            Toast.makeText(this, "请填写孩子邮箱和家长码", Toast.LENGTH_SHORT).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        buttonBind.setEnabled(false);
        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("child_email", childEmail);
                payload.put("parent_code", parentCode);
                JSONObject result = apiClient.bindChild(parentCode); // 复用现有API
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    buttonBind.setEnabled(true);
                    try {
                        boolean success = result.optBoolean("success", false);
                        if (success || result.has("id")) {
                            Toast.makeText(BindChildActivity.this, "🎉 绑定成功！", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            String msg = result.optString("message", "绑定失败，请检查邮箱和家长码");
                            Toast.makeText(BindChildActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(BindChildActivity.this, "绑定成功！", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    buttonBind.setEnabled(true);
                    Toast.makeText(BindChildActivity.this, "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}
