package com.svv.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Permanent Regression Test Suite (RT) protecting five high-risk domain invariants
 * against regressions during refactoring or policy updates.
 * Requirements: FR-04, FR-06, FR-09, FR-10, FR-11, FR-12, FR-13.
 */
@DisplayName("Permanent Regression Tests (RT)")
class RegressionTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 23);

    @Test
    @DisplayName("TC-RG-01 [RT][FR-11]: Exact equality at $49,500.00 daily floor must activate daily lock")
    void regression_exactDailyFloorEqualityActivatesDailyLock() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);
        approveAndSettle(account, "RG1-1", "250.00", "2.00", DAY_1, TradeOutcome.LOSS);
        approveAndSettle(account, "RG1-2", "248.75", "2.00", DAY_1, TradeOutcome.LOSS);
        approveAndSettle(account, "RG1-3", "1.25", "2.00", DAY_1, TradeOutcome.LOSS);

        AccountStatus status = account.getStatus();
        TradeDecision blocked = account.proposeTrade(
                new TradeRequest("RG1-4", new BigDecimal("0.01"), new BigDecimal("2.00"), DAY_1));

        assertAll(
                () -> assertEquals(new BigDecimal("49500.00"), status.currentBalance()),
                () -> assertTrue(status.dailyLocked()),
                () -> assertEquals(RiskMode.LOCKED, status.riskMode()),
                () -> assertEquals(new BigDecimal("0.00"), status.maxAllowedRisk()),
                () -> assertFalse(blocked.approved()));
    }

    @Test
    @DisplayName("TC-RG-02 [RT][FR-12]: Exact equality at $48,500.00 overall floor permanently locks across days")
    void regression_exactOverallFloorEqualityActivatesPermanentLock() {
        TradingAccount account = DecisionTableTest.createOverallLockedAccount();
        LocalDate nextDay = account.getStatus().tradingDay().plusDays(1);
        account.startNewTradingDay(nextDay);

        AccountStatus status = account.getStatus();
        TradeDecision blocked = account.proposeTrade(
                new TradeRequest("RG2-POST", new BigDecimal("0.01"), new BigDecimal("2.00"), nextDay));

        assertAll(
                () -> assertEquals(new BigDecimal("48500.00"), status.currentBalance()),
                () -> assertTrue(status.overallLocked()),
                () -> assertEquals(RiskMode.LOCKED, status.riskMode()),
                () -> assertEquals(new BigDecimal("0.00"), status.maxAllowedRisk()),
                () -> assertFalse(blocked.approved()));
    }

    @Test
    @DisplayName("TC-RG-03 [RT][FR-10/FR-13]: Recovery losing streak persists across days and resets on WIN")
    void regression_recoveryStreakPersistsAcrossDaysAndResetsOnWin() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);
        approveAndSettle(account, "RG3-1", "100.00", "2.00", DAY_1, TradeOutcome.LOSS);
        approveAndSettle(account, "RG3-2", "100.00", "2.00", DAY_1, TradeOutcome.LOSS);
        approveAndSettle(account, "RG3-3", "100.00", "2.00", DAY_1, TradeOutcome.LOSS);
        assertEquals(RiskMode.RECOVERY, account.getStatus().riskMode());

        // Streak persists across two consecutive new trading days
        LocalDate day2 = DAY_1.plusDays(1);
        account.startNewTradingDay(day2);
        assertEquals(3, account.getStatus().consecutiveLosses());
        assertEquals(RiskMode.RECOVERY, account.getStatus().riskMode());

        LocalDate day3 = DAY_1.plusDays(2);
        account.startNewTradingDay(day3);
        assertEquals(3, account.getStatus().consecutiveLosses());
        assertEquals(RiskMode.RECOVERY, account.getStatus().riskMode());

        // Single WIN resets streak and exits RECOVERY
        approveAndSettle(account, "RG3-W", "40.00", "2.00", day3, TradeOutcome.WIN);
        assertAll(
                () -> assertEquals(0, account.getStatus().consecutiveLosses()),
                () -> assertEquals(RiskMode.NORMAL, account.getStatus().riskMode()));
    }

    @Test
    @DisplayName("TC-RG-04 [RT][FR-09]: Trimmed trade ID blocks duplicate reuse and double settlement")
    void regression_trimmedTradeIdPreventsDuplicateReuseAndDoubleSettlement() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);
        assertTrue(account.proposeTrade(
                new TradeRequest("  RG-ID  ", new BigDecimal("100.00"), new BigDecimal("2.00"), DAY_1)).approved());
        account.settleTrade(" RG-ID ", TradeOutcome.WIN);
        BigDecimal balanceAfterWin = account.getStatus().currentBalance();

        assertAll(
                () -> assertThrows(IllegalStateException.class,
                        () -> account.settleTrade("RG-ID", TradeOutcome.WIN)),
                () -> assertFalse(account.proposeTrade(
                        new TradeRequest("RG-ID", new BigDecimal("50.00"), new BigDecimal("2.00"), DAY_1)).approved()),
                () -> assertEquals(balanceAfterWin, account.getStatus().currentBalance()));
    }

    @Test
    @DisplayName("TC-RG-05 [RT][FR-04/FR-06]: Risk cap recalculates immediately after losses")
    void regression_riskCapRecalculatesImmediatelyAfterLosses() {
        TradingAccount account = TradingAccount.createDefault(DAY_1);
        // After $250.00 loss -> balance $49,750.00 -> nominal cap drops to $248.75
        approveAndSettle(account, "RG5-1", "250.00", "2.00", DAY_1, TradeOutcome.LOSS);
        TradeDecision staleCap = account.proposeTrade(
                new TradeRequest("RG5-STALE", new BigDecimal("250.00"), new BigDecimal("2.00"), DAY_1));

        // After second loss of $170.00 -> balance $49,580.00 -> daily cap drops to $80.00
        approveAndSettle(account, "RG5-2", "170.00", "2.00", DAY_1, TradeOutcome.LOSS);
        TradeDecision aboveDailyCap = account.proposeTrade(
                new TradeRequest("RG5-OVER", new BigDecimal("80.01"), new BigDecimal("2.00"), DAY_1));
        TradeDecision exactDailyCap = account.proposeTrade(
                new TradeRequest("RG5-EXACT", new BigDecimal("80.00"), new BigDecimal("2.00"), DAY_1));

        assertAll(
                () -> assertFalse(staleCap.approved()),
                () -> assertEquals(new BigDecimal("248.75"), staleCap.maxAllowedRisk()),
                () -> assertFalse(aboveDailyCap.approved()),
                () -> assertEquals(new BigDecimal("80.00"), aboveDailyCap.maxAllowedRisk()),
                () -> assertTrue(exactDailyCap.approved()));
    }

    private static void approveAndSettle(
            TradingAccount account,
            String id,
            String risk,
            String rr,
            LocalDate day,
            TradeOutcome outcome) {
        TradeDecision decision = account.proposeTrade(
                new TradeRequest(id, new BigDecimal(risk), new BigDecimal(rr), day));
        assertTrue(decision.approved(), "Expected approval for " + id + ": " + decision.reason());
        account.settleTrade(id, outcome);
    }
}
