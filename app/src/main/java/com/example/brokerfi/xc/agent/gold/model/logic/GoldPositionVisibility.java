package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

/** Defines which markets belong in the active personal-holdings view. */
public final class GoldPositionVisibility {
    private GoldPositionVisibility() {
    }

    public static boolean isVisible(GoldMarketRepository.GameModel game, long nowMillis) {
        if (game == null || game.isResolved || game.isRefunded) return false;
        long nowSec = Math.max(0L, nowMillis / 1000L);
        return game.deadlineSec > nowSec;
    }
}
