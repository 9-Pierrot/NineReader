package com.ninereader.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.ninereader.ui.theme.ThemeManager;

/**
 * Miniature of an app theme in the settings: background, title bar, two cards and an accent dot.
 * The chosen theme has a thick primary outline and a check mark.
 */
public class ThemeSwatchView extends View {

    private static final float RATIO = 0.85f; // width / height

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path check = new Path();
    private final float density;
    @NonNull
    private String theme = ThemeManager.SYSTEM;

    public ThemeSwatchView(@NonNull Context context) {
        this(context, null);
    }

    public ThemeSwatchView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
    }

    public void setTheme(@NonNull String theme) {
        this.theme = theme;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(w, Math.round(w / RATIO));
    }

    /** {background, cards, title, accent} of each theme. */
    private int[] colors() {
        switch (theme) {
            case ThemeManager.LIGHT:
                return new int[]{0xFFFBFCFE, 0xFFE8EAF1, 0xFF1A1C1E, 0xFF1F45E0};
            case ThemeManager.DARK:
                return new int[]{0xFF121318, 0xFF292A2F, 0xFFE3E2E6, 0xFFB8C4FF};
            case ThemeManager.AMOLED:
                return new int[]{0xFF000000, 0xFF17171B, 0xFFFFFFFF, 0xFF6F8CFF};
            case ThemeManager.CRYSTAL:
                return new int[]{0, 0x8CFFFFFF, 0xFF141833, 0xFF3346F0};
            case ThemeManager.SEPIA:
                return new int[]{0xFFF6EEDC, 0xFFE7DBC2, 0xFF2E2416, 0xFF7A4E1D};
            default:
                return new int[]{0, 0x738E909A, 0xFF8E909A, 0xFF5C7BFF};
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = 14 * density;
        int[] c = colors();
        Path clip = new Path();
        clip.addRoundRect(0, 0, w, h, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        // Background
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        if (ThemeManager.SYSTEM.equals(theme)) {
            paint.setColor(0xFFFBFCFE);
            canvas.drawRect(0, 0, w / 2f, h, paint);
            paint.setColor(0xFF121318);
            canvas.drawRect(w / 2f, 0, w, h, paint);
        } else if (ThemeManager.CRYSTAL.equals(theme)) {
            paint.setShader(new LinearGradient(0, 0, w, h, new int[]{0xFF8FB0FF, 0xFFF3B2E0, 0xFF8DE0FF}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        } else {
            paint.setColor(c[0]);
            canvas.drawRect(0, 0, w, h, paint);
        }
        float m = 8 * density;
        // Title bar
        paint.setColor((c[2] & 0x00FFFFFF) | 0xBF000000); // 75 %
        r.set(m, m, m + (w - 2 * m) * 0.6f, m + 5 * density);
        canvas.drawRoundRect(r, 2.5f * density, 2.5f * density, paint);
        // Two cards
        paint.setColor(c[1]);
        float cardH = 16 * density;
        float top = m + 12 * density;
        for (int i = 0; i < 2; i++) {
            r.set(m, top, w - m, top + cardH);
            canvas.drawRoundRect(r, 5 * density, 5 * density, paint);
            top += cardH + 6 * density;
        }
        // Accent dot
        paint.setColor(c[3]);
        float dot = 16 * density;
        canvas.drawCircle(w - m - dot / 2f, h - m - dot / 2f, dot / 2f, paint);
        canvas.restore();

        // Outline: thick primary with a check mark when chosen
        boolean chosen = isSelected();
        paint.setStyle(Paint.Style.STROKE);
        float sw = (chosen ? 3 : 1) * density;
        paint.setStrokeWidth(sw);
        paint.setColor(MaterialColors.getColor(this, chosen ? com.google.android.material.R.attr.colorPrimary
                : com.google.android.material.R.attr.colorOutlineVariant));
        r.set(sw / 2f, sw / 2f, w - sw / 2f, h - sw / 2f);
        canvas.drawRoundRect(r, radius - sw / 2f, radius - sw / 2f, paint);
        if (chosen) {
            float cx = w / 2f;
            float cy = h / 2f + 4 * density;
            float s = 9 * density;
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, s * 1.25f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(2.5f * density);
            paint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary));
            check.reset();
            check.moveTo(cx - s * 0.55f, cy);
            check.lineTo(cx - s * 0.12f, cy + s * 0.42f);
            check.lineTo(cx + s * 0.6f, cy - s * 0.4f);
            canvas.drawPath(check, paint);
        }
    }
}
