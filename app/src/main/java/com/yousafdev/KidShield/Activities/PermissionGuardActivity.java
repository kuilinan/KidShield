package com.yousafdev.KidShield.Activities;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yousafdev.KidShield.DeviceAdmin.AdminReceiver;
import com.yousafdev.KidShield.R;
import com.yousafdev.KidShield.Services.AppAccessibilityService;

import java.util.List;

/**
 * 增强版权限安全检查引导页
 * 
 * 覆盖安装后自动检测关键安全权限是否丢失，引导用户重新激活：
 * 1. 设备管理员（防卸载核心）
 * 2. 无障碍服务（拦截卸载页面核心）
 * 3. 悬浮窗权限（防卸载覆盖层）
 * 
 * 全部通过后才跳转到目标界面，确保防卸载保护始终在线。
 */
public class PermissionGuardActivity extends AppCompatActivity {

    private static final String PREF_NAME = "kidshield";
    private static final String KEY_TARGET_ROLE = "guard_target_role";

    private DevicePolicyManager dpm;
    private ComponentName compName;

    private ImageView iconDeviceAdmin, iconAccessibility, iconOverlay;
    private TextView statusDeviceAdmin, statusAccessibility, statusOverlay;
    private Button btnDeviceAdmin, btnAccessibility, btnOverlay, btnContinue;
    private View cardDeviceAdmin, cardAccessibility, cardOverlay;

    private boolean deviceAdminOk = false;
    private boolean accessibilityOk = false;
    private boolean overlayOk = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guard);

        dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        compName = new ComponentName(this, AdminReceiver.class);

        // 初始化视图
        iconDeviceAdmin = findViewById(R.id.guard_icon_device_admin);
        statusDeviceAdmin = findViewById(R.id.guard_status_device_admin);
        btnDeviceAdmin = findViewById(R.id.guard_btn_device_admin);
        cardDeviceAdmin = findViewById(R.id.guard_card_device_admin);

        iconAccessibility = findViewById(R.id.guard_icon_accessibility);
        statusAccessibility = findViewById(R.id.guard_status_accessibility);
        btnAccessibility = findViewById(R.id.guard_btn_accessibility);
        cardAccessibility = findViewById(R.id.guard_card_accessibility);

        iconOverlay = findViewById(R.id.guard_icon_overlay);
        statusOverlay = findViewById(R.id.guard_status_overlay);
        btnOverlay = findViewById(R.id.guard_btn_overlay);
        cardOverlay = findViewById(R.id.guard_card_overlay);

        btnContinue = findViewById(R.id.guard_btn_continue);

        // 设置按钮点击事件
        btnDeviceAdmin.setOnClickListener(v -> requestDeviceAdmin());
        btnAccessibility.setOnClickListener(v -> requestAccessibilityService());
        btnOverlay.setOnClickListener(v -> requestOverlayPermission());
        btnContinue.setOnClickListener(v -> proceedToTarget());

        // 初始检查
        checkAllPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAllPermissions();
    }

    /**
     * 标记需要跳转的目标角色
     */
    public static void setTargetRole(Context context, String role) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TARGET_ROLE, role)
            .apply();
    }

    private void checkAllPermissions() {
        // 检查设备管理员
        deviceAdminOk = dpm.isAdminActive(compName);
        updateCardStatus(cardDeviceAdmin, iconDeviceAdmin, statusDeviceAdmin, btnDeviceAdmin,
                deviceAdminOk, "设备管理员", "✓ 已激活，防卸载保护正常",
                "未激活！无法阻止直接卸载应用");

        // 检查无障碍服务
        accessibilityOk = isAccessibilityServiceEnabled(this);
        updateCardStatus(cardAccessibility, iconAccessibility, statusAccessibility, btnAccessibility,
                accessibilityOk, "无障碍服务", "✓ 已开启，可拦截卸载页面",
                "未开启！无法拦截卸载操作");

        // 检查悬浮窗权限
        overlayOk = Settings.canDrawOverlays(this);
        updateCardStatus(cardOverlay, iconOverlay, statusOverlay, btnOverlay,
                overlayOk, "悬浮窗权限", "✓ 已授权，可显示防卸载覆盖层",
                "未授权！无法显示防卸载覆盖层");

        // 按钮自动检查
        btnContinue.setEnabled(deviceAdminOk && accessibilityOk && overlayOk);
        if (btnContinue.isEnabled()) {
            btnContinue.setText("✅ 全部就绪，继续使用");
            btnContinue.setAlpha(1.0f);
        } else {
            int missing = 0;
            if (!deviceAdminOk) missing++;
            if (!accessibilityOk) missing++;
            if (!overlayOk) missing++;
            btnContinue.setText("⚠ 还有 " + missing + " 项权限未开启");
            btnContinue.setAlpha(0.6f);
        }
    }

    private void updateCardStatus(View card, ImageView icon, TextView status, Button btn,
                                   boolean ok, String title, String okText, String failText) {
        if (ok) {
            card.setBackgroundResource(R.drawable.card_permission_ok);
            icon.setImageResource(android.R.drawable.presence_online);
            status.setText(okText);
            status.setTextColor(0xFF4CAF50);
            btn.setVisibility(View.GONE);
        } else {
            card.setBackgroundResource(R.drawable.card_permission_fail);
            icon.setImageResource(android.R.drawable.presence_busy);
            status.setText("⚠ " + failText);
            status.setTextColor(0xFFE53935);
            btn.setVisibility(View.VISIBLE);
        }
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "KidShield 需要设备管理员权限来防止孩子卸载管控应用，保障孩子的上网安全");
        startActivity(intent);
    }

    private void requestAccessibilityService() {
        new AlertDialog.Builder(this)
                .setTitle("开启无障碍服务")
                .setMessage("请按照以下步骤操作：\n\n" +
                        "1. 在设置中找到「已安装的应用」或「已安装服务」\n" +
                        "2. 找到 KidShield\n" +
                        "3. 开启无障碍开关\n\n" +
                        "开启后 KidShield 才能拦截卸载页面，防止孩子卸载App")
                .setPositiveButton("去设置", (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("稍后", null)
                .show();
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void proceedToTarget() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String targetRole = prefs.getString(KEY_TARGET_ROLE, "parent");

        Intent intent;
        if ("child".equals(targetRole)) {
            intent = new Intent(this, ChildSetupActivity.class);
        } else {
            intent = new Intent(this, ParentDashboardActivity.class);
        }
        // 清除标记
        prefs.edit().remove(KEY_TARGET_ROLE).apply();
        startActivity(intent);
        finish();
    }

    /**
     * 检测无障碍服务是否开启
     */
    public static boolean isAccessibilityServiceEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        if (enabledServices == null) return false;
        for (AccessibilityServiceInfo service : enabledServices) {
            if (service != null && service.getId() != null
                    && service.getId().contains(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }
}
