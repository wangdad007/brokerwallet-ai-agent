package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.config.AgentConfig;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketStatusStyle;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AgentLocalConfigTest {
    @Test
    public void localAgentConfigCentralizesServiceUrls() {
        assertEquals("10.0.2.2", AgentConfig.LOCAL_HOST);
        assertEquals("http://10.0.2.2:8081", AgentConfig.BACKEND_BASE_URL);
        assertEquals("http://10.0.2.2:56741/", AgentConfig.BROKER_CHAIN_BASE_URL);
        assertEquals("http://10.0.2.2:8083/ipfs/", AgentConfig.IPFS_GATEWAY_URL);
        assertEquals("http://10.0.2.2:5001/api/v0/add", AgentConfig.IPFS_API_ADD_URL);
        assertEquals("https://api.deepseek.com/chat/completions", AgentConfig.DEEPSEEK_API_URL);
        assertEquals("https://api.gold-api.com/price/XAU", AgentConfig.GOLD_API_URL);
        assertEquals("https://hq.sinajs.cn/list=hf_XAU", AgentConfig.SINA_GOLD_URL);
        assertEquals("https://open.er-api.com/v6/latest/USD", AgentConfig.FX_USD_CNY_URL);
    }

    @Test
    public void statusStyleSeparatesActiveExpiredResolvedAndRefundedMarkets() {
        GoldMarketStatusStyle active = GoldMarketStatusStyle.forMarket(false, false, 60);
        GoldMarketStatusStyle pending = GoldMarketStatusStyle.forMarket(false, false, 0);
        GoldMarketStatusStyle resolved = GoldMarketStatusStyle.forMarket(true, false, 0);
        GoldMarketStatusStyle refunded = GoldMarketStatusStyle.forMarket(false, true, 0);

        assertEquals("运行中", active.label);
        assertEquals("等待裁决", pending.label);
        assertEquals("已开奖", resolved.label);
        assertEquals("已退款", refunded.label);

        assertEquals(0xFF047857, active.textColor);
        assertEquals(0xFFB45309, pending.textColor);
        assertEquals(0xFF2563EB, resolved.textColor);
        assertEquals(0xFF64748B, refunded.textColor);
    }

    @Test
    public void agentSourceDoesNotReintroduceRemoteBrokerOldIpfsGatewayOrEmojiBadges()
            throws Exception {
        Path root = Paths.get("src/main/java/com/example/brokerfi/xc/agent");
        StringBuilder allSource = new StringBuilder();
        StringBuilder sourceOutsideConfig = new StringBuilder();
        Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        allSource.append(text);
                        if (!path.endsWith(Paths.get("config", "AgentConfig.java"))) {
                            sourceOutsideConfig.append(text);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        String source = allSource.toString();
        assertFalse(source.contains("dash.broker-chain.com"));
        assertFalse(source.contains("10.0.2.2:8080/ipfs"));
        assertFalse(source.contains("✅"));
        assertFalse(source.contains("❌"));
        assertFalse(source.contains("⚠️"));
        assertFalse(source.contains("⚠"));

        String nonConfigSource = sourceOutsideConfig.toString();
        assertFalse(nonConfigSource.contains("https://api.deepseek.com/chat/completions"));
        assertFalse(nonConfigSource.contains("https://api.gold-api.com/price/XAU"));
        assertFalse(nonConfigSource.contains("https://hq.sinajs.cn/list=hf_XAU"));
        assertFalse(nonConfigSource.contains("https://open.er-api.com/v6/latest/USD"));
    }
}
