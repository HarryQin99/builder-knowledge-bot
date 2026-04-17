package com.harry.knowledgebot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies the full Spring context boots cleanly. Uses the committed
 * test PDF (no real NCC needed) and a fake API key (so the Anthropic
 * starter is happy without making network calls).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.ai.anthropic.api-key=test-fake-key",
        "knowledgebot.corpus.path=classpath:test-corpus.pdf",
        "knowledgebot.corpus.page-from=1",
        "knowledgebot.corpus.page-to=2147483647"
})
class KnowledgeBotApplicationTests {

    @Test
    void contextLoads() {
    }

}
