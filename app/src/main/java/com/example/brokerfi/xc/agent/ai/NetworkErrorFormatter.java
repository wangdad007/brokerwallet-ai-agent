package com.example.brokerfi.xc.agent.ai;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

public final class NetworkErrorFormatter {
    private NetworkErrorFormatter() {}

    public static String forThrowable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof UnknownHostException) {
                return "Unable to resolve the network host. Check the device connection and retry.";
            }
            if (current instanceof SocketTimeoutException) {
                return "The AI service timed out. Please retry shortly.";
            }
            if (current instanceof ConnectException) {
                return "Unable to reach the backend. Confirm that PredictionMarket is running.";
            }
            current = current.getCause();
        }
        return forRawError(error == null ? "" : error.getMessage());
    }

    public static String forRawError(String rawError) {
        String value = rawError == null ? "" : rawError;
        if (value.contains("NO_API_KEY")) {
            return "The AI service is not configured. Check the backend API key.";
        }
        if (value.contains("401") || value.contains("403")) {
            return "AI authentication failed. Check the backend API key.";
        }
        if (value.contains("429")) {
            return "The AI service is busy. Please retry shortly.";
        }
        if (value.contains("UnknownHostException") || value.toLowerCase().contains("unable to resolve host")) {
            return "Unable to resolve the network host. Check the device connection and retry.";
        }
        if (value.toLowerCase().contains("timeout")) {
            return "The AI service timed out. Please retry shortly.";
        }
        return "The AI service is temporarily unavailable. Please retry shortly.";
    }
}
