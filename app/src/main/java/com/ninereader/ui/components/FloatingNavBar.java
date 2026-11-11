package com.ninereader.ui.components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.ninereader.R;

/**
 * Floating pill-shaped bottom bar. From Android 12, the content scrolling under it
 * ({@link BlurTargetLayout}) shows through blurred, then tinted with the theme colour (navBarFill),
 * with a thin rim. Before Android 12 the tint is made nearly opaque to stay readable.
 */
public class FloatingNavBar extends LinearLayout {

    private static final float BLUR_RADIUS_DP = 20f;
    private static final float CORNER_RADIUS_DP = 32f;
    /** Blurred colours slightly brightened, like frosted glass. */
    private static final float BLUR_SATURATION = 1.25f;
    /** Minimum opacity of the tint when nothing is blurred under it (about 92 %). */
    private static final int UNBLURRED_MIN_ALPHA = 0xEB;

    private final float cornerRadius;
    private final int blurPadding;
    private final int fillColor;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF strokeBounds = new RectF();

    /** Sliding pill under the selected tab: fractional tab index, NaN when the bar has none. */
    private float indicatorPosition = Float.NaN;
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF indicatorBounds = new RectF();
    private final float indicatorRadius;
    @Nullable
    private android.animation.ValueAnimator indicatorAnimator;
    @Nullable
    private IndicatorListener indicatorListener;

    /** Told of every position of the sliding pill (to tint the tabs it passes over). */
    public interface IndicatorListener {
        void onIndicatorMoved(float position);
    }
    /** Drawn under the content in the blur: screen background, or the window gradient (Crystal). */
    @Nullable
    private Drawable backdrop;
    @Nullable
    private final Object blurNode;
    @Nullable
    private BlurTargetLayout target;
    private final int[] location = new int[2];
    private final int[] targetLocation = new int[2];

    /**
     * Records the blur again before each frame, without invalidating the bar: its display list refers
     * to the RenderNode, so the new recording is used in the same frame. Invalidating here would
     * schedule a frame after every frame and drain the battery.
     */
    private final ViewTreeObserver.OnPreDrawListener updateBlur = () -> {
        if (BlurTargetLayout.BLUR_SUPPORTED) Api31.record(this);
        return true;
    };

    public FloatingNavBar(@NonNull Context context) {
        this(context, null);
    }

    public FloatingNavBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false); // a LinearLayout without background does not call onDraw otherwise
        float density = getResources().getDisplayMetrics().density;
        cornerRadius = CORNER_RADIUS_DP * density;
        float blurRadius = BLUR_RADIUS_DP * density;
        // Margin around the bar included in the blur: without it, its edges would be too sharp.
        blurPadding = Math.round(blurRadius * 2);

        fillColor = MaterialColors.getColor(this, R.attr.navBarFill);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(density); // 1 dp
        stroke.setColor(MaterialColors.getColor(this, R.attr.navBarStroke));

        // Pill shape: clips the blur, the tint, the children and their ripples.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius);
            }
        });
        setClipToOutline(true);
        TypedValue elevation = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.navBarElevation, elevation, true)) {
            setElevation(elevation.getDimension(getResources().getDisplayMetrics()));
        }

        indicatorRadius = 26 * density; // concentric with the 32 dp bar and its 6 dp padding
        indicatorPaint.setColor(MaterialColors.getColor(this, R.attr.navBarHighlight));
        backdrop = resolveBackdrop(context);
        blurNode = BlurTargetLayout.BLUR_SUPPORTED ? Api31.create(blurRadius) : null;
    }

    public void setIndicatorListener(@Nullable IndicatorListener listener) {
        indicatorListener = listener;
    }

    /**
     * Moves the pill of the selected tab to a (fractional) tab index. Without animation it follows the
     * finger during a swipe; with animation it slides there (tab chosen far away).
     */
    public void setIndicatorPosition(float position, boolean animate) {
        if (indicatorAnimator != null) {
            indicatorAnimator.cancel();
            indicatorAnimator = null;
        }
        if (!animate || Float.isNaN(indicatorPosition) || !isLaidOut()) {
            moveIndicator(position);
            return;
        }
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(indicatorPosition, position);
        a.setDuration(280);
        a.setInterpolator(new androidx.interpolator.view.animation.FastOutSlowInInterpolator());
        a.addUpdateListener(v -> moveIndicator((Float) v.getAnimatedValue()));
        indicatorAnimator = a;
        a.start();
    }

    public float indicatorPosition() {
        return indicatorPosition;
    }

    private void moveIndicator(float position) {
        indicatorPosition = position;
        if (indicatorListener != null) indicatorListener.onIndicatorMoved(position);
        invalidate();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && !Float.isNaN(indicatorPosition)) invalidate();
    }

    /** Pill between the tabs around the position (their bounds interpolated). */
    private boolean indicatorBounds() {
        int n = getChildCount();
        if (Float.isNaN(indicatorPosition) || n == 0) return false;
        float pos = Math.max(0, Math.min(n - 1, indicatorPosition));
        int i = (int) Math.floor(pos);
        int j = Math.min(n - 1, i + 1);
        float f = pos - i;
        View a = getChildAt(i);
        View b = getChildAt(j);
        if (a.getWidth() == 0) return false;
        indicatorBounds.set(a.getLeft() + (b.getLeft() - a.getLeft()) * f, a.getTop(),
                a.getRight() + (b.getRight() - a.getRight()) * f, a.getBottom());
        return true;
    }

    /** The content to blur under the bar. */
    public void setBlurTarget(@Nullable BlurTargetLayout target) {
        this.target = target;
        invalidate();
    }

    /**
     * Colour drawn under the content in the blur, for screens whose background is not the theme one
     * (the reader, whose background follows the reading theme).
     */
    public void setBackdropColor(int color) {
        backdrop = new ColorDrawable(color | 0xFF000000);
        invalidate();
    }

    /**
     * Opaque background drawn under the content in the blur: colorBackground when opaque, otherwise the
     * window background (theme with a transparent background over a gradient window).
     */
    @Nullable
    private static Drawable resolveBackdrop(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT
                && Color.alpha(value.data) == 0xFF) {
            return new ColorDrawable(value.data);
        }
        if (context.getTheme().resolveAttribute(android.R.attr.windowBackground, value, true)) {
            if (value.resourceId != 0) {
                Drawable drawable = ContextCompat.getDrawable(context, value.resourceId);
                return drawable == null ? null : drawable.mutate();
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return new ColorDrawable(value.data);
            }
        }
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (BlurTargetLayout.BLUR_SUPPORTED) getViewTreeObserver().addOnPreDrawListener(updateBlur);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (indicatorAnimator != null) indicatorAnimator.cancel();
        if (BlurTargetLayout.BLUR_SUPPORTED) getViewTreeObserver().removeOnPreDrawListener(updateBlur);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        boolean blurred = BlurTargetLayout.BLUR_SUPPORTED && target != null && canvas.isHardwareAccelerated()
                && Api31.draw(this, canvas);
        // 1) blur (when available)  2) theme tint  3) rim
        canvas.drawColor(blurred ? fillColor : withMinAlpha(fillColor));
        // Pill of the selected tab, under the tabs (drawn after onDraw)
        if (indicatorBounds()) canvas.drawRoundRect(indicatorBounds, indicatorRadius, indicatorRadius, indicatorPaint);
        if (Color.alpha(stroke.getColor()) > 0) {
            float inset = stroke.getStrokeWidth() / 2f;
            strokeBounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            canvas.drawRoundRect(strokeBounds, cornerRadius - inset, cornerRadius - inset, stroke);
        }
    }

    private static int withMinAlpha(int color) {
        return ColorUtils.setAlphaComponent(color, Math.max(Color.alpha(color), UNBLURRED_MIN_ALPHA));
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private static final class Api31 {
        static RenderNode create(float blurRadius) {
            RenderNode node = new RenderNode("FloatingNavBarBlur");
            ColorMatrix saturation = new ColorMatrix();
            saturation.setSaturation(BLUR_SATURATION);
            // createChainEffect(outer, inner): the blur (inner) first, then the saturation (outer).
            node.setRenderEffect(RenderEffect.createChainEffect(
                    RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(saturation)),
                    RenderEffect.createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)));
            return node;
        }

        /** Copies the part of the content under the bar (with the blur margin). */
        static void record(FloatingNavBar bar) {
            BlurTargetLayout target = bar.target;
            if (target == null || bar.getWidth() == 0 || bar.getHeight() == 0 || bar.blurNode == null) return;
            RenderNode content = target.content();
            if (content == null) return;
            bar.getLocationInWindow(bar.location);
            target.getLocationInWindow(bar.targetLocation);
            int dx = bar.location[0] - bar.targetLocation[0];
            int dy = bar.location[1] - bar.targetLocation[1];
            int pad = bar.blurPadding;

            RenderNode node = (RenderNode) bar.blurNode;
            // The node overflows by "pad" on each side: the blur samples the real neighbouring pixels
            // instead of fading at the edges. clipToOutline cuts the excess.
            node.setPosition(-pad, -pad, bar.getWidth() + pad, bar.getHeight() + pad);
            RecordingCanvas canvas = node.beginRecording();
            canvas.translate(pad - dx, pad - dy);
            // Opaque background under the content: otherwise the sharp content would show through the blur.
            if (bar.backdrop != null) {
                bar.backdrop.setBounds(0, 0, target.getWidth(), target.getHeight());
                bar.backdrop.draw(canvas);
            }
            canvas.drawRenderNode(content);
            node.endRecording();
        }

        static boolean draw(FloatingNavBar bar, Canvas canvas) {
            RenderNode node = (RenderNode) bar.blurNode;
            if (node == null || !node.hasDisplayList()) {
                record(bar);
                if (node == null || !node.hasDisplayList()) return false;
            }
            canvas.drawRenderNode(node);
            return true;
        }
    }
}
