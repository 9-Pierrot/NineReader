package com.ninereader.ui.tools;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.ninereader.R;
import com.ninereader.databinding.FragmentToolsBinding;

import java.util.ArrayList;
import java.util.List;

/** Tools tab (§3.1, §7.1 screen 5): grid of tools filterable by category; 2 columns on phones, 3-4 on tablets. */
public class ToolsFragment extends Fragment {

    private static final String STATE_CATEGORY = "category";

    private FragmentToolsBinding b;
    private ToolCatalog.Category category = ToolCatalog.Category.ALL;
    @Nullable
    private ToolCatalog.Tool pendingTool;
    private final ToolAdapter adapter = new ToolAdapter();
    private final ActivityResultLauncher<String[]> pickPdf = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> ToolLauncher.onPdfPickedForReader(this, pendingTool, uri));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentToolsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        if (state != null) {
            category = ToolCatalog.Category.valueOf(state.getString(STATE_CATEGORY, ToolCatalog.Category.ALL.name()));
            pendingTool = ToolCatalog.byId(state.getString("pending"));
        }
        int[] labels = {R.string.cat_all, R.string.cat_read, R.string.cat_create, R.string.cat_edit,
                R.string.cat_convert, R.string.cat_organize, R.string.cat_protect};
        ToolCatalog.Category[] cats = ToolCatalog.Category.values();
        for (int i = 0; i < cats.length; i++) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.chip_filter, b.categories, false);
            chip.setId(View.generateViewId());
            chip.setText(labels[i]);
            ToolCatalog.Category c = cats[i];
            chip.setChecked(c == category);
            chip.setOnCheckedChangeListener((v, checked) -> {
                if (checked) {
                    category = c;
                    adapter.submit(ToolCatalog.in(c));
                }
            });
            b.categories.addView(chip);
        }
        int columns = getResources().getInteger(R.integer.tools_grid_columns);
        b.grid.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        b.grid.setAdapter(adapter);
        adapter.submit(ToolCatalog.in(category));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_CATEGORY, category.name());
        if (pendingTool != null) out.putString("pending", pendingTool.id);
    }

    private final class ToolAdapter extends RecyclerView.Adapter<Holder> {
        private List<ToolCatalog.Tool> tools = new ArrayList<>();

        void submit(List<ToolCatalog.Tool> list) {
            tools = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tool, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ToolCatalog.Tool t = tools.get(position);
            h.icon.setImageResource(t.icon);
            h.title.setText(t.title);
            h.description.setText(t.description);
            h.itemView.setOnClickListener(v -> {
                pendingTool = t;
                ToolLauncher.launch(ToolsFragment.this, t, pickPdf);
            });
        }

        @Override
        public int getItemCount() {
            return tools.size();
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final TextView description;

        Holder(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.icon);
            title = v.findViewById(R.id.title);
            description = v.findViewById(R.id.description);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
