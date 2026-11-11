package com.ninereader.annotations;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ninereader.reader.view.PageFrame;
import com.ninereader.util.AppExecutors;

import java.util.HashSet;
import java.util.Set;

/** Draws pending annotations over a page, using a {@link PageFrame} for coordinate mapping. */
public class AnnotationPainter {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF();
    private final RectF pageRect = new RectF();
    private final float[] pt = new float[2];
    private final float[] pt2 = new float[2];
    private final Path path = new Path();
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(24 * 1024 * 1024) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
            return value.getAllocationByteCount();
        }
    };
    private final Set<String> loading = new HashSet<>();
    @Nullable
    private Runnable invalidator;
    private final float density;

    @Nullable
    private android.content.Context context;

    /** Context used to load the editing fonts (text edits are drawn with the font that will be embedded). */
    public void setContext(@Nullable android.content.Context context) {
        this.context = context == null ? null : context.getApplicationContext();
    }

    public AnnotationPainter(float density) {
        this.density = density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        selectionPaint.setStyle(Paint.Style.STROKE);
        selectionPaint.setColor(0xFF2B5BB5);
        selectionPaint.setStrokeWidth(1.5f * density);
        selectionPaint.setPathEffect(new DashPathEffect(new float[]{6 * density, 4 * density}, 0));
        handlePaint.setColor(0xFF2B5BB5);
    }

    /** Called when an asynchronously decoded image becomes available. */
    public void setInvalidator(@Nullable Runnable invalidator) {
        this.invalidator = invalidator;
    }

    public void draw(@NonNull Canvas c, @NonNull PageFrame f, @NonNull Annotation a, boolean selected) {
        float unit = f.pageUnitToPx();
        int color = a.color;
        switch (a.type) {
            case HIGHLIGHT:
                fill.setColor((color & 0x00FFFFFF) | 0x66000000);
                for (float[] q : a.rects) {
                    if (map(f, q, r)) c.drawRect(r, fill);
                }
                break;
            case UNDERLINE:
            case STRIKEOUT:
                stroke.setColor(color);
                for (float[] q : a.rects) {
                    if (!map(f, q, r)) continue;
                    float h = r.height();
                    stroke.setStrokeWidth(Math.max(1f, h * 0.08f));
                    float y = a.type == Annotation.Type.UNDERLINE ? r.bottom - h * 0.05f : r.centerY();
                    c.drawLine(r.left, y, r.right, y, stroke);
                }
                break;
            case INK:
                stroke.setColor(color);
                stroke.setAlpha(Math.round(255 * a.opacity));
                stroke.setStrokeWidth(Math.max(1f, a.width * unit));
                for (float[] s : a.strokes) drawPolyline(c, f, s);
                stroke.setAlpha(255);
                break;
            case RECT:
            case CIRCLE:
                if (a.bounds == null || !map(f, a.bounds, r)) break;
                stroke.setColor(color);
                stroke.setStrokeWidth(Math.max(1f, a.width * unit));
                if (a.type == Annotation.Type.RECT) {
                    c.drawRect(r, stroke);
                } else {
                    c.drawOval(r, stroke);
                }
                break;
            case LINE:
            case ARROW:
                if (a.bounds == null) break;
                f.pagePointToView(a.bounds[0], a.bounds[1], pt);
                f.pagePointToView(a.bounds[2], a.bounds[3], pt2);
                stroke.setColor(color);
                stroke.setStrokeWidth(Math.max(1f, a.width * unit));
                c.drawLine(pt[0], pt[1], pt2[0], pt2[1], stroke);
                if (a.type == Annotation.Type.ARROW) drawArrowHead(c, pt[0], pt[1], pt2[0], pt2[1], Math.max(10f, a.width * unit * 4));
                break;
            case NOTE:
                if (a.bounds == null || !map(f, a.bounds, r)) break;
                fill.setColor(color);
                c.drawRoundRect(r, 3 * density, 3 * density, fill);
                stroke.setColor(0xAA000000);
                stroke.setStrokeWidth(Math.max(1f, density));
                c.drawRoundRect(r, 3 * density, 3 * density, stroke);
                float inset = r.width() * 0.2f;
                for (int i = 1; i <= 3; i++) {
                    float y = r.top + r.height() * i / 4f;
                    c.drawLine(r.left + inset, y, r.right - inset, y, stroke);
                }
                break;
            case FREETEXT:
                if (a.bounds == null || !map(f, a.bounds, r) || a.text == null) break;
                textPaint.setColor(color);
                textPaint.setTextSize(Math.max(4f, a.fontSize * unit));
                int w = Math.max(1, Math.round(r.width()));
                StaticLayout layout = StaticLayout.Builder.obtain(a.text, 0, a.text.length(), textPaint, w)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).build();
                c.save();
                c.translate(r.left, r.top);
                layout.draw(c);
                c.restore();
                break;
            case IMAGE:
                if (a.bounds == null || !map(f, a.bounds, r)) break;
                Bitmap bmp = image(a.imagePath);
                if (bmp != null) {
                    c.drawBitmap(bmp, null, r, bitmapPaint);
                } else {
                    stroke.setColor(0x55000000);
                    stroke.setStrokeWidth(density);
                    c.drawRect(r, stroke);
                }
                break;
            case TEXT_EDIT:
                drawTextEdit(c, f, a, unit);
                break;
            case WHITEOUT:
                if (a.bounds == null || !map(f, a.bounds, r)) break;
                fill.setColor(0xFFFFFFFF);
                c.drawRect(r, fill);
                break;
            case REDACT:
                if (a.bounds == null || !map(f, a.bounds, r)) break;
                fill.setColor(0xE6000000);
                c.drawRect(r, fill);
                stroke.setColor(0xFFE53935);
                stroke.setStrokeWidth(Math.max(1f, density));
                c.drawRect(r, stroke);
                break;
            default:
                break;
        }
        if (selected) drawSelection(c, f, a);
    }

    /** Laid-out edited texts (layout in points), keyed by content and geometry. */
    private final LruCache<String, java.util.List<com.ninereader.edit.RichTextLayout.Line>> textLayouts = new LruCache<>(64);

    /**
     * Edited text: hides the original block with the colour of the page under it (white, or a coloured
     * panel) and draws the new text with its styles, highlights and the font that will be embedded, laid
     * out exactly like in the saved PDF.
     */
    private void drawTextEdit(Canvas c, PageFrame f, Annotation a, float unit) {
        if (a.originalBounds != null && map(f, a.originalBounds, r)) {
            fill.setColor(a.background == 0 ? 0xFFFFFFFF : a.background | 0xFF000000);
            r.inset(-1, -1);
            c.drawRect(r, fill);
        }
        if (a.bounds == null || a.text == null || a.text.isEmpty() || context == null || !map(f, a.bounds, r)) return;
        float sizePt = Math.max(2f, a.fontSize);
        float scale = com.ninereader.edit.PdfTextEditor.scaleOf(a);
        float pitchPt = com.ninereader.edit.PdfTextEditor.lineHeightOf(a);
        float widthPt = Math.max(sizePt * 2, Math.abs(a.bounds[2] - a.bounds[0]));
        com.ninereader.edit.EditFonts.Family family = com.ninereader.edit.PdfTextEditor.familyOf(a);
        java.util.List<com.ninereader.edit.StyleRun> runs = com.ninereader.edit.PdfTextEditor.runsOf(a);
        StringBuilder key = new StringBuilder(a.text).append('|').append(widthPt).append('|').append(sizePt).append('|')
                .append(scale).append('|').append(family);
        for (com.ninereader.edit.StyleRun run : runs) {
            key.append('|').append(run.start).append(',').append(run.end).append(',').append(run.bold).append(run.italic)
                    .append(',').append(run.color).append(',').append(run.highlight);
        }
        java.util.List<com.ninereader.edit.RichTextLayout.Line> lines = textLayouts.get(key.toString());
        if (lines == null) {
            lines = com.ninereader.edit.RichTextLayout.layout(a.text, runs, widthPt,
                    com.ninereader.edit.EditFonts.screenMeasurer(context, family, sizePt, scale));
            textLayouts.put(key.toString(), lines);
        }
        float firstPt = com.ninereader.edit.PdfTextEditor.firstBaselineOffset(sizePt);
        float[] span = com.ninereader.edit.RichTextLayout.highlightSpan(sizePt, pitchPt);
        textPaint.setTextSize(sizePt * unit);
        textPaint.setTextScaleX(scale);
        for (int i = 0; i < lines.size(); i++) {
            float baseline = r.top + (firstPt + i * pitchPt) * unit;
            for (com.ninereader.edit.RichTextLayout.Piece p : lines.get(i).pieces) {
                if (p.style.highlight == 0 || p.width <= 0) continue;
                fill.setColor(p.style.highlight | 0xFF000000);
                c.drawRect(r.left + p.x * unit, baseline - span[1] * unit, r.left + (p.x + p.width) * unit, baseline - span[0] * unit, fill);
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            float baseline = r.top + (firstPt + i * pitchPt) * unit;
            for (com.ninereader.edit.RichTextLayout.Piece p : lines.get(i).pieces) {
                if (p.text.trim().isEmpty()) continue;
                textPaint.setTypeface(com.ninereader.edit.EditFonts.typeface(context, family, p.style.bold, p.style.italic));
                textPaint.setColor(p.style.color | 0xFF000000);
                c.drawText(p.text, r.left + p.x * unit, baseline, textPaint);
            }
        }
        textPaint.setTypeface(null);
        textPaint.setTextScaleX(1f);
    }

    private void drawSelection(Canvas c, PageFrame f, Annotation a) {
        float[] bb = a.boundingBox();
        if (!map(f, bb, r)) return;
        float pad = 4 * density;
        r.inset(-pad, -pad);
        c.drawRect(r, selectionPaint);
        if (a.type.isBoxed()) {
            c.drawCircle(r.right, r.bottom, 7 * density, handlePaint);
        }
    }

    /** Screen-space handle position (bottom-right corner) of a boxed annotation, in spread coordinates. */
    public boolean handlePosition(@NonNull PageFrame f, @NonNull Annotation a, @NonNull float[] out) {
        float[] bb = a.boundingBox();
        if (!map(f, bb, r)) return false;
        float pad = 4 * density;
        out[0] = r.right + pad;
        out[1] = r.bottom + pad;
        return true;
    }

    private void drawPolyline(Canvas c, PageFrame f, float[] s) {
        if (s.length < 2) return;
        path.reset();
        f.pagePointToView(s[0], s[1], pt);
        path.moveTo(pt[0], pt[1]);
        if (s.length == 2) {
            path.lineTo(pt[0] + 0.1f, pt[1]);
        }
        for (int i = 2; i + 1 < s.length; i += 2) {
            f.pagePointToView(s[i], s[i + 1], pt);
            path.lineTo(pt[0], pt[1]);
        }
        c.drawPath(path, stroke);
    }

    private void drawArrowHead(Canvas c, float x1, float y1, float x2, float y2, float size) {
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double a1 = angle + Math.toRadians(150);
        double a2 = angle - Math.toRadians(150);
        c.drawLine(x2, y2, (float) (x2 + size * Math.cos(a1)), (float) (y2 + size * Math.sin(a1)), stroke);
        c.drawLine(x2, y2, (float) (x2 + size * Math.cos(a2)), (float) (y2 + size * Math.sin(a2)), stroke);
    }

    /** Maps a {left, bottom, right, top} page box to a view rect. */
    private boolean map(PageFrame f, float[] box, RectF out) {
        pageRect.set(box[0], box[3], box[2], box[1]);
        return f.pageToView(pageRect, out);
    }

    @Nullable
    private Bitmap image(@Nullable String imagePath) {
        if (imagePath == null) return null;
        Bitmap b = images.get(imagePath);
        if (b != null) return b;
        if (loading.add(imagePath)) {
            AppExecutors.get().background().execute(() -> {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(imagePath, o);
                int sample = 1;
                while (o.outWidth / sample > 1600 || o.outHeight / sample > 1600) sample *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                Bitmap decoded = BitmapFactory.decodeFile(imagePath, o2);
                AppExecutors.get().mainThread().execute(() -> {
                    loading.remove(imagePath);
                    if (decoded != null) {
                        images.put(imagePath, decoded);
                        if (invalidator != null) invalidator.run();
                    }
                });
            });
        }
        return null;
    }
}
