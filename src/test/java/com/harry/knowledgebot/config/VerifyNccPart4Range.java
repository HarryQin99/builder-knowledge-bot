package com.harry.knowledgebot.config;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * One-time sanity check: load the real NCC PDF with the configured H4
 * page range and print the token/char stats. Confirms the Phase 1
 * subset comfortably fits the 190K checkpoint before we wire it into
 * /ask. Skipped by default — run manually if the page range changes.
 */
@Disabled("Manual run only — verifies the real NCC H4 range fits Phase 1.")
class VerifyNccPart4Range {

    @Test
    void loadHaaH4AndPrintStats() {
        var nccPdf = new File("corpus/ncc-2022-vol2.pdf");
        if (!nccPdf.exists()) {
            System.out.println("(NCC PDF missing at " + nccPdf + " — skipping)");
            return;
        }
        var loader = new CorpusLoader(nccPdf.getAbsolutePath(), 117, 125);
        var corpus = loader.corpusText();
        System.out.println("=== NCC H4 (pages 117-125) ===");
        System.out.println("Chars: " + corpus.text().length());
        System.out.println("Estimated tokens (chars/4): " + (corpus.text().length() / 4));
        System.out.println("First 300 chars:");
        System.out.println(corpus.text().substring(0, Math.min(300, corpus.text().length())));
    }
}
