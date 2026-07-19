package com.yousafdev.KidShield.Activities;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yousafdev.KidShield.Adapters.AppBlockerAdapter;
import com.yousafdev.KidShield.Models.AppInfo;
import com.yousafdev.KidShield.Network.CommandStore;
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
    private CommandStore commandStore;
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

        commandStore = new CommandStore(this);
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

        // 从本地 CommandStore 读取白名单状态
        List<Map<String, String>> storedWhitelist = commandStore.getWhitelistApps();
        whitelistStatusMap.clear();
        for (Map<String, String> app : storedWhitelist) {
            String pkg = app.get("package_name");
            if (pkg != null) {
                whitelistStatusMap.put(pkg, true);
            }
        }

        loadInstalledApps();
    }

    private void loadInstalledApps() {
        fullAppList.clear();

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : installedApps) {
            String packageName = appInfo.packageName;
            String appName = pm.getApplicationLabel(appInfo).toString();
            boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

            AppInfo app = new AppInfo(appName, packageName);
            app.isSystemApp = isSystem;

            Boolean isAllowed = whitelistStatusMap.get(packageName);
            app.isAllowed = isSystem || (isAllowed != null && isAllowed);

            fullAppList.add(app);
        }

        adapter.filterList(fullAppList);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onAppBlockChanged(String packageName, boolean isAllowed) {
        // 更新内存中的列表
        for (AppInfo app : fullAppList) {
            if (app.packageName.equals(packageName)) {
                app.isAllowed = isAllowed;
                break;
            }
        }

        // 保存到本地 CommandStore
        List<Map<String, String>> currentWhitelist = commandStore.getWhitelistApps();
        boolean found = false;
        for (Map<String, String> entry : currentWhitelist) {
            if (packageName.equals(entry.get("package_name"))) {
                if (!isAllowed) {
                    // 移除
                    currentWhitelist.remove(entry);
                }
                found = true;
                break;
            }
        }
        if (isAllowed && !found) {
            Map<String, String> newEntry = new HashMap<>();
            newEntry.put("package_name", packageName);
            newEntry.put("app_name", "");
            currentWhitelist.add(newEntry);
        }
        commandStore.saveWhitelistApps(currentWhitelist);

        // 发送广播让 AppAccessibilityService 重新加载
        android.content.Intent updateIntent = new android.content.Intent("com.yousafdev.KidShield.UPDATE_WHITELIST");
        sendBroadcast(updateIntent);
    }
}
