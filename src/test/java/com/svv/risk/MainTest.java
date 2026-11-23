package com.svv.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic CLI interaction and malformed-input resilience tests for {@link Main}.
 * Techniques: Equivalence Partitioning (EP) and Negative Testing (NT).
 * Requirements: FR-15.
 */
@DisplayName("Main CLI Integration & Resilience Tests (EP & NT)")
class MainTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 11, 23);

    @Test
    @DisplayName("TC-CL-01 [EP][FR-15]: Interactive session handles help, status, risk, propose, settle, nextday, exit")
    void fullInteractiveCliWorkflow() {
        String script = String.join("\n",
                "help",
                "status",
                "risk",
                "propose T1 250.00 2.00",
                "settle T1 WIN",
                "propose T2 400.00 2.00",
                "nextday 2026-11-24",
                "exit");

        TradingAccount account = TradingAccount.createDefault(DAY_1);
        String output = runSession(account, script);

        assertAll(
                () -> assertTrue(output.contains("Available commands:")),
                () -> assertTrue(output.contains("Initial Balance:            $50000.00")),
                () -> assertTrue(output.contains("Mode: NORMAL (0.50%)")),
                () -> assertTrue(output.contains("APPROVED [T1]")),
                () -> assertTrue(output.contains("SETTLED [T1] WIN | P&L: +$500.00 | New Balance: $50500.00")),
                () -> assertTrue(output.contains("REJECTED [T2]")),
                () -> assertTrue(output.contains("NEW DAY: 2026-11-24 | Daily Floor: $49995.00")),
                () -> assertTrue(output.contains("Goodbye.")),
                () -> assertEquals("50500.00", account.getStatus().currentBalance().toPlainString()));
    }

    @Test
    @DisplayName("TC-CL-02 [NT][FR-15]: Malformed CLI commands print errors without crashing the session")
    void malformedCommandsAreHandledGracefully() {
        String script = String.join("\n",
                "",
                "   ",
                "unknowncmd",
                "propose T1 100.00",
                "propose T1 abc 2.00",
                "propose T1 100.005 2.00",
                "settle T1",
                "settle T1 DRAW",
                "nextday",
                "nextday 23-11-2026",
                "status",
                "quit");

        TradingAccount account = TradingAccount.createDefault(DAY_1);
        String output = runSession(account, script);

        assertAll(
                () -> assertTrue(output.contains("Unknown command 'unknowncmd'")),
                () -> assertTrue(output.contains("Usage: propose")),
                () -> assertTrue(output.contains("Invalid risk amount: 'abc'")),
                () -> assertTrue(output.contains("fractional cents")),
                () -> assertTrue(output.contains("Usage: settle")),
                () -> assertTrue(output.contains("Outcome must be WIN or LOSS")),
                () -> assertTrue(output.contains("Usage: nextday")),
                () -> assertTrue(output.contains("Expected YYYY-MM-DD")),
                () -> assertTrue(output.contains("Current Balance:            $50000.00")),
                () -> assertTrue(output.contains("Goodbye.")));
    }

    @Test
    @DisplayName("TC-CL-03 [NT/EP][FR-15]: CLI reports mismatched day, unauthorized settle, and daily lock alert")
    void cliReportsBusinessRejectionsAndDailyLockAlert() {
        String script = String.join("\n",
                "propose T-BAD-DAY 100.00 2.00 2026-11-30",
                "settle NONE WIN",
                "propose L1 250.00 2.00",
                "status",
                "settle L1 LOSS",
                "propose L2 248.75 2.00",
                "settle L2 LOSS",
                "propose L3 1.25 2.00",
                "settle L3 LOSS",
                "status",
                "exit");

        TradingAccount account = TradingAccount.createDefault(DAY_1);
        String output = runSession(account, script);

        assertAll(
                () -> assertTrue(output.contains("REJECTED [T-BAD-DAY]")),
                () -> assertTrue(output.contains("ERROR: No approved trade is currently pending settlement.")),
                () -> assertTrue(output.contains("Pending Trade:              L1")),
                () -> assertTrue(output.contains("ALERT: Daily loss lock activated for 2026-11-23.")),
                () -> assertTrue(output.contains("Daily Lock:                 LOCKED")),
                () -> assertTrue(account.getStatus().dailyLocked()));
    }

    private static String runSession(TradingAccount account, String commands) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        BufferedReader in = new BufferedReader(new StringReader(commands));
        new Main(account, in, out).run();
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
