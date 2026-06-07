package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

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
        assertTrue(source.contains("addMessage(\"AI\", initialSummary)"));
        assertTrue(source.contains("GoldMarketResearchPromptBuilder.withFollowUp"));
        assertTrue(source.contains("INITIAL_PROMPT"));
        assertTrue(
                "Legacy initial prompt must only be submitted when no AI summary was supplied",
                summaryDisplayGuardsLegacySubmission(source));
    }

    private static boolean summaryDisplayGuardsLegacySubmission(String source) {
        Pattern guardedLegacySubmission = Pattern.compile(
                "if\\s*\\(\\s*!TextUtils\\.isEmpty\\(initialSummary\\)\\s*\\)\\s*\\{"
                        + "[^}]*addMessage\\(\\s*\"AI\"\\s*,\\s*initialSummary\\s*\\)\\s*;"
                        + "\\s*}\\s*else\\s+if\\s*"
                        + "\\(\\s*!TextUtils\\.isEmpty\\(initialPrompt\\)\\s*\\)\\s*\\{"
                        + "[^}]*submitQuestion\\(\\s*initialPrompt\\s*\\)\\s*;",
                Pattern.DOTALL);
        return guardedLegacySubmission.matcher(source).find();
    }

    private static Path repoPath(String path) {
        Path fromRoot = Paths.get(path);
        if (Files.exists(fromRoot)) {
            return fromRoot;
        }
        return Paths.get("..").resolve(path).normalize();
    }
}
