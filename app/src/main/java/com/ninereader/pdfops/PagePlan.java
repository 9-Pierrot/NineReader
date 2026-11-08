package com.ninereader.pdfops;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Target page list of an "organize pages" operation (ORG-01, ORG-03): each entry references a source
 * page (or a blank page) with an additional rotation. Pure model, unit tested.
 */
public final class PagePlan {

    /** Marker for an inserted blank page. */
    public static final int BLANK = -1;

    /** One target page. */
    public static final class Entry {
        public final int source;
        public final int rotation;

        public Entry(int source, int rotation) {
            this.source = source;
            this.rotation = normalize(rotation);
        }

        public boolean isBlank() {
            return source == BLANK;
        }

        @NonNull
        public Entry rotated(int delta) {
            return new Entry(source, rotation + delta);
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    @NonNull
    public static PagePlan identity(int pageCount) {
        PagePlan p = new PagePlan();
        for (int i = 0; i < pageCount; i++) p.entries.add(new Entry(i, 0));
        return p;
    }

    @NonNull
    public static PagePlan of(@NonNull List<Integer> pages) {
        PagePlan p = new PagePlan();
        for (int i : pages) p.entries.add(new Entry(i, 0));
        return p;
    }

    @NonNull
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    @NonNull
    public Entry get(int i) {
        return entries.get(i);
    }

    public void move(int from, int to) {
        Entry e = entries.remove(from);
        entries.add(to, e);
    }

    public void rotate(int index, int delta) {
        entries.set(index, entries.get(index).rotated(delta));
    }

    public void remove(int index) {
        entries.remove(index);
    }

    public void duplicate(int index) {
        Entry e = entries.get(index);
        entries.add(index + 1, new Entry(e.source, e.rotation));
    }

    public void insertBlank(int index) {
        entries.add(Math.max(0, Math.min(entries.size(), index)), new Entry(BLANK, 0));
    }

    /** True when the plan keeps the source pages in their original order without changes. */
    public boolean isIdentity(int sourcePageCount) {
        if (entries.size() != sourcePageCount) return false;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.source != i || e.rotation != 0) return false;
        }
        return true;
    }

    /** Whether the order of source pages is preserved (outline links stay meaningful). */
    public boolean keepsOrder() {
        int last = -1;
        for (Entry e : entries) {
            if (e.isBlank()) continue;
            if (e.source <= last) return false;
            last = e.source;
        }
        return true;
    }

    /** Serializes as "src:rot,src:rot" (src -1 = blank). */
    @NonNull
    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.source).append(':').append(e.rotation);
        }
        return sb.toString();
    }

    @NonNull
    public static PagePlan decode(@NonNull String s) {
        PagePlan p = new PagePlan();
        if (s.trim().isEmpty()) return p;
        for (String part : s.split(",")) {
            String[] kv = part.split(":");
            p.entries.add(new Entry(Integer.parseInt(kv[0].trim()), kv.length > 1 ? Integer.parseInt(kv[1].trim()) : 0));
        }
        return p;
    }

    static int normalize(int rotation) {
        int r = rotation % 360;
        if (r < 0) r += 360;
        return (r / 90) * 90;
    }
}
