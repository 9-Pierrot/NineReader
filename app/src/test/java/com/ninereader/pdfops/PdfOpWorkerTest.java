package com.ninereader.pdfops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestWorkerBuilder;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

/** Runs {@link PdfOpWorker} end to end on PdfBox operations (WorkManager test harness). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class PdfOpWorkerTest {

    private Context context;
    private final Executor direct = Runnable::run;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PDFBoxResourceLoader.init(context);
    }

    private File pdf(int pages) throws IOException {
        File f = File.createTempFile("input", ".pdf", context.getCacheDir());
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) doc.addPage(new PDPage(PDRectangle.A5));
            doc.save(f);
        }
        return f;
    }

    private File output() throws IOException {
        return File.createTempFile("out", ".pdf", context.getCacheDir());
    }

    private ListenableWorker.Result run(Data data) {
        PdfOpWorker worker = TestWorkerBuilder.from(context, PdfOpWorker.class, direct).setInputData(data).build();
        return worker.doWork();
    }

    private static int pages(File f, String password) throws IOException {
        try (PDDocument doc = password == null ? PDDocument.load(f) : PDDocument.load(f, password)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    public void mergeWritesTheOutput() throws IOException {
        File a = pdf(2);
        File b = pdf(1);
        File out = output();
        ListenableWorker.Result r = run(new Data.Builder()
                .putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_MERGE)
                .putStringArray(PdfOpWorker.KEY_INPUTS, new String[]{Uri.fromFile(a).toString(), Uri.fromFile(b).toString()})
                .putString(PdfOpWorker.KEY_OUTPUT, Uri.fromFile(out).toString())
                .build());
        assertTrue(r instanceof ListenableWorker.Result.Success);
        assertEquals(3, pages(out, null));
        assertEquals(1, ((ListenableWorker.Result.Success) r).getOutputData().getInt(PdfOpWorker.KEY_RESULT_COUNT, 0));
    }

    @Test
    public void organizeFollowsThePlan() throws IOException {
        File in = pdf(3);
        File out = output();
        ListenableWorker.Result r = run(new Data.Builder()
                .putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_ORGANIZE)
                .putStringArray(PdfOpWorker.KEY_INPUTS, new String[]{Uri.fromFile(in).toString()})
                .putString(PdfOpWorker.KEY_OUTPUT, Uri.fromFile(out).toString())
                .putString(PdfOpWorker.KEY_PLAN, "2:0,0:90")
                .build());
        assertTrue(r instanceof ListenableWorker.Result.Success);
        assertEquals(2, pages(out, null));
    }

    @Test
    public void protectCompressAndUnprotect() throws IOException {
        File in = pdf(2);
        File protectedOut = output();
        assertTrue(run(new Data.Builder()
                .putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_PROTECT)
                .putStringArray(PdfOpWorker.KEY_INPUTS, new String[]{Uri.fromFile(in).toString()})
                .putString(PdfOpWorker.KEY_OUTPUT, Uri.fromFile(protectedOut).toString())
                .putString(PdfOpWorker.KEY_USER_PASSWORD, "1234")
                .putBoolean(PdfOpWorker.KEY_ALLOW_PRINT, true)
                .build()) instanceof ListenableWorker.Result.Success);
        assertEquals(2, pages(protectedOut, "1234"));

        // Wrong password: explicit error code.
        ListenableWorker.Result wrong = run(new Data.Builder()
                .putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_UNPROTECT)
                .putStringArray(PdfOpWorker.KEY_INPUTS, new String[]{Uri.fromFile(protectedOut).toString()})
                .putString(PdfOpWorker.KEY_OUTPUT, Uri.fromFile(output()).toString())
                .putString(PdfOpWorker.KEY_PASSWORD, "nope")
                .build());
        assertTrue(wrong instanceof ListenableWorker.Result.Failure);
        assertEquals(PdfOpWorker.ERROR_PASSWORD,
                ((ListenableWorker.Result.Failure) wrong).getOutputData().getString(PdfOpWorker.KEY_ERROR));

        // Compressing an encrypted file with its password.
        File compressed = output();
        assertTrue(run(new Data.Builder()
                .putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_COMPRESS)
                .putStringArray(PdfOpWorker.KEY_INPUTS, new String[]{Uri.fromFile(protectedOut).toString()})
                .putString(PdfOpWorker.KEY_OUTPUT, Uri.fromFile(compressed).toString())
                .putString(PdfOpWorker.KEY_PASSWORD, "1234")
                .putString(PdfOpWorker.KEY_LEVEL, "HIGH")
                .build()) instanceof ListenableWorker.Result.Success);
        // The protection is kept, with the same password.
        assertEquals(2, pages(compressed, "1234"));
        try (PDDocument doc = PDDocument.load(compressed, "1234")) {
            assertTrue(doc.isEncrypted());
        }

        File clear = output();
        assertTrue(run(new Data.Builder()
                .putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_UNPROTECT)
                .putStringArray(PdfOpWorker.KEY_INPUTS, new String[]{Uri.fromFile(protectedOut).toString()})
                .putString(PdfOpWorker.KEY_OUTPUT, Uri.fromFile(clear).toString())
                .putString(PdfOpWorker.KEY_PASSWORD, "1234")
                .build()) instanceof ListenableWorker.Result.Success);
        assertEquals(2, pages(clear, null));
    }

    @Test
    public void invalidRequestsFail() {
        ListenableWorker.Result noOutput = run(new Data.Builder().putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_MERGE).build());
        assertTrue(noOutput instanceof ListenableWorker.Result.Failure);
        ListenableWorker.Result unknown = run(new Data.Builder()
                .putString(PdfOpWorker.KEY_OP, "bogus")
                .putString(PdfOpWorker.KEY_OUTPUT, "file:///nonexistent/x.pdf")
                .build());
        assertTrue(unknown instanceof ListenableWorker.Result.Failure);
        ListenableWorker.Result missingFile = run(new Data.Builder()
                .putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_COMPRESS)
                .putStringArray(PdfOpWorker.KEY_INPUTS, new String[]{"file:///nonexistent/in.pdf"})
                .putString(PdfOpWorker.KEY_OUTPUT, "file:///nonexistent/x.pdf")
                .build());
        assertEquals(PdfOpWorker.ERROR_IO,
                ((ListenableWorker.Result.Failure) missingFile).getOutputData().getString(PdfOpWorker.KEY_ERROR));
    }
}
