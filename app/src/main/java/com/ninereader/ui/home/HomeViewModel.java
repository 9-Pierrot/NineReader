package com.ninereader.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.ninereader.NineReaderApp;
import com.ninereader.data.DocumentRepository;
import com.ninereader.data.entity.DocumentEntity;

import java.util.Calendar;
import java.util.List;

/** Home screen state (§3.1): resume reading, recent files, favourites, quick statistics. */
public class HomeViewModel extends AndroidViewModel {

    private final DocumentRepository repo;
    private final LiveData<List<DocumentEntity>> recents;
    private final LiveData<List<DocumentEntity>> favorites;
    private final LiveData<DocumentEntity> last;
    private final LiveData<Long> todayTime;
    private final LiveData<Integer> todayPages;
    private final LiveData<Long> weekTime;

    public HomeViewModel(@NonNull Application app) {
        super(app);
        repo = NineReaderApp.from(app).documents();
        recents = repo.recents();
        favorites = repo.favorites();
        last = repo.lastOpenedLive();
        long startOfDay = startOfDay();
        todayTime = repo.readingTimeSince(startOfDay);
        todayPages = repo.pagesReadSince(startOfDay);
        weekTime = repo.readingTimeSince(startOfDay - 6L * 24 * 60 * 60 * 1000);
    }

    static long startOfDay() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    public LiveData<List<DocumentEntity>> recents() {
        return recents;
    }

    public LiveData<List<DocumentEntity>> favorites() {
        return favorites;
    }

    public LiveData<DocumentEntity> lastOpened() {
        return last;
    }

    public LiveData<Long> todayTime() {
        return todayTime;
    }

    public LiveData<Integer> todayPages() {
        return todayPages;
    }

    public LiveData<Long> weekTime() {
        return weekTime;
    }

    public void toggleFavorite(@NonNull DocumentEntity doc) {
        repo.setFavorite(doc.id, !doc.favorite);
    }
}
