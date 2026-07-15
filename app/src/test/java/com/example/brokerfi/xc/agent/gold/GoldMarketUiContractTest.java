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
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                    + "GoldMarketDetailActivity.java";
    private static final String DETAIL_VIEW_MODEL_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/viewmodel/"
                    + "GoldMarketDetailViewModel.java";
    private static final String DETAIL_LAYOUT_PATH =
            "app/src/main/res/layout/activity_gold_market_detail.xml";
    private static final String POSITIONS_FRAGMENT_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                    + "GoldMyPositionsFragment.java";
    private static final String POSITION_CARD_LAYOUT_PATH =
            "app/src/main/res/layout/item_gold_position_card.xml";

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
        assertFalse("Pre-generated summaries must never trigger another AI request",
                summaryBranch.contains("submitQuestion("));

        String legacyBranch = blockAfter(source, "else if (!isBlank(initialPrompt))");
        assertInOrder(legacyBranch, "marketContext = initialPrompt",
                "legacyInitialPromptInFlight = true", "submitQuestion(initialPrompt)");

        String bypassBranch = blockAfter(source, "if (legacyInitialPromptInFlight)");
        assertInOrder(bypassBranch, "legacyInitialPromptInFlight = false", "return text");

        assertTrue("Normal follow-ups must delegate context and text to the shared builder",
                Pattern.compile(
                        "return\\s+GoldMarketResearchPromptBuilder\\.withFollowUp\\s*"
                                + "\\(\\s*marketContext\\s*,\\s*text\\s*\\)\\s*;")
                        .matcher(source).find());
        assertTrue("Blank checks must reject whitespace-only values",
                Pattern.compile(
                        "isBlank\\s*\\(\\s*String\\s+value\\s*\\).*?"
                                + "TextUtils\\.isEmpty\\(value\\).*?"
                                + "value\\.trim\\(\\)\\.isEmpty\\(\\)",
                        Pattern.DOTALL).matcher(source).find());
    }

    @Test
    public void detailLayoutPlacesAiCardBeforeTradingAndDistinguishesBuyButtons()
            throws Exception {
        String layout = readUtf8(DETAIL_LAYOUT_PATH);

        int marketPanelEnd = layout.indexOf("@+id/tv_countdown");
        int aiCard = layout.indexOf("@+id/card_market_ai");
        int tradingHeading = layout.indexOf("android:text=\"立即下注\"");
        assertTrue("AI card must follow the AMM market panel",
                marketPanelEnd >= 0 && aiCard > marketPanelEnd);
        assertTrue("AI card must precede the trading section", tradingHeading > aiCard);

        String cardTag = openingTag(layout, "LinearLayout", "card_market_ai");
        assertTrue(cardTag.contains("android:clickable=\"true\""));
        assertTrue(cardTag.contains("android:focusable=\"true\""));
        assertTrue(layout.contains("@+id/tv_market_ai_status"));
        assertTrue(layout.contains("@+id/tv_market_ai_summary"));
        assertTrue(layout.contains("AI 投研分析"));

        assertEquals("@drawable/bg_bet_yes", buttonBackground(layout, "btn_buy_up"));
        assertEquals("@drawable/bg_bet_no", buttonBackground(layout, "btn_buy_down"));
        assertMatchingButtonAttribute(layout, "android:textColor", "#FFFFFF");
        assertMatchingButtonAttribute(layout, "android:textStyle", "bold");
        assertMatchingButtonAttribute(layout, "android:layout_height", "64dp");
        assertMatchingButtonAttribute(layout, "android:layout_weight", "1");
        assertMatchingButtonAttribute(layout, "android:textAllCaps", "false");
    }

    @Test
    public void detailActivityStartsOneAnalysisAndViewModelBuildsResearchContext()
            throws Exception {
        String source = readUtf8(DETAIL_ACTIVITY_PATH);
        String viewModel = readUtf8(DETAIL_VIEW_MODEL_PATH);

        assertTrue(source.contains("private boolean requestInFlight = false;"));
        assertTrue(source.contains("private String marketAiSummary = \"\";"));
        assertTrue(source.contains("private String marketAiUnavailableMessage = \"\";"));

        String onCreate = blockAfter(source, "protected void onCreate");
        assertInOrder(onCreate, "DeepSeekClient.init(this)", "ViewModelProvider",
                "initViews()", "observeViewModel()", "viewModel.loadGameInfo");
        assertTrue(source.contains(
                "viewModel.getMarketAiSummary().observe(this, this::showMarketAiSummary)"));

        String toggle = blockAfter(source, "private void toggleAiDetails()");
        assertInOrder(toggle,
                "marketAiSummary == null || marketAiSummary.isEmpty()",
                "if (!requestInFlight)", "requestInFlight = true",
                "viewModel.startAiAnalysis()", "return");

        String request = blockAfter(viewModel, "public void startAiAnalysis()");
        assertInOrder(request, "currentGame.getValue()", "GoldAdvisoryManager.fetchPrice");
        assertEquals("Quote success and error must both build market context", 2,
                countOccurrences(request, "GoldMarketResearchPromptBuilder.buildContext("));
        assertEquals(2, countOccurrences(request, "fetchAiSummary()"));

        String fetchSummary = blockAfter(viewModel, "private void fetchAiSummary()");
        assertTrue(fetchSummary.contains("AgentManager.getInstance().askGoldResearch"));
        assertTrue(fetchSummary.contains(
                "GoldMarketResearchPromptBuilder.buildSummaryPrompt(marketAiContext)"));

        String renderSummary = blockAfter(source, "private void showMarketAiSummary(String answer)");
        assertTrue(renderSummary.contains("requestInFlight = false"));
        assertTrue(renderSummary.contains("if (destroyed) return"));
        assertTrue(renderSummary.contains("marketAiSummary = answer"));
        assertTrue(renderSummary.contains("markwon.setMarkdown"));
    }

    @Test
    public void detailActivityUsesUsefulUnavailableMessageInsteadOfLoadingToast()
            throws Exception {
        String source = readUtf8(DETAIL_ACTIVITY_PATH);

        String toggle = blockAfter(source, "private void toggleAiDetails()");
        assertInOrder(toggle, "tvMarketAiStatus.setText(\"分析中...\")",
                "tvMarketAiSummary.setText(\"AI 正在解析市场数据，请稍后...\")",
                "viewModel.startAiAnalysis()");
        assertFalse("Starting analysis should update the card instead of showing a loading toast",
                toggle.contains("Toast.makeText"));

        String unavailable = blockAfter(source,
                "private void showMarketAiUnavailable(String status, String message)");
        assertTrue(unavailable.contains("requestInFlight = false"));
        assertTrue(unavailable.contains("if (destroyed) return"));
        assertTrue(unavailable.contains("marketAiUnavailableMessage = message"));
        assertTrue(unavailable.contains("tvMarketAiSummary.setText(message)"));

        String errorObserver = blockAfter(source, "viewModel.getError().observe");
        assertInOrder(errorObserver, "err.startsWith(\"AI error:\")",
                "showMarketAiUnavailable(\"暂不可用\", message)");
    }

    @Test
    public void detailViewModelRejectsOverlappingLoadsAndActivityGuardsAsyncUi()
            throws Exception {
        String source = readUtf8(DETAIL_ACTIVITY_PATH);
        String viewModel = readUtf8(DETAIL_VIEW_MODEL_PATH);

        assertTrue(viewModel.contains("private final AtomicBoolean gameInfoRequestInFlight"));
        String loadMarketData = blockAfter(viewModel,
                "private void loadGameInfo(int gameId, String contractAddress, boolean showLoading)");
        assertInOrder(loadMarketData, "gameInfoRequestInFlight.compareAndSet(false, true)",
                "activeRepository.getGameInfo");
        assertTrue(loadMarketData.contains("finishGameInfoRequest(showLoading)"));
        assertTrue(loadMarketData.contains("currentGame.postValue(model)"));
        assertTrue(loadMarketData.contains("if (showLoading) error.postValue(err)"));

        String finish = blockAfter(viewModel, "private void finishGameInfoRequest");
        assertTrue(finish.contains("gameInfoRequestInFlight.set(false)"));
        assertTrue(finish.contains("isLoading.postValue(false)"));
        assertTrue(blockAfter(source, "private void showMarketAiSummary")
                .contains("if (destroyed) return"));
        assertTrue(blockAfter(source, "private void showMarketAiUnavailable")
                .contains("if (destroyed) return"));
    }

    @Test
    public void positionCardsUseSharesAndAmmValuation() throws Exception {
        String fragment = readUtf8(POSITIONS_FRAGMENT_PATH);
        String cardLayout = readUtf8(POSITION_CARD_LAYOUT_PATH);

        assertTrue(cardLayout.contains("持有 YES: 0.00"));
        assertTrue(cardLayout.contains("持有 NO: 0.00"));
        assertTrue(fragment.contains("GoldPositionValuation.calculateMarket"));
        assertTrue(fragment.contains("GoldPositionValuation.calculatePortfolio"));
        assertTrue(fragment.contains("\" 份额\""));
        assertFalse("Top summary must not add raw shares as BKC",
                fragment.contains("totalInvested"));

        String renderPositions = blockAfter(fragment, "private void renderPositions()");
        assertTrue(renderPositions.contains("shareText.append"));
        assertTrue(renderPositions.contains("heldOptionIndexes.add"));
        assertTrue(renderPositions.contains("marketValue.isComplete()"));
        assertFalse("Position rendering must not stop at first side",
                renderPositions.contains("break"));

        String updateSummary = blockAfter(fragment, "private void updateSummary()");
        assertTrue(updateSummary.contains(
                "GoldPositionValuation.calculatePortfolio(myPositions)"));
        assertTrue(updateSummary.contains("portfolio.getUnavailableMarketCount()"));
        assertTrue(updateSummary.contains("animateBalance(totalBkc.doubleValue())"));
    }

    private static String readUtf8(String path) throws Exception {
        return new String(Files.readAllBytes(repoPath(path)), StandardCharsets.UTF_8);
    }

    private static String openingTag(String source, String elementName, String id) {
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
        assertEquals(expectedValue, buttonAttribute(layout, "btn_buy_up", attributeName));
        assertEquals("Button attribute must match between YES and NO: " + attributeName,
                buttonAttribute(layout, "btn_buy_up", attributeName),
                buttonAttribute(layout, "btn_buy_down", attributeName));
    }

    private static String buttonAttribute(
            String layout, String buttonId, String attributeName) {
        String buttonTag = openingTag(
                layout, "androidx.appcompat.widget.AppCompatButton", buttonId);
        Pattern attribute = Pattern.compile(
                Pattern.quote(attributeName) + "\\s*=\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = attribute.matcher(buttonTag);
        assertTrue("Missing " + attributeName + " for: " + buttonId, matcher.find());
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
        if (Files.exists(fromRoot)) return fromRoot;
        return Paths.get("..").resolve(path).normalize();
    }
}
