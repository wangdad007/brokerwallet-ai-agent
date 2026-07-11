package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldMarketCardLayoutTest {
    @Test
    public void marketCardKeepsTitleOnOneAdaptiveLineAndMovesStatusToMetadataRow()
            throws Exception {
        String layout = read("app/src/main/res/layout/item_gold_market_card.xml");
        String titleTag = openingTag(layout, "TextView", "tv_market_title");
        String statusTag = openingTag(layout, "TextView", "tv_market_status");

        assertTrue(titleTag.contains("android:maxLines=\"1\""));
        assertFalse(titleTag.contains("android:ellipsize"));
        assertFalse(titleTag.contains("android:singleLine"));
        assertTrue(titleTag.contains("app:autoSizeTextType=\"uniform\""));
        assertTrue(titleTag.contains("app:layout_constraintEnd_toEndOf=\"parent\""));
        assertTrue(statusTag.contains(
                "app:layout_constraintTop_toTopOf=\"@+id/tv_total_pool\""));
        assertTrue(statusTag.contains(
                "app:layout_constraintBottom_toBottomOf=\"@+id/tv_total_pool\""));
        assertTrue(statusTag.contains(
                "app:layout_constraintEnd_toStartOf=\"@+id/tv_deadline\""));
    }

    private static String read(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        if (!Files.exists(path)) path = Paths.get("..").resolve(relativePath).normalize();
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String openingTag(String xml, String tagName, String id) {
        int idIndex = xml.indexOf("@+id/" + id);
        int start = xml.lastIndexOf("<" + tagName, idIndex);
        int end = xml.indexOf('>', idIndex);
        return xml.substring(start, end + 1);
    }
}
