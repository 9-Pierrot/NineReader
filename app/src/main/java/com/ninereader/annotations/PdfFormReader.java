package com.ninereader.annotations;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton;
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Reads the AcroForm fields of a document with PdfBox (SIG-01). */
public final class PdfFormReader {

    private PdfFormReader() {
    }

    @NonNull
    public static List<FormField> read(@NonNull PDDocument doc) throws IOException {
        List<FormField> out = new ArrayList<>();
        PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
        if (form == null) return out;
        // Map widgets to page indexes (widget /P entries are often missing).
        Map<Object, Integer> widgetPages = new IdentityHashMap<>();
        int pageIndex = 0;
        for (PDPage page : doc.getPages()) {
            for (PDAnnotation annot : page.getAnnotations()) {
                widgetPages.put(annot.getCOSObject(), pageIndex);
            }
            pageIndex++;
        }
        for (PDField field : form.getFieldTree()) {
            FormField.Kind kind;
            if (field instanceof PDTextField) {
                kind = FormField.Kind.TEXT;
            } else if (field instanceof PDCheckBox) {
                kind = FormField.Kind.CHECKBOX;
            } else if (field instanceof PDRadioButton) {
                kind = FormField.Kind.RADIO;
            } else if (field instanceof PDChoice) {
                kind = FormField.Kind.CHOICE;
            } else {
                continue; // signatures, push buttons, non terminal fields
            }
            String name = field.getFullyQualifiedName();
            if (name == null) continue;
            String value;
            try {
                value = field.getValueAsString();
            } catch (RuntimeException e) {
                value = null;
            }
            FormField ff = new FormField(name, kind, value);
            ff.readOnly = field.isReadOnly();
            if (field instanceof PDTextField) ff.multiline = ((PDTextField) field).isMultiline();
            if (field instanceof PDChoice) ff.options.addAll(((PDChoice) field).getOptionsDisplayValues());
            if (field instanceof PDCheckBox) ff.onValue = ((PDCheckBox) field).getOnValue();
            for (PDAnnotationWidget w : field.getWidgets()) {
                Integer page = widgetPages.get(w.getCOSObject());
                PDRectangle r = w.getRectangle();
                if (page == null || r == null) continue;
                String export = kind == FormField.Kind.RADIO ? onState(w) : null;
                ff.widgets.add(new FormField.Widget(page,
                        new float[]{r.getLowerLeftX(), r.getLowerLeftY(), r.getUpperRightX(), r.getUpperRightY()}, export));
            }
            if (!ff.widgets.isEmpty()) out.add(ff);
        }
        return out;
    }

    /** Name of the non-"Off" appearance state of a radio widget. */
    @Nullable
    static String onState(@NonNull PDAnnotationWidget w) {
        PDAppearanceDictionary ap = w.getAppearance();
        if (ap == null) return null;
        PDAppearanceEntry normal = ap.getNormalAppearance();
        if (normal == null || !normal.isSubDictionary()) return null;
        for (COSName n : normal.getSubDictionary().keySet()) {
            if (!COSName.Off.equals(n)) return n.getName();
        }
        return null;
    }
}
