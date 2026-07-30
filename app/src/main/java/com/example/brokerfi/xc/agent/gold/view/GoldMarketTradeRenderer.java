package com.example.brokerfi.xc.agent.gold.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketChartPresenter;
import com.github.mikephil.charting.animation.ChartAnimator;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.renderer.LineChartRenderer;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.utils.Utils;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Draws compact source-aware personal-execution annotations above the market curves. */
public final class GoldMarketTradeRenderer extends LineChartRenderer {
    private static final int AI_BLUE = 0xFF2563EB;
    private static final int GRID_TEAL = 0xFF0F766E;
    private static final int MARTINGALE_AMBER = 0xFFB45309;
    private static final int YES_COLOR = 0xFF059669;
    private static final int NO_COLOR = 0xFFE11D48;
    private static final int INK = 0xFF0F172A;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF badgeBounds = new RectF();
    private final float[] pointBuffer = new float[2];

    public GoldMarketTradeRenderer(LineDataProvider chart, ChartAnimator animator,
                                   ViewPortHandler viewPortHandler) {
        super(chart, animator, viewPortHandler);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1.5f));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD));
    }

    @Override
    public void drawExtras(Canvas canvas) {
        super.drawExtras(canvas);
        if (mChart.getLineData() == null) return;

        List<RenderMarker> markers = new ArrayList<>();
        for (ILineDataSet dataSet : mChart.getLineData().getDataSets()) {
            if (!dataSet.isVisible()) continue;
            Transformer transformer = mChart.getTransformer(dataSet.getAxisDependency());
            int visibleCount = Math.min(dataSet.getEntryCount(),
                    Math.max(0, (int) Math.ceil(dataSet.getEntryCount() * mAnimator.getPhaseX())));
            for (int index = 0; index < visibleCount; index++) {
                Entry entry = dataSet.getEntryForIndex(index);
                if (!(entry.getData() instanceof GoldMarketChartPresenter.TradePoint)) continue;
                GoldMarketChartPresenter.TradePoint trade =
                        (GoldMarketChartPresenter.TradePoint) entry.getData();
                pointBuffer[0] = entry.getX();
                pointBuffer[1] = entry.getY() * mAnimator.getPhaseY();
                transformer.pointValuesToPixel(pointBuffer);
                float x = pointBuffer[0];
                float y = pointBuffer[1];
                if (!mViewPortHandler.isInBounds(x, y)) continue;
                markers.add(new RenderMarker(trade, x, y,
                        markerHalfWidth(trade), markerHalfHeight(trade)));
            }
        }
        layoutMarkers(markers);
        for (RenderMarker marker : markers) drawConnector(canvas, marker);
        for (RenderMarker marker : markers) drawMarker(canvas, marker);
    }

    private void layoutMarkers(List<RenderMarker> markers) {
        markers.sort(Comparator
                .comparingInt((RenderMarker marker) -> marker.trade.optionId)
                .thenComparingDouble(marker -> marker.x));
        float[][] lastRightByTier = {
                {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY},
                {Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY}
        };
        float gap = dp(3f);
        for (RenderMarker marker : markers) {
            int option = marker.trade.optionId == 1 ? 1 : 0;
            int chosenTier = 0;
            for (int tier = 0; tier < lastRightByTier[option].length; tier++) {
                chosenTier = tier;
                if (marker.x - marker.halfWidth
                        >= lastRightByTier[option][tier] + gap) break;
            }
            marker.tier = chosenTier;
            lastRightByTier[option][chosenTier] = marker.x + marker.halfWidth;
            float baseOffset = marker.halfHeight + dp(8f);
            float tierOffset = chosenTier * (marker.halfHeight * 2f + dp(4f));
            marker.markerY = Math.max(marker.halfHeight + dp(2f),
                    marker.curveY - baseOffset - tierOffset);
        }
    }

    private void drawConnector(Canvas canvas, RenderMarker marker) {
        float connectorStart = marker.markerY + marker.halfHeight + dp(1f);
        float connectorEnd = marker.curveY - dp(1f);
        if (connectorEnd <= connectorStart) return;
        stroke.setColor(optionColor(marker.trade));
        stroke.setAlpha(95);
        stroke.setStrokeWidth(dp(1f));
        canvas.drawLine(marker.x, connectorStart, marker.x, connectorEnd, stroke);
        stroke.setAlpha(255);
        stroke.setStrokeWidth(dp(1.5f));
    }

    private void drawMarker(Canvas canvas, RenderMarker marker) {
        String markerLabel = GoldMarketChartPresenter.executionSourceMarker(
                marker.trade.executionSource);
        String label = marker.trade.purchaseCount > 1
                ? markerLabel + " ×" + marker.trade.purchaseCount : markerLabel;
        drawTradePill(canvas, marker.x, marker.markerY, marker.trade,
                sourceColor(marker.trade.executionSource), label);
    }

    private void drawTradePill(Canvas canvas, float x, float y,
                               GoldMarketChartPresenter.TradePoint trade,
                               int innerColor, String label) {
        text.setTextSize(dp(6.2f));
        float halfWidth = Math.max(dp(8.5f), text.measureText(label) / 2f + dp(5f));
        float outerHalfHeight = dp(8.5f);
        badgeBounds.set(x - halfWidth, y - outerHalfHeight,
                x + halfWidth, y + outerHalfHeight);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(optionColor(trade));
        canvas.drawRoundRect(badgeBounds, dp(6f), dp(6f), fill);

        float inset = dp(1.8f);
        badgeBounds.inset(inset, inset);
        fill.setColor(innerColor);
        canvas.drawRoundRect(badgeBounds, dp(4.5f), dp(4.5f), fill);
        text.setColor(Color.WHITE);
        drawCenteredText(canvas, label, x, y, text);
    }

    private float markerHalfWidth(GoldMarketChartPresenter.TradePoint trade) {
        String markerLabel = GoldMarketChartPresenter.executionSourceMarker(
                trade.executionSource);
        String label = trade.purchaseCount > 1
                ? markerLabel + " ×" + trade.purchaseCount : markerLabel;
        text.setTextSize(dp(6.2f));
        return Math.max(dp(8.5f), text.measureText(label) / 2f + dp(5f));
    }

    private static float markerHalfHeight(GoldMarketChartPresenter.TradePoint trade) {
        return dp(8.5f);
    }

    private static int optionColor(GoldMarketChartPresenter.TradePoint trade) {
        return trade.optionId == 1 ? NO_COLOR : YES_COLOR;
    }

    private static int sourceColor(String source) {
        switch (GoldMarketChartPresenter.normalizeExecutionSource(source, false)) {
            case "ai":
                return AI_BLUE;
            case "grid":
                return GRID_TEAL;
            case "martingale":
                return MARTINGALE_AMBER;
            default:
                return INK;
        }
    }

    private static void drawCenteredText(Canvas canvas, String value,
                                         float x, float y, Paint paint) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(value, x, y - (metrics.ascent + metrics.descent) / 2f, paint);
    }

    private static float dp(float value) {
        return Utils.convertDpToPixel(value);
    }

    private static final class RenderMarker {
        final GoldMarketChartPresenter.TradePoint trade;
        final float x;
        final float curveY;
        final float halfWidth;
        final float halfHeight;
        int tier;
        float markerY;

        RenderMarker(GoldMarketChartPresenter.TradePoint trade, float x, float curveY,
                     float halfWidth, float halfHeight) {
            this.trade = trade;
            this.x = x;
            this.curveY = curveY;
            this.halfWidth = halfWidth;
            this.halfHeight = halfHeight;
        }
    }
}
