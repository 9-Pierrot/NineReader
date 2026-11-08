package com.ninereader.ui.reader;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.databinding.DialogRsvpBinding;
import com.ninereader.reader.RsvpEngine;

import java.util.ArrayList;
import java.util.List;

/** Speed reading (RAP-04): words shown one at a time at 200-600 words/min with a fixation point. */
public class RsvpDialogFragment extends DialogFragment {

    public static final String TAG = "rsvp";

    private DialogRsvpBinding binding;
    private ReaderViewModel vm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<String> words = new ArrayList<>();
    private int index;
    private int page;
    private boolean playing;
    private boolean loading;
    private int wpm;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!playing || binding == null) return;
            if (index >= words.size()) {
                if (!loadNextPage()) pause();
                return;
            }
            String w = words.get(index++);
            showWord(w);
            handler.postDelayed(this, RsvpEngine.durationMs(w, wpm));
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.Theme_NineReader);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = DialogRsvpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(ReaderViewModel.class);
        wpm = RsvpEngine.clampWpm(NineReaderApp.from(requireContext()).prefs().rsvpWpm());
        page = vm.page() - 1;
        binding.rsvpToolbar.setNavigationOnClickListener(v -> dismiss());
        binding.rsvpSpeed.setValue(Math.round(wpm / 25f) * 25f);
        binding.rsvpSpeed.addOnChangeListener((s, value, fromUser) -> {
            wpm = Math.round(value);
            NineReaderApp.from(requireContext()).prefs().setRsvpWpm(wpm);
            updateInfo();
        });
        binding.rsvpToggle.setOnClickListener(v -> toggle());
        binding.rsvpStage.setOnClickListener(v -> toggle());
        loadNextPage();
        updateInfo();
    }

    private void toggle() {
        if (playing) {
            pause();
        } else {
            play();
        }
    }

    private void play() {
        playing = true;
        binding.rsvpToggle.setImageResource(R.drawable.ic_pause);
        binding.rsvpToggle.setContentDescription(getString(R.string.tts_pause));
        binding.rsvpHint.setVisibility(View.INVISIBLE);
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void pause() {
        playing = false;
        handler.removeCallbacks(tick);
        if (binding == null) return;
        binding.rsvpToggle.setImageResource(R.drawable.ic_play);
        binding.rsvpToggle.setContentDescription(getString(R.string.tts_resume));
        binding.rsvpHint.setVisibility(View.VISIBLE);
    }

    /** Appends the words of the next page; returns false at the end of the document. */
    private boolean loadNextPage() {
        if (loading) return true;
        if (page + 1 >= vm.pageCount()) return false;
        loading = true;
        int next = page + 1;
        vm.pageText(next, text -> {
            loading = false;
            if (binding == null) return;
            page = next;
            words.clear();
            index = 0;
            words.addAll(RsvpEngine.words(text));
            updateInfo();
            if (words.isEmpty()) {
                if (!loadNextPage()) pause();
            } else if (playing) {
                handler.removeCallbacks(tick);
                handler.post(tick);
            } else {
                showWord(words.get(0));
            }
        });
        return true;
    }

    private void showWord(String w) {
        int pivot = RsvpEngine.pivot(w);
        // Pad so that the pivot letter sits at the centre of the monospace line.
        int left = pivot;
        int right = w.length() - pivot - 1;
        int pad = Math.max(0, right - left);
        int padRight = Math.max(0, left - right);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pad; i++) sb.append(' ');
        int pivotPos = sb.length() + pivot;
        sb.append(w);
        for (int i = 0; i < padRight; i++) sb.append(' ');
        SpannableString s = new SpannableString(sb);
        s.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.rsvp_focus)),
                pivotPos, pivotPos + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.rsvpWord.setText(s);
        binding.rsvpWord.setContentDescription(w);
    }

    private void updateInfo() {
        if (binding == null) return;
        binding.rsvpInfo.setText(getString(R.string.rsvp_info, wpm, Math.max(1, page + 1), vm.pageCount()));
    }

    @Override
    public void onPause() {
        super.onPause();
        pause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(tick);
        super.onDestroyView();
        binding = null;
    }
}
