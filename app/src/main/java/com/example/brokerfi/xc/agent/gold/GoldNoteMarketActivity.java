package com.example.brokerfi.xc.agent.gold;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.BuildConfig;
import com.example.brokerfi.R;
import com.example.brokerfi.xc.SecurityUtil;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.DeepSeekClient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GoldNoteMarketActivity extends AppCompatActivity {
    private static final BigDecimal DISPLAY_TOKEN_UNIT = new BigDecimal("1000000000000000000");
    private static final BigDecimal MIN_DISPLAY_SHARE = new BigDecimal("0.000001");

    private GoldMarketRepository repository;
    private GoldMarketRepository.GameModel currentGame;
    private final List<GoldMarketRepository.GameModel> availableGames = new ArrayList<>();
    private String currentPrivateKey = "";
    private GoldAdvisoryManager.Advisory latestMarketQuote;
    private GoldAdvisoryManager.Advisory latestAdvice;

    private TextView tvGoldPrice, tvGoldChange, tvGoldQuoteMeta;
    private TextView tvAiSignal, tvAiConfidence, tvAiSummary;
    private TextView tvUpPct, tvDownPct, tvPool, tvCountdown;
    private TextView tvMarketMode, tvMarketSecurityHint;
    private TextView tvHoldings;
    private View barUp, barDown;
    private LinearLayout marketListContainer;
    private SwipeRefreshLayout swipeRefresh;
    private View btnClaimReward;
    private View cardAiAdvice;

    private boolean destroyed = false;
    private boolean developerMarketToolsEnabled;
    private int selectedGameId = 0;
    private String selectedContractAddress = "";
    private int hiddenClosedMarketCount = 0;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable priceRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            loadGoldPrice();
            timerHandler.postDelayed(this, 60_000);
        }
    };
    private final Runnable aiRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            loadAiAdvice();
            timerHandler.postDelayed(this, 300_000);
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
        setContentView(R.layout.activity_gold_note_market);
        destroyed = false;
        developerMarketToolsEnabled = GoldMarketSecurityPolicy.isDeveloperMarketToolsEnabled(BuildConfig.DEBUG);
        DeepSeekClient.init(this);

        try {
            currentPrivateKey = StorageUtil.getCurrentPrivatekey(this);
            if (currentPrivateKey == null || currentPrivateKey.isEmpty()) {
                Toast.makeText(this, "请先创建或导入钱包", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            // 检查私钥格式：黄金票据市场只支持新格式（64位十六进制）私钥
            if (!SecurityUtil.isNewPrivateKeyFormat(currentPrivateKey)) {
                Toast.makeText(this, "黄金票据市场仅支持新格式钱包\n请使用新创建的账户", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            // 预检查：测试是否能从私钥推导出有效地址
            String testAddr = BrokerChainClient.getAddress(currentPrivateKey);
            if (testAddr == null || testAddr.isEmpty()) {
                Toast.makeText(this, "无法从私钥推导钱包地址\n请确认私钥格式正确", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            if (testAddr.equals("0x") || testAddr.length() < 40) {
                Toast.makeText(this, "推导出的钱包地址无效\n请确认私钥格式正确", Toast.LENGTH_LONG).show();
                finish();
                return;
            }

            repository = new GoldMarketRepository(this, currentPrivateKey);
        } catch (Exception e) {
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        initViews();
        loadEverything();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        timerHandler.removeCallbacks(priceRefreshRunnable);
        timerHandler.removeCallbacks(aiRefreshRunnable);
        timerHandler.removeCallbacks(countdownRunnable);
        super.onDestroy();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tvGoldPrice = findViewById(R.id.tv_gold_price);
        tvGoldPrice.setOnLongClickListener(v -> {
            if (!developerMarketToolsEnabled) {
                Toast.makeText(this, "\u6b63\u5f0f\u7248\u53ea\u80fd\u8fde\u63a5\u5b98\u65b9\u9a8c\u8bc1\u5e02\u573a", Toast.LENGTH_SHORT).show();
                return true;
            }
            showContractAddressDialog();
            return true;
        });
        tvGoldChange = findViewById(R.id.tv_gold_change);
        tvGoldQuoteMeta = findViewById(R.id.tv_gold_quote_meta);
        tvAiSignal = findViewById(R.id.tv_ai_signal);
        tvAiConfidence = findViewById(R.id.tv_ai_confidence);
        tvAiSummary = findViewById(R.id.tv_ai_summary);
        tvMarketMode = findViewById(R.id.tv_market_mode);
        tvMarketSecurityHint = findViewById(R.id.tv_market_security_hint);
        tvUpPct = findViewById(R.id.tv_up_pct);
        tvDownPct = findViewById(R.id.tv_down_pct);
        tvPool = findViewById(R.id.tv_pool);
        tvCountdown = findViewById(R.id.tv_countdown);
        tvHoldings = findViewById(R.id.tv_holdings);
        marketListContainer = findViewById(R.id.market_list_container);
        barUp = findViewById(R.id.bar_up);
        barDown = findViewById(R.id.bar_down);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> {
            loadMarketData();
            loadGoldPrice();
            loadAiAdvice();
            swipeRefresh.setRefreshing(false);
        });

        cardAiAdvice = findViewById(R.id.card_ai_advice);
        cardAiAdvice.setOnClickListener(v -> {
            if (DeepSeekClient.isConfigured()) {
                Intent intent = new Intent(this, com.example.brokerfi.xc.AIAssistantActivity.class);
                intent.putExtra("INITIAL_PROMPT", buildGoldResearchPrompt());
                startActivity(intent);
            } else {
                showApiKeyDialog();
            }
        });

        btnClaimReward = findViewById(R.id.btn_claim_reward);
        btnClaimReward.setOnClickListener(v -> {
            int optionId = getClaimOptionId();
            if (optionId < 0) {
                Toast.makeText(this, "没有可领取的持仓", Toast.LENGTH_SHORT).show();
                return;
            }
            repositoryForSelectedMarket().claimReward(getSelectedGameId(), optionId,
                new GoldMarketRepository.TxCallback() {
                    @Override public void onTxSent(String txHash) {
                        Toast.makeText(GoldNoteMarketActivity.this, "领取交易已提交...",
                            Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onConfirmed(String message) {
                        if (destroyed) return;
                        Toast.makeText(GoldNoteMarketActivity.this, message,
                            Toast.LENGTH_SHORT).show();
                        loadMarketData();
                    }
                    @Override public void onError(String error) {
                        if (destroyed) return;
                        Toast.makeText(GoldNoteMarketActivity.this, error,
                            Toast.LENGTH_LONG).show();
                    }
                });
        });

        updateMarketModeUi();

        findViewById(R.id.btn_buy_up).setOnClickListener(v -> showBuyDialog(0, "看涨"));
        findViewById(R.id.btn_buy_down).setOnClickListener(v -> showBuyDialog(1, "看跌"));
        findViewById(R.id.btn_sell).setOnClickListener(v -> showSellDialog());
    }

    private void updateMarketModeUi() {
        View banner = findViewById(R.id.market_security_banner);
        if (developerMarketToolsEnabled) {
            banner.setBackgroundResource(R.drawable.bg_gold_market_status_debug);
            tvMarketMode.setText("\u5f00\u53d1\u6d4b\u8bd5\u6c60");
            tvMarketMode.setTextColor(Color.parseColor("#1D4ED8"));
            tvMarketSecurityHint.setText("当前为测试合约连接模式；市场创建请在 Remix 或官方 Oracle 端完成");
            tvMarketSecurityHint.setTextColor(Color.parseColor("#1E40AF"));
        } else {
            banner.setBackgroundResource(R.drawable.bg_gold_market_status);
            tvMarketMode.setText("\u5b98\u65b9\u9a8c\u8bc1\u6c60");
            tvMarketMode.setTextColor(Color.parseColor("#047857"));
            tvMarketSecurityHint.setText("用户端只参与交易和领取奖励，市场创建由官方 Oracle/Remix 端完成");
            tvMarketSecurityHint.setTextColor(Color.parseColor("#065F46"));
        }
    }

    private void loadEverything() {
        loadMarketData();
        loadGoldPrice();
        loadAiAdvice();
        timerHandler.post(priceRefreshRunnable);
        timerHandler.post(aiRefreshRunnable);
        timerHandler.post(countdownRunnable);
    }

    // ──── Market Data ────

    private void loadMarketData() {
        showMarketListLoading();
        availableGames.clear();
        currentGame = null;
        hiddenClosedMarketCount = 0;
        List<String> contractAddresses = GoldMarketRepository.getContractAddresses(this);
        if (contractAddresses.isEmpty()) {
            renderMarketList();
            updateMarketUI();
            return;
        }
        loadMarketsForContract(contractAddresses, 0);
    }

    private void loadMarketsForContract(List<String> contractAddresses, int contractIndex) {
        if (destroyed) return;
        if (contractIndex >= contractAddresses.size()) {
            finishMarketListLoad();
            return;
        }
        GoldMarketRepository contractRepository = repositoryForContract(contractAddresses.get(contractIndex));
        contractRepository.getGameCount(new GoldMarketRepository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                if (destroyed) return;
                if (count == null || count <= 0) {
                    loadMarketsForContract(contractAddresses, contractIndex + 1);
                    return;
                }
                loadAllMarkets(contractRepository, contractAddresses, contractIndex, count);
            }

            @Override
            public void onError(String error) {
                if (destroyed) return;
                loadMarketsForContract(contractAddresses, contractIndex + 1);
            }
        });
    }

    private void loadAllMarkets(GoldMarketRepository contractRepository,
                                List<String> contractAddresses,
                                int contractIndex,
                                int count) {
        loadMarketByIdAscending(contractRepository, contractAddresses, contractIndex, 1, Math.max(1, count));
    }

    private void loadMarketByIdAscending(GoldMarketRepository contractRepository,
                                         List<String> contractAddresses,
                                         int contractIndex,
                                         int gameId,
                                         int maxGameId) {
        if (destroyed) return;
        if (gameId > maxGameId) {
            loadMarketsForContract(contractAddresses, contractIndex + 1);
            return;
        }
        contractRepository.getGameInfo(gameId,
            new GoldMarketRepository.DataCallback<GoldMarketRepository.GameModel>() {
            @Override
            public void onSuccess(GoldMarketRepository.GameModel model) {
                if (destroyed) return;
                if (shouldShowInMarketList(model)) {
                    availableGames.add(model);
                } else {
                    hiddenClosedMarketCount++;
                }
                loadMarketByIdAscending(contractRepository, contractAddresses, contractIndex, gameId + 1, maxGameId);
            }
            @Override
            public void onError(String error) {
                if (destroyed) return;
                loadMarketByIdAscending(contractRepository, contractAddresses, contractIndex, gameId + 1, maxGameId);
            }
        });
    }

    private void loadSingleMarket(int gameId, boolean showDialogOnError) {
        repository.getGameInfo(gameId,
            new GoldMarketRepository.DataCallback<GoldMarketRepository.GameModel>() {
            @Override
            public void onSuccess(GoldMarketRepository.GameModel model) {
                if (destroyed) return;
                availableGames.clear();
                availableGames.add(model);
                selectedGameId = model.id;
                selectedContractAddress = model.contractAddress;
                currentGame = model;
                renderMarketList();
                updateMarketUI();
            }
            @Override
            public void onError(String error) {
                if (destroyed) return;
                Toast.makeText(GoldNoteMarketActivity.this,
                    "加载市场数据失败: " + error, Toast.LENGTH_LONG).show();
                if (showDialogOnError && developerMarketToolsEnabled) {
                    showContractAddressDialog();
                }
            }
        });
    }

    private void finishMarketListLoad() {
        if (availableGames.isEmpty()) {
            currentGame = null;
            renderMarketList();
            updateMarketUI();
            return;
        }
        GoldMarketRepository.GameModel selected = findGame(selectedContractAddress, selectedGameId);
        if (selected == null) {
            selected = availableGames.get(0);
            selectedGameId = selected.id;
            selectedContractAddress = selected.contractAddress;
        }
        currentGame = selected;
        renderMarketList();
        updateMarketUI();
        updateCountdown();
    }

    private GoldMarketRepository.GameModel findGame(String contractAddress, int gameId) {
        for (GoldMarketRepository.GameModel game : availableGames) {
            if (game.id == gameId && sameContract(game.contractAddress, contractAddress)) return game;
        }
        return null;
    }

    private boolean sameContract(String left, String right) {
        if (left == null || right == null) return false;
        return left.equalsIgnoreCase(right);
    }

    private void showMarketListLoading() {
        if (marketListContainer == null) return;
        marketListContainer.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("正在读取链上市场...");
        loading.setTextColor(Color.parseColor("#666666"));
        loading.setTextSize(14f);
        loading.setPadding(16, 16, 16, 16);
        marketListContainer.addView(loading);
    }

    private void renderMarketList() {
        if (marketListContainer == null) return;
        marketListContainer.removeAllViews();
        if (availableGames.isEmpty()) {
            String text = hiddenClosedMarketCount > 0
                ? "暂无进行中的链上市场\n已隐藏 " + hiddenClosedMarketCount
                    + " 个到期市场；如果刚在 Remix 创建，请确认使用了新版合约。"
                : "暂无进行中的链上市场";
            TextView empty = buildMarketRow(text, false);
            marketListContainer.addView(empty);
            return;
        }
        for (int i = 0; i < availableGames.size(); i++) {
            GoldMarketRepository.GameModel game = availableGames.get(i);
            TextView row = buildMarketRow(formatMarketRow(game, i + 1), isSelectedGame(game));
            row.setOnClickListener(v -> {
                selectedGameId = game.id;
                selectedContractAddress = game.contractAddress;
                currentGame = game;
                renderMarketList();
                updateMarketUI();
                updateCountdown();
            });
            marketListContainer.addView(row);
        }
    }

    private TextView buildMarketRow(String text, boolean selected) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextSize(14f);
        row.setTextColor(selected ? Color.parseColor("#065F46") : Color.parseColor("#111111"));
        row.setPadding(16, 14, 16, 14);
        row.setBackgroundResource(selected ? R.drawable.bg_gold_market_status : R.drawable.bg_gold_market_panel);
        row.setClickable(true);
        row.setFocusable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 8);
        row.setLayoutParams(params);
        return row;
    }

    private String formatMarketRow(GoldMarketRepository.GameModel game, int displayIndex) {
        String title = game.desc == null || game.desc.isEmpty()
            ? "黄金预测市场"
            : game.desc;
        return "市场 " + displayIndex + "  " + title + "\n"
            + "总池子 " + formatBkc(game.totalPool) + " BKC · "
            + formatMarketStatus(game) + "\n"
            + formatMarketReserveText(game);
    }

    private String formatMarketStatus(GoldMarketRepository.GameModel game) {
        if (game.isResolved) return "已结算";
        if (game.isRefunded) return "已退款";
        long remaining = remainingSecondsUntilDeadline(game.deadlineSec, System.currentTimeMillis());
        return remaining <= 0 ? "已到期" : "进行中";
    }

    private String formatMarketReserveText(GoldMarketRepository.GameModel game) {
        if (game.virtualReserves == null || game.virtualReserves.size() < 2) {
            return "看涨 --% / 看跌 --%";
        }
        BigInteger totalReserve = game.virtualReserves.get(0).add(game.virtualReserves.get(1));
        if (totalReserve.compareTo(BigInteger.ZERO) <= 0) {
            return "看涨 --% / 看跌 --%";
        }
        double upPct = game.virtualReserves.get(0).doubleValue() / totalReserve.doubleValue() * 100;
        double downPct = 100 - upPct;
        return String.format(Locale.getDefault(), "%s %.1f%% / %s %.1f%%",
            getOptionName(game, 0, "看涨"), upPct, getOptionName(game, 1, "看跌"), downPct);
    }

    private void updateMarketUI() {
        if (currentGame == null) {
            tvUpPct.setText("--%");
            tvDownPct.setText("--%");
            tvPool.setText("总池子: -- BKC");
            tvHoldings.setText("暂无持仓");
            tvCountdown.setText("倒计时: --:--:--");
            findViewById(R.id.btn_buy_up).setEnabled(false);
            findViewById(R.id.btn_buy_down).setEnabled(false);
            findViewById(R.id.btn_sell).setEnabled(false);
            return;
        }

        btnClaimReward.setVisibility(currentGame.isResolved || currentGame.isRefunded ? View.VISIBLE : View.GONE);
        boolean closed = isMarketClosed(currentGame);
        findViewById(R.id.btn_buy_up).setEnabled(!closed);
        findViewById(R.id.btn_buy_down).setEnabled(!closed);
        findViewById(R.id.btn_sell).setEnabled(!closed);

        if (currentGame.virtualReserves != null && currentGame.virtualReserves.size() >= 2) {
            BigInteger totalReserve = currentGame.virtualReserves.get(0)
                .add(currentGame.virtualReserves.get(1));
            if (totalReserve.compareTo(BigInteger.ZERO) > 0) {
                double upPrice = currentGame.virtualReserves.get(0).doubleValue()
                    / totalReserve.doubleValue() * 100;
                double downPrice = 100 - upPrice;
                tvUpPct.setText(String.format(Locale.getDefault(), "%.1f%%", upPrice));
                tvDownPct.setText(String.format(Locale.getDefault(), "%.1f%%", downPrice));

                int screenWidth = getResources().getDisplayMetrics().widthPixels - 64;
                LinearLayout.LayoutParams upParams =
                    (LinearLayout.LayoutParams) barUp.getLayoutParams();
                upParams.width = (int)(screenWidth * upPrice / 100);
                barUp.setLayoutParams(upParams);

                LinearLayout.LayoutParams downParams =
                    (LinearLayout.LayoutParams) barDown.getLayoutParams();
                downParams.width = (int)(screenWidth * downPrice / 100);
                barDown.setLayoutParams(downParams);
            }
        }

        if (currentGame.totalPool != null) {
            double pool = currentGame.totalPool.doubleValue() / 1e18;
            tvPool.setText(String.format("总池子: %,.2f BKC", pool));
        }

        if (currentGame.myShares != null && currentGame.myShares.size() >= 2) {
            BigInteger upShares = currentGame.myShares.get(0);
            BigInteger downShares = currentGame.myShares.get(1);
            if (upShares.compareTo(BigInteger.ZERO) > 0) {
                tvHoldings.setText(String.format(Locale.getDefault(), "%s · %s 份",
                    formatOptionName(0, "看涨"), formatShareAmount(upShares)));
            } else if (downShares.compareTo(BigInteger.ZERO) > 0) {
                tvHoldings.setText(String.format(Locale.getDefault(), "%s · %s 份",
                    formatOptionName(1, "看跌"), formatShareAmount(downShares)));
            } else {
                tvHoldings.setText("暂无持仓");
            }
        }
    }

    private void updateCountdown() {
        if (currentGame == null) {
            tvCountdown.setText("倒计时: --:--:--");
            return;
        }
        long remaining = remainingSecondsUntilDeadline(currentGame.deadlineSec, System.currentTimeMillis());
        if (remaining <= 0) {
            if (hideClosedSelectedMarketIfSafe()) {
                return;
            }
            tvCountdown.setText("已到期");
            return;
        }
        long d = remaining / 86400;
        long h = (remaining % 86400) / 3600;
        long m = (remaining % 3600) / 60;
        long s = remaining % 60;
        if (d > 0) {
            tvCountdown.setText(String.format(Locale.getDefault(),
                "倒计时: %d天%02d:%02d:%02d", d, h, m, s));
        } else {
            tvCountdown.setText(String.format(Locale.getDefault(),
                "倒计时: %02d:%02d:%02d", h, m, s));
        }
    }

    static long remainingSecondsUntilDeadline(long rawDeadline, long nowMillis) {
        if (rawDeadline <= 0) return 0;
        if (isMillisecondDeadline(rawDeadline)) {
            return Math.max(0, (rawDeadline - nowMillis + 999) / 1000);
        }
        return Math.max(0, rawDeadline - nowMillis / 1000);
    }

    static boolean isMillisecondDeadline(long rawDeadline) {
        return rawDeadline > 10_000_000_000L;
    }

    private boolean isMarketClosed(GoldMarketRepository.GameModel game) {
        return game == null || game.isResolved || game.isRefunded
            || remainingSecondsUntilDeadline(game.deadlineSec, System.currentTimeMillis()) <= 0;
    }

    private boolean shouldShowInMarketList(GoldMarketRepository.GameModel game) {
        return game != null && (!isMarketClosed(game) || hasUserShares(game));
    }

    private boolean hasUserShares(GoldMarketRepository.GameModel game) {
        if (game == null || game.myShares == null) return false;
        for (BigInteger shares : game.myShares) {
            if (shares != null && shares.compareTo(BigInteger.ZERO) > 0) return true;
        }
        return false;
    }

    private boolean hideClosedSelectedMarketIfSafe() {
        if (currentGame == null || !isMarketClosed(currentGame) || hasUserShares(currentGame)) {
            return false;
        }
        int expiredId = currentGame.id;
        String expiredContract = currentGame.contractAddress;
        for (int i = availableGames.size() - 1; i >= 0; i--) {
            GoldMarketRepository.GameModel game = availableGames.get(i);
            if (game.id == expiredId && sameContract(game.contractAddress, expiredContract)) {
                availableGames.remove(i);
            }
        }
        currentGame = availableGames.isEmpty() ? null : availableGames.get(0);
        selectedGameId = currentGame == null ? 0 : currentGame.id;
        selectedContractAddress = currentGame == null ? "" : currentGame.contractAddress;
        renderMarketList();
        updateMarketUI();
        return true;
    }

    private int getClaimOptionId() {
        if (currentGame == null) return -1;
        if (currentGame.isResolved) return currentGame.winningOption;
        if (currentGame.isRefunded && currentGame.myShares != null) {
            for (int i = 0; i < currentGame.myShares.size(); i++) {
                if (currentGame.myShares.get(i).compareTo(BigInteger.ZERO) > 0) return i;
            }
        }
        return -1;
    }

    // ──── Gold Price ────

    private void loadGoldPrice() {
        if (destroyed) return;
        GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                if (destroyed) return;
                latestMarketQuote = quote;
                updateGoldPriceUI();
            }
            @Override
            public void onError(String error) {
                if (destroyed) return;
                tvGoldPrice.setText("XAU $---.--/oz");
                tvGoldChange.setText("--.--%");
                tvGoldQuoteMeta.setText("行情暂不可用");
                tvGoldChange.setTextColor(Color.GRAY);
            }
        });
    }

    private void updateGoldPriceUI() {
        if (latestMarketQuote == null || latestMarketQuote.priceUsd <= 0) {
            tvGoldPrice.setText("XAU $---.--/oz");
            tvGoldChange.setText("--.--%");
            tvGoldQuoteMeta.setText("行情暂不可用");
            tvGoldChange.setTextColor(Color.GRAY);
            return;
        }
        tvGoldPrice.setText(String.format(Locale.getDefault(),
            "XAU $%,.2f/oz", latestMarketQuote.priceUsd));
        tvGoldQuoteMeta.setText(formatQuoteMeta(latestMarketQuote));
        String changeStr = String.format(Locale.getDefault(),
            "%+.2f%%", latestMarketQuote.change24h);
        tvGoldChange.setText(changeStr);
        if (latestMarketQuote.change24h > 0) {
            tvGoldChange.setTextColor(Color.parseColor("#047857"));
        } else if (latestMarketQuote.change24h < 0) {
            tvGoldChange.setTextColor(Color.RED);
        } else {
            tvGoldChange.setTextColor(Color.GRAY);
        }
    }

    // ──── AI Research ────

    private void loadAiAdvice() {
        if (destroyed) return;
        if (!DeepSeekClient.isConfigured()) {
            tvAiSignal.setText("--");
            tvAiConfidence.setText("DeepSeek");
            tvAiSummary.setText("点击配置 DeepSeek API Key，启用黄金投研建议");
            tvAiSignal.setTextColor(Color.BLACK);
            return;
        }
        GoldAdvisoryManager.fetch(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory advisory) {
                if (destroyed) return;
                latestAdvice = advisory;
                latestMarketQuote = advisory;
                updateAdviceUI();
                updateGoldPriceUI();
            }
            @Override
            public void onError(String error) {
                if (destroyed) return;
                tvAiSignal.setText("--");
                tvAiConfidence.setText("DeepSeek");
                tvAiSummary.setText("AI 投研暂不可用，点击可重新配置或进入助手");
                tvAiSignal.setTextColor(Color.BLACK);
            }
        });
    }

    private void updateAdviceUI() {
        if (latestAdvice == null) {
            tvAiSignal.setText("--");
            tvAiConfidence.setText("DeepSeek");
            tvAiSummary.setText("点击打开投研助手");
            tvAiSignal.setTextColor(Color.BLACK);
            return;
        }

        tvAiSignal.setText(latestAdvice.signal);
        tvAiConfidence.setText(String.format(Locale.getDefault(),
            "置信度 %d%%", latestAdvice.confidence));
        tvAiSummary.setText(latestAdvice.summary);

        int signalColor;
        switch (latestAdvice.signal) {
            case "BUY": signalColor = Color.parseColor("#047857"); break;
            case "SELL": signalColor = Color.RED; break;
            default: signalColor = Color.BLACK; break;
        }
        tvAiSignal.setTextColor(signalColor);
    }

    private String buildGoldResearchPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请基于以下 App 页面已获取数据，给出黄金票据交易建议。");
        prompt.append("必须以这里的金价和链上池子为准，不要自行编造或改写实时价格。");
        prompt.append("如果行情源显示休市或延迟，请把它作为风险提示说明。\n\n");
        prompt.append("【黄金行情】\n");
        prompt.append(formatQuoteForPrompt()).append("\n\n");
        prompt.append("【链上预测池】\n");
        prompt.append(formatMarketForPrompt()).append("\n\n");
        prompt.append("请用中文输出：结论、依据、风险提示。150字以内。");
        return prompt.toString();
    }

    private String formatQuoteForPrompt() {
        if (latestMarketQuote == null || latestMarketQuote.priceUsd <= 0) {
            return "当前 App 未成功获取实时金价。";
        }
        return String.format(Locale.getDefault(),
            "XAU/USD = $%,.2f/oz，涨跌幅 %+.2f%%，%s。",
            latestMarketQuote.priceUsd,
            latestMarketQuote.change24h,
            formatQuoteMeta(latestMarketQuote));
    }

    private String formatQuoteMeta(GoldAdvisoryManager.Advisory quote) {
        String source = quote.quoteSource == null || quote.quoteSource.isEmpty()
            ? "未知来源" : quote.quoteSource;
        String updatedAt = quote.quoteUpdatedAt == null || quote.quoteUpdatedAt.isEmpty()
            ? "更新时间未知" : quote.quoteUpdatedAt;
        String suffix = quote.quoteDelayed ? "，休市/延迟行情" : "";
        return "来源 " + source + " · " + updatedAt + suffix;
    }

    private String formatMarketForPrompt() {
        if (currentGame == null) {
            return "链上预测池暂未加载完成。";
        }
        StringBuilder sb = new StringBuilder();
        if (currentGame.desc != null && !currentGame.desc.isEmpty()) {
            sb.append("市场：").append(currentGame.desc).append("\n");
        }
        if (currentGame.condition != null && !currentGame.condition.isEmpty()) {
            sb.append("条件：").append(currentGame.condition).append("\n");
        }
        if (currentGame.totalPool != null) {
            sb.append("总池子：").append(formatBkc(currentGame.totalPool)).append(" BKC\n");
        }
        if (currentGame.virtualReserves != null && currentGame.virtualReserves.size() >= 2) {
            BigInteger totalReserve = currentGame.virtualReserves.get(0)
                    .add(currentGame.virtualReserves.get(1));
            if (totalReserve.compareTo(BigInteger.ZERO) > 0) {
                double upPct = currentGame.virtualReserves.get(0).doubleValue()
                        / totalReserve.doubleValue() * 100;
                double downPct = 100 - upPct;
                sb.append(formatOptionName(0, "看涨")).append("：")
                        .append(String.format(Locale.getDefault(), "%.1f%%", upPct)).append("\n");
                sb.append(formatOptionName(1, "看跌")).append("：")
                        .append(String.format(Locale.getDefault(), "%.1f%%", downPct)).append("\n");
            }
        }
        if (currentGame.myShares != null && currentGame.myShares.size() >= 2) {
            sb.append("我的持仓：")
                    .append(formatOptionName(0, "看涨")).append(" ")
                    .append(formatShareAmount(currentGame.myShares.get(0))).append(" 份，")
                    .append(formatOptionName(1, "看跌")).append(" ")
                    .append(formatShareAmount(currentGame.myShares.get(1))).append(" 份\n");
        }
        sb.append(currentGame.isResolved || currentGame.isRefunded ? "状态：已结束" : "状态：进行中");
        return sb.toString();
    }

    private String formatOptionName(int index, String fallback) {
        return getOptionName(currentGame, index, fallback);
    }

    private String getOptionName(GoldMarketRepository.GameModel game, int index, String fallback) {
        if (game != null && game.optionNames != null
                && game.optionNames.size() > index
                && game.optionNames.get(index) != null
                && !game.optionNames.get(index).isEmpty()) {
            return game.optionNames.get(index);
        }
        return fallback;
    }

    private int getSelectedGameId() {
        if (currentGame != null && currentGame.id > 0) return currentGame.id;
        if (selectedGameId > 0) return selectedGameId;
        return GoldMarketRepository.GOLD_GAME_ID;
    }

    private boolean isSelectedGame(GoldMarketRepository.GameModel game) {
        return game != null
            && game.id == selectedGameId
            && sameContract(game.contractAddress, selectedContractAddress);
    }

    private GoldMarketRepository repositoryForSelectedMarket() {
        String address = currentGame != null && currentGame.contractAddress != null
            ? currentGame.contractAddress
            : selectedContractAddress;
        return repositoryForContract(address);
    }

    private GoldMarketRepository repositoryForContract(String contractAddress) {
        return new GoldMarketRepository(this, currentPrivateKey, contractAddress);
    }

    private String formatBkc(BigInteger value) {
        if (value == null) return "0.00";
        double bkc = value.doubleValue() / 1e18;
        return String.format(Locale.getDefault(), "%,.2f", bkc);
    }

    static String formatShareAmount(BigInteger value) {
        if (value == null || value.compareTo(BigInteger.ZERO) <= 0) return "0";
        BigDecimal shares = new BigDecimal(value)
            .divide(DISPLAY_TOKEN_UNIT, 18, RoundingMode.DOWN);
        if (shares.compareTo(BigDecimal.ZERO) > 0
            && shares.compareTo(MIN_DISPLAY_SHARE) < 0) {
            return "<0.000001";
        }
        return shares.setScale(6, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }

    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("配置 DeepSeek API Key");

        final EditText input = new EditText(this);
        input.setHint("sk-...");
        String current = DeepSeekClient.getApiKey();
        if (current != null) input.setText(current);
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if (key.startsWith("sk-") && key.length() > 10) {
                DeepSeekClient.setApiKey(key);
                Toast.makeText(this, "API Key 已保存", Toast.LENGTH_SHORT).show();
                loadAiAdvice();
            } else {
                Toast.makeText(this, "格式无效，DeepSeek Key 以 sk- 开头", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showContractAddressDialog() {
        if (!developerMarketToolsEnabled) {
            Toast.makeText(this, "\u5408\u7ea6\u914d\u7f6e\u4ec5\u9650\u5f00\u53d1\u6d4b\u8bd5\u6a21\u5f0f", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("配置官方合约列表");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView tvRpc = new TextView(this);
        tvRpc.setText("RPC 地址（留空使用 BrokerChain 测试网）");
        tvRpc.setTextSize(13f);
        tvRpc.setTextColor(0xFF666666);
        layout.addView(tvRpc);

        final EditText etRpc = new EditText(this);
        etRpc.setHint("默认: 留空=BKC测试网  模拟器: http://10.0.2.2:48347");
        String rpc = GoldMarketRepository.getRpcUrl(this);
        if (rpc != null && !rpc.isEmpty()) etRpc.setText(rpc);
        etRpc.setPadding(0, pad/2, 0, pad);
        layout.addView(etRpc);

        TextView tvAddr = new TextView(this);
        tvAddr.setText("合约地址列表（每行一个，仅开发测试模式）");
        tvAddr.setTextSize(13f);
        tvAddr.setTextColor(0xFF666666);
        layout.addView(tvAddr);

        final EditText etAddr = new EditText(this);
        etAddr.setHint("0x...\n0x...");
        etAddr.setMinLines(3);
        etAddr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        StringBuilder addresses = new StringBuilder();
        for (String address : GoldMarketRepository.getContractAddresses(this)) {
            if (addresses.length() > 0) addresses.append('\n');
            addresses.append(address);
        }
        etAddr.setText(addresses.toString());
        etAddr.setPadding(0, pad/2, 0, 0);
        layout.addView(etAddr);

        builder.setView(layout);
        builder.setPositiveButton("保存并重试", (dialog, which) -> {
            List<String> contractAddresses = GoldMarketRepository.parseContractAddresses(
                etAddr.getText().toString());
            if (contractAddresses.isEmpty()) {
                Toast.makeText(this, "请至少输入一个有效合约地址", Toast.LENGTH_LONG).show();
                return;
            }
            GoldMarketRepository.setContractAddresses(this, contractAddresses);
            GoldMarketRepository.setRpcUrl(this, etRpc.getText().toString().trim());
            repository = new GoldMarketRepository(this, currentPrivateKey);
            loadMarketData();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ──── Trade Dialogs ────

    private void showBuyDialog(int optionId, String optionName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("买入" + optionName);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("输入 BKC 数量");
        builder.setView(input);

        builder.setPositiveButton("确认买入", (dialog, which) -> {
            String amountStr = input.getText().toString();
            if (amountStr.isEmpty()) {
                Toast.makeText(this, "请输入数量", Toast.LENGTH_SHORT).show();
                return;
            }
            BigInteger amountWei = GoldMarketRepository.parseTokenAmountToWei(amountStr);
            if (amountWei == null) {
                Toast.makeText(this, "请输入有效数量", Toast.LENGTH_SHORT).show();
                return;
            }
            repositoryForSelectedMarket().buyShares(getSelectedGameId(), optionId, amountWei,
                new GoldMarketRepository.TxCallback() {
                    @Override public void onTxSent(String txHash) {
                        Toast.makeText(GoldNoteMarketActivity.this, "交易已提交...",
                            Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onConfirmed(String message) {
                        if (destroyed) return;
                        Toast.makeText(GoldNoteMarketActivity.this, message,
                            Toast.LENGTH_SHORT).show();
                        loadMarketData();
                    }
                    @Override public void onError(String error) {
                        if (destroyed) return;
                        Toast.makeText(GoldNoteMarketActivity.this, error,
                            Toast.LENGTH_LONG).show();
                    }
                });
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showSellDialog() {
        if (currentGame == null || currentGame.myShares == null
            || currentGame.myShares.size() < 2) {
            Toast.makeText(this, "市场数据未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("卖出平仓");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("卖出份额数量");
        builder.setView(input);

        builder.setPositiveButton("确认卖出", (dialog, which) -> {
            String amountStr = input.getText().toString();
            if (amountStr.isEmpty()) {
                Toast.makeText(this, "请输入份额数量", Toast.LENGTH_SHORT).show();
                return;
            }
            BigInteger shareAmount = GoldMarketRepository.parseTokenAmountToWei(amountStr);
            if (shareAmount == null) {
                Toast.makeText(this, "请输入有效份额数量", Toast.LENGTH_SHORT).show();
                return;
            }

            int optionId;
            if (currentGame.myShares.get(0).compareTo(BigInteger.ZERO) > 0) {
                optionId = 0;
            } else if (currentGame.myShares.get(1).compareTo(BigInteger.ZERO) > 0) {
                optionId = 1;
            } else {
                Toast.makeText(this, "没有可卖出的持仓", Toast.LENGTH_SHORT).show();
                return;
            }

            repositoryForSelectedMarket().sellShares(getSelectedGameId(), optionId, shareAmount,
                new GoldMarketRepository.TxCallback() {
                    @Override public void onTxSent(String txHash) {
                        Toast.makeText(GoldNoteMarketActivity.this, "交易已提交...",
                            Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onConfirmed(String message) {
                        if (destroyed) return;
                        Toast.makeText(GoldNoteMarketActivity.this, message,
                            Toast.LENGTH_SHORT).show();
                        loadMarketData();
                    }
                    @Override public void onError(String error) {
                        if (destroyed) return;
                        Toast.makeText(GoldNoteMarketActivity.this, error,
                            Toast.LENGTH_LONG).show();
                    }
                });
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
