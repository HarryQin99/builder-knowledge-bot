package com.harry.knowledgebot.model;

/**
 * Loaded PDF corpus text. Built once at startup by CorpusLoader and
 * injected as a singleton into the answer service. Wrapper type (rather
 * than a raw String bean) so it has a distinct, unambiguous identity.
 */
public record CorpusText(String text) {
}
