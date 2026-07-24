package com.example.brokerfi.xc.agent.gold.view;

import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

/**
 * Fits a styled market title onto one line using its real rendered width.
 *
 * <p>Android's uniform auto-size can overestimate the available fit when the
 * title contains RelativeSizeSpan values. Measuring the final styled text
 * directly keeps the complete canonical title visible on normal phone widths.
 */
public final class GoldMarketTitleFitter {
    private static final float MAX_TEXT_SP = 18f;
    private static final float MIN_TEXT_SP = 10f;
    private static final float WIDTH_SAFETY_FACTOR = 0.97f;

    private GoldMarketTitleFitter() {
    }

    public static void apply(TextView view, CharSequence styledTitle) {
        if (view == null) return;
        CharSequence safeTitle = styledTitle == null ? "" : styledTitle;
        TextViewCompat.setAutoSizeTextTypeWithDefaults(
                view, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
        view.setSingleLine(true);
        view.setMaxLines(1);
        view.setText(safeTitle);
        view.addOnLayoutChangeListener((changedView, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) {
                fit(view, safeTitle);
            }
        });
        view.post(() -> fit(view, safeTitle));
    }

    private static void fit(TextView view, CharSequence title) {
        int availableWidth = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
        if (availableWidth <= 0) return;

        float maxTextPx = spToPx(view, MAX_TEXT_SP);
        float minTextPx = spToPx(view, MIN_TEXT_SP);
        TextPaint paint = new TextPaint(view.getPaint());
        paint.setTextSize(maxTextPx);
        float desiredWidth = Layout.getDesiredWidth(title, paint);

        float targetTextPx = maxTextPx;
        if (desiredWidth > availableWidth && desiredWidth > 0f) {
            targetTextPx = Math.max(
                    minTextPx,
                    maxTextPx * availableWidth / desiredWidth * WIDTH_SAFETY_FACTOR);
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, targetTextPx);

        paint.setTextSize(targetTextPx);
        boolean completeTitleFits =
                Layout.getDesiredWidth(title, paint) <= availableWidth + 1f;
        view.setEllipsize(completeTitleFits ? null : TextUtils.TruncateAt.END);
    }

    private static float spToPx(TextView view, float sp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                view.getResources().getDisplayMetrics());
    }
}
