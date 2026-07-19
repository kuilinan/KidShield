package com.yousafdev.KidShield.Activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yousafdev.KidShield.Adapters.AppBlockerAdapter;
import com.yousafdev.KidShield.Models.AppInfo;
import com.yousafdev.KidShield.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppBlockerActivity extends AppCompatActivity implements AppBlockerAdapter.OnAppBlockListener {

    private RecyclerView recyclerView;
    private AppBlockerAdapter adapter;
    private List<AppInfo> fullAppList;
    private Map<String, Boolean> whitelistStatusMap;
    private ProgressBar progressBar;
    private EditText searchEditText;
    private TextView modeDescription;
    private DatabaseReference childRef;
    private String childUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_blocker);

        childUid = getIntent().getStringExtra("CHILD_UID");
        if (childUid == null) {
            Toast.makeText(this, "未找到孩子ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        childRef = FirebaseDatabase.getInstance().getReference("users").child(childUid);
        progressBar = findViewById(R.id.progressBar_apps);
        recyclerView = findViewById(R.id.recyclerView_apps);
        searchEditText = findViewById(R.id.editText_search);
        modeDescription = findViewById(R.id.textView_mode_desc);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        fullAppList = new ArrayList<>();
        whitelistStatusMap = new HashMap<>();

        modeDescription.setText("白名单模式：仅系统应用和您勾选的应用可供孩子使用");

        adapter = new AppBlockerAdapter(new ArrayList<>(), this);
        recyclerView.setAdapter(adapter);

        loadData();

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filter(String text) {
        List<AppInfo> filteredList = new ArrayList<>();
        for (AppInfo item : fullAppList) {
            if (item.appName.toLowerCase().contains(text.toLowerCase())) {
                filteredList.add(item);
            }
        }
        adapter.filterList(filteredList);
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);

        // 先加载白名单状态
        childRef.child("whitelist_apps").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                whitelistStatusMap.clear();
                for (DataSnapshot statusSnapshot : snapshot.getChildren()) {
                    String pkg = statusSnapshot.getKey().replace("_", ".");
                    Boolean val = statusSnapshot.getValue(Boolean.class);
                    whitelistStatusMap.put(pkg, val != null && val);
                }
                loadInstalledApps();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AppBlockerActivity.this, "加载状态失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadInstalledApps() {
        childRef.child("installed_apps").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                fullAppList.clear();
                for (DataSnapshot appSnapshot : snapshot.getChildren()) {
                    String appName = appSnapshot.child("appName").getValue(String.class);
                    String packageName = appSnapshot.child("packageName").getValue(String.class);
                    Boolean isSystem = appSnapshot.child("isSystemApp").getValue(Boolean.class);

                    if (appName != null && packageName != null) {
                        AppInfo app = new AppInfo(appName, packageName);
                        app.isSystemApp = isSystem != null && isSystem;
                        // 白名单模式：查看是否在白名单中
                        Boolean isAllowed = whitelistStatusMap.get(packageName);
                        app.isAllowed = app.isSystemApp || (isAllowed != null && isAllowed);
                        fullAppList.add(app);
                    }
                }
                adapter.filterList(fullAppList);
                progressBar.setVisibility(View.GONE);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AppBlockerActivity.this, "加载应用列表失败", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAppBlockChanged(String packageName, boolean isAllowed) {
        // 保存到 Firebase whitelist_apps 节点
        childRef.child("whitelist_apps").child(packageName.replace(".", "_")).setValue(isAllowed);

        // 更新内存中的列表
        for (AppInfo app : fullAppList) {
            if (app.packageName.equals(packageName)) {
                app.isAllowed = isAllowed;
                break;
            }
        }
    }
}
