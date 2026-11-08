package com.ninereader.ui.tools;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ninereader.R;
import com.ninereader.databinding.FragmentToolRunBinding;
import com.ninereader.pdfops.CompressionLevel;
import com.ninereader.pdfops.PdfOpWorker;
import com.ninereader.pdfops.PdfOps;
import com.ninereader.ui.common.DocumentOpener;
import com.ninereader.util.AppExecutors;
import com.ninereader.util.FileUtils;
import com.ninereader.util.ShareUtils;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Generic screen of the PDF operations (ORG-02/03/04, CRE-01..03, CNV-01/02, CMP-01, SEC-01/02):
 * input files, options, output location (always chosen by the user), progress and result.
 */
public class ToolRunFragment extends Fragment {

    public static final String ARG_TOOL = "tool";
    public static final String ARG_URI = "uri";

    private FragmentToolRunBinding b;
    private ToolRunViewModel vm;
    private ToolCatalog.Tool tool;
    // Option widgets (created per tool)
    @Nullable
    private TextInputEditText rangeField;
    @Nullable
    private TextInputEditText passwordField;
    @Nullable
    private TextInputEditText userPassword;
    @Nullable
    private TextInputEditText userPasswordConfirm;
    @Nullable
    private TextInputEditText ownerPassword;
    @Nullable
    private TextInputEditText pagesField;
    @Nullable
    private ChipGroup choice1;
    @Nullable
    private ChipGroup choice2;
    @Nullable
    private ChipGroup choice3;
    @Nullable
    private MaterialSwitch switch1;
    @Nullable
    private MaterialSwitch switch2;
    @Nullable
    private Slider[] marginSliders;
    @Nullable
    private TextView estimateView;

    private final ActivityResultLauncher<String[]> pickSingle = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                DocumentOpener.persist(requireContext(), uri);
                vm.inputs.clear();
                vm.inputs.add(uri);
                renderInputs();
            });
    private final ActivityResultLauncher<String[]> pickMultiple = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null || uris.isEmpty()) return;
                for (Uri u : uris) {
                    DocumentOpener.persist(requireContext(), u);
                    vm.inputs.add(u);
                }
                renderInputs();
            });
    private final ActivityResultLauncher<String> createPdf = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), this::onOutputChosen);
    private final ActivityResultLauncher<String> createText = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/plain"), this::onOutputChosen);
    private final ActivityResultLauncher<Uri> chooseFolder = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), this::onOutputChosen);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentToolRunBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(ToolRunViewModel.class);
        Bundle args = getArguments();
        tool = ToolCatalog.byId(args == null ? null : args.getString(ARG_TOOL));
        if (tool == null) {
            NavHostFragment.findNavController(this).popBackStack();
            return;
        }
        String preset = args.getString(ARG_URI);
        if (preset != null && vm.inputs.isEmpty()) vm.inputs.add(Uri.parse(preset));
        b.toolbar.setTitle(tool.title);
        b.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        b.description.setText(tool.description);
        b.pickInput.setOnClickListener(v -> pickInputs());
        b.run.setOnClickListener(v -> start());
        buildOptions();
        renderInputs();
        if (vm.workId != null) observeWork();
        vm.estimate.observe(getViewLifecycleOwner(), s -> {
            if (estimateView != null) estimateView.setText(s);
        });
    }

    private boolean multipleInputs() {
        return "merge".equals(tool.id) || "images_to_pdf".equals(tool.id);
    }

    private boolean needsInput() {
        return !"blank".equals(tool.id);
    }

    private void pickInputs() {
        switch (tool.id) {
            case "merge":
                pickMultiple.launch(new String[]{"application/pdf", "image/*"});
                break;
            case "images_to_pdf":
                pickMultiple.launch(new String[]{"image/*"});
                break;
            default:
                pickSingle.launch(new String[]{"application/pdf"});
                break;
        }
    }

    private void renderInputs() {
        b.inputsCard.setVisibility(needsInput() ? View.VISIBLE : View.GONE);
        b.inputsList.removeAllViews();
        b.pickInput.setText(multipleInputs() ? R.string.add_files : (vm.inputs.isEmpty() ? R.string.choose_file : R.string.change_file));
        for (int i = 0; i < vm.inputs.size(); i++) {
            int index = i;
            Uri uri = vm.inputs.get(i);
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_panel_row, b.inputsList, false);
            ((TextView) row.findViewById(R.id.title)).setText(FileUtils.displayName(requireContext(), uri));
            TextView pos = row.findViewById(R.id.page);
            pos.setText(multipleInputs() ? String.valueOf(i + 1) : "");
            ImageButton remove = row.findViewById(R.id.action);
            remove.setVisibility(View.VISIBLE);
            remove.setContentDescription(getString(R.string.remove));
            remove.setOnClickListener(v -> {
                vm.inputs.remove(index);
                renderInputs();
            });
            if (multipleInputs() && i > 0) {
                row.setOnClickListener(v -> {
                    // Tap moves the file up one position (simple reordering).
                    Uri moved = vm.inputs.remove(index);
                    vm.inputs.add(index - 1, moved);
                    renderInputs();
                });
                row.setContentDescription(getString(R.string.move_up_hint));
            }
            b.inputsList.addView(row);
        }
        if (multipleInputs() && vm.inputs.size() > 1) {
            TextView hint = new TextView(requireContext());
            hint.setText(R.string.reorder_hint);
            hint.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
            b.inputsList.addView(hint);
        }
        if ("compress".equals(tool.id)) updateEstimate();
    }

    // ------------------------------------------------------------------ options

    private void buildOptions() {
        LinearLayout o = b.options;
        o.removeAllViews();
        switch (tool.id) {
            case "images_to_pdf":
                choice1 = chips(o, R.string.page_format, new int[]{R.string.format_a4, R.string.format_letter, R.string.format_fit}, 0);
                switch1 = toggle(o, R.string.add_margins, true);
                break;
            case "blank":
                choice1 = chips(o, R.string.page_format, new int[]{R.string.format_a4, R.string.format_letter, R.string.format_a5}, 0);
                choice2 = chips(o, R.string.page_pattern, new int[]{R.string.pattern_blank, R.string.pattern_lined, R.string.pattern_grid}, 0);
                switch1 = toggle(o, R.string.landscape, false);
                pagesField = text(o, R.string.page_count, InputType.TYPE_CLASS_NUMBER);
                pagesField.setText("1");
                break;
            case "ocr":
                rangeField = text(o, R.string.page_range, InputType.TYPE_CLASS_TEXT);
                switch1 = toggle(o, R.string.ocr_force, false);
                note(o, R.string.ocr_note);
                passwordField = text(o, R.string.file_password_optional, passwordType());
                break;
            case "to_images":
                rangeField = text(o, R.string.page_range, InputType.TYPE_CLASS_TEXT);
                choice1 = chips(o, R.string.image_format, new int[]{R.string.format_jpeg, R.string.format_png}, 0);
                choice2 = chips(o, R.string.resolution, new int[]{R.string.dpi_72, R.string.dpi_150, R.string.dpi_300}, 1);
                passwordField = text(o, R.string.file_password_optional, passwordType());
                break;
            case "to_text":
            case "extract":
                rangeField = text(o, R.string.page_range, InputType.TYPE_CLASS_TEXT);
                if ("extract".equals(tool.id)) rangeField.setHint(R.string.page_range_example);
                passwordField = text(o, R.string.file_password_optional, passwordType());
                break;
            case "crop":
                choice1 = chips(o, R.string.crop_mode, new int[]{R.string.crop_auto, R.string.crop_manual}, 0);
                marginSliders = new Slider[]{
                        slider(o, R.string.margin_left), slider(o, R.string.margin_top),
                        slider(o, R.string.margin_right), slider(o, R.string.margin_bottom)};
                choice1.setOnCheckedStateChangeListener((g, ids) -> updateCropSliders());
                updateCropSliders();
                rangeField = text(o, R.string.page_range, InputType.TYPE_CLASS_TEXT);
                passwordField = text(o, R.string.file_password_optional, passwordType());
                break;
            case "compress":
                choice1 = chips(o, R.string.compression_level, new int[]{R.string.level_low, R.string.level_medium, R.string.level_high}, 1);
                choice1.setOnCheckedStateChangeListener((g, ids) -> updateEstimate());
                estimateView = new TextView(requireContext());
                estimateView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
                o.addView(estimateView);
                passwordField = text(o, R.string.file_password_optional, passwordType());
                break;
            case "protect":
                userPassword = text(o, R.string.new_password, passwordType());
                userPasswordConfirm = text(o, R.string.confirm_password, passwordType());
                ownerPassword = text(o, R.string.owner_password_optional, passwordType());
                switch1 = toggle(o, R.string.allow_print, true);
                switch2 = toggle(o, R.string.allow_copy, false);
                note(o, R.string.protect_note);
                passwordField = text(o, R.string.file_password_optional, passwordType());
                break;
            case "unprotect":
                passwordField = text(o, R.string.current_password, passwordType());
                break;
            default:
                break;
        }
    }

    private int passwordType() {
        return InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    }

    private TextInputEditText text(LinearLayout parent, @StringRes int hint, int inputType) {
        TextInputLayout til = new TextInputLayout(requireContext(), null,
                com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        if ((inputType & InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0) {
            til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        }
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setInputType(inputType);
        et.setSingleLine(true);
        til.addView(et);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(til, lp);
        return et;
    }

    private ChipGroup chips(LinearLayout parent, @StringRes int label, int[] options, int checked) {
        TextView title = new TextView(requireContext());
        title.setText(label);
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        parent.addView(title, lp);
        ChipGroup group = new ChipGroup(requireContext());
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        for (int i = 0; i < options.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.chip_filter, group, false);
            chip.setId(View.generateViewId());
            chip.setText(options[i]);
            chip.setTag(i);
            group.addView(chip);
            if (i == checked) chip.setChecked(true);
        }
        parent.addView(group);
        return group;
    }

    private int selected(@Nullable ChipGroup g) {
        if (g == null) return 0;
        int id = g.getCheckedChipId();
        if (id == View.NO_ID) return 0;
        Object tag = g.findViewById(id).getTag();
        return tag instanceof Integer ? (Integer) tag : 0;
    }

    private MaterialSwitch toggle(LinearLayout parent, @StringRes int label, boolean checked) {
        MaterialSwitch s = new MaterialSwitch(requireContext());
        s.setText(label);
        s.setChecked(checked);
        s.setMinHeight(dp(48));
        parent.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private Slider slider(LinearLayout parent, @StringRes int label) {
        TextView title = new TextView(requireContext());
        title.setText(label);
        parent.addView(title);
        Slider s = new Slider(requireContext());
        s.setValueFrom(0);
        s.setValueTo(30);
        s.setStepSize(1);
        s.setValue(5);
        s.setContentDescription(getString(label));
        s.setLabelFormatter(v -> Math.round(v) + " %");
        parent.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    private void note(LinearLayout parent, @StringRes int text) {
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        parent.addView(t, lp);
    }

    private void updateCropSliders() {
        if (marginSliders == null) return;
        boolean manual = selected(choice1) == 1;
        for (Slider s : marginSliders) s.setEnabled(manual);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @NonNull
    private static String str(@Nullable TextInputEditText e) {
        return e == null || e.getText() == null ? "" : e.getText().toString();
    }

    /** Size estimate before compressing (CMP-01). */
    private void updateEstimate() {
        if (vm.inputs.isEmpty()) {
            vm.estimate.setValue("");
            return;
        }
        CompressionLevel level = CompressionLevel.values()[selected(choice1)];
        Uri uri = vm.inputs.get(0);
        String pwd = str(passwordField);
        vm.estimate.setValue(getString(R.string.estimating));
        android.content.Context ctx = requireContext().getApplicationContext();
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        AppExecutors.get().background().execute(() -> {
            String text;
            try {
                PdfOps.CompressionInfo info = PdfOps.analyze(ctx, uri, pwd.isEmpty() ? null : pwd, level);
                text = ctx.getString(R.string.compress_estimate, FileUtils.formatSize(info.fileSize, locale),
                        FileUtils.formatSize(info.estimatedSize(), locale), info.images);
            } catch (IOException | RuntimeException e) {
                text = ctx.getString(R.string.estimate_unavailable);
            }
            vm.estimate.postValue(text);
        });
    }

    // ------------------------------------------------------------------ run

    private void start() {
        if (vm.workId != null) return;
        if (needsInput() && vm.inputs.isEmpty()) {
            Toast.makeText(requireContext(), R.string.choose_file_first, Toast.LENGTH_SHORT).show();
            return;
        }
        if ("merge".equals(tool.id) && vm.inputs.size() < 2) {
            Toast.makeText(requireContext(), R.string.merge_need_two, Toast.LENGTH_SHORT).show();
            return;
        }
        Data.Builder data = new Data.Builder();
        String[] inputs = new String[vm.inputs.size()];
        for (int i = 0; i < inputs.length; i++) inputs[i] = vm.inputs.get(i).toString();
        data.putStringArray(PdfOpWorker.KEY_INPUTS, inputs);
        data.putString(PdfOpWorker.KEY_PASSWORD, str(passwordField));
        data.putString(PdfOpWorker.KEY_RANGE, str(rangeField));
        String base = vm.inputs.isEmpty() ? "document" : PdfOpWorker.baseName(requireContext(), vm.inputs.get(0));
        String suffix;
        switch (tool.id) {
            case "merge":
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_MERGE);
                suffix = "_fusion.pdf";
                break;
            case "images_to_pdf":
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_IMAGES_TO_PDF);
                data.putString(PdfOpWorker.KEY_FORMAT, new String[]{"A4", "LETTER", "FIT"}[selected(choice1)]);
                data.putInt(PdfOpWorker.KEY_MARGIN, switch1 != null && switch1.isChecked() ? 24 : 0);
                base = "images";
                suffix = ".pdf";
                break;
            case "blank": {
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_BLANK);
                data.putString(PdfOpWorker.KEY_FORMAT, new String[]{"A4", "LETTER", "A5"}[selected(choice1)]);
                data.putString(PdfOpWorker.KEY_PATTERN, new String[]{"BLANK", "LINED", "GRID"}[selected(choice2)]);
                data.putBoolean(PdfOpWorker.KEY_LANDSCAPE, switch1 != null && switch1.isChecked());
                int pages;
                try {
                    pages = Integer.parseInt(str(pagesField).trim());
                } catch (NumberFormatException e) {
                    pages = 1;
                }
                data.putInt(PdfOpWorker.KEY_PAGES, Math.max(1, Math.min(500, pages)));
                base = getString(R.string.blank_file_name);
                suffix = ".pdf";
                break;
            }
            case "ocr":
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_OCR);
                data.putBoolean(PdfOpWorker.KEY_FORCE, switch1 != null && switch1.isChecked());
                suffix = "_ocr.pdf";
                break;
            case "to_images":
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_TO_IMAGES);
                data.putBoolean(PdfOpWorker.KEY_PNG, selected(choice1) == 1);
                data.putInt(PdfOpWorker.KEY_DPI, new int[]{72, 150, 300}[selected(choice2)]);
                data.putString(PdfOpWorker.KEY_BASE_NAME, base);
                suffix = null;
                break;
            case "to_text":
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_TO_TEXT);
                suffix = ".txt";
                break;
            case "extract":
                if (str(rangeField).trim().isEmpty()) {
                    Toast.makeText(requireContext(), R.string.range_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_EXTRACT);
                suffix = "_extrait.pdf";
                break;
            case "crop":
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_CROP);
                if (selected(choice1) == 1 && marginSliders != null) {
                    data.putFloatArray(PdfOpWorker.KEY_MARGINS, new float[]{
                            marginSliders[0].getValue() / 100f, marginSliders[1].getValue() / 100f,
                            marginSliders[2].getValue() / 100f, marginSliders[3].getValue() / 100f});
                }
                suffix = "_recadre.pdf";
                break;
            case "compress":
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_COMPRESS);
                data.putString(PdfOpWorker.KEY_LEVEL, CompressionLevel.values()[selected(choice1)].name());
                suffix = "_compresse.pdf";
                break;
            case "protect": {
                String pwd = str(userPassword);
                if (pwd.length() < 4) {
                    Toast.makeText(requireContext(), R.string.password_too_short, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!pwd.equals(str(userPasswordConfirm))) {
                    Toast.makeText(requireContext(), R.string.password_mismatch, Toast.LENGTH_SHORT).show();
                    return;
                }
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_PROTECT);
                data.putString(PdfOpWorker.KEY_USER_PASSWORD, pwd);
                data.putString(PdfOpWorker.KEY_OWNER_PASSWORD, str(ownerPassword));
                data.putBoolean(PdfOpWorker.KEY_ALLOW_PRINT, switch1 != null && switch1.isChecked());
                data.putBoolean(PdfOpWorker.KEY_ALLOW_COPY, switch2 != null && switch2.isChecked());
                suffix = "_protege.pdf";
                break;
            }
            case "unprotect":
                if (str(passwordField).isEmpty()) {
                    Toast.makeText(requireContext(), R.string.password_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                data.putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_UNPROTECT);
                suffix = "_sans_mdp.pdf";
                break;
            default:
                return;
        }
        vm.pendingData = data;
        // The user always chooses where the result is written (no silent overwrite, §5.4).
        if (suffix == null) {
            chooseFolder.launch(null);
        } else if (suffix.endsWith(".txt")) {
            createText.launch(base + suffix);
        } else {
            createPdf.launch(base + suffix);
        }
    }

    private void onOutputChosen(@Nullable Uri out) {
        Data.Builder data = vm.pendingData;
        vm.pendingData = null;
        if (out == null || data == null) return;
        if ("to_images".equals(tool.id)) DocumentOpener.persist(requireContext(), out);
        data.putString(PdfOpWorker.KEY_OUTPUT, out.toString());
        vm.output = out;
        vm.workId = PdfOpWorker.enqueue(requireContext(), data.build());
        observeWork();
    }

    private void observeWork() {
        if (vm.workId == null) return;
        b.run.setEnabled(false);
        b.progress.setVisibility(View.VISIBLE);
        b.progress.setIndeterminate(true);
        b.resultActions.setVisibility(View.GONE);
        b.status.setText(R.string.working);
        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(vm.workId).observe(getViewLifecycleOwner(), info -> {
            if (info == null) return;
            WorkInfo.State state = info.getState();
            if (state == WorkInfo.State.RUNNING) {
                int p = info.getProgress().getInt(PdfOpWorker.KEY_PROGRESS, -1);
                if (p >= 0) {
                    b.progress.setIndeterminate(false);
                    b.progress.setProgressCompat(p, true);
                }
            } else if (state.isFinished()) {
                vm.workId = null;
                b.run.setEnabled(true);
                b.progress.setVisibility(View.GONE);
                if (state == WorkInfo.State.SUCCEEDED) {
                    onSuccess(info.getOutputData());
                } else {
                    onFailure(info.getOutputData().getString(PdfOpWorker.KEY_ERROR));
                }
            }
        });
    }

    private void onSuccess(@NonNull Data out) {
        int count = out.getInt(PdfOpWorker.KEY_RESULT_COUNT, 1);
        Uri result = vm.output;
        switch (tool.id) {
            case "to_images":
                b.status.setText(getString(R.string.images_exported, count));
                return;
            case "ocr":
                b.status.setText(count == 0 ? getString(R.string.ocr_nothing) : getString(R.string.ocr_done, count));
                break;
            default:
                b.status.setText(R.string.operation_done);
                break;
        }
        if (result == null) return;
        b.resultActions.setVisibility(View.VISIBLE);
        boolean text = "to_text".equals(tool.id);
        b.resultOpen.setOnClickListener(v -> {
            if (text) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(result, "text/plain")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(requireContext(), R.string.no_app_for_link, Toast.LENGTH_SHORT).show();
                }
            } else {
                DocumentOpener.open(requireContext(), result);
            }
        });
        b.resultShare.setOnClickListener(v -> ShareUtils.shareStream(requireContext(), result,
                text ? "text/plain" : "application/pdf", getString(R.string.share)));
    }

    private void onFailure(@Nullable String code) {
        int msg;
        if (PdfOpWorker.ERROR_PASSWORD.equals(code)) {
            msg = R.string.error_password_op;
        } else if (PdfOpWorker.ERROR_INVALID_RANGE.equals(code)) {
            msg = R.string.error_range;
        } else if (PdfOpWorker.ERROR_CANCELLED.equals(code)) {
            msg = R.string.cancelled;
        } else {
            msg = R.string.error_operation;
        }
        b.status.setText(msg);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    /** Unused but kept for symmetry with the inputs list. */
    static List<Uri> copy(List<Uri> in) {
        return new java.util.ArrayList<>(in);
    }
}
