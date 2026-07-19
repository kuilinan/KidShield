package com.yousafdev.KidShield.Activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yousafdev.KidShield.Models.Mission;
import com.yousafdev.KidShield.R;
import com.yousafdev.KidShield.Utils.UsageTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChildDashboardActivity extends AppCompatActivity {
    private static final String TAG = "ChildDashboard";
    
    private TextView textViewBanner;
    private TextView textViewTotalUsage;
    private RecyclerView recyclerViewUsage;
    private RecyclerView recyclerViewMissions;
    private Button buttonSubmitMission;
    private Button buttonRequestUnlock;
    private Button buttonEnableUsage;
    private LinearLayout usagePermissionLayout;
    private LinearLayout usageStatsLayout;
    private ProgressBar progressBar;
    private TextView textViewNoMissions;
    private View bannerCard;
    private View locationWarning;
    private TextView textViewLocationStatus;

    private DatabaseReference mDatabase;
    private String currentUid;
    private UsageTracker usageTracker;
    private Handler handler;
    private Runnable usageUpdateRunnable;
    private MissionAdapter missionAdapter;
    private UsageAdapter usageAdapter;
    private List<Mission> missionList = new ArrayList<>();
    private List<UsageTracker.AppUsageInfo> usageList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_dashboard);

        mDatabase = FirebaseDatabase.getInstance().getReference();
        usageTracker = new UsageTracker(this);
        handler = new Handler(Looper.getMainLooper());
        
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }

        initViews();
        setupListeners();
        loadMissions();
        updateUsageStats();
        
        // 注册定位警告广播
        IntentFilter filter = new IntentFilter("com.yousafdev.KidShield.LOCATION_WARNING");
        registerReceiver(locationWarningReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void initViews() {
        // 温馨守护横幅
        bannerCard = findViewById(R.id.card_banner);
        textViewBanner = findViewById(R.id.textView_banner);
        textViewBanner.setText(R.string.parent_guardian_banner);
        
        // 定位状态
        locationWarning = findViewById(R.id.layout_location_warning);
        textViewLocationStatus = findViewById(R.id.textView_location_status);
        
        // 使用时间统计
        usagePermissionLayout = findViewById(R.id.layout_usage_permission);
        usageStatsLayout = findViewById(R.id.layout_usage_stats);
        textViewTotalUsage = findViewById(R.id.textView_total_usage);
        recyclerViewUsage = findViewById(R.id.recyclerView_usage);
        buttonEnableUsage = findViewById(R.id.button_enable_usage);
        
        usageAdapter = new UsageAdapter(usageList);
        recyclerViewUsage.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewUsage.setAdapter(usageAdapter);

        // 任务列表
        recyclerViewMissions = findViewById(R.id.recyclerView_missions);
        textViewNoMissions = findViewById(R.id.textView_no_missions);
        buttonSubmitMission = findViewById(R.id.button_submit_mission);
        buttonRequestUnlock = findViewById(R.id.button_request_unlock);
        progressBar = findViewById(R.id.progressBar);
        
        missionAdapter = new MissionAdapter(missionList, this);
        recyclerViewMissions.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMissions.setAdapter(missionAdapter);

        // 检查定位权限
        checkLocationStatus();
    }

    private void setupListeners() {
        buttonSubmitMission.setOnClickListener(v -> showSubmitMissionDialog());
        buttonRequestUnlock.setOnClickListener(v -> showRequestUnlockDialog());
        
        buttonEnableUsage.setOnClickListener(v -> {
            startActivity(UsageTracker.getUsageSettingsIntent());
        });

        // 定位警告点击
        if (locationWarning != null) {
            locationWarning.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            });
        }
    }

    private void checkLocationStatus() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isGpsEnabled = locationManager != null && 
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean hasFineLocation = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!isGpsEnabled || !hasFineLocation || !hasCoarseLocation) {
            if (locationWarning != null) {
                locationWarning.setVisibility(View.VISIBLE);
                textViewLocationStatus.setText(R.string.location_permission_warning);
            }
        } else {
            if (locationWarning != null) {
                locationWarning.setVisibility(View.GONE);
            }
        }
    }

    private void updateUsageStats() {
        // 检查是否有使用统计权限
        if (UsageTracker.hasUsageStatsPermission(this)) {
            usagePermissionLayout.setVisibility(View.GONE);
            usageStatsLayout.setVisibility(View.VISIBLE);
            
            // 实时更新（每5秒刷新）
            usageUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    refreshUsageData();
                    handler.postDelayed(this, 5000);
                }
            };
            handler.postDelayed(usageUpdateRunnable, 1000);
        } else {
            usagePermissionLayout.setVisibility(View.VISIBLE);
            usageStatsLayout.setVisibility(View.GONE);
        }
    }

    private void refreshUsageData() {
        try {
            // 总时间
            long totalUsage = usageTracker.getTodayTotalUsage();
            textViewTotalUsage.setText(String.format(Locale.CHINA, 
                    "今日已使用 %s", UsageTracker.formatDuration(totalUsage)));

            // 各应用时间
            usageList.clear();
            usageList.addAll(usageTracker.getAppUsageList());
            usageAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "刷新使用数据失败", e);
        }
    }

    private void loadMissions() {
        if (currentUid == null) return;
        
        progressBar.setVisibility(View.VISIBLE);
        
        mDatabase.child("missions").child(currentUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        missionList.clear();
                        for (DataSnapshot missionSnap : snapshot.getChildren()) {
                            Mission mission = missionSnap.getValue(Mission.class);
                            if (mission != null) {
                                mission.id = missionSnap.getKey();
                                missionList.add(mission);
                            }
                        }
                        missionAdapter.notifyDataSetChanged();
                        progressBar.setVisibility(View.GONE);
                        
                        if (missionList.isEmpty()) {
                            textViewNoMissions.setVisibility(View.VISIBLE);
                            recyclerViewMissions.setVisibility(View.GONE);
                        } else {
                            textViewNoMissions.setVisibility(View.GONE);
                            recyclerViewMissions.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        Log.e(TAG, "加载任务失败", error.toException());
                    }
                });
    }

    private void showSubmitMissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.new_mission);

        View view = getLayoutInflater().inflate(R.layout.dialog_submit_mission, null);
        builder.setView(view);

        androidx.appcompat.widget.AppCompatEditText editTitle = view.findViewById(R.id.editText_mission_title);
        androidx.appcompat.widget.AppCompatEditText editDesc = view.findViewById(R.id.editText_mission_desc);
        androidx.appcompat.widget.AppCompatEditText editReward = view.findViewById(R.id.editText_mission_reward);

        builder.setPositiveButton(R.string.submit, null);
        builder.setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
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

            String missionId = mDatabase.child("missions").child(currentUid).push().getKey();
            if (missionId == null) return;

            Mission mission = new Mission(missionId, title, desc, reward, "child_submit");
            mDatabase.child("missions").child(currentUid).child(missionId)
                    .setValue(mission)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, R.string.mission_submitted, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "提交失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }

    private void showRequestUnlockDialog() {
        Intent intent = new Intent(this, BlockedScreenActivity.class);
        intent.putExtra("from_dashboard", true);
        startActivity(intent);
    }

    private final BroadcastReceiver locationWarningReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.yousafdev.KidShield.LOCATION_WARNING".equals(intent.getAction())) {
                checkLocationStatus();
                Toast.makeText(ChildDashboardActivity.this, 
                        R.string.location_disabled_alert, Toast.LENGTH_LONG).show();
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        checkLocationStatus();
        refreshUsageData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && usageUpdateRunnable != null) {
            handler.removeCallbacks(usageUpdateRunnable);
        }
        try {
            unregisterReceiver(locationWarningReceiver);
        } catch (Exception e) {}
    }

    // ======== 内部适配器 ========
    
    private static class MissionAdapter extends RecyclerView.Adapter<MissionAdapter.ViewHolder> {
        private List<Mission> missions;
        private Context context;

        MissionAdapter(List<Mission> missions, Context context) {
            this.missions = missions;
            this.context = context;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_mission, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Mission mission = missions.get(position);
            holder.titleView.setText(mission.title);
            if (mission.description != null && !mission.description.isEmpty()) {
                holder.descView.setText(mission.description);
                holder.descView.setVisibility(View.VISIBLE);
            } else {
                holder.descView.setVisibility(View.GONE);
            }
            
            String typeLabel = "child_submit".equals(mission.type) ? "📝 我提交的" : "📋 家长布置";
            String statusText;
            int statusColor;
            switch (mission.status) {
                case "approved":
                    statusText = "✅ 已通过 (+" + mission.rewardMinutes + "分钟)";
                    statusColor = 0xFF4CAF50;
                    break;
                case "rejected":
                    statusText = "❌ 已驳回";
                    statusColor = 0xFFF44336;
                    break;
                default:
                    statusText = "⏳ " + typeLabel + "·待审核";
                    statusColor = 0xFFFF9800;
                    break;
            }
            holder.statusView.setText(statusText);
            holder.statusView.setTextColor(statusColor);
            
            holder.rewardView.setText("奖励: " + mission.rewardMinutes + "分钟");
        }

        @Override
        public int getItemCount() {
            return missions.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView titleView, descView, statusView, rewardView;

            ViewHolder(android.view.View itemView) {
                super(itemView);
                titleView = itemView.findViewById(R.id.textView_mission_title);
                descView = itemView.findViewById(R.id.textView_mission_desc);
                statusView = itemView.findViewById(R.id.textView_mission_status);
                rewardView = itemView.findViewById(R.id.textView_mission_reward);
            }
        }
    }

    private static class UsageAdapter extends RecyclerView.Adapter<UsageAdapter.ViewHolder> {
        private List<UsageTracker.AppUsageInfo> usageList;

        UsageAdapter(List<UsageTracker.AppUsageInfo> usageList) {
            this.usageList = usageList;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app_usage, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UsageTracker.AppUsageInfo info = usageList.get(position);
            holder.appNameView.setText(info.appName);
            holder.usageTimeView.setText(info.formattedTime);
        }

        @Override
        public int getItemCount() {
            return usageList.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView appNameView, usageTimeView;

            ViewHolder(android.view.View itemView) {
                super(itemView);
                appNameView = itemView.findViewById(R.id.textView_app_name);
                usageTimeView = itemView.findViewById(R.id.textView_usage_time);
            }
        }
    }
}
