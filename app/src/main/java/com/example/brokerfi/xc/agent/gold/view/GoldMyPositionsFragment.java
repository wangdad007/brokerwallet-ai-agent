package com.example.brokerfi.xc.agent.gold.view;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.os.Handler;
import android.os.Looper;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionSet;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.EditText;
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
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketSearchMatcher;
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
    private static final int SECTION_HOLDINGS = 0;
    private static final int SECTION_LIQUIDITY = 1;
    private static final int SECTION_STRATEGIES = 2;
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
    private final List<BackendApiClient.StrategyDTO> myStrategies = new ArrayList<>();
    private final List<GoldMarketRepository.GameModel> marketCatalog = new ArrayList<>();

    private TextView tvTotalBalance, tvTotalPnl, tvPortfolioToggle, labelBalance;
    private LineChart portfolioChart;
    private View portfolioChartSection, portfolioDetails;
    private LinearLayout portfolioSummaryCard, positionsPageContent;
    private LinearLayout positionsContainer;
    private TextView positionSearchEmpty;
    private TextView tabHoldings, tabLiquidity, tabStrategies, personalSectionTitle;
    private EditText searchInput;
    private int selectedSection = SECTION_HOLDINGS;
    private String searchQuery = "";
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
        labelBalance = view.findViewById(R.id.label_balance);
        portfolioChart = view.findViewById(R.id.chart_portfolio_value);
        portfolioChartSection = view.findViewById(R.id.portfolio_chart_section);
        portfolioDetails = view.findViewById(R.id.portfolio_details);
        portfolioSummaryCard = view.findViewById(R.id.portfolio_summary_card);
        positionsPageContent = view.findViewById(R.id.positions_page_content);
        tvPortfolioToggle = view.findViewById(R.id.tv_portfolio_toggle);
        positionsContainer = view.findViewById(R.id.positions_container);
        positionSearchEmpty = view.findViewById(R.id.position_search_empty);
        searchInput = view.findViewById(R.id.position_search_input);
        tabHoldings = view.findViewById(R.id.tab_my_holdings);
        tabLiquidity = view.findViewById(R.id.tab_my_liquidity);
        tabStrategies = view.findViewById(R.id.tab_my_strategies);
        personalSectionTitle = view.findViewById(R.id.tv_personal_section_title);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> viewModel.loadPositions());
        view.findViewById(R.id.portfolio_summary_header)
                .setOnClickListener(v -> setPortfolioExpanded(
                        portfolioDetails.getVisibility() != View.VISIBLE));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                searchQuery = text == null ? "" : text.toString();
                renderPositions();
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        tabHoldings.setOnClickListener(v -> selectSection(SECTION_HOLDINGS));
        tabLiquidity.setOnClickListener(v -> selectSection(SECTION_LIQUIDITY));
        tabStrategies.setOnClickListener(v -> selectSection(SECTION_STRATEGIES));
        selectSection(SECTION_HOLDINGS);
        return view;
    }

    private void setPortfolioExpanded(boolean expanded) {
        TransitionSet transition = new TransitionSet()
                .setOrdering(TransitionSet.ORDERING_TOGETHER)
                .addTransition(new ChangeBounds())
                .addTransition(new Fade());
        transition.setDuration(240L);
        transition.setInterpolator(new DecelerateInterpolator());
        TransitionManager.beginDelayedTransition(positionsPageContent, transition);
        portfolioDetails.setVisibility(expanded ? View.VISIBLE : View.GONE);
        tvPortfolioToggle.setText(expanded ? "收起 ︿" : "展开 ﹀");
        tvPortfolioToggle.setContentDescription(expanded ? "收起资产估值走势" : "展开资产估值走势");
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
            if (selectedSection == SECTION_HOLDINGS) setupPortfolioChart();
        });
        viewModel.getStrategies().observe(getViewLifecycleOwner(), values -> {
            myStrategies.clear();
            if (values != null) myStrategies.addAll(values);
            renderPositions();
            updateSummary();
        });
        viewModel.getMarketCatalog().observe(getViewLifecycleOwner(), values -> {
            marketCatalog.clear();
            if (values != null) marketCatalog.addAll(values);
            if (selectedSection == SECTION_STRATEGIES) renderPositions();
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
        if (selectedSection == SECTION_STRATEGIES) {
            renderStrategies();
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int visibleCount = 0;
        for (GoldMarketRepository.GameModel game : myPositions) {
            if (!GoldMarketSearchMatcher.matches(game, searchQuery)) continue;
            if (selectedSection == SECTION_HOLDINGS && !hasOutcomeShares(game)) continue;
            if (selectedSection == SECTION_LIQUIDITY && !hasLiquidity(game)) continue;
            visibleCount++;
            if (selectedSection == SECTION_LIQUIDITY) {
                addLiquidityCard(inflater, game);
                continue;
            }
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
            if (selectedSection == SECTION_HOLDINGS && game.myShares != null) {
                for (int i = 0; i < game.myShares.size(); i++) {
                    BigInteger shares = game.myShares.get(i);
                    if (shares == null || shares.compareTo(BigInteger.ZERO) <= 0) continue;
                    String sideName = GoldMarketOptionText.holdingLabel(i);
                    heldOptionIndexes.add(i);
                    if (shareText.length() > 0) shareText.append('\n');
                    shareText.append(sideName).append(' ')
                            .append(GoldNoteMarketActivity.formatShareAmount(shares)).append(" 份额");
                }
            }
            boolean hasLiquidity = hasLiquidity(game);
            if (selectedSection == SECTION_LIQUIDITY && hasLiquidity) {
                if (shareText.length() > 0) shareText.append('\n');
                shareText.append("流动性：")
                        .append(GoldNoteMarketActivity.formatShareAmount(
                                game.myLiquidityShares))
                        .append(" LP");
            }
            tvSide.setText(heldOptionIndexes.isEmpty() && hasLiquidity
                    ? "流动性 LP" : sideBadgeText(heldOptionIndexes));
            tvSide.setTextColor(heldOptionIndexes.isEmpty() && hasLiquidity
                    ? NEUTRAL_TEXT : resolveSideColor(heldOptionIndexes));
            tvSide.setBackground(null);
            tvShares.setText(shareText.length() == 0 ? "暂无份额" : styleShareText(shareText.toString()));

            GoldPositionValuation.MarketValue marketValue =
                    selectedSection == SECTION_LIQUIDITY
                            ? GoldPositionValuation.calculateLiquidityMarket(game)
                            : GoldPositionValuation.calculateOutcomeMarket(game);
            tvCurrentValue.setText(marketValue.isComplete() ? GoldNoteMarketActivity.formatBkc(marketValue.getValueWei()) + " BKC" : "估值暂不可用");
            long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(game.deadlineSec, System.currentTimeMillis());
            GoldMarketStatusStyle status = GoldMarketStatusStyle.forMarket(game.isResolved, game.isRefunded, remaining);
            tvProfit.setText(status.label);
            tvProfit.setTextColor(status.textColor);
            tvProfit.setBackground(null);
            card.setBackgroundResource(R.drawable.bg_gold_market_panel);

            card.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), GoldPositionDetailActivity.class);
                intent.putExtra("GAME_ID", game.id);
                intent.putExtra("CONTRACT_ADDRESS", game.contractAddress);
                startActivity(intent);
            });
            positionsContainer.addView(card);
        }
        positionSearchEmpty.setText(searchQuery.trim().isEmpty()
                ? selectedSection == SECTION_LIQUIDITY
                    ? "当前没有质押中的流动性"
                    : "当前没有可展示的持仓"
                : "没有找到匹配的内容");
        positionSearchEmpty.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void addLiquidityCard(LayoutInflater inflater,
                                  GoldMarketRepository.GameModel game) {
        View card = inflater.inflate(
                R.layout.item_gold_liquidity_card, positionsContainer, false);
        TextView title = card.findViewById(R.id.tv_liquidity_title);
        ImageView icon = card.findViewById(R.id.iv_liquidity_icon);
        TextView myValue = card.findViewById(R.id.tv_my_liquidity_value);
        TextView poolValue = card.findViewById(R.id.tv_pool_liquidity_value);
        TextView lpValue = card.findViewById(R.id.tv_liquidity_lp);

        String rawTitle = game.desc != null && !game.desc.trim().isEmpty()
                ? game.desc.trim() : "当前博弈池";
        title.setText(stylePositionTitle(rawTitle, game.condition, game.deadlineSec));
        int templateIcon = GoldMarketTemplateIcon.forMarket(
                game.avatarUrl, rawTitle, game.condition);
        if (templateIcon != 0) {
            icon.setImageResource(templateIcon);
        } else if (game.avatarUrl != null && !game.avatarUrl.isEmpty()) {
            Glide.with(this).load(PinataClient.IPFS_GATEWAY + game.avatarUrl)
                    .placeholder(R.drawable.apartment_icon).into(icon);
        } else {
            icon.setImageResource(R.drawable.apartment_icon);
        }

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateLiquidityMarket(game);
        myValue.setText(value.isComplete()
                ? GoldNoteMarketActivity.formatBkc(value.getValueWei()) + " BKC"
                : "估值暂不可用");
        poolValue.setText(game.totalPool == null
                ? "-- BKC" : GoldNoteMarketActivity.formatBkc(game.totalPool) + " BKC");
        lpValue.setText(formatCompactLp(game.myLiquidityShares)
                + " LP · " + liquiditySharePercent(game));

        card.setOnClickListener(v -> startActivity(
                GoldLiquidityDetailActivity.createIntent(
                        requireContext(), game.id, game.contractAddress, rawTitle)));
        positionsContainer.addView(card);
    }

    private String liquiditySharePercent(GoldMarketRepository.GameModel game) {
        if (game == null || game.myLiquidityShares == null
                || game.totalLiquidityShares == null
                || game.totalLiquidityShares.signum() <= 0) return "--";
        BigDecimal percent = new BigDecimal(game.myLiquidityShares)
                .multiply(new BigDecimal("100"))
                .divide(new BigDecimal(game.totalLiquidityShares),
                        2, RoundingMode.HALF_UP);
        return percent.stripTrailingZeros().toPlainString() + "%";
    }

    private String formatCompactLp(BigInteger liquidityShares) {
        if (liquidityShares == null) return "0";
        BigDecimal value = new BigDecimal(liquidityShares)
                .divide(new BigDecimal("1000000000000000000"),
                        2, RoundingMode.HALF_UP);
        return value.stripTrailingZeros().toPlainString();
    }

    private void renderStrategies() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        List<BackendApiClient.StrategyDTO> aiStrategies = new ArrayList<>();
        List<BackendApiClient.StrategyDTO> ruleStrategies = new ArrayList<>();
        for (BackendApiClient.StrategyDTO item : myStrategies) {
            if (item == null) continue;
            GoldMarketRepository.GameModel market = findMarket(item.gameId, item.contractAddress);
            String title = market == null || market.desc == null || market.desc.trim().isEmpty()
                    ? "当前博弈池" : market.desc.trim();
            if (!searchQuery.trim().isEmpty()
                    && !title.toLowerCase(Locale.US)
                    .contains(searchQuery.trim().toLowerCase(Locale.US))) {
                continue;
            }
            BackendApiClient.AiManagedConfig config = item.strategy == null
                    ? BackendApiClient.AiManagedConfig.defaults() : item.strategy;
            String strategyType = normalizeStrategyType(config.strategyType);
            if ("ai".equals(strategyType)) aiStrategies.add(item);
            else ruleStrategies.add(item);
        }
        for (BackendApiClient.StrategyDTO item : aiStrategies) {
            addStrategyCard(inflater, item);
        }
        for (BackendApiClient.StrategyDTO item : ruleStrategies) {
            addStrategyCard(inflater, item);
        }
        int visibleCount = aiStrategies.size() + ruleStrategies.size();
        positionSearchEmpty.setText(searchQuery.trim().isEmpty()
                ? "当前没有已启用的自动策略" : "没有找到匹配的策略");
        positionSearchEmpty.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
    }

    private void addStrategyCard(LayoutInflater inflater, BackendApiClient.StrategyDTO item) {
        GoldMarketRepository.GameModel market = findMarket(item.gameId, item.contractAddress);
        String title = market == null || market.desc == null || market.desc.trim().isEmpty()
                ? "当前博弈池" : market.desc.trim();
        View card = inflater.inflate(
                R.layout.item_gold_strategy_card, positionsContainer, false);
        BackendApiClient.AiManagedConfig config = item.strategy == null
                ? BackendApiClient.AiManagedConfig.defaults() : item.strategy;
            TextView type = card.findViewById(R.id.tv_strategy_type);
            TextView status = card.findViewById(R.id.tv_strategy_status);
            TextView marketName = card.findViewById(R.id.tv_strategy_market);
            TextView summary = card.findViewById(R.id.tv_strategy_summary);
            TextView error = card.findViewById(R.id.tv_strategy_error);
            ImageView icon = card.findViewById(R.id.iv_strategy_icon);
            String strategyType = normalizeStrategyType(config.strategyType);
            type.setText("grid".equals(strategyType) ? "网格策略"
                    : "martingale".equals(strategyType) ? "马丁格尔" : "AI 托管");
            status.setText("ai".equals(strategyType) ? "AI 判断" : "阈值触发");
            if ("grid".equals(strategyType)) {
                type.setTextColor(0xFF0F766E);
            } else if ("martingale".equals(strategyType)) {
                type.setTextColor(0xFFB45309);
            } else {
                type.setTextColor(0xFF2563EB);
            }
            type.setBackground(null);
            status.setTextColor("ai".equals(strategyType) ? 0xFF2563EB : 0xFF0F766E);
            int templateIcon = GoldMarketTemplateIcon.forMarket(
                    market == null ? null : market.avatarUrl,
                    title, market == null ? null : market.condition);
            if (templateIcon != 0) {
                icon.setImageResource(templateIcon);
            } else if (market != null && market.avatarUrl != null
                    && !market.avatarUrl.isEmpty()) {
                Glide.with(this).load(PinataClient.IPFS_GATEWAY + market.avatarUrl)
                        .placeholder(R.drawable.apartment_icon).into(icon);
            } else {
                icon.setImageResource(R.drawable.apartment_icon);
            }
            marketName.setText(GoldMarketTextStyler.style(title, true));
            String direction = "no".equalsIgnoreCase(config.direction) ? "NO" : "YES";
            if ("grid".equals(strategyType)) {
                summary.setText(String.format(Locale.US,
                        "%s · %d 格 · %s BKC/单",
                        direction, config.gridLevels, config.buyAmountBKC));
            } else if ("martingale".equals(strategyType)) {
                summary.setText(String.format(Locale.US,
                        "%s · %.2f 倍 · %d 档",
                        direction,
                        config.martingaleMultiplier, config.martingaleMaxRounds));
            } else {
                summary.setText(String.format(Locale.US,
                        "%s BKC/单 · ≥%.0f%%",
                        config.buyAmountBKC, config.confidenceMin * 100));
            }
            if (item.lastError != null && !item.lastError.trim().isEmpty()) {
                error.setText("最近一次检查：" + item.lastError.trim());
                error.setVisibility(View.VISIBLE);
            }
            card.setOnClickListener(v -> {
                if ("grid".equals(strategyType) || "martingale".equals(strategyType)) {
                    startActivity(GoldCustomStrategyActivity.createDetailIntent(
                            requireContext(), item.gameId, item.contractAddress, title));
                } else {
                    config.enabled = true;
                    startActivity(GoldAiManagedSettingsActivity.createIntent(
                            requireContext(), item.gameId, item.contractAddress, title, config));
                }
            });
            positionsContainer.addView(card);
    }

    private String normalizeStrategyType(String strategyType) {
        if ("grid".equalsIgnoreCase(strategyType)) return "grid";
        if ("martingale".equalsIgnoreCase(strategyType)) return "martingale";
        return "ai";
    }

    private void styleStrategyBadge(TextView view, int textColor, int backgroundColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dp(8));
        view.setTextColor(textColor);
        view.setBackground(background);
    }

    private GoldMarketRepository.GameModel findMarket(int gameId, String contract) {
        for (GoldMarketRepository.GameModel market : marketCatalog) {
            if (market.id != gameId) continue;
            if (contract == null || market.contractAddress == null
                    || contract.equalsIgnoreCase(market.contractAddress)) return market;
        }
        return null;
    }

    private boolean hasOutcomeShares(GoldMarketRepository.GameModel game) {
        if (game == null || game.myShares == null) return false;
        for (BigInteger value : game.myShares) {
            if (value != null && value.signum() > 0) return true;
        }
        return false;
    }

    private boolean hasLiquidity(GoldMarketRepository.GameModel game) {
        return game != null && game.myLiquidityShares != null
                && game.myLiquidityShares.signum() > 0;
    }

    private void selectSection(int section) {
        selectedSection = section;
        styleSectionTab(tabHoldings, section == SECTION_HOLDINGS);
        styleSectionTab(tabLiquidity, section == SECTION_LIQUIDITY);
        styleSectionTab(tabStrategies, section == SECTION_STRATEGIES);
        personalSectionTitle.setText(section == SECTION_HOLDINGS ? "我的持仓"
                : section == SECTION_LIQUIDITY ? "我的质押" : "我的策略");
        labelBalance.setText(section == SECTION_HOLDINGS ? "个人持有估值"
                : section == SECTION_LIQUIDITY ? "个人质押估值" : "自动策略");
        searchInput.setHint(section == SECTION_HOLDINGS ? "搜索持仓标题或规则"
                : section == SECTION_LIQUIDITY ? "搜索质押博弈池"
                : "搜索策略对应的博弈池");
        renderPositions();
        updateSummary();
    }

    private void styleSectionTab(TextView view, boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(11));
        background.setColor(selected ? 0xFFFFFFFF : Color.TRANSPARENT);
        if (selected) background.setStroke((int) dp(1), 0xFFD7E0EC);
        view.setBackground(background);
        view.setTextColor(selected ? 0xFF0F172A : 0xFF64748B);
        view.setElevation(selected ? dp(1) : 0f);
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
        if (selectedSection == SECTION_STRATEGIES) {
            tvTotalBalance.setText(String.format(Locale.US, "%d 个运行中", myStrategies.size()));
            tvTotalPnl.setText("后端持续轮询并按已保存参数执行");
            portfolioChartSection.setVisibility(View.GONE);
            lastTotalBalance = 0;
            return;
        }
        List<GoldMarketRepository.GameModel> selected = new ArrayList<>();
        for (GoldMarketRepository.GameModel game : myPositions) {
            if (selectedSection == SECTION_HOLDINGS && hasOutcomeShares(game)) selected.add(game);
            if (selectedSection == SECTION_LIQUIDITY && hasLiquidity(game)) selected.add(game);
        }
        GoldPositionValuation.PortfolioValue portfolio =
                selectedSection == SECTION_LIQUIDITY
                        ? GoldPositionValuation.calculateLiquidityPortfolio(selected)
                        : GoldPositionValuation.calculateOutcomePortfolio(selected);
        BigDecimal totalBkc = new BigDecimal(portfolio.getValueWei()).divide(new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP);
        animateBalance(totalBkc.doubleValue());
        int marketCount = selected.size();
        String subtitle = String.format(Locale.US,
                selectedSection == SECTION_LIQUIDITY
                        ? "正在为 %d 个博弈池提供流动性"
                        : "累计参与 %d 个博弈池", marketCount);
        if (portfolio.getUnavailableMarketCount() > 0) {
            subtitle += String.format(Locale.US, " · %d 个暂未计入估值", portfolio.getUnavailableMarketCount());
        }
        tvTotalPnl.setText(subtitle);
        if (selectedSection == SECTION_HOLDINGS) {
            if (viewModel != null) {
                viewModel.saveAndLoadPortfolioHistory(portfolio.getValueWei(), selected.size());
            }
        }
        if (selectedSection == SECTION_HOLDINGS) setupPortfolioChart();
        else portfolioChartSection.setVisibility(View.GONE);
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
        while (start < text.length()) {
            int lineBreak = text.indexOf('\n', start);
            int inlineDivider = text.indexOf(" · ", start);
            int end = text.length();
            int delimiterLength = 0;
            if (lineBreak >= 0 && lineBreak < end) {
                end = lineBreak;
                delimiterLength = 1;
            }
            if (inlineDivider >= 0 && inlineDivider < end) {
                end = inlineDivider;
                delimiterLength = 3;
            }
            if (end > start) {
                String segment = text.substring(start, end);
                styled.setSpan(new ForegroundColorSpan(colorForSideText(segment)),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                styled.setSpan(new StyleSpan(Typeface.BOLD),
                        start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (end == text.length()) break;
            start = end + delimiterLength;
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
