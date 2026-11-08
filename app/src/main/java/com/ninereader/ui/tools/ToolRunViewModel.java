package com.ninereader.ui.tools;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Inputs and running operation of the tool screen (survives rotation). */
public class ToolRunViewModel extends ViewModel {

    final List<Uri> inputs = new ArrayList<>();
    @Nullable
    UUID workId;
    @Nullable
    Uri output;
    final MutableLiveData<String> estimate = new MutableLiveData<>("");
    /** Pending parameters while the user picks the output location. */
    @Nullable
    androidx.work.Data.Builder pendingData;
}
