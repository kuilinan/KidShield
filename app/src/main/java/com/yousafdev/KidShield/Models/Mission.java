package com.yousafdev.KidShield.Models;

import java.util.HashMap;
import java.util.Map;

public class Mission {
    public String id;
    public String title;
    public String description;
    public int rewardMinutes;
    public String status; // "pending", "approved", "rejected"
    public long createdAt;
    public String childUid;
    public String completedAt;

    public Mission() {}

    public Mission(String title, String description, int rewardMinutes, String childUid) {
        this.title = title;
        this.description = description;
        this.rewardMinutes = rewardMinutes;
        this.status = "pending";
        this.createdAt = System.currentTimeMillis();
        this.childUid = childUid;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("description", description);
        map.put("rewardMinutes", rewardMinutes);
        map.put("status", status);
        map.put("createdAt", createdAt);
        map.put("childUid", childUid);
        return map;
    }
}
