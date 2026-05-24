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
import androidx.cardview.widget.CardView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.BuildConfig;
import com.example.brokerfi.R;
import com.example.brokerfi.xc.SecurityUtil;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.DeepSeekClient;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GoldNoteMarketActivity extends AppCompatActivity {

    private GoldMarketRepository repository;
    private GoldMarketRepository.GameModel currentGame;
    private GoldAdvisoryManager.Advisory latestAdvice;

    private TextView tvGoldPrice, tvGoldChange;
    private TextView tvAiSignal, tvAiConfidence, tvAiSummary;
    private TextView tvUpPct, tvDownPct, tvPool, tvCountdown;
    private TextView tvMarketMode, tvMarketSecurityHint;
    private TextView tvHoldings;
    private View barUp, barDown;
    private SwipeRefreshLayout swipeRefresh;
    private View btnClaimReward;
    private CardView cardAiAdvice;

    private boolean destroyed = false;
    private boolean developerMarketToolsEnabled;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable adviceRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            loadAiAdvice();
            timerHandler.postDelayed(this, 60_000);
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
            String pk = StorageUtil.getCurrentPrivatekey(this);
            if (pk == null || pk.isEmpty()) {
                Toast.makeText(this, "请先创建或导入钱包", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            // 检查私钥格式：黄金票据市场只支持新格式（64位十六进制）私钥
            if (!SecurityUtil.isNewPrivateKeyFormat(pk)) {
                Toast.makeText(this, "黄金票据市场仅支持新格式钱包\n请使用新创建的账户", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            // 预检查：测试是否能从私钥推导出有效地址
            String testAddr = BrokerChainClient.getAddress(pk);
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

            repository = new GoldMarketRepository(this, pk);
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
        timerHandler.removeCallbacks(adviceRefreshRunnable);
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
        barUp = findViewById(R.id.bar_up);
        barDown = findViewById(R.id.bar_down);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> {
            loadMarketData();
            swipeRefresh.setRefreshing(false);
        });

        cardAiAdvice = findViewById(R.id.card_ai_advice);
        cardAiAdvice.setOnClickListener(v -> {
            if (DeepSeekClient.isConfigured()) {
                Intent intent = new Intent(this, com.example.brokerfi.xc.AIAssistantActivity.class);
                intent.putExtra("INITIAL_PROMPT",
                    "请帮我深度分析当前黄金市场，给出投资策略建议");
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
            repository.claimReward(GoldMarketRepository.GOLD_GAME_ID, optionId,
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

        View createMarketButton = findViewById(R.id.btn_create_market);
        if (developerMarketToolsEnabled) {
            ((TextView) createMarketButton).setText("\u6d4b\u8bd5\u521b\u5efa\u5e02\u573a");
            createMarketButton.setVisibility(View.VISIBLE);
            createMarketButton.setOnClickListener(v -> showCreateMarketDialog());
        } else {
            createMarketButton.setVisibility(View.GONE);
        }
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
            tvMarketSecurityHint.setText("\u53ef\u914d\u7f6e\u6d4b\u8bd5\u5408\u7ea6\u548c RPC\uff0c\u6b63\u5f0f\u7248\u4e0d\u5411\u666e\u901a\u7528\u6237\u5f00\u653e");
            tvMarketSecurityHint.setTextColor(Color.parseColor("#1E40AF"));
        } else {
            banner.setBackgroundResource(R.drawable.bg_gold_market_status);
            tvMarketMode.setText("\u5b98\u65b9\u9a8c\u8bc1\u6c60");
            tvMarketMode.setTextColor(Color.parseColor("#047857"));
            tvMarketSecurityHint.setText("\u4ec5\u8fde\u63a5\u5b98\u65b9\u5408\u7ea6\uff0c\u521b\u5efa\u548c\u914d\u7f6e\u5165\u53e3\u5df2\u5728\u6b63\u5f0f\u7248\u5173\u95ed");
            tvMarketSecurityHint.setTextColor(Color.parseColor("#065F46"));
        }
    }

    private void loadEverything() {
        loadMarketData();
        loadAiAdvice();
        timerHandler.post(adviceRefreshRunnable);
        timerHandler.post(countdownRunnable);
    }

    // ──── Market Data ────

    private void loadMarketData() {
        repository.getGameInfo(GoldMarketRepository.GOLD_GAME_ID,
            new GoldMarketRepository.DataCallback<GoldMarketRepository.GameModel>() {
            @Override
            public void onSuccess(GoldMarketRepository.GameModel model) {
                if (destroyed) return;
                currentGame = model;
                updateMarketUI();
            }
            @Override
            public void onError(String error) {
                if (destroyed) return;
                Toast.makeText(GoldNoteMarketActivity.this,
                    "加载市场数据失败: " + error, Toast.LENGTH_LONG).show();
                if (developerMarketToolsEnabled) {
                    showContractAddressDialog();
                }
            }
        });
    }

    private void updateMarketUI() {
        if (currentGame == null) return;

        btnClaimReward.setVisibility(currentGame.isResolved || currentGame.isRefunded ? View.VISIBLE : View.GONE);
        boolean closed = currentGame.isResolved || currentGame.isRefunded
            || (currentGame.deadlineSec > 0 && System.currentTimeMillis() / 1000 > currentGame.deadlineSec);
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
                double shares = upShares.doubleValue() / 1e18;
                tvHoldings.setText(String.format("看涨 · %.0f 份", shares));
            } else if (downShares.compareTo(BigInteger.ZERO) > 0) {
                double shares = downShares.doubleValue() / 1e18;
                tvHoldings.setText(String.format("看跌 · %.0f 份", shares));
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
        long now = System.currentTimeMillis() / 1000;
        long remaining = currentGame.deadlineSec - now;
        if (remaining <= 0) {
            tvCountdown.setText("已到期");
            return;
        }
        long h = remaining / 3600;
        long m = (remaining % 3600) / 60;
        long s = remaining % 60;
        tvCountdown.setText(String.format("倒计时: %02d:%02d:%02d", h, m, s));
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

    // ──── AI Advice ────

    private void loadAiAdvice() {
        if (destroyed) return;
        GoldAdvisoryManager.fetch(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory advisory) {
                if (destroyed) return;
                latestAdvice = advisory;
                updateAdviceUI();
                updateGoldPriceUI();
            }
            @Override
            public void onError(String error) {
                if (destroyed) return;
                if ("NO_API_KEY".equals(error)) {
                    tvAiSummary.setText("请配置 DeepSeek API Key 以启用 AI 建议");
                } else {
                    tvAiSummary.setText("AI 建议暂不可用");
                }
            }
        });
    }

    private void updateGoldPriceUI() {
        if (latestAdvice == null || latestAdvice.priceUsd <= 0) {
            tvGoldPrice.setText("XAU $---.--/oz");
            tvGoldChange.setText("--.--%");
            return;
        }
        tvGoldPrice.setText(String.format(Locale.getDefault(),
            "XAU $%,.2f/oz", latestAdvice.priceUsd));
        String changeStr = String.format(Locale.getDefault(),
            "%+.2f%%", latestAdvice.change24h);
        tvGoldChange.setText(changeStr);
        if (latestAdvice.change24h > 0) {
            tvGoldChange.setTextColor(Color.parseColor("#00AA00"));
        } else if (latestAdvice.change24h < 0) {
            tvGoldChange.setTextColor(Color.RED);
        } else {
            tvGoldChange.setTextColor(Color.GRAY);
        }
    }

    private void updateAdviceUI() {
        if (latestAdvice == null) {
            tvAiSignal.setText("--");
            tvAiConfidence.setText("置信度 --%");
            return;
        }

        tvAiSignal.setText(latestAdvice.signal);
        tvAiConfidence.setText(String.format("置信度 %d%%", latestAdvice.confidence));
        tvAiSummary.setText(latestAdvice.summary);

        int signalColor;
        switch (latestAdvice.signal) {
            case "BUY": signalColor = Color.parseColor("#00AA00"); break;
            case "SELL": signalColor = Color.RED; break;
            default: signalColor = Color.parseColor("#F59E0B"); break;
        }
        tvAiSignal.setTextColor(signalColor);
    }

    // ──── API Key Dialog ────

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
        builder.setTitle("配置合约");

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
        tvAddr.setText("合约地址");
        tvAddr.setTextSize(13f);
        tvAddr.setTextColor(0xFF666666);
        layout.addView(tvAddr);

        final EditText etAddr = new EditText(this);
        etAddr.setHint("0x...");
        etAddr.setText(GoldMarketRepository.getContractAddress(this));
        etAddr.setPadding(0, pad/2, 0, 0);
        layout.addView(etAddr);

        builder.setView(layout);
        builder.setPositiveButton("保存并重试", (dialog, which) -> {
            String addr = etAddr.getText().toString().trim();
            if (!addr.startsWith("0x") || addr.length() != 42) {
                Toast.makeText(this, "合约地址格式无效", Toast.LENGTH_LONG).show();
                return;
            }
            GoldMarketRepository.setContractAddress(this, addr);
            GoldMarketRepository.setRpcUrl(this, etRpc.getText().toString().trim());
            repository = new GoldMarketRepository(this,
                StorageUtil.getCurrentPrivatekey(this));
            loadMarketData();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ──── Create Market Dialog ────

    private void showCreateMarketDialog() {
        if (!developerMarketToolsEnabled) {
            Toast.makeText(this, "\u521b\u5efa\u5e02\u573a\u4ec5\u9650\u5b98\u65b9\u6216\u5f00\u53d1\u6d4b\u8bd5\u6a21\u5f0f", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("创建预测市场");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView tvDesc = new TextView(this);
        tvDesc.setText("市场描述");
        tvDesc.setTextSize(14f);
        tvDesc.setTextColor(0xFF333333);
        layout.addView(tvDesc);

        final EditText etDesc = new EditText(this);
        etDesc.setHint("例如：黄金24H看涨");
        etDesc.setPadding(0, pad/2, 0, pad);
        layout.addView(etDesc);

        TextView tvOpts = new TextView(this);
        tvOpts.setText("选项（逗号分隔）");
        tvOpts.setTextSize(14f);
        tvOpts.setTextColor(0xFF333333);
        layout.addView(tvOpts);

        final EditText etOpts = new EditText(this);
        etOpts.setText("看涨,看跌");
        etOpts.setPadding(0, pad/2, 0, pad);
        layout.addView(etOpts);

        TextView tvDur = new TextView(this);
        tvDur.setText("持续时长（小时）");
        tvDur.setTextSize(14f);
        tvDur.setTextColor(0xFF333333);
        layout.addView(tvDur);

        final EditText etDur = new EditText(this);
        etDur.setText("24");
        etDur.setInputType(InputType.TYPE_CLASS_NUMBER);
        etDur.setPadding(0, pad/2, 0, 0);
        layout.addView(etDur);

        builder.setView(layout);

        builder.setPositiveButton("创建", (dialog, which) -> {
            String desc = etDesc.getText().toString().trim();
            String optsStr = etOpts.getText().toString().trim();
            String durStr = etDur.getText().toString().trim();

            if (desc.isEmpty() || optsStr.isEmpty() || durStr.isEmpty()) {
                Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] optionNames = optsStr.split(",");
            if (optionNames.length < 2) {
                Toast.makeText(this, "至少需要2个选项", Toast.LENGTH_SHORT).show();
                return;
            }

            long durationSec;
            try {
                durationSec = Long.parseLong(durStr) * 3600;
            } catch (NumberFormatException e) {
                Toast.makeText(this, "时长格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> options = new ArrayList<>();
            for (String o : optionNames) options.add(o.trim());

            repository.createGame(desc, "时间到期后按AMM定价结算", "",
                desc, options, durationSec,
                new GoldMarketRepository.TxCallback() {
                    @Override public void onTxSent(String txHash) {
                        Toast.makeText(GoldNoteMarketActivity.this, "交易已提交...",
                            Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onConfirmed(String message) {
                        if (destroyed) return;
                        Toast.makeText(GoldNoteMarketActivity.this, message,
                            Toast.LENGTH_LONG).show();
                    }
                    @Override public void onError(String error) {
                        if (destroyed) return;
                        Toast.makeText(GoldNoteMarketActivity.this, "创建失败: " + error,
                            Toast.LENGTH_LONG).show();
                    }
                });
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
            repository.buyShares(GoldMarketRepository.GOLD_GAME_ID, optionId, amountWei,
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

            repository.sellShares(GoldMarketRepository.GOLD_GAME_ID, optionId, shareAmount,
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
