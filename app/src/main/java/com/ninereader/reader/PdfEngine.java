package com.ninereader.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.legere.pdfiumandroid.PdfDocument;
import io.legere.pdfiumandroid.PdfPage;
import io.legere.pdfiumandroid.PdfPasswordException;
import io.legere.pdfiumandroid.PdfTextPage;
import io.legere.pdfiumandroid.PdfiumCore;

/**
 * Thin, thread-safe wrapper around PdfiumAndroid: page geometry, rendering, text, links and outline.
 * All calls are blocking and must be made off the main thread (Pdfium serialises them internally).
 */
public final class PdfEngine implements Closeable {

    private static volatile PdfiumCore core;

    private final PdfDocument doc;
    private final int pageCount;
    /** Page sizes in PDF points (index*2 = width, index*2+1 = height); 0 = not measured yet. */
    private final float[] sizes;
    private final SparseArray<PdfPage> openPages = new SparseArray<>();
    private final List<Integer> pageLru = new ArrayList<>();
    private final String[] textCache;
    private final PageTransform[] transforms;
    private volatile boolean closed;

    private static final int MAX_OPEN_PAGES = 12;

    private PdfEngine(@NonNull PdfDocument doc) {
        this.doc = doc;
        this.pageCount = doc.getPageCount();
        this.sizes = new float[pageCount * 2];
        this.textCache = new String[pageCount];
        this.transforms = new PageTransform[pageCount];
    }

    @NonNull
    private static PdfiumCore core(@NonNull Context context) {
        if (core == null) {
            synchronized (PdfEngine.class) {
                if (core == null) core = new PdfiumCore(context.getApplicationContext(), new io.legere.pdfiumandroid.util.Config(
                        new io.legere.pdfiumandroid.DefaultLogger(),
                        io.legere.pdfiumandroid.util.AlreadyClosedBehavior.IGNORE));
            }
        }
        return core;
    }

    /**
     * Opens a document.
     *
     * @throws PdfPasswordException when the document is encrypted and the password is missing/wrong
     * @throws IOException          when the file cannot be read or is not a valid PDF
     */
    @WorkerThread
    @NonNull
    public static PdfEngine open(@NonNull Context context, @NonNull Uri uri, @Nullable String password) throws IOException {
        ParcelFileDescriptor pfd;
        try {
            pfd = context.getContentResolver().openFileDescriptor(uri, "r");
        } catch (FileNotFoundException | SecurityException e) {
            throw new IOException("Cannot open " + uri, e);
        }
        if (pfd == null) throw new IOException("Cannot open " + uri);
        try {
            PdfDocument document = core(context).newDocument(pfd, password);
            PdfEngine engine = new PdfEngine(document);
            if (engine.pageCount <= 0) {
                engine.close();
                throw new IOException("Document has no page");
            }
            return engine;
        } catch (PdfPasswordException e) {
            closeQuietly(pfd);
            throw e;
        } catch (IOException e) {
            closeQuietly(pfd);
            throw e;
        } catch (RuntimeException e) {
            closeQuietly(pfd);
            throw new IOException("Invalid PDF", e);
        }
    }

    private static void closeQuietly(ParcelFileDescriptor pfd) {
        try {
            pfd.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    public int pageCount() {
        return pageCount;
    }

    public boolean isClosed() {
        return closed;
    }

    @NonNull
    private synchronized PdfPage page(int index) {
        PdfPage p = openPages.get(index);
        if (p != null) {
            pageLru.remove(Integer.valueOf(index));
            pageLru.add(index);
            return p;
        }
        p = doc.openPage(index);
        openPages.put(index, p);
        pageLru.add(index);
        while (pageLru.size() > MAX_OPEN_PAGES) {
            int evict = pageLru.remove(0);
            PdfPage old = openPages.get(evict);
            openPages.remove(evict);
            if (old != null) old.close();
        }
        return p;
    }

    /** Page width in points (measures the page on first call). */
    @WorkerThread
    public float pageWidth(int index) {
        ensureSize(index);
        return sizes[index * 2];
    }

    @WorkerThread
    public float pageHeight(int index) {
        ensureSize(index);
        return sizes[index * 2 + 1];
    }

    /** Whether the size of a page is already known (cheap, any thread). */
    public boolean isMeasured(int index) {
        return sizes[index * 2] > 0;
    }

    /** Raw size array (points), possibly partially filled. */
    public float[] sizesSnapshot() {
        return sizes.clone();
    }

    @WorkerThread
    public void ensureSize(int index) {
        if (sizes[index * 2] > 0 || closed) return;
        synchronized (this) {
            PdfPage p = page(index);
            float w = p.getPageWidthPoint();
            float h = p.getPageHeightPoint();
            if (w <= 0 || h <= 0) {
                w = 612;
                h = 792;
            }
            PageTransform t;
            try {
                int ref = 1_000_000;
                Point p0 = p.mapPageCoordsToDevice(0, 0, ref, ref, 0, 0, 0);
                Point p1 = p.mapPageCoordsToDevice(0, 0, ref, ref, 0, 100, 0);
                Point p2 = p.mapPageCoordsToDevice(0, 0, ref, ref, 0, 0, 100);
                t = PageTransform.fromSamples(100, p0.x / (double) ref, p0.y / (double) ref,
                        p1.x / (double) ref, p1.y / (double) ref, p2.x / (double) ref, p2.y / (double) ref);
            } catch (RuntimeException e) {
                t = PageTransform.simple(w, h);
            }
            transforms[index] = t;
            sizes[index * 2] = w;
            sizes[index * 2 + 1] = h;
        }
    }

    /** Page ↔ display transform; measures the page if needed. */
    @WorkerThread
    @NonNull
    public PageTransform transform(int index) {
        ensureSize(index);
        PageTransform t = transforms[index];
        return t != null ? t : PageTransform.simple(sizes[index * 2], sizes[index * 2 + 1]);
    }

    /** Transform if already measured (any thread), else null. */
    @Nullable
    public PageTransform transformIfMeasured(int index) {
        return transforms[index];
    }

    /**
     * Renders a page into a bitmap. The full page is drawn at {@code fullW x fullH} pixels, translated by
     * ({@code -offX}, {@code -offY}); only the part covered by the bitmap is rasterised (tiles, crop).
     */
    @WorkerThread
    public void render(int index, @NonNull Bitmap bitmap, int offX, int offY, int fullW, int fullH, boolean annotations) {
        if (closed) return;
        synchronized (this) {
            if (closed) return;
            PdfPage p = page(index);
            p.renderPageBitmap(bitmap, -offX, -offY, fullW, fullH, annotations, false, 0xFFFFFFFF, 0xFFFFFFFF);
        }
    }

    /** Full plain text of a page (character indexes match Pdfium char indexes). */
    @WorkerThread
    @NonNull
    public String pageText(int index) {
        String cached = textCache[index];
        if (cached != null) return cached;
        if (closed) return "";
        String text;
        synchronized (this) {
            PdfPage p = page(index);
            PdfTextPage tp = p.openTextPage();
            try {
                int n = tp.textPageCountChars();
                text = n > 0 ? tp.textPageGetText(0, n) : "";
            } finally {
                tp.close();
            }
        }
        if (text == null) text = "";
        textCache[index] = text;
        return text;
    }

    /** Whether the text of a page was already extracted (cheap). */
    public boolean hasCachedText(int index) {
        return textCache[index] != null;
    }

    /** Rectangles (page coordinates) covering a run of characters. */
    @WorkerThread
    @NonNull
    public List<RectF> textRects(int index, int start, int count) {
        if (closed || count <= 0) return Collections.emptyList();
        List<RectF> out = new ArrayList<>();
        synchronized (this) {
            PdfPage p = page(index);
            PdfTextPage tp = p.openTextPage();
            try {
                int n = tp.textPageCountRects(start, count);
                for (int i = 0; i < n; i++) {
                    RectF r = tp.textPageGetRect(i);
                    if (r != null) out.add(r);
                }
            } finally {
                tp.close();
            }
        }
        return out;
    }

    /** Index of the character at a page coordinate, or -1. */
    @WorkerThread
    public int charIndexAt(int index, float pageX, float pageY, float tolerance) {
        if (closed) return -1;
        synchronized (this) {
            PdfPage p = page(index);
            PdfTextPage tp = p.openTextPage();
            try {
                return tp.textPageGetCharIndexAtPos(pageX, pageY, tolerance, tolerance);
            } finally {
                tp.close();
            }
        }
    }

    /** Links of a page (bounds in page coordinates). */
    @WorkerThread
    @NonNull
    public List<PdfDocument.Link> links(int index) {
        if (closed) return Collections.emptyList();
        synchronized (this) {
            return page(index).getPageLinks();
        }
    }

    /** Maps a page-space rectangle to view pixels for a page drawn at fullW x fullH, offset by off. */
    @WorkerThread
    @NonNull
    public RectF pageToView(int index, @NonNull RectF pageRect, int offX, int offY, int fullW, int fullH) {
        synchronized (this) {
            PdfPage p = page(index);
            Point a = p.mapPageCoordsToDevice(-offX, -offY, fullW, fullH, 0, pageRect.left, pageRect.top);
            Point b = p.mapPageCoordsToDevice(-offX, -offY, fullW, fullH, 0, pageRect.right, pageRect.bottom);
            return new RectF(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.max(a.x, b.x), Math.max(a.y, b.y));
        }
    }

    /** Maps a view pixel to page coordinates. */
    @WorkerThread
    @NonNull
    public PointF viewToPage(int index, float x, float y, int offX, int offY, int fullW, int fullH) {
        synchronized (this) {
            return page(index).mapDeviceCoordsToPage(-offX, -offY, fullW, fullH, 0, Math.round(x), Math.round(y));
        }
    }

    /** Flattened table of contents (LEC-06). */
    @WorkerThread
    @NonNull
    public List<OutlineItem> outline() {
        if (closed) return Collections.emptyList();
        List<OutlineItem> out = new ArrayList<>();
        synchronized (this) {
            flatten(doc.getTableOfContents(), 0, out);
        }
        return out;
    }

    private void flatten(List<PdfDocument.Bookmark> items, int level, List<OutlineItem> out) {
        for (PdfDocument.Bookmark b : items) {
            String title = b.getTitle() == null ? "" : b.getTitle().trim();
            int page = (int) Math.max(0, Math.min(pageCount - 1, b.getPageIdx()));
            out.add(new OutlineItem(title, page, level, !b.getChildren().isEmpty()));
            flatten(b.getChildren(), level + 1, out);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        synchronized (this) {
            closed = true;
            for (int i = 0; i < openPages.size(); i++) {
                PdfPage p = openPages.valueAt(i);
                if (p != null) p.close();
            }
            openPages.clear();
            pageLru.clear();
            doc.close();
        }
    }
}
