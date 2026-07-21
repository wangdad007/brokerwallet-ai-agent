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
        add(new Template(TYPE_PRICE, "Price Direction", "Predict gold's direction over a full-day observation period", R.drawable.ic_template_price));
        add(new Template(TYPE_RETURN_THRESHOLD, "Absolute Return", "Compare gold's absolute percentage return with a threshold", R.drawable.ic_template_volatility));
        add(new Template(TYPE_PRICE_THRESHOLD, "Price Threshold", "Compare the ending gold price with a target", R.drawable.ic_template_price_threshold));
        add(new Template(TYPE_PRICE_RANGE, "Price Range", "Predict whether the ending gold price falls within a range", R.drawable.ic_template_touch));
        add(new Template(TYPE_RELATIVE, "Outperformance", "Compare gold's return with BTC, ETH, SOL, or BNB", R.drawable.ic_template_relative));
        add(new Template(TYPE_STREAK, "Direction Streak", "Predict whether gold rises or falls on each consecutive day", R.drawable.ic_template_technical));
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
