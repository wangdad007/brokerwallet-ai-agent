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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
import com.example.brokerfi.xc.agent.gold.model.logic.GoldTradeSimulation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.noties.markwon.Markwon;

/**
 * Conversational research workbench for the gold market.
 *
 * <p>It intentionally separates language understanding from privileged actions:
 * the model can explain a market, while a closed local router may only offer
 * simulation, draft creation, or the existing user-confirmed product flows.</p>
 */
public class AIChatFragment extends Fragment {
    private LinearLayout messageContainer;
    private LinearLayout quickActionContainer;
    private ScrollView messageScroll;
    private EditText inputField;
    private ImageView sendBtn;
    private ImageView btnConfig;
    private TextView tvAiSignal;
    private TextView tvAiSummary;
    private TextView tvAiMeta;
    private LinearLayout cardAiAdvice;
    private Markwon markwon;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;
    private boolean requestInFlight = false;
    private String marketContext = "";
    private GoldMarketRepository marketRepository;
    private List<GoldMarketRepository.GameModel> latestGames = Collections.emptyList();
    private GoldAdvisoryManager.Advisory latestQuote;

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
        markwon = Markwon.create(requireContext());
        showWelcomeMessage();
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
        tvAiMeta = view.findViewById(R.id.tv_ai_meta);
        cardAiAdvice = view.findViewById(R.id.card_ai_advice);
        sendBtn.setOnClickListener(v -> onSendMessage());
        btnConfig.setOnClickListener(v -> showApiKeyDialog());
        cardAiAdvice.setOnClickListener(v -> submitQuestion("请给出当前黄金市场评估，包括趋势、博弈池概率和主要风险"));
    }

    private void bindQuickActions() {
        addQuickAction("市场速览", "请给出当前黄金市场评估，包括趋势、博弈池概率和主要风险");
        addQuickAction("比较博弈池", "请比较当前活跃博弈池的隐含概率、流动性和风险");
        addQuickAction("诊断持仓", "请诊断我的当前持仓、集中度和主要风险");
        addQuickAction("交易模拟", "帮我模拟买入 1 号池 YES 1 BKC");
        addQuickAction("创建草稿", "创建一个未来两天黄金是否上涨的博弈池");
        addQuickAction("托管策略", "为 1 号池配置保守的 AI 自动托管策略");
    }

    private void addQuickAction(String label, String prompt) {
        Button chip = new Button(requireContext());
        chip.setText(label);
        chip.setTextSize(13);
        chip.setAllCaps(false);
        chip.setTextColor(0xFF334155);
        chip.setPadding(dp(12), 0, dp(12), 0);
        chip.setBackground(chipBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
        params.setMargins(0, 0, dp(8), 0);
        quickActionContainer.addView(chip, params);
        chip.setOnClickListener(v -> submitQuestion(prompt));
    }

    private GradientDrawable chipBackground() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF8FAFC);
        background.setCornerRadius(dp(19));
        background.setStroke(dp(1), 0xFFE2E8F0);
        return background;
    }

    private void showWelcomeMessage() {
        addAgentCard("已就绪", "你好，我是黄金市场智能体",
                "我可以完成市场分析、博弈池对比、持仓诊断，并准备交易模拟、创建市场和 AI 托管策略草稿。\n\n"
                        + "涉及资金与策略启用时，我只会生成预览；你仍需在正式页面确认。请不要输入私钥或助记词。",
                Collections.emptyList());
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
        GoldAgentIntentRouter.Result intent = GoldAgentIntentRouter.route(text);

        if (intent.intent == GoldAgentIntentRouter.Intent.CREATE_MARKET_DRAFT) {
            showCreateDraft(text);
            return;
        }
        if (requiresAi(intent.intent) && !DeepSeekClient.isConfigured()) {
            addAgentCard("需要配置", "请先配置 DeepSeek API 密钥",
                    "市场分析、对比和持仓诊断会调用 AI。交易模拟、创建草稿和策略草稿不需要把资金操作交给 AI。",
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
            case TRADE_SIMULATION:
                showTradeSimulation(intent, loadingIndex);
                return;
            case AI_MANAGED_STRATEGY:
                showStrategyDraft(question, intent, loadingIndex);
                return;
            case MARKET_COMPARE:
                askWithCurrentContext("请以表格比较当前活跃博弈池：市场隐含概率、流动性、截止时间、"
                        + "与当前黄金行情的关联和主要风险。不要保证收益。\n用户需求：" + question,
                        loadingIndex, intent.displayName);
                return;
            case POSITION_DIAGNOSIS:
                askWithCurrentContext("请只基于快照诊断我的持仓：分别说明已有份额、估值不确定性、"
                        + "集中度和时间风险；数据缺失时明确指出。不要建议自动下单或保证收益。\n用户需求：" + question,
                        loadingIndex, intent.displayName);
                return;
            case MARKET_ANALYSIS:
                askWithCurrentContext("请综合实时黄金行情与活跃博弈池，回答趋势、市场隐含概率、"
                        + "主要分歧与风险。每项结论注明它来自行情、链上快照还是推断。\n用户需求：" + question,
                        loadingIndex, intent.displayName);
                return;
            default:
                askWithCurrentContext(question, loadingIndex, intent.displayName);
        }
    }

    private void askWithCurrentContext(String question, int loadingIndex, String taskName) {
        String prompt = GoldMarketResearchPromptBuilder.withFollowUp(marketContext, question);
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

    private void finishAnalysis(int loadingIndex, String answer, String taskName) {
        finishLoading(loadingIndex, answer);
        mainHandler.post(() -> addEvidenceCard(taskName));
    }

    private void showTradeSimulation(GoldAgentIntentRouter.Result intent, int loadingIndex) {
        GoldMarketRepository.GameModel game = findGame(intent.gameId);
        if (intent.gameId <= 0) {
            finishLoading(loadingIndex, "已识别为交易模拟，但尚未识别博弈池编号。");
            addAgentCard("需要补充", "请指定博弈池和方向",
                    "例如：`帮我模拟买入 1 号池 YES 1 BKC`。模拟只读取当前 AMM 储备，不会提交链上交易。",
                    marketButtons());
            return;
        }
        if (game == null) {
            finishLoading(loadingIndex, "未在当前链上快照中找到 #" + intent.gameId + " 博弈池。");
            addAgentCard("无法模拟", "请检查博弈池编号", "当前快照中没有该博弈池，或数据尚未同步。",
                    marketButtons());
            return;
        }
        if (intent.amountBkc == null) {
            finishLoading(loadingIndex, "已定位 #" + game.id + "，但缺少交易金额。");
            addAgentCard("需要补充", "请输入模拟金额",
                    "例如：`模拟买入 " + game.id + " 号池 YES 1 BKC`。不会直接买入。",
                    Collections.singletonList(openMarketAction(game, "查看博弈池")));
            return;
        }
        BigInteger amount = GoldMarketRepository.parseTokenAmountToWei(
                String.format(Locale.US, "%.8f", intent.amountBkc));
        int option = containsNo(inputOrEmpty()) ? 1 : 0;
        GoldTradeSimulation.Result simulation = GoldTradeSimulation.simulate(game, option, amount);
        if (!simulation.valid) {
            finishLoading(loadingIndex, "交易模拟不可用：" + simulation.error);
            return;
        }
        finishLoading(loadingIndex, "已完成 #" + game.id + " 的链上 AMM 报价模拟，未发起交易。");
        BigDecimalSummary summary = BigDecimalSummary.of(simulation);
        String title = displayGameTitle(game);
        String body = "方向：买入 " + (option == 0 ? "YES" : "NO") + "\n"
                + "投入：" + GoldTradeSimulation.formatBkc(simulation.amountWei, 4) + " BKC\n"
                + "预计获得：" + GoldTradeSimulation.formatBkc(simulation.sharesOutWei, 6) + " 份额\n"
                + "YES 隐含概率：" + summary.before + " → " + summary.after + "（变动 " + summary.impact + "）\n"
                + "当前池深：" + GoldTradeSimulation.formatBkc(simulation.depthWei, 2) + " BKC\n\n"
                + "该结果依据当前虚拟储备和合约整数公式计算；真实成交仍会受区块确认前的其他交易影响。";
        addAgentCard("仅模拟 · 未执行", title, body,
                Collections.singletonList(openMarketAction(game, "前往交易确认")));
    }

    private String inputOrEmpty() {
        // The latest user message is visible in the conversation; this helper is intentionally
        // conservative: absent an explicit NO, a requested buy defaults to YES for simulation.
        return latestUserText;
    }

    private String latestUserText = "";

    private void showCreateDraft(String text) {
        latestUserText = text;
        addAgentCard("创建草稿", "已识别自然语言建池需求",
                "我会把你的原始描述带到现有的创建页，由严格规则解析器生成可编辑配置。\n\n"
                        + "在你核对结算条件、持续时间和初始流动性并确认前，不会创建博弈池。",
                Collections.singletonList(new CardAction("去创建草稿", v -> {
                    if (getActivity() instanceof GoldNoteMarketActivity) {
                        ((GoldNoteMarketActivity) getActivity()).openCreateDraft(text);
                    }
                })));
    }

    private void showStrategyDraft(String text, GoldAgentIntentRouter.Result intent, int loadingIndex) {
        GoldMarketRepository.GameModel game = findGame(intent.gameId);
        if (intent.gameId <= 0 || game == null) {
            finishLoading(loadingIndex, "已识别为 AI 自动托管策略，但尚未定位有效博弈池。");
            addAgentCard("需要补充", "请指定博弈池编号",
                    "例如：`为 1 号池配置保守的 AI 自动托管策略，每单 0.5 BKC`。",
                    marketButtons());
            return;
        }
        BackendApiClient.AiManagedConfig config = draftStrategy(text, intent.amountBkc);
        finishLoading(loadingIndex, "已为 #" + game.id + " 准备策略草稿，尚未启用自动托管。");
        String body = "单笔基础金额：" + config.buyAmountBKC + " BKC\n"
                + "最低置信度：" + Math.round(config.confidenceMin * 100) + "%\n"
                + "最小概率优势：" + trimPercent(config.minEdgePercent) + "%\n"
                + "Kelly 缩放系数：" + Math.round(config.kellyFraction * 100) + "%\n"
                + "冷却机制：" + (config.adaptiveCooldown ? "开启" : "关闭") + "\n\n"
                + "这只是参数建议。进入设置页后仍可修改，保存后才会启用 AI 自动托管。";
        addAgentCard("待用户确认", "AI 自动托管策略草稿 · " + displayGameTitle(game), body,
                Collections.singletonList(new CardAction("查看并确认策略", v -> {
                    String contract = TextUtils.isEmpty(game.contractAddress) && marketRepository != null
                            ? marketRepository.getBoundContractAddress() : game.contractAddress;
                    Intent page = GoldAiManagedSettingsActivity.createIntent(requireContext(), game.id,
                            contract, displayGameTitle(game), config);
                    startActivity(page);
                })));
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
        String body = "任务：" + taskName + "\n"
                + "已读取：" + source + " 黄金行情、" + latestGames.size() + " 个链上博弈池快照\n"
                + "快照时间：" + updated + "\n"
                + "风险提示：市场概率不是事件真实概率；AI 输出仅供投研参考，不构成收益承诺。";
        addAgentCard("数据与边界", "本次回答的依据", body, Collections.emptyList());
    }

    private List<CardAction> marketButtons() {
        List<CardAction> actions = new ArrayList<>();
        for (GoldMarketRepository.GameModel game : latestGames) {
            if (game != null && !game.isResolved && !game.isRefunded) {
                actions.add(openMarketAction(game, "查看 #" + game.id));
                if (actions.size() == 3) break;
            }
        }
        return actions;
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

    private String displayGameTitle(GoldMarketRepository.GameModel game) {
        if (game == null) return "当前博弈池";
        if (!TextUtils.isEmpty(game.desc)) return game.desc.trim();
        if (!TextUtils.isEmpty(game.condition)) return game.condition.trim();
        return "博弈池 #" + game.id;
    }

    private void loadAiAdvice() {
        if (!DeepSeekClient.isConfigured()) {
            tvAiSummary.setText("配置 API 密钥后获取 AI 市场速览");
            tvAiMeta.setText("可先使用交易模拟、创建草稿和策略草稿");
            return;
        }
        tvAiSummary.setText("正在生成 AI 市场速览…");
        GoldAdvisoryManager.fetch(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override public void onSuccess(GoldAdvisoryManager.Advisory advisory) { updateAiAdviceUI(advisory); }
            @Override public void onError(String error) {
                if (!destroyed && isAdded()) {
                    tvAiSummary.setText("加载失败 · 点击重试");
                    tvAiMeta.setText("AI 市场速览暂不可用");
                }
            }
        });
    }

    private void updateAiAdviceUI(GoldAdvisoryManager.Advisory advisory) {
        if (destroyed || !isAdded() || advisory == null) return;
        tvAiSignal.setText(displaySignal(advisory.signal));
        tvAiSummary.setText(advisory.summary);
        tvAiMeta.setText("置信度 " + advisory.confidence + "% · "
                + (TextUtils.isEmpty(advisory.quoteSource) ? "行情来源待确认" : advisory.quoteSource)
                + " · 仅供投研参考");
        int color = "BUY".equals(advisory.signal) ? 0xFF047857
                : "SELL".equals(advisory.signal) ? Color.RED : 0xFF0F172A;
        tvAiSignal.setTextColor(color);
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
        LinearLayout bubble = new LinearLayout(requireContext());
        bubble.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(16));
        bubble.setLayoutParams(params);
        TextView senderView = new TextView(requireContext());
        senderView.setText(sender);
        senderView.setTextSize(12);
        senderView.setTextColor("AI".equals(sender) ? 0xFF2563EB : 0xFF475569);
        senderView.setPadding(0, 0, 0, dp(4));
        bubble.addView(senderView);
        TextView textView = new TextView(requireContext());
        textView.setTextSize(15);
        textView.setTextColor("AI".equals(sender) ? 0xFF0F172A : Color.WHITE);
        textView.setLineSpacing(dp(3), 1f);
        textView.setPadding(dp(16), dp(12), dp(16), dp(12));
        textView.setBackground("AI".equals(sender) ? panelBackground(0xFFF1F5F9, 0xFFE2E8F0) : panelBackground(0xFF111827, 0xFF111827));
        if ("AI".equals(sender) && markwon != null) markwon.setMarkdown(textView, text); else textView.setText(text);
        bubble.addView(textView);
        bubble.setGravity("AI".equals(sender) ? Gravity.START : Gravity.END);
        messageContainer.addView(bubble);
        int index = messageContainer.getChildCount() - 1;
        scrollToBottom();
        return index;
    }

    private void addAgentCard(String eyebrow, String title, String body, List<CardAction> actions) {
        if (destroyed || !isAdded()) return;
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(panelBackground(0xFFFFFFFF, 0xFFE2E8F0));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(16));
        messageContainer.addView(card, params);
        TextView tag = textView(eyebrow.toUpperCase(Locale.ROOT), 12, 0xFF2563EB, true);
        card.addView(tag);
        TextView heading = textView(title, 17, 0xFF0F172A, true);
        heading.setPadding(0, dp(4), 0, dp(6));
        card.addView(heading);
        TextView content = textView(body, 14, 0xFF475569, false);
        content.setLineSpacing(dp(3), 1f);
        card.addView(content);
        if (actions != null && !actions.isEmpty()) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(12), 0, 0);
            card.addView(row);
            for (CardAction action : actions) {
                Button button = new Button(requireContext());
                button.setText(action.label);
                button.setTextSize(13);
                button.setAllCaps(false);
                button.setTextColor(0xFFFFFFFF);
                button.setBackground(panelBackground(0xFF111827, 0xFF111827));
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
                buttonParams.setMargins(0, 0, dp(8), 0);
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

    private void setRequestInFlight(boolean inFlight) {
        requestInFlight = inFlight;
        sendBtn.setEnabled(!inFlight);
        sendBtn.setAlpha(inFlight ? .45f : 1f);
        inputField.setEnabled(!inFlight);
    }

    private void scrollToBottom() { messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String safeText(String text) { return TextUtils.isEmpty(text) ? "AI 未返回内容，请稍后重试" : text; }
    private boolean containsNo(String text) { String lower = text == null ? "" : text.toLowerCase(Locale.ROOT); return lower.contains("买 no") || lower.contains("买no") || lower.contains("买入 no") || lower.contains("买入no") || lower.contains(" no") || lower.contains("买入否") || lower.contains("买否") || lower.contains("选择否"); }
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
        if ("BUY".equalsIgnoreCase(signal)) return "买入";
        if ("SELL".equalsIgnoreCase(signal)) return "卖出";
        return "观望";
    }

    private static final class CardAction {
        final String label;
        final View.OnClickListener listener;
        CardAction(String label, View.OnClickListener listener) { this.label = label; this.listener = listener; }
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
}
