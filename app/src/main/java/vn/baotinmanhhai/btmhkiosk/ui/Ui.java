package vn.baotinmanhhai.btmhkiosk.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Bo cuc kiosk Windows (WinForms) duoc thiet ke tren form rong 640px (ti le xem truoc 1:2). De giao dien
 * Android giong het o moi do phan giai, MOI kich thuoc duoc ghi bang "px thiet ke" (theo 640px) roi nhan
 * he so scale = chieu rong man hinh / 640. Co chu ghi theo point cua WinForms (1pt = 96/72 px).
 *
 * Ngoai ra la BO DESIGN TOKEN cua giao dien "Champagne & Ivory Luxury" (che do sang): nen kem, card trang bo goc
 * + bong nhe, do ruou (burgundy) cho nut chinh/tieu de, vang dong cho dai Sidebar, cam dat cho gia tien. Moi view
 * dung lai token de dong bo.
 */
public final class Ui {

    private Ui() {
    }

    public static final float DESIGN_WIDTH = 640f;

    // =====================================================================================
    //  DESIGN TOKENS — CHAMPAGNE LIGHT LUXURY
    // =====================================================================================

    public static final int PAGE_BG_LIGHT = Color.rgb(255, 245, 237);   // #FFF5ED nen kem sang (lay mau tu Figma High fidelity_v1.0)
    public static final int SURFACE_WHITE = Color.rgb(255, 255, 255);   // #FFFFFF card noi dung
    public static final int SIDEBAR_GOLD_TOP = Color.rgb(198, 169, 120); // #C6A978
    public static final int SIDEBAR_GOLD_BOT = Color.rgb(152, 122, 75);  // #987A4B
    public static final int BURGUNDY_PRIMARY = Color.rgb(170, 31, 35);   // #AA1F23 nut chinh, chu tag (mau do lay dung tu Figma)
    public static final int BURGUNDY_DARK = Color.rgb(136, 22, 26);      // #88161A nut nhan/press (dam hon BURGUNDY_PRIMARY)
    public static final int PRICE_ORANGE = Color.rgb(217, 74, 30);       // #D94A1E gia tien
    public static final int TEXT_TITLE_DARK = Color.rgb(42, 30, 23);     // #2A1E17 chu tieu de
    public static final int TEXT_BODY_DARK = Color.rgb(74, 60, 52);      // #4A3C34 chu mo ta
    public static final int TEXT_MUTED_LIGHT = Color.rgb(148, 134, 124); // #94867C chu phu, ma SP
    public static final int BORDER_LIGHT = Color.rgb(230, 221, 210);     // #E6DDD2 vien card nhe
    public static final int STEPPER_BG = Color.rgb(255, 255, 255);       // nen nut stepper
    public static final int HEADER_CHAMPAGNE = Color.rgb(203, 187, 152); // #CBBB98 dinh thanh dau (champagne) -> chuyen kem
    public static final int FIELD_BORDER = Color.rgb(236, 226, 198);     // #ECE2C6 vien vang nhat cua o nhap bo tron
    public static final int FIELD_GREY = Color.rgb(233, 230, 224);       // #E9E6E0 o chi doc (ngay nhan hang)
    public static final int INK_500 = Color.rgb(80, 79, 79);             // #504F4F (ColorPalette/Ink/500 cua Figma)

    // ---- Xu huong gia (dam hon ban toi de doc duoc tren nen trang) ----
    public static final int UP_GREEN = Color.rgb(21, 128, 61);
    public static final int DOWN_RED = Color.rgb(196, 36, 36);
    public static final int UP_TINT = 0x1A15803D;
    public static final int DOWN_TINT = 0x1AC42424;

    // ---- Thang bo goc (px thiet ke) ----
    public static final float R_XS = 8, R_SM = 12, R_MD = 16, R_LG = 22, R_XL = 28, R_PILL = 200;
    // ---- Thang khoang cach (px thiet ke) ----
    public static final float S_XS = 4, S_SM = 8, S_MD = 12, S_LG = 16, S_XL = 24, S_XXL = 32;
    // ---- Thang co chu (point WinForms) ----
    public static final float T_CAPTION = 7.5f, T_SMALL = 8.5f, T_BODY = 10f, T_BODY_L = 10.5f, T_TITLE = 12f,
            T_H2 = 14f, T_H1 = 17f, T_PRICE = 13f, T_PRICE_XL = 20f;
    // ---- Do dam chu ----
    public static final int W_LIGHT = 0, W_REGULAR = 1, W_MEDIUM = 2, W_BOLD = 3;

    // ---- Khung ung dung: khong con Sidebar (da bo), vung noi dung chiem full chieu rong ----
    public static final float CANVAS_W = DESIGN_WIDTH;

    private static final int PAGE_BG_LIGHT_HI = Color.rgb(255, 249, 241); // #FFF9F1 (chuyen sac nhe voi PAGE_BG_LIGHT)
    private static final int SHADOW = 0xFF6B4A2B;                        // mau bong nau am (API 28+)

    /** Man hinh nao can biet vung an toan (cutout / thanh he thong / ban phim) thi implement. */
    public interface InsetAware {
        void onInsets(int left, int top, int right, int bottom);
    }

    private static float scale = 1f;

    public static int rgb(int r, int g, int b) {
        return Color.rgb(r, g, b);
    }

    /** Goi 1 lan khi khoi dong Activity (man hinh khoa doc nen chieu rong khong doi). */
    public static void init(Context context) {
        scale = context.getResources().getDisplayMetrics().widthPixels / DESIGN_WIDTH;
    }

    /** px thiet ke (form 640px) -> px that tren man hinh. */
    public static int px(float designPx) {
        return Math.round(designPx * scale);
    }

    /** Co chu WinForms (point) -> px that tren man hinh. */
    public static float textPx(float points) {
        return points * 96f / 72f * scale;
    }

    // ---- Chu ----

    public static Typeface typeface(int weight) {
        switch (weight) {
            case W_LIGHT:
                return Typeface.create("sans-serif-light", Typeface.NORMAL);
            case W_MEDIUM:
                return Typeface.create("sans-serif-medium", Typeface.NORMAL);
            case W_BOLD:
                return Typeface.DEFAULT_BOLD;
            default:
                return Typeface.DEFAULT;
        }
    }

    /** Nhan chu day du: co chu (point), mau, do dam. */
    public static TextView text(Context context, CharSequence text, float points, int color, int weight) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx(points));
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(typeface(weight));
        return view;
    }

    public static TextView label(Context context, CharSequence text, float points, int color, boolean bold) {
        return text(context, text, points, color, bold ? W_BOLD : W_REGULAR);
    }

    /** Chu in hoa cach chu (tieu de nho / nhan cot). */
    public static TextView caps(Context context, CharSequence text, float points, int color, float letterSpacing) {
        TextView t = text(context, text, points, color, W_BOLD);
        t.setLetterSpacing(letterSpacing);
        return t;
    }

    public static int withAlpha(int color, int alpha255) {
        return (color & 0x00FFFFFF) | (alpha255 << 24);
    }

    // ---- Nen / be mat ----

    /** Nen trang mac dinh cua moi man: kem sang, chuyen sac rat nhe theo chieu doc. */
    public static GradientDrawable pageBackground() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{PAGE_BG_LIGHT_HI, PAGE_BG_LIGHT});
        d.setShape(GradientDrawable.RECTANGLE);
        return d;
    }

    /** Hinh chu nhat (radius thiet ke 0 = vuong goc) co the co vien. */
    public static GradientDrawable rect(int fill, float radiusDesign, int stroke, float strokeDesign) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fill);
        if (radiusDesign > 0) d.setCornerRadius(px(radiusDesign));
        if (strokeDesign > 0) d.setStroke(Math.max(1, px(strokeDesign)), stroke);
        return d;
    }

    /** Nen chuyen sac doc (tren -> duoi), bo goc, vien tuy chon. */
    public static GradientDrawable gradient(int top, int bottom, float radiusDesign, int stroke, float strokeDesign) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        d.setShape(GradientDrawable.RECTANGLE);
        if (radiusDesign > 0) d.setCornerRadius(px(radiusDesign));
        if (strokeDesign > 0) d.setStroke(Math.max(1, px(strokeDesign)), stroke);
        return d;
    }

    /** Card noi dung: nen trang, vien be mo BORDER_LIGHT, bo goc. */
    public static GradientDrawable card(float radiusDesign) {
        return rect(SURFACE_WHITE, radiusDesign, BORDER_LIGHT, 1);
    }

    /** Card duoc chon (hang chon tinh/cua hang): nen kem + vien do ruou. */
    public static GradientDrawable cardSelected(float radiusDesign) {
        return rect(PAGE_BG_LIGHT, radiusDesign, BURGUNDY_PRIMARY, 1.5f);
    }

    /** Vien mo, nen trang (nut phu / chip chua chon / o nhap). */
    public static GradientDrawable outline(float radiusDesign) {
        return rect(SURFACE_WHITE, radiusDesign, BORDER_LIGHT, 1);
    }

    public static GradientDrawable oval(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    public static GradientDrawable ovalStroke(int fill, int stroke, float strokeDesign) {
        GradientDrawable d = oval(fill);
        d.setStroke(Math.max(1, px(strokeDesign)), stroke);
        return d;
    }

    /** Nen 2 trang thai: binh thuong / dang nhan (dung cho nut, chi doi mau — khong phu thuoc pressEffect). */
    public static StateListDrawable pressable(GradientDrawable normal, GradientDrawable pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    /** Do cao + bong do (mau bong tuy chon tu API 28). */
    public static void elevate(android.view.View view, float designDp, int shadowColor) {
        view.setElevation(px(designDp));
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            view.setOutlineAmbientShadowColor(shadowColor);
            view.setOutlineSpotShadowColor(shadowColor);
        }
    }

    /** Bong do nhe cho card trang tren nen kem (nau am, mo). */
    public static void softShadow(android.view.View view) {
        elevate(view, 3, SHADOW);
    }

    // ---- Nut chuan ----

    /** Nut pill do ruou chinh (chu trang, nhan = BURGUNDY_DARK, bong do ruou nhe). Cao >= 64 do nguoi goi dat. */
    public static TextView primaryButton(Context context, CharSequence label, float points) {
        TextView b = text(context, label, points, SURFACE_WHITE, W_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setLetterSpacing(0.06f);
        b.setBackground(pressable(rect(BURGUNDY_PRIMARY, R_PILL, 0, 0), rect(BURGUNDY_DARK, R_PILL, 0, 0)));
        elevate(b, 4, BURGUNDY_PRIMARY);
        pressEffect(b);
        return b;
    }

    /** Nut vien be (phu): nen trang, chu do ruou. */
    public static TextView outlineButton(Context context, CharSequence label, float points) {
        TextView b = text(context, label, points, BURGUNDY_PRIMARY, W_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setLetterSpacing(0.06f);
        b.setBackground(pressable(outline(R_PILL), rect(PAGE_BG_LIGHT, R_PILL, BORDER_LIGHT, 1)));
        pressEffect(b);
        return b;
    }

    // ---- Dinh dang ----

    /** "44.550.000 VNĐ" — giong ProductDto.FormattedPrice ("{0:N0} VNĐ") cua ban WinForms. */
    public static String price(double value) {
        return number(value) + " VNĐ";
    }

    /** "3.213.000" to, kem " VNĐ" nho hon (cung 1 TextView, noi dung van la Ui.price()). */
    public static CharSequence bigPrice(double value, float unitScale) {
        String n = number(value);
        android.text.SpannableString sp = new android.text.SpannableString(n + " VNĐ");
        sp.setSpan(new android.text.style.RelativeSizeSpan(unitScale), n.length(), sp.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    /** "44.550.000" (khong don vi) — dung trong bang gia, don vi nam o tieu de cot. */
    public static String number(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format(value);
    }

    /** Ban vi san pham: 3.00 -> "3 chỉ", 0.5 -> "0,5 chỉ". */
    public static String quantity(double q) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        nf.setMaximumFractionDigits(2);
        return nf.format(q) + " chỉ";
    }

    // ---- Tien ich view ----

    /** Cat noi dung con theo goc bo cua nen (anh trong the khong tran ra ngoai goc). */
    public static void clipRounded(android.view.View view, float radiusDesign) {
        final float r = px(radiusDesign);
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View v, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        view.setClipToOutline(true);
    }

    /** Hieu ung nhan: thu nho nhe (0.96) khi cham, tra lai khi nha (van de click hoat dong binh thuong). */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    public static void pressEffect(android.view.View view) {
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }
}
