package com.ninereader.annotations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pending annotations and form values of the open document, with an undo/redo stack (ANN-05).
 * Not thread-safe: use from the main thread. Changes are reported to a listener that persists the
 * draft (Room) so that nothing is lost on crash (§5.4).
 */
public class AnnotationStore {

    /** Change listener. */
    public interface Listener {
        void onAnnotationsChanged(@NonNull AnnotationStore store);
    }

    private static final int MAX_HISTORY = 200;
    private static final Gson GSON = new Gson();

    private final List<Annotation> items = new ArrayList<>();
    private final Map<String, String> fields = new LinkedHashMap<>();
    private final Deque<Op> undo = new ArrayDeque<>();
    private final Deque<Op> redo = new ArrayDeque<>();
    @Nullable
    private Listener listener;

    /** One reversible change. */
    private static final class Op {
        @Nullable
        final Annotation before;
        @Nullable
        final Annotation after;
        @Nullable
        final String field;
        @Nullable
        final String fieldBefore;
        @Nullable
        final String fieldAfter;
        /** Position of the annotation in the list (restores removed items in place). */
        final int index;

        Op(@Nullable Annotation before, @Nullable Annotation after, int index) {
            this.before = before;
            this.after = after;
            this.index = index;
            this.field = null;
            this.fieldBefore = null;
            this.fieldAfter = null;
        }

        Op(@NonNull String field, @Nullable String before, @Nullable String after) {
            this.before = null;
            this.after = null;
            this.index = -1;
            this.field = field;
            this.fieldBefore = before;
            this.fieldAfter = after;
        }
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    @NonNull
    public List<Annotation> all() {
        return Collections.unmodifiableList(items);
    }

    @NonNull
    public List<Annotation> forPage(int page) {
        List<Annotation> out = new ArrayList<>();
        for (Annotation a : items) if (a.page == page) out.add(a);
        return out;
    }

    @Nullable
    public Annotation find(@NonNull String id) {
        for (Annotation a : items) if (a.id.equals(id)) return a;
        return null;
    }

    @NonNull
    public Map<String, String> fields() {
        return Collections.unmodifiableMap(fields);
    }

    public boolean isEmpty() {
        return items.isEmpty() && fields.isEmpty();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void add(@NonNull Annotation a) {
        push(new Op(null, a.copy(), items.size()));
        items.add(a.copy());
        changed();
    }

    public void remove(@NonNull String id) {
        Annotation a = find(id);
        if (a == null) return;
        int index = items.indexOf(a);
        push(new Op(a.copy(), null, index));
        items.remove(a);
        changed();
    }

    /** Replaces an annotation with an edited version (move, resize, recolour, text...). */
    public void update(@NonNull Annotation edited) {
        Annotation old = find(edited.id);
        if (old == null) return;
        int index = items.indexOf(old);
        push(new Op(old.copy(), edited.copy(), index));
        items.set(index, edited.copy());
        changed();
    }

    /** Sets a pending form value (null removes the pending change). */
    public void setField(@NonNull String name, @Nullable String value) {
        String before = fields.get(name);
        if (before == null ? value == null : before.equals(value)) return;
        push(new Op(name, before, value));
        applyField(name, value);
        changed();
    }

    public boolean undo() {
        Op op = undo.poll();
        if (op == null) return false;
        revert(op);
        redo.push(op);
        changed();
        return true;
    }

    public boolean redo() {
        Op op = redo.poll();
        if (op == null) return false;
        apply(op);
        undo.push(op);
        changed();
        return true;
    }

    /** Removes everything (after a successful save) without history. */
    public void clear() {
        items.clear();
        fields.clear();
        undo.clear();
        redo.clear();
        changed();
    }

    private void push(Op op) {
        undo.push(op);
        while (undo.size() > MAX_HISTORY) undo.removeLast();
        redo.clear();
    }

    private void apply(Op op) {
        if (op.field != null) {
            applyField(op.field, op.fieldAfter);
            return;
        }
        if (op.before != null) removeById(op.before.id);
        if (op.after != null) insertAt(op.index, op.after.copy());
    }

    private void revert(Op op) {
        if (op.field != null) {
            applyField(op.field, op.fieldBefore);
            return;
        }
        int index = op.after != null ? indexOf(op.after.id) : op.index;
        if (op.after != null) removeById(op.after.id);
        if (op.before != null) insertAt(index, op.before.copy());
    }

    private void applyField(String name, @Nullable String value) {
        if (value == null) {
            fields.remove(name);
        } else {
            fields.put(name, value);
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id.equals(id)) return i;
        return -1;
    }

    private void insertAt(int index, Annotation a) {
        if (index < 0 || index > items.size()) {
            items.add(a);
        } else {
            items.add(index, a);
        }
    }

    private void removeById(String id) {
        int i = indexOf(id);
        if (i >= 0) items.remove(i);
    }

    private void changed() {
        if (listener != null) listener.onAnnotationsChanged(this);
    }

    // ------------------------------------------------------------------ serialization

    @NonNull
    public String toJson() {
        DraftData d = new DraftData();
        d.annotations = new ArrayList<>(items);
        d.fields = new LinkedHashMap<>(fields);
        return GSON.toJson(d);
    }

    /** Restores a draft (history is not restored). Invalid JSON leaves the store empty. */
    public void loadJson(@Nullable String json) {
        items.clear();
        fields.clear();
        undo.clear();
        redo.clear();
        if (json != null && !json.isEmpty()) {
            try {
                DraftData d = GSON.fromJson(json, DraftData.class);
                if (d != null) {
                    if (d.annotations != null) {
                        for (Annotation a : d.annotations) {
                            if (a != null && a.type != null) items.add(a);
                        }
                    }
                    if (d.fields != null) fields.putAll(d.fields);
                }
            } catch (JsonParseException ignored) {
                // Corrupted draft: start empty.
            }
        }
        changed();
    }
}
