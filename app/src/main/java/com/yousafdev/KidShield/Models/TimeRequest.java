package com.yousafdev.KidShield.Models;

import java.util.HashMap;
import java.util.Map;

public class TimeRequest {
    public String id;
    public String packageName;
    public String appName;
    public int requestedMinutes;
    public String reason;
    public String status; // "pending", "approved", "rejected"
    public long createdAt;
    public String childUid;

    public TimeRequest() {}

    public TimeRequest(String packageName, String appName, int requestedMinutes, String reason, String childUid) {
        this.packageName = packageName;
        this.appName = appName;
        this.requestedMinutes = requestedMinutes;
        this.reason = reason;
        this.status = "pending";
        this.createdAt = System.currentTimeMillis();
        this.childUid = childUid;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("packageName", packageName);
        map.put("appName", appName);
        map.put("requestedMinutes", requestedMinutes);
        map.put("reason", reason);
        map.put("status", status);
        map.put("createdAt", createdAt);
        map.put("childUid", childUid);
        return map;
    }
}
