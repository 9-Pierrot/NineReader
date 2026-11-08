package com.ninereader.annotations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/** An AcroForm field and its widgets (SIG-01). */
public class FormField {

    /** Supported field kinds. */
    public enum Kind { TEXT, CHECKBOX, RADIO, CHOICE }

    /** One widget: page and rectangle {left, bottom, right, top}; radio widgets carry their export value. */
    public static final class Widget {
        public final int page;
        @NonNull
        public final float[] rect;
        @Nullable
        public final String exportValue;

        public Widget(int page, @NonNull float[] rect, @Nullable String exportValue) {
            this.page = page;
            this.rect = rect;
            this.exportValue = exportValue;
        }
    }

    @NonNull
    public final String name;
    @NonNull
    public final Kind kind;
    @Nullable
    public final String value;
    @NonNull
    public final List<String> options = new ArrayList<>();
    @NonNull
    public final List<Widget> widgets = new ArrayList<>();
    public boolean multiline;
    public boolean readOnly;
    /** Value of the "on" state of a checkbox. */
    @Nullable
    public String onValue;

    public FormField(@NonNull String name, @NonNull Kind kind, @Nullable String value) {
        this.name = name;
        this.kind = kind;
        this.value = value;
    }
}
