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

import java.util.Collections;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoldMarketListFragment extends Fragment {
    private GoldMarketRepository repository;
    private final List<GoldMarketRepository.GameModel> availableGames = new ArrayList<>();
    private String currentPrivateKey;

    private TextView tvAiSignal, tvAiConfidence, tvAiSummary;
    private LinearLayout marketListContainer;
    private SwipeRefreshLayout swipeRefresh;

    private boolean isLoading = false;

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
        if (isLoading) return;
        isLoading = true;

        availableGames.clear();
        marketListContainer.removeAllViews();
        repository.getAllGamesInfo(new GoldMarketRepository.DataCallback<List<GoldMarketRepository.GameModel>>() {
            @Override
            public void onSuccess(List<GoldMarketRepository.GameModel> models) {
                isLoading = false;
                availableGames.clear();
                if (models != null) {
                    availableGames.addAll(models);
                }
                finishLoading();
            }

            @Override
            public void onError(String error) {
                swipeRefresh.setRefreshing(false);
                isLoading = false;
                Toast.makeText(requireContext(), "获取列表失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void finishLoading() {
        // Sort by ID descending to show newest first
        Collections.sort(availableGames, (g1, g2) -> Integer.compare(g2.id, g1.id));
        renderMarketList();
        swipeRefresh.setRefreshing(false);
    }

    private void renderMarketList() {
        marketListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (GoldMarketRepository.GameModel game : availableGames) {
            View card = inflater.inflate(R.layout.item_gold_market_card, marketListContainer, false);
            
            // 1. 博弈池名称 (由模版自动生成，已存储在 desc 中)
            TextView tvTitle = card.findViewById(R.id.tv_market_title);
            String rawTitle = game.desc != null && !game.desc.isEmpty() ? game.desc : "博弈池 #" + game.id;
            tvTitle.setText(styleMarketTitle(rawTitle));
            
            // 2. 博弈池总投入的 BKC
            ((TextView) card.findViewById(R.id.tv_total_pool)).setText(GoldNoteMarketActivity.formatBkc(game.totalPool) + " BKC");

            // 3. 博弈池状态 (已到期、进行中)
            long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(game.deadlineSec, System.currentTimeMillis());
            String status = remaining > 0 ? "进行中" : "已到期";
            TextView tvStatus = card.findViewById(R.id.tv_market_status);
            tvStatus.setText(status);
            tvStatus.setTextColor(remaining > 0 ? 0xFF047857 : Color.RED);

            // 4. 博弈池截止时间
            ((TextView) card.findViewById(R.id.tv_deadline)).setText(GoldNoteMarketActivity.formatRemainingTime(remaining));

            // 5. 各个选项的占比 (Polymarket 风格)
            if (game.virtualReserves != null && game.virtualReserves.size() >= 2) {
                BigInteger res0 = game.virtualReserves.get(0);
                BigInteger res1 = game.virtualReserves.get(1);
                BigInteger total = res0.add(res1);
                if (total.compareTo(BigInteger.ZERO) > 0) {
                    // 合约中 reserveNO 是 res0, reserveYES 是 res1
                    // 胜率计算: YES 概率 = res0 / total (因为 res0 越大，YES 价格越高)
                    float yesRatio = (float) (res0.doubleValue() / total.doubleValue() * 100);
                    float noRatio = 100 - yesRatio;

                    View barYes = card.findViewById(R.id.bar_yes);
                    View barNo = card.findViewById(R.id.bar_no);
                    LinearLayout.LayoutParams lpYes = (LinearLayout.LayoutParams) barYes.getLayoutParams();
                    lpYes.weight = yesRatio;
                    barYes.setLayoutParams(lpYes);

                    LinearLayout.LayoutParams lpNo = (LinearLayout.LayoutParams) barNo.getLayoutParams();
                    lpNo.weight = noRatio;
                    barNo.setLayoutParams(lpNo);

                    ((TextView) card.findViewById(R.id.tv_yes_pct)).setText(String.format(Locale.getDefault(), "YES %.1f%%", yesRatio));
                    ((TextView) card.findViewById(R.id.tv_no_pct)).setText(String.format(Locale.getDefault(), "%.1f%% NO", noRatio));
                }
            }

            card.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), GoldMarketDetailActivity.class);
                intent.putExtra("GAME_ID", game.id);
                startActivity(intent);
            });
            marketListContainer.addView(card);
        }
    }

    private SpannableStringBuilder styleMarketTitle(String title) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(title);
        
        // 1. Style Dates: Smaller, Grey
        Pattern datePattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
        Matcher matcher = datePattern.matcher(title);
        while (matcher.find()) {
            ssb.setSpan(new ForegroundColorSpan(0xFF888888), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new AbsoluteSizeSpan(12, true), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        // 2. Style "黄金价格" or specific assets: Medium size, Bold Black
        String[] subjects = {"黄金价格", "黄金波幅", "成交量", "指标", "金价", "黄金收益率"};
        for (String sub : subjects) {
            int start = title.indexOf(sub);
            if (start >= 0) {
                int end = start + sub.length();
                ssb.setSpan(new ForegroundColorSpan(Color.BLACK), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new AbsoluteSizeSpan(15, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        // 3. Highlight Key Directions: Large, Bold, Green/Red
        String[] ups = {"上涨", "剧烈", "高于", "跑赢", "Touched", "触及", "达标", "YES", "Price Up"};
        for (String kw : ups) {
            int start = title.indexOf(kw);
            if (start >= 0) {
                int end = start + kw.length();
                ssb.setSpan(new ForegroundColorSpan(0xFF047857), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new AbsoluteSizeSpan(18, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        String[] downs = {"下跌", "Price Down", "平稳", "低于", "跑输", "NO", "未达标"};
        for (String kw : downs) {
            int start = title.indexOf(kw);
            if (start >= 0) {
                int end = start + kw.length();
                ssb.setSpan(new ForegroundColorSpan(Color.RED), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new AbsoluteSizeSpan(18, true), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        
        return ssb;
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
