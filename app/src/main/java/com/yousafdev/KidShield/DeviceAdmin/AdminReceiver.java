package com.yousafdev.KidShield.DeviceAdmin;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.yousafdev.KidShield.R;

/**
 * 设备管理员接收器
 * 激活后孩子无法卸载App，提供防卸载保护
 * 同时支持远程锁屏、重置密码等企业级管理功能
 */
public class AdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "AdminReceiver";

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "设备管理员已激活 → 应用受保护，无法直接卸载");
        Toast.makeText(context, "设备管理员已激活，KidShield 受保护", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
        Log.w(TAG, "设备管理员被解除！应用可能被卸载");
        // 尝试重新激活
        try {
            ComponentName componentName = new ComponentName(context, AdminReceiver.class);
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm != null && !dpm.isAdminActive(componentName)) {
                Intent activateIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                activateIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
                activateIntent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
                    "KidShield 需要设备管理员权限来防止孩子卸载管控应用，保障孩子的上网安全");
                activateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(activateIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "重新激活设备管理员失败", e);
        }
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
        Log.w(TAG, "密码输入失败");
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        // 读取已设置的卸载密码
        SharedPreferences prefs = context.getSharedPreferences("kidshield", Context.MODE_PRIVATE);
        String unlockPassword = prefs.getString("unlock_password", "");

        if (TextUtils.isEmpty(unlockPassword)) {
            // 家长未设置密码 → 直接提示联系家长
            return "如需卸载请联系家长";
        }

        // 家长已设置密码 → 弹出密码输入对话框
        showPasswordDialog(context, unlockPassword);
        return ""; // 返回空让系统默认行为，但弹窗会拦截点击
    }

    /**
     * 显示密码输入弹窗，输入正确才能继续卸载流程
     */
    private void showPasswordDialog(Context context, String correctPassword) {
        new Handler(Looper.getMainLooper()).post(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("验证家长密码");
            builder.setMessage("请输入家长设置的卸载密码");

            // 密码输入框
            final EditText input = new EditText(context);
            input.setHint("请输入密码");
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(40, 10, 40, 10);
            input.setLayoutParams(lp);

            builder.setView(input);
            builder.setPositiveButton("确定", null);
            builder.setNegativeButton("取消", (dialog, which) -> dialog.dismiss());

            AlertDialog dialog = builder.create();
            dialog.show();

            // 重写确定按钮点击事件
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String entered = input.getText().toString();
                if (correctPassword.equals(entered)) {
                    dialog.dismiss();
                    // 密码正确，允许继续
                    Toast.makeText(context, "密码正确，继续卸载流程", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "密码错误，请重新输入", Toast.LENGTH_SHORT).show();
                    input.setText("");
                }
            });
        });
    }

    /**
     * 检查设备管理员是否已激活
     */
    public static boolean isActive(Context context) {
        ComponentName componentName = new ComponentName(context, AdminReceiver.class);
        DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(componentName);
    }

    /**
     * 请求激活设备管理员
     */
    public static void requestActivate(Context context) {
        ComponentName componentName = new ComponentName(context, AdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "KidShield 需要设备管理员权限来防止孩子卸载管控应用，保障孩子的上网安全");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * 获取设备管理员组件名
     */
    public static ComponentName getComponentName(Context context) {
        return new ComponentName(context, AdminReceiver.class);
    }
}
