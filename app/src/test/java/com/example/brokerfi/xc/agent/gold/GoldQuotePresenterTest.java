package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldQuotePresenter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GoldQuotePresenterTest {
    @Test
    public void chainlinkTechnicalDetailsAreHiddenFromHeader() {
        String label = GoldQuotePresenter.quoteMeta(
                "Chainlink XAU/USD feed=0x214eD9D round=922337",
                "2026-07-15T01:40:59Z", false);

        assertEquals("Chainlink XAU/USD · 07-15 09:40 更新", label);
        assertFalse(label.contains("0x"));
        assertFalse(label.contains("round"));
    }

    @Test
    public void missingDailyReferenceIsNotRenderedAsZero() {
        assertEquals("24h --", GoldQuotePresenter.dailyChange(0, false));
        assertEquals("24h +0.00%", GoldQuotePresenter.dailyChange(0, true));
    }
}
