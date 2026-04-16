package com.harry.knowledgebot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the model id → ModelPricing lookup. The lookup runs once
 * at startup, so failing loudly here means the app refuses to boot
 * with a misconfigured model rather than reporting wrong costs.
 */
class ModelPricingTest {

    @Test
    void forModel_resolvesKnownHaikuId() {
        assertEquals(
                ModelPricing.CLAUDE_HAIKU_4_5,
                ModelPricing.forModel("claude-haiku-4-5")
        );
    }

    @Test
    void forModel_unknownId_throwsLoudly() {
        var ex = assertThrows(
                IllegalArgumentException.class,
                () -> ModelPricing.forModel("claude-future-9000")
        );
        // Message must name the offending id and point at the fix site.
        assert ex.getMessage().contains("claude-future-9000");
        assert ex.getMessage().contains("ModelPricing");
    }
}
