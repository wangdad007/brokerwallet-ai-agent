package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/** Deterministic preview of PredictionMarket.sellShares. No chain write occurs here. */
public final class GoldSellSimulation {
    public static final int DEFAULT_SLIPPAGE_BPS = 100;
    private static final BigInteger BPS = BigInteger.valueOf(10_000);

    private GoldSellSimulation() {
    }

    /** UI option ids follow the contract: 0 = YES, 1 = NO. */
    public static Result simulate(GoldMarketRepository.GameModel game, int optionId,
                                  BigInteger shareAmountWei, int slippageBps) {
        if (game == null) return Result.invalid("未找到对应博弈池");
        if (game.isResolved || game.isRefunded) {
            return Result.invalid("该博弈池已经结束，不能继续卖出");
        }
        if (shareAmountWei == null || shareAmountWei.signum() <= 0) {
            return Result.invalid("请输入大于 0 的份额数量");
        }
        if (optionId != 0 && optionId != 1) {
            return Result.invalid("只能卖出 YES 或 NO 份额");
        }
        if (slippageBps < 0 || slippageBps > 5_000) {
            return Result.invalid("滑点范围无效");
        }
        List<BigInteger> shares = game.myShares;
        BigInteger held = shares == null || shares.size() <= optionId || shares.get(optionId) == null
                ? BigInteger.ZERO : shares.get(optionId);
        if (held.compareTo(shareAmountWei) < 0) {
            return Result.invalid("卖出份额超过当前持仓");
        }
        List<BigInteger> reserves = game.virtualReserves;
        if (reserves == null || reserves.size() < 2 || reserves.get(0) == null
                || reserves.get(1) == null || reserves.get(0).signum() <= 0
                || reserves.get(1).signum() <= 0) {
            return Result.invalid("链上虚拟储备尚未就绪，无法给出可靠报价");
        }

        BigInteger reserveNo = reserves.get(0);
        BigInteger reserveYes = reserves.get(1);
        BigInteger heldReserve = optionId == 0 ? reserveYes : reserveNo;
        BigInteger oppositeReserve = optionId == 0 ? reserveNo : reserveYes;
        BigInteger amountOut = calculateSellReturn(
                heldReserve, oppositeReserve, shareAmountWei);
        if (amountOut.signum() <= 0 || amountOut.compareTo(shareAmountWei) > 0) {
            return Result.invalid("份额过小或当前池深不足，无法获得有效卖出报价");
        }

        BigInteger minAmountOut = amountOut.multiply(
                BPS.subtract(BigInteger.valueOf(slippageBps))).divide(BPS);
        BigInteger afterNo;
        BigInteger afterYes;
        if (optionId == 0) {
            afterYes = reserveYes.add(shareAmountWei).subtract(amountOut);
            afterNo = reserveNo.subtract(amountOut);
        } else {
            afterNo = reserveNo.add(shareAmountWei).subtract(amountOut);
            afterYes = reserveYes.subtract(amountOut);
        }
        if (afterNo.signum() <= 0 || afterYes.signum() <= 0
                || afterNo.multiply(afterYes).compareTo(reserveNo.multiply(reserveYes)) < 0) {
            return Result.invalid("卖出报价会破坏 AMM 储备约束");
        }

        BigDecimal beforeYes = yesProbability(reserveNo, reserveYes);
        BigDecimal afterYesProbability = yesProbability(afterNo, afterYes);
        BigDecimal averagePrice = new BigDecimal(amountOut)
                .divide(new BigDecimal(shareAmountWei), 8, RoundingMode.HALF_UP);
        return Result.valid(optionId, shareAmountWei, amountOut, minAmountOut,
                beforeYes, afterYesProbability, averagePrice, afterNo, afterYes);
    }

    public static BigInteger calculateSellReturn(
            BigInteger heldReserve, BigInteger oppositeReserve, BigInteger shareAmount) {
        if (heldReserve == null || oppositeReserve == null || shareAmount == null
                || heldReserve.signum() <= 0 || oppositeReserve.signum() <= 0
                || shareAmount.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger b = heldReserve.add(oppositeReserve).add(shareAmount);
        BigInteger discriminant = b.multiply(b)
                .subtract(BigInteger.valueOf(4).multiply(oppositeReserve).multiply(shareAmount));
        if (discriminant.signum() < 0) return BigInteger.ZERO;
        BigInteger root = sqrtFloor(discriminant);
        if (root.multiply(root).compareTo(discriminant) < 0) {
            root = root.add(BigInteger.ONE);
        }
        return b.subtract(root).divide(BigInteger.valueOf(2));
    }

    private static BigInteger sqrtFloor(BigInteger value) {
        if (value.signum() <= 0) return BigInteger.ZERO;
        BigInteger estimate = BigInteger.ONE.shiftLeft((value.bitLength() + 1) / 2);
        while (true) {
            BigInteger next = estimate.add(value.divide(estimate)).shiftRight(1);
            if (next.compareTo(estimate) >= 0) return estimate;
            estimate = next;
        }
    }

    private static BigDecimal yesProbability(BigInteger reserveNo, BigInteger reserveYes) {
        return new BigDecimal(reserveNo).multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(reserveNo.add(reserveYes)), 2, RoundingMode.HALF_UP);
    }

    public static final class Result {
        public final boolean valid;
        public final String error;
        public final int optionId;
        public final BigInteger shareAmountWei;
        public final BigInteger amountOutWei;
        public final BigInteger minAmountOutWei;
        public final BigDecimal beforeYesProbability;
        public final BigDecimal afterYesProbability;
        public final BigDecimal averagePriceBkcPerShare;
        public final BigInteger afterReserveNo;
        public final BigInteger afterReserveYes;

        private Result(boolean valid, String error, int optionId, BigInteger shareAmountWei,
                       BigInteger amountOutWei, BigInteger minAmountOutWei,
                       BigDecimal beforeYesProbability, BigDecimal afterYesProbability,
                       BigDecimal averagePriceBkcPerShare, BigInteger afterReserveNo,
                       BigInteger afterReserveYes) {
            this.valid = valid;
            this.error = error;
            this.optionId = optionId;
            this.shareAmountWei = shareAmountWei;
            this.amountOutWei = amountOutWei;
            this.minAmountOutWei = minAmountOutWei;
            this.beforeYesProbability = beforeYesProbability;
            this.afterYesProbability = afterYesProbability;
            this.averagePriceBkcPerShare = averagePriceBkcPerShare;
            this.afterReserveNo = afterReserveNo;
            this.afterReserveYes = afterReserveYes;
        }

        private static Result invalid(String error) {
            return new Result(false, error, -1, BigInteger.ZERO, BigInteger.ZERO,
                    BigInteger.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigInteger.ZERO, BigInteger.ZERO);
        }

        private static Result valid(int optionId, BigInteger shares, BigInteger amountOut,
                                    BigInteger minAmountOut, BigDecimal before,
                                    BigDecimal after, BigDecimal average,
                                    BigInteger afterNo, BigInteger afterYes) {
            return new Result(true, "", optionId, shares, amountOut, minAmountOut,
                    before, after, average, afterNo, afterYes);
        }
    }
}
