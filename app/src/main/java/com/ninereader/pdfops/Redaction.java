package com.ninereader.pdfops;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.ninereader.reader.PageTransform;
import com.ninereader.reader.PdfEngine;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * True redaction (MOD-03): pages containing redaction boxes are flattened into an image in which the
 * boxes are painted black. The original text, images and vector content of the page are removed from
 * the file, so the hidden content cannot be recovered or copied.
 */
public final class Redaction {

    static final int DPI = 200;

    private Redaction() {
    }

    /**
     * @param boxes page index → boxes {left, bottom, right, top} in page coordinates
     */
    @WorkerThread
    public static void apply(@NonNull Context context, @NonNull PDDocument doc, @NonNull Uri src, @Nullable String password,
                             @NonNull Map<Integer, List<float[]>> boxes) throws IOException {
        if (boxes.isEmpty()) return;
        try (PdfEngine engine = PdfEngine.open(context, src, password)) {
            Paint black = new Paint();
            black.setColor(0xFF000000);
            for (Map.Entry<Integer, List<float[]>> e : boxes.entrySet()) {
                int index = e.getKey();
                if (index < 0 || index >= doc.getNumberOfPages()) continue;
                float w = engine.pageWidth(index);
                float h = engine.pageHeight(index);
                int bw = Math.max(1, Math.round(w * DPI / 72f));
                int bh = Math.max(1, Math.round(h * DPI / 72f));
                Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                try {
                    engine.render(index, bmp, 0, 0, bw, bh, true);
                    PageTransform t = engine.transform(index);
                    Canvas c = new Canvas(bmp);
                    for (float[] box : e.getValue()) {
                        float[] n = t.rectToNormalized(box[0], box[3], box[2], box[1]);
                        c.drawRect(n[0] * bw, n[1] * bh, n[2] * bw, n[3] * bh, black);
                    }
                    flatten(doc, doc.getPage(index), bmp, w, h);
                } finally {
                    bmp.recycle();
                }
            }
        }
    }

    /** Replaces all content of a page by an image of the given display size (points). */
    static void flatten(@NonNull PDDocument doc, @NonNull PDPage page, @NonNull Bitmap image, float width, float height) throws IOException {
        PDImageXObject img = JPEGFactory.createFromImage(doc, image, 0.9f);
        PDRectangle box = new PDRectangle(width, height);
        page.setMediaBox(box);
        page.setCropBox(box);
        page.getCOSObject().removeItem(COSName.getPDFName("TrimBox"));
        page.getCOSObject().removeItem(COSName.getPDFName("BleedBox"));
        page.getCOSObject().removeItem(COSName.getPDFName("ArtBox"));
        page.setRotation(0);
        page.setResources(new PDResources());
        page.getCOSObject().removeItem(COSName.ANNOTS);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, false)) {
            cs.drawImage(img, 0, 0, width, height);
        }
    }
}
