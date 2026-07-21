package com.example.brokerfi.xc.agent.gold.view;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.ai.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketTemplateCatalog;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCreationPolicy;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GoldCreatePoolFragment extends Fragment {
    private static final double CONFIDENCE_THRESHOLD = 0.7d;

    private EditText etAiInput;
    private AppCompatButton btnAiAnalyze;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gold_create_pool, container, false);
        etAiInput = view.findViewById(R.id.et_ai_input);
        btnAiAnalyze = view.findViewById(R.id.btn_ai_analyze);
        TextView tabAiCreate = view.findViewById(R.id.tab_ai_create);
        TextView tabManualCreate = view.findViewById(R.id.tab_manual_create);
        View sectionAiCreate = view.findViewById(R.id.section_ai_create);
        View sectionManualCreate = view.findViewById(R.id.section_manual_create);

        btnAiAnalyze.setOnClickListener(v -> performAiAnalysis());
        bindCreateModeTabs(tabAiCreate, tabManualCreate, sectionAiCreate, sectionManualCreate);
        initTemplates(view.findViewById(R.id.template_grid));
        return view;
    }

    public static String buildAiParserPrompt(String today) {
        return "You are a strict parser for a gold prediction market. "
                + "Return one JSON object and no markdown. Today is " + today + ".\n"
                + "Only these six types are allowed:\n"
                + "1. TYPE_PRICE: XAU direction over whole Beijing days. directionIdx 0=UP, 1=DOWN, 2=FLAT.\n"
                + "2. TYPE_RETURN_THRESHOLD: absolute XAU close-to-close return. param1=positive percent, operatorIdx 0=GTE, 1=LTE.\n"
                + "3. TYPE_PRICE_THRESHOLD: XAU price at the end boundary. param1=positive USD/oz, operatorIdx 0=GTE, 1=LTE.\n"
                + "4. TYPE_PRICE_RANGE: end-boundary XAU price in or outside a closed interval. param1=lower, param2=upper, operatorIdx 0=inside, 1=outside.\n"
                + "5. TYPE_RELATIVE: XAU return strictly greater than a crypto benchmark. param1 must be BTC, ETH, SOL, or BNB.\n"
                + "6. TYPE_STREAK: XAU rises or falls at every consecutive Beijing-day boundary. directionIdx 0=UP, 1=DOWN.\n"
                + "All markets use the Chainlink XAU/USD Data Feed on Ethereum, Beijing midnight boundaries, and observation periods of 1–4 full days. "
                + "startDaysFromNow must be 0 or greater; durationDays must be 1-4. "
                + "If the request cannot be represented exactly, set confidence below 0.7.\n"
                + "Output schema: {\"type\":\"TYPE_...\",\"param1\":\"\",\"param2\":\"\","
                + "\"directionIdx\":0,\"operatorIdx\":0,\"startDaysFromNow\":0,"
                + "\"durationDays\":2,\"liquidity\":1,\"confidence\":0.0}";
    }

    private void performAiAnalysis() {
        String input = etAiInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(getContext(), "Describe a clear gold market question", Toast.LENGTH_SHORT).show();
            return;
        }
        btnAiAnalyze.setEnabled(false);
        btnAiAnalyze.setText("Preparing market setup…");
        String prompt = buildAiParserPrompt(dateFormat.format(new Date()));
        DeepSeekClient.chatForParsing(prompt, input, new DeepSeekClient.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    resetAnalyzeButton();
                    handleAiResponse(response);
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    resetAnalyzeButton();
                    Toast.makeText(getContext(), "Unable to generate market setup: " + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void resetAnalyzeButton() {
        btnAiAnalyze.setEnabled(true);
        btnAiAnalyze.setText("Generate Market Setup");
    }

    private void handleAiResponse(String response) {
        try {
            JSONObject json = new JSONObject(extractJson(response));
            String type = json.optString("type", "").trim();
            double confidence = json.optDouble("confidence", 0d);
            if (!GoldMarketTemplateCatalog.isCreatable(type)) {
                throw new IllegalArgumentException("AI returned an unsupported market type");
            }
            if (confidence < CONFIDENCE_THRESHOLD) {
                throw new IllegalArgumentException("Add a value, direction and full-day observation period");
            }
            String validation = validateTemplateFields(type, json);
            if (validation != null) throw new IllegalArgumentException(validation);

            GoldMarketTemplateCatalog.Template template = GoldMarketTemplateCatalog.forType(type);
            Intent intent = new Intent(requireContext(), GoldCreateCustomActivity.class);
            intent.putExtra("TEMPLATE_TYPE", type);
            intent.putExtra("TEMPLATE_TITLE", template.title);
            intent.putExtra("AI_PARSED_DATA", json.toString());
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(getContext(), error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String validateTemplateFields(String type, JSONObject json) {
        int startDays = json.optInt("startDaysFromNow", -1);
        int durationDays = json.optInt("durationDays", -1);
        if (startDays < 0) return "Start-day offset must be zero or greater";
        if (durationDays < 1 || durationDays > 4) return "Observation period must be 1–4 full days";
        String param1 = json.optString("param1", "").trim();
        String param2 = json.optString("param2", "").trim();
        int direction = json.optInt("directionIdx", -1);
        int operator = json.optInt("operatorIdx", -1);

        if (GoldMarketTemplateCatalog.TYPE_PRICE.equals(type)) {
            return direction >= 0 && direction <= 2 ? null : "Select Up, Down or Flat";
        }
        if (GoldMarketTemplateCatalog.TYPE_STREAK.equals(type)) {
            return direction >= 0 && direction <= 1 ? null : "A streak supports only Up or Down";
        }
        if (GoldMarketTemplateCatalog.TYPE_RELATIVE.equals(type)) {
            return GoldMarketCreationPolicy.isSupportedBenchmark(param1)
                    ? null : "Outperformance benchmarks support BTC, ETH, SOL or BNB";
        }
        if (operator < 0 || operator > 1) return "Comparison must be At Least or At Most";
        if (!isPositiveNumber(param1)) return "AI did not extract a valid positive number";
        if (GoldMarketTemplateCatalog.TYPE_PRICE_RANGE.equals(type)) {
            if (!isPositiveNumber(param2)) return "AI did not extract an upper range bound";
            if (Double.parseDouble(param2) <= Double.parseDouble(param1)) return "Upper bound must exceed the lower bound";
        }
        return null;
    }

    private static boolean isPositiveNumber(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return parsed > 0 && !Double.isNaN(parsed) && !Double.isInfinite(parsed);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String extractJson(String response) throws Exception {
        String cleaned = response == null ? "" : response.trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) throw new Exception("AI did not return valid JSON");
        return cleaned.substring(start, end + 1);
    }

    private void initTemplates(GridLayout grid) {
        for (GoldMarketTemplateCatalog.Template template : GoldMarketTemplateCatalog.templates()) {
            addTemplate(grid, template);
        }
    }

    private void addTemplate(GridLayout grid, GoldMarketTemplateCatalog.Template template) {
        View card = LayoutInflater.from(requireContext()).inflate(
                R.layout.item_gold_template_card, grid, false);
        ((TextView) card.findViewById(R.id.tv_template_title)).setText(template.title);
        ((TextView) card.findViewById(R.id.tv_template_desc)).setText(template.hint);
        ((ImageView) card.findViewById(R.id.iv_template_icon)).setImageResource(template.drawableRes);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        card.setLayoutParams(params);
        card.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), GoldCreateCustomActivity.class);
            intent.putExtra("TEMPLATE_TYPE", template.type);
            intent.putExtra("TEMPLATE_TITLE", template.title);
            startActivity(intent);
        });
        grid.addView(card);
    }

    private void bindCreateModeTabs(TextView aiTab, TextView manualTab,
                                    View aiSection, View manualSection) {
        aiTab.setOnClickListener(v -> updateCreateMode(
                aiTab, manualTab, aiSection, manualSection, true));
        manualTab.setOnClickListener(v -> updateCreateMode(
                aiTab, manualTab, aiSection, manualSection, false));
        updateCreateMode(aiTab, manualTab, aiSection, manualSection, true);
    }

    private void updateCreateMode(TextView aiTab, TextView manualTab,
                                  View aiSection, View manualSection, boolean aiSelected) {
        aiSection.setVisibility(aiSelected ? View.VISIBLE : View.GONE);
        manualSection.setVisibility(aiSelected ? View.GONE : View.VISIBLE);
        styleCreateTab(aiTab, aiSelected);
        styleCreateTab(manualTab, !aiSelected);
    }

    private void styleCreateTab(TextView tab, boolean selected) {
        tab.setTextColor(selected ? 0xFFFFFFFF : 0xFF475569);
        tab.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? 0xFF111827 : 0x00000000);
        background.setCornerRadius(Math.round(8 * getResources().getDisplayMetrics().density));
        tab.setBackground(background);
    }
}
