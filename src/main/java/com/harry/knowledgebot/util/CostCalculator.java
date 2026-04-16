package com.harry.knowledgebot.util;

/**
 * Estimates USD cost of an Anthropic Claude call from token counts.
 * Pricing is supplied via {@link ModelPricing} — type-safe parameter
 * means callers cannot pass a String typo. Service resolves the
 * configured model id to a ModelPricing once at startup and reuses it.
 */
public final class CostCalculator {

    private static final double TOKENS_PER_MILLION = 1_000_000.0;

    private CostCalculator() {
        // utility class — no instances
    }

    public static double estimate(ModelPricing model, int inputTokens, int outputTokens) {
        return inputTokens * model.inputUsdPerMillion() / TOKENS_PER_MILLION
                + outputTokens * model.outputUsdPerMillion() / TOKENS_PER_MILLION;
    }
}
