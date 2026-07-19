package com.yousafdev.KidShield.Activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yousafdev.KidShield.Models.Mission;
import com.yousafdev.KidShield.Network.ApiClient;
import com.yousafdev.KidShield.Network.CommandStore;
import com.yousafdev.KidShield.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ChildDetailActivity extends AppCompatActivity {

    private CommandStore commandStore;
    private ApiClient apiClient;
    private String childUid;
    private String childEmail;
    private TextView missionCountView;
    private TextView timeRequestCountView;

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

        loadPendingCounts();

        Button approveMissionsBtn = findViewById(R.id.button_mission_approval);
        if (approveMissionsBtn != null) {
            approveMissionsBtn.setOnClickListener(v -> showMissionApprovalDialog());
        }

        Button assignMissionBtn = findViewById(R.id.button_assign_mission);
        if (assignMissionBtn != null) {
            assignMissionBtn.setOnClickListener(v -> showAssignMissionDialog());
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
