package com.yousafdev.KidShield.Models;

public class AppInfo {
    public String appName;
    public String packageName;
    public boolean isAllowed;    // 白名单模式下: true=允许
    public boolean isSystemApp;  // 是否是系统应用

    public AppInfo() {}

    public AppInfo(String appName, String packageName) {
        this.appName = appName;
        this.packageName = packageName;
        this.isAllowed = false;
        this.isSystemApp = false;
    }
}
