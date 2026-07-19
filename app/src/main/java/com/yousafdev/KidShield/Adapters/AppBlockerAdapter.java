package com.yousafdev.KidShield.Adapters;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.yousafdev.KidShield.Models.AppInfo;
import com.yousafdev.KidShield.R;

import java.util.ArrayList;
import java.util.List;

public class AppBlockerAdapter extends RecyclerView.Adapter<AppBlockerAdapter.ViewHolder> {
    private List<AppInfo> appList;
    private OnAppBlockListener listener;

    public AppBlockerAdapter(List<AppInfo> appList, OnAppBlockListener listener) {
        this.appList = appList;
        this.listener = listener;
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = appList.get(position);
        holder.appName.setText(app.appName);

        // 白名单模式：开关 ON = 允许（whitelisted），OFF = 禁止
        holder.blockSwitch.setOnCheckedChangeListener(null);
        holder.blockSwitch.setChecked(app.isAllowed);

        // 设置提示文字
        if (app.isSystemApp) {
            holder.systemLabel.setVisibility(View.VISIBLE);
        } else {
            holder.systemLabel.setVisibility(View.GONE);
        }

        holder.blockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                app.isAllowed = isChecked;
                listener.onAppBlockChanged(app.packageName, isChecked);
            }
        });
    }

    @Override public int getItemCount() { return appList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView appName;
        TextView systemLabel;
        SwitchMaterial blockSwitch;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.textView_app_name);
            systemLabel = itemView.findViewById(R.id.textView_system_label);
            blockSwitch = itemView.findViewById(R.id.switch_block_app);
        }
    }

    public interface OnAppBlockListener {
        void onAppBlockChanged(String packageName, boolean isAllowed);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void filterList(List<AppInfo> filteredList) {
        appList = filteredList;
        notifyDataSetChanged();
    }
}
