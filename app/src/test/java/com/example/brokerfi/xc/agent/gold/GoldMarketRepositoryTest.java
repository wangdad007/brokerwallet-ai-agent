package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;

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
    public void localEthCallTransactionIncludesBrokerChainRequiredFields() {
        Transaction transaction = GoldMarketRepository.buildLocalEthCallTransaction(
                "0x72986123faedc50b508805d4358b95c6a93fda7a",
                "0x9c30d2C05CAf1B7Fe3Fa2D81D19a0F81B2fC5444",
                "0x4d1975b4");

        assertEquals("0x4c4b40", transaction.getGas());
        assertEquals("0x0", transaction.getGasPrice());
        assertEquals("0x0", transaction.getValue());
        assertEquals("0x4d1975b4", transaction.getData());
    }

    @Test
    public void localWriteTransactionIncludesBrokerChainRequiredFieldsAndValue() {
        String buyUpData = "0xc22acd36"
                + "0000000000000000000000000000000000000000000000000000000000000001"
                + "0000000000000000000000000000000000000000000000000000000000000000";
        Transaction transaction = GoldMarketRepository.buildLocalWriteTransaction(
                "0x72986123faedc50b508805d4358b95c6a93fda7a",
                "0x9c30d2C05CAf1B7Fe3Fa2D81D19a0F81B2fC5444",
                buyUpData,
                new BigInteger("100000000000000000"));

        assertEquals("0x4c4b40", transaction.getGas());
        assertEquals("0x0", transaction.getGasPrice());
        assertEquals("0x16345785d8a0000", transaction.getValue());
        assertEquals(buyUpData, transaction.getData());
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
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/PredictionMarketBytecode.java")));
    }

    @Test
    public void predictionMarketRestrictsMarketCreationToOracle() throws Exception {
        String source = new String(Files.readAllBytes(repoPath("contracts/PredictionMarket.sol")));

        assertTrue(source.contains("modifier onlyOracle()"));
        assertTrue(source.contains(") public onlyOracle {"));
        assertTrue(source.contains("require(_duration >= MIN_GAME_DURATION"));
        assertTrue(source.contains("require(_optionNames.length <= MAX_OPTIONS"));
        assertTrue(source.contains("_durationToChainUnits"));
        assertTrue(source.contains("durationSeconds * 1000"));
    }

    @Test
    public void goldMarketUiDoesNotExposeMarketCreation() throws Exception {
        String layout = new String(Files.readAllBytes(repoPath(
                "app/src/main/res/layout/activity_gold_note_market.xml")));
        String activity = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/GoldNoteMarketActivity.java")));

        assertFalse(layout.contains("btn_create_market"));
        assertFalse(layout.contains("创建市场"));
        assertFalse(activity.contains("showCreateMarketDialog"));
    }

    @Test
    public void goldMarketUiKeepsDeepSeekResearchAssistant() throws Exception {
        String layout = new String(Files.readAllBytes(repoPath(
                "app/src/main/res/layout/activity_gold_note_market.xml")));
        String activity = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/GoldNoteMarketActivity.java")));

        assertTrue(layout.contains("card_ai_advice"));
        assertTrue(layout.contains("tv_ai_signal"));
        assertTrue(layout.contains("tv_gold_quote_meta"));
        assertTrue(layout.contains("market_list_container"));
        assertTrue(layout.contains("DeepSeek"));
        assertTrue(activity.contains("showApiKeyDialog"));
        assertTrue(activity.contains("DeepSeekClient"));
        assertTrue(activity.contains("buildGoldResearchPrompt"));
        assertTrue(activity.contains("必须以这里的金价和链上池子为准"));
        assertTrue(activity.contains("getGameCount"));
        assertTrue(activity.contains("availableGames"));
        assertTrue(activity.contains("getSelectedGameId()"));
        assertTrue(activity.contains("selectedContractAddress"));
        assertTrue(activity.contains("repositoryForSelectedMarket()"));
        assertTrue(activity.contains("GoldMarketRepository.getContractAddresses"));
        assertTrue(activity.contains("loadMarketByIdAscending"));
        assertTrue(activity.contains("shouldShowInMarketList"));
        assertTrue(activity.contains("hasUserShares"));
        assertTrue(activity.contains("\"市场 \" + displayIndex"));
        assertFalse(activity.contains("\"#\" + game.id"));
        assertTrue(activity.contains("hiddenClosedMarketCount"));
        assertTrue(activity.contains("如果刚在 Remix 创建，请确认使用了新版合约"));
    }

    @Test
    public void marketModelCarriesContractAddressForTradeRouting() throws Exception {
        String repository = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/GoldMarketRepository.java")));
        String activity = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/GoldNoteMarketActivity.java")));

        assertTrue(repository.contains("KEY_CONTRACT_ADDRS"));
        assertTrue(repository.contains("getContractAddresses"));
        assertTrue(repository.contains("public String contractAddress"));
        assertTrue(repository.contains("model.contractAddress = contractAddress"));
        assertTrue(activity.contains("repositoryForContract"));
        assertTrue(activity.contains("sameContract"));
        assertTrue(activity.contains("repositoryForSelectedMarket().buyShares"));
        assertTrue(activity.contains("repositoryForSelectedMarket().sellShares"));
        assertTrue(activity.contains("repositoryForSelectedMarket().claimReward"));
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

    @Test
    public void aiAssistantReplacesLoadingMessageByStableIndex() throws Exception {
        String activity = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/AIAssistantActivity.java")));

        assertTrue(activity.contains("private int addMessage(String sender, String text)"));
        assertTrue(activity.contains("return addMessageNow(sender, safeText(text));"));
        assertTrue(activity.contains("private int beginLoading(String text)"));
        assertTrue(activity.contains("finishLoading(loadingIndex, answer)"));
        assertTrue(activity.contains("finishLoading(loadingIndex, formatAiError(error))"));
        assertTrue(activity.contains("buildQuestionForAi"));
        assertFalse(activity.contains("addMessage(\"AI\", \"思考中...\");\n"
                + "        int loadingIndex = messageContainer.getChildCount() - 1;"));
    }

    @Test
    public void deepSeekClientReportsNetworkAndApiErrors() throws Exception {
        String client = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/DeepSeekClient.java")));

        assertTrue(client.contains("CONNECT_TIMEOUT_MS"));
        assertTrue(client.contains("READ_TIMEOUT_MS"));
        assertTrue(client.contains("conn.getErrorStream()"));
        assertTrue(client.contains("buildHttpError"));
        assertTrue(client.contains("extractContent"));
        assertTrue(client.contains("NO_API_KEY"));
    }

    @Test
    public void aiAssistantIsFocusedOnGoldResearch() throws Exception {
        String layout = new String(Files.readAllBytes(repoPath(
                "app/src/main/res/layout/activity_ai_assistant.xml")));
        String activity = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/AIAssistantActivity.java")));
        String manager = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/AgentManager.java")));

        assertTrue(layout.contains("黄金投研助手"));
        assertTrue(layout.contains("询问黄金票据投研建议"));
        assertFalse(layout.contains("btn_broker_scan"));
        assertFalse(layout.contains("btn_nft_market"));
        assertFalse(layout.contains("btn_deep_analysis"));
        assertFalse(activity.contains("BrokerAdvisor"));
        assertFalse(activity.contains("onBrokerScan"));
        assertFalse(activity.contains("onNFTMarket"));
        assertFalse(activity.contains("onDeepAnalysis"));
        assertTrue(activity.contains("askGoldResearch"));
        assertTrue(manager.contains("askGoldResearch"));
        assertTrue(manager.contains("必须以这些数据为准，不要编造其他实时价格"));
    }

    @Test
    public void goldQuoteCarriesSourceAndUpdateMeta() throws Exception {
        String advisory = new String(Files.readAllBytes(repoPath(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/GoldAdvisoryManager.java")));

        assertTrue(advisory.contains("quoteSource"));
        assertTrue(advisory.contains("quoteUpdatedAt"));
        assertTrue(advisory.contains("updatedAtReadable"));
        assertTrue(advisory.contains("新浪财经"));
    }

    private static Path repoPath(String path) {
        Path fromRoot = Paths.get(path);
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        return Paths.get("..").resolve(path).normalize();
    }
}
