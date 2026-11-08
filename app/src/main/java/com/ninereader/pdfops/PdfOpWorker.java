package com.ninereader.pdfops;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.ninereader.R;
import com.ninereader.util.FileUtils;
import com.ninereader.util.PageRangeParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Runs long PDF operations in the background with WorkManager (§6.2): merge, organize, extract,
 * crop, compress, protect, OCR, conversions and creation. Parameters and results go through
 * {@link Data}; progress is published as {@link #KEY_PROGRESS} (0..100).
 */
public class PdfOpWorker extends Worker {

    public static final String KEY_OP = "op";
    public static final String KEY_INPUTS = "inputs";
    public static final String KEY_OUTPUT = "output";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_RANGE = "range";
    public static final String KEY_PLAN = "plan";
    public static final String KEY_LEVEL = "level";
    public static final String KEY_MARGINS = "margins";
    public static final String KEY_USER_PASSWORD = "user_password";
    public static final String KEY_OWNER_PASSWORD = "owner_password";
    public static final String KEY_ALLOW_PRINT = "allow_print";
    public static final String KEY_ALLOW_COPY = "allow_copy";
    public static final String KEY_DPI = "dpi";
    public static final String KEY_PNG = "png";
    public static final String KEY_BASE_NAME = "base_name";
    public static final String KEY_FORMAT = "format";
    public static final String KEY_PATTERN = "pattern";
    public static final String KEY_LANDSCAPE = "landscape";
    public static final String KEY_PAGES = "pages";
    public static final String KEY_MARGIN = "margin";
    public static final String KEY_FORCE = "force";

    public static final String KEY_PROGRESS = "progress";
    public static final String KEY_RESULT_COUNT = "result_count";
    public static final String KEY_ERROR = "error";

    /** Operation identifiers. */
    public static final String OP_MERGE = "merge";
    public static final String OP_ORGANIZE = "organize";
    public static final String OP_EXTRACT = "extract";
    public static final String OP_CROP = "crop";
    public static final String OP_COMPRESS = "compress";
    public static final String OP_PROTECT = "protect";
    public static final String OP_UNPROTECT = "unprotect";
    public static final String OP_OCR = "ocr";
    public static final String OP_TO_IMAGES = "to_images";
    public static final String OP_TO_TEXT = "to_text";
    public static final String OP_IMAGES_TO_PDF = "images_to_pdf";
    public static final String OP_BLANK = "blank";

    /** Error codes returned in {@link #KEY_ERROR}. */
    public static final String ERROR_PASSWORD = "password";
    public static final String ERROR_INVALID_RANGE = "range";
    public static final String ERROR_IO = "io";
    public static final String ERROR_CANCELLED = "cancelled";

    public PdfOpWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Enqueues an operation and returns its id. */
    @NonNull
    public static UUID enqueue(@NonNull Context context, @NonNull Data data) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(PdfOpWorker.class).setInputData(data).addTag("pdfop").build();
        WorkManager.getInstance(context).enqueue(req);
        return req.getId();
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        Data in = getInputData();
        String op = in.getString(KEY_OP);
        String[] inputs = in.getStringArray(KEY_INPUTS);
        String output = in.getString(KEY_OUTPUT);
        String password = emptyToNull(in.getString(KEY_PASSWORD));
        PdfOps.Progress progress = new PdfOps.Progress() {
            @Override
            public void report(int done, int total) {
                int pct = total <= 0 ? 0 : Math.min(100, done * 100 / total);
                setProgressAsync(new Data.Builder().putInt(KEY_PROGRESS, pct).build());
            }

            @Override
            public boolean isCancelled() {
                return isStopped();
            }
        };
        try {
            if (op == null || output == null) throw new IllegalArgumentException("missing op/output");
            Uri out = Uri.parse(output);
            Uri src = inputs != null && inputs.length > 0 ? Uri.parse(inputs[0]) : null;
            File temp;
            int count = 1;
            switch (op) {
                case OP_MERGE: {
                    List<Uri> uris = new ArrayList<>();
                    if (inputs != null) for (String s : inputs) uris.add(Uri.parse(s));
                    temp = PdfOps.merge(ctx, uris, progress);
                    break;
                }
                case OP_ORGANIZE:
                    temp = PdfOps.organize(ctx, require(src), password, PagePlan.decode(in.getString(KEY_PLAN) == null ? "" : in.getString(KEY_PLAN)));
                    break;
                case OP_EXTRACT:
                    temp = PdfOps.organize(ctx, require(src), password, PagePlan.of(pages(ctx, src, password, in)));
                    break;
                case OP_CROP: {
                    float[] margins = in.getFloatArray(KEY_MARGINS);
                    temp = PdfOps.crop(ctx, require(src), password, margins, pages(ctx, src, password, in), progress);
                    break;
                }
                case OP_COMPRESS:
                    temp = PdfOps.compress(ctx, require(src), password, CompressionLevel.fromKey(in.getString(KEY_LEVEL)), progress);
                    break;
                case OP_PROTECT:
                    temp = PdfOps.protect(ctx, require(src), password, nonNull(in.getString(KEY_USER_PASSWORD)),
                            in.getString(KEY_OWNER_PASSWORD), in.getBoolean(KEY_ALLOW_PRINT, true), in.getBoolean(KEY_ALLOW_COPY, true));
                    break;
                case OP_UNPROTECT:
                    temp = PdfOps.unprotect(ctx, require(src), nonNull(password));
                    break;
                case OP_OCR: {
                    OcrOp.Result r = OcrOp.run(ctx, require(src), password, pages(ctx, src, password, in),
                            in.getBoolean(KEY_FORCE, false), progress);
                    temp = r.file;
                    count = r.pagesRecognized;
                    break;
                }
                case OP_TO_TEXT:
                    temp = ExportOps.exportText(ctx, require(src), password, pages(ctx, src, password, in),
                            ctx.getString(R.string.page_word), progress);
                    break;
                case OP_TO_IMAGES: {
                    // Writes directly into the chosen folder.
                    count = ExportOps.exportImages(ctx, require(src), password, pages(ctx, src, password, in),
                            in.getInt(KEY_DPI, 150), in.getBoolean(KEY_PNG, false), out,
                            nonNull(in.getString(KEY_BASE_NAME)), progress);
                    return Result.success(new Data.Builder().putString(KEY_OUTPUT, output).putInt(KEY_RESULT_COUNT, count).build());
                }
                case OP_IMAGES_TO_PDF: {
                    List<Uri> uris = new ArrayList<>();
                    if (inputs != null) for (String s : inputs) uris.add(Uri.parse(s));
                    temp = CreateOps.imagesToPdf(ctx, uris, CreateOps.PageFormat.fromKey(in.getString(KEY_FORMAT)),
                            in.getInt(KEY_MARGIN, 0), progress);
                    break;
                }
                case OP_BLANK:
                    temp = CreateOps.blank(ctx, CreateOps.PageFormat.fromKey(in.getString(KEY_FORMAT)),
                            in.getBoolean(KEY_LANDSCAPE, false), CreateOps.Pattern.fromKey(in.getString(KEY_PATTERN)),
                            in.getInt(KEY_PAGES, 1));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown op " + op);
            }
            PdfBoxHelper.publish(ctx, temp, out);
            return Result.success(new Data.Builder().putString(KEY_OUTPUT, output).putInt(KEY_RESULT_COUNT, count).build());
        } catch (com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
                 | io.legere.pdfiumandroid.PdfPasswordException e) {
            return failure(ERROR_PASSWORD, output);
        } catch (PdfOps.CancelledException e) {
            return failure(ERROR_CANCELLED, output);
        } catch (IllegalArgumentException e) {
            return failure(ERROR_INVALID_RANGE, output);
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return failure(ERROR_IO, output);
        }
    }

    private Result failure(String code, String output) {
        // Remove the empty destination document created before the operation, when possible.
        if (output != null && !OP_TO_IMAGES.equals(getInputData().getString(KEY_OP))) {
            try {
                android.provider.DocumentsContract.deleteDocument(getApplicationContext().getContentResolver(), Uri.parse(output));
            } catch (Exception ignored) {
                // Best effort only.
            }
        }
        return Result.failure(new Data.Builder().putString(KEY_ERROR, code).build());
    }

    private static List<Integer> pages(Context ctx, Uri src, String password, Data in) throws IOException {
        int count;
        try (com.ninereader.reader.PdfEngine e = com.ninereader.reader.PdfEngine.open(ctx, src, password)) {
            count = e.pageCount();
        }
        return PageRangeParser.parse(nonNull(in.getString(KEY_RANGE)), count);
    }

    private static Uri require(Uri u) {
        if (u == null) throw new IllegalArgumentException("missing input");
        return u;
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** Base name (without extension) for derived files. */
    @NonNull
    public static String baseName(@NonNull Context context, @NonNull Uri uri) {
        return FileUtils.baseName(FileUtils.displayName(context, uri));
    }
}
