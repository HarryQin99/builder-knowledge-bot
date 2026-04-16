package com.harry.knowledgebot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure-math tests for the cost estimator. No Spring, no mocks.
 * Pricing is passed as a type-safe ModelPricing enum.
 */
class CostCalculatorTest {

    private static final ModelPricing HAIKU = ModelPricing.CLAUDE_HAIKU_4_5;
    private static final double TOLERANCE = 0.000001;

    @Test
    void zeros_returnsZero() {
        assertEquals(0.0, CostCalculator.estimate(HAIKU, 0, 0), TOLERANCE);
    }

    @Test
    void oneMillionInputTokens_costsEightyCents_onHaiku() {
        assertEquals(0.80, CostCalculator.estimate(HAIKU, 1_000_000, 0), TOLERANCE);
    }

    @Test
    void oneMillionOutputTokens_costsFourDollars_onHaiku() {
        assertEquals(4.00, CostCalculator.estimate(HAIKU, 0, 1_000_000), TOLERANCE);
    }

    @Test
    void realisticPhase1Call_combinesInputAndOutputRates() {
        // 150_000 in @ $0.80/M = $0.12
        // 500 out @ $4.00/M    = $0.002
        // total                = $0.122
        assertEquals(0.122, CostCalculator.estimate(HAIKU, 150_000, 500), TOLERANCE);
    }

    @Test
    void smallTokenCounts_areAccurate() {
        // 1000 in @ $0.80/M = $0.0008
        // 100 out @ $4.00/M = $0.0004
        // total             = $0.0012
        assertEquals(0.0012, CostCalculator.estimate(HAIKU, 1000, 100), TOLERANCE);
    }
}
