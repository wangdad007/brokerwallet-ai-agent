package com.example.brokerfi.xc.agent.gold.model.logic;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Canonical whole-day creation and resolution-rule policy shared by manual and AI flows. */
public final class GoldMarketCreationPolicy {
    public static final int RULE_VERSION = 2;
    public static final String SOURCE = "CHAINLINK_DATA_FEED_ETHEREUM";
    public static final String TIMEZONE = "Asia/Shanghai";
    public static final String BOUNDARY_POLICY = "LAST_AT_OR_BEFORE";
    public static final int MAX_STALENESS_SEC = 43200;
    public static final String XAU_USD_FEED = "0x214eD9Da11D2fbe465a6fc601a91E62EbEc1a0D6";
    public static final String BTC_USD_FEED = "0xF4030086522a5bEEa4988F8cA5B36dbC97BeE88c";
    public static final String ETH_USD_FEED = "0x5f4eC3Df9cbd43714FE2740f5E3616155c5b8419";
    public static final String SOL_USD_FEED = "0x4ffC43a60e009B551865A93d232E33Fce9f01507";
    public static final String BNB_USD_FEED = "0x14e613AC84a31f709eadbdF89C6CC390fDc9540A";

    private static final TimeZone BEIJING = TimeZone.getTimeZone(TIMEZONE);
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private GoldMarketCreationPolicy() {
    }

    public static Calendar nextValidBoundary(Calendar input) {
        Calendar original = Calendar.getInstance(BEIJING);
        original.setTimeInMillis(input.getTimeInMillis());
        Calendar candidate = midnight(original);
        if (!candidate.after(original)) candidate.add(Calendar.DAY_OF_YEAR, 1);
        while (!isSettlementWeekday(candidate.get(Calendar.DAY_OF_WEEK))) {
            candidate.add(Calendar.DAY_OF_YEAR, 1);
        }
        return candidate;
    }

    public static Calendar normalizeSelectedDate(Calendar selected) {
        Calendar candidate = Calendar.getInstance(BEIJING);
        candidate.clear();
        Calendar local = Calendar.getInstance(BEIJING);
        local.setTimeInMillis(selected.getTimeInMillis());
        candidate.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
        while (!isSettlementWeekday(candidate.get(Calendar.DAY_OF_WEEK))) {
            candidate.add(Calendar.DAY_OF_YEAR, 1);
        }
        return candidate;
    }

    public static Window normalizeWindow(Calendar now, int startDaysFromNow,
                                         int durationDays, boolean streak) {
        if (startDaysFromNow < 0) throw new IllegalArgumentException("开始日期不能早于当前日期");
        if (durationDays < 1 || durationDays > 4) {
            throw new IllegalArgumentException("观察期必须为 1 至 4 个整天");
        }
        Calendar requested = Calendar.getInstance(BEIJING);
        requested.setTimeInMillis(now.getTimeInMillis());
        requested.add(Calendar.DAY_OF_YEAR, startDaysFromNow);
        Calendar start = nextValidBoundary(requested);
        if (streak) {
            while (start.get(Calendar.DAY_OF_WEEK) + durationDays > Calendar.SATURDAY) {
                start.add(Calendar.DAY_OF_YEAR, 1);
                while (start.get(Calendar.DAY_OF_WEEK) != Calendar.TUESDAY) {
                    start.add(Calendar.DAY_OF_YEAR, 1);
                }
            }
        } else {
            while (true) {
                Calendar candidateEnd = (Calendar) start.clone();
                candidateEnd.add(Calendar.DAY_OF_YEAR, durationDays);
                if (isSettlementWeekday(candidateEnd.get(Calendar.DAY_OF_WEEK))) break;
                start = nextValidBoundary(start);
            }
        }
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, durationDays);
        return new Window(start, end);
    }

    public static Window validateSelectedWindow(Calendar selectedStart, Calendar selectedEnd,
                                                boolean streak) {
        Calendar start = normalizeSelectedDate(selectedStart);
        Calendar end = normalizeSelectedDate(selectedEnd);
        if (!end.after(start)) throw new IllegalArgumentException("截止日期必须晚于开始日期");
        int days = wholeDays(start, end);
        if (days < 1 || days > 4) throw new IllegalArgumentException("观察期必须为 1 至 4 个整天");
        if (streak) {
            Calendar cursor = (Calendar) start.clone();
            for (int i = 0; i <= days; i++) {
                if (!isSettlementWeekday(cursor.get(Calendar.DAY_OF_WEEK))) {
                    throw new IllegalArgumentException("连续涨跌观察期不能跨越周日或周一");
                }
                cursor.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return new Window(start, end);
    }

    public static boolean isValidBoundary(Calendar value) {
        Calendar local = Calendar.getInstance(BEIJING);
        local.setTimeInMillis(value.getTimeInMillis());
        return local.get(Calendar.HOUR_OF_DAY) == 0 && local.get(Calendar.MINUTE) == 0
                && local.get(Calendar.SECOND) == 0 && local.get(Calendar.MILLISECOND) == 0
                && isSettlementWeekday(local.get(Calendar.DAY_OF_WEEK));
    }

    public static JSONObject buildRule(String type, String param1, String param2,
                                       int directionIdx, int operatorIdx,
                                       Calendar start, Calendar end) {
        return new JSONObject(buildRuleValues(
                type, param1, param2, directionIdx, operatorIdx, start, end));
    }

    public static Map<String, Object> buildRuleValues(String type, String param1, String param2,
                                                      int directionIdx, int operatorIdx,
                                                      Calendar start, Calendar end) {
        if (!GoldMarketTemplateCatalog.isCreatable(type)) {
            throw new IllegalArgumentException("不支持的博弈池类型: " + type);
        }
        Window window = validateSelectedWindow(start, end,
                GoldMarketTemplateCatalog.TYPE_STREAK.equals(type));
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("rule_version", RULE_VERSION);
        rule.put("type", type);
        rule.put("symbol", "XAU");
        rule.put("source", SOURCE);
        rule.put("source_contract", XAU_USD_FEED);
        rule.put("timezone", TIMEZONE);
        rule.put("boundary_policy", BOUNDARY_POLICY);
        rule.put("max_staleness_sec", MAX_STALENESS_SEC);
        rule.put("start_time_sec", window.start.getTimeInMillis() / 1000L);
        rule.put("end_time_sec", window.end.getTimeInMillis() / 1000L);
        if (GoldMarketTemplateCatalog.TYPE_PRICE.equals(type)) {
            rule.put("direction", direction(directionIdx, true));
            rule.put("flat_tolerance_percent", 0.05d);
        } else if (GoldMarketTemplateCatalog.TYPE_RETURN_THRESHOLD.equals(type)) {
            rule.put("operator", orderedOperator(operatorIdx));
            rule.put("threshold", positiveNumber(param1, "涨跌幅阈值"));
        } else if (GoldMarketTemplateCatalog.TYPE_PRICE_THRESHOLD.equals(type)) {
            rule.put("operator", orderedOperator(operatorIdx));
            rule.put("threshold", positiveNumber(param1, "目标价格"));
        } else if (GoldMarketTemplateCatalog.TYPE_PRICE_RANGE.equals(type)) {
            double lower = positiveNumber(param1, "区间下限");
            double upper = positiveNumber(param2, "区间上限");
            if (upper <= lower) throw new IllegalArgumentException("区间上限必须大于下限");
            rule.put("operator", operatorIdx == 1 ? "OUTSIDE_RANGE" : "IN_RANGE");
            rule.put("lower_threshold", lower);
            rule.put("upper_threshold", upper);
        } else if (GoldMarketTemplateCatalog.TYPE_RELATIVE.equals(type)) {
            GoldBenchmarkCatalog.Benchmark benchmark = requireBenchmark(param1);
            rule.put("benchmark", benchmark.symbol);
            rule.put("benchmark_source_contract", benchmark.feedAddress);
        } else if (GoldMarketTemplateCatalog.TYPE_STREAK.equals(type)) {
            rule.put("direction", direction(directionIdx, false));
            rule.put("streak_days", window.durationDays());
        }
        return rule;
    }

    public static String buildTitle(String type, String param1, String param2,
                                    int directionIdx, int operatorIdx, Window window) {
        int days = window.durationDays();
        if (GoldMarketTemplateCatalog.TYPE_PRICE.equals(type)) {
            return "黄金价格 " + directionText(directionIdx) + " " + days + "天";
        }
        if (GoldMarketTemplateCatalog.TYPE_RETURN_THRESHOLD.equals(type)) {
            return "黄金涨跌幅 " + operatorText(operatorIdx) + " " + cleanNumber(param1) + "%";
        }
        if (GoldMarketTemplateCatalog.TYPE_PRICE_THRESHOLD.equals(type)) {
            return "黄金价格 " + operatorText(operatorIdx) + " " + cleanNumber(param1) + "USD/盎司";
        }
        if (GoldMarketTemplateCatalog.TYPE_PRICE_RANGE.equals(type)) {
            String relation = operatorIdx == 1 ? "不在" : "位于";
            return "黄金价格 " + relation + " " + cleanNumber(param1) + "-" + cleanNumber(param2) + "USD/盎司";
        }
        if (GoldMarketTemplateCatalog.TYPE_RELATIVE.equals(type)) {
            return "黄金 跑赢 " + requireBenchmark(param1).symbol;
        }
        if (GoldMarketTemplateCatalog.TYPE_STREAK.equals(type)) {
            return "黄金价格 连续" + directionText(directionIdx) + " " + days + "天";
        }
        throw new IllegalArgumentException("不支持的博弈池类型: " + type);
    }

    public static String buildCondition(String type, String param1, String param2,
                                        int directionIdx, int operatorIdx, Window window) {
        return buildTitle(type, param1, param2, directionIdx, operatorIdx, window)
                + "；北京时间 " + formatDate(window.start) + " 00:00 至 "
                + formatDate(window.end) + " 00:00；信源为 Ethereum Chainlink Data Feed，"
                + "取边界时刻之前最后一轮有效报价。";
    }

    public static long contractDurationSeconds(Calendar now, Calendar end) {
        long duration = (end.getTimeInMillis() - now.getTimeInMillis()) / 1000L;
        if (duration <= 0) throw new IllegalArgumentException("截止日期必须晚于当前时间");
        return duration;
    }

    private static Calendar midnight(Calendar input) {
        Calendar result = Calendar.getInstance(BEIJING);
        result.setTimeInMillis(input.getTimeInMillis());
        result.set(Calendar.HOUR_OF_DAY, 0);
        result.set(Calendar.MINUTE, 0);
        result.set(Calendar.SECOND, 0);
        result.set(Calendar.MILLISECOND, 0);
        return result;
    }

    private static boolean isSettlementWeekday(int day) {
        return day >= Calendar.TUESDAY && day <= Calendar.SATURDAY;
    }

    private static int wholeDays(Calendar start, Calendar end) {
        return (int) ((end.getTimeInMillis() - start.getTimeInMillis()) / DAY_MILLIS);
    }

    private static String direction(int index, boolean allowFlat) {
        if (index == 1) return "DOWN";
        if (allowFlat && index == 2) return "FLAT";
        if (index == 0) return "UP";
        throw new IllegalArgumentException("涨跌方向无效");
    }

    private static String directionText(int index) {
        if (index == 1) return "下跌";
        if (index == 2) return "持平";
        return "上涨";
    }

    private static String orderedOperator(int index) {
        if (index == 0) return "GTE";
        if (index == 1) return "LTE";
        throw new IllegalArgumentException("比较方式只能为大于等于或小于等于");
    }

    private static String operatorText(int index) {
        return index == 1 ? "小于等于" : "大于等于";
    }

    private static double positiveNumber(String value, String label) {
        try {
            double parsed = Double.parseDouble(value == null ? "" : value.trim());
            if (parsed <= 0 || Double.isInfinite(parsed) || Double.isNaN(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "必须为正数");
        }
    }

    private static String cleanNumber(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean isSupportedBenchmark(String symbol) {
        return GoldBenchmarkCatalog.forSymbol(symbol) != null;
    }

    private static GoldBenchmarkCatalog.Benchmark requireBenchmark(String symbol) {
        GoldBenchmarkCatalog.Benchmark benchmark = GoldBenchmarkCatalog.forSymbol(symbol);
        if (benchmark == null) {
            throw new IllegalArgumentException("跑赢率标的仅支持 BTC、ETH、SOL 或 BNB");
        }
        return benchmark;
    }

    private static String formatDate(Calendar value) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                value.get(Calendar.YEAR), value.get(Calendar.MONTH) + 1,
                value.get(Calendar.DAY_OF_MONTH));
    }

    public static final class Window {
        public final Calendar start;
        public final Calendar end;

        public Window(Calendar start, Calendar end) {
            this.start = (Calendar) start.clone();
            this.end = (Calendar) end.clone();
        }

        public int durationDays() {
            return wholeDays(start, end);
        }
    }
}
