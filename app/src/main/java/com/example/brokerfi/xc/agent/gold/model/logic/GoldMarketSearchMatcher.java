package com.example.brokerfi.xc.agent.gold.model.logic;

import com.example.brokerfi.xc.agent.gold.model.data.GoldMarketRepository;

import java.util.List;
import java.util.Locale;

/** Shared, deterministic search matching for market and position lists. */
public final class GoldMarketSearchMatcher {
    private GoldMarketSearchMatcher() {
    }

    public static boolean matches(GoldMarketRepository.GameModel game, String rawQuery) {
        if (game == null) return false;
        String query = normalize(rawQuery);
        if (query.isEmpty()) return true;

        String id = String.valueOf(game.id);
        String compactIdQuery = query.replace("#", "")
                .replace("博弈池", "")
                .replace("市场", "")
                .replace("号池", "")
                .replace("号", "")
                .replace(" ", "");
        if (id.equals(compactIdQuery)) return true;
        if (contains(game.desc, query)
                || contains(game.condition, query)
                || contains(game.detailedInfo, query)
                || contains(game.ipfsCID, query)
                || normalize("博弈池 #" + id).contains(query)) {
            return true;
        }
        List<String> options = game.optionNames;
        if (options != null) {
            for (String option : options) {
                if (contains(option, query)) return true;
            }
        }
        return false;
    }

    private static boolean contains(String value, String normalizedQuery) {
        return normalize(value).contains(normalizedQuery);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
