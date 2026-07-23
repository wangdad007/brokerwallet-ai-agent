package com.example.brokerfi.xc.agent.gold.view;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldAdvisoryManager;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketResearchPromptBuilder;

import java.util.Collections;
import java.util.List;

import io.noties.markwon.Markwon;

public class AIChatFragment extends Fragment {

    private LinearLayout messageContainer;
    private ScrollView messageScroll;
    private EditText inputField;
    private ImageView sendBtn;
    private ImageView btnConfig;
    private TextView tvAiSignal;
    private TextView tvAiSummary;
    private LinearLayout cardAiAdvice;
    private Markwon markwon;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;
    private boolean requestInFlight = false;
    private String marketContext = "";
    private GoldMarketRepository marketRepository;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
        loadAiAdvice();
    }

    private void initViews(View v) {
        messageContainer = v.findViewById(R.id.message_container);
        messageScroll = v.findViewById(R.id.message_scroll);
        inputField = v.findViewById(R.id.input_field);
        sendBtn = v.findViewById(R.id.send_btn);
        btnConfig = v.findViewById(R.id.btn_config);
        tvAiSignal = v.findViewById(R.id.tv_ai_signal);
        tvAiSummary = v.findViewById(R.id.tv_ai_summary);
        cardAiAdvice = v.findViewById(R.id.card_ai_advice);

        sendBtn.setOnClickListener(v1 -> onSendMessage());
        btnConfig.setOnClickListener(v1 -> showApiKeyDialog());
        cardAiAdvice.setOnClickListener(v1 -> onCardAiAdviceClick());
    }

    private void onCardAiAdviceClick() {
        if (!DeepSeekClient.isConfigured()) {
            showApiKeyDialog();
            return;
        }
        loadInitialGoldAdvice();
    }

    private void loadInitialGoldAdvice() {
        if (!ensureIdle()) return;
        String initialQuestion = "请给出当前黄金市场评估，包括趋势分析和主要风险";
        int loadingIndex = beginLoading("正在加载实时黄金行情与博弈池快照…");
        loadLiveContextAndAsk(initialQuestion, loadingIndex);
    }

    private void askWithCurrentContext(String question, int loadingIndex) {
        if (destroyed || !isAdded()) return;
        String questionForAi = GoldMarketResearchPromptBuilder.withFollowUp(
                marketContext, question);
        AgentManager.getInstance().askGoldResearch(questionForAi, new AgentManager.AnalysisCallback() {
            @Override
            public void onBrokerReport(AgentManager.BrokerReport report) {
                finishLoading(loadingIndex, report == null ? "" : report.rawAnalysis);
            }

            @Override
            public void onGeneralAdvice(String question, String answer) {
                finishLoading(loadingIndex, answer);
            }

            @Override
            public void onError(String error) {
                finishLoading(loadingIndex, formatAiError(error));
            }
        });
    }

    private void loadLiveContextAndAsk(String question, int loadingIndex) {
        GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                loadMarketsAndAsk(question, loadingIndex, quote, "");
            }

            @Override
            public void onError(String error) {
                loadMarketsAndAsk(question, loadingIndex, null,
                        "实时黄金行情加载失败：" + safeText(error));
            }
        });
    }

    private void loadMarketsAndAsk(
            String question,
            int loadingIndex,
            GoldAdvisoryManager.Advisory quote,
            String quoteWarning) {
        if (destroyed || !isAdded()) return;
        if (marketRepository == null) {
            marketContext = GoldMarketResearchPromptBuilder.buildMarketOverview(
                    Collections.emptyList(), System.currentTimeMillis(), quote);
            if (!TextUtils.isEmpty(quoteWarning)) marketContext += "\n" + quoteWarning;
            marketContext += "\n博弈池状态：钱包尚未初始化";
            askWithCurrentContext(question, loadingIndex);
            return;
        }
        marketRepository.getAllGamesInfo(
                new GoldMarketRepository.DataCallback<List<GoldMarketRepository.GameModel>>() {
                    @Override
                    public void onSuccess(List<GoldMarketRepository.GameModel> games) {
                        marketContext = GoldMarketResearchPromptBuilder.buildMarketOverview(
                                games, System.currentTimeMillis(), quote);
                        if (!TextUtils.isEmpty(quoteWarning)) {
                            marketContext += "\n" + quoteWarning;
                        }
                        askWithCurrentContext(question, loadingIndex);
                    }

                    @Override
                    public void onError(String error) {
                        marketContext = GoldMarketResearchPromptBuilder.buildMarketOverview(
                                Collections.emptyList(), System.currentTimeMillis(), quote);
                        if (!TextUtils.isEmpty(quoteWarning)) {
                            marketContext += "\n" + quoteWarning;
                        }
                        marketContext += "\n无法加载博弈池：" + safeText(error);
                        askWithCurrentContext(question, loadingIndex);
                    }
                });
    }

    private void showWelcomeMessage() {
        addMessage("AI", "你好，我是 BrokerChain 黄金 AI 投研助手\n\n" +
                "我会结合当前黄金行情、链上博弈池快照和你的问题提供投研分析\n" +
                "每次提问都会重新获取行情与博弈池快照\n" +
                "请勿输入私钥或助记词\n\n" +
                (DeepSeekClient.isConfigured() ?
                        "AI 投研服务已就绪，可以直接提问" :
                        "尚未配置 DeepSeek API 密钥"));
    }

    private void loadAiAdvice() {
        if (!DeepSeekClient.isConfigured()) {
            tvAiSummary.setText("点击配置 DeepSeek API 密钥");
            return;
        }
        tvAiSummary.setText("正在生成 AI 市场投研…");
        GoldAdvisoryManager.fetch(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory advisory) {
                updateAiAdviceUI(advisory);
            }

            @Override
            public void onError(String error) {
                if (!destroyed && isAdded()) {
                    tvAiSummary.setText("加载失败 · 点击重试");
                }
            }
        });
    }

    private void updateAiAdviceUI(GoldAdvisoryManager.Advisory advisory) {
        if (destroyed || !isAdded() || advisory == null) return;
        tvAiSignal.setText(displaySignal(advisory.signal));
        tvAiSummary.setText(advisory.summary);
        int color = advisory.signal.equals("BUY") ? Color.parseColor("#047857") : (advisory.signal.equals("SELL") ? Color.RED : Color.BLACK);
        tvAiSignal.setTextColor(color);
    }

    private void onSendMessage() {
        String text = inputField.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        inputField.setText("");
        submitQuestion(text);
    }

    private void submitQuestion(String text) {
        if (!ensureIdle()) {
            return;
        }
        addMessage("你", text);
        if (!DeepSeekClient.isConfigured()) {
            addMessage("AI", "请先配置 DeepSeek API 密钥");
            return;
        }

        int loadingIndex = beginLoading("正在加载实时黄金行情与博弈池快照…");
        loadLiveContextAndAsk(text, loadingIndex);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        destroyed = true;
    }

    private int addMessage(String sender, String text) {
        if (destroyed) return -1;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return addMessageNow(sender, safeText(text));
        }
        mainHandler.post(() -> {
            if (!destroyed && isAdded()) {
                addMessageNow(sender, safeText(text));
            }
        });
        return -1;
    }

    private int addMessageNow(String sender, String text) {
        LinearLayout bubble = new LinearLayout(requireContext());
        bubble.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 24);
        bubble.setLayoutParams(params);

        TextView senderView = new TextView(requireContext());
        senderView.setText(sender);
        senderView.setTextSize(12);
        senderView.setTextColor(sender.equals("AI") ? 0xFF4A90D9 : 0xFF333333);
        senderView.setPadding(0, 0, 0, 4);
        bubble.addView(senderView);

        TextView textView = new TextView(requireContext());
        textView.setTextSize(15);
        textView.setTextColor(sender.equals("AI") ? 0xFF1A1A1A : 0xFFFFFFFF);
        textView.setLineSpacing(4, 1);
        textView.setPadding(24, 16, 24, 16);
        textView.setBackgroundResource(sender.equals("AI")
                ? R.drawable.custom_light_grey_background
                : R.drawable.custom_green_background);

        // 使用 Markwon 渲染 Markdown 文本
        if (sender.equals("AI") && markwon != null) {
            markwon.setMarkdown(textView, text);
        } else {
            textView.setText(text);
        }

        bubble.addView(textView);
        bubble.setGravity(sender.equals("AI") ? Gravity.START : Gravity.END);

        messageContainer.addView(bubble);
        int index = messageContainer.getChildCount() - 1;
        messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
        return index;
    }

    private void updateMessageAt(int index, String text) {
        if (index >= 0 && index < messageContainer.getChildCount()) {
            View child = messageContainer.getChildAt(index);
            if (child instanceof LinearLayout) {
                LinearLayout bubble = (LinearLayout) child;
                if (bubble.getChildCount() >= 2 && bubble.getChildAt(1) instanceof TextView) {
                    TextView textView = (TextView) bubble.getChildAt(1);
                    // 使用 Markwon 渲染 Markdown 文本
                    if (markwon != null) {
                        markwon.setMarkdown(textView, text);
                    } else {
                        textView.setText(text);
                    }
                    messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
                }
            }
        }
    }

    private boolean ensureIdle() {
        if (requestInFlight) {
            addMessage("AI", "上一次请求仍在处理中，请稍候");
            return false;
        }
        return true;
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
        sendBtn.setAlpha(inFlight ? 0.45f : 1f);
        inputField.setEnabled(!inFlight);
    }

    private String safeText(String text) {
        if (TextUtils.isEmpty(text)) {
            return "AI 未返回内容，请稍后重试";
        }
        return text;
    }

    private String formatAiError(String error) {
        if (TextUtils.isEmpty(error)) {
            return "AI 请求失败且未返回错误详情，请检查网络后重试";
        }
        String lower = error.toLowerCase();
        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid api key")) {
            return "AI 请求失败：DeepSeek API 密钥无效或已过期";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "AI 请求超时，请检查网络或稍后重试";
        }
        if (lower.contains("not configured") || lower.contains("no_api_key")) {
            return "请先配置 DeepSeek API 密钥";
        }
        if (error.length() > 300) {
            error = error.substring(0, 300) + "...";
        }
        return "AI 请求失败：" + error;
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("配置 DeepSeek API 密钥");
        final EditText input = new EditText(requireContext());
        input.setHint("请输入 API 密钥");
        input.setText(DeepSeekClient.getApiKey());
        builder.setView(input);
        builder.setPositiveButton("保存", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if (!key.isEmpty()) {
                DeepSeekClient.setApiKey(key);
                loadAiAdvice();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private String displaySignal(String signal) {
        if ("BUY".equalsIgnoreCase(signal)) return "买入";
        if ("SELL".equalsIgnoreCase(signal)) return "卖出";
        return "观望";
    }
}
