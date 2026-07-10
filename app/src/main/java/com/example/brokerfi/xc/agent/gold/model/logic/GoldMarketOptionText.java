package com.example.brokerfi.xc.agent.gold.model.logic;

import java.util.Locale;

public final class GoldMarketOptionText {
    public static final String YES_DISPLAY = "达成 (YES)";
    public static final String NO_DISPLAY = "未达成 (NO)";
    public static final String YES_SHORT = "达成";
    public static final String NO_SHORT = "未达成";

    private GoldMarketOptionText() {}

    public static String displayName(int optionIndex) {
        return optionIndex == 1 ? NO_DISPLAY : YES_DISPLAY;
    }

    public static String shortName(int optionIndex) {
        return optionIndex == 1 ? NO_SHORT : YES_SHORT;
    }

    public static String holdingLabel(int optionIndex) {
        return optionIndex == 1 ? "持有 NO" : "持有 YES";
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
                || "达成(YES)".equals(normalized))) {
            return YES_DISPLAY;
        }
        if (optionIndex == 1 && ("NO".equals(normalized)
                || "NO(未达成)".equals(normalized)
                || "未达成(NO)".equals(normalized))) {
            return NO_DISPLAY;
        }
        return trimmed;
    }

    public static String outcomeLabel(int optionIndex, boolean winner) {
        return displayName(optionIndex) + (winner ? " 胜出" : " 未胜出");
    }

    public static String probabilityLabel(int optionIndex, float probability) {
        return String.format(Locale.getDefault(), "%s %.1f%%", shortName(optionIndex), probability);
    }

    public static String chartLabel(int optionIndex) {
        return displayName(optionIndex) + " 概率";
    }
}
