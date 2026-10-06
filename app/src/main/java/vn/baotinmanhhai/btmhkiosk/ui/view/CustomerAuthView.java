package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import vn.baotinmanhhai.btmhkiosk.BuildConfig;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man "Xac thuc khach hang" (dinh danh) — buoc DAU TIEN sau khi bam "DAT MUA" o man Chi tiet, truoc man xac nhan nhan hang.
 * CHI LA GIAO DIEN (Phase 1 chua co camera FacePOS, dau doc CCCD hay API dinh danh): cung ngon ngu hinh anh voi
 * {@link GoodsPickupView} (thanh dau champagne + logo do ruou + mui ten quay lai, card trang, vong quet mat
 * {@link FaceScanView}, khung CCCD net dut) nhung xep cho man DOC va co 4 ket qua:
 * <ul>
 * <li>{@link State#WAITING} / {@link State#VERIFYING}: huong dan + vong quet mat + khung CCCD + "Thu lai" / "Tiep tuc".</li>
 * <li>{@link State#IDENTIFIED}: the thong tin khach (vien avatar ve bang Canvas, khong phai anh) — moi gia tri = "—" cho toi khi
 * co tinh nang dinh danh that goi {@link #setCustomer}.</li>
 * <li>{@link State#EXPIRED}: giay to het han, chi co "Quay lai".</li>
 * <li>{@link State#NEW_CUSTOMER}: khach moi, o nhap SDT / ho ten (CHI de xem giao dien — khong luu, khong gui di dau).</li>
 * </ul>
 * Khong co du lieu khach gia, khong mang, khong luu tru. Ban DEBUG: cham vong tron de xem VERIFYING + hang nut "Mo phong" xem 3 ket qua.
 */
public class CustomerAuthView extends FrameLayout implements Ui.InsetAware {

    public enum State { TERMS, HINT, WAITING, VERIFYING, IDENTIFIED, EXPIRED, NEW_CUSTOMER, FAILED }

    /** Noi dung "Dieu kien va dieu khoan mua vang" (theo mockup Figma "Man hinh dieu khoan va chinh sach"). */
    private static final String TERMS_TEXT = "TRÂN TRỌNG CẢM ƠN QUÝ KHÁCH HÀNG ĐÃ TIN TƯỞNG VÀ LỰA CHỌN MUA SẢN PHẨM "
            + "VÀNG BẠC CỦA CÔNG TY CỔ PHẦN VÀNG BẠC ĐÁ QUÝ BẢO TÍN MẠNH HẢI (sau đây gọi tắt là \"BTMH\"). "
            + "Quý khách hàng xác nhận đã đọc, hiểu rõ và đồng ý với các điều khoản bán hàng, chính sách bảo mật và "
            + "quy trình xác thực thông tin cá nhân (quét khuôn mặt và Căn cước công dân/VNeID) để tiếp tục giao dịch "
            + "tại BTMH Kiosk.\n\nĐiều 1. Định nghĩa\nTrừ khi ngữ cảnh quy định khác, các từ ngữ dưới đây được hiểu "
            + "theo đúng quy định tại bản điều khoản và điều kiện mua hàng.";

    /**
     * Noi dung popup "Thong bao" khi xac thuc that bai — dung dung tu Message List (sheet "Message List" trong Kiosk.xlsx),
     * du bo ma E01-E14: E01-E06 quet mat, E07-E09 quet CCCD, E10-E14 quet QR VNeID.
     */
    public static final String MSG_E01_FACE_NOT_DETECTED = "Không phát hiện khuôn mặt. Vui lòng giữ khuôn mặt ở chính giữa khung hình.";
    public static final String MSG_E02_FACE_TOO_FAR = "Khuôn mặt ở quá xa. Vui lòng di chuyển lại gần hơn.";
    public static final String MSG_E03_FACE_MULTIPLE = "Phát hiện nhiều hơn một khuôn mặt. Vui lòng chỉ để một khuôn mặt trong khung hình.";
    public static final String MSG_E04_IMAGE_BLURRY = "Ảnh bị mờ. Vui lòng giữ yên khuôn mặt ở chính giữa khung hình.";
    public static final String MSG_E05_FACE_BLURRY = "Khuôn mặt bị mờ. Vui lòng không đeo kính râm, bỏ khẩu trang hoặc mũ và nhìn thẳng vào camera.";
    public static final String MSG_E06_FACE_NO_MATCH = "Nhận diện khuôn mặt thất bại. Vui lòng quét lại hoặc đăng ký tài khoản.";
    public static final String MSG_E07_ID_NOT_FOUND = "Không tìm thấy thẻ! Vui lòng đưa thẻ Căn cước gắn chip vào đầu đọc.";
    public static final String MSG_E08_ID_CHIP_UNREADABLE = "Không đọc được chip. Vui lòng sử dụng thẻ Căn cước có gắn chip hoặc phương thức khác.";
    public static final String MSG_E09_ID_EXPIRED = "Thẻ Căn cước không còn hiệu lực. Vui lòng sử dụng phương thức khác.";
    public static final String MSG_E10_QR_NOT_FOUND = "Không tìm thấy mã QR! Vui lòng mở VNeID và đặt mã QR trước đầu quét.";
    public static final String MSG_E11_QR_TOO_FAR = "Khoảng cách quá xa. Vui lòng di chuyển lại gần hơn.";
    public static final String MSG_E12_QR_MULTIPLE = "Phát hiện nhiều hơn một mã QR. Vui lòng chỉ để một mã QR trong khung hình.";
    public static final String MSG_E13_QR_BLURRY = "Ảnh bị mờ. Vui lòng giữ yên và không che tay vào mã QR căn cước.";
    public static final String MSG_E14_QR_UNREADABLE = "Không đọc được mã QR. Quý khách vui lòng kiểm tra tại mục: Chọn mục Ví giấy tờ, bấm Xuất trình giấy tờ và chọn mã QR.";
    public static final String MSG_ID_ALREADY_EXISTS = "Số định danh đã tồn tại trên hệ thống. Vui lòng kiểm tra và cập nhật thông tin tài khoản tại đây!";
    /** Ten khach demo dung chung cho nut mo phong "Da dinh danh" (DEBUG) va man Home khi dang nhap qua luong nay. */
    public static final String DEMO_CUSTOMER_NAME = "Nguyễn Văn A (demo)";
    /** Giu lai ten cu de khong pha vo cac noi da dung MSG_FACE_NO_MATCH/MSG_ID_NOT_FOUND/MSG_ID_EXPIRED truoc do. */
    public static final String MSG_FACE_NO_MATCH = MSG_E06_FACE_NO_MATCH;
    public static final String MSG_ID_NOT_FOUND = MSG_E07_ID_NOT_FOUND;
    public static final String MSG_ID_EXPIRED = MSG_E09_ID_EXPIRED;
    /** Tat ca 15 ma, theo dung thu tu bang Message List — dung de xoay vong trong nut mo phong DEBUG. */
    private static final String[] ALL_DEBUG_MESSAGES = {
            MSG_E01_FACE_NOT_DETECTED, MSG_E02_FACE_TOO_FAR, MSG_E03_FACE_MULTIPLE, MSG_E04_IMAGE_BLURRY,
            MSG_E05_FACE_BLURRY, MSG_E06_FACE_NO_MATCH, MSG_E07_ID_NOT_FOUND, MSG_E08_ID_CHIP_UNREADABLE,
            MSG_E09_ID_EXPIRED, MSG_E10_QR_NOT_FOUND, MSG_E11_QR_TOO_FAR, MSG_E12_QR_MULTIPLE,
            MSG_E13_QR_BLURRY, MSG_E14_QR_UNREADABLE, MSG_ID_ALREADY_EXISTS,
    };
    private int debugMessageIndex = 0;
    // Dien qua debug "Da dinh danh" (demo co dinh) — dung de truyen sang OrderConfirmView.setCustomerInfo().
    private String identifiedIdNumber;
    private String identifiedPhone;
    private String identifiedAddress;

    public interface Listener {
        /** Mui ten quay lai / nut Back cung / "Quay lai" cua trang thai het han: ve man Chi tiet. */
        void onBack();

        /** "Tiep tuc" / "Tiep tuc dat hang" / "Tao ho so va tiep tuc": sang man xac nhan don hang. */
        void onContinue();
    }

    /**
     * TAM THOI (Phase 1: chua co FacePOS / dau doc CCCD / API dinh danh): cho phep khach di tiep khi CHUA dinh danh de luong mua hang
     * (xac nhan nhan hang) van vao duoc tren ban RELEASE. Ban DEBUG khong dung co nay (co hang nut "Mo phong").
     * PHAI doi thanh {@code false} (hoac xoa han nut "Tiep tuc (chua dinh danh)" + ghi chu) khi co dinh danh that.
     */
    public static final boolean ALLOW_CONTINUE_WITHOUT_IDENTIFICATION = true;

    private static final int GLOW_H = 230;
    private static final long TICK_MS = 1800;
    private static final String DASH = "—";

    private final Listener listener;
    private final LinearLayout header;
    private final TextView headerTitle;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final View glow;
    private final LinearLayout verifyInfo;
    private final View outerStepper;
    private final LinearLayout termsCard;
    private final LinearLayout termsVerticalStepper;
    private final LinearLayout hintCard;
    private final LinearLayout scanCard;
    private final QrStatusRingView scanRing;
    private final TextView scanCaption;
    private final TextView scanHelper;
    private final LinearLayout identifiedCard;
    private final LinearLayout expiredCard;
    private final LinearLayout newCard;
    private final LinearLayout failedCard;
    private final TextView failedMessage;
    private final TextView[] customerValue = new TextView[5];
    private final EditText phoneInput;
    private final EditText nameInput;
    private final EditText idNumberInput;
    private final EditText dobInput;
    private final EditText addressInput;
    private final EditText emailInput;
    private final ValueAnimator ticker;
    private State state = State.TERMS;

    public CustomerAuthView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
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
        back.setOnClickListener(v -> goBack());
        Ui.pressEffect(back);
        titleRow.addView(back, new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        headerTitle = Ui.text(context, "Xác thực khách hàng", 14f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        headerTitle.setSingleLine(true);
        headerTitle.setOnClickListener(v -> goBack());
        titleRow.addView(headerTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        titleRow.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1f));
        FrameLayout homeBtn = new FrameLayout(context);
        homeBtn.setBackground(Ui.oval(Ui.BURGUNDY_PRIMARY));
        homeBtn.addView(new IconView(context, IconView.HOME, Ui.SURFACE_WHITE).strokeUnits(1.8f),
                new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        homeBtn.setClickable(true);
        homeBtn.setOnClickListener(v -> goBack());
        Ui.pressEffect(homeBtn);
        Ui.softShadow(homeBtn);
        titleRow.addView(homeBtn, new LinearLayout.LayoutParams(Ui.px(48), Ui.px(48)));
        header.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- Noi dung cuon (an toan khi ban phim hien / man thap) ----
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

        outerStepper = buildStepper(context);
        content.addView(outerStepper, block(0));

        // ---- Stepper doc rieng cho man "Dang ky tai khoan" (chi hien o State.TERMS) ----
        termsVerticalStepper = buildVerticalStepper(context);
        content.addView(termsVerticalStepper, block(0));

        // ---- Card "Dieu khoan va chinh sach" / "Dang ky tai khoan" (hien dau tien, truoc khi quet) ----
        termsCard = card(context);
        content.addView(termsCard, block(16));
        termsCard.addView(Ui.caps(context, "ĐIỀU KIỆN VÀ ĐIỀU KHOẢN MUA VÀNG", 11f, Ui.TEXT_TITLE_DARK, 0.01f), wrap(14));
        TextView termsBody = Ui.text(context, TERMS_TEXT, 9.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        termsBody.setLineSpacing(0, 1.3f);
        ScrollView termsScroll = new ScrollView(context);
        termsScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        termsScroll.setVerticalScrollBarEnabled(false);
        termsScroll.setBackground(Ui.rect(Ui.SURFACE_WHITE, Ui.R_MD, Ui.BORDER_LIGHT, 1));
        termsScroll.setPadding(Ui.px(14), Ui.px(14), Ui.px(14), Ui.px(14));
        termsScroll.setClipToPadding(false);
        termsScroll.addView(termsBody, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams termsScrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(280));
        termsScrollLp.bottomMargin = Ui.px(16);
        termsCard.addView(termsScroll, termsScrollLp);

        // "(*) Bat buoc de su dung dich vu" + checkbox dong y PDPA (theo mockup "Dang ky tai khoan")
        TextView requiredNote = Ui.text(context, "(*) Bắt buộc để sử dụng dịch vụ", 8.5f, Ui.DOWN_RED, Ui.W_MEDIUM);
        termsCard.addView(requiredNote, wrap(10));
        LinearLayout consentRowTerms = new LinearLayout(context);
        consentRowTerms.setOrientation(LinearLayout.HORIZONTAL);
        consentRowTerms.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.CheckBox termsConsent = new android.widget.CheckBox(context);
        termsConsent.setChecked(true);
        termsConsent.setPadding(0, 0, 0, 0);
        termsConsent.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.BURGUNDY_PRIMARY));
        consentRowTerms.addView(termsConsent, new LinearLayout.LayoutParams(Ui.px(32), Ui.px(32)));
        TextView consentTextTerms = Ui.text(context,
                "Tôi đồng ý cho BTMH thu thập và xử lý dữ liệu định danh theo chính sách bảo mật (PDPA).",
                8.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        consentTextTerms.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams consentTextTermsLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        consentTextTermsLp.setMarginStart(Ui.px(8));
        consentRowTerms.addView(consentTextTerms, consentTextTermsLp);
        termsCard.addView(consentRowTerms, wrap(20));

        // "DANG KY BANG CCCD" / "DANG KY BANG VNEID": dong y dieu khoan VA chon luon phuong thuc -> vao thang man quet
        LinearLayout termsButtons = new LinearLayout(context);
        termsButtons.setOrientation(LinearLayout.HORIZONTAL);
        TextView registerCccd = Ui.primaryButton(context, "ĐĂNG KÝ BẰNG CCCD", 9.5f);
        registerCccd.setOnClickListener(v -> setState(State.WAITING));
        TextView registerVneid = Ui.primaryButton(context, "ĐĂNG KÝ BẰNG VNEID", 9.5f);
        registerVneid.setOnClickListener(v -> setState(State.WAITING));
        LinearLayout.LayoutParams tb1 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        tb1.rightMargin = Ui.px(8);
        LinearLayout.LayoutParams tb2 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        tb2.leftMargin = Ui.px(8);
        termsButtons.addView(registerCccd, tb1);
        termsButtons.addView(registerVneid, tb2);
        termsCard.addView(termsButtons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- Card "Huong dan" (chon phuong thuc xac thuc: VNeID hoac CCCD) ----
        hintCard = card(context);
        content.addView(hintCard, block(16));
        hintCard.addView(Ui.text(context, "Hướng dẫn", 12f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD), wrap(12));
        TextView hintBody = Ui.text(context,
                "Sử dụng giấy tờ gốc (bản chính) chính chủ, còn hạn.\nĐưa mặt trước thẻ Căn cước vào đầu đọc thẻ hoặc "
                        + "mở mã QR Căn cước trên VNeID trước đầu quét của Kiosk.",
                9.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        hintBody.setLineSpacing(0, 1.25f);
        hintCard.addView(hintBody, wrap(18));
        hintCard.addView(idDashedFrame(context, "Mặt trước hướng lên"), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(168)));
        LinearLayout consentRow = new LinearLayout(context);
        consentRow.setOrientation(LinearLayout.HORIZONTAL);
        consentRow.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.CheckBox consent = new android.widget.CheckBox(context);
        consent.setChecked(true);
        consent.setPadding(0, 0, 0, 0);
        consent.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.BURGUNDY_PRIMARY));
        LinearLayout.LayoutParams consentBoxLp = new LinearLayout.LayoutParams(Ui.px(32), Ui.px(32));
        consentRow.addView(consent, consentBoxLp);
        TextView consentText = Ui.text(context,
                "Tôi đồng ý cho BTMH thu thập và xử lý định danh theo chính sách bảo mật (PDPA). Xem chính sách",
                8.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        consentText.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams consentTextLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        consentTextLp.setMarginStart(Ui.px(8));
        consentRow.addView(consentText, consentTextLp);
        LinearLayout.LayoutParams consentRowLp = wrap(18);
        consentRowLp.topMargin = Ui.px(14);
        hintCard.addView(consentRow, consentRowLp);
        LinearLayout hintButtons = new LinearLayout(context);
        hintButtons.setOrientation(LinearLayout.HORIZONTAL);
        TextView useVneid = Ui.primaryButton(context, "SỬ DỤNG VNeID", 10f);
        useVneid.setOnClickListener(v -> setState(State.WAITING));
        TextView useCccd = Ui.primaryButton(context, "SỬ DỤNG CCCD", 10f);
        useCccd.setOnClickListener(v -> setState(State.WAITING));
        LinearLayout.LayoutParams hb1 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        hb1.rightMargin = Ui.px(8);
        LinearLayout.LayoutParams hb2 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        hb2.leftMargin = Ui.px(8);
        hintButtons.addView(useVneid, hb1);
        hintButtons.addView(useCccd, hb2);
        hintCard.addView(hintButtons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- Card "Quet the Can cuoc" (WAITING / VERIFYING) ----
        scanCard = card(context);
        content.addView(scanCard, block(16));
        scanCard.addView(Ui.text(context, "Quét thẻ Căn cước", 12f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD), wrap(8));
        scanCard.addView(instruction(context, "Đưa mặt trước của thẻ Căn cước vào đầu đọc thẻ."), wrap(16));

        FrameLayout scanFrame = new FrameLayout(context);
        GradientDrawable scanDashed = new GradientDrawable();
        scanDashed.setShape(GradientDrawable.RECTANGLE);
        scanDashed.setColor(Ui.rgb(250, 247, 241));
        scanDashed.setCornerRadius(Ui.px(Ui.R_MD));
        scanDashed.setStroke(Math.max(2, Ui.px(1.5f)), Ui.UP_GREEN, Ui.px(7), Ui.px(5));
        scanFrame.setBackground(scanDashed);
        scanFrame.addView(new IconView(context, IconView.ID_CARD, Ui.UP_GREEN).strokeUnits(1.4f),
                new FrameLayout.LayoutParams(Ui.px(72), Ui.px(72), Gravity.CENTER));
        scanRing = new QrStatusRingView(context);
        scanFrame.addView(scanRing, new FrameLayout.LayoutParams(Ui.px(96), Ui.px(96), Gravity.CENTER));
        scanFrame.setClickable(true);
        scanFrame.setOnClickListener(v -> {
            // Chi de xem giao dien khi debug: chua co dau doc that. Ban release khong lam gi.
            if (!BuildConfig.DEBUG) return;
            if (state == State.WAITING) setState(State.VERIFYING);
            else if (state == State.VERIFYING) setFailure(MSG_ID_NOT_FOUND);
        });
        LinearLayout.LayoutParams scanFrameLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(180));
        scanCard.addView(scanFrame, scanFrameLp);

        scanCaption = Ui.text(context, "Mặt trước hướng lên", 9.5f, Ui.UP_GREEN, Ui.W_BOLD);
        scanCaption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scLp = wrap(0);
        scLp.topMargin = Ui.px(14);
        scanCard.addView(scanCaption, scLp);
        scanHelper = Ui.text(context, "", 9f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        scanHelper.setGravity(Gravity.CENTER);
        scanHelper.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams shLp = wrap(0);
        shLp.topMargin = Ui.px(10);
        scanCard.addView(scanHelper, shLp);

        // Chu duoi vong tron (giu lai cho InsetAware/applyState khong vo khi tham chieu — hien khi VERIFYING)
        verifyInfo = new LinearLayout(context);
        verifyInfo.setVisibility(GONE);
        scanCard.addView(verifyInfo, new LinearLayout.LayoutParams(0, 0));

        // Nut: Thu lai / Tiep tuc (debug: vo hieu; release + co tam thoi: "Tiep tuc (chua dinh danh)" bam duoc)
        boolean passThrough = !BuildConfig.DEBUG && ALLOW_CONTINUE_WITHOUT_IDENTIFICATION;
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView retry = secondaryButton(context, "Thử lại");
        retry.setOnClickListener(v -> setState(State.WAITING));
        TextView next;
        if (passThrough) {
            next = secondaryButton(context, "Tiếp tục (chưa định danh)");
            next.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(10f));
            next.setOnClickListener(v -> proceed());
        } else {
            next = Ui.text(context, "Tiếp tục", 11f, Ui.TEXT_MUTED_LIGHT, Ui.W_BOLD);
            next.setGravity(Gravity.CENTER);
            next.setBackground(Ui.rect(Ui.FIELD_GREY, Ui.R_PILL, 0, 0));
            next.setEnabled(false); // chua dinh danh thi khong duoc di tiep
        }
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        b1.rightMargin = Ui.px(8);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, Ui.px(68), passThrough ? 1.7f : 1f);
        b2.leftMargin = Ui.px(8);
        buttons.addView(retry, b1);
        buttons.addView(next, b2);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = Ui.px(24);
        scanCard.addView(buttons, btnLp);
        if (passThrough) {
            TextView note = Ui.text(context,
                    "Thiết bị định danh chưa được kết nối — tạm thời cho phép tiếp tục.", 9.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM);
            note.setGravity(Gravity.CENTER);
            note.setLineSpacing(0, 1.15f);
            LinearLayout.LayoutParams noteLp = wrap(0);
            noteLp.topMargin = Ui.px(14);
            scanCard.addView(note, noteLp);
        }

        // ---- Card KH da dinh danh ----
        identifiedCard = card(context);
        content.addView(identifiedCard, block(16));
        buildIdentified(context);

        // ---- Card dinh danh het hieu luc ----
        expiredCard = card(context);
        content.addView(expiredCard, block(16));
        buildExpired(context);

        // ---- Popup "Thong bao" khi that bai (theo mockup "Xac thuc that bai" / "Xac thuc the Can cuoc, VNeID") ----
        failedCard = card(context);
        content.addView(failedCard, block(16));
        failedMessage = buildFailed(context);

        // ---- Card khach moi ----
        newCard = card(context);
        content.addView(newCard, block(16));
        // Theo spec sheet "Xác nhận thông tin khách hàng": cac truong duoc tu dong dien tu CCCD/QR VNeID that; vi App
        // Phase 1 chua co dau doc/may quet that nen de trong cho khach tu nhap tam, CHI de xem giao dien — khong luu.
        nameInput = pillInput(context, "Nhập họ và tên",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, EditorInfo.IME_ACTION_NEXT);
        idNumberInput = pillInput(context, "Nhập số CCCD", InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_ACTION_NEXT);
        dobInput = pillInput(context, "dd/mm/yyyy", InputType.TYPE_CLASS_DATETIME, EditorInfo.IME_ACTION_NEXT);
        addressInput = pillInput(context, "Nhập địa chỉ thường trú",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_NEXT);
        phoneInput = pillInput(context, "Nhập số điện thoại", InputType.TYPE_CLASS_PHONE, EditorInfo.IME_ACTION_NEXT);
        emailInput = pillInput(context, "Nhập email (không bắt buộc)",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_DONE);
        buildNewCustomer(context);

        // ---- Chi ban DEBUG: xem 3 ket qua ----
        if (BuildConfig.DEBUG) content.addView(buildDebugRow(context), block(20));

        ticker = ValueAnimator.ofFloat(0f, 1f);
        ticker.setDuration(TICK_MS);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(a -> scanRing.setPhase((float) a.getAnimatedValue()));

        applyState();
    }

    // ==================== API ====================

    public State getState() {
        return state;
    }

    public void setState(State newState) {
        state = newState == null ? State.WAITING : newState;
        applyState();
        scroll.scrollTo(0, 0);
    }

    /** Chuyen sang State.FAILED va hien dung noi dung thong bao (dung 1 trong cac hang MSG_* hoac text tuy chinh). */
    public void setFailure(String message) {
        failedMessage.setText(message == null || message.trim().isEmpty() ? MSG_FACE_NO_MATCH : message.trim());
        setState(State.FAILED);
    }

    /**
     * Thong tin khach da dinh danh — DE DANH cho tinh nang dinh danh that (FacePOS + CCCD + API hoi vien) dien vao. Hien tai khong
     * co nguon nao goi ham nay nen ca 5 dong hien "—". null/rong = "—".
     */
    public void setCustomer(String name, String memberCode, String tier, String points, String dailyLimit) {
        String[] v = {name, memberCode, tier, points, dailyLimit};
        for (int i = 0; i < customerValue.length; i++) {
            customerValue[i].setText(v[i] == null || v[i].trim().isEmpty() ? DASH : v[i].trim());
        }
    }

    /**
     * Thong tin nguoi nhan de dien sang man Xac nhan don hang ({@link OrderConfirmView#setCustomerInfo}) — lay theo
     * trang thai vua hoan tat: {@link State#IDENTIFIED} (demo co dinh, xem nut DEBUG) hoac {@link State#NEW_CUSTOMER}
     * (nguoi dung tu nhap). Tra ve {name, idNumber, phone, address}; null neu chua co gi de dien (WAITING/EXPIRED/FAILED).
     */
    public String[] getRecipientInfo() {
        if (state == State.NEW_CUSTOMER) {
            return new String[]{nameInput.getText().toString(), idNumberInput.getText().toString(),
                    phoneInput.getText().toString(), addressInput.getText().toString()};
        }
        if (state == State.IDENTIFIED) {
            return new String[]{customerValue[0].getText().toString(), identifiedIdNumber, identifiedPhone, identifiedAddress};
        }
        return null;
    }

    /** Dua man ve trang thai ban dau (goi moi lan mo): WAITING, xoa o nhap, bo ban phim. */
    public void reset() {
        phoneInput.setText("");
        nameInput.setText("");
        idNumberInput.setText("");
        dobInput.setText("");
        addressInput.setText("");
        emailInput.setText("");
        phoneInput.clearFocus();
        nameInput.clearFocus();
        idNumberInput.clearFocus();
        dobInput.clearFocus();
        addressInput.clearFocus();
        emailInput.clearFocus();
        hideKeyboard();
        setCustomer(null, null, null, null, null);
        identifiedIdNumber = identifiedPhone = identifiedAddress = null;
        setState(State.TERMS);
    }

    private void goBack() {
        hideKeyboard();
        listener.onBack();
    }

    private void proceed() {
        hideKeyboard();
        listener.onContinue();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    private void applyState() {
        boolean scanning = state == State.WAITING || state == State.VERIFYING;
        boolean verifying = state == State.VERIFYING;
        termsCard.setVisibility(state == State.TERMS ? VISIBLE : GONE);
        hintCard.setVisibility(state == State.HINT ? VISIBLE : GONE);
        scanCard.setVisibility(scanning ? VISIBLE : GONE);
        identifiedCard.setVisibility(state == State.IDENTIFIED ? VISIBLE : GONE);
        expiredCard.setVisibility(state == State.EXPIRED ? VISIBLE : GONE);
        newCard.setVisibility(state == State.NEW_CUSTOMER ? VISIBLE : GONE);
        failedCard.setVisibility(state == State.FAILED ? VISIBLE : GONE);

        headerTitle.setText(state == State.TERMS ? "Đăng ký tài khoản"
                : (state == State.HINT || scanning) ? "Xác thực thẻ Căn cước, VNeID"
                : "Xác thực khách hàng");
        outerStepper.setVisibility(state == State.TERMS ? GONE : VISIBLE);
        termsVerticalStepper.setVisibility(state == State.TERMS ? VISIBLE : GONE);

        if (scanning) {
            scanRing.setState(verifying ? QrStatusRingView.State.CHECKING : QrStatusRingView.State.NONE);
            scanCaption.setText(verifying ? "Hệ thống đang xử lý" : "Mặt trước hướng lên");
            scanCaption.setTextColor(Ui.UP_GREEN);
            scanHelper.setText("");
        }
        if (!scanning || state != State.NEW_CUSTOMER) {
            phoneInput.clearFocus();
            nameInput.clearFocus();
        }
        updateAnimation();
    }

    /** Chay hoat anh chi khi dang VERIFYING va man dang hien (tiet kiem pin, khong ro ri khi an/thoat). */
    private void updateAnimation() {
        if (ticker == null) return;
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

    // ==================== DUNG UI ====================

    private void buildIdentified(Context context) {
        identifiedCard.setGravity(Gravity.CENTER_HORIZONTAL);
        AvatarView avatar = new AvatarView(context);
        identifiedCard.addView(avatar, new LinearLayout.LayoutParams(Ui.px(120), Ui.px(120)));

        LinearLayout status = new LinearLayout(context);
        status.setOrientation(LinearLayout.HORIZONTAL);
        status.setGravity(Gravity.CENTER);
        status.addView(new IconView(context, IconView.CHECK, Ui.UP_GREEN).strokeUnits(2.4f),
                new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26)));
        TextView st = Ui.text(context, "Khách hàng đã định danh", 11f, Ui.UP_GREEN, Ui.W_BOLD);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.leftMargin = Ui.px(8);
        status.addView(st, stLp);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = Ui.px(14);
        sLp.bottomMargin = Ui.px(10);
        identifiedCard.addView(status, sLp);

        String[] labels = {"Họ tên", "Mã hội viên", "Hạng thẻ", "Điểm tích luỹ", "Hạn mức mua vàng trong ngày"};
        for (int i = 0; i < labels.length; i++) {
            View line = new View(context);
            line.setBackgroundColor(Ui.BORDER_LIGHT);
            identifiedCard.addView(line, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1))));
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = Ui.text(context, labels[i], 10f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView value = Ui.text(context, DASH, 11f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
            value.setGravity(Gravity.END);
            customerValue[i] = value;
            LinearLayout.LayoutParams vLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f);
            vLp.leftMargin = Ui.px(12);
            row.addView(value, vLp);
            row.setMinimumHeight(Ui.px(64));
            identifiedCard.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        TextView go = Ui.primaryButton(context, "Tiếp tục đặt hàng", 11f);
        go.setLetterSpacing(0.02f);
        go.setOnClickListener(v -> proceed());
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(72));
        goLp.topMargin = Ui.px(20);
        identifiedCard.addView(go, goLp);
    }

    private void buildExpired(Context context) {
        expiredCard.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout badge = new FrameLayout(context);
        badge.setBackground(Ui.oval(Ui.DOWN_TINT));
        badge.addView(new IconView(context, IconView.CLOCK, Ui.DOWN_RED).strokeUnits(2f),
                new FrameLayout.LayoutParams(Ui.px(48), Ui.px(48), Gravity.CENTER));
        expiredCard.addView(badge, new LinearLayout.LayoutParams(Ui.px(96), Ui.px(96)));

        TextView t = Ui.text(context, "Định danh hết hiệu lực", 12f, Ui.DOWN_RED, Ui.W_BOLD);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = wrap(0);
        tLp.topMargin = Ui.px(16);
        expiredCard.addView(t, tLp);
        TextView msg = Ui.text(context,
                "Giấy tờ định danh của quý khách đã hết hạn. Vui lòng liên hệ nhân viên tại quầy để cập nhật.",
                10.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        msg.setGravity(Gravity.CENTER);
        msg.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams mLp = wrap(0);
        mLp.topMargin = Ui.px(10);
        expiredCard.addView(msg, mLp);

        TextView backBtn = secondaryButton(context, "Quay lại");
        backBtn.setOnClickListener(v -> goBack());
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(72));
        bLp.topMargin = Ui.px(24);
        expiredCard.addView(backBtn, bLp);
    }

    /**
     * Popup "Thong bao" khi xac thuc that bai — theo mockup sheet "Xac thuc that bai" / "Xac thuc the Can cuoc, VNeID":
     * tieu de "Thong bao" + nut dong (X), noi dung loi, hai nut "VE TRANG CHU" (vien) / "DONG Y" (do ruou, thu lai).
     * Tra ve TextView noi dung de setFailure() doi text theo tung ma loi (E01-E15 trong Message List).
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

        TextView message = Ui.text(context, MSG_FACE_NO_MATCH, 10.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        message.setLineSpacing(0, 1.25f);
        failedCard.addView(message, wrap(22));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView home = secondaryButton(context, "VỀ TRANG CHỦ");
        home.setOnClickListener(v -> goBack());
        TextView agree = Ui.primaryButton(context, "ĐỒNG Ý", 11f);
        agree.setLetterSpacing(0.02f);
        agree.setOnClickListener(v -> setState(State.WAITING)); // thu lai: quay ve man quet
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

    private void buildNewCustomer(Context context) {
        newCard.addView(Ui.text(context, "Khách hàng mới", 12f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD), wrap(12));

        LinearLayout hint = new LinearLayout(context);
        hint.setOrientation(LinearLayout.HORIZONTAL);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setBackground(Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_MD, 0, 0));
        hint.setPadding(Ui.px(14), Ui.px(12), Ui.px(14), Ui.px(12));
        hint.addView(new IconView(context, IconView.CAMERA, Ui.BURGUNDY_PRIMARY).strokeUnits(1.8f),
                new LinearLayout.LayoutParams(Ui.px(34), Ui.px(34)));
        TextView ht = Ui.text(context, "Quét khuôn mặt để tạo hồ sơ khách hàng nhanh", 10f, Ui.TEXT_BODY_DARK, Ui.W_MEDIUM);
        ht.setLineSpacing(0, 1.15f);
        LinearLayout.LayoutParams htLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        htLp.leftMargin = Ui.px(12);
        hint.addView(ht, htLp);
        newCard.addView(hint, wrap(18));

        newCard.addView(Ui.caps(context, "HỌ VÀ TÊN", 8f, Ui.BURGUNDY_PRIMARY, 0.06f), wrap(6));
        newCard.addView(nameInput, fieldLp(16));
        newCard.addView(Ui.caps(context, "SỐ CCCD", 8f, Ui.BURGUNDY_PRIMARY, 0.06f), wrap(6));
        newCard.addView(idNumberInput, fieldLp(16));
        newCard.addView(Ui.caps(context, "NGÀY, THÁNG, NĂM SINH", 8f, Ui.BURGUNDY_PRIMARY, 0.06f), wrap(6));
        newCard.addView(dobInput, fieldLp(16));
        newCard.addView(Ui.caps(context, "ĐỊA CHỈ THƯỜNG TRÚ", 8f, Ui.BURGUNDY_PRIMARY, 0.06f), wrap(6));
        newCard.addView(addressInput, fieldLp(16));
        newCard.addView(Ui.caps(context, "SỐ ĐIỆN THOẠI", 8f, Ui.BURGUNDY_PRIMARY, 0.06f), wrap(6));
        newCard.addView(phoneInput, fieldLp(16));
        newCard.addView(Ui.caps(context, "EMAIL", 8f, Ui.BURGUNDY_PRIMARY, 0.06f), wrap(6));
        newCard.addView(emailInput, fieldLp(20));
        emailInput.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                emailInput.clearFocus();
                hideKeyboard();
                return true;
            }
            return false;
        });

        TextView create = Ui.primaryButton(context, "Tạo hồ sơ và tiếp tục", 11f);
        create.setLetterSpacing(0.02f);
        // Bat buoc dien du cac truong (tru email) truoc khi cho di tiep — tranh tao "ho so" trong khong (du Phase 1
        // chi la giao dien, khong luu/khong gui di dau).
        create.setOnClickListener(v -> {
            EditText[] required = {nameInput, idNumberInput, dobInput, addressInput, phoneInput};
            EditText firstEmpty = null;
            for (EditText f : required) {
                if (f.getText().toString().trim().isEmpty() && firstEmpty == null) firstEmpty = f;
            }
            if (firstEmpty != null) {
                firstEmpty.requestFocus();
                android.widget.Toast.makeText(context, "Vui lòng nhập đầy đủ thông tin", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            proceed();
        });
        newCard.addView(create, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(72)));
    }

    /** Hang nut xem thu 3 ket qua — chi tao o ban DEBUG (BuildConfig.DEBUG), release khong co. */
    private View buildDebugRow(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rect(0x00FFFFFF, Ui.R_MD, Ui.rgb(190, 175, 150), 1));
        box.setPadding(Ui.px(12), Ui.px(10), Ui.px(12), Ui.px(12));
        box.addView(Ui.text(context, "DEBUG — Mô phỏng (chỉ có ở bản debug):", 9f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM), wrap(8));
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Đã định danh", "Hết hạn", "Khách mới", "Thất bại"};
        State[] states = {State.IDENTIFIED, State.EXPIRED, State.NEW_CUSTOMER, State.FAILED};
        for (int i = 0; i < names.length; i++) {
            TextView b = Ui.text(context, names[i], 9.5f, Ui.TEXT_BODY_DARK, Ui.W_BOLD);
            b.setGravity(Gravity.CENTER);
            b.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BORDER_LIGHT, 1),
                    Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BORDER_LIGHT, 1)));
            Ui.pressEffect(b);
            final State s = states[i];
            b.setOnClickListener(v -> {
                // "Da dinh danh": dien du lieu demo ro rang khong phai nguoi that, chi de xem giao dien day du (DEBUG only).
                if (s == State.IDENTIFIED) {
                    setCustomer(DEMO_CUSTOMER_NAME, "DEMO-00123", "Vàng", "1.250", "50 chỉ");
                    identifiedIdNumber = "012345678912";
                    identifiedPhone = "0987654321";
                    identifiedAddress = "123 Đường Láng, Phường Đống Đa, Hà Nội";
                } else if (s == State.FAILED) {
                    // Bam nhieu lan de xoay het 15 ma loi E01-E14 + "so dinh danh da ton tai" (DEBUG only).
                    String msg = ALL_DEBUG_MESSAGES[debugMessageIndex % ALL_DEBUG_MESSAGES.length];
                    debugMessageIndex++;
                    setFailure("(" + debugMessageIndex + "/" + ALL_DEBUG_MESSAGES.length + ") " + msg);
                    return;
                }
                setState(s);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.px(64), 1f);
            if (i > 0) lp.leftMargin = Ui.px(8);
            row.addView(b, lp);
        }
        box.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(card);
        card.setPadding(Ui.px(20), Ui.px(22), Ui.px(20), Ui.px(20));
        return card;
    }

    /** Nut pill vien do ruou (nen trang, nhan = kem). */
    private static TextView secondaryButton(Context context, String label) {
        TextView b = Ui.outlineButton(context, label, 11f);
        b.setLetterSpacing(0f);
        b.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f)));
        return b;
    }

    /** O nhap dang pill (cung phong cach o tim kiem): cao 68, vien be, focus = vien do ruou. */
    private static EditText pillInput(Context context, String hint, int inputType, int imeAction) {
        EditText e = new EditText(context);
        e.setHint(hint);
        e.setHintTextColor(Ui.TEXT_MUTED_LIGHT);
        e.setTextColor(Ui.TEXT_TITLE_DARK);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(11f));
        e.setSingleLine(true);
        e.setIncludeFontPadding(false);
        e.setGravity(Gravity.CENTER_VERTICAL);
        e.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        e.setBackground(Ui.outline(Ui.R_PILL));
        e.setInputType(inputType);
        e.setImeOptions(imeAction);
        e.setOnFocusChangeListener((v, focused) -> v.setBackground(
                Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, focused ? Ui.BURGUNDY_PRIMARY : Ui.BORDER_LIGHT, focused ? 1.5f : 1)));
        return e;
    }

    private static LinearLayout.LayoutParams fieldLp(float bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(68));
        lp.bottomMargin = Ui.px(bottom);
        return lp;
    }

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

    /** Khung net dut xanh + icon CCCD + chu thich — dung cho man "Huong dan" (tinh, khong doi trang thai). */
    private static View idDashedFrame(Context context, String caption) {
        LinearLayout frame = new LinearLayout(context);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setGravity(Gravity.CENTER);
        GradientDrawable dashed = new GradientDrawable();
        dashed.setShape(GradientDrawable.RECTANGLE);
        dashed.setColor(Ui.rgb(250, 247, 241));
        dashed.setCornerRadius(Ui.px(Ui.R_MD));
        dashed.setStroke(Math.max(2, Ui.px(1.5f)), Ui.UP_GREEN, Ui.px(7), Ui.px(5));
        frame.setBackground(dashed);
        frame.addView(new IconView(context, IconView.ID_CARD, Ui.UP_GREEN).strokeUnits(1.4f),
                new LinearLayout.LayoutParams(Ui.px(64), Ui.px(64)));
        TextView t = Ui.text(context, caption, 9.5f, Ui.UP_GREEN, Ui.W_BOLD);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tLp = wrap(0);
        tLp.topMargin = Ui.px(10);
        frame.addView(t, tLp);
        return frame;
    }

    private static TextView instruction(Context context, String text) {
        TextView t = Ui.text(context, text, 10f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    /**
     * Stepper DOC rieng cho man "Dang ky tai khoan" (State.TERMS): 3 buoc con cua "Xac thuc khach hang", theo mockup
     * Figma — khac voi {@link #buildStepper} (3 buoc LON cua ca luong mua hang: Xac thuc/Xac nhan/Thanh toan).
     */
    private static LinearLayout buildVerticalStepper(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(card);
        card.setPadding(Ui.px(18), Ui.px(18), Ui.px(18), Ui.px(18));

        String[] titles = {"Xác thực thẻ Căn cước/VNeID", "Xác thực khuôn mặt", "Tạo tài khoản và xác nhận thông tin"};
        int railW = 40;
        for (int i = 0; i < titles.length; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            boolean active = i == 0;
            boolean last = i == titles.length - 1;

            // "Ray" ben trai: FrameLayout rong dung bang duong kinh vong tron, vong tron + duong noi deu CAN GIUA
            // trong do -> luon thang hang voi nhau du card co padding bao nhieu (khong con phai tinh margin tay).
            FrameLayout rail = new FrameLayout(context);
            TextView circle = Ui.text(context, String.valueOf(i + 1), 11f,
                    active ? Ui.SURFACE_WHITE : Ui.TEXT_MUTED_LIGHT, Ui.W_BOLD);
            circle.setGravity(Gravity.CENTER);
            circle.setBackground(Ui.oval(active ? Ui.BURGUNDY_PRIMARY : Ui.BORDER_LIGHT));
            rail.addView(circle, new FrameLayout.LayoutParams(Ui.px(railW), Ui.px(railW), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            if (!last) {
                View line = new View(context);
                line.setBackgroundColor(Ui.BORDER_LIGHT);
                FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(
                        Math.max(1, Ui.px(2)), Ui.px(36), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                lineLp.topMargin = Ui.px(railW);
                rail.addView(line, lineLp);
            }
            row.addView(rail, new LinearLayout.LayoutParams(Ui.px(railW), LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_VERTICAL);
            col.addView(Ui.text(context, "Bước " + (i + 1) + ":", 8.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView t = Ui.text(context, titles[i], 10.5f, active ? Ui.TEXT_TITLE_DARK : Ui.TEXT_MUTED_LIGHT, Ui.W_BOLD);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tLp.topMargin = Ui.px(2);
            col.addView(t, tLp);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, Ui.px(railW), 1f);
            colLp.setMarginStart(Ui.px(14));
            row.addView(col, colLp);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) rowLp.topMargin = Ui.px(8);
            card.addView(row, rowLp);
        }
        return card;
    }

    /** Thanh 3 buoc: Xac thuc khach hang (dang o day) -> Xac nhan nhan hang -> Thanh toan. */
    private static View buildStepper(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(card);
        card.setPadding(Ui.px(8), Ui.px(18), Ui.px(8), Ui.px(16));

        String[] titles = {"Xác thực khách hàng", "Xác nhận nhận hàng", "Thanh toán"};
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
            l.setBackgroundColor(Ui.BORDER_LIGHT);
            r.setBackgroundColor(i == 0 ? Ui.BURGUNDY_PRIMARY : Ui.BORDER_LIGHT);
            l.setVisibility(i == 0 ? INVISIBLE : VISIBLE);
            r.setVisibility(i == 2 ? INVISIBLE : VISIBLE);
            lines.addView(l, new LinearLayout.LayoutParams(0, lh, 1f));
            lines.addView(r, new LinearLayout.LayoutParams(0, lh, 1f));
            top.addView(lines, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, lh, Gravity.CENTER_VERTICAL));
            boolean active = i == 0;
            TextView circle = Ui.text(context, String.valueOf(i + 1), 11f,
                    active ? Ui.SURFACE_WHITE : Ui.TEXT_MUTED_LIGHT, Ui.W_BOLD);
            circle.setGravity(Gravity.CENTER);
            circle.setBackground(Ui.oval(active ? Ui.BURGUNDY_PRIMARY : Ui.BORDER_LIGHT));
            top.addView(circle, new FrameLayout.LayoutParams(Ui.px(44), Ui.px(44), Gravity.CENTER));
            col.addView(top, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(44)));

            TextView t = Ui.text(context, titles[i], 9f, active ? Ui.TEXT_TITLE_DARK : Ui.TEXT_MUTED_LIGHT, Ui.W_BOLD);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tLp.topMargin = Ui.px(10);
            tLp.leftMargin = Ui.px(4);
            tLp.rightMargin = Ui.px(4);
            col.addView(t, tLp);

            card.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        return card;
    }

    /** Avatar tron: hinh nguoi trung tinh (dau + vai) ve bang Canvas — KHONG phai anh khach that. */
    private static class AvatarView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF rect = new RectF();

        AvatarView(Context context) {
            super(context);
            stroke.setStyle(Paint.Style.STROKE);
        }

        @Override
        protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(getWidth(), getHeight()) / 2f - Ui.px(2);
            fill.setColor(Ui.rgb(243, 238, 230));
            c.drawCircle(cx, cy, r, fill);
            c.save();
            clip.reset();
            clip.addCircle(cx, cy, r, Path.Direction.CW);
            c.clipPath(clip);
            fill.setColor(Ui.rgb(190, 178, 166));
            c.drawCircle(cx, cy - r * 0.16f, r * 0.32f, fill);
            rect.set(cx - r * 0.70f, cy + r * 0.28f, cx + r * 0.70f, cy + r * 1.6f);
            c.drawOval(rect, fill);
            c.restore();
            stroke.setStrokeWidth(Math.max(2f, Ui.px(2.5f)));
            stroke.setColor(Ui.withAlpha(Ui.UP_GREEN, 0xFF));
            c.drawCircle(cx, cy, r, stroke);
        }
    }
}
