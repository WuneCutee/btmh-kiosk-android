package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.ui.KioskViewModel;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man "Chi tiet don hang" / "Thong tin don hang" (spec II.1.8, mo rong theo Kiosk.xlsx "Thong tin don hang"): DANH SACH
 * cac the san pham trong gio hang ({@link KioskViewModel.UiState#cart}, moi the = anh, ten, gia cam, stepper - n + gioi
 * han CHUNG theo han muc II.2.11 toan gio + ton kho rieng tung san pham, nut "x" bo san pham) -> "Thong tin mua hang"
 * (Tong san pham, Tong gia tri don = tong tat ca cac dong) -> "Nhan tai cua hang" (o bo tron Tinh/Thanh pho — mac dinh
 * HN, an neu chua co tinh — va o Cua hang; cham o mo danh sach chon: radio + o tim kiem khong phan biet hoa/thuong va
 * dau) -> "Thong tin nhan hang" (Nguoi nhan, CCCD/Ho chieu, SDT, Dia chi thuong tru: App CHUA co dinh danh khach hang
 * nen hien "—", dien qua {@link #setCustomerInfo}; but chi chi de trang tri, chua co thao tac sua) -> "Ngay nhan hang"
 * (o xam chi doc: ngay do server tinh + "Khung gio nhan") -> nut "DAT MUA". Nut nay van la Phase 1: khong dat hang
 * that/khong thanh toan that (chua co backend dat hang/cong thanh toan) — cham chi bao "Coming soon" (khong goi API nao).
 */
public class OrderConfirmView extends LinearLayout implements Ui.InsetAware {

    public interface Callbacks {
        void onSelectProvince(long provinceId);

        void onSelectStore(long storeId);

        /** Stepper [-]/[+] tren 1 dong gio hang. */
        void onChangeCartQuantity(String productId, int quantity);

        /** Nut "x" tren 1 dong gio hang. */
        void onRemoveCartItem(String productId);

        /** Nut "DAT MUA": sang man Thanh toan (QR chuyen khoan). */
        void onPlaceOrder();

        /** Nut "XAC MINH CHINH CHU": mo popup + man quet khuon mat ({@link IdentityVerifyView}). */
        void onVerifyIdentity();
    }

    private static final int MAX_UNBOUNDED = 99;
    private static final int FIELD_H = 64;   // o chon bo tron (>= 64 cham duoc de dang)
    private static final int THUMB = 96;
    private static final String DASH = "—";

    private final Callbacks callbacks;

    private final LinearLayout content;
    private final TextView loadingText;
    private final TextView errorText;
    private final LinearLayout itemsContainer;
    private final TextView emptyCartText;
    private final TextView totalCount;
    private final TextView totalValue;
    private final LinearLayout provinceGroup;
    private final TextView provinceFieldText;
    private final IconView provinceChevron;
    private final LinearLayout provinceField;
    private final LinearLayout provinceList;
    private final TextView storeFieldText;
    private final IconView storeChevron;
    private final TextView storeAddress;
    private final LinearLayout storePanel;
    private final EditText storeSearch;
    private final LinearLayout storeList;
    private final TextView recipientName;
    private final TextView recipientId;
    private final TextView recipientPhone;
    private final TextView recipientAddress;
    private final TextView dateText;
    private final TextView hoursNote;

    private boolean pricesHidden;
    private boolean verified;
    private TextView verifyButton;
    private TextView verifiedNote;
    private TextView buyButton;
    private KioskViewModel.UiState lastState;
    private boolean provinceOpen = false;
    private boolean storeOpen = true; // chua chon cua hang -> mo san danh sach
    private String storeQuery = "";
    private boolean suppressSearch = false;
    // Tham chieu da ve — chi ve lai khi du lieu doi (bind() duoc goi moi khi gia SSE doi).
    private List<Models.ProvinceDto> boundProvinces;
    private Long boundProvinceId;
    private List<Models.StoreDto> boundStores;
    private Long boundStoreId;
    private String boundQuery;
    private boolean boundProvinceOpen;
    private boolean boundStoreOpen;

    public OrderConfirmView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        setOrientation(VERTICAL);
        setClickable(true);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        content.setPadding(Ui.px(14), Ui.px(6), Ui.px(14), Ui.px(36));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        loadingText = Ui.text(context, "Đang tải thông tin nhận hàng…", 9.5f, Ui.BURGUNDY_PRIMARY, Ui.W_MEDIUM);
        loadingText.setVisibility(GONE);
        content.addView(loadingText, block(10));
        errorText = Ui.text(context, "", 9.5f, Ui.DOWN_RED, Ui.W_BOLD);
        errorText.setVisibility(GONE);
        content.addView(errorText, block(10));

        // ================= DANH SACH SAN PHAM TRONG GIO (co the nhieu dong, moi dong 1 the anh|ten+gia/stepper) =================
        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(VERTICAL);
        content.addView(itemsContainer, block(0));
        emptyCartText = Ui.text(context, "Giỏ hàng trống.", 10f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM);
        emptyCartText.setVisibility(GONE);
        content.addView(emptyCartText, block(22));

        // ================= THONG TIN MUA HANG =================
        content.addView(sectionTitle(context, "Thông tin mua hàng", Ui.BURGUNDY_PRIMARY, null), block(12));
        totalCount = Ui.text(context, "1", 10.5f, Ui.PRICE_ORANGE, Ui.W_BOLD);
        content.addView(infoRow(context, "Số lượng", totalCount, 14), padded(14));
        totalValue = Ui.text(context, "", 12f, Ui.PRICE_ORANGE, Ui.W_BOLD);
        content.addView(infoRow(context, "Thành tiền", totalValue, 0), padded(22));

        // ================= NHAN TAI CUA HANG =================
        content.addView(sectionTitle(context, "Nhận tại cửa hàng", Ui.TEXT_TITLE_DARK, null), block(10));
        LinearLayout storeCard = card(context, 18);
        storeCard.setOrientation(VERTICAL);

        // -- Tinh / Thanh pho --
        provinceGroup = new LinearLayout(context);
        provinceGroup.setOrientation(VERTICAL);
        provinceGroup.addView(requiredLabel(context, "Tỉnh/Thành phố"), block(8));
        provinceField = new LinearLayout(context);
        provinceField.setOrientation(HORIZONTAL);
        provinceField.setGravity(Gravity.CENTER_VERTICAL);
        provinceField.setBackground(fieldBg());
        provinceField.setPadding(Ui.px(24), 0, Ui.px(18), 0);
        provinceFieldText = Ui.text(context, "", 10.5f, Ui.TEXT_TITLE_DARK, Ui.W_MEDIUM);
        provinceFieldText.setSingleLine(true);
        provinceFieldText.setEllipsize(TextUtils.TruncateAt.END);
        provinceField.addView(provinceFieldText, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        provinceChevron = new IconView(context, IconView.CHEVRON_DOWN, Ui.BURGUNDY_PRIMARY).strokeUnits(2.4f);
        provinceField.addView(provinceChevron, new LayoutParams(Ui.px(28), Ui.px(28)));
        provinceField.setClickable(true);
        Ui.pressEffect(provinceField);
        provinceField.setOnClickListener(v -> {
            provinceOpen = !provinceOpen;
            renderProvinceList();
        });
        provinceGroup.addView(provinceField, sized(LayoutParams.MATCH_PARENT, Ui.px(FIELD_H), 0));
        provinceList = new LinearLayout(context);
        provinceList.setOrientation(VERTICAL);
        provinceList.setVisibility(GONE);
        provinceGroup.addView(provinceList, block(0));
        storeCard.addView(provinceGroup, block(18));

        // -- Cua hang nhan hang --
        storeCard.addView(requiredLabel(context, "Nhận tại cửa hàng"), block(8));
        LinearLayout storeField = new LinearLayout(context);
        storeField.setOrientation(HORIZONTAL);
        storeField.setGravity(Gravity.CENTER_VERTICAL);
        storeField.setBackground(fieldBg());
        storeField.setPadding(Ui.px(24), 0, Ui.px(18), 0);
        storeFieldText = Ui.text(context, "", 10.5f, Ui.TEXT_TITLE_DARK, Ui.W_MEDIUM);
        storeFieldText.setSingleLine(true);
        storeFieldText.setEllipsize(TextUtils.TruncateAt.END);
        storeField.addView(storeFieldText, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        storeChevron = new IconView(context, IconView.CHEVRON_DOWN, Ui.BURGUNDY_PRIMARY).strokeUnits(2.4f);
        storeField.addView(storeChevron, new LayoutParams(Ui.px(28), Ui.px(28)));
        storeField.setClickable(true);
        Ui.pressEffect(storeField);
        storeField.setOnClickListener(v -> {
            storeOpen = !storeOpen;
            renderStorePanel();
        });
        storeCard.addView(storeField, sized(LayoutParams.MATCH_PARENT, Ui.px(FIELD_H), 0));

        storePanel = new LinearLayout(context);
        storePanel.setOrientation(VERTICAL);
        LinearLayout searchBox = new LinearLayout(context);
        searchBox.setOrientation(HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(Ui.outline(Ui.R_PILL));
        LayoutParams siLp = new LayoutParams(Ui.px(26), Ui.px(26));
        siLp.setMargins(Ui.px(22), 0, Ui.px(4), 0);
        searchBox.addView(new IconView(context, IconView.SEARCH, Ui.TEXT_MUTED_LIGHT).strokeUnits(1.9f), siLp);
        storeSearch = new EditText(context);
        storeSearch.setHint("Tìm theo tên, địa chỉ, phường/xã…");
        storeSearch.setHintTextColor(Ui.TEXT_MUTED_LIGHT);
        storeSearch.setTextColor(Ui.TEXT_TITLE_DARK);
        storeSearch.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(10f));
        storeSearch.setSingleLine(true);
        storeSearch.setInputType(InputType.TYPE_CLASS_TEXT);
        storeSearch.setIncludeFontPadding(false);
        storeSearch.setBackground(null);
        storeSearch.setPadding(Ui.px(10), 0, Ui.px(20), 0);
        storeSearch.setOnFocusChangeListener((v, focused) -> searchBox.setBackground(
                Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, focused ? Ui.BURGUNDY_PRIMARY : Ui.BORDER_LIGHT, focused ? 1.5f : 1)));
        storeSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (suppressSearch) return;
                storeQuery = s.toString();
                renderStores(boundStores, boundStoreId);
            }
        });
        searchBox.addView(storeSearch, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        LayoutParams sbLp = sized(LayoutParams.MATCH_PARENT, Ui.px(FIELD_H), 12);
        sbLp.topMargin = Ui.px(10);
        storePanel.addView(searchBox, sbLp);
        storeList = new LinearLayout(context);
        storeList.setOrientation(VERTICAL);
        storePanel.addView(storeList, block(0));
        storeCard.addView(storePanel, block(0));

        storeAddress = Ui.text(context, "", 8.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        storeAddress.setLineSpacing(0, 1.15f);
        storeAddress.setVisibility(GONE);
        LayoutParams saLp = block(0);
        saLp.topMargin = Ui.px(10);
        storeCard.addView(storeAddress, saLp);
        TextView note = Ui.text(context, "Không hỗ trợ giao hàng khác cửa hàng quý khách đã chọn", 8f,
                Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        LayoutParams nLp = block(0);
        nLp.topMargin = Ui.px(10);
        storeCard.addView(note, nLp);
        content.addView(storeCard, block(24));

        // Man "Thong tin don hang" (theo Figma High fidelity_v1.0) KHONG co khoi "Thong tin nhan hang" rieng (Ho
        // ten/CCCD/SDT/Dia chi) — chi giu lai 4 TextView nay o dang "an" (khong gan vao layout) de setCustomerInfo()
        // (API cu, co the can lai sau nay) khong loi; gia tri khach hang hien tai khong hien thi tren man nay nua.
        recipientName = Ui.text(context, DASH, 9.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        recipientId = Ui.text(context, DASH, 9.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        recipientPhone = Ui.text(context, DASH, 9.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        recipientAddress = Ui.text(context, DASH, 9.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);

        // ================= NGAY NHAN HANG =================
        LinearLayout dateBlock = new LinearLayout(context);
        dateBlock.setOrientation(VERTICAL);
        dateBlock.setPadding(Ui.px(4), 0, Ui.px(4), 0);
        dateBlock.addView(requiredLabel(context, "Ngày nhận hàng"), block(10));
        dateText = Ui.text(context, "", 10.5f, Ui.TEXT_TITLE_DARK, Ui.W_MEDIUM);
        dateText.setGravity(Gravity.CENTER_VERTICAL);
        dateText.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        dateText.setBackground(Ui.rect(Ui.FIELD_GREY, Ui.R_PILL, 0, 0));
        dateBlock.addView(dateText, sized(LayoutParams.MATCH_PARENT, Ui.px(FIELD_H), 8));
        hoursNote = Ui.text(context, "", 8.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        dateBlock.addView(hoursNote, block(0));
        content.addView(dateBlock, block(28));

        // ================= XAC MINH CHINH CHU (truoc DAT MUA, theo mockup Figma) =================
        verifyButton = Ui.outlineButton(context, "XÁC MINH CHÍNH CHỦ", 11f);
        verifyButton.setLetterSpacing(0f);
        verifyButton.setBackground(Ui.pressable(Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BURGUNDY_PRIMARY, 1.5f)));
        verifyButton.setOnClickListener(v -> callbacks.onVerifyIdentity());
        content.addView(verifyButton, sized(LayoutParams.MATCH_PARENT, Ui.px(68), 14));
        verifiedNote = Ui.text(context, "✓ Đã xác minh chính chủ", 9f, Ui.UP_GREEN, Ui.W_BOLD);
        verifiedNote.setGravity(Gravity.CENTER);
        verifiedNote.setVisibility(GONE);
        content.addView(verifiedNote, sized(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 14));

        // ================= DAT MUA -> man Thanh toan (QR chuyen khoan) =================
        buyButton = Ui.primaryButton(context, "ĐẶT MUA", 12f);
        buyButton.setOnClickListener(v -> {
            if (lastState != null && lastState.cart.isEmpty()) {
                Toast.makeText(getContext(), "Giỏ hàng trống", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!verified) {
                Toast.makeText(getContext(), "Vui lòng xác minh chính chủ trước khi đặt mua", Toast.LENGTH_SHORT).show();
                return;
            }
            callbacks.onPlaceOrder();
        });
        content.addView(buyButton, sized(LayoutParams.MATCH_PARENT, Ui.px(68), 0));
        applyVerifiedStyle();
    }

    @Override
    public void onInsets(int l, int t, int r, int b) {
        setPadding(l, 0, r, 0);
        content.setPadding(Ui.px(14), Ui.px(6), Ui.px(14), Ui.px(36) + b);
    }

    // ==================== DU LIEU ====================

    /** Ket qua "Xac minh chinh chu" (tu {@link IdentityVerifyView}): bat nut DAT MUA, an nut Xac minh. */
    public void setVerified(boolean verified) {
        this.verified = verified;
        applyVerifiedStyle();
    }

    public boolean isVerified() {
        return verified;
    }

    private void applyVerifiedStyle() {
        verifyButton.setVisibility(verified ? GONE : VISIBLE);
        verifiedNote.setVisibility(verified ? VISIBLE : GONE);
        buyButton.setTextColor(verified ? Ui.SURFACE_WHITE : Ui.TEXT_MUTED_LIGHT);
        buyButton.setBackground(verified
                ? Ui.pressable(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0), Ui.rect(Ui.BURGUNDY_DARK, Ui.R_PILL, 0, 0))
                : Ui.rect(Ui.BORDER_LIGHT, Ui.R_PILL, 0, 0));
        buyButton.setElevation(verified ? Ui.px(4) : 0f);
    }

    /** Goi truoc khi hien man — dat lai o tim kiem cua hang (danh sach san pham lay thang tu UiState.cart trong bind()). */
    public void resetStorePicker() {
        verified = false;
        applyVerifiedStyle();
        this.storeQuery = "";
        this.provinceOpen = false;
        this.storeOpen = true;
        suppressSearch = true;
        storeSearch.setText("");
        suppressSearch = false;
        boundProvinces = null;
        boundStores = null;
        boundStoreId = null;
        boundProvinceId = null;
        boundQuery = null;
    }

    /**
     * Thong tin nguoi nhan (tuy chon): App chua co tinh nang dinh danh khach hang nen cac gia tri mac dinh la null -> "—".
     * Tinh nang dinh danh sau nay chi can goi ham nay de dien vao (hien tai khong co thao tac sua).
     */
    public void setCustomerInfo(String name, String idNumber, String phone, String permanentAddress) {
        recipientName.setText(orDash(name));
        recipientId.setText(orDash(idNumber));
        recipientPhone.setText(orDash(phone));
        recipientAddress.setText(orDash(permanentAddress));
    }

    private static String orDash(String s) {
        return s == null || s.trim().isEmpty() ? DASH : s;
    }

    /**
     * Tom tat de dien sang man Thanh toan khi bam "DAT MUA" (Phase 1: chua co API tao don that nen ma don chi la ma
     * tham khao sinh tu thoi diem bam nut). Tra ve {ten khach, ma don, gia tri don (da dinh dang), dia diem nhan}.
     */
    public String[] getPaymentSummary() {
        String code = "DH" + new java.text.SimpleDateFormat("yyMMddHHmm", java.util.Locale.ROOT).format(new java.util.Date());
        String location = storeFieldText.getText().toString();
        return new String[]{recipientName.getText().toString(), code, totalValue.getText().toString(), location};
    }

    public void bind(KioskViewModel.UiState s) {
        if (s.cart.isEmpty()) return; // man dang dong hoac gio hang vua rong (da xoa het) -> giu nguyen lan ve cuoi
        lastState = s;
        pricesHidden = s.pricesHidden;

        loadingText.setVisibility(s.orderLoading ? VISIBLE : GONE);
        errorText.setText(s.orderError != null ? s.orderError : "");
        errorText.setVisibility(s.orderError != null ? VISIBLE : GONE);

        renderItems(s);

        // Tinh/thanh: o luon hien khi da co danh sach; chi 1 tinh -> khong co mui ten, khong mo duoc (theo SRS: khong cho chon)
        boolean hasProvinces = s.provinces != null && !s.provinces.isEmpty();
        boolean multi = s.provinces != null && s.provinces.size() > 1;
        provinceGroup.setVisibility(hasProvinces ? VISIBLE : GONE);
        provinceField.setClickable(multi);
        if (!multi) provinceOpen = false;
        if (s.provinces != boundProvinces || !java.util.Objects.equals(s.selectedProvinceId, boundProvinceId)
                || provinceOpen != boundProvinceOpen) {
            boundProvinces = s.provinces;
            boundProvinceId = s.selectedProvinceId;
            boundProvinceOpen = provinceOpen;
            renderProvinceField(s, multi);
            renderProvinceList();
        }

        renderStoreField(s);
        if (s.stores != boundStores || !java.util.Objects.equals(s.selectedStoreId, boundStoreId)
                || !storeQuery.equals(boundQuery) || storeOpen != boundStoreOpen) {
            renderStores(s.stores, s.selectedStoreId);
            renderStorePanel();
        }
        renderReceiveInfo(s);
    }

    // ---- Danh sach san pham trong gio ----

    /**
     * Ve lai toan bo danh sach dong gio hang (don gian, re-render het moi lan bind — gio hang kiosk thuong chi vai dong
     * nen chi phi khong dang ke) + cap nhat "Tong san pham"/"Tong gia tri don" (tong tat ca cac dong).
     */
    private void renderItems(KioskViewModel.UiState s) {
        List<Models.CartItem> cart = s.cart;
        itemsContainer.removeAllViews();
        emptyCartText.setVisibility(cart.isEmpty() ? VISIBLE : GONE);

        double totalUnits = 0;
        int totalQty = 0;
        double totalValueSum = 0;
        for (Models.CartItem it : cart) {
            totalQty += it.quantity;
            totalValueSum += it.lineTotal();
            totalUnits += it.lineUnits();
        }
        for (int i = 0; i < cart.size(); i++) {
            Models.CartItem item = cart.get(i);
            // Han muc dung CHUNG cho ca gio hang (spec sheet "Chi tiet san pham": X = (han muc - so chi cac SP khac trong
            // gio)/ban vi), nen toi da cua 1 dong phu thuoc so chi cac dong KHAC dang chiem.
            double otherUnits = totalUnits - item.lineUnits();
            int maxQty = computeMaxQuantity(item, s.limitQuantity, otherUnits);
            LinearLayout.LayoutParams lp = block(i == cart.size() - 1 ? 22 : 10);
            itemsContainer.addView(buildItemCard(getContext(), item, maxQty), lp);
        }

        totalCount.setText(String.valueOf(totalQty));
        if (pricesHidden) {
            totalValue.setText("Đang cập nhật giá…");
            totalValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(9.5f));
            totalValue.setTextColor(Ui.TEXT_MUTED_LIGHT);
        } else {
            totalValue.setText(Ui.bigPrice(totalValueSum, 0.8f));
            totalValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(12f));
            totalValue.setTextColor(Ui.PRICE_ORANGE);
        }
    }

    /** X = floor((han muc - so chi cac san pham KHAC trong gio) / ban vi 1 san pham), roi min voi ton kho (spec II.2.11). */
    private int computeMaxQuantity(Models.CartItem item, Double limit, double otherUnitsInCart) {
        int byLimit = MAX_UNBOUNDED;
        if (limit != null && item.product != null && item.product.quantity > 0) {
            double remaining = limit - otherUnitsInCart;
            byLimit = (int) Math.floor(remaining / item.product.quantity + 1e-9);
        }
        int byStock = item.product != null && item.product.remainingQuantity != null
                ? item.product.remainingQuantity : MAX_UNBOUNDED;
        return Math.max(0, Math.min(byLimit, byStock));
    }

    /** 1 the san pham trong gio hang: anh | ten + gia / stepper [ - n + ] / nut "x" xoa — tat ca thao tac goi thang vao Callbacks. */
    private View buildItemCard(Context context, Models.CartItem item, int maxQty) {
        Models.ProductDto p = item.product;
        LinearLayout itemCard = card(context, 14);
        itemCard.setOrientation(HORIZONTAL);

        FrameLayout thumbFrame = new FrameLayout(context);
        thumbFrame.setBackgroundColor(Ui.PAGE_BG_LIGHT);
        Ui.clipRounded(thumbFrame, Ui.R_SM);
        ImageView thumb = new ImageView(context);
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String url = p != null ? p.getPrimaryImageUrl() : null;
        if (url == null || url.isEmpty()) {
            thumb.setColorFilter(Ui.SIDEBAR_GOLD_BOT, PorterDuff.Mode.SRC_IN);
            thumb.setPadding(Ui.px(18), Ui.px(18), Ui.px(18), Ui.px(18));
            thumb.setImageResource(R.drawable.logo); // images[] rong -> anh mac dinh dong goi (II.2.6)
        } else {
            thumb.setColorFilter(Ui.PAGE_BG_LIGHT, PorterDuff.Mode.MULTIPLY);
            thumb.setPadding(Ui.px(4), Ui.px(4), Ui.px(4), Ui.px(4));
            Glide.with(thumb).load(url).fitCenter()
                    .transition(DrawableTransitionOptions.withCrossFade(220))
                    .into(thumb);
        }
        thumbFrame.addView(thumb, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        itemCard.addView(thumbFrame, new LayoutParams(Ui.px(THUMB), Ui.px(THUMB)));

        LinearLayout right = new LinearLayout(context);
        right.setOrientation(VERTICAL);
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(context, p != null ? p.name : "", 10.5f, Ui.TEXT_TITLE_DARK, Ui.W_MEDIUM);
        name.setLineSpacing(0, 1.25f);
        name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);
        topRow.addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        // Nut "x": bo dong san pham khoi gio (spec "II. Man hinh xac nhan bo san pham khoi gio hang" — rut gon: xoa
        // ngay, bao Toast, khong popup xac nhan rieng de giu pham vi vua phai).
        FrameLayout remove = new FrameLayout(context);
        remove.addView(new IconView(context, IconView.CLOSE, Ui.TEXT_MUTED_LIGHT).strokeUnits(2f),
                new FrameLayout.LayoutParams(Ui.px(20), Ui.px(20), Gravity.CENTER));
        remove.setClickable(true);
        Ui.pressEffect(remove);
        remove.setOnClickListener(v -> {
            if (p != null) callbacks.onRemoveCartItem(p.id);
        });
        LayoutParams rmLp = new LayoutParams(Ui.px(40), Ui.px(40));
        rmLp.setMarginStart(Ui.px(4));
        topRow.addView(remove, rmLp);
        right.addView(topRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView price = Ui.text(context, "", 11f, Ui.PRICE_ORANGE, Ui.W_BOLD);
        price.setSingleLine(true);
        if (p != null) {
            if (pricesHidden) {
                price.setText("Đang cập nhật giá…");
                price.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(9f));
                price.setTextColor(Ui.TEXT_MUTED_LIGHT);
            } else {
                price.setText(Ui.bigPrice(p.price, 0.7f));
            }
        }
        LayoutParams priceLp = block(0);
        priceLp.topMargin = Ui.px(2);
        right.addView(price, priceLp);
        right.addView(new View(context), new LayoutParams(1, 0, 1f));

        // Stepper nho: [ - n + ] — vung cham 64 cao, nen vien be ve nho hon (inset doc)
        LinearLayout stepper = new LinearLayout(context);
        stepper.setOrientation(HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setBackground(new InsetDrawable(Ui.rect(Ui.STEPPER_BG, Ui.R_PILL, Ui.BORDER_LIGHT, 1.2f),
                0, Ui.px(10), 0, Ui.px(10)));
        View minus = stepButton(context, IconView.MINUS);
        minus.setAlpha(item.quantity > 1 ? 1f : 0.3f);
        minus.setOnClickListener(v -> {
            if (p != null && item.quantity > 1) callbacks.onChangeCartQuantity(p.id, item.quantity - 1);
        });
        stepper.addView(minus, new LayoutParams(Ui.px(58), Ui.px(64)));
        TextView qtyValue = Ui.text(context, String.valueOf(item.quantity), 11.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        qtyValue.setGravity(Gravity.CENTER);
        stepper.addView(qtyValue, new LayoutParams(Ui.px(46), LayoutParams.WRAP_CONTENT));
        View plus = stepButton(context, IconView.PLUS);
        plus.setAlpha(item.quantity < maxQty ? 1f : 0.3f);
        plus.setOnClickListener(v -> {
            if (p != null && item.quantity < maxQty) callbacks.onChangeCartQuantity(p.id, item.quantity + 1);
        });
        stepper.addView(plus, new LayoutParams(Ui.px(58), Ui.px(64)));
        LayoutParams stLp = new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.px(64));
        stLp.gravity = Gravity.START;
        stLp.topMargin = Ui.px(8);
        right.addView(stepper, stLp);

        TextView note = Ui.text(context, maxQty <= 0 ? "Vượt hạn mức hoặc hết hàng"
                : (maxQty < MAX_UNBOUNDED ? "Tối đa " + maxQty + " sản phẩm" : ""), 8f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        LayoutParams noteLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        noteLp.gravity = Gravity.START;
        noteLp.topMargin = Ui.px(4);
        if (!note.getText().toString().isEmpty()) right.addView(note, noteLp);

        LayoutParams rightLp = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f);
        rightLp.setMarginStart(Ui.px(14));
        itemCard.addView(right, rightLp);
        return itemCard;
    }

    // ---- Tinh / thanh ----

    private void renderProvinceField(KioskViewModel.UiState s, boolean expandable) {
        String name = "Chọn tỉnh/thành phố";
        boolean chosen = false;
        if (s.provinces != null && s.selectedProvinceId != null) {
            for (Models.ProvinceDto p : s.provinces) {
                if (p.id == s.selectedProvinceId) {
                    name = p.name;
                    chosen = true;
                }
            }
        }
        provinceFieldText.setText(name);
        provinceFieldText.setTextColor(chosen ? Ui.TEXT_TITLE_DARK : Ui.TEXT_MUTED_LIGHT);
        provinceChevron.setVisibility(expandable ? VISIBLE : GONE);
        provinceChevron.setIcon(provinceOpen ? IconView.CHEVRON_UP : IconView.CHEVRON_DOWN);
    }

    private void renderProvinceList() {
        provinceList.removeAllViews();
        provinceChevron.setIcon(provinceOpen ? IconView.CHEVRON_UP : IconView.CHEVRON_DOWN);
        if (!provinceOpen || boundProvinces == null) {
            provinceList.setVisibility(GONE);
            return;
        }
        provinceList.setVisibility(VISIBLE);
        for (Models.ProvinceDto p : boundProvinces) {
            boolean selected = boundProvinceId != null && p.id == boundProvinceId;
            LinearLayout row = optionRow(getContext(), selected);
            TextView label = Ui.text(getContext(), p.name + "  ·  " + p.storeCount + " cửa hàng", 10f,
                    selected ? Ui.BURGUNDY_PRIMARY : Ui.TEXT_TITLE_DARK, selected ? Ui.W_BOLD : Ui.W_MEDIUM);
            row.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> {
                provinceOpen = false;
                storeOpen = true; // doi tinh -> danh sach cua hang moi, mo san de chon
                callbacks.onSelectProvince(p.id);
            });
            provinceList.addView(row, sized(LayoutParams.MATCH_PARENT, Ui.px(FIELD_H), 6));
        }
        LayoutParams lp = (LayoutParams) provinceList.getLayoutParams();
        lp.topMargin = Ui.px(8);
        provinceList.setLayoutParams(lp);
    }

    /** Hang chon (tinh): radio ben trai, nen chon = vien do ruou. */
    private LinearLayout optionRow(Context context, boolean selected) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(selected ? Ui.cardSelected(Ui.R_MD) : Ui.card(Ui.R_MD));
        row.setPadding(Ui.px(18), 0, Ui.px(18), 0);
        row.addView(radio(context, selected), new LayoutParams(Ui.px(26), Ui.px(26)));
        View gap = new View(context);
        row.addView(gap, new LayoutParams(Ui.px(14), 1));
        row.setClickable(true);
        Ui.pressEffect(row);
        return row;
    }

    private static View radio(Context context, boolean selected) {
        View v = new View(context);
        GradientDrawable ring = Ui.ovalStroke(0x00000000, selected ? Ui.BURGUNDY_PRIMARY : Ui.TEXT_MUTED_LIGHT, 2);
        if (selected) {
            LayerDrawable ld = new LayerDrawable(new GradientDrawable[]{ring, Ui.oval(Ui.BURGUNDY_PRIMARY)});
            int inset = Ui.px(6);
            ld.setLayerInset(1, inset, inset, inset, inset);
            v.setBackground(ld);
        } else {
            v.setBackground(ring);
        }
        return v;
    }

    // ---- Cua hang ----

    private void renderStoreField(KioskViewModel.UiState s) {
        Models.StoreDto selected = selectedStore(s);
        if (selected == null) {
            storeFieldText.setText("Chọn cửa hàng nhận hàng");
            storeFieldText.setTextColor(Ui.TEXT_MUTED_LIGHT);
            storeAddress.setVisibility(GONE);
        } else {
            storeFieldText.setText(selected.name);
            storeFieldText.setTextColor(Ui.TEXT_TITLE_DARK);
            boolean hasAddress = selected.address != null && !selected.address.isEmpty();
            storeAddress.setText(hasAddress ? selected.address : "");
            storeAddress.setVisibility(hasAddress ? VISIBLE : GONE);
        }
    }

    private void renderStorePanel() {
        boundStoreOpen = storeOpen;
        storePanel.setVisibility(storeOpen ? VISIBLE : GONE);
        storeChevron.setIcon(storeOpen ? IconView.CHEVRON_UP : IconView.CHEVRON_DOWN);
    }

    private void renderStores(List<Models.StoreDto> stores, Long selectedStoreId) {
        boundStores = stores;
        boundStoreId = selectedStoreId;
        boundQuery = storeQuery;
        storeList.removeAllViews();
        if (stores == null || stores.isEmpty()) {
            TextView empty = Ui.text(getContext(), "Chưa có cửa hàng nhận hàng tại tỉnh/thành này", 9.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
            storeList.addView(empty, block(0));
            return;
        }
        String q = fold(storeQuery);
        List<Models.StoreDto> shown = new ArrayList<>();
        for (Models.StoreDto st : stores) {
            if (q.isEmpty() || fold(st.name).contains(q) || fold(st.address).contains(q) || fold(st.wardName).contains(q)) {
                shown.add(st);
            }
        }
        if (shown.isEmpty()) {
            storeList.addView(Ui.text(getContext(), "Không tìm thấy cửa hàng", 9.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR), block(0));
            return;
        }
        // Danh sach da sap san theo wardName roi name (bang chu cai tieng Viet) — giu nguyen thu tu mang.
        for (Models.StoreDto st : shown) {
            boolean selected = selectedStoreId != null && st.id == selectedStoreId;
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(selected ? Ui.cardSelected(Ui.R_MD) : Ui.card(Ui.R_MD));
            row.setPadding(Ui.px(18), Ui.px(16), Ui.px(18), Ui.px(16));
            row.setClickable(true);
            Ui.pressEffect(row);
            row.setOnClickListener(v -> {
                storeOpen = false; // da chon -> thu gon, o Cua hang hien ten cua hang vua chon
                callbacks.onSelectStore(st.id);
            });

            row.addView(radio(getContext(), selected), new LayoutParams(Ui.px(26), Ui.px(26)));

            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(VERTICAL);
            TextView name = Ui.text(getContext(), st.name, 10.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
            name.setLineSpacing(0, 1.08f);
            col.addView(name, block(5));
            TextView address = Ui.text(getContext(), st.address == null ? "" : st.address, 8.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
            address.setMaxLines(2);
            address.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(address, block(5));
            String when = receiveText(st);
            if (when != null) col.addView(Ui.text(getContext(), when, 8.5f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD), block(0));
            LayoutParams colLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            colLp.setMarginStart(Ui.px(14));
            row.addView(col, colLp);
            storeList.addView(row, sized(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 10));
        }
    }

    private static Models.StoreDto selectedStore(KioskViewModel.UiState s) {
        if (s.stores != null && s.selectedStoreId != null) {
            for (Models.StoreDto st : s.stores) if (st.id == s.selectedStoreId) return st;
        }
        return null;
    }

    /** "Ngay nhan hang" = ngay server tinh (receiveDate, App khong tu cong ngay) + "Khung gio nhan: <pickupHours>". */
    private void renderReceiveInfo(KioskViewModel.UiState s) {
        Models.StoreDto selected = selectedStore(s);
        if (selected == null) {
            dateText.setText("Vui lòng chọn cửa hàng nhận hàng");
            dateText.setTextColor(Ui.TEXT_MUTED_LIGHT);
            hoursNote.setVisibility(GONE);
            return;
        }
        String date = formatDate(selected.receiveDate);
        dateText.setText(date != null ? date : "Liên hệ cửa hàng để biết ngày nhận");
        dateText.setTextColor(date != null ? Ui.TEXT_TITLE_DARK : Ui.TEXT_MUTED_LIGHT);
        boolean hasHours = selected.pickupHours != null && !selected.pickupHours.isEmpty();
        hoursNote.setText(hasHours ? "Khung giờ nhận: " + selected.pickupHours : "");
        hoursNote.setVisibility(hasHours ? VISIBLE : GONE);
    }

    /** "Nhận từ 20/09/2026 · 09:00–20:30" — dong phu trong danh sach cua hang. */
    private static String receiveText(Models.StoreDto st) {
        String date = formatDate(st.receiveDate);
        StringBuilder sb = new StringBuilder();
        if (date != null) sb.append("Nhận từ ").append(date);
        if (st.pickupHours != null && !st.pickupHours.isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(st.pickupHours);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String formatDate(String iso) {
        if (iso == null || iso.length() < 10) return null;
        String[] p = iso.substring(0, 10).split("-");
        return p.length == 3 ? p[2] + "/" + p[1] + "/" + p[0] : null;
    }

    /** Bo dau + thuong hoa de so khop khong phan biet hoa/thuong va dau ("Dong Da" khop "Đống Đa"). */
    private static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
    }

    // ==================== TIEN ICH GIAO DIEN ====================

    /** The trang bo goc + bong nhe, padding theo px thiet ke. */
    private static LinearLayout card(Context context, float padDesign) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(card);
        card.setPadding(Ui.px(padDesign), Ui.px(padDesign), Ui.px(padDesign), Ui.px(padDesign));
        return card;
    }

    /** O nhap bo tron: nen trang, vien vang nhat. */
    private static GradientDrawable fieldBg() {
        return Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.FIELD_BORDER, 1.5f);
    }

    /** Tieu de muc (chu dam) + bieu tuong tuy chon o cuoi dong (but chi cua "Thong tin nhan hang"). */
    private static View sectionTitle(Context context, String text, int color, IconView trailing) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.px(4), 0, Ui.px(6), 0);
        row.addView(Ui.text(context, text, 11f, color, Ui.W_BOLD), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        if (trailing != null) row.addView(trailing, new LayoutParams(Ui.px(30), Ui.px(30)));
        return row;
    }

    /** Nhan co dau * do: "* Tinh/Thanh pho". */
    private static TextView requiredLabel(Context context, String text) {
        SpannableString sp = new SpannableString("* " + text);
        sp.setSpan(new ForegroundColorSpan(Ui.DOWN_RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView t = Ui.text(context, sp, 9.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        t.setPadding(Ui.px(4), 0, 0, 0);
        return t;
    }

    /** Hang "nhan muted ben trai -> gia tri (cam) ben phai", khong co the. */
    private static View infoRow(Context context, String label, TextView value, float bottomDesign) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(context, label, 10f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR),
                new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        value.setGravity(Gravity.END);
        row.addView(value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static LinearLayout.LayoutParams padded(float bottomDesign) {
        LinearLayout.LayoutParams lp = block(bottomDesign);
        lp.setMargins(Ui.px(4), 0, Ui.px(4), Ui.px(bottomDesign));
        return lp;
    }

    /** Hang trong the nhan hang: nhan muted (trai) + gia tri dam can phai (nhieu dong duoc). */
    private static View recipientRow(Context context, String label, TextView value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.addView(Ui.text(context, label, 9.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR),
                new LayoutParams(0, LayoutParams.WRAP_CONTENT, 4f));
        value.setGravity(Gravity.END);
        value.setLineSpacing(0, 1.2f);
        LayoutParams vLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 5f);
        vLp.setMarginStart(Ui.px(12));
        row.addView(value, vLp);
        return row;
    }

    /** Nut +/- cua stepper: icon o giua, vung cham rong 58 x cao 64. */
    private View stepButton(Context context, int icon) {
        FrameLayout b = new FrameLayout(context);
        b.addView(new IconView(context, icon, Ui.TEXT_TITLE_DARK).strokeUnits(2.4f),
                new FrameLayout.LayoutParams(Ui.px(22), Ui.px(22), Gravity.CENTER));
        b.setClickable(true);
        Ui.pressEffect(b);
        return b;
    }

    private static LinearLayout.LayoutParams block(float bottomDesign) {
        return sized(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, bottomDesign);
    }

    private static LinearLayout.LayoutParams sized(int width, int height, float bottomDesign) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.bottomMargin = Ui.px(bottomDesign);
        return lp;
    }
}
