package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldPositionHeaderContractTest {
    @Test
    public void positionHeaderUsesSharedMarketStylingAndShowsFullJudgmentLogic()
            throws Exception {
        String source = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/view/GoldPositionDetailActivity.java");
        String layout = read("app/src/main/res/layout/activity_gold_position_detail.xml");

        assertTrue(source.contains("GoldMarketTextStyler.style"));
        assertTrue(source.contains("GoldMarketCardPresenter.displayTitle("));
        assertTrue(source.contains("rawTitle, rawCondition, currentGame.deadlineSec"));
        assertTrue(source.contains("GoldMarketDetailPresenter.formatResolutionRule(rawCondition)"));
        assertTrue(layout.contains("@drawable/bg_gold_detail_hero"));
        assertTrue(layout.contains("@drawable/bg_gold_condition_strip"));

        String titleTag = openingTag(layout, "TextView", "tv_pool_desc");
        assertTrue(titleTag.contains("android:layout_width=\"match_parent\""));
        assertFalse(titleTag.contains("android:maxLines"));
        assertFalse(titleTag.contains("android:ellipsize"));

        String conditionTag = openingTag(layout, "TextView", "tv_pool_condition");
        assertTrue(conditionTag.contains("android:layout_width=\"match_parent\""));
        assertFalse(conditionTag.contains("android:maxLines"));
        assertFalse(conditionTag.contains("android:ellipsize"));
    }

    @Test
    public void positionCardsReuseTheMarketCardTitleWithoutDateTimeComposition()
            throws Exception {
        String source = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/view/GoldMyPositionsFragment.java");

        assertTrue(source.contains("GoldMarketCardPresenter.displayTitle("));
        assertTrue(source.contains("rawTitle, game.condition, game.deadlineSec"));
        assertFalse(source.contains("GoldMarketDetailPresenter.heroText"));
    }

    @Test
    public void positionCardDoesNotDuplicateTheHoldingPrefix() throws Exception {
        String source = read("app/src/main/java/com/example/brokerfi/xc/agent/gold/view/GoldMyPositionsFragment.java");

        assertTrue(source.contains("shareText.append(sideName).append(\"：\")"));
        assertFalse(source.contains("shareText.append(\"持有 \").append(sideName)"));
    }

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relativePath).normalize();
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String openingTag(String xml, String tagName, String id) {
        int idIndex = xml.indexOf("@+id/" + id);
        int start = xml.lastIndexOf("<" + tagName, idIndex);
        int end = xml.indexOf(">", idIndex);
        return xml.substring(start, end + 1);
    }
}
