package com.ninereader.pdfops;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.ninereader.util.FileUtils;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.pdmodel.font.PDFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font;
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Shared PdfBox helpers: loading from SAF URIs, saving atomically, Unicode font. */
public final class PdfBoxHelper {

    private static final long MAIN_MEMORY_BYTES = 48L * 1024 * 1024;

    private PdfBoxHelper() {
    }

    public static void init(@NonNull Context context) {
        if (!PDFBoxResourceLoader.isReady()) PDFBoxResourceLoader.init(context.getApplicationContext());
    }

    /** Memory policy: keep up to 48 MB in RAM, spill to temp files beyond (1 000+ page documents). */
    @NonNull
    public static MemoryUsageSetting memory() {
        return MemoryUsageSetting.setupMixed(MAIN_MEMORY_BYTES);
    }

    /**
     * Loads a document from a URI. The content is first copied to a private temporary file so that the
     * source can be safely overwritten later.
     *
     * @throws InvalidPasswordException when a password is required or wrong
     */
    @WorkerThread
    @NonNull
    public static Loaded load(@NonNull Context context, @NonNull Uri uri, @Nullable String password) throws IOException {
        init(context);
        File src = FileUtils.tempFile(context, "src", ".pdf");
        try {
            FileUtils.copyUriToFile(context, uri, src);
            PDDocument doc = PDDocument.load(src, password == null ? "" : password, memory());
            return new Loaded(doc, src);
        } catch (IOException | RuntimeException e) {
            FileUtils.deleteQuietly(src);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    /** Loads a local file. */
    @WorkerThread
    @NonNull
    public static PDDocument loadFile(@NonNull Context context, @NonNull File file, @Nullable String password) throws IOException {
        init(context);
        return PDDocument.load(file, password == null ? "" : password, memory());
    }

    /** Saves a document to a new temporary file and returns it (the caller copies it to its destination). */
    @WorkerThread
    @NonNull
    public static File saveToTemp(@NonNull Context context, @NonNull PDDocument doc) throws IOException {
        File out = FileUtils.tempFile(context, "out", ".pdf");
        try {
            doc.save(out);
        } catch (IOException | RuntimeException e) {
            FileUtils.deleteQuietly(out);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        return out;
    }

    /**
     * Saves a document that may have been opened with a password. PdfBox cannot re-encrypt a document
     * opened with the user password, so it is written decrypted, reloaded and protected again with the
     * same user password and permissions (AES-256). Unprotected documents are saved as is.
     */
    @WorkerThread
    @NonNull
    public static File saveKeepingProtection(@NonNull Context context, @NonNull PDDocument doc, @Nullable String password)
            throws IOException {
        if (!doc.isEncrypted()) return saveToTemp(context, doc);
        com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission current = doc.getCurrentAccessPermission();
        doc.setAllSecurityToBeRemoved(true);
        File clear = saveToTemp(context, doc);
        if (password == null || password.isEmpty()) return clear;
        try (PDDocument reloaded = loadFile(context, clear, null)) {
            com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission perms =
                    new com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission(current.getPermissionBytes());
            byte[] owner = new byte[24];
            new java.security.SecureRandom().nextBytes(owner);
            com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy policy =
                    new com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy(FileUtils.toHex(owner), password, perms);
            policy.setEncryptionKeyLength(256);
            reloaded.protect(policy);
            return saveToTemp(context, reloaded);
        } finally {
            FileUtils.deleteQuietly(clear);
        }
    }

    /**
     * Writes a finished temporary file to its destination, then deletes it. The destination is only
     * touched once the complete new content exists (§5.4).
     */
    @WorkerThread
    public static void publish(@NonNull Context context, @NonNull File temp, @NonNull Uri destination) throws IOException {
        try {
            FileUtils.copyFileToUri(context, temp, destination);
        } finally {
            FileUtils.deleteQuietly(temp);
        }
    }

    /** A Unicode font for text written into pages; falls back to Helvetica. */
    @NonNull
    public static PDFont unicodeFont(@NonNull PDDocument doc) {
        try (InputStream in = PDFBoxResourceLoader.getStream("com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf")) {
            return PDType0Font.load(doc, in, true);
        } catch (IOException | RuntimeException e) {
            return PDType1Font.HELVETICA;
        }
    }

    /** A loaded document and its private source copy; close it to release both. */
    public static final class Loaded implements AutoCloseable {
        @NonNull
        public final PDDocument doc;
        @NonNull
        private final File source;

        Loaded(@NonNull PDDocument doc, @NonNull File source) {
            this.doc = doc;
            this.source = source;
        }

        @Override
        public void close() {
            try {
                doc.close();
            } catch (IOException ignored) {
                // nothing to do
            }
            FileUtils.deleteQuietly(source);
        }
    }
}
