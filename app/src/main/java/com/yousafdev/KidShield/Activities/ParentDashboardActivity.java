package com.yousafdev.KidShield.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private ApiClient apiClient;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_dashboard);

        recyclerView = findViewById(R.id.recyclerView_children);
        progressBar = findViewById(R.id.progressBar_dashboard);

        apiClient = new ApiClient();

        SharedPreferences prefs = getSharedPreferences("kidshield", MODE_PRIVATE);
        token = prefs.getString("token", "");

        childList = new ArrayList<>();
        adapter = new ChildAdapter(childList, this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

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
                String result = apiClient.getChildren(token);
                JSONArray childrenArr = new JSONArray(result);

                List<Child> tempList = new ArrayList<>();
                for (int i = 0; i < childrenArr.length(); i++) {
                    JSONObject childObj = childrenArr.getJSONObject(i);
                    String uid = childObj.optString("uid", "");
                    String email = childObj.optString("email", "");
                    String nickname = childObj.optString("nickname", "");
                    if (!uid.isEmpty()) {
                        tempList.add(new Child(uid, email, nickname));
                    }
                }

                runOnUiThread(() -> {
                    childList.clear();
                    childList.addAll(tempList);
                    adapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                    if (tempList.isEmpty()) {
                        Toast.makeText(ParentDashboardActivity.this, "还没有绑定的孩子", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(ParentDashboardActivity.this, "加载孩子列表失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                });
            }
        }).start();
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
