package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCardPresenter;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class GoldMarketCardPresenterTest {
    @Test
    public void preservesCanonicalVersion2Titles() {
        assertEquals("Gold Absolute Return Above 3%",
                GoldMarketCardPresenter.displayTitle("黄金涨跌幅 大于 3%", 0));
        assertEquals("Gold Price 4000–4200 USD/oz",
                GoldMarketCardPresenter.displayTitle("黄金价格 位于 4000-4200USD/盎司", 0));
        assertEquals("Gold Price 3900–4100 USD/oz",
                GoldMarketCardPresenter.displayTitle(
                        "Gold Price Between 3900 and 4100 USD/oz", 0));
        assertEquals("Gold Price Rises for 3 Days",
                GoldMarketCardPresenter.displayTitle("黄金价格 连续上涨 3天", 0));
        assertEquals("Gold Price Rises for 2 Days",
                GoldMarketCardPresenter.displayTitle(
                        "Gold Price Rises for 2 Consecutive Days", 0));
    }
    @Test
    public void compactTitleTurnsDatedPriceDirectionIntoShortMarketName() {
        assertEquals("Gold Direction", GoldMarketCardPresenter.compactTitle(
                "2026-07-04 16:32 至 2026-07-30 16:20 黄金价格 上涨"));
    }

    @Test
    public void compactTitleTurnsThresholdMarketIntoTargetPriceName() {
        assertEquals("Gold Price Target", GoldMarketCardPresenter.compactTitle(
                "截止 2026-07-07 13:55 金价 大于 10 USD"));
    }

    @Test
    public void compactTitleKeepsUnknownTitlesReadableButShort() {
        assertEquals("Gold Correlation", GoldMarketCardPresenter.compactTitle(
                "2026-07-04 至 2026-08-20 美元指数与黄金相关性是否增强"));
    }

    @Test
    public void displayTitleKeepsDirectionAndAddsDurationDaysWhenStartAndEndTimesExist() {
        assertEquals("Gold Price Rises Over 26 Days", GoldMarketCardPresenter.displayTitle(
                "2026-07-04 16:32 至 2026-07-30 16:20 黄金价格 上涨", 0));
    }

    @Test
    public void displayTitleKeepsThresholdAndAmountInsteadOfDeadlineTime() {
        assertEquals("Gold Price Above 10 USD/oz", GoldMarketCardPresenter.displayTitle(
                "截止 2026-07-07 13:55 金价 大于 10 USD", 0));
    }

    @Test
    public void displayTitleUsesConditionWhenLegacyDescriptionOmitsThresholdAmount() {
        assertEquals("Gold Price Above 10 USD/oz", GoldMarketCardPresenter.displayTitle(
                "黄金目标价 07-06 20:30",
                "黄金价格 大于 10 USD (截至 2026-07-06 20:30)",
                0));
    }

    @Test
    public void displayTitleDoesNotAppendChainDeadlineToGenericTitle() {
        assertEquals("Gold Market", GoldMarketCardPresenter.displayTitle(
                "博弈池 #9", 1783440000L));
    }

    @Test
    public void displayTitleHidesSubDayDurationInsteadOfShowingHoursOrMinutes() {
        assertEquals("Gold Price Rises", GoldMarketCardPresenter.displayTitle(
                "2026-07-04 16:32 至 2026-07-04 21:20 黄金价格 上涨", 0));
    }

    @Test
    public void displayTitleUsesCompactTitlesForAllSimulatorMarketTemplates() {
        assertEquals("Gold Price Remains Flat Over 3 Days", GoldMarketCardPresenter.displayTitle(
                "2026-07-04 16:32 至 2026-07-07 16:20 黄金价格 持平", 0));
        assertEquals("Gold Price Equals 3200 USD/oz", GoldMarketCardPresenter.displayTitle(
                "截止 2026-07-07 13:55 金价 等于 3200 USD", 0));
        assertEquals("Event: Federal Reserve Rate Cut", GoldMarketCardPresenter.displayTitle(
                "2026-07-10 12:00 前是否发生：美联储降息", 0));
        assertEquals("Gold Volatility Above 3%", GoldMarketCardPresenter.displayTitle(
                "到 2026-07-10 12:00 黄金价格波动是否达到 3%", 0));
        assertEquals("Gold Outperforms BTC", GoldMarketCardPresenter.displayTitle(
                "到 2026-07-11 23:40 黄金表现是否跑赢 BTC",
                "黄金收益率高于 BTC，观察期截至 2026-07-11 23:40", 0));
        assertEquals("Gold Price Touches 2800 USD/oz", GoldMarketCardPresenter.displayTitle(
                "黄金是否会在 2026-07-10 12:00 前触及 2800 USD", 0));
        assertEquals("Gold MACD Crosses Above Signal", GoldMarketCardPresenter.displayTitle(
                "到 2026-07-12 04:32 黄金技术指标是否出现：MACD 上穿信号线",
                "黄金技术指标条件 MACD 上穿信号线，观察期截至 2026-07-12 04:32", 0));
        assertEquals("Gold RSI Above 70", GoldMarketCardPresenter.displayTitle(
                "黄金 指标 RSI (14) 触发 大于 (Above) 70", "", 0));
    }

    @Test
    public void displayTitleNeverTruncatesEnglishTitleCreatedByCurrentFlow() {
        assertEquals("Gold Outperforms BTC", GoldMarketCardPresenter.displayTitle(
                "Gold Outperforms BTC",
                "Gold Outperforms BTC; use the final valid quote at each boundary.", 0));
    }

    @Test
    public void displayTitleAddsRoundedDayCountForLegacyDirectionMarkets() {
        long fiveHoursFromNow = (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(5L)) / 1000L;
        assertEquals("Gold Price Remains Flat Over 1 Day", GoldMarketCardPresenter.displayTitle(
                "到 2026-07-11 13:20 黄金价格是否持平", "", fiveHoursFromNow));
    }

    @Test
    public void displayTitleUsesGoldPriceForEveryDirection() {
        assertEquals("Gold Price Falls Over 7 Days", GoldMarketCardPresenter.displayTitle(
                "黄金 下跌 7天", 0));
    }
}
