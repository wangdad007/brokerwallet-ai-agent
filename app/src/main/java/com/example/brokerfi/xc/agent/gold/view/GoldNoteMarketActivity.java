package com.example.brokerfi.xc.agent.gold.view;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.ai.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldAdvisoryManager;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldQuotePresenter;
import com.example.brokerfi.xc.agent.gold.model.data.BrokerChainClient;
import com.example.brokerfi.xc.agent.gold.viewmodel.GoldNoteMarketViewModel;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;

public class GoldNoteMarketActivity extends AppCompatActivity {
    private static final BigDecimal DISPLAY_TOKEN_UNIT = new BigDecimal("1000000000000000000");
    private GoldNoteMarketViewModel viewModel;
    private TextView tvGoldPrice, tvGoldChange, tvGoldQuoteMeta;
    private boolean hasValidQuote = false;
    private boolean destroyed = false;
    private String activeWalletAddress;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable priceRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            viewModel.loadPrice();
            timerHandler.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_note_market);
        destroyed = false;
        DeepSeekClient.init(this);
        String currentPrivateKey = StorageUtil.getCurrentPrivatekey(this);
        if (currentPrivateKey == null) { Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show(); finish(); return; }
        activeWalletAddress = BrokerChainClient.getAddress(currentPrivateKey);
        viewModel = new ViewModelProvider(this).get(GoldNoteMarketViewModel.class);
        initViews();
        observeViewModel();
        viewModel.loadPrice();
        timerHandler.post(priceRefreshRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentPrivateKey = StorageUtil.getCurrentPrivatekey(this);
        String currentWalletAddress = currentPrivateKey == null
                ? "" : BrokerChainClient.getAddress(currentPrivateKey);
        if (activeWalletAddress != null
                && !activeWalletAddress.equalsIgnoreCase(currentWalletAddress)) {
            // Fragments keep account-scoped repositories in their ViewModels.
            // Recreate them when the wallet account changes instead of reusing stale keys.
            activeWalletAddress = currentWalletAddress;
            recreate();
        }
    }

    private void observeViewModel() {
        viewModel.getQuote().observe(this, quote -> { if (quote != null) updateGoldPriceUI(quote); });
        viewModel.getError().observe(this, err -> {
            if (err != null && !hasValidQuote) {
                tvGoldPrice.setText("XAU $---.--/oz");
                tvGoldChange.setText("--.--%");
                tvGoldQuoteMeta.setText("Quote unavailable · retrying automatically");
            }
        });
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        timerHandler.removeCallbacks(priceRefreshRunnable);
        super.onDestroy();
    }

    private void initViews() {
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvGoldPrice = findViewById(R.id.tv_gold_price);
        tvGoldChange = findViewById(R.id.tv_gold_change);
        tvGoldQuoteMeta = findViewById(R.id.tv_gold_quote_meta);
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull @Override public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new GoldMarketListFragment();
                    case 1: return new GoldMyPositionsFragment();
                    case 2: return new GoldCreatePoolFragment();
                    case 3: return new AIChatFragment();
                    default: return new GoldMarketListFragment();
                }
            }
            @Override public int getItemCount() { return 4; }
        });
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("Markets"); break;
                case 1: tab.setText("Positions"); break;
                case 2: tab.setText("Create"); break;
                case 3: tab.setText("AI Research"); break;
            }
        }).attach();
    }

    private void updateGoldPriceUI(GoldAdvisoryManager.Advisory quote) {
        if (quote == null || quote.priceUsd <= 0) {
            if (!hasValidQuote) tvGoldQuoteMeta.setText("Quote unavailable · retrying automatically");
            return;
        }
        hasValidQuote = true;
        tvGoldPrice.setText(String.format(Locale.getDefault(), "XAU $%,.2f/oz", quote.priceUsd));
        tvGoldChange.setText(GoldQuotePresenter.dailyChange(
                quote.change24h, quote.changeAvailable));
        tvGoldChange.setTextColor(!quote.changeAvailable ? 0xFF64748B
                : quote.change24h > 0 ? 0xFF059669
                : quote.change24h < 0 ? 0xFFE11D48 : 0xFF64748B);
        tvGoldQuoteMeta.setText(GoldQuotePresenter.quoteMeta(
                quote.quoteSource, quote.quoteUpdatedAt, quote.quoteDelayed));
    }

    public static String formatShareAmount(BigInteger value) {
        if (value == null || value.compareTo(BigInteger.ZERO) <= 0) return "0";
        BigDecimal shares = new BigDecimal(value).divide(DISPLAY_TOKEN_UNIT, 18, RoundingMode.DOWN);
        if (shares.compareTo(BigDecimal.ZERO) > 0 && shares.compareTo(new BigDecimal("0.000001")) < 0) return "<0.000001";
        return shares.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static String formatRemainingTime(long rem) {
        if (rem < 0) return "Syncing deadline";
        if (rem == 0) return "Ended";
        long days = rem / 86400;
        long hours = (rem % 86400) / 3600;
        long minutes = (rem % 3600) / 60;
        if (days > 0) {
            return hours > 0
                    ? String.format(Locale.US, "Ends in %dd %dh", days, hours)
                    : String.format(Locale.US, "Ends in %dd", days);
        }
        if (hours > 0) {
            return minutes > 0
                    ? String.format(Locale.US, "Ends in %dh %dm", hours, minutes)
                    : String.format(Locale.US, "Ends in %dh", hours);
        }
        if (minutes > 0) {
            return String.format(Locale.US, "Ends in %dm", minutes);
        }
        return String.format(Locale.US, "Ends in %ds", rem);
    }

    public static String formatBkc(BigInteger value) {
        if (value == null) return "0.00";
        BigDecimal bkc = new BigDecimal(value).divide(DISPLAY_TOKEN_UNIT, 2, RoundingMode.HALF_UP);
        return String.format(Locale.getDefault(), "%,.2f", bkc.doubleValue());
    }

    public static long remainingSecondsUntilDeadline(long raw, long now) {
        long deadline = normalizeDeadlineMillis(raw);
        if (deadline <= 0) return -1;
        long diff = deadline - now;
        return diff > 0 ? ((diff + 999) / 1000) : 0;
    }

    public static boolean hasKnownDeadline(long raw) {
        return normalizeDeadlineMillis(raw) > 0;
    }

    public static String formatMarketStatus(long remainingSeconds) {
        if (remainingSeconds < 0) return "Syncing";
        return remainingSeconds > 0 ? "Active" : "Ended";
    }

    private static long normalizeDeadlineMillis(long raw) {
        if (raw <= 0) return -1;
        if (raw >= 10000000000L) {
            return raw;
        }
        if (raw < 1000000000L) {
            return -1;
        }
        return raw * 1000;
    }
}
