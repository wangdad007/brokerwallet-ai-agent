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
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.SUBJECT, "黄金价格"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.TREND_UP, "上涨"));
    }

    @Test
    public void highlightPartsIdentifyDownTrend() {
        List<GoldMarketDetailPresenter.Part> parts = GoldMarketDetailPresenter.highlightParts(
                "截止 2026-07-30 金价 下跌");

        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.SUBJECT, "金价"));
        assertTrue(contains(parts, GoldMarketDetailPresenter.Role.TREND_DOWN, "下跌"));
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
        org.junit.Assert.assertEquals("截止 2026-07-06 15:00", hero.timeSubtitle);
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
