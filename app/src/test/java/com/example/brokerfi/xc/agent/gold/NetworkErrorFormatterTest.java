package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.ai.NetworkErrorFormatter;

import org.junit.Test;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class NetworkErrorFormatterTest {
    @Test
    public void mapsDnsFailureToChineseMessage() {
        String message = NetworkErrorFormatter.forThrowable(
                new UnknownHostException("api.deepseek.com"));

        assertEquals("网络域名解析失败，请检查模拟器网络后重试", message);
        assertFalse(message.contains("UnknownHostException"));
    }

    @Test
    public void mapsTimeoutToChineseMessage() {
        assertEquals("AI 服务响应超时，请稍后重试",
                NetworkErrorFormatter.forThrowable(new SocketTimeoutException("timeout")));
    }

    @Test
    public void mapsKnownHttpFailuresWithoutLeakingResponseBody() {
        assertEquals("AI 服务认证失败，请检查后端 API Key",
                NetworkErrorFormatter.forRawError("HTTP 401: secret upstream response"));
        assertEquals("AI 服务繁忙，请稍后重试",
                NetworkErrorFormatter.forRawError("HTTP 429: quota details"));
        assertEquals("AI 服务暂时不可用，请稍后重试",
                NetworkErrorFormatter.forRawError("HTTP 502: proxy internals"));
    }
}
