package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import vn.baotinmanhhai.btmhkiosk.R;

/**
 * Logo BTMH (bieu tuong "M-H" do ruou tren nen trong suot) ve bang Canvas, doi mau bang {@link #tint(int)}.
 *
 * <p>R.drawable.logo gio la anh that lay tu repo chuan (chi bieu tuong, khong con ghep chung voi chu "BAO TIN
 * MANH HAI" trong 1 anh rong 350x120 nhu truoc) - nen luon ve TRON anh, khong cat theo Rect EMBLEM cu nua (cat
 * theo toa do cu se ra 1 goc rong cua anh moi, lam logo bien mat). Tham so emblemOnly giu lai cho tuong thich
 * chu ky goi ham cac noi dang dung, khong con anh huong ket qua ve.</p>
 */
public class LogoView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap bitmap;
    private final RectF dst = new RectF();

    public LogoView(Context context, boolean emblemOnly) {
        super(context);
        bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo);
    }

    /** Ve logo dat mot mau (logo goc vang tren trong suot -> loc SRC_IN giu hinh, doi mau, vd do ruou tren nen champagne). */
    public LogoView tint(int color) {
        paint.setColorFilter(new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
        invalidate();
        return this;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bitmap == null) return;
        Rect src = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        float sw = src.width();
        float sh = src.height();
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float h = getHeight() - getPaddingTop() - getPaddingBottom();
        float k = Math.min(w / sw, h / sh);
        float dw = sw * k;
        float dh = sh * k;
        float l = getPaddingLeft() + (w - dw) / 2f;
        float t = getPaddingTop() + (h - dh) / 2f;
        dst.set(l, t, l + dw, t + dh);
        c.drawBitmap(bitmap, src, dst, paint);
    }
}
