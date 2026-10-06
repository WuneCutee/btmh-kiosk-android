package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.network.AdsManager;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man cho quang cao (spec II.2.2). Phat theo order: IMAGE hien displaySeconds giay; VIDEO phat het (hoac cat
 * sau displaySeconds neu > 0) roi tu chuyen; het danh sach quay lai muc dau. Khach vuot ngang de chuyen muc;
 * cham vao banner/nut "Mua ngay" de thoat ve Home. Khong co quang cao tai duoc -> phat anh mac dinh dong goi
 * san trong App (30s/muc). Muc loi bi bo qua.
 *
 * Giao dien theo mockup Figma "Man hinh cho": anh/video quang cao phu kin toan man hinh (edge-to-edge, kieu
 * "cover"), logo + nut goi hotline/dang nhap noi o thanh dau, tam cham tien do va nut CTA do ruou "Mua vang ngay"
 * noi truc tiep tren anh gan day man.
 */
public class IdlePlayerView extends FrameLayout implements Ui.InsetAware {

    public interface Callbacks {
        /** Cham vao man cho (banner hoac nut "Mua ngay"); ad = null khi dang phat anh mac dinh. */
        void onTap(Models.AdDto ad);

        void onPlaybackError(Models.AdDto ad, String message);

        /** Nut "Đăng nhập" (chưa đăng nhập) / "Đăng xuất" (đã đăng nhập) o thanh dau man cho. */
        void onLoginLogout(boolean currentlyLoggedIn);
    }

    private static final int DEFAULT_SLIDE_SECONDS = 30;
    private static final long VIDEO_SAFETY_MS = 10 * 60 * 1000L;
    private static final int SWIPE_DISTANCE_PX_DESIGN = 120;

    /** Thiet ke goc cua mockup nay la khung 540px (rieng man hinh nay, khac DESIGN_WIDTH=640 chung cua Ui). */
    private static final float DESIGN_W = 540f;

    private static final int LOGIN_RED = 0xFFAA1F23;
    private static final int PHONE_TEXT = Ui.INK_500;
    private static final int LOGIN_TEXT = 0xFFDDDEE0;
    private static final int GLASS_WHITE = 0x66FFFFFF; // rgba(255,255,255,0.4) ~ lam tron ve he 0-255

    /** Mot muc dang phat: quang cao server (ad + file) hoac anh mac dinh (uri assets). */
    private static class Item {
        final Models.AdDto ad;
        final File file;
        final String uri;

        Item(Models.AdDto ad, File file, String uri) {
            this.ad = ad;
            this.file = file;
            this.uri = uri;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float scale540;
    private final FrameLayout bannerCard;
    private final ImageView image;
    private final CoverVideoView video;
    private final LinearLayout dots;
    private final FrameLayout headerRow;
    private final TextView phoneLabel;
    private final TextView loginLabel;
    private final FrameLayout ctaRow;
    private final GestureDetector gestures;
    private final Callbacks callbacks;

    private final List<Item> items = new ArrayList<>();
    private int index = 0;
    private boolean active = false;
    private boolean loggedIn = false;
    private String hotline = "";

    private final Runnable advance = this::next;

    public IdlePlayerView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        scale540 = context.getResources().getDisplayMetrics().widthPixels / DESIGN_W;
        setBackgroundColor(Ui.BURGUNDY_DARK); // nen letterbox thay cho cream, chi lo ra truoc khi anh/video dau tien tai xong

        // ---- Banner quang cao phu kin toan man hinh (edge-to-edge, dung "cover" nhu nen Figma) ----
        bannerCard = new FrameLayout(context);
        addView(bannerCard, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP); // phu kin man hinh, giong nen "cover" cua Figma
        bannerCard.addView(image, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        video = new CoverVideoView(context);
        video.setCover(true);
        video.setLooping(false);
        video.setVisibility(GONE);
        video.setEvents(new CoverVideoView.Events() {
            @Override
            public void onCompleted() {
                handler.post(IdlePlayerView.this::next);
            }

            @Override
            public void onError(String message) {
                handler.post(() -> {
                    Item item = current();
                    if (item != null && item.ad != null) callbacks.onPlaybackError(item.ad, message);
                    next(); // bo qua muc loi, phat tiep
                });
            }
        });
        bannerCard.addView(video, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // Cham tien do (vi tri muc dang phat), noi trong banner gan day
        dots = new LinearLayout(context);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotsLp.bottomMargin = dp(139); // tam cham noi truc tiep tren anh, ngay tren nut CTA (toa do Figma: y=814.9/960)
        bannerCard.addView(dots, dotsLp);

        // ---- Nut CTA do ruou "Mua ngay", noi truc tiep tren anh/video gan day man (khong con nen kem rieng) ----
        ctaRow = new FrameLayout(context);
        FrameLayout.LayoutParams ctaRowLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, dp(66), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        ctaRowLp.bottomMargin = dp(53); // toa do Figma: 960 - (841 + 66)
        addView(ctaRow, ctaRowLp);

        LinearLayout cta = new LinearLayout(context);
        cta.setOrientation(LinearLayout.HORIZONTAL);
        cta.setGravity(Gravity.CENTER);
        cta.setPadding(dp(24), dp(14), dp(24), dp(14));
        cta.setBackground(Ui.pressable(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0), Ui.rect(Ui.BURGUNDY_DARK, Ui.R_PILL, 0, 0)));
        Ui.elevate(cta, 6, Ui.BURGUNDY_PRIMARY);
        Ui.pressEffect(cta);
        cta.addView(new IconView(context, IconView.HOME_OUTLINE, Ui.SURFACE_WHITE).strokeUnits(2f),
                new LinearLayout.LayoutParams(dp(23), dp(23)));
        TextView ctaText = rawText("Mua vàng ngay", 22f, Ui.SURFACE_WHITE, Ui.W_MEDIUM);
        LinearLayout.LayoutParams ctaTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ctaTextLp.setMarginStart(dp(10));
        cta.addView(ctaText, ctaTextLp);
        cta.setOnClickListener(v -> triggerExit());
        ctaRow.addView(cta, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(66), Gravity.CENTER));

        // ---- Thanh dau noi tren banner: logo trai + goi hotline/dang nhap phai ----
        headerRow = new FrameLayout(context);
        headerRow.setPadding(dp(34), dp(50), dp(34), 0);
        addView(headerRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        headerRow.addView(new LogoView(context, true).tint(Ui.BURGUNDY_PRIMARY),
                new FrameLayout.LayoutParams(dp(63), dp(55), Gravity.START | Gravity.TOP));

        LinearLayout buttonStack = new LinearLayout(context);
        buttonStack.setOrientation(LinearLayout.VERTICAL);

        LinearLayout phoneBtn = pillButton(context, Ui.rect(GLASS_WHITE, Ui.R_PILL, 0, 0));
        Ui.pressEffect(phoneBtn);
        phoneBtn.addView(new IconView(context, IconView.PHONE, PHONE_TEXT).strokeUnits(1.7f), new LinearLayout.LayoutParams(dp(20), dp(20)));
        phoneLabel = rawText("", 15f, PHONE_TEXT, Ui.W_BOLD);
        phoneLabel.setSingleLine(true);
        phoneLabel.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams phoneTextLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        phoneTextLp.setMarginStart(dp(8));
        phoneBtn.addView(phoneLabel, phoneTextLp);
        phoneBtn.setOnClickListener(v -> callHotline());
        buttonStack.addView(phoneBtn, new LinearLayout.LayoutParams(dp(145), dp(32)));

        LinearLayout loginBtn = pillButton(context, Ui.pressable(Ui.rect(LOGIN_RED, Ui.R_PILL, 0, 0), Ui.rect(Ui.BURGUNDY_DARK, Ui.R_PILL, 0, 0)));
        loginBtn.addView(new IconView(context, IconView.LOGIN, Ui.SURFACE_WHITE).strokeUnits(1.7f), new LinearLayout.LayoutParams(dp(20), dp(20)));
        loginLabel = rawText("Đăng nhập", 15f, LOGIN_TEXT, Ui.W_BOLD);
        loginLabel.setSingleLine(true);
        loginLabel.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams loginTextLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        loginTextLp.setMarginStart(dp(8));
        loginBtn.addView(loginLabel, loginTextLp);
        Ui.pressEffect(loginBtn);
        loginBtn.setOnClickListener(v -> callbacks.onLoginLogout(loggedIn));
        LinearLayout.LayoutParams loginLp = new LinearLayout.LayoutParams(dp(145), dp(32));
        loginLp.topMargin = dp(8);
        buttonStack.addView(loginBtn, loginLp);

        headerRow.addView(buttonStack, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP));

        // Vuot ngang doi muc / cham vao banner -> thoat ve Home (cac nut rieng o tren tu xu ly cham cua minh,
        // khong bi gesture nay nuot vi Android chi day touch len root khi khong co con nao tieu thu truoc).
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                triggerExit();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) > Ui.px(SWIPE_DISTANCE_PX_DESIGN) && Math.abs(dx) > Math.abs(e2.getY() - e1.getY())) {
                    if (dx < 0) next();
                    else previous();
                    return true;
                }
                return false;
            }
        });
        setOnTouchListener((v, ev) -> gestures.onTouchEvent(ev));
    }

    /** px thiet ke cua MOCKUP NAY (540) -> px that, doc lap voi thang do 640 chung cua Ui. */
    private int dp(float design540Px) {
        return Math.round(design540Px * scale540);
    }

    private TextView rawText(CharSequence text, float designPx, int color, int weight) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(designPx));
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Ui.typeface(weight));
        return t;
    }

    private LinearLayout pillButton(Context context, android.graphics.drawable.Drawable bg) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setBackground(bg);
        row.setClickable(true);
        return row;
    }

    /** Goi hotline (neu co) qua app quay so mac dinh; khong co app nao xu ly duoc thi bo qua am tham. */
    private void callHotline() {
        if (hotline == null || hotline.isEmpty()) return;
        try {
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + hotline));
            dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(dial);
        } catch (Exception ignored) {
            // May kiosk co the khong co app quay so nao -> khong lam gi them.
        }
    }

    private void triggerExit() {
        Item item = current();
        callbacks.onTap(item != null ? item.ad : null);
    }

    /** Cap nhat hotline + trang thai dang nhap tren thanh dau (goi lai moi lan vao man cho). */
    public void bind(String hotlineValue, String loggedInCustomerName) {
        hotline = hotlineValue != null ? hotlineValue : "";
        phoneLabel.setText(hotline);
        loggedIn = loggedInCustomerName != null;
        loginLabel.setText(loggedIn ? "Xin chào, " + loggedInCustomerName + " · Đăng xuất" : "Đăng nhập");
    }

    /** Bat dau phat: quang cao da tai (neu co) hoac anh mac dinh trong assets. */
    public void start(List<AdsManager.PlayableAd> ads, List<String> defaultUris) {
        items.clear();
        if (ads != null && !ads.isEmpty()) {
            for (AdsManager.PlayableAd p : ads) items.add(new Item(p.ad, p.file, null));
        } else if (defaultUris != null) {
            for (String uri : defaultUris) items.add(new Item(null, null, uri));
        }
        index = 0;
        active = true;
        buildDots();
        show();
    }

    public void stop() {
        active = false;
        handler.removeCallbacks(advance);
        video.stopPlayback();
        video.setVisibility(GONE);
    }

    /** Cap nhat danh sach phat khi playlist doi luc dang o man cho (chi thay khi khong dang phat muc nao dang do). */
    public boolean isActive() {
        return active;
    }

    /** Vung an toan phia tren (cutout/thanh trang thai -> doi them vao thanh dau) va phia duoi (thanh dieu huong -> hang CTA). */
    @Override
    public void onInsets(int l, int t, int r, int b) {
        headerRow.setPadding(dp(34), dp(50) + t, dp(34), 0);
        // ctaRow la con truc tiep cua root FrameLayout -> layout params cua no la FrameLayout.LayoutParams.
        FrameLayout.LayoutParams ctaLp = (FrameLayout.LayoutParams) ctaRow.getLayoutParams();
        ctaLp.bottomMargin = dp(53) + b;
        ctaRow.setLayoutParams(ctaLp);
    }

    private void buildDots() {
        dots.removeAllViews();
        if (items.size() < 2) return;
        for (int i = 0; i < items.size(); i++) {
            View dot = new View(getContext());
            dot.setBackground(Ui.oval(0)); // mau that dat trong highlightDot()
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(13), dp(13));
            lp.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(dot, lp);
        }
        highlightDot();
    }

    private void highlightDot() {
        for (int i = 0; i < dots.getChildCount(); i++) {
            View dot = dots.getChildAt(i);
            dot.setBackground(Ui.oval(i == index ? 0xFFEAEAEA : 0xFFBFBFBF));
        }
    }

    private Item current() {
        return items.isEmpty() ? null : items.get(Math.max(0, Math.min(index, items.size() - 1)));
    }

    private void next() {
        if (!active || items.isEmpty()) return;
        index = (index + 1) % items.size();
        show();
    }

    private void previous() {
        if (!active || items.isEmpty()) return;
        index = (index - 1 + items.size()) % items.size();
        show();
    }

    private void show() {
        handler.removeCallbacks(advance);
        Item item = current();
        if (item == null) return;

        highlightDot();

        if (item.ad != null && item.ad.isVideo()) {
            video.setVisibility(VISIBLE);
            image.setVisibility(GONE);
            video.playFile(item.file);
            // VIDEO: displaySeconds = 0 -> phat het; > 0 -> cat sau ngan ay giay. Bo an toan neu video khong bao hoan tat.
            long cutMs = item.ad.displaySeconds > 0 ? item.ad.displaySeconds * 1000L : VIDEO_SAFETY_MS;
            handler.postDelayed(advance, cutMs);
        } else {
            video.stopPlayback();
            video.setVisibility(GONE);
            image.setVisibility(VISIBLE);
            Object source = item.file != null ? item.file : item.uri;
            Glide.with(this).load(source).fitCenter().into(image);
            int seconds = item.ad != null ? item.ad.displaySeconds : 8; // anh mac dinh: 8s/muc
            if (seconds <= 0) seconds = DEFAULT_SLIDE_SECONDS;
            handler.postDelayed(advance, seconds * 1000L);
        }
    }
}
