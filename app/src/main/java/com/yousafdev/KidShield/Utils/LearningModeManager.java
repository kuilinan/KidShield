package com.yousafdev.KidShield.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * 学习模式管理器
 * 开启后自动采集所有App的Activity使用记录
 */
public class LearningModeManager {
    private static final String TAG = "LearningModeManager";
    private static final String PREFS_NAME = "learning_mode";
    private static final String KEY_ENABLED = "learning_mode_enabled";
    private static final String KEY_START_TIME = "learning_mode_start_time";
    private static final String KEY_DURATION_HOURS = "learning_mode_duration";

    private final SharedPreferences prefs;

    public LearningModeManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** 是否处于学习模式 */
    public boolean isLearningMode() {
        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        if (!enabled) return false;

        // 检查是否超过时长限制，默认24小时
        long startTime = prefs.getLong(KEY_START_TIME, 0L);
        int durationHours = prefs.getInt(KEY_DURATION_HOURS, 24);
        if (startTime > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > durationHours * 3600_000L) {
                disableLearningMode();
                Log.d(TAG, "学习模式已自动过期");
                return false;
            }
        }
        return true;
    }

    /** 开启学习模式 */
    public void enableLearningMode(int durationHours) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_START_TIME, System.currentTimeMillis())
            .putInt(KEY_DURATION_HOURS, durationHours)
            .apply();
        Log.d(TAG, "学习模式已开启，持续" + durationHours + "小时");
    }

    /** 关闭学习模式 */
    public void disableLearningMode() {
        prefs.edit().clear().apply();
        Log.d(TAG, "学习模式已关闭");
    }

    /** 获取剩余时间（毫秒） */
    public long getRemainingTime() {
        long startTime = prefs.getLong(KEY_START_TIME, 0L);
        int durationHours = prefs.getInt(KEY_DURATION_HOURS, 24);
        if (startTime == 0L) return 0L;
        return (startTime + durationHours * 3600_000L) - System.currentTimeMillis();
    }
}
