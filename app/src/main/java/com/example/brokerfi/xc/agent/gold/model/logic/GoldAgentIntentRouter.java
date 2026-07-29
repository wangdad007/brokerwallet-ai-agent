package com.example.brokerfi.xc.agent.gold.model.logic;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A deliberately small, on-device intent router for the AI workbench.
 *
 * <p>Financial actions are selected from a closed, reviewable allow-list before
 * any model answer is rendered. It is not an order executor and never handles a
 * private key. The language model is used for research; this router decides
 * which product workflow may be offered to the user.</p>
 */
public final class GoldAgentIntentRouter {
    private static final Pattern GAME_ID = Pattern.compile(
            "(?:#\\s*(\\d+)|第\\s*(\\d+)\\s*号?(?:池|博弈池|市场)?|(\\d+)\\s*号(?:池|博弈池|市场))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:BKC|bkc|币|份额|份)", Pattern.CASE_INSENSITIVE);

    private GoldAgentIntentRouter() {
    }

    public static Result route(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        int gameId = findGameId(lower);
        Double amount = findAmount(lower);

        if (containsAny(lower, "自动托管", "托管策略", "凯利", "kelly", "置信度门槛")) {
            return new Result(Intent.AI_MANAGED_STRATEGY, gameId, amount,
                    "AI 托管策略草稿", false);
        }
        if (containsAny(lower, "创建博弈", "创建市场", "创建博弈池", "建一个", "建池", "新建市场")) {
            return new Result(Intent.CREATE_MARKET_DRAFT, gameId, amount,
                    "博弈池创建草稿", false);
        }
        if (containsAny(lower, "方向研判", "选项研判", "交易方向", "方向判断")) {
            return new Result(Intent.DIRECTION_JUDGMENT, gameId, amount,
                    "博弈方向研判", false);
        }
        if (containsAny(lower, "买入", "购买", "下注", "下单", "买 yes", "买 no",
                "买yes", "买no", "投入", "卖出", "减持", "卖 yes", "卖 no",
                "卖yes", "卖no", "交易模拟", "模拟交易")) {
            return new Result(Intent.TRADE_SIMULATION, gameId, amount,
                    "交易方向研判", true);
        }
        if (containsAny(lower, "持仓", "我的仓位", "我的收益", "我的亏损", "仓位风险", "诊断")) {
            return new Result(Intent.POSITION_DIAGNOSIS, gameId, amount,
                    "个人持仓诊断", false);
        }
        if (containsAny(lower, "对比", "比较", "哪个好", "哪一个", "筛选", "推荐博弈池")) {
            return new Result(Intent.MARKET_COMPARE, gameId, amount,
                    "博弈池对比", false);
        }
        if (containsAny(lower, "行情", "分析", "趋势", "金价", "黄金", "市场", "风险", "概率")) {
            return new Result(Intent.MARKET_ANALYSIS, gameId, amount,
                    "黄金市场分析", false);
        }
        return new Result(Intent.GENERAL_RESEARCH, gameId, amount, "投研问答", false);
    }

    private static boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private static int findGameId(String text) {
        Matcher matcher = GAME_ID.matcher(text);
        while (matcher.find()) {
            try {
                String raw = matcher.group(1) != null ? matcher.group(1)
                        : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                int value = Integer.parseInt(raw);
                if (value > 0 && value < 1_000_000) return value;
            } catch (NumberFormatException ignored) {
                // Continue looking for another explicit id.
            }
        }
        return -1;
    }

    private static Double findAmount(String text) {
        Matcher matcher = AMOUNT.matcher(text);
        if (!matcher.find()) return null;
        try {
            double value = Double.parseDouble(matcher.group(1));
            return value > 0d && Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public enum Intent {
        MARKET_ANALYSIS,
        MARKET_COMPARE,
        POSITION_DIAGNOSIS,
        DIRECTION_JUDGMENT,
        TRADE_SIMULATION,
        CREATE_MARKET_DRAFT,
        AI_MANAGED_STRATEGY,
        GENERAL_RESEARCH
    }

    public static final class Result {
        public final Intent intent;
        public final int gameId;
        public final Double amountBkc;
        public final String displayName;
        public final boolean requiresConfirmation;

        private Result(Intent intent, int gameId, Double amountBkc, String displayName,
                       boolean requiresConfirmation) {
            this.intent = intent;
            this.gameId = gameId;
            this.amountBkc = amountBkc;
            this.displayName = displayName;
            this.requiresConfirmation = requiresConfirmation;
        }
    }
}
