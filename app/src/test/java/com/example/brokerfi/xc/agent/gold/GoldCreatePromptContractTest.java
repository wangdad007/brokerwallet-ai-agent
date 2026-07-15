package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.view.GoldCreatePoolFragment;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldCreatePromptContractTest {
    @Test
    public void promptContainsOnlySixResolvableTypes() {
        String prompt = GoldCreatePoolFragment.buildAiParserPrompt("2026-07-14");
        for (String type : new String[]{
                "TYPE_PRICE", "TYPE_RETURN_THRESHOLD", "TYPE_PRICE_THRESHOLD",
                "TYPE_PRICE_RANGE", "TYPE_RELATIVE", "TYPE_STREAK"
        }) {
            assertTrue(type, prompt.contains(type));
        }
        for (String legacy : new String[]{
                "TYPE_VOLUME", "TYPE_TECHNICAL", "TYPE_EVENT", "TYPE_TOUCH", "TYPE_VOLATILITY"
        }) {
            assertFalse(legacy, prompt.contains(legacy));
        }
        assertTrue(prompt.contains("startDaysFromNow"));
        assertTrue(prompt.contains("durationDays"));
        assertTrue(prompt.contains("confidence"));
        for (String benchmark : new String[]{"BTC", "ETH", "SOL", "BNB"}) {
            assertTrue(prompt.contains(benchmark));
        }
    }
}
