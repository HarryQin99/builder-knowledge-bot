package com.harry.knowledgebot.config;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-time generator for the committed test fixture
 * src/test/resources/test-corpus.pdf. Run manually when the fixture
 * needs regenerating; otherwise the committed PDF is what tests read.
 *
 * Three short pages so we can verify multi-page concatenation. Page 2
 * carries a unique marker string the test asserts on.
 */
class TestPdfFixtureGenerator {

    private static final Path FIXTURE_PATH =
            Path.of("src/test/resources/test-corpus.pdf");

    @Test
    @Disabled("Manual run only — regenerates the committed test PDF. "
            + "Remove @Disabled, run, then re-add.")
    void generateFixture() throws Exception {
        Files.createDirectories(FIXTURE_PATH.getParent());

        try (var doc = new PDDocument()) {
            addPage(doc, "Section 1: Roof drainage",
                    "The minimum slope of a roof drain shall be 1:100.");
            addPage(doc, "Section 2: Window sizing",
                    "MARKER_PAGE_TWO content for testing page concatenation.");
            addPage(doc, "Section 3: Stair dimensions",
                    "Maximum riser height is 190 mm; minimum tread depth is 240 mm.");
            doc.save(FIXTURE_PATH.toFile());
        }
    }

    private static void addPage(PDDocument doc, String title, String body) throws Exception {
        var page = new PDPage();
        doc.addPage(page);
        var font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        try (var content = new PDPageContentStream(doc, page)) {
            content.beginText();
            content.setFont(font, 14);
            content.newLineAtOffset(72, 720);
            content.showText(title);
            content.endText();

            content.beginText();
            content.setFont(font, 12);
            content.newLineAtOffset(72, 690);
            content.showText(body);
            content.endText();
        }
    }
}
