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
import com.example.brokerfi.xc.agent.BrokerAdvisor;
import com.example.brokerfi.xc.agent.DeepSeekClient;

public class AIAssistantActivity extends AppCompatActivity {

    private LinearLayout messageContainer;
    private ScrollView messageScroll;
    private EditText inputField;
    private ImageView sendBtn;
    private ImageView backBtn;
    private ImageView settingsBtn;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        addMessage("AI", "Hello! I'm your BrokerChain AI Assistant.\n\n" +
                "I can help you with:\n" +
                "- Broker staking profit analysis\n" +
                "- NFT market recommendations\n" +
                "- Security checks before transactions\n\n" +
                (DeepSeekClient.isConfigured() ?
                        "DeepSeek AI is ready. Tap a quick action or ask me anything." :
                        "DeepSeek API Key not set. Tap the gear icon to configure."));

        backBtn.setOnClickListener(v -> finish());

        settingsBtn.setOnClickListener(v -> showApiKeyDialog());

        sendBtn.setOnClickListener(v -> onSendMessage());
        findViewById(R.id.btn_broker_scan).setOnClickListener(v -> onBrokerScan());
        findViewById(R.id.btn_nft_market).setOnClickListener(v -> onNFTMarket());
        findViewById(R.id.btn_deep_analysis).setOnClickListener(v -> onDeepAnalysis());
    }

    private void onSendMessage() {
        String text = inputField.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        addMessage("You", text);
        inputField.setText("");

        if (!DeepSeekClient.isConfigured()) {
            addMessage("AI", "Please configure your DeepSeek API Key first (tap gear icon).");
            return;
        }

        addMessage("AI", "Thinking...");
        int loadingIndex = messageContainer.getChildCount() - 1;

        AgentManager.getInstance().askAnything(text, new AgentManager.AnalysisCallback() {
            @Override
            public void onBrokerReport(AgentManager.BrokerReport report) {
                updateLastMessage(loadingIndex, report.rawAnalysis);
            }

            @Override
            public void onGeneralAdvice(String question, String answer) {
                updateLastMessage(loadingIndex, answer);
            }

            @Override
            public void onError(String error) {
                updateLastMessage(loadingIndex, "Error: " + error);
            }
        });
    }

    private void onBrokerScan() {
        addMessage("You", "Scan my broker staking profits");
        addMessage("AI", "Scanning...");
        int loadingIndex = messageContainer.getChildCount() - 1;

        BrokerAdvisor.quickScan(this, new BrokerAdvisor.AdviceCallback() {
            @Override
            public void onAdvice(String title, String detail) {
                updateLastMessage(loadingIndex, "**" + title + "**\n\n" + detail);
            }

            @Override
            public void onDataError(String error) {
                updateLastMessage(loadingIndex, error);
            }
        });
    }

    private void onNFTMarket() {
        addMessage("You", "Analyze NFT market");
        addMessage("AI", "Scanning NFT market...");
        int loadingIndex = messageContainer.getChildCount() - 1;

        AgentManager.getInstance().recommendNFTs(this, new AgentManager.AnalysisCallback() {
            @Override
            public void onBrokerReport(AgentManager.BrokerReport report) {}

            @Override
            public void onGeneralAdvice(String question, String answer) {
                updateLastMessage(loadingIndex, answer);
            }

            @Override
            public void onError(String error) {
                updateLastMessage(loadingIndex, error);
            }
        });
    }

    private void onDeepAnalysis() {
        addMessage("You", "Run deep AI analysis on my broker positions");
        addMessage("AI", "Running AI analysis...");
        int loadingIndex = messageContainer.getChildCount() - 1;

        BrokerAdvisor.deepAnalysis(this, new BrokerAdvisor.AdviceCallback() {
            @Override
            public void onAdvice(String title, String detail) {
                updateLastMessage(loadingIndex, "**" + title + "**\n\n" + detail);
            }

            @Override
            public void onDataError(String error) {
                updateLastMessage(loadingIndex, error);
            }
        });
    }

    // ============= UI 辅助 =============

    private void addMessage(String sender, String text) {
        mainHandler.post(() -> {
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
            textView.setTextColor(0xFF1A1A1A);
            textView.setLineSpacing(4, 1);
            textView.setPadding(24, 16, 24, 16);
            textView.setBackgroundResource(sender.equals("AI")
                    ? R.drawable.custom_light_grey_background
                    : R.drawable.custom_green_background);

            bubble.addView(textView);
            bubble.setGravity(sender.equals("AI") ? Gravity.START : Gravity.END);

            messageContainer.addView(bubble);
            messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void updateLastMessage(int index, String text) {
        mainHandler.post(() -> {
            if (index >= 0 && index < messageContainer.getChildCount()) {
                View child = messageContainer.getChildAt(index);
                if (child instanceof LinearLayout) {
                    LinearLayout bubble = (LinearLayout) child;
                    if (bubble.getChildCount() >= 2 && bubble.getChildAt(1) instanceof TextView) {
                        ((TextView) bubble.getChildAt(1)).setText(text);
                    }
                }
            }
        });
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("DeepSeek API Key");

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
                DeepSeekClient.setApiKey(key);
                addMessage("AI", "API Key saved. DeepSeek AI is now ready.");
            } else {
                addMessage("AI", "Invalid key format. DeepSeek keys start with 'sk-'.");
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
