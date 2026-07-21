package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure chart preparation logic kept outside the Activity for deterministic testing. */
public final class GoldMarketChartPresenter {
    private static final long FUTURE_TRADE_TOLERANCE_SEC = 5 * 60L;
    private static final int MAX_RENDER_POINTS = 360;
    private static final int MAX_SAMPLES_PER_INTERVAL = 12;

    private GoldMarketChartPresenter() {}

    public static final class SharePoint {
        public final long timestampSec;
        public final float yesShare;
        public final float noShare;

        SharePoint(long timestampSec, float yesShare, float noShare) {
            this.timestampSec = timestampSec;
            boolean yesValid = isFinite(yesShare);
            boolean noValid = isFinite(noShare);
            float safeYes = yesValid ? yesShare : (noValid ? 100f - noShare : 50f);
            float safeNo = noValid ? noShare : 100f - safeYes;
            float total = safeYes + safeNo;
            if (total <= 0f) {
                safeYes = 50f;
                safeNo = 50f;
            } else if (Math.abs(total - 100f) > 0.01f) {
                safeYes = safeYes / total * 100f;
                safeNo = safeNo / total * 100f;
            }
            this.yesShare = clampPercent(safeYes);
            this.noShare = clampPercent(safeNo);
        }
    }

    public static final class TradePoint {
        public final long timestampSec;
        public final int optionId;
        public final boolean aiManaged;
        public final float marketShare;
        public final String amountWei;
        public final int purchaseCount;
        public final long bucketStartSec;
        public final long bucketEndSec;

        TradePoint(long timestampSec, int optionId, boolean aiManaged,
                   float marketShare, String amountWei) {
            this(timestampSec, optionId, aiManaged, marketShare, amountWei,
                    1, timestampSec, timestampSec);
        }

        TradePoint(long timestampSec, int optionId, boolean aiManaged,
                   float marketShare, String amountWei, int purchaseCount,
                   long bucketStartSec, long bucketEndSec) {
            this.timestampSec = timestampSec;
            this.optionId = optionId;
            this.aiManaged = aiManaged;
            this.marketShare = marketShare;
            this.amountWei = amountWei == null ? "0" : amountWei;
            this.purchaseCount = Math.max(1, purchaseCount);
            this.bucketStartSec = bucketStartSec;
            this.bucketEndSec = bucketEndSec;
        }
    }

    public static final class TradeAggregation {
        public final long bucketSeconds;
        public final List<TradePoint> trades;

        TradeAggregation(long bucketSeconds, List<TradePoint> trades) {
            this.bucketSeconds = bucketSeconds;
            this.trades = trades;
        }
    }

    public static final class ChartModel {
        public final long baseTimestampSec;
        public final List<SharePoint> shares;
        public final List<TradePoint> trades;

        ChartModel(long baseTimestampSec, List<SharePoint> shares, List<TradePoint> trades) {
            this.baseTimestampSec = baseTimestampSec;
            this.shares = shares;
            this.trades = trades;
        }

        public float xOf(long timestampSec) {
            return (timestampSec - baseTimestampSec) / 60f;
        }
    }

    public static ChartModel prepare(List<BackendApiClient.HistoryPointDTO> rawHistory,
                                     List<BackendApiClient.TradeDTO> rawTrades) {
        Map<Long, SharePoint> deduplicated = new LinkedHashMap<>();
        List<BackendApiClient.HistoryPointDTO> sortedHistory = rawHistory == null
                ? Collections.emptyList() : new ArrayList<>(rawHistory);
        sortedHistory.sort(Comparator.comparingLong(point -> point.timestampSec));
        for (BackendApiClient.HistoryPointDTO point : sortedHistory) {
            if (point == null || point.timestampSec <= 0) continue;
            deduplicated.put(point.timestampSec,
                    new SharePoint(point.timestampSec, point.yesPrice, point.noPrice));
        }
        List<SharePoint> shares = new ArrayList<>(deduplicated.values());
        if (shares.isEmpty()) {
            return new ChartModel(0, shares, Collections.emptyList());
        }

        long firstTimestamp = shares.get(0).timestampSec;
        long currentTimestamp = System.currentTimeMillis() / 1000L;
        List<TradePoint> trades = new ArrayList<>();
        if (rawTrades != null) {
            for (BackendApiClient.TradeDTO trade : rawTrades) {
                if (trade == null || !trade.isSuccess || !"BUY".equalsIgnoreCase(trade.tradeType)) continue;
                long timestamp = trade.timestampSec > 0 ? trade.timestampSec : parseCreatedAt(trade.createdAt);
                // The sampler can lag behind a newly confirmed manual or AI
                // purchase. Keep valid user trades inside the selected range even
                // when they are newer than the last market sample.
                if (timestamp < firstTimestamp
                        || timestamp > currentTimestamp + FUTURE_TRADE_TOLERANCE_SEC) continue;
                int option = trade.optionId == 1 ? 1 : 0;
                float marketShare = interpolateShare(shares, timestamp, option);
                trades.add(new TradePoint(timestamp, option, trade.isAiManaged,
                        marketShare, trade.amountWei));
            }
        }
        trades.sort(Comparator.comparingLong(point -> point.timestampSec));

        // Market history and personal executions are intentionally independent:
        // history produces the two continuous curves, while trades only annotate
        // those curves. A transaction must never create or reshape market data.
        return new ChartModel(firstTimestamp, smoothShares(shares), trades);
    }

    /**
     * Groups personal purchases into range-aware time buckets. Market share history
     * remains untouched: these points are annotations only. Manual and DeepSeek
     * managed executions stay in separate groups so their origin is never hidden.
     */
    public static TradeAggregation aggregateTrades(List<TradePoint> source,
                                                    String range,
                                                    long visibleSpanSec) {
        long bucketSeconds = bucketSeconds(range, visibleSpanSec);
        if (source == null || source.isEmpty()) {
            return new TradeAggregation(bucketSeconds, Collections.emptyList());
        }

        Map<String, MutableTradeBucket> buckets = new LinkedHashMap<>();
        for (TradePoint trade : source) {
            if (trade == null) continue;
            long bucketStart = Math.floorDiv(trade.timestampSec, bucketSeconds) * bucketSeconds;
            String key = bucketStart + ":" + trade.optionId + ":" + trade.aiManaged;
            MutableTradeBucket bucket = buckets.get(key);
            if (bucket == null) {
                bucket = new MutableTradeBucket(bucketStart, bucketSeconds,
                        trade.optionId, trade.aiManaged);
                buckets.put(key, bucket);
            }
            bucket.add(trade);
        }

        List<TradePoint> result = new ArrayList<>(buckets.size());
        for (MutableTradeBucket bucket : buckets.values()) result.add(bucket.toTradePoint());
        result.sort(Comparator.comparingLong(point -> point.timestampSec));
        return new TradeAggregation(bucketSeconds, result);
    }

    public static String bucketLabel(long seconds) {
        if (seconds < 60L * 60L) return (seconds / 60L) + " min";
        if (seconds < 24L * 60L * 60L) {
            long hours = seconds / (60L * 60L);
            return hours + (hours == 1L ? " hour" : " hours");
        }
        long days = seconds / (24L * 60L * 60L);
        return days + (days == 1L ? " day" : " days");
    }

    private static long bucketSeconds(String range, long visibleSpanSec) {
        String normalized = range == null ? "1d" : range.toLowerCase(Locale.US);
        switch (normalized) {
            case "1h": return 5L * 60L;
            case "1w": return 24L * 60L * 60L;
            case "all": return adaptiveBucketSeconds(visibleSpanSec);
            case "1d":
            default: return 2L * 60L * 60L;
        }
    }

    private static long adaptiveBucketSeconds(long visibleSpanSec) {
        long target = Math.max(5L * 60L,
                (long) Math.ceil(Math.max(1L, visibleSpanSec) / 14d));
        long[] friendlyBuckets = {
                5L * 60L, 15L * 60L, 30L * 60L,
                60L * 60L, 2L * 60L * 60L, 6L * 60L * 60L,
                12L * 60L * 60L, 24L * 60L * 60L,
                2L * 24L * 60L * 60L, 3L * 24L * 60L * 60L,
                7L * 24L * 60L * 60L, 14L * 24L * 60L * 60L,
                30L * 24L * 60L * 60L
        };
        for (long candidate : friendlyBuckets) {
            if (candidate >= target) return candidate;
        }
        return friendlyBuckets[friendlyBuckets.length - 1];
    }

    private static final class MutableTradeBucket {
        final long startSec;
        final long endSec;
        final int optionId;
        final boolean aiManaged;
        long timestampTotal;
        double shareTotal;
        java.math.BigInteger amountTotal = java.math.BigInteger.ZERO;
        int count;

        MutableTradeBucket(long startSec, long bucketSeconds,
                           int optionId, boolean aiManaged) {
            this.startSec = startSec;
            this.endSec = startSec + bucketSeconds;
            this.optionId = optionId;
            this.aiManaged = aiManaged;
        }

        void add(TradePoint trade) {
            timestampTotal += trade.timestampSec;
            shareTotal += trade.marketShare;
            count += trade.purchaseCount;
            try {
                amountTotal = amountTotal.add(new java.math.BigInteger(trade.amountWei));
            } catch (NumberFormatException ignored) {
                // A malformed amount must not hide an otherwise valid purchase marker.
            }
        }

        TradePoint toTradePoint() {
            return new TradePoint(timestampTotal / Math.max(1, count), optionId, aiManaged,
                    (float) (shareTotal / Math.max(1, count)), amountTotal.toString(), count,
                    startSec, endSec);
        }
    }

    /**
     * Produces a visually smooth but shape-preserving market curve. MPAndroidChart's
     * built-in cubic renderer can fail or overshoot with irregular timestamps, so
     * we generate monotone Hermite samples and render them as one linear path.
     */
    private static List<SharePoint> smoothShares(List<SharePoint> source) {
        if (source.size() == 1) {
            SharePoint only = source.get(0);
            List<SharePoint> flat = new ArrayList<>();
            flat.add(only);
            flat.add(new SharePoint(only.timestampSec + 60L, only.yesShare, only.noShare));
            return flat;
        }

        int count = source.size();
        if (count >= MAX_RENDER_POINTS) {
            // Dense histories already form a smooth continuous path. Keeping the
            // recorded points also avoids hiding short-lived market movements.
            return new ArrayList<>(source);
        }

        double[] x = new double[count];
        double[] y = new double[count];
        long base = source.get(0).timestampSec;
        for (int i = 0; i < count; i++) {
            x[i] = source.get(i).timestampSec - base;
            y[i] = source.get(i).yesShare;
        }
        double[] slopes = monotoneSlopes(x, y);
        int samplesPerInterval = Math.max(1, Math.min(MAX_SAMPLES_PER_INTERVAL,
                (MAX_RENDER_POINTS - 1) / (count - 1)));

        List<SharePoint> result = new ArrayList<>((count - 1) * samplesPerInterval + 1);
        for (int i = 0; i < count - 1; i++) {
            double span = x[i + 1] - x[i];
            for (int sample = 0; sample < samplesPerInterval; sample++) {
                double t = sample / (double) samplesPerInterval;
                double t2 = t * t;
                double t3 = t2 * t;
                double h00 = 2d * t3 - 3d * t2 + 1d;
                double h10 = t3 - 2d * t2 + t;
                double h01 = -2d * t3 + 3d * t2;
                double h11 = t3 - t2;
                float yes = (float) (h00 * y[i] + h10 * span * slopes[i]
                        + h01 * y[i + 1] + h11 * span * slopes[i + 1]);
                long timestamp = base + Math.round(x[i] + span * t);
                appendDistinct(result, new SharePoint(timestamp, yes, 100f - yes));
            }
        }
        result.add(source.get(count - 1));
        return result;
    }

    private static double[] monotoneSlopes(double[] x, double[] y) {
        int count = x.length;
        double[] interval = new double[count - 1];
        double[] delta = new double[count - 1];
        for (int i = 0; i < count - 1; i++) {
            interval[i] = x[i + 1] - x[i];
            delta[i] = interval[i] <= 0d ? 0d : (y[i + 1] - y[i]) / interval[i];
        }
        double[] slope = new double[count];
        if (count == 2) {
            slope[0] = delta[0];
            slope[1] = delta[0];
            return slope;
        }
        slope[0] = endpointSlope(interval[0], interval[1], delta[0], delta[1]);
        for (int i = 1; i < count - 1; i++) {
            if (delta[i - 1] == 0d || delta[i] == 0d
                    || Math.signum(delta[i - 1]) != Math.signum(delta[i])) {
                slope[i] = 0d;
            } else {
                double w1 = 2d * interval[i] + interval[i - 1];
                double w2 = interval[i] + 2d * interval[i - 1];
                slope[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i]);
            }
        }
        slope[count - 1] = endpointSlope(interval[count - 2], interval[count - 3],
                delta[count - 2], delta[count - 3]);
        return slope;
    }

    private static double endpointSlope(double currentSpan, double adjacentSpan,
                                        double currentDelta, double adjacentDelta) {
        double slope = ((2d * currentSpan + adjacentSpan) * currentDelta
                - currentSpan * adjacentDelta) / (currentSpan + adjacentSpan);
        if (Math.signum(slope) != Math.signum(currentDelta)) return 0d;
        if (Math.signum(currentDelta) != Math.signum(adjacentDelta)
                && Math.abs(slope) > Math.abs(3d * currentDelta)) {
            return 3d * currentDelta;
        }
        return slope;
    }

    private static void appendDistinct(List<SharePoint> target, SharePoint point) {
        if (!target.isEmpty()
                && target.get(target.size() - 1).timestampSec == point.timestampSec) {
            target.set(target.size() - 1, point);
        } else {
            target.add(point);
        }
    }

    private static float interpolateShare(List<SharePoint> points, long timestamp, int optionId) {
        if (timestamp <= points.get(0).timestampSec) {
            return valueFor(points.get(0), optionId);
        }
        for (int i = 1; i < points.size(); i++) {
            SharePoint right = points.get(i);
            if (timestamp > right.timestampSec) continue;
            SharePoint left = points.get(i - 1);
            long span = right.timestampSec - left.timestampSec;
            if (span <= 0) return valueFor(right, optionId);
            float progress = (timestamp - left.timestampSec) / (float) span;
            return valueFor(left, optionId)
                    + (valueFor(right, optionId) - valueFor(left, optionId)) * progress;
        }
        return valueFor(points.get(points.size() - 1), optionId);
    }

    private static float valueFor(SharePoint point, int optionId) {
        return optionId == 1 ? point.noShare : point.yesShare;
    }

    private static long parseCreatedAt(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        };
        for (String pattern : patterns) {
            try {
                Date value = new SimpleDateFormat(pattern, Locale.US).parse(raw.trim());
                if (value != null) return value.getTime() / 1000L;
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }

    private static float clampPercent(float value) {
        return Math.max(0f, Math.min(100f, value));
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
