package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketSecurityPolicy;
import com.example.brokerfi.xc.agent.gold.view.GoldNoteMarketActivity;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
    public void gameCountFunction_matchesContractGetter() {
        Function function = GoldMarketRepository.buildGameCountFunction();

        assertEquals("gameCount", function.getName());
        assertTrue(function.getInputParameters().isEmpty());
        assertEquals(1, function.getOutputParameters().size());
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
    public void developerContractRegistryParsesMultipleAddresses() {
        String one = "0x1111111111111111111111111111111111111111";
        String two = "0x2222222222222222222222222222222222222222";

        List<String> addresses = GoldMarketSecurityPolicy.resolveContractAddresses(
                true, one + "\n" + two + "\ninvalid");

        assertEquals(2, addresses.size());
        assertEquals(one, addresses.get(0));
        assertEquals(two, addresses.get(1));
    }

    @Test
    public void appDoesNotShipPredictionMarketDeploymentBytecode() {
        assertFalse(Files.exists(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/data/PredictionMarketBytecode.java")));
    }

    @Test
    public void goldMarketUiKeepsDeepSeekResearchAssistant() throws Exception {
        String layout = new String(Files.readAllBytes(repoPath(
                "app/src/main/res/layout/fragment_gold_market_list.xml")));
        String activity = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/ui/GoldMarketListFragment.java")));

        assertTrue(layout.contains("card_ai_advice"));
        assertTrue(layout.contains("tv_ai_signal"));
        assertTrue(layout.contains("DeepSeek"));
    }

    @Test
    public void countdownNormalizesSecondAndMillisecondDeadlines() {
        long nowMillis = 1_764_000_000_000L;

        assertEquals(3600,
                GoldNoteMarketActivity.remainingSecondsUntilDeadline(nowMillis / 1000 + 3600, nowMillis));
        assertEquals(3600,
                GoldNoteMarketActivity.remainingSecondsUntilDeadline(nowMillis + 3_600_000, nowMillis));
        assertEquals(0,
                GoldNoteMarketActivity.remainingSecondsUntilDeadline(nowMillis - 1000, nowMillis));
    }

    @Test
    public void shareAmountDisplayKeepsFractionalHoldings() {
        assertEquals("0.2", GoldNoteMarketActivity.formatShareAmount(
                new BigInteger("200000000000000000")));
        assertEquals("1.25", GoldNoteMarketActivity.formatShareAmount(
                new BigInteger("1250000000000000000")));
        assertEquals("<0.000001", GoldNoteMarketActivity.formatShareAmount(BigInteger.ONE));
    }

    private static Path repoPath(String path) {
        Path fromRoot = Paths.get(path);
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        return Paths.get("..").resolve(path).normalize();
    }
}
