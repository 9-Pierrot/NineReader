package com.ninereader.util;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global executor pools: disk/database I/O, background computation and the main thread.
 */
public final class AppExecutors {

    private static volatile AppExecutors instance;

    private final ExecutorService diskIo;
    private final ExecutorService background;
    private final Executor mainThread;

    private AppExecutors(ExecutorService diskIo, ExecutorService background, Executor mainThread) {
        this.diskIo = diskIo;
        this.background = background;
        this.mainThread = mainThread;
    }

    public static AppExecutors get() {
        if (instance == null) {
            synchronized (AppExecutors.class) {
                if (instance == null) {
                    instance = new AppExecutors(
                            Executors.newSingleThreadExecutor(named("nr-disk")),
                            Executors.newFixedThreadPool(2, named("nr-bg")),
                            new MainThreadExecutor());
                }
            }
        }
        return instance;
    }

    /** Replaces the executors (tests): every task runs synchronously on the calling thread. */
    public static void setDirectForTests() {
        Executor direct = Runnable::run;
        instance = new AppExecutors(new DirectExecutorService(), new DirectExecutorService(), direct);
    }

    /** Restores the real executors (tests). */
    public static void resetForTests() {
        instance = null;
    }

    public ExecutorService diskIo() {
        return diskIo;
    }

    public ExecutorService background() {
        return background;
    }

    public Executor mainThread() {
        return mainThread;
    }

    static ThreadFactory named(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
    }

    private static final class MainThreadExecutor implements Executor {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                command.run();
            } else {
                handler.post(command);
            }
        }
    }
}
