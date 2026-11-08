package com.ninereader.data;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.ninereader.data.entity.SignatureEntity;
import com.ninereader.util.AppExecutors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Saved signatures and initials (SIG-03, SIG-04), stored as PNG files in internal storage. */
public class SignatureRepository {

    private final Context context;
    private final AppDatabase db;

    public SignatureRepository(@NonNull Context context, @NonNull AppDatabase db) {
        this.context = context.getApplicationContext();
        this.db = db;
    }

    public LiveData<List<SignatureEntity>> all() {
        return db.signatures().observeAll();
    }

    /** Saves a signature bitmap. Must be called off the main thread. */
    @WorkerThread
    public long save(@NonNull Bitmap bitmap, @NonNull String kind) throws IOException {
        File dir = new File(context.getFilesDir(), "signatures");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
        File f = new File(dir, "sig_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(f)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IOException("PNG encoding failed");
        }
        SignatureEntity s = new SignatureEntity();
        s.imagePath = f.getAbsolutePath();
        s.kind = kind;
        s.createdAt = System.currentTimeMillis();
        return db.signatures().insert(s);
    }

    public void delete(@NonNull SignatureEntity signature) {
        AppExecutors.get().diskIo().execute(() -> {
            db.signatures().delete(signature.id);
            File f = new File(signature.imagePath);
            if (f.exists() && !f.delete()) f.deleteOnExit();
        });
    }
}
