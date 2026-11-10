package com.ninereader.ui.reader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.Snackbar;
import com.ninereader.R;
import com.ninereader.annotations.Annotation;
import com.ninereader.annotations.AnnotationStore;
import com.ninereader.data.entity.SignatureEntity;
import com.ninereader.databinding.BarAnnotationBinding;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.view.ZoomableRecyclerView;
import com.ninereader.ui.signatures.SignatureDialogs;
import com.ninereader.util.AppExecutors;
import com.ninereader.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

/** Annotation mode of the reader: tool bar, colours, notes, text, signatures, stamps and images. */
class AnnotationUi implements AnnotationController.Host {

    static final int[] PALETTE = {0xFFFFEB3B, 0xFF66BB6A, 0xFF42A5F5, 0xFFEC407A, 0xFFE53935, 0xFF212121};

    private final ReaderActivity a;
    private final AnnotationController controller;
    private final BarAnnotationBinding bar;
    private int penColor = 0xFFE53935;
    @Nullable
    private String pendingImagePath;
    private float pendingImageAspect = 3f;
    private float pendingImageWidth = 150f;
    @Nullable
    private String pendingStampText;

    AnnotationUi(@NonNull ReaderActivity activity) {
        this.a = activity;
        AnnotationStore store = activity.vm.editor().store();
        controller = new AnnotationController(activity.b.pages, activity.b.annotationOverlay, store, this);
        controller.setWidth(activity.prefs.inkWidth());
        controller.setStylusOnly(activity.prefs.stylusOnlyDrawing());
        bar = BarAnnotationBinding.inflate(LayoutInflater.from(activity), activity.b.contextualBar, false);
        bind(bar.toolSelect, AnnotationController.Tool.SELECT);
        bind(bar.toolHighlight, AnnotationController.Tool.HIGHLIGHT);
        bind(bar.toolUnderline, AnnotationController.Tool.UNDERLINE);
        bind(bar.toolStrikeout, AnnotationController.Tool.STRIKEOUT);
        bind(bar.toolInk, AnnotationController.Tool.INK);
        bind(bar.toolEraser, AnnotationController.Tool.ERASER);
        bind(bar.toolNote, AnnotationController.Tool.NOTE);
        bind(bar.toolText, AnnotationController.Tool.FREETEXT);
        bind(bar.toolWhiteout, AnnotationController.Tool.WHITEOUT);
        bind(bar.toolRedact, AnnotationController.Tool.REDACT);
        bar.toolShapes.setOnClickListener(this::showShapes);
        bar.toolSign.setOnClickListener(v -> SignatureDialogs.showPicker(a, path -> startPlacingImage(path, 150f)));
        bar.toolStamps.setOnClickListener(this::showStamps);
        bar.toolImage.setOnClickListener(v -> a.pickImage.launch("image/*"));
        bar.annColor.setOnClickListener(v -> showColorDialog());
        bar.annUndo.setOnClickListener(v -> store.undo());
        bar.annRedo.setOnClickListener(v -> store.redo());
        bar.annSave.setOnClickListener(v -> a.askSave(false));
        bar.annDone.setOnClickListener(v -> exit());
        bar.annDelete.setOnClickListener(v -> {
            Annotation sel = controller.selected();
            if (sel != null) {
                store.remove(sel.id);
                controller.select(null);
            }
        });
        bar.annEditText.setOnClickListener(v -> {
            Annotation sel = controller.selected();
            if (sel != null) editText(sel);
        });
        refreshState();
    }

    @NonNull
    AnnotationController controller() {
        return controller;
    }

    boolean isActive() {
        return controller.isActive();
    }

    void enter() {
        if (controller.isActive()) return;
        if (a.editUi != null && a.editUi.isActive()) a.editUi.exit();
        a.vm.clearSelection();
        a.b.contextualBar.removeAllViews();
        a.b.contextualBar.addView(bar.getRoot());
        controller.setActive(true);
        setTool(AnnotationController.Tool.SELECT);
        a.setBarsVisible(true, true);
        Toast.makeText(a, R.string.annotate_hint, Toast.LENGTH_SHORT).show();
    }

    void exit() {
        if (!controller.isActive()) return;
        controller.setActive(false);
        a.b.contextualBar.removeView(bar.getRoot());
        pendingImagePath = null;
        pendingStampText = null;
        a.setBarsVisible(true, true);
        if (a.vm.editor().hasPendingChanges()) {
            Snackbar.make(a.b.root, R.string.annotations_pending, Snackbar.LENGTH_LONG)
                    .setAction(R.string.save, v -> a.askSave(false))
                    .show();
        }
    }

    void selectAnnotation(@NonNull Annotation annotation) {
        setTool(AnnotationController.Tool.SELECT);
        Annotation stored = a.vm.editor().store().find(annotation.id);
        if (stored != null) controller.select(stored);
    }

    int markupColor(@NonNull Annotation.Type type) {
        return type == Annotation.Type.HIGHLIGHT ? a.prefs.highlightColor() : penColor;
    }

    void refreshState() {
        AnnotationStore store = a.vm.editor().store();
        bar.annUndo.setEnabled(store.canUndo());
        bar.annUndo.setAlpha(store.canUndo() ? 1f : 0.38f);
        bar.annRedo.setEnabled(store.canRedo());
        bar.annRedo.setAlpha(store.canRedo() ? 1f : 0.38f);
        Annotation sel = controller.selected();
        if (sel != null && store.find(sel.id) == null) controller.select(null);
    }

    private void bind(@NonNull ImageButton button, @NonNull AnnotationController.Tool tool) {
        button.setOnClickListener(v -> setTool(tool));
    }

    void setTool(@NonNull AnnotationController.Tool tool) {
        controller.setTool(tool);
        if (tool != AnnotationController.Tool.SELECT && tool != AnnotationController.Tool.ERASER) {
            controller.setColor(tool == AnnotationController.Tool.HIGHLIGHT ? a.prefs.highlightColor() : penColor);
        }
        if (tool != AnnotationController.Tool.PLACE) pendingImagePath = null;
        if (tool != AnnotationController.Tool.FREETEXT) pendingStampText = null;
        LinearLayout tools = bar.annTools;
        for (int i = 0; i < tools.getChildCount(); i++) {
            View child = tools.getChildAt(i);
            child.setBackgroundResource(0);
        }
        View selected = buttonFor(tool);
        if (selected != null) selected.setBackgroundResource(R.drawable.bg_tool_selected);
    }

    @Nullable
    private View buttonFor(AnnotationController.Tool tool) {
        switch (tool) {
            case SELECT:
                return bar.toolSelect;
            case HIGHLIGHT:
                return bar.toolHighlight;
            case UNDERLINE:
                return bar.toolUnderline;
            case STRIKEOUT:
                return bar.toolStrikeout;
            case INK:
                return bar.toolInk;
            case ERASER:
                return bar.toolEraser;
            case NOTE:
                return bar.toolNote;
            case FREETEXT:
                return pendingStampText != null ? bar.toolStamps : bar.toolText;
            case RECT:
            case CIRCLE:
            case LINE:
            case ARROW:
                return bar.toolShapes;
            case WHITEOUT:
                return bar.toolWhiteout;
            case REDACT:
                return bar.toolRedact;
            case PLACE:
                return bar.toolSign;
            default:
                return null;
        }
    }

    private void showShapes(View anchor) {
        PopupMenu menu = new PopupMenu(a, anchor);
        menu.getMenu().add(0, 1, 0, R.string.tool_rect);
        menu.getMenu().add(0, 2, 1, R.string.tool_circle);
        menu.getMenu().add(0, 3, 2, R.string.tool_line);
        menu.getMenu().add(0, 4, 3, R.string.tool_arrow);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    setTool(AnnotationController.Tool.RECT);
                    break;
                case 2:
                    setTool(AnnotationController.Tool.CIRCLE);
                    break;
                case 3:
                    setTool(AnnotationController.Tool.LINE);
                    break;
                default:
                    setTool(AnnotationController.Tool.ARROW);
                    break;
            }
            return true;
        });
        menu.show();
    }

    /** Quick stamps: date, check mark, cross, initials (SIG-04). */
    private void showStamps(View anchor) {
        PopupMenu menu = new PopupMenu(a, anchor);
        menu.getMenu().add(0, 1, 0, R.string.stamp_date);
        menu.getMenu().add(0, 2, 1, R.string.stamp_check);
        menu.getMenu().add(0, 3, 2, R.string.stamp_cross);
        menu.getMenu().add(0, 4, 3, R.string.stamp_initials);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    startPlacingText(DateFormat.getDateInstance(DateFormat.SHORT).format(new Date()));
                    break;
                case 2:
                    placeSymbol(true);
                    break;
                case 3:
                    placeSymbol(false);
                    break;
                default:
                    placeInitials();
                    break;
            }
            return true;
        });
        menu.show();
    }

    private void placeInitials() {
        AppExecutors.get().diskIo().execute(() -> {
            String path = null;
            for (SignatureEntity s : com.ninereader.NineReaderApp.from(a).db().signatures().all()) {
                if (SignatureEntity.KIND_INITIALS.equals(s.kind)) {
                    path = s.imagePath;
                    break;
                }
            }
            String found = path;
            AppExecutors.get().mainThread().execute(() -> {
                if (found != null) {
                    startPlacingImage(found, 60f);
                } else if (!a.prefs.initials().trim().isEmpty()) {
                    startPlacingText(a.prefs.initials().trim());
                } else {
                    Toast.makeText(a, R.string.initials_missing, Toast.LENGTH_LONG).show();
                    SignatureDialogs.showPad(a, p -> startPlacingImage(p, 60f));
                }
            });
        });
    }

    private void placeSymbol(boolean check) {
        AppExecutors.get().background().execute(() -> {
            File dir = new File(a.getFilesDir(), "stamps");
            File f = new File(dir, check ? "check.png" : "cross.png");
            if (!f.exists()) {
                try {
                    if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdir");
                    Bitmap bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bmp);
                    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(14);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeJoin(Paint.Join.ROUND);
                    p.setColor(check ? 0xFF1B5E20 : 0xFFB71C1C);
                    Path path = new Path();
                    if (check) {
                        path.moveTo(20, 68);
                        path.lineTo(52, 100);
                        path.lineTo(110, 28);
                    } else {
                        path.moveTo(24, 24);
                        path.lineTo(104, 104);
                        path.moveTo(104, 24);
                        path.lineTo(24, 104);
                    }
                    c.drawPath(path, p);
                    try (FileOutputStream out = new FileOutputStream(f)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                } catch (IOException e) {
                    return;
                }
            }
            AppExecutors.get().mainThread().execute(() -> startPlacingImage(f.getAbsolutePath(), 20f));
        });
    }

    /** Copies a picked image to private storage and starts its placement (MOD-01). */
    void onImagePicked(@Nullable Uri uri) {
        if (uri == null) return;
        AppExecutors.get().background().execute(() -> {
            try {
                File dir = new File(a.getFilesDir(), "images");
                if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdir");
                String type = a.getContentResolver().getType(uri);
                String ext = type != null && type.contains("png") ? ".png" : ".jpg";
                File f = new File(dir, UUID.randomUUID() + ext);
                FileUtils.copyUriToFile(a, uri, f);
                AppExecutors.get().mainThread().execute(() -> startPlacingImage(f.getAbsolutePath(), 200f));
            } catch (IOException | SecurityException e) {
                AppExecutors.get().mainThread().execute(() -> Toast.makeText(a, R.string.error_generic, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void startPlacingImage(@NonNull String path, float widthPt) {
        if (!controller.isActive()) enter();
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        pendingImageAspect = o.outHeight > 0 ? o.outWidth / (float) o.outHeight : 1f;
        pendingImageWidth = widthPt;
        setTool(AnnotationController.Tool.PLACE);
        pendingImagePath = path;
        Toast.makeText(a, R.string.tap_to_place, Toast.LENGTH_SHORT).show();
    }

    private void startPlacingText(@NonNull String text) {
        if (!controller.isActive()) enter();
        pendingStampText = text;
        setTool(AnnotationController.Tool.FREETEXT);
        Toast.makeText(a, R.string.tap_to_place, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ host callbacks

    @Override
    public void onMarkupDown(@NonNull ZoomableRecyclerView.Hit hit) {
        if (!hit.hasPagePoint) return;
        float tol = 8f / Math.max(0.1f, hit.frame.pageUnitToPx() * a.b.pages.getScale());
        a.vm.beginSelection(hit.frame.page, hit.pageX, hit.pageY, tol, started -> { });
    }

    @Override
    public void onMarkupMove(@Nullable ZoomableRecyclerView.Hit hit) {
        if (hit == null || !hit.hasPagePoint) return;
        float tol = 12f / Math.max(0.1f, hit.frame.pageUnitToPx() * a.b.pages.getScale());
        a.vm.extendSelection(hit.frame.page, hit.pageX, hit.pageY, tol);
    }

    @Override
    public void onMarkupUp(@NonNull Annotation.Type type) {
        // Runs after the pending selection computations (same serial thread).
        a.vm.runOnTextThread(() -> AppExecutors.get().mainThread().execute(() -> a.markupFromSelection(type)));
    }

    @Override
    public void onPlaceText(@NonNull AnnotationController.Tool tool, int page, float x, float y) {
        if (tool == AnnotationController.Tool.FREETEXT && pendingStampText != null) {
            addFreeText(page, x, y, pendingStampText, 12f);
            return;
        }
        EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(2);
        boolean note = tool == AnnotationController.Tool.NOTE;
        input.setHint(note ? R.string.note_hint : R.string.text_hint);
        new MaterialAlertDialogBuilder(a)
                .setTitle(note ? R.string.tool_note : R.string.tool_text)
                .setView(a.padded(input))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    if (note) {
                        Annotation an = new Annotation();
                        an.type = Annotation.Type.NOTE;
                        an.page = page;
                        an.color = penColor == 0xFF212121 ? 0xFFFFEB3B : penColor;
                        an.bounds = new float[]{x, y - 20, x + 20, y};
                        an.text = text;
                        a.vm.editor().store().add(an);
                    } else {
                        addFreeText(page, x, y, text, 12f);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        input.requestFocus();
    }

    private void addFreeText(int page, float x, float y, String text, float size) {
        PdfEngine e = a.vm.engine();
        float pageW = e != null && e.isMeasured(page) ? e.pageWidth(page) : 612f;
        int longest = 1;
        String[] lines = text.split("\n");
        for (String l : lines) longest = Math.max(longest, l.length());
        float width = Math.min(Math.max(40f, longest * size * 0.55f + 6), Math.max(60f, pageW - x - 10));
        int estimatedLines = 0;
        for (String l : lines) estimatedLines += Math.max(1, (int) Math.ceil(l.length() * size * 0.55f / width));
        float height = estimatedLines * size * 1.25f + 4;
        Annotation an = new Annotation();
        an.type = Annotation.Type.FREETEXT;
        an.page = page;
        an.color = penColor == 0xFFFFEB3B ? 0xFF212121 : penColor;
        an.fontSize = size;
        an.text = text;
        an.bounds = new float[]{x, y - height, x + width, y};
        a.vm.editor().store().add(an);
        pendingStampText = null;
        setTool(AnnotationController.Tool.SELECT);
        Annotation stored = a.vm.editor().store().find(an.id);
        if (stored != null) controller.select(stored);
    }

    @Override
    public void onPlaceImage(int page, float x, float y) {
        if (pendingImagePath == null) return;
        float w = pendingImageWidth;
        float h = w / Math.max(0.05f, pendingImageAspect);
        Annotation an = new Annotation();
        an.type = Annotation.Type.IMAGE;
        an.page = page;
        an.imagePath = pendingImagePath;
        an.bounds = new float[]{x - w / 2, y - h / 2, x + w / 2, y + h / 2};
        a.vm.editor().store().add(an);
        pendingImagePath = null;
        setTool(AnnotationController.Tool.SELECT);
        Annotation stored = a.vm.editor().store().find(an.id);
        if (stored != null) controller.select(stored);
    }

    @Override
    public void onSelectionChanged(@Nullable Annotation selected) {
        bar.annSelectionActions.setVisibility(selected == null ? View.GONE : View.VISIBLE);
        if (selected != null) {
            bar.annSelectionLabel.setText(a.getString(R.string.selected_annotation));
            boolean editable = selected.type == Annotation.Type.NOTE || selected.type == Annotation.Type.FREETEXT;
            bar.annEditText.setVisibility(editable ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void requestRedraw() {
        a.b.pages.invalidatePages();
    }

    private void editText(@NonNull Annotation sel) {
        EditText input = new EditText(a);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(sel.text);
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.edit_text)
                .setView(a.padded(input))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Annotation edited = sel.copy();
                    edited.text = input.getText().toString();
                    a.vm.editor().store().update(edited);
                    Annotation stored = a.vm.editor().store().find(edited.id);
                    controller.select(stored);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showColorDialog() {
        View content = LayoutInflater.from(a).inflate(R.layout.dialog_color_width, null, false);
        ChipGroup colors = content.findViewById(R.id.colors);
        Slider width = content.findViewById(R.id.width);
        boolean highlightTool = controller.tool() == AnnotationController.Tool.HIGHLIGHT;
        int current = highlightTool ? a.prefs.highlightColor() : penColor;
        String[] names = a.getResources().getStringArray(R.array.palette_names);
        for (int i = 0; i < PALETTE.length; i++) {
            Chip chip = new Chip(a, null, com.google.android.material.R.attr.chipStyle);
            chip.setCheckable(true);
            chip.setText(names[i]);
            chip.setChipIconResource(R.drawable.ic_circle);
            chip.setChipIconTint(android.content.res.ColorStateList.valueOf(PALETTE[i]));
            chip.setChipIconVisible(true);
            chip.setId(View.generateViewId());
            chip.setTag(PALETTE[i]);
            chip.setChecked(PALETTE[i] == current);
            colors.addView(chip);
        }
        width.setValue(Math.max(width.getValueFrom(), Math.min(width.getValueTo(), Math.round(controller.width() * 2) / 2f)));
        new MaterialAlertDialogBuilder(a)
                .setTitle(R.string.color_and_width)
                .setView(content)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int checked = colors.getCheckedChipId();
                    if (checked != View.NO_ID) {
                        int color = (int) colors.findViewById(checked).getTag();
                        if (highlightTool) {
                            a.prefs.setHighlightColor(color);
                        } else {
                            penColor = color;
                        }
                        controller.setColor(color);
                    }
                    controller.setWidth(width.getValue());
                    a.prefs.setInkWidth(width.getValue());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
