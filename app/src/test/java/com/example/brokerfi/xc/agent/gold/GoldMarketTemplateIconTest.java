package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.view.GoldMarketTemplateIcon;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GoldMarketTemplateIconTest {
    @Test
    public void mapsEveryMarketTemplateToItsCreationIcon() {
        assertEquals(R.drawable.ic_template_price,
                GoldMarketTemplateIcon.forType("TYPE_PRICE"));
        assertEquals(R.drawable.ic_template_volatility,
                GoldMarketTemplateIcon.forType("TYPE_VOLATILITY"));
        assertEquals(R.drawable.ic_template_volume,
                GoldMarketTemplateIcon.forType("TYPE_VOLUME"));
        assertEquals(R.drawable.ic_template_technical,
                GoldMarketTemplateIcon.forType("TYPE_TECHNICAL"));
        assertEquals(R.drawable.ic_template_touch,
                GoldMarketTemplateIcon.forType("TYPE_TOUCH"));
        assertEquals(R.drawable.ic_template_relative,
                GoldMarketTemplateIcon.forType("TYPE_RELATIVE"));
        assertEquals(R.drawable.ic_template_price_threshold,
                GoldMarketTemplateIcon.forType("TYPE_PRICE_THRESHOLD"));
        assertEquals(R.drawable.ic_template_event,
                GoldMarketTemplateIcon.forType("TYPE_EVENT"));
    }

    @Test
    public void mapsSimulatorAvatarMarkerToTemplateDrawable() {
        assertEquals(R.drawable.ic_template_price,
                GoldMarketTemplateIcon.forAvatarUrl("template://TYPE_PRICE"));
        assertEquals(R.drawable.ic_template_technical,
                GoldMarketTemplateIcon.forAvatarUrl("template://TYPE_TECHNICAL"));
        assertEquals(0, GoldMarketTemplateIcon.forAvatarUrl("QmRealImageCid"));
    }
}
