package com.example.brokerfi.xc.agent.gold.view;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.data.PinataClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionValuation;
import com.example.brokerfi.xc.agent.gold.viewmodel.GoldMarketDetailViewModel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GoldPositionDetailActivity extends AppCompatActivity {
    private int gameId;
    private GoldMarketRepository.GameModel currentGame;
    private GoldMarketDetailViewModel viewModel;
    private List<BackendApiClient.TradeDTO> tradeHistory = new ArrayList<>();

    private ImageView ivPoolIcon;
    private TextView tvPoolDesc, tvPoolCondition, tvStatusBadge;
    private TextView tvPosYesLabel, tvPosYesShares, tvPosNoLabel, tvPosNoShares;
    private TextView tvPositionEmpty, tvCurrentValue, tvTotalInvested, tvReturnRate;
    private View rowPositionYes, rowPositionNo, rowReturnRate;
    private LinearLayout tradeHistoryContainer;
    private TextView tvTradeEmpty;
    private SwipeRefreshLayout swipeRefresh;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_position_detail);

        gameId = getIntent().getIntExtra("GAME_ID", -1);
        if (gameId <= 0) {
            Toast.makeText(this, "无效的博弈池 ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new androidx.lifecycle.ViewModelProvider(this).get(GoldMarketDetailViewModel.class);
        initViews();
        observeViewModel();
        viewModel.loadGameInfo(gameId);
        loadTradeHistory();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ivPoolIcon = findViewById(R.id.iv_pool_icon);
        tvPoolDesc = findViewById(R.id.tv_pool_desc);
        tvPoolCondition = findViewById(R.id.tv_pool_condition);
        tvStatusBadge = findViewById(R.id.tv_status_badge);

        tvPosYesLabel = findViewById(R.id.tv_pos_yes_label);
        tvPosYesShares = findViewById(R.id.tv_pos_yes_shares);
        tvPosNoLabel = findViewById(R.id.tv_pos_no_label);
        tvPosNoShares = findViewById(R.id.tv_pos_no_shares);
        tvPositionEmpty = findViewById(R.id.tv_position_empty);
        rowPositionYes = findViewById(R.id.row_position_yes);
        rowPositionNo = findViewById(R.id.row_position_no);

        tvCurrentValue = findViewById(R.id.tv_current_value);
        tvTotalInvested = findViewById(R.id.tv_total_invested);
        tvReturnRate = findViewById(R.id.tv_return_rate);
        rowReturnRate = findViewById(R.id.row_return_rate);

        tradeHistoryContainer = findViewById(R.id.trade_history_container);
        tvTradeEmpty = findViewById(R.id.tv_trade_empty);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.loadGameInfo(gameId);
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
    }

    private void loadTradeHistory() {
        // 在后台线程加载交易历史
        new Thread(() -> {
            try {
                String userAddress = viewModel.getWalletAddress();
                List<BackendApiClient.TradeDTO> trades = BackendApiClient.fetchTradeHistory(gameId, userAddress);
                tradeHistory.clear();
                if (trades != null) tradeHistory.addAll(trades);
            } catch (Exception e) {
                // 后端可能还没有这个接口，静默处理
                tradeHistory.clear();
            }
            runOnUiThread(this::updateTradeHistoryUI);
        }).start();
    }

    private void updateUI() {
        if (currentGame == null) {
            swipeRefresh.setRefreshing(false);
            return;
        }

        // Pool Info
        tvPoolDesc.setText(currentGame.desc != null && !currentGame.desc.isEmpty() ? currentGame.desc : "博弈池 #" + currentGame.id);
        tvPoolCondition.setText("条件: " + (currentGame.condition != null ? currentGame.condition : "暂无"));

        if (currentGame.avatarUrl != null && !currentGame.avatarUrl.isEmpty()) {
            Glide.with(this).load(PinataClient.IPFS_GATEWAY + currentGame.avatarUrl)
                    .placeholder(R.drawable.apartment_icon).into(ivPoolIcon);
        } else {
            ivPoolIcon.setImageResource(R.drawable.apartment_icon);
        }

        // Status Badge
        if (currentGame.isRefunded) {
            tvStatusBadge.setText("已退款");
            tvStatusBadge.setTextColor(0xFFD97706);
            tvStatusBadge.setBackgroundResource(R.drawable.bg_status_refunded);
        } else if (currentGame.isResolved) {
            tvStatusBadge.setText("已结算");
            tvStatusBadge.setTextColor(0xFF7C3AED);
            tvStatusBadge.setBackgroundResource(R.drawable.bg_status_resolved);
        } else {
            tvStatusBadge.setText("进行中");
            tvStatusBadge.setTextColor(0xFF059669);
            tvStatusBadge.setBackgroundResource(R.drawable.bg_status_active);
        }

        // Position Summary
        updatePositionUI();

        // Trade History
        updateTradeHistoryUI();

        swipeRefresh.setRefreshing(false);
    }

    private void updatePositionUI() {
        if (currentGame.myShares == null || currentGame.myShares.size() < 2) {
            showEmptyPosition();
            return;
        }

        BigInteger sYes = currentGame.myShares.get(0);
        BigInteger sNo = currentGame.myShares.get(1);
        boolean hasYes = sYes != null && sYes.signum() > 0;
        boolean hasNo = sNo != null && sNo.signum() > 0;

        if (!hasYes && !hasNo) {
            showEmptyPosition();
            return;
        }

        tvPositionEmpty.setVisibility(View.GONE);

        String yesName = (currentGame.optionNames != null && !currentGame.optionNames.isEmpty())
                ? currentGame.optionNames.get(0) : "YES (达成)";
        String noName = (currentGame.optionNames != null && currentGame.optionNames.size() > 1)
                ? currentGame.optionNames.get(1) : "NO (未达成)";

        if (hasYes) {
            rowPositionYes.setVisibility(View.VISIBLE);
            tvPosYesLabel.setText(yesName);
            tvPosYesShares.setText(GoldNoteMarketActivity.formatShareAmount(sYes) + " 份额");
        } else {
            rowPositionYes.setVisibility(View.GONE);
        }

        if (hasNo) {
            rowPositionNo.setVisibility(View.VISIBLE);
            tvPosNoLabel.setText(noName);
            tvPosNoShares.setText(GoldNoteMarketActivity.formatShareAmount(sNo) + " 份额");
        } else {
            rowPositionNo.setVisibility(View.GONE);
        }

        // Current Value
        GoldPositionValuation.MarketValue marketValue = GoldPositionValuation.calculateMarket(currentGame);
        if (marketValue.isComplete()) {
            tvCurrentValue.setText(GoldNoteMarketActivity.formatBkc(marketValue.getValueWei()) + " BKC");
        } else {
            tvCurrentValue.setText("暂不可估值");
        }

        // Total Invested & Return Rate
        calculateReturnRate();
    }

    private void showEmptyPosition() {
        rowPositionYes.setVisibility(View.GONE);
        rowPositionNo.setVisibility(View.GONE);
        tvPositionEmpty.setVisibility(View.VISIBLE);
        tvCurrentValue.setText("-- BKC");
        tvTotalInvested.setText("-- BKC");
        rowReturnRate.setVisibility(View.GONE);
    }

    private void calculateReturnRate() {
        if (tradeHistory.isEmpty()) {
            tvTotalInvested.setText("-- BKC");
            rowReturnRate.setVisibility(View.GONE);
            return;
        }

        // Sum all BUY trade amounts
        BigInteger totalInvestedWei = BigInteger.ZERO;
        for (BackendApiClient.TradeDTO trade : tradeHistory) {
            if ("BUY".equalsIgnoreCase(trade.tradeType) && trade.isSuccess) {
                try {
                    BigInteger amount = new BigInteger(trade.amountWei);
                    totalInvestedWei = totalInvestedWei.add(amount);
                } catch (NumberFormatException ignored) {}
            }
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
        // Return rate = (currentValue - invested) / invested * 100%
        BigDecimal currentBkc = new BigDecimal(currentValueWei).divide(
                new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP);
        BigDecimal diff = currentBkc.subtract(investedBkc);
        double rate = diff.divide(investedBkc, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100")).doubleValue();

        rowReturnRate.setVisibility(View.VISIBLE);
        String prefix = rate >= 0 ? "+" : "";
        tvReturnRate.setText(String.format(Locale.getDefault(), "收益率 %s%.2f%%", prefix, rate));
        tvReturnRate.setTextColor(rate >= 0 ? 0xFF059669 : 0xFFE11D48);
    }

    private void updateTradeHistoryUI() {
        tradeHistoryContainer.removeAllViews();

        // Filter to only BUY trades
        List<BackendApiClient.TradeDTO> buyTrades = new ArrayList<>();
        for (BackendApiClient.TradeDTO trade : tradeHistory) {
            if ("BUY".equalsIgnoreCase(trade.tradeType)) {
                buyTrades.add(trade);
            }
        }

        if (buyTrades.isEmpty()) {
            tvTradeEmpty.setVisibility(View.VISIBLE);
            tradeHistoryContainer.addView(tvTradeEmpty);
            return;
        }

        tvTradeEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (BackendApiClient.TradeDTO trade : buyTrades) {
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
            if (isYes) {
                indicator.setBackgroundColor(0xFF059669);
                tvSideBadge.setText("YES");
                tvSideBadge.setBackgroundResource(R.drawable.bg_badge_yes);
            } else {
                indicator.setBackgroundColor(0xFFE11D48);
                tvSideBadge.setText("NO");
                tvSideBadge.setBackgroundResource(R.drawable.bg_badge_no);
            }

            // AI Managed badge
            if (trade.isAiManaged) {
                tvManagedBadge.setVisibility(View.VISIBLE);
                tvManagedBadge.setText("AI托管");
            } else {
                tvManagedBadge.setVisibility(View.VISIBLE);
                tvManagedBadge.setText("手动");
                tvManagedBadge.setTextColor(0xFF64748B);
                tvManagedBadge.setBackgroundResource(R.drawable.bg_badge_ai);
            }

            // Time
            try {
                tvTradeTime.setText(trade.createdAt);
            } catch (Exception e) {
                tvTradeTime.setText("--");
            }

            // Amount in BKC
            try {
                BigInteger amountWei = new BigInteger(trade.amountWei);
                BigDecimal bkc = new BigDecimal(amountWei).divide(
                        new BigDecimal("1000000000000000000"), 2, RoundingMode.HALF_UP);
                tvTradeAmount.setText(String.format(Locale.getDefault(), "%s BKC",
                        bkc.stripTrailingZeros().toPlainString()));
            } catch (NumberFormatException e) {
                tvTradeAmount.setText("-- BKC");
            }

            // Share amount
            try {
                if (trade.shareAmountWei != null && !trade.shareAmountWei.isEmpty()) {
                    BigInteger shareWei = new BigInteger(trade.shareAmountWei);
                    BigDecimal shares = new BigDecimal(shareWei).divide(
                            new BigDecimal("1000000000000000000"), 2, RoundingMode.HALF_UP);
                    tvTradeShares.setText(String.format(Locale.getDefault(), "%s 份额",
                            shares.stripTrailingZeros().toPlainString()));
                } else {
                    tvTradeShares.setText("-- 份额");
                }
            } catch (NumberFormatException e) {
                tvTradeShares.setText("-- 份额");
            }

            tradeHistoryContainer.addView(row);
        }
    }
}
