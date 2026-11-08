package com.ninereader.reader.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Page list with pinch / double-tap zoom (LEC-04). The whole content is drawn scaled and translated;
 * vertical scrolling stays handled by RecyclerView (so pages are recycled and pre-rendered) while
 * horizontal panning, and panning inside a zoomed page in paged mode, are handled here.
 */
public class ZoomableRecyclerView extends RecyclerView {

    public static final float MAX_SCALE = 6f;
    public static final float DOUBLE_TAP_SCALE = 2.5f;

    /** Result of a hit test: the page under a point. */
    public static final class Hit {
        @NonNull
        public final SpreadView view;
        @NonNull
        public final PageFrame frame;
        /** Point in spread-view coordinates. */
        public final float x;
        public final float y;
        /** Point in page coordinates (valid when {@link #hasPagePoint}). */
        public final float pageX;
        public final float pageY;
        public final boolean hasPagePoint;

        Hit(@NonNull SpreadView view, @NonNull PageFrame frame, float x, float y) {
            this.view = view;
            this.frame = frame;
            this.x = x;
            this.y = y;
            float[] p = new float[2];
            this.hasPagePoint = frame.viewToPage(x, y, p);
            this.pageX = p[0];
            this.pageY = p[1];
        }
    }

    /** Gesture callbacks. */
    public interface Listener {
        void onSingleTap(@Nullable Hit hit, float screenX, float screenY);

        /** Return true when the double tap was handled (e.g. column zoom). */
        boolean onDoubleTap(@Nullable Hit hit, float screenX, float screenY);

        void onLongPress(@NonNull Hit hit);

        void onLongPressDrag(@Nullable Hit hit);

        void onLongPressEnd();

        void onZoomChanged(float scale);
    }

    /** Receives raw touch events first (drawing tools). Return true to consume. */
    public interface TouchInterceptor {
        boolean onTouch(@NonNull MotionEvent event);
    }

    private float scale = 1f;
    private float tx;
    private float ty;
    private boolean horizontalMode;
    private boolean ignoreRvUntilDown;
    private boolean longPressActive;
    private final OverScroller scroller;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    @Nullable
    private Listener listener;
    @Nullable
    private TouchInterceptor interceptor;
    @Nullable
    private ValueAnimator zoomAnimator;
    private final Matrix eventMatrix = new Matrix();
    private final Runnable settleRunnable = this::renderTiles;

    public ZoomableRecyclerView(@NonNull Context context) {
        this(context, null);
    }

    public ZoomableRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setClipToPadding(false);
        scroller = new OverScroller(context);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                cancelZoomAnimation();
                return true;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                applyScale(scale * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                scheduleSettle();
            }
        });
        scaleDetector.setQuickScaleEnabled(false);
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (listener != null) listener.onSingleTap(hitTest(e.getX(), e.getY()), e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                Hit hit = hitTest(e.getX(), e.getY());
                if (listener != null && listener.onDoubleTap(hit, e.getX(), e.getY())) return true;
                if (scale > 1.05f) {
                    animateScale(1f, e.getX(), e.getY());
                } else {
                    animateScale(DOUBLE_TAP_SCALE, e.getX(), e.getY());
                }
                return true;
            }

            @Override
            public void onLongPress(@NonNull MotionEvent e) {
                if (scaleDetector.isInProgress()) return;
                Hit hit = hitTest(e.getX(), e.getY());
                if (hit == null || listener == null) return;
                longPressActive = true;
                cancelRvGesture(e);
                listener.onLongPress(hit);
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float dx, float dy) {
                if (scaleDetector.isInProgress() || scale <= 1.01f) return false;
                tx -= dx;
                if (horizontalMode) ty -= dy;
                clampTranslation();
                invalidate();
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float vx, float vy) {
                if (scale <= 1.01f) return false;
                int minX = Math.round(getWidth() - getWidth() * scale);
                int minY = horizontalMode ? Math.round(getHeight() - getHeight() * scale) : 0;
                scroller.fling(Math.round(tx), Math.round(ty), Math.round(vx), horizontalMode ? Math.round(vy) : 0,
                        minX, 0, minY, 0);
                postInvalidateOnAnimation();
                return true;
            }
        });
        addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == SCROLL_STATE_IDLE) scheduleSettle();
            }
        });
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setTouchInterceptor(@Nullable TouchInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    /** Switches between vertical continuous layout and horizontal paged layout. */
    public void setHorizontalMode(boolean horizontal) {
        this.horizontalMode = horizontal;
        resetZoom();
    }

    public boolean isHorizontalMode() {
        return horizontalMode;
    }

    public float getScale() {
        return scale;
    }

    public float getTx() {
        return tx;
    }

    public float getTy() {
        return ty;
    }

    // ------------------------------------------------------------------ zoom

    public void resetZoom() {
        cancelZoomAnimation();
        scroller.forceFinished(true);
        scale = 1f;
        tx = 0;
        ty = 0;
        updatePadding();
        invalidate();
        clearTiles();
        if (listener != null) listener.onZoomChanged(scale);
    }

    /** Sets the zoom keeping the horizontal centre (used to restore the reading position). */
    public void setScaleCentered(float newScale) {
        float s = clampScale(newScale);
        scale = s;
        tx = (getWidth() - getWidth() * s) / 2f;
        ty = 0;
        updatePadding();
        clampTranslation();
        invalidate();
        scheduleSettle();
        if (listener != null) listener.onZoomChanged(scale);
    }

    /** Zooms by a factor around the view centre (keyboard / buttons). */
    public void zoomBy(float factor) {
        animateScale(scale * factor, getWidth() / 2f, getHeight() / 2f);
    }

    /**
     * Zooms so that a horizontal band of a spread (column) fills the width (RAP-05).
     *
     * @param view   the spread view containing the column
     * @param left   column left in spread coordinates
     * @param right  column right in spread coordinates
     * @param focusY screen y to keep stable
     */
    public void zoomToColumn(@NonNull SpreadView view, float left, float right, float focusY) {
        float width = right - left;
        if (width <= 0) return;
        float target = clampScale(getWidth() / (width * 1.04f));
        float contentLeft = view.getLeft() + left - width * 0.02f;
        animateTo(target, focusY, () -> {
            tx = -contentLeft * scale;
            clampTranslation();
            invalidate();
        });
    }

    private void animateTo(float target, float focusY, @Nullable Runnable end) {
        animateScale(target, getWidth() / 2f, focusY);
        if (end != null && zoomAnimator != null) {
            zoomAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    end.run();
                    scheduleSettle();
                }
            });
        }
    }

    public void animateScale(float target, float fx, float fy) {
        cancelZoomAnimation();
        float to = clampScale(target);
        ValueAnimator anim = ValueAnimator.ofFloat(scale, to);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> applyScale((float) a.getAnimatedValue(), fx, fy));
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                if (scale <= 1.01f) {
                    resetZoom();
                } else {
                    scheduleSettle();
                }
            }
        });
        zoomAnimator = anim;
        anim.start();
    }

    private void cancelZoomAnimation() {
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
            zoomAnimator = null;
        }
    }

    private float clampScale(float s) {
        return Math.max(1f, Math.min(MAX_SCALE, s));
    }

    void applyScale(float newScale, float fx, float fy) {
        float s = clampScale(newScale);
        if (s == scale) return;
        float cx = (fx - tx) / scale;
        float cy = (fy - ty) / scale;
        scale = s;
        tx = fx - cx * scale;
        if (horizontalMode) {
            ty = fy - cy * scale;
        } else {
            ty = 0;
            updatePadding();
            int dy = Math.round(cy - fy / scale);
            if (dy != 0) scrollBy(0, dy);
        }
        clampTranslation();
        invalidate();
        if (listener != null) listener.onZoomChanged(scale);
    }

    private void updatePadding() {
        if (horizontalMode) {
            if (getPaddingBottom() != 0) setPadding(0, 0, 0, 0);
            return;
        }
        int h = getHeight();
        int pad = h <= 0 ? 0 : Math.round(h - h / scale);
        if (pad != getPaddingBottom()) setPadding(0, 0, 0, pad);
    }

    private void clampTranslation() {
        float minX = getWidth() - getWidth() * scale;
        tx = Math.max(minX, Math.min(0, tx));
        if (horizontalMode) {
            float minY = getHeight() - getHeight() * scale;
            ty = Math.max(minY, Math.min(0, ty));
        } else {
            ty = 0;
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (scale == 1f && tx == 0 && ty == 0) {
            super.dispatchDraw(canvas);
            return;
        }
        int save = canvas.save();
        canvas.translate(tx, ty);
        canvas.scale(scale, scale);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (scroller.computeScrollOffset()) {
            tx = scroller.getCurrX();
            if (horizontalMode) ty = scroller.getCurrY();
            clampTranslation();
            postInvalidateOnAnimation();
            if (scroller.isFinished()) scheduleSettle();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updatePadding();
        clampTranslation();
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (interceptor != null && interceptor.onTouch(ev)) {
            return true;
        }
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            scroller.forceFinished(true);
            ignoreRvUntilDown = false;
            longPressActive = false;
        }
        scaleDetector.onTouchEvent(ev);
        if (longPressActive) {
            if (listener != null) {
                if (action == MotionEvent.ACTION_MOVE) {
                    listener.onLongPressDrag(hitTest(ev.getX(), ev.getY()));
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    longPressActive = false;
                    listener.onLongPressEnd();
                }
            }
            return true;
        }
        gestureDetector.onTouchEvent(ev);
        if (scaleDetector.isInProgress() || ev.getPointerCount() > 1) {
            cancelRvGesture(ev);
            return true;
        }
        boolean rvScrolls = !horizontalMode || scale <= 1.01f;
        if (!ignoreRvUntilDown && rvScrolls) {
            MotionEvent t = MotionEvent.obtain(ev);
            if (scale != 1f) {
                eventMatrix.setScale(1f / scale, 1f / scale);
                t.transform(eventMatrix);
            }
            super.onTouchEvent(t);
            t.recycle();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) scheduleSettle();
        return true;
    }

    private void cancelRvGesture(MotionEvent ev) {
        if (ignoreRvUntilDown) return;
        MotionEvent cancel = MotionEvent.obtain(ev);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.onTouchEvent(cancel);
        cancel.recycle();
        ignoreRvUntilDown = true;
    }

    /** Hit test in screen coordinates (relative to this view). */
    @Nullable
    public Hit hitTest(float x, float y) {
        float cx = (x - tx) / scale;
        float cy = (y - ty) / scale;
        View child = findChildViewUnder(cx, cy);
        if (!(child instanceof SpreadView)) return null;
        SpreadView sv = (SpreadView) child;
        float lx = cx - child.getLeft() - child.getTranslationX();
        float ly = cy - child.getTop() - child.getTranslationY();
        PageFrame frame = sv.frameAt(lx, ly);
        if (frame == null || frame.page < 0) return null;
        return new Hit(sv, frame, lx, ly);
    }

    /** Converts a spread-view point to screen coordinates (relative to this view). */
    public void spreadToScreen(@NonNull SpreadView view, float x, float y, @NonNull float[] out) {
        out[0] = (view.getLeft() + view.getTranslationX() + x) * scale + tx;
        out[1] = (view.getTop() + view.getTranslationY() + y) * scale + ty;
    }

    // ------------------------------------------------------------------ position

    /** Adapter position of the spread at the centre of the screen (or -1). */
    public int centerPosition() {
        float cx = (getWidth() / 2f - tx) / scale;
        float cy = (getHeight() / 2f - ty) / scale;
        if (!horizontalMode) cy = Math.min(cy, (getHeight() * 0.35f - ty) / scale);
        View child = findChildViewUnder(cx, cy);
        if (child == null) {
            LayoutManager lm = getLayoutManager();
            if (lm instanceof LinearLayoutManager) return ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
            return -1;
        }
        return getChildAdapterPosition(child);
    }

    /** Scrolls so that a spread is shown at the top (or centred in paged mode). */
    public void goToPosition(int position, boolean smooth) {
        LayoutManager lm = getLayoutManager();
        if (horizontalMode && scale > 1.01f) resetZoom();
        if (smooth && Math.abs(position - centerPosition()) <= 2) {
            smoothScrollToPosition(position);
        } else if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(position, 0);
        } else {
            scrollToPosition(position);
        }
    }

    /** Scrolls one screen (continuous mode) forward or backward. */
    public void scrollScreen(boolean forward) {
        int dy = Math.round((getHeight() / scale) * 0.9f);
        smoothScrollBy(0, forward ? dy : -dy);
    }

    // ------------------------------------------------------------------ tiles

    private void scheduleSettle() {
        removeCallbacks(settleRunnable);
        postDelayed(settleRunnable, 140);
    }

    private void renderTiles() {
        RectF visible = new RectF(-tx / scale, -ty / scale, (getWidth() - tx) / scale, (getHeight() - ty) / scale);
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (!(c instanceof SpreadView)) continue;
            SpreadView sv = (SpreadView) c;
            RectF local = new RectF(visible);
            local.offset(-c.getLeft() - c.getTranslationX(), -c.getTop() - c.getTranslationY());
            sv.requestTile(local, scale);
        }
    }

    private void clearTiles() {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c instanceof SpreadView) ((SpreadView) c).clearTiles();
        }
    }

    /** Redraws every visible spread (decorations changed). */
    public void invalidatePages() {
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).invalidate();
        invalidate();
    }
}
