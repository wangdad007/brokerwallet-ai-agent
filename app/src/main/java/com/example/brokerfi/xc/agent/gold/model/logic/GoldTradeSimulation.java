package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/** Deterministic preview of the current binary AMM buy formula. No chain write occurs here. */
public final class GoldTradeSimulation {
    private static final BigDecimal WEI_PER_BKC = new BigDecimal("1000000000000000000");
    private static final BigInteger BPS = BigInteger.valueOf(10_000);
    private static final BigInteger NET_AFTER_FEE_BPS = BigInteger.valueOf(9_900);

    private GoldTradeSimulation() {
    }

    /** UI option ids follow the contract: 0 = YES, 1 = NO. */
    public static Result simulate(GoldMarketRepository.GameModel game, int optionId,
                                  BigInteger amountWei) {
        if (game == null) return Result.invalid("未找到对应博弈池");
        if (game.isResolved || game.isRefunded) return Result.invalid("该博弈池已经结束，不能继续买入");
        if (amountWei == null || amountWei.signum() <= 0) return Result.invalid("请输入大于 0 的 BKC 金额");
        if (optionId != 0 && optionId != 1) return Result.invalid("只能研判买入 YES 或 NO");
        List<BigInteger> reserves = game.virtualReserves;
        if (reserves == null || reserves.size() < 2 || reserves.get(0) == null || reserves.get(1) == null
                || reserves.get(0).signum() <= 0 || reserves.get(1).signum() <= 0) {
            return Result.invalid("链上虚拟储备尚未就绪，无法给出可靠报价");
        }

        // getGameExtraData returns [reserveNO, reserveYES]. This matches PredictionMarket.buyShares.
        BigInteger reserveNo = reserves.get(0);
        BigInteger reserveYes = reserves.get(1);
        BigInteger netAmountWei = amountWei.multiply(NET_AFTER_FEE_BPS).divide(BPS);
        if (netAmountWei.signum() <= 0) {
            return Result.invalid("金额过小，扣除 1% LP 交易费后无法成交");
        }
        BigInteger invariant = reserveNo.multiply(reserveYes);
        BigInteger sharesOut;
        BigInteger afterNo;
        BigInteger afterYes;
        if (optionId == 0) {
            afterNo = reserveNo.add(netAmountWei);
            afterYes = invariant.divide(afterNo);
            sharesOut = netAmountWei.add(reserveYes.subtract(afterYes));
        } else {
            afterYes = reserveYes.add(netAmountWei);
            afterNo = invariant.divide(afterYes);
            sharesOut = netAmountWei.add(reserveNo.subtract(afterNo));
        }
        if (sharesOut.signum() <= 0) return Result.invalid("金额过小，按合约整数精度不能换出有效份额");

        BigDecimal beforeYes = yesProbability(reserveNo, reserveYes);
        BigDecimal afterYesProbability = yesProbability(afterNo, afterYes);
        BigDecimal averagePrice = new BigDecimal(amountWei)
                .divide(new BigDecimal(sharesOut), 8, RoundingMode.HALF_UP);
        return Result.valid(optionId, amountWei, sharesOut, beforeYes, afterYesProbability, averagePrice,
                reserveNo.add(reserveYes));
    }

    private static BigDecimal yesProbability(BigInteger reserveNo, BigInteger reserveYes) {
        return new BigDecimal(reserveNo).multiply(BigDecimal.valueOf(100))
                .divide(new BigDecimal(reserveNo.add(reserveYes)), 2, RoundingMode.HALF_UP);
    }

    public static String formatBkc(BigInteger wei, int scale) {
        if (wei == null) return "0";
        return new BigDecimal(wei).divide(WEI_PER_BKC, scale, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public static final class Result {
        public final boolean valid;
        public final String error;
        public final int optionId;
        public final BigInteger amountWei;
        public final BigInteger sharesOutWei;
        public final BigDecimal beforeYesProbability;
        public final BigDecimal afterYesProbability;
        public final BigDecimal averagePriceBkcPerShare;
        public final BigInteger depthWei;

        private Result(boolean valid, String error, int optionId, BigInteger amountWei,
                       BigInteger sharesOutWei, BigDecimal beforeYesProbability,
                       BigDecimal afterYesProbability, BigDecimal averagePriceBkcPerShare,
                       BigInteger depthWei) {
            this.valid = valid;
            this.error = error;
            this.optionId = optionId;
            this.amountWei = amountWei;
            this.sharesOutWei = sharesOutWei;
            this.beforeYesProbability = beforeYesProbability;
            this.afterYesProbability = afterYesProbability;
            this.averagePriceBkcPerShare = averagePriceBkcPerShare;
            this.depthWei = depthWei;
        }

        private static Result invalid(String error) {
            return new Result(false, error, -1, BigInteger.ZERO, BigInteger.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigInteger.ZERO);
        }

        private static Result valid(int optionId, BigInteger amountWei, BigInteger sharesOutWei,
                                    BigDecimal before, BigDecimal after, BigDecimal average,
                                    BigInteger depthWei) {
            return new Result(true, "", optionId, amountWei, sharesOutWei, before, after,
                    average, depthWei);
        }
    }
}
