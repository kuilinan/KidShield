package com.yousafdev.KidShield.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.yousafdev.KidShield.Adapters.CallLogAdapter;
import com.yousafdev.KidShield.Adapters.SmsLogAdapter;
import com.yousafdev.KidShield.Models.CallLogEntry;
import com.yousafdev.KidShield.Models.Mission;
import com.yousafdev.KidShield.Models.SmsLogEntry;
import com.yousafdev.KidShield.Models.TimeRequest;
import com.yousafdev.KidShield.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChildDetailActivity extends AppCompatActivity implements OnMapReadyCallback {
    private MapView mapView;
    private GoogleMap googleMap;
    private String childUid;
    private RecyclerView callLogRecyclerView, smsLogRecyclerView;
    private CallLogAdapter callLogAdapter;
    private SmsLogAdapter smsLogAdapter;
    private List<CallLogEntry> callLogList;
    private List<SmsLogEntry> smsLogList;
    private ProgressBar progressBar;
    private Button manageAppsButton;
    private Button missionApprovalButton;
    private Button timeRequestButton;
    private TextView missionCountView;
    private TextView timeRequestCountView;
    private Button assignMissionButton;
    private TextView textViewUsage;
    private android.widget.LinearLayout usageSection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_child_detail);

        childUid = getIntent().getStringExtra("CHILD_UID");
        String childEmail = getIntent().getStringExtra("CHILD_EMAIL");
        TextView title = findViewById(R.id.textView_child_detail_title);
        title.setText(childEmail != null ? childEmail : "孩子详情");

        progressBar = findViewById(R.id.progressBar_details);

        // 地图
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // 按钮
        manageAppsButton = findViewById(R.id.button_manage_apps);
        missionApprovalButton = findViewById(R.id.button_mission_approval);
        timeRequestButton = findViewById(R.id.button_time_requests);
        assignMissionButton = findViewById(R.id.button_assign_mission);
        assignMissionButton.setOnClickListener(v -> showAssignMissionDialog());
        missionCountView = findViewById(R.id.textView_pending_missions);
        timeRequestCountView = findViewById(R.id.textView_pending_time_requests);

        manageAppsButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, AppBlockerActivity.class);
            intent.putExtra("CHILD_UID", childUid);
            startActivity(intent);
        });

        missionApprovalButton.setOnClickListener(v -> showMissionApprovalDialog());
        timeRequestButton.setOnClickListener(v -> showTimeRequestApprovalDialog());

        // RecyclerViews
        setupRecyclerViews();
        if (childUid != null) {
            listenForDataChanges();
            loadPendingCounts();
        }
    }

    private void setupRecyclerViews() {
        callLogRecyclerView = findViewById(R.id.recyclerView_call_logs);
        callLogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        callLogList = new ArrayList<>();
        callLogAdapter = new CallLogAdapter(callLogList);
        callLogRecyclerView.setAdapter(callLogAdapter);

        smsLogRecyclerView = findViewById(R.id.recyclerView_sms_logs);
        smsLogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        smsLogList = new ArrayList<>();
        smsLogAdapter = new SmsLogAdapter(smsLogList);
        smsLogRecyclerView.setAdapter(smsLogAdapter);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
    }

    private void loadPendingCounts() {
        // 待审核任务数量
        FirebaseDatabase.getInstance().getReference("missions").child(childUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int pending = 0;
                        for (DataSnapshot s : snapshot.getChildren()) {
                            String status = s.child("status").getValue(String.class);
                            if ("pending".equals(status)) pending++;
                        }
                        missionCountView.setText("待审核: " + pending);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        // 待审核加时长申请数量
        FirebaseDatabase.getInstance().getReference("time_requests").child(childUid)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int pending = 0;
                        for (DataSnapshot s : snapshot.getChildren()) {
                            String status = s.child("status").getValue(String.class);
                            if ("pending".equals(status)) pending++;
                        }
                        timeRequestCountView.setText("待审核: " + pending);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void showMissionApprovalDialog() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("missions").child(childUid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Mission> pendingMissions = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    Mission m = s.getValue(Mission.class);
                    if (m != null && "pending".equals(m.status)) {
                        m.id = s.getKey();
                        pendingMissions.add(m);
                    }
                }

                if (pendingMissions.isEmpty()) {
                    Toast.makeText(ChildDetailActivity.this, "暂无待审核的任务", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] items = new String[pendingMissions.size()];
                for (int i = 0; i < pendingMissions.size(); i++) {
                    Mission m = pendingMissions.get(i);
                    items[i] = m.title + " (" + m.description + ")";
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(ChildDetailActivity.this);
                builder.setTitle("待审核任务")
                        .setItems(items, (dialog, which) -> {
                            Mission selected = pendingMissions.get(which);
                            showMissionActionDialog(selected);
                        })
                        .show();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showMissionActionDialog(Mission mission) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("审核任务: " + mission.title)
                .setMessage("描述: " + mission.description + "\n\n通过后是否奖励时长（分钟）？未填写则默认为0");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        android.widget.EditText rewardInput = new android.widget.EditText(this);
        rewardInput.setHint("奖励时长（分钟）");
        rewardInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(rewardInput);
        builder.setView(layout);

        builder.setPositiveButton("✅ 通过", (dialog, which) -> {
            String rewardStr = rewardInput.getText().toString().trim();
            int reward = rewardStr.isEmpty() ? 0 : Integer.parseInt(rewardStr);
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("missions")
                    .child(childUid).child(mission.id);
            ref.child("status").setValue("approved");
            ref.child("rewardMinutes").setValue(reward);
            Toast.makeText(this, "✅ 任务已通过，奖励 " + reward + " 分钟", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("❌ 驳回", (dialog, which) -> {
            FirebaseDatabase.getInstance().getReference("missions")
                    .child(childUid).child(mission.id).child("status").setValue("rejected");
            Toast.makeText(this, "❌ 任务已驳回", Toast.LENGTH_SHORT).show();
        });
        builder.setNeutralButton("取消", null);
        builder.show();
    }

    private void showTimeRequestApprovalDialog() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("time_requests").child(childUid);
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<TimeRequest> pendingRequests = new ArrayList<>();
                for (DataSnapshot s : snapshot.getChildren()) {
                    TimeRequest r = s.getValue(TimeRequest.class);
                    if (r != null && "pending".equals(r.status)) {
                        r.id = s.getKey();
                        pendingRequests.add(r);
                    }
                }

                if (pendingRequests.isEmpty()) {
                    Toast.makeText(ChildDetailActivity.this, "暂无待审核的加时长申请", Toast.LENGTH_SHORT).show();
                    return;
                }

                String[] items = new String[pendingRequests.size()];
                for (int i = 0; i < pendingRequests.size(); i++) {
                    TimeRequest r = pendingRequests.get(i);
                    items[i] = r.appName + " (+" + r.requestedMinutes + "分钟)";
                    if (r.reason != null && !r.reason.isEmpty()) {
                        items[i] += "\n理由: " + r.reason;
                    }
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(ChildDetailActivity.this);
                builder.setTitle("待审核加时长申请")
                        .setItems(items, (dialog, which) -> {
                            TimeRequest selected = pendingRequests.get(which);
                            showTimeRequestActionDialog(selected);
                        })
                        .show();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showTimeRequestActionDialog(TimeRequest request) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("加时长申请")
                .setMessage("应用: " + request.appName + "\n时长: " + request.requestedMinutes + "分钟\n理由: " +
                        (request.reason != null ? request.reason : "无"));

        builder.setPositiveButton("✅ 通过", (dialog, which) -> {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("time_requests")
                    .child(childUid).child(request.id);
            ref.child("status").setValue("approved");

            // 将额外时长写入白名单临时放行节点
            if (request.packageName != null) {
                long extraTime = (long) request.requestedMinutes * 60 * 1000 + System.currentTimeMillis();
                FirebaseDatabase.getInstance().getReference("users").child(childUid)
                        .child("temporary_allowed").child(request.packageName.replace(".", "_"))
                        .setValue(extraTime);
            }
            Toast.makeText(this, "✅ 加时长申请已通过", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("❌ 驳回", (dialog, which) -> {
            FirebaseDatabase.getInstance().getReference("time_requests")
                    .child(childUid).child(request.id).child("status").setValue("rejected");
            Toast.makeText(this, "❌ 申请已驳回", Toast.LENGTH_SHORT).show();
        });
        builder.setNeutralButton("取消", null);
        builder.show();
    }

    private void listenForDataChanges() {
        DatabaseReference childDataRef = FirebaseDatabase.getInstance().getReference("users").child(childUid).child("data");
        childDataRef.child("location").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && googleMap != null) {
                    Double lat = snapshot.child("latitude").getValue(Double.class);
                    Double lon = snapshot.child("longitude").getValue(Double.class);
                    if (lat != null && lon != null) {
                        LatLng childLocation = new LatLng(lat, lon);
                        googleMap.clear();
                        googleMap.addMarker(new MarkerOptions().position(childLocation).title("最后已知位置"));
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(childLocation, 15f));
                    }
                }
                progressBar.setVisibility(View.GONE);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        childDataRef.child("call_logs").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                callLogList.clear();
                for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                    CallLogEntry entry = logSnapshot.getValue(CallLogEntry.class);
                    if (entry != null) callLogList.add(entry);
                }
                Collections.reverse(callLogList);
                callLogAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        childDataRef.child("sms_logs").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                smsLogList.clear();
                for (DataSnapshot logSnapshot : snapshot.getChildren()) {
                    SmsLogEntry entry = logSnapshot.getValue(SmsLogEntry.class);
                    if (entry != null) smsLogList.add(entry);
                }
                Collections.reverse(smsLogList);
                smsLogAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onStop() { super.onStop(); mapView.onStop(); }
    @Override protected void onPause() { super.onPause(); mapView.onPause(); }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }

    private void showAssignMissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.assign_new_mission);

        View view = getLayoutInflater().inflate(R.layout.dialog_submit_mission, null);
        builder.setView(view);

        androidx.appcompat.widget.AppCompatEditText editTitle = view.findViewById(R.id.editText_mission_title);
        androidx.appcompat.widget.AppCompatEditText editDesc = view.findViewById(R.id.editText_mission_desc);
        androidx.appcompat.widget.AppCompatEditText editReward = view.findViewById(R.id.editText_mission_reward);

        builder.setPositiveButton(R.string.submit, (dialog, which) -> {
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

            String missionId = FirebaseDatabase.getInstance().getReference("missions")
                    .child(childUid).push().getKey();
            if (missionId == null) return;

            Mission mission = new Mission(missionId, title, desc, reward, "parent_assign");
            FirebaseDatabase.getInstance().getReference("missions")
                    .child(childUid).child(missionId)
                    .setValue(mission)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "任务已布置给孩子", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "布置失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
}
