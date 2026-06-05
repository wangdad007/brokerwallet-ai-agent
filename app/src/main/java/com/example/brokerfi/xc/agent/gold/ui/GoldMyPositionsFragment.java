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
import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class GoldMyPositionsFragment extends Fragment {
    private GoldMarketRepository repository;
    private final List<GoldMarketRepository.GameModel> myPositions = new ArrayList<>();
    private String currentPrivateKey;

    private TextView tvTotalBalance, tvTotalPnl;
    private LinearLayout positionsContainer;
    private SwipeRefreshLayout swipeRefresh;

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
        myPositions.clear();
        positionsContainer.removeAllViews();
        repository.getGameCount(new GoldMarketRepository.DataCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                if (count == null || count <= 0) {
                    swipeRefresh.setRefreshing(false);
                    updateSummary();
                    return;
                }
                checkAllGames(count);
            }

            @Override
            public void onError(String error) {
                swipeRefresh.setRefreshing(false);
                Toast.makeText(requireContext(), "获取持仓失败: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAllGames(int count) {
        for (int i = 1; i <= count; i++) {
            repository.getGameInfo(i, new GoldMarketRepository.DataCallback<GoldMarketRepository.GameModel>() {
                @Override
                public void onSuccess(GoldMarketRepository.GameModel model) {
                    if (hasUserShares(model)) {
                        myPositions.add(model);
                    }
                    renderPositions();
                    updateSummary();
                    swipeRefresh.setRefreshing(false);
                }

                @Override
                public void onError(String error) {
                }
            });
        }
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
            ((TextView) card.findViewById(R.id.tv_position_title)).setText("博弈池 #" + game.id);
            
            int sideIndex = -1;
            for (int i = 0; i < game.myShares.size(); i++) {
                if (game.myShares.get(i).compareTo(BigInteger.ZERO) > 0) {
                    sideIndex = i;
                    break;
                }
            }
            
            if (sideIndex != -1) {
                String sideName = game.optionNames.get(sideIndex);
                ((TextView) card.findViewById(R.id.tv_position_side)).setText(sideName);
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
        tvTotalBalance.setText("持仓中: " + myPositions.size() + " 个池子");
        tvTotalPnl.setText("即将支持实时估值");
    }
}
