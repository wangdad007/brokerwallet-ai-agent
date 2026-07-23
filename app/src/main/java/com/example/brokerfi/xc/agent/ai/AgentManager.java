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
            callback.onError("消息可能包含私钥或助记词，因此没有发送给 AI");
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
            callback.onError("消息可能包含私钥或助记词，因此没有发送给 AI");
            return;
        }
        DeepSeekClient.chat(
                "你是 BrokerChain 黄金预测市场的 AI 投研助手。"
                        + "只回答黄金现货、美元、避险需求、链上预测市场与交易风险相关问题。"
                        + "用户消息中的【实时黄金行情】和【链上博弈池快照】是在提问前即时获取的数据，应优先使用。"
                        + "不得声称无法访问已提供的价格，也不得编造实时数据。"
                        + "分析单个博弈池时，应考虑判定规则、当前金价、YES/NO 份额、剩余时间与市场深度。"
                        + "明确给出偏向 YES、偏向 NO 或观望的判断，并解释价格证据与市场份额之间的差异。"
                        + "扫描全市场时最多分析 12 个博弈池，完整报告最多 6000 字。"
                        + "使用结构清晰、可执行的中文，保证结尾完整，并包含风险提示与数据时间戳。",
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
