package com.example.brokerfi.xc.agent.ai;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brokerfi.xc.MyUtil;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.net.ABIUtils;
import com.google.gson.Gson;
import android.util.Log;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

public class AgentManager {
    private final Gson gson = new Gson();

    private AgentManager() {}

    private static class Holder {
        static final AgentManager INSTANCE = new AgentManager();
    }

    public static AgentManager getInstance() {
        return Holder.INSTANCE;
    }

    public void analyzeBroker(AppCompatActivity activity, AnalysisCallback callback) {
        String pk = StorageUtil.getCurrentPrivatekey(activity);
        if (pk == null || pk.isEmpty()) {
            callback.onError("No account selected");
            return;
        }

        new Thread(() -> {
            try {
                String profitJson = MyUtil.querybrokerprofit(pk);
                if (profitJson == null || profitJson.isEmpty()) {
                    callback.onError("Failed to query broker profit");
                    return;
                }

                ShardProfit[] shards = parseShardProfits(profitJson);

                StringBuilder sb = new StringBuilder();
                sb.append("Analyze the following broker-shard staking data and recommend a reallocation:\n");
                for (ShardProfit s : shards) {
                    sb.append(String.format("- Shard %d: profit=%.4f BKC, staked=%.0f BKC, yield=%.2f%%\n",
                            s.shardIndex, s.profit, s.staked, s.yieldPct * 100));
                }
                sb.append("\nProvide: 1) the shard to reduce, 2) the shard to increase, and 3) the estimated improvement. Keep it under 150 words.");

                DeepSeekClient.chat(
                        "You are a DeFi staking strategist. Give specific, numbered recommendations in English based on the shard returns.",
                        sb.toString(),
                        new DeepSeekClient.ChatCallback() {
                            @Override
                            public void onSuccess(String response) {
                                BrokerReport report = new BrokerReport();
                                report.rawAnalysis = response;
                                report.shards = shards;
                                callback.onBrokerReport(report);
                            }

                            @Override
                            public void onError(String error) {
                                BrokerReport report = new BrokerReport();
                                report.rawAnalysis = "AI analysis is unavailable. Raw data follows:";
                                report.shards = shards;
                                callback.onBrokerReport(report);
                            }
                        });
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void recommendNFTs(AppCompatActivity activity, AnalysisCallback callback) {
        String pk = StorageUtil.getCurrentPrivatekey(activity);
        if (pk == null || pk.isEmpty()) {
            callback.onError("No account selected");
            return;
        }

        new Thread(() -> {
            try {
                String data = ABIUtils.encodeGetListedNFTs();
                String hexResult = MyUtil.sendethcall(data, pk);

                ABIUtils.ListedNFTsResult result = ABIUtils.decodeGetListedNFTs(hexResult);
                if (result == null || result.names == null || result.names.length == 0) {
                    callback.onError("No NFTs listed");
                    return;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("Analyze the listed NFTs and recommend the most compelling purchases:\n");
                int count = Math.min(result.names.length, 20);
                for (int i = 0; i < count; i++) {
                    sb.append(String.format("- #%d: %s, price=%s, shares=%s\n",
                            result.nftIds[i], result.names[i],
                            result.pricesList[i], result.sharesList[i]));
                }
                sb.append("\nRecommend the top three with reasons. Keep it under 150 words.");

                DeepSeekClient.chatSimple(sb.toString(), new DeepSeekClient.ChatCallback() {
                    @Override
                    public void onSuccess(String response) {
                        callback.onGeneralAdvice("NFT Market Analysis", response);
                    }

                    @Override
                    public void onError(String error) {
                        callback.onGeneralAdvice("NFT Market (offline)", "Found " + result.names.length + " listed NFTs.");
                    }
                });
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    public void askAnything(String question, AnalysisCallback callback) {
        if (containsPrivateKey(question)) {
            callback.onError("Your message may contain a private key or recovery phrase, so it was not sent to the AI.");
            return;
        }
        DeepSeekClient.chatSimple(question, new DeepSeekClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                callback.onGeneralAdvice(question, response);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void askGoldResearch(String question, AnalysisCallback callback) {
        if (containsPrivateKey(question)) {
            callback.onError("Your message may contain a private key or recovery phrase, so it was not sent to the AI.");
            return;
        }
        DeepSeekClient.chat(
                "You are the AI research assistant for BrokerChain's gold prediction market. "
                        + "Answer only about spot gold, the US dollar, safe-haven demand, on-chain prediction markets, and trading risk. "
                        + "The [Live gold quote] and [On-chain market snapshot] in the user's message are fetched immediately before the question; prioritize them. "
                        + "Do not claim that supplied prices are inaccessible and never invent live data. "
                        + "For a specific market, consider its resolution rule, current gold price, YES/NO shares, remaining time, and market depth. "
                        + "State a clear YES bias, NO bias, or hold view and explain any difference between price evidence and market shares. "
                        + "For a market-wide scan, analyze at most 12 markets. A full report may use up to 6,000 words. "
                        + "Write structured, actionable English, complete the final sentence, and include a risk warning and data timestamp.",
                question,
                new DeepSeekClient.ChatCallback() {
                    @Override
                    public void onSuccess(String response) {
                        callback.onGeneralAdvice(question, response);
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                });
    }

    private static boolean containsPrivateKey(String text) {
        if (text == null) return false;
        String stripped = text.replaceAll("\\s+", "");
        if (stripped.matches("(?i)^[0-9a-f]{64}$")) return true;
        int wordCount = text.trim().split("\\s+").length;
        return wordCount == 12 || wordCount == 24;
    }

    public ShardProfit[] parseShardProfitsPublic(String json) {
        return parseShardProfits(json);
    }

    private ShardProfit[] parseShardProfits(String json) {
        try {
            Type mapType = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> map = gson.fromJson(json, mapType);

            ShardProfit[] shards = new ShardProfit[map.size()];
            int i = 0;
            for (Map.Entry<String, String> e : map.entrySet()) {
                ShardProfit s = new ShardProfit();
                s.shardIndex = Integer.parseInt(e.getKey());
                String[] parts = e.getValue().split("/");
                if (parts.length >= 2) {
                    s.profit = parseDouble(parts[0]);
                    s.staked = parseDouble(parts[1]);
                }
                s.yieldPct = s.staked > 0 ? s.profit / s.staked : 0;
                shards[i++] = s;
            }
            return shards;
        } catch (Exception e) {
            Log.e("AgentManager", "Failed to parse broker profit data: " + json, e);
            return new ShardProfit[0];
        }
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    public static class ShardProfit {
        public int shardIndex;
        public double profit;
        public double staked;
        public double yieldPct;
    }

    public static class BrokerReport {
        public String rawAnalysis;
        public ShardProfit[] shards;
    }

    public interface AnalysisCallback {
        void onBrokerReport(BrokerReport report);
        void onGeneralAdvice(String question, String answer);
        void onError(String error);
    }
}
