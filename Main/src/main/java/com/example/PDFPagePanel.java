package com.example;

import javax.swing.*;
import java.awt.*;

public class PDFPagePanel extends JPanel
{
    private Image image;
    private static final int SHADOW_SIZE = 8;
    private static final int PAGE_PADDING = 24;
    public PDFPagePanel()
    {
        setBackground(new Color(60, 60, 65));
    }

    public void setImage(Image img)
    {
        this.image = img;
        if(img != null)
        {
            int w = img.getWidth(this) + PAGE_PADDING * 2 + SHADOW_SIZE;
            int h = img.getHeight(this) + PAGE_PADDING * 2 + SHADOW_SIZE;
            setPreferredSize(new Dimension(w, h));
            revalidate();
        }
    }

    @Override protected void paintComponent(Graphics g)
    {
       super.paintComponent(g);
       if(image == null)
       {
            paintPlaceholder(g); 
            return;
       }

       Graphics2D g2 = (Graphics2D) g.create();
       g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
       g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
       g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

       int imgW = image.getWidth(null);
       int imgH = image.getHeight(null);
       int x = (getWidth() - imgW) / 2;
       int y = (getHeight() - imgH) / 2;
       if(y < PAGE_PADDING) y = PAGE_PADDING;

       for(int i = SHADOW_SIZE; i > 0; i--)
       {
           int alpha = (int)(180 * (1 - (float) i / SHADOW_SIZE));
           g2.setColor(new Color(0, 0, 0, alpha));
           g2.fillRoundRect(x + i, y + i, imgW, imgH, imgW, imgH);
       }

       g2.setColor(Color.WHITE);
       g2.fillRect(x, y, imgW, imgH);
       g2.drawImage(image, x, y, null);
       g2.setColor(new Color(0,0,0,80));
       g2.drawRect(x, y, imgW - 1, imgH - 1);
       g2.dispose();
    }

    private void paintPlaceholder(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int boxW = 300, boxH = 400;
        int bx = (getWidth() - boxW) / 2;
        int by = (getHeight() - boxH) / 2;

        g2.setColor(new Color(80,80,85));
        g2.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0 , new float[]{10, 8}, 0));
        g2.drawRoundRect(bx, by, boxW, boxH, 12, 12);
        g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(100,100,108));
        int lx = bx +40, lw = boxW - 80;
        g2.drawLine(lx, by + 120, lx + lw, by + 120);
        g2.drawLine(lx, by + 155, lx + lw, by + 155);
        g2.drawLine(lx, by + 190, lx + lw * 2 / 3, by + 190);

        g2.setColor(new Color(130, 130, 140));
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        FontMetrics fm = g2.getFontMetrics();
        String msg = "Open a PDF or drag one here";
        g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, by + boxH - 30);
        g2.dispose();
    }
}
