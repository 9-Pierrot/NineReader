package com.ninereader.reader.tts;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.reader.SentenceSplitter;
import com.ninereader.ui.reader.ReaderActivity;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service reading the document aloud with the platform speech engine, so reading goes on
 * with the screen off (LEC-13). Sentences are spoken one at a time and reported to
 * {@link TtsController} for highlighting.
 */
public class TtsService extends Service implements TextToSpeech.OnInitListener {

    static final String ACTION_START = "com.ninereader.tts.START";
    static final String ACTION_PAUSE = "com.ninereader.tts.PAUSE";
    static final String ACTION_RESUME = "com.ninereader.tts.RESUME";
    static final String ACTION_STOP = "com.ninereader.tts.STOP";
    static final String ACTION_RATE = "com.ninereader.tts.RATE";

    private static final int NOTIFICATION_ID = 42;

    @Nullable
    private TextToSpeech tts;
    private boolean ready;
    private boolean pendingStart;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    private int page;
    private List<SentenceSplitter.Sentence> sentences = Collections.emptyList();
    private int sentenceIndex;
    private int status = TtsController.State.STOPPED;
    private int generation;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        tts = new TextToSpeech(this, this);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_START.equals(action)) {
            startForegroundCompat();
            page = TtsController.startPage();
            status = TtsController.State.PLAYING;
            if (ready) {
                loadPage(page, TtsController.startChar());
            } else {
                pendingStart = true;
            }
        } else if (ACTION_PAUSE.equals(action)) {
            status = TtsController.State.PAUSED;
            generation++;
            if (tts != null) tts.stop();
            publish();
            updateNotification();
        } else if (ACTION_RESUME.equals(action)) {
            status = TtsController.State.PLAYING;
            speakCurrent();
            updateNotification();
        } else if (ACTION_RATE.equals(action)) {
            if (tts != null) tts.setSpeechRate(TtsController.rate());
            if (status == TtsController.State.PLAYING) speakCurrent();
        } else if (ACTION_STOP.equals(action)) {
            stopReading();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onInit(int result) {
        if (result != TextToSpeech.SUCCESS || tts == null) {
            TtsController.postEngineError();
            stopReading();
            return;
        }
        ready = true;
        Locale locale = Locale.getDefault();
        if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) tts.setLanguage(locale);
        tts.setSpeechRate(TtsController.rate());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // nothing
            }

            @Override
            public void onDone(String utteranceId) {
                main.post(() -> onUtteranceDone(utteranceId));
            }

            @Override
            public void onError(String utteranceId) {
                main.post(() -> onUtteranceDone(utteranceId));
            }
        });
        if (pendingStart) {
            pendingStart = false;
            loadPage(page, TtsController.startChar());
        }
    }

    private void loadPage(int p, int fromChar) {
        TtsController.TextProvider provider = TtsController.provider();
        if (provider == null || p >= provider.pageCount()) {
            stopReading();
            return;
        }
        int gen = ++generation;
        loader.execute(() -> {
            String text;
            try {
                text = provider.pageText(p);
            } catch (RuntimeException e) {
                text = "";
            }
            List<SentenceSplitter.Sentence> list = SentenceSplitter.split(text);
            main.post(() -> {
                if (gen != generation) return;
                page = p;
                sentences = list;
                sentenceIndex = 0;
                while (sentenceIndex < sentences.size() && sentences.get(sentenceIndex).end <= fromChar) sentenceIndex++;
                if (sentences.isEmpty() || sentenceIndex >= sentences.size()) {
                    loadPage(p + 1, 0); // empty page (scan, image): skip
                } else if (status == TtsController.State.PLAYING) {
                    speakCurrent();
                } else {
                    publish();
                }
            });
        });
    }

    private void speakCurrent() {
        if (tts == null || !ready || sentenceIndex >= sentences.size()) return;
        int gen = ++generation;
        SentenceSplitter.Sentence s = sentences.get(sentenceIndex);
        publish();
        tts.speak(s.text, TextToSpeech.QUEUE_FLUSH, null, gen + ":" + sentenceIndex);
    }

    private void onUtteranceDone(String utteranceId) {
        if (status != TtsController.State.PLAYING || utteranceId == null) return;
        String[] parts = utteranceId.split(":");
        if (parts.length != 2 || Integer.parseInt(parts[0]) != generation) return;
        sentenceIndex++;
        if (sentenceIndex < sentences.size()) {
            speakCurrent();
        } else {
            loadPage(page + 1, 0);
        }
    }

    private void publish() {
        if (sentenceIndex < sentences.size()) {
            SentenceSplitter.Sentence s = sentences.get(sentenceIndex);
            TtsController.post(new TtsController.State(status, page, s.start, s.end));
        } else {
            TtsController.post(new TtsController.State(status, page, 0, 0));
        }
    }

    private void stopReading() {
        status = TtsController.State.STOPPED;
        generation++;
        if (tts != null) tts.stop();
        TtsController.post(new TtsController.State(TtsController.State.STOPPED, page, 0, 0));
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void startForegroundCompat() {
        Notification n = buildNotification();
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type);
    }

    private void updateNotification() {
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        if (nm != null && status != TtsController.State.STOPPED) {
            try {
                nm.notify(NOTIFICATION_ID, buildNotification());
            } catch (SecurityException ignored) {
                // Notifications denied: the service keeps running.
            }
        }
    }

    @NonNull
    private Notification buildNotification() {
        TtsController.TextProvider provider = TtsController.provider();
        String title = provider == null ? getString(R.string.app_name) : provider.title();
        Intent open = new Intent(this, ReaderActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean paused = status == TtsController.State.PAUSED;
        PendingIntent toggle = PendingIntent.getService(this, 1,
                new Intent(this, TtsService.class).setAction(paused ? ACTION_RESUME : ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, TtsService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, NineReaderApp.CHANNEL_TTS)
                .setSmallIcon(R.drawable.ic_volume)
                .setContentTitle(title)
                .setContentText(getString(R.string.tts_notification_text))
                .setContentIntent(content)
                .setOngoing(!paused)
                .setSilent(true)
                .addAction(paused ? R.drawable.ic_play : R.drawable.ic_pause,
                        getString(paused ? R.string.tts_resume : R.string.tts_pause), toggle)
                .addAction(R.drawable.ic_stop, getString(R.string.tts_stop), stop)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    @Override
    public void onDestroy() {
        generation++;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        loader.shutdownNow();
        if (status != TtsController.State.STOPPED) {
            TtsController.post(new TtsController.State(TtsController.State.STOPPED, page, 0, 0));
        }
        super.onDestroy();
    }
}
