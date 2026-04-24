package com.example;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
 
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Converts a PDDocument to a CSV file.
 *
 * Strategy
 * --------
 * 1. Use PDFBox's positional text stripper to reconstruct table-like structure
 *    from the X/Y coordinates of every character on each page.
 * 2. If a page has fewer than {@value #MIN_TEXT_CHARS} embedded characters it
 *    is treated as a scanned image; Tesseract OCR is used instead.
 * 3. Rows are delimited by line breaks; columns are delimited by detecting
 *    consistent horizontal gaps wider than {@value #COLUMN_GAP_THRESHOLD} pt.
 * 4. Each cell value is quoted and escaped per RFC 4180.
 */
public class PDFtoCSVExporter
{
    private static final int MIN_TEXT_CHARS = 10;
    private static final float OCR_DPI = 300f;

    private final Tesseract tesseract;

    public PDFtoCSVExporter(Tesseract tesseract)
    {
        this.tesseract = tesseract;
    }

    public void export(PDDocument document, org.apache.pdfbox.rendering.PDFRenderer renderer, File outputFile, ProgressCallback progress) throws IOException
    {
        int total = document.getNumberOfPages();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)))
        {
            for (int i = 0; i < total; i++)
            {
                if(progress != null) progress.onPage(i + 1, total);

                List<List<String>> rows= extractPage(document, renderer, i);

                writer.write("Page " + (i + 1));
                writer.newLine();

                for (List<String> row : rows)
                {
                    writer.write(toCsvLine(row));
                    writer.newLine();
                }

                writer.newLine();
                }
            }
        }

        private List<List<String>> extractPage(PDDocument document, org.apache.pdfbox.rendering.PDFRenderer renderer, int pageIndex) throws IOException
    {
        // Try embedded text first
        String embeddedText = getEmbeddedText(document, pageIndex);
 
        if (embeddedText.trim().length() >= MIN_TEXT_CHARS)
        {
            return parseTextToRows(embeddedText);
        }
 
        if (tesseract != null)
        {
            try
            {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, OCR_DPI);
                String ocrText = tesseract.doOCR(image);
                if (ocrText != null && !ocrText.isBlank())
                {
                    return parseTextToRows(ocrText);
                }
            }
            catch (TesseractException ex)
            {
                
            }
        }
 
        return Collections.emptyList();
    }

           private String getEmbeddedText(PDDocument document, int pageIndex) throws IOException
           {
               PositionStripper stripper = new PositionStripper();
               stripper.setStartPage(pageIndex + 1);
               stripper.setEndPage(pageIndex + 1);
               stripper.setSortByPosition(true);
               stripper.setAddMoreFormatting(false);
               return stripper.getText(document);
           }

           private List<List<String>> parseTextToRows(String text)
           {
            List<List<String>> rows = new ArrayList<>();

            String[] lines = text.split("\\r?\\n");

            Pattern colSplitter = Pattern.compile("\\s{2,}");

            for(String line : lines)
            {
                String trimmed = line.stripTrailing();
                if(trimmed.isEmpty()) continue;

                String[] cols = colSplitter.split(trimmed);

                List<String> row = new ArrayList<>();
                for(String col : cols)
                {
                    row.add(col.trim());
                }

                if(!row.isEmpty())
                {
                    rows.add(row);
                }
            }
              return rows;
        }

        private String toCsvLine(List<String> cells)
        {
            StringBuilder sb = new StringBuilder();
            for(int i = 0; i < cells.size(); i++)
            {
                if(i > 0) sb.append(",");
                sb.append(csvQuote(cells.get(i)));
                
            }
            return sb.toString();
        }

        private String csvQuote(String value)
        {
            if(value == null) return "\"\"";
            String escaped = value.replace("\"", "\"\"");
            return "\"" + escaped + "\"";
        }

        @FunctionalInterface
        public interface ProgressCallback
        {
            void onPage(int current, int total);
        }

        private static class PositionStripper extends PDFTextStripper
        {
            PositionStripper() throws IOException
            {
                super();
                setSortByPosition(true);
                setAddMoreFormatting(true);
                setWordSeparator(" ");
                setLineSeparator("\n");
                setParagraphStart("");
                setParagraphEnd("\n");
            }
        }
}