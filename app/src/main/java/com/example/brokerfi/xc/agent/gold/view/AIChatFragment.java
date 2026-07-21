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
        String initialQuestion = "Provide a current gold market assessment, including trend analysis and key risks.";
        int loadingIndex = beginLoading("Loading live gold and market snapshots…");
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
                        "Live gold quote failed: " + safeText(error));
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
            marketContext += "\nMarket status: wallet not initialized";
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
                        marketContext += "\nUnable to load markets: " + safeText(error);
                        askWithCurrentContext(question, loadingIndex);
                    }
                });
    }

    private void showWelcomeMessage() {
        addMessage("AI", "Hello! I am the BrokerChain Gold Research Assistant.\n\n" +
                "I use current gold quotes, on-chain market snapshots, and your question to provide market research.\n" +
                "Each question refreshes the quote and market snapshot.\n" +
                "Never enter a private key or seed phrase.\n\n" +
                (DeepSeekClient.isConfigured() ?
                        "AI research is ready. Ask a question anytime." :
                        "A DeepSeek API key has not been configured."));
    }

    private void loadAiAdvice() {
        if (!DeepSeekClient.isConfigured()) {
            tvAiSummary.setText("Tap to configure a DeepSeek API key");
            return;
        }
        tvAiSummary.setText("Loading AI market research…");
        GoldAdvisoryManager.fetch(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory advisory) {
                updateAiAdviceUI(advisory);
            }

            @Override
            public void onError(String error) {
                if (!destroyed && isAdded()) {
                    tvAiSummary.setText("Unable to load · tap to retry");
                }
            }
        });
    }

    private void updateAiAdviceUI(GoldAdvisoryManager.Advisory advisory) {
        if (destroyed || !isAdded() || advisory == null) return;
        tvAiSignal.setText(advisory.signal);
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
        addMessage("You", text);
        if (!DeepSeekClient.isConfigured()) {
            addMessage("AI", "Configure a DeepSeek API key first.");
            return;
        }

        int loadingIndex = beginLoading("Loading live gold and market snapshots…");
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
            addMessage("AI", "The previous request is still running. Please wait.");
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
            return "AI returned no content. Please try again later.";
        }
        return text;
    }

    private String formatAiError(String error) {
        if (TextUtils.isEmpty(error)) {
            return "AI request failed without error details. Check your connection and try again.";
        }
        String lower = error.toLowerCase();
        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid api key")) {
            return "AI request failed: the DeepSeek API key is invalid or expired.";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "AI request timed out. Check your connection or try again later.";
        }
        if (lower.contains("not configured") || lower.contains("no_api_key")) {
            return "Configure a DeepSeek API key first.";
        }
        if (error.length() > 300) {
            error = error.substring(0, 300) + "...";
        }
        return "AI request failed: " + error;
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Configure DeepSeek API Key");
        final EditText input = new EditText(requireContext());
        input.setHint("Enter your API key");
        input.setText(DeepSeekClient.getApiKey());
        builder.setView(input);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if (!key.isEmpty()) {
                DeepSeekClient.setApiKey(key);
                loadAiAdvice();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
