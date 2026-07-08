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
    public void displayTitleAddsDurationSuffixWhenStartAndEndTimesExist() {
        assertEquals("黄金涨跌 26天", GoldMarketCardPresenter.displayTitle(
                "2026-07-04 16:32 至 2026-07-30 16:20 黄金价格 上涨", 0));
    }

    @Test
    public void displayTitleAddsTargetTimeSuffixWhenOnlyDeadlineExists() {
        assertEquals("黄金目标价 07-07 13:55", GoldMarketCardPresenter.displayTitle(
                "截止 2026-07-07 13:55 金价 大于 10 USD", 0));
    }

    @Test
    public void displayTitleUsesChainDeadlineWhenDescriptionHasNoTime() {
        assertEquals("黄金预测 07-08 00:00", GoldMarketCardPresenter.displayTitle(
                "博弈池 #9", 1783440000L));
    }
}
