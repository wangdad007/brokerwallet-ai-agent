package com.example.brokerfi.xc.agent.gold;

import android.app.AlertDialog;
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

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;

import java.math.BigInteger;
import java.util.Locale;

public class GoldNoteMarketActivity extends AppCompatActivity {

    private GoldMarketRepository repository;
    private GoldMarketRepository.GameModel currentGame;
    private GoldAdvisoryManager.Advisory latestAdvice;

    private TextView tvGoldPrice, tvGoldChange;
    private TextView tvAiSignal, tvAiConfidence, tvAiSummary;
    private TextView tvUpPct, tvDownPct, tvPool, tvCountdown;
    private TextView tvHoldings;
    private View barUp, barDown;

    private boolean destroyed = false;

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

        String pk = StorageUtil.getCurrentPrivatekey(this);
        if (pk == null || pk.isEmpty()) {
            Toast.makeText(this, "请先创建或导入钱包", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        repository = new GoldMarketRepository(pk);

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
        tvGoldChange = findViewById(R.id.tv_gold_change);
        tvAiSignal = findViewById(R.id.tv_ai_signal);
        tvAiConfidence = findViewById(R.id.tv_ai_confidence);
        tvAiSummary = findViewById(R.id.tv_ai_summary);
        tvUpPct = findViewById(R.id.tv_up_pct);
        tvDownPct = findViewById(R.id.tv_down_pct);
        tvPool = findViewById(R.id.tv_pool);
        tvCountdown = findViewById(R.id.tv_countdown);
        tvHoldings = findViewById(R.id.tv_holdings);
        barUp = findViewById(R.id.bar_up);
        barDown = findViewById(R.id.bar_down);

        findViewById(R.id.btn_buy_up).setOnClickListener(v -> showBuyDialog(0, "看涨"));
        findViewById(R.id.btn_buy_down).setOnClickListener(v -> showBuyDialog(1, "看跌"));
        findViewById(R.id.btn_sell).setOnClickListener(v -> showSellDialog());
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
                    "加载市场数据失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateMarketUI() {
        if (currentGame == null) return;

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
            double amount = Double.parseDouble(amountStr);
            BigInteger amountWei = new BigInteger(String.valueOf((long)(amount * 1e18)));
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
            double shares = Double.parseDouble(amountStr);
            BigInteger shareAmount = new BigInteger(String.valueOf((long)(shares * 1e18)));

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
