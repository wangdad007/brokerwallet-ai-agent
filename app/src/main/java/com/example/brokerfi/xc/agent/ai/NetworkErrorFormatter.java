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
                return "网络域名解析失败，请检查模拟器网络后重试";
            }
            if (current instanceof SocketTimeoutException) {
                return "AI 服务响应超时，请稍后重试";
            }
            if (current instanceof ConnectException) {
                return "无法连接本地后端，请确认 PredictionMarket 已启动";
            }
            current = current.getCause();
        }
        return forRawError(error == null ? "" : error.getMessage());
    }

    public static String forRawError(String rawError) {
        String value = rawError == null ? "" : rawError;
        if (value.contains("NO_API_KEY")) {
            return "AI 服务尚未配置，请检查后端 API Key";
        }
        if (value.contains("401") || value.contains("403")) {
            return "AI 服务认证失败，请检查后端 API Key";
        }
        if (value.contains("429")) {
            return "AI 服务繁忙，请稍后重试";
        }
        if (value.contains("UnknownHostException") || value.toLowerCase().contains("unable to resolve host")) {
            return "网络域名解析失败，请检查模拟器网络后重试";
        }
        if (value.toLowerCase().contains("timeout")) {
            return "AI 服务响应超时，请稍后重试";
        }
        return "AI 服务暂时不可用，请稍后重试";
    }
}
