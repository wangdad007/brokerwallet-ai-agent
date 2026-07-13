package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.GoldBackendClient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GoldBackendClientTest {
    @Test
    public void parsesPositiveBackendQuote() {
        GoldBackendClient.Quote quote = GoldBackendClient.parseQuote(
                "{\"price_usd\":2412.35,\"change_24h\":1.25,"
                        + "\"source\":\"新浪财经\",\"updated_at\":\"2026-07-14 00:10:00\"}");

        assertEquals(2412.35, quote.priceUsd, 0.0001);
        assertEquals(1.25, quote.change24h, 0.0001);
        assertEquals("新浪财经", quote.source);
        assertEquals("2026-07-14 00:10:00", quote.updatedAt);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroPriceAsUnavailable() {
        GoldBackendClient.parseQuote(
                "{\"price_usd\":0,\"change_24h\":0,\"source\":\"\",\"updated_at\":\"\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedQuote() {
        GoldBackendClient.parseQuote("{\"price_usd\":\"bad\"}");
    }
}
