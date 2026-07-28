package com.example.brokerfi.xc.agent.gold.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

final class AiDecisionUi {
    static final int NAVY = 0xFF0F172A;
    static final int MUTED = 0xFF64748B;
    static final int BORDER = 0xFFDCE5EF;
    static final int YES = 0xFF047857;
    static final int NO = 0xFFE11D48;
    static final int BLUE = 0xFF2563EB;
    static final int AMBER = 0xFFB45309;

    private AiDecisionUi() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable rounded(int fill, int stroke, int radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != Color.TRANSPARENT) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    static TextView pill(Context context, String value, int color, int fill) {
        TextView view = text(context, value, 11, color, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6));
        view.setBackground(rounded(fill, Color.TRANSPARENT, 10, context));
        return view;
    }

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.setBackground(rounded(Color.WHITE, BORDER, 18, context));
        card.setElevation(dp(context, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(context, 10));
        card.setLayoutParams(params);
        return card;
    }

    static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(0xFFEDF2F7);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return view;
    }

    static LinearLayout node(Context context, String index, String title, String body,
                             int accent, boolean last) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        LinearLayout rail = new LinearLayout(context);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView dot = pill(context, index, Color.WHITE, accent);
        dot.setMinWidth(dp(context, 34));
        rail.addView(dot, new LinearLayout.LayoutParams(dp(context, 34), dp(context, 34)));
        if (!last) {
            View line = new View(context);
            line.setBackgroundColor(0xFFD7E2EC);
            rail.addView(line, new LinearLayout.LayoutParams(dp(context, 2), dp(context, 56)));
        }
        row.addView(rail, new LinearLayout.LayoutParams(dp(context, 42),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(context, 8), dp(context, 2), 0, dp(context, 16));
        copy.addView(text(context, title, 14, NAVY, true));
        TextView detail = text(context, body, 12, MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(context, 5);
        copy.addView(detail, detailParams);
        row.addView(copy, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }
}
