package com.ninereader.ui.signatures;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Drawing surface for handwritten signatures (SIG-03), finger or stylus. Stroke width follows
 * stylus pressure. Exports a tightly cropped transparent bitmap.
 */
public class SignaturePadView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final java.util.List<Path> paths = new java.util.ArrayList<>();
    private final java.util.List<Float> widths = new java.util.ArrayList<>();
    @Nullable
    private Path current;
    private final RectF bounds = new RectF();
    private boolean empty = true;
    private float baseWidth;
    @Nullable
    private Runnable onChange;

    public SignaturePadView(Context context) {
        this(context, null);
    }

    public SignaturePadView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        baseWidth = 3f * d;
        paint.setColor(0xFF0D1B4C);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        guide.setColor(0x33000000);
        guide.setStrokeWidth(d);
        setContentDescription(context.getString(com.ninereader.R.string.signature_pad_description));
    }

    public void setInkColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setOnChangeListener(@Nullable Runnable r) {
        onChange = r;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void clear() {
        paths.clear();
        widths.clear();
        current = null;
        empty = true;
        bounds.setEmpty();
        invalidate();
        if (onChange != null) onChange.run();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                current = new Path();
                current.moveTo(x, y);
                current.lineTo(x + 0.1f, y);
                paths.add(current);
                boolean stylus = e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS;
                widths.add(stylus ? baseWidth * (0.6f + e.getPressure()) : baseWidth);
                include(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (current == null) return true;
                for (int i = 0; i < e.getHistorySize(); i++) {
                    current.lineTo(e.getHistoricalX(i), e.getHistoricalY(i));
                    include(e.getHistoricalX(i), e.getHistoricalY(i));
                }
                current.lineTo(x, y);
                include(x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                current = null;
                empty = paths.isEmpty();
                if (onChange != null) onChange.run();
                return true;
            case MotionEvent.ACTION_CANCEL:
                current = null;
                empty = paths.isEmpty();
                if (onChange != null) onChange.run();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void include(float x, float y) {
        if (bounds.isEmpty() && bounds.left == 0 && bounds.right == 0) {
            bounds.set(x, y, x, y);
        } else {
            bounds.union(x, y);
        }
        empty = false;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float y = getHeight() * 0.75f;
        float m = getWidth() * 0.08f;
        canvas.drawLine(m, y, getWidth() - m, y, guide);
        for (int i = 0; i < paths.size(); i++) {
            paint.setStrokeWidth(widths.get(i));
            canvas.drawPath(paths.get(i), paint);
        }
    }

    /** Exports the signature cropped to its strokes on a transparent background. */
    @Nullable
    public Bitmap export() {
        if (paths.isEmpty()) return null;
        float pad = baseWidth * 3;
        int left = Math.max(0, Math.round(bounds.left - pad));
        int top = Math.max(0, Math.round(bounds.top - pad));
        int right = Math.min(getWidth(), Math.round(bounds.right + pad));
        int bottom = Math.min(getHeight(), Math.round(bounds.bottom + pad));
        int w = Math.max(1, right - left);
        int h = Math.max(1, bottom - top);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.translate(-left, -top);
        for (int i = 0; i < paths.size(); i++) {
            paint.setStrokeWidth(widths.get(i));
            c.drawPath(paths.get(i), paint);
        }
        return bmp;
    }
}
