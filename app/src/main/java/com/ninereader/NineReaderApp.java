package com.ninereader;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.ninereader.data.AppDatabase;
import com.ninereader.data.CollectionRepository;
import com.ninereader.data.DocumentRepository;
import com.ninereader.data.FolderRepository;
import com.ninereader.data.SignatureRepository;
import com.ninereader.data.TrashRepository;
import com.ninereader.util.AppExecutors;
import com.ninereader.util.Prefs;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

/** Application entry point and minimal service locator (no DI framework, see §6). */
public class NineReaderApp extends Application {

    public static final String CHANNEL_TTS = "tts";
    public static final String CHANNEL_TASKS = "tasks";

    private static NineReaderApp instance;

    private volatile DocumentRepository documents;
    private volatile CollectionRepository collections;
    private volatile SignatureRepository signatures;
    private volatile TrashRepository trash;
    private volatile FolderRepository folders;
    private Prefs prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = new Prefs(this);
        Prefs.applyAppTheme(prefs.appTheme());
        createNotificationChannels();
        // PdfBox needs its resources (fonts, glyph lists) loaded once, off the main thread.
        AppExecutors.get().background().execute(() -> PDFBoxResourceLoader.init(getApplicationContext()));
        // Empty the trash of items older than 30 days (BIB-04).
        AppExecutors.get().diskIo().execute(() -> trash().purgeExpired(System.currentTimeMillis()));
    }

    @NonNull
    public static NineReaderApp get() {
        return instance;
    }

    @NonNull
    public static NineReaderApp from(@NonNull Context context) {
        return (NineReaderApp) context.getApplicationContext();
    }

    @NonNull
    public AppDatabase db() {
        return AppDatabase.get(this);
    }

    @NonNull
    public Prefs prefs() {
        return prefs;
    }

    @NonNull
    public DocumentRepository documents() {
        if (documents == null) {
            synchronized (this) {
                if (documents == null) documents = new DocumentRepository(db());
            }
        }
        return documents;
    }

    @NonNull
    public CollectionRepository collections() {
        if (collections == null) {
            synchronized (this) {
                if (collections == null) collections = new CollectionRepository(db());
            }
        }
        return collections;
    }

    @NonNull
    public SignatureRepository signatures() {
        if (signatures == null) {
            synchronized (this) {
                if (signatures == null) signatures = new SignatureRepository(this, db());
            }
        }
        return signatures;
    }

    @NonNull
    public TrashRepository trash() {
        if (trash == null) {
            synchronized (this) {
                if (trash == null) trash = new TrashRepository(this, db());
            }
        }
        return trash;
    }

    @NonNull
    public FolderRepository folders() {
        if (folders == null) {
            synchronized (this) {
                if (folders == null) folders = new FolderRepository(db());
            }
        }
        return folders;
    }

    private void createNotificationChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel tts = new NotificationChannel(CHANNEL_TTS,
                getString(R.string.channel_tts), NotificationManager.IMPORTANCE_LOW);
        NotificationChannel tasks = new NotificationChannel(CHANNEL_TASKS,
                getString(R.string.channel_tasks), NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(tts);
        nm.createNotificationChannel(tasks);
    }
}
