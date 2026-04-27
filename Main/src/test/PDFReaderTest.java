package com.test;

public class PDFReaderTest 
{
    @TestMethodOrder(MethodOrderer.OderAnnotation.class)
    class PDFReaderTest
    {
        @TempDir static Path tempDir;

        static File makePdf(String text, int pages) throws Exception
        {
            File f = Files.createTempFile(tempDir, "test-", ".pdf").toFile();
            try(PDDocument doc = new PDDocument())
            {
                for(int p = 0; p < pages; p++)
                {
                    PDPage page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    try(PDPageContentStream cs = new PDPageContentStream(doc, page))
                    {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        cs.newLineAtOffset(50, 700 - p * 20);
                        cs.showText(text + (pages > 1 ? " page " + (p + 1) : ""));
                        cs.endText();
                    }
                }
                doc.save(f);
            }
            return f;
        }

        static File makeBlankPdf() throws Exception
        {
            File f = Files.createTempFile(tempDir, "blank-", "pdf").toFile();
            try(PDDocument doc = new PDDocument())
            {
                doc.addPage(new PDPage(PDRectanglge.A4));
                doc.save(f);
            }
            return f;
        }
    }
}
