package com.example.brokerfi.xc.agent.gold.view;

import com.example.brokerfi.R;

/** Keeps template selection and market cover art on the same icon mapping. */
public final class GoldMarketTemplateIcon {
    private GoldMarketTemplateIcon() {
    }

    public static int forType(String templateType) {
        if ("TYPE_PRICE".equals(templateType)) return R.drawable.ic_template_price;
        if ("TYPE_VOLATILITY".equals(templateType)) return R.drawable.ic_template_volatility;
        if ("TYPE_VOLUME".equals(templateType)) return R.drawable.ic_template_volume;
        if ("TYPE_TECHNICAL".equals(templateType)) return R.drawable.ic_template_technical;
        if ("TYPE_TOUCH".equals(templateType)) return R.drawable.ic_template_touch;
        if ("TYPE_RELATIVE".equals(templateType)) return R.drawable.ic_template_relative;
        if ("TYPE_PRICE_THRESHOLD".equals(templateType)) return R.drawable.ic_template_price_threshold;
        if ("TYPE_EVENT".equals(templateType)) return R.drawable.ic_template_event;
        return R.drawable.apartment_icon;
    }
}
