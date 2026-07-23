package com.example.brokerfi.xc.agent.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.brokerfi.xc.agent.config.AgentConfig;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.InputStream;
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

    private static final String TAG = "DeepSeekClient";
    private static final String API_URL = AgentConfig.DEEPSEEK_API_URL;
    private static final String PREFS_NAME = "deepseek_prefs";
    private static final String KEY_API_KEY = "api_key";
    private static final int CONNECT_TIMEOUT_MS = AgentConfig.AI_CONNECT_TIMEOUT_MS;
    private static final int READ_TIMEOUT_MS = AgentConfig.AI_READ_TIMEOUT_MS;
    private static final int MAX_DIRECT_SEGMENTS = 3;

    private static Context appContext;
    private static final Gson gson = new Gson();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static boolean isConfigured() {
        boolean backendConfigured = AgentConfig.BACKEND_RESEARCH_URL != null
                && !AgentConfig.BACKEND_RESEARCH_URL.trim().isEmpty();
        return backendConfigured || (appContext != null && isValidApiKey(getApiKey()));
    }

    public static String getApiKey() {
        if (appContext == null) return null;
        String key = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_API_KEY, null);
        return key == null ? null : key.trim();
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
        if (callback == null) return;
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", userMessage));

        ChatRequest request = new ChatRequest();
        request.model = AgentConfig.DEEPSEEK_MODEL;
        request.messages = messages;
        request.temperature = 0.7;
        request.maxTokens = 8192;

        executeChat(request, callback);
    }

    /**
     * Optimized chat for structured parsing/classification tasks.
     * Uses low temperature (0.1), larger token budget, and JSON mode
     * to produce deterministic, machine-parseable output.
     */
    public static void chatForParsing(String systemPrompt, String userMessage, ChatCallback callback) {
        if (callback == null) return;
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", userMessage));

        ChatRequest request = new ChatRequest();
        request.model = AgentConfig.DEEPSEEK_MODEL;
        request.messages = messages;
        request.temperature = 0.1;
        request.maxTokens = 4096;
        request.responseFormat = new ResponseFormat("json_object");

        executeChat(request, callback);
    }

    private static void executeChat(ChatRequest request, ChatCallback callback) {
        executor.execute(() -> {
            Exception backendFailure;
            try {
                String content = BackendResearchClient.research(
                        messageContent(request, "system"),
                        messageContent(request, "user"));
                callback.onSuccess(content);
                return;
            } catch (Exception e) {
                backendFailure = e;
                Log.w(TAG, "Backend AI request failed; trying direct DeepSeek", e);
            }

            String apiKey = getApiKey();
            if (!isValidApiKey(apiKey)) {
                callback.onError(NetworkErrorFormatter.forThrowable(backendFailure));
                return;
            }
            try {
                callback.onSuccess(executeDirect(request, apiKey));
            } catch (Exception directFailure) {
                Log.w(TAG, "Direct DeepSeek request failed", directFailure);
                callback.onError(NetworkErrorFormatter.forThrowable(directFailure));
            }
        });
    }

    private static String executeDirect(ChatRequest request, String apiKey) throws Exception {
        StringBuilder complete = new StringBuilder();
        for (int segment = 1; segment <= MAX_DIRECT_SEGMENTS; segment++) {
            ChatResponse response = executeDirectOnce(request, apiKey);
            String content = extractContent(response);
            if (complete.length() > 0) complete.append('\n');
            complete.append(content);
            if (!isLengthLimited(response)) return complete.toString().trim();
            if (request.responseFormat != null) {
                throw new IllegalStateException("DeepSeek structured response was truncated");
            }
            request.messages.add(new Message("assistant", content));
            request.messages.add(new Message("user",
                    "上一段内容被截断，请从中断处继续，不要重复已有内容，完成回答并闭合所有 Markdown 结构"));
        }
        throw new IllegalStateException("DeepSeek response remained truncated after continuation");
    }

    private static ChatResponse executeDirectOnce(ChatRequest request, String apiKey) throws Exception {
        HttpURLConnection conn = null;
        try {
            String json = gson.toJson(request);
            conn = (HttpURLConnection) new URL(API_URL).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            String body = readStream(code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new IOException(buildHttpError(code, body));
            }
            return gson.fromJson(body, ChatResponse.class);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static boolean isLengthLimited(ChatResponse response) {
        return response != null && response.choices != null && !response.choices.isEmpty()
                && response.choices.get(0) != null
                && "length".equalsIgnoreCase(response.choices.get(0).finishReason);
    }

    private static String messageContent(ChatRequest request, String role) {
        if (request == null || request.messages == null) return "";
        for (Message message : request.messages) {
            if (message != null && role.equals(message.role)) {
                return message.content == null ? "" : message.content;
            }
        }
        return "";
    }

    public static void chatSimple(String userMessage, ChatCallback callback) {
        chat("你是 BrokerChain Wallet 的专业区块链与 DeFi 助手，请使用简洁、可执行的中文回答",
                userMessage, callback);
    }

    private static boolean isValidApiKey(String key) {
        return key != null && key.startsWith("sk-") && key.length() > 10;
    }

    private static String readStream(InputStream stream) {
        if (stream == null) return "";
        try (Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private static String extractContent(ChatResponse response) {
        if (response == null || response.choices == null || response.choices.isEmpty()
                || response.choices.get(0) == null
                || response.choices.get(0).message == null
                || response.choices.get(0).message.content == null
                || response.choices.get(0).message.content.trim().isEmpty()) {
            throw new IllegalStateException("DeepSeek returned an empty response");
        }
        return response.choices.get(0).message.content.trim();
    }

    private static String buildHttpError(int code, String body) {
        String detail = body == null || body.trim().isEmpty()
                ? "no response body"
                : body.trim();
        if (detail.length() > 300) {
            detail = detail.substring(0, 300) + "...";
        }
        return "HTTP " + code + ": " + detail;
    }

    static class ChatRequest {
        String model;
        List<Message> messages;
        double temperature;
        @SerializedName("max_tokens")
        int maxTokens;
        @SerializedName("response_format")
        ResponseFormat responseFormat;
    }

    static class ResponseFormat {
        String type;
        ResponseFormat(String type) { this.type = type; }
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
        @SerializedName("finish_reason")
        String finishReason;
    }
}
