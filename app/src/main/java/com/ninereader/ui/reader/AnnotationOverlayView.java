package com.ninereader.ui.reader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ninereader.annotations.Annotation;

/** Screen-space preview of the stroke or shape being drawn (instant feedback while the finger moves). */
public class AnnotationOverlayView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private boolean hasPath;
    @Nullable
    private Annotation.Type shapeType;
    private float sx;
    private float sy;
    private float ex;
    private float ey;
    private final RectF rect = new RectF();

    public AnnotationOverlayView(Context context) {
        this(context, null);
    }

    public AnnotationOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setStyle(int color, float widthPx) {
        paint.setColor(color);
        paint.setStrokeWidth(Math.max(1f, widthPx));
    }

    public void startPath(float x, float y) {
        path.reset();
        path.moveTo(x, y);
        path.lineTo(x + 0.1f, y);
        hasPath = true;
        shapeType = null;
        setVisibility(VISIBLE);
        invalidate();
    }

    public void lineTo(float x, float y) {
        path.lineTo(x, y);
        invalidate();
    }

    public void startShape(@NonNull Annotation.Type type, float x, float y) {
        shapeType = type;
        hasPath = false;
        sx = ex = x;
        sy = ey = y;
        setVisibility(VISIBLE);
        invalidate();
    }

    public void updateShape(float x, float y) {
        ex = x;
        ey = y;
        invalidate();
    }

    public void clear() {
        path.reset();
        hasPath = false;
        shapeType = null;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (hasPath) canvas.drawPath(path, paint);
        if (shapeType == null) return;
        rect.set(Math.min(sx, ex), Math.min(sy, ey), Math.max(sx, ex), Math.max(sy, ey));
        switch (shapeType) {
            case RECT:
                canvas.drawRect(rect, paint);
                break;
            case CIRCLE:
                canvas.drawOval(rect, paint);
                break;
            case WHITEOUT:
            case REDACT:
                Paint.Style old = paint.getStyle();
                int oldColor = paint.getColor();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(shapeType == Annotation.Type.REDACT ? 0xCC000000 : 0xCCFFFFFF);
                canvas.drawRect(rect, paint);
                paint.setStyle(old);
                paint.setColor(0xFF757575);
                canvas.drawRect(rect, paint);
                paint.setColor(oldColor);
                break;
            case LINE:
            case ARROW:
                canvas.drawLine(sx, sy, ex, ey, paint);
                if (shapeType == Annotation.Type.ARROW) {
                    double angle = Math.atan2(ey - sy, ex - sx);
                    float size = Math.max(12f, paint.getStrokeWidth() * 4);
                    for (int sign = -1; sign <= 1; sign += 2) {
                        double a = angle + sign * Math.toRadians(150);
                        canvas.drawLine(ex, ey, (float) (ex + size * Math.cos(a)), (float) (ey + size * Math.sin(a)), paint);
                    }
                }
                break;
            default:
                break;
        }
    }
}
