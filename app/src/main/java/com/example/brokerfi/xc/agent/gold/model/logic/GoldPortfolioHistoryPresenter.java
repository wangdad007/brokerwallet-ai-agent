package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Builds an estimated portfolio-value timeline from each market's probability history. */
public final class GoldPortfolioHistoryPresenter {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private GoldPortfolioHistoryPresenter() {
    }

    public static List<Point> pointsFor(
            List<GoldMarketRepository.GameModel> games, long currentTimeSec) {
        if (games == null || games.isEmpty()) {
            return Collections.emptyList();
        }

        TreeSet<Long> timestamps = new TreeSet<>();
        for (GoldMarketRepository.GameModel game : games) {
            if (game == null || game.history == null) continue;
            for (GoldMarketRepository.HistoryPoint point : game.history) {
                if (point != null && point.time > 0) timestamps.add(point.time);
            }
        }

        List<Point> result = new ArrayList<>();
        for (Long timestamp : timestamps) {
            BigInteger value = BigInteger.ZERO;
            boolean hasEstimate = false;
            for (GoldMarketRepository.GameModel game : games) {
                BigInteger marketValue = valueAt(game, timestamp);
                if (marketValue == null) continue;
                value = value.add(marketValue);
                hasEstimate = true;
            }
            if (hasEstimate) result.add(new Point(timestamp, value));
        }

        BigInteger currentValue = GoldPositionValuation.calculatePortfolio(games).getValueWei();
        if (currentTimeSec <= 0) {
            currentTimeSec = timestamps.isEmpty() ? 0L : timestamps.last();
        }
        if (!result.isEmpty() && result.get(result.size() - 1).timeSec == currentTimeSec) {
            result.set(result.size() - 1, new Point(currentTimeSec, currentValue));
        } else if (currentTimeSec > 0) {
            result.add(new Point(currentTimeSec, currentValue));
        }
        return result;
    }

    private static BigInteger valueAt(GoldMarketRepository.GameModel game, long timestampSec) {
        if (game == null || game.isRefunded) return null;
        if (game.isResolved) {
            GoldPositionValuation.MarketValue value = GoldPositionValuation.calculateMarket(game);
            return value.isComplete() ? value.getValueWei() : null;
        }

        GoldMarketRepository.HistoryPoint price = latestPointAt(game.history, timestampSec);
        if (price == null) return null;
        BigInteger yesValue = shareValueAtProbability(shareAt(game.myShares, 0), price.yesPrice);
        BigInteger noValue = shareValueAtProbability(shareAt(game.myShares, 1), price.noPrice);
        return yesValue.add(noValue);
    }

    private static GoldMarketRepository.HistoryPoint latestPointAt(
            List<GoldMarketRepository.HistoryPoint> history, long timestampSec) {
        if (history == null || history.isEmpty()) return null;
        GoldMarketRepository.HistoryPoint latest = null;
        for (GoldMarketRepository.HistoryPoint point : history) {
            if (point == null || point.time <= 0 || point.time > timestampSec) continue;
            if (latest == null || point.time > latest.time) latest = point;
        }
        return latest;
    }

    private static BigInteger shareValueAtProbability(BigInteger shares, float probability) {
        if (shares == null || shares.signum() <= 0) return BigInteger.ZERO;
        float boundedProbability = Math.max(0f, Math.min(100f, probability));
        return new BigDecimal(shares)
                .multiply(BigDecimal.valueOf((double) boundedProbability))
                .divide(ONE_HUNDRED, 0, RoundingMode.DOWN)
                .toBigInteger();
    }

    private static BigInteger shareAt(List<BigInteger> shares, int index) {
        if (shares == null || index < 0 || index >= shares.size() || shares.get(index) == null) {
            return BigInteger.ZERO;
        }
        return shares.get(index).max(BigInteger.ZERO);
    }

    public static final class Point {
        public final long timeSec;
        public final BigInteger valueWei;

        public Point(long timeSec, BigInteger valueWei) {
            this.timeSec = timeSec;
            this.valueWei = valueWei == null ? BigInteger.ZERO : valueWei;
        }
    }
}
