package com.example.brokerfi.xc.agent.gold.model.data;

import com.example.brokerfi.xc.agent.config.AgentConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public final class GoldBackendClient {
    private GoldBackendClient() {}

    public static final class Quote {
        public double priceUsd;
        public double change24h;
        public boolean changeAvailable;
        public String source;
        public String updatedAt;
    }

    public static Quote fetchQuote() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(AgentConfig.BACKEND_GOLD_QUOTE_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(AgentConfig.HTTP_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(AgentConfig.HTTP_READ_TIMEOUT_MS);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("Backend quote HTTP " + code);
            }
            return parseQuote(readStream(connection.getInputStream()));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static Quote parseQuote(String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            Quote quote = new Quote();
            quote.priceUsd = json.get("price_usd").getAsDouble();
            quote.change24h = json.has("change_24h") ? json.get("change_24h").getAsDouble() : 0;
            quote.changeAvailable = json.has("change_available")
                    && json.get("change_available").getAsBoolean();
            quote.source = json.has("source") && !json.get("source").isJsonNull()
                    ? json.get("source").getAsString().trim() : "";
            quote.updatedAt = json.has("updated_at") && !json.get("updated_at").isJsonNull()
                    ? json.get("updated_at").getAsString().trim() : "";
            if (quote.priceUsd <= 0) {
                throw new IllegalArgumentException("Backend returned a non-positive gold price");
            }
            return quote;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid backend quote response", e);
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }
}
