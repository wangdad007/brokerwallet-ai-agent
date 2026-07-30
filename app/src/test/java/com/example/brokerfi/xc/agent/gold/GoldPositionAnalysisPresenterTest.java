package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionAnalysisPresenter;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldPositionAnalysisPresenterTest {
    private static final BigInteger TOKEN = new BigInteger("1000000000000000000");

    @Test
    public void promptContainsPositionCashFlowAndNoWalletIdentifiers() {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.id = 7;
        game.desc = "黄金价格上涨";
        game.condition = "北京时间整日边界按 Chainlink XAU/USD 判定";
        game.optionNames = Arrays.asList("YES", "NO");
        game.totalPool = TOKEN.multiply(BigInteger.valueOf(100));
        game.virtualReserves = Arrays.asList(
                TOKEN.multiply(BigInteger.valueOf(40)),
                TOKEN.multiply(BigInteger.valueOf(60)));
        game.myShares = Arrays.asList(
                TOKEN.multiply(BigInteger.valueOf(5)), BigInteger.ZERO);
        game.deadlineSec = 1_800_000_000L;

        BackendApiClient.TradeDTO buy = trade("BUY", "10", true);
        buy.txHash = "0xsecret-transaction-hash";
        BackendApiClient.TradeDTO sell = trade("SELL", "2", false);
        BackendApiClient.TradeDTO grid = trade("BUY", "1", true);
        grid.executionSource = "grid";
        String prompt = GoldPositionAnalysisPresenter.buildPrompt(
                game, Arrays.asList(buy, sell, grid), 1_700_000_000_000L);

        assertTrue(prompt.contains("累计买入：11 BKC"));
        assertTrue(prompt.contains("累计卖出：2 BKC"));
        assertTrue(prompt.contains("净投入：9 BKC"));
        assertTrue(prompt.contains("AI 托管交易：1"));
        assertTrue(prompt.contains("网格策略交易：1"));
        assertTrue(prompt.contains("当前估值："));
        assertFalse(prompt.contains(buy.txHash));
        assertFalse(prompt.toLowerCase().contains("private_key"));
    }

    @Test
    public void parsesStructuredAnalysisIntoCardSections() {
        String response = "```json\n{\"stance\":\"Lean YES\",\"risk_level\":\"Medium\","
                + "\"summary\":\"The position is concentrated in YES; monitor the deadline.\","
                + "\"drivers\":[\"YES share 60%\",\"Net investment 8 BKC\"],"
                + "\"actions\":[\"Limit additional exposure\",\"Review before the deadline\"],"
                + "\"disclaimer\":\"For risk management reference only\"}\n```";
        GoldPositionAnalysisPresenter.Analysis analysis =
                GoldPositionAnalysisPresenter.parse(response);

        assertEquals("偏向 YES", analysis.stance);
        assertEquals("中", analysis.riskLevel);
        assertTrue(analysis.drivers.contains("• YES share 60%"));
        assertTrue(analysis.actions.contains("Review before the deadline"));
    }

    @Test
    public void plainTextResponseFallsBackWithoutCrashing() {
        GoldPositionAnalysisPresenter.Analysis analysis =
                GoldPositionAnalysisPresenter.parse("The position is concentrated; monitor risk.");
        assertEquals("观望", analysis.stance);
        assertEquals("待定", analysis.riskLevel);
        assertTrue(analysis.summary.contains("position is concentrated"));
    }

    private static BackendApiClient.TradeDTO trade(String type, String bkc, boolean managed) {
        BackendApiClient.TradeDTO trade = new BackendApiClient.TradeDTO();
        trade.tradeType = type;
        trade.amountWei = TOKEN.multiply(new BigInteger(bkc)).toString();
        trade.isSuccess = true;
        trade.isAiManaged = managed;
        return trade;
    }
}
