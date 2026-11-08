package com.ninereader.ui.reader;

import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ninereader.R;
import com.ninereader.annotations.AnnotationStore;
import com.ninereader.annotations.FormField;

/** Edits an AcroForm field value (SIG-01): text, checkbox, radio button or choice list. */
final class FormFieldEditor {

    private FormFieldEditor() {
    }

    static void edit(@NonNull AppCompatActivity activity, @NonNull AnnotationStore store, @NonNull FormField field,
                     @Nullable FormField.Widget widget) {
        String pending = store.fields().get(field.name);
        String current = pending != null ? pending : field.value;
        switch (field.kind) {
            case CHECKBOX: {
                boolean checked = pending != null ? "true".equals(pending)
                        : current != null && !current.isEmpty() && !"Off".equals(current);
                store.setField(field.name, checked ? "false" : "true");
                return;
            }
            case RADIO: {
                if (widget != null && widget.exportValue != null) store.setField(field.name, widget.exportValue);
                return;
            }
            case CHOICE: {
                if (field.options.isEmpty()) return;
                CharSequence[] items = field.options.toArray(new CharSequence[0]);
                int selected = current == null ? -1 : field.options.indexOf(current);
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(displayName(field))
                        .setSingleChoiceItems(items, selected, (d, which) -> {
                            store.setField(field.name, field.options.get(which));
                            d.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return;
            }
            case TEXT:
            default: {
                EditText input = new EditText(activity);
                input.setInputType(InputType.TYPE_CLASS_TEXT | (field.multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
                input.setText(current == null ? "" : current);
                input.setSelection(input.getText().length());
                FrameLayout box = new FrameLayout(activity);
                int pad = Math.round(20 * activity.getResources().getDisplayMetrics().density);
                box.setPadding(pad, pad / 2, pad, 0);
                box.addView(input);
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(displayName(field))
                        .setView(box)
                        .setPositiveButton(android.R.string.ok, (d, w) -> store.setField(field.name, input.getText().toString()))
                        .setNeutralButton(R.string.clear, (d, w) -> store.setField(field.name, ""))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                input.requestFocus();
            }
        }
    }

    @NonNull
    private static String displayName(@NonNull FormField f) {
        int dot = f.name.lastIndexOf('.');
        return dot >= 0 ? f.name.substring(dot + 1) : f.name;
    }
}
