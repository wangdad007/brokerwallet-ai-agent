package com.example.brokerfi.xc.agent.gold.ui;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoldMyPositionsFragment extends Fragment {
    private GoldMarketRepository repository;
    private final List<GoldMarketRepository.GameModel> myPositions = new ArrayList<>();
    private String currentPrivateKey;

    private TextView tvTotalBalance, tvTotalPnl;
    private LinearLayout positionsContainer;
    private SwipeRefreshLayout swipeRefresh;
    private double lastTotalBalance = 0.0;

    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gold_my_positions, container, false);
        tvTotalBalance = view.findViewById(R.id.tv_total_balance);
        tvTotalPnl = view.findViewById(R.id.tv_total_pnl);
        positionsContainer = view.findViewById(R.id.positions_container);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(this::loadPositions);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        currentPrivateKey = StorageUtil.getCurrentPrivatekey(requireContext());
        repository = new GoldMarketRepository(requireContext(), currentPrivateKey);
        loadPositions();
    }

    private void loadPositions() {
        if (isLoading) return;
        isLoading = true;
        
        myPositions.clear();
        positionsContainer.removeAllViews();
        repository.getGameCount(new GoldMarketRepository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                if (count == null || count <= 0) {
                    swipeRefresh.setRefreshing(false);
                    isLoading = false;
                    updateSummary();
                    return;
                }
                checkAllGames(count);
            }

            @Override
            public void onError(String error) {
                swipeRefresh.setRefreshing(false);
                isLoading = false;
                Toast.makeText(requireContext(), "获取持仓失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAllGames(int count) {
        final int[] responseCount = {0};
        synchronized (myPositions) {
            myPositions.clear();
        }
        
        for (int i = 1; i <= count; i++) {
            repository.getGameInfo(i, new GoldMarketRepository.DataCallback<GoldMarketRepository.GameModel>() {
                @Override
                public void onSuccess(GoldMarketRepository.GameModel model) {
                    synchronized (myPositions) {
                        boolean exists = false;
                        for (GoldMarketRepository.GameModel g : myPositions) {
                            if (g.id == model.id) { exists = true; break; }
                        }
                        if (!exists && hasUserShares(model)) {
                            myPositions.add(model);
                        }
                        
                        responseCount[0]++;
                        if (responseCount[0] >= count) {
                            isLoading = false;
                            finishLoading();
                        }
                    }
                }

                @Override
                public void onError(String error) {
                    synchronized (myPositions) {
                        responseCount[0]++;
                        if (responseCount[0] >= count) {
                            isLoading = false;
                            finishLoading();
                        }
                    }
                }
            });
        }
    }

    private void finishLoading() {
        renderPositions();
        updateSummary();
        swipeRefresh.setRefreshing(false);
    }

    private boolean hasUserShares(GoldMarketRepository.GameModel game) {
        if (game == null || game.myShares == null) return false;
        for (BigInteger shares : game.myShares) {
            if (shares != null && shares.compareTo(BigInteger.ZERO) > 0) return true;
        }
        return false;
    }

    private void renderPositions() {
        positionsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (GoldMarketRepository.GameModel game : myPositions) {
            View card = inflater.inflate(R.layout.item_gold_position_card, positionsContainer, false);
            
            TextView tvTitle = card.findViewById(R.id.tv_position_title);
            String rawTitle = game.desc != null && !game.desc.isEmpty() ? game.desc : "博弈池 #" + game.id;
            tvTitle.setText(styleMarketTitle(rawTitle));
            
            int sideIndex = -1;
            for (int i = 0; i < game.myShares.size(); i++) {
                if (game.myShares.get(i).compareTo(BigInteger.ZERO) > 0) {
                    sideIndex = i;
                    break;
                }
            }
            
            if (sideIndex != -1) {
                String sideName = game.optionNames.get(sideIndex);
                TextView tvSide = card.findViewById(R.id.tv_position_side);
                tvSide.setText(sideName);
                tvSide.setTextColor(sideName.contains("YES") || sideName.contains("涨") ? 0xFF047857 : Color.RED);
                
                ((TextView) card.findViewById(R.id.tv_shares)).setText(GoldNoteMarketActivity.formatShareAmount(game.myShares.get(sideIndex)) + " 份");
                ((TextView) card.findViewById(R.id.tv_current_value)).setText("-- BKC");
                ((TextView) card.findViewById(R.id.tv_profit)).setText("--");
            }

            card.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), GoldMarketDetailActivity.class);
                intent.putExtra("GAME_ID", game.id);
                startActivity(intent);
            });
            positionsContainer.addView(card);
        }
    }

    private void updateSummary() {
        BigDecimal totalInvested = BigDecimal.ZERO;
        for (GoldMarketRepository.GameModel game : myPositions) {
            for (BigInteger shares : game.myShares) {
                if (shares != null && shares.compareTo(BigInteger.ZERO) > 0) {
                    totalInvested = totalInvested.add(new BigDecimal(shares).divide(new BigDecimal("1000000000000000000"), 2, RoundingMode.HALF_UP));
                }
            }
        }
        
        animateBalance(totalInvested.doubleValue());
        tvTotalPnl.setText(String.format(Locale.getDefault(), "累计参与 %d 个博弈池", myPositions.size()));
    }

    private void animateBalance(double target) {
        ValueAnimator animator = ValueAnimator.ofFloat((float) lastTotalBalance, (float) target);
        animator.setDuration(1000);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float val = (float) animation.getAnimatedValue();
            tvTotalBalance.setText(String.format(Locale.getDefault(), "%,.2f BKC", (double) val));
        });
        animator.start();
        lastTotalBalance = target;
    }

    private SpannableStringBuilder styleMarketTitle(String title) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(title);
        Pattern datePattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
        Matcher dateMatcher = datePattern.matcher(title);
        while (dateMatcher.find()) {
            ssb.setSpan(new ForegroundColorSpan(0xFF888888), dateMatcher.start(), dateMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new AbsoluteSizeSpan(11, true), dateMatcher.start(), dateMatcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        String[] subjects = {"黄金价格", "黄金波幅", "成交量", "指标", "金价", "黄金收益率"};
        for (String sub : subjects) {
            int start = title.indexOf(sub);
            if (start >= 0) {
                ssb.setSpan(new StyleSpan(Typeface.BOLD), start, start + sub.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        String[] ups = {"上涨", "剧烈", "高于", "跑赢", "YES", "触及", "达标", "Price Up"};
        for (String kw : ups) {
            int start = title.indexOf(kw);
            if (start >= 0) {
                ssb.setSpan(new ForegroundColorSpan(0xFF047857), start, start + kw.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        String[] downs = {"下跌", "平稳", "低于", "跑输", "NO", "未达标", "Price Down"};
        for (String kw : downs) {
            int start = title.indexOf(kw);
            if (start >= 0) {
                ssb.setSpan(new ForegroundColorSpan(Color.RED), start, start + kw.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return ssb;
    }
}
