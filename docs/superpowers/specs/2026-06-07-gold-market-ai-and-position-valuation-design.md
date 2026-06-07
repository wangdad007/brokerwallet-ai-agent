# Gold Market AI and Position Valuation Design

## Goal

Improve the gold prediction market experience in three areas:

1. Add a market-specific AI research assistant to every market detail page.
2. Use one consistent visual style for both buy buttons.
3. Display position quantities as shares and calculate BKC position values using the current contract rules.

## Existing Behavior

- The market list has one general DeepSeek research assistant card.
- The market detail page has no market-specific AI summary.
- The YES and NO buy buttons use different backgrounds.
- The position summary adds raw share balances and labels the result as BKC.
- Position cards show the share quantity but do not calculate current BKC value.

## Market-Specific AI Assistant

### Placement

Add the AI research card to the market detail page between the AMM probability/pool panel and the transaction section.

The list-level general assistant remains available for:

- General gold market research.
- DeepSeek API key configuration.

### Request Timing

Request one market-specific AI summary each time the detail activity is created and the market data is available.

- Pull-to-refresh must not trigger another DeepSeek request during the same activity instance.
- Re-entering the detail page creates a new activity and requests a fresh summary.
- If DeepSeek is not configured, the card explains that configuration is available from the general assistant.

### AI Context

The prompt must identify the specific market and include:

- Market ID and title.
- Settlement condition.
- Detailed market description when available.
- YES and NO option names.
- Current AMM-implied YES and NO percentages.
- Total pool in BKC.
- Remaining time and market status.
- Current user's YES and NO share holdings.
- Current gold quote, daily change, source, update time, and delayed-data flag when available.

The prompt must ask for a concise Chinese summary covering:

- Which side currently has stronger evidence.
- The main supporting factors.
- The main risks and uncertainty.
- A clear warning that the output is research assistance rather than guaranteed profit.

### Interaction

- The card initially shows a loading state.
- On success, it shows the market-specific summary.
- On failure, it shows a concise retry/configuration message without blocking trading.
- Clicking the card opens `AIAssistantActivity`.
- The activity receives the same market context and the already generated summary as separate intent extras.
- The existing summary is displayed as the first AI message without making a duplicate DeepSeek request.
- Subsequent user questions retain the market context and trigger normal DeepSeek requests.

## Buy Button Styling

Both `买入 YES` and `买入 NO` use the existing black primary button background, white text, equal dimensions, and matching spacing.

Color remains available in probability labels and bars, but not as a conflicting action hierarchy between the two buy actions.

## Position Quantity and Valuation

### Quantity Labels

All position quantities represent prediction shares and must use the unit `份额`.

This applies to:

- Position cards.
- The market detail page's personal holdings section.
- AI context describing the user's position.

### Unresolved Market Value

For an unresolved, non-refunded market, calculate the estimated BKC liquidation value with the same formula used by the contract's `sellShares` function:

```text
x = reserve of the held option
y = reserve of the opposite option
b = x + y + shareAmount
c = y * shareAmount
returnAmount = (b - sqrt(b^2 - 4c)) / 2
```

`getGameExtraData` swaps reserve order for UI probability display:

- `virtualReserves[0]` contains `reserveNO`.
- `virtualReserves[1]` contains `reserveYES`.

The valuation helper must map these values back to the contract's option reserves before applying the formula:

- YES holding: `x = virtualReserves[1]`, `y = virtualReserves[0]`.
- NO holding: `x = virtualReserves[0]`, `y = virtualReserves[1]`.

The result is an estimate of the amount currently obtainable by selling the full position, before transaction costs.

### Resolved Market Value

Follow the current smart contract settlement rules:

- Winning shares: one whole share is redeemable for one BKC because both values use the same `1e18` base unit and the contract sets `payout = shares`.
- Losing shares: zero BKC.

### Refunded Markets

The current contract exposes an `isRefunded` state but does not provide enough refund-accounting data in `GameModel` to calculate an exact user value. Display the value as unavailable rather than inventing an estimate.

### Multiple Sides

Do not assume a user holds only one side.

- A card must total the values of all positive option balances in that market.
- The detail holdings text must list every positive side and its share quantity.
- The top summary must total every calculable market value.

### Top Summary

The top `个人总持仓估值` remains denominated in BKC, but its value becomes the sum of calculated per-market BKC values instead of the sum of share balances.

If some refunded positions cannot be valued, show the calculable total and make unavailable values explicit on their cards.

## Implementation Boundaries

- Put AMM valuation math in a pure Java helper so it can be unit tested without Android.
- Keep AI prompt construction in a focused helper rather than embedding a long prompt in the activity.
- Reuse the existing `AIAssistantActivity`, `AgentManager.askGoldResearch`, and DeepSeek configuration flow.
- Extend `AIAssistantActivity` to accept context and a pre-generated summary without changing its general-assistant behavior.
- Do not change the smart contract or redeploy it for this work.
- Do not remove the list-level general AI card.

## Testing

Add unit coverage for:

- YES liquidation value using the contract reserve mapping.
- NO liquidation value using the contract reserve mapping.
- Zero shares.
- Invalid or missing reserves.
- Resolved winning and losing positions.
- Multiple-side and multiple-market aggregation.
- Market-specific AI prompt containing market identity, condition, AMM percentages, holdings, and quote metadata.
- UI source/layout assertions that both buy buttons use the same background.
- UI source/layout assertions that the detail page includes the market-specific AI card.
- Position display assertions that share quantities use `份额` rather than BKC.

Run the focused unit test suite and a debug Android build. If a runnable local target is available, visually verify the detail and positions pages.
