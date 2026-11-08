package com.ninereader.util;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

/** Executor service running every task synchronously on the caller thread (used by tests). */
public final class DirectExecutorService extends AbstractExecutorService {

    private volatile boolean shutdown;

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @NonNull
    @Override
    public List<Runnable> shutdownNow() {
        shutdown = true;
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, @NonNull TimeUnit unit) {
        return true;
    }

    @Override
    public void execute(@NonNull Runnable command) {
        command.run();
    }
}
