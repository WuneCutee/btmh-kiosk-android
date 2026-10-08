package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import vn.baotinmanhhai.btmhkiosk.ui.KioskViewModel;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Khung ung dung (Application Shell) cho Home / San pham / Chi tiet / Xac nhan don hang: nen kem co dai champagne mo dan
 * o dinh man hinh (ve sau ca sidebar), Sidebar tam noi {@link RailView} bam mep trai (~16%) va Main Content Canvas ben phai.
 * Thanh dau dong nhat cua canvas: logo BTMH DO RUOU can giua tren nen champagne, roi hang [mui ten quay lai (khi khong phai
 * Home) + tieu de man hinh], toi vung noi dung ({@link #container()}) chua cac man nghiep vu. Man Kich hoat / Trang thai /
 * Website / Man cho phu toan man hinh, khong co rail.
 */
public class ShellView extends FrameLayout {

    public interface Callbacks {
        /** Cham dau an thuong hieu tren rail -> Home (bang gia). */
        void onNavHome();

        /** Muc "San pham" tren rail. */
        void onNavProducts();

        /** Muc chua co man hinh (Khuyen Mai / Hang & Diem / Nguoi dung, xem RailView.NAV_*): "Sap ra mat". */
        void onNavComingSoon(int nav);

        /** Mui ten quay lai tren thanh dau. */
        void onBack();

        /** Icon gio hang tren thanh dau (chi hien o San pham/Chi tiet, co san pham): mo man Chi tiet don hang voi toan bo gio hang. */
        void onViewCart();

        /** Nut "Đăng nhập" (chưa đăng nhập) / "Đăng xuất" (đã đăng nhập) o goc phai thanh dau Home. */
        void onLoginLogout(boolean currentlyLoggedIn);
    }

    private static final int GLOW_H = 230; // px thiet ke (chua tinh vung an toan phia tren)

    /** Dai champagne o dinh (champagne -> kem) nam duoi rail va thanh dau. */
    private static class TopGlow extends View {
        private final Paint paint = new Paint();

        TopGlow(Context context) {
            super(context);
        }

        @Override
        protected void onDraw(Canvas c) {
            int h = getHeight();
            if (h <= 0) return;
            paint.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{Ui.HEADER_CHAMPAGNE, 0xFFE9E0CC, 0x00F5EFE6},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, getWidth(), h, paint);
        }
    }

    private final LinearLayout canvas;
    private final FrameLayout homeButton;
    private final LinearLayout header;
    private final LinearLayout homeRow;
    private final TextView loginButton;
    private final TextView homeHotline;
    private final TopGlow glow;
    private final View backButton;
    private final LinearLayout titleRow;
    private final TextView title;
    private final TextView subtitle;
    private final FrameLayout container;
    private final FrameLayout cartButton;
    private final TextView cartBadge;
    private String shownSubtitle;
    private boolean boundLoggedIn;
    private boolean boundHasLoginState;

    public ShellView(Context context, Callbacks callbacks) {
        super(context);
        setBackground(Ui.pageBackground());

        glow = new TopGlow(context);
        addView(glow, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(GLOW_H), Gravity.TOP));

        // ---- Canvas (khong con Sidebar/rail — theo mockup Figma moi nhat: bo thanh dock trai, chi con nut Home tron o thanh dau) ----
        canvas = new LinearLayout(context);
        canvas.setOrientation(LinearLayout.VERTICAL);
        addView(canvas, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);

        // ---- Hang rieng cho Home (theo mockup Figma): logo vuong nho goc trai + loi chao/dang nhap + hotline goc phai ----
        homeRow = new LinearLayout(context);
        homeRow.setOrientation(LinearLayout.HORIZONTAL);
        homeRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout emblem = new FrameLayout(context);
        emblem.setBackground(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_SM, 0, 0));
        emblem.addView(new LogoView(context, true).tint(Ui.SURFACE_WHITE),
                new FrameLayout.LayoutParams(Ui.px(30), Ui.px(26), Gravity.CENTER));
        Ui.softShadow(emblem);
        homeRow.addView(emblem, new LinearLayout.LayoutParams(Ui.px(48), Ui.px(48)));

        homeRow.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f));

        LinearLayout greetCol = new LinearLayout(context);
        greetCol.setOrientation(LinearLayout.VERTICAL);
        greetCol.setGravity(Gravity.END);
        loginButton = Ui.text(context, "Đăng nhập", 9.5f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        loginButton.setGravity(Gravity.CENTER);
        loginButton.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.2f),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.2f)));
        loginButton.setPadding(Ui.px(18), 0, Ui.px(18), 0);
        Ui.pressEffect(loginButton);
        loginButton.setOnClickListener(v -> callbacks.onLoginLogout(boundLoggedIn));
        greetCol.addView(loginButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.px(40)));

        LinearLayout hotRow = new LinearLayout(context);
        hotRow.setOrientation(LinearLayout.HORIZONTAL);
        hotRow.setGravity(Gravity.CENTER_VERTICAL);
        hotRow.addView(new IconView(context, IconView.PHONE, Ui.TEXT_MUTED_LIGHT).strokeUnits(2f),
                new LinearLayout.LayoutParams(Ui.px(16), Ui.px(16)));
        homeHotline = Ui.text(context, "", Ui.T_SMALL, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM);
        LinearLayout.LayoutParams hotTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hotTextLp.setMarginStart(Ui.px(5));
        hotRow.addView(homeHotline, hotTextLp);
        LinearLayout.LayoutParams hotRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hotRowLp.topMargin = Ui.px(6);
        greetCol.addView(hotRow, hotRowLp);
        homeRow.addView(greetCol, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        header.addView(homeRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout back = new FrameLayout(context);
        back.addView(new IconView(context, IconView.ARROW_LEFT, Ui.TEXT_TITLE_DARK).strokeUnits(2.2f),
                new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        back.setClickable(true);
        back.setOnClickListener(v -> callbacks.onBack());
        Ui.pressEffect(back);
        backButton = back;
        titleRow.addView(back, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        title = Ui.text(context, "", 14f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        title.setSingleLine(true);
        titleCol.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        subtitle = Ui.text(context, "", Ui.T_SMALL, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        subtitle.setVisibility(GONE);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = Ui.px(4);
        titleCol.addView(subtitle, sLp);
        titleRow.addView(titleCol, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        // Ca dong tieu de cung bam duoc de quay lai (cham nhu mui ten) khi khong phai Home
        titleCol.setOnClickListener(v -> {
            if (backButton.getVisibility() == VISIBLE) callbacks.onBack();
        });
        titleRow.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f)); // day icon gio hang ve sat phai

        // Icon gio hang + badge so luong (chi hien o San pham/Chi tiet, xem setScreen()/setCartCount())
        cartButton = new FrameLayout(context);
        cartButton.addView(new IconView(context, IconView.CART, Ui.TEXT_TITLE_DARK).strokeUnits(2f),
                new FrameLayout.LayoutParams(Ui.px(30), Ui.px(30), Gravity.CENTER));
        cartBadge = Ui.text(context, "", 8f, Ui.SURFACE_WHITE, Ui.W_BOLD);
        cartBadge.setGravity(Gravity.CENTER);
        cartBadge.setBackground(Ui.oval(Ui.BURGUNDY_PRIMARY));
        cartBadge.setVisibility(GONE);
        FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.TOP | Gravity.END);
        cartButton.addView(cartBadge, badgeLp);
        cartButton.setClickable(true);
        cartButton.setOnClickListener(v -> callbacks.onViewCart());
        Ui.pressEffect(cartButton);
        cartButton.setVisibility(GONE);
        titleRow.addView(cartButton, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));

        // Nut "Ve trang chu" (thay cho rail da bo) — an o Home vi dang o do roi. Theo CSS Figma moi nhat (man
        // "Chon vat pham"): KHONG con pill/khoanh nen gi ca, chi icon nha VIEN do noi truc tiep tren nen.
        homeButton = new FrameLayout(context);
        homeButton.addView(new IconView(context, IconView.HOME_OUTLINE, Ui.BURGUNDY_PRIMARY).strokeUnits(2.4f),
                new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        homeButton.setClickable(true);
        homeButton.setOnClickListener(v -> callbacks.onNavHome());
        Ui.pressEffect(homeButton);
        LinearLayout.LayoutParams homeLp = new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64));
        homeLp.setMarginStart(Ui.px(8));
        titleRow.addView(homeButton, homeLp);

        header.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        canvas.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        container = new FrameLayout(context);
        container.setClipChildren(false);
        canvas.addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** Giu nut Home (chi ban DEBUG dung de mo man thu nghiem) — truoc day gan vao bieu tuong MH tren rail. */
    public void setEmblemLongPress(Runnable action) {
        homeButton.setOnLongClickListener(v -> {
            action.run();
            return true;
        });
    }

    /** Khung chua cac man nghiep vu (Home, San pham, Chi tiet, Xac nhan). */
    public FrameLayout container() {
        return container;
    }

    private void setTitle(String text, boolean withBack) {
        title.setText(text);
        backButton.setVisibility(withBack ? VISIBLE : GONE);
        // Home khong co mui ten: tieu de thut vao de thang hang voi cac man khac
        titleRow.setPadding(withBack ? 0 : Ui.px(8), 0, 0, 0);
    }

    /** Dat tieu de + muc dang chon tren rail + mui ten Quay lai theo man hinh. */
    public void setScreen(KioskViewModel.AppScreen screen) {
        boolean isHome = screen == KioskViewModel.AppScreen.HOME;
        homeButton.setVisibility(isHome ? GONE : VISIBLE);
        // Home dung hang rieng (logo vuong + chao/dang nhap), cac man khac dung hang tieu de + mui ten quay lai.
        homeRow.setVisibility(isHome ? VISIBLE : GONE);
        titleRow.setVisibility(isHome ? GONE : VISIBLE);
        // Dai champagne chi dung cho Home (theo mockup rieng cua Home) — mockup "Chon vat pham" nen phang #FFFBF4,
        // khong co dai gradient nay, nen an di de dung mau nen.
        glow.setVisibility(screen == KioskViewModel.AppScreen.PRODUCTS ? GONE : VISIBLE);
        switch (screen) {
            case PRODUCTS:
                // Theo mockup Figma "Man hinh Vang tich luy": thanh dau CHI con nut Home, khong mui ten/tieu de/gio hang
                // (o tim kiem + the "Tat ca/..." nam trong ProductsView, ngay duoi thanh dau).
                setTitle("", false);
                break;
            case DETAIL:
                setTitle("Chi tiết sản phẩm", true);
                break;
            case ORDER:
                setTitle("Thông tin đơn hàng", true);
                titleRow.setPadding(0, Ui.px(16), 0, 0); // Tieu de man xac nhan xuong thap hon mot chut
                break;
            default:
                break;
        }
        // Icon gio hang: chi hien o Chi tiet (San pham dung thanh tong hop day man thay the, xem ProductsView).
        cartButton.setVisibility(screen == KioskViewModel.AppScreen.DETAIL ? VISIBLE : GONE);
    }

    /** Loi chao/Dang nhap + hotline tren thanh dau Home (theo mockup Figma — thay cho nut rieng trong HomeView). */
    public void bindHome(String loggedInCustomerName, String hotline) {
        boolean loggedIn = loggedInCustomerName != null;
        if (loggedIn != boundLoggedIn || !boundHasLoginState) {
            boundLoggedIn = loggedIn;
            boundHasLoginState = true;
            loginButton.setText(loggedIn ? "Xin chào, " + loggedInCustomerName + " · Đăng xuất" : "Đăng nhập");
        }
        homeHotline.setText(hotline != null && !hotline.isEmpty() ? hotline : "");
    }

    /** So luong (dong) trong gio hang — hien thi dang cham so tren icon gio hang, an khi gio rong. */
    public void setCartCount(int count) {
        cartBadge.setVisibility(count > 0 ? VISIBLE : GONE);
        cartBadge.setText(count > 99 ? "99+" : String.valueOf(count));
    }

    /** Dong phu duoi tieu de (rong/null = an). */
    public void setSubtitle(String text) {
        if (java.util.Objects.equals(text, shownSubtitle)) return;
        shownSubtitle = text;
        boolean has = text != null && !text.isEmpty();
        subtitle.setText(has ? text : "");
        subtitle.setVisibility(has ? VISIBLE : GONE);
    }

    /**
     * Khong con rail nen canvas nhan het vung an toan trai/phai; thanh dau nhan cutout tren; cac man ben trong nhan
     * vung duoi (thanh dieu huong / ban phim: {@code bottom}). {@code barsBottom} khong dung nua (khong con rail).
     */
    public void onInsets(int left, int top, int right, int bottom, int barsBottom) {
        header.setPadding(Ui.px(6), top + Ui.px(10), Ui.px(16), Ui.px(4));
        canvas.setPadding(left, 0, right, 0);
        LayoutParams glp = (LayoutParams) glow.getLayoutParams();
        glp.height = top + Ui.px(GLOW_H);
        glow.setLayoutParams(glp);
        for (int i = 0; i < container.getChildCount(); i++) {
            View v = container.getChildAt(i);
            if (v instanceof Ui.InsetAware) ((Ui.InsetAware) v).onInsets(0, 0, 0, bottom);
            else v.setPadding(0, 0, 0, bottom);
        }
    }
}
