package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionHistoryPresenter;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldPositionHistoryPresenterTest {
    @Test
    public void buildsHoldingSnapshotRowsWhenTradeHistoryIsMissing() {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.myShares = Arrays.asList(new BigInteger("44242424000000000000"),
                new BigInteger("32631579000000000000"));

        List<BackendApiClient.TradeDTO> rows =
                GoldPositionHistoryPresenter.visibleRows(game, Collections.emptyList());

        assertEquals(2, rows.size());
        assertTrue(GoldPositionHistoryPresenter.isSnapshotRow(rows.get(0)));
        assertEquals(0, rows.get(0).optionId);
        assertEquals("44242424000000000000", rows.get(0).shareAmountWei);
        assertEquals(1, rows.get(1).optionId);
        assertEquals("32631579000000000000", rows.get(1).shareAmountWei);
    }

    @Test
    public void realBuyTradesTakePriorityOverSnapshotRows() {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.myShares = Arrays.asList(BigInteger.TEN, BigInteger.TEN);

        BackendApiClient.TradeDTO buy = new BackendApiClient.TradeDTO();
        buy.tradeType = "BUY";
        buy.optionId = 0;
        buy.shareAmountWei = "10";

        List<BackendApiClient.TradeDTO> rows =
                GoldPositionHistoryPresenter.visibleRows(game, Collections.singletonList(buy));

        assertEquals(1, rows.size());
        assertFalse(GoldPositionHistoryPresenter.isSnapshotRow(rows.get(0)));
        assertEquals("10", rows.get(0).shareAmountWei);
    }
}
