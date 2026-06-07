# Gold Market AI and Position Valuation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a market-specific DeepSeek summary to each gold market detail page, unify the YES/NO buy button style, and replace the incorrect share-as-BKC summary with contract-aligned AMM position valuation.

**Architecture:** Add two pure Java logic classes: one owns contract-compatible position valuation, and one builds stable market research context/prompts. Android activities and fragments consume those helpers, while `AIAssistantActivity` gains explicit extras for an existing summary and persistent market context so opening the chat does not duplicate the initial API request.

**Tech Stack:** Android Java 8, Android XML layouts, JUnit 4, Web3j `BigInteger` contract values, existing `AgentManager`/`DeepSeekClient`, Gradle Kotlin DSL.

---

## File Map

- Create `app/src/main/java/com/example/brokerfi/xc/agent/gold/logic/GoldPositionValuation.java`
  - Pure contract-compatible AMM and settlement valuation.
- Create `app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldPositionValuationTest.java`
  - Formula, reserve mapping, settlement, refund, and portfolio aggregation tests.
- Create `app/src/main/java/com/example/brokerfi/xc/agent/gold/logic/GoldMarketResearchPromptBuilder.java`
  - Pure market context and DeepSeek summary prompt construction.
- Create `app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketResearchPromptBuilderTest.java`
  - Market identity, probability, holdings, quote metadata, and follow-up context tests.
- Create `app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketUiContractTest.java`
  - Source/layout contract tests for the detail AI card, button style, extras, and share units.
- Modify `app/src/main/res/layout/activity_gold_market_detail.xml`
  - Insert the market-specific AI card and unify both buy button backgrounds.
- Modify `app/src/main/java/com/example/brokerfi/xc/agent/gold/ui/GoldMarketDetailActivity.java`
  - Request one summary per activity, render all held sides in shares, and open contextual chat.
- Modify `app/src/main/java/com/example/brokerfi/xc/AIAssistantActivity.java`
  - Accept pre-generated summary/context without repeating the summary request.
- Modify `app/src/main/java/com/example/brokerfi/xc/agent/gold/ui/GoldMyPositionsFragment.java`
  - Render share quantities and calculated market/portfolio BKC values.
- Modify `app/src/main/res/layout/item_gold_position_card.xml`
  - Change example quantity text from BKC/ambiguous wording to `份额`.

### Task 1: Contract-Compatible Position Valuation

**Files:**
- Create: `app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldPositionValuationTest.java`
- Create: `app/src/main/java/com/example/brokerfi/xc/agent/gold/logic/GoldPositionValuation.java`

- [ ] **Step 1: Write the failing valuation tests**

Create `GoldPositionValuationTest.java` with focused cases:

```java
package com.example.brokerfi.xc.agent.gold;

import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;
import com.example.brokerfi.xc.agent.gold.logic.GoldPositionValuation;

import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GoldPositionValuationTest {
    @Test
    public void unresolvedYesUsesContractReserveMapping() {
        GoldMarketRepository.GameModel game = game(
                false, false, 0,
                shares("20", "0"),
                shares("150", "100"));

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertTrue(value.isComplete());
        assertEquals(new BigInteger("11610373207469340365"),
                value.getValueWei());
    }

    @Test
    public void unresolvedNoUsesContractReserveMapping() {
        GoldMarketRepository.GameModel game = game(
                false, false, 0,
                shares("0", "20"),
                shares("150", "100"));

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertTrue(value.isComplete());
        assertEquals(new BigInteger("7622607971430032108"),
                value.getValueWei());
    }

    @Test
    public void resolvedMarketUsesCurrentContractPayoutRule() {
        GoldMarketRepository.GameModel game = game(
                true, false, 0,
                shares("7", "5"),
                shares("150", "100"));

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertTrue(value.isComplete());
        assertEquals(wei("7"), value.getValueWei());
    }

    @Test
    public void refundedPositionIsMarkedUnavailable() {
        GoldMarketRepository.GameModel game = game(
                false, true, 0,
                shares("3", "0"),
                shares("150", "100"));

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertFalse(value.isComplete());
        assertEquals(BigInteger.ZERO, value.getValueWei());
    }

    @Test
    public void marketTotalsBothHeldSides() {
        GoldMarketRepository.GameModel game = game(
                false, false, 0,
                shares("20", "20"),
                shares("150", "100"));

        assertEquals(new BigInteger("19232981178899372473"),
                GoldPositionValuation.calculateMarket(game).getValueWei());
    }

    @Test
    public void zeroSharesHaveZeroCompleteValue() {
        GoldMarketRepository.GameModel game = game(
                false, false, 0,
                shares("0", "0"),
                shares("150", "100"));

        GoldPositionValuation.MarketValue value =
                GoldPositionValuation.calculateMarket(game);

        assertTrue(value.isComplete());
        assertEquals(BigInteger.ZERO, value.getValueWei());
    }

    @Test
    public void missingReservesMakeActivePositionUnavailable() {
        GoldMarketRepository.GameModel game = game(
                false, false, 0,
                shares("3", "0"),
                null);

        assertFalse(GoldPositionValuation.calculateMarket(game)
                .isComplete());
    }

    @Test
    public void portfolioSumsCalculableMarketsAndCountsUnavailableMarkets() {
        GoldMarketRepository.GameModel active = game(
                false, false, 0,
                shares("20", "0"),
                shares("150", "100"));
        GoldMarketRepository.GameModel resolved = game(
                true, false, 1,
                shares("7", "5"),
                shares("150", "100"));
        GoldMarketRepository.GameModel refunded = game(
                false, true, 0,
                shares("3", "0"),
                shares("150", "100"));

        GoldPositionValuation.PortfolioValue value =
                GoldPositionValuation.calculatePortfolio(
                        Arrays.asList(active, resolved, refunded));

        assertEquals(new BigInteger("16610373207469340365"),
                value.getValueWei());
        assertEquals(1, value.getUnavailableMarketCount());
    }

    private static GoldMarketRepository.GameModel game(
            boolean resolved,
            boolean refunded,
            int winner,
            java.util.List<BigInteger> myShares,
            java.util.List<BigInteger> virtualReserves) {
        GoldMarketRepository.GameModel game = new GoldMarketRepository.GameModel();
        game.optionCount = 2;
        game.optionNames = Arrays.asList("YES", "NO");
        game.isResolved = resolved;
        game.isRefunded = refunded;
        game.winningOption = winner;
        game.myShares = myShares;
        game.virtualReserves = virtualReserves;
        return game;
    }

    private static java.util.List<BigInteger> shares(String yes, String no) {
        return Arrays.asList(wei(yes), wei(no));
    }

    private static BigInteger wei(String whole) {
        return new BigInteger(whole).multiply(
                new BigInteger("1000000000000000000"));
    }
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.brokerfi.xc.agent.gold.GoldPositionValuationTest"
```

Expected: compilation fails because `GoldPositionValuation` does not exist.

- [ ] **Step 3: Implement the pure valuation helper**

Create `GoldPositionValuation.java` with:

```java
package com.example.brokerfi.xc.agent.gold.logic;

import com.example.brokerfi.xc.agent.gold.data.GoldMarketRepository;

import java.math.BigInteger;
import java.util.List;

public final class GoldPositionValuation {
    private GoldPositionValuation() {}

    public static MarketValue calculateMarket(
            GoldMarketRepository.GameModel game) {
        if (game == null || game.myShares == null) {
            return new MarketValue(BigInteger.ZERO, false);
        }

        boolean hasShares = false;
        for (BigInteger shares : game.myShares) {
            if (positive(shares)) {
                hasShares = true;
                break;
            }
        }
        if (!hasShares) {
            return new MarketValue(BigInteger.ZERO, true);
        }
        if (game.isRefunded) {
            return new MarketValue(BigInteger.ZERO, false);
        }

        BigInteger total = BigInteger.ZERO;
        if (game.isResolved) {
            if (game.winningOption >= 0
                    && game.winningOption < game.myShares.size()) {
                BigInteger winningShares =
                        game.myShares.get(game.winningOption);
                if (positive(winningShares)) {
                    total = winningShares;
                }
            }
            return new MarketValue(total, true);
        }

        if (game.virtualReserves == null
                || game.virtualReserves.size() < 2
                || game.myShares.size() < 2) {
            return new MarketValue(BigInteger.ZERO, false);
        }

        for (int option = 0; option < 2; option++) {
            BigInteger shareAmount = game.myShares.get(option);
            if (!positive(shareAmount)) continue;

            BigInteger x = option == 0
                    ? game.virtualReserves.get(1)
                    : game.virtualReserves.get(0);
            BigInteger y = option == 0
                    ? game.virtualReserves.get(0)
                    : game.virtualReserves.get(1);
            BigInteger optionValue = estimateSellReturn(x, y, shareAmount);
            if (optionValue == null) {
                return new MarketValue(BigInteger.ZERO, false);
            }
            total = total.add(optionValue);
        }
        return new MarketValue(total, true);
    }

    public static PortfolioValue calculatePortfolio(
            List<GoldMarketRepository.GameModel> games) {
        BigInteger total = BigInteger.ZERO;
        int unavailable = 0;
        if (games != null) {
            for (GoldMarketRepository.GameModel game : games) {
                MarketValue market = calculateMarket(game);
                if (market.isComplete()) {
                    total = total.add(market.getValueWei());
                } else {
                    unavailable++;
                }
            }
        }
        return new PortfolioValue(total, unavailable);
    }

    static BigInteger estimateSellReturn(
            BigInteger x, BigInteger y, BigInteger shareAmount) {
        if (!positive(x) || !positive(y) || !positive(shareAmount)) {
            return null;
        }
        BigInteger b = x.add(y).add(shareAmount);
        BigInteger c = y.multiply(shareAmount);
        BigInteger discriminant =
                b.multiply(b).subtract(c.multiply(BigInteger.valueOf(4)));
        if (discriminant.signum() < 0) return null;
        return b.subtract(sqrt(discriminant))
                .divide(BigInteger.valueOf(2));
    }

    private static BigInteger sqrt(BigInteger value) {
        if (value.signum() <= 0) return BigInteger.ZERO;
        BigInteger z = value;
        BigInteger x = value.shiftRight(1).add(BigInteger.ONE);
        while (x.compareTo(z) < 0) {
            z = x;
            x = value.divide(x).add(x).shiftRight(1);
        }
        return z;
    }

    private static boolean positive(BigInteger value) {
        return value != null && value.signum() > 0;
    }

    public static final class MarketValue {
        private final BigInteger valueWei;
        private final boolean complete;

        MarketValue(BigInteger valueWei, boolean complete) {
            this.valueWei = valueWei;
            this.complete = complete;
        }

        public BigInteger getValueWei() {
            return valueWei;
        }

        public boolean isComplete() {
            return complete;
        }
    }

    public static final class PortfolioValue {
        private final BigInteger valueWei;
        private final int unavailableMarketCount;

        PortfolioValue(BigInteger valueWei, int unavailableMarketCount) {
            this.valueWei = valueWei;
            this.unavailableMarketCount = unavailableMarketCount;
        }

        public BigInteger getValueWei() {
            return valueWei;
        }

        public int getUnavailableMarketCount() {
            return unavailableMarketCount;
        }
    }
}
```

- [ ] **Step 4: Run the valuation tests and verify GREEN**

Run the same focused command. Expected: all eight tests pass.

- [ ] **Step 5: Commit the valuation helper**

```powershell
git add app/src/main/java/com/example/brokerfi/xc/agent/gold/logic/GoldPositionValuation.java app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldPositionValuationTest.java
git commit -m "feat: calculate prediction position values"
```

### Task 2: Stable Market Research Context and Prompt

**Files:**
- Create: `app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketResearchPromptBuilderTest.java`
- Create: `app/src/main/java/com/example/brokerfi/xc/agent/gold/logic/GoldMarketResearchPromptBuilder.java`

- [ ] **Step 1: Write failing prompt tests**

Test that the context contains:

```java
assertTrue(context.contains("博弈池 #7"));
assertTrue(context.contains("结算条件: 金价曾触及 2500 USD"));
assertTrue(context.contains("YES 概率: 60.0%"));
assertTrue(context.contains("NO 概率: 40.0%"));
assertTrue(context.contains("总池子: 300.00 BKC"));
assertTrue(context.contains("YES 2.5 份额"));
assertTrue(context.contains("黄金现价: 2388.50 USD"));
assertTrue(context.contains("行情来源: gold-api.com"));
assertTrue(context.contains("延迟行情: 是"));
```

Also test:

```java
assertTrue(GoldMarketResearchPromptBuilder.buildSummaryPrompt(context)
        .contains("只分析这个博弈池"));
assertEquals(
        context + "\n\n【用户追问】\n现在买 YES 风险大吗？",
        GoldMarketResearchPromptBuilder.withFollowUp(
                context, "现在买 YES 风险大吗？"));
```

Construct a `GameModel` with ID `7`, title, condition, pool, deadline, option names, reserves `60/40`, and YES shares `2.5e18`. Construct an `Advisory` with price, change, source, update time, and `quoteDelayed = true`.

- [ ] **Step 2: Run the prompt tests and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.brokerfi.xc.agent.gold.GoldMarketResearchPromptBuilderTest"
```

Expected: compilation fails because the builder does not exist.

- [ ] **Step 3: Implement the prompt builder**

Create a pure Java final class with these public methods:

```java
public static String buildContext(
        GoldMarketRepository.GameModel game,
        long nowMillis,
        GoldAdvisoryManager.Advisory quote)

public static String buildSummaryPrompt(String context)

public static String withFollowUp(String context, String question)
```

Implementation requirements:

- Compute YES probability as `virtualReserves[0] / sum`, matching the current detail UI and contract's swapped `getGameExtraData` return.
- Format pool values with two decimal BKC places using a local `1e18` constant.
- Format positive holdings for every option as `optionName amount 份额`.
- Normalize deadline seconds/milliseconds with the same threshold used by `GoldNoteMarketActivity.remainingSecondsUntilDeadline`.
- Include `"行情数据不可用"` when `quote` is null or has no positive price.
- `buildSummaryPrompt` appends this exact analysis contract:

```text
请只分析这个博弈池，用中文给出不超过120字的摘要：
1. 当前哪一侧证据更强；
2. 两个主要依据；
3. 最大风险与不确定性；
4. 明确说明这只是投研辅助，不保证收益。
```

- `withFollowUp` returns the unchanged question when context is blank; otherwise return `context + "\n\n【用户追问】\n" + question`.

- [ ] **Step 4: Run prompt tests and verify GREEN**

Run the focused prompt test command. Expected: all tests pass.

- [ ] **Step 5: Commit the prompt helper**

```powershell
git add app/src/main/java/com/example/brokerfi/xc/agent/gold/logic/GoldMarketResearchPromptBuilder.java app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketResearchPromptBuilderTest.java
git commit -m "feat: build market-specific AI research context"
```

### Task 3: Contextual Chat Without a Duplicate Summary Request

**Files:**
- Create: `app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketUiContractTest.java`
- Modify: `app/src/main/java/com/example/brokerfi/xc/AIAssistantActivity.java`

- [ ] **Step 1: Write the failing source contract test**

Create a test that reads `AIAssistantActivity.java` and asserts:

```java
assertTrue(source.contains("EXTRA_MARKET_CONTEXT"));
assertTrue(source.contains("EXTRA_INITIAL_AI_SUMMARY"));
assertTrue(source.contains("addMessage(\"AI\", initialSummary)"));
assertTrue(source.contains(
        "GoldMarketResearchPromptBuilder.withFollowUp"));
```

Keep the existing `INITIAL_PROMPT` branch covered with:

```java
assertTrue(source.contains("INITIAL_PROMPT"));
```

- [ ] **Step 2: Run the UI contract test and verify RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.brokerfi.xc.agent.gold.GoldMarketUiContractTest"
```

Expected: the new-extra assertions fail.

- [ ] **Step 3: Add explicit chat extras and summary reuse**

In `AIAssistantActivity` add:

```java
public static final String EXTRA_MARKET_CONTEXT = "MARKET_CONTEXT";
public static final String EXTRA_INITIAL_AI_SUMMARY = "INITIAL_AI_SUMMARY";
```

After the welcome message and listeners:

```java
marketContext = getIntent().getStringExtra(EXTRA_MARKET_CONTEXT);
String initialSummary =
        getIntent().getStringExtra(EXTRA_INITIAL_AI_SUMMARY);
if (!TextUtils.isEmpty(initialSummary)) {
    addMessage("AI", initialSummary);
}

String initialPrompt = getIntent().getStringExtra("INITIAL_PROMPT");
if (TextUtils.isEmpty(initialSummary)
        && !TextUtils.isEmpty(initialPrompt)) {
    marketContext = initialPrompt;
    submitQuestion(initialPrompt);
}
```

Replace the body of `buildQuestionForAi` with:

```java
return GoldMarketResearchPromptBuilder.withFollowUp(
        marketContext, text);
```

This preserves general-assistant behavior and prevents a second summary request when the detail page already generated one.

- [ ] **Step 4: Run the UI contract and prompt tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.brokerfi.xc.agent.gold.GoldMarketUiContractTest" --tests "com.example.brokerfi.xc.agent.gold.GoldMarketResearchPromptBuilderTest"
```

Expected: both classes pass.

- [ ] **Step 5: Commit contextual chat support**

```powershell
git add app/src/main/java/com/example/brokerfi/xc/AIAssistantActivity.java app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketUiContractTest.java
git commit -m "feat: continue market AI summaries in chat"
```

### Task 4: Detail Page AI Card, One-Shot Summary, and Unified Buttons

**Files:**
- Modify: `app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketUiContractTest.java`
- Modify: `app/src/main/res/layout/activity_gold_market_detail.xml`
- Modify: `app/src/main/java/com/example/brokerfi/xc/agent/gold/ui/GoldMarketDetailActivity.java`

- [ ] **Step 1: Extend the failing UI contract tests**

Read `activity_gold_market_detail.xml` and assert:

```java
assertTrue(layout.contains("@+id/card_market_ai"));
assertTrue(layout.contains("@+id/tv_market_ai_summary"));
assertEquals(
        buttonBackground(layout, "btn_buy_up"),
        buttonBackground(layout, "btn_buy_down"));
assertEquals("@drawable/custom_button_background",
        buttonBackground(layout, "btn_buy_up"));
```

Implement `buttonBackground` in the test by locating the `<Button` block containing the target ID and extracting the `android:background` attribute before the closing `/>`.

Read `GoldMarketDetailActivity.java` and assert:

```java
assertTrue(source.contains("marketAiRequested"));
assertTrue(source.contains("requestMarketAiSummaryOnce"));
assertTrue(source.contains("EXTRA_INITIAL_AI_SUMMARY"));
assertTrue(source.contains("\" 份额\""));
```

- [ ] **Step 2: Run the UI contract test and verify RED**

Run the focused UI contract test. Expected: detail-card, button, and activity assertions fail.

- [ ] **Step 3: Insert the AI card and unify button XML**

Between the probability/pool panel and the `交易` heading, add a clickable vertical `LinearLayout`:

```xml
<LinearLayout
    android:id="@+id/card_market_ai"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginTop="16dp"
    android:background="@drawable/bg_gold_market_panel"
    android:clickable="true"
    android:focusable="true"
    android:foreground="?attr/selectableItemBackground"
    android:orientation="vertical"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="AI 针对此池分析"
            android:textColor="#000000"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/tv_market_ai_status"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="DeepSeek ›"
            android:textColor="#666666"
            android:textSize="13sp" />
    </LinearLayout>

    <TextView
        android:id="@+id/tv_market_ai_summary"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:text="正在准备该博弈池的专属分析..."
        android:textColor="#666666"
        android:textSize="14sp" />
</LinearLayout>
```

Change `btn_buy_down` from `@drawable/custom_red_background` to `@drawable/custom_button_background`.

- [ ] **Step 4: Wire one summary request per activity**

In `GoldMarketDetailActivity`:

- Initialize `DeepSeekClient` in `onCreate`.
- Bind `card_market_ai`, `tv_market_ai_status`, and `tv_market_ai_summary`.
- Add fields:

```java
private boolean marketAiRequested = false;
private String marketAiContext = "";
private String marketAiSummary = "";
```

- Call `requestMarketAiSummaryOnce()` after `currentGame = model; updateUI();`.
- Guard the request before any asynchronous work:

```java
if (marketAiRequested || currentGame == null) return;
marketAiRequested = true;
```

- If DeepSeek is not configured, show:

```text
请先在博弈池列表顶部的总 AI 助手中配置 DeepSeek API Key。
```

- Otherwise call `GoldAdvisoryManager.fetchPrice`. Both success and error paths must build market context; the error path passes `null` quote.
- Send `GoldMarketResearchPromptBuilder.buildSummaryPrompt(marketAiContext)` through `AgentManager.getInstance().askGoldResearch`.
- Post callback UI updates with `runOnUiThread`, set `marketAiSummary`, and keep failures non-blocking.
- Do not reset `marketAiRequested` in pull-to-refresh or transaction refresh paths.

Card click behavior:

```java
if (marketAiContext.isEmpty() || marketAiSummary.isEmpty()) {
    Toast.makeText(this, "专属分析仍在生成，请稍后", Toast.LENGTH_SHORT).show();
    return;
}
Intent intent = new Intent(this, AIAssistantActivity.class);
intent.putExtra(AIAssistantActivity.EXTRA_MARKET_CONTEXT,
        marketAiContext);
intent.putExtra(AIAssistantActivity.EXTRA_INITIAL_AI_SUMMARY,
        marketAiSummary);
startActivity(intent);
```

- [ ] **Step 5: Render every positive holding in shares**

Replace the current `if/else if` holdings block with a loop:

```java
StringBuilder holdings = new StringBuilder();
for (int i = 0; i < currentGame.myShares.size(); i++) {
    BigInteger shares = currentGame.myShares.get(i);
    if (shares == null || shares.signum() <= 0) continue;
    if (holdings.length() > 0) holdings.append('\n');
    holdings.append(currentGame.optionNames.get(i))
            .append(": ")
            .append(GoldNoteMarketActivity.formatShareAmount(shares))
            .append(" 份额");
}
tvHoldings.setText(holdings.length() == 0
        ? "暂无持仓"
        : holdings.toString());
```

- [ ] **Step 6: Run focused tests and compile**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.brokerfi.xc.agent.gold.GoldMarketUiContractTest" --tests "com.example.brokerfi.xc.agent.gold.GoldMarketResearchPromptBuilderTest"
.\gradlew.bat compileDebugJavaWithJavac
```

Expected: tests pass and Java compilation succeeds.

- [ ] **Step 7: Commit detail-page behavior**

```powershell
git add app/src/main/res/layout/activity_gold_market_detail.xml app/src/main/java/com/example/brokerfi/xc/agent/gold/ui/GoldMarketDetailActivity.java app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketUiContractTest.java
git commit -m "feat: add market-specific AI research card"
```

### Task 5: Position Cards and Portfolio BKC Valuation

**Files:**
- Modify: `app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketUiContractTest.java`
- Modify: `app/src/main/java/com/example/brokerfi/xc/agent/gold/ui/GoldMyPositionsFragment.java`
- Modify: `app/src/main/res/layout/item_gold_position_card.xml`

- [ ] **Step 1: Add failing position UI contract assertions**

Assert:

```java
assertTrue(cardLayout.contains("150.00 份额"));
assertTrue(fragmentSource.contains(
        "GoldPositionValuation.calculateMarket"));
assertTrue(fragmentSource.contains(
        "GoldPositionValuation.calculatePortfolio"));
assertTrue(fragmentSource.contains("\" 份额\""));
assertFalse(fragmentSource.contains("totalInvested"));
```

- [ ] **Step 2: Run the UI contract test and verify RED**

Expected: the valuation calls and removal of `totalInvested` fail.

- [ ] **Step 3: Render all held sides and each market's current value**

In `renderPositions`, replace the first-positive-side assumption with:

```java
List<String> sideNames = new ArrayList<>();
StringBuilder shareText = new StringBuilder();
for (int i = 0; i < game.myShares.size(); i++) {
    BigInteger shares = game.myShares.get(i);
    if (shares == null || shares.signum() <= 0) continue;
    String sideName = game.optionNames.get(i);
    sideNames.add(sideName);
    if (shareText.length() > 0) shareText.append('\n');
    shareText.append(sideName)
            .append(' ')
            .append(GoldNoteMarketActivity.formatShareAmount(shares))
            .append(" 份额");
}
tvSide.setText(android.text.TextUtils.join(" / ", sideNames));
tvShares.setText(shareText);
```

Calculate the card value:

```java
GoldPositionValuation.MarketValue marketValue =
        GoldPositionValuation.calculateMarket(game);
tvCurrentValue.setText(marketValue.isComplete()
        ? GoldNoteMarketActivity.formatBkc(
                marketValue.getValueWei()) + " BKC"
        : "暂不可估值");
tvProfit.setText(game.isResolved ? "已结算" : "AMM估值");
```

Keep side coloring deterministic:

- Green when every displayed side is YES/up.
- Red when every displayed side is NO/down.
- Black when both sides are held.

- [ ] **Step 4: Replace the incorrect top summary**

Replace raw share addition with:

```java
GoldPositionValuation.PortfolioValue portfolio =
        GoldPositionValuation.calculatePortfolio(myPositions);
BigDecimal totalBkc = new BigDecimal(portfolio.getValueWei())
        .divide(new BigDecimal("1000000000000000000"),
                6, RoundingMode.HALF_UP);
animateBalance(totalBkc.doubleValue());

String subtitle = String.format(Locale.getDefault(),
        "累计参与 %d 个博弈池", myPositions.size());
if (portfolio.getUnavailableMarketCount() > 0) {
    subtitle += String.format(Locale.getDefault(),
            " · %d 个退款持仓未计入",
            portfolio.getUnavailableMarketCount());
}
tvTotalPnl.setText(subtitle);
```

- [ ] **Step 5: Update the position card example unit**

Change the `tv_shares` example text in `item_gold_position_card.xml` to:

```xml
android:text="150.00 份额"
```

Keep `tv_current_value` in BKC because it is a calculated liquidation/settlement value.

- [ ] **Step 6: Run valuation and UI tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.brokerfi.xc.agent.gold.GoldPositionValuationTest" --tests "com.example.brokerfi.xc.agent.gold.GoldMarketUiContractTest"
```

Expected: both test classes pass.

- [ ] **Step 7: Commit position valuation UI**

```powershell
git add app/src/main/java/com/example/brokerfi/xc/agent/gold/ui/GoldMyPositionsFragment.java app/src/main/res/layout/item_gold_position_card.xml app/src/test/java/com/example/brokerfi/xc/agent/gold/GoldMarketUiContractTest.java
git commit -m "fix: value prediction holdings with AMM math"
```

### Task 6: Full Verification and Visual Check

**Files:**
- Verify all files changed by Tasks 1-5.

- [ ] **Step 1: Run the complete unit suite**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Build the debug APK**

```powershell
.\gradlew.bat assembleDebug
```

Expected: `BUILD SUCCESSFUL` and
`app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: Check for an attached Android target**

```powershell
adb devices
```

If a device/emulator is listed as `device`, install and launch:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.example.brokerfi -c android.intent.category.LAUNCHER 1
```

Verify:

- General AI card still appears on the market list.
- Detail AI card appears between probability data and transaction controls.
- YES/NO buttons have identical black styling.
- Detail holdings use `份额`.
- Position card current values and top total are BKC values, not raw shares.
- Pull-to-refresh does not change the card back to loading or issue another summary request in the same detail activity.
- Clicking a completed summary opens chat with the summary already visible.

If no Android target is attached, record that device-level visual verification was unavailable; do not claim it ran.

- [ ] **Step 4: Review the final diff**

```powershell
git diff HEAD~5 --check
git status --short
```

Confirm no `.gradle`, `.idea`, `app/build`, `local.properties`, or `.superpowers` files are staged.

- [ ] **Step 5: Commit any verification-only corrections**

Only if verification required source corrections:

Stage each corrected source or test path explicitly, then run:

```powershell
git commit -m "fix: address gold market verification issues"
```

Do not commit generated build outputs or the user's pre-existing local files.
