package com.yousafdev.KidShield.Network;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 自建后端 API 客户端（替换 Firebase）
 * 使用 Android 原生 HttpURLConnection，无需额外依赖！
 * 
 * API 地址指向 Vercel 部署的生产域名
 */
public class ApiClient {
    private static final String TAG = "ApiClient";
    
    // ⭐ 这里改成你的 Vercel 生产域名！
    private static final String BASE_URL = "https://kid-shield-five.vercel.app";
    
    private static ApiClient instance;
    private String authToken;
    
    private ApiClient() {}
    
    public static synchronized ApiClient getInstance() {
        if (instance == null) instance = new ApiClient();
        return instance;
    }
    
    /** 设置登录后的 Token */
    public void setToken(String token) { this.authToken = token; }
    public String getToken() { return authToken; }
    public boolean isLoggedIn() { return authToken != null && !authToken.isEmpty(); }
    
    // ===================== 注册 =====================
    public JSONObject register(String email, String password, String role, String nickname) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        body.put("role", role);
        if (nickname != null) body.put("nickname", nickname);
        return httpPost("/api/register", body.toString());
    }
    
    // ===================== 登录 =====================
    public JSONObject login(String email, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("email", email);
        body.put("password", password);
        JSONObject resp = httpPost("/api/login", body.toString());
        if (resp.has("token")) {
            authToken = resp.getString("token");
        }
        return resp;
    }
    
    // ===================== 获取用户信息 =====================
    public JSONObject getUserMe() throws Exception {
        return httpGet("/api/user/me");
    }
    
    // ===================== 孩子绑定家长 =====================
    public JSONObject bindChild(String parentCode) throws Exception {
        JSONObject body = new JSONObject();
        body.put("parent_code", parentCode);
        return httpPost("/api/child/bind", body.toString());
    }
    
    // ===================== 查看孩子列表（家长用） =====================
    public JSONObject getChildren() throws Exception {
        return httpGet("/api/parent/children");
    }
    
    // ===================== 获取白名单（孩子用） =====================
    public JSONObject getWhitelist(String childId) throws Exception {
        return httpGet("/api/whitelist/" + childId);
    }
    
    // ===================== 获取任务列表 =====================
    public JSONObject getMissions(String childId) throws Exception {
        return httpGet("/api/missions/" + childId);
    }
    
    // ===================== 获取加时长申请 =====================
    public JSONObject getTimeRequests(String childId) throws Exception {
        return httpGet("/api/time-requests/" + childId);
    }
    
    // ===================== 获取使用统计 =====================
    public JSONObject getUsageStats(String childId) throws Exception {
        return httpGet("/api/usage/" + childId);
    }
    
    // ===================== 同步通知缓存 =====================
    public JSONObject syncNotifications(String childId, JSONObject data) throws Exception {
        return httpPost("/api/notifications/sync", data.toString());
    }
    
    // ===================== 上传使用统计 =====================
    public JSONObject reportUsage(JSONObject data) throws Exception {
        return httpPost("/api/usage/report", data.toString());
    }
    
    // ===================== HTTP 请求实现 =====================
    
    private JSONObject httpGet(String path) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-Type", "application/json");
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        
        int code = conn.getResponseCode();
        String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        conn.disconnect();
        
        Log.d(TAG, "GET " + path + " → " + code);
        return new JSONObject(body);
    }
    
    private JSONObject httpPost(String path, String jsonBody) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (authToken != null) {
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
        }
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        int code = conn.getResponseCode();
        String body = readStream(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        conn.disconnect();
        
        Log.d(TAG, "POST " + path + " → " + code);
        return new JSONObject(body);
    }
    
    private String readStream(java.io.InputStream stream) throws Exception {
        if (stream == null) return "{}";
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
