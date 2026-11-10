package com.ninereader.ui.reader;

import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.ninereader.R;
import com.ninereader.annotations.Annotation;
import com.ninereader.annotations.AnnotationStore;
import com.ninereader.databinding.BarEditBinding;
import com.ninereader.edit.EditFonts;
import com.ninereader.edit.PdfTextEditor;
import com.ninereader.edit.TextBlock;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.view.PageAdapter;
import com.ninereader.reader.view.PageFrame;
import com.ninereader.reader.view.SpreadView;
import com.ninereader.reader.view.ZoomableRecyclerView;

import java.util.List;
import java.util.Locale;

/**
 * "Modifier" mode of the reader (MOD-01, MOD-02), like the "Edit PDF" mode of desktop editors: text
 * blocks of the page are outlined; tapping one opens an inline editor placed exactly over it, with
 * its font, size and colour; changes are visible immediately and written into the PDF on save
 * (original glyphs removed, new text embedded). Long press and drag moves a block; "Add text"
 * creates a new text box.
 */
class EditModeUi {

    /** What is being edited. */
    static final class Target {
        int page;
        @Nullable
        TextBlock block;
        @Nullable
        String annotationId;
        boolean isNew;
        float[] bounds;
        float fontSize;
        float lineHeight;
        EditFonts.Family family = EditFonts.Family.SANS;
        boolean bold;
        boolean italic;
        int color = 0xFF000000;
        /** One-line text (title, label, new box): the box widens with the text instead of wrapping. */
        boolean singleLine;
        /** Right edge the box never shrinks below (original extent). */
        float minRight;
    }

    private final ReaderActivity a;
    private final TextEditSession session;
    private final BarEditBinding bar;
    private final EditText editor;
    private boolean active;
    private boolean armAddText;
    @Nullable
    private Target target;
    @Nullable
    private Annotation moving;
    private boolean movingIsNew;
    @Nullable
    private String movingBlockKey;
    private float lastX;
    private float lastY;

    EditModeUi(@NonNull ReaderActivity activity) {
        this.a = activity;
        this.session = activity.vm.textEdits();
        this.editor = activity.b.inlineEditor;
        bar = BarEditBinding.inflate(LayoutInflater.from(activity), activity.b.contextualBar, false);
        AnnotationStore store = activity.vm.editor().store();
        editor.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (target != null && target.singleLine) positionEditor();
            }
        });
        // Keyboard shown or hidden: keep the text being edited visible.
        activity.b.bottomArea.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            if (bt - t != ob - ot) editor.post(this::ensureEditorVisible);
        });
        bar.editAddText.setOnClickListener(v -> {
            commit();
            armAddText = !armAddText;
            bar.editAddText.setChecked(armAddText);
            if (armAddText) Toast.makeText(a, R.string.tap_to_place_text, Toast.LENGTH_SHORT).show();
        });
        bar.editAddText.setCheckable(true);
        bar.editAddImage.setOnClickListener(v -> {
            exit();
            if (a.annotationUi != null) a.annotationUi.enter();
            a.pickImage.launch("image/*");
        });
        bar.editUndo.setOnClickListener(v -> {
            commit();
            store.undo();
        });
        bar.editRedo.setOnClickListener(v -> {
            commit();
            store.redo();
        });
        bar.editSave.setOnClickListener(v -> {
            commit();
            a.askSave(false);
        });
        bar.editDone.setOnClickListener(v -> exit());
        bar.editCommit.setOnClickListener(v -> commit());
        bar.editDelete.setOnClickListener(v -> deleteCurrent());
        bar.editSmaller.setOnClickListener(v -> changeSize(-1));
        bar.editBigger.setOnClickListener(v -> changeSize(1));
        bar.editBold.setOnClickListener(v -> {
            if (target != null) {
                target.bold = !target.bold;
                positionEditor();
            }
        });
        bar.editItalic.setOnClickListener(v -> {
            if (target != null) {
                target.italic = !target.italic;
                positionEditor();
            }
        });
        bar.editFont.setOnClickListener(this::chooseFamily);
        bar.editColor.setOnClickListener(v -> chooseColor());
        session.version().observe(activity, v -> a.b.pages.invalidatePages());
        session.busy().observe(activity, busy -> bar.editBusy.setVisibility(Boolean.TRUE.equals(busy) ? View.VISIBLE : View.GONE));
        session.recognizedPage().observe(activity, page -> {
            if (active && page != null && page >= 0) {
                Toast.makeText(a, a.getString(R.string.text_recognized_ocr, page + 1), Toast.LENGTH_SHORT).show();
            }
        });
    }

    boolean isActive() {
        return active;
    }

    void enter() {
        if (active) return;
        if (a.annotationUi != null && a.annotationUi.isActive()) a.annotationUi.exit();
        a.vm.clearSelection();
        active = true;
        a.b.contextualBar.removeAllViews();
        a.b.contextualBar.addView(bar.getRoot());
        a.decorator.setEditBlocks(session::blocks, this::isHidden);
        onPageVisible(a.vm.page());
        a.setBarsVisible(true, true);
        a.b.pages.invalidatePages();
        Toast.makeText(a, R.string.edit_mode_hint, Toast.LENGTH_LONG).show();
    }

    void exit() {
        if (!active) return;
        commit();
        active = false;
        armAddText = false;
        bar.editAddText.setChecked(false);
        a.b.contextualBar.removeView(bar.getRoot());
        a.decorator.setEditBlocks(null, null);
        a.setBarsVisible(true, true);
        a.b.pages.invalidatePages();
        if (a.vm.editor().hasPendingChanges()) {
            Snackbar.make(a.b.root, R.string.annotations_pending, Snackbar.LENGTH_LONG)
                    .setAction(R.string.save, v -> a.askSave(false))
                    .show();
        }
    }

    /** Detects the blocks of the visible page (and the next one for double page / prefetch). */
    void onPageVisible(int page) {
        if (!active) return;
        session.request(page);
        if (page + 1 < a.vm.pageCount()) session.request(page + 1);
    }

    // ------------------------------------------------------------------ gestures

    void onTap(@Nullable ZoomableRecyclerView.Hit hit) {
        if (target != null) {
            commit();
            return;
        }
        if (hit == null || !hit.hasPagePoint) return;
        int page = hit.frame.page;
        float x = hit.pageX;
        float y = hit.pageY;
        if (armAddText) {
            armAddText = false;
            bar.editAddText.setChecked(false);
            beginNewBox(page, x, y);
            return;
        }
        float tol = 4f / Math.max(0.05f, hit.frame.pageUnitToPx() * a.b.pages.getScale());
        Annotation edited = editAt(page, x, y);
        if (edited != null) {
            beginFromAnnotation(edited);
            return;
        }
        List<TextBlock> blocks = session.blocks(page);
        if (blocks == null) {
            session.request(page);
            Toast.makeText(a, R.string.edit_analyzing, Toast.LENGTH_SHORT).show();
            return;
        }
        for (TextBlock b : blocks) {
            if (!isEdited(b) && b.contains(x, y, tol)) {
                beginFromBlock(b);
                return;
            }
        }
        Toast.makeText(a, blocks.isEmpty() ? R.string.edit_no_text : R.string.edit_tap_hint, Toast.LENGTH_SHORT).show();
    }

    /** Long press starts moving an edited block or an original one. */
    void onLongPress(@NonNull ZoomableRecyclerView.Hit hit) {
        commit();
        if (!hit.hasPagePoint) return;
        int page = hit.frame.page;
        Annotation edited = editAt(page, hit.pageX, hit.pageY);
        if (edited != null) {
            moving = edited.copy();
            movingIsNew = false;
        } else {
            List<TextBlock> blocks = session.blocks(page);
            if (blocks == null) return;
            TextBlock found = null;
            for (TextBlock b : blocks) {
                if (!isEdited(b) && b.contains(hit.pageX, hit.pageY, 2f)) found = b;
            }
            if (found == null) return;
            moving = fromBlock(found, found.text);
            movingIsNew = true;
            movingBlockKey = found.key();
        }
        lastX = hit.pageX;
        lastY = hit.pageY;
        a.decorator.setEditOverride(moving);
        a.b.pages.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        a.b.pages.invalidatePages();
    }

    void onLongPressDrag(@Nullable ZoomableRecyclerView.Hit hit) {
        Annotation m = moving;
        if (m == null || hit == null || !hit.hasPagePoint || hit.frame.page != m.page) return;
        if (m.bounds != null) {
            float dx = hit.pageX - lastX;
            float dy = hit.pageY - lastY;
            m.bounds[0] += dx;
            m.bounds[2] += dx;
            m.bounds[1] += dy;
            m.bounds[3] += dy;
        }
        lastX = hit.pageX;
        lastY = hit.pageY;
        a.b.pages.invalidatePages();
    }

    void onLongPressEnd() {
        Annotation m = moving;
        moving = null;
        movingBlockKey = null;
        a.decorator.setEditOverride(null);
        if (m == null) return;
        AnnotationStore store = a.vm.editor().store();
        if (movingIsNew) {
            store.add(m);
        } else {
            store.update(m);
        }
        a.b.pages.invalidatePages();
    }

    /** Keeps the inline editor on its block while the page scrolls or zooms. */
    void onViewportChanged() {
        if (target != null && !positionEditor()) commit();
    }

    // ------------------------------------------------------------------ editing

    private void beginFromBlock(@NonNull TextBlock b) {
        Target t = new Target();
        t.page = b.page;
        t.block = b;
        t.bounds = b.bounds.clone();
        t.singleLine = b.text.indexOf('\n') < 0 && b.bounds[3] - b.bounds[1] < b.lineHeight * 1.6f;
        t.minRight = b.bounds[2];
        t.fontSize = b.fontSize;
        t.lineHeight = b.lineHeight;
        t.family = b.family;
        t.bold = b.bold;
        t.italic = b.italic;
        t.color = b.color;
        begin(t, b.text);
    }

    private void beginFromAnnotation(@NonNull Annotation an) {
        Target t = new Target();
        t.page = an.page;
        t.annotationId = an.id;
        t.bounds = an.bounds == null ? new float[]{0, 0, 100, 20} : an.bounds.clone();
        float lh = PdfTextEditor.lineHeightOf(an);
        t.singleLine = (an.text == null || an.text.indexOf('\n') < 0) && t.bounds[3] - t.bounds[1] < lh * 1.6f;
        t.minRight = an.originalBounds != null ? an.originalBounds[2] : t.bounds[0] + 40f;
        t.fontSize = an.fontSize;
        t.lineHeight = PdfTextEditor.lineHeightOf(an);
        t.family = PdfTextEditor.familyOf(an);
        t.bold = an.bold;
        t.italic = an.italic;
        t.color = an.color;
        begin(t, an.text == null ? "" : an.text);
    }

    private void beginNewBox(int page, float x, float y) {
        PdfEngine e = a.vm.engine();
        float pageW = e != null && e.isMeasured(page) ? e.pageWidth(page) : 595f;
        Target t = new Target();
        t.singleLine = true;
        t.minRight = Math.min(pageW, x + 40f);
        t.page = page;
        t.isNew = true;
        t.fontSize = 12f;
        t.lineHeight = 12f * TextBlock.DEFAULT_LEADING;
        t.bounds = new float[]{x, y - 12f * 1.3f, t.minRight, y};
        begin(t, "");
    }

    private void begin(@NonNull Target t, @NonNull String text) {
        target = t;
        editor.setText(text);
        if (!positionEditor()) {
            target = null;
            return;
        }
        editor.setVisibility(View.VISIBLE);
        editor.requestFocus();
        editor.setSelection(editor.getText().length());
        InputMethodManager imm = a.getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        bar.editFormat.setVisibility(View.VISIBLE);
        updateFormatUi();
        a.b.pages.invalidatePages();
        editor.postDelayed(this::ensureEditorVisible, 400);
    }

    /** One-line text: the right edge follows the typed text, up to the page margin (like Acrobat). */
    private void fitWidth(@NonNull Target t) {
        if (!t.singleLine) return;
        String text = editor.getText().toString();
        if (text.indexOf('\n') >= 0) {
            t.singleLine = false; // the user broke the line: keep the current width and wrap from now on
            return;
        }
        Paint p = new Paint();
        p.setTypeface(EditFonts.typeface(a, t.family, t.bold, t.italic));
        p.setTextSize(t.fontSize);
        float needed = t.bounds[0] + p.measureText(text) + t.fontSize * 0.3f;
        PdfEngine e = a.vm.engine();
        float pageW = e != null && e.isMeasured(t.page) ? e.pageWidth(t.page) : 595f;
        float max = Math.max(t.minRight, pageW - 18f);
        t.bounds[2] = Math.min(max, Math.max(t.minRight, needed));
    }

    /** Places the editor over the block, with the block's typography at the current zoom. */
    private boolean positionEditor() {
        Target t = target;
        PageAdapter adapter = a.adapter;
        if (t == null || adapter == null) return false;
        fitWidth(t);
        RecyclerView.ViewHolder vh = a.b.pages.findViewHolderForAdapterPosition(adapter.spreads().spreadOf(t.page));
        if (vh == null || !(vh.itemView instanceof SpreadView)) return false;
        SpreadView sv = (SpreadView) vh.itemView;
        PageFrame f = sv.frameForPage(t.page);
        if (f == null) return false;
        RectF r = new RectF();
        if (!f.pageToView(new RectF(t.bounds[0], t.bounds[3], t.bounds[2], t.bounds[1]), r)) return false;
        float[] p = new float[2];
        a.b.pages.spreadToScreen(sv, r.left, r.top, p);
        float scale = a.b.pages.getScale();
        int[] pagesLoc = new int[2];
        int[] layerLoc = new int[2];
        a.b.pages.getLocationInWindow(pagesLoc);
        a.b.editLayer.getLocationInWindow(layerLoc);
        float x = pagesLoc[0] - layerLoc[0] + p[0];
        float y = pagesLoc[1] - layerLoc[1] + p[1];
        float density = a.getResources().getDisplayMetrics().density;
        int width = Math.max(Math.round(r.width() * scale), Math.round(96 * density));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) editor.getLayoutParams();
        if (lp.width != width) {
            lp.width = width;
            editor.setLayoutParams(lp);
        }
        editor.setMinHeight(Math.round(r.height() * scale));
        editor.setX(x);
        editor.setY(y);
        editor.setTag(R.id.tag_editor_unit, f.pageUnitToPx() * scale);
        applyStyleToEditor();
        return true;
    }

    /** Live typography of the editor (size, family, weight, style, colour). */
    private void applyStyleToEditor() {
        Target t = target;
        if (t == null) return;
        Object unitTag = editor.getTag(R.id.tag_editor_unit);
        float unit = unitTag instanceof Float ? (Float) unitTag : 1f;
        float size = Math.max(6f, t.fontSize * unit);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        editor.setTypeface(EditFonts.typeface(a, t.family, t.bold, t.italic));
        editor.setTextColor(t.color | 0xFF000000);
        Paint.FontMetrics fm = editor.getPaint().getFontMetrics();
        float natural = fm.descent - fm.ascent;
        editor.setLineSpacing(Math.max(t.lineHeight, t.fontSize) * unit - natural, 1f);
        updateFormatUi();
    }

    private void updateFormatUi() {
        Target t = target;
        if (t == null) return;
        bar.editSize.setText(String.format(Locale.getDefault(), t.fontSize < 10 ? "%.1f" : "%.0f", t.fontSize));
        bar.editBold.setChecked(t.bold);
        bar.editItalic.setChecked(t.italic);
        int label;
        switch (t.family) {
            case SERIF:
                label = R.string.font_serif;
                break;
            case MONO:
                label = R.string.font_mono;
                break;
            default:
                label = R.string.font_sans;
                break;
        }
        bar.editFont.setText(label);
    }

    /** Scrolls the page so that the editor is not hidden by the keyboard. */
    private void ensureEditorVisible() {
        if (target == null || editor.getVisibility() != View.VISIBLE) return;
        if (a.b.pages.isHorizontalMode()) return;
        // Room to scroll the last page above the keyboard and the bars.
        a.b.pages.setContentBottomInset(a.b.bottomArea.getHeight());
        int[] loc = new int[2];
        editor.getLocationOnScreen(loc);
        int[] barLoc = new int[2];
        a.b.bottomArea.getLocationOnScreen(barLoc);
        int[] pagesLoc = new int[2];
        a.b.pages.getLocationOnScreen(pagesLoc);
        int limit = barLoc[1] - Math.round(8 * a.getResources().getDisplayMetrics().density);
        int room = limit - pagesLoc[1] - a.b.topBar.getHeight();
        int bottom = loc[1] + Math.min(editor.getHeight(), Math.max(1, room / 2));
        if (bottom > limit) {
            int dy = Math.round((bottom - limit) / a.b.pages.getScale());
            // After the padding change has been laid out.
            a.b.pages.post(() -> {
                a.b.pages.scrollBy(0, dy);
                positionEditor();
            });
        }
    }

    private void changeSize(int direction) {
        Target t = target;
        if (t == null) return;
        float step = t.fontSize < 10 ? 0.5f : 1f;
        float old = t.fontSize;
        t.fontSize = Math.max(4f, Math.min(144f, t.fontSize + direction * step));
        t.lineHeight = t.lineHeight * t.fontSize / old; // keep the paragraph's relative spacing
        positionEditor();
    }

    private void chooseFamily(View anchor) {
        PopupMenu menu = new PopupMenu(a, anchor);
        menu.getMenu().add(0, 0, 0, R.string.font_sans);
        menu.getMenu().add(0, 1, 1, R.string.font_serif);
        menu.getMenu().add(0, 2, 2, R.string.font_mono);
        menu.setOnMenuItemClickListener(item -> {
            if (target != null) {
                target.family = EditFonts.Family.values()[item.getItemId()];
                positionEditor();
            }
            return true;
        });
        menu.show();
    }

    private void chooseColor() {
        if (target == null) return;
        int[] colors = {0xFF000000, 0xFF424242, 0xFF1565C0, 0xFFC62828, 0xFF2E7D32, 0xFF6A1B9A, 0xFFEF6C00};
        String[] names = a.getResources().getStringArray(R.array.text_color_names);
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.text_color)
                .setItems(names, (d, which) -> {
                    if (target != null) {
                        target.color = colors[which];
                        positionEditor();
                    }
                })
                .show();
    }

    private void deleteCurrent() {
        Target t = target;
        if (t == null) return;
        AnnotationStore store = a.vm.editor().store();
        if (t.annotationId != null) {
            Annotation cur = store.find(t.annotationId);
            if (cur != null) {
                if (cur.originalBounds != null) {
                    Annotation e = cur.copy();
                    e.text = "";
                    store.update(e);
                } else {
                    store.remove(cur.id);
                }
            }
        } else if (t.block != null) {
            store.add(fromBlock(t.block, ""));
        }
        target = null;
        hideEditor();
    }

    /** Validates the current edit (tap outside, scroll away, "done"). */
    void commit() {
        Target t = target;
        if (t == null) return;
        target = null;
        String text = editor.getText().toString();
        hideEditor();
        AnnotationStore store = a.vm.editor().store();
        if (t.annotationId != null) {
            Annotation cur = store.find(t.annotationId);
            if (cur != null) {
                Annotation e = cur.copy();
                applyTarget(e, t, text);
                if (!sameContent(cur, e)) store.update(e);
            }
        } else if (t.isNew) {
            if (!text.trim().isEmpty()) {
                Annotation an = new Annotation();
                an.type = Annotation.Type.TEXT_EDIT;
                an.page = t.page;
                applyTarget(an, t, text);
                store.add(an);
            }
        } else if (t.block != null) {
            TextBlock b = t.block;
            boolean unchanged = text.equals(b.text) && t.fontSize == b.fontSize && t.family == b.family && t.bold == b.bold
                    && t.italic == b.italic && t.color == b.color;
            if (!unchanged) {
                Annotation an = fromBlock(b, text);
                applyTarget(an, t, text);
                store.add(an);
            }
        }
        a.b.pages.invalidatePages();
    }

    private void hideEditor() {
        InputMethodManager imm = a.getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        editor.clearFocus();
        editor.setVisibility(View.GONE);
        a.b.pages.setContentBottomInset(0);
        bar.editFormat.setVisibility(View.GONE);
    }

    @NonNull
    private static Annotation fromBlock(@NonNull TextBlock b, @NonNull String text) {
        Annotation an = new Annotation();
        an.type = Annotation.Type.TEXT_EDIT;
        an.page = b.page;
        an.originalBounds = b.bounds.clone();
        an.bounds = b.bounds.clone();
        an.originalText = b.text;
        an.text = text;
        an.fontSize = b.fontSize;
        an.lineHeight = b.lineHeight;
        an.fontFamily = b.family.name();
        an.bold = b.bold;
        an.italic = b.italic;
        an.color = b.color;
        an.fromOcr = b.fromOcr;
        return an;
    }

    private static void applyTarget(@NonNull Annotation an, @NonNull Target t, @NonNull String text) {
        an.text = text;
        an.fontSize = t.fontSize;
        an.lineHeight = t.lineHeight;
        an.fontFamily = t.family.name();
        an.bold = t.bold;
        an.italic = t.italic;
        an.color = t.color;
        an.bounds = t.bounds.clone();
    }

    private static boolean sameContent(@NonNull Annotation x, @NonNull Annotation y) {
        return java.util.Objects.equals(x.text, y.text) && x.fontSize == y.fontSize && java.util.Objects.equals(x.fontFamily, y.fontFamily)
                && x.bold == y.bold && x.italic == y.italic && x.color == y.color;
    }

    // ------------------------------------------------------------------ lookups

    @Nullable
    private Annotation editAt(int page, float x, float y) {
        List<Annotation> onPage = a.vm.editor().store().forPage(page);
        for (int i = onPage.size() - 1; i >= 0; i--) {
            Annotation an = onPage.get(i);
            if (an.type != Annotation.Type.TEXT_EDIT || an.bounds == null) continue;
            float[] b = an.bounds;
            float extra = an.fontSize; // rendered text may overflow the box downwards
            if (x >= b[0] - 2 && x <= b[2] + 2 && y >= b[1] - extra && y <= b[3] + 2) return an;
        }
        return null;
    }

    /** Whether an original block was already replaced by an edit. */
    boolean isEdited(@NonNull TextBlock b) {
        String key = b.key();
        for (Annotation an : a.vm.editor().store().forPage(b.page)) {
            if (an.type == Annotation.Type.TEXT_EDIT && an.originalBounds != null && key.equals(keyOf(an.page, an.originalBounds))) {
                return true;
            }
        }
        return false;
    }

    /** Outlines are hidden for edited blocks and for the block being edited or moved. */
    private boolean isHidden(@NonNull TextBlock b) {
        if (target != null && target.block == b) return true;
        if (movingBlockKey != null && movingBlockKey.equals(b.key())) return true;
        return isEdited(b);
    }

    @NonNull
    static String keyOf(int page, @NonNull float[] bounds) {
        return page + ":" + Math.round(bounds[0]) + ":" + Math.round(bounds[1]) + ":" + Math.round(bounds[2]) + ":" + Math.round(bounds[3]);
    }
}
