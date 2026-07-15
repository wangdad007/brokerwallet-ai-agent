package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single source of truth for market types that can be created and settled. */
public final class GoldMarketTemplateCatalog {
    public static final String TYPE_PRICE = "TYPE_PRICE";
    public static final String TYPE_RETURN_THRESHOLD = "TYPE_RETURN_THRESHOLD";
    public static final String TYPE_PRICE_THRESHOLD = "TYPE_PRICE_THRESHOLD";
    public static final String TYPE_PRICE_RANGE = "TYPE_PRICE_RANGE";
    public static final String TYPE_RELATIVE = "TYPE_RELATIVE";
    public static final String TYPE_STREAK = "TYPE_STREAK";

    private static final Map<String, Template> TEMPLATES = new LinkedHashMap<>();

    static {
        add(new Template(TYPE_PRICE, "价格涨跌", "预测整日区间的金价方向", R.drawable.ic_template_price));
        add(new Template(TYPE_RETURN_THRESHOLD, "涨跌幅", "预测金价绝对涨跌幅", R.drawable.ic_template_volatility));
        add(new Template(TYPE_PRICE_THRESHOLD, "价格阈值", "预测截止金价与目标值关系", R.drawable.ic_template_price_threshold));
        add(new Template(TYPE_PRICE_RANGE, "价格区间", "预测截止金价是否位于区间", R.drawable.ic_template_touch));
        add(new Template(TYPE_RELATIVE, "跑赢率", "黄金与 BTC、ETH、SOL 或 BNB 的整日收益率", R.drawable.ic_template_relative));
        add(new Template(TYPE_STREAK, "连续涨跌", "预测连续交易日的金价方向", R.drawable.ic_template_technical));
    }

    private GoldMarketTemplateCatalog() {
    }

    private static void add(Template template) {
        TEMPLATES.put(template.type, template);
    }

    public static List<String> types() {
        return Collections.unmodifiableList(new ArrayList<>(TEMPLATES.keySet()));
    }

    public static List<Template> templates() {
        return Collections.unmodifiableList(new ArrayList<>(TEMPLATES.values()));
    }

    public static boolean isCreatable(String type) {
        return TEMPLATES.containsKey(type);
    }

    public static Template forType(String type) {
        return TEMPLATES.get(type);
    }

    public static final class Template {
        public final String type;
        public final String title;
        public final String hint;
        public final int drawableRes;

        private Template(String type, String title, String hint, int drawableRes) {
            this.type = type;
            this.title = title;
            this.hint = hint;
            this.drawableRes = drawableRes;
        }
    }
}
