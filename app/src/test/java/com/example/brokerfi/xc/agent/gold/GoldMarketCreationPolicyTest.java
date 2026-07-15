package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCreationPolicy;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GoldMarketCreationPolicyTest {
    private static final TimeZone BEIJING = TimeZone.getTimeZone("Asia/Shanghai");

    @Test
    public void normalizesMondayToTuesdayBeijingMidnight() {
        Calendar input = Calendar.getInstance(BEIJING);
        input.clear();
        input.set(2026, Calendar.JULY, 13, 15, 40);
        Calendar result = GoldMarketCreationPolicy.nextValidBoundary(input);
        assertEquals(Calendar.TUESDAY, result.get(Calendar.DAY_OF_WEEK));
        assertEquals(0, result.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, result.get(Calendar.MINUTE));
        assertEquals(BEIJING.getID(), result.getTimeZone().getID());
    }

    @Test
    public void streakWindowAvoidsWeekendAndUsesWholeDays() {
        Calendar input = Calendar.getInstance(BEIJING);
        input.clear();
        input.set(2026, Calendar.JULY, 17, 12, 0); // Friday
        GoldMarketCreationPolicy.Window window =
                GoldMarketCreationPolicy.normalizeWindow(input, 0, 3, true);
        assertEquals(Calendar.TUESDAY, window.start.get(Calendar.DAY_OF_WEEK));
        assertEquals(Calendar.FRIDAY, window.end.get(Calendar.DAY_OF_WEEK));
        assertEquals(3, window.durationDays());
        assertTrue(GoldMarketCreationPolicy.isValidBoundary(window.start));
        assertTrue(GoldMarketCreationPolicy.isValidBoundary(window.end));
    }

    @Test
    public void ordinaryWindowMovesTogetherAndPreservesRequestedDays() {
        Calendar input = Calendar.getInstance(BEIJING);
        input.clear();
        input.set(2026, Calendar.JULY, 17, 12, 0); // Friday
        GoldMarketCreationPolicy.Window window =
                GoldMarketCreationPolicy.normalizeWindow(input, 0, 2, false);
        assertEquals(Calendar.TUESDAY, window.start.get(Calendar.DAY_OF_WEEK));
        assertEquals(Calendar.THURSDAY, window.end.get(Calendar.DAY_OF_WEEK));
        assertEquals(2, window.durationDays());
        assertTrue(GoldMarketCreationPolicy.isValidBoundary(window.start));
        assertTrue(GoldMarketCreationPolicy.isValidBoundary(window.end));
    }

    @Test
    public void buildsCanonicalRelativeRule() {
        Calendar now = Calendar.getInstance(BEIJING);
        now.clear();
        now.set(2026, Calendar.JULY, 13, 12, 0);
        GoldMarketCreationPolicy.Window window =
                GoldMarketCreationPolicy.normalizeWindow(now, 0, 2, false);
        Map<String, Object> rule = GoldMarketCreationPolicy.buildRuleValues(
                "TYPE_RELATIVE", "BTC", "", 0, 0, window.start, window.end);
        assertEquals(2, rule.get("rule_version"));
        assertEquals("CHAINLINK_DATA_FEED_ETHEREUM", rule.get("source"));
        assertEquals("BTC", rule.get("benchmark"));
        assertEquals(GoldMarketCreationPolicy.BTC_USD_FEED,
                rule.get("benchmark_source_contract"));
    }

    @Test
    public void buildsEverySupportedCryptoBenchmarkRule() {
        Calendar start = Calendar.getInstance(BEIJING);
        start.clear();
        start.set(2026, Calendar.JULY, 14, 0, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 2);
        for (String symbol : new String[]{"BTC", "ETH", "SOL", "BNB"}) {
            Map<String, Object> rule = GoldMarketCreationPolicy.buildRuleValues(
                    "TYPE_RELATIVE", symbol, "", 0, 0, start, end);
            assertEquals(symbol, rule.get("benchmark"));
            assertTrue(String.valueOf(rule.get("benchmark_source_contract")).startsWith("0x"));
        }
    }

    @Test
    public void everyAiCreatableTemplateBuildsCanonicalResolvableRule() {
        Calendar start = Calendar.getInstance(BEIJING);
        start.clear();
        start.set(2026, Calendar.JULY, 14, 0, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_YEAR, 2);

        String[][] templates = new String[][]{
                {"TYPE_PRICE", "", ""},
                {"TYPE_RETURN_THRESHOLD", "2", ""},
                {"TYPE_PRICE_THRESHOLD", "4000", ""},
                {"TYPE_PRICE_RANGE", "3900", "4050"},
                {"TYPE_RELATIVE", "ETH", ""},
                {"TYPE_STREAK", "", ""}
        };
        for (String[] template : templates) {
            Map<String, Object> rule = GoldMarketCreationPolicy.buildRuleValues(
                    template[0], template[1], template[2], 0, 0, start, end);
            assertEquals(2, rule.get("rule_version"));
            assertEquals(template[0], rule.get("type"));
            assertEquals("XAU", rule.get("symbol"));
            assertEquals("CHAINLINK_DATA_FEED_ETHEREUM", rule.get("source"));
            assertEquals(GoldMarketCreationPolicy.XAU_USD_FEED,
                    rule.get("source_contract"));
            assertEquals("LAST_AT_OR_BEFORE", rule.get("boundary_policy"));
            assertEquals(start.getTimeInMillis() / 1000L, rule.get("start_time_sec"));
            assertEquals(end.getTimeInMillis() / 1000L, rule.get("end_time_sec"));
        }
    }
}
