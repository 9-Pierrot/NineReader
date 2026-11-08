package com.ninereader.ui.common;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.ninereader.R;
import com.ninereader.reader.PdfEngine;
import com.ninereader.util.AppExecutors;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads first-page thumbnails of documents for the library lists (bounded cache, background thread). */
public final class ThumbnailLoader {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
            return value.getAllocationByteCount();
        }
    };
    private static final Set<String> FAILED = new HashSet<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "nr-thumbs");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private ThumbnailLoader() {
    }

    public static void load(@NonNull ImageView view, @NonNull String uri, int widthPx) {
        String key = uri + "@" + widthPx;
        view.setTag(R.id.tag_thumbnail_key, key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.ic_pdf);
        if (FAILED.contains(uri)) return;
        Context app = view.getContext().getApplicationContext();
        EXECUTOR.execute(() -> {
            Bitmap bmp = null;
            try (PdfEngine engine = PdfEngine.open(app, Uri.parse(uri), null)) {
                float w = engine.pageWidth(0);
                float h = engine.pageHeight(0);
                int bh = Math.max(1, Math.round(widthPx * h / w));
                bmp = Bitmap.createBitmap(widthPx, bh, Bitmap.Config.ARGB_8888);
                engine.render(0, bmp, 0, 0, widthPx, bh, true);
            } catch (Exception | OutOfMemoryError e) {
                synchronized (FAILED) {
                    FAILED.add(uri);
                }
            }
            Bitmap result = bmp;
            if (result == null) return;
            AppExecutors.get().mainThread().execute(() -> {
                CACHE.put(key, result);
                if (key.equals(view.getTag(R.id.tag_thumbnail_key))) view.setImageBitmap(result);
            });
        });
    }

    /** Forgets a document (after it was modified). */
    public static void invalidate(@NonNull String uri) {
        CACHE.evictAll();
        synchronized (FAILED) {
            FAILED.remove(uri);
        }
    }
}
