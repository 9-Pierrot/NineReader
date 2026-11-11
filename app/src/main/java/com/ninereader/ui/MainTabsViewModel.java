package com.ninereader.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/** Current main tab (Home, Files, Create, Tools), shared by the bottom bar / rail and the swipeable pager. */
public class MainTabsViewModel extends ViewModel {

    public static final int HOME = 0;
    public static final int FILES = 1;
    public static final int CREATE = 2;
    public static final int TOOLS = 3;
    public static final int COUNT = 4;

    private final MutableLiveData<Integer> tab = new MutableLiveData<>(HOME);
    /** Scroll position of the pages (fractional tab index), for the sliding pill of the bottom bar. */
    private final MutableLiveData<Float> scroll = new MutableLiveData<>((float) HOME);

    @NonNull
    public LiveData<Float> scroll() {
        return scroll;
    }

    public void setScroll(float position) {
        scroll.setValue(position);
    }

    @NonNull
    public LiveData<Integer> tab() {
        return tab;
    }

    public int current() {
        Integer t = tab.getValue();
        return t == null ? HOME : t;
    }

    public void select(int index) {
        if (index >= 0 && index < COUNT && index != current()) tab.setValue(index);
    }
}
