package com.example.brokerfi.xc.agent.gold.view;

import com.example.brokerfi.R;

import java.util.Locale;

/** Keeps template selection and market cover art on the same icon mapping. */
public final class GoldMarketTemplateIcon {
    private static final String AVATAR_PREFIX = "template://";

    private GoldMarketTemplateIcon() {
    }

    public static int forType(String templateType) {
        if ("TYPE_PRICE".equals(templateType)) return R.drawable.ic_template_price;
        if ("TYPE_RETURN_THRESHOLD".equals(templateType)) return R.drawable.ic_template_volatility;
        if ("TYPE_PRICE_THRESHOLD".equals(templateType)) return R.drawable.ic_template_price_threshold;
        if ("TYPE_PRICE_RANGE".equals(templateType)) return R.drawable.ic_template_touch;
        if ("TYPE_RELATIVE".equals(templateType)) return R.drawable.ic_template_relative;
        if ("TYPE_STREAK".equals(templateType)) return R.drawable.ic_template_technical;
        // Legacy mappings are retained for already-created markets.
        if ("TYPE_VOLATILITY".equals(templateType)) return R.drawable.ic_template_volatility;
        if ("TYPE_VOLUME".equals(templateType)) return R.drawable.ic_template_volume;
        if ("TYPE_TECHNICAL".equals(templateType)) return R.drawable.ic_template_technical;
        if ("TYPE_TOUCH".equals(templateType)) return R.drawable.ic_template_touch;
        if ("TYPE_EVENT".equals(templateType)) return R.drawable.ic_template_event;
        return R.drawable.apartment_icon;
    }

    public static int forAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || !avatarUrl.startsWith(AVATAR_PREFIX)) return 0;
        return forType(avatarUrl.substring(AVATAR_PREFIX.length()));
    }

    public static int forMarket(String avatarUrl, String title, String condition) {
        int markedTemplate = forAvatarUrl(avatarUrl);
        if (markedTemplate != 0) return markedTemplate;
        if (avatarUrl != null && !avatarUrl.trim().isEmpty()) return 0;

        String text = ((title == null ? "" : title) + " "
                + (condition == null ? "" : condition)).toLowerCase(Locale.US);
        if (containsAny(text, "连续上涨", "连续下跌", "streak")) {
            return forType("TYPE_STREAK");
        }
        if (containsAny(text, "涨跌幅", "absolute return")) {
            return forType("TYPE_RETURN_THRESHOLD");
        }
        if (containsAny(text, "位于", "不在") && containsAny(text, "usd", "美元", "美金")) {
            return forType("TYPE_PRICE_RANGE");
        }
        if (containsAny(text, "成交量", "交易量", "volume")) {
            return forType("TYPE_VOLUME");
        }
        if (containsAny(text, "波动", "波幅", "volatility")) {
            return forType("TYPE_VOLATILITY");
        }
        if (containsAny(text, "rsi", "macd", "kdj", "boll", "技术指标")) {
            return forType("TYPE_TECHNICAL");
        }
        if (containsAny(text, "触及", "touch")) {
            return forType("TYPE_TOUCH");
        }
        if (containsAny(text, "跑赢", "outperform")) {
            return forType("TYPE_RELATIVE");
        }
        if (containsAny(text, "发生", "美联储降息", "宏观事件")) {
            return forType("TYPE_EVENT");
        }
        if (containsAny(text, "上涨", "下跌", "持平", "price up", "price down")) {
            return forType("TYPE_PRICE");
        }
        if (containsAny(text, "usd", "美元", "美金")
                && containsAny(text, "大于", "小于", "等于", "高于", "低于", "超过")) {
            return forType("TYPE_PRICE_THRESHOLD");
        }
        return 0;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.US))) return true;
        }
        return false;
    }
}
