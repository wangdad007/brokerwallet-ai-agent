package com.example.brokerfi.xc.agent.ai;

import com.example.brokerfi.xc.agent.config.AgentConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class BackendResearchClient {
    private BackendResearchClient() {}

    public static String research(String systemPrompt, String userMessage) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(AgentConfig.BACKEND_RESEARCH_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setDoOutput(true);
            connection.setConnectTimeout(AgentConfig.AI_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(AgentConfig.AI_READ_TIMEOUT_MS);

            JsonObject payload = new JsonObject();
            payload.addProperty("system_prompt", systemPrompt == null ? "" : systemPrompt);
            payload.addProperty("user_message", userMessage == null ? "" : userMessage);
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Backend research HTTP " + code);
            }
            return parseContent(readStream(connection.getInputStream()));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static String parseContent(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String content = json.has("content") && !json.get("content").isJsonNull()
                    ? json.get("content").getAsString().trim() : "";
            if (content.isEmpty()) {
                throw new IllegalArgumentException("Backend returned empty AI content");
            }
            return content;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid backend research response", e);
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
