package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public final class GoldPositionValuation {
    private GoldPositionValuation() {
    }

    public static MarketValue calculateMarket(GoldMarketRepository.GameModel game) {
        if (game == null) {
            return MarketValue.incomplete();
        }

        BigInteger yesShares = positiveShareAt(game.myShares, 0);
        BigInteger noShares = positiveShareAt(game.myShares, 1);
        BigInteger myLiquidityShares = positive(game.myLiquidityShares);
        if (yesShares.signum() == 0 && noShares.signum() == 0
                && myLiquidityShares.signum() == 0) {
            return MarketValue.complete(BigInteger.ZERO);
        }

        if (game.isRefunded) {
            return myLiquidityShares.signum() > 0
                    ? liquidityValue(game, myLiquidityShares)
                    : MarketValue.incomplete();
        }

        if (game.isResolved) {
            BigInteger winningShares = positiveShareAt(game.myShares, game.winningOption);
            MarketValue liquidity = liquidityValue(game, myLiquidityShares);
            return liquidity.isComplete()
                    ? MarketValue.complete(winningShares.add(liquidity.getValueWei()))
                    : MarketValue.incomplete();
        }

        if (!hasValidBinaryReserves(game.virtualReserves)) {
            return MarketValue.incomplete();
        }

        MarketValue outcome = activeOutcomeValue(game, yesShares, noShares);
        if (!outcome.isComplete()) return MarketValue.incomplete();
        MarketValue liquidity = liquidityValue(game, myLiquidityShares);
        return liquidity.isComplete()
                ? MarketValue.complete(outcome.getValueWei().add(liquidity.getValueWei()))
                : MarketValue.incomplete();
    }

    public static MarketValue calculateOutcomeMarket(GoldMarketRepository.GameModel game) {
        if (game == null) return MarketValue.incomplete();
        BigInteger yesShares = positiveShareAt(game.myShares, 0);
        BigInteger noShares = positiveShareAt(game.myShares, 1);
        if (yesShares.signum() == 0 && noShares.signum() == 0) {
            return MarketValue.complete(BigInteger.ZERO);
        }
        if (game.isRefunded) return MarketValue.incomplete();
        if (game.isResolved) {
            return MarketValue.complete(positiveShareAt(game.myShares, game.winningOption));
        }
        if (!hasValidBinaryReserves(game.virtualReserves)) return MarketValue.incomplete();
        return activeOutcomeValue(game, yesShares, noShares);
    }

    public static MarketValue calculateLiquidityMarket(GoldMarketRepository.GameModel game) {
        if (game == null) return MarketValue.incomplete();
        return liquidityValue(game, positive(game.myLiquidityShares));
    }

    public static PortfolioValue calculatePortfolio(
            List<GoldMarketRepository.GameModel> games) {
        BigInteger value = BigInteger.ZERO;
        int unavailableMarketCount = 0;
        if (games != null) {
            for (GoldMarketRepository.GameModel game : games) {
                MarketValue marketValue = calculateMarket(game);
                if (marketValue.isComplete()) {
                    value = value.add(marketValue.getValueWei());
                } else {
                    unavailableMarketCount++;
                }
            }
        }
        return new PortfolioValue(value, unavailableMarketCount);
    }

    public static PortfolioValue calculateOutcomePortfolio(
            List<GoldMarketRepository.GameModel> games) {
        return calculatePortfolioWith(games, true);
    }

    public static PortfolioValue calculateLiquidityPortfolio(
            List<GoldMarketRepository.GameModel> games) {
        return calculatePortfolioWith(games, false);
    }

    private static PortfolioValue calculatePortfolioWith(
            List<GoldMarketRepository.GameModel> games, boolean outcome) {
        BigInteger value = BigInteger.ZERO;
        int unavailable = 0;
        if (games != null) {
            for (GoldMarketRepository.GameModel game : games) {
                MarketValue item = outcome
                        ? calculateOutcomeMarket(game) : calculateLiquidityMarket(game);
                if (item.isComplete()) value = value.add(item.getValueWei());
                else unavailable++;
            }
        }
        return new PortfolioValue(value, unavailable);
    }

    private static BigInteger positiveShareAt(List<BigInteger> shares, int index) {
        if (shares == null || index < 0 || index >= shares.size()) {
            return BigInteger.ZERO;
        }
        BigInteger share = shares.get(index);
        return share != null && share.signum() > 0 ? share : BigInteger.ZERO;
    }

    private static boolean hasValidBinaryReserves(List<BigInteger> reserves) {
        return reserves != null
                && reserves.size() >= 2
                && reserves.get(0) != null
                && reserves.get(1) != null
                && reserves.get(0).signum() > 0
                && reserves.get(1).signum() > 0;
    }

    /**
     * Values outcome shares by the same immediate AMM exit path shown in the
     * sell ticket, including the 1% trading fee. If both sides are held, the
     * second quote uses the reserves left by the first sale rather than
     * incorrectly pricing both sales against the same pool snapshot.
     */
    private static MarketValue activeOutcomeValue(
            GoldMarketRepository.GameModel game,
            BigInteger yesShares,
            BigInteger noShares) {
        BigInteger value = BigInteger.ZERO;
        BigInteger reserveNo = game.virtualReserves.get(0);
        BigInteger reserveYes = game.virtualReserves.get(1);

        if (yesShares.signum() > 0) {
            GoldSellSimulation.Result result = GoldSellSimulation.simulate(
                    valuationGame(game, reserveNo, reserveYes), 0, yesShares,
                    GoldSellSimulation.DEFAULT_SLIPPAGE_BPS);
            if (!result.valid) return MarketValue.incomplete();
            value = value.add(result.amountOutWei);
            reserveNo = result.afterReserveNo;
            reserveYes = result.afterReserveYes;
        }
        if (noShares.signum() > 0) {
            GoldSellSimulation.Result result = GoldSellSimulation.simulate(
                    valuationGame(game, reserveNo, reserveYes), 1, noShares,
                    GoldSellSimulation.DEFAULT_SLIPPAGE_BPS);
            if (!result.valid) return MarketValue.incomplete();
            value = value.add(result.amountOutWei);
        }
        return MarketValue.complete(value);
    }

    private static GoldMarketRepository.GameModel valuationGame(
            GoldMarketRepository.GameModel source,
            BigInteger reserveNo,
            BigInteger reserveYes) {
        GoldMarketRepository.GameModel value = new GoldMarketRepository.GameModel();
        value.isResolved = source.isResolved;
        value.isRefunded = source.isRefunded;
        value.myShares = source.myShares;
        value.virtualReserves = Arrays.asList(reserveNo, reserveYes);
        return value;
    }

    /**
     * Conservative LP value: proportional immediately redeemable BKC plus
     * accrued fees. Active-pool surplus YES/NO inventory is deliberately not
     * counted because its cash value depends on a later sale and slippage.
     */
    private static MarketValue liquidityValue(
            GoldMarketRepository.GameModel game, BigInteger myLiquidityShares) {
        if (myLiquidityShares == null || myLiquidityShares.signum() <= 0) {
            return MarketValue.complete(BigInteger.ZERO);
        }
        BigInteger totalLiquidityShares = positive(game.totalLiquidityShares);
        if (totalLiquidityShares.signum() <= 0
                || myLiquidityShares.compareTo(totalLiquidityShares) > 0
                || !hasValidBinaryReserves(game.virtualReserves)) {
            return MarketValue.incomplete();
        }
        BigInteger reserveNo = game.virtualReserves.get(0);
        BigInteger reserveYes = game.virtualReserves.get(1);
        BigInteger reserveYesOut = reserveYes.multiply(myLiquidityShares)
                .divide(totalLiquidityShares);
        BigInteger reserveNoOut = reserveNo.multiply(myLiquidityShares)
                .divide(totalLiquidityShares);
        BigInteger collateralOut;
        if (game.isResolved) {
            collateralOut = game.winningOption == 0 ? reserveYesOut : reserveNoOut;
        } else {
            collateralOut = reserveYesOut.min(reserveNoOut);
        }
        return MarketValue.complete(collateralOut.add(positive(game.myLiquidityFees)));
    }

    private static BigInteger positive(BigInteger value) {
        return value != null && value.signum() > 0 ? value : BigInteger.ZERO;
    }

    public static final class MarketValue {
        private final BigInteger valueWei;
        private final boolean complete;

        private MarketValue(BigInteger valueWei, boolean complete) {
            this.valueWei = valueWei;
            this.complete = complete;
        }

        private static MarketValue complete(BigInteger valueWei) {
            return new MarketValue(valueWei, true);
        }

        private static MarketValue incomplete() {
            return new MarketValue(BigInteger.ZERO, false);
        }

        public BigInteger getValueWei() {
            return valueWei;
        }

        public boolean isComplete() {
            return complete;
        }
    }

    public static final class PortfolioValue {
        private final BigInteger valueWei;
        private final int unavailableMarketCount;

        private PortfolioValue(BigInteger valueWei, int unavailableMarketCount) {
            this.valueWei = valueWei;
            this.unavailableMarketCount = unavailableMarketCount;
        }

        public BigInteger getValueWei() {
            return valueWei;
        }

        public int getUnavailableMarketCount() {
            return unavailableMarketCount;
        }
    }
}
