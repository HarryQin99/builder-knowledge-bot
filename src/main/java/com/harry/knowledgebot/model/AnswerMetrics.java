package com.harry.knowledgebot.model;

/**
 * Observability metrics for a single /ask call. Embedded under
 * AnswerResponse.metrics so each curl response is self-documenting —
 * no log-grepping to compare runs across phases.
 */
public record AnswerMetrics(
        int inputTokens,
        int outputTokens,
        long latencyMs,
        double estimatedCostUsd
) {
}
