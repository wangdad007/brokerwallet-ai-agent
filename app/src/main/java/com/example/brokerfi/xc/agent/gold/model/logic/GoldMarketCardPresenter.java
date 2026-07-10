package com.example.brokerfi.xc.agent.gold.model.logic;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoldMarketCardPresenter {
    private static final int FALLBACK_MAX_TITLE_LENGTH = 12;
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\d{1,2}:\\d{2}");
    private static final Pattern DATE_TIME_PATTERN =
            Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:\\s+(\\d{1,2}:\\d{2}))?");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private GoldMarketCardPresenter() {}

    public static String displayTitle(String rawTitle, long deadlineSec) {
        return displayTitle(rawTitle, "", deadlineSec);
    }

    public static String displayTitle(String rawTitle, String condition, long deadlineSec) {
        GoldMarketDetailPresenter.HeroText hero =
                GoldMarketDetailPresenter.heroText(rawTitle, deadlineSec);
        String primary = cleanTitle(hero.primaryTitle);
        if (containsAmount(primary)) {
            return primary;
        }
        String conditionTitle = cleanTitle(
                GoldMarketDetailPresenter.heroText(condition, deadlineSec).primaryTitle);
        if (containsAmount(conditionTitle)) {
            return conditionTitle;
        }
        String base = compactTitle(rawTitle);
        String durationDays = durationSuffixDays(rawTitle);
        return durationDays.isEmpty() ? base : base + " " + durationDays;
    }

    private static String cleanTitle(String title) {
        if (title == null) return "";
        return title.replaceAll("\\(\\s*\\)|（\\s*）", "").trim();
    }

    public static String compactTitle(String rawTitle) {
        String normalized = normalize(rawTitle);
        if (normalized.isEmpty()) {
            return "黄金预测";
        }
        if (normalized.startsWith("博弈池 #") || normalized.startsWith("博弈池#")) {
            return "黄金预测";
        }

        String lower = normalized.toLowerCase(Locale.US);
        if (containsAny(normalized, "黄金波幅", "波动率", "剧烈", "volatility")) {
            return "黄金波动率";
        }
        if (containsAny(normalized, "成交量", "交易量", "volume")) {
            return "黄金成交量";
        }
        if (containsAny(normalized, "rsi", "macd", "kdj", "boll", "指标", "technical")) {
            return "技术指标";
        }
        if (containsAny(normalized, "触及", "高于", "低于", "大于", "小于", "不低于", "不高于")
                || lower.contains("touched")) {
            return "黄金目标价";
        }
        if (containsAny(normalized, "黄金价格", "金价", "黄金")
                && containsAny(normalized, "上涨", "下跌", "涨", "跌", "up", "down")) {
            return "黄金涨跌";
        }

        return trimFallback(normalized);
    }

    private static boolean containsAmount(String title) {
        for (GoldMarketDetailPresenter.Part part : GoldMarketDetailPresenter.highlightParts(title)) {
            if (part.role == GoldMarketDetailPresenter.Role.AMOUNT) return true;
        }
        return false;
    }

    private static String durationSuffixDays(String rawTitle) {
        List<TimePoint> points = extractTimePoints(rawTitle);
        if (points.size() >= 2) {
            long diffMs = points.get(1).date.getTime() - points.get(0).date.getTime();
            return formatDurationDays(diffMs);
        }
        return "";
    }

    private static List<TimePoint> extractTimePoints(String rawTitle) {
        List<TimePoint> points = new ArrayList<>();
        if (rawTitle == null || rawTitle.trim().isEmpty()) {
            return points;
        }
        Matcher matcher = DATE_TIME_PATTERN.matcher(rawTitle);
        while (matcher.find()) {
            String datePart = matcher.group(1);
            String timePart = matcher.group(2);
            TimePoint point = parseTimePoint(datePart, timePart);
            if (point != null) {
                points.add(point);
            }
        }
        return points;
    }

    private static TimePoint parseTimePoint(String datePart, String timePart) {
        try {
            boolean hasTime = timePart != null && !timePart.trim().isEmpty();
            String value = hasTime ? datePart + " " + timePart : datePart;
            SimpleDateFormat formatter = new SimpleDateFormat(
                    hasTime ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd", Locale.US);
            formatter.setLenient(false);
            Date parsed = formatter.parse(value);
            return parsed == null ? null : new TimePoint(parsed, hasTime);
        } catch (ParseException e) {
            return null;
        }
    }

    private static String formatDurationDays(long diffMs) {
        if (diffMs <= 0) {
            return "";
        }
        long minutes = Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(diffMs));
        long hours = (minutes + 59) / 60;
        if (hours < 24) return "";
        long days = (hours + 23) / 24;
        return days + "天";
    }

    private static String normalize(String title) {
        if (title == null) return "";
        String cleaned = DATE_PATTERN.matcher(title).replaceAll("");
        cleaned = TIME_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = cleaned.replace("至", " ")
                .replace("截止", " ")
                .replace("截至", " ")
                .replace("判定逻辑:", " ")
                .replace("条件:", " ")
                .replace("是否增强", " ")
                .replace("是否下降", " ")
                .replace("是否上涨", " ")
                .replace("是否下跌", " ")
                .replace("是否", " ")
                .replace("能否", " ");
        return WHITESPACE_PATTERN.matcher(cleaned).replaceAll(" ").trim();
    }

    private static boolean containsAny(String text, String... candidates) {
        String lower = text.toLowerCase(Locale.US);
        for (String candidate : candidates) {
            if (lower.contains(candidate.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private static String trimFallback(String normalized) {
        if (normalized.length() <= FALLBACK_MAX_TITLE_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, FALLBACK_MAX_TITLE_LENGTH);
    }

    private static final class TimePoint {
        final Date date;
        final boolean hasClockTime;

        TimePoint(Date date, boolean hasClockTime) {
            this.date = date;
            this.hasClockTime = hasClockTime;
        }
    }
}
