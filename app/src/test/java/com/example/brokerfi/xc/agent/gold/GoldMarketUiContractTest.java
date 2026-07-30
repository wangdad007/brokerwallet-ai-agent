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
    private static final String TRADE_DIALOG_LAYOUT_PATH =
            "app/src/main/res/layout/dialog_gold_trade.xml";
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
    private static final String AI_DECISION_CENTER_LAYOUT_PATH =
            "app/src/main/res/layout/activity_ai_decision_center.xml";
    private static final String AI_DECISION_CENTER_ACTIVITY_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                    + "AiDecisionCenterActivity.java";
    private static final String MARKET_REPOSITORY_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/model/data/"
                    + "GoldMarketRepository.java";
    private static final String CREATE_CUSTOM_ACTIVITY_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                    + "GoldCreateCustomActivity.java";

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
    public void missingHistoryUsesOnlyVerifiableSnapshotAndLiquidityCannotDefaultToOne()
            throws Exception {
        String repository = readUtf8(MARKET_REPOSITORY_PATH);
        String fallback = blockAfter(repository,
                "private List<HistoryPoint> generateCurrentSnapshotHistory");
        assertFalse(fallback.contains("Math.random"));
        assertFalse(repository.contains("generateMockHistory"));
        assertTrue(fallback.contains("list.add(point)"));

        String create = readUtf8(CREATE_CUSTOM_ACTIVITY_PATH);
        assertFalse(create.contains("if (liquidity.isEmpty()) liquidity = \"1\""));
        assertTrue(create.contains("池深越低，单笔交易的价格影响越大"));
    }

    @Test
    public void detailLayoutPlacesAiCardBeforeInlineTradePanel()
            throws Exception {
        String layout = readUtf8(DETAIL_LAYOUT_PATH);
        String source = readUtf8(DETAIL_ACTIVITY_PATH);

        int marketPanelEnd = layout.indexOf("@+id/tv_countdown");
        int aiCard = layout.indexOf("@+id/card_market_ai");
        int tradePanel = layout.indexOf("@+id/inline_trade_panel");
        assertTrue("AI card must follow the AMM market panel",
                marketPanelEnd >= 0 && aiCard > marketPanelEnd);
        assertTrue("AI card must precede the inline trading panel", tradePanel > aiCard);

        String cardTag = openingTag(layout, "LinearLayout", "card_market_ai");
        assertTrue(cardTag.contains("android:clickable=\"true\""));
        assertTrue(cardTag.contains("android:focusable=\"true\""));
        assertTrue(layout.contains("@+id/tv_market_ai_status"));
        assertTrue(layout.contains("@+id/tv_market_ai_summary"));
        assertTrue(layout.contains("AI 投研"));

        assertTrue(layout.contains("@+id/btn_market_rule_toggle"));
        String heroTitle = openingTag(layout, "TextView", "tv_market_desc");
        assertTrue(heroTitle.contains("android:maxLines=\"1\""));
        assertTrue(source.contains(
                "GoldMarketTitleFitter.apply(tvMarketDesc, GoldMarketTextStyler.style("));
        String ruleDetails = openingTag(
                layout, "LinearLayout", "layout_market_rule_details");
        assertTrue(ruleDetails.contains("android:visibility=\"gone\""));
        assertFalse(layout.contains("android:text=\"博弈池概览\""));
        assertTrue(layout.contains("android:text=\"实时概率\""));
        assertTrue(sourceContains(DETAIL_ACTIVITY_PATH, "private void toggleMarketRules()"));

        assertTrue(layout.contains("layout=\"@layout/dialog_gold_trade\""));
        assertFalse(layout.contains("@+id/btn_buy_up"));
        assertFalse(layout.contains("@+id/btn_buy_down"));
    }

    @Test
    public void decisionCenterUsesTheSharedLightCardDesignSystem() throws Exception {
        String layout = readUtf8(AI_DECISION_CENTER_LAYOUT_PATH);
        String source = readUtf8(AI_DECISION_CENTER_ACTIVITY_PATH);

        assertTrue(layout.contains("android:background=\"#F5F7FA\""));
        assertTrue(layout.contains("android:background=\"#FFFFFF\""));
        assertTrue(layout.contains("android:src=\"@drawable/up_circle\""));
        assertTrue(layout.contains("android:background=\"@drawable/bg_gold_detail_panel\""));
        assertTrue(layout.contains("android:background=\"@drawable/bg_trade_primary\""));
        assertTrue(layout.contains("android:background=\"@drawable/bg_ai_center_filter\""));
        assertFalse("The old dark dashboard must not return",
                layout.contains("android:background=\"@drawable/bg_ai_center_hero\""));
        assertTrue(layout.contains("@+id/tv_runtime_status"));
        assertTrue(source.contains(
                "updateConnectionStatus(\"已更新\", \"运行中\", AiDecisionUi.YES"));
        assertTrue(source.contains(
                "updateConnectionStatus(\"连接中断\", \"待连接\", AiDecisionUi.NO"));
    }

    @Test
    public void unifiedTradeTicketExposesBothSidesAndOrderTypes() throws Exception {
        String layout = readUtf8(TRADE_DIALOG_LAYOUT_PATH);
        for (String requiredId : new String[] {
                "@+id/tab_trade_buy",
                "@+id/tab_trade_sell",
                "@+id/tab_trade_stake",
                "@+id/tab_trade_unstake",
                "@+id/indicator_trade_buy",
                "@+id/indicator_trade_sell",
                "@+id/indicator_trade_stake",
                "@+id/indicator_trade_unstake",
                "@+id/tab_order_type",
                "@+id/btn_trade_yes",
                "@+id/btn_trade_no",
                "@+id/layout_trade_limit",
                "@+id/et_trade_amount",
                "@+id/tv_trade_receive",
                "@+id/btn_trade_confirm",
        }) {
            assertTrue("Unified trade ticket lacks " + requiredId, layout.contains(requiredId));
        }
        assertTrue(layout.contains("android:text=\"市价 ▾\""));
        assertTrue(layout.contains("android:text=\"限价\""));
        assertTrue(layout.contains("android:background=\"@drawable/bg_gold_detail_panel\""));
        assertFalse(layout.contains("@+id/tab_order_market"));
        assertFalse(layout.contains("@+id/tab_order_limit"));
        String source = readUtf8(
                "app/src/main/java/com/example/brokerfi/xc/agent/gold/view/GoldTradeDialog.java");
        assertTrue(source.contains("Side { BUY, SELL, STAKE, UNSTAKE }"));
        assertTrue(source.contains("GoldLiquiditySimulation.simulateAdd("));
        assertTrue(source.contains("GoldLiquiditySimulation.simulateRemove("));
        assertTrue(source.contains("LP 按份额获得 1% 交易费"));
        assertTrue(source.contains(
                "bindQuick(quick4, \"全部\", () -> setHoldingFraction(100))"));
        assertTrue(source.contains(
                "bindQuick(quick4, \"全部\", () -> setLiquidityFraction(100))"));
        assertTrue(source.contains("R.drawable.bg_trade_quick_selected"));
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
        assertTrue(source.contains("lineChart.setExtraOffsets(8, 44, 10, 8)"));
        assertTrue(layout.contains("android:layout_height=\"224dp\""));
        assertTrue(layout.contains("android:background=\"@drawable/bg_gold_chart_surface\""));
        assertTrue(tradeRenderer.contains("layoutMarkers(markers)"));
        assertTrue(tradeRenderer.contains("executionSourceMarker"));
        assertTrue(tradeRenderer.contains("drawConnector(canvas, marker)"));
        assertTrue(layout.contains("@+id/tv_trade_bucket_hint"));
        assertFalse(layout.contains("曲线包含博弈池全部交易"));
        assertFalse(layout.contains("您的购买标记"));
        assertFalse(layout.contains("每个标记代表"));
        assertFalse(layout.contains("市场定价与您的成交记录"));
        assertFalse(layout.contains("android:text=\"成交标记\""));
        assertTrue(layout.contains("android:text=\"手动\""));
        assertTrue(layout.contains("android:text=\"AI 托管\""));
        assertTrue(layout.contains("android:text=\"网格\""));
        assertTrue(layout.contains("android:text=\"马丁\""));
        assertTrue(layout.contains("@drawable/bg_chart_legend_chip"));
        assertTrue(layout.contains("@drawable/bg_chart_manual_marker"));
        assertTrue(layout.contains("@drawable/bg_chart_deepseek_marker"));
        assertTrue(tradeRenderer.contains("markerLabel + \" ×\" + trade.purchaseCount"));
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
        assertFalse(layout.contains("@drawable/bg_ai_center_cta"));
        assertFalse(layout.contains("android:background=\"#EEF2F6\""));
        assertTrue(layout.contains("android:text=\"策略与市场机会  ›\""));
        assertTrue(layout.contains("@drawable/bg_ai_center_entry"));
        assertTrue(source.contains("chip.setElevation(0f)"));
        assertTrue(source.contains("background.setColor(0xFFF8FAFC)"));
        assertFalse(source.contains("background.setStroke(dp(1), 0xFFCBD5E1)"));
    }

    @Test
    public void managedStrategyQuickActionCollectsMarketAndRiskParametersBeforeDrafting()
            throws Exception {
        String source = readUtf8(AI_CHAT_FRAGMENT_PATH);
        String marketList = readUtf8("app/src/main/res/layout/fragment_gold_market_list.xml");
        String positions = readUtf8("app/src/main/res/layout/fragment_gold_my_positions.xml");

        assertTrue(source.contains(
                "addQuickAction(\"托管策略\", \"我想配置 AI 自动托管策略\")"));
        assertTrue(source.contains(
                "addQuickAction(\"方向研判\", \"我想研判所有博弈池的 YES/NO 交易方向\")"));
        assertTrue(source.contains(
                "addQuickAction(\"创建市场\", this::beginCreateRequirementInput)"));
        assertFalse(source.contains("创建一个未来两天黄金是否上涨的博弈池"));
        assertTrue(source.contains("private boolean pendingCreateRequirements"));
        assertTrue(source.contains("描述你想判断的问题"));
        assertTrue(source.contains("描述判断条件、截止时间和初始流动性"));
        assertTrue(source.contains("hasCreateRuleDescription(text)"));
        assertTrue(source.contains("GoldCreatePoolFragment.parseAndValidateAiResponse(response)"));
        assertTrue(source.contains("addCreatePreviewCard("));
        assertTrue(source.contains("判断规则"));
        assertTrue(source.contains("初始流动性"));
        assertTrue(source.contains("结算数据"));
        assertTrue(source.contains("进入\" + template.title + \"参数定制  →"));
        assertTrue(source.contains("new Intent(requireContext(), GoldCreateCustomActivity.class)"));
        assertTrue(source.contains("page.putExtra(\"AI_PARSED_DATA\", draft.toString())"));
        assertTrue(source.contains("addResearchResultCard(taskName, sanitizeResearchAnswer(answer))"));
        assertTrue(source.contains("禁止输出博弈池ID、内部编号或#数字"));
        assertTrue(source.contains("必须逐字使用快照中的完整博弈池标题"));
        assertTrue(source.contains("不要显示博弈池 ID、#数字或内部编号"));
        assertTrue(source.contains("researchCardTitle(taskName)"));
        assertTrue(source.contains("askMarketOverviewWithCurrentContext(question, loadingIndex)"));
        assertTrue(source.contains("只返回一个 JSON 对象"));
        assertTrue(source.contains("addMarketOverviewCard(parseMarketOverview(answer))"));
        assertTrue(source.contains("addQuoteMetricPanel(card, payload)"));
        assertTrue(source.contains("metrics.setBackground(panelBackground(0xFFF8FAFC, 0xFFDCE5EF))"));
        assertTrue(source.contains("panel.addView(textView(\"行情判断\""));
        assertTrue(source.contains("TextView row = textView(\"•  \" + risk, 12, 0xFF64748B"));
        assertTrue(source.contains("addOverviewMarketComponents(card, payload)"));
        assertTrue(source.contains("addOverviewMarketCard(parent, game)"));
        assertTrue(source.contains("GoldMarketTextStyler.style(displayGameTitle(game), true)"));
        assertTrue(source.contains("market.setOnClickListener(v -> openMarket(game))"));
        assertFalse(source.contains("overviewProbabilities(game)"));
        assertTrue(source.contains("market.setOrientation(LinearLayout.HORIZONTAL)"));
        assertTrue(source.contains("LinearLayout.LayoutParams.MATCH_PARENT, dp(48)"));
        assertTrue(source.contains("addQuickAction(\"博弈对比\", this::beginCompareMarketSelection)"));
        assertTrue(source.contains("addQuickAction(\"持仓诊断\", this::beginDiagnosisSelection)"));
        assertTrue(source.contains("选择第一个市场"));
        assertTrue(source.contains("选择对比市场"));
        assertFalse(source.contains("你想重点比较什么"));
        assertFalse(source.contains("你想重点诊断什么"));
        assertTrue(source.contains("选择要诊断的博弈池"));
        assertTrue(source.contains("可选择任意一个活跃市场"));
        assertTrue(source.contains("startMarketComparison(first, game)"));
        assertTrue(source.contains("必须覆盖市场隐含概率、流动性、"));
        assertTrue(source.contains("startPositionDiagnosis(game)"));
        assertTrue(source.contains("必须覆盖当前 YES/NO 份额、方向暴露、集中度、市场流动性、"));
        assertFalse(source.contains("全部持仓  ›"));
        assertFalse(source.contains("!hasPositiveHolding(game)"));
        assertTrue(source.contains("askPositionDiagnosisWithCurrentContext("));
        assertTrue(source.contains("addPositionDiagnosisCard(game, parsePositionDiagnosis(answer, game))"));
        assertTrue(source.contains("addPositionSnapshotPanel(card, game)"));
        assertTrue(source.contains("YES 持有"));
        assertTrue(source.contains("NO 持有"));
        assertTrue(source.contains("YES 市场概率"));
        assertTrue(source.contains("市场流动性"));
        assertTrue(source.contains("AI 诊断结论"));
        assertTrue(source.contains("诊断要点"));
        assertTrue(source.contains("GoldPositionDetailActivity.class"));
        assertTrue(source.contains("系统已核验链上持仓"));
        assertTrue(source.contains("这是确定性事实，不得声称未持有"));
        assertTrue(source.contains("contradictsVerifiedHolding(payload.summary)"));
        assertTrue(source.contains("payload.findings.remove(index)"));
        assertTrue(source.contains("verifiedHoldingSummary(game)"));
        assertTrue(source.contains("new CardAction(compactMarketTitle(game, 22) + \"  ›\""));
        assertFalse(source.contains(
                "addQuickAction(\"博弈对比\", \"请比较当前活跃博弈池"));
        assertFalse(source.contains(
                "addQuickAction(\"持仓诊断\", \"请诊断我的当前持仓"));
        assertTrue(source.contains("private GoldAdvisoryManager.Advisory latestSpotAdvisory"));
        assertTrue(source.contains("payload.stance = unifiedSpotStance()"));
        assertTrue(source.contains("latestSpotAdvisory.summary"));
        assertTrue(source.contains("latestSpotAdvisory = advisory"));
        assertTrue(source.contains("现货方向必须使用统一标签"));
        assertTrue(source.contains("if (\"BUY\".equalsIgnoreCase(signal)) return \"偏多\""));
        assertTrue(source.contains("if (\"SELL\".equalsIgnoreCase(signal)) return \"偏空\""));
        assertFalse(source.contains("showWelcomeMessage()"));
        assertFalse(source.contains("你好，我是黄金市场智能体"));
        assertFalse(source.contains("insight.view"));
        assertFalse(source.contains(
                "addQuickAction(\"托管策略\", \"为 1 号池"));
        assertTrue(source.contains("private GoldMarketRepository.GameModel pendingStrategyGame"));
        assertTrue(source.contains("strategyMarketButtons()"));
        assertTrue(source.contains("beginStrategyParameterInput(game)"));
        assertTrue(source.contains("完善策略参数"));
        assertTrue(source.contains("例如：保守，每单 0.5 BKC"));
        assertTrue(source.contains("showStrategyDraftForGame("));
        assertTrue(source.contains("findGameReference(text, intent.gameId)"));
        assertTrue(source.contains("tradeMarketButtons()"));
        assertTrue(source.contains("选择评估方向与参考金额"));
        assertFalse(source.contains("addQuickAction(\"交易模拟\""));
        assertTrue(source.contains("case DIRECTION_JUDGMENT:"));
        assertTrue(source.contains("askDirectionJudgmentWithCurrentContext(question, loadingIndex)"));
        assertTrue(source.contains("addDirectionJudgmentCard(parseDirectionJudgment(answer))"));
        assertFalse(source.contains("逐市场研判"));
        assertFalse(source.contains("directionPill(insight.direction)"));
        assertFalse(source.contains("probabilityBar"));
        assertFalse(source.contains("查看市场  ›"));
        assertTrue(source.contains("directionLabel(insight.direction)"));
        assertTrue(source.contains("\"YES %.1f%%\""));
        assertTrue(source.contains("\"风险：\" + insight.risk"));
        assertTrue(source.contains("finishLoadingAndRemove(loadingIndex)"));
        assertTrue(source.contains("new CardAction(label, v -> beginStrategyParameterInput(game), true)"));
        assertTrue(source.contains("panelBackground(0xFFF8FAFC, 0xFFDCE5EF)"));
        assertTrue(source.contains("panelBackground(0xFF2563EB, 0xFF2563EB)"));
        assertFalse(source.contains("查看 #"));
        assertFalse(source.contains("\"博弈池 #\" + game.id"));
        assertFalse(source.contains("已定位 #"));
        assertFalse(source.contains("已完成 #"));
        assertFalse(source.contains("1 号池"));
        assertFalse(marketList.contains("博弈池编号"));
        assertFalse(positions.contains("博弈池编号"));
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
        assertTrue(source.contains("tvPositionAiStatus.setText(\"分析 ›\")"));
        assertTrue(source.contains("if (currentGame == null || !tradeHistoryLoadedOnce)"));
        assertTrue(source.contains("showPositionAnalysisPreparing()"));
        assertTrue(source.contains("tvPositionAiPlaceholder.setOnClickListener(v -> requestPositionAnalysis())"));
        assertTrue(source.contains("btnPositionAiRefresh.setOnClickListener(v -> requestPositionAnalysis())"));
        assertTrue(layout.contains("综合持仓、成本与退出风险"));
        assertTrue(source.contains("GoldPositionValuation.calculateOutcomeMarket(currentGame)"));
        assertTrue(layout.contains("android:text=\"当前可卖估值\""));

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

        assertTrue(cardLayout.contains("持有 YES 0.00 份额&#10;持有 NO 0.00 份额"));
        String titleTag = openingTag(cardLayout, "TextView", "tv_position_title");
        assertTrue(titleTag.contains("app:layout_constraintEnd_toStartOf=\"@id/tv_position_entry\""));
        assertTrue(titleTag.contains("android:maxLines=\"1\""));
        assertTrue(titleTag.contains("android:textSize=\"18sp\""));
        assertFalse(titleTag.contains("tv_position_side"));
        assertTrue(cardLayout.contains("android:text=\"持仓方向\""));
        assertFalse(cardLayout.contains("@+id/divider"));
        assertFalse(cardLayout.contains("@+id/ll_holdings_detail"));
        assertTrue(cardLayout.indexOf("@+id/tv_position_side")
                > cardLayout.indexOf("@+id/ll_data_row"));
        assertTrue(fragment.contains("GoldPositionValuation.calculateOutcomeMarket"));
        assertTrue(fragment.contains("GoldPositionValuation.calculateLiquidityMarket"));
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
                "GoldPositionValuation.calculateLiquidityPortfolio(selected)"));
        assertTrue(updateSummary.contains(
                "GoldPositionValuation.calculateOutcomePortfolio(selected)"));
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

    private static boolean sourceContains(String path, String expected) throws Exception {
        return readUtf8(path).contains(expected);
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
