package com.example.brokerfi.xc.agent.gold.view;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketChartPresenter;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class GoldMarketChartMarkerView extends MarkerView {
    private final TextView timeView;
    private final TextView yesView;
    private final TextView noView;
    private final TextView sourceView;
    private final SimpleDateFormat formatter = new SimpleDateFormat("MMM d, HH:mm", Locale.US);
    private final SimpleDateFormat timeOnlyFormatter = new SimpleDateFormat("HH:mm", Locale.US);

    public GoldMarketChartMarkerView(Context context) {
        super(context, R.layout.view_probability_chart_marker);
        timeView = findViewById(R.id.tv_marker_time);
        yesView = findViewById(R.id.tv_marker_yes);
        noView = findViewById(R.id.tv_marker_no);
        sourceView = findViewById(R.id.tv_marker_source);
    }

    @Override
    public void refreshContent(Entry entry, Highlight highlight) {
        Object payload = entry.getData();
        if (payload instanceof GoldMarketChartPresenter.SharePoint) {
            GoldMarketChartPresenter.SharePoint point =
                    (GoldMarketChartPresenter.SharePoint) payload;
            setTime(point.timestampSec);
            yesView.setVisibility(View.VISIBLE);
            noView.setVisibility(View.VISIBLE);
            yesView.setText(String.format(Locale.US, "YES 份额 %.1f%%", point.yesShare));
            noView.setText(String.format(Locale.US, "NO 份额 %.1f%%", point.noShare));
            sourceView.setVisibility(View.GONE);
        } else if (payload instanceof GoldMarketChartPresenter.TradePoint) {
            GoldMarketChartPresenter.TradePoint trade =
                    (GoldMarketChartPresenter.TradePoint) payload;
            if (trade.purchaseCount > 1 && trade.bucketEndSec > trade.bucketStartSec) {
                timeView.setText(formatter.format(new Date(trade.bucketStartSec * 1000L))
                        + "–" + timeOnlyFormatter.format(
                        new Date(trade.bucketEndSec * 1000L)));
            } else {
                setTime(trade.timestampSec);
            }
            boolean isYes = trade.optionId == 0;
            yesView.setVisibility(isYes ? View.VISIBLE : View.GONE);
            noView.setVisibility(isYes ? View.GONE : View.VISIBLE);
            String label = String.format(Locale.US, "%s 份额 %.1f%%",
                    isYes ? "YES" : "NO", trade.marketShare);
            if (isYes) yesView.setText(label); else noView.setText(label);
            sourceView.setVisibility(View.VISIBLE);
            String source = GoldMarketChartPresenter.executionSourceLabel(
                    trade.executionSource);
            sourceView.setText(trade.purchaseCount == 1
                    ? source
                    : String.format(Locale.US, "%s · 该时间段共 %d 笔购买",
                    source, trade.purchaseCount));
        }
        super.refreshContent(entry, highlight);
    }

    private void setTime(long timestampSec) {
        timeView.setText(formatter.format(new Date(timestampSec * 1000L)));
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-getWidth() / 2f, -getHeight() - 12f);
    }
}
