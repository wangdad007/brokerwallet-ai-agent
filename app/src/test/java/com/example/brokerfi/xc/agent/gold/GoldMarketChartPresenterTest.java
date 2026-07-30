package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketChartPresenter;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldMarketChartPresenterTest {
    @Test
    public void preparesSortedSharesAndInterpolatedManualAndAiPurchases() {
        BackendApiClient.HistoryPointDTO later = history(1_700_000_600L, 70f, 30f);
        BackendApiClient.HistoryPointDTO first = history(1_700_000_000L, 50f, 50f);
        BackendApiClient.HistoryPointDTO duplicate = history(1_700_000_600L, 72f, 28f);

        BackendApiClient.TradeDTO manualYes = trade(1_700_000_300L, 0, false);
        BackendApiClient.TradeDTO aiNo = trade(1_700_000_600L, 1, true);
        BackendApiClient.TradeDTO failed = trade(1_700_000_300L, 0, true);
        failed.isSuccess = false;
        BackendApiClient.TradeDTO sell = trade(1_700_000_300L, 1, false);
        sell.tradeType = "SELL";

        GoldMarketChartPresenter.ChartModel model = GoldMarketChartPresenter.prepare(
                Arrays.asList(later, first, duplicate),
                Arrays.asList(aiNo, failed, manualYes, sell));

        assertEquals(1_700_000_000L, model.baseTimestampSec);
        assertTrue(model.shares.size() > 3);
        assertEquals(72f, model.shares.get(model.shares.size() - 1).yesShare, 0.001f);
        assertEquals(2, model.trades.size());
        assertFalse(model.trades.get(0).aiManaged);
        assertEquals(61f, model.trades.get(0).marketShare, 0.001f);
        assertTrue(model.trades.get(1).aiManaged);
        assertEquals(28f, model.trades.get(1).marketShare, 0.001f);
        assertEquals(28f, shareAt(model, 1_700_000_600L, 1), 0.001f);
        assertEquals(10f, model.xOf(1_700_000_600L), 0.001f);
    }

    @Test
    public void filtersPurchasesOutsideTheVisibleRangeAndClampsShares() {
        BackendApiClient.TradeDTO beforeRange = trade(999L, 0, false);
        GoldMarketChartPresenter.ChartModel model = GoldMarketChartPresenter.prepare(
                Collections.singletonList(history(1_000L, 120f, -20f)),
                Collections.singletonList(beforeRange));

        assertEquals(100f, model.shares.get(0).yesShare, 0.001f);
        assertEquals(0f, model.shares.get(0).noShare, 0.001f);
        assertEquals(2, model.shares.size());
        assertTrue(model.trades.isEmpty());
    }

    @Test
    public void keepsFreshAiPurchaseJustAfterLatestSamplerPoint() {
        BackendApiClient.TradeDTO aiTrade = trade(1_120L, 0, true);
        GoldMarketChartPresenter.ChartModel model = GoldMarketChartPresenter.prepare(
                Arrays.asList(history(1_000L, 50f, 50f), history(1_060L, 55f, 45f)),
                Collections.singletonList(aiTrade));

        assertEquals(1, model.trades.size());
        assertTrue(model.trades.get(0).aiManaged);
        assertEquals(55f, model.trades.get(0).marketShare, 0.001f);
        assertTrue(model.shares.size() > 2);
        assertEquals(55f, model.shares.get(model.shares.size() - 1).yesShare, 0.001f);
    }

    @Test
    public void purchaseMarkersNeverCreateOrReshapeTheMarketCurve() {
        java.util.List<BackendApiClient.HistoryPointDTO> history = Arrays.asList(
                history(1_000L, 50f, 50f),
                history(1_300L, 65f, 35f),
                history(1_900L, 55f, 45f));
        GoldMarketChartPresenter.ChartModel withoutTrades =
                GoldMarketChartPresenter.prepare(history, Collections.emptyList());
        GoldMarketChartPresenter.ChartModel withTrades = GoldMarketChartPresenter.prepare(
                history, Arrays.asList(trade(1_150L, 0, false), trade(1_500L, 1, true)));

        assertEquals(withoutTrades.shares.size(), withTrades.shares.size());
        for (int i = 0; i < withoutTrades.shares.size(); i++) {
            GoldMarketChartPresenter.SharePoint expected = withoutTrades.shares.get(i);
            GoldMarketChartPresenter.SharePoint actual = withTrades.shares.get(i);
            assertEquals(expected.timestampSec, actual.timestampSec);
            assertEquals(expected.yesShare, actual.yesShare, 0.001f);
            assertEquals(expected.noShare, actual.noShare, 0.001f);
        }
        assertEquals(2, withTrades.trades.size());
        assertEquals(57.5f, withTrades.trades.get(0).marketShare, 0.001f);
        assertEquals(38.333f, withTrades.trades.get(1).marketShare, 0.01f);
    }

    @Test
    public void groupsPersonalPurchasesByRangeWithoutMixingManualAndDeepSeekTrades() {
        GoldMarketChartPresenter.ChartModel model = GoldMarketChartPresenter.prepare(
                Arrays.asList(history(1_000L, 50f, 50f), history(30_000L, 70f, 30f)),
                Arrays.asList(
                        trade(1_810L, 0, false),
                        trade(1_850L, 0, false),
                        trade(1_870L, 0, true),
                        strategyTrade(1_875L, 0, "grid"),
                        strategyTrade(1_880L, 0, "martingale"),
                        trade(1_890L, 1, false)));

        GoldMarketChartPresenter.TradeAggregation oneHour =
                GoldMarketChartPresenter.aggregateTrades(model.trades, "1h", 3_600L);
        assertEquals(5L * 60L, oneHour.bucketSeconds);
        assertEquals(5, oneHour.trades.size());
        GoldMarketChartPresenter.TradePoint groupedManualYes =
                findTrade(oneHour, 0, false);
        assertEquals(2, groupedManualYes.purchaseCount);
        assertEquals("2000000000000000000", groupedManualYes.amountWei);
        assertEquals(1, findTrade(oneHour, 0, true).purchaseCount);
        assertEquals(1, findTradeBySource(oneHour, 0, "grid").purchaseCount);
        assertEquals(1, findTradeBySource(oneHour, 0, "martingale").purchaseCount);
        assertEquals(1, findTrade(oneHour, 1, false).purchaseCount);

        assertEquals(2L * 60L * 60L,
                GoldMarketChartPresenter.aggregateTrades(model.trades, "1d", 86_400L)
                        .bucketSeconds);
        assertEquals(24L * 60L * 60L,
                GoldMarketChartPresenter.aggregateTrades(model.trades, "1w", 604_800L)
                        .bucketSeconds);
        assertEquals(2L * 60L * 60L,
                GoldMarketChartPresenter.aggregateTrades(model.trades, "all", 86_400L)
                        .bucketSeconds);
        assertEquals("5 分钟", GoldMarketChartPresenter.bucketLabel(oneHour.bucketSeconds));
    }

    @Test
    public void smoothingIsContinuousNormalizedAndDoesNotOvershootRecordedRange() {
        GoldMarketChartPresenter.ChartModel model = GoldMarketChartPresenter.prepare(
                Arrays.asList(
                        history(1_000L, 50f, 50f),
                        history(1_060L, 60f, 40f),
                        history(1_600L, 80f, 20f)),
                Collections.emptyList());

        assertTrue(model.shares.size() > 3);
        long previousTimestamp = Long.MIN_VALUE;
        for (GoldMarketChartPresenter.SharePoint point : model.shares) {
            assertTrue(point.timestampSec > previousTimestamp);
            assertTrue(point.yesShare >= 50f && point.yesShare <= 80f);
            assertEquals(100f, point.yesShare + point.noShare, 0.01f);
            previousTimestamp = point.timestampSec;
        }
        assertEquals(50f, model.shares.get(0).yesShare, 0.001f);
        assertEquals(80f, model.shares.get(model.shares.size() - 1).yesShare, 0.001f);
    }

    @Test
    public void rapidOpposingTradesRemainExactStepsInsteadOfSmoothedArtifacts() {
        GoldMarketChartPresenter.ChartModel model = GoldMarketChartPresenter.prepare(
                Arrays.asList(
                        history(1_000L, 79f, 21f),
                        history(1_004L, 14f, 86f),
                        history(1_165L, 99f, 1f)),
                Collections.emptyList());

        assertEquals(79f, shareAt(model, 1_003L, 0), 0.001f);
        assertEquals(14f, shareAt(model, 1_004L, 0), 0.001f);
        assertEquals(14f, shareAt(model, 1_164L, 0), 0.001f);
        assertEquals(99f, shareAt(model, 1_165L, 0), 0.001f);
    }

    @Test
    public void rejectsFutureDatedPurchases() {
        BackendApiClient.TradeDTO future = trade(
                System.currentTimeMillis() / 1000L + 3_600L, 0, false);
        GoldMarketChartPresenter.ChartModel model = GoldMarketChartPresenter.prepare(
                Collections.singletonList(history(1_000L, 50f, 50f)),
                Collections.singletonList(future));

        assertTrue(model.trades.isEmpty());
    }

    private static float shareAt(GoldMarketChartPresenter.ChartModel model,
                                 long timestamp, int optionId) {
        for (GoldMarketChartPresenter.SharePoint point : model.shares) {
            if (point.timestampSec == timestamp) {
                return optionId == 1 ? point.noShare : point.yesShare;
            }
        }
        throw new AssertionError("missing line point at " + timestamp);
    }

    private static GoldMarketChartPresenter.TradePoint findTrade(
            GoldMarketChartPresenter.TradeAggregation aggregation,
            int optionId, boolean aiManaged) {
        for (GoldMarketChartPresenter.TradePoint trade : aggregation.trades) {
            if (trade.optionId == optionId && trade.aiManaged == aiManaged) return trade;
        }
        throw new AssertionError("missing aggregated purchase marker");
    }

    private static GoldMarketChartPresenter.TradePoint findTradeBySource(
            GoldMarketChartPresenter.TradeAggregation aggregation,
            int optionId, String executionSource) {
        for (GoldMarketChartPresenter.TradePoint trade : aggregation.trades) {
            if (trade.optionId == optionId
                    && executionSource.equals(trade.executionSource)) return trade;
        }
        throw new AssertionError("missing aggregated source marker " + executionSource);
    }

    private static BackendApiClient.HistoryPointDTO history(long timestamp, float yes, float no) {
        BackendApiClient.HistoryPointDTO point = new BackendApiClient.HistoryPointDTO();
        point.timestampSec = timestamp;
        point.yesPrice = yes;
        point.noPrice = no;
        return point;
    }

    private static BackendApiClient.TradeDTO trade(long timestamp, int option, boolean aiManaged) {
        BackendApiClient.TradeDTO trade = new BackendApiClient.TradeDTO();
        trade.timestampSec = timestamp;
        trade.optionId = option;
        trade.isAiManaged = aiManaged;
        trade.isSuccess = true;
        trade.tradeType = "BUY";
        trade.amountWei = "1000000000000000000";
        return trade;
    }

    private static BackendApiClient.TradeDTO strategyTrade(
            long timestamp, int option, String executionSource) {
        BackendApiClient.TradeDTO trade = trade(timestamp, option, true);
        trade.executionSource = executionSource;
        return trade;
    }
}
