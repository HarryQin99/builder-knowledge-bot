package com.harry.knowledgebot.util;

/**
 * Per-model pricing for Anthropic Claude calls. Adding a new model =
 * one new enum entry. Source: https://www.anthropic.com/pricing
 *
 * Phase 1 only needs Haiku 4.5; Phase 5 may add Sonnet/Opus or
 * cached vs uncached variants — extend this enum then.
 */
public enum ModelPricing {

    CLAUDE_HAIKU_4_5("claude-haiku-4-5", 0.80, 4.00);

    private final String modelId;
    private final double inputUsdPerMillion;
    private final double outputUsdPerMillion;

    ModelPricing(String modelId, double inputUsdPerMillion, double outputUsdPerMillion) {
        this.modelId = modelId;
        this.inputUsdPerMillion = inputUsdPerMillion;
        this.outputUsdPerMillion = outputUsdPerMillion;
    }

    public double inputUsdPerMillion() {
        return inputUsdPerMillion;
    }

    public double outputUsdPerMillion() {
        return outputUsdPerMillion;
    }

    /**
     * Look up pricing for a model id (the same string used in
     * application.properties' spring.ai.anthropic.chat.options.model).
     * Throws loudly on unknown ids — silent cost drift is not acceptable.
     */
    public static ModelPricing forModel(String modelId) {
        for (ModelPricing pricing : values()) {
            if (pricing.modelId.equals(modelId)) {
                return pricing;
            }
        }
        throw new IllegalArgumentException(
                "No pricing configured for model '" + modelId + "'. "
                        + "Add an entry to ModelPricing enum."
        );
    }
}
