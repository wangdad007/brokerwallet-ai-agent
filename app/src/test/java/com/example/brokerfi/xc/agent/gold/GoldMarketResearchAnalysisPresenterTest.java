package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketResearchAnalysisPresenter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GoldMarketResearchAnalysisPresenterTest {
    @Test
    public void promptRequiresStructuredResearchWithoutSensitiveIdentifiers() {
        String prompt = GoldMarketResearchAnalysisPresenter.buildPrompt(
                "YES 概率: 62.0%\nNO 概率: 38.0%\n剩余时间: 2天");

        assertTrue(prompt.contains("YES 概率: 62.0%"));
        assertTrue(prompt.contains("结构化投研 JSON"));
        assertTrue(prompt.contains("不要输出钱包地址、私钥或交易哈希"));
        assertTrue(GoldMarketResearchAnalysisPresenter.systemPrompt().contains("不可信数据"));
    }

    @Test
    public void parsesStructuredResearchIntoUnifiedInsightSections() {
        GoldMarketResearchAnalysisPresenter.Analysis analysis =
                GoldMarketResearchAnalysisPresenter.parse("{"
                        + "\"stance\":\"偏向YES\",\"risk_level\":\"中\","
                        + "\"summary\":\"YES 概率 62%，但距离结算仍有两天。\","
                        + "\"drivers\":[\"YES 概率 62%\",\"剩余时间 2天\"],"
                        + "\"actions\":[\"关注赔率变化\"],"
                        + "\"disclaimer\":\"仅供投研参考\"}");

        assertEquals("偏向YES", analysis.stance);
        assertEquals("中", analysis.riskLevel);
        assertTrue(analysis.drivers.contains("• YES 概率 62%"));
        assertTrue(analysis.actions.contains("• 关注赔率变化"));
    }

    @Test
    public void nonJsonResponseUsesSafeFallback() {
        GoldMarketResearchAnalysisPresenter.Analysis analysis =
                GoldMarketResearchAnalysisPresenter.parse("当前证据不足，建议等待更多行情数据。");

        assertEquals("待观察", analysis.stance);
        assertEquals("待评估", analysis.riskLevel);
        assertTrue(analysis.summary.contains("证据不足"));
        assertTrue(analysis.disclaimer.contains("不构成收益承诺"));
    }
}
