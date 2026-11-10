package com.ninereader.ui.tools;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ninereader.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** All tools of the Tools tab with their categories (§3.1): Read, Create, Edit, Convert, Organize, Protect. */
public final class ToolCatalog {

    /** Tool categories. */
    public enum Category { ALL, READ, CREATE, EDIT, CONVERT, ORGANIZE, PROTECT }

    /** How a tool is launched. */
    public enum Launch {
        /** Pick a PDF, then open the reader with an action. */
        READER,
        /** Generic operation screen. */
        RUN,
        /** Page organizer. */
        ORGANIZE,
        /** Signature manager. */
        SIGNATURES,
        /** Side-by-side comparison. */
        COMPARE
    }

    /** A tool. */
    public static final class Tool {
        @NonNull
        public final String id;
        @StringRes
        public final int title;
        @StringRes
        public final int description;
        @DrawableRes
        public final int icon;
        @NonNull
        public final Launch launch;
        /** Reader action for {@link Launch#READER} tools. */
        @Nullable
        public final String readerAction;
        @NonNull
        public final List<Category> categories;

        Tool(@NonNull String id, int title, int description, int icon, @NonNull Launch launch, @Nullable String readerAction,
             Category... categories) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.launch = launch;
            this.readerAction = readerAction;
            this.categories = Collections.unmodifiableList(Arrays.asList(categories));
        }

        public boolean in(@NonNull Category c) {
            return c == Category.ALL || categories.contains(c);
        }
    }

    public static final List<Tool> TOOLS;

    static {
        List<Tool> t = new ArrayList<>();
        t.add(new Tool("open", R.string.tool_open, R.string.tool_open_desc, R.drawable.ic_file_open, Launch.READER, null, Category.READ));
        t.add(new Tool("reflow", R.string.reflow_title, R.string.tool_reflow_desc, R.drawable.ic_reflow, Launch.READER, "reflow", Category.READ));
        t.add(new Tool("tts", R.string.read_aloud, R.string.tool_tts_desc, R.drawable.ic_volume, Launch.READER, "tts", Category.READ));
        t.add(new Tool("rsvp", R.string.rsvp_title, R.string.tool_rsvp_desc, R.drawable.ic_speed, Launch.READER, "rsvp", Category.READ));
        t.add(new Tool("presentation", R.string.presentation_mode, R.string.tool_presentation_desc, R.drawable.ic_slideshow,
                Launch.READER, "presentation", Category.READ));
        t.add(new Tool("compare", R.string.tool_compare, R.string.tool_compare_desc, R.drawable.ic_compare, Launch.COMPARE, null, Category.READ));
        t.add(new Tool("images_to_pdf", R.string.tool_images_to_pdf, R.string.tool_images_to_pdf_desc, R.drawable.ic_image,
                Launch.RUN, null, Category.CREATE, Category.CONVERT));
        t.add(new Tool("blank", R.string.tool_blank, R.string.tool_blank_desc, R.drawable.ic_note_add, Launch.RUN, null, Category.CREATE));
        t.add(new Tool("merge", R.string.tool_merge, R.string.tool_merge_desc, R.drawable.ic_merge, Launch.RUN, null,
                Category.CREATE, Category.ORGANIZE));
        t.add(new Tool("ocr", R.string.tool_ocr, R.string.tool_ocr_desc, R.drawable.ic_ocr, Launch.RUN, null, Category.CREATE, Category.CONVERT));
        t.add(new Tool("annotate", R.string.annotate, R.string.tool_annotate_desc, R.drawable.ic_highlight, Launch.READER, "annotate", Category.EDIT));
        t.add(new Tool("fill_sign", R.string.tool_fill_sign, R.string.tool_fill_sign_desc, R.drawable.ic_signature, Launch.READER, "sign",
                Category.EDIT));
        t.add(new Tool("edit_content", R.string.tool_edit_content, R.string.tool_edit_content_desc, R.drawable.ic_text_fields,
                Launch.READER, "edit", Category.EDIT));
        t.add(new Tool("signatures", R.string.signatures, R.string.tool_signatures_desc, R.drawable.ic_edit, Launch.SIGNATURES, null, Category.EDIT));
        t.add(new Tool("redact", R.string.tool_redact, R.string.tool_redact_desc, R.drawable.ic_redact, Launch.READER, "redact",
                Category.EDIT, Category.PROTECT));
        t.add(new Tool("to_images", R.string.tool_to_images, R.string.tool_to_images_desc, R.drawable.ic_download, Launch.RUN, null, Category.CONVERT));
        t.add(new Tool("to_text", R.string.tool_to_text, R.string.tool_to_text_desc, R.drawable.ic_text_file, Launch.RUN, null, Category.CONVERT));
        t.add(new Tool("organize", R.string.tool_organize, R.string.tool_organize_desc, R.drawable.ic_dashboard, Launch.ORGANIZE, null,
                Category.ORGANIZE, Category.EDIT));
        t.add(new Tool("extract", R.string.tool_extract, R.string.tool_extract_desc, R.drawable.ic_split, Launch.RUN, null, Category.ORGANIZE));
        t.add(new Tool("crop", R.string.tool_crop, R.string.tool_crop_desc, R.drawable.ic_crop, Launch.RUN, null, Category.ORGANIZE, Category.EDIT));
        t.add(new Tool("compress", R.string.tool_compress, R.string.tool_compress_desc, R.drawable.ic_compress, Launch.RUN, null,
                Category.ORGANIZE, Category.CONVERT));
        t.add(new Tool("protect", R.string.tool_protect, R.string.tool_protect_desc, R.drawable.ic_lock, Launch.RUN, null, Category.PROTECT));
        t.add(new Tool("unprotect", R.string.tool_unprotect, R.string.tool_unprotect_desc, R.drawable.ic_lock_open, Launch.RUN, null, Category.PROTECT));
        TOOLS = Collections.unmodifiableList(t);
    }

    private ToolCatalog() {
    }

    @Nullable
    public static Tool byId(@Nullable String id) {
        for (Tool t : TOOLS) if (t.id.equals(id)) return t;
        return null;
    }

    @NonNull
    public static List<Tool> in(@NonNull Category c) {
        List<Tool> out = new ArrayList<>();
        for (Tool t : TOOLS) if (t.in(c)) out.add(t);
        return out;
    }
}
