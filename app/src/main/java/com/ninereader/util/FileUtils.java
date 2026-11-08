package com.ninereader.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.util.Locale;

/** File and Storage Access Framework helpers. */
public final class FileUtils {

    private static final int BUFFER = 64 * 1024;

    private FileUtils() {
    }

    /** Display name of a content/file URI, or the last path segment as fallback. */
    @NonNull
    public static String displayName(@NonNull Context context, @NonNull Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor c = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    return c.getString(0);
                }
            } catch (RuntimeException ignored) {
                // Some providers throw on query; fall back below.
            }
        }
        String last = uri.getLastPathSegment();
        if (last == null) return "document.pdf";
        int slash = last.lastIndexOf('/');
        return slash >= 0 ? last.substring(slash + 1) : last;
    }

    /** Size in bytes of the content behind a URI, -1 when unknown. */
    public static long size(@NonNull Context context, @NonNull Uri uri) {
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor c = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.SIZE}, null, null, null)) {
                if (c != null && c.moveToFirst() && !c.isNull(0)) {
                    return c.getLong(0);
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        } else if (ContentResolver.SCHEME_FILE.equals(uri.getScheme()) && uri.getPath() != null) {
            return new File(uri.getPath()).length();
        }
        return -1;
    }

    public static long copy(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buf = new byte[BUFFER];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            total += n;
        }
        out.flush();
        return total;
    }

    /** Copies a URI to a local file. */
    public static void copyUriToFile(@NonNull Context context, @NonNull Uri uri, @NonNull File dest) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) throw new IOException("Cannot open " + uri);
            copy(in, out);
        }
    }

    /**
     * Writes a local file to a destination URI. The source must already be complete (written to a
     * temporary file first) so that the destination is only touched once the new content is valid.
     */
    public static void copyFileToUri(@NonNull Context context, @NonNull File src, @NonNull Uri dest) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = context.getContentResolver().openOutputStream(dest, "wt")) {
            if (out == null) throw new IOException("Cannot write " + dest);
            copy(in, out);
        }
    }

    /** Creates a fresh temporary file in the app cache. */
    @NonNull
    public static File tempFile(@NonNull Context context, @NonNull String prefix, @NonNull String suffix) throws IOException {
        File dir = new File(context.getCacheDir(), "work");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        return File.createTempFile(prefix, suffix, dir);
    }

    /** Quick content fingerprint: size + SHA-1 of the first and last 64 KiB. */
    @Nullable
    public static String quickHash(@NonNull Context context, @NonNull Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] head = new byte[BUFFER];
            int read = readFully(in, head);
            md.update(head, 0, read);
            byte[] ring = new byte[BUFFER];
            long total = read;
            int ringLen = 0;
            int n;
            byte[] buf = new byte[BUFFER];
            while ((n = in.read(buf)) > 0) {
                total += n;
                // Keep only the last BUFFER bytes.
                if (n >= BUFFER) {
                    System.arraycopy(buf, n - BUFFER, ring, 0, BUFFER);
                    ringLen = BUFFER;
                } else {
                    int keep = Math.min(ringLen, BUFFER - n);
                    System.arraycopy(ring, ringLen - keep, ring, 0, keep);
                    System.arraycopy(buf, 0, ring, keep, n);
                    ringLen = keep + n;
                }
            }
            md.update(ring, 0, ringLen);
            md.update(Long.toString(total).getBytes());
            return toHex(md.digest());
        } catch (IOException | NoSuchAlgorithmException | SecurityException e) {
            return null;
        }
    }

    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        int n;
        while (off < buf.length && (n = in.read(buf, off, buf.length - off)) > 0) {
            off += n;
        }
        return off;
    }

    @NonNull
    public static String toHex(@NonNull byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02x", b));
        }
        return sb.toString();
    }

    /** Human readable size ("1,2 Mo" / "1.2 MB" depending on locale). */
    @NonNull
    public static String formatSize(long bytes, @NonNull Locale locale) {
        if (bytes < 0) return "?";
        boolean fr = "fr".equals(locale.getLanguage());
        String[] units = fr ? new String[]{"o", "Ko", "Mo", "Go"} : new String[]{"B", "KB", "MB", "GB"};
        double v = bytes;
        int u = 0;
        while (v >= 1024 && u < units.length - 1) {
            v /= 1024;
            u++;
        }
        DecimalFormat df = (DecimalFormat) DecimalFormat.getInstance(locale);
        df.applyPattern(u == 0 ? "0" : "0.#");
        return df.format(v) + " " + units[u];
    }

    /** Removes the extension of a file name. */
    @NonNull
    public static String baseName(@NonNull String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Replaces characters that are not allowed in file names. */
    @NonNull
    public static String sanitizeFileName(@NonNull String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        return s.isEmpty() ? "document" : s;
    }

    public static void deleteQuietly(@Nullable File f) {
        if (f != null && f.exists() && !f.delete()) {
            f.deleteOnExit();
        }
    }
}
