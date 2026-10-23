package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;

import java.util.Locale;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.databinding.ActivityPaymentDetailBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.ActivityPaymentResultBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.ItemPaymentRowBinding;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man "Thanh toan" (QR chuyen khoan + ket qua giao dich). Doi tu ban ve tay Canvas sang inflate dung
 * activity_payment_detail.xml / activity_payment_result.xml that qua ViewBinding de khop pixel voi app chuan.
 * Header tu ve rieng (giong OrderConfirmView) vi binding.header (FragmentContainerView/HeaderFragment that)
 * khong ton tai trong du an nay - an di, dung topBar hand-drawn thay the.
 *
 * Giu nguyen logic nghiep vu da co (khong co trong XML, do Phase 1 chua co cong thanh toan/API that):
 * - Lay QR chuyen khoan qua VietQR (img.vietqr.io) thay vi giai ma qrBase64 tu backend that.
 * - Vong polling mo phong "cho den khi nhan duoc thanh toan" (pollTransferStatus/MAX_CHECK_ATTEMPTS).
 * - Man ket qua "thanh cong" dung lai QR bien lai (api.qrserver.com) thay cho QR giay hen that (can API MinIO
 *   chua co). Khoi "Cham de danh gia" (5 sao, Figma) chi luu diem tai may - CHUA co API nhan danh gia.
 * - Man that/that-bai/chua-xac-dinh hien QR Zalo OA neu co link cau hinh; du an nay chua co du lieu Zalo OA nen
 *   an han khoi QR o 2 trang thai nay (dung "an neu khong co", khong bia du lieu).
 */
public class PaymentView extends FrameLayout implements Ui.InsetAware {

    public enum State { TRANSFER, RESULT_SUCCESS, RESULT_UNDETERMINED, RESULT_FAILED }

    public interface Listener {
        void onCancel();
        void onFinished();
        void onGoHome();
        /** Het thoi gian o man ket qua ma khach khong thao tac: ket thuc phien (xoa gio, ve man cho). */
        void onSessionExpired();
    }

    private static final long COUNTDOWN_MS = 5 * 60 * 1000L;
    private static final int MAX_CHECK_ATTEMPTS = 3; // mo phong "cho den khi nhan duoc thanh cong"

    private final Listener listener;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    private final LinearLayout header;
    private final FrameLayout headerBackBtn;
    private final TextView headerTitle;
    private final FrameLayout homeBtn;

    private final ActivityPaymentDetailBinding detail;
    private final ActivityPaymentResultBinding result;

    private final int colorGreen;
    private final int colorRed;
    private final int colorOrange;
    private final int colorBrandRed;

    private State state = State.TRANSFER;
    private long remainingMs = COUNTDOWN_MS;
    private int checkAttempts = 0;
    private static final int STAR_ON = 0xFFFFCC00;
    private static final int STAR_OFF = 0xFFF2F2F2;
    private android.widget.ImageView[] stars;
    private int rating = 0;

    private String customerStr = "";
    private String codeStr = "";
    private String valueStr = "";
    private String locationStr = "";
    private String hotline = "1800.2209";

    public PaymentView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackground(Ui.pageBackground());
        setClickable(true);

        colorGreen = ContextCompat.getColor(context, R.color.payment_green);
        colorRed = ContextCompat.getColor(context, R.color.payment_red);
        colorOrange = ContextCompat.getColor(context, R.color.payment_orange);
        colorBrandRed = ContextCompat.getColor(context, R.color.brand_red);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ================= HEADER (tu ve, cung kieu voi OrderConfirmView) =================
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.px(20), Ui.px(56), Ui.px(32), Ui.px(8)); // Home: cach tren 56, phai 32 (Figma/fragment_header)

        headerBackBtn = new FrameLayout(context);
        headerBackBtn.setClickable(true);
        Ui.pressEffect(headerBackBtn);
        headerBackBtn.setOnClickListener(v -> cancelTransfer());
        IconView backIcon = new IconView(context, IconView.CHEVRON_LEFT, Color.BLACK).strokeUnits(2.2f);
        headerBackBtn.addView(backIcon, new FrameLayout.LayoutParams(Ui.px(24), Ui.px(24), Gravity.CENTER));
        header.addView(headerBackBtn, new LinearLayout.LayoutParams(Ui.px(48), Ui.px(48)));

        headerTitle = Ui.text(context, "Thanh toán đơn hàng", 24f, Color.BLACK, Ui.W_MEDIUM);
        headerTitle.setGravity(Gravity.CENTER_VERTICAL);
        headerTitle.setIncludeFontPadding(false);
        headerTitle.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, Ui.px(48), 1f);
        titleLp.setMarginStart(Ui.px(8));
        header.addView(headerTitle, titleLp);

        homeBtn = new FrameLayout(context);
        homeBtn.setClickable(true);
        Ui.pressEffect(homeBtn);
        homeBtn.setOnClickListener(v -> listener.onGoHome());
        android.view.View homeIcon = Ui.homeIcon(context);
        homeBtn.addView(homeIcon, new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        header.addView(homeBtn, new LinearLayout.LayoutParams(Ui.px(66), Ui.px(66)));

        column.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ================= NOI DUNG: 2 layout that, chi 1 cai hien tai 1 thoi diem =================
        // Nen chi ve MOT lan o PaymentView (Ui.pageBackground, phu ca header). 2 layout that tu co
        // @drawable/bg rieng bat dau tu duoi header -> tao duong noi lech nen voi topbar, nen bo di.
        detail = ActivityPaymentDetailBinding.inflate(LayoutInflater.from(context), null, false);
        detail.header.setVisibility(GONE);
        detail.getRoot().setBackground(null);
        column.addView(detail.getRoot(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        result = ActivityPaymentResultBinding.inflate(LayoutInflater.from(context), null, false);
        result.header.setVisibility(GONE);
        result.getRoot().setBackground(null);
        column.addView(result.getRoot(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        stars = new android.widget.ImageView[] {result.star1, result.star2, result.star3, result.star4, result.star5};
        for (int i = 0; i < stars.length; i++) {
            final int value = i + 1;
            stars[i].setContentDescription(context.getString(R.string.result_rate_star, value));
            // Cham sao -> popup danh gia (Figma), san so sao vua cham; chi to sao tren man khi khach bam GUI.
            stars[i].setOnClickListener(v -> OrderPopups.showRating(getContext(), value, (submitted, comment) -> {
                setRating(submitted);
                KioskToast.success(getContext(), R.string.rating_thanks);
            }));
            Ui.pressEffect(stars[i]);
        }
        setRating(0);

        detail.tvNote.setText(HtmlCompat.fromHtml(context.getString(R.string.payment_note), HtmlCompat.FROM_HTML_MODE_LEGACY));
        label(detail.rowCustomer, "Khách hàng");
        label(detail.rowOrderCode, "Mã đơn hàng");
        label(detail.rowAmount, "Giá trị đơn hàng");
        detail.rowAmount.tvValue.setTextColor(colorBrandRed);
        label(detail.rowStore, "Địa điểm nhận hàng");

        detail.btnCancel.setOnClickListener(v -> cancelTransfer());
        detail.btnClaim.setOnClickListener(v -> startChecking());
        detail.overlay.setOnClickListener(v -> { }); // that: cham vung toi khi dang CHECKING se dismiss; giu im lang, khong doi hanh vi polling

        result.btnContinue.setOnClickListener(v -> listener.onFinished());

        applyState();
    }

    private static void label(ItemPaymentRowBinding row, String text) {
        row.tvLabel.setText(text);
    }

    private void setRating(int value) {
        rating = value;
        for (int i = 0; i < stars.length; i++) {
            stars[i].setImageTintList(android.content.res.ColorStateList.valueOf(i < value ? STAR_ON : STAR_OFF));
        }
    }

    // ==================== BIND DATA ====================

    public void bind(String customer, String code, String value, String location, String hotline) {
        customerStr = customer;
        codeStr = code;
        valueStr = value;
        locationStr = location;
        this.hotline = hotline != null && !hotline.isEmpty() ? hotline : "1800.2209";
        remainingMs = COUNTDOWN_MS;
        checkAttempts = 0;
        state = State.TRANSFER;
        detail.btnCancel.setEnabled(true);
        detail.btnClaim.setEnabled(true);
        detail.btnClaim.setBackgroundResource(R.drawable.bg_pill_red);
        detail.overlay.setVisibility(GONE);
        detail.rowCustomer.tvValue.setText(customerStr);
        detail.rowOrderCode.tvValue.setText(codeStr);
        detail.rowAmount.tvValue.setText(valueStr);
        detail.rowStore.tvValue.setText(locationStr);
        loadTransferQr(code, value);
        applyState();
        handler.removeCallbacks(countdownRunnable);
        handler.post(countdownRunnable);
    }

    private void loadTransferQr(String orderCode, String formattedValue) {
        String amount = formattedValue == null ? "" : formattedValue.replaceAll("[^0-9]", "");
        String addInfo = orderCode == null ? "" : orderCode;
        String url = "https://img.vietqr.io/image/MB-0865372338-qr_only.png"
                + "?amount=" + amount
                + "&addInfo=" + android.net.Uri.encode(addInfo);
        // Truoc day an "Dang tao ma..." (creatingQr) NGAY LAP TUC, khong doi Glide tai xong - nguoi dung co the
        // thay vung QR trong trong vai giay neu mang cham. Gio giu spinner hien cho den khi anh ve xong that
        // (onResourceReady) hoac loi han (onLoadFailed), roi moi hien logo Bao Tin Manh Hai giua ma.
        detail.creatingQr.setVisibility(VISIBLE);
        detail.qrLogo.setVisibility(GONE);
        Glide.with(detail.ivQr).load(url).listener(new RequestListener<android.graphics.drawable.Drawable>() {
            @Override
            public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e,
                                         Object model, Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                detail.creatingQr.setVisibility(GONE);
                return false;
            }

            @Override
            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                            Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                detail.creatingQr.setVisibility(GONE);
                // Anh VietQR tu img.vietqr.io luon tu nhung mot logo nho (chu "V") giua ma, nghia la dich vu nay da tao
                // o muc sua loi du cao de chiu duoc logo giua - the cho bang logo Bao Tin Manh Hai (qrLogo, @drawable/logo,
                // chi ~4% dien tich ma) van nam trong nguong do, giong dung "logo giua QR" cua app that.
                detail.qrLogo.setVisibility(VISIBLE);
                return false;
            }
        }).into(detail.ivQr);
    }

    private void loadResultReceiptQr(String orderCode) {
        String content = "https://btmh.vn/receipt/" + (orderCode != null ? orderCode : "DH");
        String url = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=" + android.net.Uri.encode(content);
        // qrProgress co san trong layout nhung truoc day khong duoc dung toi (always GONE) - QR bien lai bi
        // "nhay" ra dot ngot khi Glide tai xong, khong co trang thai dang tai nao ca. Gio hien spinner nay
        // trong luc tai, giong dung cach da sua cho QR chuyen khoan o loadTransferQr().
        result.qrProgress.setVisibility(VISIBLE);
        Glide.with(result.ivQr).load(url).listener(new RequestListener<android.graphics.drawable.Drawable>() {
            @Override
            public boolean onLoadFailed(GlideException e, Object model,
                                         Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                result.qrProgress.setVisibility(GONE);
                return false;
            }

            @Override
            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                            Target<android.graphics.drawable.Drawable> target, DataSource dataSource, boolean isFirstResource) {
                result.qrProgress.setVisibility(GONE);
                return false;
            }
        }).into(result.ivQr);
    }

    private void cancelTransfer() {
        detail.btnCancel.setEnabled(false);
        detail.btnClaim.setEnabled(false);
        handler.removeCallbacks(countdownRunnable);
        handler.postDelayed(listener::onCancel, 300);
    }

    public State getState() {
        return state;
    }

    private final Runnable countdownRunnable = this::tickCountdown;

    private void tickCountdown() {
        if (state != State.TRANSFER || !isShown()) {
            handler.postDelayed(countdownRunnable, 1000);
            return;
        }
        remainingMs -= 1000;
        if (remainingMs <= 0) {
            remainingMs = 0;
            detail.tvCountdown.setText("Đã hết hạn thanh toán — vui lòng huỷ và đặt lại");
            showOverlay(Overlay.FAILED);
            return;
        }
        long s = remainingMs / 1000;
        detail.tvCountdown.setText(String.format(Locale.ROOT, "Hết hạn sau %02d:%02d", s / 60, s % 60));
        handler.postDelayed(countdownRunnable, 1000);
    }

    /**
     * Bam "TOI DA CHUYEN KHOAN": kiem tra xem ngan hang DA thuc su nhan duoc tien chua, chua co thi hien lai
     * trang thai "chua nhan duoc, dang cho tiep" roi tu dong kiem tra lai - lap lai cho den khi nhan duoc
     * thanh cong (hoac het han/huy). Phase 1 chua co cong thanh toan that nen mo phong vai lan "chua co" roi
     * moi "thanh cong", de giao dien dung hanh vi cho-va-kiem-tra-lai thay vi doan ngay mot lan la xong.
     */
    private void startChecking() {
        if (remainingMs <= 0 || state != State.TRANSFER) return;
        checkAttempts = 0;
        detail.btnClaim.setEnabled(false);
        pollTransferStatus();
    }

    private void pollTransferStatus() {
        if (state != State.TRANSFER) return;
        showOverlay(Overlay.CHECKING);
        handler.postDelayed(() -> {
            if (state != State.TRANSFER) return;
            checkAttempts++;
            boolean received = checkAttempts >= MAX_CHECK_ATTEMPTS; // mo phong: nhung lan truoc "chua co", lan cuoi "da nhan"
            if (received) {
                showOverlay(Overlay.SUCCESS);
                handler.postDelayed(() -> setResultState(State.RESULT_SUCCESS), 900);
            } else if (remainingMs <= 0) {
                showOverlay(Overlay.FAILED);
                detail.btnClaim.setEnabled(true);
            } else {
                pollTransferStatus(); // chua nhan duoc -> doi roi kiem tra lai, KHONG dung lai
            }
        }, 1800);
    }

    private enum Overlay { NONE, CHECKING, SUCCESS, FAILED, UNKNOWN }

    /** activity_payment_detail.xml that: overlay toan man + PaymentRingView + icon + chu, thay vi vong ve rieng trong khung QR. */
    private void showOverlay(Overlay overlay) {
        if (overlay == Overlay.NONE) {
            detail.overlay.setVisibility(GONE);
            return;
        }
        detail.overlay.setVisibility(VISIBLE);
        int color;
        int textRes;
        Integer iconRes;
        boolean spin;
        switch (overlay) {
            case SUCCESS:
                color = colorGreen; textRes = R.string.payment_success; iconRes = R.drawable.ic_payment_success; spin = false;
                break;
            case FAILED:
                color = colorRed; textRes = R.string.payment_failed; iconRes = R.drawable.ic_payment_failed; spin = false;
                break;
            case UNKNOWN:
                color = colorOrange; textRes = R.string.payment_unknown; iconRes = null; spin = false;
                break;
            case CHECKING:
            default:
                color = colorGreen; textRes = R.string.payment_checking; iconRes = null; spin = true;
                break;
        }
        detail.ring.show(color, spin);
        detail.tvOverlay.setText(textRes);
        detail.tvOverlay.setTextColor(color);
        if (iconRes != null) {
            detail.ivOverlayIcon.setVisibility(VISIBLE);
            detail.ivOverlayIcon.setImageResource(iconRes);
            detail.ivOverlayIcon.setColorFilter(color);
        } else {
            detail.ivOverlayIcon.setVisibility(GONE);
        }
    }

    public void setResultState(State resultState) {
        state = resultState;
        applyState();
    }

    private void applyState() {
        boolean transfer = state == State.TRANSFER;
        detail.getRoot().setVisibility(transfer ? VISIBLE : GONE);
        result.getRoot().setVisibility(transfer ? GONE : VISIBLE);

        // Man "Ket qua giao dich" an nut back + tieu de can giua, dung mockup that.
        headerBackBtn.setVisibility(transfer ? VISIBLE : GONE);
        headerTitle.setText(transfer ? "Thanh toán đơn hàng" : "Kết quả giao dịch");

        if (transfer) {
            showOverlay(Overlay.NONE);
            return;
        }

        boolean success = state == State.RESULT_SUCCESS;
        result.tvTime.setVisibility(success ? VISIBLE : GONE);
        if (success) {
            result.tvTime.setText(new java.text.SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.ROOT).format(new java.util.Date()));
        }
        result.tvHotline.setText(hotline);
        result.hotline.setVisibility(hotline != null && !hotline.isEmpty() ? VISIBLE : GONE);
        startAutoEndCountdown();

        result.rating.setVisibility(success ? VISIBLE : GONE);
        setRating(0);

        if (success) {
            result.tvStatus.setText(R.string.result_success);
            result.tvMessage.setVisibility(GONE);
            result.tvQrLogo.setVisibility(GONE);
            result.tvCaption.setVisibility(VISIBLE);
            result.tvCaption.setText(R.string.result_scan_appointment);
            result.info.setVisibility(VISIBLE);
            result.rowCustomer.tvLabel.setText("Khách hàng");
            result.rowCustomer.tvValue.setText(customerStr);
            result.rowOrderCode.tvLabel.setText("Mã đơn hàng");
            result.rowOrderCode.tvValue.setText(codeStr);
            result.rowAmount.tvLabel.setText("Giá trị đơn hàng");
            result.rowAmount.tvValue.setText(valueStr);
            result.rowAmount.tvValue.setTextColor(colorBrandRed);
            result.rowStore.tvLabel.setText("Địa điểm nhận hàng");
            result.rowStore.tvValue.setText(locationStr);
            result.qrBox.setVisibility(VISIBLE);
            android.view.ViewGroup.LayoutParams lp = result.qrBox.getLayoutParams();
            // Figma: anh QR 198 + vien trang (padding 8) -> 214.
            lp.width = Ui.px(214);
            lp.height = Ui.px(214);
            result.qrBox.setLayoutParams(lp);
            loadResultReceiptQr(codeStr);
        } else {
            boolean undetermined = state == State.RESULT_UNDETERMINED;
            result.tvStatus.setText(undetermined ? R.string.result_unknown : R.string.result_failed);
            result.info.setVisibility(GONE);
            // Chua co du lieu link Zalo OA that trong du an nay -> an khoi QR (dung "an neu khong co", khong bia).
            result.tvCaption.setVisibility(GONE);
            result.qrBox.setVisibility(GONE);
            result.tvMessage.setVisibility(VISIBLE);
            boolean hasHotline = hotline != null && !hotline.isEmpty();
            int msgRes = undetermined
                    ? (hasHotline ? R.string.result_unknown_message : R.string.result_unknown_message_no_hotline)
                    : (hasHotline ? R.string.result_failed_message : R.string.result_failed_message_no_hotline);
            result.tvMessage.setText(hasHotline ? context().getString(msgRes, hotline) : context().getString(msgRes));
        }
    }

    private Context context() {
        return getContext();
    }

    // ==================== TU DONG KET THUC PHIEN O MAN KET QUA ====================
    // "Phien giao dich se tu dong ket thuc sau N giay neu quy khach khong tiep tuc": dem nguoc THAT (truoc day so
    // 120 chi la chu tinh, man ket qua khong bao gio tu dong - idleCheck cua KioskActivity bo qua man thanh toan).
    // Khach cham man hinh = con dang dung -> dem lai tu dau; het gio -> listener.onSessionExpired().

    private static final int AUTO_END_SECONDS = 120;
    private int autoEndRemaining = AUTO_END_SECONDS;
    private final Runnable autoEndTick = this::tickAutoEnd;

    private boolean isResultState() {
        return state == State.RESULT_SUCCESS || state == State.RESULT_UNDETERMINED || state == State.RESULT_FAILED;
    }

    private void startAutoEndCountdown() {
        autoEndRemaining = AUTO_END_SECONDS;
        renderAutoEnd();
        handler.removeCallbacks(autoEndTick);
        handler.postDelayed(autoEndTick, 1000);
    }

    private void tickAutoEnd() {
        if (!isResultState() || getVisibility() != VISIBLE) return; // roi man ket qua: dung dem
        autoEndRemaining--;
        if (autoEndRemaining <= 0) {
            listener.onSessionExpired();
            return;
        }
        renderAutoEnd();
        handler.postDelayed(autoEndTick, 1000);
    }

    private void renderAutoEnd() {
        result.tvAutoEnd.setText(context().getString(R.string.result_auto_end, autoEndRemaining));
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && isResultState() && getVisibility() == VISIBLE) {
            autoEndRemaining = AUTO_END_SECONDS;
            renderAutoEnd();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        setPadding(left, 0, right, 0);
        header.setPadding(Ui.px(20), Ui.px(56) + top, Ui.px(32), Ui.px(8));
    }
}
