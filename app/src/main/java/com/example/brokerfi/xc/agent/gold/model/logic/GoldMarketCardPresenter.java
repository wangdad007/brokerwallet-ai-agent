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
    private static final Pattern DAY_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*天");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
    private static final Pattern USD_PATTERN =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(USD|USDT|美元|美金)", Pattern.CASE_INSENSITIVE);
    private static final Pattern USD_RANGE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*[-~至到]\\s*(\\d+(?:\\.\\d+)?)\\s*(?:USD|USDT|美元|美金)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TON_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*吨");
    private static final Pattern INDICATOR_PATTERN =
            Pattern.compile("\\b(RSI|MACD|KDJ|BOLL)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TECHNICAL_VALUE_PATTERN = Pattern.compile(
            "(?:大于|小于|等于|高于|低于|超过|above|below|equal)(?:\\s*\\([^)]*\\))?\\s*(\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private GoldMarketCardPresenter() {}

    public static String displayTitle(String rawTitle, long deadlineSec) {
        return displayTitle(rawTitle, "", deadlineSec);
    }

    public static String displayTitle(String rawTitle, String condition, long deadlineSec) {
        String semanticTitle = semanticTitle(rawTitle, condition, deadlineSec);
        if (!semanticTitle.isEmpty()) {
            return semanticTitle;
        }
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
        String directionalTitle = directionalTitle(rawTitle);
        if (!directionalTitle.isEmpty()) {
            String durationDays = durationSuffixDays(rawTitle);
            return durationDays.isEmpty() ? directionalTitle : directionalTitle + " " + durationDays;
        }
        String base = compactTitle(rawTitle);
        String durationDays = durationSuffixDays(rawTitle);
        return durationDays.isEmpty() ? base : base + " " + durationDays;
    }

    private static String semanticTitle(String rawTitle, String condition, long deadlineSec) {
        String raw = rawTitle == null ? "" : rawTitle;
        String combined = raw + " " + (condition == null ? "" : condition);

        String streak = streakTitle(combined);
        if (!streak.isEmpty()) return streak;

        String returnThreshold = returnThresholdTitle(combined);
        if (!returnThreshold.isEmpty()) return returnThreshold;

        String range = priceRangeTitle(combined);
        if (!range.isEmpty()) return range;

        String directional = directionalTitle(combined);
        if (!directional.isEmpty()) {
            String days = durationSuffixDays(raw);
            if (days.isEmpty()) days = explicitDurationDays(raw);
            if (days.isEmpty()) days = deadlineSuffixDays(deadlineSec);
            return days.isEmpty() ? directional : directional + " " + days;
        }

        String event = eventTitle(raw);
        if (!event.isEmpty()) return event;

        String volatility = volatilityTitle(combined);
        if (!volatility.isEmpty()) return volatility;

        String relative = relativeTitle(combined);
        if (!relative.isEmpty()) return relative;

        String touch = touchTitle(combined);
        if (!touch.isEmpty()) return touch;

        String volume = volumeTitle(combined);
        if (!volume.isEmpty()) return volume;

        String technical = technicalTitle(combined);
        if (!technical.isEmpty()) return technical;

        return thresholdTitle(combined);
    }

    private static String streakTitle(String text) {
        if (!containsAny(text, "连续") || !containsAny(text, "上涨", "下跌")) return "";
        Matcher days = DAY_PATTERN.matcher(text);
        if (!days.find()) return "";
        String direction = containsAny(text, "下跌") ? "下跌" : "上涨";
        return "黄金价格 连续" + direction + " " + days.group(1) + "天";
    }

    private static String returnThresholdTitle(String text) {
        if (!containsAny(text, "涨跌幅")) return "";
        Matcher amount = PERCENT_PATTERN.matcher(text);
        if (!amount.find()) return "";
        return "黄金涨跌幅 " + comparatorFor(text, "大于等于")
                + " " + amount.group(1) + "%";
    }

    private static String priceRangeTitle(String text) {
        if (!containsAny(text, "位于", "不在", "区间")) return "";
        Matcher range = USD_RANGE_PATTERN.matcher(text);
        if (!range.find()) return "";
        String relation = containsAny(text, "不在") ? "不在" : "位于";
        return "黄金价格 " + relation + " " + range.group(1) + "-"
                + range.group(2) + "USD/盎司";
    }

    private static String eventTitle(String rawTitle) {
        String normalized = normalize(rawTitle);
        int eventIndex = normalized.indexOf("发生");
        if (eventIndex < 0) return "";
        String event = normalized.substring(eventIndex + "发生".length())
                .replaceFirst("^[：:「“\\s]+", "")
                .replaceAll("[」”？?\\s]+$", "").trim();
        return event.isEmpty() ? "" : "发生 " + event;
    }

    private static String volatilityTitle(String text) {
        if (!containsAny(text, "波幅", "波动", "波动率", "volatility")) return "";
        Matcher amount = PERCENT_PATTERN.matcher(text);
        if (!amount.find()) return "";
        return "黄金波动 " + comparatorFor(text, "大于") + " " + amount.group(1) + "%";
    }

    private static String relativeTitle(String text) {
        int index = text.indexOf("跑赢");
        if (index < 0) return "";
        String asset = benchmarkName(text.substring(index + "跑赢".length()));
        if (asset.isEmpty()) return "";
        return "黄金 跑赢 " + asset;
    }

    private static String benchmarkName(String text) {
        if (containsAny(text, "比特币", "BTC", "Bitcoin")) return "BTC";
        if (containsAny(text, "以太坊", "ETH", "Ethereum")) return "ETH";
        if (containsAny(text, "SOL", "Solana")) return "SOL";
        if (containsAny(text, "BNB", "Binance Coin")) return "BNB";
        if (containsAny(text, "标普500", "S&P 500", "S&P")) return "标普500";
        if (containsAny(text, "白银")) return "白银";
        return text.replaceFirst("^[：:「“\\s]+", "")
                .replaceAll("\\s*\\(.*", "")
                .replaceAll("[，,].*", "")
                .replaceAll("[）)\\s]+$", "").trim();
    }

    private static String technicalTitle(String text) {
        Matcher indicator = INDICATOR_PATTERN.matcher(text);
        if (!indicator.find()) return "";
        String name = indicator.group(1).toUpperCase(Locale.US);
        if (containsAny(text, "上穿信号线", "上穿")) return "黄金" + name + " 上穿信号线";
        if (containsAny(text, "交叉向上", "金叉", "cross up")) return "黄金" + name + " 交叉向上";
        if (containsAny(text, "下穿信号线", "下穿")) return "黄金" + name + " 下穿信号线";
        if (containsAny(text, "交叉向下", "死叉", "cross down")) return "黄金" + name + " 交叉向下";
        Matcher value = TECHNICAL_VALUE_PATTERN.matcher(text);
        if (value.find()) {
            return "黄金" + name + " " + comparatorFor(text, "大于") + " " + value.group(1);
        }
        return "黄金" + name + " 指标";
    }

    private static String touchTitle(String text) {
        if (!containsAny(text, "触及", "touch")) return "";
        Matcher amount = USD_PATTERN.matcher(text);
        if (!amount.find()) return "";
        return "黄金价格 触及 " + amount.group(1) + "USD/盎司";
    }

    private static String volumeTitle(String text) {
        if (!containsAny(text, "成交量", "交易量", "volume")) return "";
        Matcher amount = TON_PATTERN.matcher(text);
        if (!amount.find()) return "";
        return "黄金成交量 " + comparatorFor(text, "大于") + " " + amount.group(1) + "吨";
    }

    private static String thresholdTitle(String text) {
        Matcher amount = USD_PATTERN.matcher(text);
        if (!amount.find() || !containsAny(text, "大于", "小于", "等于", "高于", "低于", "超过")) return "";
        return "黄金价格 " + comparatorFor(text, "大于") + " "
                + amount.group(1) + "USD/盎司";
    }

    private static String comparatorFor(String text, String fallback) {
        if (containsAny(text, "小于等于", "不高于")) return "小于等于";
        if (containsAny(text, "大于等于", "不低于")) return "大于等于";
        if (containsAny(text, "小于", "低于", "below", "less than")) return "小于";
        if (containsAny(text, "等于", "equal")) return "等于";
        if (containsAny(text, "大于", "高于", "超过", "above", "greater than", "达到")) return "大于";
        return fallback;
    }

    private static String explicitDurationDays(String title) {
        Matcher matcher = DAY_PATTERN.matcher(title == null ? "" : title);
        return matcher.find() ? matcher.group(1) + "天" : "";
    }

    private static String deadlineSuffixDays(long deadlineSec) {
        if (deadlineSec <= 0L) return "";
        long diffMs = deadlineSec * 1000L - System.currentTimeMillis();
        if (diffMs <= 0L) return "";
        long hours = Math.max(1L, (diffMs + TimeUnit.HOURS.toMillis(1L) - 1L)
                / TimeUnit.HOURS.toMillis(1L));
        return ((hours + 23L) / 24L) + "天";
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

    private static String directionalTitle(String rawTitle) {
        String text = rawTitle == null ? "" : rawTitle.toLowerCase(Locale.US);
        boolean goldRelated = text.contains("黄金") || text.contains("金价") || text.contains("gold");
        if (!goldRelated) return "";
        if (text.contains("下跌") || text.contains("下降") || text.contains("price down")
                || text.contains(" down")) {
            return "黄金价格 下跌";
        }
        if (text.contains("上涨") || text.contains("上升") || text.contains("price up")
                || text.contains(" up")) {
            return "黄金价格 上涨";
        }
        if (text.contains("持平") || text.contains("横盘") || text.contains("flat")
                || text.contains("range")) {
            return "黄金价格 持平";
        }
        return "";
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
