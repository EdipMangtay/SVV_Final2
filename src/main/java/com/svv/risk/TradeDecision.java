package com.svv.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable result of evaluating a {@link TradeRequest} against risk rules.
 */
public record TradeDecision(
        boolean approved,
        String tradeId,
        String reason,
        BigDecimal requestedRisk,
        BigDecimal maxAllowedRisk,
        RiskMode riskMode) {

    public TradeDecision {
        Objects.requireNonNull(tradeId, "Trade ID must not be null.");
        Objects.requireNonNull(reason, "Reason must not be null.");
        Objects.requireNonNull(requestedRisk, "Requested risk must not be null.");
        Objects.requireNonNull(maxAllowedRisk, "Maximum allowed risk must not be null.");
        Objects.requireNonNull(riskMode, "Risk mode must not be null.");
        requestedRisk = requestedRisk.setScale(2, RoundingMode.UNNECESSARY);
        maxAllowedRisk = maxAllowedRisk.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static TradeDecision approve(
            String tradeId,
            BigDecimal requestedRisk,
            BigDecimal maxAllowedRisk,
            RiskMode riskMode,
            String reason) {
        return new TradeDecision(true, tradeId, reason, requestedRisk, maxAllowedRisk, riskMode);
    }

    public static TradeDecision reject(
            String tradeId,
            BigDecimal requestedRisk,
            BigDecimal maxAllowedRisk,
            RiskMode riskMode,
            String reason) {
        return new TradeDecision(false, tradeId, reason, requestedRisk, maxAllowedRisk, riskMode);
    }
}
