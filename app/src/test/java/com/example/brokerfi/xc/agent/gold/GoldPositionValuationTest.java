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
            new BigInteger("11494269475394646961");
    private static final BigInteger ACTIVE_NO_VALUE =
            new BigInteger("7546381891715731787");

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

        assertEquals(new BigInteger("19800000000000000000"), value.getValueWei());
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
    public void lpOnlyPositionIncludesProportionalRedeemableCollateralAndFees() {
        GoldMarketRepository.GameModel game = activeGame(
                BigInteger.ZERO, BigInteger.ZERO);
        game.totalLiquidityShares = amount(100);
        game.myLiquidityShares = amount(20);
        game.myLiquidityFees = amount(2);

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        // Reserves are YES=100, NO=150. Twenty percent can immediately merge
        // 20 BKC, with the surplus NO inventory deliberately valued at zero.
        assertEquals(amount(22), value.getValueWei());
        assertTrue(value.isComplete());
    }

    @Test
    public void outcomeValueExcludesLiquidityOwnedByTheSameAccount() {
        GoldMarketRepository.GameModel game = activeGame(amount(20), BigInteger.ZERO);
        game.totalLiquidityShares = amount(100);
        game.myLiquidityShares = amount(20);
        game.myLiquidityFees = amount(2);

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateOutcomeMarket(game);

        assertEquals(ACTIVE_YES_VALUE, value.getValueWei());
        assertTrue(value.isComplete());
    }

    @Test
    public void resolvedCreatorLpIncludesWinningReserveAndFees() {
        GoldMarketRepository.GameModel game = activeGame(
                BigInteger.ZERO, BigInteger.ZERO);
        game.totalLiquidityShares = amount(100);
        game.myLiquidityShares = amount(25);
        game.myLiquidityFees = amount(3);
        game.isResolved = true;
        game.winningOption = 0;

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertEquals(amount(28), value.getValueWei());
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

        assertEquals(new BigInteger("16494269475394646961"), value.getValueWei());
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
