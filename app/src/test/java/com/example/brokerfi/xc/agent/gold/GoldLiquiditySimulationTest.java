package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldLiquiditySimulation;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldLiquiditySimulationTest {
    private static final BigInteger E18 = new BigInteger("1000000000000000000");
    private static final long NOW = 1_800_000_000L;

    @Test
    public void proportionalContributionPreservesSkewAndReturnsSurplusOutcomeShares() {
        GoldMarketRepository.GameModel game = game(
                amount(150), amount(50), amount(100), amount(20), BigInteger.ZERO);

        GoldLiquiditySimulation.AddResult result =
                GoldLiquiditySimulation.simulateAdd(game, amount(30), NOW);

        assertTrue(result.valid);
        assertEquals(amount(20), result.liquiditySharesOutWei);
        assertEquals(amount(20), result.returnedYesWei);
        assertEquals(BigInteger.ZERO, result.returnedNoWei);
        assertEquals(result.liquiditySharesOutWei.multiply(BigInteger.valueOf(9_900))
                .divide(BigInteger.valueOf(10_000)),
                result.minimumLiquiditySharesOutWei);
    }

    @Test
    public void activeWithdrawalReturnsBkcSingleSidedInventoryAndAccruedFees() {
        GoldMarketRepository.GameModel game = game(
                amount(150), amount(50), amount(100), amount(20), amount(2));

        GoldLiquiditySimulation.RemoveResult result =
                GoldLiquiditySimulation.simulateRemove(game, amount(10), NOW);

        assertTrue(result.valid);
        assertEquals(amount(5), result.collateralOutWei);
        assertEquals(amount(2), result.feeOutWei);
        assertEquals(amount(7), result.totalAmountOutWei);
        assertEquals(BigInteger.ZERO, result.returnedYesWei);
        assertEquals(amount(10), result.returnedNoWei);
    }

    @Test
    public void creatorInitialLiquidityStaysLockedUntilSettlement() {
        GoldMarketRepository.GameModel game = game(
                amount(100), amount(100), amount(100), amount(100), BigInteger.ZERO);
        game.isCreator = true;
        game.creatorLockedLiquidityShares = amount(100);

        assertEquals(amount(100), game.myLiquidityShares);
        assertFalse(GoldLiquiditySimulation.simulateRemove(
                game, amount(1), NOW).valid);
        assertEquals(BigInteger.ZERO, GoldLiquiditySimulation.maximumRemovableLP(game));

        game.isResolved = true;
        assertEquals(amount(100), GoldLiquiditySimulation.maximumRemovableLP(game));
    }

    @Test
    public void resolvedLpReceivesWinningReserveAndFees() {
        GoldMarketRepository.GameModel game = game(
                amount(140), amount(60), amount(100), amount(25), amount(3));
        game.isResolved = true;
        game.winningOption = 0;

        GoldLiquiditySimulation.RemoveResult result =
                GoldLiquiditySimulation.simulateRemove(game, amount(25), NOW);

        assertTrue(result.valid);
        assertEquals(amount(15), result.collateralOutWei);
        assertEquals(amount(18), result.totalAmountOutWei);
        assertEquals(BigInteger.ZERO, result.returnedYesWei);
        assertEquals(BigInteger.ZERO, result.returnedNoWei);
    }

    private static GoldMarketRepository.GameModel game(
            BigInteger reserveNo, BigInteger reserveYes, BigInteger totalLP,
            BigInteger myLP, BigInteger myFees) {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.virtualReserves = Arrays.asList(reserveNo, reserveYes);
        game.totalPool = amount(200);
        game.totalLiquidityShares = totalLP;
        game.myLiquidityShares = myLP;
        game.myLiquidityFees = myFees;
        game.liquidityFeePool = myFees;
        game.creatorLockedLiquidityShares = BigInteger.ZERO;
        game.deadlineSec = NOW + 86_400;
        return game;
    }

    private static BigInteger amount(long value) {
        return E18.multiply(BigInteger.valueOf(value));
    }
}
