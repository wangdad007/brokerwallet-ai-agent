package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.ai.DeepSeekClient;
import com.example.brokerfi.xc.agent.config.AgentConfig;
import com.example.brokerfi.xc.agent.gold.model.data.AppExecutors;
import com.example.brokerfi.xc.agent.gold.model.data.GoldBackendClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoldAdvisoryManager {

    private static double sinaPrevClose = 0;
    private static boolean sinaPrevCloseFetched = false;
    private static Advisory lastValidQuote;

    public static class Advisory {
        public String signal = "HOLD";
        public int confidence = 50;
        public double priceUsd = 0;
        public double change24h = 0;
        public boolean changeAvailable = false;
        public double usdCny = 0;
        public String quoteSource = "";
        public String quoteUpdatedAt = "";
        public boolean quoteDelayed = false;
        public String summary = "";
        public List<String> factors = new ArrayList<>();
    }

    public interface AdvisoryCallback {
        void onSuccess(Advisory advisory);
        void onError(String error);
    }

    public static void fetch(AdvisoryCallback callback) {
        if (!DeepSeekClient.isConfigured()) {
            AppExecutors.getInstance().mainThread().execute(() -> callback.onError("NO_API_KEY"));
            return;
        }
        AppExecutors.getInstance().networkIO().execute(() -> {
            AtomicReference<Advisory> goldQuote = new AtomicReference<>(emptyQuote());
            AtomicReference<Double> usdCny = new AtomicReference<>(0.0);
            CountDownLatch latch = new CountDownLatch(2);

            AppExecutors.getInstance().networkIO().execute(() -> {
                try { goldQuote.set(fetchGoldQuote()); } finally { latch.countDown(); }
            });
            AppExecutors.getInstance().networkIO().execute(() -> {
                try { usdCny.set(fetchUsdCny()); } finally { latch.countDown(); }
            });

            try { latch.await(); } catch (InterruptedException ignored) {}

            Advisory quote = goldQuote.get();
            double price = quote.priceUsd;
            double change = quote.change24h;
            double cny = usdCny.get();

            String systemPrompt = buildSystemPrompt();
            String userMessage = buildUserPrompt(quote, cny);

            DeepSeekClient.chat(systemPrompt, userMessage, new DeepSeekClient.ChatCallback() {
                @Override
                public void onSuccess(String reply) {
                    Advisory a = parseAdvisory(reply, price, change, cny);
                    copyQuoteMeta(quote, a);
                    AppExecutors.getInstance().mainThread().execute(() -> callback.onSuccess(a));
                }
                @Override
                public void onError(String error) {
                    AppExecutors.getInstance().mainThread().execute(() -> callback.onError(error));
                }
            });
        });
    }

    public static void fetchPrice(AdvisoryCallback callback) {
        AppExecutors.getInstance().networkIO().execute(() -> {
            try {
                Advisory quote = fetchGoldQuote();
                if (quote == null || quote.priceUsd <= 0) {
                    throw new IllegalStateException("实时黄金行情暂不可用");
                }
                AppExecutors.getInstance().mainThread().execute(() -> callback.onSuccess(quote));
            } catch (Exception e) {
                AppExecutors.getInstance().mainThread().execute(() -> callback.onError(e.getMessage()));
            }
        });
    }

    // Gold price: local backend first, then public sources, then last valid cache.

    static Advisory fetchGoldQuote() {
        try {
            GoldBackendClient.Quote backend = GoldBackendClient.fetchQuote();
            Advisory quote = emptyQuote();
            quote.priceUsd = backend.priceUsd;
            quote.change24h = backend.change24h;
            quote.changeAvailable = backend.changeAvailable;
            quote.quoteSource = backend.source;
            quote.quoteUpdatedAt = backend.updatedAt;
            quote.quoteDelayed = isWeekendNow();
            return rememberValidQuote(quote);
        } catch (Exception ignored) {}

        Advisory direct = fetchGoldApi();
        if (direct.priceUsd > 0) return rememberValidQuote(direct);

        Advisory sina = fetchGoldSina();
        if (sina.priceUsd > 0) return rememberValidQuote(sina);

        Advisory cached = cachedQuote();
        return cached != null ? cached : emptyQuote();
    }

    private static Advisory fetchGoldApi() {
        try {
            URL url = new URL(AgentConfig.GOLD_API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(AgentConfig.MARKET_DATA_TIMEOUT_MS);
            conn.setReadTimeout(AgentConfig.MARKET_DATA_TIMEOUT_MS);
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     Scanner sc = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
                    String body = sc.hasNext() ? sc.next() : "";
                    JSONObject json = new JSONObject(body);
                    double price = json.optDouble("price", 0);
                    if (price > 0) {
                        double prevClose = getSinaPrevClose();
                        double change = prevClose > 0 ? (price - prevClose) / prevClose * 100 : 0;
                        Advisory quote = emptyQuote();
                        quote.priceUsd = price;
                        quote.change24h = change;
                        quote.changeAvailable = prevClose > 0;
                        quote.quoteSource = "gold-api.com";
                        quote.quoteUpdatedAt = json.optString("updatedAtReadable",
                                json.optString("updatedAt", ""));
                        quote.quoteDelayed = isWeekendNow();
                        return quote;
                    }
                }
            }
        } catch (Exception ignored) {}
        return emptyQuote();
    }

    private static synchronized double getSinaPrevClose() {
        if (sinaPrevCloseFetched) return sinaPrevClose;
        sinaPrevCloseFetched = true;
        try {
            URL url = new URL(AgentConfig.SINA_GOLD_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(AgentConfig.MARKET_DATA_TIMEOUT_MS); conn.setReadTimeout(AgentConfig.MARKET_DATA_TIMEOUT_MS);
            conn.setRequestProperty("Referer", AgentConfig.SINA_REFERER);
            conn.setRequestProperty("User-Agent", AgentConfig.MARKET_USER_AGENT);
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     Scanner sc = new Scanner(is, "GBK").useDelimiter("\\A")) {
                    String body = sc.hasNext() ? sc.next() : "";
                    int start = body.indexOf('"');
                    int end = body.lastIndexOf('"');
                    if (start >= 0 && end > start) {
                        String[] fields = body.substring(start + 1, end).split(",");
                        if (fields.length > 1) sinaPrevClose = Double.parseDouble(fields[1]);
                    }
                }
            }
        } catch (Exception ignored) {}
        return sinaPrevClose;
    }

    private static Advisory fetchGoldSina() {
        try {
            URL url = new URL(AgentConfig.SINA_GOLD_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(AgentConfig.MARKET_DATA_TIMEOUT_MS); conn.setReadTimeout(AgentConfig.MARKET_DATA_TIMEOUT_MS);
            conn.setRequestProperty("Referer", AgentConfig.SINA_REFERER);
            conn.setRequestProperty("User-Agent", AgentConfig.MARKET_USER_AGENT);
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     Scanner sc = new Scanner(is, "GBK").useDelimiter("\\A")) {
                    String body = sc.hasNext() ? sc.next() : "";
                    int start = body.indexOf('"');
                    int end = body.lastIndexOf('"');
                    if (start >= 0 && end > start) {
                        String[] fields = body.substring(start + 1, end).split(",");
                        if (fields.length > 1) {
                            double price = Double.parseDouble(fields[0]);
                            double prevClose = Double.parseDouble(fields[1]);
                            double change = (prevClose > 0) ? (price - prevClose) / prevClose * 100 : 0;
                            if (price > 0) {
                                Advisory quote = emptyQuote();
                                quote.priceUsd = price;
                                quote.change24h = change;
                                quote.changeAvailable = prevClose > 0;
                                quote.quoteSource = "新浪财经";
                                quote.quoteUpdatedAt = fields.length > 12
                                        ? fields[12] + " " + fields[6]
                                        : fields.length > 6 ? fields[6] : "";
                                quote.quoteDelayed = true;
                                return quote;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return emptyQuote();
    }

    static double fetchUsdCny() {
        try {
            URL url = new URL(AgentConfig.FX_USD_CNY_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(AgentConfig.MARKET_DATA_TIMEOUT_MS); conn.setReadTimeout(AgentConfig.MARKET_DATA_TIMEOUT_MS);
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     Scanner sc = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
                    String body = sc.hasNext() ? sc.next() : "";
                    return new JSONObject(body).getJSONObject("rates").optDouble("CNY", 0);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static String buildSystemPrompt() {
        return "你是面向普通投资者的专业黄金市场分析师。" +
                "请综合多个维度给出简洁的市场评估。" +
                "用户提供的应用内行情或链上博弈池数据应视为权威输入，不得编造实时价格。" +
                "只返回指定 JSON，不要使用代码块，所有自然语言字段必须使用中文。";
    }

    private static String buildUserPrompt(Advisory quote, double cny) {
        String changeInfo = quote.changeAvailable
                ? String.format(Locale.US, "24 小时涨跌 %+.2f%%", quote.change24h)
                : "24 小时涨跌暂不可用";
        String priceInfo = quote.priceUsd > 0
                ? String.format(Locale.US, "当前黄金现货价格 %.2f 美元/盎司，%s，来源 %s，更新时间 %s",
                        quote.priceUsd, changeInfo, quote.quoteSource, quote.quoteUpdatedAt)
                : "（实时黄金行情暂不可用）";
        String cnyInfo = cny > 0
                ? String.format(Locale.US, "当前美元兑人民币汇率 %.4f", cny)
                : "（汇率暂不可用）";

        return "【实时市场数据】\n" +
                "• " + priceInfo + "\n" +
                "• " + cnyInfo + "\n\n" +
                "请从以下维度评估市场：\n" +
                "1. 当前价格水平与近期趋势\n" +
                "2. 地缘政治与避险需求\n" +
                "3. 美元走势与通胀预期\n" +
                "4. 央行购金与机构资金流\n\n" +
                "请严格返回以下 JSON，不要使用代码块：\n" +
                "GOLD_ADVISORY:{\"signal\":\"BUY|HOLD|SELL\",\"confidence\":0 to 100," +
                "\"priceUsd\":" + (quote.priceUsd > 0 ? quote.priceUsd : 0) + "," +
                "\"summary\":\"一条语义完整、不超过 50 个汉字的中文句子\"," +
                "\"factors\":[\"中文因素1\",\"中文因素2\",\"中文因素3\"]}";
    }

    private static Advisory parseAdvisory(String reply, double price, double change, double cny) {
        Advisory a = new Advisory();
        a.priceUsd = price;
        a.change24h = change;
        a.usdCny = cny;
        try {
            Pattern p = Pattern.compile("GOLD_ADVISORY:(\\{.*\\})", Pattern.DOTALL);
            Matcher m = p.matcher(reply);
            if (m.find()) {
                JSONObject json = new JSONObject(m.group(1));
                a.signal = json.optString("signal", "HOLD").toUpperCase();
                a.confidence = Math.max(0, Math.min(100, json.optInt("confidence", 50)));
                a.summary = json.optString("summary", "");
                JSONArray arr = json.optJSONArray("factors");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) a.factors.add(arr.getString(i));
                }
            } else {
                String upper = reply.toUpperCase();
                if (upper.contains("BUY") || upper.contains("买入") || upper.contains("看多")) a.signal = "BUY";
                else if (upper.contains("SELL") || upper.contains("卖出") || upper.contains("看空")) a.signal = "SELL";
                a.summary = reply.length() > 80 ? reply.substring(0, 80) + "…" : reply;
            }
        } catch (Exception e) {
            a.summary = "分析结果解析失败，请重试";
        }
        return a;
    }

    private static Advisory emptyQuote() {
        Advisory quote = new Advisory();
        quote.quoteSource = "";
        quote.quoteUpdatedAt = "";
        quote.quoteDelayed = true;
        return quote;
    }

    private static synchronized Advisory rememberValidQuote(Advisory quote) {
        if (quote == null || quote.priceUsd <= 0) return quote;
        lastValidQuote = copyQuote(quote);
        return quote;
    }

    private static synchronized Advisory cachedQuote() {
        if (lastValidQuote == null || lastValidQuote.priceUsd <= 0) return null;
        Advisory cached = copyQuote(lastValidQuote);
        cached.quoteDelayed = true;
        cached.quoteSource = cached.quoteSource == null || cached.quoteSource.trim().isEmpty()
                ? "近期行情（缓存）" : cached.quoteSource + "（缓存）";
        return cached;
    }

    private static Advisory copyQuote(Advisory source) {
        Advisory copy = emptyQuote();
        copy.priceUsd = source.priceUsd;
        copy.change24h = source.change24h;
        copy.changeAvailable = source.changeAvailable;
        copy.usdCny = source.usdCny;
        copy.quoteSource = source.quoteSource;
        copy.quoteUpdatedAt = source.quoteUpdatedAt;
        copy.quoteDelayed = source.quoteDelayed;
        return copy;
    }

    private static void copyQuoteMeta(Advisory from, Advisory to) {
        to.quoteSource = from.quoteSource;
        to.quoteUpdatedAt = from.quoteUpdatedAt;
        to.quoteDelayed = from.quoteDelayed;
        to.changeAvailable = from.changeAvailable;
    }

    private static boolean isWeekendNow() {
        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY;
    }
}
