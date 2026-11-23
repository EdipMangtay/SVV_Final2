package com.svv.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Pure, deterministic risk-management policy encapsulating loss-floor calculation,
 * dynamic risk-mode selection, permitted risk sizing, trade evaluation, and
 * simulated settlement P&L calculation.
 */
public final class RiskPolicy {

    public static final BigDecimal DEFAULT_INITIAL_BALANCE = new BigDecimal("50000.00");
    public static final BigDecimal DEFAULT_DAILY_LOSS_RATE = new BigDecimal("0.01");
    public static final BigDecimal DEFAULT_OVERALL_LOSS_RATE = new BigDecimal("0.03");
    public static final BigDecimal GROWTH_THRESHOLD = new BigDecimal("0.02");
    public static final BigDecimal SAFE_THRESHOLD = new BigDecimal("-0.02");
    public static final int RECOVERY_LOSS_STREAK = 3;
    public static final BigDecimal ZERO_MONEY = new BigDecimal("0.00");

    private final BigDecimal dailyLossRate;
    private final BigDecimal overallLossRate;

    public RiskPolicy(BigDecimal dailyLossRate, BigDecimal overallLossRate) {
        if (dailyLossRate == null
                || dailyLossRate.compareTo(BigDecimal.ZERO) <= 0
                || dailyLossRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Daily loss rate must be between 0 and 1 (exclusive).");
        }
        if (overallLossRate == null
                || overallLossRate.compareTo(BigDecimal.ZERO) <= 0
                || overallLossRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("Overall loss rate must be between 0 and 1 (exclusive).");
        }
        if (dailyLossRate.compareTo(overallLossRate) > 0) {
            throw new IllegalArgumentException("Daily loss rate must not exceed overall loss rate.");
        }
        this.dailyLossRate = dailyLossRate;
        this.overallLossRate = overallLossRate;
    }

    public static RiskPolicy defaultPolicy() {
        return new RiskPolicy(DEFAULT_DAILY_LOSS_RATE, DEFAULT_OVERALL_LOSS_RATE);
    }

    public BigDecimal dailyLossRate() {
        return dailyLossRate;
    }

    public BigDecimal overallLossRate() {
        return overallLossRate;
    }

    /**
     * DailyFloor = DayStartingBalance * (1 - dailyLossRate), rounded upward (CEILING)
     * to two decimal places so rounding never grants extra allowable loss.
     */
    public BigDecimal calculateDailyLossFloor(BigDecimal dayStartingBalance) {
        BigDecimal validated = requirePositiveMoney(dayStartingBalance, "Day starting balance");
        return validated
                .multiply(BigDecimal.ONE.subtract(dailyLossRate))
                .setScale(2, RoundingMode.CEILING);
    }

    /**
     * Static OverallFloor = InitialBalance * (1 - overallLossRate), rounded upward (CEILING)
     * to two decimal places. Computed once from initial balance and never trailed upward.
     */
    public BigDecimal calculateOverallLossFloor(BigDecimal initialBalance) {
        BigDecimal validated = requirePositiveMoney(initialBalance, "Initial balance");
        return validated
                .multiply(BigDecimal.ONE.subtract(overallLossRate))
                .setScale(2, RoundingMode.CEILING);
    }

    /**
     * Inclusive boundary check: reaching the exact daily floor activates the daily lock.
     */
    public boolean isDailyLocked(BigDecimal currentBalance, BigDecimal dailyFloor) {
        BigDecimal balance = requireMoney(currentBalance, "Current balance");
        BigDecimal floor = requireMoney(dailyFloor, "Daily floor");
        return balance.compareTo(floor) <= 0;
    }

    /**
     * Inclusive boundary check: reaching the exact overall floor activates the permanent overall lock.
     */
    public boolean isOverallLocked(BigDecimal currentBalance, BigDecimal overallFloor) {
        BigDecimal balance = requireMoney(currentBalance, "Current balance");
        BigDecimal floor = requireMoney(overallFloor, "Overall floor");
        return balance.compareTo(floor) <= 0;
    }

    /**
     * Determines the active risk mode using mandatory priority:
     * LOCKED > RECOVERY > SAFE > GROWTH > NORMAL.
     * Uses exact cross-multiplication against initialBalance to avoid division rounding errors.
     */
    public RiskMode determineRiskMode(
            BigDecimal initialBalance,
            BigDecimal currentBalance,
            int consecutiveLosses,
            boolean dailyLocked,
            boolean overallLocked) {
        BigDecimal initial = requirePositiveMoney(initialBalance, "Initial balance");
        BigDecimal current = requireMoney(currentBalance, "Current balance");
        if (consecutiveLosses < 0) {
            throw new IllegalArgumentException("Consecutive losses must not be negative.");
        }
        if (dailyLocked || overallLocked) {
            return RiskMode.LOCKED;
        }
        if (consecutiveLosses >= RECOVERY_LOSS_STREAK) {
            return RiskMode.RECOVERY;
        }
        BigDecimal netReturn = current.subtract(initial);
        if (netReturn.compareTo(initial.multiply(SAFE_THRESHOLD)) <= 0) {
            return RiskMode.SAFE;
        }
        if (netReturn.compareTo(initial.multiply(GROWTH_THRESHOLD)) >= 0) {
            return RiskMode.GROWTH;
        }
        return RiskMode.NORMAL;
    }

    /**
     * Calculates remaining loss capacity before a floor, clamped at $0.00.
     */
    public BigDecimal calculateRemainingCapacity(BigDecimal currentBalance, BigDecimal floor) {
        BigDecimal balance = requireMoney(currentBalance, "Current balance");
        BigDecimal validatedFloor = requireMoney(floor, "Floor");
        BigDecimal diff = balance.subtract(validatedFloor).setScale(2, RoundingMode.UNNECESSARY);
        return diff.compareTo(ZERO_MONEY) > 0 ? diff : ZERO_MONEY;
    }

    /**
     * Calculates nominal risk = CurrentBalance * ActiveRiskPercentage,
     * rounded downward (DOWN) to two decimal places so rounding never exceeds the risk budget.
     */
    public BigDecimal calculateNominalRisk(BigDecimal currentBalance, RiskMode mode) {
        BigDecimal balance = requireMoney(currentBalance, "Current balance");
        if (mode == null) {
            throw new IllegalArgumentException("Risk mode must not be null.");
        }
        if (mode == RiskMode.LOCKED || balance.compareTo(ZERO_MONEY) <= 0) {
            return ZERO_MONEY;
        }
        return balance.multiply(mode.riskFraction()).setScale(2, RoundingMode.DOWN);
    }

    /**
     * MaximumAllowedRisk = min(NominalRisk, RemainingDailyCapacity, RemainingOverallCapacity),
     * or $0.00 when the account is locked.
     */
    public BigDecimal calculateMaxAllowedRisk(
            BigDecimal currentBalance,
            BigDecimal dailyFloor,
            BigDecimal overallFloor,
            RiskMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Risk mode must not be null.");
        }
        if (mode == RiskMode.LOCKED) {
            return ZERO_MONEY;
        }
        BigDecimal nominal = calculateNominalRisk(currentBalance, mode);
        BigDecimal remainingDaily = calculateRemainingCapacity(currentBalance, dailyFloor);
        BigDecimal remainingOverall = calculateRemainingCapacity(currentBalance, overallFloor);
        return nominal.min(remainingDaily).min(remainingOverall);
    }

    /**
     * Evaluates a proposed trade request against all active risk and lifecycle rules.
     */
    public TradeDecision evaluateTrade(
            TradeRequest request,
            LocalDate currentTradingDay,
            RiskMode currentMode,
            boolean dailyLocked,
            boolean overallLocked,
            boolean hasPendingTrade,
            boolean duplicateTradeId,
            BigDecimal nominalRisk,
            BigDecimal remainingDailyCapacity,
            BigDecimal remainingOverallCapacity,
            BigDecimal maxAllowedRisk) {
        if (request == null) {
            throw new IllegalArgumentException("Trade request must not be null.");
        }
        if (currentTradingDay == null || currentMode == null) {
            throw new IllegalArgumentException("Current trading day and risk mode must not be null.");
        }

        BigDecimal req = request.riskAmount();
        if (overallLocked) {
            return TradeDecision.reject(
                    request.tradeId(), req, ZERO_MONEY, RiskMode.LOCKED,
                    "Account is permanently locked after reaching the overall loss floor.");
        }
        if (dailyLocked) {
            return TradeDecision.reject(
                    request.tradeId(), req, ZERO_MONEY, RiskMode.LOCKED,
                    "Account is locked for the current trading day after reaching the daily loss floor.");
        }
        if (hasPendingTrade) {
            return TradeDecision.reject(
                    request.tradeId(), req, maxAllowedRisk, currentMode,
                    "Another approved trade is already pending settlement.");
        }
        if (duplicateTradeId) {
            return TradeDecision.reject(
                    request.tradeId(), req, maxAllowedRisk, currentMode,
                    "Trade ID '" + request.tradeId() + "' has already been used.");
        }
        if (!request.tradingDay().equals(currentTradingDay)) {
            return TradeDecision.reject(
                    request.tradeId(), req, maxAllowedRisk, currentMode,
                    "Trading day " + request.tradingDay()
                            + " does not match active trading day " + currentTradingDay + ".");
        }
        if (req.compareTo(ZERO_MONEY) <= 0) {
            return TradeDecision.reject(
                    request.tradeId(), req, maxAllowedRisk, currentMode,
                    "Requested risk must be strictly positive (received $" + req.toPlainString() + ").");
        }
        if (req.compareTo(maxAllowedRisk) > 0) {
            String constraint;
            if (maxAllowedRisk.compareTo(remainingOverallCapacity) == 0
                    && remainingOverallCapacity.compareTo(nominalRisk) < 0
                    && remainingOverallCapacity.compareTo(remainingDailyCapacity) <= 0) {
                constraint = "limited by remaining overall capacity";
            } else if (maxAllowedRisk.compareTo(remainingDailyCapacity) == 0
                    && remainingDailyCapacity.compareTo(nominalRisk) < 0) {
                constraint = "limited by remaining daily capacity";
            } else {
                constraint = "limited by " + currentMode + " mode allowance";
            }
            return TradeDecision.reject(
                    request.tradeId(), req, maxAllowedRisk, currentMode,
                    "Requested risk $" + req.toPlainString()
                            + " exceeds maximum allowed risk $" + maxAllowedRisk.toPlainString()
                            + " (" + constraint + ").");
        }
        return TradeDecision.approve(
                request.tradeId(), req, maxAllowedRisk, currentMode,
                "Trade approved under " + currentMode + " mode (risk $"
                        + req.toPlainString() + " <= max $" + maxAllowedRisk.toPlainString() + ").");
    }

    /**
     * Calculates simulated profit/loss for an approved trade:
     * WIN profits are rounded HALF_UP to two decimal places; LOSS deductions are exact.
     */
    public BigDecimal calculateSettlementPnl(TradeRequest approvedTrade, TradeOutcome outcome) {
        if (approvedTrade == null) {
            throw new IllegalArgumentException("Approved trade must not be null.");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("Trade outcome must not be null.");
        }
        if (outcome == TradeOutcome.WIN) {
            return approvedTrade.riskAmount()
                    .multiply(approvedTrade.rewardToRiskRatio())
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return approvedTrade.riskAmount().negate().setScale(2, RoundingMode.UNNECESSARY);
    }

    static BigDecimal requireMoney(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null.");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    fieldName + " must not contain fractional cents: " + value.toPlainString());
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    static BigDecimal requirePositiveMoney(BigDecimal value, String fieldName) {
        BigDecimal validated = requireMoney(value, fieldName);
        if (validated.compareTo(ZERO_MONEY) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be strictly positive.");
        }
        return validated;
    }
}
