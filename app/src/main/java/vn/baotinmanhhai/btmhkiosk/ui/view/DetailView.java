package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man chi tiet san pham (spec II.2.8), nam trong khung {@link ShellView} (nut "Quay lai" + tieu de do thanh dau cua khung).
 * Cuon duoc: MOT the trang chinh (tag do "San pham", ten, GIA BAN noi bat mau cam, o anh ep vi can giua — vuot xem nhieu anh,
 * ma san pham, mo ta) va ngay duoi the la cum "Thong tin mua hang": chon so luong bang stepper [ - ] [ n ] [ + ] (toi da theo
 * han muc giao dich / ton kho, mac dinh 10), ghi chu han muc va dong "Gia tri don hang" = gia x so luong (cap nhat tuc thi).
 * Thanh nut co dinh o day: "THEM VAO GIO HANG" (Coming soon) + "DAT MUA" (pill do ruou; mo man xac nhan don hang voi so luong da chon).
 * Het hang / ngoai gio ban / dang cap nhat gia -> nut chinh xam, khong bam duoc (giu nguyen chu cu).
 */
public class DetailView extends LinearLayout implements Ui.InsetAware {

    public interface Callbacks {
        /** "THEM VAO GIO HANG": Phase 1 chua co gio hang -> chi bao "Coming soon". */
        void onAddToCart(Models.ProductDto product, int quantity);

        /** "DAT MUA": mo man xac nhan don hang voi so luong da chon; totalAmount = don gia x so luong. */
        void onBuyNow(Models.ProductDto product, int quantity, double totalAmount);
    }

    /** So luong toi da mac dinh moi giao dich khi server khong tra han muc. */
    public static final int DEFAULT_MAX_QUANTITY = 10;

    private static final int IMAGE_H = 280; // px thiet ke
    private static final int STEP = 64;     // nut stepper >= 64 (cham duoc de dang)

    private final Callbacks callbacks;
    private final ImagePagerView pager;
    private final TextView nameView;
    private final TextView weightChip;
    private final TextView soldOutBadge;
    private final TextView priceView;
    private final TextView codeView;
    private final TextView descriptionView;
    private final TextView qtyValue;
    private final View minus;
    private final View plus;
    private final TextView limitNote;
    private final TextView totalValue;
    private final TextView buyButton;
    private final LinearLayout actions;

    private Models.ProductDto product;
    private String boundProductId;
    private boolean pricesHidden;
    private boolean canBuy;
    private int quantity = 1;
    private int maxQuantity = DEFAULT_MAX_QUANTITY;

    public DetailView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        setOrientation(VERTICAL);

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(VERTICAL);
        body.setPadding(Ui.px(14), Ui.px(14), Ui.px(14), Ui.px(18));
        scroll.addView(body, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        // ================= THE TRANG CHINH (theo mockup Figma "Man hinh chi tiet san pham_Con hang": anh truoc, ten
        // duoi anh, roi khoi "Thong tin san pham" dang hang gia tri thay vi gia to + tag o dau) =================
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        card.setBackground(Ui.card(Ui.R_LG));
        card.setPadding(Ui.px(18), Ui.px(18), Ui.px(18), Ui.px(20));
        Ui.softShadow(card);

        // O anh ep vi: nen ngà, vien nhe, anh can giua (vuot xem them anh)
        FrameLayout imageFrame = new FrameLayout(context);
        imageFrame.setBackground(Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_MD, Ui.BORDER_LIGHT, 1));
        Ui.clipRounded(imageFrame, Ui.R_MD);
        pager = new ImagePagerView(context);
        imageFrame.addView(pager, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        card.addView(imageFrame, new LayoutParams(LayoutParams.MATCH_PARENT, Ui.px(IMAGE_H)));

        nameView = Ui.text(context, "", 13.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        nameView.setLineSpacing(0, 1.12f);
        card.addView(nameView, block(16));

        soldOutBadge = Ui.text(context, "HẾT HÀNG", 8f, Ui.SURFACE_WHITE, Ui.W_BOLD);
        soldOutBadge.setBackground(Ui.rect(Ui.DOWN_RED, Ui.R_PILL, 0, 0));
        soldOutBadge.setPadding(Ui.px(12), Ui.px(5), Ui.px(12), Ui.px(6));
        card.addView(soldOutBadge, block(10));

        View div0 = new View(context);
        div0.setBackgroundColor(Ui.BORDER_LIGHT);
        LayoutParams div0Lp = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1)));
        div0Lp.topMargin = Ui.px(14);
        card.addView(div0, div0Lp);

        card.addView(Ui.text(context, "Thông tin sản phẩm", 10.5f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD), block(14));
        priceView = Ui.text(context, "", 11.5f, Ui.PRICE_ORANGE, Ui.W_BOLD);
        priceView.setSingleLine(true);
        card.addView(infoRow(context, "Giá bán", priceView), block(12));
        codeView = Ui.text(context, "", 10f, Ui.TEXT_TITLE_DARK, Ui.W_MEDIUM);
        card.addView(infoRow(context, "Mã sản phẩm", codeView), block(12));
        weightChip = Ui.text(context, "", 10f, Ui.TEXT_TITLE_DARK, Ui.W_MEDIUM);
        card.addView(infoRow(context, "Định lượng (chỉ)", weightChip), block(16));

        descriptionView = Ui.text(context, "", 9.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        descriptionView.setLineSpacing(0, 1.25f);
        card.addView(descriptionView, block(0));

        body.addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // ================= THONG TIN MUA HANG =================
        LinearLayout buyInfo = new LinearLayout(context);
        buyInfo.setOrientation(VERTICAL);
        buyInfo.setPadding(Ui.px(6), 0, Ui.px(6), 0);

        buyInfo.addView(Ui.text(context, "Thông tin mua hàng", 10.5f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD),
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        LinearLayout qtyRow = new LinearLayout(context);
        qtyRow.setOrientation(HORIZONTAL);
        qtyRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout qtyLeft = new LinearLayout(context);
        qtyLeft.setOrientation(VERTICAL);
        LinearLayout labelRow = new LinearLayout(context);
        labelRow.setOrientation(HORIZONTAL);
        labelRow.addView(Ui.text(context, "*", 10f, Ui.DOWN_RED, Ui.W_BOLD),
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        TextView qtyLabel = Ui.text(context, "Số lượng sản phẩm:", 10f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        LayoutParams qlLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        qlLp.setMarginStart(Ui.px(4));
        labelRow.addView(qtyLabel, qlLp);
        qtyLeft.addView(labelRow, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        LinearLayout noteRow = new LinearLayout(context);
        noteRow.setOrientation(HORIZONTAL);
        noteRow.setGravity(Gravity.TOP);
        TextView infoIcon = Ui.text(context, "i", 7.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_BOLD);
        infoIcon.setGravity(Gravity.CENTER);
        infoIcon.setBackground(Ui.ovalStroke(Ui.SURFACE_WHITE, Ui.TEXT_MUTED_LIGHT, 1.2f));
        LayoutParams iiLp = new LayoutParams(Ui.px(20), Ui.px(20));
        iiLp.topMargin = Ui.px(1);
        noteRow.addView(infoIcon, iiLp);
        limitNote = Ui.text(context, "", 8.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        limitNote.setLineSpacing(0, 1.15f);
        LayoutParams lnLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        lnLp.setMarginStart(Ui.px(8));
        noteRow.addView(limitNote, lnLp);
        LayoutParams nrLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        nrLp.topMargin = Ui.px(8);
        qtyLeft.addView(noteRow, nrLp);
        LayoutParams qlcLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        qlcLp.setMarginEnd(Ui.px(14));
        qtyRow.addView(qtyLeft, qlcLp);

        // Stepper pill: [ - ] [ so luong ] [ + ]
        LinearLayout stepper = new LinearLayout(context);
        stepper.setOrientation(HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setBackground(Ui.rect(Ui.STEPPER_BG, Ui.R_PILL, Ui.BORDER_LIGHT, 1.5f));
        minus = stepButton(context, IconView.MINUS);
        minus.setOnClickListener(v -> changeQuantity(-1));
        stepper.addView(minus, new LayoutParams(Ui.px(STEP), Ui.px(STEP)));
        qtyValue = Ui.text(context, "1", 14f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        qtyValue.setGravity(Gravity.CENTER);
        stepper.addView(qtyValue, new LayoutParams(Ui.px(64), LayoutParams.WRAP_CONTENT));
        plus = stepButton(context, IconView.PLUS);
        plus.setOnClickListener(v -> changeQuantity(1));
        stepper.addView(plus, new LayoutParams(Ui.px(STEP), Ui.px(STEP)));
        qtyRow.addView(stepper, new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.px(STEP)));
        LayoutParams qrLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        qrLp.topMargin = Ui.px(14);
        buyInfo.addView(qtyRow, qrLp);

        View div = new View(context);
        div.setBackgroundColor(Ui.BORDER_LIGHT);
        LayoutParams divLp = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1)));
        divLp.setMargins(0, Ui.px(16), 0, Ui.px(14));
        buyInfo.addView(div, divLp);

        LinearLayout totalRow = new LinearLayout(context);
        totalRow.setOrientation(HORIZONTAL);
        totalRow.setGravity(Gravity.CENTER_VERTICAL);
        totalRow.addView(Ui.text(context, "Giá trị", 10f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR),
                new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        totalValue = Ui.text(context, "", 13f, Ui.PRICE_ORANGE, Ui.W_BOLD);
        totalValue.setGravity(Gravity.END);
        totalRow.addView(totalValue, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        buyInfo.addView(totalRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LayoutParams biLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        biLp.topMargin = Ui.px(20);
        body.addView(buyInfo, biLp);

        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // ================= THANH NUT CO DINH O DAY =================
        actions = new LinearLayout(context);
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setBackgroundColor(Ui.SURFACE_WHITE);
        actions.setPadding(Ui.px(14), Ui.px(14), Ui.px(14), Ui.px(14));
        Ui.elevate(actions, 6, 0xFF6B4A2B);

        TextView cartButton = Ui.text(context, "THÊM VÀO GIỎ HÀNG", 10f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        cartButton.setGravity(Gravity.CENTER);
        cartButton.setLetterSpacing(0.04f);
        cartButton.setBackground(Ui.pressable(
                Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, Ui.withAlpha(Ui.BURGUNDY_PRIMARY, 0x70), 1.5f),
                Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.withAlpha(Ui.BURGUNDY_PRIMARY, 0x70), 1.5f)));
        Ui.pressEffect(cartButton);
        cartButton.setOnClickListener(v -> {
            if (product != null) callbacks.onAddToCart(product, quantity);
        });
        LayoutParams cartLp = new LayoutParams(0, Ui.px(64), 5f);
        cartLp.setMarginEnd(Ui.px(12));
        actions.addView(cartButton, cartLp);

        buyButton = Ui.primaryButton(context, "MUA HÀNG", 12f);
        buyButton.setOnClickListener(v -> {
            if (canBuy && product != null) {
                callbacks.onBuyNow(product, quantity, product.price * quantity);
            }
        });
        actions.addView(buyButton, new LayoutParams(0, Ui.px(64), 4f));
        addView(actions, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /** Hang "nhan muted ben trai -> gia tri dam ben phai" trong khoi "Thong tin san pham". */
    private static View infoRow(Context context, String label, TextView value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(context, label, 9.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR),
                new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        value.setGravity(Gravity.END);
        row.addView(value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static LayoutParams block(int topMarginDesign) {
        LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.px(topMarginDesign);
        return lp;
    }

    /** Nut tron/vuong cua stepper: icon +/- o giua, vung cham STEP x STEP. */
    private static View stepButton(Context context, int icon) {
        FrameLayout f = new FrameLayout(context);
        f.setClickable(true);
        f.addView(new IconView(context, icon, Ui.BURGUNDY_PRIMARY).strokeUnits(2.6f),
                new FrameLayout.LayoutParams(Ui.px(26), Ui.px(26), Gravity.CENTER));
        Ui.pressEffect(f);
        return f;
    }

    /** Vung an toan phia tren do thanh dau cua khung lo; o day chi con thanh dieu huong phia duoi. */
    @Override
    public void onInsets(int l, int t, int r, int b) {
        setPadding(l, 0, r, 0);
        actions.setPadding(Ui.px(14), Ui.px(14), Ui.px(14), Ui.px(14) + b);
    }

    /** Vao man Chi tiet tu danh sach: dat lai so luong ve 1 (khong dung khi quay lai tu man xac nhan). */
    public void resetQuantity() {
        quantity = 1;
    }

    /**
     * pricesHidden: gia cu qua stalePriceSeconds -> an gia. sellingOpen: trong khung gio mo ban (theo gio server);
     * ngoai gio ban hoac het hang -> khong cho DAT MUA. limitQuantity: han muc/giao dich (don vi chi) — null hoac <= 0 = khong
     * co han muc -> toi da DEFAULT_MAX_QUANTITY san pham; ton kho con lai (remainingQuantity) la can tren them.
     */
    public void bind(Models.ProductDto product, boolean pricesHidden, boolean sellingOpen, Double limitQuantity) {
        this.product = product;
        this.pricesHidden = pricesHidden;
        if (!product.id.equals(boundProductId)) {
            boundProductId = product.id;
            quantity = 1;
        }

        pager.bind(product);
        nameView.setText(product.name);
        weightChip.setText(Ui.quantity(product.quantity));
        boolean hasCode = product.code != null && !product.code.isEmpty();
        codeView.setText(hasCode ? product.code : "—");

        boolean hasDescription = product.description != null && !product.description.trim().isEmpty();
        descriptionView.setText(hasDescription ? product.description : "Thông tin chi tiết đang được cập nhật.");

        boolean soldOut = product.isSoldOut();
        soldOutBadge.setVisibility(soldOut ? View.VISIBLE : View.GONE);
        canBuy = !soldOut && sellingOpen && !pricesHidden;
        buyButton.setText(soldOut ? "HẾT HÀNG" : (!sellingOpen ? "NGỪNG GIAO DỊCH" : "MUA HÀNG"));
        buyButton.setTextColor(canBuy ? Ui.SURFACE_WHITE : Ui.TEXT_MUTED_LIGHT);
        buyButton.setBackground(canBuy
                ? Ui.pressable(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0), Ui.rect(Ui.BURGUNDY_DARK, Ui.R_PILL, 0, 0))
                : Ui.rect(Ui.BORDER_LIGHT, Ui.R_PILL, 0, 0));
        buyButton.setElevation(canBuy ? Ui.px(4) : 0f);

        // Gioi han so luong: han muc (mac dinh 10) va ton kho. Het hang -> stepper khoa (max 0).
        int byLimit = DEFAULT_MAX_QUANTITY;
        if (limitQuantity != null && limitQuantity > 0 && product.quantity > 0) {
            byLimit = (int) Math.floor(limitQuantity / product.quantity + 1e-9);
        }
        int byStock = product.remainingQuantity != null ? product.remainingQuantity : Integer.MAX_VALUE;
        maxQuantity = Math.max(0, Math.min(byLimit, byStock));
        // Vuot han muc ngay tu 1 san pham (nhung chua het hang): van cho mo man xac nhan, man do se bao "khong the dat" nhu cu.
        quantity = Math.max(1, Math.min(quantity, Math.max(1, maxQuantity)));

        if (soldOut) {
            limitNote.setText("Sản phẩm hiện đã hết hàng");
        } else if (maxQuantity == 0) {
            limitNote.setText("Sản phẩm vượt hạn mức giao dịch của khách hàng");
        } else {
            limitNote.setText("Khách hàng được mua tối đa " + maxQuantity + " sản phẩm/giao dịch");
        }
        renderQuantity();
    }

    private void changeQuantity(int delta) {
        int next = quantity + delta;
        if (next < 1 || next > maxQuantity) return;
        quantity = next;
        renderQuantity();
    }

    /** Cap nhat so luong, do mo cua +/- va "Gia tri don hang" = don gia x so luong. */
    private void renderQuantity() {
        qtyValue.setText(String.valueOf(quantity));
        minus.setAlpha(quantity > 1 ? 1f : 0.35f);
        plus.setAlpha(quantity < maxQuantity ? 1f : 0.35f);
        if (product == null) return;
        if (pricesHidden) {
            priceView.setText("Đang cập nhật giá…");
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(11f));
            priceView.setTextColor(Ui.TEXT_MUTED_LIGHT);
            totalValue.setText("Đang cập nhật giá…");
            totalValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(10f));
            totalValue.setTextColor(Ui.TEXT_MUTED_LIGHT);
        } else {
            priceView.setText(Ui.bigPrice(product.price, 0.65f));
            priceView.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(16f));
            priceView.setTextColor(Ui.PRICE_ORANGE);
            totalValue.setText(Ui.bigPrice(product.price * quantity, 0.75f));
            totalValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.textPx(13f));
            totalValue.setTextColor(Ui.PRICE_ORANGE);
        }
    }
}
