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
 * Man "Thanh toan don hang" (QR chuyen khoan, buoc 3/3 sau Xac thuc khach hang / Xac nhan nhan hang) + man "Ket qua giao
 * dich" tiep theo (Phase 1: CHUA co cong thanh toan/API thanh toan that — toan bo la MO PHONG giao dien theo mockup Figma):
 * <ul>
 * <li>{@link State#TRANSFER}: QR gia + dem nguoc 5 phut, nut "HUY" / "TOI DA CHUYEN KHOAN" -> mo phong kiem tra (vong tron
 * trang quay -> xanh/do/cam) roi tu dong sang Ket qua giao dich neu thanh cong.</li>
 * <li>{@link State#RESULT_SUCCESS} / {@link State#RESULT_UNDETERMINED} / {@link State#RESULT_FAILED}: QR nhan giay hen lay
 * hang (hoac QR Zalo OA/hotline neu khong thanh cong), popup danh gia sao, nut "TIEP TUC TRAI NGHIEM" -> ve Home.</li>
 * </ul>
 */
public class PaymentView extends FrameLayout implements Ui.InsetAware {

    public enum State { TRANSFER, RESULT_SUCCESS, RESULT_UNDETERMINED, RESULT_FAILED }

    public interface Listener {
        /** Nut "HUY" o man chuyen khoan: ve man Xac nhan don hang. */
        void onCancel();

        /** Nut "TIEP TUC TRAI NGHIEM" o man Ket qua giao dich: ve Home, xoa gio hang. */
        void onFinished();
    }

    private static final int GLOW_H = 230;
    private static final long COUNTDOWN_MS = 5 * 60 * 1000L;
    private static final long TICK_MS = 1400;

    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout header;
    private final LinearLayout content;
    private final View glow;

    private final LinearLayout transferCard;
    private final TextView customerName;
    private final TextView orderCode;
    private final TextView orderValue;
    private final TextView pickupLocation;
    private final TextView countdownText;
    private final QrPlaceholderView transferQr;
    private final QrStatusRingView ring;
    private final TextView ringCaption;
    private final TextView cancelBtn;
    private final TextView confirmBtn;

    private final LinearLayout resultCard;
    private final TextView resultTitle;
    private final TextView resultSubtitle;
    private final FrameLayout resultQrFrame;
    private final TextView resultMessage;
    private final TextView resultHotline;
    private final TextView continueBtn;

    private final FrameLayout ratingOverlay;
    private final TextView[] stars = new TextView[5];
    private int ratingValue = 0;

    private final ValueAnimator ticker;
    private final Runnable countdownRunnable = this::tickCountdown;
    private State state = State.TRANSFER;
    private long remainingMs = COUNTDOWN_MS;
    private String hotline = "1800.2309";

    public PaymentView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setBackground(Ui.pageBackground());
        setClickable(true);

        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.HEADER_CHAMPAGNE, 0xFFE9E0CC, 0x00F5EFE6});
        glow = new View(context);
        glow.setBackground(g);
        addView(glow, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(GLOW_H), Gravity.TOP));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(Ui.px(196), Ui.px(68));
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(new LogoView(context, false).tint(Ui.BURGUNDY_PRIMARY), logoLp);
        TextView title = Ui.text(context, "Thanh toán đơn hàng", 14f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        title.setSingleLine(true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = Ui.px(6);
        header.addView(title, titleLp);
        column.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.px(16), Ui.px(8), Ui.px(16), Ui.px(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // ================= THE CHUYEN KHOAN (QR) =================
        transferCard = card(context);
        content.addView(transferCard, wrap(16));

        TextView warn = Ui.text(context, "Lưu ý quan trọng: Thao tác với ngân hàng, quý khách cần thực hiện với " +
                "ngân hàng chính chủ, không sử dụng hộ.", 9f, Ui.DOWN_RED, Ui.W_MEDIUM);
        warn.setLineSpacing(0, 1.2f);
        transferCard.addView(warn, wrap(14));
        TextView hint = Ui.text(context, "Quý khách vui lòng quét mã để thanh toán", 10f, Ui.TEXT_BODY_DARK, Ui.W_MEDIUM);
        hint.setGravity(Gravity.CENTER);
        transferCard.addView(hint, wrap(14));

        FrameLayout qrFrame = new FrameLayout(context);
        transferQr = new QrPlaceholderView(context, 7);
        qrFrame.addView(transferQr, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ring = new QrStatusRingView(context);
        qrFrame.addView(ring, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(Ui.px(220), Ui.px(220));
        qrLp.gravity = Gravity.CENTER_HORIZONTAL;
        transferCard.addView(qrFrame, qrLp);

        ringCaption = Ui.text(context, "", 9.5f, Ui.UP_GREEN, Ui.W_BOLD);
        ringCaption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rcLp = wrap(0);
        rcLp.topMargin = Ui.px(12);
        transferCard.addView(ringCaption, rcLp);

        countdownText = Ui.text(context, "", 9.5f, Ui.DOWN_RED, Ui.W_BOLD);
        countdownText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cdLp = wrap(16);
        cdLp.topMargin = Ui.px(14);
        transferCard.addView(countdownText, cdLp);

        View div1 = divider(context);
        transferCard.addView(div1, wrap(14));
        customerName = infoLine(context, transferCard, "Khách hàng");
        orderCode = infoLine(context, transferCard, "Mã đơn hàng");
        orderValue = infoLine(context, transferCard, "Giá trị đơn hàng");
        pickupLocation = infoLine(context, transferCard, "Địa điểm nhận hàng");

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        cancelBtn = Ui.outlineButton(context, "HỦY", 11f);
        cancelBtn.setLetterSpacing(0f);
        cancelBtn.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f)));
        cancelBtn.setOnClickListener(v -> listener.onCancel());
        confirmBtn = Ui.primaryButton(context, "TÔI ĐÃ CHUYỂN KHOẢN", 10.5f);
        confirmBtn.setOnClickListener(v -> startChecking());
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, Ui.px(64), 1f);
        b1.rightMargin = Ui.px(8);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, Ui.px(64), 1.4f);
        b2.leftMargin = Ui.px(8);
        buttons.addView(cancelBtn, b1);
        buttons.addView(confirmBtn, b2);
        LinearLayout.LayoutParams btnLp = wrap(0);
        btnLp.topMargin = Ui.px(20);
        transferCard.addView(buttons, btnLp);

        if (BuildConfig.DEBUG) transferCard.addView(buildDebugRow(context), wrap(0));

        // ================= KET QUA GIAO DICH =================
        resultCard = card(context);
        resultCard.setGravity(Gravity.CENTER_HORIZONTAL);
        content.addView(resultCard, wrap(16));

        FrameLayout resultHeader = new FrameLayout(context);
        resultTitle = Ui.text(context, "Kết quả giao dịch", 12f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        resultTitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        resultHeader.addView(resultTitle, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        resultCard.addView(resultHeader, wrap(14));

        resultSubtitle = Ui.text(context, "", 10.5f, Ui.UP_GREEN, Ui.W_BOLD);
        resultSubtitle.setGravity(Gravity.CENTER);
        resultCard.addView(resultSubtitle, wrap(16));

        resultQrFrame = new FrameLayout(context);
        LinearLayout.LayoutParams rqLp = new LinearLayout.LayoutParams(Ui.px(176), Ui.px(176));
        rqLp.gravity = Gravity.CENTER_HORIZONTAL;
        resultCard.addView(resultQrFrame, rqLp);

        resultMessage = Ui.text(context, "", 9.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        resultMessage.setGravity(Gravity.CENTER);
        resultMessage.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams rmLp = wrap(14);
        rmLp.topMargin = Ui.px(16);
        resultCard.addView(resultMessage, rmLp);

        resultHotline = Ui.text(context, "", 9.5f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        resultHotline.setGravity(Gravity.CENTER);
        resultCard.addView(resultHotline, wrap(18));

        continueBtn = Ui.primaryButton(context, "TIẾP TỤC TRẢI NGHIỆM", 11f);
        continueBtn.setOnClickListener(v -> listener.onFinished());
        resultCard.addView(continueBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(68)));

        // ================= POPUP DANH GIA SAO =================
        ratingOverlay = new FrameLayout(context);
        ratingOverlay.setBackgroundColor(Ui.withAlpha(0xFF000000, 0x66));
        ratingOverlay.setClickable(true);
        ratingOverlay.setVisibility(GONE);
        LinearLayout ratingCard = new LinearLayout(context);
        ratingCard.setOrientation(LinearLayout.VERTICAL);
        ratingCard.setGravity(Gravity.CENTER_HORIZONTAL);
        ratingCard.setBackground(Ui.card(Ui.R_LG));
        ratingCard.setPadding(Ui.px(22), Ui.px(24), Ui.px(22), Ui.px(22));
        FrameLayout ratingHeader = new FrameLayout(context);
        TextView ratingTitle = Ui.text(context, "Cảm ơn quý khách đã sử dụng dịch vụ", 11.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        ratingTitle.setGravity(Gravity.CENTER);
        ratingHeader.addView(ratingTitle, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        FrameLayout closeRating = new FrameLayout(context);
        closeRating.addView(new IconView(context, IconView.CLOSE, Ui.TEXT_MUTED_LIGHT).strokeUnits(2f),
                new FrameLayout.LayoutParams(Ui.px(22), Ui.px(22), Gravity.CENTER));
        closeRating.setClickable(true);
        closeRating.setOnClickListener(v -> ratingOverlay.setVisibility(GONE));
        Ui.pressEffect(closeRating);
        ratingHeader.addView(closeRating, new FrameLayout.LayoutParams(Ui.px(44), Ui.px(44), Gravity.TOP | Gravity.END));
        ratingCard.addView(ratingHeader, wrap(18));

        LinearLayout starRow = new LinearLayout(context);
        starRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            TextView star = new TextView(context);
            star.setText("★");
            star.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(22f));
            star.setTextColor(Ui.BORDER_LIGHT);
            star.setPadding(Ui.px(4), 0, Ui.px(4), 0);
            final int idx = i + 1;
            star.setOnClickListener(v -> setRating(idx));
            stars[i] = star;
            starRow.addView(star, new LinearLayout.LayoutParams(Ui.px(44), Ui.px(44)));
        }
        ratingCard.addView(starRow, wrap(18));
        TextView submitRating = Ui.primaryButton(context, "GỬI", 11f);
        submitRating.setOnClickListener(v -> ratingOverlay.setVisibility(GONE));
        ratingCard.addView(submitRating, new LinearLayout.LayoutParams(Ui.px(240), Ui.px(60)));
        ratingOverlay.addView(ratingCard, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        addView(ratingOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        ticker = ValueAnimator.ofFloat(0f, 1f);
        ticker.setDuration(TICK_MS);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(a -> ring.setPhase((float) a.getAnimatedValue()));

        applyState();
    }

    // ==================== API ====================

    /** Goi moi lan mo man: dien thong tin don + dat lai dem nguoc/trang thai. */
    public void bind(String customer, String code, String value, String location, String hotline) {
        customerName.setText(customer);
        orderCode.setText(code);
        orderValue.setText(value);
        pickupLocation.setText(location);
        this.hotline = hotline != null && !hotline.isEmpty() ? hotline : "1800.2309";
        remainingMs = COUNTDOWN_MS;
        ring.setState(QrStatusRingView.State.NONE);
        ringCaption.setText("");
        state = State.TRANSFER;
        ratingValue = 0;
        applyState();
        handler.removeCallbacks(countdownRunnable);
        handler.post(countdownRunnable);
    }

    public State getState() {
        return state;
    }

    private void tickCountdown() {
        if (state != State.TRANSFER || !isShown()) {
            handler.postDelayed(countdownRunnable, 1000);
            return;
        }
        remainingMs -= 1000;
        if (remainingMs <= 0) {
            remainingMs = 0;
            countdownText.setText("Đã hết hạn thanh toán — vui lòng huỷ và đặt lại");
            ring.setState(QrStatusRingView.State.FAILED);
            ringCaption.setText("Hết hạn giao dịch");
            ringCaption.setTextColor(Ui.DOWN_RED);
            updateAnimation();
            return;
        }
        long s = remainingMs / 1000;
        countdownText.setText(String.format(java.util.Locale.ROOT, "Hết hạn sau %02d:%02d", s / 60, s % 60));
        handler.postDelayed(countdownRunnable, 1000);
    }

    private void startChecking() {
        if (remainingMs <= 0) return;
        ring.setState(QrStatusRingView.State.CHECKING);
        ringCaption.setText("Đang kiểm tra trạng thái giao dịch, vui lòng chờ trong giây lát");
        ringCaption.setTextColor(Ui.TEXT_BODY_DARK);
        updateAnimation();
        // Phase 1: chua co cong thanh toan that -> mo phong ket qua thanh cong sau mot nhip cho ngan.
        handler.postDelayed(() -> resolveChecking(QrStatusRingView.State.SUCCESS), 1600);
    }

    private void resolveChecking(QrStatusRingView.State outcome) {
        if (state != State.TRANSFER) return;
        ring.setState(outcome);
        updateAnimation();
        switch (outcome) {
            case SUCCESS:
                ringCaption.setText("Thanh toán thành công");
                ringCaption.setTextColor(Ui.UP_GREEN);
                handler.postDelayed(() -> setResultState(State.RESULT_SUCCESS), 900);
                break;
            case FAILED:
                ringCaption.setText("Giao dịch thất bại");
                ringCaption.setTextColor(Ui.DOWN_RED);
                break;
            case UNKNOWN:
                ringCaption.setText("Không xác định trạng thái thanh toán");
                ringCaption.setTextColor(Ui.rgb(217, 143, 20));
                break;
            default:
                break;
        }
    }

    /** Chuyen sang man Ket qua giao dich (thanh cong / chua xac dinh / that bai). */
    public void setResultState(State resultState) {
        state = resultState;
        applyState();
    }

    private void applyState() {
        boolean transfer = state == State.TRANSFER;
        transferCard.setVisibility(transfer ? VISIBLE : GONE);
        resultCard.setVisibility(transfer ? GONE : VISIBLE);
        if (transfer) return;

        resultQrFrame.removeAllViews();
        boolean success = state == State.RESULT_SUCCESS;
        resultHotline.setText("Hotline hỗ trợ: " + hotline);
        if (success) {
            resultSubtitle.setText("Giao dịch thành công");
            resultSubtitle.setTextColor(Ui.UP_GREEN);
            resultQrFrame.addView(new QrPlaceholderView(getContext(), 42), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            resultMessage.setText("Quét mã để nhận giấy hẹn lấy hàng. Cảm ơn quý khách đã sử dụng dịch vụ.");
            handler.postDelayed(() -> ratingOverlay.setVisibility(VISIBLE), 400);
        } else {
            boolean undetermined = state == State.RESULT_UNDETERMINED;
            resultSubtitle.setText(undetermined ? "Giao dịch chưa xác định" : "Giao dịch thất bại");
            resultSubtitle.setTextColor(undetermined ? Ui.rgb(217, 143, 20) : Ui.DOWN_RED);
            FrameLayout zalo = new FrameLayout(getContext());
            zalo.setBackground(Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_MD, Ui.BORDER_LIGHT, 1));
            zalo.addView(new IconView(getContext(), IconView.QR, Ui.TEXT_MUTED_LIGHT).strokeUnits(1.6f),
                    new FrameLayout.LayoutParams(Ui.px(70), Ui.px(70), Gravity.CENTER));
            resultQrFrame.addView(zalo, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            resultMessage.setText(undetermined
                    ? "Chưa xác định được trạng thái thanh toán. Trong trường hợp bị trừ tiền, quý khách vui lòng liên hệ hotline hoặc quét mã QR để được hỗ trợ."
                    : "Thanh toán đơn hàng thất bại. Trong trường hợp bị trừ tiền, quý khách vui lòng liên hệ hotline hoặc quét mã QR để được hỗ trợ.");
        }
        setRating(0);
    }

    private void setRating(int value) {
        ratingValue = value;
        for (int i = 0; i < stars.length; i++) {
            stars[i].setTextColor(i < value ? Ui.rgb(230, 170, 30) : Ui.BORDER_LIGHT);
        }
    }

    private void updateAnimation() {
        boolean run = ring.getVisibility() == VISIBLE && isAttachedToWindow() && isShown()
                && state == State.TRANSFER && remainingMs > 0 && captionMeansChecking();
        if (run && !ticker.isStarted()) ticker.start();
        else if (!run && ticker.isStarted()) ticker.cancel();
    }

    private boolean captionMeansChecking() {
        return "Đang kiểm tra trạng thái giao dịch, vui lòng chờ trong giây lát".contentEquals(ringCaption.getText());
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
    public void onInsets(int left, int top, int right, int bottom) {
        setPadding(left, 0, right, 0);
        header.setPadding(Ui.px(6), top + Ui.px(10), Ui.px(16), Ui.px(4));
        content.setPadding(Ui.px(16), Ui.px(8), Ui.px(16), Ui.px(24) + bottom);
        LayoutParams glp = (LayoutParams) glow.getLayoutParams();
        glp.height = top + Ui.px(GLOW_H);
        glow.setLayoutParams(glp);
    }

    // ==================== DUNG UI ====================

    private static LinearLayout card(Context context) {
        LinearLayout c = new LinearLayout(context);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(c);
        c.setPadding(Ui.px(20), Ui.px(22), Ui.px(20), Ui.px(20));
        return c;
    }

    private static View divider(Context context) {
        View v = new View(context);
        v.setBackgroundColor(Ui.BORDER_LIGHT);
        return v;
    }

    private TextView infoLine(Context context, LinearLayout parent, String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(context, label, 9.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = Ui.text(context, "", 9.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        value.setGravity(Gravity.END);
        LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f);
        row.addView(value, vLp);
        parent.addView(row, wrap(10));
        return value;
    }

    /** Hang nut xem thu 3 ket qua kiem tra — chi co o ban DEBUG. */
    private View buildDebugRow(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rect(0x00FFFFFF, Ui.R_MD, Ui.rgb(190, 175, 150), 1));
        box.setPadding(Ui.px(12), Ui.px(10), Ui.px(12), Ui.px(12));
        LinearLayout.LayoutParams topLp = wrap(8);
        topLp.topMargin = Ui.px(16);
        box.addView(Ui.text(context, "DEBUG — Mô phỏng kết quả kiểm tra:", 9f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM), topLp);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Thành công", "Thất bại", "Không xác định"};
        QrStatusRingView.State[] outcomes = {QrStatusRingView.State.SUCCESS, QrStatusRingView.State.FAILED, QrStatusRingView.State.UNKNOWN};
        State[] resultStates = {State.RESULT_SUCCESS, State.RESULT_FAILED, State.RESULT_UNDETERMINED};
        for (int i = 0; i < names.length; i++) {
            TextView b = Ui.text(context, names[i], 9.5f, Ui.TEXT_BODY_DARK, Ui.W_BOLD);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BORDER_LIGHT, 1),
                    Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BORDER_LIGHT, 1)));
            Ui.pressEffect(b);
            final int idx = i;
            b.setOnClickListener(v -> {
                ring.setState(QrStatusRingView.State.CHECKING);
                ringCaption.setText("Đang kiểm tra trạng thái giao dịch, vui lòng chờ trong giây lát");
                ringCaption.setTextColor(Ui.TEXT_BODY_DARK);
                updateAnimation();
                handler.postDelayed(() -> {
                    if (idx == 0) resolveChecking(outcomes[idx]);
                    else {
                        ring.setState(outcomes[idx]);
                        updateAnimation();
                        ringCaption.setText(idx == 1 ? "Giao dịch thất bại" : "Không xác định trạng thái thanh toán");
                        ringCaption.setTextColor(idx == 1 ? Ui.DOWN_RED : Ui.rgb(217, 143, 20));
                    }
                }, 900);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(56), 1f);
            if (i > 0) lp.leftMargin = Ui.px(6);
            row.addView(b, lp);
        }
        box.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams row2Lp = wrap(0);
        row2Lp.topMargin = Ui.px(8);
        TextView jump = Ui.text(context, "Xem thẳng màn Kết quả (thành công)", 9f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        jump.setGravity(Gravity.CENTER);
        jump.setOnClickListener(v -> setResultState(State.RESULT_SUCCESS));
        box.addView(jump, row2Lp);
        return box;
    }

    private static LinearLayout.LayoutParams wrap(float bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(bottom);
        return lp;
    }
}
