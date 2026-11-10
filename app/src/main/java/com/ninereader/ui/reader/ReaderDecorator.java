package com.ninereader.ui.reader;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ninereader.annotations.Annotation;
import com.ninereader.annotations.AnnotationPainter;
import com.ninereader.annotations.AnnotationStore;
import com.ninereader.annotations.FormField;
import com.ninereader.reader.view.PageDecorator;
import com.ninereader.reader.view.PageFrame;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Draws everything the reader shows on top of rendered pages: pending annotations, search hits,
 * the text selection, the sentence being read aloud and form fields with pending values.
 */
public class ReaderDecorator implements PageDecorator {

    private final AnnotationPainter painter;
    private final Paint searchPaint = new Paint();
    private final Paint currentPaint = new Paint();
    private final Paint selectionPaint = new Paint();
    private final Paint ttsPaint = new Paint();
    private final Paint fieldPaint = new Paint();
    private final Paint fieldFill = new Paint();
    private final Paint fieldText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockFill = new Paint();
    /** Edit mode: editable text blocks to outline, and the key of the block being edited. */
    @Nullable
    private java.util.function.IntFunction<java.util.List<com.ninereader.edit.TextBlock>> editBlocks;
    @Nullable
    private java.util.function.Predicate<com.ninereader.edit.TextBlock> blockHidden;
    private final RectF tmp = new RectF();
    private final RectF view = new RectF();

    @Nullable
    private AnnotationStore store;
    @Nullable
    private AnnotationController controller;
    /** Edit mode: annotation drawn instead of its stored version (block being moved). */
    @Nullable
    private Annotation editOverride;
    /** Search hits: page → rects (page coordinates). */
    private int searchPage = -1;
    private List<RectF> searchRects = Collections.emptyList();
    @Nullable
    private TextSelection selection;
    private int ttsPage = -1;
    private List<RectF> ttsRects = Collections.emptyList();
    private List<FormField> fields = Collections.emptyList();
    private boolean showFields;

    public ReaderDecorator(float density) {
        painter = new AnnotationPainter(density);
        blockPaint.setStyle(Paint.Style.STROKE);
        blockPaint.setStrokeWidth(Math.max(1f, density));
        blockPaint.setColor(0xAA3D7BE0);
        blockPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{5 * density, 3 * density}, 0));
        blockFill.setColor(0x123D7BE0);
        searchPaint.setColor(0x66FFC107);
        currentPaint.setColor(0x99FF5722);
        selectionPaint.setColor(0x553D7BE0);
        ttsPaint.setColor(0x5534A853);
        fieldPaint.setColor(0x553D7BE0);
        fieldPaint.setStyle(Paint.Style.STROKE);
        fieldPaint.setStrokeWidth(density);
        fieldFill.setColor(0x1A3D7BE0);
        fieldText.setColor(0xFF0D47A1);
    }

    @NonNull
    public AnnotationPainter painter() {
        return painter;
    }

    public void setStore(@Nullable AnnotationStore store, @Nullable AnnotationController controller) {
        this.store = store;
        this.controller = controller;
    }

    public void setSearchHit(int page, @NonNull List<RectF> rects) {
        searchPage = page;
        searchRects = rects;
    }

    public void clearSearch() {
        searchPage = -1;
        searchRects = Collections.emptyList();
    }

    public void setSelection(@Nullable TextSelection selection) {
        this.selection = selection;
    }

    public void setTts(int page, @NonNull List<RectF> rects) {
        ttsPage = page;
        ttsRects = rects;
    }

    public void setEditOverride(@Nullable Annotation annotation) {
        this.editOverride = annotation;
    }

    /** Shows the editable text blocks (edit mode), or hides them with null. */
    public void setEditBlocks(@Nullable java.util.function.IntFunction<java.util.List<com.ninereader.edit.TextBlock>> blocks,
                              @Nullable java.util.function.Predicate<com.ninereader.edit.TextBlock> hidden) {
        this.editBlocks = blocks;
        this.blockHidden = hidden;
    }

    public void setFields(@NonNull List<FormField> fields, boolean show) {
        this.fields = fields;
        this.showFields = show;
    }

    @Override
    public void decorate(@NonNull Canvas c, @NonNull PageFrame f) {
        // Form fields (SIG-01)
        if (showFields && !fields.isEmpty()) {
            Map<String, String> pending = store == null ? Collections.emptyMap() : store.fields();
            for (FormField field : fields) {
                String value = pending.get(field.name);
                for (FormField.Widget w : field.widgets) {
                    if (w.page != f.page) continue;
                    tmp.set(w.rect[0], w.rect[3], w.rect[2], w.rect[1]);
                    if (!f.pageToView(tmp, view)) continue;
                    if (value == null) {
                        c.drawRect(view, fieldFill);
                        c.drawRect(view, fieldPaint);
                    } else {
                        drawPendingValue(c, field, w, value, view);
                    }
                }
            }
        }
        // Search hits (LEC-07)
        if (f.page == searchPage) {
            for (RectF r : searchRects) {
                tmp.set(r);
                if (f.pageToView(tmp, view)) c.drawRect(view, currentPaint);
            }
        }
        // Read aloud (LEC-13)
        if (f.page == ttsPage) {
            for (RectF r : ttsRects) {
                tmp.set(r);
                if (f.pageToView(tmp, view)) c.drawRect(view, ttsPaint);
            }
        }
        // Pending annotations
        if (store != null) {
            Annotation editing = controller == null ? null : controller.editingCopy();
            Annotation selected = controller == null ? null : controller.selected();
            for (Annotation a : store.all()) {
                if (a.page != f.page) continue;
                if (editing != null && editing.id.equals(a.id)) continue;
                if (editOverride != null && editOverride.id.equals(a.id)) continue;
                painter.draw(c, f, a, selected != null && selected.id.equals(a.id));
            }
            if (editing != null && editing.page == f.page) painter.draw(c, f, editing, true);
            if (editOverride != null && editOverride.page == f.page) painter.draw(c, f, editOverride, true);
        }
        // Editable text blocks (edit mode, MOD-02)
        if (editBlocks != null) {
            java.util.List<com.ninereader.edit.TextBlock> blocks = editBlocks.apply(f.page);
            if (blocks != null) {
                for (com.ninereader.edit.TextBlock b : blocks) {
                    if (blockHidden != null && blockHidden.test(b)) continue;
                    tmp.set(b.bounds[0], b.bounds[3], b.bounds[2], b.bounds[1]);
                    if (!f.pageToView(tmp, view)) continue;
                    view.inset(-2, -2);
                    c.drawRect(view, blockFill);
                    c.drawRect(view, blockPaint);
                }
            }
        }
        // Text selection (LEC-11)
        TextSelection sel = selection;
        if (sel != null && sel.page == f.page) {
            for (RectF r : sel.rects) {
                tmp.set(r);
                if (f.pageToView(tmp, view)) c.drawRect(view, selectionPaint);
            }
        }
    }

    private void drawPendingValue(Canvas c, FormField field, FormField.Widget w, String value, RectF r) {
        c.drawRect(r, fieldFill);
        switch (field.kind) {
            case CHECKBOX:
                if ("true".equals(value)) drawCheck(c, r);
                break;
            case RADIO:
                if (value.equals(w.exportValue)) {
                    fieldText.setStyle(Paint.Style.FILL);
                    c.drawCircle(r.centerX(), r.centerY(), Math.min(r.width(), r.height()) * 0.3f, fieldText);
                }
                break;
            default:
                fieldText.setStyle(Paint.Style.FILL);
                fieldText.setTextSize(Math.max(8f, Math.min(r.height() * 0.7f, 40f)));
                c.save();
                c.clipRect(r);
                c.drawText(value.replace('\n', ' '), r.left + 4, r.centerY() + fieldText.getTextSize() * 0.35f, fieldText);
                c.restore();
                break;
        }
        c.drawRect(r, fieldPaint);
    }

    private void drawCheck(Canvas c, RectF r) {
        fieldText.setStyle(Paint.Style.STROKE);
        fieldText.setStrokeWidth(Math.max(2f, r.width() * 0.12f));
        c.drawLine(r.left + r.width() * 0.2f, r.centerY(), r.left + r.width() * 0.42f, r.bottom - r.height() * 0.2f, fieldText);
        c.drawLine(r.left + r.width() * 0.42f, r.bottom - r.height() * 0.2f, r.right - r.width() * 0.15f, r.top + r.height() * 0.2f, fieldText);
        fieldText.setStyle(Paint.Style.FILL);
    }

    /** Form field under a page point, or null. */
    @Nullable
    public FormField fieldAt(int page, float x, float y) {
        for (FormField field : fields) {
            for (FormField.Widget w : field.widgets) {
                if (w.page != page) continue;
                float l = Math.min(w.rect[0], w.rect[2]);
                float r = Math.max(w.rect[0], w.rect[2]);
                float b = Math.min(w.rect[1], w.rect[3]);
                float t = Math.max(w.rect[1], w.rect[3]);
                if (x >= l && x <= r && y >= b && y <= t) return field;
            }
        }
        return null;
    }

    /** Widget of a field under a page point (radio buttons). */
    @Nullable
    public FormField.Widget widgetAt(@NonNull FormField field, int page, float x, float y) {
        for (FormField.Widget w : field.widgets) {
            if (w.page != page) continue;
            float l = Math.min(w.rect[0], w.rect[2]);
            float r = Math.max(w.rect[0], w.rect[2]);
            float b = Math.min(w.rect[1], w.rect[3]);
            float t = Math.max(w.rect[1], w.rect[3]);
            if (x >= l && x <= r && y >= b && y <= t) return w;
        }
        return null;
    }

    public boolean hasFields() {
        return !fields.isEmpty();
    }
}
