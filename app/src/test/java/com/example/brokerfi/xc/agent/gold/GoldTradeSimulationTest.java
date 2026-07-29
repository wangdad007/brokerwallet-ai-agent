package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldTradeSimulation;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldTradeSimulationTest {
    private static final BigInteger E18 = new BigInteger("1000000000000000000");

    @Test
    public void yesBuyMatchesConstantProductReserveUpdate() {
        GoldMarketRepository.GameModel game = activeGame();
        GoldTradeSimulation.Result result = GoldTradeSimulation.simulate(game, 0, amount(10));

        assertTrue(result.valid);
        assertEquals(amount(10).add(
                        amount(100).subtract(amount(100).multiply(amount(100)).divide(amount(110)))),
                result.sharesOutWei);
        assertEquals("50.00", result.beforeYesProbability.toPlainString());
        assertTrue(result.afterYesProbability.doubleValue() > result.beforeYesProbability.doubleValue());
    }

    @Test
    public void noBuyMovesYesProbabilityDownAndDoesNotWriteAnything() {
        GoldTradeSimulation.Result result = GoldTradeSimulation.simulate(activeGame(), 1, amount(10));
        assertTrue(result.valid);
        assertTrue(result.afterYesProbability.doubleValue() < result.beforeYesProbability.doubleValue());
    }

    @Test
    public void shallowLiquidityCorrectlyProducesLargePriceImpact() {
        GoldMarketRepository.GameModel shallow = activeGame();
        shallow.virtualReserves = Arrays.asList(amount(1), amount(1));
        GoldTradeSimulation.Result shallowResult =
                GoldTradeSimulation.simulate(shallow, 0, amount(2));

        GoldMarketRepository.GameModel deep = activeGame();
        GoldTradeSimulation.Result deepResult =
                GoldTradeSimulation.simulate(deep, 0, amount(2));

        assertTrue(shallowResult.valid);
        assertEquals("90.00", shallowResult.afterYesProbability.toPlainString());
        assertTrue(shallowResult.afterYesProbability.subtract(shallowResult.beforeYesProbability)
                .doubleValue()
                > deepResult.afterYesProbability.subtract(deepResult.beforeYesProbability)
                .doubleValue());
    }

    @Test
    public void refusesEndedOrUnpricedMarkets() {
        GoldMarketRepository.GameModel ended = activeGame();
        ended.isResolved = true;
        assertFalse(GoldTradeSimulation.simulate(ended, 0, amount(1)).valid);
        GoldMarketRepository.GameModel invalid = activeGame();
        invalid.virtualReserves = Arrays.asList(BigInteger.ZERO, amount(1));
        assertFalse(GoldTradeSimulation.simulate(invalid, 0, amount(1)).valid);
    }

    private static GoldMarketRepository.GameModel activeGame() {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.virtualReserves = Arrays.asList(amount(100), amount(100)); // [reserveNO, reserveYES]
        return game;
    }

    private static BigInteger amount(long value) { return E18.multiply(BigInteger.valueOf(value)); }
}
