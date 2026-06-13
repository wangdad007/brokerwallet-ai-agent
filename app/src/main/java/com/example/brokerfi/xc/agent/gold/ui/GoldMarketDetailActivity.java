package com.example.brokerfi.xc.agent.gold.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.AIAssistantActivity;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.AgentManager;
import com.example.brokerfi.xc.agent.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.logic.GoldAdvisoryManager;
import com.example.brokerfi.xc.agent.gold.logic.GoldGameJudge;
import com.example.brokerfi.xc.agent.gold.logic.GoldMarketResearchPromptBuilder;

import java.math.BigInteger;
import java.util.Locale;

public class GoldMarketDetailActivity extends AppCompatActivity {
    private static final String MARKET_AI_LOADING_MESSAGE = "专属分析仍在生成，请稍后";
    private static final String MARKET_AI_CONFIG_GUIDANCE = "请先在博弈池列表顶部的总 AI 助手中配置 DeepSeek API Key。";
    private static final String MARKET_AI_FAILURE_MESSAGE = "AI 分析暂时不可用，请稍后重新进入页面。";
    private static final String MARKET_AI_LOAD_FAILURE_MESSAGE = "博弈池加载失败，暂时无法生成专属分析。";

    private GoldMarketRepository repository;
    private GoldMarketRepository.GameModel currentGame;
    private int gameId;
    private String currentPrivateKey;

    private TextView tvMarketDesc, tvMarketCondition;
    private TextView tvUpPct, tvDownPct, tvPool, tvCountdown, tvHoldings;
    private TextView tvMarketAiStatus, tvMarketAiSummary;
    private View barUp, barDown, btnClaimReward, btnAdminResolve, cardMarketAi;
    private SwipeRefreshLayout swipeRefresh;

    private boolean marketAiRequested = false;
    private String marketAiContext = "";
    private String marketAiSummary = "";
    private String marketAiUnavailableMessage = "";
    private int marketDataLoadSeq = 0;
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

        DeepSeekClient.init(this);
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
        tvMarketAiStatus = findViewById(R.id.tv_market_ai_status);
        tvMarketAiSummary = findViewById(R.id.tv_market_ai_summary);
        barUp = findViewById(R.id.bar_up);
        barDown = findViewById(R.id.bar_down);
        cardMarketAi = findViewById(R.id.card_market_ai);
        btnClaimReward = findViewById(R.id.btn_claim_reward);
        btnAdminResolve = findViewById(R.id.btn_admin_resolve);

        swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this::loadMarketData);

        findViewById(R.id.btn_buy_up).setOnClickListener(v -> showBuyDialog(0, "YES"));
        findViewById(R.id.btn_buy_down).setOnClickListener(v -> showBuyDialog(1, "NO"));
        cardMarketAi.setOnClickListener(v -> {
            if (!marketAiContext.isEmpty() && !marketAiSummary.isEmpty()) {
                Intent intent = new Intent(this, AIAssistantActivity.class);
                intent.putExtra(AIAssistantActivity.EXTRA_MARKET_CONTEXT, marketAiContext);
                intent.putExtra(AIAssistantActivity.EXTRA_INITIAL_AI_SUMMARY, marketAiSummary);
                startActivity(intent);
                return;
            }
            String message = marketAiUnavailableMessage.isEmpty() ? MARKET_AI_LOADING_MESSAGE : marketAiUnavailableMessage;
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
        btnClaimReward.setOnClickListener(v -> claimReward());
        btnAdminResolve.setOnClickListener(v -> performAdminResolve());
    }

    private void loadMarketData() {
        final int requestId = ++marketDataLoadSeq;
        repository.getGameInfo(gameId, new GoldMarketRepository.DataCallback<GoldMarketRepository.GameModel>() {
            @Override
            public void onSuccess(GoldMarketRepository.GameModel model) {
                if (requestId != marketDataLoadSeq || destroyed) return;
                currentGame = model;
                updateUI();
                requestMarketAiSummaryOnce();
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onError(String error) {
                if (requestId != marketDataLoadSeq || destroyed) return;
                Toast.makeText(GoldMarketDetailActivity.this, "加载失败: " + error, Toast.LENGTH_SHORT).show();
                if (marketAiSummary.isEmpty()) {
                    showMarketAiUnavailable("加载失败", MARKET_AI_LOAD_FAILURE_MESSAGE);
                }
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    private void updateUI() {
        if (currentGame == null) return;

        // 1. 博弈池名称
        tvMarketDesc.setText(currentGame.desc != null && !currentGame.desc.isEmpty() ? currentGame.desc : "博弈池 #" + currentGame.id);
        tvMarketCondition.setText("判定逻辑: " + (currentGame.condition != null ? currentGame.condition : "暂无"));

        long rem = GoldNoteMarketActivity.remainingSecondsUntilDeadline(currentGame.deadlineSec, System.currentTimeMillis());
        
        if (rem <= 0 && !currentGame.isResolved && !currentGame.isRefunded) {
            GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
                @Override
                public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                    int winner = GoldGameJudge.evaluateGameWinner(currentGame, quote);
                    String winnerName = (currentGame.optionNames != null && winner < currentGame.optionNames.size())
                            ? currentGame.optionNames.get(winner) : (winner == 0 ? "YES" : "NO");
                    tvMarketCondition.setText("判定逻辑: " + currentGame.condition + "\n系统判定胜出: " + winnerName);
                }
                @Override public void onError(String error) {}
            });
        }

        btnClaimReward.setVisibility(currentGame.isResolved || currentGame.isRefunded ? View.VISIBLE : View.GONE);
        btnAdminResolve.setVisibility(rem <= 0 && !currentGame.isResolved && !currentGame.isRefunded ? View.VISIBLE : View.GONE);
        
        // 2. 选项占比可视化 (使用权重 LayoutParams 更准确)
        if (currentGame.virtualReserves != null && currentGame.virtualReserves.size() >= 2) {
            BigInteger res0 = currentGame.virtualReserves.get(0); // reserveNO
            BigInteger res1 = currentGame.virtualReserves.get(1); // reserveYES
            BigInteger total = res0.add(res1);
            if (total.compareTo(BigInteger.ZERO) > 0) {
                float p0 = (float) (res0.doubleValue() / total.doubleValue() * 100);
                float p1 = 100 - p0;
                tvUpPct.setText(String.format(Locale.getDefault(), "%.1f%%", p0));
                tvDownPct.setText(String.format(Locale.getDefault(), "%.1f%%", p1));
                
                // 必须明确设置 width 为 0，weight 才能在 LinearLayout 中正确按比例分配空间
                LinearLayout.LayoutParams lp0 = (LinearLayout.LayoutParams) barUp.getLayoutParams();
                lp0.width = 0;
                lp0.weight = p0;
                barUp.setLayoutParams(lp0);
                
                LinearLayout.LayoutParams lp1 = (LinearLayout.LayoutParams) barDown.getLayoutParams();
                lp1.width = 0;
                lp1.weight = p1;
                barDown.setLayoutParams(lp1);
            }
        }

        // 3. 总投入的 BKC
        tvPool.setText("总池子: " + GoldNoteMarketActivity.formatBkc(currentGame.totalPool) + " BKC");

        // 4. 持仓汇总
        StringBuilder holdings = new StringBuilder();
        if (currentGame.myShares != null) {
            for (int i = 0; i < currentGame.myShares.size(); i++) {
                BigInteger shares = currentGame.myShares.get(i);
                if (shares == null || shares.signum() <= 0) continue;
                if (holdings.length() > 0) holdings.append('\n');
                String optionName = (currentGame.optionNames != null && i < currentGame.optionNames.size())
                        ? currentGame.optionNames.get(i) : (i == 0 ? "YES" : "NO");
                holdings.append(optionName).append(": ").append(GoldNoteMarketActivity.formatShareAmount(shares)).append(" 份额");
            }
        }
        tvHoldings.setText(holdings.length() == 0 ? "暂无持仓" : holdings.toString());
        
        // 5. 截止时间
        updateCountdown();
    }

    private void requestMarketAiSummaryOnce() {
        if (marketAiRequested || currentGame == null) return;
        marketAiRequested = true;

        if (!DeepSeekClient.isConfigured()) {
            showMarketAiUnavailable("未配置", MARKET_AI_CONFIG_GUIDANCE);
            return;
        }

        GoldMarketRepository.GameModel gameForResearch = currentGame;
        tvMarketAiStatus.setText("分析中");
        Runnable askResearch = () -> AgentManager.getInstance().askGoldResearch(
                GoldMarketResearchPromptBuilder.buildSummaryPrompt(marketAiContext),
                new AgentManager.AnalysisCallback() {
                    @Override public void onBrokerReport(AgentManager.BrokerReport report) { showMarketAiSummary(report == null ? "" : report.rawAnalysis); }
                    @Override public void onGeneralAdvice(String question, String answer) { showMarketAiSummary(answer); }
                    @Override public void onError(String error) { showMarketAiUnavailable("暂不可用", MARKET_AI_FAILURE_MESSAGE); }
                });

        GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                if (destroyed) return;
                marketAiContext = GoldMarketResearchPromptBuilder.buildContext(gameForResearch, System.currentTimeMillis(), quote);
                askResearch.run();
            }
            @Override public void onError(String error) {
                if (destroyed) return;
                marketAiContext = GoldMarketResearchPromptBuilder.buildContext(gameForResearch, System.currentTimeMillis(), null);
                askResearch.run();
            }
        });
    }

    private void showMarketAiSummary(String answer) {
        runOnUiThread(() -> {
            if (destroyed) return;
            if (answer == null || answer.trim().isEmpty()) {
                showMarketAiUnavailable("暂不可用", MARKET_AI_FAILURE_MESSAGE);
                return;
            }
            marketAiUnavailableMessage = "";
            marketAiSummary = answer;
            tvMarketAiStatus.setText("DeepSeek ›");
            tvMarketAiSummary.setText(answer);
        });
    }

    private void showMarketAiUnavailable(String status, String message) {
        runOnUiThread(() -> {
            if (destroyed) return;
            marketAiSummary = "";
            marketAiUnavailableMessage = message;
            tvMarketAiStatus.setText(status);
            tvMarketAiSummary.setText(message);
        });
    }

    private void updateCountdown() {
        if (currentGame == null) return;
        long rem = GoldNoteMarketActivity.remainingSecondsUntilDeadline(currentGame.deadlineSec, System.currentTimeMillis());
        tvCountdown.setText(GoldNoteMarketActivity.formatRemainingTime(rem));
    }

    private void showBuyDialog(int optionId, String optionName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_gold_buy_confirm, null);
        builder.setView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_buy_title);
        tvTitle.setText("确认下注 " + optionName);
        EditText etAmount = dialogView.findViewById(R.id.et_buy_amount);
        Button btnConfirm = dialogView.findViewById(R.id.btn_buy_confirm);
        Button btnCancel = dialogView.findViewById(R.id.btn_buy_cancel);

        // Make dialog confirm button color consistent with the bet side
        if (optionId == 0) {
            btnConfirm.setBackgroundResource(R.drawable.bg_bet_yes);
        } else {
            btnConfirm.setBackgroundResource(R.drawable.bg_bet_no);
        }

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnConfirm.setOnClickListener(v -> {
            String val = etAmount.getText().toString().trim();
            if (val.isEmpty()) return;
            BigInteger wei = GoldMarketRepository.parseTokenAmountToWei(val);
            if (wei == null) return;

            dialog.dismiss();
            repository.buyShares(gameId, optionId, wei, new GoldMarketRepository.TxCallback() {
                @Override public void onTxSent(String txHash) {
                    Toast.makeText(GoldMarketDetailActivity.this, "交易已发送", Toast.LENGTH_SHORT).show();
                }
                @Override public void onConfirmed(String msg) {
                    Toast.makeText(GoldMarketDetailActivity.this, "下注成功", Toast.LENGTH_SHORT).show();
                    loadMarketData();
                }
                @Override public void onError(String err) {
                    Toast.makeText(GoldMarketDetailActivity.this, "失败: " + err, Toast.LENGTH_LONG).show();
                }
            });
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void performAdminResolve() {
        if (currentGame == null) return;
        Toast.makeText(this, "获取行情中...", Toast.LENGTH_SHORT).show();
        GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                int winner = GoldGameJudge.evaluateGameWinner(currentGame, quote);
                String winnerName = winner == 0 ? "YES" : "NO";
                new AlertDialog.Builder(GoldMarketDetailActivity.this)
                    .setTitle("管理员开奖确认")
                    .setMessage("判定结果: " + winnerName + "\n确定要执行链上结算吗？")
                    .setPositiveButton("立即结算", (d, w) -> {
                        repository.resolveGame(gameId, winner, new GoldMarketRepository.TxCallback() {
                            @Override public void onTxSent(String txHash) {}
                            @Override public void onConfirmed(String msg) {
                                Toast.makeText(GoldMarketDetailActivity.this, "结算完成！", Toast.LENGTH_SHORT).show();
                                loadMarketData();
                            }
                            @Override public void onError(String err) {
                                Toast.makeText(GoldMarketDetailActivity.this, "结算失败: " + err, Toast.LENGTH_LONG).show();
                            }
                        });
                    })
                    .setNegativeButton("取消", null).show();
            }
            @Override public void onError(String error) {
                Toast.makeText(GoldMarketDetailActivity.this, "获取失败", Toast.LENGTH_SHORT).show();
            }
        });
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
