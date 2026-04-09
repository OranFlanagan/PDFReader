package com.example;

import javax.swing.JPanel;
import java.awt.Image;

public class PDFPagePanel extends JPanel
{
    private Image image;

    public void setImage(Image img) {
        this.image = img;
        repaint();
    }
}
