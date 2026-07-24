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
import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.PinataClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCardPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketOptionText;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketStatusStyle;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPortfolioHistoryPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionValuation;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldPositionVisibility;
import com.example.brokerfi.xc.agent.gold.viewmodel.GoldMyPositionsViewModel;
import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import android.widget.ImageView;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
    private final List<BackendApiClient.PortfolioHistoryPointDTO> savedPortfolioHistory = new ArrayList<>();

    private TextView tvTotalBalance, tvTotalPnl;
    private LineChart portfolioChart;
    private View portfolioChartSection;
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
        portfolioChart = view.findViewById(R.id.chart_portfolio_value);
        portfolioChartSection = view.findViewById(R.id.portfolio_chart_section);
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
                long nowMillis = System.currentTimeMillis();
                for (GoldMarketRepository.GameModel position : positions) {
                    if (GoldPositionVisibility.isVisible(position, nowMillis)) {
                        myPositions.add(position);
                    }
                }
            }
            renderPositions();
            updateSummary();
        });
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> swipeRefresh.setRefreshing(loading));
        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(requireContext(), "加载失败：" + err, Toast.LENGTH_SHORT).show();
        });

        viewModel.getPortfolioHistory().observe(getViewLifecycleOwner(), points -> {
            savedPortfolioHistory.clear();
            if (points != null) savedPortfolioHistory.addAll(points);
            setupPortfolioChart();
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
            tvTitle.setText(stylePositionTitle(rawTitle, game.condition, game.deadlineSec));

            int templateIcon = GoldMarketTemplateIcon.forMarket(
                    game.avatarUrl, rawTitle, game.condition);
            if (templateIcon != 0) {
                ivIcon.setImageResource(templateIcon);
            } else if (game.avatarUrl != null && !game.avatarUrl.isEmpty()) {
                Glide.with(this).load(PinataClient.IPFS_GATEWAY + game.avatarUrl).placeholder(R.drawable.apartment_icon).into(ivIcon);
            } else {
                ivIcon.setImageResource(R.drawable.apartment_icon);
            }

            TextView tvSide = card.findViewById(R.id.tv_position_side);
            TextView tvShares = card.findViewById(R.id.tv_shares);
            TextView tvCurrentValue = card.findViewById(R.id.tv_current_value);
            TextView tvProfit = card.findViewById(R.id.tv_profit);

            List<Integer> heldOptionIndexes = new ArrayList<>();
            StringBuilder shareText = new StringBuilder();
            if (game.myShares != null) {
                for (int i = 0; i < game.myShares.size(); i++) {
                    BigInteger shares = game.myShares.get(i);
                    if (shares == null || shares.compareTo(BigInteger.ZERO) <= 0) continue;
                    String sideName = GoldMarketOptionText.holdingLabel(i);
                    heldOptionIndexes.add(i);
                    if (shareText.length() > 0) shareText.append('\n');
                    shareText.append(sideName).append("：")
                            .append(GoldNoteMarketActivity.formatShareAmount(shares)).append(" 份额");
                }
            }
            tvSide.setText(sideBadgeText(heldOptionIndexes));
            tvSide.setTextColor(resolveSideColor(heldOptionIndexes));
            tvSide.setBackground(makeRoundedBackground(resolveSideBackground(heldOptionIndexes), 999));
            tvShares.setText(shareText.length() == 0 ? "暂无份额" : styleShareText(shareText.toString()));

            GoldPositionValuation.MarketValue marketValue = GoldPositionValuation.calculateMarket(game);
            tvCurrentValue.setText(marketValue.isComplete() ? GoldNoteMarketActivity.formatBkc(marketValue.getValueWei()) + " BKC" : "估值暂不可用");
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
        int marketCount = myPositions.size();
        String subtitle = String.format(Locale.US, "累计参与 %d 个博弈池", marketCount);
        if (portfolio.getUnavailableMarketCount() > 0) {
            subtitle += String.format(Locale.US, " · %d 个暂未计入估值", portfolio.getUnavailableMarketCount());
        }
        tvTotalPnl.setText(subtitle);
        viewModel.saveAndLoadPortfolioHistory(portfolio.getValueWei(), myPositions.size());
        setupPortfolioChart();
    }

    private String sideBadgeText(List<Integer> heldOptionIndexes) {
        if (heldOptionIndexes.isEmpty()) return "--";
        boolean hasYes = heldOptionIndexes.contains(0);
        boolean hasNo = heldOptionIndexes.contains(1);
        if (hasYes && hasNo) return "双向持有";
        return GoldMarketOptionText.holdingLabel(hasNo ? 1 : 0);
    }

    private int resolveSideColor(List<Integer> heldOptionIndexes) {
        if (heldOptionIndexes.isEmpty()) return Color.BLACK;
        boolean hasYes = heldOptionIndexes.contains(0);
        boolean hasNo = heldOptionIndexes.contains(1);
        if (hasYes && !hasNo) return YES_COLOR;
        if (hasNo && !hasYes) return NO_COLOR;
        return NEUTRAL_TEXT;
    }

    private int resolveSideBackground(List<Integer> heldOptionIndexes) {
        if (heldOptionIndexes.isEmpty()) return NEUTRAL_BACKGROUND;
        int color = resolveSideColor(heldOptionIndexes);
        if (color == YES_COLOR) return YES_BACKGROUND;
        if (color == NO_COLOR) return NO_BACKGROUND;
        return NEUTRAL_BACKGROUND;
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

    private CharSequence stylePositionTitle(String rawTitle, String condition, long deadlineSec) {
        return GoldMarketTextStyler.style(
                GoldMarketCardPresenter.displayTitle(rawTitle, condition, deadlineSec), true);
    }


    private void setupPortfolioChart() {
        if (portfolioChart == null || portfolioChartSection == null) return;
        final List<GoldPortfolioHistoryPresenter.Point> points = portfolioChartPoints();
        if (points.size() < 2) {
            portfolioChartSection.setVisibility(View.GONE);
            return;
        }
        portfolioChartSection.setVisibility(View.VISIBLE);

        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            BigDecimal bkc = new BigDecimal(points.get(i).valueWei)
                    .divide(new BigDecimal("1000000000000000000"), 4, RoundingMode.HALF_UP);
            entries.add(new Entry(i, bkc.floatValue()));
        }

        LineDataSet dataSet = new LineDataSet(entries, "");
        dataSet.setColor(0xFF5EEAD4);
        dataSet.setLineWidth(2.25f);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(points.size() <= 8);
        dataSet.setCircleRadius(2.5f);
        dataSet.setCircleColor(0xFF5EEAD4);
        dataSet.setDrawCircleHole(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(0xFF5EEAD4);
        dataSet.setFillAlpha(32);
        portfolioChart.setData(new LineData(dataSet));
        portfolioChart.getDescription().setEnabled(false);
        portfolioChart.getLegend().setEnabled(false);
        portfolioChart.setDrawGridBackground(false);
        portfolioChart.setTouchEnabled(true);
        portfolioChart.setScaleEnabled(false);
        portfolioChart.setPinchZoom(false);
        portfolioChart.setExtraOffsets(0f, 4f, 0f, 0f);

        XAxis xAxis = portfolioChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setTextColor(0xFF94A3B8);
        xAxis.setTextSize(9f);
        xAxis.setLabelCount(Math.min(3, points.size()), false);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat formatter = new SimpleDateFormat("MM-dd", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                int index = Math.round(value);
                if (index < 0 || index >= points.size()) return "";
                return formatter.format(new Date(points.get(index).timeSec * 1000L));
            }
        });

        portfolioChart.getAxisRight().setEnabled(false);
        portfolioChart.getAxisLeft().setDrawAxisLine(false);
        portfolioChart.getAxisLeft().setDrawGridLines(true);
        portfolioChart.getAxisLeft().setGridColor(0x22FFFFFF);
        portfolioChart.getAxisLeft().setTextColor(0xFF94A3B8);
        portfolioChart.getAxisLeft().setTextSize(9f);
        portfolioChart.getAxisLeft().setLabelCount(3, true);
        portfolioChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f", value);
            }
        });
        portfolioChart.animateX(500);
        portfolioChart.invalidate();
    }

    private List<GoldPortfolioHistoryPresenter.Point> portfolioChartPoints() {
        if (savedPortfolioHistory.size() >= 2) {
            List<GoldPortfolioHistoryPresenter.Point> points = new ArrayList<>();
            for (BackendApiClient.PortfolioHistoryPointDTO saved : savedPortfolioHistory) {
                if (saved == null || saved.timestampSec <= 0 || saved.totalValueWei == null) continue;
                try {
                    points.add(new GoldPortfolioHistoryPresenter.Point(
                            saved.timestampSec, new BigInteger(saved.totalValueWei)));
                } catch (NumberFormatException ignored) {
                    // Skip malformed historical rows without hiding valid chart data.
                }
            }
            if (points.size() >= 2) return points;
        }
        return GoldPortfolioHistoryPresenter.pointsFor(
                myPositions, System.currentTimeMillis() / 1000L);
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
