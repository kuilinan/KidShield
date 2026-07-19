package com.yousafdev.KidShield.Models;

import java.util.HashMap;
import java.util.Map;

public class TimeRestriction {
    public int dayOfWeek;
    public String startTime; // HH:mm
    public String endTime;   // HH:mm

    public TimeRestriction() {}

    public TimeRestriction(int dayOfWeek, String startTime, String endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("dayOfWeek", dayOfWeek);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        return map;
    }

    public boolean isInRestriction() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int currentDay = cal.get(java.util.Calendar.DAY_OF_WEEK);
        if (currentDay != dayOfWeek) return false;

        int currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);
        String[] startParts = startTime.split(":");
        String[] endParts = endTime.split(":");
        int startMinutes = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);
        int endMinutes = Integer.parseInt(endParts[0]) * 60 + Integer.parseInt(endParts[1]);

        if (startMinutes <= endMinutes) {
            return currentMinutes >= startMinutes && currentMinutes < endMinutes;
        } else {
            // 跨天（如22:00-07:00）
            return currentMinutes >= startMinutes || currentMinutes < endMinutes;
        }
    }
}
