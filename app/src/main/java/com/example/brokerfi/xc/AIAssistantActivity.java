package com.example.brokerfi.xc;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.ai.AgentManager;
import com.example.brokerfi.xc.agent.ai.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketResearchPromptBuilder;

public class AIAssistantActivity extends AppCompatActivity {

    public static final String EXTRA_MARKET_CONTEXT = "MARKET_CONTEXT";
    public static final String EXTRA_INITIAL_AI_SUMMARY = "INITIAL_AI_SUMMARY";

    private LinearLayout messageContainer;
    private ScrollView messageScroll;
    private EditText inputField;
    private ImageView sendBtn;
    private ImageView backBtn;
    private ImageView settingsBtn;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;
    private boolean requestInFlight = false;
    private boolean legacyInitialPromptInFlight = false;
    private String marketContext = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);

        messageContainer = findViewById(R.id.message_container);
        messageScroll = findViewById(R.id.message_scroll);
        inputField = findViewById(R.id.input_field);
        sendBtn = findViewById(R.id.send_btn);
        backBtn = findViewById(R.id.back_btn);
        settingsBtn = findViewById(R.id.settings_btn);

        // 初始化 DeepSeek
        DeepSeekClient.init(this);

        // 欢迎消息
        addMessage("AI", "Hello! I am the BrokerChain Gold Research Assistant.\n\n" +
                "I use the live gold quote, on-chain market snapshot and your question to provide research insights.\n" +
                "Never enter a private key or recovery phrase.\n\n" +
                (DeepSeekClient.isConfigured() ?
                        "DeepSeek AI is ready. Ask about gold markets at any time." :
                        "DeepSeek API key is not configured. Use the settings icon to add one."));

        backBtn.setOnClickListener(v -> finish());

        settingsBtn.setOnClickListener(v -> showApiKeyDialog());

        sendBtn.setOnClickListener(v -> onSendMessage());

        String initialMarketContext = getIntent().getStringExtra(EXTRA_MARKET_CONTEXT);
        marketContext = isBlank(initialMarketContext) ? "" : initialMarketContext;
        String initialSummary = getIntent().getStringExtra(EXTRA_INITIAL_AI_SUMMARY);
        String initialPrompt = getIntent().getStringExtra("INITIAL_PROMPT");
        if (!isBlank(initialSummary)) {
            addMessage("AI", initialSummary);
        } else if (!isBlank(initialPrompt)) {
            marketContext = initialPrompt;
            legacyInitialPromptInFlight = true;
            submitQuestion(initialPrompt);
        }
    }

    private void onSendMessage() {
        String text = inputField.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        inputField.setText("");
        submitQuestion(text);
    }

    private void submitQuestion(String text) {
        if (!ensureIdle()) {
            legacyInitialPromptInFlight = false;
            return;
        }
        addMessage("You", text);
        if (!DeepSeekClient.isConfigured()) {
            legacyInitialPromptInFlight = false;
            addMessage("AI", "Configure a DeepSeek API key from the settings icon first.");
            return;
        }

        int loadingIndex = beginLoading("Analyzing...");
        String questionForAi = buildQuestionForAi(text);

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

    private String buildQuestionForAi(String text) {
        if (legacyInitialPromptInFlight) {
            legacyInitialPromptInFlight = false;
            return text;
        }
        return GoldMarketResearchPromptBuilder.withFollowUp(marketContext, text);
    }

    private static boolean isBlank(String value) {
        return TextUtils.isEmpty(value) || value.trim().isEmpty();
    }

    // ============= UI 辅助 =============

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
    }

    private int addMessage(String sender, String text) {
        if (destroyed) return -1;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return addMessageNow(sender, safeText(text));
        }
        mainHandler.post(() -> {
            if (!destroyed) {
                addMessageNow(sender, safeText(text));
            }
        });
        return -1;
    }

    private int addMessageNow(String sender, String text) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 24);
        bubble.setLayoutParams(params);

        TextView senderView = new TextView(this);
        senderView.setText(sender);
        senderView.setTextSize(12);
        senderView.setTextColor(sender.equals("AI") ? 0xFF4A90D9 : 0xFF333333);
        senderView.setPadding(0, 0, 0, 4);
        bubble.addView(senderView);

        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(15);
        textView.setTextColor(sender.equals("AI") ? 0xFF1A1A1A : 0xFFFFFFFF);
        textView.setLineSpacing(4, 1);
        textView.setPadding(24, 16, 24, 16);
        textView.setBackgroundResource(sender.equals("AI")
                ? R.drawable.custom_light_grey_background
                : R.drawable.custom_green_background);

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
                    ((TextView) bubble.getChildAt(1)).setText(text);
                    messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
                }
            }
        }
    }

    private boolean ensureIdle() {
        if (requestInFlight) {
            addMessage("AI", "The previous request is still running. Please wait a moment.");
            return false;
        }
        return true;
    }

    private int beginLoading(String text) {
        setRequestInFlight(true);
        return addMessage("AI", text);
    }

    private void finishLoading(int index, String text) {
        if (destroyed) return;
        mainHandler.post(() -> {
            if (destroyed) return;
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
            return "The AI returned no content. Please retry shortly.";
        }
        return text;
    }

    private String formatAiError(String error) {
        if (TextUtils.isEmpty(error)) {
            return "The AI request failed without error details. Check the network and retry.";
        }
        String lower = error.toLowerCase();
        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid api key")) {
            return "The DeepSeek API key is invalid or expired. Reconfigure it in settings.";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "The AI request timed out. Check the device network and retry.";
        }
        if (lower.contains("no account selected")) {
            return "No wallet account is selected. Select or import an account first.";
        }
        if (lower.contains("not configured") || lower.contains("no_api_key")) {
            return "Configure a DeepSeek API key from the settings icon first.";
        }
        if (error.length() > 300) {
            error = error.substring(0, 300) + "...";
        }
        return "AI request failed: " + error;
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Configure DeepSeek API Key");

        EditText input = new EditText(this);
        input.setHint("sk-...");
        input.setPadding(32, 32, 32, 32);
        String current = DeepSeekClient.getApiKey();
        if (current != null) {
            input.setText(current);
        }
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if (key.startsWith("sk-") && key.length() > 10) {
                boolean saved = DeepSeekClient.setApiKey(key);
                if (saved) {
                    addMessage("AI", "API key saved. DeepSeek AI is ready.");
                } else {
                    addMessage("AI", "Unable to save the key. Restart the app and retry.");
                }
            } else {
                addMessage("AI", "Invalid format. A DeepSeek key must start with sk-.");
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
