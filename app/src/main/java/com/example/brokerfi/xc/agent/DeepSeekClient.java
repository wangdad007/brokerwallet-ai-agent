package com.example.brokerfi.xc.agent;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeepSeekClient {

    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final String PREFS_NAME = "deepseek_prefs";
    private static final String KEY_API_KEY = "api_key";

    private static Context appContext;
    private static final Gson gson = new Gson();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static boolean isConfigured() {
        if (appContext == null) return false;
        String key = getApiKey();
        return key != null && key.startsWith("sk-") && key.length() > 10;
    }

    public static String getApiKey() {
        if (appContext == null) return null;
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_API_KEY, null);
    }

    public static boolean setApiKey(String key) {
        if (appContext == null) return false;
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_API_KEY, key).apply();
        return true;
    }

    public interface ChatCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public static void chat(String systemPrompt, String userMessage, ChatCallback callback) {
        executor.execute(() -> {
            try {
                List<Message> messages = new ArrayList<>();
                messages.add(new Message("system", systemPrompt));
                messages.add(new Message("user", userMessage));

                ChatRequest request = new ChatRequest();
                request.model = "deepseek-chat";
                request.messages = messages;
                request.temperature = 0.7;
                request.maxTokens = 1024;

                String json = gson.toJson(request);

                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + getApiKey());
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    Scanner scanner = new Scanner(conn.getInputStream(), "UTF-8")
                            .useDelimiter("\\A");
                    String body = scanner.hasNext() ? scanner.next() : "";
                    scanner.close();

                    ChatResponse response = gson.fromJson(body, ChatResponse.class);
                    String content = response.choices.get(0).message.content;
                    callback.onSuccess(content);
                } else {
                    java.io.InputStream errStream = conn.getErrorStream();
                    String err;
                    if (errStream != null) {
                        Scanner scanner = new Scanner(errStream, "UTF-8")
                                .useDelimiter("\\A");
                        err = scanner.hasNext() ? scanner.next() : "HTTP " + code;
                        scanner.close();
                    } else {
                        err = "HTTP " + code + " (no body)";
                    }
                    callback.onError(err);
                }
                conn.disconnect();
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public static void chatSimple(String userMessage, ChatCallback callback) {
        chat("You are a helpful blockchain DeFi assistant. Keep answers concise and actionable.",
                userMessage, callback);
    }

    // --- request/response models ---

    static class ChatRequest {
        String model;
        List<Message> messages;
        double temperature;
        @SerializedName("max_tokens")
        int maxTokens;
    }

    static class Message {
        String role;
        String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    static class ChatResponse {
        List<Choice> choices;
    }

    static class Choice {
        Message message;
    }
}
