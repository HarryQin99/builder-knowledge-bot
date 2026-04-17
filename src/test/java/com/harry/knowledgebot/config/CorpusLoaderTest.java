package com.harry.knowledgebot.config;

import com.harry.knowledgebot.model.CorpusText;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies CorpusLoader extracts and concatenates pages from a real
 * (committed) test PDF. Reads src/test/resources/test-corpus.pdf via
 * the classpath: prefix.
 */
class CorpusLoaderTest {

    /** Default range = whole PDF (Spring's default for the optional properties). */
    private static final int ALL_FROM = 1;
    private static final int ALL_TO = Integer.MAX_VALUE;

    @Test
    void loadsCommittedFixturePdfAndConcatenatesPages() {
        var loader = new CorpusLoader("classpath:test-corpus.pdf", ALL_FROM, ALL_TO);

        CorpusText corpus = loader.corpusText();

        assertNotNull(corpus.text());
        assertFalse(corpus.text().isBlank(), "extracted text should not be blank");
        // Each page carries a single-token marker (PDFBox preserves visual
        // gaps between words, so multi-word substrings won't match — single
        // tokens like underscored markers do).
        assertTrue(corpus.text().contains("MARKER_PAGE_TWO"),
                "page 2 marker missing — multi-page concatenation broken");
        // Sanity: page-1 and page-3 content also present (single tokens).
        assertTrue(corpus.text().contains("Roof"), "page 1 content missing");
        assertTrue(corpus.text().contains("Stair"), "page 3 content missing");
    }

    @Test
    void honoursPageRange_keepsOnlySelectedPages() {
        // Take only page 2 of the 3-page fixture.
        var loader = new CorpusLoader("classpath:test-corpus.pdf", 2, 2);

        CorpusText corpus = loader.corpusText();

        assertTrue(corpus.text().contains("MARKER_PAGE_TWO"),
                "page 2 should be present");
        assertFalse(corpus.text().contains("Roof"),
                "page 1 should be excluded");
        assertFalse(corpus.text().contains("Stair"),
                "page 3 should be excluded");
    }

    @Test
    void sliceRange_clampsToActualPageCount() {
        // Asking for pages well past the end shouldn't NPE — clamps cleanly.
        List<Document> dummy = List.of(
                new Document("page-1"),
                new Document("page-2"),
                new Document("page-3")
        );
        assertEquals(2, CorpusLoader.sliceRange(dummy, 2, 999).size());
        assertEquals(0, CorpusLoader.sliceRange(dummy, 50, 60).size());
        assertEquals(3, CorpusLoader.sliceRange(dummy, 1, Integer.MAX_VALUE).size());
    }

    @Test
    void rejectsInvalidPageRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new CorpusLoader("classpath:test-corpus.pdf", 0, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new CorpusLoader("classpath:test-corpus.pdf", 5, 3));
    }

    @Test
    void estimateTokens_isCharsDividedByFour() {
        assertEquals(0L, CorpusLoader.estimateTokens(""));
        assertEquals(25L, CorpusLoader.estimateTokens("a".repeat(100)));
    }

    @Test
    void overSizedTokenCount_classifiesAsOverCheckpoint() {
        // chars/4 ≥ 190_000 ⇒ chars ≥ 760_000.
        long simulatedTokens = CorpusLoader.estimateTokens("x".repeat(800_000));
        assertTrue(simulatedTokens >= CorpusLoader.TOKEN_CHECKPOINT,
                "estimator should classify 800K chars as over-checkpoint");
    }

    @Test
    void missingPdf_throwsClearError() {
        var loader = new CorpusLoader("classpath:does-not-exist.pdf", ALL_FROM, ALL_TO);
        assertThrows(Exception.class, loader::corpusText);
    }
}
