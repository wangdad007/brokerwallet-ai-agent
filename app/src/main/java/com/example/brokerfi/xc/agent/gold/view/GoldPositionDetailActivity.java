package com.example.brokerfi.xc.agent.gold.view;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.ai.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.data.PinataClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCardPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketDetailPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketOptionText;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionHistoryPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionAnalysisPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketStatusStyle;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionValuation;
import com.example.brokerfi.xc.agent.gold.viewmodel.GoldMarketDetailViewModel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public class GoldPositionDetailActivity extends AppCompatActivity {
    private static final long DATA_REFRESH_INTERVAL_MS = 15_000L;
    private int gameId;
    private String contractAddress;
    private GoldMarketRepository.GameModel currentGame;
    private GoldMarketDetailViewModel viewModel;
    private List<BackendApiClient.TradeDTO> tradeHistory = new ArrayList<>();

    private ImageView ivPoolIcon;
    private TextView tvPoolDesc, tvPoolCondition, tvStatusBadge, tvPoolTime;
    private TextView btnPositionRuleToggle;
    private TextView tvPosYesLabel, tvPosYesShares, tvPosNoLabel, tvPosNoShares;
    private TextView btnSellPositionYes, btnSellPositionNo;
    private TextView tvPositionEmpty, tvCurrentValue, tvTotalInvested, tvReturnRate;
    private View rowPositionYes, rowPositionNo, rowReturnRate;
    private LinearLayout tradeHistoryContainer;
    private TextView tvTradeEmpty;
    private View cardPositionAi, layoutPositionAiResult, layoutPositionAiDetails;
    private View layoutPositionRuleDetails;
    private TextView tvPositionAiStatus, tvPositionAiPlaceholder, tvPositionAiStance;
    private TextView tvPositionAiRisk, tvPositionAiSummary, tvPositionAiDrivers;
    private TextView tvPositionAiActions, tvPositionAiDisclaimer, btnPositionAiRefresh;
    private ProgressBar progressPositionAi;
    private SwipeRefreshLayout swipeRefresh;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    private final AtomicBoolean tradeHistoryRequestInFlight = new AtomicBoolean(false);
    private final AtomicBoolean positionAnalysisInFlight = new AtomicBoolean(false);
    private boolean tradeHistoryLoadedOnce;
    private boolean positionAnalysisHasResult;
    private boolean positionAnalysisExpanded = true;
    private boolean positionRulesExpanded;
    private boolean destroyed;
    private final Handler dataRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable dataRefreshRunnable = new Runnable() {
        @Override public void run() {
            viewModel.refreshGameInfo(gameId, resolveContractAddress());
            loadTradeHistory();
            dataRefreshHandler.postDelayed(this, DATA_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_position_detail);
        DeepSeekClient.init(this);

        gameId = getIntent().getIntExtra("GAME_ID", -1);
        contractAddress = getIntent().getStringExtra("CONTRACT_ADDRESS");
        if (gameId <= 0) {
            Toast.makeText(this, "博弈池 ID 无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(GoldMarketDetailViewModel.class);
        initViews();
        observeViewModel();
        viewModel.loadGameInfo(gameId, contractAddress);
        loadTradeHistory();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ivPoolIcon = findViewById(R.id.iv_pool_icon);
        tvPoolDesc = findViewById(R.id.tv_pool_desc);
        tvPoolCondition = findViewById(R.id.tv_pool_condition);
        tvStatusBadge = findViewById(R.id.tv_status_badge);
        tvPoolTime = findViewById(R.id.tv_pool_time);
        btnPositionRuleToggle = findViewById(R.id.btn_position_rule_toggle);
        layoutPositionRuleDetails = findViewById(R.id.layout_position_rule_details);
        btnPositionRuleToggle.setOnClickListener(v -> togglePositionRules());

        tvPosYesLabel = findViewById(R.id.tv_pos_yes_label);
        tvPosYesShares = findViewById(R.id.tv_pos_yes_shares);
        tvPosNoLabel = findViewById(R.id.tv_pos_no_label);
        tvPosNoShares = findViewById(R.id.tv_pos_no_shares);
        btnSellPositionYes = findViewById(R.id.btn_sell_position_yes);
        btnSellPositionNo = findViewById(R.id.btn_sell_position_no);
        btnSellPositionYes.setOnClickListener(v -> showSellDialog(0));
        btnSellPositionNo.setOnClickListener(v -> showSellDialog(1));
        tvPositionEmpty = findViewById(R.id.tv_position_empty);
        rowPositionYes = findViewById(R.id.row_position_yes);
        rowPositionNo = findViewById(R.id.row_position_no);

        tvCurrentValue = findViewById(R.id.tv_current_value);
        tvTotalInvested = findViewById(R.id.tv_total_invested);
        tvReturnRate = findViewById(R.id.tv_return_rate);
        rowReturnRate = findViewById(R.id.row_return_rate);

        tradeHistoryContainer = findViewById(R.id.trade_history_container);
        tvTradeEmpty = findViewById(R.id.tv_trade_empty);

        cardPositionAi = findViewById(R.id.card_position_ai);
        layoutPositionAiResult = findViewById(R.id.layout_position_ai_result);
        layoutPositionAiDetails = findViewById(R.id.layout_position_ai_details);
        tvPositionAiStatus = findViewById(R.id.tv_position_ai_status);
        tvPositionAiPlaceholder = findViewById(R.id.tv_position_ai_placeholder);
        tvPositionAiStance = findViewById(R.id.tv_position_ai_stance);
        tvPositionAiRisk = findViewById(R.id.tv_position_ai_risk);
        tvPositionAiSummary = findViewById(R.id.tv_position_ai_summary);
        tvPositionAiDrivers = findViewById(R.id.tv_position_ai_drivers);
        tvPositionAiActions = findViewById(R.id.tv_position_ai_actions);
        tvPositionAiDisclaimer = findViewById(R.id.tv_position_ai_disclaimer);
        btnPositionAiRefresh = findViewById(R.id.btn_position_ai_refresh);
        progressPositionAi = findViewById(R.id.progress_position_ai);
        btnPositionAiRefresh.setOnClickListener(v -> requestPositionAnalysis());
        tvPositionAiStatus.setOnClickListener(v -> togglePositionAnalysisDetails());
        tvPositionAiPlaceholder.setOnClickListener(v -> requestPositionAnalysis());
        cardPositionAi.setOnClickListener(v -> togglePositionAnalysisDetails());

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadGameInfo(gameId, resolveContractAddress());
            loadTradeHistory();
        });
    }

    private void observeViewModel() {
        viewModel.getCurrentGame().observe(this, game -> {
            currentGame = game;
            updateUI();
        });
        viewModel.getIsLoading().observe(this, loading -> {
            if (!loading) swipeRefresh.setRefreshing(false);
        });
        viewModel.getError().observe(this, err -> {
            if (err != null && !err.isEmpty()) {
                swipeRefresh.setRefreshing(false);
            }
        });
        viewModel.getTxStatus().observe(this, status -> {
            if (status == null || status.trim().isEmpty()) return;
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
            if (status.contains("卖出成功")) {
                loadTradeHistory();
            }
        });
        viewModel.getTradeError().observe(this, err -> {
            if (err != null && !err.trim().isEmpty()) {
                showTradeErrorDialog(err);
            }
        });
    }

    private void loadTradeHistory() {
        if (!tradeHistoryRequestInFlight.compareAndSet(false, true)) return;
        // 在后台线程加载交易历史
        new Thread(() -> {
            try {
                String userAddress = viewModel.getWalletAddress();
                List<BackendApiClient.TradeDTO> trades = BackendApiClient.fetchTradeHistory(gameId, userAddress);
                tradeHistory.clear();
                if (trades != null) {
                    tradeHistory.addAll(trades);
                    normalizeTradeHistory(tradeHistory);
                }
            } catch (Exception e) {
                // 保留最后一次成功结果，避免短暂网络故障让交易记录闪空。
            } finally {
                tradeHistoryRequestInFlight.set(false);
                tradeHistoryLoadedOnce = true;
            }
            runOnUiThread(() -> {
                updateTradeHistoryUI();
                updatePositionUI();
            });
        }).start();
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

    @Override
    protected void onDestroy() {
        destroyed = true;
        dataRefreshHandler.removeCallbacks(dataRefreshRunnable);
        super.onDestroy();
    }

    private String resolveContractAddress() {
        if (currentGame != null && currentGame.contractAddress != null && !currentGame.contractAddress.trim().isEmpty()) {
            return currentGame.contractAddress;
        }
        return contractAddress;
    }

    private void updateUI() {
        if (currentGame == null) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        String rawTitle = currentGame.desc != null && !currentGame.desc.isEmpty()
                ? currentGame.desc : "博弈池 #" + currentGame.id;
        String rawCondition = currentGame.condition == null ? "" : currentGame.condition;
        String condition = GoldMarketDetailPresenter.formatResolutionRule(rawCondition);
        GoldMarketDetailPresenter.HeroText hero =
                GoldMarketDetailPresenter.heroText(rawTitle, currentGame.deadlineSec);
        GoldMarketTitleFitter.apply(tvPoolDesc, GoldMarketTextStyler.style(
                GoldMarketCardPresenter.displayTitle(
                        rawTitle, rawCondition, currentGame.deadlineSec), true));
        tvPoolTime.setText(hero.timeSubtitle == null || hero.timeSubtitle.trim().isEmpty()
                ? "结算规则已冻结" : hero.timeSubtitle + " · 规则已冻结");
        tvPoolCondition.setText(GoldMarketTextStyler.style(condition, false));

        int templateIcon = GoldMarketTemplateIcon.forMarket(
                currentGame.avatarUrl, rawTitle, rawCondition);
        if (templateIcon != 0) {
            ivPoolIcon.setImageResource(templateIcon);
        } else if (currentGame.avatarUrl != null && !currentGame.avatarUrl.isEmpty()) {
            Glide.with(this).load(PinataClient.IPFS_GATEWAY + currentGame.avatarUrl)
                    .placeholder(R.drawable.apartment_icon).into(ivPoolIcon);
        } else {
            ivPoolIcon.setImageResource(R.drawable.apartment_icon);
        }

        long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(
                currentGame.deadlineSec, System.currentTimeMillis());
        GoldMarketStatusStyle status = GoldMarketStatusStyle.forMarket(
                currentGame.isResolved, currentGame.isRefunded, remaining);
        tvStatusBadge.setText(status.label);
        tvStatusBadge.setTextColor(status.textColor);
        tvStatusBadge.setBackground(makeStatusBackground(status.backgroundColor));

        // Position Summary
        updatePositionUI();

        // Trade History
        updateTradeHistoryUI();

        swipeRefresh.setRefreshing(false);
    }

    private void togglePositionRules() {
        positionRulesExpanded = !positionRulesExpanded;
        layoutPositionRuleDetails.setVisibility(
                positionRulesExpanded ? View.VISIBLE : View.GONE);
        btnPositionRuleToggle.setText(
                positionRulesExpanded ? "收起规则⌃" : "查看规则⌄");
    }

    private GradientDrawable makeStatusBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(999 * getResources().getDisplayMetrics().density);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updatePositionUI() {
        if (currentGame == null) return;
        if (currentGame.myShares == null || currentGame.myShares.size() < 2) {
            showEmptyPosition();
            return;
        }

        BigInteger sYes = currentGame.myShares.get(0);
        BigInteger sNo = currentGame.myShares.get(1);
        boolean hasYes = sYes != null && sYes.signum() > 0;
        boolean hasNo = sNo != null && sNo.signum() > 0;
        boolean canSell = GoldNoteMarketActivity.remainingSecondsUntilDeadline(
                currentGame.deadlineSec, System.currentTimeMillis()) > 0
                && !currentGame.isResolved
                && !currentGame.isRefunded;

        if (!hasYes && !hasNo) {
            showEmptyPosition();
            return;
        }

        tvPositionEmpty.setVisibility(View.GONE);

        String yesName = (currentGame.optionNames != null && !currentGame.optionNames.isEmpty())
                ? GoldMarketOptionText.displayName(currentGame.optionNames.get(0), 0)
                : GoldMarketOptionText.displayName(0);
        String noName = (currentGame.optionNames != null && currentGame.optionNames.size() > 1)
                ? GoldMarketOptionText.displayName(currentGame.optionNames.get(1), 1)
                : GoldMarketOptionText.displayName(1);

        if (hasYes) {
            rowPositionYes.setVisibility(View.VISIBLE);
            tvPosYesLabel.setText(yesName);
            tvPosYesShares.setText(GoldNoteMarketActivity.formatShareAmount(sYes) + " 份额");
            btnSellPositionYes.setVisibility(canSell ? View.VISIBLE : View.GONE);
        } else {
            rowPositionYes.setVisibility(View.GONE);
        }

        if (hasNo) {
            rowPositionNo.setVisibility(View.VISIBLE);
            tvPosNoLabel.setText(noName);
            tvPosNoShares.setText(GoldNoteMarketActivity.formatShareAmount(sNo) + " 份额");
            btnSellPositionNo.setVisibility(canSell ? View.VISIBLE : View.GONE);
        } else {
            rowPositionNo.setVisibility(View.GONE);
        }

        // Current Value
        GoldPositionValuation.MarketValue marketValue = GoldPositionValuation.calculateMarket(currentGame);
        if (marketValue.isComplete()) {
            tvCurrentValue.setText(GoldNoteMarketActivity.formatBkc(marketValue.getValueWei()) + " BKC");
        } else {
            tvCurrentValue.setText("估值不可用");
        }

        // Total Invested & Return Rate
        calculateReturnRate();
        showPositionAnalysisReady();
    }

    private void showEmptyPosition() {
        positionAnalysisHasResult = false;
        positionAnalysisExpanded = true;
        rowPositionYes.setVisibility(View.GONE);
        rowPositionNo.setVisibility(View.GONE);
        btnSellPositionYes.setVisibility(View.GONE);
        btnSellPositionNo.setVisibility(View.GONE);
        tvPositionEmpty.setVisibility(View.VISIBLE);
        tvCurrentValue.setText("-- BKC");
        tvTotalInvested.setText("-- BKC");
        rowReturnRate.setVisibility(View.GONE);
        showPositionAnalysisEmpty();
    }

    private void showSellDialog(int optionId) {
        if (currentGame == null) {
            showTradeErrorDialog("卖出失败：持仓数据尚未加载完成");
            return;
        }
        GoldTradeDialog.show(this, currentGame, GoldTradeDialog.Side.SELL, optionId,
                new GoldTradeDialog.Listener() {
                    @Override
                    public void onBuy(int selectedOption, BigInteger amountWei) {
                        Toast.makeText(GoldPositionDetailActivity.this,
                                "正在按 AMM 报价提交买入…", Toast.LENGTH_SHORT).show();
                        viewModel.buyShares(gameId, resolveContractAddress(),
                                selectedOption, amountWei);
                    }

                    @Override
                    public void onSell(int selectedOption, BigInteger shareAmountWei,
                                       BigInteger minimumAmountOutWei,
                                       BigInteger quotedAmountOutWei) {
                        Toast.makeText(GoldPositionDetailActivity.this,
                                "正在按 AMM 报价提交卖出…", Toast.LENGTH_SHORT).show();
                        viewModel.sellShares(gameId, resolveContractAddress(), selectedOption,
                                shareAmountWei, minimumAmountOutWei, quotedAmountOutWei);
                    }
                });
    }

    private void showTradeErrorDialog(String message) {
        new AlertDialog.Builder(this)
                .setTitle("交易未执行")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    private boolean hasPosition() {
        if (currentGame == null || currentGame.myShares == null) return false;
        for (BigInteger shares : currentGame.myShares) {
            if (shares != null && shares.signum() > 0) return true;
        }
        return false;
    }

    private void requestPositionAnalysis() {
        if (currentGame == null || !tradeHistoryLoadedOnce) {
            showPositionAnalysisPreparing();
            return;
        }
        if (!hasPosition()) {
            showPositionAnalysisEmpty();
            return;
        }
        if (!positionAnalysisInFlight.compareAndSet(false, true)) return;

        progressPositionAi.setVisibility(View.VISIBLE);
        tvPositionAiStatus.setVisibility(View.GONE);
        tvPositionAiPlaceholder.setVisibility(View.VISIBLE);
        tvPositionAiPlaceholder.setText("AI 正在分析持仓结构、资金流与市场风险…");
        if (!positionAnalysisHasResult) layoutPositionAiResult.setVisibility(View.GONE);

        String prompt = GoldPositionAnalysisPresenter.buildPrompt(
                currentGame, new ArrayList<>(tradeHistory), System.currentTimeMillis());
        DeepSeekClient.chatForParsing(
                GoldPositionAnalysisPresenter.systemPrompt(), prompt,
                new DeepSeekClient.ChatCallback() {
                    @Override
                    public void onSuccess(String response) {
                        GoldPositionAnalysisPresenter.Analysis analysis =
                                GoldPositionAnalysisPresenter.parse(response);
                        runOnUiThread(() -> showPositionAnalysis(analysis));
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> showPositionAnalysisError(error));
                    }
        });
    }

    private void showPositionAnalysisReady() {
        if (positionAnalysisHasResult || positionAnalysisInFlight.get() || progressPositionAi == null) {
            return;
        }
        if (!tradeHistoryLoadedOnce) {
            showPositionAnalysisPreparing();
            return;
        }
        progressPositionAi.setVisibility(View.GONE);
        tvPositionAiStatus.setVisibility(View.VISIBLE);
        tvPositionAiStatus.setText("开始分析 ›");
        layoutPositionAiResult.setVisibility(View.GONE);
        layoutPositionAiDetails.setVisibility(View.GONE);
        tvPositionAiPlaceholder.setVisibility(View.VISIBLE);
        tvPositionAiPlaceholder.setText(
                "点击“开始分析”生成个性化持仓与风险报告");
    }

    private void showPositionAnalysisPreparing() {
        if (progressPositionAi == null || positionAnalysisHasResult || positionAnalysisInFlight.get()) {
            return;
        }
        progressPositionAi.setVisibility(View.GONE);
        tvPositionAiStatus.setVisibility(View.VISIBLE);
        tvPositionAiStatus.setText("准备中…");
        layoutPositionAiResult.setVisibility(View.GONE);
        layoutPositionAiDetails.setVisibility(View.GONE);
        tvPositionAiPlaceholder.setVisibility(View.VISIBLE);
        tvPositionAiPlaceholder.setText("持仓与交易数据仍在加载中…");
    }

    private void togglePositionAnalysisDetails() {
        if (!positionAnalysisHasResult) {
            requestPositionAnalysis();
            return;
        }
        positionAnalysisExpanded = !positionAnalysisExpanded;
        layoutPositionAiDetails.setVisibility(positionAnalysisExpanded ? View.VISIBLE : View.GONE);
        tvPositionAiStatus.setText(positionAnalysisExpanded ? "收起详情 ↑" : "展开详情 ↓");
    }

    private void showPositionAnalysis(GoldPositionAnalysisPresenter.Analysis analysis) {
        positionAnalysisInFlight.set(false);
        if (destroyed || analysis == null) return;
        positionAnalysisHasResult = true;
        positionAnalysisExpanded = true;
        progressPositionAi.setVisibility(View.GONE);
        tvPositionAiStatus.setVisibility(View.VISIBLE);
        tvPositionAiStatus.setText("收起详情 ↑");
        tvPositionAiPlaceholder.setVisibility(View.GONE);
        layoutPositionAiResult.setVisibility(View.VISIBLE);
        layoutPositionAiDetails.setVisibility(View.VISIBLE);
        tvPositionAiStance.setText(analysis.stance);
        tvPositionAiRisk.setText("风险 " + analysis.riskLevel);
        tvPositionAiRisk.setBackground(riskBackground(analysis.riskLevel));
        tvPositionAiRisk.setTextColor(riskTextColor(analysis.riskLevel));
        tvPositionAiSummary.setText(analysis.summary);
        tvPositionAiDrivers.setText(analysis.drivers);
        tvPositionAiActions.setText(analysis.actions);
        tvPositionAiDisclaimer.setText(analysis.disclaimer);
    }

    private void showPositionAnalysisError(String error) {
        positionAnalysisInFlight.set(false);
        if (destroyed) return;
        progressPositionAi.setVisibility(View.GONE);
        tvPositionAiStatus.setVisibility(View.VISIBLE);
        if (positionAnalysisHasResult) {
            tvPositionAiStatus.setText(positionAnalysisExpanded ? "收起详情 ↑" : "展开详情 ↓");
            tvPositionAiPlaceholder.setVisibility(View.GONE);
            layoutPositionAiResult.setVisibility(View.VISIBLE);
            Toast.makeText(this, "AI 持仓分析失败，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        tvPositionAiStatus.setText("重试");
        layoutPositionAiResult.setVisibility(View.GONE);
        layoutPositionAiDetails.setVisibility(View.GONE);
        tvPositionAiPlaceholder.setVisibility(View.VISIBLE);
        tvPositionAiPlaceholder.setText("AI 分析暂不可用，点击此处或选择“重试”再次尝试");
    }

    private void showPositionAnalysisEmpty() {
        positionAnalysisInFlight.set(false);
        if (progressPositionAi == null) return;
        progressPositionAi.setVisibility(View.GONE);
        tvPositionAiStatus.setVisibility(View.VISIBLE);
        tvPositionAiStatus.setText("暂无持仓");
        layoutPositionAiResult.setVisibility(View.GONE);
        layoutPositionAiDetails.setVisibility(View.GONE);
        tvPositionAiPlaceholder.setVisibility(View.VISIBLE);
        tvPositionAiPlaceholder.setText("建立持仓后即可生成个性化持仓与风险分析");
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

    private void calculateReturnRate() {
        if (tradeHistory.isEmpty()) {
            tvTotalInvested.setText("-- BKC");
            rowReturnRate.setVisibility(View.GONE);
            return;
        }

        // 累计投入保留所有成功 BUY；已卖出到账单独计入总收益。
        BigInteger totalInvestedWei = BigInteger.ZERO;
        BigInteger totalSellProceedsWei = BigInteger.ZERO;
        for (BackendApiClient.TradeDTO trade : tradeHistory) {
            if (!trade.isSuccess) continue;
            try {
                BigInteger amount = new BigInteger(trade.amountWei);
                if ("BUY".equalsIgnoreCase(trade.tradeType)) {
                    totalInvestedWei = totalInvestedWei.add(amount);
                } else if ("SELL".equalsIgnoreCase(trade.tradeType)) {
                    totalSellProceedsWei = totalSellProceedsWei.add(amount);
                }
            } catch (NumberFormatException ignored) {}
        }

        BigDecimal investedBkc = new BigDecimal(totalInvestedWei).divide(
                new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP);
        tvTotalInvested.setText(String.format(Locale.getDefault(), "%.2f BKC", investedBkc.doubleValue()));

        // Calculate return rate
        GoldPositionValuation.MarketValue marketValue = GoldPositionValuation.calculateMarket(currentGame);
        if (!marketValue.isComplete() || totalInvestedWei.compareTo(BigInteger.ZERO) <= 0) {
            rowReturnRate.setVisibility(View.GONE);
            return;
        }

        BigInteger currentValueWei = marketValue.getValueWei();
        // 总收益率 =（当前剩余持仓估值 + 已卖出到账 - 累计投入）/ 累计投入。
        BigDecimal currentBkc = new BigDecimal(currentValueWei).divide(
                new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP);
        BigDecimal sellProceedsBkc = new BigDecimal(totalSellProceedsWei).divide(
                new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP);
        BigDecimal diff = currentBkc.add(sellProceedsBkc).subtract(investedBkc);
        double rate = diff.divide(investedBkc, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).doubleValue();

        rowReturnRate.setVisibility(View.VISIBLE);
        String prefix = rate >= 0 ? "+" : "";
        tvReturnRate.setText(String.format(Locale.US, "收益率 %s%.2f%%", prefix, rate));
        tvReturnRate.setTextColor(rate >= 0 ? 0xFF059669 : 0xFFE11D48);
    }

    private void updateTradeHistoryUI() {
        tradeHistoryContainer.removeAllViews();

        List<BackendApiClient.TradeDTO> tradeRows =
                GoldPositionHistoryPresenter.visibleRows(currentGame, tradeHistory);

        if (tradeRows.isEmpty()) {
            tvTradeEmpty.setVisibility(View.VISIBLE);
            tradeHistoryContainer.addView(tvTradeEmpty);
            return;
        }

        tvTradeEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (BackendApiClient.TradeDTO trade : tradeRows) {
            View row = inflater.inflate(R.layout.item_trade_history, tradeHistoryContainer, false);

            // Side indicator color
            View indicator = row.findViewById(R.id.indicator_side);
            TextView tvSideBadge = row.findViewById(R.id.tv_side_badge);
            TextView tvManagedBadge = row.findViewById(R.id.tv_managed_badge);
            TextView tvTradeTime = row.findViewById(R.id.tv_trade_time);
            TextView tvTradeAmount = row.findViewById(R.id.tv_trade_amount);
            TextView tvTradeShares = row.findViewById(R.id.tv_trade_shares);

            // YES=0, NO=1
            boolean isYes = (trade.optionId == 0);
            boolean isSell = "SELL".equalsIgnoreCase(trade.tradeType);
            if (isYes) {
                indicator.setBackgroundColor(0xFF059669);
                tvSideBadge.setText((isSell ? "卖出 " : "") + GoldMarketOptionText.shortName(0));
                tvSideBadge.setBackgroundResource(R.drawable.bg_badge_yes);
            } else {
                indicator.setBackgroundColor(0xFFE11D48);
                tvSideBadge.setText((isSell ? "卖出 " : "") + GoldMarketOptionText.shortName(1));
                tvSideBadge.setBackgroundResource(R.drawable.bg_badge_no);
            }

            boolean snapshotRow = GoldPositionHistoryPresenter.isSnapshotRow(trade);

            // Trade-source badge
            if (snapshotRow) {
                tvManagedBadge.setVisibility(View.VISIBLE);
                tvManagedBadge.setText("持仓快照");
                tvManagedBadge.setTextColor(0xFF64748B);
                tvManagedBadge.setBackgroundResource(R.drawable.bg_badge_ai);
            } else if (trade.isAiManaged) {
                tvManagedBadge.setVisibility(View.VISIBLE);
                tvManagedBadge.setText("AI 托管");
            } else {
                tvManagedBadge.setVisibility(View.VISIBLE);
                tvManagedBadge.setText("手动");
                tvManagedBadge.setTextColor(0xFF64748B);
                tvManagedBadge.setBackgroundResource(R.drawable.bg_badge_ai);
            }

            // Time
            tvTradeTime.setText(snapshotRow ? "链上持仓快照" : formatTradeTime(trade.createdAt));

            // Amount in BKC
            if (snapshotRow) {
                tvTradeAmount.setText("投入记录同步中…");
            } else {
                try {
                    BigInteger amountWei = new BigInteger(trade.amountWei);
                    BigDecimal bkc = new BigDecimal(amountWei).divide(
                            new BigDecimal("1000000000000000000"), 2, RoundingMode.HALF_UP);
                    tvTradeAmount.setText(String.format(Locale.getDefault(), "%s%s BKC",
                            isSell ? "到账 " : "",
                            bkc.stripTrailingZeros().toPlainString()));
                } catch (NumberFormatException e) {
                    tvTradeAmount.setText("-- BKC");
                }
            }

            // Share amount
            String shareAmountText = formatShareAmount(trade.shareAmountWei);
            tvTradeShares.setText(shareAmountText != null
                    ? (isSell ? "卖出 " : "") + shareAmountText
                    : "份额同步中…");

            tradeHistoryContainer.addView(row);
        }
    }

    private void normalizeTradeHistory(List<BackendApiClient.TradeDTO> trades) {
        Collections.sort(trades,
                (a, b) -> Long.compare(parseTradeTimeMillis(b != null ? b.createdAt : null),
                        parseTradeTimeMillis(a != null ? a.createdAt : null)));

        BigInteger previousYesShares = BigInteger.ZERO;
        BigInteger previousNoShares = BigInteger.ZERO;
        for (int i = trades.size() - 1; i >= 0; i--) {
            BackendApiClient.TradeDTO trade = trades.get(i);
            if (trade == null) continue;

            BigInteger afterYes = parseNullableWei(trade.mySharesYESAfter);
            BigInteger afterNo = parseNullableWei(trade.mySharesNOAfter);
            int optionId = trade.optionId;

            if ("BUY".equalsIgnoreCase(trade.tradeType)
                    && isMissingShareAmount(trade.shareAmountWei)) {
                BigInteger beforeShares = optionId == 0 ? previousYesShares : previousNoShares;
                BigInteger afterShares = optionId == 0 ? afterYes : afterNo;
                if (afterShares != null) {
                    BigInteger delta = afterShares.subtract(beforeShares);
                    if (delta.signum() > 0) {
                        trade.shareAmountWei = delta.toString();
                    }
                }
            }

            if (afterYes != null) previousYesShares = afterYes;
            if (afterNo != null) previousNoShares = afterNo;
        }
    }

    private boolean isMissingShareAmount(String shareAmountWei) {
        if (shareAmountWei == null || shareAmountWei.trim().isEmpty()) return true;
        try {
            return new BigInteger(shareAmountWei.trim()).compareTo(BigInteger.ZERO) <= 0;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    private String formatTradeTime(String rawTime) {
        long millis = parseTradeTimeMillis(rawTime);
        if (millis <= 0) {
            return "时间同步中";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(millis));
    }

    private long parseTradeTimeMillis(String rawTime) {
        if (rawTime == null) return -1L;
        String normalized = rawTime.trim();
        if (normalized.isEmpty()
                || normalized.startsWith("1970-01-01")
                || "0001-01-01T00:00:00Z".equals(normalized)) {
            return -1L;
        }

        String[] patterns = new String[] {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.contains("'Z'") || pattern.contains("XXX")) {
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date parsed = parser.parse(normalized);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {
            }
        }
        return -1L;
    }

    private BigInteger parseNullableWei(String rawWei) {
        if (rawWei == null || rawWei.trim().isEmpty()) return null;
        try {
            return new BigInteger(rawWei.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatShareAmount(String shareAmountWei) {
        if (shareAmountWei == null || shareAmountWei.trim().isEmpty()) return null;
        try {
            BigInteger shareWei = new BigInteger(shareAmountWei.trim());
            if (shareWei.compareTo(BigInteger.ZERO) <= 0) return null;
            BigDecimal shares = new BigDecimal(shareWei).divide(
                    new BigDecimal("1000000000000000000"), 2, RoundingMode.HALF_UP);
            return String.format(Locale.US, "%s 份额",
                    shares.stripTrailingZeros().toPlainString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
