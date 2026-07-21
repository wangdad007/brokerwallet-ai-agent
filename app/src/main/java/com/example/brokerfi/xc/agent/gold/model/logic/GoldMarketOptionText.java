package com.example.brokerfi.xc.agent.gold.model.logic;

import java.util.Locale;

public final class GoldMarketOptionText {
    public static final String YES_DISPLAY = "YES";
    public static final String NO_DISPLAY = "NO";
    public static final String YES_SHORT = "YES";
    public static final String NO_SHORT = "NO";

    private GoldMarketOptionText() {}

    public static String displayName(int optionIndex) {
        return optionIndex == 1 ? NO_DISPLAY : YES_DISPLAY;
    }

    public static String shortName(int optionIndex) {
        return optionIndex == 1 ? NO_SHORT : YES_SHORT;
    }

    public static String holdingLabel(int optionIndex) {
        return optionIndex == 1 ? "Holding NO" : "Holding YES";
    }

    public static String displayName(String rawName, int optionIndex) {
        String fallback = displayName(optionIndex);
        if (rawName == null || rawName.trim().isEmpty()) {
            return fallback;
        }
        String trimmed = rawName.trim();
        String normalized = trimmed
                .replace("（", "(")
                .replace("）", ")")
                .replace(" ", "")
                .toUpperCase(Locale.US);
        if (optionIndex == 0 && ("YES".equals(normalized)
                || "YES(达成)".equals(normalized)
                || "达成(YES)".equals(normalized)
                || "达成".equals(normalized))) {
            return YES_DISPLAY;
        }
        if (optionIndex == 1 && ("NO".equals(normalized)
                || "NO(未达成)".equals(normalized)
                || "未达成(NO)".equals(normalized)
                || "未达成".equals(normalized))) {
            return NO_DISPLAY;
        }
        return trimmed;
    }

    public static String outcomeLabel(int optionIndex, boolean winner) {
        return displayName(optionIndex) + (winner ? " Won" : " Did not win");
    }

    public static String shareLabel(int optionIndex, float share) {
        return String.format(Locale.getDefault(), "%s %.1f%%", chartLabel(optionIndex), share);
    }

    public static String chartLabel(int optionIndex) {
        return displayName(optionIndex) + " Share";
    }
}
