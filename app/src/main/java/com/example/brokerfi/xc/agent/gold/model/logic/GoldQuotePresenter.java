package com.example.brokerfi.xc.agent.gold.model.logic;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Pure formatting helpers for the compact quote header. */
public final class GoldQuotePresenter {
    private static final TimeZone BEIJING = TimeZone.getTimeZone("Asia/Shanghai");
    private static final String[] INPUT_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm"
    };

    private GoldQuotePresenter() {
    }

    public static String sourceLabel(String rawSource) {
        String source = rawSource == null ? "" : rawSource.trim();
        boolean cached = source.contains("缓存");
        if (source.toLowerCase(Locale.US).startsWith("chainlink")) {
            return cached ? "Chainlink XAU/USD（缓存）" : "Chainlink XAU/USD";
        }
        int feed = source.indexOf(" feed=");
        if (feed > 0) source = source.substring(0, feed).trim();
        int round = source.indexOf(" round=");
        if (round > 0) source = source.substring(0, round).trim();
        return source.isEmpty() ? "行情服务" : source;
    }

    public static String quoteMeta(String rawSource, String rawUpdatedAt, boolean delayed) {
        String source = sourceLabel(rawSource);
        String time = compactUpdatedAt(rawUpdatedAt);
        String suffix = delayed && !source.contains("缓存") ? " · 延迟行情" : "";
        return time.isEmpty() ? source + suffix : source + " · " + time + " 更新" + suffix;
    }

    public static String dailyChange(double change, boolean available) {
        if (!available || Double.isNaN(change) || Double.isInfinite(change)) return "24h --";
        return String.format(Locale.getDefault(), "24h %+.2f%%", change);
    }

    private static String compactUpdatedAt(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return "";
        for (String pattern : INPUT_PATTERNS) {
            SimpleDateFormat input = new SimpleDateFormat(pattern, Locale.US);
            input.setLenient(false);
            input.setTimeZone(pattern.endsWith("'Z'") ? TimeZone.getTimeZone("UTC") : BEIJING);
            try {
                Date parsed = input.parse(value);
                if (parsed == null) continue;
                SimpleDateFormat output = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
                output.setTimeZone(BEIJING);
                return output.format(parsed);
            } catch (ParseException ignored) {
            }
        }
        return value.length() > 16 ? value.substring(0, 16) : value;
    }
}
