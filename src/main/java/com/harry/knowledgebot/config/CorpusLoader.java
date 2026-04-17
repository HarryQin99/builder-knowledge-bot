package com.harry.knowledgebot.config;

import com.harry.knowledgebot.model.CorpusText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads the corpus PDF once at startup, exposes the concatenated text
 * as a singleton {@link CorpusText} bean, and enforces the Phase 1
 * 190K-token checkpoint — refuses to boot if the (subset of the) PDF
 * won't fit Claude's ~200K context window with reasonable headroom.
 *
 * <p>Supports an optional inclusive page range
 * ({@code knowledgebot.corpus.page-from} / {@code .page-to}, both 1-based)
 * so we can scope down a large PDF without preprocessing it. Phase 1's
 * NCC corpus is 312 pages / ~607K tokens — far over the 190K ceiling —
 * so we bound to a single section (e.g. Part H4 Health and amenity).
 * Phase 2 RAG can read the entire PDF once we have chunking + retrieval.
 */
@Configuration
public class CorpusLoader {

    private static final Logger log = LoggerFactory.getLogger(CorpusLoader.class);

    /**
     * Phase 1 hard ceiling. Claude's context window is ~200K tokens;
     * leaving 10K for the system prompt, question, and answer means
     * the corpus itself can occupy at most ~190K. If the PDF estimates
     * over this, the long-context approach has failed by construction —
     * stop and surface, don't silently truncate.
     */
    static final long TOKEN_CHECKPOINT = 190_000L;

    private final String corpusPath;
    private final int pageFrom;
    private final int pageTo;

    public CorpusLoader(
            @Value("${knowledgebot.corpus.path}") String corpusPath,
            @Value("${knowledgebot.corpus.page-from:1}") int pageFrom,
            @Value("${knowledgebot.corpus.page-to:2147483647}") int pageTo
    ) {
        if (pageFrom < 1) {
            throw new IllegalArgumentException("page-from must be >= 1, got " + pageFrom);
        }
        if (pageTo < pageFrom) {
            throw new IllegalArgumentException(
                    "page-to (" + pageTo + ") must be >= page-from (" + pageFrom + ")");
        }
        this.corpusPath = corpusPath;
        this.pageFrom = pageFrom;
        this.pageTo = pageTo;
    }

    @Bean
    public CorpusText corpusText() {
        long startMs = System.currentTimeMillis();
        Resource pdfResource = resolveResource(corpusPath);
        var reader = new PagePdfDocumentReader(pdfResource);
        List<Document> allPages = reader.get();
        List<Document> selectedPages = sliceRange(allPages, pageFrom, pageTo);
        String text = selectedPages.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        long elapsedMs = System.currentTimeMillis() - startMs;
        long estimatedTokens = estimateTokens(text);

        log.info("Corpus loaded from '{}' (pages {}-{} of {}): {} pages selected, {} chars, ~{} estimated tokens, {} ms",
                corpusPath, pageFrom, Math.min(pageTo, allPages.size()), allPages.size(),
                selectedPages.size(), text.length(), estimatedTokens, elapsedMs);

        if (estimatedTokens >= TOKEN_CHECKPOINT) {
            throw new IllegalStateException(
                    "Estimated corpus tokens (" + estimatedTokens
                            + ") exceeds Phase 1 checkpoint of " + TOKEN_CHECKPOINT
                            + ". The long-context approach won't fit Claude's ~200K window. "
                            + "Stop and surface — don't silently truncate or swap models."
            );
        }
        return new CorpusText(text);
    }

    /**
     * Slice {@code pages} to the inclusive 1-based range [{@code from}, {@code to}],
     * clamped to the actual page count.
     */
    static List<Document> sliceRange(List<Document> pages, int from, int to) {
        int fromIdx = Math.max(0, from - 1);
        int toIdx = Math.min(pages.size(), to);
        if (fromIdx >= pages.size()) {
            return List.of();
        }
        return pages.subList(fromIdx, toIdx);
    }

    /**
     * Rough chars-per-token estimate (≈4 for English). Good enough for
     * the startup checkpoint; the real per-call token counts come back
     * from Anthropic's response usage.
     */
    static long estimateTokens(String text) {
        return text.length() / 4L;
    }

    private static Resource resolveResource(String path) {
        var loader = new DefaultResourceLoader();
        if (path.startsWith("classpath:") || path.startsWith("file:")) {
            return loader.getResource(path);
        }
        return loader.getResource("file:" + path);
    }
}
