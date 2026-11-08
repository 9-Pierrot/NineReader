package com.ninereader.reader;

import androidx.annotation.NonNull;

/**
 * Affine mapping between PDF page coordinates (points, y up, rotation applied by Pdfium) and
 * normalised display coordinates in [0..1] (y down). Computed once per page from Pdfium so that all
 * per-frame mapping is pure arithmetic on the UI thread.
 */
public final class PageTransform {

    // nx = a*px + b*py + e ; ny = c*px + d*py + f
    final double a;
    final double b;
    final double c;
    final double d;
    final double e;
    final double f;

    public PageTransform(double a, double b, double c, double d, double e, double f) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
        this.f = f;
    }

    /** Transform of an unrotated page whose origin is the bottom-left corner. */
    @NonNull
    public static PageTransform simple(float pageWidth, float pageHeight) {
        return new PageTransform(1.0 / pageWidth, 0, 0, -1.0 / pageHeight, 0, 1);
    }

    /**
     * Builds the transform from the device positions (normalised) of three page points:
     * origin, (unit, 0) and (0, unit).
     */
    @NonNull
    public static PageTransform fromSamples(double unit, double x0, double y0, double x1, double y1, double x2, double y2) {
        return new PageTransform((x1 - x0) / unit, (x2 - x0) / unit, (y1 - y0) / unit, (y2 - y0) / unit, x0, y0);
    }

    /** Page point → normalised display point; writes into out[0..1]. */
    public void toNormalized(double px, double py, @NonNull float[] out) {
        out[0] = (float) (a * px + b * py + e);
        out[1] = (float) (c * px + d * py + f);
    }

    /** Normalised display point → page point; writes into out[0..1]. */
    public void toPage(double nx, double ny, @NonNull float[] out) {
        double det = a * d - b * c;
        if (Math.abs(det) < 1e-12) {
            out[0] = 0;
            out[1] = 0;
            return;
        }
        double x = nx - e;
        double y = ny - f;
        out[0] = (float) ((d * x - b * y) / det);
        out[1] = (float) ((-c * x + a * y) / det);
    }

    /**
     * Maps a page rectangle (left, top, right, bottom in page units, any orientation) to a normalised
     * bounding box {l, t, r, b}.
     */
    @NonNull
    public float[] rectToNormalized(float left, float top, float right, float bottom) {
        float[] p = new float[2];
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float[][] corners = {{left, top}, {right, top}, {left, bottom}, {right, bottom}};
        for (float[] corner : corners) {
            toNormalized(corner[0], corner[1], p);
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    /** Scale factor from page units to normalised units along the display x axis. */
    public double unitScaleX() {
        return Math.hypot(a, c);
    }
}
