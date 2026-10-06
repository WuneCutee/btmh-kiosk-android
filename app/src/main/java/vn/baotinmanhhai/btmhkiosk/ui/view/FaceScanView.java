package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Vong tron quet khuon mat (UI tinh, khong co camera/FacePOS): CHO = vong tron be trong; DANG XAC MINH = hinh nguoi trung tinh
 * (ve bang Canvas, KHONG phai anh that) + vong xanh: cung quay + song lan toa. Hoat anh do {@link GoodsPickupView} cap "pha" 0..1.
 */
class FaceScanView extends View {

    static final int GREEN_RING = Ui.rgb(52, 168, 83);
    private static final int FACE_BG = Ui.rgb(243, 238, 230);
    private static final int FACE_BG_ACTIVE = Ui.rgb(236, 231, 223);
    private static final int SILHOUETTE = Ui.rgb(190, 178, 166);

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private boolean verifying;
    private float phase;

    FaceScanView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
    }

    void setVerifying(boolean verifying) {
        this.verifying = verifying;
        invalidate();
    }

    void setPhase(float phase) {
        this.phase = phase;
        if (verifying) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float big = Math.min(getWidth(), getHeight()) / 2f;
        float faceR = big * 0.80f;

        // Nen vong tron + vien
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(verifying ? FACE_BG_ACTIVE : FACE_BG);
        c.drawCircle(cx, cy, faceR, fill);
        stroke.setStrokeWidth(Math.max(1f, Ui.px(1.5f)));
        stroke.setColor(verifying ? Ui.withAlpha(GREEN_RING, 0xFF) : Ui.BORDER_LIGHT);
        c.drawCircle(cx, cy, faceR, stroke);

        if (!verifying) return;

        // Hinh nguoi trung tinh (dau + vai), cat theo vong tron
        c.save();
        clip.reset();
        clip.addCircle(cx, cy, faceR - Ui.px(1), Path.Direction.CW);
        c.clipPath(clip);
        fill.setColor(SILHOUETTE);
        c.drawCircle(cx, cy - faceR * 0.14f, faceR * 0.30f, fill);
        rect.set(cx - faceR * 0.66f, cy + faceR * 0.28f, cx + faceR * 0.66f, cy + faceR * 1.6f);
        c.drawOval(rect, fill);
        c.restore();

        // Vong xanh: cung quay (2 cung doi xung)
        float ringR = big * 0.90f;
        rect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        stroke.setStrokeWidth(Ui.px(4));
        stroke.setColor(GREEN_RING);
        float start = phase * 360f;
        c.drawArc(rect, start, 95, false, stroke);
        stroke.setColor(Ui.withAlpha(GREEN_RING, 0x80));
        c.drawArc(rect, start + 180f, 55, false, stroke);
        stroke.setColor(Ui.withAlpha(GREEN_RING, 0x40));
        stroke.setStrokeWidth(Ui.px(2));
        c.drawCircle(cx, cy, ringR, stroke);

        // Song lan toa nhe (giu vien ngoai cung trong khung)
        float t = (phase * 2f) % 1f;
        float rippleR = ringR + (big - ringR - Ui.px(2)) * t;
        stroke.setStrokeWidth(Ui.px(2));
        stroke.setColor(Ui.withAlpha(GREEN_RING, Math.round(0x70 * (1f - t))));
        c.drawCircle(cx, cy, rippleR, stroke);
    }
}
