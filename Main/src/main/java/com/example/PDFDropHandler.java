package com.example;

import javax.swing.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;


public class PDFDropHandler extends TransferHandler
{
    private final Consumer<File> onFileDrop;

    public PDFDropHandler(Consumer<File> onFileDrop)
    {
        this.onFileDrop = onFileDrop;
    }

    @Override
    public boolean canImport(TransferSupport support)
    {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override
    public boolean importData(TransferSupport support)
    {
        if (!canImport(support))
        {
            return false;
        }

        try
        {
           @SuppressWarnings("unchecked")
           List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
           files.stream().filter(f -> f.getName().toLowerCase().endsWith(".pdf")).findFirst().ifPresent(onFileDrop);
           return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

}