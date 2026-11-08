package com.ninereader.reader;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Renders pages off the UI thread (§5.1). Requests are prioritised (visible high-resolution first,
 * then visible low-resolution, then prefetch) and can be cancelled when a page leaves the screen.
 */
public final class RenderScheduler {

    public static final int PRIORITY_VISIBLE = 0;
    public static final int PRIORITY_TILE = 1;
    public static final int PRIORITY_PREFETCH = 2;
    public static final int PRIORITY_THUMBNAIL = 3;

    private final PdfEngine engine;
    private final BitmapCache cache;
    private final ThreadPoolExecutor executor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicLong sequence = new AtomicLong();

    /** Result callback, always invoked on the main thread. */
    public interface Callback {
        void onRendered(@NonNull Bitmap bitmap);
    }

    /** A pending render that can be cancelled. */
    public static final class Ticket {
        volatile boolean cancelled;

        public void cancel() {
            cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    public RenderScheduler(@NonNull PdfEngine engine, @NonNull BitmapCache cache) {
        this.engine = engine;
        this.cache = cache;
        PriorityBlockingQueue<Runnable> queue = new PriorityBlockingQueue<>(64,
                Comparator.comparingInt((Runnable r) -> ((Job) r).priority).thenComparingLong(r -> ((Job) r).seq));
        executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, queue, r -> {
            Thread t = new Thread(r, "nr-render");
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });
    }

    @NonNull
    public PdfEngine engine() {
        return engine;
    }

    @NonNull
    public BitmapCache cache() {
        return cache;
    }

    /**
     * Requests a render. When {@code cacheKey} is non-null the result is cached and served from cache
     * synchronously when available (the callback is then invoked immediately and null is returned).
     */
    @Nullable
    public Ticket request(int page, int bmpW, int bmpH, int offX, int offY, int fullW, int fullH,
                          int priority, @Nullable String cacheKey, @NonNull Callback callback) {
        if (cacheKey != null) {
            Bitmap cached = cache.get(cacheKey);
            if (cached != null) {
                callback.onRendered(cached);
                return null;
            }
        }
        Ticket ticket = new Ticket();
        if (bmpW <= 0 || bmpH <= 0) return ticket;
        executor.execute(new Job(priority, sequence.incrementAndGet()) {
            @Override
            public void run() {
                if (ticket.cancelled || engine.isClosed()) return;
                Bitmap bmp;
                try {
                    bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888);
                } catch (OutOfMemoryError oom) {
                    cache.trimToHalf();
                    return;
                }
                try {
                    engine.render(page, bmp, offX, offY, fullW, fullH, true);
                } catch (RuntimeException e) {
                    return;
                }
                if (cacheKey != null) cache.put(cacheKey, bmp);
                if (ticket.cancelled) return;
                main.post(() -> {
                    if (!ticket.cancelled) callback.onRendered(bmp);
                });
            }
        });
        return ticket;
    }

    /** Runs arbitrary engine work on the render thread (keeps Pdfium access serialised). */
    public void post(int priority, @NonNull Runnable work) {
        executor.execute(new Job(priority, sequence.incrementAndGet()) {
            @Override
            public void run() {
                if (!engine.isClosed()) work.run();
            }
        });
    }

    public void shutdown() {
        executor.getQueue().clear();
        executor.shutdown();
    }

    private abstract static class Job implements Runnable {
        final int priority;
        final long seq;

        Job(int priority, long seq) {
            this.priority = priority;
            this.seq = seq;
        }
    }
}
