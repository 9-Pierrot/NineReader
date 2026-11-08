package com.ninereader.pdfops;

import androidx.annotation.NonNull;

/**
 * Compression levels (CMP-01) and the size estimate shown before running the operation.
 */
public enum CompressionLevel {
    LOW(2200, 0.85f, 0.32f),
    MEDIUM(1600, 0.70f, 0.20f),
    HIGH(1100, 0.50f, 0.12f);

    /** Maximum image dimension (pixels) after down-sampling. */
    public final int maxDimension;
    /** JPEG quality in [0..1]. */
    public final float quality;
    /** Empirical JPEG size in bytes per pixel at this quality. */
    final float bytesPerPixel;

    CompressionLevel(int maxDimension, float quality, float bytesPerPixel) {
        this.maxDimension = maxDimension;
        this.quality = quality;
        this.bytesPerPixel = bytesPerPixel;
    }

    @NonNull
    public static CompressionLevel fromKey(String key) {
        for (CompressionLevel l : values()) if (l.name().equalsIgnoreCase(key)) return l;
        return MEDIUM;
    }

    /** Estimated size of one re-encoded image; never more than its current size. */
    public long estimateImage(int width, int height, long currentBytes) {
        double scale = Math.min(1.0, maxDimension / (double) Math.max(1, Math.max(width, height)));
        double pixels = Math.max(1.0, width * scale) * Math.max(1.0, height * scale);
        long estimated = Math.round(pixels * bytesPerPixel);
        return Math.min(currentBytes, estimated);
    }

    /**
     * Estimated size of the compressed file.
     *
     * @param fileSize    current file size
     * @param imageBytes  current encoded size of the images that will be re-encoded
     * @param newImageBytes estimated size of those images after re-encoding
     */
    public static long estimateFile(long fileSize, long imageBytes, long newImageBytes) {
        long other = Math.max(0, fileSize - imageBytes);
        // Rewriting drops unreferenced objects; assume a small gain on the rest of the file.
        long rest = Math.round(other * 0.95);
        return Math.max(1, rest + newImageBytes);
    }
}
