package com.example.brokerfi.xc.agent.gold.view;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.os.Handler;
import android.os.Looper;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.data.PinataClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCardPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketOptionText;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketStatusStyle;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionValuation;
import com.example.brokerfi.xc.agent.gold.viewmodel.GoldMyPositionsViewModel;
import com.bumptech.glide.Glide;

import android.widget.ImageView;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GoldMyPositionsFragment extends Fragment {
    private static final long DATA_REFRESH_INTERVAL_MS = 15_000L;
    private static final int YES_COLOR = GoldMarketStatusStyle.YES_TEXT;
    private static final int YES_BACKGROUND = GoldMarketStatusStyle.YES_BACKGROUND;
    private static final int NO_COLOR = GoldMarketStatusStyle.NO_TEXT;
    private static final int NO_BACKGROUND = GoldMarketStatusStyle.NO_BACKGROUND;
    private static final int NEUTRAL_TEXT = 0xFF475569;
    private static final int NEUTRAL_BACKGROUND = 0xFFF1F5F9;
    private GoldMyPositionsViewModel viewModel;
    private final List<GoldMarketRepository.GameModel> myPositions = new ArrayList<>();

    private TextView tvTotalBalance, tvTotalPnl;
    private LinearLayout positionsContainer;
    private SwipeRefreshLayout swipeRefresh;
    private double lastTotalBalance = 0.0;
    private final Handler dataRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable dataRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (viewModel != null) viewModel.refreshPositions();
            dataRefreshHandler.postDelayed(this, DATA_REFRESH_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_gold_my_positions, container, false);
        tvTotalBalance = view.findViewById(R.id.tv_total_balance);
        tvTotalPnl = view.findViewById(R.id.tv_total_pnl);
        positionsContainer = view.findViewById(R.id.positions_container);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> viewModel.loadPositions());
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(GoldMyPositionsViewModel.class);
        observeViewModel();
        viewModel.loadPositions();
    }

    private void observeViewModel() {
        viewModel.getMyPositions().observe(getViewLifecycleOwner(), positions -> {
            myPositions.clear();
            if (positions != null) {
                myPositions.addAll(positions);
            }
            renderPositions();
            updateSummary();
        });
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> swipeRefresh.setRefreshing(loading));
        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(requireContext(), "Error: " + err, Toast.LENGTH_SHORT).show();
        });

        viewModel.getDebugToast().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        dataRefreshHandler.removeCallbacks(dataRefreshRunnable);
        dataRefreshHandler.post(dataRefreshRunnable);
    }

    @Override
    public void onPause() {
        dataRefreshHandler.removeCallbacks(dataRefreshRunnable);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        dataRefreshHandler.removeCallbacks(dataRefreshRunnable);
        super.onDestroyView();
    }

    private void renderPositions() {
        positionsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (GoldMarketRepository.GameModel game : myPositions) {
            View card = inflater.inflate(R.layout.item_gold_position_card, positionsContainer, false);
            TextView tvTitle = card.findViewById(R.id.tv_position_title);
            ImageView ivIcon = card.findViewById(R.id.iv_position_icon);
            String rawTitle = game.desc != null && !game.desc.isEmpty() ? game.desc : "博弈池 #" + game.id;
            tvTitle.setText(GoldMarketCardPresenter.displayTitle(rawTitle, game.deadlineSec));

            if (game.avatarUrl != null && !game.avatarUrl.isEmpty()) {
                Glide.with(this).load(PinataClient.IPFS_GATEWAY + game.avatarUrl).placeholder(R.drawable.apartment_icon).into(ivIcon);
            } else {
                ivIcon.setImageResource(R.drawable.apartment_icon);
            }

            TextView tvSide = card.findViewById(R.id.tv_position_side);
            TextView tvShares = card.findViewById(R.id.tv_shares);
            TextView tvCurrentValue = card.findViewById(R.id.tv_current_value);
            TextView tvProfit = card.findViewById(R.id.tv_profit);

            List<String> sideNames = new ArrayList<>();
            StringBuilder shareText = new StringBuilder();
            if (game.myShares != null) {
                for (int i = 0; i < game.myShares.size(); i++) {
                    BigInteger shares = game.myShares.get(i);
                    if (shares == null || shares.compareTo(BigInteger.ZERO) <= 0) continue;
                    String sideName = optionNameFor(game, i);
                    sideNames.add(sideName);
                    if (shareText.length() > 0) shareText.append('\n');
                    shareText.append(sideName).append(": ").append(GoldNoteMarketActivity.formatShareAmount(shares)).append(" 份额");
                }
            }
            tvSide.setText(styleSideText(joinSideNames(sideNames)));
            tvSide.setTextColor(resolveSideColor(sideNames));
            tvSide.setBackground(makeRoundedBackground(resolveSideBackground(sideNames), 999));
            tvShares.setText(shareText.length() == 0 ? "暂无份额" : styleShareText(shareText.toString()));

            GoldPositionValuation.MarketValue marketValue = GoldPositionValuation.calculateMarket(game);
            tvCurrentValue.setText(marketValue.isComplete() ? GoldNoteMarketActivity.formatBkc(marketValue.getValueWei()) + " BKC" : "暂不可估值");
            long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(game.deadlineSec, System.currentTimeMillis());
            GoldMarketStatusStyle status = GoldMarketStatusStyle.forMarket(game.isResolved, game.isRefunded, remaining);
            tvProfit.setText(status.label);
            tvProfit.setTextColor(status.textColor);
            tvProfit.setBackground(makeRoundedBackground(status.backgroundColor, 999));
            card.setBackgroundResource(R.drawable.bg_gold_market_card);

            card.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), GoldPositionDetailActivity.class);
                intent.putExtra("GAME_ID", game.id);
                intent.putExtra("CONTRACT_ADDRESS", game.contractAddress);
                startActivity(intent);
            });
            positionsContainer.addView(card);
        }
    }

    private GradientDrawable makeRoundedBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void updateSummary() {
        GoldPositionValuation.PortfolioValue portfolio = GoldPositionValuation.calculatePortfolio(myPositions);
        BigDecimal totalBkc = new BigDecimal(portfolio.getValueWei()).divide(new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP);
        animateBalance(totalBkc.doubleValue());
        String subtitle = String.format(Locale.getDefault(), "累计参与 %d 个博弈池", myPositions.size());
        if (portfolio.getUnavailableMarketCount() > 0) {
            subtitle += String.format(Locale.getDefault(), " · %d 个持有暂未计入估值", portfolio.getUnavailableMarketCount());
        }
        tvTotalPnl.setText(subtitle);
    }

    private String optionNameFor(GoldMarketRepository.GameModel game, int index) {
        if (game.optionNames != null && index < game.optionNames.size()) {
            return GoldMarketOptionText.displayName(game.optionNames.get(index), index);
        }
        return GoldMarketOptionText.displayName(index);
    }

    private String joinSideNames(List<String> sideNames) {
        if (sideNames.isEmpty()) return "--";
        StringBuilder joined = new StringBuilder();
        for (String s : sideNames) {
            if (joined.length() > 0) joined.append(" / ");
            joined.append(s);
        }
        return joined.toString();
    }

    private int resolveSideColor(List<String> sideNames) {
        if (sideNames.isEmpty()) return Color.BLACK;
        boolean allPositive = true, allNegative = true;
        for (String s : sideNames) {
            String upper = (s == null ? "" : s).toUpperCase(Locale.US);
            boolean neg = upper.contains("NO") || upper.contains("DOWN") || upper.contains("跌")
                    || upper.contains("未达标") || upper.contains("未达成");
            boolean pos = !neg && (upper.contains("YES") || upper.contains("UP") || upper.contains("涨")
                    || upper.contains("达标") || upper.contains("达成"));
            allPositive = allPositive && pos;
            allNegative = allNegative && neg;
        }
        if (allPositive) return YES_COLOR;
        if (allNegative) return NO_COLOR;
        return NEUTRAL_TEXT;
    }

    private int resolveSideBackground(List<String> sideNames) {
        if (sideNames.isEmpty()) return NEUTRAL_BACKGROUND;
        int color = resolveSideColor(sideNames);
        if (color == YES_COLOR) return YES_BACKGROUND;
        if (color == NO_COLOR) return NO_BACKGROUND;
        return NEUTRAL_BACKGROUND;
    }

    private CharSequence styleSideText(String text) {
        if (text == null || text.isEmpty()) return "--";
        SpannableStringBuilder styled = new SpannableStringBuilder(text);
        applySideKeywords(styled, text, YES_COLOR, "YES", "UP", "涨", "达标", "达成");
        applySideKeywords(styled, text, NO_COLOR, "NO", "DOWN", "跌", "未达标", "未达成");
        return styled;
    }

    private CharSequence styleShareText(String text) {
        SpannableStringBuilder styled = new SpannableStringBuilder(text);
        int start = 0;
        while (start <= text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) end = text.length();
            String line = text.substring(start, end);
            int color = colorForSideText(line);
            styled.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            styled.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (end == text.length()) break;
            start = end + 1;
        }
        return styled;
    }

    private int colorForSideText(String text) {
        String upper = text == null ? "" : text.toUpperCase(Locale.US);
        if (upper.contains("NO") || upper.contains("DOWN") || upper.contains("跌")
                || upper.contains("未达标") || upper.contains("未达成")) {
            return NO_COLOR;
        }
        if (upper.contains("YES") || upper.contains("UP") || upper.contains("涨")
                || upper.contains("达标") || upper.contains("达成")) {
            return YES_COLOR;
        }
        return NEUTRAL_TEXT;
    }

    private void applySideKeywords(SpannableStringBuilder styled, String text, int color, String... keywords) {
        String lower = text.toLowerCase(Locale.US);
        for (String keyword : keywords) {
            String key = keyword.toLowerCase(Locale.US);
            int start = lower.indexOf(key);
            while (start >= 0) {
                int end = start + keyword.length();
                styled.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                styled.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = lower.indexOf(key, end);
            }
        }
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

}
