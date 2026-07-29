package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldSellSimulation;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldSellSimulationTest {
    private static final BigInteger E18 = new BigInteger("1000000000000000000");

    @Test
    public void yesSalePreservesInvariantAndAppliesSlippageFloor() {
        GoldSellSimulation.Result result = GoldSellSimulation.simulate(
                activeGame(amount(20), BigInteger.ZERO), 0, amount(10), 100);

        assertTrue(result.valid);
        assertTrue(result.amountOutWei.signum() > 0);
        assertEquals(result.amountOutWei.multiply(BigInteger.valueOf(9_900))
                .divide(BigInteger.valueOf(10_000)), result.minAmountOutWei);
        assertTrue(result.afterReserveNo.multiply(result.afterReserveYes)
                .compareTo(amount(100).multiply(amount(100))) >= 0);
        assertTrue(result.afterYesProbability.doubleValue()
                < result.beforeYesProbability.doubleValue());
    }

    @Test
    public void noSaleMovesYesProbabilityUp() {
        GoldSellSimulation.Result result = GoldSellSimulation.simulate(
                activeGame(BigInteger.ZERO, amount(20)), 1, amount(10), 100);

        assertTrue(result.valid);
        assertTrue(result.afterYesProbability.doubleValue()
                > result.beforeYesProbability.doubleValue());
    }

    @Test
    public void rejectsOversellAndEndedMarket() {
        GoldMarketRepository.GameModel game = activeGame(amount(5), BigInteger.ZERO);
        assertFalse(GoldSellSimulation.simulate(game, 0, amount(6), 100).valid);
        game.isResolved = true;
        assertFalse(GoldSellSimulation.simulate(game, 0, amount(1), 100).valid);
    }

    private static GoldMarketRepository.GameModel activeGame(
            BigInteger yesShares, BigInteger noShares) {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.virtualReserves = Arrays.asList(amount(100), amount(100));
        game.myShares = Arrays.asList(yesShares, noShares);
        game.totalPool = amount(100);
        return game;
    }

    private static BigInteger amount(long value) {
        return E18.multiply(BigInteger.valueOf(value));
    }
}
