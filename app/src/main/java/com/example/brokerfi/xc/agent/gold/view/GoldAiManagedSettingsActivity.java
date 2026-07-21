package com.example.brokerfi.xc.agent.gold.view;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import java.util.Locale;

/** Full-screen editor for the guardrails used by the backend AI trading engine. */
public class GoldAiManagedSettingsActivity extends AppCompatActivity {
    private static final String EXTRA_GAME_ID = "GAME_ID";
    private static final String EXTRA_CONTRACT_ADDRESS = "CONTRACT_ADDRESS";
    private static final String EXTRA_MARKET_TITLE = "MARKET_TITLE";
    private static final String EXTRA_ENABLED = "AI_ENABLED";
    private static final String EXTRA_AMOUNT = "AI_AMOUNT";
    private static final String EXTRA_CONFIDENCE = "AI_CONFIDENCE";
    private static final String EXTRA_EDGE = "AI_EDGE";
    private static final String EXTRA_KELLY = "AI_KELLY";
    private static final String EXTRA_COOLDOWN = "AI_COOLDOWN";

    private EditText etOrderAmount;
    private EditText etConfidence;
    private EditText etEdge;
    private EditText etKelly;
    private SwitchCompat switchCooldown;
    private Button btnSave;
    private TextView presetConservative;
    private TextView presetBalanced;
    private TextView presetActive;
    private boolean saving;

    public static Intent createIntent(Context context, int gameId, String contractAddress,
                                      String marketTitle,
                                      BackendApiClient.AiManagedConfig current) {
        BackendApiClient.AiManagedConfig value = current == null
                ? BackendApiClient.AiManagedConfig.defaults() : current;
        return new Intent(context, GoldAiManagedSettingsActivity.class)
                .putExtra(EXTRA_GAME_ID, gameId)
                .putExtra(EXTRA_CONTRACT_ADDRESS, contractAddress)
                .putExtra(EXTRA_MARKET_TITLE, marketTitle)
                .putExtra(EXTRA_ENABLED, value.enabled)
                .putExtra(EXTRA_AMOUNT, value.buyAmountBKC)
                .putExtra(EXTRA_CONFIDENCE, value.confidenceMin)
                .putExtra(EXTRA_EDGE, value.minEdgePercent)
                .putExtra(EXTRA_KELLY, value.kellyFraction)
                .putExtra(EXTRA_COOLDOWN, value.adaptiveCooldown);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_ai_managed_settings);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        etOrderAmount = findViewById(R.id.et_order_amount);
        etConfidence = findViewById(R.id.et_confidence);
        etEdge = findViewById(R.id.et_edge);
        etKelly = findViewById(R.id.et_kelly);
        switchCooldown = findViewById(R.id.switch_adaptive_cooldown);
        btnSave = findViewById(R.id.btn_save_strategy);
        presetConservative = findViewById(R.id.preset_conservative);
        presetBalanced = findViewById(R.id.preset_balanced);
        presetActive = findViewById(R.id.preset_active);

        TextView marketName = findViewById(R.id.tv_market_name);
        String title = getIntent().getStringExtra(EXTRA_MARKET_TITLE);
        marketName.setText(title == null || title.trim().isEmpty()
                ? "Current Market" : title.trim());
        TextView status = findViewById(R.id.tv_strategy_status);
        status.setText(getIntent().getBooleanExtra(EXTRA_ENABLED, false) ? "Enabled" : "Ready");

        etOrderAmount.setText(getIntent().getStringExtra(EXTRA_AMOUNT));
        etConfidence.setText(formatPercent(getIntent().getDoubleExtra(EXTRA_CONFIDENCE, 0.70) * 100));
        etEdge.setText(formatPercent(getIntent().getDoubleExtra(EXTRA_EDGE, 5)));
        etKelly.setText(formatPercent(getIntent().getDoubleExtra(EXTRA_KELLY, 0.25) * 100));
        switchCooldown.setChecked(getIntent().getBooleanExtra(EXTRA_COOLDOWN, true));

        presetConservative.setOnClickListener(v -> applyPreset("conservative"));
        presetBalanced.setOnClickListener(v -> applyPreset("balanced"));
        presetActive.setOnClickListener(v -> applyPreset("active"));
        btnSave.setOnClickListener(v -> saveStrategy());
        updatePresetSelection(detectPreset());
    }

    private void applyPreset(String preset) {
        switch (preset) {
            case "conservative":
                setInputs("0.5", "80", "8", "15", true);
                break;
            case "active":
                setInputs("2", "60", "3", "40", true);
                break;
            default:
                setInputs("1", "70", "5", "25", true);
                preset = "balanced";
                break;
        }
        updatePresetSelection(preset);
    }

    private void setInputs(String amount, String confidence, String edge,
                           String kelly, boolean cooldown) {
        etOrderAmount.setText(amount);
        etConfidence.setText(confidence);
        etEdge.setText(edge);
        etKelly.setText(kelly);
        switchCooldown.setChecked(cooldown);
    }

    private void updatePresetSelection(String selected) {
        stylePreset(presetConservative, "conservative".equals(selected));
        stylePreset(presetBalanced, "balanced".equals(selected));
        stylePreset(presetActive, "active".equals(selected));
    }

    private String detectPreset() {
        double amount = numberOr(etOrderAmount, -1);
        double confidence = numberOr(etConfidence, -1);
        double edge = numberOr(etEdge, -1);
        double kelly = numberOr(etKelly, -1);
        if (near(amount, 0.5) && near(confidence, 80) && near(edge, 8) && near(kelly, 15)) {
            return "conservative";
        }
        if (near(amount, 1) && near(confidence, 70) && near(edge, 5) && near(kelly, 25)) {
            return "balanced";
        }
        if (near(amount, 2) && near(confidence, 60) && near(edge, 3) && near(kelly, 40)) {
            return "active";
        }
        return "custom";
    }

    private static double numberOr(EditText field, double fallback) {
        try {
            return Double.parseDouble(field.getText().toString().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean near(double left, double right) {
        return Math.abs(left - right) < 0.0001;
    }

    private void stylePreset(TextView view, boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(selected ? 0xFFEEF2FF : 0xFFF8FAFC);
        background.setStroke(dp(1), selected ? 0xFF6366F1 : 0xFFE2E8F0);
        view.setBackground(background);
        view.setTextColor(selected ? 0xFF4338CA : 0xFF475569);
    }

    private void saveStrategy() {
        if (saving) return;
        Double amount = readNumber(etOrderAmount, "Enter a valid order size");
        Double confidence = readNumber(etConfidence, "Enter a valid confidence threshold");
        Double edge = readNumber(etEdge, "Enter a valid model edge");
        Double kelly = readNumber(etKelly, "Enter a valid Kelly allocation");
        if (amount == null || confidence == null || edge == null || kelly == null) return;
        if (amount <= 0 || amount > 1000) {
            showFieldError(etOrderAmount, "Order size must be greater than 0 and no more than 1,000 BKC");
            return;
        }
        if (confidence < 50 || confidence > 99) {
            showFieldError(etConfidence, "Confidence must be between 50% and 99%");
            return;
        }
        if (edge < 1 || edge > 30) {
            showFieldError(etEdge, "Model edge must be between 1% and 30%");
            return;
        }
        if (kelly < 5 || kelly > 100) {
            showFieldError(etKelly, "Kelly allocation must be between 5% and 100%");
            return;
        }

        int gameId = getIntent().getIntExtra(EXTRA_GAME_ID, 0);
        String contractAddress = getIntent().getStringExtra(EXTRA_CONTRACT_ADDRESS);
        BackendApiClient.AiManagedConfig config = BackendApiClient.AiManagedConfig.defaults();
        config.enabled = true;
        config.buyAmountBKC = trimNumber(amount);
        config.confidenceMin = confidence / 100d;
        config.minEdgePercent = edge;
        config.kellyFraction = kelly / 100d;
        config.adaptiveCooldown = switchCooldown.isChecked();

        String privateKey = StorageUtil.getCurrentPrivatekey(this);
        if (gameId <= 0 || contractAddress == null || contractAddress.trim().isEmpty()
                || privateKey == null || privateKey.trim().isEmpty()) {
            Toast.makeText(this, "Wallet or market configuration is unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        saving = true;
        btnSave.setEnabled(false);
        btnSave.setText("Saving strategy…");
        GoldMarketRepository repository = new GoldMarketRepository(this, privateKey, contractAddress);
        repository.configureAiManaged(gameId, true, config,
                new GoldMarketRepository.DataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean enabled) {
                saving = false;
                setResult(Activity.RESULT_OK);
                Toast.makeText(GoldAiManagedSettingsActivity.this,
                        "AI-managed trading enabled", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                saving = false;
                btnSave.setEnabled(true);
                btnSave.setText("Save & Enable AI Trading");
                Toast.makeText(GoldAiManagedSettingsActivity.this,
                        error == null ? "Unable to save strategy" : error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private Double readNumber(EditText field, String message) {
        try {
            return Double.parseDouble(field.getText().toString().trim());
        } catch (Exception ignored) {
            showFieldError(field, message);
            return null;
        }
    }

    private void showFieldError(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
    }

    private static String formatPercent(double value) {
        return Math.abs(value - Math.rint(value)) < 0.0001
                ? String.format(Locale.US, "%.0f", value)
                : String.format(Locale.US, "%.1f", value);
    }

    private static String trimNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 0.000001
                ? String.format(Locale.US, "%.0f", value)
                : String.format(Locale.US, "%.6f", value).replaceFirst("0+$", "").replaceFirst("\\.$", "");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
