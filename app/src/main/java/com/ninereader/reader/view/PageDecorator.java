package com.ninereader.reader.view;

import android.graphics.Canvas;

import androidx.annotation.NonNull;

/**
 * Draws page overlays (search hits, selection, annotations being edited, TTS sentence, form fields)
 * on top of a rendered page. Coordinates are those of the hosting {@link SpreadView}.
 */
public interface PageDecorator {
    void decorate(@NonNull Canvas canvas, @NonNull PageFrame frame);
}
