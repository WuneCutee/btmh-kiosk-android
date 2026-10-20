package vn.btmh.ui.customviews;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Java port cua vn.btmh.ui.customviews.DashedCircleView (that, Kotlin) - giu dung package/ten lop
 * vi view binding cua cac layout enroll_face/enroll_card/face_auth that tham chieu FQCN nay truc tiep.
 */
public class DashedCircleView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean isSuccess = false;
    private boolean isError = false;
    private float progress = 0f;
    private float rotationAngle = 0f;
    private boolean isSpinningMode = false;
    private int numDashes = 100;

    public DashedCircleView(Context context) {
        this(context, null, 0);
    }

    public DashedCircleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DashedCircleView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setSuccess(boolean value) {
        isSuccess = value;
        invalidate();
    }

    public void setError(boolean value) {
        isError = value;
        invalidate();
    }

    public void setProgress(float value) {
        progress = Math.max(0f, Math.min(100f, value));
        invalidate();
    }

    public void setRotationAngle(float value) {
        rotationAngle = value;
        invalidate();
    }

    public void setSpinningMode(boolean value) {
        isSpinningMode = value;
        invalidate();
    }

    public void setNumDashes(int value) {
        numDashes = value;
        invalidate();
    }

    /** Tang (hoac giam) phan tram tien trinh hien tai. */
    public void advanceProgress(float percent) {
        setProgress(progress + percent);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - 20f;

        paint.setStrokeWidth(6f);
        float dashLength = 24f;

        canvas.save();
        if (isSpinningMode) {
            canvas.rotate(rotationAngle, cx, cy);
        }

        int progressDashes = (int) (progress / 100f * numDashes);

        for (int i = 0; i < numDashes; i++) {
            float angle = i * (360f / numDashes);

            if (isError) {
                paint.setColor(Color.parseColor("#FF0000"));
            } else if (isSuccess) {
                paint.setColor(Color.parseColor("#4CAF50"));
            } else if (isSpinningMode) {
                int alpha = (int) (255 * ((float) i / numDashes));
                paint.setColor(Color.argb(alpha, 76, 175, 80));
            } else if (i < progressDashes) {
                paint.setColor(Color.parseColor("#4CAF50"));
            } else {
                paint.setColor(Color.BLACK);
            }

            canvas.save();
            canvas.rotate(angle, cx, cy);
            canvas.drawLine(cx, cy - radius, cx, cy - radius + dashLength, paint);
            canvas.restore();
        }

        canvas.restore();

        if (isError) {
            paint.setColor(Color.parseColor("#FF0000"));
            float innerRadius = 80f;
            paint.setStrokeWidth(4f);
            canvas.drawCircle(cx, cy, innerRadius, paint);

            float xSize = 30f;
            canvas.drawLine(cx - xSize, cy - xSize, cx + xSize, cy + xSize, paint);
            canvas.drawLine(cx - xSize, cy + xSize, cx + xSize, cy - xSize, paint);

        } else if (isSuccess) {
            paint.setColor(Color.parseColor("#4CAF50"));
            float innerRadius = 80f;
            paint.setStrokeWidth(4f);
            canvas.drawCircle(cx, cy, innerRadius, paint);

            Path path = new Path();
            path.moveTo(cx - 25f, cy + 5f);
            path.lineTo(cx - 5f, cy + 25f);
            path.lineTo(cx + 30f, cy - 20f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            canvas.drawPath(path, paint);
        }
    }
}
