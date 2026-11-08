package com.ninereader.pdfops;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.ninereader.reader.MarginDetector;
import com.ninereader.reader.PageTransform;
import com.ninereader.reader.PdfEngine;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDPageTree;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission;
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import com.tom_roush.pdfbox.pdmodel.graphics.PDXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject;
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory;
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF operations built on PdfBox (§6.1 pdfops): organize, merge, extract, crop, compress, protect.
 * Every method writes a new temporary file; the caller publishes it to the destination chosen by the
 * user (no silent overwrite, §5.4).
 */
public final class PdfOps {

    private PdfOps() {
    }

    /** Progress reporting and cancellation. */
    public interface Progress {
        void report(int done, int total);

        boolean isCancelled();

        Progress NONE = new Progress() {
            @Override
            public void report(int done, int total) {
                // no-op
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };
    }

    /** Thrown when the user cancelled the operation. */
    public static final class CancelledException extends IOException {
        public CancelledException() {
            super("cancelled");
        }
    }

    static void check(Progress p) throws CancelledException {
        if (p.isCancelled()) throw new CancelledException();
    }

    // ------------------------------------------------------------------ organize (ORG-01, ORG-03)

    /** Rebuilds the page tree of a document according to a plan. */
    static void applyPlan(@NonNull PDDocument doc, @NonNull PagePlan plan) {
        PDPageTree tree = doc.getPages();
        int count = tree.getCount();
        List<PDPage> original = new ArrayList<>(count);
        for (PDPage p : tree) original.add(p);
        boolean[] used = new boolean[count];
        List<PDPage> result = new ArrayList<>();
        PDRectangle lastBox = PDRectangle.A4;
        for (PagePlan.Entry e : plan.entries()) {
            PDPage page;
            if (e.isBlank()) {
                page = new PDPage(lastBox);
            } else {
                if (e.source < 0 || e.source >= count) continue;
                PDPage src = original.get(e.source);
                if (used[e.source]) {
                    // Duplicate: shallow copy of the page dictionary (shares content and resources).
                    COSDictionary copy = new COSDictionary(src.getCOSObject());
                    copy.removeItem(COSName.ANNOTS);
                    copy.removeItem(COSName.PARENT);
                    page = new PDPage(copy);
                } else {
                    page = src;
                    used[e.source] = true;
                }
                lastBox = src.getMediaBox();
                if (e.rotation != 0) page.setRotation((page.getRotation() + e.rotation) % 360);
            }
            result.add(page);
        }
        for (int i = count - 1; i >= 0; i--) tree.remove(i);
        for (PDPage p : result) tree.add(p);
        if (!plan.keepsOrder() || result.size() < count) {
            // Outline entries may point to removed or moved pages.
            doc.getDocumentCatalog().setDocumentOutline(null);
        }
    }

    @WorkerThread
    @NonNull
    public static File organize(@NonNull Context context, @NonNull Uri src, @Nullable String password,
                                @NonNull PagePlan plan) throws IOException {
        if (plan.size() == 0) throw new IOException("Empty plan");
        try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, src, password)) {
            applyPlan(loaded.doc, plan);
            return PdfBoxHelper.saveKeepingProtection(context, loaded.doc, password);
        }
    }

    // ------------------------------------------------------------------ merge (ORG-02)

    /** Merges PDFs and images (in the given order) into one PDF. */
    @WorkerThread
    @NonNull
    public static File merge(@NonNull Context context, @NonNull List<Uri> inputs, @NonNull Progress progress) throws IOException {
        PdfBoxHelper.init(context);
        List<PdfBoxHelper.Loaded> opened = new ArrayList<>();
        try (PDDocument out = new PDDocument(PdfBoxHelper.memory())) {
            PDFMergerUtility merger = new PDFMergerUtility();
            int i = 0;
            for (Uri uri : inputs) {
                check(progress);
                String type = context.getContentResolver().getType(uri);
                if (type != null && type.startsWith("image/")) {
                    addImagePage(context, out, uri);
                } else {
                    PdfBoxHelper.Loaded l = PdfBoxHelper.load(context, uri, null);
                    opened.add(l);
                    if (l.doc.isEncrypted()) l.doc.setAllSecurityToBeRemoved(true);
                    merger.appendDocument(out, l.doc);
                }
                progress.report(++i, inputs.size());
            }
            return PdfBoxHelper.saveToTemp(context, out);
        } finally {
            for (PdfBoxHelper.Loaded l : opened) l.close();
        }
    }

    /** Adds an image as a new A4 page (orientation follows the image). */
    static void addImagePage(@NonNull Context context, @NonNull PDDocument doc, @NonNull Uri uri) throws IOException {
        Bitmap bmp = decodeImage(context, uri, 2480);
        if (bmp == null) throw new IOException("Cannot decode image " + uri);
        try {
            PDRectangle a4 = PDRectangle.A4;
            boolean landscape = bmp.getWidth() > bmp.getHeight();
            PDRectangle box = landscape ? new PDRectangle(a4.getHeight(), a4.getWidth()) : a4;
            PDPage page = new PDPage(box);
            doc.addPage(page);
            PDImageXObject img = JPEGFactory.createFromImage(doc, bmp, 0.9f);
            float margin = 0;
            float scale = Math.min((box.getWidth() - 2 * margin) / bmp.getWidth(), (box.getHeight() - 2 * margin) / bmp.getHeight());
            float w = bmp.getWidth() * scale;
            float h = bmp.getHeight() * scale;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(img, (box.getWidth() - w) / 2, (box.getHeight() - h) / 2, w, h);
            }
        } finally {
            bmp.recycle();
        }
    }

    /** Decodes an image with sub-sampling and EXIF orientation applied. */
    @Nullable
    public static Bitmap decodeImage(@NonNull Context context, @NonNull Uri uri, int maxDim) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > maxDim * 2) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, o2);
        }
        if (bmp == null) return null;
        int rotation = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in != null) {
                ExifInterface exif = new ExifInterface(in);
                int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientation == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                if (orientation == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                if (orientation == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
            }
        } catch (IOException | RuntimeException ignored) {
            // No EXIF data.
        }
        float scale = Math.min(1f, maxDim / (float) Math.max(bmp.getWidth(), bmp.getHeight()));
        if (rotation != 0 || scale < 1f) {
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postRotate(rotation);
            Bitmap t = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            if (t != bmp) bmp.recycle();
            bmp = t;
        }
        return bmp;
    }

    // ------------------------------------------------------------------ crop (ORG-04)

    /**
     * Sets the crop box of the selected pages.
     *
     * @param margins {left, top, right, bottom} fractions to remove (display orientation), or null to
     *                detect white margins automatically on every page
     */
    @WorkerThread
    @NonNull
    public static File crop(@NonNull Context context, @NonNull Uri src, @Nullable String password,
                            @Nullable float[] margins, @NonNull List<Integer> pages, @NonNull Progress progress) throws IOException {
        List<float[]> normalized = new ArrayList<>();
        List<PageTransform> transforms = new ArrayList<>();
        try (PdfEngine engine = PdfEngine.open(context, src, password)) {
            int i = 0;
            for (int p : pages) {
                check(progress);
                transforms.add(engine.transform(p));
                if (margins != null) {
                    normalized.add(new float[]{margins[0], margins[1], 1f - margins[2], 1f - margins[3]});
                } else {
                    float w = engine.pageWidth(p);
                    float h = engine.pageHeight(p);
                    int bw = 300;
                    int bh = Math.max(1, Math.round(bw * h / w));
                    Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                    engine.render(p, bmp, 0, 0, bw, bh, false);
                    int[] px = new int[bw * bh];
                    bmp.getPixels(px, 0, bw, 0, 0, bw, bh);
                    bmp.recycle();
                    normalized.add(MarginDetector.detect(px, bw, bh));
                }
                progress.report(++i, pages.size() * 2);
            }
        }
        try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, src, password)) {
            for (int k = 0; k < pages.size(); k++) {
                PDPage page = loaded.doc.getPage(pages.get(k));
                float[] n = normalized.get(k);
                PageTransform t = transforms.get(k);
                float[] a = new float[2];
                float[] b = new float[2];
                t.toPage(n[0], n[1], a);
                t.toPage(n[2], n[3], b);
                PDRectangle r = new PDRectangle();
                r.setLowerLeftX(Math.min(a[0], b[0]));
                r.setLowerLeftY(Math.min(a[1], b[1]));
                r.setUpperRightX(Math.max(a[0], b[0]));
                r.setUpperRightY(Math.max(a[1], b[1]));
                if (r.getWidth() > 10 && r.getHeight() > 10) page.setCropBox(r);
                progress.report(pages.size() + k + 1, pages.size() * 2);
            }
            return PdfBoxHelper.saveKeepingProtection(context, loaded.doc, password);
        }
    }

    // ------------------------------------------------------------------ compress (CMP-01)

    /** Size analysis before compression. */
    public static final class CompressionInfo {
        public long fileSize;
        public long imageBytes;
        public long estimatedImageBytes;
        public int images;

        public long estimatedSize() {
            return CompressionLevel.estimateFile(fileSize, imageBytes, estimatedImageBytes);
        }
    }

    @WorkerThread
    @NonNull
    public static CompressionInfo analyze(@NonNull Context context, @NonNull Uri src, @Nullable String password,
                                          @NonNull CompressionLevel level) throws IOException {
        CompressionInfo info = new CompressionInfo();
        info.fileSize = Math.max(0, com.ninereader.util.FileUtils.size(context, src));
        try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, src, password)) {
            Map<COSBase, Boolean> seen = new IdentityHashMap<>();
            for (PDPage page : loaded.doc.getPages()) {
                analyzeResources(page.getResources(), level, info, seen, 0);
            }
        }
        return info;
    }

    private static void analyzeResources(@Nullable PDResources res, CompressionLevel level, CompressionInfo info,
                                         Map<COSBase, Boolean> seen, int depth) throws IOException {
        if (res == null || depth > 8) return;
        for (COSName name : res.getXObjectNames()) {
            PDXObject xo;
            try {
                xo = res.getXObject(name);
            } catch (IOException e) {
                continue;
            }
            if (xo == null || seen.put(xo.getCOSObject(), Boolean.TRUE) != null) continue;
            if (xo instanceof PDImageXObject) {
                PDImageXObject img = (PDImageXObject) xo;
                if (!isRecompressible(img)) continue;
                long len = img.getCOSObject().getLength();
                info.images++;
                info.imageBytes += len;
                info.estimatedImageBytes += level.estimateImage(img.getWidth(), img.getHeight(), len);
            } else if (xo instanceof PDFormXObject) {
                analyzeResources(((PDFormXObject) xo).getResources(), level, info, seen, depth + 1);
            }
        }
    }

    static boolean isRecompressible(@NonNull PDImageXObject img) {
        if (img.isStencil()) return false;
        if (img.getCOSObject().containsKey(COSName.SMASK) || img.getCOSObject().containsKey(COSName.MASK)) return false;
        if (img.getBitsPerComponent() == 1) return false;
        return img.getCOSObject().getLength() > 16 * 1024;
    }

    @WorkerThread
    @NonNull
    public static File compress(@NonNull Context context, @NonNull Uri src, @Nullable String password,
                                @NonNull CompressionLevel level, @NonNull Progress progress) throws IOException {
        try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, src, password)) {
            PDDocument doc = loaded.doc;
            Map<COSBase, PDXObject> replaced = new IdentityHashMap<>();
            int n = doc.getNumberOfPages();
            int i = 0;
            for (PDPage page : doc.getPages()) {
                check(progress);
                compressResources(doc, page.getResources(), level, replaced, 0);
                progress.report(++i, n);
            }
            // Thumbnails and private application data are not needed.
            for (PDPage page : doc.getPages()) {
                page.getCOSObject().removeItem(COSName.getPDFName("Thumb"));
                page.getCOSObject().removeItem(COSName.getPDFName("PieceInfo"));
            }
            return PdfBoxHelper.saveKeepingProtection(context, doc, password);
        }
    }

    private static void compressResources(PDDocument doc, @Nullable PDResources res, CompressionLevel level,
                                          Map<COSBase, PDXObject> replaced, int depth) throws IOException {
        if (res == null || depth > 8) return;
        for (COSName name : res.getXObjectNames()) {
            PDXObject xo;
            try {
                xo = res.getXObject(name);
            } catch (IOException e) {
                continue;
            }
            if (xo == null) continue;
            PDXObject done = replaced.get(xo.getCOSObject());
            if (done != null) {
                if (done != xo) res.put(name, done);
                continue;
            }
            if (xo instanceof PDImageXObject) {
                PDImageXObject img = (PDImageXObject) xo;
                PDXObject result = img;
                if (isRecompressible(img)) {
                    try {
                        PDImageXObject smaller = recompress(doc, img, level);
                        if (smaller != null) {
                            res.put(name, smaller);
                            result = smaller;
                        }
                    } catch (IOException | RuntimeException | OutOfMemoryError e) {
                        // Unsupported colour space or too large: keep the original image.
                    }
                }
                replaced.put(img.getCOSObject(), result);
            } else if (xo instanceof PDFormXObject) {
                replaced.put(xo.getCOSObject(), xo);
                compressResources(doc, ((PDFormXObject) xo).getResources(), level, replaced, depth + 1);
            }
        }
    }

    @Nullable
    private static PDImageXObject recompress(PDDocument doc, PDImageXObject img, CompressionLevel level) throws IOException {
        long original = img.getCOSObject().getLength();
        Bitmap bmp = img.getImage();
        if (bmp == null) return null;
        try {
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            float scale = Math.min(1f, level.maxDimension / (float) Math.max(w, h));
            Bitmap scaled = bmp;
            if (scale < 1f) {
                scaled = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
            }
            PDImageXObject jpeg = JPEGFactory.createFromImage(doc, scaled, level.quality);
            if (scaled != bmp) scaled.recycle();
            if (jpeg.getCOSObject().getLength() >= original) return null;
            return jpeg;
        } finally {
            bmp.recycle();
        }
    }

    // ------------------------------------------------------------------ protect (SEC-01, SEC-02)

    /** Encrypts with AES-256 and sets permissions. */
    @WorkerThread
    @NonNull
    public static File protect(@NonNull Context context, @NonNull Uri src, @Nullable String currentPassword,
                               @NonNull String userPassword, @Nullable String ownerPassword,
                               boolean allowPrint, boolean allowCopy) throws IOException {
        try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, src, currentPassword)) {
            PDDocument doc = loaded.doc;
            if (doc.isEncrypted()) doc.setAllSecurityToBeRemoved(true);
            AccessPermission ap = new AccessPermission();
            ap.setCanPrint(allowPrint);
            ap.setCanPrintFaithful(allowPrint);
            ap.setCanExtractContent(allowCopy);
            ap.setCanExtractForAccessibility(true);
            String owner = ownerPassword == null || ownerPassword.isEmpty() ? randomPassword() : ownerPassword;
            StandardProtectionPolicy policy = new StandardProtectionPolicy(owner, userPassword, ap);
            policy.setEncryptionKeyLength(256);
            doc.protect(policy);
            return PdfBoxHelper.saveToTemp(context, doc);
        }
    }

    /** Removes the password (the user must know it). */
    @WorkerThread
    @NonNull
    public static File unprotect(@NonNull Context context, @NonNull Uri src, @NonNull String password) throws IOException {
        try (PdfBoxHelper.Loaded loaded = PdfBoxHelper.load(context, src, password)) {
            loaded.doc.setAllSecurityToBeRemoved(true);
            return PdfBoxHelper.saveToTemp(context, loaded.doc);
        }
    }

    @NonNull
    static String randomPassword() {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        return com.ninereader.util.FileUtils.toHex(b);
    }
}
