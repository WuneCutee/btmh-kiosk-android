package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.databinding.ActivityStandbyBinding;
import vn.baotinmanhhai.btmhkiosk.network.AdsManager;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man cho quang cao (spec II.2.2) - DUNG THANG layout that activity_standby.xml qua ViewBinding
 * (khong con tu ve bang Ui.px()/Canvas nhu truoc) de dam bao khop pixel 100% voi app chuan.
 *
 * <p>Gian luoc co chu dich so voi StandbyActivity.kt that: that dung ViewPager2 + 1 ExoPlayer dung
 * chung chuyen qua tung trang (FragmentStateAdapter). O day giu lai co che don gian da co san cua
 * BtmhKioskAndroid (1 ImageView/CoverVideoView, tu chuyen muc bang Handler + GestureDetector vuot
 * ngang) vi KET QUA HINH ANH tai moi thoi diem la NHU NHAU (1 anh/video phu kin man), chi khac co
 * che noi bo; ViewPager2 trong layout that bi an (GONE), thay bang 1 FrameLayout rieng cung vi tri.
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
    private static final int SWIPE_DISTANCE_DP = 120;

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
    private final ActivityStandbyBinding binding;
    private final ImageView image;
    private final CoverVideoView video;
    private final GestureDetector gestures;
    private final Callbacks callbacks;
    private final float density;

    private final List<Item> items = new ArrayList<>();
    private int index = 0;
    private boolean active = false;
    private boolean loggedIn = false;
    private String hotline = "";

    private final Runnable advance = this::next;

    public IdlePlayerView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        this.density = context.getResources().getDisplayMetrics().density;
        binding = ActivityStandbyBinding.inflate(LayoutInflater.from(context), this, true);

        // ViewPager2 that thay bang 1 FrameLayout anh/video don gian, cung vi tri full-bleed (xem javadoc lop).
        binding.pagerAds.setVisibility(GONE);
        FrameLayout banner = new FrameLayout(context);
        ConstraintLayout.LayoutParams bannerLp = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT);
        bannerLp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        bannerLp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        bannerLp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        bannerLp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        ((ConstraintLayout) binding.getRoot()).addView(banner, 0, bannerLp);

        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.addView(image, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

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
                    next();
                });
            }
        });
        banner.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        binding.btnBuyNow.setOnClickListener(v -> triggerExit());
        binding.chipLogin.setOnClickListener(v -> callbacks.onLoginLogout(loggedIn));
        binding.chipHotline.setOnClickListener(v -> callHotline());

        // Vuot ngang doi muc / cham vao banner -> thoat ve Home (nut rieng o tren tu xu ly cham cua
        // minh, khong bi gesture nay nuot vi Android chi day touch len root khi khong co con nao
        // tieu thu truoc).
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
                float swipePx = SWIPE_DISTANCE_DP * density;
                if (Math.abs(dx) > swipePx && Math.abs(dx) > Math.abs(e2.getY() - e1.getY())) {
                    if (dx < 0) next();
                    else previous();
                    return true;
                }
                return false;
            }
        });
        banner.setOnTouchListener((v, ev) -> gestures.onTouchEvent(ev));
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

    /**
     * Cap nhat hotline + trang thai dang nhap tren thanh dau (goi lai moi lan vao man cho).
     * Nhan "Đăng Nhập" trong layout that la tinh (khong doi theo trang thai dang nhap tren man cho
     * nay) - giu nguyen chu that, chi doi hanh vi onLoginLogout(loggedIn) de KioskActivity tu quyet
     * dinh dang nhap/dang xuat.
     */
    public void bind(String hotlineValue, String loggedInCustomerName) {
        hotline = hotlineValue != null ? hotlineValue : "";
        binding.tvHotline.setText(hotline);
        binding.chipHotline.setVisibility(hotline.isEmpty() ? GONE : VISIBLE);
        loggedIn = loggedInCustomerName != null;
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

    public boolean isActive() {
        return active;
    }

    /**
     * Vung an toan phia tren (cutout/thanh trang thai) va phia duoi (thanh dieu huong).
     * QUAN TRONG: header co layout_height CO DINH (standby_header_height=72dp) - cong inset vao
     * PADDING noi bo se bop meo 2 pill con (da gap loi nay: chipLogin con ~16px cao thay vi 64px).
     * Phai cong inset vao MARGIN-TOP cua header (ben ngoai vung co dinh) thay vi padding ben trong.
     */
    @Override
    public void onInsets(int l, int t, int r, int b) {
        ConstraintLayout.LayoutParams headerLp = (ConstraintLayout.LayoutParams) binding.header.getLayoutParams();
        headerLp.topMargin = Math.round(getResources().getDimension(R.dimen.standby_top_margin)) + t;
        binding.header.setLayoutParams(headerLp);
        ConstraintLayout.LayoutParams btnLp = (ConstraintLayout.LayoutParams) binding.btnBuyNow.getLayoutParams();
        btnLp.bottomMargin = Math.round(getResources().getDimension(R.dimen.standby_buy_bottom)) + b;
        binding.btnBuyNow.setLayoutParams(btnLp);
    }

    private void buildDots() {
        binding.dots.removeAllViews();
        if (items.size() < 2) {
            binding.dots.setVisibility(GONE);
            return;
        }
        binding.dots.setVisibility(VISIBLE);
        int size = getResources().getDimensionPixelSize(R.dimen.standby_dot_size);
        int gap = getResources().getDimensionPixelSize(R.dimen.standby_dot_gap);
        for (int i = 0; i < items.size(); i++) {
            View dot = new View(getContext());
            dot.setBackgroundResource(R.drawable.bg_standby_dot);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(size, size);
            if (i > 0) lp.setMarginStart(gap);
            binding.dots.addView(dot, lp);
        }
        highlightDot();
    }

    private void highlightDot() {
        for (int i = 0; i < binding.dots.getChildCount(); i++) {
            View dot = binding.dots.getChildAt(i);
            int color = i == index ? R.color.standby_dot_active : R.color.standby_dot_inactive;
            dot.setBackgroundTintList(ContextCompat.getColorStateList(getContext(), color));
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
            long cutMs = item.ad.displaySeconds > 0 ? item.ad.displaySeconds * 1000L : VIDEO_SAFETY_MS;
            handler.postDelayed(advance, cutMs);
        } else {
            video.stopPlayback();
            video.setVisibility(GONE);
            image.setVisibility(VISIBLE);
            Object source = item.file != null ? item.file : item.uri;
            Glide.with(this).load(source).fitCenter().into(image);
            int seconds = item.ad != null ? item.ad.displaySeconds : 8;
            if (seconds <= 0) seconds = DEFAULT_SLIDE_SECONDS;
            handler.postDelayed(advance, seconds * 1000L);
        }
    }
}
