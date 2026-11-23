package com.svv.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stateful account lifecycle, invariant enforcement, and negative testing for {@link TradingAccount}.
 * Techniques: Equivalence Partitioning (EP), Boundary Value Analysis (BVA), Negative Testing (NT).
 * Requirements: FR-01, FR-02, FR-05, FR-06, FR-07, FR-08, FR-09, FR-10, FR-11, FR-12, FR-13, FR-14.
 */
@DisplayName("TradingAccount Lifecycle & Negative Tests")
class TradingAccountTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 23);

    private TradingAccount account;

    @BeforeEach
    void setUp() {
        account = TradingAccount.createDefault(DAY_1);
    }

    @Test
    @DisplayName("TC-TA-01 [EP][FR-01/FR-14]: Default account initialization creates expected status snapshot")
    void defaultAccountInitializationStatus() {
        AccountStatus status = account.getStatus();

        assertAll(
                () -> assertEquals(new BigDecimal("50000.00"), status.initialBalance()),
                () -> assertEquals(new BigDecimal("50000.00"), status.dayStartingBalance()),
                () -> assertEquals(new BigDecimal("50000.00"), status.currentBalance()),
                () -> assertEquals(new BigDecimal("0.00"), status.realizedPnl()),
                () -> assertEquals(DAY_1, status.tradingDay()),
                () -> assertEquals(RiskMode.NORMAL, status.riskMode()),
                () -> assertEquals(new BigDecimal("250.00"), status.maxAllowedRisk()),
                () -> assertEquals(new BigDecimal("49500.00"), status.dailyLossFloor()),
                () -> assertEquals(new BigDecimal("48500.00"), status.overallLossFloor()),
                () -> assertEquals(new BigDecimal("500.00"), status.remainingDailyCapacity()),
                () -> assertEquals(new BigDecimal("1500.00"), status.remainingOverallCapacity()),
                () -> assertEquals(0, status.consecutiveLosses()),
                () -> assertFalse(status.dailyLocked()),
                () -> assertFalse(status.overallLocked()),
                () -> assertNull(status.pendingTradeId()),
                () -> assertFalse(status.hasPendingTrade()),
                () -> assertEquals(0, status.settledTradeCount()));
    }

    @Test
    @DisplayName("TC-TA-02 [NT][FR-01/FR-07]: Invalid constructor inputs throw IllegalArgumentException")
    void invalidAccountConstructorArgumentsThrow() {
        RiskPolicy policy = RiskPolicy.defaultPolicy();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TradingAccount(BigDecimal.ZERO, DAY_1, policy)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TradingAccount(new BigDecimal("-500.00"), DAY_1, policy)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TradingAccount(new BigDecimal("50000.001"), DAY_1, policy)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TradingAccount(new BigDecimal("50000.00"), null, policy)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new TradingAccount(new BigDecimal("50000.00"), DAY_1, null)));
    }

    @Test
    @DisplayName("TC-TA-03 [EP][FR-02/FR-05/FR-08/FR-10]: WIN settlement updates equity, resets streak, keeps static overall floor")
    void winSettlementUpdatesBalanceAndKeepsOverallFloorStatic() {
        // First incur a loss to establish a streak
        TradeDecision lossProposal = account.proposeTrade(
                new TradeRequest("T1", new BigDecimal("100.00"), new BigDecimal("2.00"), DAY_1));
        assertTrue(lossProposal.approved());
        account.settleTrade("T1", TradeOutcome.LOSS);
        assertEquals(1, account.getStatus().consecutiveLosses());

        // Now win $500.00 -> net balance $50,400.00
        TradeDecision winProposal = account.proposeTrade(
                new TradeRequest("T2", new BigDecimal("200.00"), new BigDecimal("2.50"), DAY_1));
        assertTrue(winProposal.approved());
        BigDecimal pnl = account.settleTrade("T2", TradeOutcome.WIN);
        AccountStatus status = account.getStatus();

        assertAll(
                () -> assertEquals(new BigDecimal("500.00"), pnl),
                () -> assertEquals(new BigDecimal("50400.00"), status.currentBalance()),
                () -> assertEquals(new BigDecimal("400.00"), status.realizedPnl()),
                () -> assertEquals(0, status.consecutiveLosses()),
                () -> assertEquals(new BigDecimal("48500.00"), status.overallLossFloor()),
                () -> assertEquals(2, status.settledTradeCount()));
    }

    @Test
    @DisplayName("TC-TA-04 [EP/NT][FR-06/FR-14]: Zero and negative well-formed risks are REJECTED without mutating state")
    void zeroAndNegativeRiskProposalsRejectedWithoutStateChange() {
        AccountStatus before = account.getStatus();

        TradeDecision zeroDecision = account.proposeTrade(
                new TradeRequest("T-ZERO", new BigDecimal("0.00"), new BigDecimal("2.00"), DAY_1));
        TradeDecision negDecision = account.proposeTrade(
                new TradeRequest("T-NEG", new BigDecimal("-50.00"), new BigDecimal("2.00"), DAY_1));
        AccountStatus after = account.getStatus();

        assertAll(
                () -> assertFalse(zeroDecision.approved()),
                () -> assertFalse(negDecision.approved()),
                () -> assertEquals(before, after));
    }

    @Test
    @DisplayName("TC-TA-05 [NT][FR-09/FR-13/FR-14]: Pending trade lifecycle guards prevent unauthorized actions")
    void pendingTradeGuardsEnforceAuthorizationAndConsistency() {
        // Settlement without any pending trade fails
        assertThrows(IllegalStateException.class, () -> account.settleTrade("NONE", TradeOutcome.WIN));
        assertThrows(IllegalArgumentException.class, () -> account.proposeTrade(null));

        // Approve T1
        TradeDecision approved = account.proposeTrade(
                new TradeRequest("T1", new BigDecimal("250.00"), new BigDecimal("2.00"), DAY_1));
        assertTrue(approved.approved());
        AccountStatus whilePending = account.getStatus();
        assertTrue(whilePending.hasPendingTrade());
        assertEquals("T1", whilePending.pendingTradeId());

        // Concurrent second trade rejected; new day blocked; wrong ID and invalid args rejected
        TradeDecision concurrent = account.proposeTrade(
                new TradeRequest("T2", new BigDecimal("50.00"), new BigDecimal("2.00"), DAY_1));

        assertAll(
                () -> assertFalse(concurrent.approved()),
                () -> assertTrue(concurrent.reason().contains("pending settlement")),
                () -> assertThrows(IllegalStateException.class,
                        () -> account.startNewTradingDay(DAY_1.plusDays(1))),
                () -> assertThrows(IllegalStateException.class,
                        () -> account.settleTrade("T2", TradeOutcome.WIN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> account.settleTrade("   ", TradeOutcome.WIN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> account.settleTrade("T1", null)),
                () -> assertEquals(whilePending, account.getStatus()));

        // Valid settlement succeeds, then duplicate settlement fails without modifying state
        account.settleTrade("T1", TradeOutcome.WIN);
        AccountStatus afterSettle = account.getStatus();
        assertThrows(IllegalStateException.class, () -> account.settleTrade("T1", TradeOutcome.WIN));
        assertEquals(afterSettle, account.getStatus());
    }

    @Test
    @DisplayName("TC-TA-06 [NT][FR-09/FR-14]: Completed trade ID cannot be reused even with extra whitespace")
    void duplicateTradeIdReuseIsRejectedWithoutStateChange() {
        assertTrue(account.proposeTrade(
                new TradeRequest("  T-DUP  ", new BigDecimal("100.00"), new BigDecimal("2.00"), DAY_1)).approved());
        account.settleTrade("T-DUP", TradeOutcome.WIN);
        AccountStatus beforeReuse = account.getStatus();

        TradeDecision reuse = account.proposeTrade(
                new TradeRequest("T-DUP", new BigDecimal("100.00"), new BigDecimal("2.00"), DAY_1));

        assertAll(
                () -> assertFalse(reuse.approved()),
                () -> assertTrue(reuse.reason().contains("already been used")),
                () -> assertEquals(beforeReuse, account.getStatus()));
    }

    @Test
    @DisplayName("TC-TA-07 [NT][FR-13/FR-14]: Same-day, backward, and null day transitions fail safely")
    void invalidTradingDayTransitionsThrowAndPreserveState() {
        AccountStatus before = account.getStatus();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> account.startNewTradingDay(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> account.startNewTradingDay(DAY_1)),
                () -> assertThrows(IllegalArgumentException.class, () -> account.startNewTradingDay(DAY_1.minusDays(1))),
                () -> assertEquals(before, account.getStatus()));
    }

    @Test
    @DisplayName("TC-TA-08 [EP][FR-10/FR-11/FR-13]: New day clears daily lock, recalculates daily floor, preserves streak")
    void newDayClearsDailyLockAndPreservesConsecutiveLosses() {
        // Three losses reach exact daily floor $49,500.00 -> daily lock + 3 consecutive losses
        assertTrue(account.proposeTrade(new TradeRequest("D1-1", new BigDecimal("250.00"), new BigDecimal("2.00"), DAY_1)).approved());
        account.settleTrade("D1-1", TradeOutcome.LOSS);
        assertTrue(account.proposeTrade(new TradeRequest("D1-2", new BigDecimal("248.75"), new BigDecimal("2.00"), DAY_1)).approved());
        account.settleTrade("D1-2", TradeOutcome.LOSS);
        assertTrue(account.proposeTrade(new TradeRequest("D1-3", new BigDecimal("1.25"), new BigDecimal("2.00"), DAY_1)).approved());
        account.settleTrade("D1-3", TradeOutcome.LOSS);

        assertTrue(account.getStatus().dailyLocked());
        assertEquals(RiskMode.LOCKED, account.getStatus().riskMode());

        // Advance to Day 2
        LocalDate day2 = DAY_1.plusDays(1);
        account.startNewTradingDay(day2);
        AccountStatus status = account.getStatus();

        assertAll(
                () -> assertEquals(day2, status.tradingDay()),
                () -> assertFalse(status.dailyLocked()),
                () -> assertFalse(status.overallLocked()),
                () -> assertEquals(new BigDecimal("49500.00"), status.dayStartingBalance()),
                () -> assertEquals(new BigDecimal("49005.00"), status.dailyLossFloor()),
                () -> assertEquals(new BigDecimal("48500.00"), status.overallLossFloor()),
                () -> assertEquals(3, status.consecutiveLosses()),
                () -> assertEquals(RiskMode.RECOVERY, status.riskMode()),
                // 49500.00 * 0.0010 = 49.50
                () -> assertEquals(new BigDecimal("49.50"), status.maxAllowedRisk()));
    }

    @Test
    @DisplayName("TC-TA-09 [BVA/EP][FR-11/FR-12]: Overall capacity constraint rejects $26.98 and permits exact $26.97 floor landing")
    void overallCapacityConstraintEnforcedBeforePermanentLock() {
        TradingAccount safeAccount = DecisionTableTest.createSafeModeAccount();
        LocalDate day3 = safeAccount.getStatus().tradingDay();
        // Advance Day 3 to daily lock at $48,526.97
        for (String[] spec : new String[][]{
                {"L1", "122.50", "LOSS"},
                {"L2", "122.19", "LOSS"},
                {"W1", "1.00", "WIN"},
                {"L3", "121.89", "LOSS"},
                {"L4", "107.45", "LOSS"}}) {
            assertTrue(safeAccount.proposeTrade(
                    new TradeRequest(spec[0], new BigDecimal(spec[1]), new BigDecimal("1.00"), day3)).approved());
            safeAccount.settleTrade(spec[0], TradeOutcome.valueOf(spec[2]));
        }

        LocalDate day4 = day3.plusDays(1);
        safeAccount.startNewTradingDay(day4);

        // Remaining overall capacity is $26.97; $26.98 must be rejected due to overall capacity
        TradeDecision overOverall = safeAccount.proposeTrade(
                new TradeRequest("OVER", new BigDecimal("26.98"), new BigDecimal("2.00"), day4));
        TradeDecision exactOverall = safeAccount.proposeTrade(
                new TradeRequest("EXACT", new BigDecimal("26.97"), new BigDecimal("2.00"), day4));
        safeAccount.settleTrade("EXACT", TradeOutcome.LOSS);
        AccountStatus lockedStatus = safeAccount.getStatus();

        assertAll(
                () -> assertFalse(overOverall.approved()),
                () -> assertTrue(overOverall.reason().contains("remaining overall capacity")),
                () -> assertTrue(exactOverall.approved()),
                () -> assertEquals(new BigDecimal("48500.00"), lockedStatus.currentBalance()),
                () -> assertTrue(lockedStatus.overallLocked()),
                () -> assertEquals(RiskMode.LOCKED, lockedStatus.riskMode()),
                () -> assertEquals(new BigDecimal("0.00"), lockedStatus.maxAllowedRisk()));
    }
}
