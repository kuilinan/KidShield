package com.yousafdev.KidShield.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.yousafdev.KidShield.Adapters.ChildAdapter;
import com.yousafdev.KidShield.Models.Child;
import com.yousafdev.KidShield.Network.ApiClient;
import com.yousafdev.KidShield.R;
import com.yousafdev.KidShield.Utils.VivoKeepAliveHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ParentDashboardActivity extends AppCompatActivity implements ChildAdapter.OnChildListener {
    private RecyclerView recyclerView;
    private ChildAdapter adapter;
    private List<Child> childList;
    private ProgressBar progressBar;
    private TextView textViewEmptyState;
    private FloatingActionButton fabAddChild;
    private ApiClient apiClient;
    private String token;

    // 统计卡片
    private TextView textViewStatChildrenCount;
    private TextView textViewStatScreenTime;
    private TextView textViewStatAlerts;

    // VIVO 保活引导
    private CardView cardVivoKeepalive;
    private TextView textViewVivoStatus;
    private TextView textViewVivoGuide;
    private Button btnVivoBattery, btnVivoAutostart, btnVivoLockClean, btnVivoHighpower;
    private boolean vivoGuideExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_dashboard);

        // 统计卡片
        textViewStatChildrenCount = findViewById(R.id.textView_stat_children_count);
        textViewStatScreenTime = findViewById(R.id.textView_stat_screen_time);
        textViewStatAlerts = findViewById(R.id.textView_stat_alerts);

        recyclerView = findViewById(R.id.recyclerView_children);
        progressBar = findViewById(R.id.progressBar_dashboard);
        textViewEmptyState = findViewById(R.id.textView_empty_state);
        fabAddChild = findViewById(R.id.fab_add_child);

        // VIVO 保活引导
        cardVivoKeepalive = findViewById(R.id.card_vivo_keepalive);
        textViewVivoStatus = findViewById(R.id.textView_vivo_status);
        textViewVivoGuide = findViewById(R.id.textView_vivo_guide);
        btnVivoBattery = findViewById(R.id.btn_vivo_battery_whitelist);
        btnVivoAutostart = findViewById(R.id.btn_vivo_autostart);
        btnVivoLockClean = findViewById(R.id.btn_vivo_lock_clean);
        btnVivoHighpower = findViewById(R.id.btn_vivo_highpower);

        apiClient = new ApiClient();
        SharedPreferences prefs = getSharedPreferences("kidshield", MODE_PRIVATE);
        token = prefs.getString("token", "");
        apiClient.setToken(token);

        childList = new ArrayList<>();
        adapter = new ChildAdapter(childList, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setNestedScrollingEnabled(false);

        fabAddChild.setOnClickListener(v -> {
            Intent intent = new Intent(this, BindChildActivity.class);
            startActivity(intent);
        });

        // 初始化 VIVO 保活引导
        initVivoKeepAliveGuide();

        fetchChildren();
    }

    private void initVivoKeepAliveGuide() {
        // 仅 VIVO 设备显示保活引导
        if (!VivoKeepAliveHelper.isVivoDevice()) {
            cardVivoKeepalive.setVisibility(View.GONE);
            return;
        }

        // 检查保活状态
        boolean isFullyConfigured = VivoKeepAliveHelper.isFullyConfigured(this);
        if (isFullyConfigured) {
            // 全部已配置，隐藏卡片（或显示完成状态）
            cardVivoKeepalive.setVisibility(View.GONE);
            return;
        }

        // 未完全配置，显示引导卡片
        cardVivoKeepalive.setVisibility(View.VISIBLE);

        // 点击状态栏展开/收起详细引导文案
        textViewVivoStatus.setOnClickListener(v -> {
            vivoGuideExpanded = !vivoGuideExpanded;
            if (vivoGuideExpanded) {
                textViewVivoGuide.setVisibility(View.VISIBLE);
                textViewVivoGuide.setText(VivoKeepAliveHelper.getLockTaskGuideText() +
                        "\n\n完成后重启 KidShield 确保生效");
            } else {
                textViewVivoGuide.setVisibility(View.GONE);
            }
        });

        // 电池白名单
        btnVivoBattery.setOnClickListener(v -> {
            try {
                startActivity(VivoKeepAliveHelper.getBatteryOptimizationIntent(this));
                Toast.makeText(this, "请将 KidShield 设为\"不允许优化\"", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "无法打开设置: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 自启动管理
        btnVivoAutostart.setOnClickListener(v -> {
            try {
                Intent intent = VivoKeepAliveHelper.getAutoStartIntent(this);
                if (intent != null) {
                    startActivity(intent);
                    Toast.makeText(this, "请开启 KidShield 的自启动权限", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "无法打开自启动设置: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 锁屏清理白名单
        btnVivoLockClean.setOnClickListener(v -> {
            try {
                Intent intent = VivoKeepAliveHelper.getLockScreenCleanIntent(this);
                if (intent != null) {
                    startActivity(intent);
                    Toast.makeText(this, "请将 KidShield 加入锁屏清理白名单", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "无法打开设置: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 后台高耗电白名单
        btnVivoHighpower.setOnClickListener(v -> {
            try {
                Intent intent = VivoKeepAliveHelper.getHighPowerIntent(this);
                if (intent != null) {
                    startActivity(intent);
                    Toast.makeText(this, "请允许 KidShield 后台高耗电", Toast.LENGTH_LONG).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "无法打开设置: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 更新状态文案
        String[] items = VivoKeepAliveHelper.getKeepAliveGuideItems(this);
        StringBuilder statusText = new StringBuilder("需要配置以下项目：\n");
        for (String item : items) {
            statusText.append("• ").append(item).append("\n");
        }
        textViewVivoStatus.setText(statusText.toString().trim());
    }

    private void fetchChildren() {
        progressBar.setVisibility(View.VISIBLE);
        if (token.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            return;
        }
        new Thread(() -> {
            try {
                String result = apiClient.getChildren().toString();
                JSONObject respObj = new JSONObject(result);
                JSONArray childrenArr = respObj.optJSONArray("children");
                if (childrenArr == null) {
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        textViewEmptyState.setVisibility(View.VISIBLE);
                        updateStats(0, "0", 0);
                    });
                    return;
                }
                List<Child> tempList = new ArrayList<>();
                int totalAlerts = 0;
                for (int i = 0; i < childrenArr.length(); i++) {
                    JSONObject childObj = childrenArr.getJSONObject(i);
                    String id = childObj.optString("id", "");
                    String email = childObj.optString("email", "");
                    if (!id.isEmpty()) {
                        Child child = new Child(id, email);
                        tempList.add(child);
                        totalAlerts += childObj.optInt("alertCount", 0);
                    }
                }
                final int alertCount = totalAlerts;
                runOnUiThread(() -> {
                    childList.clear();
                    childList.addAll(tempList);
                    adapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                    if (tempList.isEmpty()) {
                        textViewEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        textViewEmptyState.setVisibility(View.GONE);
                    }
                    updateStats(tempList.size(), "--:--", alertCount);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ParentDashboardActivity.this, "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void updateStats(int childCount, String screenTime, int alerts) {
        if (textViewStatChildrenCount != null) {
            textViewStatChildrenCount.setText(String.valueOf(childCount));
        }
        if (textViewStatScreenTime != null) {
            textViewStatScreenTime.setText(screenTime);
        }
        if (textViewStatAlerts != null) {
            textViewStatAlerts.setText(String.valueOf(alerts));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchChildren();
        // 回到界面时重新检查保活状态
        if (VivoKeepAliveHelper.isVivoDevice() && cardVivoKeepalive != null) {
            boolean isFullyConfigured = VivoKeepAliveHelper.isFullyConfigured(this);
            cardVivoKeepalive.setVisibility(isFullyConfigured ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onChildClick(int position) {
        Child selectedChild = childList.get(position);
        Intent intent = new Intent(this, ChildDetailActivity.class);
        intent.putExtra("CHILD_UID", selectedChild.getUid());
        intent.putExtra("CHILD_EMAIL", selectedChild.getEmail());
        startActivity(intent);
    }
}
