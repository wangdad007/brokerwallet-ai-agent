package com.example.brokerfi.xc.agent;

import android.app.Activity;

import com.example.brokerfi.xc.MyUtil;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.net.ABIUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * AI Agent 主控制器 — 单例，编排 Wallet 现有 API + DeepSeek AI 分析。
 * 不修改任何 Wallet 底层代码，只做只读调用和策略建议。
 */
public class AgentManager {
    private static AgentManager instance;
    private final Gson gson = new Gson();

    private AgentManager() {}

    public static AgentManager getInstance() {
        if (instance == null) instance = new AgentManager();
        return instance;
    }

    // =============== Broker 收益分析 ===============

    /**
     * 查询各 shard 收益并让 AI 给出调仓建议。
     * 返回的 BrokerReport 可直接展示给用户。
     */
    public void analyzeBroker(Activity activity, AnalysisCallback callback) {
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

                // 解析 shard 数据
                ShardProfit[] shards = parseShardProfits(profitJson);

                // 构建 prompt
                StringBuilder sb = new StringBuilder();
                sb.append("Analyze these Broker staking shards and give rebalancing advice:\n");
                for (ShardProfit s : shards) {
                    sb.append(String.format("- Shard %d: profit=%.4f BKC, stake=%.0f BKC, yield=%.2f%%\n",
                            s.shardIndex, s.profit, s.staked, s.yieldPct * 100));
                }
                sb.append("\nGive: 1) which shard to withdraw from, 2) which shard to stake more, 3) estimated improvement. Keep under 150 words.");

                DeepSeekClient.chat(
                        "You are a DeFi staking strategist. Analyze shard profit data and give specific, numbered advice.",
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
                                // 即使 AI 不可用，也返回原始数据
                                BrokerReport report = new BrokerReport();
                                report.rawAnalysis = "AI analysis unavailable. Raw data shown below.";
                                report.shards = shards;
                                callback.onBrokerReport(report);
                            }
                        });
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    // =============== NFT 市场 AI 推荐 ===============

    public void recommendNFTs(Activity activity, AnalysisCallback callback) {
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
                sb.append("Analyze these listed NFTs and recommend which are worth buying:\n");
                int count = Math.min(result.names.length, 20);
                for (int i = 0; i < count; i++) {
                    sb.append(String.format("- #%d: %s, price=%s, shares=%s\n",
                            result.nftIds[i], result.names[i],
                            result.pricesList[i], result.sharesList[i]));
                }
                sb.append("\nRecommend top 3 with reasoning. Keep under 150 words.");

                DeepSeekClient.chatSimple(sb.toString(), new DeepSeekClient.ChatCallback() {
                    @Override
                    public void onSuccess(String response) {
                        callback.onGeneralAdvice("NFT Market Analysis", response);
                    }

                    @Override
                    public void onError(String error) {
                        callback.onGeneralAdvice("NFT Market (no AI)", "Found " + result.names.length + " NFTs listed.");
                    }
                });
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    // =============== 通用对话 ===============

    public void askAnything(String question, AnalysisCallback callback) {
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

    // =============== 工具方法 ===============

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
            return new ShardProfit[0];
        }
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    // =============== 数据模型 ===============

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
