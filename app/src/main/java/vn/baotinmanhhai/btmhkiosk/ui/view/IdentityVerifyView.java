package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import vn.baotinmanhhai.btmhkiosk.BuildConfig;
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
    }

    private static final long TICK_MS = 1800;

    private final Listener listener;
    private final FrameLayout popupOverlay;
    private final LinearLayout scanColumn;
    private final FaceScanView face;
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
        FrameLayout homeBtn = new FrameLayout(context);
        homeBtn.setBackground(Ui.oval(Ui.BURGUNDY_PRIMARY));
        homeBtn.addView(new IconView(context, IconView.HOME, Ui.SURFACE_WHITE).strokeUnits(1.8f),
                new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        homeBtn.setClickable(true);
        homeBtn.setOnClickListener(v -> listener.onCancel());
        Ui.pressEffect(homeBtn);
        Ui.softShadow(homeBtn);
        header.addView(homeBtn, new LinearLayout.LayoutParams(Ui.px(48), Ui.px(48)));
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

        LinearLayout scanCard = new LinearLayout(context);
        scanCard.setOrientation(LinearLayout.VERTICAL);
        scanCard.setGravity(Gravity.CENTER_HORIZONTAL);
        scanCard.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(scanCard);
        scanCard.setPadding(Ui.px(20), Ui.px(28), Ui.px(20), Ui.px(24));
        scanColumn.addView(scanCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        FrameLayout faceFrame = new FrameLayout(context);
        face = new FaceScanView(context);
        faceFrame.addView(face, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        successBadge = buildSuccessBadge(context);
        successBadge.setVisibility(GONE);
        faceFrame.addView(successBadge, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        scanCard.addView(faceFrame, new LinearLayout.LayoutParams(Ui.px(240), Ui.px(240)));

        scanCaption = Ui.text(context, "", 10f, Ui.TEXT_BODY_DARK, Ui.W_MEDIUM);
        scanCaption.setGravity(Gravity.CENTER);
        scanCaption.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        capLp.topMargin = Ui.px(20);
        scanCard.addView(scanCaption, capLp);

        scanButton = Ui.primaryButton(context, "BẮT ĐẦU QUÉT", 11f);
        scanButton.setOnClickListener(v -> onScanTap());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(68));
        btnLp.topMargin = Ui.px(24);
        scanCard.addView(scanButton, btnLp);

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
        ticker.addUpdateListener(a -> face.setPhase((float) a.getAnimatedValue()));

        applyState();
    }

    private void buildPopup(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(card);
        card.setPadding(Ui.px(22), Ui.px(20), Ui.px(22), Ui.px(22));

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(context, "Xác minh chính chủ để tiếp tục", 11.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        title.setLineSpacing(0, 1.15f);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout close = new FrameLayout(context);
        close.addView(new IconView(context, IconView.CLOSE, Ui.TEXT_MUTED_LIGHT).strokeUnits(2f),
                new FrameLayout.LayoutParams(Ui.px(22), Ui.px(22), Gravity.CENTER));
        close.setClickable(true);
        close.setOnClickListener(v -> listener.onCancel());
        Ui.pressEffect(close);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(Ui.px(44), Ui.px(44));
        closeLp.setMarginStart(Ui.px(8));
        titleRow.addView(close, closeLp);
        card.addView(titleRow, wrap(16));

        FrameLayout iconFrame = new FrameLayout(context);
        GradientDrawable dashed = new GradientDrawable();
        dashed.setShape(GradientDrawable.RECTANGLE);
        dashed.setColor(Ui.rgb(250, 247, 241));
        dashed.setCornerRadius(Ui.px(Ui.R_MD));
        dashed.setStroke(Math.max(2, Ui.px(1.5f)), Ui.rgb(190, 175, 150), Ui.px(7), Ui.px(5));
        iconFrame.setBackground(dashed);
        iconFrame.addView(new IconView(context, IconView.CAMERA, Ui.rgb(150, 132, 108)).strokeUnits(1.5f),
                new FrameLayout.LayoutParams(Ui.px(56), Ui.px(56), Gravity.CENTER));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(140));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        iconLp.bottomMargin = Ui.px(18);
        card.addView(iconFrame, iconLp);

        TextView body = Ui.text(context,
                "Theo quy định Nhà nước, thông tin thanh toán và hoá đơn cần trùng khớp với Căn cước công dân của Quý khách.\n\n"
                        + "Để đảm bảo quyền lợi và căn cứ xác minh trả hàng tại cửa hàng, Quý khách vui lòng xác thực gương mặt "
                        + "và kiểm tra thông tin giao dịch.\n\n"
                        + "Đồng ý chia sẻ dữ liệu cá nhân theo nghị định 13 về bảo vệ dữ liệu cá nhân.",
                9.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        body.setLineSpacing(0, 1.3f);
        card.addView(body, wrap(20));

        TextView agree = Ui.primaryButton(context, "ĐỒNG Ý CHIA SẺ & TIẾP TỤC", 10.5f);
        agree.setOnClickListener(v -> setState(State.WAITING));
        card.addView(agree, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(68)));

        popupOverlay.addView(card, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        ((FrameLayout.LayoutParams) card.getLayoutParams()).setMargins(Ui.px(20), 0, Ui.px(20), 0);
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
        face.setVerifying(verifying || success);
        successBadge.setVisibility(success ? VISIBLE : GONE);
        face.setVisibility(success ? GONE : VISIBLE);
        if (verifying) face.setPhase(0f);
        switch (state) {
            case WAITING:
                scanCaption.setText("Vui lòng nhìn thẳng và giữ khuôn mặt vào giữa khung hình");
                scanCaption.setTextColor(Ui.TEXT_BODY_DARK);
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

    private static LinearLayout.LayoutParams wrap(float bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(bottom);
        return lp;
    }
}
