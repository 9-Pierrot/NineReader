package com.ninereader.annotations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serialized draft of a document: pending annotations and form field values. */
public class DraftData {
    public List<Annotation> annotations = new ArrayList<>();
    /** Pending AcroForm values by fully qualified field name (SIG-01). */
    public Map<String, String> fields = new LinkedHashMap<>();
}
