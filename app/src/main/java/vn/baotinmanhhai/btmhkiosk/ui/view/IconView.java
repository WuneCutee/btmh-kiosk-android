package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Bieu tuong ve bang Canvas tren luoi 24x24 (net 2, dau tron) — khong can file anh/vector, doi mau theo chu.
 * Dung cho nut quay lai, tim kiem, xoa, ban tay cham, o khoa, mat wifi, dong ho, cap nhat, gio hang, QR...
 */
public class IconView extends View {

    public static final int BACK = 0, SEARCH = 1, CLOSE = 2, TAP = 3, LOCK = 4, WIFI_OFF = 5, CLOCK = 6, UPDATE = 7,
            PAUSE = 8, CHECK = 9, CART = 10, PLUS = 11, MINUS = 12, ARROW_RIGHT = 13, QR = 14, CHEVRON_DOWN = 15,
            CHEVRON_UP = 16, PIN = 17, CAMERA = 18, PRINTER = 19, ID_CARD = 20, CHART = 21, GEM = 22, PHONE = 23,
            GOLD_BARS = 24, GIFT = 25, PROMOTION = GIFT, STAR = 26, USER = 27, ARROW_LEFT = 28, EDIT = 29, USER_ROUND = 30,
            HOME = 31, HOME_OUTLINE = 32, LOGIN = 33;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private int icon;
    private float strokeUnits = 2f;

    public IconView(Context context, int icon, int color) {
        super(context);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setColor(color);
        this.icon = icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    public void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidate();
    }

    public IconView strokeUnits(float units) {
        strokeUnits = units;
        return this;
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24f * s) / 2f, (getHeight() - 24f * s) / 2f);
        c.scale(s, s);
        stroke.setStrokeWidth(strokeUnits);
        path.reset();
        switch (icon) {
            case BACK:
                path.moveTo(15, 5);
                path.lineTo(8, 12);
                path.lineTo(15, 19);
                c.drawPath(path, stroke);
                break;
            case ARROW_RIGHT:
                c.drawLine(5, 12, 19, 12, stroke);
                path.moveTo(13, 6);
                path.lineTo(19, 12);
                path.lineTo(13, 18);
                c.drawPath(path, stroke);
                break;
            case CHEVRON_DOWN:
                path.moveTo(6, 9);
                path.lineTo(12, 15);
                path.lineTo(18, 9);
                c.drawPath(path, stroke);
                break;
            case CHEVRON_UP:
                path.moveTo(6, 15);
                path.lineTo(12, 9);
                path.lineTo(18, 15);
                c.drawPath(path, stroke);
                break;
            case SEARCH:
                c.drawCircle(10.5f, 10.5f, 6.5f, stroke);
                c.drawLine(15.4f, 15.4f, 20f, 20f, stroke);
                break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, stroke);
                c.drawLine(18, 6, 6, 18, stroke);
                break;
            case CHECK:
                path.moveTo(5, 12.5f);
                path.lineTo(10, 17.5f);
                path.lineTo(19, 7.5f);
                c.drawPath(path, stroke);
                break;
            case PLUS:
                c.drawLine(12, 5, 12, 19, stroke);
                c.drawLine(5, 12, 19, 12, stroke);
                break;
            case MINUS:
                c.drawLine(5, 12, 19, 12, stroke);
                break;
            case LOCK:
                rect.set(5, 11, 19, 21);
                c.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                path.moveTo(8, 11);
                path.lineTo(8, 8);
                rect.set(8, 3, 16, 13);
                path.arcTo(rect, 180, 180);
                path.lineTo(16, 11);
                c.drawPath(path, stroke);
                c.drawCircle(12, 16, 1.4f, fill);
                break;
            case WIFI_OFF:
                for (float r : new float[]{5f, 9.5f, 14f}) {
                    rect.set(12 - r, 19.5f - r, 12 + r, 19.5f + r);
                    c.drawArc(rect, 228, 84, false, stroke);
                }
                c.drawCircle(12, 19.5f, 1.3f, fill);
                c.drawLine(3.5f, 3.5f, 20.5f, 20.5f, stroke);
                break;
            case CLOCK:
                c.drawCircle(12, 12, 9, stroke);
                path.moveTo(12, 7);
                path.lineTo(12, 12);
                path.lineTo(15.5f, 14);
                c.drawPath(path, stroke);
                break;
            case UPDATE:
                c.drawLine(12, 3.5f, 12, 15, stroke);
                path.moveTo(7, 10.5f);
                path.lineTo(12, 15.5f);
                path.lineTo(17, 10.5f);
                c.drawPath(path, stroke);
                c.drawLine(5, 20, 19, 20, stroke);
                break;
            case PAUSE:
                c.drawCircle(12, 12, 9, stroke);
                c.drawLine(9.5f, 8.5f, 9.5f, 15.5f, stroke);
                c.drawLine(14.5f, 8.5f, 14.5f, 15.5f, stroke);
                break;
            case CART:
                path.moveTo(3, 4);
                path.lineTo(5.5f, 4);
                path.lineTo(7.8f, 15);
                path.lineTo(18, 15);
                path.lineTo(20, 7.5f);
                path.lineTo(6.2f, 7.5f);
                c.drawPath(path, stroke);
                c.drawCircle(9.5f, 19.5f, 1.4f, fill);
                c.drawCircle(16.5f, 19.5f, 1.4f, fill);
                break;
            case QR:
                rect.set(4, 4, 10, 10);
                c.drawRoundRect(rect, 1.2f, 1.2f, stroke);
                rect.set(14, 4, 20, 10);
                c.drawRoundRect(rect, 1.2f, 1.2f, stroke);
                rect.set(4, 14, 10, 20);
                c.drawRoundRect(rect, 1.2f, 1.2f, stroke);
                c.drawRect(14, 14, 16.2f, 16.2f, fill);
                c.drawRect(17.8f, 17.8f, 20, 20, fill);
                c.drawRect(17.8f, 14, 20, 16.2f, fill);
                c.drawRect(14, 17.8f, 16.2f, 20, fill);
                break;
            case PIN:
                path.moveTo(12, 21);
                path.cubicTo(6, 14.5f, 5, 12.5f, 5, 9.5f);
                path.cubicTo(5, 5.6f, 8.2f, 3, 12, 3);
                path.cubicTo(15.8f, 3, 19, 5.6f, 19, 9.5f);
                path.cubicTo(19, 12.5f, 18, 14.5f, 12, 21);
                c.drawPath(path, stroke);
                c.drawCircle(12, 9.5f, 2.4f, stroke);
                break;
            case CAMERA:
                rect.set(3, 7, 21, 19);
                c.drawRoundRect(rect, 3, 3, stroke);
                c.drawCircle(12, 13, 3.4f, stroke);
                path.moveTo(8.5f, 7);
                path.lineTo(10, 4.5f);
                path.lineTo(14, 4.5f);
                path.lineTo(15.5f, 7);
                c.drawPath(path, stroke);
                break;
            case PRINTER:
                rect.set(3, 9, 21, 18);
                c.drawRoundRect(rect, 2.5f, 2.5f, stroke);
                rect.set(7, 3.5f, 17, 9);
                c.drawRect(rect, stroke);
                rect.set(7, 14, 17, 20.5f);
                c.drawRect(rect, stroke);
                break;
            case ID_CARD:
                rect.set(3, 5, 21, 19);
                c.drawRoundRect(rect, 3, 3, stroke);
                c.drawCircle(8.5f, 11, 2, stroke);
                c.drawLine(13, 10, 18, 10, stroke);
                c.drawLine(13, 14, 18, 14, stroke);
                c.drawLine(6, 16, 11, 16, stroke);
                break;
            case CHART:
                c.drawLine(3.5f, 20.5f, 20.5f, 20.5f, stroke);
                path.moveTo(4, 16);
                path.lineTo(9, 10.5f);
                path.lineTo(13, 13.5f);
                path.lineTo(20, 5.5f);
                c.drawPath(path, stroke);
                path.reset();
                path.moveTo(15, 5.5f);
                path.lineTo(20, 5.5f);
                path.lineTo(20, 10.5f);
                c.drawPath(path, stroke);
                break;
            case GEM:
                path.moveTo(6.5f, 4);
                path.lineTo(17.5f, 4);
                path.lineTo(22, 9.5f);
                path.lineTo(12, 21);
                path.lineTo(2, 9.5f);
                path.close();
                c.drawPath(path, stroke);
                c.drawLine(2, 9.5f, 22, 9.5f, stroke);
                path.reset();
                path.moveTo(8.5f, 9.5f);
                path.lineTo(12, 4);
                path.lineTo(15.5f, 9.5f);
                path.lineTo(12, 21);
                path.close();
                c.drawPath(path, stroke);
                break;
            case PHONE:
                path.moveTo(6, 3.5f);
                path.lineTo(9.5f, 3.5f);
                path.lineTo(11, 8);
                path.lineTo(8.8f, 9.6f);
                path.cubicTo(10, 12.2f, 11.8f, 14, 14.4f, 15.2f);
                path.lineTo(16, 13);
                path.lineTo(20.5f, 14.5f);
                path.lineTo(20.5f, 18);
                path.cubicTo(20.5f, 19.2f, 19.5f, 20.2f, 18.3f, 20.2f);
                path.cubicTo(10.6f, 19.8f, 4.2f, 13.4f, 3.8f, 5.7f);
                path.cubicTo(3.8f, 4.5f, 4.8f, 3.5f, 6, 3.5f);
                path.close();
                c.drawPath(path, stroke);
                break;
            case GOLD_BARS:
                // 3 thoi vang xep chong (2 duoi + 1 tren): hinh thang, day rong o duoi
                drawIngot(c, 3f, 13.5f, 11.5f, 19.5f);
                drawIngot(c, 12.5f, 13.5f, 21f, 19.5f);
                drawIngot(c, 7.75f, 6f, 16.25f, 12.5f);
                break;
            case GIFT:
                rect.set(4, 11.5f, 20, 20.5f);
                c.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                rect.set(3, 7.5f, 21, 11.5f);
                c.drawRoundRect(rect, 1.5f, 1.5f, stroke);
                c.drawLine(12, 7.5f, 12, 20.5f, stroke);
                path.moveTo(12, 7.5f);
                path.cubicTo(8.5f, 7.5f, 6.5f, 5.8f, 7.5f, 4.3f);
                path.cubicTo(8.6f, 2.8f, 11.6f, 4.2f, 12, 7.5f);
                path.cubicTo(12.4f, 4.2f, 15.4f, 2.8f, 16.5f, 4.3f);
                path.cubicTo(17.5f, 5.8f, 15.5f, 7.5f, 12, 7.5f);
                c.drawPath(path, stroke);
                break;
            case STAR:
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float r = (i % 2 == 0) ? 9.8f : 4.3f;
                    float px = 12f + (float) Math.cos(a) * r;
                    float py = 12.8f + (float) Math.sin(a) * r;
                    if (i == 0) path.moveTo(px, py);
                    else path.lineTo(px, py);
                }
                path.close();
                c.drawPath(path, stroke);
                break;
            case USER:
                c.drawCircle(12, 8, 4, stroke);
                rect.set(4.5f, 14f, 19.5f, 29f);
                path.moveTo(4.5f, 21.5f);
                path.arcTo(rect, 180, 180);
                c.drawPath(path, stroke);
                break;
            case ARROW_LEFT:
                c.drawLine(20, 12, 4.5f, 12, stroke);
                path.moveTo(10.5f, 6);
                path.lineTo(4.5f, 12);
                path.lineTo(10.5f, 18);
                c.drawPath(path, stroke);
                break;
            case EDIT:
                // but chi: than xien + dau nhon + duong ke duoi
                path.moveTo(4.5f, 19.5f);
                path.lineTo(5.3f, 15.2f);
                path.lineTo(15.8f, 4.7f);
                path.lineTo(19.3f, 8.2f);
                path.lineTo(8.8f, 18.7f);
                path.close();
                c.drawPath(path, stroke);
                c.drawLine(13.6f, 6.9f, 17.1f, 10.4f, stroke);
                c.drawLine(11, 20.5f, 20, 20.5f, stroke);
                break;
            case USER_ROUND:
                // avatar tron: vien tron + dau + vai (cat theo hinh tron)
                c.drawCircle(12, 12, 10.2f, stroke);
                c.save();
                path.addCircle(12, 12, 10.2f, Path.Direction.CW);
                c.clipPath(path);
                path.reset();
                c.drawCircle(12, 9.3f, 3.6f, fill);
                rect.set(4.3f, 15f, 19.7f, 30f);
                c.drawOval(rect, fill);
                c.restore();
                break;
            case HOME:
                // nha don gian: mai nhon + than vuong (dung cho nut "ve trang chu" tren nen tron do)
                path.moveTo(3.2f, 11.5f);
                path.lineTo(12, 3.8f);
                path.lineTo(20.8f, 11.5f);
                c.drawPath(path, stroke);
                c.drawLine(5.4f, 10, 5.4f, 20, stroke);
                c.drawLine(18.6f, 10, 18.6f, 20, stroke);
                c.drawLine(5.4f, 20, 18.6f, 20, stroke);
                rect.set(9.8f, 13.5f, 14.2f, 20);
                c.drawRect(rect, stroke);
                break;
            case TAP:
                drawHand(c);
                break;
            case HOME_OUTLINE: {
                // icon_ht_home that cua Figma (export SVG 32x32, stroke-width 3.5, #AA1F23) — giu nguyen toa do
                // goc cua SVG, chi phong to/thu nho cuc bo canvas ve luoi 24x24 chung cua IconView (thay vi tu
                // quy doi tay tung diem cubic, de sai so). Dung cho nut "ve Home" (ShellView) va icon nut
                // "Mua vàng ngay" (man hinh cho).
                c.save();
                c.scale(24f / 32f, 24f / 32f);
                stroke.setStrokeWidth(3.5f); // dung ty le that cua SVG (3.5/32), bo qua strokeUnits() noi goi
                path.moveTo(15.9167f, 24.4167f);
                path.lineTo(15.9167f, 20.1667f);
                c.drawPath(path, stroke);
                path.reset();
                path.moveTo(2.2478f, 17.6358f);
                path.cubicTo(1.74769f, 14.3813f, 1.49764f, 12.7541f, 2.11291f, 11.3116f);
                path.cubicTo(2.72817f, 9.86909f, 4.09321f, 8.88211f, 6.82328f, 6.90817f);
                path.lineTo(8.86307f, 5.43333f);
                path.cubicTo(12.2592f, 2.97778f, 13.9573f, 1.75f, 15.9167f, 1.75f);
                path.cubicTo(17.8759f, 1.75f, 19.5741f, 2.97778f, 22.9703f, 5.43333f);
                path.lineTo(25.01f, 6.90817f);
                path.cubicTo(27.7402f, 8.88211f, 29.1052f, 9.86909f, 29.7204f, 11.3116f);
                path.cubicTo(30.3357f, 12.7541f, 30.0856f, 14.3813f, 29.5855f, 17.6358f);
                path.lineTo(29.1591f, 20.4109f);
                path.cubicTo(28.4501f, 25.0243f, 28.0956f, 27.331f, 26.4411f, 28.7072f);
                path.cubicTo(24.7866f, 30.0833f, 22.3676f, 30.0833f, 17.53f, 30.0833f);
                path.lineTo(14.3034f, 30.0833f);
                path.cubicTo(9.46565f, 30.0833f, 7.04679f, 30.0833f, 5.39225f, 28.7072f);
                path.cubicTo(3.73771f, 27.331f, 3.38323f, 25.0243f, 2.67428f, 20.4109f);
                path.lineTo(2.2478f, 17.6358f);
                c.drawPath(path, stroke);
                stroke.setStrokeWidth(strokeUnits); // khoi phuc cho icon tiep theo dung instance co the ve
                c.restore();
                break;
            }
            case LOGIN:
                // Mui ten di vao khung cua: khung ben trai (ho mot canh) + mui ten tro vao giua
                path.moveTo(11, 4);
                path.lineTo(5.5f, 4);
                path.cubicTo(4.4f, 4, 3.5f, 4.9f, 3.5f, 6);
                path.lineTo(3.5f, 18);
                path.cubicTo(3.5f, 19.1f, 4.4f, 20, 5.5f, 20);
                path.lineTo(11, 20);
                c.drawPath(path, stroke);
                c.drawLine(8.5f, 12, 21, 12, stroke);
                path.reset();
                path.moveTo(16.5f, 7);
                path.lineTo(21, 12);
                path.lineTo(16.5f, 17);
                c.drawPath(path, stroke);
                break;
            default:
                break;
        }
        c.restore();
    }

    /** Mot thoi vang hinh thang: day (x0..x1, y1) rong hon mat tren (y0). */
    private void drawIngot(Canvas c, float x0, float y0, float x1, float y1) {
        float inset = (y1 - y0) * 0.32f;
        Path p = new Path();
        p.moveTo(x0, y1);
        p.lineTo(x0 + inset, y0);
        p.lineTo(x1 - inset, y0);
        p.lineTo(x1, y1);
        p.close();
        c.drawPath(p, stroke);
    }

    /** Ban tay chi ngon tro (icon "cham de bat dau") + 2 vong song lan toa phia tren dau ngon. */
    private void drawHand(Canvas c) {
        // ngon tro
        path.moveTo(9.5f, 14f);
        path.lineTo(9.5f, 6.2f);
        path.cubicTo(9.5f, 4.3f, 12.5f, 4.3f, 12.5f, 6.2f);
        path.lineTo(12.5f, 11f);
        // ngon giua + ap ut + ut
        path.cubicTo(12.5f, 9.6f, 15.5f, 9.6f, 15.5f, 11f);
        path.cubicTo(15.5f, 9.9f, 18.5f, 9.9f, 18.5f, 11.3f);
        path.lineTo(18.5f, 15.5f);
        path.cubicTo(18.5f, 19.3f, 16.2f, 21.5f, 13f, 21.5f);
        path.lineTo(12.4f, 21.5f);
        path.cubicTo(10.3f, 21.5f, 8.8f, 20.6f, 7.7f, 19f);
        path.lineTo(4.6f, 14.6f);
        path.cubicTo(3.9f, 13.5f, 5.5f, 12.4f, 6.5f, 13.4f);
        path.lineTo(9.5f, 16.2f);
        c.drawPath(path, stroke);
        // song lan toa
        rect.set(5.2f, 0.4f, 16.8f, 12f);
        c.drawArc(rect, 205, 40, false, stroke);
        c.drawArc(rect, 295, 40, false, stroke);
    }
}
