package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.model.data.BackendApiClient;
import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldMarketChainStateTest {
    @Test
    public void deadlineOnlyPlaceholderIsNotTreatedAsCompleteState() throws Exception {
        BackendApiClient.ChainStateDTO state = completeState();
        state.totalPool = "0";

        assertFalse(hasUsableChainState(state));
    }

    @Test
    public void positivePoolAndReservesAreTreatedAsCompleteState() throws Exception {
        assertTrue(hasUsableChainState(completeState()));
    }

    private static BackendApiClient.ChainStateDTO completeState() {
        BackendApiClient.ChainStateDTO state = new BackendApiClient.ChainStateDTO();
        state.deadlineSec = 1_800_000_000L;
        state.totalPool = "1000000000000000000";
        state.reserveYES = "1000000000000000000";
        state.reserveNO = "1000000000000000000";
        return state;
    }

    private static boolean hasUsableChainState(
            BackendApiClient.ChainStateDTO state) throws Exception {
        Method method = GoldMarketRepository.class.getDeclaredMethod(
                "hasUsableChainState", BackendApiClient.ChainStateDTO.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, state);
    }
}
