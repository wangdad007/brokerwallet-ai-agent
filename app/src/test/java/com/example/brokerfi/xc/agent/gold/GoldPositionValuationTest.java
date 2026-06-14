package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionValuation;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldPositionValuationTest {
    private static final BigInteger E18 = new BigInteger("1000000000000000000");
    private static final BigInteger ACTIVE_YES_VALUE =
            new BigInteger("11610373207469340365");
    private static final BigInteger ACTIVE_NO_VALUE =
            new BigInteger("7622607971430032108");

    @Test
    public void activeYesPositionUsesContractReserveMappingAndSellFormula() {
        GoldMarketRepository.GameModel game = activeGame(
                amount(20), BigInteger.ZERO);

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertEquals(ACTIVE_YES_VALUE, value.getValueWei());
        assertTrue(value.isComplete());
    }

    @Test
    public void activeNoPositionUsesContractReserveMappingAndSellFormula() {
        GoldMarketRepository.GameModel game = activeGame(
                BigInteger.ZERO, amount(20));

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertEquals(ACTIVE_NO_VALUE, value.getValueWei());
        assertTrue(value.isComplete());
    }

    @Test
    public void activePositionSumsBothHeldSides() {
        GoldMarketRepository.GameModel game = activeGame(amount(20), amount(20));

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertEquals(new BigInteger("19232981178899372473"), value.getValueWei());
        assertTrue(value.isComplete());
    }

    @Test
    public void resolvedPositionCountsOnlyWinningSharesAtFaceValue() {
        GoldMarketRepository.GameModel game = gameWithShares(amount(7), amount(5));
        game.isResolved = true;
        game.winningOption = 0;

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertEquals(amount(7), value.getValueWei());
        assertTrue(value.isComplete());
    }

    @Test
    public void refundedPositionWithSharesIsUnavailable() {
        GoldMarketRepository.GameModel game = gameWithShares(amount(7), BigInteger.ZERO);
        game.isRefunded = true;

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertEquals(BigInteger.ZERO, value.getValueWei());
        assertFalse(value.isComplete());
    }

    @Test
    public void positionWithoutPositiveSharesIsCompleteAndWorthZero() {
        GoldMarketRepository.GameModel game = gameWithShares(
                BigInteger.ZERO, BigInteger.ZERO);

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertEquals(BigInteger.ZERO, value.getValueWei());
        assertTrue(value.isComplete());
    }

    @Test
    public void activePositionWithMissingReservesIsUnavailable() {
        GoldMarketRepository.GameModel game = gameWithShares(amount(20), BigInteger.ZERO);
        game.virtualReserves = null;

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertEquals(BigInteger.ZERO, value.getValueWei());
        assertFalse(value.isComplete());
    }

    @Test
    public void portfolioSumsCompleteValuesAndCountsUnavailableMarkets() {
        GoldMarketRepository.GameModel active = activeGame(amount(20), BigInteger.ZERO);
        GoldMarketRepository.GameModel resolved = gameWithShares(
                BigInteger.ZERO, amount(5));
        resolved.isResolved = true;
        resolved.winningOption = 1;
        GoldMarketRepository.GameModel refunded = gameWithShares(
                amount(3), BigInteger.ZERO);
        refunded.isRefunded = true;

        GoldPositionValuation.PortfolioValue value =
                GoldPositionValuation.calculatePortfolio(
                        Arrays.asList(active, resolved, refunded));

        assertEquals(new BigInteger("16610373207469340365"), value.getValueWei());
        assertEquals(1, value.getUnavailableMarketCount());
    }

    private static GoldMarketRepository.GameModel activeGame(
            BigInteger yesShares, BigInteger noShares) {
        GoldMarketRepository.GameModel game = gameWithShares(yesShares, noShares);
        game.virtualReserves = Arrays.asList(amount(150), amount(100));
        return game;
    }

    private static GoldMarketRepository.GameModel gameWithShares(
            BigInteger yesShares, BigInteger noShares) {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.myShares = Arrays.asList(yesShares, noShares);
        game.virtualReserves = Collections.emptyList();
        return game;
    }

    private static BigInteger amount(long tokens) {
        return E18.multiply(BigInteger.valueOf(tokens));
    }
}
