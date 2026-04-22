package com.example.verson1;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTP helper for ShiftSync backend (JWT bearer).
 * Multiple IP options for USB debugging connection issues.
 */
public final class ApiClient {

    private static final String[] POSSIBLE_IPS = {
        BuildConfig.BACKEND_BASE_URL,
        "http://10.0.2.2:3000",
        "http://localhost:3000"
    };
    
    public static String BASE_URL = POSSIBLE_IPS[0];
    
    // Method to find working IP (runs on background thread)
    public static String findWorkingIP() {
        for (String ip : POSSIBLE_IPS) {
            try {
                java.net.URL url = new java.net.URL(ip + "/health");
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(1000); // 1 second timeout for faster testing
                connection.setReadTimeout(1000);
                connection.connect();
                int responseCode = connection.getResponseCode();
                connection.disconnect();
                if (responseCode != -1) {
                    BASE_URL = ip; // Set working IP as default
                    return ip;
                }
            } catch (Exception e) {
                // Try next IP
                continue;
            }
        }
        return null; // No IP worked
    }
    
    // Simple connection test (non-blocking)
    public static boolean testConnection() {
        try {
            java.net.URL url = new java.net.URL(BASE_URL + "/health");
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(500); // Very short timeout
            connection.connect();
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode != -1;
        } catch (Exception e) {
            return false;
        }
    }

    private ApiClient() {}

    public static final class HttpResult {
        public final int code;
        public final String body;

        public HttpResult(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    public static HttpResult get(String path, String token) throws Exception {
        return request("GET", path, token, null);
    }

    public static HttpResult post(String path, String token, String jsonBody) throws Exception {
        return request("POST", path, token, jsonBody);
    }

    public static HttpResult patch(String path, String token, String jsonBody) throws Exception {
        return request("PATCH", path, token, jsonBody);
    }

    public static HttpResult delete(String path, String token) throws Exception {
        return request("DELETE", path, token, null);
    }

    public static HttpResult put(String path, String token, String jsonBody) throws Exception {
        return request("PUT", path, token, jsonBody);
    }

    private static HttpResult request(String method, String path, String token, String jsonBody)
            throws Exception {
        String urlStr = path.startsWith("http") ? path : BASE_URL + path;
        HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
        try {
            con.setRequestMethod(method);
            con.setConnectTimeout(15000);
            con.setReadTimeout(15000);
            if (token != null && !token.isEmpty()) {
                con.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (jsonBody != null) {
                con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                con.setDoOutput(true);
                byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
                OutputStream os = con.getOutputStream();
                os.write(bytes);
                os.flush();
                os.close();
            }

            int code = con.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream();
            String body = readStream(stream);
            return new HttpResult(code, body);
        } finally {
            con.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }
}
