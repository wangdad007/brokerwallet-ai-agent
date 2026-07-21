package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.ai.NetworkErrorFormatter;

import org.junit.Test;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class NetworkErrorFormatterTest {
    @Test
    public void mapsDnsFailureToEnglishMessage() {
        String message = NetworkErrorFormatter.forThrowable(
                new UnknownHostException("api.deepseek.com"));

        assertEquals("Unable to resolve the network host. Check the device connection and retry.", message);
        assertFalse(message.contains("UnknownHostException"));
    }

    @Test
    public void mapsTimeoutToEnglishMessage() {
        assertEquals("The AI service timed out. Please retry shortly.",
                NetworkErrorFormatter.forThrowable(new SocketTimeoutException("timeout")));
    }

    @Test
    public void mapsKnownHttpFailuresWithoutLeakingResponseBody() {
        assertEquals("AI authentication failed. Check the backend API key.",
                NetworkErrorFormatter.forRawError("HTTP 401: secret upstream response"));
        assertEquals("The AI service is busy. Please retry shortly.",
                NetworkErrorFormatter.forRawError("HTTP 429: quota details"));
        assertEquals("The AI service is temporarily unavailable. Please retry shortly.",
                NetworkErrorFormatter.forRawError("HTTP 502: proxy internals"));
    }
}
