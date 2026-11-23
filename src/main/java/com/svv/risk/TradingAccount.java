package com.svv.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Stateful aggregate representing a proprietary trading account.
 * Owns mutable balance, lock flags, consecutive-loss streak, trading day,
 * single pending approved trade, and used trade IDs. All business calculations
 * delegate to {@link RiskPolicy}; failed operations leave account state unchanged.
 */
public final class TradingAccount {

    private final BigDecimal initialBalance;
    private final BigDecimal overallLossFloor;
    private final RiskPolicy policy;

    private BigDecimal dayStartingBalance;
    private BigDecimal dailyLossFloor;
    private BigDecimal currentBalance;
    private LocalDate currentTradingDay;
    private int consecutiveLosses;
    private boolean dailyLocked;
    private boolean overallLocked;
    private TradeRequest pendingTrade;
    private final Set<String> usedTradeIds = new HashSet<>();
    private int settledTradeCount;

    public TradingAccount(BigDecimal initialBalance, LocalDate startDate, RiskPolicy policy) {
        if (startDate == null) {
            throw new IllegalArgumentException("Start date must not be null.");
        }
        if (policy == null) {
            throw new IllegalArgumentException("Risk policy must not be null.");
        }
        this.policy = policy;
        this.initialBalance = RiskPolicy.requirePositiveMoney(initialBalance, "Initial balance");
        this.currentBalance = this.initialBalance;
        this.dayStartingBalance = this.initialBalance;
        this.currentTradingDay = startDate;
        this.overallLossFloor = policy.calculateOverallLossFloor(this.initialBalance);
        this.dailyLossFloor = policy.calculateDailyLossFloor(this.dayStartingBalance);
        this.dailyLocked = policy.isDailyLocked(this.currentBalance, this.dailyLossFloor);
        this.overallLocked = policy.isOverallLocked(this.currentBalance, this.overallLossFloor);
    }

    public static TradingAccount createDefault(LocalDate startDate) {
        return new TradingAccount(
                RiskPolicy.DEFAULT_INITIAL_BALANCE,
                startDate,
                RiskPolicy.defaultPolicy());
    }

    /**
     * Evaluates a proposed trade. If approved, reserves the trade internally as
     * the single pending trade and records its normalized ID. Rejected trades
     * do not modify any account state.
     */
    public TradeDecision proposeTrade(TradeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Trade request must not be null.");
        }
        RiskMode mode = currentRiskMode();
        BigDecimal nominalRisk = policy.calculateNominalRisk(currentBalance, mode);
        BigDecimal remainingDaily = policy.calculateRemainingCapacity(currentBalance, dailyLossFloor);
        BigDecimal remainingOverall = policy.calculateRemainingCapacity(currentBalance, overallLossFloor);
        BigDecimal maxAllowed = policy.calculateMaxAllowedRisk(
                currentBalance, dailyLossFloor, overallLossFloor, mode);

        TradeDecision decision = policy.evaluateTrade(
                request,
                currentTradingDay,
                mode,
                dailyLocked,
                overallLocked,
                pendingTrade != null,
                usedTradeIds.contains(request.tradeId()),
                nominalRisk,
                remainingDaily,
                remainingOverall,
                maxAllowed);

        if (decision.approved()) {
            pendingTrade = request;
            usedTradeIds.add(request.tradeId());
        }
        return decision;
    }

    /**
     * Settles the currently pending approved trade using the internally stored
     * approval terms. Returns the realized signed P&L.
     */
    public BigDecimal settleTrade(String tradeId, TradeOutcome outcome) {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("Trade ID must not be null or blank.");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("Trade outcome must not be null.");
        }
        String normalizedId = tradeId.trim();
        if (pendingTrade == null) {
            throw new IllegalStateException("No approved trade is currently pending settlement.");
        }
        if (!pendingTrade.tradeId().equals(normalizedId)) {
            throw new IllegalStateException(
                    "Pending trade ID is '" + pendingTrade.tradeId()
                            + "', cannot settle '" + normalizedId + "'.");
        }

        BigDecimal pnl = policy.calculateSettlementPnl(pendingTrade, outcome);
        currentBalance = currentBalance.add(pnl).setScale(2, RoundingMode.UNNECESSARY);
        if (outcome == TradeOutcome.WIN) {
            consecutiveLosses = 0;
        } else {
            consecutiveLosses++;
        }
        pendingTrade = null;
        settledTradeCount++;

        if (policy.isOverallLocked(currentBalance, overallLossFloor)) {
            overallLocked = true;
        }
        if (policy.isDailyLocked(currentBalance, dailyLossFloor)) {
            dailyLocked = true;
        }
        return pnl;
    }

    /**
     * Advances to a strictly later trading day when no trade is pending.
     * Recalculates the daily loss floor and clears a daily lock unless the
     * permanent overall lock is active. Consecutive losses and overall floor persist.
     */
    public void startNewTradingDay(LocalDate nextDay) {
        if (nextDay == null) {
            throw new IllegalArgumentException("Next trading day must not be null.");
        }
        if (pendingTrade != null) {
            throw new IllegalStateException(
                    "Cannot start a new trading day while trade '"
                            + pendingTrade.tradeId() + "' is pending settlement.");
        }
        if (!nextDay.isAfter(currentTradingDay)) {
            throw new IllegalArgumentException(
                    "New trading day " + nextDay
                            + " must be strictly after current trading day " + currentTradingDay + ".");
        }

        currentTradingDay = nextDay;
        if (!overallLocked) {
            dayStartingBalance = currentBalance;
            dailyLossFloor = policy.calculateDailyLossFloor(dayStartingBalance);
            dailyLocked = policy.isDailyLocked(currentBalance, dailyLossFloor);
        }
    }

    /**
     * Returns an immutable snapshot of the current account state.
     */
    public AccountStatus getStatus() {
        RiskMode mode = currentRiskMode();
        BigDecimal remainingDaily = policy.calculateRemainingCapacity(currentBalance, dailyLossFloor);
        BigDecimal remainingOverall = policy.calculateRemainingCapacity(currentBalance, overallLossFloor);
        BigDecimal maxAllowed = policy.calculateMaxAllowedRisk(
                currentBalance, dailyLossFloor, overallLossFloor, mode);
        BigDecimal realizedPnl = currentBalance.subtract(initialBalance)
                .setScale(2, RoundingMode.UNNECESSARY);

        return new AccountStatus(
                initialBalance,
                dayStartingBalance,
                currentBalance,
                realizedPnl,
                currentTradingDay,
                mode,
                maxAllowed,
                dailyLossFloor,
                overallLossFloor,
                remainingDaily,
                remainingOverall,
                consecutiveLosses,
                dailyLocked,
                overallLocked,
                pendingTrade != null ? pendingTrade.tradeId() : null,
                settledTradeCount);
    }

    private RiskMode currentRiskMode() {
        return policy.determineRiskMode(
                initialBalance,
                currentBalance,
                consecutiveLosses,
                dailyLocked,
                overallLocked);
    }
}
