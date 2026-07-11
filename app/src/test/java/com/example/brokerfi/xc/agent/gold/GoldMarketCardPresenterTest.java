package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCardPresenter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GoldMarketCardPresenterTest {
    @Test
    public void compactTitleTurnsDatedPriceDirectionIntoShortMarketName() {
        assertEquals("黄金涨跌", GoldMarketCardPresenter.compactTitle(
                "2026-07-04 16:32 至 2026-07-30 16:20 黄金价格 上涨"));
    }

    @Test
    public void compactTitleTurnsThresholdMarketIntoTargetPriceName() {
        assertEquals("黄金目标价", GoldMarketCardPresenter.compactTitle(
                "截止 2026-07-07 13:55 金价 大于 10 USD"));
    }

    @Test
    public void compactTitleKeepsUnknownTitlesReadableButShort() {
        assertEquals("美元指数与黄金相关性", GoldMarketCardPresenter.compactTitle(
                "2026-07-04 至 2026-08-20 美元指数与黄金相关性是否增强"));
    }

    @Test
    public void displayTitleKeepsDirectionAndAddsDurationDaysWhenStartAndEndTimesExist() {
        assertEquals("黄金 上涨 26天", GoldMarketCardPresenter.displayTitle(
                "2026-07-04 16:32 至 2026-07-30 16:20 黄金价格 上涨", 0));
    }

    @Test
    public void displayTitleKeepsThresholdAndAmountInsteadOfDeadlineTime() {
        assertEquals("金价 大于 10 USD", GoldMarketCardPresenter.displayTitle(
                "截止 2026-07-07 13:55 金价 大于 10 USD", 0));
    }

    @Test
    public void displayTitleUsesConditionWhenLegacyDescriptionOmitsThresholdAmount() {
        assertEquals("黄金价格 大于 10 USD", GoldMarketCardPresenter.displayTitle(
                "黄金目标价 07-06 20:30",
                "黄金价格 大于 10 USD (截至 2026-07-06 20:30)",
                0));
    }

    @Test
    public void displayTitleDoesNotAppendChainDeadlineToGenericTitle() {
        assertEquals("黄金预测", GoldMarketCardPresenter.displayTitle(
                "博弈池 #9", 1783440000L));
    }

    @Test
    public void displayTitleHidesSubDayDurationInsteadOfShowingHoursOrMinutes() {
        assertEquals("黄金 上涨", GoldMarketCardPresenter.displayTitle(
                "2026-07-04 16:32 至 2026-07-04 21:20 黄金价格 上涨", 0));
    }
}
