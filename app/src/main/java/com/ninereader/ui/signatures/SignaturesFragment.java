package com.ninereader.ui.signatures;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.data.entity.SignatureEntity;
import com.ninereader.databinding.FragmentSignaturesBinding;

import java.util.ArrayList;
import java.util.List;

/** Signature management (SIG-03, §7.1 screen 7): create, list and delete saved signatures and initials. */
public class SignaturesFragment extends Fragment {

    private FragmentSignaturesBinding b;
    private final Adapter adapter = new Adapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentSignaturesBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        b.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());
        b.list.setLayoutManager(new LinearLayoutManager(requireContext()));
        b.list.setAdapter(adapter);
        b.add.setOnClickListener(v -> SignatureDialogs.showPad(requireActivity(), path -> { }));
        NineReaderApp.from(requireContext()).signatures().all().observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list);
            b.empty.setVisibility(list == null || list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        private List<SignatureEntity> items = new ArrayList<>();

        void submit(List<SignatureEntity> list) {
            items = list == null ? new ArrayList<>() : list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_signature, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            SignatureEntity s = items.get(position);
            h.image.setImageBitmap(BitmapFactory.decodeFile(s.imagePath));
            h.kind.setText(SignatureEntity.KIND_INITIALS.equals(s.kind) ? R.string.initials : R.string.signature);
            h.image.setContentDescription(h.kind.getText());
            h.delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.signature_delete_confirm)
                    .setPositiveButton(R.string.delete, (d, w) -> NineReaderApp.from(requireContext()).signatures().delete(s))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView kind;
        final ImageButton delete;

        Holder(@NonNull View v) {
            super(v);
            image = v.findViewById(R.id.image);
            kind = v.findViewById(R.id.kind);
            delete = v.findViewById(R.id.delete);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
