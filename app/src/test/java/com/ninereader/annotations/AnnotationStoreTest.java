package com.ninereader.annotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class AnnotationStoreTest {

    private AnnotationStore store;
    private int changes;

    @Before
    public void setUp() {
        store = new AnnotationStore();
        store.setListener(s -> changes++);
    }

    private static Annotation highlight(int page) {
        Annotation a = new Annotation();
        a.type = Annotation.Type.HIGHLIGHT;
        a.page = page;
        a.rects.add(new float[]{10, 10, 100, 20});
        a.text = "hello";
        return a;
    }

    @Test
    public void addUndoRedo() {
        Annotation a = highlight(1);
        store.add(a);
        assertEquals(1, store.all().size());
        assertTrue(store.canUndo());
        assertTrue(store.undo());
        assertTrue(store.all().isEmpty());
        assertTrue(store.canRedo());
        assertTrue(store.redo());
        assertEquals(a.id, store.all().get(0).id);
        assertFalse(store.redo());
        assertEquals(3, changes);
    }

    @Test
    public void removeAndUndoRestoresPosition() {
        Annotation a = highlight(0);
        Annotation b = highlight(0);
        Annotation c = highlight(2);
        store.add(a);
        store.add(b);
        store.add(c);
        store.remove(b.id);
        assertEquals(2, store.all().size());
        store.undo();
        assertEquals(b.id, store.all().get(1).id);
        assertEquals(2, store.forPage(0).size());
    }

    @Test
    public void updateIsUndoable() {
        Annotation a = highlight(0);
        store.add(a);
        Annotation edited = store.find(a.id).copy();
        edited.color = 0xFF00FF00;
        store.update(edited);
        assertEquals(0xFF00FF00, store.find(a.id).color);
        store.undo();
        assertEquals(a.color, store.find(a.id).color);
        store.redo();
        assertEquals(0xFF00FF00, store.find(a.id).color);
    }

    @Test
    public void storedCopiesAreIndependent() {
        Annotation a = highlight(0);
        store.add(a);
        a.color = 0;
        assertTrue(store.find(a.id).color != 0);
    }

    @Test
    public void formFieldsAreUndoable() {
        store.setField("name", "Alice");
        store.setField("name", "Bob");
        assertEquals("Bob", store.fields().get("name"));
        store.undo();
        assertEquals("Alice", store.fields().get("name"));
        store.undo();
        assertNull(store.fields().get("name"));
        assertTrue(store.isEmpty());
        // Same value: no history entry
        store.setField("x", "1");
        store.setField("x", "1");
        store.undo();
        assertFalse(store.canUndo());
    }

    @Test
    public void newActionClearsRedo() {
        store.add(highlight(0));
        store.undo();
        store.add(highlight(1));
        assertFalse(store.canRedo());
    }

    @Test
    public void jsonRoundTrip() {
        Annotation a = highlight(3);
        Annotation ink = new Annotation();
        ink.type = Annotation.Type.INK;
        ink.strokes.add(new float[]{1, 2, 3, 4});
        store.add(a);
        store.add(ink);
        store.setField("agree", "true");
        String json = store.toJson();

        AnnotationStore other = new AnnotationStore();
        other.loadJson(json);
        assertEquals(2, other.all().size());
        assertEquals(Annotation.Type.INK, other.all().get(1).type);
        assertEquals(4, other.all().get(1).strokes.get(0).length);
        assertEquals("true", other.fields().get("agree"));
        assertFalse(other.canUndo());
    }

    @Test
    public void invalidJsonGivesEmptyStore() {
        store.add(highlight(0));
        store.loadJson("{not json");
        assertTrue(store.isEmpty());
        store.loadJson(null);
        assertTrue(store.isEmpty());
    }

    @Test
    public void clearRemovesEverything() {
        store.add(highlight(0));
        store.setField("a", "b");
        store.clear();
        assertTrue(store.isEmpty());
        assertFalse(store.canUndo());
    }
}
