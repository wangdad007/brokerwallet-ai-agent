package com.example.brokerfi.xc.agent.gold.view;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketDetailPresenter;

/** Applies the shared market-title color, weight, and size system. */
public final class GoldMarketTextStyler {
    private GoldMarketTextStyler() {
    }

    public static CharSequence style(String text, boolean title) {
        String safeText = text == null ? "" : text;
        SpannableStringBuilder styled = new SpannableStringBuilder(safeText);
        for (GoldMarketDetailPresenter.Part part : GoldMarketDetailPresenter.highlightParts(safeText)) {
            styled.setSpan(
                    new ForegroundColorSpan(GoldMarketDetailPresenter.colorForRole(part.role)),
                    part.start,
                    part.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    part.start,
                    part.end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (title) {
                float scale = part.role == GoldMarketDetailPresenter.Role.TIME ? 0.86f : 1.05f;
                styled.setSpan(
                        new RelativeSizeSpan(scale),
                        part.start,
                        part.end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return styled;
    }
}
