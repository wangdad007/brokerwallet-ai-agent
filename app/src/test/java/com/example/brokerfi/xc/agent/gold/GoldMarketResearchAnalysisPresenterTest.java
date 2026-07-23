package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketResearchAnalysisPresenter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GoldMarketResearchAnalysisPresenterTest {
    @Test
    public void promptRequiresStructuredResearchWithoutSensitiveIdentifiers() {
        String prompt = GoldMarketResearchAnalysisPresenter.buildPrompt(
                "YES share: 62.0%\nNO share: 38.0%\nTime remaining: 2d");

        assertTrue(prompt.contains("YES share: 62.0%"));
        assertTrue(prompt.contains("生成结构化中文投研 JSON"));
        assertTrue(prompt.contains("不得输出钱包地址、私钥或交易哈希"));
        assertTrue(GoldMarketResearchAnalysisPresenter.systemPrompt().contains("不可信数据"));
    }

    @Test
    public void parsesStructuredResearchIntoUnifiedInsightSections() {
        GoldMarketResearchAnalysisPresenter.Analysis analysis =
                GoldMarketResearchAnalysisPresenter.parse("{"
                        + "\"stance\":\"Lean YES\",\"risk_level\":\"Medium\","
                        + "\"summary\":\"YES share is 62%, with two days remaining.\","
                        + "\"drivers\":[\"YES share 62%\",\"Time remaining 2d\"],"
                        + "\"actions\":[\"Monitor share changes\"],"
                        + "\"disclaimer\":\"Research reference only\"}");

        assertEquals("偏向 YES", analysis.stance);
        assertEquals("中", analysis.riskLevel);
        assertTrue(analysis.drivers.contains("• YES share 62%"));
        assertTrue(analysis.actions.contains("• Monitor share changes"));
    }

    @Test
    public void nonJsonResponseUsesSafeFallback() {
        GoldMarketResearchAnalysisPresenter.Analysis analysis =
                GoldMarketResearchAnalysisPresenter.parse("Evidence is currently limited; wait for more market data.");

        assertEquals("观望", analysis.stance);
        assertEquals("待定", analysis.riskLevel);
        assertTrue(analysis.summary.contains("Evidence is currently limited"));
        assertTrue(analysis.disclaimer.contains("不承诺收益"));
    }
}
