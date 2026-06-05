package com.example.brokerfi.xc.agent.gold.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.logic.GoldAdvisoryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GoldMarketListFragment extends Fragment {
    private GoldMarketRepository repository;
    private final List<GoldMarketRepository.GameModel> availableGames = new ArrayList<>();
    private String currentPrivateKey;

    private TextView tvAiSignal, tvAiConfidence, tvAiSummary;
    private LinearLayout marketListContainer;
    private SwipeRefreshLayout swipeRefresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gold_market_list, container, false);
        initViews(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        currentPrivateKey = StorageUtil.getCurrentPrivatekey(requireContext());
        repository = new GoldMarketRepository(requireContext(), currentPrivateKey);
        loadData();
    }

    private void initViews(View v) {
        tvAiSignal = v.findViewById(R.id.tv_ai_signal);
        tvAiConfidence = v.findViewById(R.id.tv_ai_confidence);
        tvAiSummary = v.findViewById(R.id.tv_ai_summary);
        marketListContainer = v.findViewById(R.id.market_list_container);
        swipeRefresh = v.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this::loadData);

        v.findViewById(R.id.card_ai_advice).setOnClickListener(view -> {
            if (!DeepSeekClient.isConfigured()) {
                showApiKeyDialog();
            } else {
                Intent intent = new Intent(requireContext(), com.example.brokerfi.xc.AIAssistantActivity.class);
                startActivity(intent);
            }
        });

        v.findViewById(R.id.iv_ai_config).setOnClickListener(view -> showApiKeyDialog());
    }

    private void loadData() {
        loadMarketData();
        updateAiAdvice();
    }

    private void loadMarketData() {
        availableGames.clear();
        marketListContainer.removeAllViews();
        repository.getGameCount(new GoldMarketRepository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                if (count == null || count <= 0) {
                    swipeRefresh.setRefreshing(false);
                    return;
                }
                loadAllGames(count);
            }

            @Override
            public void onError(String error) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(requireContext(), "获取列表失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadAllGames(int count) {
        for (int i = 1; i <= count; i++) {
            repository.getGameInfo(i, new GoldMarketRepository.DataCallback<GoldMarketRepository.GameModel>() {
                @Override
                public void onSuccess(GoldMarketRepository.GameModel model) {
                    availableGames.add(model);
                    if (availableGames.size() >= count) {
                        renderMarketList();
                        swipeRefresh.setRefreshing(false);
                    }
                }

                @Override
                public void onError(String error) {
                    if (availableGames.size() >= count - 1) {
                        renderMarketList();
                        swipeRefresh.setRefreshing(false);
                    }
                }
            });
        }
    }

    private void renderMarketList() {
        marketListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (GoldMarketRepository.GameModel game : availableGames) {
            View card = inflater.inflate(R.layout.item_gold_market_card, marketListContainer, false);
            
            // Use the descriptive name stored in 'desc' field
            TextView tvTitle = card.findViewById(R.id.tv_market_title);
            tvTitle.setText(game.desc != null && !game.desc.isEmpty() ? game.desc : "博弈池 #" + game.id);
            
            ((TextView) card.findViewById(R.id.tv_market_condition)).setText("判定条件: " + game.condition);
            ((TextView) card.findViewById(R.id.tv_total_pool)).setText(GoldNoteMarketActivity.formatBkc(game.totalPool) + " BKC");

            long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(game.deadlineSec, System.currentTimeMillis());
            String status = remaining > 0 ? "进行中" : "已到期";
            ((TextView) card.findViewById(R.id.tv_market_status)).setText(status);
            ((TextView) card.findViewById(R.id.tv_market_status)).setTextColor(remaining > 0 ? Color.parseColor("#047857") : Color.RED);

            if (remaining > 0) {
                long h = remaining / 3600;
                long m = (remaining % 3600) / 60;
                long s = remaining % 60;
                ((TextView) card.findViewById(R.id.tv_deadline)).setText(String.format(Locale.getDefault(), "距结束 %02d:%02d:%02d", h, m, s));
            } else {
                ((TextView) card.findViewById(R.id.tv_deadline)).setText("已截止");
            }

            card.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), GoldMarketDetailActivity.class);
                intent.putExtra("GAME_ID", game.id);
                startActivity(intent);
            });
            marketListContainer.addView(card);
        }
    }

    private void updateAiAdvice() {
        if (!DeepSeekClient.isConfigured()) {
            tvAiSignal.setText("未配置");
            tvAiSignal.setTextColor(Color.GRAY);
            tvAiConfidence.setText("点击配置 API Key");
            tvAiSummary.setText("配置后开启 AI 投研辅助分析功能");
            return;
        }

        GoldAdvisoryManager.fetch(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory advisory) {
                if (getActivity() == null) return;
                tvAiSignal.setText(advisory.signal);
                tvAiConfidence.setText("DeepSeek 置信度 " + advisory.confidence + "%");
                tvAiSummary.setText(advisory.summary);
                int color = advisory.signal.equals("BUY") ? Color.parseColor("#047857") : (advisory.signal.equals("SELL") ? Color.RED : Color.BLACK);
                tvAiSignal.setTextColor(color);
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                tvAiSummary.setText("行情获取失败: " + error);
            }
        });
    }

    private void showApiKeyDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(requireContext());
        builder.setTitle("配置 DeepSeek API Key");
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("输入你的 API Key");
        input.setText(DeepSeekClient.getApiKey());
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if (!key.isEmpty()) {
                DeepSeekClient.setApiKey(key);
                updateAiAdvice();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}
