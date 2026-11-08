package com.ninereader.ui.reader;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.databinding.DialogReflowBinding;
import com.ninereader.reader.ReaderTheme;
import com.ninereader.reader.ReflowFormatter;
import com.ninereader.util.Prefs;

import java.util.List;

/** Liquid mode (LEC-12): the text of the page re-flowed to the screen width, adjustable font and spacing. */
public class ReflowDialogFragment extends DialogFragment {

    public static final String TAG = "reflow";
    private static final String STATE_PAGE = "page";
    private static final float[] SPACINGS = {1.2f, 1.4f, 1.7f, 2.0f};

    private DialogReflowBinding binding;
    private ReaderViewModel vm;
    private Prefs prefs;
    private int page;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.Theme_NineReader);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        binding = DialogReflowBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(requireActivity()).get(ReaderViewModel.class);
        prefs = NineReaderApp.from(requireContext()).prefs();
        page = state != null ? state.getInt(STATE_PAGE) : vm.page();
        binding.reflowToolbar.setNavigationOnClickListener(v -> dismiss());
        binding.reflowOriginal.setOnClickListener(v -> dismiss());
        binding.reflowPrev.setOnClickListener(v -> show(page - 1));
        binding.reflowNext.setOnClickListener(v -> show(page + 1));
        binding.reflowSmaller.setOnClickListener(v -> {
            prefs.setReflowFontSize(Math.max(12, prefs.reflowFontSize() - 2));
            applyStyle();
        });
        binding.reflowBigger.setOnClickListener(v -> {
            prefs.setReflowFontSize(Math.min(40, prefs.reflowFontSize() + 2));
            applyStyle();
        });
        binding.reflowSpacing.setOnClickListener(v -> {
            float cur = prefs.reflowLineSpacing();
            int idx = 0;
            for (int i = 0; i < SPACINGS.length; i++) if (Math.abs(SPACINGS[i] - cur) < 0.05f) idx = i;
            prefs.setReflowLineSpacing(SPACINGS[(idx + 1) % SPACINGS.length]);
            applyStyle();
        });
        applyTheme();
        applyStyle();
        show(page);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_PAGE, page);
    }

    private void applyTheme() {
        ReaderTheme theme = vm.theme() == null ? ReaderTheme.LIGHT : vm.theme();
        int bg;
        int fg;
        switch (theme) {
            case DARK:
            case INVERTED:
                bg = 0xFF121212;
                fg = 0xFFE3E3E3;
                break;
            case SEPIA:
                bg = 0xFFF4ECD8;
                fg = 0xFF5B4636;
                break;
            default:
                bg = 0xFFFFFFFF;
                fg = 0xFF1B1B1F;
                break;
        }
        binding.reflowScroll.setBackgroundColor(bg);
        binding.reflowText.setTextColor(fg);
    }

    private void applyStyle() {
        binding.reflowText.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.reflowFontSize());
        binding.reflowText.setLineSpacing(0f, prefs.reflowLineSpacing());
    }

    private void show(int p) {
        int count = vm.pageCount();
        if (count == 0) return;
        page = Math.max(0, Math.min(count - 1, p));
        binding.reflowPage.setText(getString(R.string.page_of, page + 1, count));
        binding.reflowPrev.setEnabled(page > 0);
        binding.reflowNext.setEnabled(page < count - 1);
        int requested = page;
        vm.pageText(page, text -> {
            if (binding == null || requested != page) return;
            if (!ReflowFormatter.hasUsableText(text)) {
                binding.reflowText.setText(R.string.reflow_no_text);
                binding.reflowOriginal.setVisibility(View.VISIBLE);
                return;
            }
            binding.reflowOriginal.setVisibility(View.GONE);
            List<String> paragraphs = ReflowFormatter.paragraphs(text);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            for (String para : paragraphs) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append(para);
            }
            binding.reflowText.setText(sb);
            binding.reflowScroll.scrollTo(0, 0);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
