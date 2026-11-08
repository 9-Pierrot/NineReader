package com.ninereader.reader.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ninereader.reader.BitmapCache;
import com.ninereader.reader.RenderScheduler;

/**
 * One item of the page list: one page, or two pages side by side (double page mode). Draws a quick
 * low-resolution render first, then the full-resolution render, then an optional high-resolution
 * tile of the visible area when zoomed in (§5.1).
 */
public class SpreadView extends View {

    static final float LOW_RES_FACTOR = 0.35f;

    private final Slot[] slots = {new Slot(), new Slot()};
    private int slotCount;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint pagePaint = new Paint();
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    @Nullable
    private RenderScheduler scheduler;
    @Nullable
    private PageDecorator decorator;
    private int spreadIndex = -1;
    private int pageBackground = 0xFFFFFFFF;
    private int cropKey;

    public SpreadView(Context context) {
        super(context);
        pagePaint.setColor(pageBackground);
        shadowPaint.setColor(0x22000000);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** Per page drawing state. */
    static final class Slot {
        final PageFrame frame = new PageFrame();
        @Nullable
        Bitmap low;
        @Nullable
        Bitmap full;
        @Nullable
        Bitmap tile;
        final RectF tileRect = new RectF();
        int fullKeyW;
        int fullKeyH;
        @Nullable
        RenderScheduler.Ticket lowTicket;
        @Nullable
        RenderScheduler.Ticket fullTicket;
        @Nullable
        RenderScheduler.Ticket tileTicket;

        void cancelAll() {
            if (lowTicket != null) lowTicket.cancel();
            if (fullTicket != null) fullTicket.cancel();
            if (tileTicket != null) tileTicket.cancel();
            lowTicket = fullTicket = tileTicket = null;
        }

        void reset() {
            cancelAll();
            low = null;
            full = null;
            tile = null;
            frame.page = -1;
            frame.transform = null;
        }
    }

    public void setScheduler(@Nullable RenderScheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void setDecorator(@Nullable PageDecorator decorator) {
        this.decorator = decorator;
    }

    public void setColorFilter(@Nullable ColorFilter filter, int pageBackground) {
        bitmapPaint.setColorFilter(filter);
        this.pageBackground = pageBackground;
        pagePaint.setColor(pageBackground);
        invalidate();
    }

    public int getSpreadIndex() {
        return spreadIndex;
    }

    public int getSlotCount() {
        return slotCount;
    }

    @NonNull
    public PageFrame frame(int slot) {
        return slots[slot].frame;
    }

    /** Frame of the page under a point, or null. */
    @Nullable
    public PageFrame frameAt(float x, float y) {
        for (int i = 0; i < slotCount; i++) {
            if (slots[i].frame.contains(x, y)) return slots[i].frame;
        }
        // Tolerate taps in the gutter: return the closest page.
        if (slotCount == 0) return null;
        if (slotCount == 1) return slots[0].frame;
        return x < getWidth() / 2f ? slots[0].frame : slots[1].frame;
    }

    @Nullable
    public PageFrame frameForPage(int page) {
        for (int i = 0; i < slotCount; i++) {
            if (slots[i].frame.page == page) return slots[i].frame;
        }
        return null;
    }

    /**
     * Binds the view to a spread.
     *
     * @param frames    geometry of each page of the spread (already laid out for this view size)
     * @param cropKey   identifies the crop setting, part of the cache key
     */
    public void bind(int spreadIndex, @NonNull PageFrame[] frames, int cropKey) {
        boolean same = this.spreadIndex == spreadIndex && this.cropKey == cropKey && slotCount == frames.length;
        if (same) {
            for (int i = 0; i < frames.length; i++) {
                PageFrame f = slots[i].frame;
                PageFrame n = frames[i];
                if (f.page != n.page || Math.round(f.fullW) != Math.round(n.fullW)
                        || Math.round(f.fullH) != Math.round(n.fullH) || !f.slot.equals(n.slot)) {
                    same = false;
                    break;
                }
            }
        }
        this.spreadIndex = spreadIndex;
        this.cropKey = cropKey;
        if (!same) {
            for (Slot s : slots) s.reset();
        }
        slotCount = frames.length;
        for (int i = 0; i < frames.length; i++) {
            PageFrame f = slots[i].frame;
            PageFrame n = frames[i];
            f.page = n.page;
            f.slot.set(n.slot);
            f.fullW = n.fullW;
            f.fullH = n.fullH;
            f.offX = n.offX;
            f.offY = n.offY;
            f.transform = n.transform;
        }
        setContentDescription(null);
        requestRender();
        invalidate();
    }

    /** Releases bitmaps and cancels renders (view recycled or detached). */
    public void unbind() {
        for (Slot s : slots) s.reset();
        spreadIndex = -1;
        slotCount = 0;
    }

    /** Starts (or restarts) the low then full resolution renders of the bound pages. */
    public void requestRender() {
        RenderScheduler sch = scheduler;
        if (sch == null) return;
        for (int i = 0; i < slotCount; i++) {
            Slot s = slots[i];
            PageFrame f = s.frame;
            int w = Math.round(f.slot.width());
            int h = Math.round(f.slot.height());
            if (w <= 0 || h <= 0 || f.page < 0) continue;
            if (s.full != null && s.fullKeyW == w && s.fullKeyH == h) continue;
            final int page = f.page;
            int fullW = Math.round(f.fullW);
            int fullH = Math.round(f.fullH);
            int offX = Math.round(f.offX);
            int offY = Math.round(f.offY);
            if (s.low == null && s.lowTicket == null) {
                int lw = Math.max(1, Math.round(w * LOW_RES_FACTOR));
                int lh = Math.max(1, Math.round(h * LOW_RES_FACTOR));
                String lowKey = BitmapCache.key(page, lw, lh, 0, cropKey);
                s.lowTicket = sch.request(page, lw, lh, Math.round(offX * LOW_RES_FACTOR), Math.round(offY * LOW_RES_FACTOR),
                        Math.round(fullW * LOW_RES_FACTOR), Math.round(fullH * LOW_RES_FACTOR),
                        RenderScheduler.PRIORITY_VISIBLE, lowKey, bmp -> {
                            if (s.frame.page == page) {
                                s.low = bmp;
                                invalidate();
                            }
                        });
            }
            if (s.fullTicket != null) s.fullTicket.cancel();
            String fullKey = BitmapCache.key(page, w, h, 1, cropKey);
            s.fullTicket = sch.request(page, w, h, offX, offY, fullW, fullH,
                    RenderScheduler.PRIORITY_PREFETCH, fullKey, bmp -> {
                        if (s.frame.page == page) {
                            s.full = bmp;
                            s.fullKeyW = w;
                            s.fullKeyH = h;
                            invalidate();
                        }
                    });
        }
    }

    /**
     * Renders high-resolution tiles of the visible area at the given zoom.
     *
     * @param visible visible area in this view's coordinates
     */
    public void requestTile(@NonNull RectF visible, float zoom) {
        RenderScheduler sch = scheduler;
        if (sch == null) return;
        for (int i = 0; i < slotCount; i++) {
            Slot s = slots[i];
            if (s.tileTicket != null) {
                s.tileTicket.cancel();
                s.tileTicket = null;
            }
            PageFrame f = s.frame;
            RectF t = new RectF(f.slot);
            if (zoom <= 1.05f || !t.intersect(visible)) {
                if (s.tile != null) {
                    s.tile = null;
                    invalidate();
                }
                continue;
            }
            if (s.tile != null && s.tileRect.contains(t) && Math.abs(s.tile.getWidth() - s.tileRect.width() * zoom) < 2) {
                continue; // current tile still covers the visible area at this zoom
            }
            final RectF tileRect = new RectF(t);
            int bw = Math.round(tileRect.width() * zoom);
            int bh = Math.round(tileRect.height() * zoom);
            if (bw <= 0 || bh <= 0 || (long) bw * bh > 24_000_000L) continue;
            int offX = Math.round((f.offX + tileRect.left - f.slot.left) * zoom);
            int offY = Math.round((f.offY + tileRect.top - f.slot.top) * zoom);
            final int page = f.page;
            s.tileTicket = sch.request(page, bw, bh, offX, offY, Math.round(f.fullW * zoom), Math.round(f.fullH * zoom),
                    RenderScheduler.PRIORITY_TILE, null, bmp -> {
                        if (s.frame.page == page) {
                            s.tile = bmp;
                            s.tileRect.set(tileRect);
                            invalidate();
                        }
                    });
        }
    }

    /** Drops high-resolution tiles (zoom reset). */
    public void clearTiles() {
        for (Slot s : slots) {
            if (s.tileTicket != null) s.tileTicket.cancel();
            s.tileTicket = null;
            s.tile = null;
        }
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        for (int i = 0; i < slotCount; i++) {
            Slot s = slots[i];
            RectF slot = s.frame.slot;
            canvas.drawRect(slot.left + 2, slot.top + 3, slot.right + 2, slot.bottom + 3, shadowPaint);
            Bitmap base = s.full != null ? s.full : s.low;
            if (base != null && !base.isRecycled()) {
                src.set(0, 0, base.getWidth(), base.getHeight());
                canvas.drawBitmap(base, src, slot, bitmapPaint);
            } else {
                canvas.drawRect(slot, pagePaint);
            }
            Bitmap tile = s.tile;
            if (tile != null && !tile.isRecycled()) {
                src.set(0, 0, tile.getWidth(), tile.getHeight());
                dst.set(s.tileRect);
                canvas.drawBitmap(tile, src, dst, bitmapPaint);
            }
            if (decorator != null) decorator.decorate(canvas, s.frame);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (Slot s : slots) s.cancelAll();
    }
}
