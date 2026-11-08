package com.ninereader.ui.signatures;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.data.SignatureRepository;
import com.ninereader.data.entity.SignatureEntity;
import com.ninereader.util.AppExecutors;

import java.io.IOException;
import java.util.List;

/** Dialogs to draw a new signature and to pick a saved one (SIG-03, SIG-04). */
public final class SignatureDialogs {

    /** Receives the chosen signature image file. */
    public interface Callback {
        void onSignature(@NonNull String imagePath);
    }

    private SignatureDialogs() {
    }

    /** Shows the drawing pad; the signature is saved and returned through the callback. */
    public static void showPad(@NonNull FragmentActivity activity, @NonNull Callback callback) {
        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_signature_pad, null, false);
        SignaturePadView pad = content.findViewById(R.id.pad);
        MaterialButtonToggleGroup kinds = content.findViewById(R.id.kind_group);
        ChipGroup colors = content.findViewById(R.id.ink_colors);
        colors.setOnCheckedStateChangeListener((group, ids) ->
                pad.setInkColor(ids.contains(R.id.ink_black) ? 0xFF111111 : 0xFF0D1B4C));
        content.findViewById(R.id.clear).setOnClickListener(v -> pad.clear());
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.new_signature)
                .setView(content)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Bitmap bmp = pad.export();
            if (bmp == null) {
                Toast.makeText(activity, R.string.signature_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            String kind = kinds.getCheckedButtonId() == R.id.kind_initials
                    ? SignatureEntity.KIND_INITIALS : SignatureEntity.KIND_SIGNATURE;
            SignatureRepository repo = NineReaderApp.from(activity).signatures();
            AppExecutors.get().diskIo().execute(() -> {
                try {
                    long id = repo.save(bmp, kind);
                    SignatureEntity saved = NineReaderApp.from(activity).db().signatures().byId(id);
                    AppExecutors.get().mainThread().execute(() -> {
                        if (saved != null) callback.onSignature(saved.imagePath);
                    });
                } catch (IOException e) {
                    AppExecutors.get().mainThread().execute(() ->
                            Toast.makeText(activity, R.string.error_generic, Toast.LENGTH_SHORT).show());
                }
            });
            dialog.dismiss();
        }));
        dialog.show();
    }

    /** Lists saved signatures; offers to draw a new one. */
    public static void showPicker(@NonNull FragmentActivity activity, @NonNull Callback callback) {
        AppExecutors.get().diskIo().execute(() -> {
            List<SignatureEntity> list = NineReaderApp.from(activity).db().signatures().all();
            AppExecutors.get().mainThread().execute(() -> {
                if (activity.isFinishing()) return;
                if (list.isEmpty()) {
                    showPad(activity, callback);
                    return;
                }
                LinearLayout box = new LinearLayout(activity);
                box.setOrientation(LinearLayout.VERTICAL);
                int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
                box.setPadding(pad, pad / 2, pad, 0);
                AlertDialog[] holder = new AlertDialog[1];
                for (SignatureEntity s : list) {
                    ImageView iv = new ImageView(activity);
                    Bitmap bmp = BitmapFactory.decodeFile(s.imagePath);
                    iv.setImageBitmap(bmp);
                    iv.setAdjustViewBounds(true);
                    iv.setMaxHeight(Math.round(72 * activity.getResources().getDisplayMetrics().density));
                    iv.setBackgroundResource(R.drawable.bg_signature_pad);
                    iv.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
                    iv.setContentDescription(activity.getString(SignatureEntity.KIND_INITIALS.equals(s.kind)
                            ? R.string.initials : R.string.signature));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = pad / 2;
                    iv.setOnClickListener(v -> {
                        if (holder[0] != null) holder[0].dismiss();
                        callback.onSignature(s.imagePath);
                    });
                    box.addView(iv, lp);
                }
                TextView hint = new TextView(activity);
                hint.setText(R.string.signature_pick_hint);
                box.addView(hint);
                android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
                scroll.addView(box);
                holder[0] = new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.choose_signature)
                        .setView(scroll)
                        .setPositiveButton(R.string.new_signature, (d, w) -> showPad(activity, callback))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }
}
