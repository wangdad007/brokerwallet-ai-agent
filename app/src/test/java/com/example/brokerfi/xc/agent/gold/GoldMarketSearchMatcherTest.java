package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketSearchMatcher;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldMarketSearchMatcherTest {
    @Test
    public void blankQueryShowsEveryValidGame() {
        assertTrue(GoldMarketSearchMatcher.matches(game(), ""));
        assertTrue(GoldMarketSearchMatcher.matches(game(), "   "));
        assertFalse(GoldMarketSearchMatcher.matches(null, ""));
    }

    @Test
    public void searchesIdTitleRuleDetailAndOptions() {
        GoldMarketRepository.GameModel game = game();
        assertTrue(GoldMarketSearchMatcher.matches(game, "12号池"));
        assertTrue(GoldMarketSearchMatcher.matches(game, "#12"));
        assertTrue(GoldMarketSearchMatcher.matches(game, "突破 4100"));
        assertTrue(GoldMarketSearchMatcher.matches(game, "chainlink"));
        assertTrue(GoldMarketSearchMatcher.matches(game, "yes"));
        assertFalse(GoldMarketSearchMatcher.matches(game, "连续下跌"));
    }

    private static GoldMarketRepository.GameModel game() {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.id = 12;
        game.desc = "黄金能否突破 4100 美元";
        game.condition = "截止价格不低于目标价";
        game.detailedInfo = "使用 Chainlink XAU/USD 报价";
        game.optionNames = Arrays.asList("YES", "NO");
        return game;
    }
}
