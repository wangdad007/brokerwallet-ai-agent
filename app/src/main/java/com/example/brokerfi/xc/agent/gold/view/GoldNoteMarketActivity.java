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
    private ViewPager2 viewPager;
    private String pendingCreateDraft = "";
    private TextView tvGoldPrice, tvGoldChange;
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
        if (currentPrivateKey == null) { Toast.makeText(this, "请先登录钱包账户", Toast.LENGTH_SHORT).show(); finish(); return; }
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
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull @Override public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new GoldMarketListFragment();
                    case 1: return new GoldMyPositionsFragment();
                    case 2: return GoldCreatePoolFragment.newInstance(pendingCreateDraft);
                    case 3: return new AIChatFragment();
                    default: return new GoldMarketListFragment();
                }
            }
            @Override public int getItemCount() { return 4; }
        });
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("博弈市场"); break;
                case 1: tab.setText("我的"); break;
                case 2: tab.setText("创建博弈"); break;
                case 3: tab.setText("AI投研"); break;
            }
        }).attach();
    }

    /** Opens the existing market-creation flow with a user-approved natural-language draft. */
    public void openCreateDraft(String draft) {
        pendingCreateDraft = draft == null ? "" : draft.trim();
        if (viewPager == null) return;
        viewPager.setCurrentItem(2, true);
        // FragmentStateAdapter creates an off-screen page asynchronously on some devices.
        // Retry after the tab animation and also avoid depending on its internal f2 tag alone.
        viewPager.postDelayed(() -> {
            Fragment current = getSupportFragmentManager().findFragmentByTag("f2");
            if (!(current instanceof GoldCreatePoolFragment)) {
                for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                    if (fragment instanceof GoldCreatePoolFragment) {
                        current = fragment;
                        break;
                    }
                }
            }
            if (current instanceof GoldCreatePoolFragment) {
                ((GoldCreatePoolFragment) current).applyAiDraft(pendingCreateDraft);
            }
        }, 260L);
    }

    private void updateGoldPriceUI(GoldAdvisoryManager.Advisory quote) {
        if (quote == null || quote.priceUsd <= 0) {
            return;
        }
        hasValidQuote = true;
        tvGoldPrice.setText(String.format(Locale.getDefault(), "XAU $%,.2f/oz", quote.priceUsd));
        tvGoldChange.setText(GoldQuotePresenter.dailyChange(
                quote.change24h, quote.changeAvailable));
        tvGoldChange.setTextColor(!quote.changeAvailable ? 0xFF64748B
                : quote.change24h > 0 ? 0xFF059669
                : quote.change24h < 0 ? 0xFFE11D48 : 0xFF64748B);
    }

    public static String formatShareAmount(BigInteger value) {
        if (value == null || value.compareTo(BigInteger.ZERO) <= 0) return "0";
        BigDecimal shares = new BigDecimal(value).divide(DISPLAY_TOKEN_UNIT, 18, RoundingMode.DOWN);
        if (shares.compareTo(BigDecimal.ZERO) > 0 && shares.compareTo(new BigDecimal("0.000001")) < 0) return "<0.000001";
        return shares.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static String formatRemainingTime(long rem) {
        if (rem < 0) return "正在同步截止时间";
        if (rem == 0) return "已截止";
        long days = rem / 86400;
        long hours = (rem % 86400) / 3600;
        long minutes = (rem % 3600) / 60;
        if (days > 0) {
            return hours > 0
                    ? String.format(Locale.US, "距结束 %d天%d小时", days, hours)
                    : String.format(Locale.US, "距结束 %d天", days);
        }
        if (hours > 0) {
            return minutes > 0
                    ? String.format(Locale.US, "距结束 %d小时%d分钟", hours, minutes)
                    : String.format(Locale.US, "距结束 %d小时", hours);
        }
        if (minutes > 0) {
            return String.format(Locale.US, "距结束 %d分钟", minutes);
        }
        return String.format(Locale.US, "距结束 %d秒", rem);
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
        if (remainingSeconds < 0) return "同步中";
        return remainingSeconds > 0 ? "运行中" : "已截止";
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
