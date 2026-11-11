package com.ninereader.reader.tts;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Read-aloud state shared between the reader screen and {@link TtsService} (LEC-13). Uses the
 * platform TextToSpeech engine only (no generative AI).
 */
public final class TtsController {

    /** Provides page texts to the speech service. */
    public interface TextProvider {
        int pageCount();

        @WorkerThread
        @NonNull
        String pageText(int page);

        @NonNull
        String title();
    }

    /** Playback state. */
    public static final class State {
        public static final int STOPPED = 0;
        public static final int PLAYING = 1;
        public static final int PAUSED = 2;

        public final int status;
        public final int page;
        /** Character range of the sentence being read in the page text. */
        public final int start;
        public final int end;

        public State(int status, int page, int start, int end) {
            this.status = status;
            this.page = page;
            this.start = start;
            this.end = end;
        }

        public boolean isActive() {
            return status != STOPPED;
        }
    }

    private static final MutableLiveData<State> STATE = new MutableLiveData<>(new State(State.STOPPED, 0, 0, 0));
    private static final MutableLiveData<Boolean> ENGINE_ERROR = new MutableLiveData<>(false);
    @Nullable
    private static volatile TextProvider provider;
    private static volatile float rate = 1f;
    private static volatile int startPage;
    private static volatile int startChar;

    private TtsController() {
    }

    public static LiveData<State> state() {
        return STATE;
    }

    public static LiveData<Boolean> engineError() {
        return ENGINE_ERROR;
    }

    static void post(@NonNull State s) {
        STATE.postValue(s);
    }

    static void postEngineError() {
        ENGINE_ERROR.postValue(true);
    }

    @Nullable
    static TextProvider provider() {
        return provider;
    }

    static int startPage() {
        return startPage;
    }

    static int startChar() {
        return startChar;
    }

    public static float rate() {
        return rate;
    }

    public static void start(@NonNull Context context, @NonNull TextProvider textProvider, int page, int fromChar, float speechRate) {
        provider = textProvider;
        startPage = page;
        startChar = Math.max(0, fromChar);
        rate = speechRate;
        ENGINE_ERROR.setValue(false);
        Intent i = new Intent(context, TtsService.class).setAction(TtsService.ACTION_START);
        try {
            ContextCompat.startForegroundService(context, i);
        } catch (IllegalStateException | SecurityException e) {
            // Not allowed from the background (Android 12+): report it instead of crashing.
            ENGINE_ERROR.setValue(true);
        }
    }

    public static void pause(@NonNull Context context) {
        send(context, TtsService.ACTION_PAUSE);
    }

    public static void resume(@NonNull Context context) {
        send(context, TtsService.ACTION_RESUME);
    }

    public static void stop(@NonNull Context context) {
        State s = STATE.getValue();
        if (s == null || !s.isActive()) return;
        send(context, TtsService.ACTION_STOP);
    }

    public static void setRate(@NonNull Context context, float speechRate) {
        rate = speechRate;
        State s = STATE.getValue();
        if (s != null && s.isActive()) send(context, TtsService.ACTION_RATE);
    }

    /** Called by the reader when the document closes. */
    public static void detach(@NonNull Context context, @NonNull TextProvider textProvider) {
        if (provider == textProvider) {
            stop(context);
            provider = null;
        }
    }

    private static void send(Context context, String action) {
        Intent i = new Intent(context, TtsService.class).setAction(action);
        try {
            context.startService(i);
        } catch (IllegalStateException e) {
            // App in background without a running service: nothing to control.
        }
    }
}
