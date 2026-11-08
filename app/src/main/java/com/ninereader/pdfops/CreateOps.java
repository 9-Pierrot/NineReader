package com.ninereader.pdfops;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.ninereader.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** PDF creation with the platform {@link PdfDocument}: from images (CRE-01) and blank documents (CRE-02). */
public final class CreateOps {

    /** Page formats in points. */
    public enum PageFormat {
        A4(595, 842), LETTER(612, 792), A5(420, 595), FIT(0, 0);

        public final int width;
        public final int height;

        PageFormat(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @NonNull
        public static PageFormat fromKey(String key) {
            for (PageFormat f : values()) if (f.name().equalsIgnoreCase(key)) return f;
            return A4;
        }
    }

    /** Blank page patterns. */
    public enum Pattern {
        BLANK, LINED, GRID;

        @NonNull
        public static Pattern fromKey(String key) {
            for (Pattern p : values()) if (p.name().equalsIgnoreCase(key)) return p;
            return BLANK;
        }
    }

    private CreateOps() {
    }

    /** One image per page, scaled to fit with a margin (FIT: page size follows the image). */
    @WorkerThread
    @NonNull
    public static File imagesToPdf(@NonNull Context context, @NonNull List<Uri> images, @NonNull PageFormat format,
                                   int marginPt, @NonNull PdfOps.Progress progress) throws IOException {
        PdfDocument pdf = new PdfDocument();
        File out = FileUtils.tempFile(context, "images", ".pdf");
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        try {
            int index = 0;
            for (Uri uri : images) {
                PdfOps.check(progress);
                Bitmap bmp = PdfOps.decodeImage(context, uri, 2480);
                if (bmp == null) throw new IOException("Cannot decode " + uri);
                try {
                    boolean landscape = bmp.getWidth() > bmp.getHeight();
                    int pw;
                    int ph;
                    if (format == PageFormat.FIT) {
                        // 150 dpi equivalent
                        pw = Math.max(72, Math.round(bmp.getWidth() * 72f / 150f));
                        ph = Math.max(72, Math.round(bmp.getHeight() * 72f / 150f));
                    } else {
                        pw = landscape ? format.height : format.width;
                        ph = landscape ? format.width : format.height;
                    }
                    PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pw, ph, ++index).create());
                    Canvas c = page.getCanvas();
                    float margin = format == PageFormat.FIT ? 0 : marginPt;
                    float scale = Math.min((pw - 2 * margin) / bmp.getWidth(), (ph - 2 * margin) / bmp.getHeight());
                    float w = bmp.getWidth() * scale;
                    float h = bmp.getHeight() * scale;
                    c.drawBitmap(bmp, null, new RectF((pw - w) / 2, (ph - h) / 2, (pw + w) / 2, (ph + h) / 2), paint);
                    pdf.finishPage(page);
                } finally {
                    bmp.recycle();
                }
                progress.report(index, images.size());
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                pdf.writeTo(fos);
            }
            return out;
        } catch (IOException | RuntimeException e) {
            FileUtils.deleteQuietly(out);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        } finally {
            pdf.close();
        }
    }

    /** Creates a blank, lined or grid document. */
    @WorkerThread
    @NonNull
    public static File blank(@NonNull Context context, @NonNull PageFormat format, boolean landscape,
                             @NonNull Pattern pattern, int pages) throws IOException {
        PageFormat f = format == PageFormat.FIT ? PageFormat.A4 : format;
        int pw = landscape ? f.height : f.width;
        int ph = landscape ? f.width : f.height;
        PdfDocument pdf = new PdfDocument();
        File out = FileUtils.tempFile(context, "blank", ".pdf");
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0xFFB0BEC5);
        line.setStrokeWidth(0.5f);
        Paint margin = new Paint(Paint.ANTI_ALIAS_FLAG);
        margin.setColor(0xFFEF9A9A);
        margin.setStrokeWidth(0.75f);
        try {
            for (int i = 1; i <= Math.max(1, pages); i++) {
                PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(pw, ph, i).create());
                Canvas c = page.getCanvas();
                drawPattern(c, pattern, pw, ph, line, margin);
                pdf.finishPage(page);
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                pdf.writeTo(fos);
            }
            return out;
        } catch (IOException | RuntimeException e) {
            FileUtils.deleteQuietly(out);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        } finally {
            pdf.close();
        }
    }

    static void drawPattern(Canvas c, Pattern pattern, int pw, int ph, Paint line, Paint margin) {
        switch (pattern) {
            case LINED: {
                float step = 24f; // ~8.5 mm
                for (float y = 72; y < ph - 36; y += step) c.drawLine(36, y, pw - 36, y, line);
                c.drawLine(72, 36, 72, ph - 36, margin);
                break;
            }
            case GRID: {
                float step = 14.17f; // 5 mm
                for (float x = 28; x <= pw - 28; x += step) c.drawLine(x, 28, x, ph - 28, line);
                for (float y = 28; y <= ph - 28; y += step) c.drawLine(28, y, pw - 28, y, line);
                break;
            }
            case BLANK:
            default:
                break;
        }
    }
}
