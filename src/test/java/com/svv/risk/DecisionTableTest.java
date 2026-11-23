package com.svv.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decision Table Testing (DT) with eight executable business-rule scenarios (R1-R8).
 * Every scenario reaches its target account state strictly through valid domain operations.
 * Requirements: FR-03, FR-04, FR-05, FR-06, FR-10, FR-11, FR-12, FR-13.
 */
@DisplayName("Decision Table Tests (DT: Rules R1-R8)")
class DecisionTableTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 23);

    @Test
    @DisplayName("DT-R1: Unlocked | NORMAL | Day Match | Risk $250.00 <= $250.00 (Nominal) -> APPROVED")
    void rule1_normalUnlockedWithinNominalCap_approved() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);

        TradeDecision decision = account.proposeTrade(
                new TradeRequest("R1-T1", new BigDecimal("250.00"), new BigDecimal("2.00"), DAY_1));

        assertAll(
                () -> assertTrue(decision.approved()),
                () -> assertEquals(RiskMode.NORMAL, decision.riskMode()),
                () -> assertEquals(new BigDecimal("250.00"), decision.maxAllowedRisk()));
    }

    @Test
    @DisplayName("DT-R2: Unlocked | GROWTH | Day Match | Risk $382.50 <= $382.50 (Nominal) -> APPROVED")
    void rule2_growthUnlockedWithinNominalCap_approved() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);
        // Win $1,000.00 to reach $51,000.00 (+2.00% -> GROWTH)
        executeTrade(account, "SETUP-G1", "250.00", "4.00", DAY_1, TradeOutcome.WIN);

        TradeDecision decision = account.proposeTrade(
                new TradeRequest("R2-T1", new BigDecimal("382.50"), new BigDecimal("2.00"), DAY_1));

        assertAll(
                () -> assertTrue(decision.approved()),
                () -> assertEquals(RiskMode.GROWTH, decision.riskMode()),
                () -> assertEquals(new BigDecimal("382.50"), decision.maxAllowedRisk()));
    }

    @Test
    @DisplayName("DT-R3: Unlocked | NORMAL | Day Match | Risk $247.90 > $80.00 (Daily Cap) -> REJECTED")
    void rule3_normalDailyCapacityTighterThanNominal_rejected() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);
        // Lose $250.00 then $170.00 -> balance $49,580.00 (remaining daily = $80.00, nominal = $247.90)
        executeTrade(account, "SETUP-L1", "250.00", "2.00", DAY_1, TradeOutcome.LOSS);
        executeTrade(account, "SETUP-L2", "170.00", "2.00", DAY_1, TradeOutcome.LOSS);

        TradeDecision decision = account.proposeTrade(
                new TradeRequest("R3-T1", new BigDecimal("247.90"), new BigDecimal("2.00"), DAY_1));

        assertAll(
                () -> assertFalse(decision.approved()),
                () -> assertEquals(RiskMode.NORMAL, decision.riskMode()),
                () -> assertEquals(new BigDecimal("80.00"), decision.maxAllowedRisk()),
                () -> assertTrue(decision.reason().contains("remaining daily capacity")));
    }

    @Test
    @DisplayName("DT-R4: Unlocked | SAFE | Day Match | Risk $122.50 <= $122.50 (Nominal) -> APPROVED")
    void rule4_safeUnlockedWithinNominalCap_approved() {
        TradingAccount account = createSafeModeAccount();
        LocalDate day3 = account.getStatus().tradingDay();

        TradeDecision decision = account.proposeTrade(
                new TradeRequest("R4-T1", new BigDecimal("122.50"), new BigDecimal("2.00"), day3));

        assertAll(
                () -> assertTrue(decision.approved()),
                () -> assertEquals(RiskMode.SAFE, decision.riskMode()),
                () -> assertEquals(new BigDecimal("122.50"), decision.maxAllowedRisk()));
    }

    @Test
    @DisplayName("DT-R5: Unlocked | RECOVERY (overrides GROWTH) | Day Match | Risk $60.00 > $51.00 -> REJECTED")
    void rule5_recoveryOverridesGrowthAndRejectsExcessRisk() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);
        // Reach $51,030.00 (+2.06% GROWTH), then incur 3 small losses -> $51,000.00 with streak 3 (RECOVERY)
        executeTrade(account, "SETUP-W1", "250.00", "4.12", DAY_1, TradeOutcome.WIN);
        executeTrade(account, "SETUP-L1", "10.00", "2.00", DAY_1, TradeOutcome.LOSS);
        executeTrade(account, "SETUP-L2", "10.00", "2.00", DAY_1, TradeOutcome.LOSS);
        executeTrade(account, "SETUP-L3", "10.00", "2.00", DAY_1, TradeOutcome.LOSS);

        TradeDecision decision = account.proposeTrade(
                new TradeRequest("R5-T1", new BigDecimal("60.00"), new BigDecimal("2.00"), DAY_1));

        assertAll(
                () -> assertFalse(decision.approved()),
                () -> assertEquals(RiskMode.RECOVERY, decision.riskMode()),
                () -> assertEquals(new BigDecimal("51.00"), decision.maxAllowedRisk()),
                () -> assertTrue(decision.reason().contains("RECOVERY")));
    }

    @Test
    @DisplayName("DT-R6: Unlocked | NORMAL | Day Mismatch | Risk $100.00 <= $250.00 -> REJECTED")
    void rule6_unlockedWrongTradingDay_rejected() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);

        TradeDecision decision = account.proposeTrade(
                new TradeRequest("R6-T1", new BigDecimal("100.00"), new BigDecimal("2.00"), DAY_1.plusDays(1)));

        assertAll(
                () -> assertFalse(decision.approved()),
                () -> assertTrue(decision.reason().contains("does not match active trading day")));
    }

    @Test
    @DisplayName("DT-R7: Daily Locked | LOCKED | Day Match | Risk $10.00 > $0.00 -> REJECTED")
    void rule7_dailyLockedRejectsAnyTrade() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);
        executeTrade(account, "SETUP-L1", "250.00", "2.00", DAY_1, TradeOutcome.LOSS);
        executeTrade(account, "SETUP-L2", "248.75", "2.00", DAY_1, TradeOutcome.LOSS);
        executeTrade(account, "SETUP-L3", "1.25", "2.00", DAY_1, TradeOutcome.LOSS);

        TradeDecision decision = account.proposeTrade(
                new TradeRequest("R7-T1", new BigDecimal("10.00"), new BigDecimal("2.00"), DAY_1));

        assertAll(
                () -> assertFalse(decision.approved()),
                () -> assertEquals(RiskMode.LOCKED, decision.riskMode()),
                () -> assertEquals(new BigDecimal("0.00"), decision.maxAllowedRisk()),
                () -> assertTrue(decision.reason().contains("daily loss floor")));
    }

    @Test
    @DisplayName("DT-R8: Permanent Overall Locked (after nextDay) | LOCKED | Day Match -> REJECTED")
    void rule8_overallLockedPersistsAcrossDaysAndRejectsTrade() {
        TradingAccount account = createOverallLockedAccount();
        LocalDate day5 = account.getStatus().tradingDay().plusDays(1);
        account.startNewTradingDay(day5);

        TradeDecision decision = account.proposeTrade(
                new TradeRequest("R8-T1", new BigDecimal("10.00"), new BigDecimal("2.00"), day5));

        assertAll(
                () -> assertFalse(decision.approved()),
                () -> assertEquals(RiskMode.LOCKED, decision.riskMode()),
                () -> assertEquals(new BigDecimal("0.00"), decision.maxAllowedRisk()),
                () -> assertTrue(account.getStatus().overallLocked()),
                () -> assertTrue(decision.reason().contains("permanently locked")));
    }

    static TradingAccount createSafeModeAccount() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);
        // Day 1: $50,000.00 -> $49,510.00 (unlocked, 2 losses)
        executeTrade(account, "S-D1-1", "250.00", "2.00", DAY_1, TradeOutcome.LOSS);
        executeTrade(account, "S-D1-2", "240.00", "2.00", DAY_1, TradeOutcome.LOSS);

        // Day 2: reset streak with $1 win, then lose down to $49,017.14 (above daily floor $49,014.90)
        LocalDate day2 = DAY_1.plusDays(1);
        account.startNewTradingDay(day2);
        executeTrade(account, "S-D2-W", "1.00", "1.00", day2, TradeOutcome.WIN);
        executeTrade(account, "S-D2-1", "247.55", "2.00", day2, TradeOutcome.LOSS);
        executeTrade(account, "S-D2-2", "246.31", "2.00", day2, TradeOutcome.LOSS);

        // Day 3: reset streak, lose $18.14 -> exact $49,000.00 (-2.00%, streak=1 -> SAFE)
        LocalDate day3 = DAY_1.plusDays(2);
        account.startNewTradingDay(day3);
        executeTrade(account, "S-D3-W", "1.00", "1.00", day3, TradeOutcome.WIN);
        executeTrade(account, "S-D3-1", "18.14", "2.00", day3, TradeOutcome.LOSS);
        return account;
    }

    static TradingAccount createOverallLockedAccount() {
        TradingAccount account = createSafeModeAccount();
        LocalDate day3 = account.getStatus().tradingDay();
        // Day 3 daily floor = $48,526.97; step down cleanly to daily lock at $48,526.97
        executeTrade(account, "O-D3-1", "122.50", "2.00", day3, TradeOutcome.LOSS);
        executeTrade(account, "O-D3-2", "122.19", "2.00", day3, TradeOutcome.LOSS);
        executeTrade(account, "O-D3-W", "1.00", "1.00", day3, TradeOutcome.WIN);
        executeTrade(account, "O-D3-3", "121.89", "2.00", day3, TradeOutcome.LOSS);
        executeTrade(account, "O-D3-4", "107.45", "2.00", day3, TradeOutcome.LOSS);

        // Day 4: remaining overall capacity is $26.97 -> final exact loss hits $48,500.00
        LocalDate day4 = day3.plusDays(1);
        account.startNewTradingDay(day4);
        executeTrade(account, "O-D4-1", "26.97", "2.00", day4, TradeOutcome.LOSS);
        return account;
    }

    private static void executeTrade(
            TradingAccount account,
            String id,
            String risk,
            String rr,
            LocalDate day,
            TradeOutcome outcome) {
        TradeDecision decision = account.proposeTrade(
                new TradeRequest(id, new BigDecimal(risk), new BigDecimal(rr), day));
        assertTrue(decision.approved(), "Setup trade " + id + " failed: " + decision.reason());
        account.settleTrade(id, outcome);
    }
}
