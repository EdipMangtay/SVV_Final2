package com.svv.risk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Objects;

/**
 * Simple interactive command-line interface for the Prop Firm Risk Management
 * and Trade Validation System. Input and output streams are injectable to
 * support deterministic automated testing.
 */
public final class Main {

    private final TradingAccount account;
    private final BufferedReader input;
    private final PrintStream output;

    public Main(TradingAccount account, BufferedReader input, PrintStream output) {
        this.account = Objects.requireNonNull(account, "Account must not be null.");
        this.input = Objects.requireNonNull(input, "Input reader must not be null.");
        this.output = Objects.requireNonNull(output, "Output stream must not be null.");
    }

    public static void main(String[] args) {
        TradingAccount account = TradingAccount.createDefault(LocalDate.now());
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));
        new Main(account, reader, System.out).run();
    }

    public void run() {
        output.println("Prop Firm Risk Management & Trade Validation System");
        output.println("Type 'help' for available commands.");
        try {
            String line;
            while ((line = input.readLine()) != null) {
                if (!executeCommand(line)) {
                    break;
                }
            }
        } catch (IOException e) {
            output.println("ERROR: Failed to read command input: " + e.getMessage());
        }
    }

    /**
     * Parses and executes a single CLI command line.
     *
     * @return {@code true} to continue processing commands, {@code false} to exit.
     */
    public boolean executeCommand(String rawLine) {
        if (rawLine == null) {
            return false;
        }
        String trimmed = rawLine.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        String[] tokens = trimmed.split("\\s+");
        String command = tokens[0].toLowerCase(Locale.US);

        try {
            switch (command) {
                case "help" -> printHelp();
                case "status" -> printStatus();
                case "risk" -> printRisk();
                case "propose" -> handlePropose(tokens);
                case "settle" -> handleSettle(tokens);
                case "nextday" -> handleNextDay(tokens);
                case "exit", "quit" -> {
                    output.println("Goodbye.");
                    return false;
                }
                default -> output.println(
                        "ERROR: Unknown command '" + tokens[0] + "'. Type 'help' for available commands.");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            output.println("ERROR: " + e.getMessage());
        }
        return true;
    }

    private void printHelp() {
        output.println("Available commands:");
        output.println("  help                                      - Show this command list");
        output.println("  status                                    - Display full account status");
        output.println("  risk                                      - Display current risk mode and limits");
        output.println("  propose <id> <risk> <rr> [YYYY-MM-DD]     - Propose a trade for approval");
        output.println("  settle <id> <WIN|LOSS>                    - Settle the pending approved trade");
        output.println("  nextday <YYYY-MM-DD>                      - Transition to a future trading day");
        output.println("  exit                                      - Exit the application");
    }

    private void printStatus() {
        AccountStatus s = account.getStatus();
        output.println("=== Account Status ===");
        output.println("Initial Balance:            $" + s.initialBalance().toPlainString());
        output.println("Current Balance:            $" + s.currentBalance().toPlainString());
        output.println("Realized P&L:               " + formatSignedMoney(s.realizedPnl()));
        output.println("Trading Day:                " + s.tradingDay());
        output.println("Risk Mode:                  " + s.riskMode() + " (" + s.riskMode().riskPercent().toPlainString() + "%)");
        output.println("Maximum Permitted Risk:     $" + s.maxAllowedRisk().toPlainString());
        output.println("Daily Loss Floor:           $" + s.dailyLossFloor().toPlainString());
        output.println("Overall Loss Floor:         $" + s.overallLossFloor().toPlainString());
        output.println("Remaining Daily Capacity:   $" + s.remainingDailyCapacity().toPlainString());
        output.println("Remaining Overall Capacity: $" + s.remainingOverallCapacity().toPlainString());
        output.println("Consecutive Losses:         " + s.consecutiveLosses());
        output.println("Daily Lock:                 " + (s.dailyLocked() ? "LOCKED" : "ACTIVE"));
        output.println("Permanent Overall Lock:     " + (s.overallLocked() ? "LOCKED" : "ACTIVE"));
        output.println("Pending Trade:              " + (s.hasPendingTrade() ? s.pendingTradeId() : "NONE"));
        output.println("Settled Trades:             " + s.settledTradeCount());
    }

    private void printRisk() {
        AccountStatus s = account.getStatus();
        output.println("Mode: " + s.riskMode() + " (" + s.riskMode().riskPercent().toPlainString() + "%)");
        output.println("Maximum Allowed Risk:       $" + s.maxAllowedRisk().toPlainString());
        output.println("Remaining Daily Capacity:   $" + s.remainingDailyCapacity().toPlainString());
        output.println("Remaining Overall Capacity: $" + s.remainingOverallCapacity().toPlainString());
    }

    private void handlePropose(String[] tokens) {
        if (tokens.length != 4 && tokens.length != 5) {
            output.println("ERROR: Usage: propose <id> <risk> <rr> [YYYY-MM-DD]");
            return;
        }
        String tradeId = tokens[1];
        BigDecimal risk = parseDecimal(tokens[2], "risk amount");
        BigDecimal rr = parseDecimal(tokens[3], "reward-to-risk ratio");
        LocalDate day = tokens.length == 5
                ? parseDate(tokens[4])
                : account.getStatus().tradingDay();

        TradeRequest request = new TradeRequest(tradeId, risk, rr, day);
        TradeDecision decision = account.proposeTrade(request);
        String verdict = decision.approved() ? "APPROVED" : "REJECTED";
        output.println(verdict + " [" + decision.tradeId() + "]: " + decision.reason());
    }

    private void handleSettle(String[] tokens) {
        if (tokens.length != 3) {
            output.println("ERROR: Usage: settle <id> <WIN|LOSS>");
            return;
        }
        String tradeId = tokens[1];
        TradeOutcome outcome;
        try {
            outcome = TradeOutcome.valueOf(tokens[2].toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            output.println("ERROR: Outcome must be WIN or LOSS.");
            return;
        }

        BigDecimal pnl = account.settleTrade(tradeId, outcome);
        AccountStatus s = account.getStatus();
        output.println("SETTLED [" + tradeId.trim() + "] " + outcome
                + " | P&L: " + formatSignedMoney(pnl)
                + " | New Balance: $" + s.currentBalance().toPlainString()
                + " | Mode: " + s.riskMode());
        if (s.overallLocked()) {
            output.println("ALERT: Permanent overall loss lock activated.");
        } else if (s.dailyLocked()) {
            output.println("ALERT: Daily loss lock activated for " + s.tradingDay() + ".");
        }
    }

    private void handleNextDay(String[] tokens) {
        if (tokens.length != 2) {
            output.println("ERROR: Usage: nextday <YYYY-MM-DD>");
            return;
        }
        LocalDate nextDay = parseDate(tokens[1]);
        account.startNewTradingDay(nextDay);
        AccountStatus s = account.getStatus();
        output.println("NEW DAY: " + s.tradingDay()
                + " | Daily Floor: $" + s.dailyLossFloor().toPlainString()
                + " | Overall Floor: $" + s.overallLossFloor().toPlainString()
                + " | Mode: " + s.riskMode()
                + " | Max Risk: $" + s.maxAllowedRisk().toPlainString());
    }

    private static BigDecimal parseDecimal(String text, String fieldName) {
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": '" + text + "'.");
        }
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date '" + text + "'. Expected YYYY-MM-DD.");
        }
    }

    private static String formatSignedMoney(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return "+$" + amount.toPlainString();
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return "-$" + amount.abs().toPlainString();
        }
        return "$0.00";
    }
}
