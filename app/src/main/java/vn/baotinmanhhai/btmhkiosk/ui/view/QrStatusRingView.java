package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Vong tron trang thai ve chong len {@link QrPlaceholderView} (theo mockup Figma man "Thanh toan don hang"): CHECKING =
 * vong trang cung quay; SUCCESS = vong xanh + dau check; FAILED = vong do + dau X; UNKNOWN = vong cam (khong icon).
 * NONE = an han (chua bam "Toi da chuyen khoan").
 */
public class QrStatusRingView extends View {

    public enum State { NONE, CHECKING, SUCCESS, FAILED, UNKNOWN }

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private State state = State.NONE;
    private float phase;

    public QrStatusRingView(Context context) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    public void setState(State state) {
        this.state = state == null ? State.NONE : state;
        invalidate();
    }

    public State getState() {
        return state;
    }

    public void setPhase(float phase) {
        this.phase = phase;
        if (state == State.CHECKING) invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (state == State.NONE) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f - Ui.px(4);
        rect.set(cx - r, cy - r, cx + r, cy + r);

        int color;
        switch (state) {
            case SUCCESS:
                color = Ui.UP_GREEN;
                break;
            case FAILED:
                color = Ui.DOWN_RED;
                break;
            case UNKNOWN:
                color = Ui.rgb(217, 143, 20);
                break;
            default:
                color = Ui.rgb(214, 206, 194); // CHECKING: trang/xam nhe
                break;
        }

        if (state == State.CHECKING) {
            stroke.setStrokeWidth(Ui.px(4));
            stroke.setColor(Ui.withAlpha(color, 0x55));
            c.drawCircle(cx, cy, r, stroke);
            stroke.setColor(Ui.SURFACE_WHITE);
            float start = phase * 360f;
            c.drawArc(rect, start, 110, false, stroke);
        } else {
            stroke.setStrokeWidth(Ui.px(4));
            stroke.setColor(color);
            c.drawCircle(cx, cy, r, stroke);
        }

        // Dau hieu o giua (de khong che QR nhieu: ve o mot the nho goc duoi-phai vong tron)
        float badgeR = r * 0.26f;
        float bx = cx + r * 0.72f;
        float by = cy + r * 0.72f;
        if (state == State.SUCCESS || state == State.FAILED) {
            fill.setColor(color);
            c.drawCircle(bx, by, badgeR, fill);
            stroke.setStrokeWidth(Ui.px(2.6f));
            stroke.setColor(Ui.SURFACE_WHITE);
            path.reset();
            if (state == State.SUCCESS) {
                path.moveTo(bx - badgeR * 0.45f, by);
                path.lineTo(bx - badgeR * 0.1f, by + badgeR * 0.4f);
                path.lineTo(bx + badgeR * 0.5f, by - badgeR * 0.4f);
            } else {
                path.moveTo(bx - badgeR * 0.4f, by - badgeR * 0.4f);
                path.lineTo(bx + badgeR * 0.4f, by + badgeR * 0.4f);
                path.moveTo(bx + badgeR * 0.4f, by - badgeR * 0.4f);
                path.lineTo(bx - badgeR * 0.4f, by + badgeR * 0.4f);
            }
            c.drawPath(path, stroke);
        }
    }
}
