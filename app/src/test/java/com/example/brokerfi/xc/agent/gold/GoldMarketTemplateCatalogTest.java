package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.logic.GoldMarketTemplateCatalog;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GoldMarketTemplateCatalogTest {
    @Test
    public void catalogContainsOnlySixResolvableTemplates() {
        assertEquals(Arrays.asList(
                "TYPE_PRICE", "TYPE_RETURN_THRESHOLD", "TYPE_PRICE_THRESHOLD",
                "TYPE_PRICE_RANGE", "TYPE_RELATIVE", "TYPE_STREAK"
        ), GoldMarketTemplateCatalog.types());
        assertFalse(GoldMarketTemplateCatalog.isCreatable("TYPE_EVENT"));
        assertFalse(GoldMarketTemplateCatalog.isCreatable("TYPE_TECHNICAL"));
    }
}
