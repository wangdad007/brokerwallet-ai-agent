package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GoldMarketRepositoryTest {
    @Test
    public void parseTokenAmountToWei_acceptsDecimalBkcValues() {
        assertEquals(new BigInteger("1250000000000000000"),
                GoldMarketRepository.parseTokenAmountToWei("1.25"));
        assertEquals(new BigInteger("1000000000000000"),
                GoldMarketRepository.parseTokenAmountToWei("0.001"));
    }

    @Test
    public void parseTokenAmountToWei_rejectsInvalidOrNonPositiveValues() {
        assertNull(GoldMarketRepository.parseTokenAmountToWei(""));
        assertNull(GoldMarketRepository.parseTokenAmountToWei("abc"));
        assertNull(GoldMarketRepository.parseTokenAmountToWei("0"));
        assertNull(GoldMarketRepository.parseTokenAmountToWei("-1"));
    }

    @Test
    public void claimRewardFunction_matchesContractSignature() {
        Function function = GoldMarketRepository.buildClaimRewardFunction(1, 0);

        assertEquals("claimReward", function.getName());
        assertEquals(2, function.getInputParameters().size());
        assertEquals(BigInteger.ONE,
                ((Uint256) function.getInputParameters().get(0)).getValue());
        assertEquals(BigInteger.ZERO,
                ((Uint8) function.getInputParameters().get(1)).getValue());
    }

    @Test
    public void releaseModeUsesOnlyOfficialMarketEndpoint() {
        assertEquals(GoldMarketSecurityPolicy.DEFAULT_CONTRACT_ADDRESS,
                GoldMarketSecurityPolicy.resolveContractAddress(false,
                        "0x1111111111111111111111111111111111111111"));
        assertEquals("",
                GoldMarketSecurityPolicy.resolveRpcUrl(false, "http://10.0.2.2:48347"));
        assertFalse(GoldMarketSecurityPolicy.isDeveloperMarketToolsEnabled(false));
    }

    @Test
    public void debugModeAllowsDeveloperMarketOverrides() {
        String customAddress = "0x2222222222222222222222222222222222222222";
        String customRpc = "http://10.0.2.2:48347";

        assertEquals(customAddress,
                GoldMarketSecurityPolicy.resolveContractAddress(true, customAddress));
        assertEquals(customRpc, GoldMarketSecurityPolicy.resolveRpcUrl(true, customRpc));
        assertTrue(GoldMarketSecurityPolicy.isDeveloperMarketToolsEnabled(true));
    }

    @Test
    public void appDoesNotShipPredictionMarketDeploymentBytecode() {
        assertFalse(Files.exists(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/PredictionMarketBytecode.java")));
    }

    @Test
    public void predictionMarketRestrictsMarketCreationToOracle() throws Exception {
        String source = new String(Files.readAllBytes(repoPath("contracts/PredictionMarket.sol")));

        assertTrue(source.contains("modifier onlyOracle()"));
        assertTrue(source.contains(") public onlyOracle {"));
        assertTrue(source.contains("require(_duration >= MIN_GAME_DURATION"));
        assertTrue(source.contains("require(_optionNames.length <= MAX_OPTIONS"));
    }

    private static Path repoPath(String path) {
        Path fromRoot = Paths.get(path);
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        return Paths.get("..").resolve(path).normalize();
    }
}
