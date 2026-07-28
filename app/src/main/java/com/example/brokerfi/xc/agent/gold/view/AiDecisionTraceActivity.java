package com.example.brokerfi.xc.agent.gold.view;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;

import java.util.Locale;

public class AiDecisionTraceActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_audit_detail);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        BackendApiClient.AiManagedDecisionDTO item =
                (BackendApiClient.AiManagedDecisionDTO) getIntent().getSerializableExtra("decision");
        if (item == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.tv_page_title)).setText("策略判断详情");
        ((TextView) findViewById(R.id.tv_badge)).setText("记录 #" + item.id);
        ((TextView) findViewById(R.id.tv_market_title)).setText(
                TextUtils.isEmpty(item.marketTitle) ? "博弈池 #" + item.gameId : item.marketTitle);
        ((TextView) findViewById(R.id.tv_summary)).setText(
                action(item.action) + " · " + outcome(item.outcome)
                        + String.format(Locale.getDefault(), " · 置信度 %.0f%%", item.confidence * 100));
        LinearLayout trace = findViewById(R.id.trace_container);
        trace.addView(AiDecisionUi.node(this, "01", "市场快照",
                item.historyPoints + " 个历史份额点 · 当前市场 YES 隐含概率 "
                        + percentOrUnknown(item.marketProbYES), AiDecisionUi.BLUE, false));
        trace.addView(AiDecisionUi.node(this, "02", "指标评估",
                "计算市场概率、近期趋势、波动、剩余时间与资金池深度。",
                0xFF7C3AED, false));
        trace.addView(AiDecisionUi.node(this, "03", "方向判断",
                "输出 " + action(item.action) + " · YES 胜率估计 "
                        + percentOrUnknown(item.estimatedProbYES) + " · 置信度 "
                        + String.format(Locale.getDefault(), "%.0f%%", item.confidence * 100)
                        + "\n依据摘要：" + cleanReason(item.reason),
                0xFF0891B2, false));

        boolean edgeKnown = item.estimatedProbYES > 0 && item.marketProbYES > 0;
        boolean isNO = "buy_no".equals(item.action);
        double sideEdge = isNO ? -item.probabilityEdgePercent : item.probabilityEdgePercent;
        boolean edgePass = ("buy_yes".equals(item.action) || isNO) && sideEdge >= 5;
        trace.addView(AiDecisionUi.node(this, "04", "概率优势门控",
                edgeKnown
                        ? String.format(Locale.getDefault(), "%s · %s 相对市场优势 %.1f%%，最低门槛 5%%。",
                        edgePass ? "通过" : "未通过", isNO ? "NO" : "YES", Math.max(0, sideEdge))
                        : "历史记录未保存独立概率字段；该条记录仅展示当时保存的最终方向。",
                edgePass ? AiDecisionUi.YES : AiDecisionUi.AMBER, false));

        boolean confidencePass = item.confidence >= .70;
        trace.addView(AiDecisionUi.node(this, "05", "置信度门控",
                String.format(Locale.getDefault(), "%s · 判断置信度 %.0f%%，默认最低门槛 70%%。",
                        confidencePass ? "通过" : "拦截", item.confidence * 100),
                confidencePass ? AiDecisionUi.YES : AiDecisionUi.NO, false));
        trace.addView(AiDecisionUi.node(this, "06", "执行检查",
                outcomeDetail(item) + (TextUtils.isEmpty(item.txHash) ? "" : "\n交易哈希：" + item.txHash),
                outcomeColor(item.outcome), true));
        ((TextView) findViewById(R.id.tv_footer_note)).setText(
                "记录说明：页面内容来自实际保存的市场快照、判断结果与执行状态，仅供投研参考。");
    }

    private String cleanReason(String reason) {
        if (TextUtils.isEmpty(reason)) return "未保存文字依据";
        return reason.replaceAll("\\s*\\|\\s*est_prob=.*$", "").trim();
    }

    private String percentOrUnknown(double value) {
        return value <= 0 ? "历史未记录" :
                String.format(Locale.getDefault(), "%.1f%%", value * 100);
    }

    private String action(String value) {
        if ("buy_yes".equals(value)) return "看涨";
        if ("buy_no".equals(value)) return "看跌";
        return "观望";
    }

    private String outcome(String value) {
        if ("traded".equals(value)) return "已执行";
        if ("cooldown".equals(value)) return "冷却期";
        if ("low_confidence".equals(value)) return "置信度拦截";
        if ("trade_failed".equals(value)) return "执行失败";
        if ("market_signal_unavailable".equals(value)) return "行情证据不足";
        if ("metadata_unavailable".equals(value)) return "规则读取失败";
        if ("quote_unavailable".equals(value)) return "行情暂不可用";
        if ("hold".equals(value)) return "保持观望";
        return TextUtils.isEmpty(value) ? "等待结果" : value;
    }

    private String outcomeDetail(BackendApiClient.AiManagedDecisionDTO item) {
        String value = item.outcome;
        if ("traded".equals(value)) return "全部门控通过，后端调用 buyShares 并保存交易记录。";
        if ("cooldown".equals(value)) return "同方向交易仍处于冷却期，本轮不重复追单。";
        if ("low_confidence".equals(value)) return "置信度未达到用户/系统门槛，本轮强制 HOLD。";
        if ("market_signal_unavailable".equals(value)) {
            return "冻结规则所需的 Chainlink 边界报价尚不完整，本轮不会调用模型猜测，也不会交易。"
                    + (TextUtils.isEmpty(item.errorSummary) ? "" : "\n原因：" + item.errorSummary);
        }
        if ("metadata_unavailable".equals(value)) {
            return "暂时无法读取创建时冻结的结算规则，本轮保持观望。"
                    + (TextUtils.isEmpty(item.errorSummary) ? "" : "\n原因：" + item.errorSummary);
        }
        if ("quote_unavailable".equals(value)) {
            return "实时黄金行情暂不可用，本轮保持观望。"
                    + (TextUtils.isEmpty(item.errorSummary) ? "" : "\n原因：" + item.errorSummary);
        }
        if ("hold".equals(value)) return "模型选择观望，或方向一致性检查将动作改为 HOLD。";
        if ("trade_failed".equals(value)) return "判断通过但链上执行失败：" + item.errorSummary;
        return "最终状态：" + outcome(value)
                + (TextUtils.isEmpty(item.errorSummary) ? "" : " · " + item.errorSummary);
    }

    private int outcomeColor(String value) {
        if ("traded".equals(value)) return AiDecisionUi.YES;
        if ("trade_failed".equals(value)) return AiDecisionUi.NO;
        return AiDecisionUi.AMBER;
    }
}
