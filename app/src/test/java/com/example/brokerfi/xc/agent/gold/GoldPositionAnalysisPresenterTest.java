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
        String prompt = GoldPositionAnalysisPresenter.buildPrompt(
                game, Arrays.asList(buy, sell), 1_700_000_000_000L);

        assertTrue(prompt.contains("累计买入: 10 BKC"));
        assertTrue(prompt.contains("累计卖出: 2 BKC"));
        assertTrue(prompt.contains("净现金投入: 8 BKC"));
        assertTrue(prompt.contains("其中 AI 托管交易数: 1"));
        assertTrue(prompt.contains("当前估值:"));
        assertFalse(prompt.contains(buy.txHash));
        assertFalse(prompt.toLowerCase().contains("private_key"));
    }

    @Test
    public void parsesStructuredAnalysisIntoCardSections() {
        String response = "```json\n{\"stance\":\"偏向YES\",\"risk_level\":\"中\","
                + "\"summary\":\"仓位集中在YES，需关注截止时间。\","
                + "\"drivers\":[\"YES概率60%\",\"净投入8 BKC\"],"
                + "\"actions\":[\"控制新增仓位\",\"临近截止前复核\"],"
                + "\"disclaimer\":\"仅供风险管理参考\"}\n```";
        GoldPositionAnalysisPresenter.Analysis analysis =
                GoldPositionAnalysisPresenter.parse(response);

        assertEquals("偏向YES", analysis.stance);
        assertEquals("中", analysis.riskLevel);
        assertTrue(analysis.drivers.contains("• YES概率60%"));
        assertTrue(analysis.actions.contains("临近截止前复核"));
    }

    @Test
    public void plainTextResponseFallsBackWithoutCrashing() {
        GoldPositionAnalysisPresenter.Analysis analysis =
                GoldPositionAnalysisPresenter.parse("当前仓位较集中，请注意风险。");
        assertEquals("待观察", analysis.stance);
        assertEquals("待评估", analysis.riskLevel);
        assertTrue(analysis.summary.contains("仓位较集中"));
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
