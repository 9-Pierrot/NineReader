package com.ninereader.ui.reader;

import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ninereader.annotations.Annotation;
import com.ninereader.annotations.AnnotationStore;
import com.ninereader.reader.view.PageFrame;
import com.ninereader.reader.view.SpreadView;
import com.ninereader.reader.view.ZoomableRecyclerView;

import java.util.List;

/**
 * Touch handling of the annotation tools: free drawing (ANN-03, with stylus support and palm
 * rejection), shapes (ANN-04), eraser, text markup by dragging (ANN-01), selection / move / resize
 * of boxed annotations and placement of notes, text, signatures and images.
 */
public class AnnotationController implements ZoomableRecyclerView.TouchInterceptor {

    /** Active tool. */
    public enum Tool {
        SELECT, HIGHLIGHT, UNDERLINE, STRIKEOUT, NOTE, INK, ERASER, RECT, CIRCLE, LINE, ARROW, FREETEXT, PLACE, WHITEOUT, REDACT
    }

    /** Host callbacks (implemented by the reader activity). */
    public interface Host {
        /** Text markup tool: selection gestures. */
        void onMarkupDown(@NonNull ZoomableRecyclerView.Hit hit);

        void onMarkupMove(@Nullable ZoomableRecyclerView.Hit hit);

        void onMarkupUp(@NonNull Annotation.Type type);

        /** Note / free text tools: a page point was tapped. */
        void onPlaceText(@NonNull Tool tool, int page, float pageX, float pageY);

        /** Placement of the pending image (signature, stamp, picture). */
        void onPlaceImage(int page, float pageX, float pageY);

        void onSelectionChanged(@Nullable Annotation selected);

        void requestRedraw();
    }

    private final ZoomableRecyclerView pages;
    private final AnnotationOverlayView overlay;
    private final AnnotationStore store;
    private final Host host;

    private Tool tool = Tool.SELECT;
    private boolean active;
    private int color = 0xFFE53935;
    private float width = 2.5f;
    private boolean stylusOnly;
    private boolean stylusSeen;

    // Current gesture
    @Nullable
    private SpreadView gestureView;
    @Nullable
    private PageFrame gestureFrame;
    private float[] points = new float[256];
    private int pointCount;
    private float pressureSum;
    private int pressureSamples;
    private boolean gestureActive;
    private float startPageX;
    private float startPageY;
    private final float[] tmp = new float[2];

    // Selection editing
    @Nullable
    private Annotation selected;
    @Nullable
    private Annotation editing;
    private boolean resizing;
    private float lastPageX;
    private float lastPageY;

    public AnnotationController(@NonNull ZoomableRecyclerView pages, @NonNull AnnotationOverlayView overlay,
                                @NonNull AnnotationStore store, @NonNull Host host) {
        this.pages = pages;
        this.overlay = overlay;
        this.store = store;
        this.host = host;
    }

    public void setActive(boolean active) {
        this.active = active;
        pages.setTouchInterceptor(active ? this : null);
        if (!active) {
            select(null);
            overlay.clear();
        }
        overlay.setVisibility(active ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    public boolean isActive() {
        return active;
    }

    public void setTool(@NonNull Tool tool) {
        this.tool = tool;
        if (tool != Tool.SELECT) select(null);
    }

    @NonNull
    public Tool tool() {
        return tool;
    }

    public void setColor(int color) {
        this.color = color;
        if (selected != null) {
            Annotation edited = selected.copy();
            edited.color = color;
            store.update(edited);
            selected = store.find(edited.id);
            host.requestRedraw();
        }
    }

    public int color() {
        return color;
    }

    public void setWidth(float width) {
        this.width = width;
    }

    public float width() {
        return width;
    }

    public void setStylusOnly(boolean stylusOnly) {
        this.stylusOnly = stylusOnly;
    }

    @Nullable
    public Annotation selected() {
        return editing != null ? editing : selected;
    }

    public void select(@Nullable Annotation a) {
        selected = a;
        editing = null;
        host.onSelectionChanged(a);
        host.requestRedraw();
    }

    /** Annotation to draw instead of the stored one while it is being moved/resized. */
    @Nullable
    public Annotation editingCopy() {
        return editing;
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouch(@NonNull MotionEvent ev) {
        if (!active) return false;
        if (ev.getPointerCount() > 1) {
            cancelGesture();
            return false; // pinch zoom / two-finger pan stays available
        }
        int toolType = ev.getToolType(0);
        boolean isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER;
        if (isStylus) stylusSeen = true;
        boolean drawingTool = tool != Tool.SELECT;
        // Palm rejection: once a stylus is used (or in stylus-only mode), fingers scroll instead of drawing.
        if (drawingTool && !isStylus && (stylusOnly || stylusSeen) && toolType == MotionEvent.TOOL_TYPE_FINGER) {
            return false;
        }
        Tool effective = toolType == MotionEvent.TOOL_TYPE_ERASER ? Tool.ERASER : tool;
        switch (effective) {
            case SELECT:
                return onSelectTouch(ev);
            case INK:
                return onInkTouch(ev);
            case ERASER:
                return onEraserTouch(ev);
            case RECT:
            case CIRCLE:
            case LINE:
            case ARROW:
            case WHITEOUT:
            case REDACT:
                return onShapeTouch(ev, effective);
            case HIGHLIGHT:
            case UNDERLINE:
            case STRIKEOUT:
                return onMarkupTouch(ev, effective);
            case NOTE:
            case FREETEXT:
            case PLACE:
                return onTapTool(ev, effective);
            default:
                return false;
        }
    }

    private void cancelGesture() {
        gestureActive = false;
        pointCount = 0;
        overlay.clear();
    }

    private boolean beginOnPage(MotionEvent ev) {
        ZoomableRecyclerView.Hit hit = pages.hitTest(ev.getX(), ev.getY());
        if (hit == null || !hit.hasPagePoint) return false;
        gestureView = hit.view;
        gestureFrame = hit.frame;
        startPageX = hit.pageX;
        startPageY = hit.pageY;
        gestureActive = true;
        return true;
    }

    /** Screen point → page point using the page where the gesture started. */
    private boolean toPage(float x, float y, float[] out) {
        if (gestureView == null || gestureFrame == null) return false;
        float s = pages.getScale();
        float cx = (x - pages.getTx()) / s - gestureView.getLeft() - gestureView.getTranslationX();
        float cy = (y - pages.getTy()) / s - gestureView.getTop() - gestureView.getTranslationY();
        return gestureFrame.viewToPage(cx, cy, out);
    }

    private float screenPxPerPoint() {
        return gestureFrame == null ? 1f : gestureFrame.pageUnitToPx() * pages.getScale();
    }

    private boolean onInkTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!beginOnPage(ev)) return true;
                pointCount = 0;
                pressureSum = 0;
                pressureSamples = 0;
                addPoint(startPageX, startPageY, ev.getPressure());
                overlay.setStyle(color, width * screenPxPerPoint());
                overlay.startPath(ev.getX(), ev.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!gestureActive) return true;
                for (int h = 0; h < ev.getHistorySize(); h++) {
                    if (toPage(ev.getHistoricalX(h), ev.getHistoricalY(h), tmp)) {
                        addPoint(tmp[0], tmp[1], ev.getHistoricalPressure(h));
                        overlay.lineTo(ev.getHistoricalX(h), ev.getHistoricalY(h));
                    }
                }
                if (toPage(ev.getX(), ev.getY(), tmp)) {
                    addPoint(tmp[0], tmp[1], ev.getPressure());
                    overlay.lineTo(ev.getX(), ev.getY());
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (gestureActive && gestureFrame != null && pointCount >= 1) {
                    Annotation a = new Annotation();
                    a.type = Annotation.Type.INK;
                    a.page = gestureFrame.page;
                    a.color = color;
                    float avgPressure = pressureSamples == 0 ? 0.5f : pressureSum / pressureSamples;
                    boolean stylus = ev.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS;
                    // Stylus pressure modulates the stroke width (ANN-03).
                    a.width = stylus ? width * (0.5f + Math.min(1f, avgPressure)) : width;
                    float[] stroke = new float[pointCount * 2];
                    System.arraycopy(points, 0, stroke, 0, pointCount * 2);
                    a.strokes.add(simplify(stroke, 0.4f));
                    store.add(a);
                }
                cancelGesture();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelGesture();
                return true;
            default:
                return true;
        }
    }

    private void addPoint(float x, float y, float pressure) {
        if (pointCount * 2 + 2 > points.length) {
            float[] bigger = new float[points.length * 2];
            System.arraycopy(points, 0, bigger, 0, points.length);
            points = bigger;
        }
        points[pointCount * 2] = x;
        points[pointCount * 2 + 1] = y;
        pointCount++;
        pressureSum += pressure;
        pressureSamples++;
    }

    /** Drops points closer than {@code minDist} to the previous kept point. */
    @NonNull
    static float[] simplify(@NonNull float[] pts, float minDist) {
        if (pts.length <= 4) return pts;
        float[] out = new float[pts.length];
        int n = 0;
        out[n++] = pts[0];
        out[n++] = pts[1];
        for (int i = 2; i + 1 < pts.length; i += 2) {
            float dx = pts[i] - out[n - 2];
            float dy = pts[i + 1] - out[n - 1];
            if (dx * dx + dy * dy >= minDist * minDist || i + 2 >= pts.length) {
                out[n++] = pts[i];
                out[n++] = pts[i + 1];
            }
        }
        float[] res = new float[n];
        System.arraycopy(out, 0, res, 0, n);
        return res;
    }

    private boolean onEraserTouch(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN && !beginOnPage(ev)) return true;
        if (!gestureActive) return true;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            if (gestureFrame != null && toPage(ev.getX(), ev.getY(), tmp)) {
                float tol = 10f / Math.max(0.01f, screenPxPerPoint());
                List<Annotation> onPage = store.forPage(gestureFrame.page);
                for (Annotation a : onPage) {
                    if (a.hitTest(tmp[0], tmp[1], tol)) store.remove(a.id);
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            cancelGesture();
        }
        return true;
    }

    private boolean onShapeTouch(MotionEvent ev, Tool t) {
        Annotation.Type type = Annotation.Type.valueOf(t.name());
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!beginOnPage(ev)) return true;
                overlay.setStyle(color, width * screenPxPerPoint());
                overlay.startShape(type, ev.getX(), ev.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (gestureActive) overlay.updateShape(ev.getX(), ev.getY());
                return true;
            case MotionEvent.ACTION_UP:
                if (gestureActive && gestureFrame != null && toPage(ev.getX(), ev.getY(), tmp)) {
                    float ex = tmp[0];
                    float ey = tmp[1];
                    if (Math.hypot(ex - startPageX, ey - startPageY) > 3) {
                        Annotation a = new Annotation();
                        a.type = type;
                        a.page = gestureFrame.page;
                        a.color = type == Annotation.Type.WHITEOUT ? 0xFFFFFFFF : type == Annotation.Type.REDACT ? 0xFF000000 : color;
                        a.width = width;
                        if (type.isLine()) {
                            a.bounds = new float[]{startPageX, startPageY, ex, ey};
                        } else {
                            a.bounds = new float[]{Math.min(startPageX, ex), Math.min(startPageY, ey),
                                    Math.max(startPageX, ex), Math.max(startPageY, ey)};
                        }
                        store.add(a);
                    }
                }
                cancelGesture();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelGesture();
                return true;
            default:
                return true;
        }
    }

    private boolean onMarkupTouch(MotionEvent ev, Tool t) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                ZoomableRecyclerView.Hit hit = pages.hitTest(ev.getX(), ev.getY());
                if (hit == null) return true;
                gestureActive = true;
                host.onMarkupDown(hit);
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (gestureActive) host.onMarkupMove(pages.hitTest(ev.getX(), ev.getY()));
                return true;
            case MotionEvent.ACTION_UP:
                if (gestureActive) host.onMarkupUp(Annotation.Type.valueOf(t.name()));
                gestureActive = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                gestureActive = false;
                return true;
            default:
                return true;
        }
    }

    private boolean onTapTool(MotionEvent ev, Tool t) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureActive = beginOnPage(ev);
                lastPageX = ev.getX();
                lastPageY = ev.getY();
                return true;
            case MotionEvent.ACTION_UP:
                if (gestureActive && gestureFrame != null
                        && Math.hypot(ev.getX() - lastPageX, ev.getY() - lastPageY) < 24 * pages.getResources().getDisplayMetrics().density) {
                    if (t == Tool.PLACE) {
                        host.onPlaceImage(gestureFrame.page, startPageX, startPageY);
                    } else {
                        host.onPlaceText(t, gestureFrame.page, startPageX, startPageY);
                    }
                }
                gestureActive = false;
                return true;
            default:
                return true;
        }
    }

    private boolean onSelectTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                ZoomableRecyclerView.Hit hit = pages.hitTest(ev.getX(), ev.getY());
                if (hit == null || !hit.hasPagePoint) return false;
                gestureView = hit.view;
                gestureFrame = hit.frame;
                float tol = 12f / Math.max(0.01f, screenPxPerPoint());
                // Resize handle of the current selection?
                if (selected != null && selected.page == hit.frame.page && selected.type.isBoxed()) {
                    float[] bb = selected.boundingBox();
                    float handleTol = 24f / Math.max(0.01f, screenPxPerPoint());
                    if (Math.hypot(hit.pageX - bb[2], hit.pageY - bb[1]) <= handleTol) {
                        editing = selected.copy();
                        resizing = true;
                        gestureActive = true;
                        lastPageX = hit.pageX;
                        lastPageY = hit.pageY;
                        return true;
                    }
                }
                Annotation found = null;
                List<Annotation> onPage = store.forPage(hit.frame.page);
                for (int i = onPage.size() - 1; i >= 0; i--) {
                    if (onPage.get(i).hitTest(hit.pageX, hit.pageY, tol)) {
                        found = onPage.get(i);
                        break;
                    }
                }
                if (found == null) {
                    if (selected != null) select(null);
                    return false; // let the page scroll / tap normally
                }
                if (selected == null || !selected.id.equals(found.id)) select(found);
                editing = found.copy();
                resizing = false;
                gestureActive = true;
                lastPageX = hit.pageX;
                lastPageY = hit.pageY;
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (!gestureActive || editing == null) return false;
                if (toPage(ev.getX(), ev.getY(), tmp)) {
                    float dx = tmp[0] - lastPageX;
                    float dy = tmp[1] - lastPageY;
                    if (resizing && editing.bounds != null) {
                        resize(editing, dx, dy);
                    } else {
                        editing.translate(dx, dy);
                    }
                    lastPageX = tmp[0];
                    lastPageY = tmp[1];
                    host.requestRedraw();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (gestureActive && editing != null && selected != null) {
                    boolean moved = !java.util.Arrays.equals(editing.boundingBox(), selected.boundingBox());
                    if (moved) {
                        store.update(editing);
                        selected = store.find(editing.id);
                    }
                }
                editing = null;
                resizing = false;
                gestureActive = false;
                host.requestRedraw();
                return true;
            default:
                return gestureActive;
        }
    }

    /** Resizes a boxed annotation by dragging its bottom-right corner (keeps image aspect ratio). */
    static void resize(@NonNull Annotation a, float dx, float dy) {
        float[] b = a.bounds;
        if (b == null) return;
        float l = Math.min(b[0], b[2]);
        float r = Math.max(b[0], b[2]);
        float bottom = Math.min(b[1], b[3]);
        float t = Math.max(b[1], b[3]);
        float newR = Math.max(l + 8, r + dx);
        float newB = Math.min(t - 8, bottom + dy);
        if (a.type == Annotation.Type.IMAGE) {
            float aspect = (r - l) / Math.max(1f, t - bottom);
            float w = newR - l;
            newB = t - w / aspect;
        }
        a.bounds = new float[]{l, newB, newR, t};
        if (a.type == Annotation.Type.FREETEXT && r - l > 0) {
            a.fontSize = Math.max(4f, a.fontSize * (newR - l) / (r - l));
        }
    }
}
