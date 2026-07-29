package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldLimitOrderPolicy;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldLimitOrderPolicyTest {
    @Test
    public void buyRequiresAveragePriceAtOrBelowLimit() {
        assertTrue(GoldLimitOrderPolicy.buyQuoteMatches(
                new BigDecimal("0.42"), new BigDecimal("0.43")));
        assertFalse(GoldLimitOrderPolicy.buyQuoteMatches(
                new BigDecimal("0.44"), new BigDecimal("0.43")));
    }

    @Test
    public void sellRequiresAveragePriceAtOrAboveLimit() {
        assertTrue(GoldLimitOrderPolicy.sellQuoteMatches(
                new BigDecimal("0.44"), new BigDecimal("0.43")));
        assertFalse(GoldLimitOrderPolicy.sellQuoteMatches(
                new BigDecimal("0.42"), new BigDecimal("0.43")));
    }

    @Test
    public void sellUsesStricterOfLimitAndSlippageMinimum() {
        BigInteger shares = new BigInteger("10000000000000000000");
        BigInteger slippageFloor = new BigInteger("4000000000000000000");

        assertEquals(new BigInteger("4300000000000000000"),
                GoldLimitOrderPolicy.sellMinimumAmountOut(
                        shares, new BigDecimal("0.43"), slippageFloor));
        assertEquals(slippageFloor,
                GoldLimitOrderPolicy.sellMinimumAmountOut(
                        shares, new BigDecimal("0.35"), slippageFloor));
    }
}
