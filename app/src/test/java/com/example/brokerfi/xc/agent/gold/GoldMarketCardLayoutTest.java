package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class GoldMarketCardLayoutTest {
    @Test
    public void marketCardGivesTitleRoomAndKeepsStatusInsideMetadataRow()
            throws Exception {
        String layout = read("app/src/main/res/layout/item_gold_market_card.xml");
        String titleTag = openingTag(
                layout, "androidx.appcompat.widget.AppCompatTextView", "tv_market_title");
        String statusTag = openingTag(layout, "TextView", "tv_market_status");

        assertTrue(titleTag.contains("android:maxLines=\"1\""));
        assertTrue(titleTag.contains("android:singleLine=\"true\""));
        assertTrue(titleTag.contains("android:ellipsize=\"end\""));
        assertTrue(titleTag.contains("app:autoSizeTextType=\"uniform\""));
        assertTrue(titleTag.contains("app:autoSizeMinTextSize=\"10sp\""));
        assertTrue(titleTag.contains("app:layout_constraintEnd_toEndOf=\"parent\""));
        assertTrue(statusTag.contains(
                "app:layout_constraintStart_toEndOf=\"@+id/tv_total_pool\""));
        assertTrue(statusTag.contains(
                "app:layout_constraintEnd_toStartOf=\"@+id/tv_deadline\""));
        assertTrue(statusTag.contains(
                "app:layout_constraintTop_toTopOf=\"@+id/tv_total_pool\""));
        assertTrue(statusTag.contains(
                "app:layout_constraintBottom_toBottomOf=\"@+id/tv_total_pool\""));
        assertTrue(statusTag.contains("android:maxLines=\"1\""));
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
