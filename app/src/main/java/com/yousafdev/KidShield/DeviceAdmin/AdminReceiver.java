package com.yousafdev.KidShield.DeviceAdmin;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
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
        // 返回提示信息，用户点击"停用"时会显示
        return "停用设备管理员后将无法保护孩子安全，确定要停用吗？";
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
