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
    private static final String ARG_AI_DRAFT = "ARG_AI_DRAFT";

    private EditText etAiInput;
    private AppCompatButton btnAiAnalyze;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public static GoldCreatePoolFragment newInstance(String aiDraft) {
        GoldCreatePoolFragment fragment = new GoldCreatePoolFragment();
        Bundle args = new Bundle();
        args.putString(ARG_AI_DRAFT, aiDraft);
        fragment.setArguments(args);
        return fragment;
    }

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
        String draft = getArguments() == null ? "" : getArguments().getString(ARG_AI_DRAFT, "");
        applyAiDraft(draft);
        return view;
    }

    /** Called by the AI workbench after the user explicitly chooses to create a draft. */
    public void applyAiDraft(String draft) {
        if (etAiInput == null || draft == null || draft.trim().isEmpty()) return;
        etAiInput.setText(draft.trim());
        etAiInput.setSelection(etAiInput.length());
        etAiInput.requestFocus();
    }

    public static String buildAiParserPrompt(String today) {
        return "你是黄金预测博弈池的严格规则解析器。"
                + "只返回一个 JSON 对象，不要输出 Markdown。今天是 " + today + "。\n"
                + "只允许以下六种类型：\n"
                + "1. TYPE_PRICE：按北京时间整日边界判断 XAU 方向，directionIdx 0=上涨、1=下跌、2=持平。\n"
                + "2. TYPE_RETURN_THRESHOLD：XAU 收盘价之间的绝对涨跌幅，param1=正百分比，operatorIdx 0=大于等于、1=小于等于。\n"
                + "3. TYPE_PRICE_THRESHOLD：截止边界的 XAU 价格，param1=正数 USD/盎司，operatorIdx 0=大于等于、1=小于等于。\n"
                + "4. TYPE_PRICE_RANGE：截止边界的 XAU 价格是否位于闭区间，param1=下限、param2=上限，operatorIdx 0=区间内、1=区间外。\n"
                + "5. TYPE_RELATIVE：XAU 收益率是否严格跑赢加密货币标的，param1 只能为 BTC、ETH、SOL 或 BNB。\n"
                + "6. TYPE_STREAK：XAU 在连续的北京时间整日边界上涨或下跌，directionIdx 0=上涨、1=下跌。\n"
                + "所有博弈池使用 Ethereum Chainlink XAU/USD 数据源、北京时间零点边界和 1 至 4 个整天的观察期。"
                + "startDaysFromNow 必须大于等于 0，durationDays 必须为 1 至 4。"
                + "若用户需求无法准确表示，请将 confidence 设为低于 0.7。\n"
                + "输出结构：{\"type\":\"TYPE_...\",\"param1\":\"\",\"param2\":\"\","
                + "\"directionIdx\":0,\"operatorIdx\":0,\"startDaysFromNow\":0,"
                + "\"durationDays\":2,\"liquidity\":1,\"confidence\":0.0}";
    }

    private void performAiAnalysis() {
        String input = etAiInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(getContext(), "请清楚描述一个黄金预测问题", Toast.LENGTH_SHORT).show();
            return;
        }
        btnAiAnalyze.setEnabled(false);
        btnAiAnalyze.setText("正在生成博弈池配置…");
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
                    Toast.makeText(getContext(), "无法生成博弈池配置：" + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void resetAnalyzeButton() {
        btnAiAnalyze.setEnabled(true);
        btnAiAnalyze.setText("生成博弈池配置");
    }

    private void handleAiResponse(String response) {
        try {
            JSONObject json = parseAndValidateAiResponse(response);
            String type = json.optString("type", "").trim();
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

    /**
     * Shared strict parser used by both the create page and the conversational workbench.
     * Keeping validation here guarantees that a chat preview can only open a supported,
     * editable template configuration.
     */
    public static JSONObject parseAndValidateAiResponse(String response) throws Exception {
        JSONObject json = new JSONObject(extractJson(response));
        String type = json.optString("type", "").trim();
        double confidence = json.optDouble("confidence", 0d);
        if (!GoldMarketTemplateCatalog.isCreatable(type)) {
            throw new IllegalArgumentException("AI 返回了不支持的博弈池类型");
        }
        if (confidence < CONFIDENCE_THRESHOLD) {
            throw new IllegalArgumentException("请补充数值、方向和整日观察周期");
        }
        String validation = validateTemplateFields(type, json);
        if (validation != null) throw new IllegalArgumentException(validation);
        return json;
    }

    private static String validateTemplateFields(String type, JSONObject json) {
        int startDays = json.optInt("startDaysFromNow", -1);
        int durationDays = json.optInt("durationDays", -1);
        if (startDays < 0) return "开始日期偏移必须大于等于 0";
        if (durationDays < 1 || durationDays > 4) return "观察期必须为 1 至 4 个整天";
        String param1 = json.optString("param1", "").trim();
        String param2 = json.optString("param2", "").trim();
        int direction = json.optInt("directionIdx", -1);
        int operator = json.optInt("operatorIdx", -1);

        if (GoldMarketTemplateCatalog.TYPE_PRICE.equals(type)) {
            return direction >= 0 && direction <= 2 ? null : "请选择上涨、下跌或持平";
        }
        if (GoldMarketTemplateCatalog.TYPE_STREAK.equals(type)) {
            return direction >= 0 && direction <= 1 ? null : "连续涨跌只支持上涨或下跌";
        }
        if (GoldMarketTemplateCatalog.TYPE_RELATIVE.equals(type)) {
            return GoldMarketCreationPolicy.isSupportedBenchmark(param1)
                    ? null : "跑赢率标的仅支持 BTC、ETH、SOL 或 BNB";
        }
        if (operator < 0 || operator > 1) return "比较方式必须为大于等于或小于等于";
        if (!isPositiveNumber(param1)) return "AI 未提取到有效正数";
        if (GoldMarketTemplateCatalog.TYPE_PRICE_RANGE.equals(type)) {
            if (!isPositiveNumber(param2)) return "AI 未提取到有效的区间上限";
            if (Double.parseDouble(param2) <= Double.parseDouble(param1)) return "区间上限必须大于下限";
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
        if (start < 0 || end <= start) throw new Exception("AI 未返回有效 JSON");
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
        int gap = Math.round(5 * getResources().getDisplayMetrics().density);
        params.setMargins(gap, gap, gap, gap);
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
