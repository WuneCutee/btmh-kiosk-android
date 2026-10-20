package vn.btmh.common.widgets;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import vn.baotinmanhhai.btmhkiosk.R;

/**
 * Java port cua vn.btmh.common.widgets.ShimmerLayout (that, Kotlin) - giu dung package/ten lop vi
 * view binding cua layout that tham chieu FQCN nay truc tiep.
 */
public class ShimmerLayout extends FrameLayout {

    private static final long DURATION_MS = 1300L;
    private static final float BAND_RATIO = 0.35f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int shine;
    private final Matrix shaderMatrix = new Matrix();
    private LinearGradient shader;
    private float progress = 0f;
    private boolean running = false;
    private ValueAnimator animator;

    public ShimmerLayout(Context context) {
        this(context, null);
    }

    public ShimmerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        shine = ContextCompat.getColor(context, R.color.list_skeleton_shine);
        setWillNotDraw(false);
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        syncAnimator();
    }

    public void startShimmer() {
        running = true;
        syncAnimator();
    }

    public void stopShimmer() {
        running = false;
        syncAnimator();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float band = w * BAND_RATIO;
        shader = new LinearGradient(-band, 0f, band, 0f,
                new int[]{Color.TRANSPARENT, shine, Color.TRANSPARENT}, null, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncAnimator();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        syncAnimator();
    }

    private void syncAnimator() {
        if (animator == null) return;
        boolean shouldRun = running && isAttachedToWindow() && isShown();
        if (shouldRun && !animator.isStarted()) animator.start();
        if (!shouldRun && animator.isStarted()) animator.cancel();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        LinearGradient gradient = shader;
        if (!running || gradient == null || getWidth() == 0) {
            super.dispatchDraw(canvas);
            return;
        }
        int save = canvas.saveLayer(0f, 0f, getWidth(), getHeight(), null);
        super.dispatchDraw(canvas);
        float band = getWidth() * BAND_RATIO;
        shaderMatrix.setTranslate(-band + (getWidth() + 2 * band) * progress, 0f);
        gradient.setLocalMatrix(shaderMatrix);
        paint.setShader(gradient);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        canvas.restoreToCount(save);
    }
}
