package com.svv.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Immutable representation of a proposed trade submitted for risk validation.
 * Enforces structural validity (non-blank trimmed ID, cent-precision money,
 * positive reward-to-risk ratio, and non-null trading day). Well-formed zero
 * or negative monetary risks are accepted structurally so {@link RiskPolicy}
 * can return a structured REJECTED decision.
 */
public record TradeRequest(
        String tradeId,
        BigDecimal riskAmount,
        BigDecimal rewardToRiskRatio,
        LocalDate tradingDay) {

    public TradeRequest {
        if (tradeId == null || tradeId.isBlank()) {
            throw new IllegalArgumentException("Trade ID must not be null or blank.");
        }
        tradeId = tradeId.trim();

        if (riskAmount == null) {
            throw new IllegalArgumentException("Risk amount must not be null.");
        }
        if (riskAmount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "Risk amount must not contain fractional cents: " + riskAmount.toPlainString());
        }
        riskAmount = riskAmount.setScale(2, RoundingMode.UNNECESSARY);

        if (rewardToRiskRatio == null || rewardToRiskRatio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Reward-to-risk ratio must be strictly positive.");
        }

        if (tradingDay == null) {
            throw new IllegalArgumentException("Trading day must not be null.");
        }
    }
}
