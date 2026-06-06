package com.example.brokerfi.xc.agent.gold.ui;

import android.app.AlertDialog;
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

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.logic.GoldAdvisoryManager;

import java.math.BigInteger;
import java.util.Locale;

public class GoldMarketDetailActivity extends AppCompatActivity {
    private GoldMarketRepository repository;
    private GoldMarketRepository.GameModel currentGame;
    private int gameId;
    private String currentPrivateKey;

    private TextView tvMarketDesc, tvMarketCondition;
    private TextView tvUpPct, tvDownPct, tvPool, tvCountdown, tvHoldings;
    private View barUp, barDown, btnClaimReward;
    private SwipeRefreshLayout swipeRefresh;

    private boolean destroyed = false;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
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

        gameId = getIntent().getIntExtra("GAME_ID", 1);
        currentPrivateKey = StorageUtil.getCurrentPrivatekey(this);
        repository = new GoldMarketRepository(this, currentPrivateKey);

        initViews();
        loadMarketData();
        timerHandler.post(countdownRunnable);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        timerHandler.removeCallbacks(countdownRunnable);
        super.onDestroy();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvMarketDesc = findViewById(R.id.tv_market_desc);
        tvMarketCondition = findViewById(R.id.tv_market_condition);
        tvUpPct = findViewById(R.id.tv_up_pct);
        tvDownPct = findViewById(R.id.tv_down_pct);
        tvPool = findViewById(R.id.tv_pool);
        tvCountdown = findViewById(R.id.tv_countdown);
        tvHoldings = findViewById(R.id.tv_holdings);
        barUp = findViewById(R.id.bar_up);
        barDown = findViewById(R.id.bar_down);
        btnClaimReward = findViewById(R.id.btn_claim_reward);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this::loadMarketData);

        findViewById(R.id.btn_buy_up).setOnClickListener(v -> showBuyDialog(0, "YES"));
        findViewById(R.id.btn_buy_down).setOnClickListener(v -> showBuyDialog(1, "NO"));
        btnClaimReward.setOnClickListener(v -> claimReward());
    }

    private void loadMarketData() {
        repository.getGameInfo(gameId, new GoldMarketRepository.DataCallback<GoldMarketRepository.GameModel>() {
            @Override
            public void onSuccess(GoldMarketRepository.GameModel model) {
                if (destroyed) return;
                currentGame = model;
                updateUI();
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onError(String error) {
                if (destroyed) return;
                Toast.makeText(GoldMarketDetailActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void updateUI() {
        if (currentGame == null) return;

        tvMarketDesc.setText("博弈池 #" + currentGame.id);
        tvMarketCondition.setText("结算条件: " + currentGame.condition);

        long rem = GoldNoteMarketActivity.remainingSecondsUntilDeadline(currentGame.deadlineSec, System.currentTimeMillis());
        
        // 如果已到期但未结算，显示系统判定的结果 (If-Else Logic)
        if (rem <= 0 && !currentGame.isResolved && !currentGame.isRefunded) {
            GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
                @Override
                public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                    int winner = GoldAdvisoryManager.evaluateGameWinner(currentGame, quote);
                    String winnerName = winner == 0 ? "看涨 (YES)" : "看跌 (NO)";
                    tvMarketCondition.setText("结算条件: " + currentGame.condition + "\n系统判定胜出: " + winnerName);
                }
                @Override public void onError(String error) {}
            });
        }

        btnClaimReward.setVisibility(currentGame.isResolved || currentGame.isRefunded ? View.VISIBLE : View.GONE);
        
        if (currentGame.virtualReserves != null && currentGame.virtualReserves.size() >= 2) {
            BigInteger res0 = currentGame.virtualReserves.get(0);
            BigInteger res1 = currentGame.virtualReserves.get(1);
            BigInteger total = res0.add(res1);
            if (total.compareTo(BigInteger.ZERO) > 0) {
                double p0 = res0.doubleValue() / total.doubleValue() * 100;
                double p1 = 100 - p0;
                tvUpPct.setText(String.format(Locale.getDefault(), "%.1f%%", p0));
                tvDownPct.setText(String.format(Locale.getDefault(), "%.1f%%", p1));
                
                int screenWidth = getResources().getDisplayMetrics().widthPixels - 64;
                LinearLayout.LayoutParams lp0 = (LinearLayout.LayoutParams) barUp.getLayoutParams();
                lp0.width = (int) (screenWidth * p0 / 100);
                barUp.setLayoutParams(lp0);
                
                LinearLayout.LayoutParams lp1 = (LinearLayout.LayoutParams) barDown.getLayoutParams();
                lp1.width = (int) (screenWidth * p1 / 100);
                barDown.setLayoutParams(lp1);
            }
        }

        tvPool.setText("总池子: " + GoldNoteMarketActivity.formatBkc(currentGame.totalPool) + " BKC");

        if (currentGame.myShares != null && currentGame.myShares.size() >= 2) {
            BigInteger s0 = currentGame.myShares.get(0);
            BigInteger s1 = currentGame.myShares.get(1);
            if (s0.compareTo(BigInteger.ZERO) > 0) {
                tvHoldings.setText(currentGame.optionNames.get(0) + ": " + GoldNoteMarketActivity.formatShareAmount(s0) + " 份");
            } else if (s1.compareTo(BigInteger.ZERO) > 0) {
                tvHoldings.setText(currentGame.optionNames.get(1) + ": " + GoldNoteMarketActivity.formatShareAmount(s1) + " 份");
            } else {
                tvHoldings.setText("暂无持仓");
            }
        }
        updateCountdown();
    }

    private void updateCountdown() {
        if (currentGame == null) return;
        long rem = GoldNoteMarketActivity.remainingSecondsUntilDeadline(currentGame.deadlineSec, System.currentTimeMillis());
        tvCountdown.setText(GoldNoteMarketActivity.formatRemainingTime(rem));
    }

    private void showBuyDialog(int optionId, String name) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("买入 " + name);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("输入 BKC 数量");
        builder.setView(input);

        builder.setPositiveButton("确认买入", (dialog, which) -> {
            String val = input.getText().toString();
            if (val.isEmpty()) return;
            BigInteger wei = GoldMarketRepository.parseTokenAmountToWei(val);
            if (wei == null) return;

            repository.buyShares(gameId, optionId, wei, new GoldMarketRepository.TxCallback() {
                @Override public void onTxSent(String txHash) {
                    Toast.makeText(GoldMarketDetailActivity.this, "交易已提交", Toast.LENGTH_SHORT).show();
                }
                @Override public void onConfirmed(String msg) {
                    Toast.makeText(GoldMarketDetailActivity.this, "购买成功", Toast.LENGTH_SHORT).show();
                    loadMarketData();
                }
                @Override public void onError(String err) {
                    Toast.makeText(GoldMarketDetailActivity.this, err, Toast.LENGTH_LONG).show();
                }
            });
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void claimReward() {
        if (currentGame == null) return;
        int opt = -1;
        if (currentGame.isResolved) opt = currentGame.winningOption;
        else if (currentGame.isRefunded) {
            for (int i=0; i<currentGame.myShares.size(); i++) {
                if (currentGame.myShares.get(i).compareTo(BigInteger.ZERO) > 0) { opt = i; break; }
            }
        }
        if (opt == -1) return;

        repository.claimReward(gameId, opt, new GoldMarketRepository.TxCallback() {
            @Override public void onTxSent(String txHash) {}
            @Override public void onConfirmed(String msg) {
                Toast.makeText(GoldMarketDetailActivity.this, "领取成功", Toast.LENGTH_SHORT).show();
                loadMarketData();
            }
            @Override public void onError(String err) {
                Toast.makeText(GoldMarketDetailActivity.this, err, Toast.LENGTH_LONG).show();
            }
        });
    }
}
