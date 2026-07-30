package com.example.brokerfi.xc.agent.gold.view;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.PinataClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCardPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketChartPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketDetailPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketResearchAnalysisPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketOptionText;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketStatusStyle;
import com.example.brokerfi.xc.agent.gold.viewmodel.GoldMarketDetailViewModel;
import com.example.brokerfi.xc.agent.ai.DeepSeekClient;
import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import android.widget.ImageView;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GoldMarketDetailActivity extends AppCompatActivity {
    private static final int REQUEST_AI_MANAGED_SETTINGS = 4201;
    private static final String MARKET_AI_LOADING_MESSAGE = "投研分析仍在生成中";
    private static final long DATA_REFRESH_INTERVAL_MS = 15_000L;
    private static final int YES_COLOR = GoldMarketStatusStyle.YES_TEXT;
    private static final int YES_BACKGROUND = GoldMarketStatusStyle.YES_BACKGROUND;
    private static final int NO_COLOR = GoldMarketStatusStyle.NO_TEXT;
    private static final int NO_BACKGROUND = GoldMarketStatusStyle.NO_BACKGROUND;
    private static final int MUTED_TEXT = 0xFF64748B;
    private static final int MUTED_BACKGROUND = 0xFFF8FAFC;
    private static final int MUTED_BORDER = 0xFFE2E8F0;

    private GoldMarketDetailViewModel viewModel;
    private GoldMarketRepository.GameModel currentGame;
    private int gameId;
    private String contractAddress;

    private TextView tvMarketDesc, tvMarketTime, tvMarketCondition, btnMarketRuleToggle;
    private TextView tvUpLabel, tvDownLabel, tvUpPct, tvDownPct, tvPool, tvCountdown;
    private TextView tvHoldingsEmpty, tvHoldingYesLabel, tvHoldingYesAmount, tvHoldingNoLabel, tvHoldingNoAmount;
    private TextView btnSellYes, btnSellNo;
    private View cardMetricYes, cardMetricNo, cardHoldingYes, cardHoldingNo;
    private TextView tvMarketAiStatus, tvMarketAiSummary, tvMarketAiStance, tvMarketAiRisk;
    private TextView tvMarketAiDrivers, tvMarketAiActions, tvMarketAiDisclaimer, btnMarketAiRefresh;
    private ImageView ivMarketIcon;
    private View barUp, barDown, btnClaimReward, cardMarketAi, layoutAiDetails;
    private View layoutMarketAiChips, layoutChartContainer, layoutMarketRuleDetails;
    private android.widget.ProgressBar progressMarketAi;
    private LineChart lineChart;
    private TextView tvTradeBucketHint;
    private final Map<String, TextView> chartRangeViews = new LinkedHashMap<>();
    private String selectedChartRange = "1d";
    private int chartRequestedGameId = -1;
    private androidx.appcompat.widget.SwitchCompat switchAiManaged;
    private TextView tvAiManagedSummary, tvAiManagedAction;
    private TextView tvRuleStrategyTitle, tvRuleStrategySummary, tvRuleStrategyAction;
    private View btnAiManagedSettings, btnCustomStrategy;
    private BackendApiClient.AiManagedConfig aiManagedConfig =
            BackendApiClient.AiManagedConfig.defaults();
    private boolean suppressAiManagedListener;
    private SwipeRefreshLayout swipeRefresh;
    private NestedScrollView detailScroll;
    private View inlineTradePanel;
    private GoldTradeDialog tradePanelController;

    private boolean aiExpanded = false;
    private String marketAiSummary = "";
    private String marketAiUnavailableMessage = "";
    private boolean marketAiHasResult = false;
    private boolean marketRulesExpanded = false;
    private boolean destroyed = false;
    private boolean requestInFlight = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Handler dataRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable dataRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            viewModel.refreshGameInfo(gameId, resolveContractAddress());
            viewModel.loadChartData(gameId, selectedChartRange);
            dataRefreshHandler.postDelayed(this, DATA_REFRESH_INTERVAL_MS);
        }
    };
    private final Runnable countdownRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            updateCountdown();
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_market_detail);
        destroyed = false;
        DeepSeekClient.init(this);
        gameId = getIntent().getIntExtra("GAME_ID", 1);
        contractAddress = getIntent().getStringExtra("CONTRACT_ADDRESS");
        viewModel = new ViewModelProvider(this).get(GoldMarketDetailViewModel.class);
        initViews();
        observeViewModel();
        viewModel.loadGameInfo(gameId, contractAddress);
        timerHandler.post(countdownRunnable);
    }

    private void observeViewModel() {
        viewModel.getCurrentGame().observe(this, game -> {
            currentGame = game;
            updateUI();
            bindOrUpdateTradePanel();
        });
        viewModel.getMarketAiSummary().observe(this, this::showMarketAiSummary);
        viewModel.getIsLoading().observe(this, loading -> swipeRefresh.setRefreshing(loading));
        viewModel.getTxStatus().observe(this, status -> {
            if (status != null) Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        });
        viewModel.getError().observe(this, err -> {
            if (err != null) {
                Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
                if (err.startsWith("AI error:")) {
                    String message = err.substring("AI error:".length()).trim();
                    showMarketAiUnavailable("暂不可用", message);
                }
            }
        });
        viewModel.getTradeError().observe(this, err -> {
            if (err != null && !err.trim().isEmpty()) {
                showTradeErrorDialog(err);
            }
        });

        viewModel.getChartData().observe(this, data -> {
            if (data == null || data.gameId != gameId || !selectedChartRange.equals(data.range)) return;
            setupHistoryChart(data);
        });
        viewModel.getAiManagedConfig().observe(this, config -> {
            if (config == null) return;
            aiManagedConfig = config;
            updateAiManagedSummary();
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        timerHandler.removeCallbacks(countdownRunnable);
        dataRefreshHandler.removeCallbacks(dataRefreshRunnable);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        dataRefreshHandler.removeCallbacks(dataRefreshRunnable);
        dataRefreshHandler.postDelayed(dataRefreshRunnable, DATA_REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onStop() {
        dataRefreshHandler.removeCallbacks(dataRefreshRunnable);
        super.onStop();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvMarketDesc = findViewById(R.id.tv_market_desc);
        tvMarketTime = findViewById(R.id.tv_market_time);
        tvMarketCondition = findViewById(R.id.tv_market_condition);
        btnMarketRuleToggle = findViewById(R.id.btn_market_rule_toggle);
        layoutMarketRuleDetails = findViewById(R.id.layout_market_rule_details);
        tvUpLabel = findViewById(R.id.tv_up_label);
        tvDownLabel = findViewById(R.id.tv_down_label);
        tvUpPct = findViewById(R.id.tv_up_pct);
        tvDownPct = findViewById(R.id.tv_down_pct);
        tvPool = findViewById(R.id.tv_pool);
        tvCountdown = findViewById(R.id.tv_countdown);
        tvHoldingsEmpty = findViewById(R.id.tv_holdings_empty);
        tvHoldingYesLabel = findViewById(R.id.tv_holding_yes_label);
        tvHoldingYesAmount = findViewById(R.id.tv_holding_yes_amount);
        tvHoldingNoLabel = findViewById(R.id.tv_holding_no_label);
        tvHoldingNoAmount = findViewById(R.id.tv_holding_no_amount);
        btnSellYes = findViewById(R.id.btn_sell_yes);
        btnSellNo = findViewById(R.id.btn_sell_no);
        detailScroll = findViewById(R.id.detail_scroll);
        inlineTradePanel = findViewById(R.id.inline_trade_panel);
        cardMetricYes = findViewById(R.id.card_metric_yes);
        cardMetricNo = findViewById(R.id.card_metric_no);
        cardHoldingYes = findViewById(R.id.card_holding_yes);
        cardHoldingNo = findViewById(R.id.card_holding_no);

        tvMarketAiStatus = findViewById(R.id.tv_market_ai_status);
        tvMarketAiStatus.setText("分析 ›");
        tvMarketAiSummary = findViewById(R.id.tv_market_ai_summary);
        tvMarketAiSummary.setText("综合行情、市场概率与结算风险");
        tvMarketAiStance = findViewById(R.id.tv_market_ai_stance);
        tvMarketAiRisk = findViewById(R.id.tv_market_ai_risk);
        tvMarketAiDrivers = findViewById(R.id.tv_market_ai_drivers);
        tvMarketAiActions = findViewById(R.id.tv_market_ai_actions);
        tvMarketAiDisclaimer = findViewById(R.id.tv_market_ai_disclaimer);
        btnMarketAiRefresh = findViewById(R.id.btn_market_ai_refresh);
        progressMarketAi = findViewById(R.id.progress_market_ai);
        ivMarketIcon = findViewById(R.id.iv_market_detail_icon);
        switchAiManaged = findViewById(R.id.switch_ai_managed);
        tvAiManagedSummary = findViewById(R.id.tv_ai_managed_summary);
        tvAiManagedAction = findViewById(R.id.tv_ai_managed_action);
        tvRuleStrategyTitle = findViewById(R.id.tv_rule_strategy_title);
        tvRuleStrategySummary = findViewById(R.id.tv_rule_strategy_summary);
        tvRuleStrategyAction = findViewById(R.id.tv_rule_strategy_action);
        btnAiManagedSettings = findViewById(R.id.btn_ai_managed_settings);
        btnCustomStrategy = findViewById(R.id.btn_custom_strategy);
        barUp = findViewById(R.id.bar_up);
        barDown = findViewById(R.id.bar_down);
        cardMarketAi = findViewById(R.id.card_market_ai);
        layoutAiDetails = findViewById(R.id.layout_ai_details);
        layoutMarketAiChips = findViewById(R.id.layout_market_ai_chips);
        layoutChartContainer = findViewById(R.id.layout_chart_container);
        lineChart = findViewById(R.id.line_chart);
        tvTradeBucketHint = findViewById(R.id.tv_trade_bucket_hint);
        lineChart.setRenderer(new GoldMarketTradeRenderer(
                lineChart, lineChart.getAnimator(), lineChart.getViewPortHandler()));
        chartRangeViews.put("1h", findViewById(R.id.chart_range_1h));
        chartRangeViews.put("1d", findViewById(R.id.chart_range_1d));
        chartRangeViews.put("1w", findViewById(R.id.chart_range_1w));
        chartRangeViews.put("all", findViewById(R.id.chart_range_all));
        for (Map.Entry<String, TextView> entry : chartRangeViews.entrySet()) {
            entry.getValue().setOnClickListener(v -> selectChartRange(entry.getKey()));
        }
        updateChartRangeStyles();
        btnClaimReward = findViewById(R.id.btn_claim_reward);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadGameInfo(gameId, resolveContractAddress());
            viewModel.loadChartData(gameId, selectedChartRange);
        });

        btnSellYes.setOnClickListener(
                v -> focusTradePanel(GoldTradeDialog.Side.SELL, 0));
        btnSellNo.setOnClickListener(
                v -> focusTradePanel(GoldTradeDialog.Side.SELL, 1));
        btnMarketRuleToggle.setOnClickListener(v -> toggleMarketRules());
        cardMarketAi.setOnClickListener(v -> toggleAiDetails());
        tvMarketAiStatus.setOnClickListener(v -> toggleAiDetails());
        btnMarketAiRefresh.setOnClickListener(v -> refreshMarketAiAnalysis());
        switchAiManaged.setOnCheckedChangeListener((btn, isChecked) -> {
            if (suppressAiManagedListener || currentGame == null
                    || isAiStrategyEnabled() == isChecked) return;
            if (isChecked) {
                // Enabling always goes through the guardrail editor so users
                // explicitly review the limits before granting execution access.
                suppressAiManagedListener = true;
                switchAiManaged.setChecked(false);
                suppressAiManagedListener = false;
                openAiManagedSettings();
            } else {
                viewModel.toggleAiManaged(gameId, resolveContractAddress(), false);
            }
        });
        btnAiManagedSettings.setOnClickListener(v -> openAiManagedSettings());
        btnCustomStrategy.setOnClickListener(v -> {
            if (currentGame == null) return;
            startActivity(GoldCustomStrategyActivity.createManagementIntent(
                    this, currentGame.id, resolveContractAddress(),
                    currentGame.desc));
        });
        btnClaimReward.setOnClickListener(v -> claimReward());
    }

    private void selectChartRange(String range) {
        if (range == null || range.equals(selectedChartRange)
                && viewModel.getChartData().getValue() != null) return;
        selectedChartRange = range;
        updateChartRangeStyles();
        layoutChartContainer.setVisibility(View.VISIBLE);
        lineChart.clear();
        lineChart.setNoDataText("正在加载份额历史…");
        viewModel.loadChartData(gameId, selectedChartRange);
    }

    private void updateChartRangeStyles() {
        for (Map.Entry<String, TextView> entry : chartRangeViews.entrySet()) {
            boolean selected = entry.getKey().equals(selectedChartRange);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(12));
            background.setColor(selected ? 0xFF0F172A : Color.TRANSPARENT);
            entry.getValue().setBackground(background);
            entry.getValue().setTextColor(selected ? Color.WHITE : 0xFF64748B);
        }
    }

    private void updateUI() {
        if (currentGame == null) return;
        String title = currentGame.desc != null && !currentGame.desc.isEmpty() ? currentGame.desc : "博弈池 #" + currentGame.id;
        String condition = GoldMarketDetailPresenter.formatResolutionRule(currentGame.condition);
        GoldMarketDetailPresenter.HeroText hero =
                GoldMarketDetailPresenter.heroText(title, currentGame.deadlineSec);
        GoldMarketTitleFitter.apply(tvMarketDesc, GoldMarketTextStyler.style(
                GoldMarketCardPresenter.displayTitle(
                        title, currentGame.condition, currentGame.deadlineSec), true));
        tvMarketTime.setText(hero.timeSubtitle == null || hero.timeSubtitle.trim().isEmpty()
                ? "结算规则已冻结" : hero.timeSubtitle + " · 规则已冻结");
        tvMarketTime.setVisibility(View.VISIBLE);
        tvMarketCondition.setText(styleMarketText(condition, false));
        
        int templateIcon = GoldMarketTemplateIcon.forMarket(
                currentGame.avatarUrl, title, currentGame.condition);
        if (templateIcon != 0) {
            ivMarketIcon.setImageResource(templateIcon);
        } else if (currentGame.avatarUrl != null && !currentGame.avatarUrl.isEmpty()) {
            Glide.with(this).load(PinataClient.IPFS_GATEWAY + currentGame.avatarUrl).placeholder(R.drawable.apartment_icon).into(ivMarketIcon);
        } else {
            ivMarketIcon.setImageResource(R.drawable.apartment_icon);
        }

        suppressAiManagedListener = true;
        switchAiManaged.setChecked(isAiStrategyEnabled());
        suppressAiManagedListener = false;
        updateAiManagedSummary();

        btnClaimReward.setVisibility(shouldShowClaimReward() ? View.VISIBLE : View.GONE);
        if (currentGame.virtualReserves != null && currentGame.virtualReserves.size() >= 2) {
            BigInteger res0 = currentGame.virtualReserves.get(0);
            BigInteger res1 = currentGame.virtualReserves.get(1);
            BigInteger total = res0.add(res1);
            if (total.compareTo(BigInteger.ZERO) > 0) {
                float p0 = (float) (res0.doubleValue() / total.doubleValue() * 100);
                float p1 = 100 - p0;
                tvUpPct.setText(String.format(Locale.getDefault(), "%.1f%%", p0));
                tvDownPct.setText(String.format(Locale.getDefault(), "%.1f%%", p1));
                LinearLayout.LayoutParams lp0 = (LinearLayout.LayoutParams) barUp.getLayoutParams();
                lp0.width = 0; lp0.weight = p0; barUp.setLayoutParams(lp0);
                LinearLayout.LayoutParams lp1 = (LinearLayout.LayoutParams) barDown.getLayoutParams();
                lp1.width = 0; lp1.weight = p1; barDown.setLayoutParams(lp1);
            }
        }
        tvPool.setText("流动性 · "
                + GoldNoteMarketActivity.formatBkc(currentGame.totalPool) + " BKC");
        updateHoldingsUI();
        if (chartRequestedGameId != currentGame.id) {
            chartRequestedGameId = currentGame.id;
            layoutChartContainer.setVisibility(View.VISIBLE);
            lineChart.setNoDataText("正在加载份额历史…");
            viewModel.loadChartData(currentGame.id, selectedChartRange);
        }
        updateCountdown();
    }

    private CharSequence styleMarketText(String text, boolean title) {
        return GoldMarketTextStyler.style(text, title);
    }

    private void toggleMarketRules() {
        marketRulesExpanded = !marketRulesExpanded;
        layoutMarketRuleDetails.setVisibility(
                marketRulesExpanded ? View.VISIBLE : View.GONE);
        btnMarketRuleToggle.setText(
                marketRulesExpanded ? "收起规则⌃" : "查看规则⌄");
    }

    private void updateHoldingsUI() {
        if (currentGame.myShares == null || currentGame.myShares.size() < 2) {
            tvHoldingsEmpty.setVisibility(View.VISIBLE);
            cardHoldingYes.setVisibility(View.GONE);
            cardHoldingNo.setVisibility(View.GONE);
            return;
        }

        BigInteger sYes = currentGame.myShares.get(0);
        BigInteger sNo = currentGame.myShares.get(1);
        boolean hasYes = sYes != null && sYes.signum() > 0;
        boolean hasNo = sNo != null && sNo.signum() > 0;

        if (!hasYes && !hasNo) {
            tvHoldingsEmpty.setVisibility(View.VISIBLE);
            cardHoldingYes.setVisibility(View.GONE);
            cardHoldingNo.setVisibility(View.GONE);
        } else {
            tvHoldingsEmpty.setVisibility(View.GONE);
            
            if (hasYes) {
                cardHoldingYes.setVisibility(View.VISIBLE);
                String name = optionName(0);
                tvHoldingYesLabel.setText(name);
                tvHoldingYesAmount.setText(GoldNoteMarketActivity.formatShareAmount(sYes) + " 份额");
                tvHoldingYesLabel.setTextColor(YES_COLOR);
                tvHoldingYesAmount.setTextColor(YES_COLOR);
                styleHoldingCard(cardHoldingYes, true);
            } else {
                cardHoldingYes.setVisibility(View.GONE);
            }

            if (hasNo) {
                cardHoldingNo.setVisibility(View.VISIBLE);
                String name = optionName(1);
                tvHoldingNoLabel.setText(name);
                tvHoldingNoAmount.setText(GoldNoteMarketActivity.formatShareAmount(sNo) + " 份额");
                tvHoldingNoLabel.setTextColor(NO_COLOR);
                tvHoldingNoAmount.setTextColor(NO_COLOR);
                styleHoldingCard(cardHoldingNo, false);
            } else {
                cardHoldingNo.setVisibility(View.GONE);
            }
        }
    }

    private void setupHistoryChart(GoldMarketDetailViewModel.ChartData rawData) {
        GoldMarketChartPresenter.ChartModel model =
                GoldMarketChartPresenter.prepare(rawData.history, rawData.trades);
        if (model.shares.isEmpty()) {
            layoutChartContainer.setVisibility(View.GONE);
            return;
        }
        layoutChartContainer.setVisibility(View.VISIBLE);

        List<Entry> yesEntries = new ArrayList<>();
        List<Entry> noEntries = new ArrayList<>();
        for (GoldMarketChartPresenter.SharePoint point : model.shares) {
            float x = model.xOf(point.timestampSec);
            yesEntries.add(new Entry(x, point.yesShare, point));
            noEntries.add(new Entry(x, point.noShare, point));
        }

        LineDataSet setYes = new LineDataSet(yesEntries, GoldMarketOptionText.chartLabel(0));
        setYes.setColor(0xFF059669);
        setYes.setLineWidth(2.2f);
        setYes.setDrawCircles(false);
        setYes.setDrawValues(false);
        setYes.setMode(LineDataSet.Mode.LINEAR);
        setYes.setDrawFilled(true);
        setYes.setFillColor(0xFF059669);
        setYes.setFillAlpha(20);
        setYes.setHighlightEnabled(true);
        setYes.setHighLightColor(0xFF94A3B8);
        setYes.enableDashedHighlightLine(4f, 3f, 0f);

        LineDataSet setNo = new LineDataSet(noEntries, GoldMarketOptionText.chartLabel(1));
        setNo.setColor(0xFFE11D48);
        setNo.setLineWidth(2.2f);
        setNo.setDrawCircles(false);
        setNo.setDrawValues(false);
        setNo.setMode(LineDataSet.Mode.LINEAR);
        setNo.setDrawFilled(false);
        setNo.setHighlightEnabled(true);
        setNo.setHighLightColor(0xFF94A3B8);
        setNo.enableDashedHighlightLine(4f, 3f, 0f);

        long visibleSpanSec = model.shares.get(model.shares.size() - 1).timestampSec
                - model.shares.get(0).timestampSec;
        GoldMarketChartPresenter.TradeAggregation aggregation =
                GoldMarketChartPresenter.aggregateTrades(
                        model.trades, rawData.range, visibleSpanSec);
        String rangeLabel = rawData.range == null
                ? "1D" : rawData.range.toUpperCase(Locale.US);
        tvTradeBucketHint.setText(String.format(Locale.US, "%s · %s/点",
                rangeLabel, GoldMarketChartPresenter.bucketLabel(
                        aggregation.bucketSeconds).replace(" ", "")));

        List<Entry> manualYes = new ArrayList<>();
        List<Entry> manualNo = new ArrayList<>();
        List<Entry> aiYes = new ArrayList<>();
        List<Entry> aiNo = new ArrayList<>();
        List<Entry> gridYes = new ArrayList<>();
        List<Entry> gridNo = new ArrayList<>();
        List<Entry> martingaleYes = new ArrayList<>();
        List<Entry> martingaleNo = new ArrayList<>();
        for (GoldMarketChartPresenter.TradePoint trade : aggregation.trades) {
            Entry entry = new Entry(model.xOf(trade.timestampSec), trade.marketShare, trade);
            boolean yes = trade.optionId == 0;
            switch (trade.executionSource) {
                case "ai":
                    (yes ? aiYes : aiNo).add(entry);
                    break;
                case "grid":
                    (yes ? gridYes : gridNo).add(entry);
                    break;
                case "martingale":
                    (yes ? martingaleYes : martingaleNo).add(entry);
                    break;
                default:
                    (yes ? manualYes : manualNo).add(entry);
                    break;
            }
        }

        List<ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(setYes);
        dataSets.add(setNo);
        addTradeDataSet(dataSets, manualYes);
        addTradeDataSet(dataSets, manualNo);
        addTradeDataSet(dataSets, aiYes);
        addTradeDataSet(dataSets, aiNo);
        addTradeDataSet(dataSets, gridYes);
        addTradeDataSet(dataSets, gridNo);
        addTradeDataSet(dataSets, martingaleYes);
        addTradeDataSet(dataSets, martingaleNo);

        LineData data = new LineData(dataSets);
        lineChart.setData(data);

        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setTouchEnabled(true);
        lineChart.setDragEnabled(true);
        lineChart.setScaleXEnabled(true);
        lineChart.setScaleYEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setHighlightPerDragEnabled(true);
        // Reserve a clean annotation lane above the curves so personal purchase
        // badges never cover the market-share path, including near 100%.
        lineChart.setExtraOffsets(8, 44, 10, 8);
        lineChart.getLegend().setEnabled(false);
        lineChart.setMarker(new GoldMarketChartMarkerView(this));

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(0xFF94A3B8);
        xAxis.setAvoidFirstLastClipping(true);
        configureChartScale(rawData.range, model, xAxis);

        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisLeft().setDrawAxisLine(false);
        lineChart.getAxisLeft().setGridColor(0xFFF1F5F9);
        lineChart.getAxisLeft().setTextColor(0xFF94A3B8);
        lineChart.getAxisLeft().setAxisMaximum(100f);
        lineChart.getAxisLeft().setAxisMinimum(0f);
        lineChart.getAxisLeft().setLabelCount(5);
        lineChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override public String getFormattedValue(float value) {
                return String.format(Locale.US, "%.0f%%", value);
            }
        });

        lineChart.fitScreen();
        lineChart.animateX(chartAnimationDuration(rawData.range));
        lineChart.invalidate();
    }

    private void configureChartScale(String range,
                                     GoldMarketChartPresenter.ChartModel model,
                                     XAxis xAxis) {
        String normalized = range == null ? "1d" : range.toLowerCase(Locale.US);
        long windowSeconds;
        float granularityMinutes;
        int labelCount;
        String pattern;
        switch (normalized) {
            case "1h":
                windowSeconds = 60L * 60L;
                granularityMinutes = 15f;
                labelCount = 5;
                pattern = "HH:mm";
                break;
            case "1w":
                windowSeconds = 7L * 24L * 60L * 60L;
                granularityMinutes = 2f * 24f * 60f;
                labelCount = 4;
                pattern = "MM-dd";
                break;
            case "all":
                windowSeconds = 0L;
                granularityMinutes = 1f;
                labelCount = 4;
                long span = model.shares.get(model.shares.size() - 1).timestampSec
                        - model.shares.get(0).timestampSec;
                pattern = span <= 2L * 24L * 60L * 60L ? "HH:mm" : "MM-dd";
                break;
            case "1d":
            default:
                windowSeconds = 24L * 60L * 60L;
                granularityMinutes = 6f * 60f;
                labelCount = 5;
                pattern = "HH:mm";
                break;
        }

        xAxis.setLabelCount(labelCount, false);
        xAxis.setGranularityEnabled(true);
        xAxis.setGranularity(granularityMinutes);
        final java.text.SimpleDateFormat formatter =
                new java.text.SimpleDateFormat(pattern, Locale.US);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                long timestamp = model.baseTimestampSec + Math.round(value * 60f);
                return formatter.format(new Date(timestamp * 1000L));
            }
        });

        if (windowSeconds > 0L) {
            long latestSample = model.shares.get(model.shares.size() - 1).timestampSec;
            long endTimestamp = Math.max(latestSample, System.currentTimeMillis() / 1000L);
            xAxis.setAxisMinimum(model.xOf(endTimestamp - windowSeconds));
            xAxis.setAxisMaximum(model.xOf(endTimestamp));
        } else {
            xAxis.resetAxisMinimum();
            xAxis.resetAxisMaximum();
        }
    }

    private int chartAnimationDuration(String range) {
        if ("1h".equalsIgnoreCase(range)) return 220;
        if ("1d".equalsIgnoreCase(range)) return 280;
        if ("1w".equalsIgnoreCase(range)) return 340;
        return 380;
    }

    private void addTradeDataSet(List<ILineDataSet> target, List<Entry> entries) {
        if (entries.isEmpty()) return;
        LineDataSet set = new LineDataSet(entries, null);
        set.setColor(Color.TRANSPARENT);
        set.setLineWidth(0.2f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setDrawFilled(false);
        set.setHighlightEnabled(true);
        target.add(set);
    }

    private void openAiManagedSettings() {
        String title = currentGame == null ? "当前博弈池"
                : GoldMarketCardPresenter.displayTitle(currentGame.desc, currentGame.condition,
                currentGame.deadlineSec);
        Intent intent = GoldAiManagedSettingsActivity.createIntent(
                this, gameId, resolveContractAddress(), title, aiManagedConfig);
        startActivityForResult(intent, REQUEST_AI_MANAGED_SETTINGS);
    }

    private void updateAiManagedSummary() {
        if (tvAiManagedSummary == null || aiManagedConfig == null) return;
        String strategyType = normalizeStrategyType(aiManagedConfig.strategyType);
        boolean aiEnabled = aiManagedConfig.enabled && "ai".equals(strategyType);
        suppressAiManagedListener = true;
        switchAiManaged.setChecked(aiEnabled);
        suppressAiManagedListener = false;
        String amount = aiManagedConfig.buyAmountBKC == null
                ? "1" : aiManagedConfig.buyAmountBKC;
        tvAiManagedSummary.setText(aiEnabled
                ? String.format(Locale.US,
                "每单 %s BKC · 置信度 ≥%.0f%%",
                amount, aiManagedConfig.confidenceMin * 100d)
                : "模型研判 · 风控执行");
        if (tvAiManagedAction != null) {
            tvAiManagedAction.setText(aiEnabled ? "管理" : "设置");
        }
        if (tvRuleStrategyTitle != null && tvRuleStrategySummary != null
                && tvRuleStrategyAction != null) {
            String direction = "no".equalsIgnoreCase(aiManagedConfig.direction)
                    ? "NO" : "YES";
            if (aiManagedConfig.enabled && "grid".equals(strategyType)) {
                tvRuleStrategyTitle.setText("网格策略");
                tvRuleStrategySummary.setText(String.format(Locale.US,
                        "%s 方向 · %d 档 · 每单 %s BKC",
                        direction, aiManagedConfig.gridLevels, amount));
                tvRuleStrategyAction.setText("管理 ›");
            } else if (aiManagedConfig.enabled && "martingale".equals(strategyType)) {
                tvRuleStrategyTitle.setText("马丁格尔策略");
                tvRuleStrategySummary.setText(String.format(Locale.US,
                        "%s 方向 · %.1f%% 触发 · 最多 %d 轮",
                        direction, aiManagedConfig.martingaleTriggerPercent,
                        aiManagedConfig.martingaleMaxRounds));
                tvRuleStrategyAction.setText("管理 ›");
            } else {
                tvRuleStrategyTitle.setText("规则策略");
                tvRuleStrategySummary.setText("网格 / 马丁格尔 · 阈值触发");
                tvRuleStrategyAction.setText("设置 ›");
            }
        }
    }

    private boolean isAiStrategyEnabled() {
        return aiManagedConfig != null && aiManagedConfig.enabled
                && "ai".equals(normalizeStrategyType(aiManagedConfig.strategyType));
    }

    private String normalizeStrategyType(String value) {
        if ("grid".equalsIgnoreCase(value)) return "grid";
        if ("martingale".equalsIgnoreCase(value)) return "martingale";
        return "ai";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_AI_MANAGED_SETTINGS && resultCode == Activity.RESULT_OK) {
            viewModel.refreshGameInfo(gameId, resolveContractAddress());
        }
    }

    private void toggleAiDetails() {
        if (!marketAiHasResult) {
            requestMarketAiAnalysis();
            return;
        }
        aiExpanded = !aiExpanded;
        layoutAiDetails.setVisibility(aiExpanded ? View.VISIBLE : View.GONE);
        tvMarketAiStatus.setText(aiExpanded ? "收起详情 ↑" : "展开详情 ↓");
    }

    private void refreshMarketAiAnalysis() {
        aiExpanded = false;
        layoutAiDetails.setVisibility(View.GONE);
        requestMarketAiAnalysis();
    }

    private void requestMarketAiAnalysis() {
        if (requestInFlight) return;
        requestInFlight = true;
        progressMarketAi.setVisibility(View.VISIBLE);
        tvMarketAiStatus.setVisibility(View.GONE);
        tvMarketAiSummary.setText("AI 正在核对行情、份额分布、判定规则与时间风险…");
        if (!marketAiHasResult) {
            layoutMarketAiChips.setVisibility(View.GONE);
            layoutAiDetails.setVisibility(View.GONE);
        }
        viewModel.startAiAnalysis();
    }

    private void showMarketAiSummary(String answer) {
        requestInFlight = false;
        if (destroyed) return;
        if (answer == null || answer.trim().isEmpty()) {
            showMarketAiUnavailable("暂不可用", "AI 投研分析暂时不可用");
            return;
        }
        marketAiSummary = answer;
        marketAiHasResult = true;
        GoldMarketResearchAnalysisPresenter.Analysis analysis =
                GoldMarketResearchAnalysisPresenter.parse(answer);
        progressMarketAi.setVisibility(View.GONE);
        tvMarketAiStatus.setVisibility(View.VISIBLE);
        tvMarketAiStatus.setText("展开详情 ↓");
        tvMarketAiSummary.setText(analysis.summary);
        tvMarketAiStance.setText(analysis.stance);
        tvMarketAiRisk.setText("风险 " + analysis.riskLevel);
        tvMarketAiRisk.setBackground(riskBackground(analysis.riskLevel));
        tvMarketAiRisk.setTextColor(riskTextColor(analysis.riskLevel));
        tvMarketAiDrivers.setText(analysis.drivers);
        tvMarketAiActions.setText(analysis.actions);
        tvMarketAiDisclaimer.setText(analysis.disclaimer);
        layoutMarketAiChips.setVisibility(View.VISIBLE);
        aiExpanded = false;
        layoutAiDetails.setVisibility(View.GONE);
    }

    private void showMarketAiUnavailable(String status, String message) {
        requestInFlight = false;
        if (destroyed) return;
        marketAiUnavailableMessage = message;
        marketAiHasResult = false;
        progressMarketAi.setVisibility(View.GONE);
        tvMarketAiStatus.setVisibility(View.VISIBLE);
        tvMarketAiStatus.setText(status);
        tvMarketAiSummary.setText(message);
        layoutMarketAiChips.setVisibility(View.GONE);
        layoutAiDetails.setVisibility(View.GONE);
    }

    private GradientDrawable riskBackground(String riskLevel) {
        int fill;
        int stroke;
        if ("High".equalsIgnoreCase(riskLevel) || "高".equals(riskLevel)) {
            fill = 0xFFFFE4E6;
            stroke = 0xFFFDA4AF;
        } else if ("Medium".equalsIgnoreCase(riskLevel) || "中".equals(riskLevel)) {
            fill = 0xFFFFF7ED;
            stroke = 0xFFFDBA74;
        } else if ("Low".equalsIgnoreCase(riskLevel) || "低".equals(riskLevel)) {
            fill = 0xFFECFDF5;
            stroke = 0xFF6EE7B7;
        } else {
            fill = 0xFFF1F5F9;
            stroke = 0xFFCBD5E1;
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(999));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private int riskTextColor(String riskLevel) {
        if ("High".equalsIgnoreCase(riskLevel) || "高".equals(riskLevel)) return 0xFFBE123C;
        if ("Medium".equalsIgnoreCase(riskLevel) || "中".equals(riskLevel)) return 0xFFC2410C;
        if ("Low".equalsIgnoreCase(riskLevel) || "低".equals(riskLevel)) return 0xFF047857;
        return 0xFF475569;
    }

    private void updateCountdown() {
        if (currentGame == null) return;
        long rem = GoldNoteMarketActivity.remainingSecondsUntilDeadline(currentGame.deadlineSec, System.currentTimeMillis());
        GoldMarketStatusStyle status = GoldMarketStatusStyle.forMarketOutcome(
                currentGame.isResolved,
                currentGame.isRefunded,
                rem,
                currentGame.winningOption,
                optionName(0),
                optionName(1));
        tvCountdown.setText(statusTextForCountdown(status, rem));
        tvCountdown.setTextColor(status.textColor);
        styleMetricCards();
    }

    private String statusTextForCountdown(GoldMarketStatusStyle status, long remainingSeconds) {
        if (currentGame.isResolved || currentGame.isRefunded || remainingSeconds <= 0) {
            return status.label;
        }
        return GoldNoteMarketActivity.formatRemainingTime(remainingSeconds);
    }

    private void styleMetricCards() {
        if (currentGame == null) return;
        boolean resolved = currentGame.isResolved && !currentGame.isRefunded;
        styleMetricCard(cardMetricYes, tvUpLabel, tvUpPct, true, resolved && currentGame.winningOption == 0, resolved);
        styleMetricCard(cardMetricNo, tvDownLabel, tvDownPct, false, resolved && currentGame.winningOption == 1, resolved);
    }

    private void styleMetricCard(View card, TextView label, TextView value, boolean yes, boolean winner, boolean resolved) {
        int color = yes ? YES_COLOR : NO_COLOR;
        if (resolved) {
            label.setText(GoldMarketOptionText.outcomeLabel(yes ? 0 : 1, winner));
            label.setTextColor(winner ? color : MUTED_TEXT);
            value.setTextColor(winner ? color : MUTED_TEXT);
            card.setBackground(makeOptionBackground(yes, winner, true));
            return;
        }
        label.setText(GoldMarketOptionText.chartLabel(yes ? 0 : 1));
        label.setTextColor(color);
        value.setTextColor(color);
        card.setBackground(makeOptionBackground(yes, false, false));
    }

    private void styleHoldingCard(View card, boolean yes) {
        boolean winner = currentGame != null
                && currentGame.isResolved
                && !currentGame.isRefunded
                && currentGame.winningOption == (yes ? 0 : 1);
        card.setBackground(makeOptionBackground(yes, winner, currentGame != null && currentGame.isResolved));
    }

    private GradientDrawable makeOptionBackground(boolean yes, boolean winner, boolean resolved) {
        int color = yes ? YES_COLOR : NO_COLOR;
        int fill = yes ? YES_BACKGROUND : NO_BACKGROUND;
        int strokeColor = yes ? 0xFF86EFAC : 0xFFFFB3C1;
        int strokeWidth = 1;
        if (resolved && winner) {
            fill = yes ? 0xFFD1FAE5 : 0xFFFFDCE5;
            strokeColor = color;
            strokeWidth = 2;
        } else if (resolved) {
            fill = MUTED_BACKGROUND;
            strokeColor = MUTED_BORDER;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private GradientDrawable makeRoundedBackground(int fill, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String optionName(int index) {
        if (currentGame != null
                && currentGame.optionNames != null
                && index >= 0
                && index < currentGame.optionNames.size()
                && currentGame.optionNames.get(index) != null
                && !currentGame.optionNames.get(index).trim().isEmpty()) {
            return GoldMarketOptionText.displayName(currentGame.optionNames.get(index), index);
        }
        return GoldMarketOptionText.displayName(index);
    }

    private void bindOrUpdateTradePanel() {
        if (currentGame == null) {
            return;
        }
        if (tradePanelController == null) {
            tradePanelController = GoldTradeDialog.attach(
                    this, inlineTradePanel, currentGame, GoldTradeDialog.Side.BUY, 0,
                    tradeListener());
        } else {
            tradePanelController.updateGame(currentGame);
        }
    }

    private void focusTradePanel(GoldTradeDialog.Side side, int optionId) {
        if (currentGame == null) {
            showTradeErrorDialog("交易失败：博弈池数据尚未加载完成，请稍后重试");
            return;
        }
        bindOrUpdateTradePanel();
        if (tradePanelController == null) return;
        tradePanelController.select(side, optionId);
        detailScroll.post(() -> detailScroll.smoothScrollTo(0, inlineTradePanel.getTop()));
    }

    private GoldTradeDialog.Listener tradeListener() {
        return new GoldTradeDialog.Listener() {
            @Override
            public void onBuy(int optionId, BigInteger amountWei) {
                String preflightError = validateBuyRequest(optionId, amountWei.toString());
                if (preflightError != null) {
                    showTradeErrorDialog(preflightError);
                    return;
                }
                Toast.makeText(GoldMarketDetailActivity.this,
                        "正在按 AMM 报价提交买入…", Toast.LENGTH_SHORT).show();
                viewModel.buyShares(gameId, resolveContractAddress(), optionId, amountWei);
            }

            @Override
            public void onSell(int optionId, BigInteger shareAmountWei,
                               BigInteger minimumAmountOutWei,
                               BigInteger quotedAmountOutWei) {
                Toast.makeText(GoldMarketDetailActivity.this,
                        "正在按 AMM 报价提交卖出…", Toast.LENGTH_SHORT).show();
                viewModel.sellShares(gameId, resolveContractAddress(), optionId,
                        shareAmountWei, minimumAmountOutWei, quotedAmountOutWei);
            }

            @Override
            public void onAddLiquidity(BigInteger amountWei,
                                       BigInteger minimumLiquiditySharesWei,
                                       BigInteger quotedLiquiditySharesWei) {
                Toast.makeText(GoldMarketDetailActivity.this,
                        "正在提交流动性质押…", Toast.LENGTH_SHORT).show();
                viewModel.addLiquidity(gameId, resolveContractAddress(), amountWei,
                        minimumLiquiditySharesWei, quotedLiquiditySharesWei);
            }

            @Override
            public void onRemoveLiquidity(BigInteger liquiditySharesWei,
                                          BigInteger minimumAmountOutWei,
                                          BigInteger quotedAmountOutWei) {
                Toast.makeText(GoldMarketDetailActivity.this,
                        "正在提交质押取回…", Toast.LENGTH_SHORT).show();
                viewModel.removeLiquidity(gameId, resolveContractAddress(),
                        liquiditySharesWei, minimumAmountOutWei, quotedAmountOutWei);
            }
        };
    }

    private void claimReward() {
        if (currentGame == null) return;
        if (!shouldShowClaimReward()) {
            Toast.makeText(this, "当前没有可领取的收益", Toast.LENGTH_SHORT).show();
            return;
        }
        int optIndex = -1;
        if (currentGame.isResolved) optIndex = currentGame.winningOption;
        else if (currentGame.isRefunded) {
            for (int i=0; i<currentGame.myShares.size(); i++) {
                if (currentGame.myShares.get(i).compareTo(BigInteger.ZERO) > 0) { optIndex = i; break; }
            }
        }
        if (optIndex != -1) viewModel.claimReward(gameId, resolveContractAddress(), optIndex);
    }

    private String resolveContractAddress() {
        if (currentGame != null && currentGame.contractAddress != null && !currentGame.contractAddress.trim().isEmpty()) {
            return currentGame.contractAddress;
        }
        return contractAddress;
    }

    private boolean shouldShowClaimReward() {
        if (currentGame == null) return false;
        long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(
                currentGame.deadlineSec, System.currentTimeMillis());
        if (remaining > 0) return false;
        if (!currentGame.isResolved && !currentGame.isRefunded) return false;
        if (currentGame.myShares == null || currentGame.myShares.size() < 2) return false;

        if (currentGame.isResolved) {
            int winningIndex = currentGame.winningOption;
            if (winningIndex < 0 || winningIndex >= currentGame.myShares.size()) return false;
            BigInteger winningShares = currentGame.myShares.get(winningIndex);
            return winningShares != null && winningShares.signum() > 0;
        }

        for (BigInteger shares : currentGame.myShares) {
            if (shares != null && shares.signum() > 0) return true;
        }
        return false;
    }

    private String validateBuyRequest(int optionId, String amountText) {
        if (currentGame == null) {
            return "交易失败：博弈池数据尚未加载完成，请稍后重试";
        }
        long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(
                currentGame.deadlineSec, System.currentTimeMillis());
        if (remaining == 0) {
            return "交易失败：该博弈池已经截止，不再接受 YES/NO 份额购买";
        }
        if (remaining < 0) {
            return "交易失败：博弈池截止时间尚未同步完成\n\n"
                    + "请下拉刷新；若问题仍存在，缓存或链上截止时间可能暂不可用";
        }
        if (currentGame.isResolved || currentGame.isRefunded) {
            return "交易失败：该博弈池"
                    + (currentGame.isRefunded ? "已退款" : "已开奖")
                    + "，不再接受购买";
        }
        if (resolveContractAddress() == null || resolveContractAddress().trim().isEmpty()) {
            return "交易失败：博弈池合约地址不可用";
        }
        if (amountText == null || amountText.trim().isEmpty()) {
            return "交易失败：请输入交易金额";
        }
        return null;
    }

    private void showTradeErrorDialog(String message) {
        if (isFinishing() || destroyed) return;
        new AlertDialog.Builder(this)
                .setTitle("交易失败")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }
}
