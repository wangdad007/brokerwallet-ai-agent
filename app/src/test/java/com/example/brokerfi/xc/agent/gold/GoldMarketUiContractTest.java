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
    private static final String POSITION_DETAIL_ACTIVITY_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                    + "GoldPositionDetailActivity.java";
    private static final String POSITION_DETAIL_LAYOUT_PATH =
            "app/src/main/res/layout/activity_gold_position_detail.xml";
    private static final String AI_CHAT_FRAGMENT_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/AIChatFragment.java";
    private static final String AI_MANAGED_SETTINGS_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                    + "GoldAiManagedSettingsActivity.java";
    private static final String AI_MANAGED_SETTINGS_LAYOUT_PATH =
            "app/src/main/res/layout/activity_gold_ai_managed_settings.xml";

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
    public void globalResearchChatRefreshesQuoteAndPoolSnapshotBeforeEveryQuestion()
            throws Exception {
        String source = readUtf8(AI_CHAT_FRAGMENT_PATH);
        String refresh = blockAfter(source, "private void loadLiveContextAndHandle");
        assertTrue(refresh.contains("GoldAdvisoryManager.fetchPrice"));
        String pools = blockAfter(source, "private void loadMarketsAndHandle");
        assertTrue(pools.contains("marketRepository.getAllGamesInfo"));
        assertTrue(pools.contains("GoldMarketResearchPromptBuilder.buildMarketOverview"));
        assertTrue(pools.contains("handleIntentWithSnapshot"));
        String submit = blockAfter(source, "private void submitQuestion");
        assertInOrder(submit, "beginLoading", "loadLiveContextAndHandle");
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
    public void detailActivityBuildsStructuredResearchAndKeepsDetailsCollapsible()
            throws Exception {
        String source = readUtf8(DETAIL_ACTIVITY_PATH);
        String viewModel = readUtf8(DETAIL_VIEW_MODEL_PATH);

        assertTrue(source.contains("private boolean requestInFlight = false;"));
        assertTrue(source.contains("private String marketAiSummary = \"\";"));
        assertTrue(source.contains("private String marketAiUnavailableMessage = \"\";"));
        assertTrue(source.contains("private boolean marketAiHasResult = false;"));

        String onCreate = blockAfter(source, "protected void onCreate");
        assertInOrder(onCreate, "DeepSeekClient.init(this)", "ViewModelProvider",
                "initViews()", "observeViewModel()", "viewModel.loadGameInfo");
        assertTrue(source.contains(
                "viewModel.getMarketAiSummary().observe(this, this::showMarketAiSummary)"));

        String toggle = blockAfter(source, "private void toggleAiDetails()");
        assertInOrder(toggle,
                "if (!marketAiHasResult)", "requestMarketAiAnalysis()", "return");
        assertTrue(toggle.contains("layoutAiDetails.setVisibility"));

        String requestCard = blockAfter(source, "private void requestMarketAiAnalysis()");
        assertInOrder(requestCard, "requestInFlight = true", "progressMarketAi.setVisibility",
                "AI 正在核对行情、份额分布、判定规则与时间风险", "viewModel.startAiAnalysis()");

        String request = blockAfter(viewModel, "public void startAiAnalysis()");
        assertInOrder(request, "currentGame.getValue()", "GoldAdvisoryManager.fetchPrice");
        assertEquals("Quote success and error must both build market context", 2,
                countOccurrences(request, "GoldMarketResearchPromptBuilder.buildContext("));
        assertEquals(2, countOccurrences(request, "fetchAiSummary()"));

        String fetchSummary = blockAfter(viewModel, "private void fetchAiSummary()");
        assertTrue(fetchSummary.contains("DeepSeekClient.chatForParsing"));
        assertTrue(fetchSummary.contains(
                "GoldMarketResearchAnalysisPresenter.systemPrompt()"));
        assertTrue(fetchSummary.contains(
                "GoldMarketResearchAnalysisPresenter.buildPrompt(marketAiContext)"));

        String renderSummary = blockAfter(source, "private void showMarketAiSummary(String answer)");
        assertTrue(renderSummary.contains("requestInFlight = false"));
        assertTrue(renderSummary.contains("if (destroyed) return"));
        assertTrue(renderSummary.contains("marketAiSummary = answer"));
        assertTrue(renderSummary.contains("GoldMarketResearchAnalysisPresenter.parse(answer)"));
        assertTrue(renderSummary.contains("layoutMarketAiChips.setVisibility(View.VISIBLE)"));
    }

    @Test
    public void detailChartKeepsMarketLinesContinuousAndSeparatesPersonalTradeMarkers()
            throws Exception {
        String source = readUtf8(DETAIL_ACTIVITY_PATH);
        String viewModel = readUtf8(DETAIL_VIEW_MODEL_PATH);
        String layout = readUtf8(DETAIL_LAYOUT_PATH);
        String tradeRenderer = readUtf8(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                        + "GoldMarketTradeRenderer.java");

        assertTrue(source.contains("setYes.setMode(LineDataSet.Mode.LINEAR)"));
        assertTrue(source.contains("setNo.setMode(LineDataSet.Mode.LINEAR)"));
        assertTrue(source.contains("setYes.setDrawCircles(false)"));
        assertTrue(source.contains("setNo.setDrawCircles(false)"));
        assertTrue(source.contains("manualYes"));
        assertTrue(source.contains("manualNo"));
        assertTrue(source.contains("aiYes"));
        assertTrue(source.contains("aiNo"));
        assertTrue(source.contains("GoldMarketChartPresenter.aggregateTrades("));
        assertTrue(source.contains("GoldMarketTradeRenderer"));
        assertTrue(source.contains("lineChart.setExtraOffsets(2, 54, 4, 2)"));
        assertTrue(layout.contains("android:layout_height=\"250dp\""));
        assertTrue(tradeRenderer.contains("layoutMarkers(markers)"));
        assertTrue(tradeRenderer.contains("DS ×"));
        assertTrue(tradeRenderer.contains("drawConnector(canvas, marker)"));
        assertTrue(layout.contains("@+id/tv_trade_bucket_hint"));
        assertTrue(layout.contains("您的购买标记"));
        assertTrue(layout.contains("DeepSeek 托管"));
        assertTrue(layout.contains("@drawable/bg_chart_manual_marker"));
        assertTrue(tradeRenderer.contains("? \"M ×\" + trade.purchaseCount : \"M\""));
        assertFalse(layout.contains("实心点 = 手动购买"));
        assertTrue(viewModel.contains("BackendApiClient.fetchTradeHistory(gameId, wallet)"));
        assertFalse(layout.contains("@+id/chart_range_30m"));
        assertTrue(layout.contains("@+id/chart_range_1h"));
        assertTrue(layout.contains("@+id/chart_range_1d"));
        assertTrue(layout.contains("@+id/chart_range_1w"));
        assertFalse(layout.contains("@+id/tv_chart_window"));
        assertFalse(source.contains("Last 1 hour · minute-level share movement"));
        assertTrue(source.contains("xAxis.setAxisMinimum"));
        assertTrue(source.contains("xAxis.setGranularity(granularityMinutes)"));
    }

    @Test
    public void researchAssistantDoesNotExposeModelConfidenceInTheCard() throws Exception {
        String source = readUtf8(AI_CHAT_FRAGMENT_PATH);
        String layout = readUtf8("app/src/main/res/layout/fragment_ai_chat.xml");

        assertFalse(layout.contains("@+id/tv_ai_confidence"));
        assertFalse(source.contains("tvAiConfidence"));
        assertFalse(source.contains("AI confidence "));
    }

    @Test
    public void aiAutoManagementHasEditablePersistedRiskGuardrails() throws Exception {
        String source = readUtf8(AI_MANAGED_SETTINGS_PATH);
        String layout = readUtf8(AI_MANAGED_SETTINGS_LAYOUT_PATH);

        for (String id : new String[]{"et_order_amount", "et_confidence", "et_edge",
                "et_kelly", "switch_adaptive_cooldown", "btn_save_strategy"}) {
            assertTrue("missing AI strategy control " + id, layout.contains("@+id/" + id));
        }
        assertTrue(source.contains("config.buyAmountBKC"));
        assertTrue(source.contains("config.confidenceMin"));
        assertTrue(source.contains("config.minEdgePercent"));
        assertTrue(source.contains("config.kellyFraction"));
        assertTrue(source.contains("config.adaptiveCooldown"));
        assertTrue(source.contains("repository.configureAiManaged(gameId, true, config"));
    }

    @Test
    public void detailActivityUsesUsefulUnavailableMessageInsteadOfLoadingToast()
            throws Exception {
        String source = readUtf8(DETAIL_ACTIVITY_PATH);

        String toggle = blockAfter(source, "private void requestMarketAiAnalysis()");
        assertInOrder(toggle, "progressMarketAi.setVisibility(View.VISIBLE)",
                "tvMarketAiSummary.setText(\"AI 正在核对行情、份额分布、判定规则与时间风险…\")",
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
    public void positionInsightKeepsSummaryVisibleAndCollapsesDetailedSignals() throws Exception {
        String source = readUtf8(POSITION_DETAIL_ACTIVITY_PATH);
        String layout = readUtf8(POSITION_DETAIL_LAYOUT_PATH);

        assertTrue(layout.contains("@+id/layout_position_ai_details"));
        assertTrue(layout.contains("@+id/card_position_ai"));
        assertTrue(openingTag(layout, "LinearLayout", "card_position_ai")
                .contains("android:animateLayoutChanges=\"true\""));
        assertInOrder(source, "tvPositionAiStatus.setOnClickListener",
                "togglePositionAnalysisDetails", "cardPositionAi.setOnClickListener");

        String toggle = blockAfter(source, "private void togglePositionAnalysisDetails()");
        assertInOrder(toggle, "if (!positionAnalysisHasResult)",
                "requestPositionAnalysis()", "layoutPositionAiDetails.setVisibility");
        String render = blockAfter(source, "private void showPositionAnalysis(");
        assertTrue(render.contains("positionAnalysisExpanded = true"));
        assertTrue(render.contains("layoutPositionAiDetails.setVisibility(View.VISIBLE)"));
    }

    @Test
    public void positionInsightStartsOnlyAfterExplicitUserAction() throws Exception {
        String source = readUtf8(POSITION_DETAIL_ACTIVITY_PATH);
        String layout = readUtf8(POSITION_DETAIL_LAYOUT_PATH);

        assertFalse(source.contains("maybeStartPositionAnalysis()"));
        assertFalse(source.contains("positionAnalysisAutoRequested"));
        assertTrue(source.contains("private void showPositionAnalysisReady()"));
        assertTrue(source.contains("tvPositionAiStatus.setText(\"开始分析 ›\")"));
        assertTrue(source.contains("if (currentGame == null || !tradeHistoryLoadedOnce)"));
        assertTrue(source.contains("showPositionAnalysisPreparing()"));
        assertTrue(source.contains("tvPositionAiPlaceholder.setOnClickListener(v -> requestPositionAnalysis())"));
        assertTrue(source.contains("btnPositionAiRefresh.setOnClickListener(v -> requestPositionAnalysis())"));
        assertTrue(layout.contains("点击分析，生成个性化持仓与风险报告"));

        String ready = blockAfter(source, "private void showPositionAnalysisReady()");
        assertFalse(ready.contains("DeepSeekClient.chatForParsing"));
        String request = blockAfter(source, "private void requestPositionAnalysis()");
        assertTrue(request.contains("DeepSeekClient.chatForParsing"));
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

        assertTrue(cardLayout.contains("持有 YES：0.00 份额"));
        assertTrue(cardLayout.contains("持有 NO：0.00 份额"));
        String titleTag = openingTag(cardLayout, "TextView", "tv_position_title");
        assertTrue(titleTag.contains("app:layout_constraintEnd_toEndOf=\"parent\""));
        assertTrue(titleTag.contains("android:maxLines=\"2\""));
        assertFalse(titleTag.contains("tv_position_side"));
        assertTrue(cardLayout.contains("android:text=\"持仓方向\""));
        assertTrue(cardLayout.indexOf("@+id/tv_position_side")
                > cardLayout.indexOf("@+id/ll_data_row"));
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

    @Test
    public void chineseProductCopyUsesConsistentNaturalTerminology() throws Exception {
        String copy = readUtf8(AI_MANAGED_SETTINGS_LAYOUT_PATH)
                + readUtf8(DETAIL_LAYOUT_PATH)
                + readUtf8(POSITION_DETAIL_LAYOUT_PATH)
                + readUtf8("app/src/main/res/layout/dialog_gold_pool_summary.xml")
                + readUtf8("app/src/main/res/layout/fragment_gold_market_list.xml")
                + readUtf8(DETAIL_ACTIVITY_PATH)
                + readUtf8(POSITION_DETAIL_ACTIVITY_PATH)
                + readUtf8(DETAIL_VIEW_MODEL_PATH);

        for (String retiredCopy : new String[]{
                "AI Auto-Management", "AI auto-management", "AI Managed",
                "DeepSeek managed", "Awaiting Resolution", "Claim Sent",
                "Claim Success", "Start–End Dates", "On-chain Markets",
                "Current market", "Collapse ˄", "View details ˅"
        }) {
            assertFalse("Retired English UI copy returned: " + retiredCopy,
                    copy.contains(retiredCopy));
        }
        assertTrue(copy.contains("AI 自动托管"));
        assertTrue(copy.contains("DeepSeek 托管"));
        assertTrue(copy.contains("开始与截止日期"));
        assertTrue(copy.contains("链上博弈市场"));
        assertTrue(copy.contains("收益领取交易已提交"));
        assertTrue(copy.contains("收益领取成功"));

        for (String viewPath : new String[]{
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/GoldMarketListFragment.java",
                POSITIONS_FRAGMENT_PATH,
                DETAIL_ACTIVITY_PATH
        }) {
            assertFalse("Internal timing diagnostics must not appear in production UI",
                    readUtf8(viewPath).contains("getDebugToast().observe"));
        }
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
