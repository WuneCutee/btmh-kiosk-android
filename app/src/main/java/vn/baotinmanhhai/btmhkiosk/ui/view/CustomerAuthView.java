package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import vn.btmh.ui.customviews.DashedCircleView;
import vn.baotinmanhhai.btmhkiosk.BuildConfig;
import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.databinding.FragmentHeaderBinding;
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

    public enum State { TERMS, HINT, CARD_SCAN, WAITING, VERIFYING, CONFIRM_INFO, IDENTIFIED, EXPIRED, NEW_CUSTOMER, FAILED }

    /** Trang thai con cua {@link State#CARD_SCAN} — theo dung UIState cua EnrollCardActivity.kt that. */
    private enum CardSubState { INTRO, SCANNING, SUCCESS, ERROR }

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

    private static final long TICK_MS = 1800;
    private static final String DASH = "—";

    private final Listener listener;
    private final LinearLayout header;
    private final View headerLogo;
    private final View headerBack;
    private final TextView headerTitle;
    private final ScrollView scroll;
    private final LinearLayout content;
    private final View outerStepper;
    private final LinearLayout termsRoot;
    private final LinearLayout termsCard;
    private final ScrollView termsBodyScroll;
    private final LinearLayout termsVerticalStepper;
    private final LinearLayout hintCard;
    // ---- activity_enroll_card.xml THAT (man "Quet the Can cuoc" / "Quet VNeID" - dung CHUNG 1 layout, chi doi
    // chu theo phuong thuc da chon o hintCard) ----
    private final LinearLayout cardScanCard;
    private final View cardProgress;
    private final TextView cardTitle;
    private final TextView cardSubtitle;
    private final TextView cardNote;
    private final DashedCircleView cardDashedCircle;
    private final ImageView cardIcon;
    private final TextView cardBoxMsg;
    private final TextView cardBottomMsg;
    private final ValueAnimator cardTicker;
    private String cardMethod = "CCCD"; // "CCCD" hoac "VNEID" - dat truoc khi setState(CARD_SCAN)
    private CardSubState cardSubState = CardSubState.INTRO;
    private final Handler cardHandler = new Handler(Looper.getMainLooper());
    // true = den WAITING/VERIFYING nay la tu luong dang ky (sau khi quet CCCD/VNeID thanh cong), nen xac thuc
    // khuon mat xong se sang State.CONFIRM_INFO; false = tu nut "Dang Nhap" o Home (luong cu, khong doi).
    private boolean registrationFlow = false;
    // ---- Man "Xac nhan thong tin" (State.CONFIRM_INFO) — Figma 1192:17621: 4 truong CHI DOC (dien san tu CCCD/
    // quet mat) + SDT/Email CHO SUA + nut "HOAN THANH". ----
    private final LinearLayout confirmInfoCard;
    private final EditText confirmPhoneInput;
    private final EditText confirmEmailInput;
    // "Alert" (Figma, Intent=Positive): toast "Tao Tai Khoan thanh cong!" noi tren goc phai man "Xac nhan thong
    // tin" khi vua vao tu luong dang ky - vien xanh, nen trang, icon check xanh, tu an sau vai giay.
    private final View successToast;
    private final LinearLayout scanCard;
    private final View scanProgress;
    private final View scanLogo;
    private final TextView scanRegisterBtn;
    private final View scanButtonsRow;
    private LinearLayout.LayoutParams scanRegisterLp;
    private LinearLayout.LayoutParams scanBtnLp;
    private final DashedCircleView dashedCircle;
    private final ImageView faceIdIcon;
    private final View scanAvatar;
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

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- Thanh dau: fragment_header.xml THAT (dung chung voi ShellView/OrderConfirmView/PaymentView/
        // CustomerConfirmView), CO DINH vi tri - khong con nhet logo do ruou vao chung voi no nhu truoc (logo
        // an/hien theo trang thai lam ca thanh dau bi "nhay" len xuong, vi header la 1 cot doc [logo, titleRow]:
        // logo GONE thi titleRow (va nut Home ben trong) bi day len tren, logo VISIBLE lai day xuong duoi).
        // activity_face_auth.xml/activity_enroll_term.xml THAT cung tach rieng: header luon o vi tri CO DINH tren
        // cung, logo (neu co) la 1 phan tu RIENG nam trong vung noi dung cuon ben duoi, khong lien quan gi header.
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        FragmentHeaderBinding headerBinding = FragmentHeaderBinding.inflate(LayoutInflater.from(context), header, false);
        View titleRow = headerBinding.getRoot();
        headerBack = headerBinding.ivBack;
        headerBack.setOnClickListener(v -> goBack());
        headerTitle = headerBinding.tvTitle;
        headerTitle.setText("Xác thực khách hàng");
        headerTitle.setOnClickListener(v -> goBack());
        headerBinding.ivHome.setOnClickListener(v -> goBack());
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

        // Logo do ruou: gio la 1 phan tu cua NOI DUNG cuon (khong con chung voi thanh dau co dinh ben tren nua -
        // xem ghi chu o header), an/hien KHONG anh huong vi tri header/nut Home.
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(Ui.px(196), Ui.px(68));
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        headerLogo = new LogoView(context, false).tint(Ui.BURGUNDY_PRIMARY);
        content.addView(headerLogo, logoLp);

        outerStepper = buildStepper(context);
        content.addView(outerStepper, block(0));

        // ---- Man "Dang ky tai khoan" (State.TERMS) — activity_enroll_term.xml THAT dung ConstraintLayout rieng,
        // KHONG nam trong 1 ScrollView toan trang nhu cac man khac: layoutStepper (wrap) + cardTerms (0dp, CHIEM
        // PHAN CON LAI giua stepper va layoutBottom, chi rieng van ban dieu khoan ben trong moi cuon) + layoutBottom
        // (wrap, LUON ghim duoi cung man hinh). Truoc day nhet ca cum nay vao chung `content` dang cuon toan trang
        // nen nut CCCD/VNeID chi thay duoc khi cuon het xuong, nhin nhu "bi cat" o day man hinh - sua lai dung
        // kien truc that: termsRoot la 1 nhanh RIENG cung cap do voi `scroll`, tu ban than no co the phan (card
        // dan + panel nut ghim day) nen nut LUON hien du khong cuon trang ngoai, khop dung hanh vi app goc.
        termsRoot = new LinearLayout(context);
        termsRoot.setOrientation(LinearLayout.VERTICAL);
        termsRoot.setPadding(Ui.px(10), Ui.px(8), Ui.px(10), Ui.px(24));
        termsRoot.setVisibility(GONE);
        column.addView(termsRoot, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        termsVerticalStepper = buildVerticalStepper(context);
        LinearLayout.LayoutParams stepperLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        termsRoot.addView(termsVerticalStepper, stepperLp);

        // activity_enroll_term.xml + EnrollTermActivity.kt THAT: nut "Dang ky" tren man quet luon mo thang vao
        // UIState.REGISTER (FLOW_01) - KHONG co buoc trung gian "KHONG DONG Y / DONG Y VA TIEP TUC" (layoutButtonsState1
        // CHI hien o UIState.TERM_ONLY, luong khac khong lien quan nut nay). REGISTER hien NGAY: stepper + the vien
        // (radius16dp, stroke2dp, mau #5B5B5B, NEN TRONG SUOT - set thang trong code Kotlin, khong phai card trang)
        // + checkbox + 2 nut CCCD/VNeID, khong co buoc bam "Dong y" nao ca.
        termsCard = new LinearLayout(context);
        termsCard.setOrientation(LinearLayout.VERTICAL);
        termsCard.setBackground(Ui.rect(0x00000000, 16f, 0xFF5B5B5B, 2f));
        termsCard.setPadding(Ui.px(14), Ui.px(16), Ui.px(14), Ui.px(16));
        LinearLayout.LayoutParams termsCardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        termsCardLp.topMargin = Ui.px(32);
        termsCardLp.bottomMargin = Ui.px(24);
        termsRoot.addView(termsCard, termsCardLp);
        termsCard.addView(Ui.text(context, "ĐIỀU KIỆN VÀ ĐIỀU KHOẢN MUA VÀNG", 22f, 0xFF000000, Ui.W_BOLD), wrap(16));
        TextView termsBody = Ui.text(context, TERMS_TEXT, 20f, 0xFF000000, Ui.W_REGULAR);
        termsBody.setLineSpacing(Ui.px(8), 1f);
        termsBodyScroll = new ScrollView(context);
        termsBodyScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        termsBodyScroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        termsBodyScroll.setClipToPadding(false);
        termsBodyScroll.addView(termsBody, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        termsCard.addView(termsBodyScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // layoutBottom that: tvNote + layoutCheckbox + layoutButtonsState2 — LUON wrap_content, ghim duoi cung.
        LinearLayout termsBottom = new LinearLayout(context);
        termsBottom.setOrientation(LinearLayout.VERTICAL);
        termsRoot.addView(termsBottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // tvNote that: 20sp inter_24pt_regular do #FF0000, KHONG in nghieng (XML khong co thuoc tinh italic).
        TextView requiredNote = Ui.text(context, "(*) Bắt buộc để sử dụng dịch vụ", 20f, 0xFFFF0000, Ui.W_REGULAR);
        termsBottom.addView(requiredNote, wrap(24));
        LinearLayout consentRowTerms = new LinearLayout(context);
        consentRowTerms.setOrientation(LinearLayout.HORIZONTAL);
        consentRowTerms.setGravity(Gravity.CENTER_VERTICAL);
        // layoutCheckbox that: VISIBLE ngay (UIState.REGISTER set VISIBLE tu dau, khong can bam gi de hien).
        android.widget.CheckBox termsConsent = new android.widget.CheckBox(context);
        termsConsent.setChecked(false); // cbAgree that: mac dinh CHUA tick
        termsConsent.setPadding(0, 0, 0, 0);
        termsConsent.setButtonTintList(android.content.res.ColorStateList.valueOf(0xFFAA1F23));
        consentRowTerms.addView(termsConsent, new LinearLayout.LayoutParams(Ui.px(32), Ui.px(32)));
        TextView consentTextTerms = Ui.text(context,
                "Tôi đồng ý cho BTMH thu thập và xử lý dữ liệu định danh theo chính sách bảo mật (PDPA).",
                20f, 0xFF1A1A1A, Ui.W_REGULAR);
        consentTextTerms.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams consentTextTermsLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        consentTextTermsLp.setMarginStart(Ui.px(16));
        consentRowTerms.addView(consentTextTerms, consentTextTermsLp);
        termsBottom.addView(consentRowTerms, wrap(24));

        // layoutButtonsState2 that: "DANG KY BANG CCCD" / "DANG KY BANG VNEID", 72dp cao, XML 24sp semibold.
        // Ui.primaryButton() dung W_BOLD + letterSpacing rong hon font Inter Semibold that nen 24sp bi tran xuong
        // 2 dong trong pill nua-be-rong - giam xuong 16f de dam bao 1 dong, day la thoa hiep duy nhat con lai.
        LinearLayout termsButtons = new LinearLayout(context);
        termsButtons.setOrientation(LinearLayout.HORIZONTAL);
        TextView registerCccd = Ui.primaryButton(context, "ĐĂNG KÝ BẰNG CCCD", 16f);
        // Da chon CCCD/VNeID ngay o day roi - KHONG can hoi lai lan nua o man Huong dan (SU DUNG VNEID/CCCD) nua,
        // bo qua thang State.HINT, vao thang man quet dung phuong thuc da chon.
        registerCccd.setOnClickListener(v -> { cardMethod = "CCCD"; setState(State.CARD_SCAN); });
        TextView registerVneid = Ui.primaryButton(context, "ĐĂNG KÝ BẰNG VNEID", 16f);
        registerVneid.setOnClickListener(v -> { cardMethod = "VNEID"; setState(State.CARD_SCAN); });
        LinearLayout.LayoutParams tb1 = new LinearLayout.LayoutParams(0, Ui.px(72), 1f);
        tb1.rightMargin = Ui.px(8);
        LinearLayout.LayoutParams tb2 = new LinearLayout.LayoutParams(0, Ui.px(72), 1f);
        tb2.leftMargin = Ui.px(8);
        termsButtons.addView(registerCccd, tb1);
        termsButtons.addView(registerVneid, tb2);
        termsBottom.addView(termsButtons, new LinearLayout.LayoutParams(
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
                "Tôi đồng ý cho BTMH thu thập và xử lý dữ liệu định danh theo chính sách bảo mật (PDPA). Xem chính sách",
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
        TextView useVneid = Ui.primaryButton(context, "SỬ DỤNG VNEID", 10f);
        useVneid.setOnClickListener(v -> { cardMethod = "VNEID"; setState(State.CARD_SCAN); });
        TextView useCccd = Ui.primaryButton(context, "SỬ DỤNG CCCD", 10f);
        useCccd.setOnClickListener(v -> { cardMethod = "CCCD"; setState(State.CARD_SCAN); });
        LinearLayout.LayoutParams hb1 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        hb1.rightMargin = Ui.px(8);
        LinearLayout.LayoutParams hb2 = new LinearLayout.LayoutParams(0, Ui.px(68), 1f);
        hb2.leftMargin = Ui.px(8);
        hintButtons.addView(useVneid, hb1);
        hintButtons.addView(useCccd, hb2);
        hintCard.addView(hintButtons, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // ---- Man "Quet the Can cuoc" / "Quet VNeID" (State.CARD_SCAN) — activity_enroll_card.xml THAT: progress
        // 3 doan (doan 1 active), tieu de + mo ta + [ghi chu do, chi VNeID], khung net dut xanh (DashedCircleView
        // + icon the), thong bao duoi. 1 layout DUNG CHUNG cho ca CCCD va VNeID, chi doi noi dung chu theo
        // cardMethod (giong het EnrollCardActivity.kt that - tvTitle/tvSubtitle/tvBoxMsg/tvBottomMsg deu la bien).
        cardScanCard = new LinearLayout(context);
        cardScanCard.setOrientation(LinearLayout.VERTICAL);
        content.addView(cardScanCard, block(16));

        LinearLayout progressRow = new LinearLayout(context);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        // Mau that tu dev-mode CSS Figma: doan active #AA1F23 (do), doan chua toi #969696 (xam) - khong phai
        // BURGUNDY_PRIMARY/BORDER_LIGHT (mau rieng cua app, khac voi 2 mau nay).
        View seg1 = new View(context);
        seg1.setBackground(Ui.rect(0xFFAA1F23, 100f, 0, 0));
        View seg2 = new View(context);
        seg2.setBackground(Ui.rect(0xFF969696, 100f, 0, 0));
        View seg3 = new View(context);
        seg3.setBackground(Ui.rect(0xFF969696, 100f, 0, 0));
        LinearLayout.LayoutParams segLp1 = new LinearLayout.LayoutParams(0, Ui.px(6), 1f);
        LinearLayout.LayoutParams segLp2 = new LinearLayout.LayoutParams(0, Ui.px(6), 1f);
        segLp2.setMarginStart(Ui.px(16));
        LinearLayout.LayoutParams segLp3 = new LinearLayout.LayoutParams(0, Ui.px(6), 1f);
        segLp3.setMarginStart(Ui.px(16));
        progressRow.addView(seg1, segLp1);
        progressRow.addView(seg2, segLp2);
        progressRow.addView(seg3, segLp3);
        cardProgress = progressRow;
        cardScanCard.addView(progressRow, wrap(32));

        cardTitle = Ui.text(context, "", 20f, 0xFF000000, Ui.W_BOLD);
        cardScanCard.addView(cardTitle, wrap(24));
        cardSubtitle = Ui.text(context, "", 16f, 0xFF000000, Ui.W_REGULAR);
        cardScanCard.addView(cardSubtitle, wrap(12));
        cardNote = Ui.text(context, "", 16f, 0xFFFF383C, Ui.W_REGULAR);
        cardNote.setLineSpacing(0, 1.2f);
        cardScanCard.addView(cardNote, wrap(12));

        LinearLayout dashedBox = new LinearLayout(context);
        dashedBox.setOrientation(LinearLayout.VERTICAL);
        dashedBox.setGravity(Gravity.CENTER);
        GradientDrawable dashedBg = new GradientDrawable();
        dashedBg.setShape(GradientDrawable.RECTANGLE);
        dashedBg.setColor(Ui.rgb(250, 247, 241));
        dashedBg.setCornerRadius(Ui.px(Ui.R_MD));
        dashedBg.setStroke(Math.max(2, Ui.px(2f)), Ui.UP_GREEN, Ui.px(10), Ui.px(7));
        dashedBox.setBackground(dashedBg);
        FrameLayout cardFrame = new FrameLayout(context);
        cardDashedCircle = new DashedCircleView(context);
        cardDashedCircle.setVisibility(GONE);
        cardFrame.addView(cardDashedCircle, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cardIcon = new ImageView(context);
        cardIcon.setImageResource(R.drawable.ic_card);
        cardFrame.addView(cardIcon, new FrameLayout.LayoutParams(Ui.px(110), Ui.px(110), Gravity.CENTER));
        dashedBox.addView(cardFrame, new LinearLayout.LayoutParams(Ui.px(240), Ui.px(240)));
        cardBoxMsg = Ui.text(context, "Mặt trước hướng lên", 18f, Ui.UP_GREEN, Ui.W_REGULAR);
        cardBoxMsg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cardBoxMsgLp = wrap(0);
        cardBoxMsgLp.topMargin = Ui.px(18);
        dashedBox.addView(cardBoxMsg, cardBoxMsgLp);
        // Phase 1 chua co dau doc CCCD/may quet VNeID that: cham vao khung de mo phong INTRO -> SCANNING (2s,
        // giong dung animator that cua EnrollCardActivity.kt) -> SUCCESS, CHI o ban DEBUG.
        dashedBox.setClickable(true);
        dashedBox.setOnClickListener(v -> {
            if (!BuildConfig.DEBUG) return;
            if (cardSubState == CardSubState.INTRO) startCardScanning();
        });
        LinearLayout.LayoutParams dashedBoxLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(380));
        dashedBoxLp.topMargin = Ui.px(32);
        dashedBoxLp.bottomMargin = Ui.px(32);
        cardScanCard.addView(dashedBox, dashedBoxLp);

        cardBottomMsg = Ui.text(context, "", 18f, 0xFF000000, Ui.W_REGULAR);
        cardBottomMsg.setGravity(Gravity.CENTER);
        cardBottomMsg.setLineSpacing(0, 1.2f);
        cardScanCard.addView(cardBottomMsg, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Chua co dau doc CCCD/may quet VNeID that (Phase 1): them 2 nut DEBUG rieng cho man nay de xem ngay giao
        // dien SUCCESS/ERROR ma khong can cham vao khung cho 2s - CHI o ban DEBUG, giong quy uoc buildDebugRow().
        if (BuildConfig.DEBUG) {
            LinearLayout cardDebugRow = new LinearLayout(context);
            cardDebugRow.setOrientation(LinearLayout.HORIZONTAL);
            TextView cardDebugSuccess = Ui.text(context, "DEBUG: Bước này thành công", 9.5f, Ui.TEXT_BODY_DARK, Ui.W_BOLD);
            cardDebugSuccess.setGravity(Gravity.CENTER);
            cardDebugSuccess.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BORDER_LIGHT, 1),
                    Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BORDER_LIGHT, 1)));
            Ui.pressEffect(cardDebugSuccess);
            cardDebugSuccess.setOnClickListener(v -> {
                cardHandler.removeCallbacksAndMessages(null);
                cardSubState = CardSubState.SUCCESS;
                applyCardSubState();
            });
            TextView cardDebugError = Ui.text(context, "DEBUG: Bước này thất bại", 9.5f, Ui.TEXT_BODY_DARK, Ui.W_BOLD);
            cardDebugError.setGravity(Gravity.CENTER);
            cardDebugError.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BORDER_LIGHT, 1),
                    Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BORDER_LIGHT, 1)));
            Ui.pressEffect(cardDebugError);
            cardDebugError.setOnClickListener(v -> {
                cardHandler.removeCallbacksAndMessages(null);
                cardSubState = CardSubState.ERROR;
                applyCardSubState();
            });
            LinearLayout.LayoutParams cdb1 = new LinearLayout.LayoutParams(0, Ui.px(64), 1f);
            cdb1.rightMargin = Ui.px(8);
            LinearLayout.LayoutParams cdb2 = new LinearLayout.LayoutParams(0, Ui.px(64), 1f);
            cdb2.leftMargin = Ui.px(8);
            cardDebugRow.addView(cardDebugSuccess, cdb1);
            cardDebugRow.addView(cardDebugError, cdb2);
            LinearLayout.LayoutParams cardDebugRowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardDebugRowLp.topMargin = Ui.px(24);
            cardScanCard.addView(cardDebugRow, cardDebugRowLp);
        }

        // Vong tron xoay (spinning) trong luc dang quet - giong het kieu ticker cua man "Xac thuc khuon mat" ben
        // duoi, de giua "dang xu ly" co hieu ung chuyen dong thay vi chi fill tinh.
        cardTicker = ValueAnimator.ofFloat(0f, 360f);
        cardTicker.setDuration(TICK_MS);
        cardTicker.setRepeatCount(ValueAnimator.INFINITE);
        cardTicker.setInterpolator(new LinearInterpolator());
        cardTicker.addUpdateListener(a -> cardDashedCircle.setRotationAngle((float) a.getAnimatedValue()));

        // ---- Man "Xac nhan thong tin" (State.CONFIRM_INFO) — Figma 1192:17621: sau khi quet CCCD/VNeID +
        // xac thuc khuon mat xong (luong dang ky), hien lai thong tin da doc duoc de khach kiem tra truoc khi
        // hoan tat. 4 truong dau CHI DOC (da dien tu CCCD/mat), rieng SDT + Email cho sua.
        confirmInfoCard = new LinearLayout(context);
        confirmInfoCard.setOrientation(LinearLayout.VERTICAL);
        content.addView(confirmInfoCard, block(16));

        LinearLayout confirmProgress = new LinearLayout(context);
        confirmProgress.setOrientation(LinearLayout.HORIZONTAL);
        View cp1 = new View(context);
        cp1.setBackground(Ui.rect(0xFFAA1F23, 100f, 0, 0));
        View cp2 = new View(context);
        cp2.setBackground(Ui.rect(0xFFAA1F23, 100f, 0, 0));
        View cp3 = new View(context);
        cp3.setBackground(Ui.rect(0xFF969696, 100f, 0, 0));
        LinearLayout.LayoutParams cpLp1 = new LinearLayout.LayoutParams(0, Ui.px(6), 1f);
        LinearLayout.LayoutParams cpLp2 = new LinearLayout.LayoutParams(0, Ui.px(6), 1f);
        cpLp2.setMarginStart(Ui.px(16));
        LinearLayout.LayoutParams cpLp3 = new LinearLayout.LayoutParams(0, Ui.px(6), 1f);
        cpLp3.setMarginStart(Ui.px(16));
        confirmProgress.addView(cp1, cpLp1);
        confirmProgress.addView(cp2, cpLp2);
        confirmProgress.addView(cp3, cpLp3);
        confirmInfoCard.addView(confirmProgress, wrap(32));

        String[] readonlyLabels = {"Người nhận", "Số căn cước công dân/Hộ chiếu", "Ngày, tháng, năm sinh", "Địa chỉ thường trú"};
        String[] readonlyDemo = {CustomerAuthView.DEMO_CUSTOMER_NAME, "099999999999", "09/09/1999", "123 Đường Láng, Phường Đống Đa, Hà Nội"};
        for (int i = 0; i < readonlyLabels.length; i++) {
            confirmInfoCard.addView(Ui.text(context, readonlyLabels[i], 11f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD), wrap(8));
            EditText ro = pillInput(context, "", 0, EditorInfo.IME_ACTION_NEXT);
            ro.setText(readonlyDemo[i]);
            ro.setEnabled(false);
            ro.setTextColor(Ui.TEXT_BODY_DARK);
            ro.setBackground(Ui.rect(0xFFE9E9E9, Ui.R_PILL, 0, 0));
            confirmInfoCard.addView(ro, fieldLp(16));
        }
        TextView phoneLabel = Ui.text(context, "* Số điện thoại", 11f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        confirmInfoCard.addView(phoneLabel, wrap(8));
        confirmPhoneInput = pillInput(context, "099999999", InputType.TYPE_CLASS_PHONE, EditorInfo.IME_ACTION_NEXT);
        confirmInfoCard.addView(confirmPhoneInput, fieldLp(16));
        confirmInfoCard.addView(Ui.text(context, "Email", 11f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD), wrap(8));
        confirmEmailInput = pillInput(context, "email@vidu.com",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, EditorInfo.IME_ACTION_DONE);
        confirmInfoCard.addView(confirmEmailInput, fieldLp(8));

        TextView confirmNote = Ui.text(context, "Vui lòng kiểm tra và cập nhật thông tin (nếu có)", 11f, Ui.BURGUNDY_PRIMARY, Ui.W_REGULAR);
        confirmNote.setTypeface(confirmNote.getTypeface(), android.graphics.Typeface.ITALIC);
        confirmInfoCard.addView(confirmNote, wrap(20));

        TextView complete = Ui.primaryButton(context, "HOÀN THÀNH", 24f);
        complete.setOnClickListener(v -> proceed());
        confirmInfoCard.addView(complete, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(72)));

        // "Alert" Intent=Positive (Figma 1192:17621): toast noi goc tren-phai man "Xac nhan thong tin", de len
        // tren ca thanh dau — vien xanh 1.5dp, nen trang, bo goc 16dp, icon check xanh + chu xam #666666 16sp.
        LinearLayout toastRow = new LinearLayout(context);
        toastRow.setOrientation(LinearLayout.HORIZONTAL);
        toastRow.setGravity(Gravity.CENTER_VERTICAL);
        toastRow.setBackground(Ui.rect(Ui.SURFACE_WHITE, 16f, Ui.UP_GREEN, 1.5f));
        toastRow.setPadding(Ui.px(16), Ui.px(14), Ui.px(20), Ui.px(14));
        toastRow.addView(new IconView(context, IconView.CHECK, Ui.UP_GREEN).strokeUnits(2.2f),
                new LinearLayout.LayoutParams(Ui.px(24), Ui.px(24)));
        TextView toastText = Ui.text(context, "Tạo Tài Khoản thành công!", 16f, 0xFF666666, Ui.W_REGULAR);
        LinearLayout.LayoutParams toastTextLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        toastTextLp.setMarginStart(Ui.px(12));
        toastRow.addView(toastText, toastTextLp);
        successToast = toastRow;
        successToast.setVisibility(GONE);
        Ui.softShadow(toastRow);
        LayoutParams toastLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        toastLp.gravity = Gravity.TOP | Gravity.END;
        toastLp.topMargin = Ui.px(35);
        toastLp.rightMargin = Ui.px(19);
        addView(successToast, toastLp);

        // ---- "Xac thuc khuon mat" (WAITING / VERIFYING) — dung CHUNG scanCard cho 2 luong: (a) bam "Dang Nhap"
        // o Home (Figma 850:12944: khong header back/title, co logo+2 nut), (b) Buoc 2 cua luong dang ky sau khi
        // quet CCCD/VNeID (Figma "Man hinh quet mat_default": CO header back/title, CO thanh tien trinh 3 doan,
        // KHONG co logo, CHI 1 nut "BAT DAU QUET" day man). Hai Figma deu thong nhat vong tron 330dp/icon 86dp
        // (khac voi 640dp/114dp cua activity_face_auth.xml that - uu tien Figma vi la ban moi hon, da duoc xac
        // nhan qua nhieu node). applyState() se bat/tat tung phan theo registrationFlow.
        scanCard = new LinearLayout(context);
        scanCard.setOrientation(LinearLayout.VERTICAL);
        scanCard.setGravity(Gravity.CENTER_HORIZONTAL);
        scanCard.setPadding(Ui.px(32), 0, Ui.px(32), 0); // header_padding_h
        content.addView(scanCard, block(16));

        // Thanh tien trinh 3 doan (doan 1+2 do #AA1F23 - "Buoc 2" dang xac thuc khuon mat, doan 3 xam #969696) —
        // CHI hien khi registrationFlow (luong dang ky), thay the outerStepper/headerLogo bi an luc scanning.
        LinearLayout scanProgressRow = new LinearLayout(context);
        scanProgressRow.setOrientation(LinearLayout.HORIZONTAL);
        View sp1 = new View(context);
        sp1.setBackground(Ui.rect(0xFFAA1F23, 100f, 0, 0));
        View sp2 = new View(context);
        sp2.setBackground(Ui.rect(0xFFAA1F23, 100f, 0, 0));
        View sp3 = new View(context);
        sp3.setBackground(Ui.rect(0xFF969696, 100f, 0, 0));
        LinearLayout.LayoutParams spLp1 = new LinearLayout.LayoutParams(0, Ui.px(6), 1f);
        LinearLayout.LayoutParams spLp2 = new LinearLayout.LayoutParams(0, Ui.px(6), 1f);
        spLp2.setMarginStart(Ui.px(16));
        LinearLayout.LayoutParams spLp3 = new LinearLayout.LayoutParams(0, Ui.px(6), 1f);
        spLp3.setMarginStart(Ui.px(16));
        scanProgressRow.addView(sp1, spLp1);
        scanProgressRow.addView(sp2, spLp2);
        scanProgressRow.addView(sp3, spLp3);
        scanProgress = scanProgressRow;
        LinearLayout.LayoutParams scanProgressLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(6));
        scanProgressLp.topMargin = Ui.px(24);
        scanCard.addView(scanProgressRow, scanProgressLp);

        ImageView logoImg = new ImageView(context);
        logoImg.setAdjustViewBounds(true);
        logoImg.setImageResource(R.drawable.logo_with_text);
        scanLogo = logoImg;
        LinearLayout.LayoutParams scanLogoLp = new LinearLayout.LayoutParams(
                Ui.px(376), LinearLayout.LayoutParams.WRAP_CONTENT);
        scanLogoLp.topMargin = Ui.px(16);
        scanCard.addView(logoImg, scanLogoLp);

        FrameLayout circleFrame = new FrameLayout(context);
        dashedCircle = new DashedCircleView(context);
        circleFrame.addView(dashedCircle, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        ImageView faceIdIcon = new ImageView(context);
        faceIdIcon.setImageResource(R.drawable.ic_face_id);
        this.faceIdIcon = faceIdIcon;
        circleFrame.addView(faceIdIcon, new FrameLayout.LayoutParams(Ui.px(86), Ui.px(86), Gravity.CENTER));
        // Figma 1192:17576 ("Xac thuc khuon mat" dang VERIFYING): icon FaceID nho o giua duoc thay bang anh
        // camera dang quet (vien xanh, lap day gan het vong tron) - Phase 1 chua co camera that nen dung lai
        // AvatarView (silhouette ve san, da dung o the "Da dinh danh") de mo phong, thay vi 1 icon tinh nho.
        scanAvatar = new AvatarView(context);
        scanAvatar.setVisibility(GONE);
        circleFrame.addView(scanAvatar, new FrameLayout.LayoutParams(Ui.px(270), Ui.px(270), Gravity.CENTER));
        circleFrame.setClickable(true);
        circleFrame.setOnClickListener(v -> {
            // Chi de xem giao dien khi debug: chua co camera that. Ban release khong lam gi (nut that cung
            // chua noi gi trong app chuan - xem FaceAuthActivity.kt, btnRegister/btnScan khong co listener).
            if (!BuildConfig.DEBUG) return;
            if (state == State.WAITING) setState(State.VERIFYING);
            else if (state == State.VERIFYING) setFailure(MSG_ID_NOT_FOUND);
        });
        LinearLayout.LayoutParams circleFrameLp = new LinearLayout.LayoutParams(Ui.px(330), Ui.px(330));
        circleFrameLp.topMargin = Ui.px(32);
        scanCard.addView(circleFrame, circleFrameLp);

        // scanCaption = tvInstruction that (20sp dung Figma); scanHelper = tvExtraMessage that (an mac dinh).
        scanCaption = Ui.text(context, "", 20f, Color.BLACK, Ui.W_REGULAR);
        scanCaption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scLp.topMargin = Ui.px(24);
        scanCard.addView(scanCaption, scLp);

        scanHelper = Ui.text(context, "Còn 1 lần quét", 24f, 0xFFFF3333, Ui.W_REGULAR);
        scanHelper.setVisibility(GONE);
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        shLp.gravity = Gravity.CENTER_HORIZONTAL;
        shLp.bottomMargin = Ui.px(42);
        scanCard.addView(scanHelper, shLp);

        // btnContainer: luong "Dang Nhap" don thuan = 2 nut nua-be-rong 72dp (XML that); luong dang ky (Buoc 2) =
        // CHI 1 nut "BAT DAU QUET" day man 56dp (Figma "Man hinh quet mat_default") - applyState() doi LayoutParams
        // + an/hien nut ĐĂNG KÝ tuy theo registrationFlow.
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        scanButtonsRow = buttons;
        TextView register = Ui.outlineButton(context, "ĐĂNG KÝ", 24f);
        register.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f)));
        scanRegisterBtn = register;
        // activity_enroll_term.xml THAT: bam DANG KY tu man quet mo stepper 3 buoc + the dieu khoan (State.TERMS,
        // da dung gia tri checkbox/nut CCCD-VNeID that san co ben duoi) - truoc day set lai WAITING nen bam khong
        // co tac dung gi.
        register.setOnClickListener(v -> setState(State.TERMS));
        TextView scanBtn = Ui.primaryButton(context, "BẮT ĐẦU QUÉT", 24f);
        scanBtn.setOnClickListener(v -> {
            if (state == State.WAITING) setState(State.VERIFYING);
            else if (state == State.VERIFYING) setFailure(MSG_ID_NOT_FOUND);
        });
        scanRegisterLp = new LinearLayout.LayoutParams(0, Ui.px(72), 1f);
        scanRegisterLp.rightMargin = Ui.px(12);
        scanBtnLp = new LinearLayout.LayoutParams(0, Ui.px(72), 1f);
        scanBtnLp.leftMargin = Ui.px(12);
        buttons.addView(register, scanRegisterLp);
        buttons.addView(scanBtn, scanBtnLp);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = Ui.px(24);
        btnLp.bottomMargin = Ui.px(24);
        scanCard.addView(buttons, btnLp);

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

        ticker = ValueAnimator.ofFloat(0f, 360f);
        ticker.setDuration(TICK_MS);
        ticker.setRepeatCount(ValueAnimator.INFINITE);
        ticker.setInterpolator(new LinearInterpolator());
        ticker.addUpdateListener(a -> dashedCircle.setRotationAngle((float) a.getAnimatedValue()));

        applyState();
    }

    // ==================== API ====================

    public State getState() {
        return state;
    }

    public void setState(State newState) {
        state = newState == null ? State.WAITING : newState;
        if (state == State.CARD_SCAN) {
            cardHandler.removeCallbacksAndMessages(null);
            cardTicker.cancel();
            cardSubState = CardSubState.INTRO;
        }
        if (state == State.VERIFYING && registrationFlow) {
            // Luong dang ky (vua quet CCCD/VNeID xong): xac thuc khuon mat "thanh cong" sau TICK_MS roi sang
            // man "Xac nhan thong tin" - luong "Dang Nhap" don thuan (registrationFlow=false) khong doi, giu
            // nguyen hanh vi cu (cham lai de mo phong that bai, demo DEBUG).
            cardHandler.postDelayed(() -> {
                if (state == State.VERIFYING && registrationFlow) setState(State.CONFIRM_INFO);
            }, TICK_MS);
        }
        if (state == State.CONFIRM_INFO && registrationFlow) {
            // "Alert" Intent=Positive (Figma): bao "Tao Tai Khoan thanh cong!" khi vua vao man nay tu luong dang
            // ky, tu an sau 3s.
            successToast.setVisibility(VISIBLE);
            cardHandler.postDelayed(() -> successToast.setVisibility(GONE), 3000);
        }
        applyState();
        scroll.scrollTo(0, 0);
        if (state == State.TERMS) termsBodyScroll.scrollTo(0, 0);
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
        if (state == State.CONFIRM_INFO) {
            return new String[]{DEMO_CUSTOMER_NAME, "099999999999", confirmPhoneInput.getText().toString(),
                    "123 Đường Láng, Phường Đống Đa, Hà Nội"};
        }
        return null;
    }

    /**
     * Dua man ve trang thai ban dau khi mo tu nut "Dang nhap" o Home: theo dung Figma "Man hinh dang nhap"
     * (node 850:12944) - vao THANG man quet khuon mat (WAITING), KHONG qua "Dang ky tai khoan"/dieu khoan
     * truoc (do la luong rieng cho khach moi, bam "DANG KY" tu chinh man quet nay moi toi). Truoc day goi
     * nham setState(TERMS) nen bam "Dang Nhap" lai hien giao dien "Dang ky tai khoan" sai thiet ke.
     */
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
        registrationFlow = false; // nut "Dang Nhap" o Home luon la luong don thuan, khong phai dang ky
        successToast.setVisibility(GONE);
        setState(State.WAITING);
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
        boolean terms = state == State.TERMS;
        // termsRoot la nhanh RIENG ngoai `scroll` (xem ghi chu o noi khoi tao) - chi 1 trong 2 duoc hien tai 1 thoi
        // diem, khong con can toggle rieng termsCard/termsVerticalStepper ben trong no nua (luon hien cung termsRoot).
        termsRoot.setVisibility(terms ? VISIBLE : GONE);
        scroll.setVisibility(terms ? GONE : VISIBLE);
        hintCard.setVisibility(state == State.HINT ? VISIBLE : GONE);
        cardScanCard.setVisibility(state == State.CARD_SCAN ? VISIBLE : GONE);
        scanCard.setVisibility(scanning ? VISIBLE : GONE);
        confirmInfoCard.setVisibility(state == State.CONFIRM_INFO ? VISIBLE : GONE);
        identifiedCard.setVisibility(state == State.IDENTIFIED ? VISIBLE : GONE);
        expiredCard.setVisibility(state == State.EXPIRED ? VISIBLE : GONE);
        newCard.setVisibility(state == State.NEW_CUSTOMER ? VISIBLE : GONE);
        failedCard.setVisibility(state == State.FAILED ? VISIBLE : GONE);

        headerTitle.setText(terms ? "Đăng ký tài khoản"
                : state == State.HINT || state == State.CARD_SCAN ? "Xác thực thẻ Căn cước, VNeID"
                : scanning ? "Xác thực khuôn mặt"
                : state == State.CONFIRM_INFO ? "Xác nhận thông tin"
                : "Xác thực khách hàng");
        if (state == State.CARD_SCAN) applyCardMethodText();
        // Figma "Man hinh dang nhap" (node 850:12944): man quet mo tu nut "Dang nhap" o Home KHONG co stepper
        // 3 buoc, khong co mui ten quay lai/tieu de - chi 1 nut Home don o goc phai, logo_with_text to nam
        // duoi no (da co san trong scanCard). An bot phan "thanh dau" chung nay de khop dung thiet ke khi quet.
        // activity_enroll_card.xml THAT: man "Quet the Can cuoc/VNeID" KHONG co thanh "Xac thuc khach hang/Xac
        // nhan nhan hang/Thanh toan" dang the tron+chu nay - chi co 1 thanh tien trinh 3 doan mong (cardProgress,
        // da ve rieng ben trong cardScanCard). An outerStepper o CARD_SCAN/CONFIRM_INFO (co thanh tien trinh rieng).
        outerStepper.setVisibility(scanning || state == State.CARD_SCAN || state == State.CONFIRM_INFO ? GONE : VISIBLE);
        headerLogo.setVisibility(scanning || state == State.CARD_SCAN || state == State.CONFIRM_INFO ? GONE : VISIBLE);
        // Figma "Man hinh quet mat_default" (Buoc 2 luong dang ky, sau khi quet CCCD/VNeID) KHAC voi Figma
        // "Man hinh dang nhap" (850:12944, bam thang "Dang Nhap" o Home): ban dang ky CO mui ten quay lai + tieu
        // de + thanh tien trinh 3 doan, KHONG co logo; ban dang nhap don thuan thi nguoc lai (co logo, khong co
        // back/tieu de/tien trinh). scanRegisterBtn (nut "DANG KY") chi hien o ban dang nhap don thuan.
        boolean scanRegFlow = scanning && registrationFlow;
        headerBack.setVisibility(!scanning || registrationFlow ? VISIBLE : GONE);
        headerTitle.setVisibility(!scanning || registrationFlow ? VISIBLE : GONE);
        scanProgress.setVisibility(scanRegFlow ? VISIBLE : GONE);
        scanLogo.setVisibility(scanning && !registrationFlow ? VISIBLE : GONE);
        scanRegisterBtn.setVisibility(scanRegFlow ? GONE : VISIBLE);
        if (scanning) {
            scanCaption.setText(registrationFlow
                    ? "Vui lòng nhìn thẳng và giữ khuôn mặt vào giữa khung hình."
                    : "Vui lòng nhìn thẳng và giữ khuôn mặt vào giữa khung hình hoặc đăng ký tài khoản nếu là khách hàng mới.");
            // Luong dang ky: 1 nut "BAT DAU QUET" day man 56dp cao (Figma); luong dang nhap don thuan: 2 nut
            // nua-be-rong 72dp cao (XML that) - doi lai LayoutParams moi lan vao/roi trang thai scanning.
            scanRegisterLp.width = 0;
            scanRegisterLp.weight = registrationFlow ? 0 : 1;
            scanBtnLp.width = registrationFlow ? LinearLayout.LayoutParams.MATCH_PARENT : 0;
            scanBtnLp.weight = registrationFlow ? 0 : 1;
            scanBtnLp.height = Ui.px(registrationFlow ? 56 : 72);
            scanBtnLp.leftMargin = registrationFlow ? 0 : Ui.px(12);
            scanButtonsRow.requestLayout();
            // dashedCircleView that: WAITING = tinh (den), VERIFYING = quay (isSpinningMode, xem ticker).
            dashedCircle.setError(false);
            dashedCircle.setSuccess(false);
            dashedCircle.setSpinningMode(verifying);
            scanHelper.setVisibility(GONE);
            // Figma 1192:17576: dang VERIFYING hien anh "camera" (AvatarView mo phong) thay icon FaceID tinh.
            faceIdIcon.setVisibility(verifying ? GONE : VISIBLE);
            scanAvatar.setVisibility(verifying ? VISIBLE : GONE);
        }
        if (!scanning || state != State.NEW_CUSTOMER) {
            phoneInput.clearFocus();
            nameInput.clearFocus();
        }
        if (state == State.CARD_SCAN) applyCardSubState();
        updateAnimation();
    }

    /** activity_enroll_card.xml THAT: tvTitle/tvSubtitle/tvNote/tvBottomMsg doi theo cardMethod ("CCCD"/"VNEID"). */
    private void applyCardMethodText() {
        boolean vneid = "VNEID".equals(cardMethod);
        cardTitle.setText(vneid ? "Quét VNeID" : "Quét thẻ Căn cước");
        cardSubtitle.setText(vneid
                ? "Mở QR trên VNeID và đặt trước đầu quét để đọc thông tin."
                : "Đưa mặt trước của thẻ Căn cước vào đầu đọc thẻ.");
        cardNote.setVisibility(vneid ? VISIBLE : GONE);
        if (vneid) {
            cardNote.setText("Lưu ý: Trong trường hợp xác thực bằng VNeID, khi nhận hàng quý khách vui lòng xuất "
                    + "trình thẻ Căn cước.");
        }
    }

    /** Mo phong INTRO -> SCANNING (2s, dung animator) -> SUCCESS — CHI goi khi BuildConfig.DEBUG (chua co dau doc that). */
    private void startCardScanning() {
        cardSubState = CardSubState.SCANNING;
        applyCardSubState();
        cardTicker.start();
        cardHandler.postDelayed(() -> {
            if (state == State.CARD_SCAN && cardSubState == CardSubState.SCANNING) {
                cardSubState = CardSubState.SUCCESS;
                applyCardSubState();
            }
        }, 2000);
    }

    /** activity_enroll_card.xml THAT: 4 trang thai INTRO/SCANNING/SUCCESS/ERROR cua EnrollCardActivity.kt. */
    private void applyCardSubState() {
        boolean vneid = "VNEID".equals(cardMethod);
        switch (cardSubState) {
            case INTRO:
                cardDashedCircle.setVisibility(GONE);
                cardIcon.setVisibility(VISIBLE);
                cardBoxMsg.setVisibility(VISIBLE);
                cardBoxMsg.setText("Mặt trước hướng lên");
                cardBoxMsg.setTextColor(Ui.UP_GREEN);
                cardBottomMsg.setText(vneid
                        ? "Vui lòng đặt căn cước trước đầu quét."
                        : "Vui lòng đưa căn cước vào đầu đọc theo đúng hướng dẫn.");
                cardBottomMsg.setTextColor(0xFF000000);
                break;
            case SCANNING:
                cardDashedCircle.setNumDashes(50);
                cardDashedCircle.setError(false);
                cardDashedCircle.setSuccess(false);
                cardDashedCircle.setSpinningMode(true);
                cardDashedCircle.setVisibility(VISIBLE);
                cardIcon.setVisibility(VISIBLE);
                cardBoxMsg.setVisibility(VISIBLE);
                cardBoxMsg.setText("Hệ thống đang xử lý");
                cardBoxMsg.setTextColor(0xFF000000);
                cardBottomMsg.setText("Đang đọc dữ liệu thẻ...");
                cardBottomMsg.setTextColor(0xFF000000);
                break;
            case SUCCESS:
                cardTicker.cancel();
                cardDashedCircle.setSpinningMode(false);
                cardDashedCircle.setError(false); // reset neu truoc do da bam "That bai" roi moi bam "Thanh cong"
                cardDashedCircle.setSuccess(true);
                cardDashedCircle.setProgress(100f);
                cardDashedCircle.setVisibility(VISIBLE);
                cardIcon.setVisibility(GONE);
                cardBoxMsg.setVisibility(GONE);
                cardBottomMsg.setText("Đọc thẻ thành công!\nVui lòng lấy thẻ ra khỏi đầu đọc.");
                cardBottomMsg.setTextColor(Ui.UP_GREEN);
                // Quet CCCD/VNeID xong quay lai man "Xac thuc khuon mat" (Buoc 2 trong stepper) de hoan tat dinh
                // danh - cho 1.4s de khach kip thay thong bao thanh cong truoc khi tu chuyen man.
                cardHandler.postDelayed(() -> {
                    if (state == State.CARD_SCAN && cardSubState == CardSubState.SUCCESS) {
                        registrationFlow = true;
                        setState(State.WAITING);
                    }
                }, 1400);
                break;
            case ERROR:
                cardTicker.cancel();
                cardDashedCircle.setSpinningMode(false);
                cardDashedCircle.setSuccess(false); // reset neu truoc do da bam "Thanh cong" roi moi bam "That bai"
                cardDashedCircle.setError(true);
                cardDashedCircle.setProgress(100f);
                cardDashedCircle.setVisibility(VISIBLE);
                cardIcon.setVisibility(GONE);
                cardBoxMsg.setVisibility(GONE);
                cardBottomMsg.setText("Đọc thẻ không thành công.\nVui lòng thử lại.");
                cardBottomMsg.setTextColor(0xFFFF0000);
                break;
        }
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
        cardTicker.cancel();
        cardHandler.removeCallbacksAndMessages(null);
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
        header.setPadding(0, top, 0, Ui.px(4)); // fragment_header tu lo 56/32 -> nut Home cung vi tri moi man
        content.setPadding(Ui.px(16), Ui.px(8), Ui.px(16), Ui.px(24) + bottom);
        termsRoot.setPadding(Ui.px(16), Ui.px(8), Ui.px(16), Ui.px(24) + bottom);
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
                KioskToast.error(context, "Vui lòng nhập đầy đủ thông tin");
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
     * Stepper DOC rieng cho man "Dang ky tai khoan" (State.TERMS): 3 buoc con cua "Xac thuc khach hang". DUNG so
     * lieu THAT tu activity_enroll_term.xml (layoutStepper) — KHONG boc the trang/bong do (XML khong co background
     * cho ConstraintLayout nay, nam THANG tren nen gradient trang nhu tvNote/checkbox ben duoi).
     */
    private static LinearLayout buildVerticalStepper(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);

        // step1/2/3Circle that: 64dp, bg_circle_outline_black (vien 2px den khi khong active; o day dung mau do
        // ruou cho buoc active - XML khong phan biet active/inactive vi JS/Kotlin khong doi mau theo buoc thuc te,
        // nhung giu phan biet nay cho de hieu luong 3 buoc). So 24sp bold, "Buoc N:" 20sp (sans-serif) #1A1A1A,
        // mo ta 18sp (inter_24pt_regular) #504F4F. Duong noi 3dp rong, cach nhau 32dp (step2Circle marginTop that).
        // Thu nho so voi so that (64dp/32dp) theo yeu cau nguoi dung ("3 cai buoc kia nho lai 1 iu") - canvas
        // 540dp cua ban clone nay nho hon nhieu man kiosk that (sw720dp+) nen vong tron 64dp trong ty le hoi to.
        String[] titles = {"Xác thực thẻ Căn cước/VNeID", "Xác thực khuôn mặt", "Tạo tài khoản và xác nhận thông tin"};
        int railW = 52;
        int gap = 26;
        for (int i = 0; i < titles.length; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            boolean active = i == 0;
            boolean last = i == titles.length - 1;

            // "Ray" ben trai: FrameLayout rong dung bang duong kinh vong tron, vong tron + duong noi deu CAN GIUA
            // trong do -> luon thang hang voi nhau du card co padding bao nhieu (khong con phai tinh margin tay).
            FrameLayout rail = new FrameLayout(context);
            TextView circle = Ui.text(context, String.valueOf(i + 1), 20f,
                    active ? Ui.BURGUNDY_PRIMARY : 0xFF000000, Ui.W_BOLD);
            circle.setGravity(Gravity.CENTER);
            circle.setBackground(Ui.rect(0x00000000, 100f, active ? Ui.BURGUNDY_PRIMARY : 0xFF000000, 2f));
            rail.addView(circle, new FrameLayout.LayoutParams(Ui.px(railW), Ui.px(railW), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            if (!last) {
                View line = new View(context);
                line.setBackgroundColor(0xFF000000);
                FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(
                        Math.max(1, Ui.px(3)), Ui.px(gap), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                lineLp.topMargin = Ui.px(railW);
                rail.addView(line, lineLp);
            }
            row.addView(rail, new LinearLayout.LayoutParams(Ui.px(railW), LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_VERTICAL);
            col.addView(Ui.text(context, "Bước " + (i + 1) + ":", 17f, 0xFF1A1A1A, Ui.W_MEDIUM),
                    new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView t = Ui.text(context, titles[i], 15f, 0xFF504F4F, Ui.W_REGULAR);
            LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tLp.topMargin = Ui.px(2);
            col.addView(t, tLp);
            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, Ui.px(railW), 1f);
            colLp.setMarginStart(Ui.px(20));
            row.addView(col, colLp);

            // Rail cua tung row da TU CAO du (railW + gap, vi duong noi nam trong CHINH rail nay) nen KHONG can
            // them topMargin giua cac row - rail da "dai" het khoang cach toi vong tron tiep theo.
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
