package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.FrameLayout;

import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Hinh QR gia (Phase 1 chua co cong thanh toan/API tao QR that): luoi o vuong ngau nhien CO DINH (seed theo mot gia tri
 * bat ky truyen vao, khong doi giua cac lan ve) + 3 "mat" dinh vi goc tren-trai/tren-phai/duoi-trai nhu QR that, logo
 * thuong hieu nho o chinh giua (dung {@link LogoView} dang bieu tuong). Chi de xem giao dien — khong quet duoc.
 */
public class QrPlaceholderView extends FrameLayout {

    private static final int CELLS = 21;

    private final Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean[][] grid = new boolean[CELLS][CELLS];
    private final LogoView emblem;

    public QrPlaceholderView(Context context, long seed) {
        super(context);
        dark.setColor(Ui.TEXT_TITLE_DARK);
        light.setColor(Ui.SURFACE_WHITE);
        java.util.Random r = new java.util.Random(seed);
        for (int y = 0; y < CELLS; y++) {
            for (int x = 0; x < CELLS; x++) grid[y][x] = r.nextBoolean();
        }
        emblem = new LogoView(context, true);
        emblem.tint(Ui.BURGUNDY_PRIMARY);
        emblem.setBackgroundColor(Ui.SURFACE_WHITE);
        emblem.setPadding(Ui.px(3), Ui.px(3), Ui.px(3), Ui.px(3));
        addView(emblem, new FrameLayout.LayoutParams(0, 0, android.view.Gravity.CENTER));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) emblem.getLayoutParams();
        lp.width = Math.round(w * 0.26f);
        lp.height = Math.round(h * 0.2f);
        emblem.setLayoutParams(lp);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / (float) CELLS;
        float offX = (getWidth() - s * CELLS) / 2f;
        float offY = (getHeight() - s * CELLS) / 2f;
        c.drawColor(Ui.SURFACE_WHITE);
        for (int y = 0; y < CELLS; y++) {
            for (int x = 0; x < CELLS; x++) {
                if (inFinder(x, y) || !grid[y][x]) continue;
                c.drawRect(offX + x * s, offY + y * s, offX + (x + 1) * s, offY + (y + 1) * s, dark);
            }
        }
        drawFinder(c, offX, offY, s, 0, 0);
        drawFinder(c, offX, offY, s, CELLS - 7, 0);
        drawFinder(c, offX, offY, s, 0, CELLS - 7);
    }

    private static boolean inFinder(int x, int y) {
        return (x < 7 && y < 7) || (x >= CELLS - 7 && y < 7) || (x < 7 && y >= CELLS - 7);
    }

    private void drawFinder(Canvas c, float offX, float offY, float s, int gx, int gy) {
        float x = offX + gx * s;
        float y = offY + gy * s;
        c.drawRect(x, y, x + 7 * s, y + 7 * s, dark);
        c.drawRect(x + s, y + s, x + 6 * s, y + 6 * s, light);
        c.drawRect(x + 2 * s, y + 2 * s, x + 5 * s, y + 5 * s, dark);
    }
}
