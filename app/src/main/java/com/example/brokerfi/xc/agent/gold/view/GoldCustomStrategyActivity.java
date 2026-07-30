package com.example.brokerfi.xc.agent.gold.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketChartPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketOptionText;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Configures deterministic grid and martingale strategies executed by the Go backend. */
public class GoldCustomStrategyActivity extends AppCompatActivity {
    private static final String EXTRA_GAME_ID = "GAME_ID";
    private static final String EXTRA_CONTRACT = "CONTRACT_ADDRESS";
    private static final String EXTRA_TITLE = "MARKET_TITLE";
    private static final String EXTRA_SHOW_HISTORY = "SHOW_STRATEGY_HISTORY";

    private String strategyType = "grid";
    private String direction = "yes";
    private TextView tabGrid, tabMartingale, directionYes, directionNo, save;
    private View groupGrid, groupMartingale;
    private EditText amount, gridLower, gridUpper, gridLevels;
    private EditText trigger, multiplier, rounds;
    private LinearLayout tradeHistory;
    private TextView tradeEmpty;
    private final List<BackendApiClient.TradeDTO> strategyTrades = new ArrayList<>();
    private GoldMarketRepository repository;
    private boolean saving;
    private boolean showHistory;

    public static Intent createManagementIntent(Context context, int gameId,
                                                String contractAddress, String title) {
        return createIntent(context, gameId, contractAddress, title, false);
    }

    public static Intent createDetailIntent(Context context, int gameId,
                                            String contractAddress, String title) {
        return createIntent(context, gameId, contractAddress, title, true);
    }

    private static Intent createIntent(Context context, int gameId,
                                       String contractAddress, String title,
                                       boolean showHistory) {
        return new Intent(context, GoldCustomStrategyActivity.class)
                .putExtra(EXTRA_GAME_ID, gameId)
                .putExtra(EXTRA_CONTRACT, contractAddress)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SHOW_HISTORY, showHistory);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_custom_strategy);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tv_market_name)).setText(
                emptyDefault(getIntent().getStringExtra(EXTRA_TITLE), "当前博弈池"));
        tabGrid = findViewById(R.id.tab_grid);
        tabMartingale = findViewById(R.id.tab_martingale);
        directionYes = findViewById(R.id.direction_yes);
        directionNo = findViewById(R.id.direction_no);
        groupGrid = findViewById(R.id.group_grid);
        groupMartingale = findViewById(R.id.group_martingale);
        amount = findViewById(R.id.et_base_amount);
        gridLower = findViewById(R.id.et_grid_lower);
        gridUpper = findViewById(R.id.et_grid_upper);
        gridLevels = findViewById(R.id.et_grid_levels);
        trigger = findViewById(R.id.et_martingale_trigger);
        multiplier = findViewById(R.id.et_martingale_multiplier);
        rounds = findViewById(R.id.et_martingale_rounds);
        save = findViewById(R.id.btn_save_strategy);
        tradeHistory = findViewById(R.id.strategy_trade_history);
        tradeEmpty = findViewById(R.id.tv_strategy_trade_empty);
        showHistory = getIntent().getBooleanExtra(EXTRA_SHOW_HISTORY, false);
        findViewById(R.id.strategy_history_section).setVisibility(
                showHistory ? View.VISIBLE : View.GONE);
        tabGrid.setOnClickListener(v -> selectType("grid"));
        tabMartingale.setOnClickListener(v -> selectType("martingale"));
        directionYes.setOnClickListener(v -> selectDirection("yes"));
        directionNo.setOnClickListener(v -> selectDirection("no"));
        save.setOnClickListener(v -> save());
        selectType("grid");
        selectDirection("yes");
        loadExisting();
        if (showHistory) loadStrategyTrades();
    }

    private void loadExisting() {
        int gameId = getIntent().getIntExtra(EXTRA_GAME_ID, 0);
        String contract = getIntent().getStringExtra(EXTRA_CONTRACT);
        String privateKey = StorageUtil.getCurrentPrivatekey(this);
        if (privateKey == null || gameId <= 0) return;
        repository = new GoldMarketRepository(this, privateKey, contract);
        repository.getAiManagedConfig(gameId, new GoldMarketRepository.DataCallback<BackendApiClient.AiManagedConfig>() {
            @Override public void onSuccess(BackendApiClient.AiManagedConfig value) {
                if (value == null || "ai".equals(value.strategyType)) return;
                selectType(value.strategyType);
                selectDirection(value.direction);
                amount.setText(value.buyAmountBKC);
                gridLower.setText(trim(value.gridLowerPercent));
                gridUpper.setText(trim(value.gridUpperPercent));
                gridLevels.setText(String.valueOf(value.gridLevels));
                trigger.setText(trim(value.martingaleTriggerPercent));
                multiplier.setText(trim(value.martingaleMultiplier));
                rounds.setText(String.valueOf(value.martingaleMaxRounds));
            }
            @Override public void onError(String error) { }
        });
    }

    private void selectType(String type) {
        strategyType = "martingale".equals(type) ? "martingale" : "grid";
        groupGrid.setVisibility("grid".equals(strategyType) ? View.VISIBLE : View.GONE);
        groupMartingale.setVisibility("martingale".equals(strategyType) ? View.VISIBLE : View.GONE);
        styleChoice(tabGrid, "grid".equals(strategyType), false);
        styleChoice(tabMartingale, "martingale".equals(strategyType), false);
        renderStrategyTrades();
    }

    private void selectDirection(String value) {
        direction = "no".equals(value) ? "no" : "yes";
        styleChoice(directionYes, "yes".equals(direction), true);
        styleChoice(directionNo, "no".equals(direction), true);
    }

    private void styleChoice(TextView view, boolean selected, boolean colored) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        int accent = colored && view == directionNo ? 0xFFE11D48
                : colored ? 0xFF059669 : 0xFF0F172A;
        bg.setColor(selected ? accent : 0xFFF8FAFC);
        bg.setStroke(dp(1), selected ? accent : 0xFFD7E0EC);
        view.setBackground(bg);
        view.setTextColor(selected ? Color.WHITE : 0xFF64748B);
    }

    private void save() {
        if (saving) return;
        Double base = number(amount, "请输入基础单笔金额");
        if (base == null || base <= 0 || base > 1000) {
            error(amount, "基础金额必须大于 0 且不超过 1,000 BKC");
            return;
        }
        BackendApiClient.AiManagedConfig config = BackendApiClient.AiManagedConfig.defaults();
        config.enabled = true;
        config.strategyType = strategyType;
        config.direction = direction;
        config.buyAmountBKC = trim(base);
        config.confidenceMin = 0.70;
        config.minEdgePercent = 5;
        config.kellyFraction = 0.25;
        config.adaptiveCooldown = true;

        if ("grid".equals(strategyType)) {
            Double lower = number(gridLower, "请输入网格下限");
            Double upper = number(gridUpper, "请输入网格上限");
            Integer levels = integer(gridLevels, "请输入网格数量");
            if (lower == null || upper == null || levels == null) return;
            if (lower <= 0 || upper >= 100 || lower >= upper) {
                error(gridLower, "网格区间必须位于 0% 至 100%，且下限小于上限");
                return;
            }
            if (levels < 2 || levels > 50) {
                error(gridLevels, "网格数量必须位于 2 至 50 之间");
                return;
            }
            config.gridLowerPercent = lower;
            config.gridUpperPercent = upper;
            config.gridLevels = levels;
        } else {
            Double triggerValue = number(trigger, "请输入补仓触发概率");
            Double multiplierValue = number(multiplier, "请输入递增倍数");
            Integer roundValue = integer(rounds, "请输入最大补仓档数");
            if (triggerValue == null || multiplierValue == null || roundValue == null) return;
            if (triggerValue <= 1 || triggerValue >= 99) {
                error(trigger, "触发概率必须位于 1% 至 99% 之间");
                return;
            }
            if (multiplierValue < 1 || multiplierValue > 5) {
                error(multiplier, "递增倍数必须位于 1 至 5 之间");
                return;
            }
            if (roundValue < 1 || roundValue > 10) {
                error(rounds, "最大补仓档数必须位于 1 至 10 之间");
                return;
            }
            config.martingaleTriggerPercent = triggerValue;
            config.martingaleMultiplier = multiplierValue;
            config.martingaleMaxRounds = roundValue;
        }

        String key = StorageUtil.getCurrentPrivatekey(this);
        String contract = getIntent().getStringExtra(EXTRA_CONTRACT);
        int gameId = getIntent().getIntExtra(EXTRA_GAME_ID, 0);
        if (key == null || contract == null || gameId <= 0) {
            Toast.makeText(this, "钱包或博弈池配置不可用", Toast.LENGTH_LONG).show();
            return;
        }
        saving = true;
        save.setEnabled(false);
        save.setText("正在保存…");
        repository = new GoldMarketRepository(this, key, contract);
        repository.configureAiManaged(gameId, true, config, new GoldMarketRepository.DataCallback<Boolean>() {
            @Override public void onSuccess(Boolean value) {
                saving = false;
                save.setEnabled(true);
                save.setText("保存修改");
                Toast.makeText(GoldCustomStrategyActivity.this,
                        "策略配置已保存", Toast.LENGTH_SHORT).show();
                if (showHistory) loadStrategyTrades();
            }
            @Override public void onError(String message) {
                saving = false;
                save.setEnabled(true);
                save.setText("保存并启动策略");
                Toast.makeText(GoldCustomStrategyActivity.this,
                        emptyDefault(message, "无法保存策略"), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadStrategyTrades() {
        if (!showHistory) return;
        int gameId = getIntent().getIntExtra(EXTRA_GAME_ID, 0);
        String key = StorageUtil.getCurrentPrivatekey(this);
        String contract = getIntent().getStringExtra(EXTRA_CONTRACT);
        if (gameId <= 0 || key == null) return;
        if (repository == null) {
            repository = new GoldMarketRepository(this, key, contract);
        }
        final String wallet = repository.getWalletAddress();
        new Thread(() -> {
            List<BackendApiClient.TradeDTO> loaded = new ArrayList<>();
            try {
                List<BackendApiClient.TradeDTO> values =
                        BackendApiClient.fetchTradeHistory(gameId, wallet);
                if (values != null) {
                    for (BackendApiClient.TradeDTO value : values) {
                        if (value == null || !value.isSuccess) continue;
                        if (!"BUY".equalsIgnoreCase(value.tradeType)
                                && !"SELL".equalsIgnoreCase(value.tradeType)) continue;
                        String source = GoldMarketChartPresenter.normalizeExecutionSource(
                                value.executionSource, value.isAiManaged);
                        if ("grid".equals(source) || "martingale".equals(source)) {
                            loaded.add(value);
                        }
                    }
                }
                Collections.sort(loaded,
                        (left, right) -> Long.compare(right.timestampSec, left.timestampSec));
            } catch (Exception ignored) {
                // The editor remains usable if the history endpoint is temporarily unavailable.
            }
            runOnUiThread(() -> {
                strategyTrades.clear();
                strategyTrades.addAll(loaded);
                renderStrategyTrades();
            });
        }).start();
    }

    private void renderStrategyTrades() {
        if (tradeHistory == null || tradeEmpty == null) return;
        tradeHistory.removeAllViews();
        int count = 0;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (BackendApiClient.TradeDTO trade : strategyTrades) {
            String source = GoldMarketChartPresenter.normalizeExecutionSource(
                    trade.executionSource, trade.isAiManaged);
            if (!strategyType.equals(source)) continue;
            count++;
            View row = inflater.inflate(
                    R.layout.item_trade_history, tradeHistory, false);
            boolean yes = trade.optionId == 0;
            boolean sell = "SELL".equalsIgnoreCase(trade.tradeType);
            View indicator = row.findViewById(R.id.indicator_side);
            TextView side = row.findViewById(R.id.tv_side_badge);
            TextView sourceBadge = row.findViewById(R.id.tv_managed_badge);
            TextView time = row.findViewById(R.id.tv_trade_time);
            TextView tradeAmount = row.findViewById(R.id.tv_trade_amount);
            TextView tradeShares = row.findViewById(R.id.tv_trade_shares);
            int sideColor = yes ? 0xFF059669 : 0xFFE11D48;
            indicator.setBackgroundColor(sideColor);
            side.setText((sell ? "卖出 " : "买入 ")
                    + GoldMarketOptionText.shortName(yes ? 0 : 1));
            styleTradeBadge(side, sideColor, yes ? 0xFFECFDF5 : 0xFFFFF1F2);
            boolean grid = "grid".equals(source);
            sourceBadge.setText(grid ? "网格策略" : "马丁格尔");
            styleTradeBadge(sourceBadge,
                    grid ? 0xFF0F766E : 0xFFB45309,
                    grid ? 0xFFF0FDFA : 0xFFFFF7ED);
            time.setText(formatTradeTime(trade.timestampSec));
            tradeAmount.setText((sell ? "到账 " : "投入 ")
                    + formatBkc(trade.amountWei) + " BKC");
            tradeShares.setText((sell ? "卖出 " : "获得 ")
                    + formatShare(trade.shareAmountWei) + " 份额");
            tradeHistory.addView(row);
        }
        tradeEmpty.setText("grid".equals(strategyType)
                ? "网格策略暂未触发交易" : "马丁格尔策略暂未触发交易");
        tradeEmpty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private void styleTradeBadge(TextView badge, int textColor, int backgroundColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dp(6));
        badge.setTextColor(textColor);
        badge.setBackground(background);
    }

    private String formatTradeTime(long timestampSec) {
        if (timestampSec <= 0) return "时间同步中";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(timestampSec * 1000L));
    }

    private String formatBkc(String wei) {
        try {
            return GoldNoteMarketActivity.formatBkc(new BigInteger(wei));
        } catch (Exception ignored) {
            return "--";
        }
    }

    private String formatShare(String wei) {
        try {
            return GoldNoteMarketActivity.formatShareAmount(new BigInteger(wei));
        } catch (Exception ignored) {
            return "--";
        }
    }

    private Double number(EditText field, String message) {
        try { return Double.parseDouble(field.getText().toString().trim()); }
        catch (Exception ignored) { error(field, message); return null; }
    }

    private Integer integer(EditText field, String message) {
        try { return Integer.parseInt(field.getText().toString().trim()); }
        catch (Exception ignored) { error(field, message); return null; }
    }

    private void error(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    private static String emptyDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
