package com.example;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import org.apache.pdfbox.Loader;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import com.example.PDFtoCSVExporter;
 
 
public class PDFReaderApp extends JFrame
{
    private static class PdfTab
    {
        final File       file;
        PDDocument       document;
        PDFRenderer      renderer;
        PDFPagePanel     pagePanel;
        JScrollPane      scrollPane;
        int              currentPage  = 0;
        float            zoomLevel    = 1.0f;
        boolean[]        pageIsScanned;
 
        PdfTab(File file)
        { 
            this.file = file;
        }
 
        boolean hasDocument() 
        {
             return document != null; 
        }
        String  shortName()  
        { 
            return file.getName();
        }
    }
 
    private JTabbedPane  tabbedPane;
    private JLabel       pageLabel;
    private JLabel       ocrStatusLabel;
    private JButton      prevBtn, nextBtn, openBtn, zoomInBtn, zoomOutBtn, exportCsvBtn;
    private JTextField   pageField;
    private JSlider      zoomSlider;
    private JLabel       zoomPercentLabel;
    private JProgressBar progressBar;

    private Tesseract tesseract;
    private boolean ocrEnabled  = false;
    private PDFtoCSVExporter csvExporter;

    private int loadingCount = 0;
    private boolean dropHovering = false;

    private static final float ZOOM_STEP = 0.25f;
    private static final float ZOOM_MIN  = 0.25f;
    private static final float ZOOM_MAX  = 4.0f;
    private static final int   OCR_TEXT_THRESHOLD = 10;
 
    private static final Color TOOLBAR_BG = new Color(45, 45, 48);
    private static final Color ACCENT     = new Color(0, 120, 215);
    private static final Color CSV_ACCENT = new Color(30, 140, 80);
    private static final Color OCR_ACCENT = new Color(180, 100, 0);
    private static final Color TAB_BG     = new Color(37, 37, 40);
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
        csvExporter = new PDFtoCSVExporter(tesseract);
        initUI();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private PdfTab activeTab()
    {
        int idx = tabbedPane.getSelectedIndex();
        if(idx < 0)
        {
            return null;
        }
        Component c = tabbedPane.getComponentAt(idx);
        if(!(c instanceof JScrollPane))
        {
            return null;
        }
        return(PdfTab) ((JScrollPane) c).getClientProperty("tab");
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

    private boolean isScannedPage(PdfTab tab, int pageIndex)
    {
        return tab.pageIsScanned != null && pageIndex < tab.pageIsScanned.length && tab.pageIsScanned[pageIndex];
    }

    private void analysePages(PdfTab tab)
    {
        if (!tab.hasDocument())
        {
            return;
        }
        int total = tab.document.getNumberOfPages();
        tab.pageIsScanned = new boolean[total];
        try
        {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int i = 0; i < total; i++)
            {
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String text = stripper.getText(tab.document).trim();
                tab.pageIsScanned[i] = text.length() < OCR_TEXT_THRESHOLD;
            }
        }
        catch (Exception ignored) 
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
        catch (Throwable ex)
        {
            System.err.println("OCR skipped: " + ex.getMessage());
            return null;
        }
    }

    private void initUI()
    {
        setLayout(new BorderLayout());
        getContentPane().setBackground(CANVAS_BG);
        add(buildToolbar(), BorderLayout.NORTH);
        add(buildTabArea(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        setupKeyBindings();
        setupDropTarget();
        updateControls();
    }

    private JPanel buildToolbar()
    {
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(TOOLBAR_BG);
        toolbar.setBorder(new EmptyBorder(6, 10, 6, 10));
 
        // --- left ---
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
 
        openBtn = styledButton("Open PDF", ACCENT);
        openBtn.setToolTipText("Open one or more PDFs (Ctrl + 0)");
        openBtn.addActionListener(e -> openFile());
        left.add(openBtn);
 
        exportCsvBtn = styledButton("Export CSV", CSV_ACCENT);
        exportCsvBtn.setToolTipText("Extract text from every page and save as a CSV file");
        exportCsvBtn.addActionListener(e -> exportToCsv());
        exportCsvBtn.setEnabled(false);
        left.add(exportCsvBtn);
 
        ocrStatusLabel = new JLabel();
        ocrStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        ocrStatusLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        ocrStatusLabel.setOpaque(true);
        ocrStatusLabel.setVisible(false);
        left.add(ocrStatusLabel);
 
        // --- centre: zoom ---
        JPanel centre = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        centre.setOpaque(false);
 
        zoomOutBtn = iconButton("−");
        zoomOutBtn.setToolTipText("Zoom out");
        zoomOutBtn.addActionListener(e -> adjustZoom(-ZOOM_STEP));
 
        zoomSlider = new JSlider(25, 400, 100);
        zoomSlider.setOpaque(false);
        zoomSlider.setPreferredSize(new Dimension(140, 24));
        zoomSlider.addChangeListener(e -> {
            PdfTab tab = activeTab();
            if (tab == null) 
            {
                return;
            }
            tab.zoomLevel = zoomSlider.getValue() / 100f;
            updateZoomLabel(tab);
            if (!zoomSlider.getValueIsAdjusting())
            {
                renderCurrentPage(tab);
            }
        });
 
        zoomSlider.addMouseListener(new MouseAdapter() 
        {
            public void mouseClicked(MouseEvent e)
            {
                if(e.getClickCount() == 2)
                {
                    resetZoom();
                }
            }
        });

        zoomInBtn = iconButton("+");
        zoomInBtn.setToolTipText("Zoom in");
        zoomInBtn.addActionListener(e -> adjustZoom(ZOOM_STEP));
 
        zoomPercentLabel = new JLabel("100%");
        zoomPercentLabel.setForeground(new Color(180, 180, 180));
        zoomPercentLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        zoomPercentLabel.setPreferredSize(new Dimension(40, 16));
        zoomPercentLabel.setHorizontalAlignment(SwingConstants.LEFT);
        zoomPercentLabel.setToolTipText("Double-click the slider to reset to 100%");
        
        JLabel zoomLabel = new JLabel("Zoom:");
        zoomLabel.setForeground(new Color(180, 180, 180));
        zoomLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        centre.add(zoomLabel);
        centre.add(zoomOutBtn);
        centre.add(zoomSlider);
        centre.add(zoomInBtn);
        centre.add(zoomPercentLabel);
 
        // --- right: navigation ---
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);
 
        prevBtn = iconButton("←");
        prevBtn.setToolTipText("Previous Page");
        prevBtn.addActionListener(e -> {
            PdfTab t = activeTab();
            if(t != null)
            {
                goToPage(t, t.currentPage - 1);
            }
        });
 
        pageField = new JTextField("0", 4);
        pageField.setHorizontalAlignment(JTextField.CENTER);
        pageField.setBackground(new Color(60, 60, 65));
        pageField.setForeground(Color.WHITE);
        pageField.setCaretColor(Color.WHITE);
        pageField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(90, 90, 95)), new EmptyBorder(2, 4, 2, 4)));
        pageField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        pageField.addActionListener(e -> jumpToPage());
 
        pageLabel = new JLabel("/0");
        pageLabel.setForeground(new Color(180, 180, 180));
        pageLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
 
        nextBtn = iconButton("→");
        nextBtn.setToolTipText("Next Page");
        nextBtn.addActionListener(e -> {
            PdfTab t = activeTab();
            if (t != null) goToPage(t, t.currentPage + 1);
        });
 
        right.add(prevBtn);
        right.add(pageField);
        right.add(pageLabel);
        right.add(nextBtn);
 
        toolbar.add(left,   BorderLayout.WEST);
        toolbar.add(centre, BorderLayout.CENTER);
        toolbar.add(right,  BorderLayout.EAST);
        return toolbar;
    }

    private JTabbedPane buildTabArea()
    {
        tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        tabbedPane.setBackground(TAB_BG);
        tabbedPane.setForeground(new Color(200, 200, 200));
        tabbedPane.setFont(new Font("SansSerif", Font.PLAIN, 12));
 
        tabbedPane.addChangeListener((ChangeEvent e) -> {
            PdfTab tab = activeTab();
            syncToolbarToBar(tab);
            if (tab != null && tab.hasDocument()) 
            {  
                renderCurrentPage(tab);
            }
        });
 
        return tabbedPane;
    }

    private JScrollPane buildTabCanvas(PdfTab tab)
    {
        tab.pagePanel = new PDFPagePanel();
        tab.pagePanel.setBackground(CANVAS_BG);
        tab.pagePanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        tab.scrollPane = new JScrollPane(tab.pagePanel);
        tab.scrollPane.setBackground(CANVAS_BG);
        tab.scrollPane.getViewport().setBackground(CANVAS_BG);
        tab.scrollPane.setBorder(null);
        tab.scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        tab.scrollPane.getHorizontalScrollBar().setUnitIncrement(16);
        tab.scrollPane.addMouseWheelListener(e -> {
            if(e.isControlDown())
            {
                adjustZoom(e.getWheelRotation() > 0 ? -ZOOM_STEP : ZOOM_STEP);
            } else 
            {
                tab.scrollPane.getVerticalScrollBar().setValue(tab.scrollPane.getVerticalScrollBar().getValue() + e.getUnitsToScroll() * 16);
            }
        });
        setUpDragToScroll(tab);
        tab.scrollPane.putClientProperty("tab", tab);
        return tab.scrollPane;
    }

    private void setUpDragToScroll(PdfTab tab)
    {
        final Point[] dragOrigin = { null };
        tab.pagePanel.addMouseListener(new MouseAdapter() 
        {
            public void mousePressed(MouseEvent e)
            {
                dragOrigin[0] = e.getPoint();
            }
            public void mouseReleased(MouseEvent e)
            {
                dragOrigin[0] = null;
            }
        });
        tab.pagePanel.addMouseMotionListener(new MouseMotionAdapter()
        {
            public void mouseDragged(MouseEvent e)
            {
                if(dragOrigin[0] == null)
                {
                    return;
                }

                JScrollBar h = tab.scrollPane.getHorizontalScrollBar();
                JScrollBar v = tab.scrollPane.getVerticalScrollBar();

                h.setValue(h.getValue() + (dragOrigin[0].x - e.getX()));
                v.setValue(v.getValue() + (dragOrigin[0].y - e.getY()));

                dragOrigin[0] = e.getPoint();
            }
        });
    }

    private JPanel buildStatusBar()
    {
        JPanel bar = new JPanel(new BorderLayout(10,0));
        bar.setBackground(STATUS_BG);
        bar.setBorder(new EmptyBorder(4, 10, 4,10));
        JLabel hint = new JLabel("Ctrl+O open  |  Ctrl+W close tab  |  Ctrl+Scroll zoom  |  \u2190 \u2192 navigate");
        hint.setForeground(new Color(130,130,130));
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        
        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(160, 14));
        progressBar.setVisible(false);
        progressBar.setBackground(new Color(50, 50, 54));
        progressBar.setForeground(ACCENT);
        progressBar.setStringPainted(true);

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
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(6, 14, 6, 14));
        btn.addMouseListener(hoverEffect(btn, bg, bg.brighter()));
        return btn;
    }

    private JButton iconButton(String text)
    {
        JButton btn = new JButton(text);
        Color base = new Color(70,70,75);
        btn.setBackground(base);
        btn.setForeground(BTN_FG);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setPreferredSize(new Dimension(36, 28));
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

    private void setupDropTarget()
    {
        JPanel glassPane = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                if (!dropHovering)
                {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                
                g2.setColor(new Color(0, 120, 215, 55));
                g2.fillRect(0, 0, getWidth(), getHeight());
                
                g2.setColor(new Color(0, 120, 215, 200));
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{10f, 6f}, 0f));
                g2.drawRect(6, 6, getWidth() - 12, getHeight() - 12);
                
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                FontMetrics fm = g2.getFontMetrics();
                String msg = "Drop PDF(s) to open";
                int tx = (getWidth()  - fm.stringWidth(msg)) / 2;
                int ty = (getHeight() + fm.getAscent())       / 2;
                
                g2.setColor(new Color(0, 0, 0, 120));
                g2.drawString(msg, tx + 1, ty + 1);
                g2.setColor(Color.WHITE);
                g2.drawString(msg, tx, ty);
                g2.dispose();
            }
        };
        glassPane.setOpaque(false);
        glassPane.setVisible(true);
        setGlassPane(glassPane);
 
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter()
        {
            @Override
            public void dragEnter(DropTargetDragEvent e)
            {
                if (isPdfDrag(e.getTransferable()))
                {
                    e.acceptDrag(DnDConstants.ACTION_COPY);
                    dropHovering = true;
                    glassPane.repaint();
                }
                else
                {
                    e.rejectDrag();
                }
            }
 
            @Override
            public void dragOver(DropTargetDragEvent e)
            {
                if (isPdfDrag(e.getTransferable()))
                {
                    e.acceptDrag(DnDConstants.ACTION_COPY);
                }
                else
                {
                    e.rejectDrag();
                }
            }
 
            @Override
            public void dragExit(DropTargetEvent e)
            {
                dropHovering = false;
                glassPane.repaint();
            }
 
            @Override
            public void drop(DropTargetDropEvent e)
            {
                dropHovering = false;
                glassPane.repaint();
 
                try
                {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    Transferable t = e.getTransferable();
                    if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                    {
                        @SuppressWarnings("unchecked")
                        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                        for (File file : files)
                        {
                            if (file.getName().toLowerCase().endsWith(".pdf"))
                            {
                                openPDF(file);
                            }
                            else
                            {
                                showNonPdfWarning(file.getName());
                            }
                        }
                    }
                    e.dropComplete(true);
                }
                catch (Exception ex)
                {
                    e.dropComplete(false);
                    showError("Drop failed", ex);
                }
            }
 
            /** Returns true only if the drag payload contains at least one file. */
            private boolean isPdfDrag(Transferable t)
            {
                return t.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
        }, true);
    }
 
    /** Shows a brief non-blocking warning when a non-PDF file is dropped. */
    private void showNonPdfWarning(String filename)
    {
        JOptionPane.showMessageDialog( PDFReaderApp.this, "\"" + filename + "\" is not a PDF file and was skipped.", "Unsupported file", JOptionPane.WARNING_MESSAGE);
    }

    private void setupKeyBindings()
    {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(
            e -> {
                if(e.getID() != KeyEvent.KEY_PRESSED)
                { 
                    return false;
                }

                if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_O)
                {
                    openFile();
                    return true;
                }

                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_W) 
                {
                    closeActiveTab(); 
                    return true; 
                }

                PdfTab tab = activeTab();
                if(tab == null)
                {
                    return false;
                }

                switch (e.getKeyCode())
                {
                    case KeyEvent.VK_LEFT: 
                    case KeyEvent.VK_PAGE_UP:
                        goToPage(tab, tab.currentPage - 1);
                        return true;
                    case KeyEvent.VK_RIGHT: 
                    case KeyEvent.VK_PAGE_DOWN:
                        goToPage(tab, tab.currentPage + 1);
                        return true;
                    case KeyEvent.VK_HOME:
                        goToPage(tab, 0);
                        return true;
                    case KeyEvent.VK_END:
                        if(tab.hasDocument()) 
                        {
                            goToPage(tab, tab.document.getNumberOfPages() - 1);
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
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PDF Files (*.PDF)", "pdf"));
        if(chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
        {
            for(File file : chooser.getSelectedFiles())
            {
                openPDF(file);
            }
        }
    }

    private void openPDF(File file)
    {
        for(int i = 0; i < tabbedPane.getTabCount(); i++)
        {
            Component c = tabbedPane.getComponentAt(i);
            if(!(c instanceof JScrollPane))
            {
                continue;
            }

            PdfTab existing = (PdfTab)((JScrollPane) c).getClientProperty("tab");
            if(existing != null && existing.file.equals(file))
            {
                tabbedPane.setSelectedIndex(i);
                return;
            }
        }

        PdfTab tab = new PdfTab(file);
        JScrollPane canvas = buildTabCanvas(tab);
 
        tabbedPane.addTab(file.getName(), canvas);
        int tabIndex = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(tabIndex, buildTabHeader(file.getName(), tab));
        tabbedPane.setSelectedIndex(tabIndex);
 
        loadingCount++;
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Loading " + file.getName() + "...");
        setTitle("PDF Reader - Loading...");

       SwingWorker<PDDocument, Void> worker = new SwingWorker<>()
       {
            @Override protected PDDocument doInBackground() throws Exception
            {
                return Loader.loadPDF(file);
            }

            @Override protected void done()
            {
              loadingCount--;
              if(loadingCount <= 0)
              {
                loadingCount = 0;
                progressBar.setVisible(false);
              }

              try
              {
                tab.document   = get();
                tab.renderer   = new PDFRenderer(tab.document);
                tab.currentPage = 0;
                tab.zoomLevel  = calcFitToWidthZoom(tab);
                setTitle("PDF Reader - " + file.getName());
                syncToolbarToBar(tab);

                new Thread(() -> {
                    analysePages(tab);
                    SwingUtilities.invokeLater(() ->{
                        if(tab == activeTab())
                        {
                            renderCurrentPage(tab);
                        }
                    });
                }, "pdf-analyser").start();
              } 
              catch (Exception ex)
              {
                showError("Failed to load PDF: " + file.getName(), ex);
                int idx = indexOfTab(tab);
                if(idx >= 0)
                {
                    tabbedPane.removeTabAt(idx);
                }
                setTitle("PDF Reader");
              }
            }
       };
        worker.execute();
    }

    private JPanel buildTabHeader(String title, PdfTab tab)
    {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        header.setOpaque(false);
 
        JLabel label = new JLabel(title);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
 
        JButton closeBtn = new JButton("x");
        closeBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        closeBtn.setPreferredSize(new Dimension(18, 18));
        closeBtn.setFocusPainted(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setForeground(new Color(160, 160, 160));
        closeBtn.setToolTipText("Close tab (Ctrl+W)");
        closeBtn.addActionListener(e -> closeTab(tab));
        closeBtn.addMouseListener(new MouseAdapter()
        {
            public void mouseEntered(MouseEvent e) 
            { 
                closeBtn.setForeground(Color.WHITE);
            }
            public void mouseExited(MouseEvent e) 
            { 
                closeBtn.setForeground(new Color(160, 160, 160)); 
            }
        });
 
        header.add(label);
        header.add(closeBtn);
        return header;
    }
 
    private void closeActiveTab()
    {
        PdfTab tab = activeTab();
        if (tab != null)
        {
            closeTab(tab);
        }
    }
 
    private void closeTab(PdfTab tab)
    {
        int idx = indexOfTab(tab);
        if (idx < 0)
        {
            return;
        }
        try 
        { 
            if (tab.document != null) 
            {
                tab.document.close();
            } 
        }
        catch (Exception ignored) 
        {

        }
        tabbedPane.removeTabAt(idx);
        updateControls();
        if (tabbedPane.getTabCount() == 0)
        {
            setTitle("PDF Reader");
        }
    }
 
    private int indexOfTab(PdfTab tab)
    {
        for (int i = 0; i < tabbedPane.getTabCount(); i++)
        {
            Component c = tabbedPane.getComponent(i);
            if(c instanceof JScrollPane sp && sp.getClientProperty("tab") == tab)
            {
                return i;
            }
        }
        return -1;
    }

    private void exportToCsv()
    {
        PdfTab tab = activeTab();
        if (tab == null || !tab.hasDocument())
        {
            return;
        }
 
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save CSV File");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        chooser.setSelectedFile(new File(tab.file.getName().replaceAll("(?i)\\.pdf$", "") + ".csv"));
 
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }
 
        File dest = chooser.getSelectedFile();
        if (!dest.getName().toLowerCase().endsWith(".csv"))
        {
            dest = new File(dest.getAbsolutePath() + ".csv");
        }
 
        final File finalDest = dest;
        final int  total     = tab.document.getNumberOfPages();
 
        exportCsvBtn.setEnabled(false);
        progressBar.setVisible(true);
        progressBar.setIndeterminate(false);
        progressBar.setValue(0);
        progressBar.setString("Exporting... 0 / " + total);
        progressBar.setForeground(CSV_ACCENT);
 
        SwingWorker<Void, Integer> worker = new SwingWorker<>()
        {
            @Override protected Void doInBackground() throws Exception
            {
                csvExporter.export(tab.document, tab.renderer, finalDest, (cur, total) -> publish(cur));
                return null;
            }
            @Override protected void process(java.util.List<Integer> chunks)
            {
                int latest = chunks.get(chunks.size() - 1);
                progressBar.setValue((int)((latest / (float) total) * 100));
                progressBar.setString("Exporting... " + latest + " / " + total);
            }
            @Override protected void done()
            {
                exportCsvBtn.setEnabled(true);
                progressBar.setVisible(false);
                progressBar.setForeground(ACCENT);
                try
                {
                    get();
                    JOptionPane.showMessageDialog(PDFReaderApp.this, "CSV saved to:\n" + finalDest.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
                }
                catch (Exception ex) 
                { 
                    showError("Export failed", ex);
                }
            }
        };
        worker.execute();
    }

    private void goToPage(PdfTab tab, int page)
    {
        if(!tab.hasDocument())
        {
            return;
        }
        int total = tab.document.getNumberOfPages();
        if(page < 0 || page >= total)
        {
            return;
        }
        tab.currentPage = page;
        updateControls();
        renderCurrentPage(tab);
        tab.scrollPane.getVerticalScrollBar().setValue(0);
    }

    private void jumpToPage()
    {
        PdfTab tab = activeTab();
        if(tab == null || !tab.hasDocument())
        {
            return;
        }
        try
        {
            goToPage(tab, Integer.parseInt(pageField.getText().trim()) -1);
        }
        catch(NumberFormatException ex)
        {
            updateControls();
        }
    }

    private void adjustZoom(float delta)
    {
        PdfTab tab = activeTab();
        if (tab == null) 
        {
            return;
        }
        float newZoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, tab.zoomLevel + delta));
        if (newZoom == tab.zoomLevel)
        {
            return;
        }
        tab.zoomLevel = newZoom;
        zoomSlider.setValue(Math.round(tab.zoomLevel * 100));
        updateZoomLabel(tab);
        renderCurrentPage(tab);
    }

    private void resetZoom()
    {
        PdfTab tab = activeTab();
        if (tab == null) return;
        tab.zoomLevel = 1.0f;
        zoomSlider.setValue(100);
        updateZoomLabel(tab);
        renderCurrentPage(tab);
    }

    private void updateZoomLabel(PdfTab tab)
    {
        zoomPercentLabel.setText(Math.round(tab.zoomLevel *100) + "%");
    }

    private float calcFitToWidthZoom(PdfTab tab)
    {
        if (!tab.hasDocument())
        {
            return 1.0f;
        }

        try
        {
            float pageWidthPt = tab.document.getPage(0).getMediaBox().getWidth();
            int viewWidth = tab.scrollPane.getViewport().getWidth();
            if (viewWidth <= 0) 
            {
                viewWidth = tab.scrollPane.getPreferredSize().width;
            }
            float fit = (viewWidth - 48) / pageWidthPt;
            return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, fit));
        }

        catch (Exception ex)
        { 
            return 1.0f;
        }
    }
    private void renderCurrentPage(PdfTab tab)
    {
        if (!tab.hasDocument())
        {
            return;
        }
 
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Rendering...");
 
        final int     pageIndex = tab.currentPage;
        final float   zoom      = tab.zoomLevel;
        final boolean scanned   = isScannedPage(tab, pageIndex);
 
        SwingWorker<RenderResult, Void> worker = new SwingWorker<>()
        {
            @Override protected RenderResult doInBackground() throws Exception
            {
                BufferedImage image   = tab.renderer.renderImage(pageIndex, zoom);
                String        ocrText = (scanned && ocrEnabled) ? runOCR(image) : null;
                return new RenderResult(image, ocrText, scanned);
            }
            @Override protected void done()
            {
                progressBar.setVisible(false);
                try
                {
                    RenderResult r = get();
                    tab.pagePanel.setImage(r.image);
                    tab.pagePanel.setOcrText(r.ocrText);
                    tab.pagePanel.revalidate();
                    tab.pagePanel.repaint();
                    if (tab == activeTab()) updateOcrBadge(r);
                }
                catch (Exception ex) { showError("Failed to render page", ex); }
            }
        };
        worker.execute();
    }
 
    private void updateOcrBadge(RenderResult result)
    {
        if (!result.isScanned) 
        { 
            ocrStatusLabel.setVisible(false); 
            return; 
        }
        ocrStatusLabel.setVisible(true);
        if (result.ocrText != null && !result.ocrText.isBlank())
        {
            ocrStatusLabel.setText("OCR");
            ocrStatusLabel.setBackground(OCR_ACCENT);
            ocrStatusLabel.setForeground(Color.WHITE);
            ocrStatusLabel.setToolTipText("Scanned page - text extracted via OCR");
        }
        else
        {
            ocrStatusLabel.setText(ocrEnabled ? "Scanned" : "Scanned - no OCR");
            ocrStatusLabel.setBackground(new Color(80, 40, 40));
            ocrStatusLabel.setForeground(Color.WHITE);
            ocrStatusLabel.setToolTipText(ocrEnabled ? "Scanned page - OCR found no text" : "Scanned page - install Tesseract to enable OCR");
        }
    }


    private void syncToolbarToBar(PdfTab tab)
    {
        if(tab == null || !tab.hasDocument())
        {
            updateControls();
            return;
        }

        zoomSlider.setValue(Math.round(tab.zoomLevel * 100));
        updateZoomLabel(tab);
        setTitle("PDF Reader - " + tab.file.getName());
        updateControls();
    }

    private void updateControls()
    {
        PdfTab tab = activeTab();
        boolean hasDoc = tab != null && tab.hasDocument();
        int total = hasDoc ? tab.document.getNumberOfPages() : 0;
 
        prevBtn.setEnabled(hasDoc && tab.currentPage > 0);
        nextBtn.setEnabled(hasDoc && tab.currentPage < total - 1);
        zoomInBtn.setEnabled(hasDoc && tab.zoomLevel < ZOOM_MAX);
        zoomOutBtn.setEnabled(hasDoc && tab.zoomLevel > ZOOM_MIN);
        pageField.setEnabled(hasDoc);
        zoomSlider.setEnabled(hasDoc);
        exportCsvBtn.setEnabled(hasDoc);
        ocrStatusLabel.setVisible(false);
 
        if (hasDoc)
        {
            pageField.setText(String.valueOf(tab.currentPage + 1));
            pageLabel.setText("/" + total);
            zoomPercentLabel.setText(Math.round(tab.zoomLevel * 100) + "%");
        }
        else
        {
            pageField.setText("0");
            pageLabel.setText("/0");
            zoomPercentLabel.setText("100%");
        }
    }

    private void showError(String message, Exception ex)
    {
        JOptionPane.showMessageDialog(this, message + "\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static class RenderResult
    {
        final BufferedImage image;
        final String        ocrText;
        final boolean       isScanned;

        RenderResult(BufferedImage img, String ocr, boolean scanned)
        {
            image = img; ocrText = ocr; isScanned = scanned;
        }
    }

    /*private static class PDFtoCsvExporter
    {
        private final Tesseract tesseract;

        PDFtoCsvExporter(Tesseract tesseract)
        {
            this.tesseract = tesseract;
        }

        void export(PDDocument document, PDFRenderer renderer, File destination, java.util.function.BiConsumer<Integer, Integer> progressCallback) throws Exception
        {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(destination)))
            {
                PDFTextStripper stripper = new PDFTextStripper();
                int totalPages = document.getNumberOfPages();
                writer.println("Page,Text");

                for (int pageIndex = 0; pageIndex < totalPages; pageIndex++)
                {
                    stripper.setStartPage(pageIndex + 1);
                    stripper.setEndPage(pageIndex + 1);
                    String text = stripper.getText(document).trim();

                    if (text.length() < OCR_TEXT_THRESHOLD && tesseract != null)
                    {
                        try
                        {
                            BufferedImage pageImage = renderer.renderImage(pageIndex, 2.0f);
                            String ocrText = tesseract.doOCR(pageImage);
                            if (ocrText != null && !ocrText.isBlank())
                            {
                                text = ocrText.trim();
                            }
                        }
                        catch (TesseractException ignore)
                        {
                        }
                    }

                    String csvText = text.replace("\"", "\"\"");
                    writer.print(pageIndex + 1);
                    writer.print(",\"");
                    writer.print(csvText);
                    writer.println("\"");

                    if (progressCallback != null)
                    {
                        progressCallback.accept(pageIndex + 1, totalPages);
                    }
                }
            }
        }
    }*/

    public static void main(String[] args)
    {
        SwingUtilities.invokeLater(() -> {
            try
            {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
            catch (Exception ignored)
            {
            }
            new PDFReaderApp();
        });
    }
}
