package com.example.brokerfi.xc.agent.gold.view;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.example.brokerfi.R;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldLimitOrderPolicy;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldLiquiditySimulation;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketOptionText;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldSellSimulation;
import com.example.brokerfi.xc.agent.gold.model.logic.GoldTradeSimulation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Locale;

/** Reusable AMM trade ticket: inline on market details, modal on position details. */
public final class GoldTradeDialog {
    public enum Side { BUY, SELL, STAKE, UNSTAKE }
    private enum OrderType { MARKET, LIMIT }

    public interface Listener {
        void onBuy(int optionId, BigInteger amountWei);
        void onSell(int optionId, BigInteger shareAmountWei,
                    BigInteger minimumAmountOutWei, BigInteger quotedAmountOutWei);
        void onAddLiquidity(BigInteger amountWei, BigInteger minimumLiquiditySharesWei,
                            BigInteger quotedLiquiditySharesWei);
        void onRemoveLiquidity(BigInteger liquiditySharesWei,
                               BigInteger minimumAmountOutWei,
                               BigInteger quotedAmountOutWei);
    }

    private final Activity activity;
    private GoldMarketRepository.GameModel game;
    private final Listener listener;
    private final Dialog dialog;
    private final TextView tabBuy;
    private final TextView tabSell;
    private final TextView tabStake;
    private final TextView tabUnstake;
    private final TextView tabOrderType;
    private final View indicatorBuy;
    private final View indicatorSell;
    private final View indicatorStake;
    private final View indicatorUnstake;
    private final TextView optionYes;
    private final TextView optionNo;
    private final View limitLayout;
    private final View quoteLayout;
    private final TextView limitHint;
    private final EditText limitInput;
    private final TextView inputLabel;
    private final TextView holdingHint;
    private final EditText amountInput;
    private final TextView quick1;
    private final TextView quick2;
    private final TextView quick3;
    private final TextView quick4;
    private final TextView receiveLabel;
    private final TextView receiveValue;
    private final TextView priceValue;
    private final TextView impactValue;
    private final TextView orderNote;
    private final TextView errorValue;
    private final Button confirmButton;

    private Side side;
    private OrderType orderType = OrderType.MARKET;
    private int optionId;
    private boolean quoteValid;
    private BigInteger buyAmountWei = BigInteger.ZERO;
    private BigInteger sellSharesWei = BigInteger.ZERO;
    private BigInteger quotedSellAmountWei = BigInteger.ZERO;
    private BigInteger minimumSellAmountWei = BigInteger.ZERO;
    private BigInteger stakeAmountWei = BigInteger.ZERO;
    private BigInteger quotedLiquiditySharesWei = BigInteger.ZERO;
    private BigInteger minimumLiquiditySharesWei = BigInteger.ZERO;
    private BigInteger unstakeLiquiditySharesWei = BigInteger.ZERO;
    private BigInteger quotedLiquidityAmountOutWei = BigInteger.ZERO;
    private BigInteger minimumLiquidityAmountOutWei = BigInteger.ZERO;

    private GoldTradeDialog(Activity activity, View root, Dialog dialog,
                            GoldMarketRepository.GameModel game, Side initialSide,
                            int initialOption, Listener listener) {
        this.activity = activity;
        this.dialog = dialog;
        this.game = game;
        this.listener = listener;
        this.side = initialSide == null ? Side.BUY : initialSide;
        this.optionId = initialOption == 1 ? 1 : 0;

        tabBuy = root.findViewById(R.id.tab_trade_buy);
        tabSell = root.findViewById(R.id.tab_trade_sell);
        tabStake = root.findViewById(R.id.tab_trade_stake);
        tabUnstake = root.findViewById(R.id.tab_trade_unstake);
        tabOrderType = root.findViewById(R.id.tab_order_type);
        indicatorBuy = root.findViewById(R.id.indicator_trade_buy);
        indicatorSell = root.findViewById(R.id.indicator_trade_sell);
        indicatorStake = root.findViewById(R.id.indicator_trade_stake);
        indicatorUnstake = root.findViewById(R.id.indicator_trade_unstake);
        optionYes = root.findViewById(R.id.btn_trade_yes);
        optionNo = root.findViewById(R.id.btn_trade_no);
        limitLayout = root.findViewById(R.id.layout_trade_limit);
        quoteLayout = root.findViewById(R.id.layout_trade_quote);
        limitHint = root.findViewById(R.id.tv_trade_limit_hint);
        limitInput = root.findViewById(R.id.et_trade_limit);
        inputLabel = root.findViewById(R.id.tv_trade_input_label);
        holdingHint = root.findViewById(R.id.tv_trade_holding);
        amountInput = root.findViewById(R.id.et_trade_amount);
        quick1 = root.findViewById(R.id.btn_trade_quick_1);
        quick2 = root.findViewById(R.id.btn_trade_quick_2);
        quick3 = root.findViewById(R.id.btn_trade_quick_3);
        quick4 = root.findViewById(R.id.btn_trade_quick_4);
        receiveLabel = root.findViewById(R.id.tv_trade_receive_label);
        receiveValue = root.findViewById(R.id.tv_trade_receive);
        priceValue = root.findViewById(R.id.tv_trade_price);
        impactValue = root.findViewById(R.id.tv_trade_impact);
        orderNote = root.findViewById(R.id.tv_trade_order_note);
        errorValue = root.findViewById(R.id.tv_trade_error);
        confirmButton = root.findViewById(R.id.btn_trade_confirm);

        tabBuy.setOnClickListener(v -> setSide(Side.BUY));
        tabSell.setOnClickListener(v -> setSide(Side.SELL));
        tabStake.setOnClickListener(v -> setSide(Side.STAKE));
        tabUnstake.setOnClickListener(v -> setSide(Side.UNSTAKE));
        tabOrderType.setOnClickListener(v -> showOrderTypeMenu());
        optionYes.setOnClickListener(v -> setOption(0));
        optionNo.setOnClickListener(v -> setOption(1));
        root.findViewById(R.id.btn_limit_minus).setOnClickListener(v -> adjustLimit(-0.01));
        root.findViewById(R.id.btn_limit_plus).setOnClickListener(v -> adjustLimit(0.01));

        amountInput.addTextChangedListener(simpleWatcher(() -> {
            updateAmountTextSize();
            clearQuickSelection();
            renderQuote();
        }));
        limitInput.addTextChangedListener(simpleWatcher(this::renderQuote));
        confirmButton.setOnClickListener(v -> submit());

        if (side == Side.SELL && heldShares(optionId).signum() <= 0) {
            if (heldShares(1 - optionId).signum() > 0) optionId = 1 - optionId;
        }
        bindQuickButtons();
        setLimitToCurrentPrice();
        render();
    }

    public static void show(Activity activity, GoldMarketRepository.GameModel game,
                            Side initialSide, int initialOption, Listener listener) {
        if (activity == null || activity.isFinishing() || game == null || listener == null) return;
        View root = activity.getLayoutInflater().inflate(R.layout.dialog_gold_trade, null, false);
        Dialog dialog = new Dialog(activity);
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        GoldTradeDialog ticket = new GoldTradeDialog(
                activity, root, dialog, game, initialSide, initialOption, listener);
        ticket.showInternal();
    }

    public static GoldTradeDialog attach(Activity activity, View root,
                                         GoldMarketRepository.GameModel game,
                                         Side initialSide, int initialOption,
                                         Listener listener) {
        if (activity == null || root == null || game == null || listener == null) return null;
        return new GoldTradeDialog(
                activity, root, null, game, initialSide, initialOption, listener);
    }

    private void showInternal() {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.48f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.setStatusBarColor(Color.TRANSPARENT);
            }
        }
    }

    public void updateGame(GoldMarketRepository.GameModel updatedGame) {
        if (updatedGame == null) return;
        game = updatedGame;
        if (side == Side.SELL && heldShares(optionId).signum() <= 0
                && heldShares(1 - optionId).signum() > 0) {
            optionId = 1 - optionId;
        }
        render();
    }

    public void select(Side selectedSide, int selectedOption) {
        side = selectedSide == null ? Side.BUY : selectedSide;
        optionId = selectedOption == 1 ? 1 : 0;
        amountInput.setText("");
        if (side == Side.SELL && heldShares(optionId).signum() <= 0
                && heldShares(1 - optionId).signum() > 0) {
            optionId = 1 - optionId;
        }
        bindQuickButtons();
        setLimitToCurrentPrice();
        render();
    }

    private void setSide(Side value) {
        if (side == value) return;
        side = value;
        amountInput.setText("");
        if (side == Side.SELL && heldShares(optionId).signum() <= 0
                && heldShares(1 - optionId).signum() > 0) {
            optionId = 1 - optionId;
        }
        bindQuickButtons();
        render();
    }

    private void setOrderType(OrderType value) {
        if (orderType == value) return;
        orderType = value;
        if (orderType == OrderType.LIMIT) setLimitToCurrentPrice();
        render();
    }

    private void showOrderTypeMenu() {
        if (!isOutcomeTrade()) return;
        PopupMenu menu = new PopupMenu(activity, tabOrderType);
        menu.getMenu().add("市价单");
        menu.getMenu().add("限价单");
        menu.setOnMenuItemClickListener(item -> {
            setOrderType("限价单".contentEquals(item.getTitle())
                    ? OrderType.LIMIT : OrderType.MARKET);
            return true;
        });
        menu.show();
    }

    private void setOption(int value) {
        if (!isOutcomeTrade()) return;
        if (optionId == value) return;
        optionId = value;
        amountInput.setText("");
        setLimitToCurrentPrice();
        render();
    }

    private void render() {
        boolean buying = side == Side.BUY;
        boolean selling = side == Side.SELL;
        boolean staking = side == Side.STAKE;
        boolean unstaking = side == Side.UNSTAKE;
        tabBuy.setTextColor(buying ? 0xFF0F172A : 0xFF94A3B8);
        tabSell.setTextColor(selling ? 0xFF0F172A : 0xFF94A3B8);
        tabStake.setTextColor(staking ? 0xFF0F172A : 0xFF94A3B8);
        tabUnstake.setTextColor(unstaking ? 0xFF0F172A : 0xFF94A3B8);
        indicatorBuy.setVisibility(buying ? View.VISIBLE : View.INVISIBLE);
        indicatorSell.setVisibility(selling ? View.VISIBLE : View.INVISIBLE);
        indicatorStake.setVisibility(staking ? View.VISIBLE : View.INVISIBLE);
        indicatorUnstake.setVisibility(unstaking ? View.VISIBLE : View.INVISIBLE);
        // Keep the order-type column in the layout for LP operations. Making
        // it invisible (instead of gone) prevents all four action tabs from
        // changing width and jumping when users switch to stake/unstake.
        tabOrderType.setVisibility(isOutcomeTrade() ? View.VISIBLE : View.INVISIBLE);
        tabOrderType.setText(orderType == OrderType.MARKET ? "市价 ▾" : "限价 ▾");
        styleOptions();
        limitLayout.setVisibility(isOutcomeTrade() && orderType == OrderType.LIMIT
                ? View.VISIBLE : View.GONE);

        String optionName = optionName(optionId);
        if (buying) {
            inputLabel.setText("买入金额");
            holdingHint.setText("输入希望投入的 BKC");
            amountInput.setHint("0 BKC");
            receiveLabel.setText("预计获得");
            limitHint.setText("每份最高买入价");
            confirmButton.setText("确认买入 " + optionName);
        } else if (selling) {
            inputLabel.setText("卖出份额");
            holdingHint.setText("可卖 "
                    + GoldNoteMarketActivity.formatShareAmount(heldShares(optionId)) + " 份额");
            amountInput.setHint("0 份额");
            receiveLabel.setText("预计到账");
            limitHint.setText("每份最低卖出价");
            confirmButton.setText("确认卖出 " + optionName);
        } else if (staking) {
            inputLabel.setText("质押金额");
            holdingHint.setText("注入 BKC，按池深度铸造 LP 份额");
            amountInput.setHint("0 BKC");
            receiveLabel.setText("预计获得");
            confirmButton.setText("确认质押");
        } else {
            inputLabel.setText("取回 LP");
            BigInteger removable = maximumRemovableLP();
            holdingHint.setText(removable.signum() == 0 && game.isCreator
                    && !game.isResolved && !game.isRefunded
                    ? "创建者初始 LP 已锁定，结算后可取回"
                    : "可取回 "
                        + GoldNoteMarketActivity.formatShareAmount(removable)
                        + " LP");
            amountInput.setHint("0 LP");
            receiveLabel.setText("预计到账");
            confirmButton.setText("确认取回质押");
        }
        confirmButton.setBackgroundResource(R.drawable.bg_trade_primary);
        renderOrderNote();
        renderQuote();
    }

    private void styleOptions() {
        if (!isOutcomeTrade()) {
            BigInteger totalLP = nonNegative(game.totalLiquidityShares);
            BigInteger myLP = nonNegative(game.myLiquidityShares);
            optionYes.setText("池内总 LP\n"
                    + GoldNoteMarketActivity.formatShareAmount(totalLP));
            optionNo.setText("我的 LP\n"
                    + GoldNoteMarketActivity.formatShareAmount(myLP)
                    + " · " + formatPercentage(myLP, totalLP));
            optionYes.setBackgroundResource(R.drawable.bg_trade_segment_idle);
            optionNo.setBackgroundResource(R.drawable.bg_trade_segment_idle);
            optionYes.setTextColor(0xFF334155);
            optionNo.setTextColor(0xFF334155);
            optionYes.setAlpha(1f);
            optionNo.setAlpha(1f);
            return;
        }
        BigDecimal yes = currentPrice(0);
        BigDecimal no = currentPrice(1);
        optionYes.setText(String.format(Locale.getDefault(),
                "%s  %s BKC", optionName(0), formatPrice(yes)));
        optionNo.setText(String.format(Locale.getDefault(),
                "%s  %s BKC", optionName(1), formatPrice(no)));
        styleOption(optionYes, optionId == 0, true);
        styleOption(optionNo, optionId == 1, false);
    }

    private void styleOption(TextView view, boolean selected, boolean yes) {
        if (selected) {
            view.setBackgroundResource(yes
                    ? R.drawable.bg_trade_option_yes : R.drawable.bg_trade_option_no);
            view.setTextColor(Color.WHITE);
            view.setAlpha(1f);
        } else {
            view.setBackgroundResource(R.drawable.bg_trade_segment_idle);
            view.setTextColor(0xFF64748B);
            view.setAlpha(0.88f);
        }
    }

    private void renderOrderNote() {
        if (side == Side.STAKE) {
            orderNote.setText("流动性按当前储备比例进入池中，不会把市场概率强行拉回 50%；"
                    + "LP 按份额获得 1% 交易费。");
            return;
        }
        if (side == Side.UNSTAKE) {
            orderNote.setText(game.isResolved
                    ? "结算后按 LP 占比领取胜方储备与累计交易费。"
                    : "运行中取回会返还可合并的 BKC；多出的单边 YES/NO 份额会一并返还。");
            return;
        }
        if (orderType == OrderType.MARKET) {
            orderNote.setText(side == Side.SELL
                    ? "市价卖出按当前 AMM 报价立即执行，并使用 1% 链上最低到账保护。"
                    : "市价买入按当前 AMM 报价提交，请在交易前确认平均成交价与价格影响。");
            return;
        }
        orderNote.setText(side == Side.SELL
                ? "限价保护单为立即成交或取消；最低卖出价会写入链上，报价不满足时交易回滚。"
                : "限价保护单为立即成交或取消，不会挂单等待；买入限价目前在提交前校验。");
    }

    private void renderQuote() {
        quoteValid = false;
        buyAmountWei = BigInteger.ZERO;
        sellSharesWei = BigInteger.ZERO;
        quotedSellAmountWei = BigInteger.ZERO;
        minimumSellAmountWei = BigInteger.ZERO;
        stakeAmountWei = BigInteger.ZERO;
        quotedLiquiditySharesWei = BigInteger.ZERO;
        minimumLiquiditySharesWei = BigInteger.ZERO;
        unstakeLiquiditySharesWei = BigInteger.ZERO;
        quotedLiquidityAmountOutWei = BigInteger.ZERO;
        minimumLiquidityAmountOutWei = BigInteger.ZERO;
        errorValue.setVisibility(View.GONE);
        quoteLayout.setVisibility(View.GONE);
        confirmButton.setEnabled(false);
        confirmButton.setAlpha(0.45f);

        long remaining = GoldNoteMarketActivity.remainingSecondsUntilDeadline(
                game.deadlineSec, System.currentTimeMillis());
        if (isOutcomeTrade() && (remaining <= 0 || game.isResolved || game.isRefunded)) {
            showError("博弈池已经截止或结算，当前不可交易");
            clearQuote();
            return;
        }

        BigInteger inputWei = GoldMarketRepository.parseTokenAmountToWei(
                amountInput.getText().toString().trim());
        if (inputWei == null || inputWei.signum() <= 0) {
            clearQuote();
            return;
        }

        BigDecimal limitPrice = parseLimitPrice();
        if (isOutcomeTrade() && orderType == OrderType.LIMIT && limitPrice == null) {
            showError("请输入 0～1 BKC 之间的有效限价");
            clearQuote();
            return;
        }

        if (side == Side.BUY) {
            GoldTradeSimulation.Result result = GoldTradeSimulation.simulate(game, optionId, inputWei);
            if (!result.valid) {
                showError(result.error);
                clearQuote();
                return;
            }
            buyAmountWei = inputWei;
            receiveValue.setText(GoldNoteMarketActivity.formatShareAmount(result.sharesOutWei)
                    + " 份额");
            priceValue.setText("平均成交价 "
                    + formatPrice(result.averagePriceBkcPerShare) + " BKC/份额");
            impactValue.setText(probabilityImpact(
                    result.beforeYesProbability, result.afterYesProbability));
            if (orderType == OrderType.LIMIT
                    && !GoldLimitOrderPolicy.buyQuoteMatches(
                    result.averagePriceBkcPerShare, limitPrice)) {
                showError("当前平均买入价高于你的限价，订单不会提交");
                quoteLayout.setVisibility(View.VISIBLE);
                return;
            }
        } else if (side == Side.SELL) {
            GoldSellSimulation.Result result = GoldSellSimulation.simulate(
                    game, optionId, inputWei, GoldSellSimulation.DEFAULT_SLIPPAGE_BPS);
            if (!result.valid) {
                showError(result.error);
                clearQuote();
                return;
            }
            sellSharesWei = result.shareAmountWei;
            quotedSellAmountWei = result.amountOutWei;
            minimumSellAmountWei = result.minAmountOutWei;
            receiveValue.setText(GoldNoteMarketActivity.formatBkc(result.amountOutWei) + " BKC");
            priceValue.setText("平均成交价 "
                    + formatPrice(result.averagePriceBkcPerShare) + " BKC/份额");
            impactValue.setText(probabilityImpact(
                    result.beforeYesProbability, result.afterYesProbability));
            if (orderType == OrderType.LIMIT) {
                if (!GoldLimitOrderPolicy.sellQuoteMatches(
                        result.averagePriceBkcPerShare, limitPrice)) {
                    showError("当前平均卖出价低于你的限价，订单不会提交");
                    quoteLayout.setVisibility(View.VISIBLE);
                    return;
                }
                minimumSellAmountWei = GoldLimitOrderPolicy.sellMinimumAmountOut(
                        result.shareAmountWei, limitPrice, result.minAmountOutWei);
            }
        } else if (side == Side.STAKE) {
            GoldLiquiditySimulation.AddResult result =
                    GoldLiquiditySimulation.simulateAdd(
                            game, inputWei, System.currentTimeMillis() / 1000L);
            if (!result.valid) {
                showError(result.error);
                clearQuote();
                return;
            }
            stakeAmountWei = result.amountInWei;
            quotedLiquiditySharesWei = result.liquiditySharesOutWei;
            minimumLiquiditySharesWei = result.minimumLiquiditySharesOutWei;
            receiveValue.setText(
                    GoldNoteMarketActivity.formatShareAmount(result.liquiditySharesOutWei)
                            + " LP");
            priceValue.setText("质押后池占比 "
                    + formatPercent(result.poolShareAfter) + "%");
            impactValue.setText(liquidityReturnSummary(
                    result.returnedYesWei, result.returnedNoWei));
        } else {
            GoldLiquiditySimulation.RemoveResult result =
                    GoldLiquiditySimulation.simulateRemove(
                            game, inputWei, System.currentTimeMillis() / 1000L);
            if (!result.valid) {
                showError(result.error);
                clearQuote();
                return;
            }
            unstakeLiquiditySharesWei = result.liquiditySharesInWei;
            quotedLiquidityAmountOutWei = result.totalAmountOutWei;
            minimumLiquidityAmountOutWei = result.minimumAmountOutWei;
            receiveValue.setText(
                    GoldNoteMarketActivity.formatBkc(result.totalAmountOutWei) + " BKC");
            priceValue.setText("储备 "
                    + GoldNoteMarketActivity.formatBkc(result.collateralOutWei)
                    + " BKC · 交易费 "
                    + GoldNoteMarketActivity.formatBkc(result.feeOutWei) + " BKC");
            impactValue.setText(liquidityReturnSummary(
                    result.returnedYesWei, result.returnedNoWei));
        }

        quoteValid = true;
        quoteLayout.setVisibility(View.VISIBLE);
        confirmButton.setEnabled(true);
        confirmButton.setAlpha(1f);
    }

    private void clearQuote() {
        if (side == Side.BUY) {
            receiveValue.setText("-- 份额");
            priceValue.setText("平均成交价 --");
            impactValue.setText("成交后市场概率 --");
        } else if (side == Side.SELL) {
            receiveValue.setText("-- BKC");
            priceValue.setText("平均成交价 --");
            impactValue.setText("成交后市场概率 --");
        } else if (side == Side.STAKE) {
            receiveValue.setText("-- LP");
            priceValue.setText("质押后池占比 --");
            impactValue.setText("返还单边份额 --");
        } else {
            receiveValue.setText("-- BKC");
            priceValue.setText("储备与交易费 --");
            impactValue.setText("返还单边份额 --");
        }
    }

    private void showError(String message) {
        errorValue.setText(message);
        errorValue.setVisibility(View.VISIBLE);
    }

    private void submit() {
        renderQuote();
        if (!quoteValid) return;
        if (dialog != null) dialog.dismiss();
        if (side == Side.BUY) {
            listener.onBuy(optionId, buyAmountWei);
        } else if (side == Side.SELL) {
            listener.onSell(optionId, sellSharesWei,
                    minimumSellAmountWei, quotedSellAmountWei);
        } else if (side == Side.STAKE) {
            listener.onAddLiquidity(stakeAmountWei, minimumLiquiditySharesWei,
                    quotedLiquiditySharesWei);
        } else {
            listener.onRemoveLiquidity(unstakeLiquiditySharesWei,
                    minimumLiquidityAmountOutWei, quotedLiquidityAmountOutWei);
        }
    }

    private void bindQuickButtons() {
        clearQuickSelection();
        if (side == Side.BUY || side == Side.STAKE) {
            bindQuick(quick1, "1", () -> amountInput.setText("1"));
            bindQuick(quick2, "5", () -> amountInput.setText("5"));
            bindQuick(quick3, "10", () -> amountInput.setText("10"));
            bindQuick(quick4, "25", () -> amountInput.setText("25"));
            return;
        }
        if (side == Side.SELL) {
            bindQuick(quick1, "25%", () -> setHoldingFraction(25));
            bindQuick(quick2, "50%", () -> setHoldingFraction(50));
            bindQuick(quick3, "75%", () -> setHoldingFraction(75));
            bindQuick(quick4, "全部", () -> setHoldingFraction(100));
        } else {
            bindQuick(quick1, "25%", () -> setLiquidityFraction(25));
            bindQuick(quick2, "50%", () -> setLiquidityFraction(50));
            bindQuick(quick3, "75%", () -> setLiquidityFraction(75));
            bindQuick(quick4, "全部", () -> setLiquidityFraction(100));
        }
    }

    private void bindQuick(TextView view, String label, Runnable action) {
        view.setText(label);
        view.setOnClickListener(v -> {
            action.run();
            selectQuick(view);
        });
    }

    private void selectQuick(TextView selected) {
        for (TextView view : new TextView[] {quick1, quick2, quick3, quick4}) {
            boolean active = view == selected;
            view.setBackgroundResource(active
                    ? R.drawable.bg_trade_quick_selected
                    : R.drawable.bg_trade_segment_idle);
            view.setTextColor(active ? 0xFF2563EB : 0xFF64748B);
        }
    }

    private void clearQuickSelection() {
        selectQuick(null);
    }

    private void updateAmountTextSize() {
        int length = amountInput.getText() == null ? 0 : amountInput.getText().length();
        if (length <= 8) {
            amountInput.setTextSize(34);
        } else if (length <= 12) {
            amountInput.setTextSize(28);
        } else if (length <= 16) {
            amountInput.setTextSize(22);
        } else {
            amountInput.setTextSize(17);
        }
    }

    private void setHoldingFraction(int percent) {
        BigInteger held = heldShares(optionId);
        BigInteger shares = held.multiply(BigInteger.valueOf(percent))
                .divide(BigInteger.valueOf(100));
        amountInput.setText(formatTokenInput(shares));
    }

    private void setLiquidityFraction(int percent) {
        BigInteger removable = maximumRemovableLP();
        BigInteger shares = removable.multiply(BigInteger.valueOf(percent))
                .divide(BigInteger.valueOf(100));
        amountInput.setText(formatTokenInput(shares));
    }

    private void adjustLimit(double delta) {
        BigDecimal value = parseLimitPrice();
        if (value == null) value = currentPrice(optionId);
        value = value.add(BigDecimal.valueOf(delta));
        if (value.compareTo(new BigDecimal("0.001")) < 0) value = new BigDecimal("0.001");
        if (value.compareTo(BigDecimal.ONE) > 0) value = BigDecimal.ONE;
        limitInput.setText(value.setScale(3, RoundingMode.HALF_UP).toPlainString());
    }

    private void setLimitToCurrentPrice() {
        limitInput.setText(currentPrice(optionId)
                .setScale(3, RoundingMode.HALF_UP).toPlainString());
    }

    private BigDecimal parseLimitPrice() {
        try {
            BigDecimal value = new BigDecimal(limitInput.getText().toString().trim());
            if (value.signum() <= 0 || value.compareTo(BigDecimal.ONE) > 0) return null;
            return value;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BigDecimal currentPrice(int option) {
        if (game.virtualReserves == null || game.virtualReserves.size() < 2
                || game.virtualReserves.get(0) == null || game.virtualReserves.get(1) == null) {
            return new BigDecimal("0.5");
        }
        BigInteger reserveNo = game.virtualReserves.get(0);
        BigInteger reserveYes = game.virtualReserves.get(1);
        BigInteger total = reserveNo.add(reserveYes);
        if (total.signum() <= 0) return new BigDecimal("0.5");
        BigDecimal yes = new BigDecimal(reserveNo)
                .divide(new BigDecimal(total), 8, RoundingMode.HALF_UP);
        return option == 0 ? yes : BigDecimal.ONE.subtract(yes);
    }

    private BigInteger heldShares(int option) {
        if (game.myShares == null || game.myShares.size() <= option
                || game.myShares.get(option) == null) {
            return BigInteger.ZERO;
        }
        return game.myShares.get(option);
    }

    private boolean isOutcomeTrade() {
        return side == Side.BUY || side == Side.SELL;
    }

    private BigInteger maximumRemovableLP() {
        return GoldLiquiditySimulation.maximumRemovableLP(game);
    }

    private BigInteger nonNegative(BigInteger value) {
        return value == null || value.signum() < 0 ? BigInteger.ZERO : value;
    }

    private String formatPercentage(BigInteger numerator, BigInteger denominator) {
        if (denominator == null || denominator.signum() <= 0) return "0%";
        BigDecimal value = new BigDecimal(nonNegative(numerator))
                .multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(denominator), 2, RoundingMode.HALF_UP);
        return formatPercent(value) + "%";
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "0";
        return value.setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private String liquidityReturnSummary(BigInteger returnedYes, BigInteger returnedNo) {
        BigInteger yes = nonNegative(returnedYes);
        BigInteger no = nonNegative(returnedNo);
        if (yes.signum() == 0 && no.signum() == 0) {
            return "不产生单边份额返还";
        }
        if (yes.signum() > 0) {
            return "另返 "
                    + GoldNoteMarketActivity.formatShareAmount(yes) + " YES 份额";
        }
        return "另返 "
                + GoldNoteMarketActivity.formatShareAmount(no) + " NO 份额";
    }

    private String optionName(int option) {
        if (game.optionNames != null && game.optionNames.size() > option) {
            return GoldMarketOptionText.displayName(game.optionNames.get(option), option);
        }
        return GoldMarketOptionText.displayName(option);
    }

    private String probabilityImpact(BigDecimal before, BigDecimal after) {
        return String.format(Locale.getDefault(),
                "YES 隐含概率 %.2f%% → %.2f%%", before.doubleValue(), after.doubleValue());
    }

    private String formatPrice(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private String formatTokenInput(BigInteger wei) {
        if (wei == null || wei.signum() <= 0) return "";
        return new BigDecimal(wei)
                .divide(BigDecimal.TEN.pow(18), 18, RoundingMode.DOWN)
                .stripTrailingZeros().toPlainString();
    }

    private TextWatcher simpleWatcher(Runnable callback) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                callback.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
    }
}
