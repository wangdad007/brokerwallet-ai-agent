package com.example.brokerfi.xc.agent.gold.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionValuation;
import com.example.brokerfi.xc.agent.gold.viewmodel.GoldMarketDetailViewModel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Dedicated LP view: liquidity position summary plus every add/remove record. */
public class GoldLiquidityDetailActivity extends AppCompatActivity {
    private static final String EXTRA_GAME_ID = "GAME_ID";
    private static final String EXTRA_CONTRACT = "CONTRACT_ADDRESS";
    private static final String EXTRA_TITLE = "MARKET_TITLE";

    private int gameId;
    private String contractAddress;
    private String fallbackTitle;
    private GoldMarketDetailViewModel viewModel;
    private GoldMarketRepository.GameModel currentGame;
    private final List<BackendApiClient.TradeDTO> liquidityHistory = new ArrayList<>();
    private final AtomicInteger historyRequestSequence = new AtomicInteger(0);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int historyRetryCount;
    private boolean historyLoading;

    private TextView marketTitle, myValue, poolValue, lpSummary, historyEmpty;
    private LinearLayout historyContainer;
    private SwipeRefreshLayout swipeRefresh;

    public static Intent createIntent(Context context, int gameId,
                                      String contractAddress, String title) {
        return new Intent(context, GoldLiquidityDetailActivity.class)
                .putExtra(EXTRA_GAME_ID, gameId)
                .putExtra(EXTRA_CONTRACT, contractAddress)
                .putExtra(EXTRA_TITLE, title);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_liquidity_detail);
        gameId = getIntent().getIntExtra(EXTRA_GAME_ID, 0);
        contractAddress = getIntent().getStringExtra(EXTRA_CONTRACT);
        fallbackTitle = getIntent().getStringExtra(EXTRA_TITLE);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        marketTitle = findViewById(R.id.tv_market_title);
        myValue = findViewById(R.id.tv_my_value);
        poolValue = findViewById(R.id.tv_pool_value);
        lpSummary = findViewById(R.id.tv_lp_summary);
        historyContainer = findViewById(R.id.liquidity_history_container);
        historyEmpty = findViewById(R.id.tv_history_empty);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        marketTitle.setText(GoldMarketTextStyler.style(
                nonEmpty(fallbackTitle, "当前博弈池"), true));

        viewModel = new ViewModelProvider(this).get(GoldMarketDetailViewModel.class);
        viewModel.getCurrentGame().observe(this, game -> {
            currentGame = game;
            renderSummary();
            // The chain snapshot and history request finish independently.
            // Re-render here so a legacy/on-chain LP record appears immediately
            // even when the history request completed first.
            renderHistory();
        });
        viewModel.getIsLoading().observe(this, loading -> {
            if (!loading && !historyLoading) swipeRefresh.setRefreshing(false);
        });
        swipeRefresh.setOnRefreshListener(this::refresh);
        refresh();
    }

    private void refresh() {
        int requestSequence = historyRequestSequence.incrementAndGet();
        historyRetryCount = 0;
        historyLoading = true;
        historyEmpty.setText("正在加载质押记录…");
        historyEmpty.setVisibility(View.VISIBLE);
        swipeRefresh.setRefreshing(true);
        viewModel.loadGameInfo(gameId, contractAddress);
        loadHistory(requestSequence);
    }

    private void renderSummary() {
        if (currentGame == null) return;
        String title = nonEmpty(currentGame.desc, fallbackTitle);
        marketTitle.setText(GoldMarketTextStyler.style(
                nonEmpty(title, "当前博弈池"), true));
        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateLiquidityMarket(currentGame);
        myValue.setText(value.isComplete()
                ? GoldNoteMarketActivity.formatBkc(value.getValueWei()) + " BKC"
                : "估值暂不可用");
        poolValue.setText(currentGame.totalPool == null
                ? "-- BKC"
                : GoldNoteMarketActivity.formatBkc(currentGame.totalPool) + " BKC");
        lpSummary.setText(GoldNoteMarketActivity.formatShareAmount(
                currentGame.myLiquidityShares) + " LP · 占池 "
                + liquidityPercent(currentGame));
    }

    private void loadHistory(int requestSequence) {
        if (gameId <= 0) {
            historyLoading = false;
            historyEmpty.setText("无法识别当前博弈池");
            swipeRefresh.setRefreshing(false);
            return;
        }
        new Thread(() -> {
            List<BackendApiClient.TradeDTO> loaded = new ArrayList<>();
            boolean requestSucceeded = false;
            try {
                String wallet = viewModel.getWalletAddress();
                List<BackendApiClient.TradeDTO> records =
                        BackendApiClient.fetchTradeHistory(gameId, wallet);
                if (records != null) {
                    for (BackendApiClient.TradeDTO record : records) {
                        if (record == null || !record.isSuccess) continue;
                        if ("LIQUIDITY_ADD".equalsIgnoreCase(record.tradeType)
                                || "LIQUIDITY_REMOVE".equalsIgnoreCase(record.tradeType)) {
                            loaded.add(record);
                        }
                    }
                }
                Collections.sort(loaded,
                        (left, right) -> Long.compare(tradeTime(right), tradeTime(left)));
                requestSucceeded = true;
            } catch (Exception ignored) {
                // A bounded automatic retry below handles startup/backend races.
            }
            final boolean succeeded = requestSucceeded;
            runOnUiThread(() -> {
                if (requestSequence != historyRequestSequence.get()) return;
                if (succeeded) {
                    liquidityHistory.clear();
                    liquidityHistory.addAll(loaded);
                }
                boolean shouldRetry = (!succeeded || loaded.isEmpty())
                        && historyRetryCount < 2;
                if (!shouldRetry) {
                    historyLoading = false;
                }
                renderHistory();
                if (shouldRetry) {
                    historyRetryCount++;
                    mainHandler.postDelayed(
                            () -> loadHistory(requestSequence),
                            600L * historyRetryCount);
                } else {
                    swipeRefresh.setRefreshing(false);
                }
            });
        }).start();
    }

    private void renderHistory() {
        historyContainer.removeAllViews();
        historyEmpty.setText(historyLoading
                ? "正在加载质押记录…" : "暂无质押记录");
        boolean hasRecordedAdd = false;
        for (BackendApiClient.TradeDTO trade : liquidityHistory) {
            if ("LIQUIDITY_ADD".equalsIgnoreCase(trade.tradeType)) {
                hasRecordedAdd = true;
                break;
            }
        }
        boolean showLegacySnapshot = !hasRecordedAdd && currentGame != null
                && currentGame.myLiquidityShares != null
                && currentGame.myLiquidityShares.signum() > 0;
        historyEmpty.setVisibility(
                liquidityHistory.isEmpty() && !showLegacySnapshot
                        ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (BackendApiClient.TradeDTO trade : liquidityHistory) {
            View row = inflater.inflate(
                    R.layout.item_trade_history, historyContainer, false);
            boolean remove = "LIQUIDITY_REMOVE".equalsIgnoreCase(trade.tradeType);
            View indicator = row.findViewById(R.id.indicator_side);
            TextView action = row.findViewById(R.id.tv_side_badge);
            TextView source = row.findViewById(R.id.tv_managed_badge);
            TextView time = row.findViewById(R.id.tv_trade_time);
            TextView amount = row.findViewById(R.id.tv_trade_amount);
            TextView shares = row.findViewById(R.id.tv_trade_shares);
            int accent = remove ? 0xFF64748B : 0xFF0F766E;
            int surface = remove ? 0xFFF1F5F9 : 0xFFF0FDFA;
            indicator.setBackgroundColor(accent);
            action.setText(remove ? "取回" : "质押");
            styleBadge(action, accent, surface);
            source.setText("流动性 LP");
            styleBadge(source, 0xFF475569, 0xFFF1F5F9);
            time.setText(formatTime(trade));
            amount.setText((remove ? "到账 " : "投入 ")
                    + formatBkc(trade.amountWei) + " BKC"
                    + (remove ? " · 销毁 " : " · 获得 ")
                    + formatLp(trade.shareAmountWei) + " LP");
            shares.setText(returnedShareSummary(trade));
            historyContainer.addView(row);
        }
        if (showLegacySnapshot) {
            View row = inflater.inflate(
                    R.layout.item_trade_history, historyContainer, false);
            View indicator = row.findViewById(R.id.indicator_side);
            TextView action = row.findViewById(R.id.tv_side_badge);
            TextView source = row.findViewById(R.id.tv_managed_badge);
            TextView time = row.findViewById(R.id.tv_trade_time);
            TextView amount = row.findViewById(R.id.tv_trade_amount);
            TextView shares = row.findViewById(R.id.tv_trade_shares);
            indicator.setBackgroundColor(0xFF0F766E);
            action.setText(currentGame.isCreator ? "初始质押" : "历史质押");
            styleBadge(action, 0xFF0F766E, 0xFFF0FDFA);
            source.setText("链上锁定");
            styleBadge(source, 0xFF475569, 0xFFF1F5F9);
            time.setText(currentGame.isCreator
                    ? "创建市场时注入 · 链上补全"
                    : "历史明细缺失 · 链上持仓补全");
            GoldPositionValuation.MarketValue currentValue =
                    GoldPositionValuation.calculateLiquidityMarket(currentGame);
            amount.setText(currentValue.isComplete()
                    ? "当前价值 "
                        + GoldNoteMarketActivity.formatBkc(
                                currentValue.getValueWei()) + " BKC"
                    : "当前价值暂不可用");
            shares.setText("获得 " + GoldNoteMarketActivity.formatShareAmount(
                    currentGame.myLiquidityShares) + " LP");
            historyContainer.addView(row);
        }
    }

    private void styleBadge(TextView badge, int textColor, int backgroundColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dp(6));
        badge.setTextColor(textColor);
        badge.setBackground(background);
    }

    private String liquidityPercent(GoldMarketRepository.GameModel game) {
        if (game.myLiquidityShares == null || game.totalLiquidityShares == null
                || game.totalLiquidityShares.signum() <= 0) return "--";
        return new BigDecimal(game.myLiquidityShares)
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal(game.totalLiquidityShares), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%";
    }

    private String formatBkc(String wei) {
        try {
            return GoldNoteMarketActivity.formatBkc(new BigInteger(wei));
        } catch (Exception ignored) {
            return "--";
        }
    }

    private String formatLp(String wei) {
        try {
            return GoldNoteMarketActivity.formatShareAmount(new BigInteger(wei));
        } catch (Exception ignored) {
            return "--";
        }
    }

    private String returnedShareSummary(BackendApiClient.TradeDTO trade) {
        if (trade == null || (isBlank(trade.returnedYesWei)
                && isBlank(trade.returnedNoWei))) {
            return "份额返还明细未记录";
        }
        BigInteger yes = parseNonNegative(trade.returnedYesWei);
        BigInteger no = parseNonNegative(trade.returnedNoWei);
        if (yes.signum() == 0 && no.signum() == 0) {
            return "本次没有额外 YES/NO 份额";
        }
        StringBuilder result = new StringBuilder("另获 ");
        if (yes.signum() > 0) {
            result.append(GoldNoteMarketActivity.formatShareAmount(yes))
                    .append(" YES");
        }
        if (no.signum() > 0) {
            if (yes.signum() > 0) result.append(" · ");
            result.append(GoldNoteMarketActivity.formatShareAmount(no))
                    .append(" NO");
        }
        return result.append(" 份额").toString();
    }

    private BigInteger parseNonNegative(String value) {
        try {
            return new BigInteger(value).max(BigInteger.ZERO);
        } catch (Exception ignored) {
            return BigInteger.ZERO;
        }
    }

    private String formatTime(BackendApiClient.TradeDTO trade) {
        long seconds = tradeTime(trade);
        if (seconds <= 0) return "时间同步中";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(seconds * 1000L));
    }

    private long tradeTime(BackendApiClient.TradeDTO trade) {
        return trade == null ? 0L : trade.timestampSec;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override protected void onDestroy() {
        historyRequestSequence.incrementAndGet();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
