package com.example.brokerfi.xc.agent.gold;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldMarketUiContractTest {
    private static final String AI_ACTIVITY_PATH =
            "app/src/main/java/com/example/brokerfi/xc/AIAssistantActivity.java";
    private static final String DETAIL_ACTIVITY_PATH =
            "app/src/main/java/com/example/brokerfi/xc/agent/gold/ui/"
                    + "GoldMarketDetailActivity.java";
    private static final String DETAIL_LAYOUT_PATH =
            "app/src/main/res/layout/activity_gold_market_detail.xml";

    @Test
    public void chatDisplaysExistingSummaryAndUsesSavedContextForFollowUps() throws Exception {
        String source = readUtf8(AI_ACTIVITY_PATH);

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

    @Test
    public void detailLayoutPlacesAiCardBeforeTransactionsAndUnifiesBuyButtons()
            throws Exception {
        String layout = readUtf8(DETAIL_LAYOUT_PATH);

        int marketPanelEnd = layout.indexOf("@+id/tv_countdown");
        int aiCard = layout.indexOf("@+id/card_market_ai");
        int transactionHeading = layout.indexOf("android:text=\"交易\"");
        assertTrue("AI card must follow the AMM market panel",
                marketPanelEnd >= 0 && aiCard > marketPanelEnd);
        assertTrue("AI card must precede the transaction section",
                transactionHeading > aiCard);

        String cardTag = openingTag(layout, "LinearLayout", "card_market_ai");
        assertTrue(cardTag.contains("android:clickable=\"true\""));
        assertTrue(cardTag.contains("android:focusable=\"true\""));
        assertTrue(layout.contains("@+id/tv_market_ai_status"));
        assertTrue(layout.contains("@+id/tv_market_ai_summary"));
        assertTrue(layout.contains("AI 针对此池分析"));

        assertEquals(
                buttonBackground(layout, "btn_buy_up"),
                buttonBackground(layout, "btn_buy_down"));
        assertEquals("@drawable/custom_button_background",
                buttonBackground(layout, "btn_buy_up"));
    }

    @Test
    public void detailActivityRequestsOneSummaryAndPassesItToResearchChat()
            throws Exception {
        String source = readUtf8(DETAIL_ACTIVITY_PATH);

        assertTrue(source.contains("private boolean marketAiRequested = false;"));
        assertTrue(source.contains("private String marketAiContext = \"\";"));
        assertTrue(source.contains("private String marketAiSummary = \"\";"));

        String onCreate = blockAfter(source, "protected void onCreate");
        assertInOrder(onCreate, "DeepSeekClient.init(this)", "initViews()");

        String loadSuccess = blockAfter(
                source,
                "public void onSuccess(GoldMarketRepository.GameModel model)");
        assertInOrder(loadSuccess,
                "currentGame = model",
                "updateUI()",
                "requestMarketAiSummaryOnce()");

        String request = blockAfter(
                source, "private void requestMarketAiSummaryOnce()");
        assertInOrder(request,
                "if (marketAiRequested || currentGame == null) return",
                "marketAiRequested = true",
                "DeepSeekClient.isConfigured()");
        assertTrue(request.contains("GoldAdvisoryManager.fetchPrice"));
        assertEquals(
                "Quote success and error must both build market context",
                2,
                countOccurrences(
                        request,
                        "GoldMarketResearchPromptBuilder.buildContext("));
        assertTrue(request.contains(
                "GoldMarketResearchPromptBuilder.buildSummaryPrompt(marketAiContext)"));
        assertTrue(request.contains(
                "AgentManager.getInstance().askGoldResearch"));
        assertTrue(request.contains("runOnUiThread"));
        assertTrue(request.contains("AI 分析暂时不可用"));
        assertTrue(source.contains(
                "请先在博弈池列表顶部的总 AI 助手中配置 DeepSeek API Key。"));

        String renderSummary = blockAfter(
                source, "private void showMarketAiSummary(String answer)");
        assertTrue(renderSummary.contains("runOnUiThread"));
        assertTrue(renderSummary.contains("marketAiSummary = answer"));

        String initViews = blockAfter(source, "private void initViews()");
        assertTrue(initViews.contains("R.id.card_market_ai"));
        assertTrue(initViews.contains("EXTRA_MARKET_CONTEXT"));
        assertTrue(initViews.contains("EXTRA_INITIAL_AI_SUMMARY"));
        assertTrue(initViews.contains("专属分析仍在生成，请稍后"));

        assertTrue(source.contains(
                "for (int i = 0; i < currentGame.myShares.size(); i++)"));
        assertTrue(source.contains("\" 份额\""));
        assertFalse(source.contains("else if (s1.compareTo"));
    }

    private static String readUtf8(String path) throws Exception {
        return new String(
                Files.readAllBytes(repoPath(path)),
                StandardCharsets.UTF_8);
    }

    private static String openingTag(
            String source, String elementName, String id) {
        int idIndex = source.indexOf("@+id/" + id);
        assertTrue("Missing XML id: " + id, idIndex >= 0);
        int tagStart = source.lastIndexOf("<" + elementName, idIndex);
        int tagEnd = source.indexOf('>', idIndex);
        assertTrue("Missing <" + elementName + "> for id: " + id,
                tagStart >= 0 && tagEnd > idIndex);
        return source.substring(tagStart, tagEnd + 1);
    }

    private static String buttonBackground(String layout, String buttonId) {
        String buttonTag = openingTag(layout, "Button", buttonId);
        Pattern background = Pattern.compile(
                "android:background\\s*=\\s*\"([^\"]+)\"");
        java.util.regex.Matcher matcher = background.matcher(buttonTag);
        assertTrue("Missing button background for: " + buttonId, matcher.find());
        return matcher.group(1);
    }

    private static int countOccurrences(String source, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
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
