package com.example.brokerfi.xc.agent.gold.logic;

import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GoldMarketResearchPromptBuilder {
    private static final BigDecimal TOKEN_UNIT = new BigDecimal("1000000000000000000");
    private static final BigInteger MIN_DISPLAYABLE_SHARE_WEI =
            new BigInteger("1000000000000");
    private static final long MILLIS_THRESHOLD = 10_000_000_000L;
    private static final String SUMMARY_CONTRACT =
            "请只分析这个博弈池，用中文给出不超过120字的摘要：\n"
                    + "1. 当前哪一侧证据更强；\n"
                    + "2. 两个主要依据；\n"
                    + "3. 最大风险与不确定性；\n"
                    + "4. 明确说明这只是投研辅助，不保证收益。";

    private GoldMarketResearchPromptBuilder() {
    }

    public static String buildContext(
            GoldMarketRepository.GameModel game,
            long nowMillis,
            GoldAdvisoryManager.Advisory quote) {
        List<String> lines = new ArrayList<>();

        if (game == null) {
            lines.add("博弈池信息不可用");
        } else {
            lines.add("博弈池 #" + game.id);
            addIfPresent(lines, "标题/描述: ", game.desc);
            addIfPresent(lines, "结算条件: ", game.condition);
            addIfPresent(lines, "详细信息: ", game.detailedInfo);
            addOptions(lines, game.optionNames);
            addProbabilities(lines, game.virtualReserves);
            lines.add("总池子: " + formatBkc(game.totalPool) + " BKC");

            long remainingSeconds = remainingSeconds(
                    game.deadlineSec, nowMillis);
            lines.add("市场状态: " + marketStatus(game, remainingSeconds));
            lines.add("剩余时间: " + formatRemainingTime(remainingSeconds));
            addHoldings(lines, game.optionNames, game.myShares);
        }

        addQuote(lines, quote);
        return joinLines(lines);
    }

    public static String buildSummaryPrompt(String context) {
        return safe(context) + "\n\n" + SUMMARY_CONTRACT;
    }

    public static String withFollowUp(String context, String question) {
        String safeQuestion = safe(question);
        if (context == null || context.trim().isEmpty()) {
            return safeQuestion;
        }
        return context + "\n\n【用户追问】\n" + safeQuestion;
    }

    private static void addIfPresent(
            List<String> lines, String prefix, String value) {
        if (value != null && !value.trim().isEmpty()) {
            lines.add(prefix + value.trim());
        }
    }

    private static void addOptions(List<String> lines, List<String> optionNames) {
        if (optionNames == null || optionNames.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (String optionName : optionNames) {
            if (optionName != null && !optionName.trim().isEmpty()) {
                names.add(optionName.trim());
            }
        }
        if (!names.isEmpty()) {
            lines.add("选项: " + join(names, " / "));
        }
    }

    private static void addProbabilities(
            List<String> lines, List<BigInteger> virtualReserves) {
        if (virtualReserves == null || virtualReserves.size() < 2) {
            return;
        }
        BigInteger yesReserve = virtualReserves.get(0);
        BigInteger noReserve = virtualReserves.get(1);
        if (yesReserve == null || noReserve == null
                || yesReserve.signum() < 0 || noReserve.signum() < 0) {
            return;
        }
        BigInteger total = yesReserve.add(noReserve);
        if (total.signum() <= 0) {
            return;
        }

        BigDecimal yesPercent = roundedPercent(yesReserve, total);
        BigDecimal noPercent = new BigDecimal("100.0").subtract(yesPercent);
        lines.add("YES 概率: " + formatPercent(yesPercent));
        lines.add("NO 概率: " + formatPercent(noPercent));
    }

    private static BigDecimal roundedPercent(
            BigInteger reserve, BigInteger total) {
        return new BigDecimal(reserve)
                .multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(total), 1, RoundingMode.HALF_UP);
    }

    private static String formatPercent(BigDecimal percent) {
        return percent.setScale(1, RoundingMode.UNNECESSARY).toPlainString() + "%";
    }

    private static String formatBkc(BigInteger value) {
        if (value == null) {
            return "0.00";
        }
        return new BigDecimal(value)
                .divide(TOKEN_UNIT, 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private static long remainingSeconds(long rawDeadline, long nowMillis) {
        if (rawDeadline <= 0) {
            return 0;
        }
        long deadlineMillis = rawDeadline > MILLIS_THRESHOLD
                ? rawDeadline
                : rawDeadline * 1000L;
        long difference = deadlineMillis - nowMillis;
        return difference > 0 ? difference / 1000L : 0;
    }

    private static String marketStatus(
            GoldMarketRepository.GameModel game, long remainingSeconds) {
        if (game.isRefunded) {
            return "已退款";
        }
        if (game.isResolved) {
            return "已结算";
        }
        if (remainingSeconds <= 0) {
            return "已到期，待结算";
        }
        return "进行中";
    }

    private static String formatRemainingTime(long remainingSeconds) {
        if (remainingSeconds <= 0) {
            return "0秒";
        }
        long days = remainingSeconds / 86_400L;
        long hours = remainingSeconds % 86_400L / 3_600L;
        long minutes = remainingSeconds % 3_600L / 60L;
        long seconds = remainingSeconds % 60L;
        StringBuilder result = new StringBuilder();
        if (days > 0) {
            result.append(days).append("天 ");
        }
        if (hours > 0) {
            result.append(hours).append("小时 ");
        }
        if (minutes > 0) {
            result.append(minutes).append("分钟 ");
        }
        if (seconds > 0) {
            result.append(seconds).append("秒");
        }
        return result.toString().trim();
    }

    private static void addHoldings(
            List<String> lines,
            List<String> optionNames,
            List<BigInteger> shares) {
        if (shares == null) {
            return;
        }
        for (int index = 0; index < shares.size(); index++) {
            BigInteger amount = shares.get(index);
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            lines.add(optionName(optionNames, index)
                    + " " + formatShare(amount) + " 份额");
        }
    }

    private static String optionName(List<String> optionNames, int index) {
        if (optionNames != null && index < optionNames.size()) {
            String name = optionNames.get(index);
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        return "选项" + (index + 1);
    }

    private static String formatShare(BigInteger amount) {
        if (amount.compareTo(MIN_DISPLAYABLE_SHARE_WEI) < 0) {
            return "<0.000001";
        }
        BigDecimal shares = new BigDecimal(amount)
                .divide(TOKEN_UNIT, 6, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return shares.toPlainString();
    }

    private static void addQuote(
            List<String> lines, GoldAdvisoryManager.Advisory quote) {
        if (quote == null || !Double.isFinite(quote.priceUsd)
                || quote.priceUsd <= 0) {
            lines.add("行情数据不可用");
            return;
        }

        lines.add(String.format(
                Locale.US, "黄金现价: %.2f USD", quote.priceUsd));
        if (Double.isFinite(quote.change24h)) {
            lines.add(String.format(
                    Locale.US, "日涨跌: %+.2f%%", quote.change24h));
        } else {
            lines.add("日涨跌: 数据不可用");
        }
        lines.add("行情来源: " + valueOrUnknown(quote.quoteSource));
        lines.add("行情更新时间: " + valueOrUnknown(quote.quoteUpdatedAt));
        lines.add("延迟行情: " + (quote.quoteDelayed ? "是" : "否"));
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.trim().isEmpty() ? "未知" : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String joinLines(List<String> lines) {
        return join(lines, "\n");
    }

    private static String join(List<String> values, String delimiter) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(delimiter);
            }
            result.append(value);
        }
        return result.toString();
    }
}
