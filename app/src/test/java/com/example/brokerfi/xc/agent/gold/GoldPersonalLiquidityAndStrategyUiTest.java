package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldPersonalLiquidityAndStrategyUiTest {
    @Test
    public void liquidityTabUsesDedicatedCardAndRecordDetail() throws Exception {
        String fragment = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                + "GoldMyPositionsFragment.java");
        String detail = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                + "GoldLiquidityDetailActivity.java");
        String detailLayout = read(
                "app/src/main/res/layout/activity_gold_liquidity_detail.xml");
        String backendClient = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/"
                + "model/data/BackendApiClient.java");
        String repository = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/"
                + "model/data/GoldMarketRepository.java");
        String card = read("app/src/main/res/layout/item_gold_liquidity_card.xml");
        String positionCard = read("app/src/main/res/layout/item_gold_position_card.xml");
        String strategyCard = read("app/src/main/res/layout/item_gold_strategy_card.xml");

        assertTrue(fragment.contains("R.layout.item_gold_liquidity_card"));
        assertTrue(fragment.contains("GoldLiquidityDetailActivity.createIntent"));
        assertTrue(card.contains("我的质押"));
        assertTrue(card.contains("池内总额"));
        assertTrue(card.contains("记录 ›"));
        assertTrue(card.contains("android:textSize=\"18sp\""));
        assertTrue(positionCard.contains("android:textSize=\"18sp\""));
        assertTrue(strategyCard.contains("android:textSize=\"18sp\""));
        assertTrue(positionCard.contains("android:text=\"当前估值\""));
        assertTrue(positionCard.contains("android:text=\"持仓方向\""));
        assertTrue(positionCard.contains("android:text=\"博弈状态\""));
        assertTrue(strategyCard.contains("android:text=\"策略类型\""));
        assertTrue(strategyCard.contains("android:text=\"执行方式\""));
        assertTrue(strategyCard.contains("android:text=\"核心参数\""));
        assertTrue(detail.contains("\"LIQUIDITY_ADD\""));
        assertTrue(detail.contains("\"LIQUIDITY_REMOVE\""));
        assertTrue(detail.contains("BackendApiClient.fetchTradeHistory"));
        assertTrue(detail.contains("renderSummary();"));
        assertTrue(detail.contains("renderHistory();"));
        assertTrue(detail.contains("historyRetryCount < 2"));
        assertTrue(detail.contains("returnedShareSummary"));
        assertTrue(detailLayout.contains("为什么会收到 YES/NO 份额？"));
        assertTrue(detailLayout.contains("它不是退回 BKC"));
        assertTrue(backendClient.contains("@SerializedName(\"returned_yes_wei\")"));
        assertTrue(backendClient.contains("@SerializedName(\"returned_no_wei\")"));
        assertTrue(repository.contains("resolveLiquidityReturnedShareWei"));
    }

    @Test
    public void strategyListHasNoRedundantSectionCopyAndEditorShowsOwnTrades()
            throws Exception {
        String fragment = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                + "GoldMyPositionsFragment.java");
        String editor = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                + "GoldCustomStrategyActivity.java");
        String marketDetail = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/view/"
                + "GoldMarketDetailActivity.java");
        String layout = read("app/src/main/res/layout/activity_gold_custom_strategy.xml");

        assertFalse(fragment.contains("addStrategySection("));
        assertFalse(fragment.contains("\"规则策略\","));
        assertTrue(editor.contains("BackendApiClient.fetchTradeHistory"));
        assertTrue(editor.contains("strategyType.equals(source)"));
        assertTrue(editor.contains("createManagementIntent"));
        assertTrue(editor.contains("createDetailIntent"));
        assertTrue(editor.contains("if (!showHistory) return"));
        assertTrue(marketDetail.contains(
                "GoldCustomStrategyActivity.createManagementIntent"));
        assertTrue(fragment.contains(
                "GoldCustomStrategyActivity.createDetailIntent"));
        assertTrue(editor.contains("save.setText(\"保存修改\")"));
        assertTrue(layout.contains("策略交易记录"));
        assertTrue(layout.contains("@+id/strategy_history_section"));
        assertTrue(layout.contains("@+id/strategy_trade_history"));
    }

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relativePath).normalize();
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
