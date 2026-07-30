package com.example.brokerfi.xc.agent.gold.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.BrokerChainClient;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiDecisionCenterActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout managedContainer;
    private LinearLayout settlementContainer;
    private LinearLayout opportunityContainer;
    private TextView tvStatus;
    private TextView tvRuntimeStatus;
    private TextView tvSubtitle;
    private TextView tvManaged;
    private TextView tvDecisions;
    private TextView tvAudits;
    private TextView tabYES;
    private TextView tabNO;
    private BackendApiClient.AiDecisionCenterDTO data;
    private String opportunitySide = "YES";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_decision_center);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        swipeRefresh = findViewById(R.id.swipe_refresh);
        managedContainer = findViewById(R.id.managed_container);
        settlementContainer = findViewById(R.id.settlement_container);
        opportunityContainer = findViewById(R.id.opportunity_container);
        tvStatus = findViewById(R.id.tv_live_status);
        tvRuntimeStatus = findViewById(R.id.tv_runtime_status);
        tvSubtitle = findViewById(R.id.tv_hero_subtitle);
        tvManaged = findViewById(R.id.tv_stat_managed);
        tvDecisions = findViewById(R.id.tv_stat_decisions);
        tvAudits = findViewById(R.id.tv_stat_audits);
        tabYES = findViewById(R.id.tab_yes);
        tabNO = findViewById(R.id.tab_no);
        swipeRefresh.setColorSchemeColors(AiDecisionUi.BLUE, AiDecisionUi.YES);
        swipeRefresh.setOnRefreshListener(this::load);
        tabYES.setOnClickListener(v -> selectOpportunitySide("YES"));
        tabNO.setOnClickListener(v -> selectOpportunitySide("NO"));
        showLoadingCards();
        load();
    }

    private void load() {
        swipeRefresh.setRefreshing(true);
        updateConnectionStatus("同步中", "同步中", AiDecisionUi.BLUE, 0xFFEFF6FF);
        String privateKey = StorageUtil.getCurrentPrivatekey(this);
        String wallet = TextUtils.isEmpty(privateKey) ? "" : BrokerChainClient.getAddress(privateKey);
        executor.execute(() -> {
            try {
                BackendApiClient.getBaseUrl(this);
                BackendApiClient.AiDecisionCenterDTO loaded =
                        BackendApiClient.fetchAiDecisionCenter(wallet);
                main.post(() -> render(loaded));
            } catch (Exception error) {
                main.post(() -> renderError(error));
            }
        });
    }

    private void render(BackendApiClient.AiDecisionCenterDTO loaded) {
        data = loaded;
        swipeRefresh.setRefreshing(false);
        updateConnectionStatus("已更新", "运行中", AiDecisionUi.YES, 0xFFECFDF5);
        if (loaded.stats != null) {
            tvManaged.setText(loaded.stats.managedMarkets + "\n托管中");
            tvDecisions.setText(loaded.stats.recentDecisions + "\n24h 判断");
            tvAudits.setText(loaded.stats.settledAudits + "\n已复核");
        }
        tvSubtitle.setText("行情、持仓与执行状态已完成同步");
        renderManaged(loaded.managedDecisions);
        renderSettlements(loaded.settlementAudits);
        renderOpportunities();
    }

    private void renderManaged(List<BackendApiClient.AiManagedDecisionDTO> items) {
        managedContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            managedContainer.addView(emptyCard("暂无策略记录",
                    "启用自动托管后，最新判断会显示在这里。"));
            return;
        }
        LinearLayout group = compactGroup();
        Set<String> markets = new HashSet<>();
        int shown = 0;
        for (BackendApiClient.AiManagedDecisionDTO item : items) {
            String key = (item.contractAddress == null ? "" : item.contractAddress.toLowerCase(Locale.US))
                    + ":" + item.gameId;
            if (!markets.add(key)) continue;
            if (shown > 0) group.addView(AiDecisionUi.divider(this));
            group.addView(managedRow(item));
            shown++;
            if (shown == 4) break;
        }
        managedContainer.addView(group);
    }

    private void renderSettlements(List<BackendApiClient.AiSettlementAuditDTO> items) {
        settlementContainer.removeAllViews();
        if (items == null || items.isEmpty()) {
            settlementContainer.addView(emptyCard("暂无复核记录",
                    "新到期市场完成结算后，结果与依据会显示在这里。"));
            return;
        }
        LinearLayout group = compactGroup();
        for (int i = 0; i < Math.min(3, items.size()); i++) {
            BackendApiClient.AiSettlementAuditDTO item = items.get(i);
            if (i > 0) group.addView(AiDecisionUi.divider(this));
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, AiDecisionUi.dp(this, 12), 0, AiDecisionUi.dp(this, 12));
            LinearLayout header = horizontal();
            TextView title = AiDecisionUi.text(this, safeTitle(item.marketTitle, item.gameId),
                    14, AiDecisionUi.NAVY, true);
            header.addView(title, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            header.addView(AiDecisionUi.pill(this, item.finalDecision,
                    "YES".equals(item.finalDecision) ? AiDecisionUi.YES :
                            ("NO".equals(item.finalDecision) ? AiDecisionUi.NO : AiDecisionUi.AMBER),
                    "YES".equals(item.finalDecision) ? 0xFFECFDF5 :
                            ("NO".equals(item.finalDecision) ? 0xFFFFF1F2 : 0xFFFFF7ED)));
            card.addView(header);
            card.addView(AiDecisionUi.text(this,
                    String.format(Locale.getDefault(), "%.0f%% 置信度 · %.0f%% 结果一致 · %s",
                            item.finalConfidence * 100, item.consensusRatio * 100,
                            item.resolvedAt == null ? "时间未知" : item.resolvedAt.substring(0,
                                    Math.min(10, item.resolvedAt.length()))),
                    12, AiDecisionUi.MUTED, false), marginTop(7));
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, AiSettlementAuditActivity.class);
                intent.putExtra("audit", item);
                startActivity(intent);
            });
            group.addView(card);
        }
        settlementContainer.addView(group);
    }

    private View managedRow(BackendApiClient.AiManagedDecisionDTO item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, AiDecisionUi.dp(this, 12), 0, AiDecisionUi.dp(this, 12));

        LinearLayout titleRow = horizontal();
        TextView title = AiDecisionUi.text(this, safeTitle(item.marketTitle, item.gameId),
                14, AiDecisionUi.NAVY, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView time = AiDecisionUi.text(this, formatUnix(item.observedAt), 11,
                AiDecisionUi.MUTED, false);
        titleRow.addView(time);
        row.addView(titleRow);

        LinearLayout statusRow = horizontal();
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = AiDecisionUi.dp(this, 9);
        statusRow.addView(AiDecisionUi.pill(this, actionText(item.action),
                actionColor(item.action), actionFill(item.action)), statusParams);
        boolean targetsNO = "buy_no".equals(item.action) || "sell_yes".equals(item.action);
        double sideEdge = targetsNO
                ? -item.probabilityEdgePercent : item.probabilityEdgePercent;
        String advantage = Math.abs(sideEdge) < .01 || "hold".equals(item.action)
                ? ""
                : String.format(Locale.getDefault(), " · %s 优势 %.1f%%",
                targetsNO ? "NO" : "YES", Math.max(0, sideEdge));
        TextView detail = AiDecisionUi.text(this,
                String.format(Locale.getDefault(), "%s · 置信度 %.0f%%%s",
                        outcomeText(item.outcome), item.confidence * 100, advantage),
                12, AiDecisionUi.MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        detailParams.leftMargin = AiDecisionUi.dp(this, 10);
        detailParams.topMargin = AiDecisionUi.dp(this, 9);
        statusRow.addView(detail, detailParams);
        TextView chevron = AiDecisionUi.text(this, "›", 22, 0xFF94A3B8, false);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chevronParams.leftMargin = AiDecisionUi.dp(this, 4);
        chevronParams.topMargin = AiDecisionUi.dp(this, 3);
        statusRow.addView(chevron, chevronParams);
        row.addView(statusRow);

        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, AiDecisionTraceActivity.class);
            intent.putExtra("decision", item);
            startActivity(intent);
        });
        return row;
    }

    private void renderOpportunities() {
        opportunityContainer.removeAllViews();
        List<BackendApiClient.AiOpportunityDTO> filtered = new ArrayList<>();
        if (data != null && data.opportunities != null) {
            for (BackendApiClient.AiOpportunityDTO item : data.opportunities) {
                if (opportunitySide.equalsIgnoreCase(item.side)) filtered.add(item);
            }
        }
        if (filtered.isEmpty()) {
            opportunityContainer.addView(emptyCard("暂无 " + opportunitySide + " 方向机会",
                    "当前判断尚未达到策略门槛。"));
            return;
        }
        LinearLayout group = compactGroup();
        for (int index = 0; index < filtered.size(); index++) {
            BackendApiClient.AiOpportunityDTO item = filtered.get(index);
            if (index > 0) group.addView(AiDecisionUi.divider(this));
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(0, AiDecisionUi.dp(this, 12), 0, AiDecisionUi.dp(this, 12));
            LinearLayout header = horizontal();
            int color = "YES".equalsIgnoreCase(item.side) ? AiDecisionUi.YES : AiDecisionUi.NO;
            int fill = "YES".equalsIgnoreCase(item.side) ? 0xFFECFDF5 : 0xFFFFF1F2;
            header.addView(AiDecisionUi.pill(this, item.side, color, fill));
            TextView edge = AiDecisionUi.text(this,
                    String.format(Locale.getDefault(), "优势 +%.1f%%", item.edgePercent),
                    13, color, true);
            edge.setGravity(Gravity.END);
            header.addView(edge, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            card.addView(header);
            card.addView(AiDecisionUi.text(this, safeTitle(item.marketTitle, item.gameId),
                    15, AiDecisionUi.NAVY, true), marginTop(12));
            card.addView(AiDecisionUi.text(this,
                    String.format(Locale.getDefault(), "判断 %.1f%%  ·  市场 %.1f%%  ·  置信度 %.0f%%",
                            item.estimatedProbability * 100, item.marketProbability * 100,
                            item.confidence * 100),
                    12, AiDecisionUi.MUTED, false), marginTop(8));
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, GoldMarketDetailActivity.class);
                intent.putExtra("GAME_ID", item.gameId);
                intent.putExtra("CONTRACT_ADDRESS", item.contractAddress);
                startActivity(intent);
            });
            group.addView(card);
        }
        opportunityContainer.addView(group);
    }

    private void selectOpportunitySide(String side) {
        opportunitySide = side;
        boolean yes = "YES".equals(side);
        tabYES.setBackgroundResource(yes ? R.drawable.bg_ai_center_segment_active :
                R.drawable.bg_ai_center_segment_idle);
        tabNO.setBackgroundResource(yes ? R.drawable.bg_ai_center_segment_idle :
                R.drawable.bg_ai_center_segment_active);
        tabYES.setTextColor(yes ? 0xFFFFFFFF : AiDecisionUi.MUTED);
        tabNO.setTextColor(yes ? AiDecisionUi.MUTED : 0xFFFFFFFF);
        renderOpportunities();
    }

    private void renderError(Exception error) {
        swipeRefresh.setRefreshing(false);
        updateConnectionStatus("连接中断", "待连接", AiDecisionUi.NO, 0xFFFFF1F2);
        managedContainer.removeAllViews();
        settlementContainer.removeAllViews();
        opportunityContainer.removeAllViews();
        managedContainer.addView(emptyCard("暂时无法读取策略记录",
                "服务接口不可用，请检查后端版本后下拉刷新。"));
        settlementContainer.addView(emptyCard("暂时无法读取复核结果", "连接恢复后自动更新。"));
        opportunityContainer.addView(emptyCard("暂时无法更新市场机会", "连接恢复后自动筛选。"));
        Toast.makeText(this, "投研中心连接失败", Toast.LENGTH_SHORT).show();
    }

    private void updateConnectionStatus(String headerText, String runtimeText,
                                        int color, int fill) {
        styleStatus(tvStatus, headerText, color, fill);
        styleStatus(tvRuntimeStatus, runtimeText, color, fill);
    }

    private void styleStatus(TextView view, String text, int color, int fill) {
        view.setText(text);
        view.setTextColor(color);
        view.setBackground(AiDecisionUi.rounded(fill, 0, 999, this));
    }

    private void showLoadingCards() {
        managedContainer.addView(emptyCard("正在更新近期策略…", "请稍候"));
        settlementContainer.addView(emptyCard("正在更新结算结果…", "请稍候"));
        opportunityContainer.addView(emptyCard("正在筛选市场机会…", "请稍候"));
    }

    private View emptyCard(String title, String body) {
        LinearLayout row = compactGroup();
        row.setContentDescription(title + (TextUtils.isEmpty(body) ? "" : "。" + body));
        TextView text = AiDecisionUi.text(this, title, 13, AiDecisionUi.MUTED, false);
        text.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, AiDecisionUi.dp(this, 44)));
        return row;
    }

    private LinearLayout compactGroup() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(AiDecisionUi.dp(this, 12), 0,
                AiDecisionUi.dp(this, 12), 0);
        group.setBackground(AiDecisionUi.rounded(
                0xFFF8FAFC, 0xFFE2E8F0, 12, this));
        return group;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = AiDecisionUi.dp(this, dp);
        return params;
    }

    private String safeTitle(String value, int gameId) {
        return TextUtils.isEmpty(value) ? "未命名博弈池" : value.trim();
    }

    private String formatUnix(long seconds) {
        return seconds <= 0 ? "时间未知" : timeFormat.format(new Date(seconds * 1000L));
    }

    private String actionText(String action) {
        if ("buy_yes".equals(action)) return "买入 YES";
        if ("buy_no".equals(action)) return "买入 NO";
        if ("sell_yes".equals(action)) return "减持 YES";
        if ("sell_no".equals(action)) return "减持 NO";
        return "观望";
    }

    private int actionColor(String action) {
        if ("buy_yes".equals(action) || "sell_no".equals(action)) return AiDecisionUi.YES;
        if ("buy_no".equals(action) || "sell_yes".equals(action)) return AiDecisionUi.NO;
        return AiDecisionUi.AMBER;
    }

    private int actionFill(String action) {
        if ("buy_yes".equals(action) || "sell_no".equals(action)) return 0xFFECFDF5;
        if ("buy_no".equals(action) || "sell_yes".equals(action)) return 0xFFFFF1F2;
        return 0xFFFFF7ED;
    }

    private String outcomeText(String outcome) {
        if ("traded".equals(outcome)) return "已执行";
        if ("cooldown".equals(outcome)) return "冷却期";
        if ("low_confidence".equals(outcome)) return "未达门槛";
        if ("trade_failed".equals(outcome)) return "执行失败";
        if ("market_signal_unavailable".equals(outcome)) return "行情证据不足";
        if ("metadata_unavailable".equals(outcome)) return "规则读取失败";
        if ("quote_unavailable".equals(outcome)) return "行情暂不可用";
        if ("hold".equals(outcome)) return "暂不操作";
        return TextUtils.isEmpty(outcome) ? "等待结果" : outcome;
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
