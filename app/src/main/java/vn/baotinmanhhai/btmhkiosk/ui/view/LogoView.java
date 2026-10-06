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
 * Logo BTMH (vang tren nen trong suot, 350x120) ve bang Canvas: nguyen logo, hoac chi bieu tuong "MH" (cat phan tren) de
 * lam dau an thuong hieu o dai Sidebar. Logo vang nen dat tren nen toi, hoac doi mau bang {@link #tint(int)} (do ruou tren nen champagne).
 */
public class LogoView extends View {

    /** Vung bieu tuong "MH" trong anh logo 350x120. */
    private static final Rect EMBLEM = new Rect(126, 0, 224, 80);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap bitmap;
    private final boolean emblemOnly;
    private final RectF dst = new RectF();

    public LogoView(Context context, boolean emblemOnly) {
        super(context);
        this.emblemOnly = emblemOnly;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false; // drawable-nodpi: giu nguyen 350x120
        bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo, o);
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
        Rect src = emblemOnly ? EMBLEM : new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
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
