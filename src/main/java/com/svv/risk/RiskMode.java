package com.svv.risk;

import java.math.BigDecimal;

/**
 * Dynamic risk modes ranked by enforcement priority:
 * LOCKED > RECOVERY > SAFE > GROWTH > NORMAL.
 */
public enum RiskMode {
    NORMAL(new BigDecimal("0.0050"), new BigDecimal("0.50")),
    GROWTH(new BigDecimal("0.0075"), new BigDecimal("0.75")),
    SAFE(new BigDecimal("0.0025"), new BigDecimal("0.25")),
    RECOVERY(new BigDecimal("0.0010"), new BigDecimal("0.10")),
    LOCKED(new BigDecimal("0.0000"), new BigDecimal("0.00"));

    private final BigDecimal riskFraction;
    private final BigDecimal riskPercent;

    RiskMode(BigDecimal riskFraction, BigDecimal riskPercent) {
        this.riskFraction = riskFraction;
        this.riskPercent = riskPercent;
    }

    /**
     * Returns the decimal risk fraction applied to account equity (e.g., 0.0050 for 0.50%).
     */
    public BigDecimal riskFraction() {
        return riskFraction;
    }

    /**
     * Returns the human-readable percentage representation (e.g., 0.50 for 0.50%).
     */
    public BigDecimal riskPercent() {
        return riskPercent;
    }
}
