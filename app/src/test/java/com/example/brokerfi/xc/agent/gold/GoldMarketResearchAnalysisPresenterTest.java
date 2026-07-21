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
        assertTrue(prompt.contains("Generate structured research JSON"));
        assertTrue(prompt.contains("Do not output wallet addresses, private keys or transaction hashes"));
        assertTrue(GoldMarketResearchAnalysisPresenter.systemPrompt().contains("untrusted data"));
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

        assertEquals("Lean YES", analysis.stance);
        assertEquals("Medium", analysis.riskLevel);
        assertTrue(analysis.drivers.contains("• YES share 62%"));
        assertTrue(analysis.actions.contains("• Monitor share changes"));
    }

    @Test
    public void nonJsonResponseUsesSafeFallback() {
        GoldMarketResearchAnalysisPresenter.Analysis analysis =
                GoldMarketResearchAnalysisPresenter.parse("Evidence is currently limited; wait for more market data.");

        assertEquals("Watch", analysis.stance);
        assertEquals("Pending", analysis.riskLevel);
        assertTrue(analysis.summary.contains("Evidence is currently limited"));
        assertTrue(analysis.disclaimer.contains("does not promise returns"));
    }
}
