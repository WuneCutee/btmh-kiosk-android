package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.ui.KioskViewModel;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man Home = BANG GIA VANG, nam trong khung {@link ShellView} (rail + thanh dau logo/tieu de): banner video thuong hieu
 * (the bo goc) -> the trang "bien dong gia vang theo ngay" (chon san pham, tom tat, danh sach tung ngay; chi phan danh
 * sach cuon; du lieu tu website BTMH, xem {@link GoldHistoryView}) -> nut "SAN PHAM TICH LUY" + hotline.
 */
public class HomeView extends LinearLayout implements Ui.InsetAware {

    public interface Callbacks {
        void onOpenProducts();

        void onSelectGoldProduct(String code);
    }

    private static final int BANNER_H = 118; // px thiet ke

    private final CoverVideoView video;
    private final GoldHistoryView history;
    private final TextView hotlineText;
    private final View cta;
    private final LinearLayout footer;
    private ObjectAnimator pulse;

    public HomeView(Context context, Callbacks callbacks) {
        super(context);
        setOrientation(VERTICAL);

        // ---- Banner video (the bo goc) ----
        FrameLayout banner = new FrameLayout(context);
        banner.setBackgroundColor(Ui.BURGUNDY_DARK);
        video = new CoverVideoView(context, R.raw.banner);
        banner.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        Ui.clipRounded(banner, Ui.R_LG);
        Ui.softShadow(banner);
        LayoutParams bannerLp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(BANNER_H));
        bannerLp.setMargins(Ui.px(14), Ui.px(14), Ui.px(14), Ui.px(12));
        addView(banner, bannerLp);

        // ---- Bien dong gia theo ngay ----
        history = new GoldHistoryView(context, callbacks::onSelectGoldProduct);
        LayoutParams panelLp = new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f);
        panelLp.setMargins(Ui.px(14), 0, Ui.px(14), 0);
        addView(history, panelLp);

        // ---- Nut "San pham tich luy" + hotline ----
        footer = new LinearLayout(context);
        footer.setOrientation(VERTICAL);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setClipChildren(false);
        footer.setClipToPadding(false);
        footer.setPadding(Ui.px(14), Ui.px(14), Ui.px(14), Ui.px(10));

        // Ben ngoai "tho" (pulse), ben trong co hieu ung nhan — tach 2 lop de scale khong tranh chap.
        FrameLayout ctaHolder = new FrameLayout(context);
        ctaHolder.setClipChildren(false);
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.pressable(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0),
                Ui.rect(Ui.BURGUNDY_DARK, Ui.R_PILL, 0, 0)));
        Ui.elevate(button, 5, Ui.BURGUNDY_PRIMARY);
        TextView buttonText = Ui.text(context, "SẢN PHẨM TÍCH LUỸ", 12.5f, Ui.SURFACE_WHITE, Ui.W_BOLD);
        buttonText.setLetterSpacing(0.06f);
        button.addView(buttonText, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        IconView arrowIcon = new IconView(context, IconView.ARROW_RIGHT, Ui.SURFACE_WHITE).strokeUnits(2.4f);
        LayoutParams arrowLp = new LayoutParams(Ui.px(26), Ui.px(26));
        arrowLp.setMarginStart(Ui.px(12));
        button.addView(arrowIcon, arrowLp);
        button.setOnClickListener(v -> callbacks.onOpenProducts());
        Ui.pressEffect(button);
        ctaHolder.addView(button, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.px(68)));
        cta = ctaHolder;
        footer.addView(ctaHolder, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // ---- Nut "San pham trang suc" (theo mockup Figma "Home_chua login": 2 nut xep chong, nut duoi nhe hon) ----
        // Cac danh muc trang suc (Vong tay vang, Charm bac, Day chuyen vang...) da co du lieu that va man San pham da
        // loc duoc — nut nay dung lai dung man San pham, chi them loi vao rieng tu Home cho khop mockup.
        TextView jewelryButton = Ui.text(context, "SẢN PHẨM TRANG SỨC", 12.5f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        jewelryButton.setGravity(Gravity.CENTER);
        jewelryButton.setLetterSpacing(0.06f);
        jewelryButton.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f)));
        jewelryButton.setOnClickListener(v -> callbacks.onOpenProducts());
        Ui.pressEffect(jewelryButton);
        LayoutParams jewelryLp = new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(68));
        jewelryLp.topMargin = Ui.px(12);
        footer.addView(jewelryButton, jewelryLp);

        hotlineText = Ui.text(context, "", 9f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        hotlineText.setGravity(Gravity.CENTER);
        hotlineText.setLetterSpacing(0.03f);
        LayoutParams hotLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        hotLp.topMargin = Ui.px(10);
        footer.addView(hotlineText, hotLp);
        addView(footer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /** Vung an toan phia tren do thanh dau cua khung lo; o day chi con thanh dieu huong / ban phim phia duoi. */
    @Override
    public void onInsets(int l, int t, int r, int b) {
        setPadding(l, 0, r, 0);
        footer.setPadding(Ui.px(14), Ui.px(14), Ui.px(14), Ui.px(10) + b);
    }

    /** Dong phu duoi tieu de "BANG GIA VANG" (thanh dau cua khung). */
    public static String subtitle(KioskViewModel.UiState state) {
        return state.goldHistory != null && state.goldHistory.dataPoints != null && !state.goldHistory.dataPoints.isEmpty()
                ? "Biến động giá theo ngày · VNĐ/chỉ · Dữ liệu đến "
                + state.goldHistory.dataPoints.get(state.goldHistory.dataPoints.size() - 1).date
                : "Biến động giá theo ngày · VNĐ/chỉ";
    }

    public void bind(KioskViewModel.UiState state) {
        history.bind(state.goldHistory, state.goldCode, state.goldLoading, state.goldError, state.hotline);
        hotlineText.setText(state.hotline != null && !state.hotline.isEmpty()
                ? "Hotline hỗ trợ: " + state.hotline : "");
    }

    public void pauseVideo() {
        video.pause();
    }

    public void resumeVideo() {
        video.resume();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Nut CTA "tho" rat nhe de thu hut khach cham.
        pulse = ObjectAnimator.ofPropertyValuesHolder(cta,
                android.animation.PropertyValuesHolder.ofFloat(SCALE_X, 1f, 1.02f),
                android.animation.PropertyValuesHolder.ofFloat(SCALE_Y, 1f, 1.02f));
        pulse.setDuration(1400);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (pulse != null) pulse.cancel();
        super.onDetachedFromWindow();
    }
}
