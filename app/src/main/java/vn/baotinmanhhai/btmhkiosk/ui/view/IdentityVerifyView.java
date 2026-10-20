package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import vn.baotinmanhhai.btmhkiosk.BuildConfig;
import vn.baotinmanhhai.btmhkiosk.databinding.SheetVerifyBinding;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * "Xac minh chinh chu" ngay truoc khi DAT MUA (khac voi luong "Dang ky tai khoan" CCCD/VNeID danh cho khach moi chua co
 * tai khoan): theo mockup Figma, bam nut "XAC MINH CHINH CHU" o man Thong tin don hang se hien POPUP nay (ten hoa don
 * phai trung CCCD nguoi thanh toan theo quy dinh Nha nuoc), dong y roi moi sang man "Xac thuc khuon mat" (dung lai
 * {@link FaceScanView} da co). Phase 1: chua co camera/API that, chi la mo phong giao dien.
 */
public class IdentityVerifyView extends FrameLayout implements Ui.InsetAware {

    public enum State { POPUP, WAITING, VERIFYING, SUCCESS }

    public interface Listener {
        /** Dong popup / nut quay lai man quet: dong lop phu, KHONG danh dau da xac minh. */
        void onCancel();

        /** Xac minh thanh cong: dong lop phu, danh dau don hang da xac minh chinh chu. */
        void onVerified();

        /** Nut home o goc tren-phai: dong lop phu RA HAN man Home, giong het moi man khac co nut home
         * (OrderConfirmView/PaymentView/CustomerConfirmView) - truoc day bi gan nham vao onCancel() nen bam
         * vao chi dong lop nay, van dung lai o man ben duoi (Xac nhan don hang) thay vi ve Home that. */
        void onGoHome();
    }

    private static final long TICK_MS = 1800;

    private final Listener listener;
    private final FrameLayout popupOverlay;
    private final LinearLayout scanColumn;
    private vn.btmh.ui.customviews.DashedCircleView dashedCircle;
    private final TextView scanCaption;
    private final TextView scanButton;
    private final View successBadge;
    private final ValueAnimator ticker;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private State state = State.POPUP;

    public IdentityVerifyView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setClickable(true);

        // ================= MAN "XAC THUC KHUON MAT" (nam duoi, popup che len tren) =================
        setBackground(Ui.pageBackground());
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.px(6), Ui.px(16), Ui.px(16), Ui.px(8));
        FrameLayout back = new FrameLayout(context);
        back.addView(new IconView(context, IconView.ARROW_LEFT, Ui.TEXT_TITLE_DARK).strokeUnits(2.2f),
                new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        back.setClickable(true);
        back.setOnClickListener(v -> listener.onCancel());
        Ui.pressEffect(back);
        header.addView(back, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        TextView title = Ui.text(context, "Xác thực khuôn mặt", 14f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        // Dung dung 1 kieu icon home voi moi man khac (OrderConfirmView/PaymentView/CustomerConfirmView): icon
        // vien (HOME_OUTLINE) mau do thuong hieu, KHONG nen dac/khong shadow - truoc day man nay tu ve rieng
        // kieu icon dac nen tron + shadow, khac han cac man con lai (app that dung chung 1 HeaderFragment nen
        // icon home GIONG HET nhau o moi man, xem activity_face_auth.xml).
        FrameLayout homeBtn = new FrameLayout(context);
        homeBtn.addView(new IconView(context, IconView.HOME_OUTLINE, Ui.BURGUNDY_PRIMARY).strokeUnits(2.0f),
                new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        homeBtn.setClickable(true);
        homeBtn.setOnClickListener(v -> listener.onGoHome());
        Ui.pressEffect(homeBtn);
        header.addView(homeBtn, new LinearLayout.LayoutParams(Ui.px(66), Ui.px(66)));
        column.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        column.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        scanColumn = new LinearLayout(context);
        scanColumn.setOrientation(LinearLayout.VERTICAL);
        scanColumn.setPadding(Ui.px(16), Ui.px(8), Ui.px(16), Ui.px(24));
        scroll.addView(scanColumn, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        // activity_face_auth.xml that: KHONG co the trang trang boc ngoai - logo_with_text + vong tron net dut +
        // icon FaceID + huong dan + hang 2 nut, nam truc tiep tren nen gradient trang. Truoc day boc trong 1
        // "scanCard" trang bo goc + vong tron mau be tu ve (FaceScanView) - khong khop thiet ke that, thay het.
        LinearLayout scanCard = new LinearLayout(context);
        scanCard.setOrientation(LinearLayout.VERTICAL);
        scanCard.setGravity(Gravity.CENTER_HORIZONTAL);
        scanColumn.addView(scanCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // activity_face_auth.xml: ivLogo + btnRegister chi hien o luong FLOW_01 (dang nhap tu man cho/bang gia
        // vang). Man nay la luong "XAC MINH CHINH CHU" truoc DAT MUA (header co nut back + tieu de) nen theo dung
        // FaceAuthActivity.setupViews() nhanh else: ivLogo GONE, chi con 1 nut "BAT DAU QUET" day du chieu rong.

        // dashedCircleView that: 640dp - qua kho rong so voi man hinh thiet ke 540dp (se tran canh), giam con
        // 380dp de vua khung ma van la vong tron chu dao, to, noi bat dung tinh than thiet ke.
        FrameLayout faceFrame = new FrameLayout(context);
        vn.btmh.ui.customviews.DashedCircleView dashedCircle = new vn.btmh.ui.customviews.DashedCircleView(context);
        faceFrame.addView(dashedCircle, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        android.widget.ImageView faceIdIcon = new android.widget.ImageView(context);
        faceIdIcon.setImageResource(vn.baotinmanhhai.btmhkiosk.R.drawable.ic_face_id);
        faceFrame.addView(faceIdIcon, new FrameLayout.LayoutParams(Ui.px(114), Ui.px(114), Gravity.CENTER));
        this.dashedCircle = dashedCircle;
        successBadge = buildSuccessBadge(context);
        successBadge.setVisibility(GONE);
        faceFrame.addView(successBadge, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams faceFrameLp = new LinearLayout.LayoutParams(Ui.px(380), Ui.px(380));
        faceFrameLp.topMargin = Ui.px(32);
        scanCard.addView(faceFrame, faceFrameLp);

        scanCaption = Ui.text(context, "", 24f, Color.BLACK, Ui.W_REGULAR);
        scanCaption.setGravity(Gravity.CENTER);
        scanCaption.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = Ui.px(32);
        scanCard.addView(scanCaption, capLp);

        scanButton = Ui.primaryButton(context, "BẮT ĐẦU QUÉT", 24f);
        scanButton.setOnClickListener(v -> onScanTap());
        LinearLayout.LayoutParams scanLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(72));
        scanLp.topMargin = Ui.px(42);
        scanCard.addView(scanButton, scanLp);

        // ================= POPUP "XAC MINH CHINH CHU DE TIEP TUC" (che tren cung) =================
        popupOverlay = new FrameLayout(context);
        popupOverlay.setBackgroundColor(Ui.withAlpha(0xFF000000, 0x66));
        popupOverlay.setClickable(true);
        addView(popupOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        buildPopup(context);

        ticker = ValueAnimator.ofFloat(0f, 1f);
        ticker.setDuration(TICK_MS);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(a -> dashedCircle.setRotationAngle((float) a.getAnimatedValue() * 360f));

        applyState();
    }

    /** Bottom sheet "Xac minh chinh chu de tiep tuc" — inflate dung sheet_verify.xml that qua ViewBinding. */
    private void buildPopup(Context context) {
        SheetVerifyBinding sheet = SheetVerifyBinding.inflate(LayoutInflater.from(context), popupOverlay, false);
        sheet.btnClose.setOnClickListener(v -> listener.onCancel());
        sheet.tvBody1.setText(buildBody1());
        sheet.tvBody3.setText(buildBody3());
        sheet.btnAccept.setOnClickListener(v -> setState(State.WAITING));
        popupOverlay.addView(sheet.getRoot(), new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    /** "Theo <b><i><u>quy định Nhà nước</u></i></b>, thông tin thanh toán..." (verify_body_1). */
    private static CharSequence buildBody1() {
        String prefix = "Theo ";
        String bolded = "quy định Nhà nước";
        String suffix = ", thông tin thanh toán và hóa đơn cần trùng khớp với Căn cước công dân của Quý khách.";
        android.text.SpannableString sp = new android.text.SpannableString(prefix + bolded + suffix);
        int s = prefix.length(), e = s + bolded.length();
        sp.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC), s, e, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new android.text.style.UnderlineSpan(), s, e, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    /** "Đồng ý chia sẻ dữ liệu cá nhân theo <u>nghị định 13 về bảo vệ dữ liệu cá nhân</u>" (verify_body_3). */
    private static CharSequence buildBody3() {
        String prefix = "Đồng ý chia sẻ dữ liệu cá nhân theo ";
        String underlined = "nghị định 13 về bảo vệ dữ liệu cá nhân";
        android.text.SpannableString sp = new android.text.SpannableString(prefix + underlined);
        sp.setSpan(new android.text.style.UnderlineSpan(), prefix.length(), prefix.length() + underlined.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    private static View buildSuccessBadge(Context context) {
        FrameLayout badge = new FrameLayout(context);
        badge.setBackground(Ui.oval(Ui.withAlpha(Ui.UP_GREEN, 0x22)));
        TextView check = Ui.text(context, "✓", 32f, Ui.UP_GREEN, Ui.W_BOLD);
        check.setGravity(Gravity.CENTER);
        badge.addView(check, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        return badge;
    }

    private void onScanTap() {
        if (state == State.WAITING) {
            setState(State.VERIFYING);
            handler.postDelayed(() -> {
                if (state == State.VERIFYING) setState(State.SUCCESS);
            }, 1700);
            handler.postDelayed(() -> {
                if (state == State.SUCCESS) listener.onVerified();
            }, 2600);
        } else if (!BuildConfig.DEBUG) {
            // release: chi cho bam lai khi dang cho (chua co camera that de bam "quet lai")
        }
    }

    /** Dua ve trang thai ban dau (goi moi lan mo tu man Thong tin don hang). */
    public void reset() {
        handler.removeCallbacksAndMessages(null);
        setState(State.POPUP);
    }

    public void setState(State newState) {
        state = newState;
        applyState();
    }

    private void applyState() {
        popupOverlay.setVisibility(state == State.POPUP ? VISIBLE : GONE);
        boolean verifying = state == State.VERIFYING;
        boolean success = state == State.SUCCESS;
        dashedCircle.setSpinningMode(verifying);
        dashedCircle.setSuccess(success);
        successBadge.setVisibility(success ? VISIBLE : GONE);
        dashedCircle.setVisibility(success ? GONE : VISIBLE);
        if (verifying) dashedCircle.setRotationAngle(0f);
        switch (state) {
            case WAITING:
                // real activity_face_auth.xml: huong dan tinh, khong doi theo trang thai.
                scanCaption.setText("Vui lòng nhìn thẳng và giữ khuôn mặt vào giữa khung hình hoặc đăng ký tài khoản nếu là khách hàng mới.");
                scanCaption.setTextColor(Color.BLACK);
                scanButton.setText("BẮT ĐẦU QUÉT");
                scanButton.setVisibility(VISIBLE);
                break;
            case VERIFYING:
                scanCaption.setText("Đang xác minh khuôn mặt…");
                scanCaption.setTextColor(Ui.UP_GREEN);
                scanButton.setVisibility(GONE);
                break;
            case SUCCESS:
                scanCaption.setText("Nhận diện thành công");
                scanCaption.setTextColor(Ui.UP_GREEN);
                scanButton.setVisibility(GONE);
                break;
            default:
                break;
        }
        updateAnimation();
    }

    private void updateAnimation() {
        boolean run = (state == State.VERIFYING) && isAttachedToWindow() && isShown();
        if (run && !ticker.isStarted()) ticker.start();
        else if (!run && ticker.isStarted()) ticker.cancel();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        ticker.cancel();
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimation();
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        setPadding(left, top, right, bottom);
    }

}
