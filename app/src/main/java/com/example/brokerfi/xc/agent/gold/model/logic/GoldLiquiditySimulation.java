package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/** Deterministic preview of addLiquidity/removeLiquidity. No chain write occurs here. */
public final class GoldLiquiditySimulation {
    public static final int DEFAULT_SLIPPAGE_BPS = 100;
    private static final BigInteger BPS = BigInteger.valueOf(10_000);

    private GoldLiquiditySimulation() {
    }

    public static AddResult simulateAdd(GoldMarketRepository.GameModel game,
                                        BigInteger amountWei, long nowSec) {
        String stateError = activeLiquidityError(game, nowSec, true);
        if (stateError != null) return AddResult.invalid(stateError);
        if (amountWei == null || amountWei.signum() <= 0) {
            return AddResult.invalid("请输入大于 0 的 BKC 金额");
        }

        ReserveState state = reserveState(game);
        if (!state.valid) return AddResult.invalid(state.error);
        BigInteger totalLP = nonNegative(game.totalLiquidityShares);
        if (totalLP.signum() <= 0) {
            return AddResult.invalid("链上 LP 总份额尚未就绪");
        }

        BigInteger maxReserve = state.reserveYes.max(state.reserveNo);
        BigInteger mintedLP = amountWei.multiply(totalLP).divide(maxReserve);
        if (mintedLP.signum() <= 0) {
            return AddResult.invalid("质押金额过小，无法铸造有效 LP 份额");
        }

        BigInteger depositYes = state.reserveYes.multiply(amountWei).divide(maxReserve);
        BigInteger depositNo = state.reserveNo.multiply(amountWei).divide(maxReserve);
        BigInteger returnedYes = amountWei.subtract(depositYes);
        BigInteger returnedNo = amountWei.subtract(depositNo);
        BigInteger minLP = applySlippage(mintedLP);
        BigInteger afterMyLP = nonNegative(game.myLiquidityShares).add(mintedLP);
        BigInteger afterTotalLP = totalLP.add(mintedLP);
        BigDecimal poolShare = percentage(afterMyLP, afterTotalLP);
        return AddResult.valid(amountWei, mintedLP, minLP, returnedYes, returnedNo, poolShare);
    }

    public static RemoveResult simulateRemove(GoldMarketRepository.GameModel game,
                                              BigInteger lpAmountWei, long nowSec) {
        if (game == null) return RemoveResult.invalid("未找到对应博弈池");
        if (lpAmountWei == null || lpAmountWei.signum() <= 0) {
            return RemoveResult.invalid("请输入大于 0 的 LP 份额");
        }
        ReserveState state = reserveState(game);
        if (!state.valid) return RemoveResult.invalid(state.error);

        BigInteger totalLP = nonNegative(game.totalLiquidityShares);
        BigInteger myLP = nonNegative(game.myLiquidityShares);
        if (totalLP.signum() <= 0 || myLP.signum() <= 0) {
            return RemoveResult.invalid("当前没有可取回的 LP 份额");
        }
        if (lpAmountWei.compareTo(myLP) > 0) {
            return RemoveResult.invalid("取回数量超过当前持有的 LP 份额");
        }
        if (!game.isResolved && !game.isRefunded) {
            String stateError = activeLiquidityError(game, nowSec, false);
            if (stateError != null) return RemoveResult.invalid(stateError);
            BigInteger creatorLock = game.isCreator
                    ? nonNegative(game.creatorLockedLiquidityShares)
                    : BigInteger.ZERO;
            if (myLP.subtract(lpAmountWei).compareTo(creatorLock) < 0) {
                return RemoveResult.invalid("创建者的初始流动性将在博弈池结算后解锁");
            }
            if (lpAmountWei.compareTo(totalLP) >= 0) {
                return RemoveResult.invalid("运行中的博弈池必须保留最小流动性");
            }
        }

        BigInteger reserveYesOut = lpAmountWei.equals(totalLP)
                ? state.reserveYes : state.reserveYes.multiply(lpAmountWei).divide(totalLP);
        BigInteger reserveNoOut = lpAmountWei.equals(totalLP)
                ? state.reserveNo : state.reserveNo.multiply(lpAmountWei).divide(totalLP);
        BigInteger collateralOut;
        BigInteger returnedYes = BigInteger.ZERO;
        BigInteger returnedNo = BigInteger.ZERO;
        if (game.isResolved) {
            collateralOut = game.winningOption == 0 ? reserveYesOut : reserveNoOut;
        } else {
            collateralOut = reserveYesOut.min(reserveNoOut);
            if (!game.isRefunded) {
                returnedYes = reserveYesOut.subtract(collateralOut);
                returnedNo = reserveNoOut.subtract(collateralOut);
            }
        }

        BigInteger feeOut = nonNegative(game.myLiquidityFees);
        BigInteger amountOut = collateralOut.add(feeOut);
        if (amountOut.signum() <= 0 && returnedYes.signum() <= 0 && returnedNo.signum() <= 0) {
            return RemoveResult.invalid("当前 LP 份额无法换出有效资产");
        }
        BigInteger afterMyLP = myLP.subtract(lpAmountWei);
        BigInteger afterTotalLP = totalLP.subtract(lpAmountWei);
        BigDecimal remainingShare = afterTotalLP.signum() == 0
                ? BigDecimal.ZERO : percentage(afterMyLP, afterTotalLP);
        return RemoveResult.valid(lpAmountWei, collateralOut, feeOut, amountOut,
                applySlippage(amountOut), returnedYes, returnedNo, remainingShare);
    }

    public static BigInteger maximumRemovableLP(GoldMarketRepository.GameModel game) {
        if (game == null) return BigInteger.ZERO;
        BigInteger mine = nonNegative(game.myLiquidityShares);
        BigInteger total = nonNegative(game.totalLiquidityShares);
        if (!game.isResolved && !game.isRefunded && game.isCreator) {
            BigInteger locked = nonNegative(game.creatorLockedLiquidityShares);
            if (mine.compareTo(locked) <= 0) return BigInteger.ZERO;
            mine = mine.subtract(locked);
        }
        if (game.isResolved || game.isRefunded || mine.compareTo(total) < 0) return mine;
        return total.compareTo(BigInteger.ONE) > 0 ? total.subtract(BigInteger.ONE) : BigInteger.ZERO;
    }

    private static String activeLiquidityError(GoldMarketRepository.GameModel game,
                                               long nowSec, boolean adding) {
        if (game == null) return "未找到对应博弈池";
        if (game.isResolved || game.isRefunded) {
            return adding ? "博弈池已经结束，不能继续注入流动性" : null;
        }
        if (game.deadlineSec > 0 && nowSec >= game.deadlineSec) {
            return adding ? "博弈池已经截止，不能继续注入流动性"
                    : "博弈池正在等待结算，暂时不能取回流动性";
        }
        return null;
    }

    private static ReserveState reserveState(GoldMarketRepository.GameModel game) {
        if (game == null) return ReserveState.invalid("未找到对应博弈池");
        List<BigInteger> reserves = game.virtualReserves;
        if (reserves == null || reserves.size() < 2 || reserves.get(0) == null
                || reserves.get(1) == null || reserves.get(0).signum() <= 0
                || reserves.get(1).signum() <= 0) {
            return ReserveState.invalid("链上虚拟储备尚未就绪，无法计算 LP 报价");
        }
        // getGameExtraData returns [reserveNO, reserveYES].
        return ReserveState.valid(reserves.get(1), reserves.get(0));
    }

    private static BigInteger applySlippage(BigInteger value) {
        return value.multiply(BPS.subtract(BigInteger.valueOf(DEFAULT_SLIPPAGE_BPS))).divide(BPS);
    }

    private static BigInteger nonNegative(BigInteger value) {
        return value == null || value.signum() < 0 ? BigInteger.ZERO : value;
    }

    private static BigDecimal percentage(BigInteger numerator, BigInteger denominator) {
        if (denominator == null || denominator.signum() <= 0) return BigDecimal.ZERO;
        return new BigDecimal(numerator).multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(denominator), 2, RoundingMode.HALF_UP);
    }

    private static final class ReserveState {
        final boolean valid;
        final String error;
        final BigInteger reserveYes;
        final BigInteger reserveNo;

        private ReserveState(boolean valid, String error,
                             BigInteger reserveYes, BigInteger reserveNo) {
            this.valid = valid;
            this.error = error;
            this.reserveYes = reserveYes;
            this.reserveNo = reserveNo;
        }

        static ReserveState valid(BigInteger reserveYes, BigInteger reserveNo) {
            return new ReserveState(true, "", reserveYes, reserveNo);
        }

        static ReserveState invalid(String error) {
            return new ReserveState(false, error, BigInteger.ZERO, BigInteger.ZERO);
        }
    }

    public static final class AddResult {
        public final boolean valid;
        public final String error;
        public final BigInteger amountInWei;
        public final BigInteger liquiditySharesOutWei;
        public final BigInteger minimumLiquiditySharesOutWei;
        public final BigInteger returnedYesWei;
        public final BigInteger returnedNoWei;
        public final BigDecimal poolShareAfter;

        private AddResult(boolean valid, String error, BigInteger amountInWei,
                          BigInteger liquiditySharesOutWei,
                          BigInteger minimumLiquiditySharesOutWei,
                          BigInteger returnedYesWei, BigInteger returnedNoWei,
                          BigDecimal poolShareAfter) {
            this.valid = valid;
            this.error = error;
            this.amountInWei = amountInWei;
            this.liquiditySharesOutWei = liquiditySharesOutWei;
            this.minimumLiquiditySharesOutWei = minimumLiquiditySharesOutWei;
            this.returnedYesWei = returnedYesWei;
            this.returnedNoWei = returnedNoWei;
            this.poolShareAfter = poolShareAfter;
        }

        static AddResult invalid(String error) {
            return new AddResult(false, error, BigInteger.ZERO, BigInteger.ZERO,
                    BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO, BigDecimal.ZERO);
        }

        static AddResult valid(BigInteger amount, BigInteger lpOut, BigInteger minLP,
                               BigInteger yes, BigInteger no, BigDecimal share) {
            return new AddResult(true, "", amount, lpOut, minLP, yes, no, share);
        }
    }

    public static final class RemoveResult {
        public final boolean valid;
        public final String error;
        public final BigInteger liquiditySharesInWei;
        public final BigInteger collateralOutWei;
        public final BigInteger feeOutWei;
        public final BigInteger totalAmountOutWei;
        public final BigInteger minimumAmountOutWei;
        public final BigInteger returnedYesWei;
        public final BigInteger returnedNoWei;
        public final BigDecimal poolShareAfter;

        private RemoveResult(boolean valid, String error, BigInteger liquiditySharesInWei,
                             BigInteger collateralOutWei, BigInteger feeOutWei,
                             BigInteger totalAmountOutWei, BigInteger minimumAmountOutWei,
                             BigInteger returnedYesWei, BigInteger returnedNoWei,
                             BigDecimal poolShareAfter) {
            this.valid = valid;
            this.error = error;
            this.liquiditySharesInWei = liquiditySharesInWei;
            this.collateralOutWei = collateralOutWei;
            this.feeOutWei = feeOutWei;
            this.totalAmountOutWei = totalAmountOutWei;
            this.minimumAmountOutWei = minimumAmountOutWei;
            this.returnedYesWei = returnedYesWei;
            this.returnedNoWei = returnedNoWei;
            this.poolShareAfter = poolShareAfter;
        }

        static RemoveResult invalid(String error) {
            return new RemoveResult(false, error, BigInteger.ZERO, BigInteger.ZERO,
                    BigInteger.ZERO, BigInteger.ZERO, BigInteger.ZERO,
                    BigInteger.ZERO, BigInteger.ZERO, BigDecimal.ZERO);
        }

        static RemoveResult valid(BigInteger lpIn, BigInteger collateral, BigInteger fees,
                                  BigInteger total, BigInteger min, BigInteger yes,
                                  BigInteger no, BigDecimal share) {
            return new RemoveResult(true, "", lpIn, collateral, fees, total, min,
                    yes, no, share);
        }
    }
}
