package com.ninereader.pdfops;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.ninereader.reader.PdfEngine;
import com.ninereader.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Conversions: pages to JPEG/PNG images (CNV-01) and plain text extraction (CNV-02). */
public final class ExportOps {

    private ExportOps() {
    }

    /**
     * Renders pages to images in a folder chosen by the user (SAF tree).
     *
     * @return number of images written
     */
    @WorkerThread
    public static int exportImages(@NonNull Context context, @NonNull Uri src, @Nullable String password,
                                   @NonNull List<Integer> pages, int dpi, boolean png, @NonNull Uri folderTree,
                                   @NonNull String baseName, @NonNull PdfOps.Progress progress) throws IOException {
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(folderTree, DocumentsContract.getTreeDocumentId(folderTree));
        String mime = png ? "image/png" : "image/jpeg";
        int written = 0;
        int digits = String.valueOf(pages.size()).length();
        try (PdfEngine engine = PdfEngine.open(context, src, password)) {
            for (int p : pages) {
                PdfOps.check(progress);
                float w = engine.pageWidth(p);
                float h = engine.pageHeight(p);
                int bw = Math.max(1, Math.round(w * dpi / 72f));
                int bh = Math.max(1, Math.round(h * dpi / 72f));
                // Keep bitmaps below ~100 MB.
                double limit = Math.sqrt(25_000_000.0 / ((double) bw * bh));
                if (limit < 1) {
                    bw = (int) Math.max(1, bw * limit);
                    bh = (int) Math.max(1, bh * limit);
                }
                Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                try {
                    engine.render(p, bmp, 0, 0, bw, bh, true);
                    String name = String.format(Locale.ROOT, "%s_%0" + digits + "d.%s", baseName, p + 1, png ? "png" : "jpg");
                    Uri out = DocumentsContract.createDocument(context.getContentResolver(), parent, mime, name);
                    if (out == null) throw new IOException("Cannot create " + name);
                    try (OutputStream os = context.getContentResolver().openOutputStream(out, "w")) {
                        if (os == null) throw new IOException("Cannot write " + name);
                        bmp.compress(png ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 92, os);
                    }
                    written++;
                } finally {
                    bmp.recycle();
                }
                progress.report(written, pages.size());
            }
        }
        return written;
    }

    /** Extracts the text of pages into a temporary UTF-8 file (one section per page). */
    @WorkerThread
    @NonNull
    public static File exportText(@NonNull Context context, @NonNull Uri src, @Nullable String password,
                                  @NonNull List<Integer> pages, @NonNull String pageLabel,
                                  @NonNull PdfOps.Progress progress) throws IOException {
        File out = FileUtils.tempFile(context, "text", ".txt");
        try (PdfEngine engine = PdfEngine.open(context, src, password);
             Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
            int i = 0;
            for (int p : pages) {
                PdfOps.check(progress);
                String text = engine.pageText(p).replace("\r\n", "\n").replace('\r', '\n');
                if (pages.size() > 1) w.write("\n----- " + pageLabel + " " + (p + 1) + " -----\n\n");
                w.write(text.trim());
                w.write("\n");
                progress.report(++i, pages.size());
            }
        } catch (IOException e) {
            FileUtils.deleteQuietly(out);
            throw e;
        }
        return out;
    }
}
