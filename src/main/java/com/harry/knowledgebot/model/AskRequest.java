package com.harry.knowledgebot.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /ask.
 */
public record AskRequest(
        @NotBlank String question
) {
}
