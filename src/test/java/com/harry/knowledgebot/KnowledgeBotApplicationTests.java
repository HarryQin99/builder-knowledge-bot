package com.harry.knowledgebot;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Phase 2 context-load requires a running Postgres+pgvector. Re-enable in Phase 2 task 11 with Testcontainers or a test profile that excludes pgvector auto-config.")
class KnowledgeBotApplicationTests {

    @Test
    void contextLoads() {
    }

}
