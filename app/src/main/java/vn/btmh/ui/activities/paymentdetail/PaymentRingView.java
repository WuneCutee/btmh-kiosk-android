package vn.btmh.ui.activities.paymentdetail;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Java port cua vn.btmh.ui.activities.paymentdetail.PaymentRingView (that, Kotlin) - giu dung
 * package/ten lop vi view binding cua layout that tham chieu FQCN nay truc tiep.
 */
public class PaymentRingView extends View {

    private static final int TICKS = 60;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float rotationDeg = 0f;
    private boolean spinning = false;
    private final ValueAnimator animator;

    public PaymentRingView(Context context) {
        this(context, null);
    }

    public PaymentRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(1200L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            rotationDeg = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    public void show(int color, boolean spin) {
        paint.setColor(color);
        spinning = spin;
        if (spin && isAttachedToWindow()) animator.start(); else animator.cancel();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (spinning) animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float outer = size / 2f - size * 0.02f;
        float inner = outer - size * 0.09f;
        paint.setStrokeWidth(size * 0.022f);
        int alpha = paint.getAlpha();
        for (int i = 0; i < TICKS; i++) {
            double angle = Math.toRadians((i * 360.0 / TICKS) + rotationDeg);
            if (spinning) paint.setAlpha((int) (255 * (0.15f + 0.85f * i / TICKS)));
            canvas.drawLine(
                    (float) (cx + inner * Math.cos(angle)), (float) (cy + inner * Math.sin(angle)),
                    (float) (cx + outer * Math.cos(angle)), (float) (cy + outer * Math.sin(angle)),
                    paint);
        }
        paint.setAlpha(alpha);
    }
}
