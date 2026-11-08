package com.ninereader.ui.create;

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

import com.ninereader.R;
import com.ninereader.databinding.FragmentToolsBinding;
import com.ninereader.ui.tools.ToolCatalog;
import com.ninereader.ui.tools.ToolLauncher;

import java.util.ArrayList;
import java.util.List;

/** Create tab (§3.1): images → PDF (CRE-01), blank PDF (CRE-02), combine files (ORG-02), OCR (CRE-03). */
public class CreateFragment extends Fragment {

    private static final String[] TOOL_IDS = {"images_to_pdf", "blank", "merge", "ocr"};

    private FragmentToolsBinding b;
    @Nullable
    private ToolCatalog.Tool pending;
    private final ActivityResultLauncher<String[]> pickPdf = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> ToolLauncher.onPdfPickedForReader(this, pending, uri));

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentToolsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        b.toolbar.setTitle(R.string.nav_create);
        b.categories.setVisibility(View.GONE);
        List<ToolCatalog.Tool> tools = new ArrayList<>();
        for (String id : TOOL_IDS) {
            ToolCatalog.Tool t = ToolCatalog.byId(id);
            if (t != null) tools.add(t);
        }
        int columns = getResources().getInteger(R.integer.tools_grid_columns);
        b.grid.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        b.grid.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tool, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) { };
            }

            @Override
            public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder h, int position) {
                ToolCatalog.Tool t = tools.get(position);
                ((ImageView) h.itemView.findViewById(R.id.icon)).setImageResource(t.icon);
                ((TextView) h.itemView.findViewById(R.id.title)).setText(t.title);
                ((TextView) h.itemView.findViewById(R.id.description)).setText(t.description);
                h.itemView.setOnClickListener(v -> {
                    pending = t;
                    ToolLauncher.launch(CreateFragment.this, t, pickPdf);
                });
            }

            @Override
            public int getItemCount() {
                return tools.size();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
