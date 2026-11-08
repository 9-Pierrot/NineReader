package com.ninereader.ui.common;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.ninereader.ui.reader.ReaderActivity;

/** Opens documents in the reader and keeps access to them across restarts (SAF persistable grants). */
public final class DocumentOpener {

    private DocumentOpener() {
    }

    /** Keeps read/write access to a URI picked with ACTION_OPEN_DOCUMENT(_TREE), when the provider allows it. */
    public static void persist(@NonNull Context context, @NonNull Uri uri) {
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException e) {
            try {
                context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Not persistable: access lasts for this session only.
            }
        }
    }

    public static void open(@NonNull Context context, @NonNull Uri uri) {
        context.startActivity(ReaderActivity.intent(context, uri));
    }

    /** Opens a document with an action to run once loaded (reflow, rsvp, tts, annotate, sign, presentation). */
    public static void openWithAction(@NonNull Context context, @NonNull Uri uri, @NonNull String action) {
        context.startActivity(ReaderActivity.intent(context, uri).putExtra(ReaderActivity.EXTRA_ACTION, action));
    }
}
