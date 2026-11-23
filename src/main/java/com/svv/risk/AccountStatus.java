package com.svv.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable read-only snapshot of a {@link TradingAccount}'s state at a point in time.
 */
public record AccountStatus(
        BigDecimal initialBalance,
        BigDecimal dayStartingBalance,
        BigDecimal currentBalance,
        BigDecimal realizedPnl,
        LocalDate tradingDay,
        RiskMode riskMode,
        BigDecimal maxAllowedRisk,
        BigDecimal dailyLossFloor,
        BigDecimal overallLossFloor,
        BigDecimal remainingDailyCapacity,
        BigDecimal remainingOverallCapacity,
        int consecutiveLosses,
        boolean dailyLocked,
        boolean overallLocked,
        String pendingTradeId,
        int settledTradeCount) {

    public AccountStatus {
        Objects.requireNonNull(initialBalance, "Initial balance must not be null.");
        Objects.requireNonNull(dayStartingBalance, "Day starting balance must not be null.");
        Objects.requireNonNull(currentBalance, "Current balance must not be null.");
        Objects.requireNonNull(realizedPnl, "Realized P&L must not be null.");
        Objects.requireNonNull(tradingDay, "Trading day must not be null.");
        Objects.requireNonNull(riskMode, "Risk mode must not be null.");
        Objects.requireNonNull(maxAllowedRisk, "Maximum allowed risk must not be null.");
        Objects.requireNonNull(dailyLossFloor, "Daily loss floor must not be null.");
        Objects.requireNonNull(overallLossFloor, "Overall loss floor must not be null.");
        Objects.requireNonNull(remainingDailyCapacity, "Remaining daily capacity must not be null.");
        Objects.requireNonNull(remainingOverallCapacity, "Remaining overall capacity must not be null.");
    }

    public boolean hasPendingTrade() {
        return pendingTradeId != null;
    }
}
