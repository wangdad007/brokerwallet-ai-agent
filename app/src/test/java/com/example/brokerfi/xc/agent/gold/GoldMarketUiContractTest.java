package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldMarketUiContractTest {
    private static final String ACTIVITY_PATH =
            "app/src/main/java/com/example/brokerfi/xc/AIAssistantActivity.java";

    @Test
    public void chatDisplaysExistingSummaryAndUsesSavedContextForFollowUps() throws Exception {
        String source = new String(
                Files.readAllBytes(repoPath(ACTIVITY_PATH)),
                StandardCharsets.UTF_8);

        assertTrue(source.contains(
                "public static final String EXTRA_MARKET_CONTEXT = \"MARKET_CONTEXT\";"));
        assertTrue(source.contains(
                "public static final String EXTRA_INITIAL_AI_SUMMARY = \"INITIAL_AI_SUMMARY\";"));
        assertTrue(source.contains("INITIAL_PROMPT"));
        assertTrue(source.contains("!isBlank(initialSummary)"));
        assertTrue(source.contains("!isBlank(initialPrompt)"));

        String summaryBranch = blockAfter(source, "if (!isBlank(initialSummary))");
        assertTrue(summaryBranch.contains("addMessage(\"AI\", initialSummary)"));
        assertFalse(
                "Pre-generated summaries must never trigger another AI request",
                summaryBranch.contains("submitQuestion("));

        String legacyBranch = blockAfter(source, "else if (!isBlank(initialPrompt))");
        assertInOrder(
                legacyBranch,
                "marketContext = initialPrompt",
                "legacyInitialPromptInFlight = true",
                "submitQuestion(initialPrompt)");

        String bypassBranch = blockAfter(
                source, "if (legacyInitialPromptInFlight)");
        assertInOrder(
                bypassBranch,
                "legacyInitialPromptInFlight = false",
                "return text");

        assertTrue(
                "Normal follow-ups must delegate context and text to the shared builder",
                Pattern.compile(
                        "return\\s+GoldMarketResearchPromptBuilder\\.withFollowUp\\s*"
                                + "\\(\\s*marketContext\\s*,\\s*text\\s*\\)\\s*;")
                        .matcher(source)
                        .find());
        assertTrue(
                "Blank checks must reject whitespace-only values",
                Pattern.compile(
                        "isBlank\\s*\\(\\s*String\\s+value\\s*\\).*?"
                                + "TextUtils\\.isEmpty\\(value\\).*?"
                                + "value\\.trim\\(\\)\\.isEmpty\\(\\)",
                        Pattern.DOTALL)
                        .matcher(source)
                        .find());
    }

    private static String blockAfter(String source, String marker) {
        int markerIndex = source.indexOf(marker);
        assertTrue("Missing source marker: " + marker, markerIndex >= 0);
        int openingBrace = source.indexOf('{', markerIndex + marker.length());
        assertTrue("Missing block after: " + marker, openingBrace >= 0);

        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openingBrace + 1, index);
                }
            }
        }
        throw new AssertionError("Unclosed block after: " + marker);
    }

    private static void assertInOrder(String source, String... expectedParts) {
        int previousIndex = -1;
        for (String expected : expectedParts) {
            int index = source.indexOf(expected, previousIndex + 1);
            assertTrue("Expected source part in order: " + expected, index >= 0);
            previousIndex = index;
        }
    }

    private static Path repoPath(String path) {
        Path fromRoot = Paths.get(path);
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        return Paths.get("..").resolve(path).normalize();
    }
}
