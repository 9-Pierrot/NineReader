package com.ninereader.reader;

import android.graphics.Bitmap;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Bounded bitmap cache (§5.1): sized in bytes against the app heap, so a 1 000-page document never
 * exhausts memory. Keys encode page, render width and render quality.
 */
public final class BitmapCache {

    private final LruCache<String, Bitmap> cache;

    public BitmapCache(int maxBytes) {
        cache = new LruCache<String, Bitmap>(maxBytes) {
            @Override
            protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
                return value.getAllocationByteCount();
            }
        };
    }

    /** Default cache: one sixth of the maximum heap. */
    @NonNull
    public static BitmapCache forHeap() {
        long max = Runtime.getRuntime().maxMemory();
        return new BitmapCache((int) Math.min(Integer.MAX_VALUE, max / 6));
    }

    @NonNull
    public static String key(int page, int width, int height, int quality, int cropHash) {
        return page + ":" + width + "x" + height + ":" + quality + ":" + cropHash;
    }

    @Nullable
    public Bitmap get(@NonNull String key) {
        Bitmap b = cache.get(key);
        if (b != null && b.isRecycled()) {
            cache.remove(key);
            return null;
        }
        return b;
    }

    public void put(@NonNull String key, @NonNull Bitmap bitmap) {
        cache.put(key, bitmap);
    }

    public void clear() {
        cache.evictAll();
    }

    public void trimToHalf() {
        cache.trimToSize(cache.maxSize() / 2);
    }

    public int sizeBytes() {
        return cache.size();
    }
}
