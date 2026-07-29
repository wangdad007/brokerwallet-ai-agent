package com.example.brokerfi.xc.agent.gold.model.logic;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Deterministic immediate-or-cancel limit protection for the current AMM.
 *
 * <p>This is not a resting order book. A limit order is submitted only when
 * the current quote satisfies the requested price. SELL also receives an
 * on-chain minimum-output guard; BUY is preflight-only until the contract
 * exposes a minSharesOut parameter.</p>
 */
public final class GoldLimitOrderPolicy {
    private GoldLimitOrderPolicy() {
    }

    public static boolean buyQuoteMatches(BigDecimal averagePrice, BigDecimal maximumPrice) {
        return validPrice(averagePrice) && validPrice(maximumPrice)
                && averagePrice.compareTo(maximumPrice) <= 0;
    }

    public static boolean sellQuoteMatches(BigDecimal averagePrice, BigDecimal minimumPrice) {
        return validPrice(averagePrice) && validPrice(minimumPrice)
                && averagePrice.compareTo(minimumPrice) >= 0;
    }

    public static BigInteger sellMinimumAmountOut(
            BigInteger shareAmountWei,
            BigDecimal minimumPriceBkcPerShare,
            BigInteger slippageMinimumWei) {
        if (shareAmountWei == null || shareAmountWei.signum() <= 0
                || !validPrice(minimumPriceBkcPerShare)) {
            return slippageMinimumWei == null ? BigInteger.ZERO : slippageMinimumWei;
        }
        BigInteger limitMinimum = new BigDecimal(shareAmountWei)
                .multiply(minimumPriceBkcPerShare)
                .setScale(0, RoundingMode.CEILING)
                .toBigInteger();
        if (slippageMinimumWei == null || limitMinimum.compareTo(slippageMinimumWei) > 0) {
            return limitMinimum;
        }
        return slippageMinimumWei;
    }

    private static boolean validPrice(BigDecimal price) {
        return price != null && price.signum() > 0 && price.compareTo(BigDecimal.ONE) <= 0;
    }
}
