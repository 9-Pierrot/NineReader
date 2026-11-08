package com.ninereader.data;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.concurrent.Executor;

/** In-memory Room database with synchronous executors. */
public abstract class DbTestBase {

    @Rule
    public InstantTaskExecutorRule instant = new InstantTaskExecutorRule();

    protected AppDatabase db;
    protected Context context;
    protected final Executor direct = Runnable::run;

    @Before
    public void openDb() {
        com.ninereader.util.AppExecutors.setDirectForTests();
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .setQueryExecutor(direct)
                .setTransactionExecutor(direct)
                .build();
    }

    @After
    public void closeDb() {
        db.close();
    }

    /** Reads the current value of a LiveData (observing it once). */
    protected static <T> T value(LiveData<T> live) {
        Object[] holder = new Object[1];
        androidx.lifecycle.Observer<T> obs = v -> holder[0] = v;
        live.observeForever(obs);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        live.removeObserver(obs);
        @SuppressWarnings("unchecked")
        T t = (T) holder[0];
        return t;
    }
}
