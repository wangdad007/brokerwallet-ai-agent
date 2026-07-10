package com.example.brokerfi.xc.agent.gold.model.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoldMarketDetailPresenter {
    private static final Pattern DATE_TIME_PATTERN =
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}(?:\\s+\\d{1,2}:\\d{2})?");
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?i)(?:[$¥￥]\\s*)?\\d+(?:\\.\\d+)?\\s*(?:USD|USDT|美元|美金|BKC|%|吨|盎司|克|kg|g|tons?)");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final String[] SUBJECTS = {
            "黄金价格", "黄金波幅", "黄金收益率", "金价", "黄金", "成交量", "技术指标", "指标"
    };
    private static final String[] TIME_WORDS = {
            "截止", "截至"
    };
    private static final String[] COMPARATORS = {
            "greater than", "less than", "not below", "not above", "大于等于", "小于等于",
            "不低于", "不高于", "高于", "低于", "大于", "小于", "等于", "超过",
            "above", "below", "equal"
    };
    private static final String[] UP_TRENDS = {
            "上涨", "上升", "触及", "达标", "Price Up", "UP"
    };
    private static final String[] DOWN_TRENDS = {
            "下跌", "下降", "未达标", "Price Down", "DOWN"
    };

    private GoldMarketDetailPresenter() {}

    public static HeroText heroText(String rawTitle, long deadlineSec) {
        String title = rawTitle == null ? "" : rawTitle.trim();
        List<String> times = new ArrayList<>();
        Matcher matcher = DATE_TIME_PATTERN.matcher(title);
        while (matcher.find()) {
            times.add(matcher.group());
        }

        String primary = matcherReplace(title)
                .replace("至", " ")
                .replace("截止", " ")
                .replace("截至", " ");
        primary = SPACE_PATTERN.matcher(primary).replaceAll(" ").trim();
        if (primary.isEmpty() || primary.startsWith("博弈池 #") || primary.startsWith("博弈池#")) {
            primary = "黄金预测";
        }

        String subtitle = "";
        if (times.size() >= 2) {
            subtitle = times.get(0) + " - " + times.get(1);
        } else if (times.size() == 1) {
            boolean deadlineLike = title.contains("截止") || title.contains("截至");
            subtitle = deadlineLike ? "截止 " + times.get(0) : times.get(0);
        } else if (deadlineSec > 0) {
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            subtitle = "截止 " + formatter.format(new java.util.Date(deadlineSec * 1000L));
        }

        return new HeroText(primary, subtitle);
    }

    private static String matcherReplace(String text) {
        return DATE_TIME_PATTERN.matcher(text).replaceAll(" ");
    }

    public static List<Part> highlightParts(String text) {
        List<Part> parts = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return parts;
        }
        addPatternParts(parts, text, DATE_TIME_PATTERN, Role.TIME);
        addKeywordParts(parts, text, TIME_WORDS, Role.TIME);
        addKeywordParts(parts, text, SUBJECTS, Role.SUBJECT);
        addKeywordParts(parts, text, COMPARATORS, Role.COMPARATOR);
        addPatternParts(parts, text, AMOUNT_PATTERN, Role.AMOUNT);
        addKeywordParts(parts, text, UP_TRENDS, Role.TREND_UP);
        addKeywordParts(parts, text, DOWN_TRENDS, Role.TREND_DOWN);
        return parts;
    }

    private static void addPatternParts(List<Part> parts, String text, Pattern pattern, Role role) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            addPart(parts, text, matcher.start(), matcher.end(), role);
        }
    }

    private static void addKeywordParts(List<Part> parts, String text, String[] keywords, Role role) {
        String lower = text.toLowerCase(Locale.US);
        for (String keyword : keywords) {
            String key = keyword.toLowerCase(Locale.US);
            int start = lower.indexOf(key);
            while (start >= 0) {
                addPart(parts, text, start, start + keyword.length(), role);
                start = lower.indexOf(key, start + key.length());
            }
        }
    }

    private static void addPart(List<Part> parts, String text, int start, int end, Role role) {
        if (start < 0 || end <= start || end > text.length()) return;
        for (Part existing : parts) {
            if (start < existing.end && end > existing.start) {
                return;
            }
        }
        parts.add(new Part(start, end, text.substring(start, end), role));
    }

    public enum Role {
        TIME,
        SUBJECT,
        COMPARATOR,
        AMOUNT,
        TREND_UP,
        TREND_DOWN
    }

    public static final class Part {
        public final int start;
        public final int end;
        public final String text;
        public final Role role;

        public Part(int start, int end, String text, Role role) {
            this.start = start;
            this.end = end;
            this.text = text;
            this.role = role;
        }
    }

    public static final class HeroText {
        public final String primaryTitle;
        public final String timeSubtitle;

        public HeroText(String primaryTitle, String timeSubtitle) {
            this.primaryTitle = primaryTitle;
            this.timeSubtitle = timeSubtitle;
        }
    }
}
