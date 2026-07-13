package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.ai.BackendResearchClient;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BackendResearchClientTest {
    @Test
    public void parsesBackendResearchContent() {
        assertEquals("YES 侧证据较强。", BackendResearchClient.parseContent(
                "{\"content\":\"YES 侧证据较强。\"}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptyBackendResearchContent() {
        BackendResearchClient.parseContent("{\"content\":\"   \"}");
    }
}
