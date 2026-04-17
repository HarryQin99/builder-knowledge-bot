package com.harry.knowledgebot.config;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

/**
 * One-time helper: prints the NCC PDF's outline (bookmarks) so we can
 * pick a clean self-contained section for Phase 1's bounded baseline.
 *
 * Run manually: remove @Disabled, run, copy the section + page range
 * into application.properties, then re-add @Disabled.
 */
@Disabled("Manual run only — prints NCC outline to pick a Phase 1 section.")
class InspectNccOutline {

    @Test
    void printOutline() throws Exception {
        File pdf = new File("corpus/ncc-2022-vol2.pdf");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            System.out.println("=== TOTAL PAGES: " + doc.getNumberOfPages() + " ===");
            PDDocumentCatalog catalog = doc.getDocumentCatalog();
            PDDocumentOutline outline = catalog.getDocumentOutline();
            if (outline == null) {
                System.out.println("(No outline / bookmarks in this PDF)");
                return;
            }
            walk(doc, outline.getFirstChild(), 0);
        }
    }

    private static void walk(PDDocument doc, PDOutlineItem item, int depth) throws Exception {
        while (item != null) {
            String indent = "  ".repeat(depth);
            int pageNum = resolvePage(doc, item);
            System.out.printf("%sp.%4d  %s%n", indent, pageNum, item.getTitle());
            walk(doc, item.getFirstChild(), depth + 1);
            item = item.getNextSibling();
        }
    }

    private static int resolvePage(PDDocument doc, PDOutlineItem item) throws Exception {
        PDDestination dest = item.getDestination();
        if (dest == null && item.getAction() instanceof PDActionGoTo go) {
            dest = go.getDestination();
        }
        if (dest instanceof PDPageDestination pd) {
            PDPage page = pd.getPage();
            if (page != null) {
                return doc.getPages().indexOf(page) + 1;
            }
            return pd.getPageNumber() + 1;
        }
        return -1;
    }
}
