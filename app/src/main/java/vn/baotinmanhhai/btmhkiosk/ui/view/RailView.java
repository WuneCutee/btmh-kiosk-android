package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Sidebar (Navigation Rail) ben trai, ~16% chieu rong, dang TAM NOI: mot tam vang dong/taupe chuyen sac doc bam mep trai,
 * bo tron MAT goc tren-phai (lon) va duoi-phai, chi cao ~2/3 man hinh (khong phu het chieu cao; phan con lai cua cot trai
 * de trong, noi dung o vung canvas ben phai khong bi che). Tren cung la bieu tuong MH do ruou (bam duoc -> Home), roi 4 muc:
 * icon tren + nhan trang nho duoi (muc dang chon = nhan gach chan): San Pham, Khuyen Mai, Hang & Diem, Nguoi dung (avatar tron).
 * Bang gia (Home) vao qua bieu tuong MH; khong co muc nao sang khi dang o Home ({@link #NAV_NONE}).
 */
public class RailView extends FrameLayout {

    public static final int NAV_PRODUCTS = 0, NAV_PROMOS = 1, NAV_LOYALTY = 2, NAV_ACCOUNT = 3;
    /** Khong muc nao dang chon (man Home). */
    public static final int NAV_NONE = -1;

    public interface Callbacks {
        /** Cham bieu tuong MH -> ve Home (bang gia). */
        void onHome();

        /** Muc "San pham". */
        void onProducts();

        /** Muc chua co man hinh (Khuyen Mai / Hang & Diem / Nguoi dung): chi bao "Sap ra mat". */
        void onComingSoon(int nav);
    }

    private static final int ITEM_W = 96;      // px thiet ke
    private static final int ITEM_H = 108;     // >= 64 (cham duoc de dang)
    private static final float PANEL_FRACTION = 1.0f;  // tam cao TOAN man hinh (theo yeu cau: dock trai can ca man, khong 3/4)
    private static final float R_TOP_RIGHT = 60;       // bo goc tren-phai (lon)
    private static final float R_BOTTOM_RIGHT = 44;    // bo goc duoi-phai

    private static final int ICON_COLOR = 0xFFF6E9C4;        // vang nhat: icon chua chon
    private static final int ICON_COLOR_ACTIVE = 0xFFFFFFFF; // icon dang chon

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body = new Path();
    private final RectF rect = new RectF();

    private final LinearLayout column;
    private final FrameLayout emblemMark;
    private final NavItem[] items = new NavItem[4];
    private int active = NAV_NONE;
    private int insetTop, insetBottom;
    private float panelBottom;

    public RailView(Context context, Callbacks callbacks) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        linePaint.setStyle(Paint.Style.STROKE);

        column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- Bieu tuong MH do ruou (logo vang doi mau) ----
        FrameLayout mark = new FrameLayout(context);
        LogoView emblem = new LogoView(context, true).tint(Ui.BURGUNDY_PRIMARY);
        mark.addView(emblem, new FrameLayout.LayoutParams(Ui.px(60), Ui.px(50), Gravity.CENTER));
        mark.setClickable(true);
        mark.setOnClickListener(v -> callbacks.onHome());
        Ui.pressEffect(mark);
        emblemMark = mark;
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(Ui.px(84), Ui.px(84));
        markLp.topMargin = Ui.px(18);
        column.addView(mark, markLp);

        // khoang trong tren nhom muc (nhom muc nam thap hon, giong mockup)
        column.addView(new android.view.View(context), new LinearLayout.LayoutParams(1, 0, 1f));

        // ---- Muc dieu huong ----
        int[] icons = {IconView.GOLD_BARS, IconView.PROMOTION, IconView.STAR, IconView.USER_ROUND};
        String[] labels = {"Sản Phẩm", "Khuyến Mãi", "Hạng & Điểm", "Người dùng"};
        for (int i = 0; i < items.length; i++) {
            final int nav = i;
            items[i] = new NavItem(context, icons[i], labels[i]);
            items[i].setOnClickListener(v -> {
                if (nav == NAV_PRODUCTS) callbacks.onProducts();
                else callbacks.onComingSoon(nav);
            });
            column.addView(items[i], new LinearLayout.LayoutParams(Ui.px(ITEM_W), Ui.px(ITEM_H)));
        }
        column.addView(new android.view.View(context), new LinearLayout.LayoutParams(1, 0, 1f));

        setActive(NAV_NONE);
        setInsets(0, 0, 0);
    }

    /** Vung an toan: cutout/thanh he thong phia tren (tam tran len cutout), thanh dieu huong phia duoi. */
    public void setInsets(int left, int top, int bottom) {
        insetTop = top;
        insetBottom = bottom;
        layoutColumn();
    }

    /** Giu bieu tuong MH (chi dung cho ban DEBUG: mo man thu nghiem). null = go bo. */
    public void setEmblemLongPress(Runnable action) {
        emblemMark.setOnLongClickListener(action == null ? null : v -> {
            action.run();
            return true;
        });
    }

    public void setActive(int nav) {
        active = nav;
        for (int i = 0; i < items.length; i++) items[i].setActive(i == nav);
    }

    public int active() {
        return active;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutColumn();
    }

    /** Tam cao toan man hinh (PANEL_FRACTION); cot noi dung nam giua tam: bieu tuong tren, 4 muc can doi, chua thap hon. */
    private void layoutColumn() {
        int h = getHeight();
        if (h <= 0) {
            column.setPadding(0, insetTop, 0, insetBottom);
            return;
        }
        float needed = insetTop + Ui.px(18 + 84 + 16 + ITEM_H * 4 + 46);
        panelBottom = Math.min(h, Math.max(h * PANEL_FRACTION, needed));
        column.setPadding(0, insetTop, 0, Math.round(h - panelBottom) + Ui.px(20) + insetBottom);
        invalidate();
    }

    // ==================== VE TAM ====================

    private static int blend(int a, int b, float t) {
        return Color.rgb(
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        if (w <= 0 || panelBottom <= 0) return;
        float h = panelBottom;
        float rt = Ui.px(R_TOP_RIGHT);
        float rb = Ui.px(R_BOTTOM_RIGHT);

        // Tam: goc trai vuong (bam mep man hinh), goc tren-phai + duoi-phai bo tron
        body.reset();
        body.moveTo(0, 0);
        body.lineTo(w - rt, 0);
        rect.set(w - 2 * rt, 0, w, 2 * rt);
        body.arcTo(rect, 270, 90);
        body.lineTo(w, h - rb);
        rect.set(w - 2 * rb, h - 2 * rb, w, h);
        body.arcTo(rect, 0, 90);
        body.lineTo(0, h);
        body.close();

        int mid = blend(Ui.SIDEBAR_GOLD_TOP, Ui.SIDEBAR_GOLD_BOT, 0.5f);
        fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{Ui.SIDEBAR_GOLD_TOP, mid, Ui.SIDEBAR_GOLD_BOT},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(body, fillPaint);

        // Anh kim ngang rat nhe: sang o mep trai, toi dan ve mep phai
        c.save();
        c.clipPath(body);
        sheenPaint.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0x22FFFFFF, 0x00FFFFFF, 0x1A3A2508},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, sheenPaint);
        c.restore();

        // Vien sang mong theo mep phai
        linePaint.setStrokeWidth(Math.max(1.5f, Ui.px(1.2f)));
        linePaint.setColor(0x66FFF3D6);
        c.drawPath(body, linePaint);
    }

    // ==================== MUC DIEU HUONG ====================

    private static class NavItem extends LinearLayout {
        private final IconView icon;
        private final TextView label;

        NavItem(Context context, int iconType, String text) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setClickable(true);
            setFocusable(true);
            icon = new IconView(context, iconType, ICON_COLOR).strokeUnits(1.7f);
            addView(icon, new LayoutParams(Ui.px(46), Ui.px(46)));
            label = Ui.text(context, text, 8f, Ui.SURFACE_WHITE, Ui.W_MEDIUM);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.px(8);
            addView(label, lp);
            Ui.pressEffect(this);
            setActive(false);
        }

        /** Muc dang chon: nhan gach chan + chu dam + icon trang; chua chon: icon vang nhat, nhan trang thuong. */
        void setActive(boolean on) {
            icon.setColor(on ? ICON_COLOR_ACTIVE : ICON_COLOR);
            label.setPaintFlags(on ? label.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
                    : label.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            label.setTypeface(Ui.typeface(on ? Ui.W_BOLD : Ui.W_MEDIUM));
        }
    }
}
