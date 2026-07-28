package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldAgentIntentRouter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class GoldAgentIntentRouterTest {
    @Test
    public void routesClosedFinancialWorkflowsWithoutTreatingAmountAsGameId() {
        GoldAgentIntentRouter.Result result = GoldAgentIntentRouter.route("帮我模拟买入 YES 1 BKC");
        assertEquals(GoldAgentIntentRouter.Intent.TRADE_SIMULATION, result.intent);
        assertEquals(-1, result.gameId);
        assertEquals(1d, result.amountBkc, .00001);
    }

    @Test
    public void readsExplicitPoolAndStrategyIntent() {
        GoldAgentIntentRouter.Result result = GoldAgentIntentRouter.route(
                "为 12 号池配置保守的 AI 自动托管，每单 0.5 BKC");
        assertEquals(GoldAgentIntentRouter.Intent.AI_MANAGED_STRATEGY, result.intent);
        assertEquals(12, result.gameId);
        assertEquals(.5d, result.amountBkc, .00001);
    }

    @Test
    public void sendsCreationAndComparisonToDifferentWorkflows() {
        assertEquals(GoldAgentIntentRouter.Intent.CREATE_MARKET_DRAFT,
                GoldAgentIntentRouter.route("创建一个未来两天黄金上涨市场").intent);
        assertEquals(GoldAgentIntentRouter.Intent.MARKET_COMPARE,
                GoldAgentIntentRouter.route("比较 #1 和 #2 哪个更有风险").intent);
        assertNull(GoldAgentIntentRouter.route("分析黄金行情").amountBkc);
    }
}
