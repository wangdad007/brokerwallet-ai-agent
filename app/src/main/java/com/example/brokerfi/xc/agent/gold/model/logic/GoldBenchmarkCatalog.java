package com.example.brokerfi.xc.agent.gold.model.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Allow-list shared by the creation UI and the committed settlement rule. */
public final class GoldBenchmarkCatalog {
    private static final List<Benchmark> BENCHMARKS;

    static {
        List<Benchmark> values = new ArrayList<>();
        values.add(new Benchmark("BTC", "Bitcoin", "₿", 0xFFF7931A,
                "0xF4030086522a5bEEa4988F8cA5B36dbC97BeE88c"));
        values.add(new Benchmark("ETH", "Ethereum", "Ξ", 0xFF627EEA,
                "0x5f4eC3Df9cbd43714FE2740f5E3616155c5b8419"));
        values.add(new Benchmark("SOL", "Solana", "◎", 0xFF14F195,
                "0x4ffC43a60e009B551865A93d232E33Fce9f01507"));
        values.add(new Benchmark("BNB", "BNB", "◆", 0xFFF3BA2F,
                "0x14e613AC84a31f709eadbdF89C6CC390fDc9540A"));
        BENCHMARKS = Collections.unmodifiableList(values);
    }

    private GoldBenchmarkCatalog() {
    }

    public static List<Benchmark> all() {
        return BENCHMARKS;
    }

    public static Benchmark forSymbol(String symbol) {
        String normalized = symbol == null ? "" : symbol.trim().toUpperCase(Locale.US);
        for (Benchmark benchmark : BENCHMARKS) {
            if (benchmark.symbol.equals(normalized)) return benchmark;
        }
        return null;
    }

    public static int indexOf(String symbol) {
        Benchmark target = forSymbol(symbol);
        return target == null ? 0 : BENCHMARKS.indexOf(target);
    }

    public static final class Benchmark {
        public final String symbol;
        public final String name;
        public final String glyph;
        public final int color;
        public final String feedAddress;

        private Benchmark(String symbol, String name, String glyph, int color, String feedAddress) {
            this.symbol = symbol;
            this.name = name;
            this.glyph = glyph;
            this.color = color;
            this.feedAddress = feedAddress;
        }
    }
}
