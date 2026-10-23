package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.view.LayoutInflater;

import vn.baotinmanhhai.btmhkiosk.databinding.FragmentHeaderBinding;
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

    private final LinearLayout canvas;
    private final View homeButton;
    private final LinearLayout header;
    private final LinearLayout homeRow;
    private final TextView loginButton;
    private final TextView homeHotline;
    private final View backButton;
    private final View titleRow;
    private final TextView title;
    private final FrameLayout container;
    private String shownSubtitle;
    private boolean boundLoggedIn;
    private boolean boundHasLoginState;

    public ShellView(Context context, Callbacks callbacks) {
        super(context);
        // activity_home.xml/activity_product_list.xml... THAT deu dung android:background="@drawable/bg" (anh
        // PNG that, khong phai gradient tu ve) cho chinh man hinh noi dung. Truoc day ShellView (khung thanh dau +
        // nut Home) lai tu ve rieng Ui.pageBackground() (RadialGradient uoc luong, vi tri glow khac anh that) -
        // 2 nen chong len nhau lech vi tri tai ranh gioi header/noi dung, nhin nhu "lech nen" o quanh icon Home.
        // Dung THANG anh that cho ca ShellView de dam bao lien tuc tuyet doi voi noi dung ben duoi.
        setBackground(context.getResources().getDrawable(vn.baotinmanhhai.btmhkiosk.R.drawable.bg, context.getTheme()));

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

        // activity_home.xml that: ivLogo la anh logo_mark THUONG (scaleType fitCenter), KHONG co khung/nen do
        // boc quanh - truoc day boc trong 1 "emblem" FrameLayout nen do bo goc day khong co trong thiet ke that.
        // home_logo_width=82dp / home_logo_height=75dp / home_logo_start=38dp / home_logo_top=3dp.
        LogoView homeLogo = new LogoView(context, true).tint(Ui.BURGUNDY_PRIMARY);
        LinearLayout.LayoutParams homeLogoLp = new LinearLayout.LayoutParams(Ui.px(82), Ui.px(75));
        homeLogoLp.setMarginStart(Ui.px(38));
        homeLogoLp.topMargin = Ui.px(3);
        homeRow.addView(homeLogo, homeLogoLp);

        homeRow.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f));

        // activity_home.xml that: cot phai la llPills rong CO DINH 145dp, THU TU la hotline chip (tren) roi
        // nut dang nhap (duoi, marginTop=home_pill_gap 8dp) - truoc day bi dao nguoc thu tu va kich thuoc sai.
        LinearLayout greetCol = new LinearLayout(context);
        greetCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout hotRow = new LinearLayout(context);
        hotRow.setOrientation(LinearLayout.HORIZONTAL);
        hotRow.setGravity(Gravity.CENTER_VERTICAL);
        hotRow.setBackground(Ui.rect(0x66FFFFFF, Ui.R_PILL, 0, 0)); // bg_standby_chip that: standby_chip_background
        hotRow.setPadding(Ui.px(16), 0, Ui.px(16), 0); // home_pill_padding
        hotRow.addView(new IconView(context, IconView.PHONE, 0xFF504F4F).strokeUnits(2f), // standby_chip_icon
                new LinearLayout.LayoutParams(Ui.px(20), Ui.px(20))); // home_pill_icon
        homeHotline = Ui.text(context, "", 16f, Color.BLACK, Ui.W_MEDIUM); // HomePillText: home_text_body/black
        LinearLayout.LayoutParams hotTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hotTextLp.setMarginStart(Ui.px(6)); // home_pill_icon_gap
        hotRow.addView(homeHotline, hotTextLp);
        greetCol.addView(hotRow, new LinearLayout.LayoutParams(Ui.px(145), Ui.px(44))); // home_pill_width/height

        LinearLayout loginRow = new LinearLayout(context);
        loginRow.setOrientation(LinearLayout.HORIZONTAL);
        loginRow.setGravity(Gravity.CENTER);
        loginRow.setBackground(Ui.pressable(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0),
                Ui.rect(Ui.BURGUNDY_DARK, Ui.R_PILL, 0, 0))); // bg_pill_red
        loginRow.setPadding(Ui.px(16), 0, Ui.px(16), 0); // home_pill_padding
        loginRow.addView(new IconView(context, IconView.LOGIN, 0xFFF1F1F1).strokeUnits(2f), // standby_login_icon
                new LinearLayout.LayoutParams(Ui.px(20), Ui.px(20))); // home_pill_icon
        loginButton = Ui.text(context, "Đăng Nhập", 16f, 0xFFDDDEE0, Ui.W_MEDIUM); // standby_login_text
        LinearLayout.LayoutParams loginTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        loginTextLp.setMarginStart(Ui.px(6)); // home_pill_icon_gap
        loginRow.addView(loginButton, loginTextLp);
        Ui.pressEffect(loginRow);
        loginRow.setOnClickListener(v -> callbacks.onLoginLogout(boundLoggedIn));
        LinearLayout.LayoutParams loginLp = new LinearLayout.LayoutParams(Ui.px(145), Ui.px(32)); // home_login_height
        loginLp.topMargin = Ui.px(8); // home_pill_gap
        greetCol.addView(loginRow, loginLp);
        homeRow.addView(greetCol, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        header.addView(homeRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // fragment_header.xml that (dung chung moi man khac Home/Order): mui ten quay lai trai, tieu de, nut Home
        // tron phai. Inflate dung layout that thay vi tu ve bang Ui.px() de khop pixel tuyet doi.
        FragmentHeaderBinding headerBinding = FragmentHeaderBinding.inflate(LayoutInflater.from(context), header, false);
        titleRow = headerBinding.getRoot();
        backButton = headerBinding.ivBack;
        backButton.setOnClickListener(v -> callbacks.onBack());
        title = headerBinding.tvTitle;
        View home = headerBinding.ivHome;
        home.setOnClickListener(v -> callbacks.onNavHome());
        homeButton = home;
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
        // Giu padding 56/32 cua fragment_header (truoc day ghi de ve 0 -> nut Home lech so voi cac man khac).
        int padH = getResources().getDimensionPixelSize(vn.baotinmanhhai.btmhkiosk.R.dimen.header_padding_h);
        int padTop = getResources().getDimensionPixelSize(vn.baotinmanhhai.btmhkiosk.R.dimen.header_padding_top);
        titleRow.setPadding(padH + (withBack ? 0 : Ui.px(8)), padTop, padH, 0);
    }

    /** Dat tieu de + muc dang chon tren rail + mui ten Quay lai theo man hinh. */
    public void setScreen(KioskViewModel.AppScreen screen) {
        boolean isHome = screen == KioskViewModel.AppScreen.HOME;
        boolean isOrder = screen == KioskViewModel.AppScreen.ORDER;
        homeButton.setVisibility(isHome ? GONE : VISIBLE);
        // Home dung hang rieng (logo vuong + chao/dang nhap), cac man khac dung hang tieu de + mui ten quay lai.
        homeRow.setVisibility(isHome ? VISIBLE : GONE);
        // Man Xac nhan don hang tu ve header rieng (back/home/tieu de) -> an HET ca khung header dung chung (khong
        // chi an titleRow) de khong con dai dem rong phia tren no (truoc day con mot soc mong gay "lech hang").
        header.setVisibility(isOrder ? GONE : VISIBLE);
        titleRow.setVisibility(isHome || isOrder ? GONE : VISIBLE);
        // Dai champagne chi dung cho Home (theo mockup rieng cua Home) — mockup "Chon vat pham" nen phang #FFFBF4,
        // khong co dai gradient nay, nen an di de dung mau nen.
        switch (screen) {
            case PRODUCTS:
                // Theo mockup Figma "Man hinh Vang tich luy": thanh dau CHI con nut Home, khong mui ten/tieu de/gio hang
                // (o tim kiem + the "Tat ca/..." nam trong ProductsView, ngay duoi thanh dau).
                setTitle("", false);
                break;
            case DETAIL:
                // Tieu de = TEN SAN PHAM (activity_product_detail.xml that dung title dong, khong phai chu tinh
                // "Chi tiet san pham") - dat tam placeholder, KioskActivity.render() se goi setDetailTitle() ngay
                // sau do voi ten that.
                setTitle("", true);
                break;
            default:
                break;
        }
    }

    /** Tieu de dong cua man Chi tiet = TEN SAN PHAM (activity_product_detail.xml that). */
    public void setDetailTitle(String productName) {
        setTitle(productName != null ? productName : "", true);
    }

    /**
     * Loi chao/Dang nhap + hotline tren thanh dau Home. Figma node 1085:11214 ("Bang gia vang" da dang nhap):
     * nut chi doi thanh "Đăng xuất" don gian (KHONG them "Xin chao, {ten} ·" truoc do) - man nay la thiet ke
     * chua tung dua vao code that (activity_home.xml goc chi co 1 trang thai "chua login"), nhung theo Figma
     * chinh xac hon suy doan "Xin chao" truoc day.
     */
    public void bindHome(String loggedInCustomerName, String hotline) {
        boolean loggedIn = loggedInCustomerName != null;
        if (loggedIn != boundLoggedIn || !boundHasLoginState) {
            boundLoggedIn = loggedIn;
            boundHasLoginState = true;
            loginButton.setText(loggedIn ? "Đăng xuất" : "Đăng Nhập");
        }
        homeHotline.setText(hotline != null && !hotline.isEmpty() ? hotline : "");
    }

    /** fragment_header.xml that khong co icon gio hang (vao gio hang qua nut "TIEP TUC" o ProductsView) - no-op,
     * giu lai chi de KioskActivity.java khong phai sua goi. */
    public void setCartCount(int count) {
    }

    /** fragment_header.xml that khong co o phu duoi tieu de - no-op (HomeView.subtitle() da luon tra ve null). */
    public void setSubtitle(String text) {
        shownSubtitle = text;
    }

    /**
     * Khong con rail nen canvas nhan het vung an toan trai/phai; thanh dau nhan cutout tren; cac man ben trong nhan
     * vung duoi (thanh dieu huong / ban phim: {@code bottom}). {@code barsBottom} khong dung nua (khong con rail).
     */
    public void onInsets(int left, int top, int right, int bottom, int barsBottom) {
        // Nut Home phai CUNG vi tri o moi man (Figma: khung 66, cach tren 56, cach phai 32 - dung fragment_header):
        // khung header KHONG them padding, le cu (6/10/16) chi ap cho homeRow cua man Home.
        header.setPadding(0, top, 0, Ui.px(4));
        homeRow.setPadding(Ui.px(6), Ui.px(60), Ui.px(16), 0); // Figma Home: hang logo bat dau o y=63 (logo marginTop 3)
        canvas.setPadding(left, 0, right, 0);
        for (int i = 0; i < container.getChildCount(); i++) {
            View v = container.getChildAt(i);
            if (v instanceof Ui.InsetAware) ((Ui.InsetAware) v).onInsets(0, 0, 0, bottom);
            else v.setPadding(0, 0, 0, bottom);
        }
    }
}
