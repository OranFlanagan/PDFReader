package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PDFPagePanel extends JPanel
{
    private Image image;
    private String ocrText;

    private static final int    OVERLAY_PADDING  = 12;
    private static final int    OVERLAY_MAX_LINES = 6;
    private static final Color  OVERLAY_BG       = new Color(0, 0, 0, 170);
    private static final Color  OVERLAY_FG       = new Color(240, 240, 240);
    private static final Font   OVERLAY_FONT     = new Font("Segoe UI", Font.PLAIN, 12);
    
    public PDFPagePanel()
    {
        setOpaque(true);
    }

    public void setImage(Image img)
    {
        this.image = img;
        if(image != null)
        {
            setPreferredSize(new Dimension(
                image.getWidth(this) + 40,
                image.getHeight(this) + 40
            ));
        }
    }

    public void setOcrText(String text)
    {
        this.ocrText = (ocrText != null && !ocrText.isBlank()) ? text.trim() : null;
    }

    @Override protected void paintComponent(Graphics g)
    {
       super.paintComponent(g);
       
       if(image == null)
       {
           return;
       }

       Graphics2D g2 = (Graphics2D) g.create();
       g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
       g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

       int imgW = image.getWidth(this);
       int imgH = image.getHeight(this);
       int x = Math.max(20,(getWidth() - imgW) / 2);
       int y = Math.max(20,(getHeight() - imgH) / 2);


       g2.setColor(new Color(0,0,0,80));
       g2.fillRect(x + 4, y + 4, imgW, imgH);

       g2.drawImage(image, x, y, this);

       if(ocrText != null)
       {
             paintPlaceholder(g2, x, y, imgW, imgH);
       }

       g2.dispose();
    }

    private void paintPlaceholder(Graphics2D g2, int imgX, int imgY, int imgW, int imgH)
    {
        g2.setFont(OVERLAY_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int lineH = fm.getHeight();

        String[] rawLines = ocrText.split("\\r?\\n");
        String[] lines = new String[Math.min(rawLines.length, OVERLAY_MAX_LINES)];
        System.arraycopy(rawLines, 0, lines, 0, lines.length);
        boolean truncated = rawLines.length > OVERLAY_MAX_LINES;

        int overlayH = OVERLAY_PADDING * 2 + lines.length * lineH +(truncated ? lineH : 0);
        int overlayY = imgY + imgH - overlayH;

        g2.setColor(OVERLAY_BG);
        g2.fillRect(imgX, overlayY, imgW, overlayH);

        g2.setColor(OVERLAY_FG);
        int textY = overlayY + OVERLAY_PADDING + fm.getAscent();
        
        for(String line : lines)
        {
            String displayed = clipText(g2, fm, line, imgW - OVERLAY_PADDING * 2);  
            g2.drawString(displayed, imgX + OVERLAY_PADDING, textY);
            textY += lineH;
        }

        if(truncated)
        {
            g2.setColor(new Color(180,180,180));
            g2.drawString("... (text continues)", imgX + OVERLAY_PADDING, textY);
        }
    }

        private String clipText(Graphics2D g2, FontMetrics fm, String text, int maxWidth)
        {
            if(fm.stringWidth(text) <= maxWidth)
            {
                return text;
            }
            String ellipsis = "...";
            while(!text.isEmpty() && fm.stringWidth(text + ellipsis) > maxWidth)
            {
                text = text.substring(0, text.length() - 1);
            }  
            return text + ellipsis; 
        }
}
