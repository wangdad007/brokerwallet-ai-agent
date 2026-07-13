package com.example.brokerfi.xc.agent.gold.view;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import io.noties.markwon.Markwon;
import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.data.PinataClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCardPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketDetailPresenter;
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
import com.github.mikephil.charting.formatter.ValueFormatter;

import android.widget.ImageView;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GoldMarketDetailActivity extends AppCompatActivity {
    private static final String MARKET_AI_LOADING_MESSAGE = "专属分析仍在生成，请稍后";
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

    private TextView tvMarketDesc, tvMarketTime, tvMarketCondition;
    private TextView tvUpLabel, tvDownLabel, tvUpPct, tvDownPct, tvPool, tvCountdown;
    private TextView tvHoldingsEmpty, tvHoldingYesLabel, tvHoldingYesAmount, tvHoldingNoLabel, tvHoldingNoAmount;
    private View cardMetricYes, cardMetricNo, cardHoldingYes, cardHoldingNo;
    private TextView tvMarketAiStatus, tvMarketAiSummary, tvMarketAiFull;
    private ImageView ivMarketIcon;
    private View barUp, barDown, btnClaimReward, cardMarketAi, layoutAiDetails, layoutChartContainer;
    private LineChart lineChart;
    private androidx.appcompat.widget.SwitchCompat switchAiManaged;
    private SwipeRefreshLayout swipeRefresh;

    private Markwon markwon;
    private boolean aiExpanded = false;
    private String marketAiSummary = "";
    private String marketAiUnavailableMessage = "";
    private boolean destroyed = false;
    private boolean requestInFlight = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Handler dataRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable dataRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            viewModel.refreshGameInfo(gameId, resolveContractAddress());
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
        markwon = Markwon.create(this);
        initViews();
        observeViewModel();
        viewModel.loadGameInfo(gameId, contractAddress);
        timerHandler.post(countdownRunnable);
    }

    private void observeViewModel() {
        viewModel.getCurrentGame().observe(this, game -> {
            currentGame = game;
            updateUI();
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

        viewModel.getDebugToast().observe(this, msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
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
        cardMetricYes = findViewById(R.id.card_metric_yes);
        cardMetricNo = findViewById(R.id.card_metric_no);
        cardHoldingYes = findViewById(R.id.card_holding_yes);
        cardHoldingNo = findViewById(R.id.card_holding_no);

        tvMarketAiStatus = findViewById(R.id.tv_market_ai_status);
        tvMarketAiStatus.setText("待启动 ›");
        tvMarketAiSummary = findViewById(R.id.tv_market_ai_summary);
        tvMarketAiSummary.setText("点击卡片启动 AI 专属深度投研分析");
        tvMarketAiFull = findViewById(R.id.tv_market_ai_full);
        ivMarketIcon = findViewById(R.id.iv_market_detail_icon);
        switchAiManaged = findViewById(R.id.switch_ai_managed);
        barUp = findViewById(R.id.bar_up);
        barDown = findViewById(R.id.bar_down);
        cardMarketAi = findViewById(R.id.card_market_ai);
        layoutAiDetails = findViewById(R.id.layout_ai_details);
        layoutChartContainer = findViewById(R.id.layout_chart_container);
        lineChart = findViewById(R.id.line_chart);
        btnClaimReward = findViewById(R.id.btn_claim_reward);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> viewModel.loadGameInfo(gameId, resolveContractAddress()));

        findViewById(R.id.btn_buy_up).setOnClickListener(v -> showBuyDialog(0, GoldMarketOptionText.displayName(0)));
        findViewById(R.id.btn_buy_down).setOnClickListener(v -> showBuyDialog(1, GoldMarketOptionText.displayName(1)));
        cardMarketAi.setOnClickListener(v -> toggleAiDetails());
        switchAiManaged.setOnCheckedChangeListener((btn, isChecked) -> {
            if (currentGame != null && currentGame.isManaged != isChecked) {
                viewModel.toggleAiManaged(gameId, resolveContractAddress(), isChecked);
            }
        });
        btnClaimReward.setOnClickListener(v -> claimReward());
    }

    private void updateUI() {
        if (currentGame == null) return;
        String title = currentGame.desc != null && !currentGame.desc.isEmpty() ? currentGame.desc : "博弈池 #" + currentGame.id;
        String condition = "判定逻辑: " + (currentGame.condition != null ? currentGame.condition : "暂无");
        tvMarketDesc.setText(styleMarketText(
                GoldMarketCardPresenter.displayTitle(title, currentGame.condition, currentGame.deadlineSec), true));
        tvMarketTime.setText("");
        tvMarketTime.setVisibility(View.GONE);
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

        switchAiManaged.setChecked(currentGame.isManaged);

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
        tvPool.setText(GoldNoteMarketActivity.formatBkc(currentGame.totalPool) + " BKC");
        updateHoldingsUI();
        setupHistoryChart();
        updateCountdown();
    }

    private CharSequence styleMarketText(String text, boolean title) {
        return GoldMarketTextStyler.style(text, title);
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

    private void setupHistoryChart() {
        if (currentGame == null || currentGame.history == null || currentGame.history.isEmpty()) {
            layoutChartContainer.setVisibility(View.GONE);
            return;
        }
        layoutChartContainer.setVisibility(View.VISIBLE);

        List<Entry> yesEntries = new ArrayList<>();
        List<Entry> noEntries = new ArrayList<>();
        for (int i = 0; i < currentGame.history.size(); i++) {
            GoldMarketRepository.HistoryPoint p = currentGame.history.get(i);
            yesEntries.add(new Entry(i, p.yesPrice));
            noEntries.add(new Entry(i, p.noPrice));
        }

        // YES 曲线 (深绿色)
        LineDataSet setYes = new LineDataSet(yesEntries, GoldMarketOptionText.chartLabel(0));
        setYes.setColor(0xFF059669);
        setYes.setCircleColor(0xFF059669);
        setYes.setLineWidth(2.5f);
        setYes.setCircleRadius(3f);
        setYes.setDrawCircleHole(false);
        setYes.setDrawValues(false);
        setYes.setMode(LineDataSet.Mode.CUBIC_BEZIER); // 平滑曲线
        setYes.setDrawFilled(true);
        setYes.setFillColor(0xFF059669);
        setYes.setFillAlpha(40);

        // NO 曲线 (红色)
        LineDataSet setNo = new LineDataSet(noEntries, GoldMarketOptionText.chartLabel(1));
        setNo.setColor(0xFFE11D48);
        setNo.setCircleColor(0xFFE11D48);
        setNo.setLineWidth(2.5f);
        setNo.setCircleRadius(3f);
        setNo.setDrawCircleHole(false);
        setNo.setDrawValues(false);
        setNo.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        setNo.setDrawFilled(true);
        setNo.setFillColor(0xFFE11D48);
        setNo.setFillAlpha(40);

        LineData data = new LineData(setYes, setNo);
        lineChart.setData(data);

        // 全局交互设置
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setTouchEnabled(true);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setExtraOffsets(0, 10, 0, 10);
        
        // 图例美化
        com.github.mikephil.charting.components.Legend legend = lineChart.getLegend();
        legend.setTextColor(0xFF475569);
        legend.setForm(com.github.mikephil.charting.components.Legend.LegendForm.CIRCLE);
        legend.setHorizontalAlignment(com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);

        // X 轴
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(true);
        xAxis.setAxisLineColor(0xFFE2E8F0);
        xAxis.setTextColor(0xFF94A3B8);
        xAxis.setLabelCount(Math.min(5, currentGame.history.size()));
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int idx = (int) value;
                if (idx >= 0 && idx < currentGame.history.size()) {
                    long t = currentGame.history.get(idx).time;
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd", Locale.getDefault());
                    return sdf.format(new java.util.Date(t * 1000));
                }
                return "";
            }
        });

        // Y 轴
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getAxisLeft().setDrawGridLines(true);
        lineChart.getAxisLeft().setGridColor(0xFFF1F5F9);
        lineChart.getAxisLeft().setTextColor(0xFF94A3B8);
        lineChart.getAxisLeft().setAxisMaximum(100f);
        lineChart.getAxisLeft().setAxisMinimum(0f);
        lineChart.getAxisLeft().setLabelCount(5);

        lineChart.animateX(1200);
        lineChart.invalidate();
    }

    private void toggleAiDetails() {
        if (marketAiSummary == null || marketAiSummary.isEmpty()) {
            if (!requestInFlight) {
                tvMarketAiStatus.setText("分析中...");
                tvMarketAiSummary.setText("AI 正在解析市场数据，请稍后...");
                requestInFlight = true;
                viewModel.startAiAnalysis();
            }
            return;
        }
        aiExpanded = !aiExpanded;
        layoutAiDetails.setVisibility(aiExpanded ? View.VISIBLE : View.GONE);
        tvMarketAiSummary.setVisibility(aiExpanded ? View.GONE : View.VISIBLE);
        tvMarketAiStatus.setText(aiExpanded ? "收起报告 ˄" : "展开详情 ˅");
    }

    private void showMarketAiSummary(String answer) {
        requestInFlight = false;
        if (destroyed) return;
        if (answer == null || answer.trim().isEmpty()) {
            showMarketAiUnavailable("暂不可用", "AI 分析暂时不可用");
            return;
        }
        marketAiSummary = answer;
        tvMarketAiStatus.setText("展开详情 ˅");
        tvMarketAiSummary.setText(answer);
        markwon.setMarkdown(tvMarketAiFull, answer);
    }

    private void showMarketAiUnavailable(String status, String message) {
        requestInFlight = false;
        if (destroyed) return;
        marketAiUnavailableMessage = message;
        tvMarketAiStatus.setText(status);
        tvMarketAiSummary.setText(message);
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
        label.setText(GoldMarketOptionText.displayName(yes ? 0 : 1));
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
        drawable.setCornerRadius(dp(8));
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

    private void showBuyDialog(int optionId, String name) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_gold_buy_confirm, null);
        builder.setView(v);
        ((TextView) v.findViewById(R.id.tv_buy_title)).setText("确认下注 " + name);
        EditText et = v.findViewById(R.id.et_buy_amount);
        Button btn = v.findViewById(R.id.btn_buy_confirm);
        btn.setBackgroundResource(optionId == 0 ? R.drawable.bg_bet_yes : R.drawable.bg_bet_no);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        btn.setOnClickListener(view -> {
            String val = et.getText().toString().trim();
            if (val.isEmpty()) {
                Toast.makeText(GoldMarketDetailActivity.this, "请输入下注金额", Toast.LENGTH_SHORT).show();
                return;
            }
            String preflightError = validateBuyRequest(optionId, val);
            if (preflightError != null) {
                showTradeErrorDialog(preflightError);
                return;
            }
            BigInteger wei = GoldMarketRepository.parseTokenAmountToWei(val);
            if (wei == null) {
                Toast.makeText(GoldMarketDetailActivity.this, "金额格式无效，请输入有效数字（如 100）", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            Toast.makeText(GoldMarketDetailActivity.this, "正在发送交易...", Toast.LENGTH_SHORT).show();
            viewModel.buyShares(gameId, resolveContractAddress(), optionId, wei);
        });
        v.findViewById(R.id.btn_buy_cancel).setOnClickListener(view -> dialog.dismiss());
        dialog.show();
    }

    private void claimReward() {
        if (currentGame == null) return;
        if (!shouldShowClaimReward()) {
            Toast.makeText(this, "当前暂无可领取收益", Toast.LENGTH_SHORT).show();
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
            return "下单失败：当前市场数据尚未加载完成，请稍后重试。";
        }
        long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(
                currentGame.deadlineSec, System.currentTimeMillis());
        if (remaining == 0) {
            return "下单失败：该博弈池已经截止，链上不会再接受新的 YES/NO 份额购买。";
        }
        if (remaining < 0) {
            return "下单失败：该博弈池的截止时间还未同步完成。\n\n"
                    + "建议先下拉刷新一次；如果仍然失败，说明后端缓存或链上 deadline 状态异常。";
        }
        if (currentGame.isResolved || currentGame.isRefunded) {
            return "下单失败：该博弈池当前状态为"
                    + (currentGame.isRefunded ? "已退款" : "已结算")
                    + "，不允许继续购买。";
        }
        if (resolveContractAddress() == null || resolveContractAddress().trim().isEmpty()) {
            return "下单失败：未能识别该市场所属的合约地址，前端无法正确路由交易。";
        }
        if (amountText == null || amountText.trim().isEmpty()) {
            return "下单失败：请输入下注金额。";
        }
        return null;
    }

    private void showTradeErrorDialog(String message) {
        if (isFinishing() || destroyed) return;
        new AlertDialog.Builder(this)
                .setTitle("下单失败")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }
}
