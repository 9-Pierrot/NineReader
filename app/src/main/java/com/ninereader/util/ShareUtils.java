package com.ninereader.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Sharing helpers (FileProvider, clipboard). */
public final class ShareUtils {

    private ShareUtils() {
    }

    /** Writes a bitmap as PNG in the shared cache folder and returns a content URI for it. */
    @NonNull
    public static Uri pngForSharing(@NonNull Context context, @NonNull Bitmap bitmap, @NonNull String name) throws IOException {
        File dir = new File(context.getCacheDir(), "shared");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File f = new File(dir, FileUtils.sanitizeFileName(name) + ".png");
        try (FileOutputStream out = new FileOutputStream(f)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("PNG encoding failed");
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", f);
    }

    /** Copies a local file to the shared cache folder and returns a content URI for it. */
    @NonNull
    public static Uri fileForSharing(@NonNull Context context, @NonNull File src, @NonNull String name) throws IOException {
        File dir = new File(context.getCacheDir(), "shared");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File f = new File(dir, FileUtils.sanitizeFileName(name));
        try (java.io.InputStream in = new java.io.FileInputStream(src); FileOutputStream out = new FileOutputStream(f)) {
            FileUtils.copy(in, out);
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", f);
    }

    public static void shareStream(@NonNull Context context, @NonNull Uri uri, @NonNull String mime, @NonNull CharSequence title) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(mime)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(null, uri));
        context.startActivity(Intent.createChooser(send, title));
    }

    public static void shareText(@NonNull Context context, @NonNull String text, @NonNull CharSequence title) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(send, title));
    }

    public static void copyToClipboard(@NonNull Context context, @NonNull String label, @NonNull String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }
}
