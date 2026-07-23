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
        if (startDaysFromNow < 0) throw new IllegalArgumentException("Start date cannot be earlier than today");
        if (durationDays < 1 || durationDays > 4) {
            throw new IllegalArgumentException("Observation period must be 1–4 full days");
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

    /** Returns the most recent fully elapsed whole-day window for demo settlement. */
    public static Window latestExpiredWindow(Calendar now, int durationDays, boolean streak) {
        if (durationDays < 1 || durationDays > 4) {
            throw new IllegalArgumentException("Observation period must be 1–4 full days");
        }
        Calendar candidateEnd = midnight(now);
        for (int attempt = 0; attempt < 14; attempt++) {
            Calendar candidateStart = (Calendar) candidateEnd.clone();
            candidateStart.add(Calendar.DAY_OF_YEAR, -durationDays);
            try {
                Window window = validateSelectedWindow(candidateStart, candidateEnd, streak);
                if (window.durationDays() == durationDays && !window.end.after(now)) {
                    return window;
                }
            } catch (IllegalArgumentException ignored) {
                // Move to the previous boundary until a complete valid window is found.
            }
            candidateEnd.add(Calendar.DAY_OF_YEAR, -1);
        }
        throw new IllegalArgumentException("Unable to find a recent completed observation period");
    }

    public static Window validateSelectedWindow(Calendar selectedStart, Calendar selectedEnd,
                                                boolean streak) {
        Calendar start = normalizeSelectedDate(selectedStart);
        Calendar end = normalizeSelectedDate(selectedEnd);
        if (!end.after(start)) throw new IllegalArgumentException("End date must be later than the start date");
        int days = wholeDays(start, end);
        if (days < 1 || days > 4) throw new IllegalArgumentException("Observation period must be 1–4 full days");
        if (streak) {
            Calendar cursor = (Calendar) start.clone();
            for (int i = 0; i <= days; i++) {
                if (!isSettlementWeekday(cursor.get(Calendar.DAY_OF_WEEK))) {
                    throw new IllegalArgumentException("A direction streak cannot cross Sunday or Monday");
                }
                cursor.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        return new Window(start, end);
    }

    /**
     * Returns a valid 1–4 whole-day end boundary while preserving a manually
     * selected start date. This is used by the date picker so invalid windows
     * cannot be selected and rejected only at deployment time.
     */
    public static Calendar defaultEndForSelectedStart(Calendar selectedStart,
                                                       int preferredDurationDays,
                                                       boolean streak) {
        Calendar start = normalizeSelectedDate(selectedStart);
        int preferred = preferredDurationDays >= 1 && preferredDurationDays <= 4
                ? preferredDurationDays : 2;
        int[] candidates = {preferred, 1, 2, 3, 4};
        for (int days : candidates) {
            Calendar end = (Calendar) start.clone();
            end.add(Calendar.DAY_OF_YEAR, days);
            try {
                validateSelectedWindow(start, end, streak);
                return end;
            } catch (IllegalArgumentException ignored) {
                // Try another duration that still falls within the 1–4 day rule.
            }
        }
        throw new IllegalArgumentException("The selected start date cannot form a 1–4 day observation period");
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
            throw new IllegalArgumentException("Unsupported market type: " + type);
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
            rule.put("threshold", positiveNumber(param1, "Return threshold"));
        } else if (GoldMarketTemplateCatalog.TYPE_PRICE_THRESHOLD.equals(type)) {
            rule.put("operator", orderedOperator(operatorIdx));
            rule.put("threshold", positiveNumber(param1, "Target price"));
        } else if (GoldMarketTemplateCatalog.TYPE_PRICE_RANGE.equals(type)) {
            double lower = positiveNumber(param1, "Lower bound");
            double upper = positiveNumber(param2, "Upper bound");
            if (upper <= lower) throw new IllegalArgumentException("Upper bound must exceed the lower bound");
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
            return "Gold Price " + directionText(directionIdx) + " Over " + days + " "
                    + (days == 1 ? "Day" : "Days");
        }
        if (GoldMarketTemplateCatalog.TYPE_RETURN_THRESHOLD.equals(type)) {
            return "Gold Absolute Return " + operatorText(operatorIdx) + " "
                    + cleanNumber(param1) + "%";
        }
        if (GoldMarketTemplateCatalog.TYPE_PRICE_THRESHOLD.equals(type)) {
            return "Gold Price " + operatorText(operatorIdx) + " " + cleanNumber(param1) + " USD/oz";
        }
        if (GoldMarketTemplateCatalog.TYPE_PRICE_RANGE.equals(type)) {
            if (operatorIdx == 1) {
                return "Gold Price Outside " + cleanNumber(param1) + "–"
                        + cleanNumber(param2) + " USD/oz";
            }
            return "Gold Price Between " + cleanNumber(param1) + " and "
                    + cleanNumber(param2) + " USD/oz";
        }
        if (GoldMarketTemplateCatalog.TYPE_RELATIVE.equals(type)) {
            return "Gold Outperforms " + requireBenchmark(param1).symbol;
        }
        if (GoldMarketTemplateCatalog.TYPE_STREAK.equals(type)) {
            return "Gold Price " + directionText(directionIdx) + " for " + days
                    + (days == 1 ? " Day" : " Days");
        }
        throw new IllegalArgumentException("Unsupported market type: " + type);
    }

    public static String buildCondition(String type, String param1, String param2,
                                        int directionIdx, int operatorIdx, Window window) {
        return buildTitle(type, param1, param2, directionIdx, operatorIdx, window)
                + "; Beijing time " + formatDate(window.start) + " 00:00 to "
                + formatDate(window.end) + " 00:00; source: Chainlink XAU/USD Data Feed on Ethereum; "
                + "use the final valid quote at or before each boundary.";
    }

    public static long contractDurationSeconds(Calendar now, Calendar end) {
        return contractDurationSeconds(now, end, false, 1L);
    }

    /**
     * Produces the contract duration without changing the historical observation
     * window stored in the resolution rule. The deployed contract rejects zero
     * duration, so demo mode uses its advertised minimum positive duration.
     */
    public static long contractDurationSeconds(Calendar now, Calendar end,
                                               boolean allowExpiredMarketCreation,
                                               long demoDurationSeconds) {
        long duration = (end.getTimeInMillis() - now.getTimeInMillis()) / 1000L;
        if (duration <= 0) {
            if (allowExpiredMarketCreation) return Math.max(1L, demoDurationSeconds);
            throw new IllegalArgumentException("End date must be later than the current time");
        }
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
        throw new IllegalArgumentException("Invalid price direction");
    }

    private static String directionText(int index) {
        if (index == 1) return "Falls";
        if (index == 2) return "Remains Flat";
        return "Rises";
    }

    private static String orderedOperator(int index) {
        if (index == 0) return "GTE";
        if (index == 1) return "LTE";
        throw new IllegalArgumentException("Comparison must be At Least or At Most");
    }

    private static String operatorText(int index) {
        return index == 1 ? "At Most" : "At Least";
    }

    private static double positiveNumber(String value, String label) {
        try {
            double parsed = Double.parseDouble(value == null ? "" : value.trim());
            if (parsed <= 0 || Double.isInfinite(parsed) || Double.isNaN(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + " must be a positive number");
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
            throw new IllegalArgumentException("Outperformance supports BTC, ETH, SOL or BNB");
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
