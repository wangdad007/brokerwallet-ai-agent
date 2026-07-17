package com.example.brokerfi.xc.agent.gold.model.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GoldMarketDetailPresenter {
    private static final Pattern DATE_TIME_PATTERN =
            Pattern.compile("(?<!\\d)(?:\\d{4}-)?\\d{2}-\\d{2}"
                    + "(?:\\s+\\d{1,2}:\\d{2})?(?!\\d)");
    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("(?i)(?:[$¥￥]\\s*)?\\d+(?:\\.\\d+)?\\s*(?:(?:USD|USDT|美元|美金)(?:/盎司)?|BKC|%|天|吨|盎司|克|kg|g|tons?)");
    private static final Pattern PRICE_RANGE_AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:[$¥￥]\\s*)?\\d+(?:\\.\\d+)?\\s*[-~至到]\\s*"
                    + "\\d+(?:\\.\\d+)?\\s*(?:(?:USD|USDT|美元|美金)(?:/盎司)?)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final String[] NOUNS = {
            "美联储降息", "成交量", "收益率", "涨跌幅", "价格区间", "价格", "金价", "波动",
            "BTC", "比特币", "Bitcoin", "ETH", "以太坊", "SOL", "Solana", "BNB",
            "标普500", "S&P 500", "白银", "RSI", "MACD", "KDJ", "BOLL", "指标"
    };
    private static final String[] TIME_WORDS = {
            "截止", "截至"
    };
    private static final String[] COMPARATORS = {
            "greater than", "less than", "not below", "not above", "大于等于", "小于等于",
            "不低于", "不高于", "高于", "低于", "大于", "小于", "等于", "超过", "位于", "不在",
            "持平", "above", "below", "equal"
    };
    private static final String[] UP_TRENDS = {
            "交叉向上", "上穿", "金叉", "上涨", "上升", "触及", "达标", "发生", "跑赢", "Price Up", "UP"
    };
    private static final String[] DOWN_TRENDS = {
            "交叉向下", "下穿", "死叉", "下跌", "下降", "未达标", "Price Down", "DOWN"
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
        addKeywordParts(parts, text, NOUNS, Role.NOUN);
        addKeywordParts(parts, text, COMPARATORS, Role.COMPARATOR);
        // Match a complete price interval before individual numbers so the lower
        // bound, separator, upper bound, and unit always share one color.
        addPatternParts(parts, text, PRICE_RANGE_AMOUNT_PATTERN, Role.AMOUNT);
        addPatternParts(parts, text, AMOUNT_PATTERN, Role.AMOUNT);
        addPatternParts(parts, text, NUMBER_PATTERN, Role.AMOUNT);
        addKeywordParts(parts, text, UP_TRENDS, Role.TREND_UP);
        addKeywordParts(parts, text, DOWN_TRENDS, Role.TREND_DOWN);
        return parts;
    }

    /** Converts the metadata condition into a compact, scannable rule card. */
    public static String formatResolutionRule(String condition) {
        String normalized = condition == null ? "" : condition.trim();
        if (normalized.isEmpty()) return "判定条件\n暂无";

        String[] clauses = normalized.replace('\r', '\n').split("[；;]");
        List<String> sections = new ArrayList<>();
        for (String rawClause : clauses) {
            String clause = rawClause.trim().replaceFirst("[。.]$", "");
            if (clause.isEmpty()) continue;
            if (clause.startsWith("北京时间")) {
                sections.add("观察周期\n" + clause);
                continue;
            }
            if (clause.startsWith("信源为")) {
                String sourceAndPolicy = clause.substring("信源为".length()).trim();
                int separator = sourceAndPolicy.indexOf('，');
                if (separator < 0) separator = sourceAndPolicy.indexOf(',');
                if (separator >= 0) {
                    String source = sourceAndPolicy.substring(0, separator).trim();
                    String policy = sourceAndPolicy.substring(separator + 1).trim();
                    sections.add("数据来源\n" + source);
                    if (!policy.isEmpty()) sections.add("取价规则\n" + policy);
                } else {
                    sections.add("数据来源\n" + sourceAndPolicy);
                }
                continue;
            }
            sections.add("判定条件\n" + clause);
        }
        if (sections.isEmpty()) return "判定条件\n" + normalized;
        return joinSections(sections);
    }

    private static String joinSections(List<String> sections) {
        StringBuilder result = new StringBuilder();
        for (String section : sections) {
            if (result.length() > 0) result.append("\n\n");
            result.append(section);
        }
        return result.toString();
    }

    public static int colorForRole(Role role) {
        if (role == Role.TIME || role == Role.AMOUNT) return 0xFFB45309;
        if (role == Role.NOUN) return 0xFF2563EB;
        if (role == Role.COMPARATOR || role == Role.TREND_UP || role == Role.TREND_DOWN) {
            return 0xFF047857;
        }
        return 0xFF111827;
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
                int end = start + keyword.length();
                if (!isEmbeddedAsciiKeyword(text, keyword, start, end)) {
                    addPart(parts, text, start, end, role);
                }
                start = lower.indexOf(key, start + key.length());
            }
        }
    }

    // Short ticker symbols such as ETH must not color the prefix of a longer
    // English word such as Ethereum. Chinese text is intentionally not treated
    // as an ASCII word character, so "ETH价格" remains a valid ticker match.
    private static boolean isEmbeddedAsciiKeyword(
            String text, String keyword, int start, int end) {
        if (!isAsciiWord(keyword)) return false;
        boolean joinedOnLeft = start > 0 && isAsciiWordChar(text.charAt(start - 1));
        boolean joinedOnRight = end < text.length() && isAsciiWordChar(text.charAt(end));
        return joinedOnLeft || joinedOnRight;
    }

    private static boolean isAsciiWord(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            if (!isAsciiWordChar(value.charAt(index))) return false;
        }
        return true;
    }

    private static boolean isAsciiWordChar(char value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '_';
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
        NOUN,
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
