package com.ninereader.ui.tools;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ninereader.R;
import com.ninereader.ui.common.DocumentOpener;
import com.ninereader.ui.compare.CompareActivity;

/** Starts a tool from the Tools or Create tab. */
public final class ToolLauncher {

    private ToolLauncher() {
    }

    /**
     * @param pickPdf launcher used by reader tools to pick the document first
     */
    public static void launch(@NonNull Fragment f, @NonNull ToolCatalog.Tool tool,
                              @NonNull ActivityResultLauncher<String[]> pickPdf) {
        switch (tool.launch) {
            case READER:
                pickPdf.launch(new String[]{"application/pdf"});
                break;
            case ORGANIZE: {
                Bundle args = new Bundle();
                args.putString(ToolRunFragment.ARG_TOOL, tool.id);
                NavHostFragment.findNavController(f).navigate(R.id.organizeFragment, args);
                break;
            }
            case SIGNATURES:
                NavHostFragment.findNavController(f).navigate(R.id.action_global_signatures);
                break;
            case COMPARE:
                f.startActivity(new Intent(f.requireContext(), CompareActivity.class));
                break;
            case RUN:
            default: {
                Bundle args = new Bundle();
                args.putString(ToolRunFragment.ARG_TOOL, tool.id);
                NavHostFragment.findNavController(f).navigate(R.id.toolRunFragment, args);
                break;
            }
        }
    }

    /** Opens the picked document in the reader with the action of a reader tool. */
    public static void onPdfPickedForReader(@NonNull Fragment f, @Nullable ToolCatalog.Tool tool, @Nullable Uri uri) {
        if (uri == null || tool == null) return;
        DocumentOpener.persist(f.requireContext(), uri);
        if (tool.readerAction == null) {
            DocumentOpener.open(f.requireContext(), uri);
        } else {
            DocumentOpener.openWithAction(f.requireContext(), uri, tool.readerAction);
        }
    }
}
