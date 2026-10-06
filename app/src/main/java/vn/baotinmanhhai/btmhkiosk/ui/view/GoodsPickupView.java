package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
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
 * Man "Tra hang" (buoc 1: xac nhan khach hang) — CHI LA GIAO DIEN TINH (Phase 1 chua co camera FacePOS, dau doc CCCD
 * hay API): 2 trang thai {@link State#WAITING} (cho khach) va {@link State#VERIFYING} (khach dang quet khuon mat).
 * Mockup Figma la ngang, o day xep lai cho man doc: thanh dau champagne (logo do ruou + mui ten quay lai + tieu de),
 * thanh 3 buoc ngang, roi card trang "BAT DAU THAO TAC" (huong dan, vong quet mat, khung CCCD net dut, nut Thu lai / Tiep tuc).
 * Khong co du lieu khach hang gia; "Tiep tuc" luon vo hieu (chua co buoc ke tiep).
 */
public class GoodsPickupView extends FrameLayout implements Ui.InsetAware {

    public enum State { WAITING, VERIFYING, FAILED }

    private static final int GLOW_H = 230;
    private static final long TICK_MS = 1800;
    private static final int LINE_IDLE = Ui.BORDER_LIGHT;

    private final LinearLayout header;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final TextView[] stepCircle = new TextView[3];
    private final View[] lineLeft = new View[3];
    private final View[] lineRight = new View[3];
    private final FaceScanView face;
    private final DotsView dots;
    private final LinearLayout verifyInfo;
    private final LinearLayout scanCard;
    private final LinearLayout failedCard;
    private final TextView failedMessage;
    private final View glow;
    private final ValueAnimator ticker;
    private State state = State.WAITING;

    public GoodsPickupView(Context context, Runnable onBack) {
        super(context);
        setBackground(Ui.pageBackground());
        setClickable(true); // chan cham xuyen xuong lop duoi

        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.HEADER_CHAMPAGNE, 0xFFE9E0CC, 0x00F5EFE6});
        glow = new View(context);
        glow.setBackground(g);
        addView(glow, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(GLOW_H), Gravity.TOP));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- Thanh dau: logo do ruou + [mui ten quay lai] tieu de ----
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(Ui.px(196), Ui.px(68));
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        header.addView(new LogoView(context, false).tint(Ui.BURGUNDY_PRIMARY), logoLp);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout back = new FrameLayout(context);
        back.addView(new IconView(context, IconView.ARROW_LEFT, Ui.TEXT_TITLE_DARK).strokeUnits(2.2f),
                new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        back.setClickable(true);
        back.setOnClickListener(v -> onBack.run());
        Ui.pressEffect(back);
        titleRow.addView(back, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        TextView title = Ui.text(context, "Trả hàng", 14f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        title.setSingleLine(true);
        title.setOnClickListener(v -> onBack.run());
        titleRow.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        header.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- Noi dung cuon (an toan tren man hinh thap / co ban phim) ----
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.px(16), Ui.px(8), Ui.px(16), Ui.px(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        column.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(buildStepper(context), block(0));

        // ---- Card "BAT DAU THAO TAC" ----
        LinearLayout card = new LinearLayout(context);
        scanCard = card;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(card);
        card.setPadding(Ui.px(20), Ui.px(22), Ui.px(20), Ui.px(20));
        content.addView(card, block(16));

        card.addView(Ui.text(context, "BẮT ĐẦU THAO TÁC:", 11f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD), wrap(10));
        card.addView(instruction(context, "1. Hướng dẫn khách nhìn vào thiết bị FacePOS để quét khuôn mặt."), wrap(6));
        card.addView(instruction(context, "2. Yêu cầu khách đặt/đưa CCCD vào Đầu đọc thẻ."), wrap(16));

        View divider = new View(context);
        divider.setBackgroundColor(Ui.BORDER_LIGHT);
        card.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1))));

        // Khoi quet khuon mat
        TextView faceTitle = Ui.text(context, "Quét khuôn mặt tại Face POS", 10f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        faceTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ftLp = wrap(0);
        ftLp.topMargin = Ui.px(18);
        card.addView(faceTitle, ftLp);

        face = new FaceScanView(context);
        LinearLayout.LayoutParams faceLp = new LinearLayout.LayoutParams(Ui.px(250), Ui.px(250));
        faceLp.gravity = Gravity.CENTER_HORIZONTAL;
        faceLp.topMargin = Ui.px(6);
        card.addView(face, faceLp);
        face.setClickable(true);
        face.setOnClickListener(v -> {
            // Chi de xem giao dien khi debug: chua co FacePOS that. Ban release khong lam gi.
            if (!BuildConfig.DEBUG) return;
            if (state == State.WAITING) setState(State.VERIFYING);
            else if (state == State.VERIFYING) setFailure(CustomerAuthView.MSG_FACE_NO_MATCH);
        });

        // Chu duoi vong tron (chi hien khi VERIFYING, nhung van giu cho de bo cuc khong nhay)
        verifyInfo = new LinearLayout(context);
        verifyInfo.setOrientation(LinearLayout.VERTICAL);
        verifyInfo.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView caption = Ui.text(context, "Vui lòng giữ khuôn mặt trong khung", 9.5f, Ui.TEXT_BODY_DARK, Ui.W_MEDIUM);
        caption.setGravity(Gravity.CENTER);
        verifyInfo.addView(caption, wrap(8));
        dots = new DotsView(context);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(Ui.px(88), Ui.px(12));
        dotsLp.bottomMargin = Ui.px(6);
        verifyInfo.addView(dots, dotsLp);
        TextView verifying = Ui.text(context, "Đang xác minh khuôn mặt", 8.5f, Ui.UP_GREEN, Ui.W_MEDIUM);
        verifying.setGravity(Gravity.CENTER);
        verifyInfo.addView(verifying, wrap(0));
        LinearLayout.LayoutParams viLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        viLp.topMargin = Ui.px(10);
        card.addView(verifyInfo, viLp);

        // Khoi quet CCCD
        TextView idTitle = Ui.text(context, "Quét CCCD tại Đầu đọc thẻ", 10f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        idTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams itLp = wrap(0);
        itLp.topMargin = Ui.px(22);
        card.addView(idTitle, itLp);

        LinearLayout idFrame = new LinearLayout(context);
        idFrame.setOrientation(LinearLayout.VERTICAL);
        idFrame.setGravity(Gravity.CENTER);
        GradientDrawable dashed = new GradientDrawable();
        dashed.setShape(GradientDrawable.RECTANGLE);
        dashed.setColor(Ui.rgb(250, 247, 241));
        dashed.setCornerRadius(Ui.px(Ui.R_MD));
        dashed.setStroke(Math.max(2, Ui.px(1.5f)), Ui.rgb(190, 175, 150), Ui.px(7), Ui.px(5));
        idFrame.setBackground(dashed);
        idFrame.addView(new IconView(context, IconView.ID_CARD, Ui.rgb(150, 132, 108)).strokeUnits(1.4f),
                new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        TextView idHint = Ui.text(context, "Mặt trước, chữ hướng lên", 9.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM);
        idHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = wrap(0);
        hintLp.topMargin = Ui.px(10);
        idFrame.addView(idHint, hintLp);
        LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(168));
        idLp.topMargin = Ui.px(12);
        card.addView(idFrame, idLp);

        // Nut: Thu lai (vien do ruou) / Tiep tuc (vo hieu)
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView retry = Ui.outlineButton(context, "Thử lại", 11f);
        retry.setLetterSpacing(0f);
        retry.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f)));
        retry.setOnClickListener(v -> setState(State.WAITING));
        TextView next = Ui.text(context, "Tiếp tục", 11f, Ui.TEXT_MUTED_LIGHT, Ui.W_BOLD);
        next.setGravity(Gravity.CENTER);
        next.setBackground(Ui.rect(Ui.FIELD_GREY, Ui.R_PILL, 0, 0));
        next.setEnabled(false); // chua co buoc ke tiep
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        b1.rightMargin = Ui.px(8);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        b2.leftMargin = Ui.px(8);
        buttons.addView(retry, b1);
        buttons.addView(next, b2);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = Ui.px(24);
        card.addView(buttons, btnLp);

        // ---- Popup "Thong bao" khi xac thuc that bai (dung chung noi dung voi CustomerAuthView) ----
        failedCard = new LinearLayout(context);
        failedCard.setOrientation(LinearLayout.VERTICAL);
        failedCard.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(failedCard);
        failedCard.setPadding(Ui.px(20), Ui.px(22), Ui.px(20), Ui.px(20));
        content.addView(failedCard, block(16));
        failedMessage = buildFailed(context);

        if (BuildConfig.DEBUG) content.addView(buildDebugRow(context), block(20));

        ticker = ValueAnimator.ofFloat(0f, 1f);
        ticker.setDuration(TICK_MS);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            face.setPhase(p);
            dots.setPhase(p);
        });

        applyState();
    }

    // ==================== API ====================

    public State getState() {
        return state;
    }

    public void setState(State newState) {
        state = newState == null ? State.WAITING : newState;
        applyState();
    }

    /** Chuyen sang State.FAILED va hien dung noi dung thong bao (vd CustomerAuthView.MSG_FACE_NO_MATCH, MSG_ID_NOT_FOUND...). */
    public void setFailure(String message) {
        failedMessage.setText(message == null || message.trim().isEmpty()
                ? CustomerAuthView.MSG_FACE_NO_MATCH : message.trim());
        setState(State.FAILED);
    }

    private void applyState() {
        boolean verifying = state == State.VERIFYING;
        boolean scanning = state == State.WAITING || state == State.VERIFYING;
        scanCard.setVisibility(scanning ? VISIBLE : GONE);
        failedCard.setVisibility(state == State.FAILED ? VISIBLE : GONE);
        face.setVerifying(verifying);
        verifyInfo.setVisibility(verifying ? VISIBLE : INVISIBLE);
        for (int i = 0; i < 3; i++) {
            boolean active = verifying && i == 0;
            stepCircle[i].setBackground(Ui.oval(active ? Ui.BURGUNDY_PRIMARY : Ui.BORDER_LIGHT));
            stepCircle[i].setTextColor(active ? Ui.SURFACE_WHITE : Ui.TEXT_MUTED_LIGHT);
            if (lineRight[i] != null) lineRight[i].setBackgroundColor(active ? Ui.BURGUNDY_PRIMARY : LINE_IDLE);
        }
        if (verifying) face.setPhase(0f);
        updateAnimation();
    }

    /** Chay hoat anh chi khi dang VERIFYING va man dang hien (tiet kiem pin, khong ro ri khi an/thoat). */
    private void updateAnimation() {
        boolean run = state == State.VERIFYING && isAttachedToWindow() && isShown();
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
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateAnimation();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        updateAnimation();
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

    @Override
    public void setPadding(int l, int t, int r, int b) {
        super.setPadding(l, t, r, b);
    }

    // ==================== DUNG UI ====================

    private static LinearLayout.LayoutParams block(float topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(topMargin);
        return lp;
    }

    private static LinearLayout.LayoutParams wrap(float bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(bottom);
        return lp;
    }

    private static TextView instruction(Context context, String text) {
        TextView t = Ui.text(context, text, 10f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    /** Thanh 3 buoc ngang trong card trang: vong tron so + duong noi + ten buoc + mo ta ngan. */
    private View buildStepper(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(card);
        card.setPadding(Ui.px(8), Ui.px(18), Ui.px(8), Ui.px(16));

        String[] titles = {"Xác nhận khách hàng", "Chọn trả hàng", "Xuất hóa đơn"};
        String[] subs = {"Khách hàng quét khuôn mặt và CCCD", "Chọn loại hàng cần trả", "Chọn loại hóa đơn cần xuất"};
        for (int i = 0; i < 3; i++) {
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);

            FrameLayout top = new FrameLayout(context);
            LinearLayout lines = new LinearLayout(context);
            lines.setOrientation(LinearLayout.HORIZONTAL);
            int lh = Math.max(2, Ui.px(2));
            View l = new View(context);
            View r = new View(context);
            l.setBackgroundColor(LINE_IDLE);
            r.setBackgroundColor(LINE_IDLE);
            l.setVisibility(i == 0 ? INVISIBLE : VISIBLE);
            r.setVisibility(i == 2 ? INVISIBLE : VISIBLE);
            lines.addView(l, new LinearLayout.LayoutParams(0, lh, 1f));
            lines.addView(r, new LinearLayout.LayoutParams(0, lh, 1f));
            top.addView(lines, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, lh, Gravity.CENTER_VERTICAL));
            lineLeft[i] = l;
            lineRight[i] = r;
            TextView circle = Ui.text(context, String.valueOf(i + 1), 11f, Ui.TEXT_MUTED_LIGHT, Ui.W_BOLD);
            circle.setGravity(Gravity.CENTER);
            stepCircle[i] = circle;
            top.addView(circle, new FrameLayout.LayoutParams(Ui.px(44), Ui.px(44), Gravity.CENTER));
            col.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(44)));

            TextView t = Ui.text(context, titles[i], 9f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tLp.topMargin = Ui.px(10);
            tLp.leftMargin = Ui.px(4);
            tLp.rightMargin = Ui.px(4);
            col.addView(t, tLp);
            TextView s = Ui.text(context, subs[i], 8f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
            s.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sLp.topMargin = Ui.px(4);
            sLp.leftMargin = Ui.px(4);
            sLp.rightMargin = Ui.px(4);
            col.addView(s, sLp);

            card.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return card;
    }

    /**
     * Popup "Thong bao" khi xac thuc that bai — theo mockup sheet "Xac thuc that bai" (Kiosk.xlsx): tieu de "Thong bao"
     * + nut dong (X), noi dung loi, hai nut "VE TRANG CHU" (vien) / "DONG Y" (do ruou, quay ve WAITING de thu lai).
     */
    private TextView buildFailed(Context context) {
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(context, "Thông báo", 12f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        FrameLayout close = new FrameLayout(context);
        close.addView(new IconView(context, IconView.CLOSE, Ui.TEXT_MUTED_LIGHT).strokeUnits(2f),
                new FrameLayout.LayoutParams(Ui.px(24), Ui.px(24), Gravity.CENTER));
        close.setClickable(true);
        close.setOnClickListener(v -> setState(State.WAITING));
        Ui.pressEffect(close);
        titleRow.addView(close, new LinearLayout.LayoutParams(Ui.px(48), Ui.px(48)));
        failedCard.addView(titleRow, wrap(14));

        TextView message = Ui.text(context, CustomerAuthView.MSG_FACE_NO_MATCH, 10.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        message.setLineSpacing(0, 1.25f);
        failedCard.addView(message, wrap(22));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView home = Ui.outlineButton(context, "VỀ TRANG CHỦ", 11f);
        home.setLetterSpacing(0f);
        home.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f)));
        home.setOnClickListener(v -> setState(State.WAITING));
        TextView agree = Ui.primaryButton(context, "ĐỒNG Ý", 11f);
        agree.setLetterSpacing(0.02f);
        agree.setOnClickListener(v -> setState(State.WAITING));
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        b1.rightMargin = Ui.px(8);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        b2.leftMargin = Ui.px(8);
        buttons.addView(home, b1);
        buttons.addView(agree, b2);
        failedCard.addView(buttons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return message;
    }

    /** Hang nut xem thu trang thai That bai — chi tao o ban DEBUG, release khong co. */
    private View buildDebugRow(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rect(0x00FFFFFF, Ui.R_MD, Ui.rgb(190, 175, 150), 1));
        box.setPadding(Ui.px(12), Ui.px(10), Ui.px(12), Ui.px(12));
        box.addView(Ui.text(context, "DEBUG — Mô phỏng (chỉ có ở bản debug):", 9f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM), wrap(8));
        TextView b = Ui.text(context, "Thất bại", 9.5f, Ui.TEXT_BODY_DARK, Ui.W_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BORDER_LIGHT, 1),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BORDER_LIGHT, 1)));
        Ui.pressEffect(b);
        b.setOnClickListener(v -> setFailure(CustomerAuthView.MSG_FACE_NO_MATCH));
        box.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(64)));
        return box;
    }

    /** 5 cham xanh nho chay lan luot (bao "dang xu ly", khong phai tien do that). */
    private static class DotsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float phase;

        DotsView(Context context) {
            super(context);
        }

        void setPhase(float phase) {
            this.phase = phase;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int n = 5;
            float d = Math.min(getHeight(), getWidth() / (n * 2f - 1f));
            float gap = (getWidth() - n * d) / (n - 1f);
            float cy = getHeight() / 2f;
            for (int i = 0; i < n; i++) {
                float pos = ((phase * n) - i + n) % n; // 0 = dang sang nhat, tang dan = mo dan
                int alpha = pos < 3f ? Math.round(255 * (1f - pos / 3f * 0.75f)) : 64;
                paint.setColor(Ui.withAlpha(FaceScanView.GREEN_RING, alpha));
                c.drawCircle(d / 2f + i * (d + gap), cy, d / 2f, paint);
            }
        }
    }
}
