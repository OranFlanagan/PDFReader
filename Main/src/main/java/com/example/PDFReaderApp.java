package com.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;

import org.apache.pdfbox.rendering.PDFRenderer;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

public class PDFReaderApp extends JFrame
{
    private PDFPagePanel pagePanel;
    private JScrollPane scrollPane;
    private JLabel pageLabel;
    private JLabel fileLabel;
    private JButton prevBtn, nextBtn, openBtn, zoomInBtn, zoomOutBtn;
    private JTextField pageField;
    private JSlider zoomSlider;
    private JProgressBar progressBar;
    private JLabel ocrStatusLabel;

    private PDDocument pdfDocument;
    private PDFRenderer pdfRenderer;
    private int currentPage = 0;
    private float zoomLevel = 1.0f;


    private Tesseract tesseract;
    private boolean ocrEnabled = false;
    private boolean[] pageIsScanned;

    private static final float ZOOM_STEP = 0.25f;
    private static final float ZOOM_MIN  = 0.25f;
    private static final float ZOOM_MAX  = 4.0f;
    private static final int OCR_TEXT_THRESHOLD = 10;

    private static final Color TOOLBAR_BG = new Color(45, 45, 48);
    private static final Color ACCENT     = new Color(0, 120, 215);
    private static final Color OCR_ACCENT = new Color(100, 100, 0);
    private static final Color BTN_FG     = Color.WHITE;
    private static final Color STATUS_BG  = new Color(37, 37, 38);
    private static final Color CANVAS_BG  = new Color(60, 60, 65);

    public PDFReaderApp()
    {
        super("PDF Reader");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(800,600));
        setPreferredSize(new Dimension(1100,800));
        initTesseract();
        initUI();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initTesseract()
    {
        try
        {
            tesseract = new Tesseract();
            String tessPrefix = System.getenv("TESSDATA_PREFIX");
            if(tessPrefix != null && !tessPrefix.isBlank())
            {
                tesseract.setDatapath(tessPrefix);
            }
            tesseract.setLanguage("eng");
            ocrEnabled = true;
        }
        catch (Exception ex)
        {
            tesseract = null;
            ocrEnabled = false;
        }
    }
    private boolean isScannedPage(int pageIndex)
    {
        if(pdfDocument == null && pageIndex < pageIsScanned.length)
        {
            return pageIsScanned[pageIndex];
        }
        return false;
    }

    private void analysePages()
    {
        if(pdfDocument == null)
        {
            return;
        }
        int total = pdfDocument.getNumberOfPages();
        pageIsScanned = new boolean[total];
        try
        {
            PDFTextStripper stripper = new PDFTextStripper();
            for(int i = 0; i < total; i++)
            {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String text = stripper.getText(pdfDocument).trim();
                pageIsScanned[i] = text.length() < OCR_TEXT_THRESHOLD;
            }
        }
        catch(Exception ex)
        {

        }
    }

    private String runOCR(BufferedImage image)
    {
        if (!ocrEnabled || tesseract == null)
        { 
            return null;
        }
        try
        {
            return tesseract.doOCR(image);
        }
        catch (TesseractException ex)
        {
            return null;
        }
    }
    private void initUI()
    {
       setLayout(new BorderLayout());
       getContentPane().setBackground(CANVAS_BG);
       add(buildToolbar(), BorderLayout.NORTH);
       add(buildCanvas(), BorderLayout.CENTER);
       add(buildStatusBar(), BorderLayout.SOUTH);
       setupKeyBindings();
       updateControls();
    }

    private JPanel buildToolbar()
    {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(TOOLBAR_BG);
        toolbar.setBorder(new EmptyBorder(6, 10, 6, 10));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        openBtn = styledButton("Open PDF", ACCENT);
        openBtn.addActionListener(e -> openFile());
        left.add(openBtn);
        fileLabel = new JLabel("No file loaded");
        fileLabel.setForeground(new Color(180, 180, 180));
        fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        left.add(fileLabel);

        ocrStatusLabel = new JLabel();
        ocrStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        ocrStatusLabel.setBorder(new EmptyBorder(2,8,2,8));
        ocrStatusLabel.setOpaque(true);
        ocrStatusLabel.setVisible(false);
        left.add(ocrStatusLabel);
        
        JPanel centre = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        centre.setOpaque(false);
        zoomOutBtn = iconButton("-");
        zoomOutBtn.addActionListener(e -> adjustZoom(-ZOOM_STEP));
        zoomSlider = new JSlider(25, 400, 100);
        zoomSlider.setOpaque(false);
        zoomSlider.setPreferredSize(new Dimension(140, 24));
        zoomSlider.addChangeListener(e -> {
            if(!zoomSlider.getValueIsAdjusting())
            {
                zoomLevel = zoomSlider.getValue() / 100f;
                renderCurrentPage();
            }
        });
        zoomInBtn = iconButton("+");
        zoomInBtn.addActionListener(e -> adjustZoom(ZOOM_STEP));
        JLabel zoomLabel = new JLabel("Zoom:");
        zoomLabel.setForeground(new Color(180, 180, 180));
        zoomLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        centre.add(zoomLabel);
        centre.add(zoomOutBtn);
        centre.add(zoomSlider);
        centre.add(zoomInBtn);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
        prevBtn = iconButton("<-");
        prevBtn.addActionListener(e -> goToPage(currentPage - 1));
        pageField = new JTextField("0",4);
        pageField.setHorizontalAlignment(JTextField.CENTER);
        pageField.setBackground(new Color(60, 60, 65));
        pageField.setForeground(Color.WHITE);
        pageField.setCaretColor(Color.WHITE);
        pageField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(90,90,95)),
            new EmptyBorder(2,4,2,4)));
        pageField.setFont(new Font("Segoe UI", Font.PLAIN, 12));   
        pageField.addActionListener(e -> jumpToPage());
        pageLabel = new JLabel("/0");
        pageLabel.setForeground(new Color(180,180,180));
        pageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        nextBtn = iconButton("->");
        nextBtn.addActionListener(e -> goToPage(currentPage + 1));
        right.add(prevBtn);
        right.add(pageField);
        right.add(pageLabel);
        right.add(nextBtn);
        
        toolbar.add(left, BorderLayout.WEST);
        toolbar.add(centre, BorderLayout.CENTER);
        toolbar.add(right, BorderLayout.EAST);
        return toolbar;
    }

    private JScrollPane buildCanvas()
    {
        pagePanel = new PDFPagePanel();
        pagePanel.setBackground(CANVAS_BG);
        scrollPane = new JScrollPane(pagePanel);
        scrollPane.setBackground(CANVAS_BG);
        scrollPane.getViewport().setBackground(CANVAS_BG);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        scrollPane.addMouseWheelListener(e -> {
            if(e.isControlDown())
            {
                adjustZoom(e.getWheelRotation() > 0 ? -ZOOM_STEP : ZOOM_STEP);
            } else 
            {
                scrollPane.getVerticalScrollBar().setValue(scrollPane.getVerticalScrollBar().getValue() + e.getUnitsToScroll() * 16);
            }
        });
        return scrollPane;
    }

    private JPanel buildStatusBar()
    {
        JPanel bar = new JPanel(new BorderLayout(10,0));
        bar.setBackground(STATUS_BG);
        bar.setBorder(new EmptyBorder(4, 10, 4,10));
        JLabel hint = new JLabel("Ctrl + Scroll to zoom |  <- -> to navigate  | Drag and drop a PDF file");
        hint.setForeground(new Color(130,130,130));
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(140, 14));
        progressBar.setVisible(false);
        progressBar.setBackground(new Color(50, 50, 54));
        progressBar.setForeground(ACCENT);
        bar.add(hint, BorderLayout.WEST);
        bar.add(progressBar, BorderLayout.EAST);
        return bar;
    }

    private JButton styledButton(String text, Color bg)
    {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(BTN_FG);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        btn.addMouseListener(hoverEffect(btn, bg, bg.brighter()));
        return btn;
    }

    private JButton iconButton(String text)
    {
        JButton btn = new JButton(text);
        btn.setBackground(new Color(70,70,75));
        btn.setForeground(BTN_FG);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setPreferredSize(new Dimension(32, 28));
        Color base = new Color(70,70,75);
        btn.addMouseListener(hoverEffect(btn, base, new Color(90, 90, 95)));
        return btn;
    }

    private MouseAdapter hoverEffect(JButton btn, Color normal, Color hover)
    {
        return new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e)
            {
                btn.setBackground(hover);
            }
            public void mouseExited(MouseEvent e)
            {
                btn.setBackground(normal);
            }
        };
    }

    private void setupKeyBindings()
    {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(
            e -> {
                if(e.getID() != KeyEvent.KEY_PRESSED)
                    { 
                        return false;
                    }
                switch (e.getID())
                {
                    case KeyEvent.VK_LEFT: 
                    case KeyEvent.VK_PAGE_UP:
                        goToPage(currentPage - 1);
                        return true;
                    case KeyEvent.VK_RIGHT: 
                    case KeyEvent.VK_PAGE_DOWN:
                        goToPage(currentPage + 1);
                        return true;
                    case KeyEvent.VK_HOME:
                        goToPage(0);
                        return true;
                    case KeyEvent.VK_END:
                        if(pdfDocument != null)
                        {
                            goToPage(pdfDocument.getNumberOfPages() - 1);
                        }
                        return true;
                }
                return false;
            }
        );
    }

    private void openFile()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open PDF Files");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files (*.PDF)", "pdf"));
        if(chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            openPDF(chooser.getSelectedFile());
        }
    }

    private void openPDF(File file)
    {
       progressBar.setVisible(true);
       progressBar.setIndeterminate(true);
       setTitle("PDF Reader - Loading");
       ocrStatusLabel.setVisible(false);

       SwingWorker<PDDocument, Void> worker = new SwingWorker<>()
       {
            @Override protected PDDocument doInBackground() throws Exception
            {
                return Loader.loadPDF(file);
            }

            @Override protected void done()
            {
              progressBar.setVisible(false);
              try
              {
                if(pdfDocument != null)
                {
                    pdfDocument.close();
                    pdfRenderer = null;
                }
                pdfDocument = get();
                pdfRenderer = new PDFRenderer(pdfDocument);
                currentPage = 0;
                zoomLevel = 1.0f;
                zoomSlider.setValue(100);
                fileLabel.setText(file.getName());
                setTitle("PDF Reader - " + file.getName());
                updateControls();

                new Thread(() -> {
                    analysePages();
                    SwingUtilities.invokeLater(() -> renderCurrentPage());
                }, "pdf-analyser").start();
              } catch (Exception ex)
              {
                showError("Failed to load PDF: ", ex);
                setTitle("PDF Reader");
              }
            }
       };
        worker.execute();
    }

    private void goToPage(int page)
    {
        if(pdfDocument == null)
        {
            return;
        }
        int total = pdfDocument.getNumberOfPages();
        if(page >= 0 && page < total)
        {
            return;
        }
        currentPage = page;
        updateControls();
        renderCurrentPage();
        scrollPane.getVerticalScrollBar().setValue(0);
    }

    private void jumpToPage()
    {
        if(pdfDocument == null)
        {
            return;
        }
        try
        {
            int page = Integer.parseInt(pageField.getText().trim()) - 1;
            goToPage(page);
        } catch (NumberFormatException ex)
        {
            updateControls();
        }
    }

    private void adjustZoom(float delta)
    {
       float newZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoomLevel + delta));
       if(newZoom == zoomLevel)
       {
           return;
       }
       zoomLevel = newZoom;
       zoomSlider.setValue(Math.round(zoomLevel * 100));
       renderCurrentPage();
    }

    private void renderCurrentPage()
    {
        if(pdfDocument == null || pdfRenderer == null)
        {
            return;
        }
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);

        final int pageIndex = currentPage;
        final float zoom = zoomLevel;
        final boolean scanned = isScannedPage(pageIndex);

        SwingWorker<RenderResult, Void> worker = new SwingWorker<>()
        {
            @Override protected RenderResult doInBackground() throws Exception
            {  
                BufferedImage image = pdfRenderer.renderImage(pageIndex, zoom);
                String ocrText = null;
                if(scanned && ocrEnabled)
                {
                    ocrText = runOCR(image);
                }
                return new RenderResult(image, ocrText, scanned);
            }
           @Override protected void done() 
           {
            progressBar.setVisible(false);
            try
            {
                RenderResult result = get();
                pagePanel.setImage(result.image);
                pagePanel.setOcrText(result.ocrText);
                pagePanel.revalidate();
                pagePanel.repaint();
                updateControls(result);
            }
            catch(Exception ex)
            {
                showError("Failed to render page: ", ex);
            }
            }
        };
        worker.execute();
    }

    private void updateControls(RenderResult result)
    {
        if(!result.isScanned)
        {
            ocrStatusLabel.setVisible(false);
            return;
        } 
        ocrStatusLabel.setVisible(true);

        if(result.ocrText != null && !result.ocrText.isBlank())
        {
            ocrStatusLabel.setText("OCR");
            ocrStatusLabel.setBackground(OCR_ACCENT);
            ocrStatusLabel.setForeground(Color.WHITE);
            ocrStatusLabel.setToolTipText("This page is scanned - Text extracted via OCR");
        }

        else if(ocrEnabled)
        {
            ocrStatusLabel.setText("Scanned");
            ocrStatusLabel.setBackground(new Color(100,50,50))
            ocrStatusLabel.setForeground(Color.WHITE);
            ocrStatusLabel.setToolTipText("Scanned page - OCR found no test");
        }

        else
        {
            ocrStatusLabel.setText("Scanned - no OCR");
            ocrStatusLabel.setBackground(new Color(80,80,40));
            ocrStatusLabel.setForeground(Color.WHITE);
            ocrStatusLabel.setToolTipText("Scanned page - OCR is disabled");
        }
    }

    private void updateControls()
    {
        boolean hasDoc = pdfDocument != null;
        int total = hasDoc ? pdfDocument.getNumberOfPages() : 0;
        prevBtn.setEnabled(hasDoc && currentPage > 0);
        nextBtn.setEnabled(hasDoc && currentPage < total - 1);
        zoomInBtn.setEnabled(hasDoc && zoomLevel < ZOOM_MAX);
        zoomOutBtn.setEnabled(hasDoc && zoomLevel > ZOOM_MIN);
        pageField.setEnabled(hasDoc);
        zoomSlider.setEnabled(hasDoc);

        if(hasDoc)
        {
            pageField.setText(String.valueOf(currentPage + 1));
            pageLabel.setText("/" + total);
        }
        else
        {
            pageField.setText("0");
            pageLabel.setText("/0");
        }
    }

    private void showError(String message, Exception ex)
    {
        JOptionPane.showMessageDialog(this, message + "\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static class RenderResult
    {
        final BufferedImage image;
        final String ocrText;
        final boolean isScanned;

        RenderResult(BufferedImage image, String ocrText, boolean isScanned)
        {
            this.image = image;
            this.ocrText = ocrText;
            this.isScanned = isScanned;
        }
    }
    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(() -> {
            try
            {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } 
            catch (Exception ignored)
            {
                // Ignore and use default
            }
            new PDFReaderApp();
        });
    }
}
