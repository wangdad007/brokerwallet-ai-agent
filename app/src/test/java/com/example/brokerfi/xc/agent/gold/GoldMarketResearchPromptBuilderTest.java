package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.logic.GoldAdvisoryManager;
import com.example.brokerfi.xc.agent.gold.logic.GoldMarketResearchPromptBuilder;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldMarketResearchPromptBuilderTest {
    private static final BigDecimal E18 = new BigDecimal("1000000000000000000");
    private static final long NOW_MILLIS = 1_700_000_000_000L;

    @Test
    public void buildsStableMarketContextWithQuoteAndSwappedReserveProbabilities() {
        GoldMarketRepository.GameModel game = completeGame();
        GoldAdvisoryManager.Advisory quote = completeQuote();

        String context = GoldMarketResearchPromptBuilder.buildContext(
                game, NOW_MILLIS, quote);

        assertContains(context,
                "博弈池 #7",
                "黄金是否突破前高",
                "结算条件: 金价曾触及 2500 USD",
                "观察国际金价是否在截止前突破关键价位",
                "选项: YES / NO",
                "YES 概率: 60.0%",
                "NO 概率: 40.0%",
                "总池子: 300.00 BKC",
                "市场状态: 进行中",
                "剩余时间: 1天 1小时 1分钟 1秒",
                "YES 2.5 份额",
                "黄金现价: 2388.50 USD",
                "日涨跌: +1.25%",
                "行情来源: gold-api.com",
                "行情更新时间: 2026-06-07 10:30:00",
                "延迟行情: 是");
    }

    @Test
    public void noProbabilityComplementsRoundedYesProbability() {
        GoldMarketRepository.GameModel game = completeGame();
        game.virtualReserves = Arrays.asList(amount("1"), amount("1999"));

        String context = GoldMarketResearchPromptBuilder.buildContext(
                game, NOW_MILLIS, completeQuote());

        assertContains(context,
                "YES 概率: 0.1%",
                "NO 概率: 99.9%");
        assertFalse(context.contains("NO 概率: 100.0%"));
    }

    @Test
    public void summaryPromptAppendsExactAnalysisContract() {
        String context = "市场上下文";
        String contract =
                "请只分析这个博弈池，用中文给出不超过120字的摘要：\n"
                        + "1. 当前哪一侧证据更强；\n"
                        + "2. 两个主要依据；\n"
                        + "3. 最大风险与不确定性；\n"
                        + "4. 明确说明这只是投研辅助，不保证收益。";

        assertEquals(context + "\n\n" + contract,
                GoldMarketResearchPromptBuilder.buildSummaryPrompt(context));
    }

    @Test
    public void followUpUsesExactFormatAndHandlesBlankContextSafely() {
        assertEquals("上下文\n\n【用户追问】\n为什么？",
                GoldMarketResearchPromptBuilder.withFollowUp("上下文", "为什么？"));
        assertEquals("原问题",
                GoldMarketResearchPromptBuilder.withFollowUp("  ", "原问题"));
        assertEquals("",
                GoldMarketResearchPromptBuilder.withFollowUp(null, null));
    }

    @Test
    public void unavailableQuoteIsExplicitForNullOrNonPositivePrice() {
        GoldMarketRepository.GameModel game = completeGame();

        assertContains(
                GoldMarketResearchPromptBuilder.buildContext(game, NOW_MILLIS, null),
                "行情数据不可用");

        GoldAdvisoryManager.Advisory quote = completeQuote();
        quote.priceUsd = 0;
        assertContains(
                GoldMarketResearchPromptBuilder.buildContext(game, NOW_MILLIS, quote),
                "行情数据不可用");
    }

    @Test
    public void includesEveryPositiveHoldingWithUsefulFractionalPrecision() {
        GoldMarketRepository.GameModel game = completeGame();
        game.myShares = Arrays.asList(
                amount("2.500000"),
                amount("1.234567"),
                BigInteger.ONE);
        game.optionNames = Arrays.asList("YES", "NO");

        String context = GoldMarketResearchPromptBuilder.buildContext(
                game, NOW_MILLIS, completeQuote());

        assertContains(context,
                "YES 2.5 份额",
                "NO 1.234567 份额",
                "选项3 <0.000001 份额");
    }

    @Test
    public void secondsAndMillisecondsDeadlinesProduceEquivalentContext() {
        GoldMarketRepository.GameModel secondsGame = completeGame();
        GoldMarketRepository.GameModel millisGame = completeGame();
        millisGame.deadlineSec = secondsGame.deadlineSec * 1000L;

        String secondsContext = GoldMarketResearchPromptBuilder.buildContext(
                secondsGame, NOW_MILLIS, null);
        String millisContext = GoldMarketResearchPromptBuilder.buildContext(
                millisGame, NOW_MILLIS, null);

        assertEquals(secondsContext, millisContext);
    }

    @Test
    public void reportsResolvedRefundedAndExpiredStatuses() {
        GoldMarketRepository.GameModel game = completeGame();
        game.isResolved = true;
        assertContains(GoldMarketResearchPromptBuilder.buildContext(game, NOW_MILLIS, null),
                "市场状态: 已结算");

        game.isResolved = false;
        game.isRefunded = true;
        assertContains(GoldMarketResearchPromptBuilder.buildContext(game, NOW_MILLIS, null),
                "市场状态: 已退款");

        game.isRefunded = false;
        game.deadlineSec = (NOW_MILLIS / 1000L) - 1;
        assertContains(GoldMarketResearchPromptBuilder.buildContext(game, NOW_MILLIS, null),
                "市场状态: 已到期，待结算",
                "剩余时间: 0秒");
    }

    @Test
    public void nullGameAndShortListsReturnUsefulContextWithoutThrowing() {
        assertContains(
                GoldMarketResearchPromptBuilder.buildContext(
                        null, NOW_MILLIS, completeQuote()),
                "博弈池信息不可用",
                "黄金现价: 2388.50 USD");

        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.id = 9;
        game.optionNames = Collections.singletonList("ONLY");
        game.virtualReserves = Collections.singletonList(amount("10"));
        game.myShares = Arrays.asList(null, amount("1"));

        assertContains(
                GoldMarketResearchPromptBuilder.buildContext(
                        game, NOW_MILLIS, completeQuote()),
                "博弈池 #9",
                "选项: ONLY",
                "选项2 1 份额");
    }

    private static GoldMarketRepository.GameModel completeGame() {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.id = 7;
        game.desc = "黄金是否突破前高";
        game.condition = "金价曾触及 2500 USD";
        game.detailedInfo = "观察国际金价是否在截止前突破关键价位";
        game.optionNames = Arrays.asList("YES", "NO");
        game.optionCount = 2;
        game.virtualReserves = Arrays.asList(amount("60"), amount("40"));
        game.totalPool = amount("300");
        game.deadlineSec = (NOW_MILLIS / 1000L) + 90_061L;
        game.myShares = Arrays.asList(amount("2.5"), BigInteger.ZERO);
        return game;
    }

    private static GoldAdvisoryManager.Advisory completeQuote() {
        GoldAdvisoryManager.Advisory quote = new GoldAdvisoryManager.Advisory();
        quote.priceUsd = 2388.50;
        quote.change24h = 1.25;
        quote.quoteSource = "gold-api.com";
        quote.quoteUpdatedAt = "2026-06-07 10:30:00";
        quote.quoteDelayed = true;
        return quote;
    }

    private static BigInteger amount(String tokens) {
        return new BigDecimal(tokens).multiply(E18).toBigIntegerExact();
    }

    private static void assertContains(String actual, String... expectedParts) {
        for (String expected : expectedParts) {
            assertTrue("Expected context to contain: " + expected + "\nActual:\n" + actual,
                    actual.contains(expected));
        }
    }
}
