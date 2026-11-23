package com.svv.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Structural validation tests for {@link TradeRequest}.
 * Techniques: Equivalence Partitioning (EP) and Negative Testing (NT).
 * Requirements: FR-06, FR-07.
 */
@DisplayName("TradeRequest Structural Validation Tests (EP & NT)")
class TradeRequestTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    @Test
    @DisplayName("TC-TR-01 [EP][FR-07]: Valid request trims trade ID and normalizes cent scale")
    void validRequestNormalizesIdAndMoneyScale() {
        TradeRequest request = new TradeRequest(
                "  T-01  ",
                new BigDecimal("250"),
                new BigDecimal("2.00"),
                DAY);

        assertAll(
                () -> assertEquals("T-01", request.tradeId()),
                () -> assertEquals(new BigDecimal("250.00"), request.riskAmount()),
                () -> assertEquals(2, request.riskAmount().scale()),
                () -> assertEquals(new BigDecimal("2.00"), request.rewardToRiskRatio()),
                () -> assertEquals(DAY, request.tradingDay()));
    }

    @ParameterizedTest(name = "Blank trade ID \"{0}\" throws IllegalArgumentException")
    @ValueSource(strings = {"", "   \t "})
    @DisplayName("TC-TR-02 [EP/NT][FR-07]: Blank trade IDs fail structural validation")
    void blankTradeIdThrowsException(String invalidId) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TradeRequest(invalidId, new BigDecimal("100.00"), new BigDecimal("2.00"), DAY));
    }

    @Test
    @DisplayName("TC-TR-03 [NT][FR-07]: Null required fields fail structural validation")
    void nullRequiredFieldsThrowException() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TradeRequest(null, new BigDecimal("100.00"), new BigDecimal("2.00"), DAY)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TradeRequest("T1", null, new BigDecimal("2.00"), DAY)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TradeRequest("T1", new BigDecimal("100.00"), null, DAY)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new TradeRequest("T1", new BigDecimal("100.00"), new BigDecimal("2.00"), null)));
    }

    @ParameterizedTest(name = "Fractional-cent risk {0} throws IllegalArgumentException")
    @ValueSource(strings = {"250.001", "0.005", "100.125"})
    @DisplayName("TC-TR-04 [EP/NT][FR-07]: Unsupported fractional-cent precision is rejected")
    void fractionalCentRiskThrowsException(String invalidRisk) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TradeRequest("T1", new BigDecimal(invalidRisk), new BigDecimal("2.00"), DAY));
    }

    @ParameterizedTest(name = "Non-positive reward-to-risk ratio {0} throws IllegalArgumentException")
    @ValueSource(strings = {"0.00", "-1.50"})
    @DisplayName("TC-TR-05 [EP/NT][FR-07]: Non-positive reward-to-risk ratios fail validation")
    void nonPositiveRewardToRiskThrowsException(String invalidRatio) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TradeRequest("T1", new BigDecimal("100.00"), new BigDecimal(invalidRatio), DAY));
    }

    @ParameterizedTest(name = "Well-formed risk {0} passes structural validation for business-rule rejection")
    @ValueSource(strings = {"0.00", "-25.00"})
    @DisplayName("TC-TR-06 [EP][FR-06/FR-07]: Well-formed zero or negative risks defer rejection to RiskPolicy")
    void zeroOrNegativeWellFormedRiskPassesStructuralValidation(String riskText) {
        TradeRequest request = new TradeRequest("T1", new BigDecimal(riskText), new BigDecimal("2.00"), DAY);
        assertEquals(0, new BigDecimal(riskText).compareTo(request.riskAmount()));
    }
}
