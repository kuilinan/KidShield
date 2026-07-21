package com.yousafdev.KidShield.Activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.yousafdev.KidShield.Models.Mission;
import com.yousafdev.KidShield.Network.ApiClient;
import com.yousafdev.KidShield.Network.CommandStore;
import com.yousafdev.KidShield.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChildDetailActivity extends AppCompatActivity implements LocationListener {

    private CommandStore commandStore;
    private ApiClient apiClient;
    private String childUid;
    private String childEmail;
    private TextView missionCountView;
    private TextView timeRequestCountView;
    private TextView textViewLocation;
    private TextView textViewLocationAddress;
    private TextView textViewLocationTime;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_detail);

        childUid = getIntent().getStringExtra("CHILD_UID");
        childEmail = getIntent().getStringExtra("CHILD_EMAIL");

        commandStore = new CommandStore(this);
        apiClient = new ApiClient();

        missionCountView = findViewById(R.id.textView_pending_missions);
        timeRequestCountView = findViewById(R.id.textView_pending_time_requests);
        textViewLocation = findViewById(R.id.textView_location);
        textViewLocationAddress = findViewById(R.id.textView_location_address);
        textViewLocationTime = findViewById(R.id.textView_location_time);

        loadPendingCounts();
        startLocationUpdates();

        // 管理应用按钮 → 跳转AppBlocker
        Button manageAppsBtn = findViewById(R.id.button_manage_apps);
        if (manageAppsBtn != null) {
            manageAppsBtn.setOnClickListener(v -> {
                Intent intent = new Intent(this, AppBlockerActivity.class);
                intent.putExtra("CHILD_UID", childUid);
                intent.putExtra("CHILD_EMAIL", childEmail);
                startActivity(intent);
            });
        }

        Button approveMissionsBtn = findViewById(R.id.button_mission_approval);
        if (approveMissionsBtn != null) {
            approveMissionsBtn.setOnClickListener(v -> showMissionApprovalDialog());
        }

        Button assignMissionBtn = findViewById(R.id.button_assign_mission);
        if (assignMissionBtn != null) {
            assignMissionBtn.setOnClickListener(v -> showAssignMissionDialog());
        }
    }

    /**
     * 使用标准Android LocationManager获取位置（非Google Maps）
     * GPS/基站/WiFi定位，所有中国设备通用
     */
    private void startLocationUpdates() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (locationManager == null) return;

        // 检查权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            textViewLocation.setText("位置权限未授予");
            return;
        }

        // 获取上次已知位置
        Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (lastKnown == null) {
            lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (lastKnown != null) {
            onLocationChanged(lastKnown);
        }

        // 请求位置更新
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 100, this);
        } catch (Exception e) {
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 100, this);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null) return;
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        textViewLocation.setText(String.format(Locale.CHINA, "📍 %.6f, %.6f", lat, lng));

        // 用Geocoder反查地址（可选，网络请求）
        try {
            android.location.Geocoder geocoder = new android.location.Geocoder(this, Locale.CHINA);
            List<android.location.Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address addr = addresses.get(0);
                String addrText = "";
                if (addr.getLocality() != null) addrText += addr.getLocality();
                if (addr.getSubLocality() != null) addrText += " " + addr.getSubLocality();
                if (addr.getFeatureName() != null) addrText += " " + addr.getFeatureName();
                if (!addrText.isEmpty()) textViewLocationAddress.setText(addrText);
            }
        } catch (Exception e) {
            // Geocoder可能不可用，忽略
        }

        // 更新时间
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.CHINA);
        textViewLocationTime.setText("更新于 " + sdf.format(new Date()));
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onProviderDisabled(String provider) {
        textViewLocation.setText("定位已关闭");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) {}
        }
    }

    private void loadPendingCounts() {
        try {
            JSONArray missions = commandStore.getMissions();
            int pendingCount = 0;
            for (int i = 0; i < missions.length(); i++) {
                JSONObject obj = missions.getJSONObject(i);
                if ("pending".equals(obj.optString("status"))) {
                    pendingCount++;
                }
            }
            missionCountView.setText("待审核: " + pendingCount);
            timeRequestCountView.setText("待审核: 0");
        } catch (Exception e) {
            missionCountView.setText("待审核: 0");
            timeRequestCountView.setText("待审核: 0");
        }
    }

    private void showMissionApprovalDialog() {
        try {
            JSONArray missions = commandStore.getMissions();
            List<Mission> pendingMissions = new ArrayList<>();

            for (int i = 0; i < missions.length(); i++) {
                JSONObject obj = missions.getJSONObject(i);
                if ("pending".equals(obj.optString("status"))) {
                    Mission m = new Mission();
                    m.id = obj.optString("id", "");
                    m.title = obj.optString("title", "");
                    m.description = obj.optString("description", "");
                    m.rewardMinutes = obj.optInt("rewardMinutes", obj.optInt("reward", 0));
                    m.status = "pending";
                    pendingMissions.add(m);
                }
            }

            if (pendingMissions.isEmpty()) {
                Toast.makeText(this, "暂无待审核的任务", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] items = new String[pendingMissions.size()];
            for (int i = 0; i < pendingMissions.size(); i++) {
                Mission m = pendingMissions.get(i);
                items[i] = m.title + " (" + m.description + ")";
            }

            boolean[] checked = new boolean[pendingMissions.size()];

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("审核任务");
            builder.setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                checked[which] = isChecked;
            });

            builder.setPositiveButton("通过选中", (dialog, which) -> {
                String token = getSharedPreferences("kidshield", MODE_PRIVATE).getString("token", "");
                for (int i = 0; i < pendingMissions.size(); i++) {
                    if (checked[i]) {
                        Mission m = pendingMissions.get(i);
                        approveMission(token, m.id);
                    }
                }
                loadPendingCounts();
                Toast.makeText(this, "操作完成", Toast.LENGTH_SHORT).show();
            });
            builder.setNegativeButton("取消", null);
            builder.show();
        } catch (Exception e) {
            Toast.makeText(this, "加载任务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void approveMission(String token, String missionId) {
        // 通过 API 审核任务
        new Thread(() -> {
            try {
                // 这里调用 API 审核任务
                // apiClient.approveMission(token, childUid, missionId);
                // 然后更新本地 CommandStore
                runOnUiThread(() -> Toast.makeText(ChildDetailActivity.this,
                    "任务已审核", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(ChildDetailActivity.this,
                    "审核失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showAssignMissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("布置新任务");

        View view = getLayoutInflater().inflate(R.layout.dialog_submit_mission, null);
        builder.setView(view);

        androidx.appcompat.widget.AppCompatEditText editTitle = view.findViewById(R.id.editText_mission_title);
        androidx.appcompat.widget.AppCompatEditText editDesc = view.findViewById(R.id.editText_mission_desc);
        androidx.appcompat.widget.AppCompatEditText editReward = view.findViewById(R.id.editText_mission_reward);

        builder.setPositiveButton("布置", (dialog, which) -> {
            String title = editTitle.getText().toString().trim();
            String desc = editDesc.getText().toString().trim();
            String rewardStr = editReward.getText().toString().trim();

            if (title.isEmpty()) {
                Toast.makeText(this, "请输入任务标题", Toast.LENGTH_SHORT).show();
                return;
            }

            int reward = 0;
            if (!rewardStr.isEmpty()) {
                try {
                    reward = Integer.parseInt(rewardStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "奖励时长请输入数字", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String missionId = "mission_" + System.currentTimeMillis();

            // 通过 API 布置任务
            String token = getSharedPreferences("kidshield", MODE_PRIVATE).getString("token", "");
            final int rewardFinal = reward;
            new Thread(() -> {
                try {
                    String result = apiClient.createMission(token, childUid, title, desc, rewardFinal);
                    commandStore.saveMissions(new JSONArray("[" + result + "]"));
                    runOnUiThread(() -> {
                        Toast.makeText(ChildDetailActivity.this, "任务已布置", Toast.LENGTH_SHORT).show();
                        loadPendingCounts();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        Toast.makeText(ChildDetailActivity.this, "布置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
