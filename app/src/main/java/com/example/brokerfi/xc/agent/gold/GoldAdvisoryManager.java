package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.DeepSeekClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoldAdvisoryManager {

    public static class Advisory {
        public String signal = "HOLD";
        public int confidence = 50;
        public double priceUsd = 0;
        public double change24h = 0;
        public double usdCny = 0;
        public String summary = "";
        public List<String> factors = new ArrayList<>();
    }

    public interface AdvisoryCallback {
        void onSuccess(Advisory advisory);
        void onError(String error);
    }

    public static void fetch(AdvisoryCallback callback) {
        AppExecutors.getInstance().networkIO().execute(() -> {
            AtomicReference<double[]> goldData = new AtomicReference<>(new double[]{0, 0});
            AtomicReference<Double> usdCny = new AtomicReference<>(0.0);
            CountDownLatch latch = new CountDownLatch(2);

            AppExecutors.getInstance().networkIO().execute(() -> {
                try { goldData.set(fetchGoldWithChange()); } finally { latch.countDown(); }
            });
            AppExecutors.getInstance().networkIO().execute(() -> {
                try { usdCny.set(fetchUsdCny()); } finally { latch.countDown(); }
            });

            try { latch.await(); } catch (InterruptedException ignored) {}

            double price = goldData.get()[0];
            double change = goldData.get()[1];
            double cny = usdCny.get();

            String systemPrompt = buildSystemPrompt();
            String userMessage = buildUserPrompt(price, change, cny);

            DeepSeekClient.chat(systemPrompt, userMessage, new DeepSeekClient.ChatCallback() {
                @Override
                public void onSuccess(String reply) {
                    Advisory a = parseAdvisory(reply, price, change, cny);
                    AppExecutors.getInstance().mainThread().execute(() -> callback.onSuccess(a));
                }
                @Override
                public void onError(String error) {
                    AppExecutors.getInstance().mainThread().execute(() -> callback.onError(error));
                }
            });
        });
    }

    // Gold price: gold-api.com with sina fallback

    static double[] fetchGoldWithChange() {
        try {
            URL url = new URL("https://api.gold-api.com/price/XAU");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     Scanner sc = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
                    String body = sc.hasNext() ? sc.next() : "";
                    JSONObject json = new JSONObject(body);
                    double price = json.optDouble("price", 0);
                    if (price > 0) {
                        double prevClose = fetchSinaPrevClose();
                        double change = prevClose > 0 ? (price - prevClose) / prevClose * 100 : 0;
                        return new double[]{price, change};
                    }
                }
            }
        } catch (Exception ignored) {}
        return fetchGoldSina();
    }

    private static double fetchSinaPrevClose() {
        try {
            URL url = new URL("https://hq.sinajs.cn/list=hf_XAU");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)");
            if (conn.getResponseCode() == 200) {
                try (InputStream is = conn.getInputStream();
                     Scanner sc = new Scanner(is, "GBK").useDelimiter("\\A")) {
                    String body = sc.hasNext() ? sc.next() : "";
                    int start = body.indexOf('"');
                    int end = body.lastIndexOf('"');
                    if (start >= 0 && end > start) {
                        String[] fields = body.substring(start + 1, end).split(",");
                        if (fields.length > 1) return Double.parseDouble(fields[1]);
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private static double[] fetchGoldSina() {
        try {
            URL url = new URL("https://hq.sinajs.cn/list=hf_XAU");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
            conn.setRequestProperty("Referer", "https://finance.sina.com.cn");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)");
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
                            if (price > 0) return new double[]{price, change};
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return new double[]{0, 0};
    }

    // USD/CNY exchange rate

    static double fetchUsdCny() {
        try {
            URL url = new URL("https://open.er-api.com/v6/latest/USD");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
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

    // DeepSeek prompts

    private static String buildSystemPrompt() {
        return "你是一位专业的黄金市场分析师，服务于散户投资者。" +
                "请综合多个维度进行分析，给出简明投资建议。" +
                "严格按要求的JSON格式输出，不要用代码块包裹。";
    }

    private static String buildUserPrompt(double price, double change, double cny) {
        String priceInfo = price > 0
                ? String.format("当前黄金现货价格 $%.2f/盎司，24小时涨跌幅 %+.2f%%", price, change)
                : "（实时金价暂时获取失败）";
        String cnyInfo = cny > 0
                ? String.format("当前美元兑人民币汇率 %.4f", cny)
                : "（汇率暂时获取失败）";

        return "【实时行情】\n" +
                "• " + priceInfo + "\n" +
                "• " + cnyInfo + "\n\n" +
                "请综合以下维度进行分析，给出简明投资建议：\n" +
                "1. 当前价位与近期走势研判\n" +
                "2. 地缘政治对黄金避险需求的影响\n" +
                "3. 美元走势与通胀预期\n" +
                "4. 央行购金动态与机构资金流向\n\n" +
                "严格按此 JSON 格式输出（不要用代码块包裹）：\n" +
                "GOLD_ADVISORY:{\"signal\":\"BUY或HOLD或SELL\",\"confidence\":置信度0到100," +
                "\"priceUsd\":" + (price > 0 ? price : 0) + "," +
                "\"summary\":\"一句话核心结论（中文，30字以内）\"," +
                "\"factors\":[\"因素1\",\"因素2\",\"因素3\"]}";
    }

    // Parse DeepSeek response

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
                if (upper.contains("BUY") || upper.contains("买入")) a.signal = "BUY";
                else if (upper.contains("SELL") || upper.contains("卖出")) a.signal = "SELL";
                a.summary = reply.length() > 80 ? reply.substring(0, 80) + "…" : reply;
            }
        } catch (Exception e) {
            a.summary = "结果解析异常，请重试";
        }
        return a;
    }
}
