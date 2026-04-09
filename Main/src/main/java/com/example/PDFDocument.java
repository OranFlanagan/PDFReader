package com.example;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import org.apache.pdfbox.Loader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;

public class PDFDocument implements AutoCloseable
{
    private final PDDocument document;
    private final PDFRenderer renderer;
    private final String fileName;

    public PDFDocument(File file) throws IOException
    {
        this.document = Loader.loadPDF(file);
        this.renderer = new PDFRenderer(document);
        this.fileName = file.getName();
    }

    public int getPageCount()
    {
        return document.getNumberOfPages();
    }

    public String getFileName()
    {
        return fileName;
    }

    public Image renderPage(int pageIndex, float zoomLevel) throws IOException
    {
        float dpi = 72f * zoomLevel;
        BufferedImage img = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.RGB);
        return img;
    }

    @Override
    public void close()
    {
        try 
        {
            document.close();
        } 
        catch (IOException ignored) 
        {

        }
    }
}