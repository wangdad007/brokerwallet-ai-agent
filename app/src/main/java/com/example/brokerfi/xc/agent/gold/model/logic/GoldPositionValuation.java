package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import java.math.BigInteger;
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
        if (yesShares.signum() == 0 && noShares.signum() == 0) {
            return MarketValue.complete(BigInteger.ZERO);
        }

        if (game.isRefunded) {
            return MarketValue.incomplete();
        }

        if (game.isResolved) {
            BigInteger winningShares = positiveShareAt(game.myShares, game.winningOption);
            return MarketValue.complete(winningShares);
        }

        if (!hasValidBinaryReserves(game.virtualReserves)) {
            return MarketValue.incomplete();
        }

        BigInteger reserveNo = game.virtualReserves.get(0);
        BigInteger reserveYes = game.virtualReserves.get(1);
        BigInteger value = BigInteger.ZERO;
        if (yesShares.signum() > 0) {
            value = value.add(GoldSellSimulation.calculateSellReturn(
                    reserveYes, reserveNo, yesShares));
        }
        if (noShares.signum() > 0) {
            value = value.add(GoldSellSimulation.calculateSellReturn(
                    reserveNo, reserveYes, noShares));
        }
        return MarketValue.complete(value);
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
