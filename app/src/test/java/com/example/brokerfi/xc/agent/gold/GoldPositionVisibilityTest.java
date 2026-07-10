package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionVisibility;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldPositionVisibilityTest {
    @Test
    public void showsOnlyOpenUnresolvedAndUnrefundedPositions() {
        long nowMs = 1_700_000_000_000L;

        assertTrue(GoldPositionVisibility.isVisible(openPosition(1_700_000_001L), nowMs));
        assertFalse(GoldPositionVisibility.isVisible(openPosition(1_699_999_999L), nowMs));

        GoldMarketRepository.GameModel resolved = openPosition(1_700_000_001L);
        resolved.isResolved = true;
        assertFalse(GoldPositionVisibility.isVisible(resolved, nowMs));

        GoldMarketRepository.GameModel refunded = openPosition(1_700_000_001L);
        refunded.isRefunded = true;
        assertFalse(GoldPositionVisibility.isVisible(refunded, nowMs));
    }

    private static GoldMarketRepository.GameModel openPosition(long deadlineSec) {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.deadlineSec = deadlineSec;
        return game;
    }
}
