package com.ninereader.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Container whose content can be blurred by a {@link FloatingNavBar} drawn above it. From Android 12,
 * its children are recorded into a RenderNode that the bar draws again, blurred: no screenshot, no
 * bitmap copy, the GPU does all the work.
 */
public class BlurTargetLayout extends FrameLayout {

    /** Blurring a view (RenderEffect) needs Android 12. */
    public static final boolean BLUR_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

    // Typed Object: no RenderNode in the signatures of this class, which also loads on API < 29.
    @Nullable
    private final Object content;

    public BlurTargetLayout(@NonNull Context context) {
        this(context, null);
    }

    public BlurTargetLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        content = BLUR_SUPPORTED ? Api31.create() : null;
    }

    /** Content recorded at the last draw, or null before Android 12. */
    @Nullable
    @RequiresApi(Build.VERSION_CODES.S)
    RenderNode content() {
        return (RenderNode) content;
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        // Software rendering (view drawn into a Bitmap): drawRenderNode is not allowed there.
        if (BLUR_SUPPORTED && content != null && canvas.isHardwareAccelerated()) {
            Api31.record((RenderNode) content, this, canvas);
        } else {
            super.dispatchDraw(canvas);
        }
    }

    private void drawChildren(Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static final class Api31 {
        static RenderNode create() {
            return new RenderNode("BlurTarget");
        }

        static void record(RenderNode node, BlurTargetLayout layout, Canvas canvas) {
            node.setPosition(0, 0, layout.getWidth(), layout.getHeight());
            RecordingCanvas recording = node.beginRecording();
            layout.drawChildren(recording);
            node.endRecording();
            canvas.drawRenderNode(node);
        }
    }
}
