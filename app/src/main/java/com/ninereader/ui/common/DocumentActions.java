package com.ninereader.ui.common;

import android.net.Uri;
import android.provider.DocumentsContract;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.ninereader.NineReaderApp;
import com.ninereader.R;
import com.ninereader.data.entity.CollectionEntity;
import com.ninereader.data.entity.DocumentEntity;
import com.ninereader.util.AppExecutors;
import com.ninereader.util.ShareUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Library actions on a document (BIB-03, BIB-04): share, favourite, collections, rename, move,
 * delete to the internal trash, remove from recents.
 */
public final class DocumentActions {

    /** Requests a destination folder for "move" (the fragment launches OPEN_DOCUMENT_TREE). */
    public interface MoveRequester {
        void requestMoveDestination(@NonNull DocumentEntity doc);
    }

    private static final int MENU_SHARE = 1;
    private static final int MENU_COLLECTIONS = 2;
    private static final int MENU_RENAME = 3;
    private static final int MENU_MOVE = 4;
    private static final int MENU_DELETE = 5;
    private static final int MENU_REMOVE_RECENT = 6;

    private DocumentActions() {
    }

    public static void showMenu(@NonNull Fragment f, @NonNull DocumentEntity doc, @NonNull View anchor,
                                @Nullable MoveRequester mover) {
        PopupMenu menu = new PopupMenu(f.requireContext(), anchor);
        Menu m = menu.getMenu();
        m.add(0, MENU_SHARE, 0, R.string.share);
        m.add(0, MENU_COLLECTIONS, 1, R.string.collections_manage);
        m.add(0, MENU_RENAME, 2, R.string.rename);
        if (mover != null && doc.parentUri != null) m.add(0, MENU_MOVE, 3, R.string.move);
        m.add(0, MENU_DELETE, 4, R.string.delete);
        if (doc.lastOpened > 0) m.add(0, MENU_REMOVE_RECENT, 5, R.string.remove_from_recents);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case MENU_SHARE:
                    ShareUtils.shareStream(f.requireContext(), Uri.parse(doc.uri), "application/pdf", f.getString(R.string.share));
                    return true;
                case MENU_COLLECTIONS:
                    editCollections(f, doc);
                    return true;
                case MENU_RENAME:
                    rename(f, doc);
                    return true;
                case MENU_MOVE:
                    if (mover != null) mover.requestMoveDestination(doc);
                    return true;
                case MENU_DELETE:
                    delete(f, doc);
                    return true;
                case MENU_REMOVE_RECENT:
                    NineReaderApp.from(f.requireContext()).documents().removeFromRecents(doc.id);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    static void rename(@NonNull Fragment f, @NonNull DocumentEntity doc) {
        EditText input = new EditText(f.requireContext());
        input.setSingleLine(true);
        input.setText(doc.name);
        int dot = doc.name.lastIndexOf('.');
        input.setSelection(0, dot > 0 ? dot : doc.name.length());
        FrameLayout box = new FrameLayout(f.requireContext());
        int pad = Math.round(20 * f.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(f.requireContext())
                .setTitle(R.string.rename)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty() || name.equals(doc.name)) return;
                    if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) name = name + ".pdf";
                    String finalName = name;
                    android.content.Context ctx = f.requireContext().getApplicationContext();
                    AppExecutors.get().diskIo().execute(() -> {
                        Uri renamed = null;
                        try {
                            renamed = DocumentsContract.renameDocument(ctx.getContentResolver(), Uri.parse(doc.uri), finalName);
                        } catch (FileNotFoundException | SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
                            renamed = null;
                        }
                        Uri result = renamed;
                        AppExecutors.get().mainThread().execute(() -> {
                            if (result == null) {
                                Toast.makeText(ctx, R.string.rename_unsupported, Toast.LENGTH_LONG).show();
                            } else {
                                NineReaderApp.from(ctx).documents().updateLocation(doc.id, result.toString(), finalName, null);
                                ThumbnailLoader.invalidate(doc.uri);
                            }
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Moves a document into a folder (tree URI) chosen by the user. */
    public static void move(@NonNull Fragment f, @NonNull DocumentEntity doc, @NonNull Uri targetTree) {
        android.content.Context ctx = f.requireContext().getApplicationContext();
        AppExecutors.get().diskIo().execute(() -> {
            Uri moved = null;
            String targetParent = null;
            try {
                Uri target = DocumentsContract.buildDocumentUriUsingTree(targetTree, DocumentsContract.getTreeDocumentId(targetTree));
                targetParent = target.toString();
                if (doc.parentUri != null) {
                    moved = DocumentsContract.moveDocument(ctx.getContentResolver(), Uri.parse(doc.uri), Uri.parse(doc.parentUri), target);
                }
            } catch (FileNotFoundException | SecurityException | IllegalArgumentException | UnsupportedOperationException e) {
                moved = null;
            }
            Uri result = moved;
            String parent = targetParent;
            AppExecutors.get().mainThread().execute(() -> {
                if (result == null) {
                    Toast.makeText(ctx, R.string.move_unsupported, Toast.LENGTH_LONG).show();
                } else {
                    NineReaderApp.from(ctx).documents().updateLocation(doc.id, result.toString(), doc.name, parent);
                    Toast.makeText(ctx, R.string.moved, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    static void delete(@NonNull Fragment f, @NonNull DocumentEntity doc) {
        new MaterialAlertDialogBuilder(f.requireContext())
                .setTitle(R.string.delete_title)
                .setMessage(f.getString(R.string.delete_message, doc.name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    View root = f.getView();
                    NineReaderApp app = NineReaderApp.from(f.requireContext());
                    AppExecutors.get().diskIo().execute(() -> {
                        boolean ok;
                        try {
                            app.trash().moveToTrash(doc);
                            ok = true;
                        } catch (IOException e) {
                            ok = false;
                        }
                        boolean success = ok;
                        AppExecutors.get().mainThread().execute(() -> {
                            if (root == null) return;
                            Snackbar.make(root, success ? R.string.moved_to_trash : R.string.delete_unsupported,
                                    Snackbar.LENGTH_LONG).show();
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Multi-choice dialog to add/remove a document from collections; allows creating one. */
    public static void editCollections(@NonNull Fragment f, @NonNull DocumentEntity doc) {
        NineReaderApp app = NineReaderApp.from(f.requireContext());
        AppExecutors.get().diskIo().execute(() -> {
            List<CollectionEntity> all = app.collections().allSync();
            List<Long> current = app.collections().collectionsOf(doc.id);
            AppExecutors.get().mainThread().execute(() -> {
                if (!f.isAdded()) return;
                String[] names = new String[all.size()];
                boolean[] checked = new boolean[all.size()];
                for (int i = 0; i < all.size(); i++) {
                    names[i] = all.get(i).name;
                    checked[i] = current.contains(all.get(i).id);
                }
                new MaterialAlertDialogBuilder(f.requireContext())
                        .setTitle(R.string.collections_manage)
                        .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                        .setPositiveButton(android.R.string.ok, (d, w) -> {
                            List<Long> ids = new ArrayList<>();
                            for (int i = 0; i < all.size(); i++) if (checked[i]) ids.add(all.get(i).id);
                            app.collections().setCollections(doc.id, ids);
                        })
                        .setNeutralButton(R.string.collection_new, (d, w) -> createCollection(f, name ->
                                AppExecutors.get().diskIo().execute(() -> {
                                    long id = app.collections().create(name, 0);
                                    List<Long> ids = new ArrayList<>(current);
                                    if (!ids.contains(id)) ids.add(id);
                                    app.collections().setCollections(doc.id, ids);
                                })))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
        });
    }

    /** Asks for a collection name. */
    public static void createCollection(@NonNull Fragment f, @NonNull java.util.function.Consumer<String> created) {
        EditText input = new EditText(f.requireContext());
        input.setSingleLine(true);
        input.setHint(R.string.collection_name_hint);
        FrameLayout box = new FrameLayout(f.requireContext());
        int pad = Math.round(20 * f.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);
        new MaterialAlertDialogBuilder(f.requireContext())
                .setTitle(R.string.collection_new)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) created.accept(name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
