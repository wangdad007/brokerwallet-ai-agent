package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketDetailPresenter;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;

public class GoldMarketDetailPresenterTest {
    @Test
    public void highlightPartsIdentifyTimeSubjectAndUpTrend() {
        List<GoldMarketDetailPresenter.Part> parts = GoldMarketDetailPresenter.highlightParts(
                "2026-07-04 至 2026-08-20 黄金价格 上涨");

        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.TIME, "2026-07-04"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.TIME, "2026-08-20"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.NOUN, "价格"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.TREND_UP, "上涨"));
    }

    @Test
    public void highlightPartsIdentifyDownTrend() {
        List<GoldMarketDetailPresenter.Part> parts = GoldMarketDetailPresenter.highlightParts(
                "截止 2026-07-30 金价 下跌");

        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.NOUN, "金价"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.TREND_DOWN, "下跌"));
    }

    @Test
    public void highlightPartsIdentifyComparatorAndAmount() {
        List<GoldMarketDetailPresenter.Part> parts = GoldMarketDetailPresenter.highlightParts(
                "金价 大于 10 USD");

        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.NOUN, "金价"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.COMPARATOR, "大于"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.AMOUNT, "10 USD"));
    }

    @Test
    public void highlightPartsTreatDurationDaysAsAnAmount() {
        List<GoldMarketDetailPresenter.Part> parts = GoldMarketDetailPresenter.highlightParts(
                "黄金 上涨 19天");

        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.TREND_UP, "上涨"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.AMOUNT, "19天"));
    }

    @Test
    public void highlightPartsTreatsWholePriceRangeAsOneAmount() {
        List<GoldMarketDetailPresenter.Part> parts = GoldMarketDetailPresenter.highlightParts(
                "黄金价格 位于 3900-4050USD/盎司");

        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.AMOUNT,
                "3900-4050USD/盎司"));
    }

    @Test
    public void englishTitlePartsSeparateContentActionValueAndDuration() {
        List<GoldMarketDetailPresenter.Part> threshold =
                GoldMarketDetailPresenter.highlightParts("Gold Price At Least 3000 USD/oz");
        assertTrue(contains(threshold, GoldMarketDetailPresenter.Role.NOUN, "Price"));
        assertTrue(contains(threshold, GoldMarketDetailPresenter.Role.COMPARATOR, "At Least"));
        assertTrue(contains(threshold, GoldMarketDetailPresenter.Role.AMOUNT, "3000 USD/oz"));

        List<GoldMarketDetailPresenter.Part> streak =
                GoldMarketDetailPresenter.highlightParts("Gold Price Rises for 2 Days");
        assertTrue(contains(streak, GoldMarketDetailPresenter.Role.TREND_UP, "Rises"));
        assertTrue(contains(streak, GoldMarketDetailPresenter.Role.AMOUNT,
                "2 Days"));

        List<GoldMarketDetailPresenter.Part> relative =
                GoldMarketDetailPresenter.highlightParts("Gold Outperforms BTC");
        assertTrue(contains(relative, GoldMarketDetailPresenter.Role.COMPARATOR, "Outperforms"));
        assertTrue(contains(relative, GoldMarketDetailPresenter.Role.NOUN, "BTC"));
    }

    @Test
    public void resolutionRuleUsesReadableSections() {
        String formatted = GoldMarketDetailPresenter.formatResolutionRule(
                "黄金价格 位于 3900-4050USD/盎司；北京时间 2026-07-15 00:00 至 "
                        + "2026-07-16 00:00；信源为 Ethereum Chainlink Data Feed，"
                        + "取边界时刻之前最后一轮有效报价。");

        assertTrue(formatted.contains("Resolution Rule\nGold Price Between 3900 and 4050 USD/oz"));
        assertTrue(formatted.contains("Observation Period\nBeijing time 2026-07-15 00:00 to 2026-07-16 00:00"));
        assertTrue(formatted.contains(
                "Data Source\nChainlink XAU/USD Data Feed on Ethereum"));
        assertTrue(formatted.contains("Pricing Policy\nUse the final valid quote at or before each boundary"));
    }

    @Test
    public void formatsEnglishCreationPolicyIntoSections() {
        String formatted = GoldMarketDetailPresenter.formatResolutionRule(
                "Gold Price Above 4000 USD/oz; Beijing time 2026-07-15 00:00 to "
                        + "2026-07-16 00:00; source: Ethereum Chainlink Data Feed; "
                        + "use the final valid quote at or before each boundary.");

        assertTrue(formatted.contains("Resolution Rule\nGold Price Above 4000 USD/oz"));
        assertTrue(formatted.contains("Observation Period\nBeijing time 2026-07-15 00:00 to 2026-07-16 00:00"));
        assertTrue(formatted.contains(
                "Data Source\nChainlink XAU/USD Data Feed on Ethereum"));
        assertTrue(formatted.contains("Pricing Policy\nUse the final valid quote at or before each boundary"));
    }

    @Test
    public void highlightPartsDistinguishesRelativeMarketDirectionAndBenchmark() {
        List<GoldMarketDetailPresenter.Part> parts = GoldMarketDetailPresenter.highlightParts(
                "黄金 跑赢 比特币");

        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.NOUN, "比特币"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.TREND_UP, "跑赢"));
    }

    @Test
    public void tickerKeywordDoesNotColorEthereumDataSourcePrefix() {
        List<GoldMarketDetailPresenter.Part> sourceParts =
                GoldMarketDetailPresenter.highlightParts(
                        "数据来源\nEthereum Chainlink Data Feed");
        org.junit.Assert.assertFalse(
                contains(sourceParts, GoldMarketDetailPresenter.Role.NOUN, "Eth"));

        List<GoldMarketDetailPresenter.Part> tickerParts =
                GoldMarketDetailPresenter.highlightParts("黄金跑赢 ETH");
        assertTrue(contains(tickerParts, GoldMarketDetailPresenter.Role.NOUN, "ETH"));
    }

    @Test
    public void highlightPartsDistinguishesTechnicalIndicatorAndSignalDirection() {
        List<GoldMarketDetailPresenter.Part> parts = GoldMarketDetailPresenter.highlightParts(
                "黄金 MACD 上穿信号线");

        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.NOUN, "MACD"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.TREND_UP, "上穿"));
    }

    @Test
    public void highlightPartsTreatsBareTechnicalThresholdAsAnAmount() {
        List<GoldMarketDetailPresenter.Part> parts = GoldMarketDetailPresenter.highlightParts(
                "黄金RSI 大于 70");

        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.NOUN, "RSI"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.COMPARATOR, "大于"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.AMOUNT, "70"));
    }

    @Test
    public void heroTextSplitsDateRangeFromMarketTitle() {
        GoldMarketDetailPresenter.HeroText hero = GoldMarketDetailPresenter.heroText(
                "2026-07-04 16:40 至 2026-07-23 04:25 黄金价格 上涨", 0);

        org.junit.Assert.assertEquals("黄金价格 上涨", hero.primaryTitle);
        org.junit.Assert.assertEquals("2026-07-04 16:40 - 2026-07-23 04:25", hero.timeSubtitle);
    }

    @Test
    public void heroTextSplitsDeadlineFromThresholdTitle() {
        GoldMarketDetailPresenter.HeroText hero = GoldMarketDetailPresenter.heroText(
                "截止 2026-07-06 15:00 金价 大于 10 USD", 0);

        org.junit.Assert.assertEquals("金价 大于 10 USD", hero.primaryTitle);
        org.junit.Assert.assertEquals("Ends 2026-07-06 15:00", hero.timeSubtitle);
    }

    @Test
    public void heroTextSplitsMonthDayTimestampWithoutDuplicatingIt() {
        GoldMarketDetailPresenter.HeroText hero = GoldMarketDetailPresenter.heroText(
                "黄金目标价 07-10 13:42", 0);

        org.junit.Assert.assertEquals("黄金目标价", hero.primaryTitle);
        org.junit.Assert.assertEquals("07-10 13:42", hero.timeSubtitle);
    }

    private static boolean contains(List<GoldMarketDetailPresenter.Part> parts,
                                    GoldMarketDetailPresenter.Role role,
                                    String text) {
        for (GoldMarketDetailPresenter.Part part : parts) {
            if (part.role == role && text.equals(part.text)) {
                return true;
            }
        }
        return false;
    }
}
