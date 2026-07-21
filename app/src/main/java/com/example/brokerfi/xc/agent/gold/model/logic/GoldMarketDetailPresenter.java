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
            Pattern.compile("(?i)(?:[$¥￥]\\s*)?\\d+(?:\\.\\d+)?\\s*"
                    + "(?:(?:USD|USDT|美元|美金)(?:/(?:oz|盎司))?|BKC|%|天|"
                    + "(?:consecutive\\s+)?days?|hours?|吨|盎司|克|kg|g|tons?)");
    private static final Pattern PRICE_RANGE_AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:[$¥￥]\\s*)?\\d+(?:\\.\\d+)?\\s*"
                    + "(?:[-–—~至到]|\\band\\b)\\s*"
                    + "\\d+(?:\\.\\d+)?\\s*(?:(?:USD|USDT|美元|美金)(?:/(?:oz|盎司))?)");
    private static final Pattern BETWEEN_RANGE_PATTERN = Pattern.compile(
            "(?i)\\bBetween\\s+(\\d+(?:\\.\\d+)?)\\s*[-–—~]\\s*"
                    + "(\\d+(?:\\.\\d+)?)\\s*(USD(?:/oz)?)");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private static final String[] NOUNS = {
            "美联储降息", "成交量", "收益率", "涨跌幅", "价格区间", "价格", "金价", "波动",
            "Price", "Return", "Volatility", "Volume", "Technical Indicator",
            "BTC", "比特币", "Bitcoin", "ETH", "以太坊", "SOL", "Solana", "BNB",
            "标普500", "S&P 500", "白银", "RSI", "MACD", "KDJ", "BOLL", "指标"
    };
    private static final String[] TIME_WORDS = {
            "截止", "截至"
    };
    private static final String[] COMPARATORS = {
            "at least", "at most", "greater than", "less than", "not below", "not above",
            "between", "outside", "outperforms", "touches", "crosses above", "crosses below",
            "大于等于", "小于等于",
            "不低于", "不高于", "高于", "低于", "大于", "小于", "等于", "超过", "位于", "不在",
            "持平", "above", "below", "equal"
    };
    private static final String[] UP_TRENDS = {
            "交叉向上", "上穿", "金叉", "上涨", "上升", "触及", "达标", "发生", "跑赢",
            "Price Up", "Bullish", "Rises", "Up"
    };
    private static final String[] DOWN_TRENDS = {
            "交叉向下", "下穿", "死叉", "下跌", "下降", "未达标",
            "Price Down", "Bearish", "Falls", "Down"
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
            primary = "Gold Market";
        }

        String subtitle = "";
        if (times.size() >= 2) {
            subtitle = times.get(0) + " - " + times.get(1);
        } else if (times.size() == 1) {
            boolean deadlineLike = title.contains("截止") || title.contains("截至");
            subtitle = deadlineLike ? "Ends " + times.get(0) : times.get(0);
        } else if (deadlineSec > 0) {
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            subtitle = "Ends " + formatter.format(new java.util.Date(deadlineSec * 1000L));
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
        if (normalized.isEmpty()) return "Resolution Rule\nUnavailable";

        String[] clauses = normalized.replace('\r', '\n').split("[；;]");
        List<String> sections = new ArrayList<>();
        for (String rawClause : clauses) {
            String clause = rawClause.trim().replaceFirst("[。.]$", "");
            if (clause.isEmpty()) continue;
            if (startsWithIgnoreCase(clause, "Beijing time") || clause.startsWith("北京时间")) {
                sections.add("Observation Period\n" + translateLegacyClause(clause));
                continue;
            }
            if (startsWithIgnoreCase(clause, "source:")) {
                sections.add("Data Source\n" + normalizeDataSource(
                        clause.substring(clause.indexOf(':') + 1).trim()));
                continue;
            }
            if (startsWithIgnoreCase(clause, "use the final valid quote")) {
                sections.add("Pricing Policy\n" + capitalizeSentence(clause));
                continue;
            }
            if (clause.startsWith("信源为")) {
                String sourceAndPolicy = clause.substring("信源为".length()).trim();
                int separator = sourceAndPolicy.indexOf('，');
                if (separator < 0) separator = sourceAndPolicy.indexOf(',');
                if (separator >= 0) {
                    String source = sourceAndPolicy.substring(0, separator).trim();
                    String policy = sourceAndPolicy.substring(separator + 1).trim();
                    sections.add("Data Source\n" + normalizeDataSource(source));
                    if (!policy.isEmpty()) {
                        sections.add("Pricing Policy\n" + translateLegacyClause(policy));
                    }
                } else {
                    sections.add("Data Source\n" + normalizeDataSource(sourceAndPolicy));
                }
                continue;
            }
            sections.add("Resolution Rule\n" + translateLegacyClause(clause));
        }
        if (sections.isEmpty()) return "Resolution Rule\n" + translateLegacyClause(normalized);
        return joinSections(sections);
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String normalizeDataSource(String source) {
        String normalized = source == null ? "" : source.trim();
        if (normalized.equalsIgnoreCase("Ethereum Chainlink Data Feed")) {
            return "Chainlink XAU/USD Data Feed on Ethereum";
        }
        return normalized;
    }

    private static String capitalizeSentence(String value) {
        if (value == null || value.isEmpty()) return "";
        int first = value.codePointAt(0);
        int upper = Character.toUpperCase(first);
        if (first == upper) return value;
        return new String(Character.toChars(upper))
                + value.substring(Character.charCount(first));
    }

    /** Converts legacy Chinese template metadata without changing stored settlement rules. */
    private static String translateLegacyClause(String raw) {
        String translated = raw == null ? "" : raw.trim();
        String[][] replacements = {
                {"取边界时刻之前最后一轮有效报价", "Use the final valid quote at or before each boundary"},
                {"美联储降息", "Federal Reserve Rate Cut"},
                {"连续上涨", "Rises on each consecutive day"},
                {"连续下跌", "Falls on each consecutive day"},
                {"黄金价格", "Gold Price"},
                {"黄金收益率", "Gold Absolute Return"},
                {"金价", "Gold Price"},
                {"价格区间", "Price Range"},
                {"涨跌幅", "Absolute Return"},
                {"波动率", "Volatility"},
                {"波幅", "Volatility"},
                {"成交量", "Trading Volume"},
                {"交易量", "Trading Volume"},
                {"技术指标", "Technical Indicator"},
                {"相对基准", "relative to the baseline"},
                {"大于等于", "At Least"},
                {"小于等于", "At Most"},
                {"不低于", "At Least"},
                {"不高于", "At Most"},
                {"曾触及", "Touched"},
                {"跑赢", "Outperforms"},
                {"上涨", "Rises"},
                {"下跌", "Falls"},
                {"持平", "Remains Flat"},
                {"位于", "Between"},
                {"不在", "Outside"},
                {"大于", "Above"},
                {"小于", "Below"},
                {"等于", "Equals"},
                {"超过", "Above"},
                {"触及", "Touches"},
                {"发生", "Occurs"},
                {"北京时间", "Beijing time"},
                {"USD/盎司", "USD/oz"},
                {"美元/盎司", "USD/oz"},
                {"盎司", "oz"},
                {"，", ", "},
                {"至", " to "}
        };
        for (String[] replacement : replacements) {
            translated = translated.replace(replacement[0], replacement[1]);
        }
        translated = SPACE_PATTERN.matcher(translated).replaceAll(" ").trim();
        translated = BETWEEN_RANGE_PATTERN.matcher(translated)
                .replaceAll("Between $1 and $2 $3");
        return containsHan(translated)
                ? "This legacy market uses its original structured resolution rule."
                : translated;
    }

    private static boolean containsHan(String value) {
        for (int offset = 0; value != null && offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if ((codePoint >= 0x3400 && codePoint <= 0x4DBF)
                    || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                    || (codePoint >= 0xF900 && codePoint <= 0xFAFF)) return true;
            offset += Character.charCount(codePoint);
        }
        return false;
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
