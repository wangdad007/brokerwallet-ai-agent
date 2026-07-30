package com.example.brokerfi.xc.agent.gold.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionSet;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.ai.AgentManager;
import com.example.brokerfi.xc.agent.ai.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldAdvisoryManager;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldAgentIntentRouter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketResearchPromptBuilder;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketTemplateCatalog;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldTradeSimulation;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldSellSimulation;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Conversational research workbench for the gold market.
 *
 * <p>It intentionally separates language understanding from privileged actions:
 * the model can explain a market, while a closed local router may only offer
 * simulation, draft creation, or the existing user-confirmed product flows.</p>
 */
public class AIChatFragment extends Fragment {
    private LinearLayout messageContainer;
    private GridLayout quickActionContainer;
    private ScrollView messageScroll;
    private EditText inputField;
    private ImageView sendBtn;
    private ImageView btnConfig;
    private TextView tvAiSignal;
    private TextView tvAiSummary;
    private TextView tvAiCardToggle;
    private View aiAdviceContent;
    private LinearLayout cardAiAdvice;
    private ViewGroup aiChatRoot;
    private Markwon markwon;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;
    private boolean requestInFlight = false;
    private String marketContext = "";
    private GoldMarketRepository marketRepository;
    private List<GoldMarketRepository.GameModel> latestGames = Collections.emptyList();
    private GoldAdvisoryManager.Advisory latestQuote;
    private GoldAdvisoryManager.Advisory latestSpotAdvisory;
    private GoldMarketRepository.GameModel pendingStrategyGame;
    private GoldMarketRepository.GameModel pendingTradeGame;
    private GoldMarketRepository.GameModel pendingCompareFirstGame;
    private GoldMarketRepository.GameModel diagnosisResultGame;
    private boolean pendingCreateRequirements;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_ai_chat, container, false);
        initViews(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        destroyed = false;
        requestInFlight = false;
        DeepSeekClient.init(requireContext());
        String privateKey = StorageUtil.getCurrentPrivatekey(requireContext());
        if (!TextUtils.isEmpty(privateKey)) {
            marketRepository = new GoldMarketRepository(requireContext(), privateKey);
        }
        markwon = Markwon.builder(requireContext())
                .usePlugin(TablePlugin.create(requireContext()))
                .build();
        bindQuickActions();
        loadAiAdvice();
    }

    private void initViews(View view) {
        messageContainer = view.findViewById(R.id.message_container);
        quickActionContainer = view.findViewById(R.id.quick_action_container);
        messageScroll = view.findViewById(R.id.message_scroll);
        inputField = view.findViewById(R.id.input_field);
        sendBtn = view.findViewById(R.id.send_btn);
        btnConfig = view.findViewById(R.id.btn_config);
        tvAiSignal = view.findViewById(R.id.tv_ai_signal);
        tvAiSummary = view.findViewById(R.id.tv_ai_summary);
        tvAiCardToggle = view.findViewById(R.id.tv_ai_card_toggle);
        aiAdviceContent = view.findViewById(R.id.ai_advice_content);
        cardAiAdvice = view.findViewById(R.id.card_ai_advice);
        aiChatRoot = view.findViewById(R.id.ai_chat_root);
        tvAiSignal.setPadding(dp(10), dp(6), dp(10), dp(6));
        sendBtn.setOnClickListener(v -> onSendMessage());
        btnConfig.setOnClickListener(v -> showApiKeyDialog());
        view.findViewById(R.id.btn_ai_decision_center).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AiDecisionCenterActivity.class)));
        view.findViewById(R.id.ai_advice_header)
                .setOnClickListener(v -> setAiAdviceExpanded(
                        aiAdviceContent.getVisibility() != View.VISIBLE));
        tvAiSummary.setOnClickListener(v -> submitQuestion(
                "请给出当前黄金市场评估，包括趋势、博弈池概率和主要风险"));
    }

    private void bindQuickActions() {
        addQuickAction("市场速览", "请给出当前黄金市场评估，包括趋势、博弈池概率和主要风险");
        addQuickAction("博弈对比", this::beginCompareMarketSelection);
        addQuickAction("持仓诊断", this::beginDiagnosisSelection);
        addQuickAction("方向研判", "我想研判所有博弈池的 YES/NO 交易方向");
        addQuickAction("创建市场", this::beginCreateRequirementInput);
        addQuickAction("托管策略", "我想配置 AI 自动托管策略");
    }

    private void addQuickAction(String label, String prompt) {
        addQuickAction(label, () -> submitQuestion(prompt));
    }

    private void addQuickAction(String label, Runnable action) {
        TextView chip = new TextView(requireContext());
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTextColor(0xFF334155);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(4), 0, dp(4), 0);
        chip.setBackground(quickActionBackground());
        chip.setElevation(0f);
        int column = quickActionContainer.getChildCount() % 3;
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(40);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(column == 0 ? 0 : dp(3), 0,
                column == 2 ? 0 : dp(3), dp(6));
        quickActionContainer.addView(chip, params);
        chip.setOnClickListener(v -> {
            setAiAdviceExpanded(false);
            resetPendingWorkflows();
            inputField.setHint("输入问题或交代任务…");
            action.run();
        });
    }

    private void resetPendingWorkflows() {
        pendingCreateRequirements = false;
        pendingStrategyGame = null;
        pendingTradeGame = null;
        clearCompareWorkflow();
        clearDiagnosisWorkflow();
    }

    private void setAiAdviceExpanded(boolean expanded) {
        TransitionSet transition = new TransitionSet()
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(new ChangeBounds())
                .addTransition(new Fade());
        transition.setDuration(220L);
        TransitionManager.beginDelayedTransition(aiChatRoot, transition);
        aiAdviceContent.setVisibility(expanded ? View.VISIBLE : View.GONE);
        tvAiCardToggle.setText(expanded ? "收起 ︿" : "展开 ﹀");
        tvAiCardToggle.setContentDescription(expanded ? "收起 AI 投研工作台" : "展开 AI 投研工作台");
    }

    private GradientDrawable quickActionBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF8FAFC);
        background.setCornerRadius(dp(10));
        return background;
    }

    private void onSendMessage() {
        String text = inputField.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;
        inputField.setText("");
        submitQuestion(text);
    }

    private void submitQuestion(String text) {
        if (!ensureIdle()) return;
        addMessage("你", text);
        if (pendingCreateRequirements) {
            if (text.contains("取消")) {
                pendingCreateRequirements = false;
                inputField.setHint("输入问题或交代任务…");
                addAgentCard("已取消", "未生成创建草稿",
                        "你可以重新点击“创建市场”，再描述希望创建的判断问题。",
                        Collections.emptyList());
                return;
            }
            if (!hasCreateRuleDescription(text)) {
                addAgentCard("还需补充", "请说明具体判断条件",
                        "需要明确结果如何判定，例如价格方向、目标价格、涨跌幅、"
                                + "价格区间或与其他资产的表现对比。\n"
                                + "也可以同时写明截止时间和计划投入的初始流动性。",
                        Collections.emptyList());
                inputField.setHint("描述判断条件、截止时间和初始流动性");
                inputField.requestFocus();
                return;
            }
            pendingCreateRequirements = false;
            inputField.setHint("输入问题或交代任务…");
            showCreateDraft(text);
            return;
        }
        if (pendingStrategyGame != null) {
            if (text.contains("取消")) {
                pendingStrategyGame = null;
                inputField.setHint("输入问题或交代任务…");
                addAgentCard("已取消", "未生成托管策略",
                        "你可以重新点击“托管策略”，选择其他博弈池。",
                        Collections.emptyList());
                return;
            }
            GoldMarketRepository.GameModel selectedGame = pendingStrategyGame;
            pendingStrategyGame = null;
            inputField.setHint("输入问题或交代任务…");
            GoldAgentIntentRouter.Result parameters =
                    GoldAgentIntentRouter.route("AI 自动托管策略 " + text);
            int loadingIndex = beginLoading("正在根据你的参数生成托管策略草稿…");
            showStrategyDraftForGame(text, parameters.amountBkc, selectedGame, loadingIndex);
            return;
        }
        if (pendingTradeGame != null) {
            if (text.contains("取消")) {
                pendingTradeGame = null;
                inputField.setHint("输入问题或交代任务…");
                addAgentCard("已取消", "未生成方向研判",
                        "你可以重新点击“方向研判”，选择其他博弈池。",
                        Collections.emptyList());
                return;
            }
            GoldMarketRepository.GameModel selectedGame = pendingTradeGame;
            pendingTradeGame = null;
            inputField.setHint("输入问题或交代任务…");
            GoldAgentIntentRouter.Result parameters =
                    GoldAgentIntentRouter.route("方向研判 " + text);
            int loadingIndex = beginLoading("正在读取所选博弈池的 AMM 报价…");
            showTradeSimulation(parameters, loadingIndex, selectedGame);
            return;
        }
        GoldAgentIntentRouter.Result intent = GoldAgentIntentRouter.route(text);

        if (intent.intent == GoldAgentIntentRouter.Intent.CREATE_MARKET_DRAFT) {
            if (hasCreateRuleDescription(text)) {
                showCreateDraft(text);
            } else {
                beginCreateRequirementInput();
            }
            return;
        }
        if (requiresAi(intent.intent) && !DeepSeekClient.isConfigured()) {
            addAgentCard("需要配置", "请先配置 DeepSeek API 密钥",
                    "市场分析、对比、持仓诊断和方向研判会调用 AI；所有结果仅供研究，不会直接执行资金操作。",
                    Collections.singletonList(new CardAction("配置 API 密钥", v -> showApiKeyDialog())));
            return;
        }
        int loadingIndex = beginLoading("正在读取实时黄金行情与链上博弈池快照…");
        loadLiveContextAndHandle(text, intent, loadingIndex);
    }

    private boolean requiresAi(GoldAgentIntentRouter.Intent intent) {
        return intent == GoldAgentIntentRouter.Intent.MARKET_ANALYSIS
                || intent == GoldAgentIntentRouter.Intent.MARKET_COMPARE
                || intent == GoldAgentIntentRouter.Intent.POSITION_DIAGNOSIS
                || intent == GoldAgentIntentRouter.Intent.DIRECTION_JUDGMENT
                || intent == GoldAgentIntentRouter.Intent.GENERAL_RESEARCH;
    }

    private void loadLiveContextAndHandle(String question, GoldAgentIntentRouter.Result intent,
                                          int loadingIndex) {
        GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                loadMarketsAndHandle(question, intent, loadingIndex, quote, "");
            }

            @Override public void onError(String error) {
                loadMarketsAndHandle(question, intent, loadingIndex, null,
                        "实时黄金行情加载失败：" + safeText(error));
            }
        });
    }

    private void loadMarketsAndHandle(String question, GoldAgentIntentRouter.Result intent,
                                      int loadingIndex, GoldAdvisoryManager.Advisory quote,
                                      String quoteWarning) {
        if (destroyed || !isAdded()) return;
        if (marketRepository == null) {
            latestGames = Collections.emptyList();
            latestQuote = quote;
            marketContext = GoldMarketResearchPromptBuilder.buildMarketOverview(
                    latestGames, System.currentTimeMillis(), quote) + "\n博弈池状态：钱包尚未初始化";
            handleIntentWithSnapshot(question, intent, loadingIndex, quoteWarning);
            return;
        }
        marketRepository.getAllGamesInfo(new GoldMarketRepository.DataCallback<List<GoldMarketRepository.GameModel>>() {
            @Override public void onSuccess(List<GoldMarketRepository.GameModel> games) {
                latestGames = games == null ? Collections.emptyList() : new ArrayList<>(games);
                latestQuote = quote;
                marketContext = GoldMarketResearchPromptBuilder.buildMarketOverview(
                        latestGames, System.currentTimeMillis(), quote);
                handleIntentWithSnapshot(question, intent, loadingIndex, quoteWarning);
            }

            @Override public void onError(String error) {
                latestGames = Collections.emptyList();
                latestQuote = quote;
                marketContext = GoldMarketResearchPromptBuilder.buildMarketOverview(
                        latestGames, System.currentTimeMillis(), quote)
                        + "\n无法加载博弈池：" + safeText(error);
                handleIntentWithSnapshot(question, intent, loadingIndex, quoteWarning);
            }
        });
    }

    private void handleIntentWithSnapshot(String question, GoldAgentIntentRouter.Result intent,
                                          int loadingIndex, String quoteWarning) {
        if (!TextUtils.isEmpty(quoteWarning)) marketContext += "\n" + quoteWarning;
        switch (intent.intent) {
            case DIRECTION_JUDGMENT:
                askDirectionJudgmentWithCurrentContext(question, loadingIndex);
                return;
            case TRADE_SIMULATION:
                showTradeSimulation(intent, loadingIndex);
                return;
            case AI_MANAGED_STRATEGY:
                showStrategyDraft(question, intent, loadingIndex);
                return;
            case MARKET_COMPARE:
                askWithCurrentContext("请比较当前活跃博弈池的市场隐含概率、流动性、截止时间、"
                        + "与当前黄金行情的关联和主要风险。先给出简短结论，再逐个博弈池分析；"
                        + "不要使用 Markdown 表格，不要保证收益。\n用户需求：" + question,
                        loadingIndex, intent.displayName);
                return;
            case POSITION_DIAGNOSIS:
                if (diagnosisResultGame != null) {
                    GoldMarketRepository.GameModel refreshed = findGame(diagnosisResultGame.id);
                    askPositionDiagnosisWithCurrentContext(question, loadingIndex,
                            refreshed == null ? diagnosisResultGame : refreshed);
                    return;
                }
                askWithCurrentContext("请只基于快照诊断我的持仓：分别说明已有份额、估值不确定性、"
                        + "集中度和时间风险；数据缺失时明确指出。不要建议自动下单或保证收益。\n用户需求：" + question,
                        loadingIndex, intent.displayName);
                return;
            case MARKET_ANALYSIS:
                askMarketOverviewWithCurrentContext(question, loadingIndex);
                return;
            default:
                askWithCurrentContext(question, loadingIndex, intent.displayName);
        }
    }

    private void askWithCurrentContext(String question, int loadingIndex, String taskName) {
        String mobileFormat = "\n\n【移动端排版要求】\n"
                + "不要使用 Markdown 表格或 HTML。使用简短小标题、短段落和项目符号；"
                + "每段尽量不超过 3 行，避免连续堆砌竖线、分隔符和超长句。"
                + "不要显示博弈池 ID、#数字或内部编号，只能用完整市场标题。";
        String prompt = GoldMarketResearchPromptBuilder.withFollowUp(
                marketContext, question + mobileFormat);
        AgentManager.getInstance().askGoldResearch(prompt, new AgentManager.AnalysisCallback() {
            @Override public void onBrokerReport(AgentManager.BrokerReport report) {
                finishAnalysis(loadingIndex, report == null ? "" : report.rawAnalysis, taskName);
            }

            @Override public void onGeneralAdvice(String ignored, String answer) {
                finishAnalysis(loadingIndex, answer, taskName);
            }

            @Override public void onError(String error) {
                finishAnalysis(loadingIndex, formatAiError(error), taskName);
            }
        });
    }

    private void askMarketOverviewWithCurrentContext(String question, int loadingIndex) {
        String unifiedStance = unifiedSpotStance();
        String instruction = "请基于快照生成移动端市场速览。只返回一个 JSON 对象，"
                + "不要输出 Markdown、代码围栏或额外说明。结构为："
                + "{\"stance\":\"偏多|观望|偏空\",\"summary\":\"不超过80字的总体判断\","
                + "\"markets\":[{\"title\":\"必须逐字使用快照中的完整博弈池标题\"}],"
                + "\"risks\":[\"不超过35字的风险\"]}。"
                + "markets 最多4项，risks 最多3项。"
                + "现货方向必须使用统一标签“" + unifiedStance + "”，不得改成其他方向；"
                + "禁止输出博弈池ID、内部编号或#数字；不要保证收益，"
                + "不要把市场隐含概率说成事件真实概率。用户需求：" + question;
        String prompt = GoldMarketResearchPromptBuilder.withFollowUp(marketContext, instruction);
        AgentManager.getInstance().askGoldResearch(prompt, new AgentManager.AnalysisCallback() {
            @Override public void onBrokerReport(AgentManager.BrokerReport report) {
                finishMarketOverview(loadingIndex, report == null ? "" : report.rawAnalysis);
            }

            @Override public void onGeneralAdvice(String ignored, String answer) {
                finishMarketOverview(loadingIndex, answer);
            }

            @Override public void onError(String error) {
                finishMarketOverview(loadingIndex, "");
            }
        });
    }

    private void askDirectionJudgmentWithCurrentContext(String question, int loadingIndex) {
        String instruction = "请逐一研判快照中的全部活跃博弈池。只返回一个 JSON 对象，"
                + "不要输出 Markdown、代码围栏或额外说明。结构为："
                + "{\"summary\":\"不超过70字的总体结论\","
                + "\"markets\":[{\"title\":\"必须逐字使用快照中的完整博弈池标题\","
                + "\"direction\":\"YES|NO|HOLD\","
                + "\"reason\":\"不超过55字的主要依据\","
                + "\"risk\":\"不超过40字的关键风险\"}]}。"
                + "每个活跃博弈池必须且只能出现一次；证据不足时必须选择 HOLD。"
                + "不得使用立即买入、稳赚、无风险、必然获胜或锁定收益等绝对化表达；"
                + "市场隐含概率不等于事件真实概率，也不要编造快照中不存在的数据。"
                + "用户需求：" + question;
        String prompt = GoldMarketResearchPromptBuilder.withFollowUp(marketContext, instruction);
        AgentManager.getInstance().askGoldResearch(prompt, new AgentManager.AnalysisCallback() {
            @Override public void onBrokerReport(AgentManager.BrokerReport report) {
                finishDirectionJudgment(loadingIndex,
                        report == null ? "" : report.rawAnalysis);
            }

            @Override public void onGeneralAdvice(String ignored, String answer) {
                finishDirectionJudgment(loadingIndex, answer);
            }

            @Override public void onError(String error) {
                finishDirectionJudgment(loadingIndex, "");
            }
        });
    }

    private void finishDirectionJudgment(int loadingIndex, String answer) {
        mainHandler.post(() -> {
            if (destroyed || !isAdded()) return;
            if (loadingIndex >= 0 && loadingIndex < messageContainer.getChildCount()) {
                messageContainer.removeViewAt(loadingIndex);
            }
            setRequestInFlight(false);
            addDirectionJudgmentCard(parseDirectionJudgment(answer));
        });
    }

    private void askPositionDiagnosisWithCurrentContext(String question, int loadingIndex,
                                                        GoldMarketRepository.GameModel game) {
        String verifiedHolding = hasAnyShares(game)
                ? "系统已核验链上持仓：YES " + positionShares(game, 0)
                        + "，NO " + positionShares(game, 1) + "。这是确定性事实，不得声称未持有。"
                : "系统已核验链上持仓：YES 0 份额，NO 0 份额。";
        String instruction = "只诊断标题为「" + displayGameTitle(game) + "」的用户持仓。"
                + verifiedHolding
                + "只返回一个 JSON 对象，不要输出 Markdown 或额外说明。结构为："
                + "{\"riskLevel\":\"低风险|中风险|高风险\","
                + "\"summary\":\"不超过70字的持仓结论\","
                + "\"findings\":[\"不超过40字的诊断要点\"]}。"
                + "findings 最多3项。若持仓为零，必须明确写明当前未持有，"
                + "不得编造成本、收益或交易记录；市场概率不等于事件真实概率。"
                + "用户诊断重点：" + question;
        String prompt = GoldMarketResearchPromptBuilder.withFollowUp(marketContext, instruction);
        AgentManager.getInstance().askGoldResearch(prompt, new AgentManager.AnalysisCallback() {
            @Override public void onBrokerReport(AgentManager.BrokerReport report) {
                finishPositionDiagnosis(loadingIndex, game,
                        report == null ? "" : report.rawAnalysis);
            }

            @Override public void onGeneralAdvice(String ignored, String answer) {
                finishPositionDiagnosis(loadingIndex, game, answer);
            }

            @Override public void onError(String error) {
                finishPositionDiagnosis(loadingIndex, game, "");
            }
        });
    }

    private void finishPositionDiagnosis(int loadingIndex,
                                         GoldMarketRepository.GameModel game,
                                         String answer) {
        mainHandler.post(() -> {
            if (destroyed || !isAdded()) return;
            if (loadingIndex >= 0 && loadingIndex < messageContainer.getChildCount()) {
                messageContainer.removeViewAt(loadingIndex);
            }
            setRequestInFlight(false);
            diagnosisResultGame = null;
            addPositionDiagnosisCard(game, parsePositionDiagnosis(answer, game));
        });
    }

    private void finishMarketOverview(int loadingIndex, String answer) {
        mainHandler.post(() -> {
            if (destroyed || !isAdded()) return;
            if (loadingIndex >= 0 && loadingIndex < messageContainer.getChildCount()) {
                messageContainer.removeViewAt(loadingIndex);
            }
            setRequestInFlight(false);
            addMarketOverviewCard(parseMarketOverview(answer));
        });
    }

    private void finishAnalysis(int loadingIndex, String answer, String taskName) {
        mainHandler.post(() -> {
            if (destroyed || !isAdded()) return;
            if (loadingIndex >= 0 && loadingIndex < messageContainer.getChildCount()) {
                messageContainer.removeViewAt(loadingIndex);
            }
            setRequestInFlight(false);
            addResearchResultCard(taskName, sanitizeResearchAnswer(answer));
        });
    }

    private void showTradeSimulation(GoldAgentIntentRouter.Result intent, int loadingIndex) {
        showTradeSimulation(intent, loadingIndex, null);
    }

    private void showTradeSimulation(GoldAgentIntentRouter.Result intent, int loadingIndex,
                                     GoldMarketRepository.GameModel selectedGame) {
        boolean selling = containsSell(inputOrEmpty());
        GoldMarketRepository.GameModel game = selectedGame != null
                ? selectedGame : findGameReference(inputOrEmpty(), intent.gameId);
        if (game == null) {
            finishLoading(loadingIndex, "已识别为方向研判，请先选择需要分析的博弈池。");
            addAgentCard("需要补充", "请选择需要研判的博弈池",
                    "请直接按标题选择市场。选择后，再输入需要评估的 YES/NO 方向及参考金额。",
                    tradeMarketButtons());
            return;
        }
        if (intent.amountBkc == null) {
            finishLoading(loadingIndex, "已定位「" + displayGameTitle(game) + "」，但缺少"
                    + (selling ? "卖出份额。" : "交易金额。"));
            addAgentCard("需要补充", selling ? "请输入卖出份额" : "请输入参考金额",
                    selling
                            ? "例如：`卖出 YES 10 份额`。不会直接卖出。"
                            : "例如：`买入 YES 1 BKC`。不会直接买入。",
                    Collections.singletonList(openMarketAction(game, "查看博弈池")));
            return;
        }
        BigInteger amount = GoldMarketRepository.parseTokenAmountToWei(
                String.format(Locale.US, "%.8f", intent.amountBkc));
        int option = containsNo(inputOrEmpty()) ? 1 : 0;
        if (selling) {
            GoldSellSimulation.Result result = GoldSellSimulation.simulate(
                    game, option, amount, GoldSellSimulation.DEFAULT_SLIPPAGE_BPS);
            if (!result.valid) {
                finishLoading(loadingIndex, "卖出方向研判不可用：" + result.error);
                return;
            }
            finishLoading(loadingIndex, "已完成「" + displayGameTitle(game)
                    + "」的 AMM 卖出方向研判，未发起交易。");
            String body = "方向：卖出 " + (option == 0 ? "YES" : "NO") + "\n"
                    + "卖出份额：" + GoldTradeSimulation.formatBkc(result.shareAmountWei, 6) + "\n"
                    + "预计收到：" + GoldTradeSimulation.formatBkc(result.amountOutWei, 6) + " BKC\n"
                    + "最低到账：" + GoldTradeSimulation.formatBkc(result.minAmountOutWei, 6)
                    + " BKC（1% 滑点保护）\n"
                    + String.format(Locale.getDefault(), "YES 隐含概率：%.2f%% → %.2f%%\n\n",
                    result.beforeYesProbability.doubleValue(), result.afterYesProbability.doubleValue())
                    + "报价由当前虚拟储备和合约整数公式确定；正式页面仍需用户确认后才会上链。";
            addAgentCard("方向研判 · 未执行", displayGameTitle(game), body,
                    Collections.singletonList(openMarketAction(game, "前往卖出确认")));
            return;
        }
        GoldTradeSimulation.Result simulation = GoldTradeSimulation.simulate(game, option, amount);
        if (!simulation.valid) {
            finishLoading(loadingIndex, "方向研判不可用：" + simulation.error);
            return;
        }
        finishLoading(loadingIndex, "已完成「" + displayGameTitle(game)
                + "」的链上 AMM 方向研判，未发起交易。");
        BigDecimalSummary summary = BigDecimalSummary.of(simulation);
        String title = displayGameTitle(game);
        String body = "方向：买入 " + (option == 0 ? "YES" : "NO") + "\n"
                + "投入：" + GoldTradeSimulation.formatBkc(simulation.amountWei, 4) + " BKC\n"
                + "预计获得：" + GoldTradeSimulation.formatBkc(simulation.sharesOutWei, 6) + " 份额\n"
                + "YES 隐含概率：" + summary.before + " → " + summary.after + "（变动 " + summary.impact + "）\n"
                + "当前池深：" + GoldTradeSimulation.formatBkc(simulation.depthWei, 2) + " BKC\n\n"
                + "该结果依据当前虚拟储备和合约整数公式计算；真实成交仍会受区块确认前的其他交易影响。";
        addAgentCard("方向研判 · 未执行", title, body,
                Collections.singletonList(openMarketAction(game, "前往交易确认")));
    }

    private String inputOrEmpty() {
        // The latest user message is visible in the conversation; this helper is intentionally
        // conservative: absent an explicit NO, a requested buy defaults to YES for simulation.
        return latestUserText;
    }

    private String latestUserText = "";

    private void beginCreateRequirementInput() {
        if (!ensureIdle()) return;
        pendingStrategyGame = null;
        pendingTradeGame = null;
        if (!pendingCreateRequirements) {
            pendingCreateRequirements = true;
            addAgentCard("创建市场", "描述你想判断的问题",
                    "请用自己的话说明判断条件。为了让规则更完整，建议同时写明：\n"
                            + "• 判断什么结果\n"
                            + "• 何时截止\n"
                            + "• 计划投入多少初始流动性\n\n"
                            + "这里不会替你预设市场内容，输入“取消”可以退出。",
                    Collections.emptyList());
        }
        inputField.setHint("描述判断条件、截止时间和初始流动性");
        inputField.requestFocus();
    }

    private boolean hasCreateRuleDescription(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String[] ruleSignals = {
                "是否", "上涨", "下跌", "横盘", "高于", "低于", "大于", "小于",
                "超过", "达到", "区间", "涨幅", "跌幅", "跑赢", "连续"
        };
        for (String signal : ruleSignals) {
            if (normalized.contains(signal)) return true;
        }
        return false;
    }

    private void showCreateDraft(String text) {
        latestUserText = text;
        int loadingIndex = beginLoading("正在识别博弈池模板并整理可编辑参数…");
        if (!DeepSeekClient.isConfigured()) {
            finishLoadingAndRemove(loadingIndex);
            showCreatePreview(inferCreateDraft(text), false);
            return;
        }
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        DeepSeekClient.chatForParsing(GoldCreatePoolFragment.buildAiParserPrompt(today), text,
                new DeepSeekClient.ChatCallback() {
                    @Override public void onSuccess(String response) {
                        mainHandler.post(() -> {
                            if (destroyed || !isAdded()) return;
                            try {
                                JSONObject parsed =
                                        GoldCreatePoolFragment.parseAndValidateAiResponse(response);
                                finishLoadingAndRemove(loadingIndex);
                                showCreatePreview(parsed, true);
                            } catch (Exception error) {
                                finishLoadingAndRemove(loadingIndex);
                                showCreatePreview(inferCreateDraft(text), false);
                            }
                        });
                    }

                    @Override public void onError(String error) {
                        mainHandler.post(() -> {
                            if (destroyed || !isAdded()) return;
                            finishLoadingAndRemove(loadingIndex);
                            showCreatePreview(inferCreateDraft(text), false);
                        });
                    }
                });
    }

    private void showCreatePreview(JSONObject draft, boolean fullyParsed) {
        String type = draft.optString("type", GoldMarketTemplateCatalog.TYPE_PRICE);
        if (!GoldMarketTemplateCatalog.isCreatable(type)) {
            type = GoldMarketTemplateCatalog.TYPE_PRICE;
        }
        GoldMarketTemplateCatalog.Template template = GoldMarketTemplateCatalog.forType(type);
        addCreatePreviewCard(template, draft, fullyParsed, v -> {
            Intent page = new Intent(requireContext(), GoldCreateCustomActivity.class);
            page.putExtra("TEMPLATE_TYPE", template.type);
            page.putExtra("TEMPLATE_TITLE", template.title);
            page.putExtra("AI_PARSED_DATA", draft.toString());
            startActivity(page);
        });
    }

    private void beginCompareMarketSelection() {
        if (!ensureIdle()) return;
        ensureLatestGamesLoaded(() -> {
            List<CardAction> actions = compareFirstMarketButtons();
            addAgentCard("博弈对比", "选择第一个市场",
                    actions.isEmpty()
                            ? "当前没有可用于对比的活跃博弈池。"
                            : "依次选择两个市场，系统将直接生成完整对比。",
                    actions);
        });
    }

    private List<CardAction> compareFirstMarketButtons() {
        List<CardAction> actions = new ArrayList<>();
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (game == null || game.isResolved || game.isRefunded) continue;
            actions.add(new CardAction(compactMarketTitle(game, 22) + "  ›",
                    v -> beginCompareSecondSelection(game), true));
            if (actions.size() == 5) break;
        }
        return actions;
    }

    private void beginCompareSecondSelection(GoldMarketRepository.GameModel first) {
        pendingCompareFirstGame = first;
        addMessage("你", "对比起点：「" + displayGameTitle(first) + "」");
        List<CardAction> actions = new ArrayList<>();
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (game == null || game == first || game.isResolved || game.isRefunded) continue;
            actions.add(new CardAction(compactMarketTitle(game, 22) + "  ›",
                    v -> startMarketComparison(first, game), true));
            if (actions.size() == 5) break;
        }
        addAgentCard("继续选择", "选择对比市场",
                actions.isEmpty() ? "当前没有第二个可对比的活跃市场。"
                        : "选择需要与「" + compactMarketTitle(first, 16) + "」比较的市场。",
                actions);
    }

    private void startMarketComparison(GoldMarketRepository.GameModel first,
                                       GoldMarketRepository.GameModel second) {
        if (first == null || second == null || !ensureIdle()) return;
        clearCompareWorkflow();
        addMessage("你", "对比目标：「" + displayGameTitle(first)
                + "」与「" + displayGameTitle(second) + "」");
        String question = "请直接比较「" + displayGameTitle(first) + "」与「"
                + displayGameTitle(second) + "」。必须覆盖市场隐含概率、流动性、"
                + "剩余时间、当前黄金行情关联、主要分歧和风险。"
                + "只能使用完整市场标题，不要显示任何内部编号。";
        int loadingIndex = beginLoading("正在读取两个市场的最新概率与流动性…");
        loadLiveContextAndHandle(question, GoldAgentIntentRouter.route("博弈对比 " + question),
                loadingIndex);
    }

    private void clearCompareWorkflow() {
        pendingCompareFirstGame = null;
    }

    private void beginDiagnosisSelection() {
        if (!ensureIdle()) return;
        ensureLatestGamesLoaded(() -> {
            List<CardAction> actions = diagnosisScopeButtons();
            addAgentCard("持仓诊断", "选择要诊断的博弈池",
                    actions.isEmpty()
                            ? "当前没有可诊断的活跃博弈池。"
                            : "可选择任意一个活跃市场，不要求当前已经持有份额。",
                    actions);
        });
    }

    private List<CardAction> diagnosisScopeButtons() {
        List<CardAction> actions = new ArrayList<>();
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (game == null || game.isResolved || game.isRefunded) continue;
            actions.add(new CardAction(compactMarketTitle(game, 22) + "  ›",
                    v -> startPositionDiagnosis(game), true));
        }
        return actions;
    }

    private void startPositionDiagnosis(GoldMarketRepository.GameModel game) {
        if (game == null || !ensureIdle()) return;
        diagnosisResultGame = game;
        addMessage("你", "诊断目标：「" + displayGameTitle(game) + "」");
        String question = "请直接诊断我在「" + displayGameTitle(game) + "」中的持仓。"
                + "必须覆盖当前 YES/NO 份额、方向暴露、集中度、市场流动性、"
                + "退出风险和剩余时间；即使当前份额为零也要明确说明。"
                + "只使用市场标题，不要显示任何内部编号。";
        int loadingIndex = beginLoading("正在读取所选持仓、成本与市场快照…");
        loadLiveContextAndHandle(question, GoldAgentIntentRouter.route("持仓诊断 " + question),
                loadingIndex);
    }

    private void clearDiagnosisWorkflow() {
        diagnosisResultGame = null;
    }

    private void ensureLatestGamesLoaded(Runnable onReady) {
        if (latestGames != null && !latestGames.isEmpty()) {
            onReady.run();
            return;
        }
        if (marketRepository == null) {
            addAgentCard("暂不可用", "无法读取博弈池与持仓",
                    "请先完成钱包初始化，再重试该快捷任务。",
                    Collections.emptyList());
            return;
        }
        int loadingIndex = beginLoading("正在读取可选市场与当前持仓…");
        marketRepository.getAllGamesInfo(
                new GoldMarketRepository.DataCallback<List<GoldMarketRepository.GameModel>>() {
                    @Override public void onSuccess(List<GoldMarketRepository.GameModel> games) {
                        mainHandler.post(() -> {
                            if (destroyed || !isAdded()) return;
                            latestGames = games == null
                                    ? Collections.emptyList() : new ArrayList<>(games);
                            finishLoadingAndRemove(loadingIndex);
                            onReady.run();
                        });
                    }

                    @Override public void onError(String error) {
                        mainHandler.post(() -> {
                            if (destroyed || !isAdded()) return;
                            finishLoadingAndRemove(loadingIndex);
                            addAgentCard("读取失败", "暂时无法加载市场数据",
                                    "请检查本地后端与链连接后重试。",
                                    Collections.emptyList());
                        });
                    }
                });
    }

    private JSONObject inferCreateDraft(String text) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String type;
        if (containsAny(normalized, "跑赢", "btc", "eth", "sol", "bnb")) {
            type = GoldMarketTemplateCatalog.TYPE_RELATIVE;
        } else if (normalized.contains("连续")) {
            type = GoldMarketTemplateCatalog.TYPE_STREAK;
        } else if (containsAny(normalized, "区间", "介于", "之间")) {
            type = GoldMarketTemplateCatalog.TYPE_PRICE_RANGE;
        } else if (containsAny(normalized, "涨幅", "跌幅", "%", "％")) {
            type = GoldMarketTemplateCatalog.TYPE_RETURN_THRESHOLD;
        } else if (containsAny(normalized, "达到", "高于", "低于", "大于", "小于",
                "超过", "美元", "美金", "usd")) {
            type = GoldMarketTemplateCatalog.TYPE_PRICE_THRESHOLD;
        } else {
            type = GoldMarketTemplateCatalog.TYPE_PRICE;
        }
        JSONObject json = new JSONObject();
        try {
            json.put("type", type);
            json.put("param1", inferPrimaryParameter(normalized, type));
            json.put("param2", "");
            json.put("directionIdx",
                    containsAny(normalized, "下跌", "下降") ? 1
                            : normalized.contains("横盘") || normalized.contains("持平") ? 2 : 0);
            json.put("operatorIdx",
                    containsAny(normalized, "低于", "小于", "不超过", "至多") ? 1 : 0);
            json.put("startDaysFromNow", 0);
            json.put("durationDays", inferDurationDays(normalized));
            String liquidity = inferBkcAmount(normalized);
            if (!TextUtils.isEmpty(liquidity)) json.put("liquidity", liquidity);
            json.put("confidence", 0.5d);
        } catch (Exception ignored) {
            // JSONObject only receives local primitive values.
        }
        return json;
    }

    private String inferPrimaryParameter(String text, String type) {
        if (GoldMarketTemplateCatalog.TYPE_RELATIVE.equals(type)) {
            for (String symbol : new String[]{"BTC", "ETH", "SOL", "BNB"}) {
                if (text.contains(symbol.toLowerCase(Locale.ROOT))) return symbol;
            }
            return "BTC";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(\\d+(?:\\.\\d+)?)\\s*(?:美元|美金|usd|%|％)",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private int inferDurationDays(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?:未来|持续|观察)?\\s*([1-4])\\s*(?:天|日)").matcher(text);
        if (matcher.find()) return Integer.parseInt(matcher.group(1));
        if (text.contains("一天") || text.contains("一日")) return 1;
        if (text.contains("三天") || text.contains("三日")) return 3;
        if (text.contains("四天") || text.contains("四日")) return 4;
        return 2;
    }

    private String inferBkcAmount(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(\\d+(?:\\.\\d+)?)\\s*bkc", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) return true;
        }
        return false;
    }

    private void addCreatePreviewCard(GoldMarketTemplateCatalog.Template template,
                                      JSONObject draft, boolean fullyParsed,
                                      View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(panelBackground(0xFFFFFFFF, 0xFFD6E1EE));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12));
        messageContainer.addView(card, cardParams);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = textView("创建预览", 12, 0xFF2563EB, true);
        tag.setPadding(dp(8), dp(4), dp(8), dp(4));
        tag.setBackground(panelBackground(0xFFEFF6FF, 0xFFBFDBFE));
        header.addView(tag);
        TextView state = textView(fullyParsed ? "规则已识别" : "模板已识别", 12,
                fullyParsed ? 0xFF059669 : 0xFFB45309, true);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        stateParams.gravity = Gravity.END;
        state.setGravity(Gravity.END);
        header.addView(state, stateParams);
        card.addView(header);

        TextView title = textView(template.title + " · 参数草稿", 18, 0xFF0F172A, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(12), 0, dp(4));
        card.addView(title, titleParams);
        TextView subtitle = textView("已根据你的描述匹配到具体博弈池模板", 13,
                0xFF64748B, false);
        card.addView(subtitle);

        LinearLayout summary = new LinearLayout(requireContext());
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(13), dp(8), dp(13), dp(8));
        summary.setBackground(panelBackground(0xFFF8FAFC, 0xFFE2E8F0));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.setMargins(0, dp(13), 0, 0);
        card.addView(summary, summaryParams);
        addCreatePreviewRow(summary, "模板", template.title);
        addCreatePreviewRow(summary, "判断规则", createRuleSummary(template.type, draft));
        int start = Math.max(0, draft.optInt("startDaysFromNow", 0));
        int duration = Math.max(1, draft.optInt("durationDays", 2));
        addCreatePreviewRow(summary, "观察周期",
                (start == 0 ? "从最近有效边界开始" : start + " 天后开始")
                        + " · 持续 " + duration + " 个整日");
        String liquidity = draft.optString("liquidity", "").trim();
        addCreatePreviewRow(summary, "初始流动性",
                TextUtils.isEmpty(liquidity) ? "待填写" : liquidity + " BKC");
        addCreatePreviewRow(summary, "结算数据", "Chainlink XAU/USD");

        TextView note = textView(fullyParsed
                        ? "参数均可在下一页修改，确认部署前不会上链。"
                        : "已完成模板匹配；缺失参数请在下一页补充，确认部署前不会上链。",
                12, 0xFF64748B, false);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.setMargins(0, dp(11), 0, dp(12));
        card.addView(note, noteParams);

        Button action = new Button(requireContext());
        action.setText("进入" + template.title + "参数定制  →");
        action.setTextSize(14);
        action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        action.setTextColor(Color.WHITE);
        action.setAllCaps(false);
        action.setStateListAnimator(null);
        action.setBackground(panelBackground(0xFF2563EB, 0xFF2563EB));
        card.addView(action, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        action.setOnClickListener(listener);
        scrollToBottom();
    }

    private void addCreatePreviewRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(6), 0, dp(6));
        TextView key = textView(label, 13, 0xFF64748B, false);
        TextView content = textView(value, 13, 0xFF0F172A, true);
        content.setGravity(Gravity.END);
        row.addView(key, new LinearLayout.LayoutParams(dp(78),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.addView(content, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row);
    }

    private String createRuleSummary(String type, JSONObject draft) {
        String param1 = draft.optString("param1", "").trim();
        String param2 = draft.optString("param2", "").trim();
        int direction = draft.optInt("directionIdx", 0);
        int operator = draft.optInt("operatorIdx", 0);
        if (GoldMarketTemplateCatalog.TYPE_PRICE.equals(type)) {
            return "黄金价格" + (direction == 1 ? "下跌" : direction == 2 ? "横盘" : "上涨");
        }
        if (GoldMarketTemplateCatalog.TYPE_STREAK.equals(type)) {
            return "黄金连续" + (direction == 1 ? "下跌" : "上涨");
        }
        if (GoldMarketTemplateCatalog.TYPE_RELATIVE.equals(type)) {
            return "黄金收益率跑赢 " + (TextUtils.isEmpty(param1) ? "待选择资产" : param1);
        }
        if (GoldMarketTemplateCatalog.TYPE_PRICE_RANGE.equals(type)) {
            String range = TextUtils.isEmpty(param1) || TextUtils.isEmpty(param2)
                    ? "待填写价格区间" : param1 + "–" + param2 + " USD/盎司";
            return operator == 1 ? "黄金价格位于区间外：" + range : "黄金价格位于区间内：" + range;
        }
        String operatorText = operator == 1 ? "≤" : "≥";
        if (GoldMarketTemplateCatalog.TYPE_RETURN_THRESHOLD.equals(type)) {
            return TextUtils.isEmpty(param1) ? "待填写涨跌幅阈值"
                    : "黄金绝对涨跌幅 " + operatorText + " " + param1 + "%";
        }
        return TextUtils.isEmpty(param1) ? "待填写目标价格"
                : "黄金价格 " + operatorText + " " + param1 + " USD/盎司";
    }

    private void showStrategyDraft(String text, GoldAgentIntentRouter.Result intent, int loadingIndex) {
        GoldMarketRepository.GameModel game = findGameReference(text, intent.gameId);
        if (game == null) {
            finishLoadingAndRemove(loadingIndex);
            List<CardAction> actions = strategyMarketButtons();
            String body = actions.isEmpty()
                    ? "当前没有可配置的活跃博弈池。请刷新市场数据后重试。"
                    : "按标题选择目标市场。选择后，再设置风险偏好和单笔金额。";
            addAgentCard("需要补充", "请选择要托管的博弈池", body, actions);
            return;
        }
        showStrategyDraftForGame(text, intent.amountBkc, game, loadingIndex);
    }

    private void showStrategyDraftForGame(String text, Double amount,
                                          GoldMarketRepository.GameModel game,
                                          int loadingIndex) {
        BackendApiClient.AiManagedConfig config = draftStrategy(text, amount);
        finishLoadingAndRemove(loadingIndex);
        String body = "单笔基础金额：" + config.buyAmountBKC + " BKC\n"
                + "最低置信度：" + Math.round(config.confidenceMin * 100) + "%\n"
                + "最小概率优势：" + trimPercent(config.minEdgePercent) + "%\n"
                + "Kelly 缩放系数：" + Math.round(config.kellyFraction * 100) + "%\n"
                + "冷却机制：" + (config.adaptiveCooldown ? "开启" : "关闭") + "\n\n"
                + "进入设置页后仍可修改；只有保存确认后才会启用。";
        addAgentCard("待用户确认", "AI 自动托管策略草稿 · " + displayGameTitle(game), body,
                Collections.singletonList(new CardAction("查看并确认策略  →", v -> {
                    String contract = TextUtils.isEmpty(game.contractAddress) && marketRepository != null
                            ? marketRepository.getBoundContractAddress() : game.contractAddress;
                    Intent page = GoldAiManagedSettingsActivity.createIntent(requireContext(), game.id,
                            contract, displayGameTitle(game), config);
                    startActivity(page);
                })));
    }

    private List<CardAction> strategyMarketButtons() {
        List<CardAction> actions = new ArrayList<>();
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (game == null || game.isResolved || game.isRefunded) continue;
            String label = compactMarketTitle(game, 18) + "  ›";
            actions.add(new CardAction(label, v -> beginStrategyParameterInput(game), true));
            if (actions.size() == 4) break;
        }
        return actions;
    }

    private void beginStrategyParameterInput(GoldMarketRepository.GameModel game) {
        pendingTradeGame = null;
        pendingStrategyGame = game;
        addMessage("你", "托管目标：「" + displayGameTitle(game) + "」");
        addAgentCard("继续配置", "完善策略参数",
                "还需要风险偏好和单笔基础金额。\n"
                        + "例如：“保守，每单 0.5 BKC”或“激进，每单 2 BKC”。"
                        + " 输入“取消”可以结束配置。",
                Collections.emptyList());
        inputField.setHint("例如：保守，每单 0.5 BKC");
        inputField.requestFocus();
    }

    private List<CardAction> tradeMarketButtons() {
        List<CardAction> actions = new ArrayList<>();
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (game == null || game.isResolved || game.isRefunded) continue;
            String label = compactMarketTitle(game, 18) + "  ›";
            actions.add(new CardAction(label, v -> beginTradeParameterInput(game), true));
            if (actions.size() == 4) break;
        }
        return actions;
    }

    private void beginTradeParameterInput(GoldMarketRepository.GameModel game) {
        pendingStrategyGame = null;
        pendingTradeGame = game;
        addMessage("你", "研判目标：「" + displayGameTitle(game) + "」");
        addAgentCard("继续研判", "选择评估方向与参考金额",
                "请输入需要评估的方向和参考金额，例如：“买入 YES 1 BKC”"
                        + "或“卖出 NO 10 份额”。\n"
                        + "系统只生成 AMM 报价与价格影响，不会直接执行交易。输入“取消”可以退出。",
                Collections.emptyList());
        inputField.setHint("例如：买入 YES 1 BKC");
        inputField.requestFocus();
    }

    private BackendApiClient.AiManagedConfig draftStrategy(String text, Double amount) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        BackendApiClient.AiManagedConfig config = BackendApiClient.AiManagedConfig.defaults();
        if (lower.contains("保守")) {
            config.buyAmountBKC = "0.5";
            config.confidenceMin = .80d;
            config.minEdgePercent = 8d;
            config.kellyFraction = .15d;
        } else if (lower.contains("激进") || lower.contains("积极")) {
            config.buyAmountBKC = "2";
            config.confidenceMin = .60d;
            config.minEdgePercent = 3d;
            config.kellyFraction = .40d;
        }
        if (amount != null && amount > 0d && amount <= 1000d) {
            config.buyAmountBKC = String.format(Locale.US, "%.4f", amount)
                    .replaceFirst("0+$", "").replaceFirst("\\.$", "");
        }
        return config;
    }

    private void addEvidenceCard(String taskName) {
        GoldAdvisoryManager.Advisory quote = latestQuote;
        String source = quote == null || TextUtils.isEmpty(quote.quoteSource) ? "行情来源暂不可用" : quote.quoteSource;
        String updated = quote == null || TextUtils.isEmpty(quote.quoteUpdatedAt) ? "刚刚获取" : quote.quoteUpdatedAt;
        String body = source + " · " + latestGames.size() + " 个链上博弈池\n"
                + updated + " · AI 观点仅供投研参考";
        addAgentCard("数据快照", taskName + " · 已核验输入", body, Collections.emptyList());
    }

    private String sanitizeResearchAnswer(String answer) {
        String cleaned = safeText(answer);
        cleaned = cleaned.replaceAll(
                "博弈池\\s*#\\s*\\d+\\s*[（(]([^）)]+)[）)]", "「$1」");
        cleaned = cleaned.replaceAll(
                "#\\s*\\d+\\s*[（(]([^）)]+)[）)]", "「$1」");
        cleaned = cleaned.replaceAll("博弈池\\s*#\\s*\\d+", "该博弈池");
        cleaned = cleaned.replaceAll("#\\s*\\d+\\s*博弈池", "该博弈池");
        cleaned = cleaned.replaceAll("(?m)^\\s*[-_*]{3,}\\s*$", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }

    private void addResearchResultCard(String taskName, String answer) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        card.setBackground(panelBackground(0xFFFFFFFF, 0xFFD6E1EE));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12));
        messageContainer.addView(card, cardParams);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = textView(taskName, 12, 0xFF2563EB, true);
        tag.setPadding(dp(8), dp(4), dp(8), dp(4));
        tag.setBackground(panelBackground(0xFFEFF6FF, 0xFFBFDBFE));
        header.addView(tag);
        TextView state = textView("实时快照", 12, 0xFF059669, true);
        state.setGravity(Gravity.END);
        header.addView(state, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(header);

        TextView title = textView(researchCardTitle(taskName), 19, 0xFF0F172A, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(12), 0, dp(8));
        card.addView(title, titleParams);

        TextView content = textView("", 14, 0xFF172033, false);
        content.setLineSpacing(dp(3), 1.06f);
        content.setPadding(dp(2), 0, dp(2), 0);
        if (markwon != null) markwon.setMarkdown(content, answer); else content.setText(answer);
        card.addView(content);

        View divider = new View(requireContext());
        divider.setBackgroundColor(0xFFE7EDF4);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.setMargins(0, dp(13), 0, dp(10));
        card.addView(divider, dividerParams);

        GoldAdvisoryManager.Advisory quote = latestQuote;
        String source = quote == null || TextUtils.isEmpty(quote.quoteSource)
                ? "行情来源暂不可用" : quote.quoteSource;
        TextView footer = textView(source + " · " + latestGames.size()
                + " 个链上市场 · 仅供投研参考", 11, 0xFF8492A6, false);
        card.addView(footer);
        scrollToBottom();
    }

    private String researchCardTitle(String taskName) {
        if ("黄金市场分析".equals(taskName)) return "黄金市场速览";
        if ("博弈池对比".equals(taskName)) return "市场对比结论";
        if ("个人持仓诊断".equals(taskName)) return "持仓风险诊断";
        return "AI 投研结论";
    }

    private DirectionJudgmentPayload parseDirectionJudgment(String answer) {
        DirectionJudgmentPayload payload = new DirectionJudgmentPayload();
        try {
            String raw = answer == null ? "" : answer.trim();
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) throw new IllegalArgumentException("missing JSON");
            JSONObject json = new JSONObject(raw.substring(start, end + 1));
            payload.summary = safeDirectionText(
                    cleanOverviewText(json.optString("summary", ""), 100), 100);
            JSONArray markets = json.optJSONArray("markets");
            if (markets != null) {
                for (int index = 0; index < markets.length(); index++) {
                    JSONObject item = markets.optJSONObject(index);
                    if (item == null) continue;
                    GoldMarketRepository.GameModel game = findGameReference(
                            item.optString("title", ""), -1);
                    if (game == null || game.isResolved || game.isRefunded
                            || containsDirectionGame(payload.markets, game)) {
                        continue;
                    }
                    payload.markets.add(new DirectionJudgmentInsight(
                            game,
                            normalizeDirection(item.optString("direction", "HOLD")),
                            safeDirectionText(cleanOverviewText(
                                    item.optString("reason", ""), 80), 80),
                            safeDirectionText(cleanOverviewText(
                                    item.optString("risk", ""), 65), 65)));
                }
            }
        } catch (Exception ignored) {
            // Deterministic market metrics still render below with HOLD fallbacks.
        }
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (game == null || game.isResolved || game.isRefunded
                    || containsDirectionGame(payload.markets, game)) {
                continue;
            }
            payload.markets.add(new DirectionJudgmentInsight(
                    game, "HOLD",
                    "当前证据不足，暂不形成明确的 YES 或 NO 方向判断。",
                    "等待行情、市场定价或剩余时间出现更清晰变化。"));
        }
        if (TextUtils.isEmpty(payload.summary)) {
            payload.summary = payload.markets.isEmpty()
                    ? "当前没有可研判的活跃博弈池。"
                    : "已结合实时黄金行情与链上市场快照逐一评估；证据不足的市场保持观望。";
        }
        return payload;
    }

    private boolean containsDirectionGame(List<DirectionJudgmentInsight> insights,
                                          GoldMarketRepository.GameModel game) {
        for (DirectionJudgmentInsight insight : insights) {
            if (insight != null && insight.game != null && insight.game.id == game.id
                    && TextUtils.equals(insight.game.contractAddress, game.contractAddress)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeDirection(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("YES")) return "YES";
        if (normalized.contains("NO")) return "NO";
        return "HOLD";
    }

    private String safeDirectionText(String value, int maxLength) {
        String cleaned = value == null ? "" : value;
        cleaned = cleaned.replace("立即买入", "关注买入方向")
                .replace("立即卖出", "关注卖出方向")
                .replace("无风险套利", "相对高确定性机会")
                .replace("无风险", "风险较低")
                .replace("稳赚", "存在潜在机会")
                .replace("锁定收益", "关注潜在收益")
                .replace("必然获胜", "胜率可能较高")
                .replace("必然", "较可能");
        return cleaned.length() <= maxLength
                ? cleaned : cleaned.substring(0, maxLength) + "…";
    }

    private MarketOverviewPayload parseMarketOverview(String answer) {
        MarketOverviewPayload payload = new MarketOverviewPayload();
        try {
            String raw = answer == null ? "" : answer.trim();
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) throw new IllegalArgumentException("missing JSON");
            JSONObject json = new JSONObject(raw.substring(start, end + 1));
            payload.stance = cleanOverviewText(json.optString("stance", "观望"), 8);
            payload.summary = cleanOverviewText(json.optString("summary", ""), 120);
            JSONArray markets = json.optJSONArray("markets");
            if (markets != null) {
                for (int index = 0; index < markets.length() && payload.markets.size() < 4; index++) {
                    JSONObject item = markets.optJSONObject(index);
                    if (item == null) continue;
                    String title = cleanOverviewText(item.optString("title", ""), 100);
                    if (!TextUtils.isEmpty(title)) {
                        payload.markets.add(new MarketOverviewInsight(title));
                    }
                }
            }
            JSONArray risks = json.optJSONArray("risks");
            if (risks != null) {
                for (int index = 0; index < risks.length() && payload.risks.size() < 3; index++) {
                    String risk = cleanOverviewText(risks.optString(index, ""), 60);
                    if (!TextUtils.isEmpty(risk)) payload.risks.add(risk);
                }
            }
        } catch (Exception ignored) {
            // Live market data below still produces a useful component-based overview.
        }
        payload.stance = unifiedSpotStance();
        if (latestSpotAdvisory != null && !TextUtils.isEmpty(latestSpotAdvisory.summary)) {
            payload.summary = cleanOverviewText(latestSpotAdvisory.summary, 120);
        } else if (TextUtils.isEmpty(payload.summary)) {
            payload.summary = localOverviewSummary();
        }
        if (payload.risks.isEmpty()) {
            payload.risks.add("市场隐含概率不等于事件真实概率");
            payload.risks.add("临近截止时，价格和份额可能快速波动");
            payload.risks.add("低流动性市场可能出现较大的成交影响");
        }
        return payload;
    }

    private String cleanOverviewText(String value, int maxLength) {
        String cleaned = sanitizeResearchAnswer(value)
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength) + "…";
    }

    private String unifiedSpotStance() {
        if (latestSpotAdvisory != null) {
            return displaySignal(latestSpotAdvisory.signal);
        }
        if (latestQuote == null || !latestQuote.changeAvailable) return "观望";
        if (latestQuote.change24h > .3d) return "偏多";
        if (latestQuote.change24h < -.3d) return "偏空";
        return "观望";
    }

    private String localOverviewSummary() {
        if (latestQuote == null || latestQuote.priceUsd <= 0d) {
            return "实时行情暂不可用，以下仅展示链上市场当前定价。";
        }
        String direction = latestQuote.changeAvailable
                ? latestQuote.change24h > 0 ? "上涨" : latestQuote.change24h < 0 ? "下跌" : "持平"
                : "方向暂不可用";
        return String.format(Locale.getDefault(),
                "黄金现货约 %,.2f 美元，24 小时%s；请结合各市场条件与剩余时间判断。",
                latestQuote.priceUsd, direction);
    }

    private PositionDiagnosisPayload parsePositionDiagnosis(
            String answer, GoldMarketRepository.GameModel game) {
        PositionDiagnosisPayload payload = new PositionDiagnosisPayload();
        try {
            String raw = answer == null ? "" : answer.trim();
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) throw new IllegalArgumentException("missing JSON");
            JSONObject json = new JSONObject(raw.substring(start, end + 1));
            payload.riskLevel = cleanOverviewText(
                    json.optString("riskLevel", ""), 8);
            payload.summary = cleanOverviewText(json.optString("summary", ""), 110);
            JSONArray findings = json.optJSONArray("findings");
            if (findings != null) {
                for (int index = 0; index < findings.length()
                        && payload.findings.size() < 3; index++) {
                    String finding = cleanOverviewText(findings.optString(index, ""), 70);
                    if (!TextUtils.isEmpty(finding)) payload.findings.add(finding);
                }
            }
        } catch (Exception ignored) {
            // The deterministic position snapshot below remains available.
        }
        boolean holding = hasAnyShares(game);
        if (holding && contradictsVerifiedHolding(payload.summary)) {
            payload.summary = verifiedHoldingSummary(game);
        }
        if (holding) {
            for (int index = payload.findings.size() - 1; index >= 0; index--) {
                if (contradictsVerifiedHolding(payload.findings.get(index))) {
                    payload.findings.remove(index);
                }
            }
        }
        if (TextUtils.isEmpty(payload.riskLevel)) {
            payload.riskLevel = holding ? "中风险" : "观察中";
        }
        if (TextUtils.isEmpty(payload.summary)) {
            payload.summary = holding
                    ? "当前已持有该市场份额，请结合市场定价、流动性和剩余时间持续评估。"
                    : "当前未持有该市场份额，本次诊断仅用于观察市场风险。";
        }
        if (payload.findings.isEmpty()) {
            payload.findings.add(holding
                    ? "份额已暴露于该市场的方向与结算风险"
                    : "当前 YES 与 NO 持有份额均为零");
            payload.findings.add("市场隐含概率不等于事件真实概率");
            payload.findings.add("成交前应检查池深与截止时间");
        }
        return payload;
    }

    private boolean contradictsVerifiedHolding(String text) {
        if (TextUtils.isEmpty(text)) return false;
        String normalized = text.replaceAll("\\s+", "");
        return normalized.contains("未持有")
                || normalized.contains("没有持有")
                || normalized.contains("并未持有")
                || normalized.contains("持仓为零")
                || normalized.contains("持仓是零")
                || normalized.contains("持仓为空")
                || normalized.contains("份额为零")
                || normalized.contains("无需调整持仓");
    }

    private String verifiedHoldingSummary(GoldMarketRepository.GameModel game) {
        return "链上已确认持有 YES " + positionShares(game, 0)
                + "、NO " + positionShares(game, 1)
                + "；应结合双向敞口、市场定价、流动性和剩余时间继续评估。";
    }

    private void addPositionDiagnosisCard(GoldMarketRepository.GameModel game,
                                          PositionDiagnosisPayload payload) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(15));
        card.setBackground(panelBackground(0xFFFFFFFF, 0xFFD6E1EE));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12));
        messageContainer.addView(card, cardParams);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = textView("持仓诊断", 12, 0xFF2563EB, true);
        tag.setPadding(dp(8), dp(4), dp(8), dp(4));
        tag.setBackground(panelBackground(0xFFEFF6FF, 0xFFBFDBFE));
        header.addView(tag);
        TextView risk = textView(payload.riskLevel, 12,
                diagnosisRiskColor(payload.riskLevel), true);
        risk.setGravity(Gravity.END);
        header.addView(risk, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(header);

        TextView title = textView("单市场持仓诊断", 19, 0xFF0F172A, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(12), 0, dp(10));
        card.addView(title, titleParams);

        addPositionMarketLink(card, game);
        addPositionSnapshotPanel(card, game);

        LinearLayout conclusion = new LinearLayout(requireContext());
        conclusion.setOrientation(LinearLayout.VERTICAL);
        conclusion.setPadding(dp(12), dp(10), dp(12), dp(10));
        conclusion.setBackground(panelBackground(0xFFF0F7FF, 0xFFBFDBFE));
        LinearLayout.LayoutParams conclusionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        conclusionParams.setMargins(0, dp(10), 0, 0);
        card.addView(conclusion, conclusionParams);
        conclusion.addView(textView("AI 诊断结论", 12, 0xFF2563EB, true));
        TextView summary = textView(payload.summary, 13, 0xFF1E3A5F, false);
        summary.setLineSpacing(dp(3), 1.03f);
        summary.setPadding(0, dp(5), 0, 0);
        conclusion.addView(summary);

        TextView findingsTitle = textView("诊断要点", 14, 0xFF0F172A, true);
        LinearLayout.LayoutParams findingsTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        findingsTitleParams.setMargins(0, dp(13), 0, dp(4));
        card.addView(findingsTitle, findingsTitleParams);
        for (int index = 0; index < payload.findings.size(); index++) {
            LinearLayout finding = new LinearLayout(requireContext());
            finding.setGravity(Gravity.TOP);
            finding.setPadding(0, dp(5), 0, dp(5));
            TextView number = textView(String.valueOf(index + 1), 11, Color.WHITE, true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(panelBackground(0xFF334155, 0xFF334155));
            finding.addView(number, new LinearLayout.LayoutParams(dp(22), dp(22)));
            TextView value = textView(payload.findings.get(index), 12, 0xFF475569, false);
            value.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            valueParams.setMargins(dp(9), dp(1), 0, 0);
            finding.addView(value, valueParams);
            card.addView(finding);
        }

        Button details = new Button(requireContext());
        details.setText(hasAnyShares(game) ? "查看持仓详情  →" : "查看博弈池详情  →");
        details.setTextSize(13);
        details.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        details.setTextColor(0xFF334155);
        details.setAllCaps(false);
        details.setStateListAnimator(null);
        details.setBackground(panelBackground(0xFFF8FAFC, 0xFFDCE5EF));
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        detailsParams.setMargins(0, dp(10), 0, 0);
        card.addView(details, detailsParams);
        details.setOnClickListener(v -> {
            Intent page = new Intent(requireContext(),
                    hasAnyShares(game) ? GoldPositionDetailActivity.class
                            : GoldMarketDetailActivity.class);
            page.putExtra("GAME_ID", game.id);
            if (!TextUtils.isEmpty(game.contractAddress)) {
                page.putExtra("CONTRACT_ADDRESS", game.contractAddress);
            }
            startActivity(page);
        });
        scrollToBottom();
    }

    private void addPositionMarketLink(LinearLayout parent,
                                       GoldMarketRepository.GameModel game) {
        LinearLayout market = new LinearLayout(requireContext());
        market.setGravity(Gravity.CENTER_VERTICAL);
        market.setPadding(dp(12), 0, dp(12), 0);
        market.setBackground(panelBackground(0xFFF8FAFC, 0xFFDCE5EF));
        TextView title = textView("", 14, 0xFF0F172A, true);
        title.setText(GoldMarketTextStyler.style(displayGameTitle(game), true));
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        market.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        market.addView(textView("›", 18, 0xFF64748B, true));
        parent.addView(market, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        market.setOnClickListener(v -> openMarket(game));
    }

    private void addPositionSnapshotPanel(LinearLayout parent,
                                          GoldMarketRepository.GameModel game) {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(10), dp(12), dp(10));
        panel.setBackground(panelBackground(0xFF0F172A, 0xFF0F172A));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        panelParams.setMargins(0, dp(9), 0, 0);
        parent.addView(panel, panelParams);

        LinearLayout shares = new LinearLayout(requireContext());
        shares.addView(positionMetric("YES 持有", positionShares(game, 0),
                0xFF34D399, false), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        shares.addView(positionMetric("NO 持有", positionShares(game, 1),
                0xFFFB7185, true), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(shares);

        LinearLayout marketData = new LinearLayout(requireContext());
        marketData.setPadding(0, dp(10), 0, 0);
        double yesProbability = positionYesProbability(game);
        marketData.addView(positionMetric("YES 市场概率",
                String.format(Locale.getDefault(), "%.1f%%", yesProbability),
                0xFFE2E8F0, false), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        String liquidity = GoldNoteMarketActivity.formatBkc(game.totalPool) + " BKC";
        marketData.addView(positionMetric("市场流动性", liquidity,
                0xFFE2E8F0, true), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(marketData);

        long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(
                game.deadlineSec, System.currentTimeMillis());
        TextView time = textView(GoldNoteMarketActivity.formatRemainingTime(remaining),
                11, 0xFF94A3B8, false);
        time.setPadding(0, dp(10), 0, 0);
        panel.addView(time);
    }

    private LinearLayout positionMetric(String label, String value,
                                        int valueColor, boolean alignEnd) {
        LinearLayout metric = new LinearLayout(requireContext());
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(alignEnd ? Gravity.END : Gravity.START);
        TextView labelView = textView(label, 10, 0xFF94A3B8, true);
        labelView.setGravity(alignEnd ? Gravity.END : Gravity.START);
        metric.addView(labelView);
        TextView valueView = textView(value, 14, valueColor, true);
        valueView.setGravity(alignEnd ? Gravity.END : Gravity.START);
        valueView.setPadding(0, dp(3), 0, 0);
        metric.addView(valueView);
        return metric;
    }

    private String positionShares(GoldMarketRepository.GameModel game, int option) {
        if (game == null || game.myShares == null || option < 0
                || option >= game.myShares.size()) return "0 份额";
        return GoldNoteMarketActivity.formatShareAmount(game.myShares.get(option)) + " 份额";
    }

    private boolean hasAnyShares(GoldMarketRepository.GameModel game) {
        if (game == null || game.myShares == null) return false;
        for (BigInteger shares : game.myShares) {
            if (shares != null && shares.signum() > 0) return true;
        }
        return false;
    }

    private double positionYesProbability(GoldMarketRepository.GameModel game) {
        if (game == null || game.virtualReserves == null || game.virtualReserves.size() < 2
                || game.virtualReserves.get(0) == null || game.virtualReserves.get(1) == null) {
            return 50d;
        }
        BigInteger yes = game.virtualReserves.get(0);
        BigInteger no = game.virtualReserves.get(1);
        BigInteger total = yes.add(no);
        if (yes.signum() < 0 || no.signum() < 0 || total.signum() <= 0) return 50d;
        return new java.math.BigDecimal(yes)
                .multiply(java.math.BigDecimal.valueOf(100))
                .divide(new java.math.BigDecimal(total), 1, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    private int diagnosisRiskColor(String riskLevel) {
        if (riskLevel != null && riskLevel.contains("高")) return 0xFFE11D48;
        if (riskLevel != null && riskLevel.contains("低")) return 0xFF059669;
        return 0xFFB45309;
    }

    private void addDirectionJudgmentCard(DirectionJudgmentPayload payload) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(15));
        card.setBackground(panelBackground(0xFFFFFFFF, 0xFFD6E1EE));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12));
        messageContainer.addView(card, cardParams);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = textView("方向研判", 12, 0xFF2563EB, true);
        tag.setPadding(dp(8), dp(4), dp(8), dp(4));
        tag.setBackground(panelBackground(0xFFEFF6FF, 0xFFBFDBFE));
        header.addView(tag);
        TextView live = textView("● 实时快照", 12, 0xFF059669, true);
        live.setGravity(Gravity.END);
        header.addView(live, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(header);

        TextView title = textView("博弈方向研判", 20, 0xFF0F172A, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(13), 0, dp(9));
        card.addView(title, titleParams);

        TextView summaryText = textView(payload.summary, 13, 0xFF1E3A5F, false);
        summaryText.setLineSpacing(dp(2), 1.03f);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        summaryParams.setMargins(0, 0, 0, dp(12));
        card.addView(summaryText, summaryParams);

        if (payload.markets.isEmpty()) {
            TextView empty = textView("当前没有可研判的活跃博弈池。", 13, 0xFF64748B, false);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            empty.setBackground(panelBackground(0xFFF8FAFC, 0xFFE2E8F0));
            card.addView(empty);
        } else {
            for (DirectionJudgmentInsight insight : payload.markets) {
                addDirectionMarketCard(card, insight);
            }
        }

        String source = latestQuote == null || TextUtils.isEmpty(latestQuote.quoteSource)
                ? "行情来源暂不可用" : latestQuote.quoteSource;
        TextView footer = textView(source + " · 链上市场快照 · 仅供投研参考",
                11, 0xFF8492A6, false);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerParams.setMargins(0, dp(4), 0, 0);
        card.addView(footer, footerParams);
        scrollToBottom();
    }

    private void addDirectionMarketCard(LinearLayout parent,
                                        DirectionJudgmentInsight insight) {
        GoldMarketRepository.GameModel game = insight.game;
        LinearLayout market = new LinearLayout(requireContext());
        market.setOrientation(LinearLayout.VERTICAL);
        market.setPadding(dp(13), dp(11), dp(13), dp(11));
        market.setBackground(panelBackground(0xFFF8FAFC, 0xFFDCE5EF));
        LinearLayout.LayoutParams marketParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        marketParams.setMargins(0, 0, 0, dp(9));
        parent.addView(market, marketParams);

        LinearLayout heading = new LinearLayout(requireContext());
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView marketTitle = textView("", 14, 0xFF0F172A, true);
        marketTitle.setText(GoldMarketTextStyler.style(displayGameTitle(game), true));
        marketTitle.setMaxLines(1);
        marketTitle.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(marketTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView direction = textView(directionLabel(insight.direction), 12,
                directionTextColor(insight.direction), true);
        LinearLayout.LayoutParams directionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        directionParams.setMargins(dp(9), 0, 0, 0);
        heading.addView(direction, directionParams);
        market.addView(heading);

        double yesProbability = positionYesProbability(game);
        long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(
                game.deadlineSec, System.currentTimeMillis());
        String meta = String.format(Locale.getDefault(), "YES %.1f%%", yesProbability)
                + "  ·  流动性 " + GoldNoteMarketActivity.formatBkc(game.totalPool) + " BKC"
                + "  ·  " + GoldNoteMarketActivity.formatRemainingTime(remaining);
        TextView metadata = textView(meta, 11, 0xFF64748B, false);
        metadata.setMaxLines(1);
        metadata.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams metadataParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        metadataParams.setMargins(0, dp(7), 0, dp(7));
        market.addView(metadata, metadataParams);

        String reason = TextUtils.isEmpty(insight.reason)
                ? "当前证据不足，暂不形成明确方向判断。" : insight.reason;
        TextView reasonText = textView(reason, 12, 0xFF334155, false);
        reasonText.setLineSpacing(dp(2), 1.02f);
        reasonText.setMaxLines(2);
        reasonText.setEllipsize(TextUtils.TruncateAt.END);
        market.addView(reasonText);

        if (!TextUtils.isEmpty(insight.risk)) {
            TextView risk = textView("风险：" + insight.risk, 11, 0xFF7C889B, false);
            risk.setMaxLines(1);
            risk.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams riskParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            riskParams.setMargins(0, dp(6), 0, 0);
            market.addView(risk, riskParams);
        }
        market.setOnClickListener(v -> openMarket(game));
    }

    private String directionLabel(String direction) {
        if ("YES".equals(direction)) return "偏向 YES  ›";
        if ("NO".equals(direction)) return "偏向 NO  ›";
        return "观望  ›";
    }

    private int directionTextColor(String direction) {
        if ("YES".equals(direction)) return 0xFF047857;
        if ("NO".equals(direction)) return 0xFFBE123C;
        return 0xFF64748B;
    }

    private void addMarketOverviewCard(MarketOverviewPayload payload) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(15));
        card.setBackground(panelBackground(0xFFFFFFFF, 0xFFD6E1EE));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, dp(12));
        messageContainer.addView(card, cardParams);

        LinearLayout header = new LinearLayout(requireContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView tag = textView("市场速览", 12, 0xFF2563EB, true);
        tag.setPadding(dp(8), dp(4), dp(8), dp(4));
        tag.setBackground(panelBackground(0xFFEFF6FF, 0xFFBFDBFE));
        header.addView(tag);
        TextView live = textView("● 实时快照", 12, 0xFF059669, true);
        live.setGravity(Gravity.END);
        header.addView(live, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(header);

        TextView title = textView("黄金市场速览", 20, 0xFF0F172A, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(13), 0, dp(10));
        card.addView(title, titleParams);

        addQuoteMetricPanel(card, payload);
        addOverviewSummaryPanel(card, payload);

        TextView marketSection = textView("重点市场", 15, 0xFF0F172A, true);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sectionParams.setMargins(0, dp(16), 0, dp(8));
        card.addView(marketSection, sectionParams);
        addOverviewMarketComponents(card, payload);

        TextView riskTitle = textView("风险提示", 15, 0xFF0F172A, true);
        LinearLayout.LayoutParams riskTitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        riskTitleParams.setMargins(0, dp(15), 0, dp(8));
        card.addView(riskTitle, riskTitleParams);
        LinearLayout risks = new LinearLayout(requireContext());
        risks.setOrientation(LinearLayout.VERTICAL);
        for (String risk : payload.risks) {
            TextView row = textView("•  " + risk, 12, 0xFF64748B, false);
            row.setLineSpacing(dp(2), 1f);
            row.setPadding(0, dp(3), 0, dp(3));
            risks.addView(row);
        }
        card.addView(risks);

        String source = latestQuote == null || TextUtils.isEmpty(latestQuote.quoteSource)
                ? "行情来源暂不可用" : latestQuote.quoteSource;
        TextView footer = textView(source + " · 链上市场快照 · 仅供投研参考",
                11, 0xFF8492A6, false);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerParams.setMargins(0, dp(11), 0, 0);
        card.addView(footer, footerParams);
        scrollToBottom();
    }

    private void addQuoteMetricPanel(LinearLayout parent, MarketOverviewPayload payload) {
        LinearLayout metrics = new LinearLayout(requireContext());
        metrics.setOrientation(LinearLayout.HORIZONTAL);
        metrics.setPadding(dp(12), dp(10), dp(12), dp(10));
        metrics.setBackground(panelBackground(0xFFF8FAFC, 0xFFDCE5EF));

        LinearLayout price = new LinearLayout(requireContext());
        price.setOrientation(LinearLayout.VERTICAL);
        price.addView(textView("XAU/USD", 11, 0xFF94A3B8, true));
        String priceText = latestQuote == null || latestQuote.priceUsd <= 0
                ? "--" : String.format(Locale.getDefault(), "$%,.2f", latestQuote.priceUsd);
        TextView priceValue = textView(priceText, 18, 0xFF0F172A, true);
        price.addView(priceValue);
        metrics.addView(price, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout change = new LinearLayout(requireContext());
        change.setOrientation(LinearLayout.VERTICAL);
        change.setGravity(Gravity.END);
        change.addView(textView("24小时", 11, 0xFF94A3B8, true));
        String changeText = latestQuote == null || !latestQuote.changeAvailable
                ? "--" : String.format(Locale.getDefault(), "%+.2f%%", latestQuote.change24h);
        int changeColor = latestQuote == null || !latestQuote.changeAvailable
                ? 0xFF64748B : latestQuote.change24h >= 0 ? 0xFF047857 : 0xFFBE123C;
        TextView changeValue = textView(changeText, 15, changeColor, true);
        changeValue.setGravity(Gravity.END);
        change.addView(changeValue);
        metrics.addView(change, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        int stanceColor = "偏多".equals(payload.stance) ? 0xFF047857
                : "偏空".equals(payload.stance) ? 0xFFBE123C : 0xFF64748B;
        TextView stance = textView(payload.stance, 12, stanceColor, true);
        stance.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stanceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stanceParams.gravity = Gravity.CENTER_VERTICAL;
        stanceParams.setMargins(dp(12), 0, 0, 0);
        metrics.addView(stance, stanceParams);
        parent.addView(metrics);
    }

    private void addOverviewSummaryPanel(LinearLayout parent, MarketOverviewPayload payload) {
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(11), 0, 0);
        parent.addView(panel, params);
        panel.addView(textView("行情判断", 11, 0xFF7C889B, true));
        TextView summary = textView(payload.summary, 13, 0xFF334155, false);
        summary.setLineSpacing(dp(2), 1.03f);
        summary.setPadding(0, dp(4), 0, 0);
        panel.addView(summary);
    }

    private void addOverviewMarketComponents(LinearLayout parent, MarketOverviewPayload payload) {
        List<GoldMarketRepository.GameModel> rendered = new ArrayList<>();
        for (MarketOverviewInsight insight : payload.markets) {
            GoldMarketRepository.GameModel game = findGameReference(insight.title, -1);
            if (game == null || rendered.contains(game) || game.isResolved || game.isRefunded) continue;
            addOverviewMarketCard(parent, game);
            rendered.add(game);
        }
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (rendered.size() >= 4) break;
            if (game == null || rendered.contains(game) || game.isResolved || game.isRefunded) continue;
            addOverviewMarketCard(parent, game);
            rendered.add(game);
        }
        if (rendered.isEmpty()) {
            TextView empty = textView("当前没有可展示的活跃博弈池。", 13, 0xFF64748B, false);
            empty.setPadding(dp(12), dp(12), dp(12), dp(12));
            empty.setBackground(panelBackground(0xFFF8FAFC, 0xFFE2E8F0));
            parent.addView(empty);
        }
    }

    private void addOverviewMarketCard(LinearLayout parent,
                                       GoldMarketRepository.GameModel game) {
        LinearLayout market = new LinearLayout(requireContext());
        market.setOrientation(LinearLayout.HORIZONTAL);
        market.setGravity(Gravity.CENTER_VERTICAL);
        market.setPadding(dp(13), 0, dp(13), 0);
        market.setBackground(panelBackground(0xFFF8FAFC, 0xFFDCE5EF));
        LinearLayout.LayoutParams marketParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        marketParams.setMargins(0, 0, 0, dp(7));
        parent.addView(market, marketParams);

        TextView marketTitle = textView("", 14, 0xFF0F172A, true);
        marketTitle.setText(GoldMarketTextStyler.style(displayGameTitle(game), true));
        marketTitle.setMaxLines(1);
        marketTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams marketTitleParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        marketTitleParams.setMargins(0, 0, dp(8), 0);
        market.addView(marketTitle, marketTitleParams);
        TextView open = textView("›", 18, 0xFF64748B, true);
        market.addView(open);
        market.setOnClickListener(v -> openMarket(game));
    }

    private void openMarket(GoldMarketRepository.GameModel game) {
        Intent page = new Intent(requireContext(), GoldMarketDetailActivity.class);
        page.putExtra("GAME_ID", game.id);
        if (!TextUtils.isEmpty(game.contractAddress)) {
            page.putExtra("CONTRACT_ADDRESS", game.contractAddress);
        }
        startActivity(page);
    }

    private CardAction openMarketAction(GoldMarketRepository.GameModel game, String label) {
        return new CardAction(label, v -> {
            Intent page = new Intent(requireContext(), GoldMarketDetailActivity.class);
            page.putExtra("GAME_ID", game.id);
            if (!TextUtils.isEmpty(game.contractAddress)) page.putExtra("CONTRACT_ADDRESS", game.contractAddress);
            startActivity(page);
        });
    }

    private GoldMarketRepository.GameModel findGame(int id) {
        if (id <= 0) return null;
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (game != null && game.id == id) return game;
        }
        return null;
    }

    private GoldMarketRepository.GameModel findGameReference(String text, int id) {
        GoldMarketRepository.GameModel byId = findGame(id);
        if (byId != null) return byId;
        String normalizedText = normalizeMarketReference(text);
        if (normalizedText.isEmpty()) return null;
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (game == null) continue;
            String title = normalizeMarketReference(displayGameTitle(game));
            if (!title.isEmpty() && normalizedText.contains(title)) return game;
        }
        return null;
    }

    private String normalizeMarketReference(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s`\"'“”‘’《》【】（）()]+", "");
    }

    private String displayGameTitle(GoldMarketRepository.GameModel game) {
        if (game == null) return "当前博弈池";
        if (!TextUtils.isEmpty(game.desc)) return game.desc.trim();
        if (!TextUtils.isEmpty(game.condition)) return game.condition.trim();
        return "未命名博弈池";
    }

    private String compactMarketTitle(GoldMarketRepository.GameModel game, int maxChars) {
        String title = displayGameTitle(game);
        return title.length() <= maxChars ? title : title.substring(0, maxChars) + "…";
    }

    private void loadAiAdvice() {
        if (!DeepSeekClient.isConfigured()) {
            tvAiSummary.setText("配置 API 密钥后获取 AI 市场速览");
            return;
        }
        tvAiSummary.setText("正在生成 AI 市场速览…");
        GoldAdvisoryManager.fetch(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override public void onSuccess(GoldAdvisoryManager.Advisory advisory) { updateAiAdviceUI(advisory); }
            @Override public void onError(String error) {
                if (!destroyed && isAdded()) {
                    tvAiSummary.setText("加载失败 · 点击重试");
                }
            }
        });
    }

    private void updateAiAdviceUI(GoldAdvisoryManager.Advisory advisory) {
        if (destroyed || !isAdded() || advisory == null) return;
        latestSpotAdvisory = advisory;
        tvAiSignal.setText(displaySignal(advisory.signal));
        tvAiSummary.setText(advisory.summary);
        int color = "BUY".equals(advisory.signal) ? 0xFF047857
                : "SELL".equals(advisory.signal) ? Color.RED : 0xFF0F172A;
        tvAiSignal.setTextColor(color);
        int signalFill = "BUY".equals(advisory.signal) ? 0xFFECFDF5
                : "SELL".equals(advisory.signal) ? 0xFFFFF1F2 : 0xFFF1F5F9;
        int signalStroke = "BUY".equals(advisory.signal) ? 0xFFA7F3D0
                : "SELL".equals(advisory.signal) ? 0xFFFECDD3 : 0xFFE2E8F0;
        tvAiSignal.setBackground(panelBackground(signalFill, signalStroke));
    }

    @Override public void onDestroyView() {
        destroyed = true;
        super.onDestroyView();
    }

    private int addMessage(String sender, String text) {
        if (destroyed) return -1;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMessageNow(sender, safeText(text)));
            return -1;
        }
        return addMessageNow(sender, safeText(text));
    }

    private int addMessageNow(String sender, String text) {
        if ("你".equals(sender)) latestUserText = text;
        boolean aiMessage = "AI".equals(sender);
        LinearLayout bubble = new LinearLayout(requireContext());
        bubble.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        bubble.setLayoutParams(params);
        if (aiMessage) {
            TextView senderView = new TextView(requireContext());
            senderView.setText("AI 智能体");
            senderView.setTextSize(11);
            senderView.setTextColor(0xFF2563EB);
            senderView.setPadding(dp(2), 0, dp(2), dp(5));
            bubble.addView(senderView);
        }
        TextView textView = new TextView(requireContext());
        textView.setTextSize(15);
        textView.setTextColor(aiMessage ? 0xFF0F172A : Color.WHITE);
        textView.setLineSpacing(dp(3), 1f);
        textView.setPadding(dp(16), dp(12), dp(16), dp(12));
        textView.setBackground(aiMessage
                ? panelBackground(0xFFF4F7FB, 0xFFDCE5F0)
                : panelBackground(0xFF172033, 0xFF172033));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                aiMessage ? LinearLayout.LayoutParams.MATCH_PARENT
                        : LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (!aiMessage) {
            textView.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * .82f));
            textParams.gravity = Gravity.END;
        }
        if (aiMessage && markwon != null) markwon.setMarkdown(textView, text); else textView.setText(text);
        bubble.addView(textView, textParams);
        bubble.setGravity(aiMessage ? Gravity.START : Gravity.END);
        messageContainer.addView(bubble);
        int index = messageContainer.getChildCount() - 1;
        scrollToBottom();
        return index;
    }

    private void addAgentCard(String eyebrow, String title, String body, List<CardAction> actions) {
        if (destroyed || !isAdded()) return;
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(panelBackground(0xFFFFFFFF, 0xFFDCE5EF));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        messageContainer.addView(card, params);
        TextView tag = textView(eyebrow.toUpperCase(Locale.ROOT), 12, 0xFF2563EB, true);
        tag.setPadding(dp(8), dp(4), dp(8), dp(4));
        tag.setBackground(panelBackground(0xFFEFF6FF, 0xFFBFDBFE));
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tagParams.bottomMargin = dp(7);
        card.addView(tag, tagParams);
        TextView heading = textView(title, 17, 0xFF0F172A, true);
        heading.setPadding(0, 0, 0, dp(7));
        card.addView(heading);
        TextView content = textView(body, 14, 0xFF475569, false);
        content.setLineSpacing(dp(2), 1f);
        card.addView(content);
        if (actions != null && !actions.isEmpty()) {
            boolean stacked = actions.size() > 1;
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(12), 0, 0);
            card.addView(row);
            for (CardAction action : actions) {
                Button button = new Button(requireContext());
                button.setText(action.label);
                button.setTextSize(13);
                button.setAllCaps(false);
                button.setTextColor(action.outline ? 0xFF334155 : 0xFFFFFFFF);
                button.setGravity(action.outline
                        ? Gravity.CENTER_VERTICAL | Gravity.START : Gravity.CENTER);
                button.setPadding(dp(14), 0, dp(14), 0);
                button.setStateListAnimator(null);
                button.setBackground(action.outline
                        ? panelBackground(0xFFF8FAFC, 0xFFDCE5EF)
                        : panelBackground(0xFF2563EB, 0xFF2563EB));
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
                buttonParams.setMargins(0, 0, 0, stacked ? dp(7) : 0);
                row.addView(button, buttonParams);
                button.setOnClickListener(action.listener);
            }
        }
        scrollToBottom();
    }

    private TextView textView(String text, int size, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private GradientDrawable panelBackground(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void updateMessageAt(int index, String text) {
        if (index < 0 || index >= messageContainer.getChildCount()) return;
        View child = messageContainer.getChildAt(index);
        if (!(child instanceof LinearLayout)) return;
        LinearLayout bubble = (LinearLayout) child;
        if (bubble.getChildCount() < 2 || !(bubble.getChildAt(1) instanceof TextView)) return;
        TextView value = (TextView) bubble.getChildAt(1);
        if (markwon != null) markwon.setMarkdown(value, text); else value.setText(text);
        scrollToBottom();
    }

    private boolean ensureIdle() {
        if (!requestInFlight) return true;
        addMessage("AI", "上一次请求仍在处理中，请稍候");
        return false;
    }

    private int beginLoading(String text) {
        setRequestInFlight(true);
        return addMessage("AI", text);
    }

    private void finishLoading(int index, String text) {
        if (destroyed || !isAdded()) return;
        mainHandler.post(() -> {
            if (destroyed || !isAdded()) return;
            updateMessageAt(index, safeText(text));
            setRequestInFlight(false);
        });
    }

    private void finishLoadingAndRemove(int index) {
        if (destroyed || !isAdded()) return;
        Runnable finish = () -> {
            if (destroyed || !isAdded()) return;
            if (index >= 0 && index < messageContainer.getChildCount()) {
                messageContainer.removeViewAt(index);
            }
            setRequestInFlight(false);
            scrollToBottom();
        };
        if (Looper.myLooper() == Looper.getMainLooper()) finish.run();
        else mainHandler.post(finish);
    }

    private void setRequestInFlight(boolean inFlight) {
        requestInFlight = inFlight;
        sendBtn.setEnabled(!inFlight);
        sendBtn.setAlpha(inFlight ? .45f : 1f);
        inputField.setEnabled(!inFlight);
    }

    private void scrollToBottom() { messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String safeText(String text) { return TextUtils.isEmpty(text) ? "AI 未返回内容，请稍后重试" : text; }
    private boolean containsNo(String text) { String lower = text == null ? "" : text.toLowerCase(Locale.ROOT); return lower.contains("买 no") || lower.contains("买no") || lower.contains("买入 no") || lower.contains("买入no") || lower.contains("卖 no") || lower.contains("卖no") || lower.contains("卖出 no") || lower.contains("卖出no") || lower.contains(" no") || lower.contains("买入否") || lower.contains("买否") || lower.contains("选择否"); }
    private boolean containsSell(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return lower.contains("卖出") || lower.contains("减持")
                || lower.contains("sell") || lower.contains("卖 yes")
                || lower.contains("卖 no") || lower.contains("卖yes") || lower.contains("卖no");
    }
    private String trimPercent(double value) { return Math.abs(value - Math.rint(value)) < .0001 ? String.valueOf((int) value) : String.format(Locale.US, "%.1f", value); }

    private String formatAiError(String error) {
        if (TextUtils.isEmpty(error)) return "AI 请求失败且未返回错误详情，请检查网络后重试";
        String lower = error.toLowerCase(Locale.ROOT);
        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid api key")) return "AI 请求失败：DeepSeek API 密钥无效或已过期";
        if (lower.contains("timeout") || lower.contains("timed out")) return "AI 请求超时，请检查网络或稍后重试";
        if (lower.contains("not configured") || lower.contains("no_api_key")) return "请先配置 DeepSeek API 密钥";
        return "AI 请求失败：" + (error.length() > 300 ? error.substring(0, 300) + "…" : error);
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("配置 DeepSeek API 密钥");
        EditText input = new EditText(requireContext());
        input.setHint("请输入 API 密钥");
        input.setText(DeepSeekClient.getApiKey());
        builder.setView(input);
        builder.setPositiveButton("保存", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if (!key.isEmpty()) { DeepSeekClient.setApiKey(key); loadAiAdvice(); }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private String displaySignal(String signal) {
        if ("BUY".equalsIgnoreCase(signal)) return "偏多";
        if ("SELL".equalsIgnoreCase(signal)) return "偏空";
        return "观望";
    }

    private static final class CardAction {
        final String label;
        final View.OnClickListener listener;
        final boolean outline;
        CardAction(String label, View.OnClickListener listener) {
            this(label, listener, false);
        }
        CardAction(String label, View.OnClickListener listener, boolean outline) {
            this.label = label;
            this.listener = listener;
            this.outline = outline;
        }
    }

    private static final class BigDecimalSummary {
        final String before;
        final String after;
        final String impact;
        private BigDecimalSummary(String before, String after, String impact) { this.before = before; this.after = after; this.impact = impact; }
        static BigDecimalSummary of(GoldTradeSimulation.Result result) {
            double delta = result.afterYesProbability.subtract(result.beforeYesProbability).doubleValue();
            return new BigDecimalSummary(result.beforeYesProbability.toPlainString() + "%",
                    result.afterYesProbability.toPlainString() + "%",
                    String.format(Locale.US, "%+.2f 个百分点", delta));
        }
    }

    private static final class DirectionJudgmentPayload {
        String summary = "";
        final List<DirectionJudgmentInsight> markets = new ArrayList<>();
    }

    private static final class DirectionJudgmentInsight {
        final GoldMarketRepository.GameModel game;
        final String direction;
        final String reason;
        final String risk;

        DirectionJudgmentInsight(GoldMarketRepository.GameModel game, String direction,
                                 String reason, String risk) {
            this.game = game;
            this.direction = direction;
            this.reason = reason;
            this.risk = risk;
        }
    }

    private static final class MarketOverviewPayload {
        String stance = "";
        String summary = "";
        final List<MarketOverviewInsight> markets = new ArrayList<>();
        final List<String> risks = new ArrayList<>();
    }

    private static final class MarketOverviewInsight {
        final String title;

        MarketOverviewInsight(String title) {
            this.title = title;
        }
    }

    private static final class PositionDiagnosisPayload {
        String riskLevel = "";
        String summary = "";
        final List<String> findings = new ArrayList<>();
    }
}
