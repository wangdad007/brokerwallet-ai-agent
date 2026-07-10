package com.example.brokerfi.xc.agent.gold.model.logic;

public final class GoldMarketStatusStyle {
    public static final int ACTIVE_TEXT = 0xFF047857;
    public static final int ACTIVE_BACKGROUND = 0xFFE7F6EF;
    public static final int PENDING_TEXT = 0xFFB45309;
    public static final int PENDING_BACKGROUND = 0xFFFFF4DE;
    public static final int RESOLVED_TEXT = 0xFF2563EB;
    public static final int RESOLVED_BACKGROUND = 0xFFEAF1FF;
    public static final int REFUNDED_TEXT = 0xFF64748B;
    public static final int REFUNDED_BACKGROUND = 0xFFF1F5F9;
    public static final int YES_TEXT = 0xFF047857;
    public static final int YES_BACKGROUND = 0xFFE6FAF2;
    public static final int NO_TEXT = 0xFFE11D48;
    public static final int NO_BACKGROUND = 0xFFFFEEF2;

    public final String label;
    public final int textColor;
    public final int backgroundColor;

    private GoldMarketStatusStyle(String label, int textColor, int backgroundColor) {
        this.label = label;
        this.textColor = textColor;
        this.backgroundColor = backgroundColor;
    }

    public static GoldMarketStatusStyle forMarket(boolean isResolved, boolean isRefunded, long remainingSeconds) {
        if (isRefunded) {
            return new GoldMarketStatusStyle("已退款", REFUNDED_TEXT, REFUNDED_BACKGROUND);
        }
        if (isResolved) {
            return new GoldMarketStatusStyle("已开奖", RESOLVED_TEXT, RESOLVED_BACKGROUND);
        }
        if (remainingSeconds <= 0) {
            return new GoldMarketStatusStyle("等待裁决", PENDING_TEXT, PENDING_BACKGROUND);
        }
        return new GoldMarketStatusStyle("运行中", ACTIVE_TEXT, ACTIVE_BACKGROUND);
    }

    public static GoldMarketStatusStyle forMarketOutcome(boolean isResolved,
                                                         boolean isRefunded,
                                                         long remainingSeconds,
                                                         int winningOption,
                                                         String yesName,
                                                         String noName) {
        if (!isResolved || isRefunded) {
            return forMarket(isResolved, isRefunded, remainingSeconds);
        }
        boolean noWins = winningOption == 1;
        String winnerName = GoldMarketOptionText.displayName(noWins ? noName : yesName, noWins ? 1 : 0);
        return new GoldMarketStatusStyle(
                winnerName + " 胜出",
                noWins ? NO_TEXT : YES_TEXT,
                noWins ? NO_BACKGROUND : YES_BACKGROUND);
    }

    private GoldMarketStatusStyle() {
        this.label = "";
        this.textColor = ACTIVE_TEXT;
        this.backgroundColor = ACTIVE_BACKGROUND;
    }
}
