package com.ninereader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission;
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/** Generates test PDFs on the device (text, outline, links, scanned page, large images, encryption). */
public final class TestPdfs {

    public static final String WORD = "Pythagore";
    public static final String OCR_WORD = "NINEREADER";

    private TestPdfs() {
    }

    @NonNull
    private static File file(Context context, String name) {
        File dir = new File(context.getCacheDir(), "testpdfs");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, name);
    }

    /**
     * Text document: every page has a title "Chapitre k", body text containing {@link #WORD} once,
     * page 0 has a link to the last page and the outline has one entry per page.
     */
    @NonNull
    public static Uri text(@NonNull Context context, int pages, @NonNull String name) throws IOException {
        PDFBoxResourceLoader.init(context);
        File f = file(context, name);
        try (PDDocument doc = new PDDocument()) {
            PDDocumentOutline outline = new PDDocumentOutline();
            doc.getDocumentCatalog().setDocumentOutline(outline);
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(PDRectangle.A4);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 22);
                    cs.newLineAtOffset(72, 760);
                    cs.showText("Chapitre " + (i + 1));
                    cs.setFont(PDType1Font.HELVETICA, 12);
                    cs.setLeading(16);
                    for (int l = 0; l < 25; l++) {
                        cs.newLine();
                        cs.showText(l == 10 ? "Le theoreme de " + WORD + " est connu depuis longtemps." : "Ligne de texte numero " + l + " pour le test.");
                    }
                    cs.endText();
                }
                PDOutlineItem item = new PDOutlineItem();
                item.setTitle("Chapitre " + (i + 1));
                PDPageFitDestination dest = new PDPageFitDestination();
                dest.setPage(page);
                item.setDestination(dest);
                outline.addLast(item);
            }
            PDAnnotationLink link = new PDAnnotationLink();
            link.setRectangle(new PDRectangle(72, 72, 200, 30));
            PDPageFitDestination last = new PDPageFitDestination();
            last.setPage(doc.getPage(pages - 1));
            PDActionGoTo goTo = new PDActionGoTo();
            goTo.setDestination(last);
            link.setAction(goTo);
            doc.getPage(0).getAnnotations().add(link);
            doc.save(f);
        }
        return Uri.fromFile(f);
    }

    /** Same as {@link #text} but encrypted (AES-256) with a user password. */
    @NonNull
    public static Uri encrypted(@NonNull Context context, @NonNull String password) throws IOException {
        Uri clear = text(context, 2, "clear_for_encryption.pdf");
        File f = file(context, "encrypted.pdf");
        try (PDDocument doc = PDDocument.load(new File(clear.getPath()))) {
            StandardProtectionPolicy p = new StandardProtectionPolicy("owner-" + password, password, new AccessPermission());
            p.setEncryptionKeyLength(256);
            doc.protect(p);
            doc.save(f);
        }
        return Uri.fromFile(f);
    }

    /** A "scanned" document: one page that is only an image of large black text (no text layer). */
    @NonNull
    public static Uri scanned(@NonNull Context context) throws IOException {
        PDFBoxResourceLoader.init(context);
        File f = file(context, "scanned.pdf");
        Bitmap bmp = Bitmap.createBitmap(1240, 1754, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setTextSize(110);
        c.drawText(OCR_WORD, 120, 400, p);
        p.setTextSize(70);
        c.drawText("Lecture rapide et confortable", 120, 600, p);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDImageXObject img = LosslessFactory.createFromImage(doc, bmp);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(img, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            }
            doc.save(f);
        } finally {
            bmp.recycle();
        }
        return Uri.fromFile(f);
    }

    /** A document with a large, noisy (photo-like) lossless image: very compressible to JPEG. */
    @NonNull
    public static Uri heavyImage(@NonNull Context context) throws IOException {
        PDFBoxResourceLoader.init(context);
        File f = file(context, "heavy.pdf");
        Bitmap bmp = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888);
        Random r = new Random(42);
        int[] row = new int[bmp.getWidth()];
        for (int y = 0; y < bmp.getHeight(); y++) {
            for (int x = 0; x < row.length; x++) {
                int base = (x / 8 + y / 8) % 255;
                row[x] = Color.rgb(Math.min(255, base + r.nextInt(30)), Math.min(255, base / 2 + r.nextInt(30)), 255 - base);
            }
            bmp.setPixels(row, 0, row.length, 0, y, row.length, 1);
        }
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDImageXObject img = LosslessFactory.createFromImage(doc, bmp);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(img, 20, 200, 555, 416);
            }
            doc.save(f);
        } finally {
            bmp.recycle();
        }
        return Uri.fromFile(f);
    }

    /** A PNG image file (for images → PDF). */
    @NonNull
    public static Uri png(@NonNull Context context, int w, int h) throws IOException {
        File f = file(context, "image_" + w + "x" + h + ".png");
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(bmp).drawColor(Color.rgb(40, 90, 180));
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            bmp.recycle();
        }
        return Uri.fromFile(f);
    }

    /** A truncated, unreadable PDF. */
    @NonNull
    public static Uri corrupted(@NonNull Context context) throws IOException {
        File f = file(context, "corrupted.pdf");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
            out.write("%PDF-1.4\n1 0 obj << /Type /Catalog".getBytes());
        }
        return Uri.fromFile(f);
    }
}
