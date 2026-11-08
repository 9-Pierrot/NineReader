package com.ninereader.ui.files;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.util.ArrayList;
import java.util.List;

/** Lists the sub-folders and PDF files of a folder granted through the Storage Access Framework. */
public final class SafBrowser {

    private SafBrowser() {
    }

    @WorkerThread
    @NonNull
    public static List<FileRow> list(@NonNull Context context, @NonNull Uri treeUri, @NonNull String documentId) {
        List<FileRow> rows = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor c = context.getContentResolver().query(children, projection, null, null, null)) {
            if (c == null) return rows;
            while (c.moveToNext()) {
                String id = c.getString(0);
                String name = c.isNull(1) ? "" : c.getString(1);
                String mime = c.isNull(2) ? "" : c.getString(2);
                if (name.startsWith(".")) continue;
                boolean dir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                boolean pdf = "application/pdf".equals(mime) || name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
                if (!dir && !pdf) continue;
                Uri uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                FileRow row = new FileRow(dir ? FileRow.Kind.FOLDER : FileRow.Kind.PDF, name, uri.toString());
                row.treeUri = treeUri.toString();
                row.documentId = id;
                row.parentUri = parent.toString();
                row.size = c.isNull(3) ? -1 : c.getLong(3);
                row.date = c.isNull(4) ? 0 : c.getLong(4);
                rows.add(row);
            }
        } catch (RuntimeException e) {
            // Permission revoked or provider error: show what we have.
        }
        return rows;
    }
}
