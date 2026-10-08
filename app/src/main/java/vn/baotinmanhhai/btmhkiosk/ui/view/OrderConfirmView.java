package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

public class OrderConfirmView extends LinearLayout implements Ui.InsetAware {

    public interface Callbacks {
        void onSelectProvince(long provinceId);
        void onSelectStore(long storeId);
        void onChangeCartQuantity(String productId, int quantity);
        void onRemoveCartItem(String productId);
        void onPlaceOrder();
        void onVerifyIdentity();
    }

    // Các thông số Pixel chính xác từ Figma CSS (Frame 540px)
    private static final int MAX_UNBOUNDED = 99;
    private static final int FIELD_H = 56;
    private static final int ITEMS_H = 298; // Figma: Frame 2147224633 height: 298px
    private static final int CARD_H = 144;  // Figma: Card height: 144px
    private static final int THUMB_W = 112; // Figma: Img width: 112px, height: 144px
    private static final int BTN_H = 56;    // Figma: Buttons height: 56px

    // Mã màu chính xác từ Figma Design Token
    private static final int COLOR_RED_BRAND = 0xFFAA1F23;    // ColorPalette/Red/400
    private static final int COLOR_RED_CLOSE = 0xFF7C0410;    // ColorPalette/Red/500
    private static final int COLOR_BORDER_CREAM = 0xFFFBECD9; // DeepCream02
    private static final int COLOR_STEPPER_BG = 0xFFFFFBF4;   // ColorPalette/Gold/100
    private static final int COLOR_STEPPER_BORDER = 0xFFF4E3CA; // ColorPalette/Gold/300
    private static final int COLOR_DATE_BG = 0xFFD9D9D9;      // ColorPalette/Grey/500
    private static final int COLOR_BTN_DISABLED = 0xFF9A9A9A; // ColorPalette/Grey/700
    private static final int COLOR_GRAY_500 = 0xFF535862;     // Colortext/Gray/500
    private static final int COLOR_GRAY_400 = 0xFF757981;     // Colortext/Gray/400
    private static final int COLOR_GRAY_600 = 0xFF42464E;     // Colortext/Gray/600
    private static final int COLOR_INK_900 = 0xFF000000;      // ColorPalette/Ink/900
    private static final int COLOR_CHEVRON = 0xFF504F4F;      // ColorPalette/Ink/500
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
    private TextView verifyHintText;
    private TextView verifiedNote;
    private TextView buyButton;
    private LinearLayout footer; // Hai nut XAC MINH / DAT MUA ghim duoi cung man, ngoai vung cuon
    private KioskViewModel.UiState lastState;
    private boolean provinceOpen = false;
    private boolean storeOpen = false;
    private String storeQuery = "";
    private boolean suppressSearch = false;

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
        // Figma Frame 2147224642: padding 0px 32px; gap: 24px;
        content.setPadding(Ui.px(32), Ui.px(8), Ui.px(32), Ui.px(32));
        scroll.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        loadingText = Ui.text(context, "Đang tải thông tin nhận hàng…", 14f, COLOR_RED_BRAND, Ui.W_MEDIUM);
        loadingText.setVisibility(GONE);
        content.addView(loadingText, block(8));
        errorText = Ui.text(context, "", 14f, Ui.DOWN_RED, Ui.W_BOLD);
        errorText.setVisibility(GONE);
        content.addView(errorText, block(8));

        // ================= 1. DANH SACH SAN PHAM (Figma Frame 2147224633: height 298px) =================
        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(VERTICAL);
        ScrollView itemsScroll = new ScrollView(context);
        itemsScroll.setVerticalScrollBarEnabled(false);
        itemsScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        itemsScroll.addView(itemsContainer, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        // Figma Frame 2147224633: danh sach + dai trang tri vang nhat ben phai (Frame 2147224631/Rectangle 161123975)
        LinearLayout itemsRow = new LinearLayout(context);
        itemsRow.setOrientation(HORIZONTAL);
        itemsRow.addView(itemsScroll, new LinearLayout.LayoutParams(0, Ui.px(ITEMS_H), 1f));

        FrameLayout scrollDeco = new FrameLayout(context);
        scrollDeco.setBackground(Ui.rect(Color.WHITE, 12, 0, 0));
        View decoBar = new View(context);
        decoBar.setBackground(Ui.rect(COLOR_STEPPER_BORDER, 12, 0, 0));
        scrollDeco.addView(decoBar, new FrameLayout.LayoutParams(Ui.px(5), Ui.px(108), Gravity.CENTER));
        LinearLayout.LayoutParams decoLp = new LinearLayout.LayoutParams(Ui.px(5), Ui.px(ITEMS_H));
        decoLp.setMarginStart(Ui.px(10));
        itemsRow.addView(scrollDeco, decoLp);

        content.addView(itemsRow, sized(LayoutParams.MATCH_PARENT, Ui.px(ITEMS_H), 16));

        emptyCartText = Ui.text(context, "Giỏ hàng trống.", 14f, COLOR_GRAY_400, Ui.W_MEDIUM);
        emptyCartText.setVisibility(GONE);
        content.addView(emptyCartText, block(16));

        // ================= 2. THONG TIN MUA HANG (Figma Frame 2147224635) =================
        content.addView(sectionTitle(context, "Thông tin mua hàng"), block(10));
        
        // Hang "Số lượng" (Figma: Label 16px regular #535862, Value 16px semibold #000000)
        totalCount = Ui.text(context, "0", 16f, COLOR_INK_900, Ui.W_BOLD);
        content.addView(infoRow(context, "Số lượng", totalCount), sized(LayoutParams.MATCH_PARENT, Ui.px(24), 0));

        // Hang "Thành tiền" (Figma: Label 16px regular #535862, Value 16px semibold #AA1F23)
        totalValue = Ui.text(context, "0 VND", 16f, COLOR_RED_BRAND, Ui.W_BOLD);
        content.addView(infoRow(context, "Thành tiền", totalValue), sized(LayoutParams.MATCH_PARENT, Ui.px(24), 16));

        // ================= 3. NHAN TAI CUA HANG (Figma Frame 2147224636) =================
        content.addView(sectionTitle(context, "Nhận tại cửa hàng"), block(12));

        // -- Tinh / Thanh pho --
        provinceGroup = new LinearLayout(context);
        provinceGroup.setOrientation(VERTICAL);
        provinceGroup.addView(requiredLabel(context, "Tỉnh/Thành phố"), block(8));

        provinceField = new LinearLayout(context);
        provinceField.setOrientation(HORIZONTAL);
        provinceField.setGravity(Gravity.CENTER_VERTICAL);
        provinceField.setBackground(fieldBg());
        provinceField.setPadding(Ui.px(16), 0, Ui.px(16), 0);
        provinceFieldText = Ui.text(context, "Hà Nội", 16f, COLOR_INK_900, Ui.W_REGULAR);
        provinceFieldText.setSingleLine(true);
        provinceFieldText.setEllipsize(TextUtils.TruncateAt.END);
        provinceField.addView(provinceFieldText, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        provinceChevron = new IconView(context, IconView.CHEVRON_DOWN, COLOR_CHEVRON).strokeUnits(1.5f);
        provinceField.addView(provinceChevron, new LayoutParams(Ui.px(24), Ui.px(24)));
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
        content.addView(provinceGroup, block(14));

        // -- Nhan tai cua hang --
        content.addView(requiredLabel(context, "Nhận tại cửa hàng"), block(8));

        LinearLayout storeField = new LinearLayout(context);
        storeField.setOrientation(HORIZONTAL);
        storeField.setGravity(Gravity.CENTER_VERTICAL);
        storeField.setBackground(fieldBg());
        storeField.setPadding(Ui.px(16), 0, Ui.px(16), 0);
        storeFieldText = Ui.text(context, "Chọn cửa hàng", 16f, COLOR_GRAY_400, Ui.W_REGULAR);
        storeFieldText.setSingleLine(true);
        storeFieldText.setEllipsize(TextUtils.TruncateAt.END);
        storeField.addView(storeFieldText, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        storeChevron = new IconView(context, IconView.CHEVRON_DOWN, COLOR_CHEVRON).strokeUnits(1.5f);
        storeField.addView(storeChevron, new LayoutParams(Ui.px(24), Ui.px(24)));
        storeField.setClickable(true);
        Ui.pressEffect(storeField);
        storeField.setOnClickListener(v -> {
            storeOpen = !storeOpen;
            renderStorePanel();
        });
        content.addView(storeField, sized(LayoutParams.MATCH_PARENT, Ui.px(FIELD_H), 6));

        // Note: font 12px italic #757981
        TextView note = Ui.text(context, "Không hỗ trợ giao hàng khác cửa hàng quý khách đã chọn", 12f,
                COLOR_GRAY_400, Ui.W_REGULAR);
        note.setTypeface(note.getTypeface(), Typeface.ITALIC);
        content.addView(note, block(16));

        // Search Panel cua hang
        storePanel = new LinearLayout(context);
        storePanel.setOrientation(VERTICAL);
        storePanel.setVisibility(GONE);
        LinearLayout searchBox = new LinearLayout(context);
        searchBox.setOrientation(HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(Ui.outline(Ui.R_PILL));
        LayoutParams siLp = new LayoutParams(Ui.px(22), Ui.px(22));
        siLp.setMargins(Ui.px(16), 0, Ui.px(4), 0);
        searchBox.addView(new IconView(context, IconView.SEARCH, COLOR_GRAY_400).strokeUnits(1.8f), siLp);
        storeSearch = new EditText(context);
        storeSearch.setHint("Tìm theo tên, địa chỉ, phường/xã…");
        storeSearch.setHintTextColor(COLOR_GRAY_400);
        storeSearch.setTextColor(COLOR_INK_900);
        storeSearch.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(14f));
        storeSearch.setSingleLine(true);
        storeSearch.setInputType(InputType.TYPE_CLASS_TEXT);
        storeSearch.setIncludeFontPadding(false);
        storeSearch.setBackground(null);
        storeSearch.setPadding(Ui.px(10), 0, Ui.px(16), 0);
        storeSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (suppressSearch) return;
                storeQuery = s.toString();
                renderStores(boundStores, boundStoreId);
            }
        });
        searchBox.addView(storeSearch, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        storePanel.addView(searchBox, sized(LayoutParams.MATCH_PARENT, Ui.px(FIELD_H), 8));
        storeList = new LinearLayout(context);
        storeList.setOrientation(VERTICAL);
        storePanel.addView(storeList, block(0));
        content.addView(storePanel, block(0));

        storeAddress = Ui.text(context, "", 12f, COLOR_GRAY_600, Ui.W_REGULAR);
        storeAddress.setVisibility(GONE);
        content.addView(storeAddress, block(6));

        recipientName = Ui.text(context, DASH, 14f, COLOR_INK_900, Ui.W_BOLD);
        recipientId = Ui.text(context, DASH, 14f, COLOR_INK_900, Ui.W_BOLD);
        recipientPhone = Ui.text(context, DASH, 14f, COLOR_INK_900, Ui.W_BOLD);
        recipientAddress = Ui.text(context, DASH, 14f, COLOR_INK_900, Ui.W_BOLD);

        // ================= 4. THONG TIN NHAN HANG (Figma Frame 2147224637) =================
        content.addView(sectionTitle(context, "Thông tin nhận hàng"), block(12));
        
        // Figma: Label 16px semibold #1A1A1A (Khong co dau sao do)
        TextView dateLabel = Ui.text(context, "Ngày nhận hàng", 16f, 0xFF1A1A1A, Ui.W_BOLD);
        content.addView(dateLabel, block(8));

        // Figma: Background #D9D9D9, border #FBECD9, height 56px, text 16px #42464E
        dateText = Ui.text(context, "10/08/2026", 16f, COLOR_GRAY_600, Ui.W_REGULAR);
        dateText.setGravity(Gravity.CENTER_VERTICAL);
        dateText.setPadding(Ui.px(16), 0, Ui.px(16), 0);
        dateText.setBackground(Ui.rect(COLOR_DATE_BG, Ui.R_PILL, COLOR_BORDER_CREAM, 1f));
        content.addView(dateText, sized(LayoutParams.MATCH_PARENT, Ui.px(FIELD_H), 6));

        // Figma: 12px italic #757981
        hoursNote = Ui.text(context, "Khung giờ nhận: 09:00 - 20:30", 12f, COLOR_GRAY_400, Ui.W_REGULAR);
        hoursNote.setTypeface(hoursNote.getTypeface(), Typeface.ITALIC);
        content.addView(hoursNote, block(24));

        // ================= 5. BUTTONS (Figma Frame 2147224701) — ghim duoi cung man =================
        footer = new LinearLayout(context);
        footer.setOrientation(VERTICAL);
        footer.setPadding(Ui.px(32), Ui.px(8), Ui.px(32), Ui.px(16));
        addView(footer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // Button 1: "XÁC MINH CHÍNH CHỦ" (Figma: height 56px, bg #AA1F23, text 20px uppercase white)
        verifyButton = Ui.primaryButton(context, "XÁC MINH CHÍNH CHỦ", 20f);
        verifyButton.setBackground(Ui.rect(COLOR_RED_BRAND, Ui.R_PILL, 0, 0));
        verifyButton.setOnClickListener(v -> callbacks.onVerifyIdentity());
        footer.addView(verifyButton, sized(LayoutParams.MATCH_PARENT, Ui.px(BTN_H), 4));

        // Figma: 12px italic #AA1F23
        verifyHintText = Ui.text(context, "Vui lòng xác minh chính chủ trước khi đặt mua", 12f, COLOR_RED_BRAND, Ui.W_REGULAR);
        verifyHintText.setTypeface(verifyHintText.getTypeface(), Typeface.ITALIC);
        verifyHintText.setPadding(Ui.px(2), 0, 0, 0);
        footer.addView(verifyHintText, block(14));

        verifiedNote = Ui.text(context, "✓ Đã xác minh chính chủ", 14f, Ui.UP_GREEN, Ui.W_BOLD);
        verifiedNote.setGravity(Gravity.CENTER);
        verifiedNote.setVisibility(GONE);
        footer.addView(verifiedNote, sized(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 12));

        // Button 2: "ĐẶT MUA" (Figma: height 56px, bg #9A9A9A, text 20px uppercase white)
        buyButton = Ui.primaryButton(context, "ĐẶT MUA", 20f);
        buyButton.setBackground(Ui.rect(COLOR_BTN_DISABLED, Ui.R_PILL, 0, 0));
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
        footer.addView(buyButton, sized(LayoutParams.MATCH_PARENT, Ui.px(BTN_H), 0));
        applyVerifiedStyle();
    }

    @Override
    public void onInsets(int l, int t, int r, int b) {
        setPadding(l, 0, r, 0);
        content.setPadding(Ui.px(32), Ui.px(8), Ui.px(32), Ui.px(32));
        footer.setPadding(Ui.px(32), Ui.px(8), Ui.px(32), Ui.px(16) + b);
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
        applyVerifiedStyle();
    }

    public boolean isVerified() {
        return verified;
    }

    private void applyVerifiedStyle() {
        verifyButton.setVisibility(verified ? GONE : VISIBLE);
        verifyHintText.setVisibility(verified ? GONE : VISIBLE);
        verifiedNote.setVisibility(verified ? VISIBLE : GONE);

        if (verified) {
            buyButton.setTextColor(Ui.SURFACE_WHITE);
            buyButton.setBackground(Ui.pressable(
                    Ui.rect(COLOR_RED_BRAND, Ui.R_PILL, 0, 0),
                    Ui.rect(COLOR_RED_CLOSE, Ui.R_PILL, 0, 0)));
            buyButton.setElevation(Ui.px(2));
        } else {
            buyButton.setTextColor(Ui.SURFACE_WHITE);
            buyButton.setBackground(Ui.rect(COLOR_BTN_DISABLED, Ui.R_PILL, 0, 0));
            buyButton.setElevation(0f);
        }
    }

    public void resetStorePicker() {
        verified = false;
        applyVerifiedStyle();
        this.storeQuery = "";
        this.provinceOpen = false;
        this.storeOpen = false;
        suppressSearch = true;
        storeSearch.setText("");
        suppressSearch = false;
        boundProvinces = null;
        boundStores = null;
        boundStoreId = null;
        boundProvinceId = null;
        boundQuery = null;
    }

    public void setCustomerInfo(String name, String idNumber, String phone, String permanentAddress) {
        recipientName.setText(orDash(name));
        recipientId.setText(orDash(idNumber));
        recipientPhone.setText(orDash(phone));
        recipientAddress.setText(orDash(permanentAddress));
    }

    private static String orDash(String s) {
        return s == null || s.trim().isEmpty() ? DASH : s;
    }

    public String[] getPaymentSummary() {
        String code = "DH" + new java.text.SimpleDateFormat("yyMMddHHmm", Locale.ROOT).format(new java.util.Date());
        String location = storeFieldText.getText().toString();
        return new String[]{recipientName.getText().toString(), code, totalValue.getText().toString(), location};
    }

    public void bind(KioskViewModel.UiState s) {
        if (s.cart.isEmpty()) return;
        lastState = s;
        pricesHidden = s.pricesHidden;

        loadingText.setVisibility(s.orderLoading ? VISIBLE : GONE);
        errorText.setText(s.orderError != null ? s.orderError : "");
        errorText.setVisibility(s.orderError != null ? VISIBLE : GONE);

        renderItems(s);

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
            double otherUnits = totalUnits - item.lineUnits();
            int maxQty = computeMaxQuantity(item, s.limitQuantity, otherUnits);
            // Figma: gap 10px giua cac card
            LinearLayout.LayoutParams lp = block(i == cart.size() - 1 ? 0 : 10);
            itemsContainer.addView(buildItemCard(getContext(), item, maxQty), lp);
        }

        totalCount.setText(String.valueOf(totalQty));
        if (pricesHidden) {
            totalValue.setText("Đang cập nhật giá…");
            totalValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(14f));
            totalValue.setTextColor(COLOR_GRAY_400);
        } else {
            totalValue.setText(formatMoneyVnd(totalValueSum));
            totalValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(16f));
            totalValue.setTextColor(COLOR_RED_BRAND);
        }
    }

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

    // ================= THE SAN PHAM CHUAN FIGMA (Card: 461x144px, border-radius: 12px) =================
    private View buildItemCard(Context context, Models.CartItem item, int maxQty) {
        Models.ProductDto p = item.product;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Ui.rect(Color.WHITE, 12, 0, 0));
        card.setPadding(Ui.px(16), 0, Ui.px(16), 0);
        card.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(CARD_H)));

        // 1. Thumbnail (Img: 112x144px)
        FrameLayout thumbFrame = new FrameLayout(context);
        ImageView thumb = new ImageView(context);
        thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String url = p != null ? p.getPrimaryImageUrl() : null;
        if (url == null || url.isEmpty()) {
            thumb.setColorFilter(Ui.SIDEBAR_GOLD_BOT, PorterDuff.Mode.SRC_IN);
            thumb.setPadding(Ui.px(14), Ui.px(14), Ui.px(14), Ui.px(14));
            thumb.setImageResource(R.drawable.logo);
        } else {
            Glide.with(thumb).load(url).fitCenter()
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .into(thumb);
        }
        thumbFrame.addView(thumb, new FrameLayout.LayoutParams(Ui.px(85), Ui.px(125), Gravity.CENTER));
        card.addView(thumbFrame, new LayoutParams(Ui.px(THUMB_W), Ui.px(CARD_H)));

        // 2. Content (width: 299px, height: 120px, gap: 8px)
        LinearLayout contentCol = new LinearLayout(context);
        contentCol.setOrientation(VERTICAL);
        contentCol.setGravity(Gravity.CENTER_VERTICAL);
        contentCol.setPadding(Ui.px(10), Ui.px(4), 0, Ui.px(4));

        // Title + Nut X
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(HORIZONTAL);
        topRow.setGravity(Gravity.TOP);

        // Figma: font 20px, line-height 24px, color #1A1A1A, max 2 lines
        TextView name = Ui.text(context, p != null ? p.name : "", 20f, 0xFF1A1A1A, Ui.W_MEDIUM);
        name.setLineSpacing(0, 1.15f);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        topRow.addView(name, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        // Nut X (Figma: border 2px solid #7C0410, 20x20px)
        FrameLayout remove = new FrameLayout(context);
        remove.addView(new IconView(context, IconView.CLOSE, COLOR_RED_CLOSE).strokeUnits(2f),
                new FrameLayout.LayoutParams(Ui.px(16), Ui.px(16), Gravity.CENTER));
        remove.setClickable(true);
        Ui.pressEffect(remove);
        remove.setOnClickListener(v -> {
            if (p != null) callbacks.onRemoveCartItem(p.id);
        });
        LayoutParams rmLp = new LayoutParams(Ui.px(24), Ui.px(24));
        rmLp.setMarginStart(Ui.px(4));
        topRow.addView(remove, rmLp);
        contentCol.addView(topRow, block(4));

        // Giá (Figma: font 18px semibold #AA1F23)
        TextView price = Ui.text(context, "", 18f, COLOR_RED_BRAND, Ui.W_BOLD);
        price.setSingleLine(true);
        if (p != null) {
            price.setText(pricesHidden ? "Đang cập nhật giá…" : formatMoneyVnd(p.price));
        }
        contentCol.addView(price, block(6));

        // Stepper (Figma Frame 1410067893: width 110px, height 28px, bg #FFFBF4, border #F4E3CA, radius 24px)
        LinearLayout stepper = new LinearLayout(context);
        stepper.setOrientation(HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setBackground(Ui.rect(COLOR_STEPPER_BG, Ui.R_PILL, COLOR_STEPPER_BORDER, 1f));

        View minus = stepButton(context, IconView.MINUS);
        minus.setAlpha(item.quantity > 1 ? 1f : 0.3f);
        minus.setOnClickListener(v -> {
            if (p != null && item.quantity > 1) callbacks.onChangeCartQuantity(p.id, item.quantity - 1);
        });
        stepper.addView(minus, new LayoutParams(Ui.px(34), Ui.px(28)));

        TextView qtyValue = Ui.text(context, String.valueOf(item.quantity), 12f, COLOR_INK_900, Ui.W_REGULAR);
        qtyValue.setGravity(Gravity.CENTER);
        stepper.addView(qtyValue, new LayoutParams(Ui.px(38), LayoutParams.WRAP_CONTENT));

        View plus = stepButton(context, IconView.PLUS);
        plus.setAlpha(item.quantity < maxQty ? 1f : 0.3f);
        plus.setOnClickListener(v -> {
            if (p != null && item.quantity < maxQty) callbacks.onChangeCartQuantity(p.id, item.quantity + 1);
        });
        stepper.addView(plus, new LayoutParams(Ui.px(34), Ui.px(28)));

        contentCol.addView(stepper, new LayoutParams(Ui.px(110), Ui.px(28)));

        card.addView(contentCol, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
        return card;
    }

    private void renderProvinceField(KioskViewModel.UiState s, boolean expandable) {
        String name = "Hà Nội";
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
        provinceFieldText.setTextColor(chosen ? COLOR_INK_900 : COLOR_GRAY_400);
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
            TextView label = Ui.text(getContext(), p.name + "  ·  " + p.storeCount + " cửa hàng", 14f,
                    selected ? COLOR_RED_BRAND : COLOR_INK_900, selected ? Ui.W_BOLD : Ui.W_MEDIUM);
            row.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            row.setOnClickListener(v -> {
                provinceOpen = false;
                storeOpen = false;
                callbacks.onSelectProvince(p.id);
            });
            provinceList.addView(row, sized(LayoutParams.MATCH_PARENT, Ui.px(FIELD_H), 6));
        }
        LayoutParams lp = (LayoutParams) provinceList.getLayoutParams();
        lp.topMargin = Ui.px(6);
        provinceList.setLayoutParams(lp);
    }

    private LinearLayout optionRow(Context context, boolean selected) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(selected ? Ui.cardSelected(Ui.R_MD) : Ui.card(Ui.R_MD));
        row.setPadding(Ui.px(16), 0, Ui.px(16), 0);
        row.addView(radio(context, selected), new LayoutParams(Ui.px(22), Ui.px(22)));
        View gap = new View(context);
        row.addView(gap, new LayoutParams(Ui.px(12), 1));
        row.setClickable(true);
        Ui.pressEffect(row);
        return row;
    }

    private static View radio(Context context, boolean selected) {
        View v = new View(context);
        GradientDrawable ring = Ui.ovalStroke(0x00000000, selected ? COLOR_RED_BRAND : COLOR_GRAY_400, 1.8f);
        if (selected) {
            LayerDrawable ld = new LayerDrawable(new GradientDrawable[]{ring, Ui.oval(COLOR_RED_BRAND)});
            int inset = Ui.px(5);
            ld.setLayerInset(1, inset, inset, inset, inset);
            v.setBackground(ld);
        } else {
            v.setBackground(ring);
        }
        return v;
    }

    private void renderStoreField(KioskViewModel.UiState s) {
        Models.StoreDto selected = selectedStore(s);
        if (selected == null) {
            storeFieldText.setText("Chọn cửa hàng");
            storeFieldText.setTextColor(COLOR_GRAY_400);
            storeAddress.setVisibility(GONE);
        } else {
            storeFieldText.setText(selected.name);
            storeFieldText.setTextColor(COLOR_INK_900);
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
            TextView empty = Ui.text(getContext(), "Chưa có cửa hàng nhận hàng tại tỉnh/thành này", 14f, COLOR_GRAY_400, Ui.W_REGULAR);
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
            storeList.addView(Ui.text(getContext(), "Không tìm thấy cửa hàng", 14f, COLOR_GRAY_400, Ui.W_REGULAR), block(0));
            return;
        }
        for (Models.StoreDto st : shown) {
            boolean selected = selectedStoreId != null && st.id == selectedStoreId;
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(selected ? Ui.cardSelected(Ui.R_MD) : Ui.card(Ui.R_MD));
            row.setPadding(Ui.px(16), Ui.px(12), Ui.px(16), Ui.px(12));
            row.setClickable(true);
            Ui.pressEffect(row);
            row.setOnClickListener(v -> {
                storeOpen = false;
                callbacks.onSelectStore(st.id);
            });

            row.addView(radio(getContext(), selected), new LayoutParams(Ui.px(22), Ui.px(22)));

            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(VERTICAL);
            TextView name = Ui.text(getContext(), st.name, 14f, COLOR_INK_900, Ui.W_BOLD);
            col.addView(name, block(3));
            TextView address = Ui.text(getContext(), st.address == null ? "" : st.address, 12f, COLOR_GRAY_600, Ui.W_REGULAR);
            address.setMaxLines(2);
            address.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(address, block(3));
            String when = receiveText(st);
            if (when != null) col.addView(Ui.text(getContext(), when, 12f, COLOR_RED_BRAND, Ui.W_BOLD), block(0));
            LayoutParams colLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            colLp.setMarginStart(Ui.px(12));
            row.addView(col, colLp);
            storeList.addView(row, sized(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 8));
        }
    }

    private static Models.StoreDto selectedStore(KioskViewModel.UiState s) {
        if (s.stores != null && s.selectedStoreId != null) {
            for (Models.StoreDto st : s.stores) if (st.id == s.selectedStoreId) return st;
        }
        return null;
    }

    private void renderReceiveInfo(KioskViewModel.UiState s) {
        Models.StoreDto selected = selectedStore(s);
        if (selected == null) {
            dateText.setText("10/08/2026");
            dateText.setTextColor(COLOR_GRAY_600);
            hoursNote.setText("Khung giờ nhận: 09:00 - 20:30");
            hoursNote.setVisibility(VISIBLE);
            return;
        }
        String date = formatDate(selected.receiveDate);
        dateText.setText(date != null ? date : "10/08/2026");
        dateText.setTextColor(COLOR_GRAY_600);
        boolean hasHours = selected.pickupHours != null && !selected.pickupHours.isEmpty();
        hoursNote.setText(hasHours ? "Khung giờ nhận: " + selected.pickupHours : "Khung giờ nhận: 09:00 - 20:30");
        hoursNote.setVisibility(VISIBLE);
    }

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

    private static String formatMoneyVnd(double amount) {
        return String.format(Locale.US, "%,d VND", (long) amount);
    }

    private static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
    }

    // Figma Frame 1410067893: background #FFFFFF, border 1px solid #FBECD9, radius 24px
    private static GradientDrawable fieldBg() {
        return Ui.rect(Color.WHITE, Ui.R_PILL, COLOR_BORDER_CREAM, 1f);
    }

    // Figma: Title 18px semibold #000000
    private static View sectionTitle(Context context, String text) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(context, text, 18f, COLOR_INK_900, Ui.W_BOLD), new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    // Figma: * #AA1F23, Text 16px semibold #1A1A1A
    private static TextView requiredLabel(Context context, String text) {
        SpannableString sp = new SpannableString("* " + text);
        sp.setSpan(new ForegroundColorSpan(COLOR_RED_BRAND), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return Ui.text(context, sp, 16f, 0xFF1A1A1A, Ui.W_BOLD);
    }

    // Figma: Label 16px #535862, Value can phai
    private static View infoRow(Context context, String label, TextView value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(context, label, 16f, COLOR_GRAY_500, Ui.W_REGULAR),
                new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        value.setGravity(Gravity.END);
        row.addView(value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View stepButton(Context context, int icon) {
        FrameLayout b = new FrameLayout(context);
        b.addView(new IconView(context, icon, COLOR_INK_900).strokeUnits(1.5f),
                new FrameLayout.LayoutParams(Ui.px(14), Ui.px(14), Gravity.CENTER));
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