package com.svv.risk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure business-rule verification for {@link RiskPolicy}.
 * Techniques: Boundary Value Analysis (BVA), Equivalence Partitioning (EP), Negative Testing (NT).
 * Requirements: FR-02, FR-03, FR-04, FR-05, FR-06, FR-07, FR-08, FR-10, FR-11, FR-12.
 */
@DisplayName("RiskPolicy Pure Business-Rule Tests (BVA & EP)")
class RiskPolicyTest {

    private static final BigDecimal INITIAL_50K = new BigDecimal("50000.00");
    private static final BigDecimal DAILY_FLOOR_DEFAULT = new BigDecimal("49500.00");
    private static final BigDecimal OVERALL_FLOOR_DEFAULT = new BigDecimal("48500.00");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    private RiskPolicy policy;

    @BeforeEach
    void setUp() {
        policy = RiskPolicy.defaultPolicy();
    }

    @Test
    @DisplayName("TC-RP-01 [EP][FR-02]: Loss floors calculate accurately and round upward (CEILING) conservatively")
    void lossFloorsRoundUpwardConservatively() {
        assertAll(
                () -> assertEquals(new BigDecimal("0.01"), policy.dailyLossRate()),
                () -> assertEquals(new BigDecimal("0.03"), policy.overallLossRate()),
                () -> assertEquals(DAILY_FLOOR_DEFAULT, policy.calculateDailyLossFloor(INITIAL_50K)),
                () -> assertEquals(OVERALL_FLOOR_DEFAULT, policy.calculateOverallLossFloor(INITIAL_50K)),
                // 49500.55 * 0.99 = 49005.5445 -> CEILING = 49005.55
                () -> assertEquals(new BigDecimal("49005.55"),
                        policy.calculateDailyLossFloor(new BigDecimal("49500.55"))),
                // 50000.55 * 0.97 = 48500.5335 -> CEILING = 48500.54
                () -> assertEquals(new BigDecimal("48500.54"),
                        policy.calculateOverallLossFloor(new BigDecimal("50000.55"))));
    }

    @ParameterizedTest(name = "Balance ${0} vs $49,500.00 daily floor -> dailyLocked={1}")
    @CsvSource({
            "49499.99, true",
            "49500.00, true",
            "49500.01, false"
    })
    @DisplayName("TC-RP-02 [BVA][FR-11]: Daily loss floor boundary ($49,500.00 inclusive)")
    void dailyLossFloorBoundary(String balance, boolean expectedLocked) {
        assertEquals(expectedLocked, policy.isDailyLocked(new BigDecimal(balance), DAILY_FLOOR_DEFAULT));
    }

    @ParameterizedTest(name = "Balance ${0} vs $48,500.00 overall floor -> overallLocked={1}")
    @CsvSource({
            "48499.99, true",
            "48500.00, true",
            "48500.01, false"
    })
    @DisplayName("TC-RP-03 [BVA][FR-12]: Overall loss floor boundary ($48,500.00 inclusive)")
    void overallLossFloorBoundary(String balance, boolean expectedLocked) {
        assertEquals(expectedLocked, policy.isOverallLocked(new BigDecimal(balance), OVERALL_FLOOR_DEFAULT));
    }

    @ParameterizedTest(name = "Balance ${0} ({2}) -> expected mode {1}")
    @CsvSource({
            "50995.00, NORMAL, +1.99%",
            "51000.00, GROWTH, +2.00%",
            "51005.00, GROWTH, +2.01%"
    })
    @DisplayName("TC-RP-04 [BVA][FR-03]: GROWTH mode activation boundary (+2.00%)")
    void growthThresholdBoundary(String balance, RiskMode expectedMode, String label) {
        RiskMode actual = policy.determineRiskMode(INITIAL_50K, new BigDecimal(balance), 0, false, false);
        assertEquals(expectedMode, actual);
    }

    @ParameterizedTest(name = "Balance ${0} ({2}) -> expected mode {1}")
    @CsvSource({
            "49005.00, NORMAL, -1.99%",
            "49000.00, SAFE,   -2.00%",
            "48995.00, SAFE,   -2.01%"
    })
    @DisplayName("TC-RP-05 [BVA][FR-03]: SAFE mode activation boundary (-2.00%)")
    void safeThresholdBoundary(String balance, RiskMode expectedMode, String label) {
        RiskMode actual = policy.determineRiskMode(INITIAL_50K, new BigDecimal(balance), 0, false, false);
        assertEquals(expectedMode, actual);
    }

    @ParameterizedTest(name = "{0} consecutive losses -> expected mode {1}")
    @CsvSource({
            "2, NORMAL",
            "3, RECOVERY",
            "4, RECOVERY"
    })
    @DisplayName("TC-RP-06 [BVA][FR-03/FR-10]: RECOVERY mode consecutive-loss boundary (3 losses)")
    void recoveryStreakBoundary(int consecutiveLosses, RiskMode expectedMode) {
        RiskMode actual = policy.determineRiskMode(INITIAL_50K, INITIAL_50K, consecutiveLosses, false, false);
        assertEquals(expectedMode, actual);
    }

    @Test
    @DisplayName("TC-RP-07 [EP][FR-03]: Mode priority enforces LOCKED > RECOVERY > SAFE > GROWTH > NORMAL")
    void riskModePriorityPrecedence() {
        assertAll(
                // Profitable (+4%) + 3 consecutive losses -> RECOVERY overrides GROWTH
                () -> assertEquals(RiskMode.RECOVERY,
                        policy.determineRiskMode(INITIAL_50K, new BigDecimal("52000.00"), 3, false, false)),
                // Drawdown (-2.5%) + 3 consecutive losses -> RECOVERY overrides SAFE
                () -> assertEquals(RiskMode.RECOVERY,
                        policy.determineRiskMode(INITIAL_50K, new BigDecimal("48750.00"), 3, false, false)),
                // Profitable (+4%) + daily lock -> LOCKED overrides GROWTH
                () -> assertEquals(RiskMode.LOCKED,
                        policy.determineRiskMode(INITIAL_50K, new BigDecimal("52000.00"), 0, true, false)),
                // Overall lock + 4 losses -> LOCKED overrides RECOVERY
                () -> assertEquals(RiskMode.LOCKED,
                        policy.determineRiskMode(INITIAL_50K, new BigDecimal("48500.00"), 4, false, true)));
    }

    @Test
    @DisplayName("TC-RP-08 [EP/BVA][FR-04]: Nominal risk rounds DOWN and max risk clamps to tighter floor capacity")
    void nominalRoundingAndCapacityClamping() {
        assertAll(
                // 49751.00 * 0.0050 = 248.755 -> DOWN = 248.75
                () -> assertEquals(new BigDecimal("248.75"),
                        policy.calculateNominalRisk(new BigDecimal("49751.00"), RiskMode.NORMAL)),
                // Daily capacity ($80.00) is tighter than nominal risk ($247.90)
                () -> assertEquals(new BigDecimal("80.00"),
                        policy.calculateMaxAllowedRisk(
                                new BigDecimal("49580.00"), DAILY_FLOOR_DEFAULT, OVERALL_FLOOR_DEFAULT, RiskMode.NORMAL)),
                // Overall capacity ($50.00) is tighter than daily capacity ($450.00) and SAFE nominal ($121.37)
                () -> assertEquals(new BigDecimal("50.00"),
                        policy.calculateMaxAllowedRisk(
                                new BigDecimal("48550.00"), new BigDecimal("48100.00"), OVERALL_FLOOR_DEFAULT, RiskMode.SAFE)),
                // Locked mode always produces $0.00
                () -> assertEquals(new BigDecimal("0.00"),
                        policy.calculateMaxAllowedRisk(
                                INITIAL_50K, DAILY_FLOOR_DEFAULT, OVERALL_FLOOR_DEFAULT, RiskMode.LOCKED)),
                // Balance below floor clamps remaining capacity to $0.00
                () -> assertEquals(new BigDecimal("0.00"),
                        policy.calculateRemainingCapacity(new BigDecimal("49400.00"), DAILY_FLOOR_DEFAULT)));
    }

    @ParameterizedTest(name = "Requested risk ${0} vs $250.00 cap -> approved={1}")
    @CsvSource({
            "249.99, true",
            "250.00, true",
            "250.01, false"
    })
    @DisplayName("TC-RP-09 [BVA][FR-05/FR-06]: Maximum trade risk boundary ($250.00)")
    void maxTradeRiskBoundary(String requestedRisk, boolean expectedApproved) {
        TradeRequest request = new TradeRequest("T-BVA", new BigDecimal(requestedRisk), new BigDecimal("2.00"), DAY);
        TradeDecision decision = policy.evaluateTrade(
                request,
                DAY,
                RiskMode.NORMAL,
                false,
                false,
                false,
                false,
                new BigDecimal("250.00"),
                new BigDecimal("500.00"),
                new BigDecimal("1500.00"),
                new BigDecimal("250.00"));
        assertEquals(expectedApproved, decision.approved());
    }

    @Test
    @DisplayName("TC-RP-10 [EP][FR-08]: Settlement P&L rounds WIN profits HALF_UP and deducts LOSS exactly")
    void settlementPnlRoundingAndExactLoss() {
        TradeRequest standard = new TradeRequest("T1", new BigDecimal("250.00"), new BigDecimal("2.00"), DAY);
        TradeRequest halfUpCase = new TradeRequest("T2", new BigDecimal("33.33"), new BigDecimal("1.50"), DAY);

        assertAll(
                () -> assertEquals(new BigDecimal("500.00"), policy.calculateSettlementPnl(standard, TradeOutcome.WIN)),
                () -> assertEquals(new BigDecimal("-250.00"), policy.calculateSettlementPnl(standard, TradeOutcome.LOSS)),
                // 33.33 * 1.50 = 49.995 -> HALF_UP = 50.00
                () -> assertEquals(new BigDecimal("50.00"), policy.calculateSettlementPnl(halfUpCase, TradeOutcome.WIN)),
                () -> assertEquals(new BigDecimal("-33.33"), policy.calculateSettlementPnl(halfUpCase, TradeOutcome.LOSS)));
    }

    @Test
    @DisplayName("TC-RP-11 [NT][FR-07]: Invalid policy configuration and invalid inputs throw IllegalArgumentException")
    void invalidPolicyParametersThrowException() {
        TradeRequest sample = new TradeRequest("T-N", new BigDecimal("100.00"), new BigDecimal("2.00"), DAY);
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RiskPolicy(null, new BigDecimal("0.03"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RiskPolicy(BigDecimal.ZERO, new BigDecimal("0.03"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RiskPolicy(BigDecimal.ONE, new BigDecimal("0.03"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RiskPolicy(new BigDecimal("0.01"), null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RiskPolicy(new BigDecimal("0.01"), BigDecimal.ZERO)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RiskPolicy(new BigDecimal("0.01"), BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RiskPolicy(new BigDecimal("0.05"), new BigDecimal("0.03"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> policy.determineRiskMode(INITIAL_50K, INITIAL_50K, -1, false, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> policy.calculateNominalRisk(INITIAL_50K, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> policy.calculateMaxAllowedRisk(
                                INITIAL_50K, DAILY_FLOOR_DEFAULT, OVERALL_FLOOR_DEFAULT, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> policy.calculateSettlementPnl(null, TradeOutcome.WIN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> policy.calculateSettlementPnl(sample, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> policy.isDailyLocked(null, DAILY_FLOOR_DEFAULT)));
    }
}
