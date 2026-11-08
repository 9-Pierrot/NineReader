package com.ninereader.ui.tools;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.ninereader.R;
import com.ninereader.databinding.FragmentOrganizeBinding;
import com.ninereader.pdfops.PagePlan;
import com.ninereader.pdfops.PdfOpWorker;
import com.ninereader.reader.BitmapCache;
import com.ninereader.reader.PdfEngine;
import com.ninereader.reader.RenderScheduler;
import com.ninereader.ui.common.DocumentOpener;

/** Page organizer (ORG-01, §7.1 screen 6): grid with drag & drop, rotate, delete, duplicate, insert. */
public class OrganizeFragment extends Fragment {

    private FragmentOrganizeBinding b;
    private OrganizeViewModel vm;
    private final PageAdapter adapter = new PageAdapter();

    private final ActivityResultLauncher<String[]> pick = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                DocumentOpener.persist(requireContext(), uri);
                vm.open(uri, null);
            });
    private final ActivityResultLauncher<String> save = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/pdf"), this::onSaveTarget);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentOrganizeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        vm = new ViewModelProvider(this).get(OrganizeViewModel.class);
        b.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        b.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save) {
                startSave();
                return true;
            }
            return false;
        });
        int cols = getResources().getInteger(R.integer.organize_grid_columns);
        b.grid.setLayoutManager(new GridLayoutManager(requireContext(), cols));
        b.grid.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.START | ItemTouchHelper.END, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from, @NonNull RecyclerView.ViewHolder to) {
                int f = from.getBindingAdapterPosition();
                int t = to.getBindingAdapterPosition();
                if (f < 0 || t < 0) return false;
                vm.move(f, t);
                adapter.notifyItemMoved(f, t);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                // no swipe
            }

            @Override
            public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                super.clearView(rv, vh);
                adapter.notifyDataSetChanged(); // refresh page numbers
            }
        }).attachToRecyclerView(b.grid);

        b.pick.setOnClickListener(v -> pick.launch(new String[]{"application/pdf"}));
        b.rotateLeft.setOnClickListener(v -> withSelection(() -> vm.rotateSelected(-90)));
        b.rotateRight.setOnClickListener(v -> withSelection(() -> vm.rotateSelected(90)));
        b.duplicate.setOnClickListener(v -> withSelection(vm::duplicateSelected));
        b.delete.setOnClickListener(v -> withSelection(() -> {
            if (vm.selection.size() >= vm.plan.size()) {
                Toast.makeText(requireContext(), R.string.cannot_delete_all, Toast.LENGTH_SHORT).show();
                return;
            }
            vm.deleteSelected();
        }));
        b.insertBlank.setOnClickListener(v -> vm.insertBlankAfterSelection());
        b.selectAll.setOnClickListener(v -> vm.selectAll());
        b.reset.setOnClickListener(v -> vm.reset());

        vm.status.observe(getViewLifecycleOwner(), this::onStatus);
        vm.version.observe(getViewLifecycleOwner(), v -> {
            adapter.notifyDataSetChanged();
            b.selectionCount.setText(getString(R.string.selected_count, vm.selection.size(), vm.plan.size()));
        });
        Bundle args = getArguments();
        String preset = args == null ? null : args.getString(ToolRunFragment.ARG_URI);
        if (vm.status.getValue() == OrganizeViewModel.Status.EMPTY && preset != null) vm.open(Uri.parse(preset), null);
        if (vm.workId != null) observeWork();
    }

    private void withSelection(Runnable r) {
        if (vm.selection.isEmpty()) {
            Toast.makeText(requireContext(), R.string.select_pages_first, Toast.LENGTH_SHORT).show();
            return;
        }
        r.run();
    }

    private void onStatus(OrganizeViewModel.Status s) {
        b.progress.setVisibility(s == OrganizeViewModel.Status.LOADING ? View.VISIBLE : View.GONE);
        b.empty.setVisibility(s == OrganizeViewModel.Status.READY || s == OrganizeViewModel.Status.LOADING ? View.GONE : View.VISIBLE);
        if (s == OrganizeViewModel.Status.ERROR) b.emptyText.setText(R.string.error_open);
        if (s == OrganizeViewModel.Status.PASSWORD) askPassword();
        if (s == OrganizeViewModel.Status.READY) {
            adapter.notifyDataSetChanged();
            b.selectionCount.setText(getString(R.string.selected_count, vm.selection.size(), vm.plan.size()));
        }
    }

    private void askPassword() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        FrameLayout box = new FrameLayout(requireContext());
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.password_required)
                .setView(box)
                .setPositiveButton(R.string.open, (d, w) -> {
                    if (vm.uri != null) vm.open(vm.uri, input.getText().toString());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startSave() {
        if (vm.uri == null || vm.status.getValue() != OrganizeViewModel.Status.READY) {
            Toast.makeText(requireContext(), R.string.choose_file_first, Toast.LENGTH_SHORT).show();
            return;
        }
        save.launch(PdfOpWorker.baseName(requireContext(), vm.uri) + "_organise.pdf");
    }

    private void onSaveTarget(@Nullable Uri out) {
        if (out == null || vm.uri == null) return;
        Data data = new Data.Builder()
                .putString(PdfOpWorker.KEY_OP, PdfOpWorker.OP_ORGANIZE)
                .putStringArray(PdfOpWorker.KEY_INPUTS, new String[]{vm.uri.toString()})
                .putString(PdfOpWorker.KEY_OUTPUT, out.toString())
                .putString(PdfOpWorker.KEY_PASSWORD, vm.password == null ? "" : vm.password)
                .putString(PdfOpWorker.KEY_PLAN, vm.plan.encode())
                .build();
        vm.output = out;
        vm.workId = PdfOpWorker.enqueue(requireContext(), data);
        observeWork();
    }

    private void observeWork() {
        if (vm.workId == null) return;
        b.progress.setVisibility(View.VISIBLE);
        WorkManager.getInstance(requireContext()).getWorkInfoByIdLiveData(vm.workId).observe(getViewLifecycleOwner(), info -> {
            if (info == null || !info.getState().isFinished()) return;
            vm.workId = null;
            b.progress.setVisibility(View.GONE);
            Uri out = vm.output;
            if (info.getState() == WorkInfo.State.SUCCEEDED && out != null) {
                Snackbar.make(b.getRoot(), R.string.operation_done, Snackbar.LENGTH_LONG)
                        .setAction(R.string.open, v -> DocumentOpener.open(requireContext(), out))
                        .show();
            } else {
                Snackbar.make(b.getRoot(), R.string.error_operation, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    // ------------------------------------------------------------------ adapter

    private final class PageAdapter extends RecyclerView.Adapter<Holder> {

        PageAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return position < vm.ids.size() ? vm.ids.get(position) : position;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_organize_page, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            RenderScheduler sch = vm.scheduler;
            PagePlan.Entry e = vm.plan.get(position);
            boolean selected = vm.selection.contains(position);
            h.check.setVisibility(selected ? View.VISIBLE : View.GONE);
            h.frame.setBackgroundResource(selected ? R.drawable.bg_tool_selected_rect : R.drawable.bg_thumbnail);
            h.label.setText(e.isBlank() ? getString(R.string.blank_page_label, position + 1)
                    : getString(R.string.page_from_source, position + 1, e.source + 1));
            h.itemView.setContentDescription(h.label.getText() + (selected ? ", " + getString(R.string.selected) : ""));
            h.thumb.setRotation(e.rotation);
            h.itemView.setOnClickListener(v -> vm.toggle(h.getBindingAdapterPosition()));
            if (h.ticket != null) h.ticket.cancel();
            h.thumb.setImageBitmap(null);
            if (e.isBlank() || sch == null) {
                h.thumb.setImageResource(R.drawable.ic_note_add);
                return;
            }
            PdfEngine engine = sch.engine();
            int w = 220;
            float pw = engine.isMeasured(e.source) ? engine.pageWidth(e.source) : 612f;
            float ph = engine.isMeasured(e.source) ? engine.pageHeight(e.source) : 792f;
            int hh = Math.max(1, Math.round(w * ph / pw));
            int source = e.source;
            h.source = source;
            h.ticket = sch.request(source, w, hh, 0, 0, w, hh, RenderScheduler.PRIORITY_THUMBNAIL,
                    BitmapCache.key(source, w, hh, 7, 0), bmp -> {
                        if (h.source == source) h.thumb.setImageBitmap(bmp);
                    });
        }

        @Override
        public int getItemCount() {
            return vm.status.getValue() == OrganizeViewModel.Status.READY ? vm.plan.size() : 0;
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final ImageView check;
        final TextView label;
        final View frame;
        @Nullable
        RenderScheduler.Ticket ticket;
        int source = -1;

        Holder(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            check = v.findViewById(R.id.check);
            label = v.findViewById(R.id.label);
            frame = v.findViewById(R.id.frame);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
