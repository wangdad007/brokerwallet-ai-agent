package com.example.brokerfi.xc.agent.gold;

final class GoldMarketSecurityPolicy {
    static final String DEFAULT_CONTRACT_ADDRESS = "0x713099ddD3Afe863b44F27f85099beadEf779764";

    private GoldMarketSecurityPolicy() {
    }

    static boolean isDeveloperMarketToolsEnabled(boolean buildDebug) {
        return buildDebug;
    }

    static String resolveContractAddress(boolean developerToolsEnabled, String savedAddress) {
        if (!developerToolsEnabled || savedAddress == null || savedAddress.trim().isEmpty()) {
            return DEFAULT_CONTRACT_ADDRESS;
        }
        return savedAddress.trim();
    }

    static String resolveRpcUrl(boolean developerToolsEnabled, String savedRpcUrl) {
        if (!developerToolsEnabled || savedRpcUrl == null) {
            return "";
        }
        return savedRpcUrl.trim();
    }
}
