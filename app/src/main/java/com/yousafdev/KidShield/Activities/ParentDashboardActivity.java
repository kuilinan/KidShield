package com.yousafdev.KidShield.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
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

        apiClient = new ApiClient();
        SharedPreferences prefs = getSharedPreferences("kidshield", MODE_PRIVATE);
        token = prefs.getString("token", "");
        apiClient.setToken(token);

        childList = new ArrayList<>();
        adapter = new ChildAdapter(childList, this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.setNestedScrollingEnabled(false);

        // 下拉刷新手动拉数据
        fabAddChild.setOnClickListener(v -> {
            Intent intent = new Intent(this, BindChildActivity.class);
            startActivity(intent);
        });

        fetchChildren();
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
                        // 尝试读取告警数
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
                    // 更新统计卡片
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

    /**
     * 更新统计卡片
     */
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
