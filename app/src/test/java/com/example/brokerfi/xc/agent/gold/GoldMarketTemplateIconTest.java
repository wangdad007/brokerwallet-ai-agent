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

    @Test
    public void infersTemplateDrawableWhenLegacyMarketHasNoAvatar() {
        assertEquals(R.drawable.ic_template_price_threshold,
                GoldMarketTemplateIcon.forMarket("", "黄金价格 大于 10USD/盎司", ""));
        assertEquals(R.drawable.ic_template_price,
                GoldMarketTemplateIcon.forMarket("", "黄金价格 上涨 7天", ""));
        assertEquals(R.drawable.ic_template_volatility,
                GoldMarketTemplateIcon.forMarket("", "黄金波动 大于 3%", ""));
        assertEquals(R.drawable.ic_template_volume,
                GoldMarketTemplateIcon.forMarket("", "黄金成交量 大于 300吨", ""));
        assertEquals(R.drawable.ic_template_technical,
                GoldMarketTemplateIcon.forMarket("", "黄金MACD 上穿信号线", ""));
        assertEquals(R.drawable.ic_template_touch,
                GoldMarketTemplateIcon.forMarket("", "黄金价格 触及 2800USD/盎司", ""));
        assertEquals(R.drawable.ic_template_relative,
                GoldMarketTemplateIcon.forMarket("", "黄金 跑赢 BTC", ""));
        assertEquals(R.drawable.ic_template_event,
                GoldMarketTemplateIcon.forMarket("", "发生 美联储降息", ""));
    }

    @Test
    public void keepsRealIpfsAvatarInsteadOfReplacingItWithTemplateDrawable() {
        assertEquals(0, GoldMarketTemplateIcon.forMarket(
                "QmRealImageCid", "黄金价格 大于 10USD/盎司", ""));
    }
}
