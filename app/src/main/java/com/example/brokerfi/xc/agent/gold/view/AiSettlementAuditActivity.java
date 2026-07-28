package com.example.brokerfi.xc.agent.gold.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class AiSettlementAuditActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_audit_detail);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        BackendApiClient.AiSettlementAuditDTO item =
                (BackendApiClient.AiSettlementAuditDTO) getIntent().getSerializableExtra("audit");
        if (item == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.tv_page_title)).setText("结算复核详情");
        ((TextView) findViewById(R.id.tv_badge)).setText("复核记录 #" + item.id);
        ((TextView) findViewById(R.id.tv_market_title)).setText(
                TextUtils.isEmpty(item.marketTitle) ? "博弈池 #" + item.gameId : item.marketTitle);
        ((TextView) findViewById(R.id.tv_summary)).setText(
                "最终 " + item.finalDecision
                        + String.format(Locale.getDefault(), " · 裁判置信度 %.0f%% · 同向率 %.0f%%",
                        item.finalConfidence * 100, item.consensusRatio * 100));

        LinearLayout trace = findViewById(R.id.trace_container);
        String ruleBody = TextUtils.isEmpty(item.ruleSummary)
                ? "结算规则已由 IPFS CID 冻结；该条历史记录未保存规则摘要。"
                : abbreviate(item.ruleSummary, 360);
        trace.addView(AiDecisionUi.node(this, "01", "规则与证据冻结", ruleBody,
                AiDecisionUi.BLUE, false));
        String candidateBody = TextUtils.isEmpty(item.deterministicCandidate)
                ? "事件型市场：由可靠外部证据交给模型独立复核，不预设二元答案。"
                : "Chainlink 边界报价和公式先生成可复现候选结果："
                + item.deterministicCandidate + "。候选结果仍需模型复核。";
        trace.addView(AiDecisionUi.node(this, "02", "确定性候选计算", candidateBody,
                0xFF7C3AED, false));

        List<BackendApiClient.AiModelOpinionDTO> opinions =
                item.opinions == null ? Collections.emptyList() : item.opinions;
        int number = 3;
        for (BackendApiClient.AiModelOpinionDTO opinion : opinions) {
            String title = opinion.isFinal ? "最终裁判 · " + safe(opinion.modelName)
                    : "独立复核 · " + safe(opinion.modelName);
            String body;
            if (!TextUtils.isEmpty(opinion.error)) {
                body = "调用失败/弃权：" + opinion.error;
            } else {
                body = safe(opinion.decision)
                        + String.format(Locale.getDefault(), " · 置信度 %.0f%%", opinion.confidence * 100)
                        + "\n依据摘要：" + abbreviate(opinion.reasoning, 220);
            }
            trace.addView(AiDecisionUi.node(this, String.format(Locale.getDefault(), "%02d", number++),
                    title, body, opinion.isFinal ? AiDecisionUi.AMBER : 0xFF0891B2, false));
        }
        if (opinions.isEmpty()) {
            trace.addView(AiDecisionUi.node(this, "03", "模型独立复核",
                    "该历史记录没有保存逐模型结果，因此界面不会推测或补造模型意见。",
                    AiDecisionUi.AMBER, false));
        }
        trace.addView(AiDecisionUi.node(this,
                String.format(Locale.getDefault(), "%02d", number),
                "最终裁决输出",
                "决策 " + item.finalDecision
                        + String.format(Locale.getDefault(), " · 置信度 %.0f%%\n", item.finalConfidence * 100)
                        + abbreviate(item.finalSummary, 300),
                decisionColor(item.finalDecision), true));
        ((TextView) findViewById(R.id.tv_footer_note)).setText(
                "链上说明：审计记录保存的是 Sentinel 实际提交写链前产生的证据和裁决。"
                        + "最终链上状态仍以合约为准；INDETERMINATE 不会被错误等同于 NO。");
    }

    private String safe(String value) {
        return TextUtils.isEmpty(value) ? "未知模型" : value;
    }

    private String abbreviate(String value, int max) {
        if (TextUtils.isEmpty(value)) return "未保存摘要";
        String clean = value.trim().replaceAll("\\s+", " ");
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    private int decisionColor(String value) {
        if ("YES".equals(value)) return AiDecisionUi.YES;
        if ("NO".equals(value)) return AiDecisionUi.NO;
        return AiDecisionUi.AMBER;
    }
}
