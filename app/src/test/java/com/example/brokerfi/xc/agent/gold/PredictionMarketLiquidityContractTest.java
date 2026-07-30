package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PredictionMarketLiquidityContractTest {
    @Test
    public void creatorAndLaterProvidersReceiveAuditableLpRights() throws Exception {
        String source = read("contracts/PredictionMarket.sol");

        assertTrue(source.contains("mapping(uint256 => uint256) public totalLiquidityShares"));
        assertTrue(source.contains(
                "mapping(uint256 => mapping(address => uint256)) public liquidityShares"));
        assertTrue(source.contains("totalLiquidityShares[gameCount] = msg.value"));
        assertTrue(source.contains("liquidityShares[gameCount][msg.sender] = msg.value"));
        assertTrue(source.contains("gameCreators[gameCount] = msg.sender"));
        assertTrue(source.contains("creatorLockedLiquidityShares[gameCount] = msg.value"));
        assertTrue(source.contains("\"Creator initial liquidity is locked\""));
        assertTrue(source.contains("function addLiquidity("));
        assertTrue(source.contains("function removeLiquidity("));
        assertTrue(source.contains("function quoteAddLiquidity("));
        assertTrue(source.contains("function quoteRemoveLiquidity("));
        assertTrue(source.contains("function getLiquidityPosition("));
        assertTrue(source.contains("liquidityShares[i][_user] > 0"));
        assertTrue(source.contains("TRADING_FEE_BPS = 100"));
        assertTrue(source.contains("_accrueTradingFee(_gameId, fee)"));
        assertFalse(source.contains("Admin liquidity reclaim failed"));
    }

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relativePath).normalize();
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
