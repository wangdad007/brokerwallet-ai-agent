package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPortfolioHistoryPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionValuation;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class GoldPortfolioHistoryPresenterTest {
    private static final BigInteger E18 = new BigInteger("1000000000000000000");

    @Test
    public void aggregatesHeldYesAndNoValueAcrossMarketHistory() {
        GoldMarketRepository.GameModel yesMarket = market(
                amount(2), BigInteger.ZERO,
                point(100, 50f, 50f),
                point(200, 60f, 40f));
        GoldMarketRepository.GameModel noMarket = market(
                BigInteger.ZERO, amount(1),
                point(100, 50f, 50f),
                point(200, 60f, 40f));

        List<GoldPortfolioHistoryPresenter.Point> points =
                GoldPortfolioHistoryPresenter.pointsFor(
                        Arrays.asList(yesMarket, noMarket), 300);

        assertEquals(3, points.size());
        assertPoint(points.get(0), 100, amount("1.5"));
        assertPoint(points.get(1), 200, amount("1.6"));
        assertEquals(300, points.get(2).timeSec);
        assertEquals(
                GoldPositionValuation.calculatePortfolio(Arrays.asList(yesMarket, noMarket)).getValueWei(),
                points.get(2).valueWei);
    }

    private static GoldMarketRepository.GameModel market(
            BigInteger yesShares,
            BigInteger noShares,
            GoldMarketRepository.HistoryPoint... history) {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.myShares = Arrays.asList(yesShares, noShares);
        game.virtualReserves = Arrays.asList(amount(100), amount(100));
        game.history = Arrays.asList(history);
        return game;
    }

    private static GoldMarketRepository.HistoryPoint point(long time, float yesPrice, float noPrice) {
        GoldMarketRepository.HistoryPoint point = new GoldMarketRepository.HistoryPoint();
        point.time = time;
        point.yesPrice = yesPrice;
        point.noPrice = noPrice;
        return point;
    }

    private static void assertPoint(GoldPortfolioHistoryPresenter.Point point, long time, BigInteger value) {
        assertEquals(time, point.timeSec);
        assertEquals(value, point.valueWei);
    }

    private static BigInteger amount(long amount) {
        return E18.multiply(BigInteger.valueOf(amount));
    }

    private static BigInteger amount(String amount) {
        return new java.math.BigDecimal(amount).movePointRight(18).toBigIntegerExact();
    }
}
