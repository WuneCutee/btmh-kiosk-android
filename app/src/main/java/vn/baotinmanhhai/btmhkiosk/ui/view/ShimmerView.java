package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Khoi giu cho (skeleton) co vet sang chay ngang — hien trong luc tai danh sach san pham. Chi 1 ValueAnimator
 * nhe, tu dung khi view roi man hinh.
 */
public class ShimmerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private LinearGradient shader;
    private ValueAnimator animator;
    private float phase;
    private final float radius;

    public ShimmerView(Context context, float radiusDesign) {
        super(context);
        radius = Ui.px(radiusDesign);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float band = Math.max(1, w) * 0.8f;
        shader = new LinearGradient(0, 0, band, 0,
                new int[]{0x00FFFFFF, 0xB3FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        paint.setShader(shader);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (shader == null) return;
        float band = getWidth() * 0.8f;
        matrix.setTranslate(-band + phase * (getWidth() + band), 0);
        shader.setLocalMatrix(matrix);
        c.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, paint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1300);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
