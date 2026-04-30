A simple PDF viewer that lets you open, read, and export PDF files to CSV — built for SG Education quote processing.

## How to install

1. Go to the **Releases** page on the right side of this GitHub page
2. Download **PDFReader.zip**
3. Right-click the zip file and click **Extract All**
4. Open the extracted **PDFReaderApp** folder
5. Left click on **PDFReader.exe** and click **Show More Options**
6. Once there click on **send to** and then click on **Desktop (Create Shortcut)**
7. Double-click the **PDF Reader.exe** shortcut on your desktop to open the app

> ⚠️ **Important:** Do not move or delete the PDFReaderApp folder after extracting it. The desktop shortcut needs it to stay in the same place.

---

## How to use

### Opening a PDF
- Click **Open PDF** in the top left, or
- Drag and drop a PDF file directly onto the app window, or
- Press **Ctrl + O**

You can open multiple PDFs at once — each one opens in its own tab.

### Navigating pages
- Click the **← →** arrows to move between pages
- Type a page number in the box and press **Enter** to jump to that page
- Use the **keyboard arrow keys** to navigate
- Press **Ctrl + W** to close the current tab

### Zooming
- Use the **− +** buttons or drag the zoom slider
- Scroll with **Ctrl + mouse wheel** to zoom in and out
- **Double-click the slider** to reset zoom to 100%

### Exporting to CSV
- Open the PDF you want to export
- Click **Export CSV**
- Choose where to save the file
- Open the saved `.csv` file in Excel

## Requirements

- **Windows 10 or 11**
- No Java installation needed — it is bundled inside the app

---

## Troubleshooting

**The app won't open**
Make sure the `jre` folder is in the same folder as `PDFReader.exe`. If you moved the EXE on its own, move it back.

**The shortcut stopped working**
Delete the old shortcut from your desktop and double-click `PDFReader.exe` again to recreate it.

**The CSV is missing some information**
The export is designed for SG Education quote PDFs. Other PDF formats may not parse correctly.

**I get a Windows security warning when opening the app**
Click **More info** then **Run anyway** — this appears because the app was downloaded from the internet, not because it is harmful.

---
