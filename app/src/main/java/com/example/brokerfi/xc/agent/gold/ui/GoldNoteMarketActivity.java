package com.example.brokerfi.xc.agent.gold.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.StorageUtil;
import com.example.brokerfi.xc.agent.DeepSeekClient;
import com.example.brokerfi.xc.agent.gold.logic.GoldAdvisoryManager;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;

public class GoldNoteMarketActivity extends AppCompatActivity {
    private static final BigDecimal DISPLAY_TOKEN_UNIT = new BigDecimal("1000000000000000000");
    private static final BigDecimal MIN_DISPLAY_SHARE = new BigDecimal("0.000001");

    private TextView tvGoldPrice, tvGoldChange, tvGoldQuoteMeta;
    private GoldAdvisoryManager.Advisory latestMarketQuote;
    private boolean destroyed = false;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable priceRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            loadGoldPrice();
            timerHandler.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gold_note_market);
        destroyed = false;
        DeepSeekClient.init(this);

        String pk = StorageUtil.getCurrentPrivatekey(this);
        if (pk == null || pk.isEmpty()) {
            Toast.makeText(this, "请先创建或导入钱包", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadGoldPrice();
        timerHandler.post(priceRefreshRunnable);
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
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new GoldMarketListFragment();
                    case 1: return new GoldMyPositionsFragment();
                    case 2: return new GoldCreatePoolFragment();
                    default: return new GoldMarketListFragment();
                }
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText("博弈市场"); break;
                case 1: tab.setText("个人持仓"); break;
                case 2: tab.setText("创建博弈"); break;
            }
        }).attach();
    }

    private void loadGoldPrice() {
        GoldAdvisoryManager.fetchPrice(new GoldAdvisoryManager.AdvisoryCallback() {
            @Override
            public void onSuccess(GoldAdvisoryManager.Advisory quote) {
                if (destroyed) return;
                latestMarketQuote = quote;
                updateGoldPriceUI();
            }
            @Override
            public void onError(String error) {
                if (destroyed) return;
                tvGoldPrice.setText("XAU $---.--/oz");
                tvGoldChange.setText("--.--%");
            }
        });
    }

    private void updateGoldPriceUI() {
        if (latestMarketQuote == null) return;
        tvGoldPrice.setText(String.format(Locale.getDefault(), "XAU $%,.2f/oz", latestMarketQuote.priceUsd));
        tvGoldChange.setText(String.format(Locale.getDefault(), "%+.2f%%", latestMarketQuote.change24h));
        tvGoldQuoteMeta.setText("来源 " + latestMarketQuote.quoteSource + " · " + latestMarketQuote.quoteUpdatedAt);
    }

    // Helper methods for formatting
    public static String formatShareAmount(BigInteger value) {
        if (value == null || value.compareTo(BigInteger.ZERO) <= 0) return "0";
        BigDecimal shares = new BigDecimal(value).divide(DISPLAY_TOKEN_UNIT, 18, RoundingMode.DOWN);
        if (shares.compareTo(BigDecimal.ZERO) > 0 && shares.compareTo(MIN_DISPLAY_SHARE) < 0) {
            return "<0.000001";
        }
        return shares.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    public static String formatBkc(BigInteger value) {
        if (value == null) return "0.00";
        BigDecimal bkc = new BigDecimal(value).divide(DISPLAY_TOKEN_UNIT, 2, RoundingMode.HALF_UP);
        return String.format(Locale.getDefault(), "%,.2f", bkc.doubleValue());
    }

    public static long remainingSecondsUntilDeadline(long rawDeadline, long nowMillis) {
        if (rawDeadline <= 0) return 0;
        if (rawDeadline > 10_000_000_000L) { // ms
            return Math.max(0, (rawDeadline - nowMillis + 999) / 1000);
        }
        return Math.max(0, rawDeadline - nowMillis / 1000);
    }
}
