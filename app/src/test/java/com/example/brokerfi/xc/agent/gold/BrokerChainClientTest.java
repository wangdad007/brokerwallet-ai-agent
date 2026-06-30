package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.BrokerChainClient;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class BrokerChainClientTest {
    @Test
    public void receiptStatusDistinguishesPendingSuccessAndFailure() throws Exception {
        assertNull(parse("{\"result\":null}"));
        assertEquals(Boolean.TRUE, parse("{\"result\":{\"status\":\"0x1\"}}"));
        assertEquals(Boolean.FALSE, parse("{\"result\":{\"status\":\"0x0\"}}"));
    }

    private static Boolean parse(String json) throws Exception {
        Method method = BrokerChainClient.class.getDeclaredMethod(
                "parseTransactionReceiptStatus", String.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, json);
    }
}
