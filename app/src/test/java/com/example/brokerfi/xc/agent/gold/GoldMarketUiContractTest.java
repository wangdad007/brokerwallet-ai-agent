package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldMarketUiContractTest {
    private static final String AI_ACTIVITY_PATH =
            "app/src/main/java/com/example/brokerfi/xc/AIAssistantActivity.java";
    private static final String DETAIL_ACTIVITY_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/ui/"
                    + "GoldMarketDetailActivity.java";
    private static final String DETAIL_LAYOUT_PATH =
            "app/src/main/res/layout/activity_gold_market_detail.xml";

    @Test
    public void chatDisplaysExistingSummaryAndUsesSavedContextForFollowUps() throws Exception {
        String source = readUtf8(AI_ACTIVITY_PATH);

        assertTrue(source.contains(
                "public static final String EXTRA_MARKET_CONTEXT = \"MARKET_CONTEXT\";"));
        assertTrue(source.contains(
                "public static final String EXTRA_INITIAL_AI_SUMMARY = \"INITIAL_AI_SUMMARY\";"));
        assertTrue(source.contains("INITIAL_PROMPT"));
        assertTrue(source.contains("!isBlank(initialSummary)"));
        assertTrue(source.contains("!isBlank(initialPrompt)"));

        String summaryBranch = blockAfter(source, "if (!isBlank(initialSummary))");
        assertTrue(summaryBranch.contains("addMessage(\"AI\", initialSummary)"));
        assertFalse(
                "Pre-generated summaries must never trigger another AI request",
                summaryBranch.contains("submitQuestion("));

        String legacyBranch = blockAfter(source, "else if (!isBlank(initialPrompt))");
        assertInOrder(
                legacyBranch,
                "marketContext = initialPrompt",
                "legacyInitialPromptInFlight = true",
                "submitQuestion(initialPrompt)");

        String bypassBranch = blockAfter(
                source, "if (legacyInitialPromptInFlight)");
        assertInOrder(
                bypassBranch,
                "legacyInitialPromptInFlight = false",
                "return text");

        assertTrue(
                "Normal follow-ups must delegate context and text to the shared builder",
                Pattern.compile(
                        "return\\s+GoldMarketResearchPromptBuilder\\.withFollowUp\\s*"
                                + "\\(\\s*marketContext\\s*,\\s*text\\s*\\)\\s*;")
                        .matcher(source)
                        .find());
        assertTrue(
                "Blank checks must reject whitespace-only values",
                Pattern.compile(
                        "isBlank\\s*\\(\\s*String\\s+value\\s*\\).*?"
                                + "TextUtils\\.isEmpty\\(value\\).*?"
                                + "value\\.trim\\(\\)\\.isEmpty\\(\\)",
                        Pattern.DOTALL)
                        .matcher(source)
                        .find());
    }

    @Test
    public void detailLayoutPlacesAiCardBeforeTransactionsAndUnifiesBuyButtons()
            throws Exception {
        String layout = readUtf8(DETAIL_LAYOUT_PATH);

        int marketPanelEnd = layout.indexOf("@+id/tv_countdown");
        int aiCard = layout.indexOf("@+id/card_market_ai");
        int transactionHeading = layout.indexOf("android:text=\"交易\"");
        assertTrue("AI card must follow the AMM market panel",
                marketPanelEnd >= 0 && aiCard > marketPanelEnd);
        assertTrue("AI card must precede the transaction section",
                transactionHeading > aiCard);

        String cardTag = openingTag(layout, "LinearLayout", "card_market_ai");
        assertTrue(cardTag.contains("android:clickable=\"true\""));
        assertTrue(cardTag.contains("android:focusable=\"true\""));
        assertTrue(layout.contains("@+id/tv_market_ai_status"));
        assertTrue(layout.contains("@+id/tv_market_ai_summary"));
        assertTrue(layout.contains("AI 针对此池分析"));

        assertEquals(
                buttonBackground(layout, "btn_buy_up"),
                buttonBackground(layout, "btn_buy_down"));
        assertEquals("@drawable/custom_button_background",
                buttonBackground(layout, "btn_buy_up"));
        assertMatchingButtonAttribute(
                layout, "android:textColor", "#FFFFFF");
        assertMatchingButtonAttribute(
                layout, "android:textStyle", "bold");
        assertMatchingButtonAttribute(
                layout, "android:layout_height", "52dp");
        assertMatchingButtonAttribute(
                layout, "android:layout_weight", "1");
        assertMatchingButtonAttribute(
                layout, "android:textAllCaps", "false");
    }

    @Test
    public void detailActivityRequestsOneSummaryAndPassesItToResearchChat()
            throws Exception {
        String source = readUtf8(DETAIL_ACTIVITY_PATH);

        assertTrue(source.contains("private boolean marketAiRequested = false;"));
        assertTrue(source.contains("private String marketAiContext = \"\";"));
        assertTrue(source.contains("private String marketAiSummary = \"\";"));
        assertTrue(source.contains("private String marketAiUnavailableMessage = \"\";"));
        assertEquals(
                "Only field initialization may set the one-shot flag false",
                1,
                countOccurrences(source, "marketAiRequested = false"));
        assertEquals(
                "The one-shot request flag must only be claimed once",
                1,
                countOccurrences(source, "marketAiRequested = true"));

        String onCreate = blockAfter(source, "protected void onCreate");
        assertInOrder(onCreate, "DeepSeekClient.init(this)", "initViews()");

        String loadMarketData = blockAfter(source, "private void loadMarketData()");
        assertFalse(
                "Refresh/load paths must not reset the one-shot AI flag",
                loadMarketData.contains("marketAiRequested = false"));

        String loadSuccess = blockAfter(
                source,
                "public void onSuccess(GoldMarketRepository.GameModel model)");
        assertInOrder(loadSuccess,
                "currentGame = model",
                "updateUI()",
                "requestMarketAiSummaryOnce()");

        String request = blockAfter(
                source, "private void requestMarketAiSummaryOnce()");
        assertInOrder(request,
                "if (marketAiRequested || currentGame == null) return",
                "marketAiRequested = true",
                "DeepSeekClient.isConfigured()");
        assertTrue(
                "The one-shot flag must be claimed before quote/network work",
                request.indexOf("marketAiRequested = true")
                        < request.indexOf("GoldAdvisoryManager.fetchPrice"));
        assertTrue(request.contains("GoldAdvisoryManager.fetchPrice"));
        assertEquals(
                "Quote success and error must both build market context",
                2,
                countOccurrences(
                        request,
                        "GoldMarketResearchPromptBuilder.buildContext("));
        assertTrue(request.contains(
                "GoldMarketResearchPromptBuilder.buildSummaryPrompt(marketAiContext)"));
        assertTrue(request.contains(
                "AgentManager.getInstance().askGoldResearch"));
        assertTrue(source.contains("AI 分析暂时不可用"));
        assertTrue(source.contains(
                "请先在博弈池列表顶部的总 AI 助手中配置 DeepSeek API Key。"));

        String renderSummary = blockAfter(
                source, "private void showMarketAiSummary(String answer)");
        assertTrue(renderSummary.contains("runOnUiThread"));
        assertTrue(renderSummary.contains("marketAiSummary = answer"));
        assertTrue(renderSummary.contains("marketAiUnavailableMessage = \"\""));

        String initViews = blockAfter(source, "private void initViews()");
        assertTrue(initViews.contains("R.id.card_market_ai"));
        assertTrue(initViews.contains("EXTRA_MARKET_CONTEXT"));
        assertTrue(initViews.contains("EXTRA_INITIAL_AI_SUMMARY"));
        assertTrue(source.contains("专属分析仍在生成，请稍后"));

        String holdingsBlock = blockAfter(
                source, "if (currentGame.myShares != null)");
        String holdingsLoop = blockAfter(
                holdingsBlock,
                "for (int i = 0; i < currentGame.myShares.size(); i++)");
        assertTrue(holdingsLoop.contains("shares.signum() <= 0"));
        assertTrue(holdingsLoop.contains("holdings.append('\\n')"));
        assertTrue(holdingsLoop.contains("holdings.append(optionName)"));
        assertTrue(holdingsLoop.contains("\" 份额\""));
        assertFalse("Holdings loop must not stop after the first side",
                holdingsLoop.contains("break"));
        assertFalse("Holdings loop must not encode one-sided else-if logic",
                holdingsLoop.contains("else if"));
        assertFalse("Holdings loop must not return after the first side",
                holdingsLoop.contains("return"));
    }

    @Test
    public void detailActivityUsesUsefulUnavailableMessageInsteadOfLoadingToast()
            throws Exception {
        String source = readUtf8(DETAIL_ACTIVITY_PATH);

        String initViews = blockAfter(source, "private void initViews()");
        String clickHandler = blockAfter(
                initViews, "cardMarketAi.setOnClickListener");
        assertTrue(clickHandler.contains("marketAiUnavailableMessage"));
        assertTrue(clickHandler.contains("MARKET_AI_LOADING_MESSAGE"));
        assertTrue(source.contains("专属分析仍在生成，请稍后"));
        assertTrue(clickHandler.contains("startActivity(intent)"));

        String request = blockAfter(
                source, "private void requestMarketAiSummaryOnce()");
        assertTrue(request.contains(
                "showMarketAiUnavailable(\"未配置\", MARKET_AI_CONFIG_GUIDANCE)"));
        assertTrue(request.contains(
                "showMarketAiUnavailable(\"暂不可用\", MARKET_AI_FAILURE_MESSAGE)"));
        assertQuoteFetchFailureStillRunsResearch(request);

        String unavailable = blockAfter(
                source,
                "private void showMarketAiUnavailable(String status, String message)");
        assertTrue(unavailable.contains("marketAiUnavailableMessage = message"));
        assertTrue(unavailable.contains("tvMarketAiSummary.setText(message)"));
        assertTrue(source.contains(
                "private static final String MARKET_AI_CONFIG_GUIDANCE"));
        assertTrue(source.contains(
                "private static final String MARKET_AI_FAILURE_MESSAGE"));
    }

    private static void assertQuoteFetchFailureStillRunsResearch(
            String requestSource) {
        String quoteFetchCallback = blockAfter(
                requestSource, "GoldAdvisoryManager.fetchPrice");
        String quoteError = blockAfter(
                quoteFetchCallback, "public void onError(String error)");
        assertInOrder(
                quoteError,
                "marketAiContext = GoldMarketResearchPromptBuilder.buildContext",
                "gameForResearch",
                "System.currentTimeMillis()",
                "null",
                "askResearch.run()");
        assertFalse(
                "Quote errors should still ask AI with null-quote context; "
                        + "only AI request errors mark the card unavailable",
                quoteError.contains("showMarketAiUnavailable"));
    }

    private static String readUtf8(String path) throws Exception {
        return new String(
                Files.readAllBytes(repoPath(path)),
                StandardCharsets.UTF_8);
    }

    private static String openingTag(
            String source, String elementName, String id) {
        int idIndex = source.indexOf("@+id/" + id);
        assertTrue("Missing XML id: " + id, idIndex >= 0);
        int tagStart = source.lastIndexOf("<" + elementName, idIndex);
        int tagEnd = source.indexOf('>', idIndex);
        assertTrue("Missing <" + elementName + "> for id: " + id,
                tagStart >= 0 && tagEnd > idIndex);
        return source.substring(tagStart, tagEnd + 1);
    }

    private static String buttonBackground(String layout, String buttonId) {
        return buttonAttribute(layout, buttonId, "android:background");
    }

    private static void assertMatchingButtonAttribute(
            String layout, String attributeName, String expectedValue) {
        assertEquals(expectedValue,
                buttonAttribute(layout, "btn_buy_up", attributeName));
        assertEquals(
                "Button attribute must match between YES and NO: "
                        + attributeName,
                buttonAttribute(layout, "btn_buy_up", attributeName),
                buttonAttribute(layout, "btn_buy_down", attributeName));
    }

    private static String buttonAttribute(
            String layout, String buttonId, String attributeName) {
        String buttonTag = openingTag(layout, "Button", buttonId);
        Pattern background = Pattern.compile(
                Pattern.quote(attributeName) + "\\s*=\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = background.matcher(buttonTag);
        assertTrue("Missing " + attributeName + " for: " + buttonId,
                matcher.find());
        return matcher.group(1);
    }

    private static int countOccurrences(String source, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
    }

    private static String blockAfter(String source, String marker) {
        int markerIndex = source.indexOf(marker);
        assertTrue("Missing source marker: " + marker, markerIndex >= 0);
        int openingBrace = source.indexOf('{', markerIndex + marker.length());
        assertTrue("Missing block after: " + marker, openingBrace >= 0);

        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openingBrace + 1, index);
                }
            }
        }
        throw new AssertionError("Unclosed block after: " + marker);
    }

    private static void assertInOrder(String source, String... expectedParts) {
        int previousIndex = -1;
        for (String expected : expectedParts) {
            int index = source.indexOf(expected, previousIndex + 1);
            assertTrue("Expected source part in order: " + expected, index >= 0);
            previousIndex = index;
        }
    }

    private static Path repoPath(String path) {
        Path fromRoot = Paths.get(path);
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        return Paths.get("..").resolve(path).normalize();
    }
}
