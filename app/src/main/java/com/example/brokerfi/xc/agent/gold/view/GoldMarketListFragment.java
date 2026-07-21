package com.example.brokerfi.xc.agent.gold.view;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.ai.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.data.PinataClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldAdvisoryManager;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketCardPresenter;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketOptionText;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketStatusStyle;
import com.example.brokerfi.xc.agent.gold.viewmodel.GoldMarketViewModel;
import com.bumptech.glide.Glide;

import android.widget.ImageView;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GoldMarketListFragment extends Fragment {
    private static final long DATA_REFRESH_INTERVAL_MS = 15_000L;
    private GoldMarketViewModel viewModel;
    private final List<GoldMarketRepository.GameModel> availableGames = new ArrayList<>();

    private LinearLayout marketListContainer;
    private SwipeRefreshLayout swipeRefresh;
    private final Handler dataRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable dataRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (viewModel != null) viewModel.refreshData();
            dataRefreshHandler.postDelayed(this, DATA_REFRESH_INTERVAL_MS);
        }
    };

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
        viewModel = new ViewModelProvider(this).get(GoldMarketViewModel.class);
        observeViewModel();
        viewModel.loadData();
    }

    private void initViews(View v) {
        marketListContainer = v.findViewById(R.id.market_list_container);
        swipeRefresh = v.findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> viewModel.loadData());
    }

    private void observeViewModel() {
        viewModel.getAvailableGames().observe(getViewLifecycleOwner(), games -> {
            availableGames.clear();
            if (games != null) {
                availableGames.addAll(games);
                Collections.sort(availableGames, (g1, g2) -> Integer.compare(g2.id, g1.id));
            }
            renderMarketList();
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> swipeRefresh.setRefreshing(loading));

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(requireContext(), "Error: " + err, Toast.LENGTH_SHORT).show();
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

    private void renderMarketList() {
        marketListContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (GoldMarketRepository.GameModel game : availableGames) {
            View card = inflater.inflate(R.layout.item_gold_market_card, marketListContainer, false);
            TextView tvTitle = card.findViewById(R.id.tv_market_title);
            ImageView ivIcon = card.findViewById(R.id.iv_market_icon);
            String rawTitle = game.desc != null && !game.desc.isEmpty() ? game.desc : "Market #" + game.id;
            tvTitle.setText(GoldMarketTextStyler.style(
                    GoldMarketCardPresenter.displayTitle(rawTitle, game.condition, game.deadlineSec), true));

            int templateIcon = GoldMarketTemplateIcon.forMarket(
                    game.avatarUrl, rawTitle, game.condition);
            if (templateIcon != 0) {
                ivIcon.setImageResource(templateIcon);
            } else if (game.avatarUrl != null && !game.avatarUrl.isEmpty()) {
                Glide.with(this).load(PinataClient.IPFS_GATEWAY + game.avatarUrl).placeholder(R.drawable.apartment_icon).into(ivIcon);
            } else {
                ivIcon.setImageResource(R.drawable.apartment_icon);
            }

            ((TextView) card.findViewById(R.id.tv_total_pool)).setText(GoldNoteMarketActivity.formatBkc(game.totalPool) + " BKC");
            long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(game.deadlineSec, System.currentTimeMillis());
            GoldMarketStatusStyle status = GoldMarketStatusStyle.forMarket(game.isResolved, game.isRefunded, remaining);
            TextView tvStatus = card.findViewById(R.id.tv_market_status);
            tvStatus.setText(status.label);
            tvStatus.setTextColor(status.textColor);
            tvStatus.setBackground(makeRoundedBackground(status.backgroundColor, 999));
            card.setBackgroundResource(R.drawable.bg_gold_market_card);
            ((TextView) card.findViewById(R.id.tv_deadline)).setText(GoldNoteMarketActivity.formatRemainingTime(remaining));

            if (game.virtualReserves != null && game.virtualReserves.size() >= 2) {
                BigInteger res0 = game.virtualReserves.get(0);
                BigInteger res1 = game.virtualReserves.get(1);
                BigInteger total = res0.add(res1);
                if (total.compareTo(BigInteger.ZERO) > 0) {
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
                    ((TextView) card.findViewById(R.id.tv_yes_pct)).setText(GoldMarketOptionText.shareLabel(0, yesRatio));
                    ((TextView) card.findViewById(R.id.tv_no_pct)).setText(GoldMarketOptionText.shareLabel(1, noRatio));
                }
            }
            card.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), GoldMarketDetailActivity.class);
                intent.putExtra("GAME_ID", game.id);
                intent.putExtra("CONTRACT_ADDRESS", game.contractAddress);
                startActivity(intent);
            });
            marketListContainer.addView(card);
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

}
