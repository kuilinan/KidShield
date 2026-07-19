package com.yousafdev.KidShield.Models;

public class Mission {
    public String id;
    public String title;
    public String description;
    public int rewardMinutes;
    public String status; // pending / approved / rejected
    public String type; // "child_submit" 孩子提交 / "parent_assign" 家长布置
    public long timestamp;

    public Mission() {
        // 默认构造函数（Firebase反序列化用）
    }

    public Mission(String id, String title, String description, int rewardMinutes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.rewardMinutes = rewardMinutes;
        this.status = "pending";
        this.type = "child_submit";
        this.timestamp = System.currentTimeMillis();
    }

    public Mission(String id, String title, String description, int rewardMinutes, String type) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.rewardMinutes = rewardMinutes;
        this.status = "pending";
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isApproved() {
        return "approved".equals(status);
    }

    public boolean isRejected() {
        return "rejected".equals(status);
    }

    public boolean isPending() {
        return "pending".equals(status);
    }
}
