package com.yousafdev.KidShield.Activities;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yousafdev.KidShield.R;
import com.yousafdev.KidShield.Utils.ActivityBlockerManager;
import com.yousafdev.KidShield.Utils.ActivityRecordManager;
import com.yousafdev.KidShield.Utils.LearningModeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity 学习模式 + Activity级拦截 + URL黑名单 管理界面
 * 家长端功能：开启学习模式采集数据 → 查看App使用排行 → 精细到Activity的管控
 */
public class LearningModeActivity extends AppCompatActivity {

    private LearningModeManager learningModeManager;
    private ActivityRecordManager recordManager;
    private ActivityBlockerManager blockerManager;

    private Switch switchLearning;
    private TextView tvRemaining;
    private RecyclerView rvAppSummary;
    private AppSummaryAdapter adapter;
    private Handler handler = new Handler();
    private Runnable updateRemainingTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learning_mode);

        learningModeManager = new LearningModeManager(this);
        recordManager = new ActivityRecordManager(this);
        blockerManager = new ActivityBlockerManager(this);

        initViews();
        updateRemainingTime();

        // 每30秒更新一次剩余时间
        updateRemainingTask = new Runnable() {
            @Override
            public void run() {
                updateRemainingTime();
                handler.postDelayed(this, 30000);
            }
        };
        handler.postDelayed(updateRemainingTask, 30000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updateRemainingTask != null) {
            handler.removeCallbacks(updateRemainingTask);
        }
    }

    private void initViews() {
        switchLearning = findViewById(R.id.switch_learning_mode);
        tvRemaining = findViewById(R.id.tv_remaining_time);
        rvAppSummary = findViewById(R.id.rv_app_summary);

        switchLearning.setChecked(learningModeManager.isLearningMode());

        switchLearning.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                learningModeManager.enableLearningMode(24);
                Toast.makeText(this, "学习模式已开启（持续24小时）", Toast.LENGTH_SHORT).show();
            } else {
                learningModeManager.disableLearningMode();
                Toast.makeText(this, "学习模式已关闭", Toast.LENGTH_SHORT).show();
            }
            updateRemainingTime();
        });

        rvAppSummary.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppSummaryAdapter(this, recordManager, blockerManager, learningModeManager);
        rvAppSummary.setAdapter(adapter);

        findViewById(R.id.btn_clear_data).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("清空所有Activity采集记录？此操作不可恢复")
                .setPositiveButton("确定", (dialog, which) -> {
                    recordManager.clearAll();
                    loadData();
                    Toast.makeText(this, "数据已清空", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
        });

        findViewById(R.id.btn_url_blacklist).setOnClickListener(v -> {
            showUrlBlacklistDialog();
        });
    }

    private void updateRemainingTime() {
        if (learningModeManager.isLearningMode()) {
            long remaining = learningModeManager.getRemainingTime();
            if (remaining > 0) {
                long hours = remaining / 3600_000;
                long minutes = (remaining % 3600_000) / 60_000;
                tvRemaining.setText("剩余学习时间: " + hours + "h " + minutes + "m");
            } else {
                tvRemaining.setText("学习模式即将过期");
            }
        } else {
            tvRemaining.setText("学习模式未开启");
        }
    }

    private void loadData() {
        List<ActivityRecordManager.AppUsageSummary> summary = recordManager.getAppSummary();
        adapter.updateData(summary);
    }

    // ==================== URL 黑名单管理弹窗 ====================

    private void showUrlBlacklistDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("URL 黑名单管理");

        View view = getLayoutInflater().inflate(R.layout.dialog_url_blacklist, null);
        builder.setView(view);

        EditText etUrl = view.findViewById(R.id.et_new_url);
        Button btnAdd = view.findViewById(R.id.btn_add_url);
        RecyclerView rvUrls = view.findViewById(R.id.rv_blocked_urls);
        rvUrls.setLayoutManager(new LinearLayoutManager(this));

        UrlAdapter urlAdapter = new UrlAdapter(this, blockerManager);
        rvUrls.setAdapter(urlAdapter);
        urlAdapter.loadData();

        btnAdd.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(this, "请输入网址", Toast.LENGTH_SHORT).show();
                return;
            }
            blockerManager.addBlockedUrl(url);
            etUrl.setText("");
            urlAdapter.loadData();
            Toast.makeText(this, "已添加: " + url, Toast.LENGTH_SHORT).show();
        });

        builder.setPositiveButton("完成", null);
        builder.show();
    }

    // ==================== App 排行列表适配器 ====================

    private static class AppSummaryAdapter extends RecyclerView.Adapter<AppSummaryAdapter.ViewHolder> {
        private final AppCompatActivity activity;
        private final ActivityRecordManager recordManager;
        private final ActivityBlockerManager blockerManager;
        private final LearningModeManager learningModeManager;
        private List<ActivityRecordManager.AppUsageSummary> items = new ArrayList<>();

        AppSummaryAdapter(AppCompatActivity activity, ActivityRecordManager rm,
                          ActivityBlockerManager bm, LearningModeManager lm) {
            this.activity = activity;
            this.recordManager = rm;
            this.blockerManager = bm;
            this.learningModeManager = lm;
        }

        void updateData(List<ActivityRecordManager.AppUsageSummary> list) {
            this.items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ActivityRecordManager.AppUsageSummary item = items.get(position);
            holder.text1.setText(item.appName + " (" + item.packageName + ")");
            holder.text2.setText("访问 " + item.visitCount + " 次 · 共 " + item.getFormattedDuration());

            // 点击查看Activity详情 + 设置拦截
            holder.itemView.setOnClickListener(v -> {
                if (!learningModeManager.isLearningMode()) {
                    Toast.makeText(activity, "请先开启学习模式采集数据", Toast.LENGTH_SHORT).show();
                    return;
                }
                showActivityDetailDialog(item.packageName, item.appName);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private void showActivityDetailDialog(String pkg, String appName) {
            List<ActivityRecordManager.ActivityDetail> activities = recordManager.getActivityDetail(pkg);
            List<ActivityBlockerManager.ActivityBlockRule> existingRules = blockerManager.getRulesForApp(pkg);

            if (activities.isEmpty()) {
                Toast.makeText(activity, "暂无Activity记录", Toast.LENGTH_SHORT).show();
                return;
            }

            // 构建选项列表
            String[] itemNames = new String[activities.size()];
            boolean[] checkedItems = new boolean[activities.size()];

            for (int i = 0; i < activities.size(); i++) {
                ActivityRecordManager.ActivityDetail detail = activities.get(i);
                String simpleName = detail.getSimpleName();
                boolean isBlocked = false;
                for (ActivityBlockerManager.ActivityBlockRule rule : existingRules) {
                    if (detail.className.contains(rule.activityClassName)) {
                        isBlocked = true;
                        break;
                    }
                }
                String status = isBlocked ? "🔴 已禁止" : "🟢 允许";
                itemNames[i] = status + " " + simpleName + " (" + detail.visitCount + "次 · " + detail.getFormattedDuration() + ")";
                checkedItems[i] = isBlocked;
            }

            new AlertDialog.Builder(activity)
                .setTitle(appName + " 功能列表")
                .setMultiChoiceItems(itemNames, checkedItems, (dialog, which, isChecked) -> {
                    ActivityRecordManager.ActivityDetail detail = activities.get(which);
                    if (isChecked) {
                        blockerManager.addActivityRule(
                            pkg, detail.className, appName, "家长已禁止此功能");
                        Toast.makeText(activity, detail.getSimpleName() + " 已禁止", Toast.LENGTH_SHORT).show();
                    } else {
                        blockerManager.removeActivityRule(pkg, detail.className);
                        Toast.makeText(activity, detail.getSimpleName() + " 已允许", Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton("完成", null)
                .show();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView text1;
            final TextView text2;

            ViewHolder(View view) {
                super(view);
                text1 = view.findViewById(android.R.id.text1);
                text2 = view.findViewById(android.R.id.text2);
            }
        }
    }

    // ==================== URL 列表适配器 ====================

    private static class UrlAdapter extends RecyclerView.Adapter<UrlAdapter.UrlViewHolder> {
        private final AppCompatActivity activity;
        private final ActivityBlockerManager blockerManager;
        private List<String> urls = new ArrayList<>();

        UrlAdapter(AppCompatActivity activity, ActivityBlockerManager blockerManager) {
            this.activity = activity;
            this.blockerManager = blockerManager;
        }

        void loadData() {
            this.urls = blockerManager.getBlockedUrls();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public UrlViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new UrlViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull UrlViewHolder holder, int position) {
            String url = urls.get(position);
            holder.textView.setText(url);
            holder.textView.setOnLongClickListener(v -> {
                new AlertDialog.Builder(activity)
                    .setTitle("移除此URL?")
                    .setMessage(url)
                    .setPositiveButton("移除", (dialog, which) -> {
                        blockerManager.removeBlockedUrl(url);
                        loadData();
                        Toast.makeText(activity, "已移除: " + url, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        static class UrlViewHolder extends RecyclerView.ViewHolder {
            final TextView textView;

            UrlViewHolder(View view) {
                super(view);
                textView = view.findViewById(android.R.id.text1);
            }
        }
    }
}
