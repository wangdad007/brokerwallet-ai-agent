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
import com.example.brokerfi.xc.agent.AgentManager;
import com.example.brokerfi.xc.agent.DeepSeekClient;

public class AIAssistantActivity extends AppCompatActivity {

    private LinearLayout messageContainer;
    private ScrollView messageScroll;
    private EditText inputField;
    private ImageView sendBtn;
    private ImageView backBtn;
    private ImageView settingsBtn;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean destroyed = false;
    private boolean requestInFlight = false;
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
        addMessage("AI", "你好！我是 BrokerChain 黄金票据投研助手。\n\n" +
                "我会基于 App 页面传入的金价、链上预测池和你的问题，给出黄金票据交易建议。\n" +
                "请不要输入私钥或助记词。\n\n" +
                (DeepSeekClient.isConfigured() ?
                        "DeepSeek AI 已就绪，可以直接询问黄金票据走势。" :
                        "尚未配置 DeepSeek API Key，点击右上角齿轮图标配置。"));

        backBtn.setOnClickListener(v -> finish());

        settingsBtn.setOnClickListener(v -> showApiKeyDialog());

        sendBtn.setOnClickListener(v -> onSendMessage());

        String initialPrompt = getIntent().getStringExtra("INITIAL_PROMPT");
        if (!TextUtils.isEmpty(initialPrompt)) {
            marketContext = initialPrompt;
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
        if (!ensureIdle()) return;
        addMessage("你", text);
        if (!DeepSeekClient.isConfigured()) {
            addMessage("AI", "请先配置 DeepSeek API Key（点击齿轮图标）。");
            return;
        }

        int loadingIndex = beginLoading("思考中...");
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
        if (TextUtils.isEmpty(marketContext) || marketContext.equals(text)) {
            return text;
        }
        return marketContext + "\n\n【用户追问】\n" + text;
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
            addMessage("AI", "上一条请求还在处理，请稍后再试。");
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
            return "AI 暂时没有返回内容，请稍后重试。";
        }
        return text;
    }

    private String formatAiError(String error) {
        if (TextUtils.isEmpty(error)) {
            return "AI 请求失败：没有收到错误详情，请检查网络或稍后重试。";
        }
        String lower = error.toLowerCase();
        if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid api key")) {
            return "AI 请求失败：DeepSeek API Key 无效或已过期，请点击右上角齿轮重新配置。";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "AI 请求超时：请检查模拟器/手机网络，或稍后再试。";
        }
        if (lower.contains("no account selected")) {
            return "当前钱包未选择账户，请先在钱包页面选择或导入账户。";
        }
        if (lower.contains("not configured") || lower.contains("no_api_key")) {
            return "请先配置 DeepSeek API Key（点击右上角齿轮图标）。";
        }
        if (error.length() > 300) {
            error = error.substring(0, 300) + "...";
        }
        return "AI 请求失败：" + error;
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("配置 DeepSeek API Key");

        EditText input = new EditText(this);
        input.setHint("sk-...");
        input.setPadding(32, 32, 32, 32);
        String current = DeepSeekClient.getApiKey();
        if (current != null) {
            input.setText(current);
        }
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if (key.startsWith("sk-") && key.length() > 10) {
                boolean saved = DeepSeekClient.setApiKey(key);
                if (saved) {
                    addMessage("AI", "API Key 已保存，DeepSeek AI 已就绪。");
                } else {
                    addMessage("AI", "保存失败，请重启应用后重试。");
                }
            } else {
                addMessage("AI", "格式无效，DeepSeek Key 以 sk- 开头。");
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
