package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.databinding.ActivityProductDetailBinding;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man chi tiet san pham (spec II.2.8) — doi tu ve tay Canvas sang inflate dung activity_product_detail.xml
 * that qua ViewBinding. Anh san pham dung lai {@link ImagePagerView} da co (RecyclerView+PagerSnapHelper,
 * tuong duong ViewPager2 ve ket qua hien thi) thay vi pagerImages/dots that — ca hai bi an (GONE), ImagePagerView
 * chen vao dung vi tri trong the MaterialCardView.
 */
public class DetailView extends FrameLayout implements Ui.InsetAware {

    public interface Callbacks {
        void onAddToCart(Models.ProductDto product, int quantity);

        /** "MUA HÀNG": mo man xac nhan don hang voi so luong da chon; totalAmount = don gia x so luong. */
        void onBuyNow(Models.ProductDto product, int quantity, double totalAmount);
    }

    /** So luong toi da mac dinh moi giao dich khi server khong tra han muc. */
    public static final int DEFAULT_MAX_QUANTITY = 10;

    private final Callbacks callbacks;
    private final ActivityProductDetailBinding binding;
    private final ImagePagerView pager;

    private Models.ProductDto product;
    private String boundProductId;
    private boolean pricesHidden;
    private boolean canBuy;
    private int quantity = 1;
    private int maxQuantity = DEFAULT_MAX_QUANTITY;
    private Double limitQuantity; // han muc that (chi) tu server; null = dung DEFAULT_MAX_QUANTITY (khong phai han muc that)
    private Integer limitByChi; // maxQuantity tinh rieng tu limitQuantity (khong xet ton kho) - de biet dung popup nao

    public DetailView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        binding = ActivityProductDetailBinding.inflate(LayoutInflater.from(context), this, true);
        binding.header.setVisibility(GONE); // ShellView da ve rieng header (back/tieu de/home) cho khung chung.
        binding.scroll.setVisibility(VISIBLE);
        binding.actions.setVisibility(VISIBLE);

        binding.pagerImages.setVisibility(GONE);
        binding.dots.setVisibility(GONE);
        pager = new ImagePagerView(context);
        android.view.ViewGroup imageCard = (android.view.ViewGroup) binding.pagerImages.getParent();
        imageCard.addView(pager, 0, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT, android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        binding.btnMinus.setOnClickListener(v -> changeQuantity(-1));
        binding.btnPlus.setOnClickListener(v -> changeQuantity(1));
        binding.etCount.setFocusable(false);
        binding.etCount.setClickable(false);

        binding.btnAddToCart.setOnClickListener(v -> {
            if (product != null) callbacks.onAddToCart(product, quantity);
        });
        binding.btnBuyNow.setOnClickListener(v -> {
            if (canBuy && product != null) callbacks.onBuyNow(product, quantity, product.price * quantity);
        });
    }

    /** "08:30:00" -> "08:30" (server tra HH:mm:ss, hien thi chi can HH:mm). */
    private static String shortTime(String hhmmss) {
        return hhmmss.length() >= 5 ? hhmmss.substring(0, 5) : hhmmss;
    }

    @Override
    public void onInsets(int l, int t, int r, int b) {
        binding.getRoot().setPadding(l, 0, r, 0);
        binding.actions.setPadding(binding.actions.getPaddingLeft(), binding.actions.getPaddingTop(),
                binding.actions.getPaddingRight(), Ui.px(19) + b);
    }

    /** Vao man Chi tiet tu danh sach: dat lai so luong ve 1 (khong dung khi quay lai tu man xac nhan). */
    public void resetQuantity() {
        quantity = 1;
    }

    /**
     * pricesHidden: gia cu qua stalePriceSeconds -> an gia. sellingOpen: trong khung gio mo ban (theo gio server);
     * ngoai gio ban hoac het hang -> khong cho MUA HANG. limitQuantity: han muc/giao dich (don vi chi) — null hoac <= 0 = khong
     * co han muc -> toi da DEFAULT_MAX_QUANTITY san pham; ton kho con lai (remainingQuantity) la can tren them.
     */
    public void bind(Models.ProductDto product, boolean pricesHidden, boolean sellingOpen, Double limitQuantity,
                      String openTime, String closeTime) {
        this.product = product;
        this.pricesHidden = pricesHidden;
        if (!product.id.equals(boundProductId)) {
            boundProductId = product.id;
            quantity = 1;
        }

        pager.bind(product);
        binding.tvName.setText(product.name);
        binding.tvChi.setText(Ui.quantity(product.quantity));
        boolean hasCode = product.code != null && !product.code.isEmpty();
        binding.tvCode.setText(hasCode ? product.code : "—");

        boolean hasDescription = product.description != null && !product.description.trim().isEmpty();
        binding.tvDescription.setText(hasDescription ? product.description : "Thông tin chi tiết đang được cập nhật.");

        boolean soldOut = product.isSoldOut();
        canBuy = !soldOut && sellingOpen && !pricesHidden;

        if (!soldOut && !sellingOpen && openTime != null && closeTime != null) {
            binding.tvTradingNotice.setText("Thời gian mở bán: từ " + shortTime(openTime) + " đến " + shortTime(closeTime));
            binding.tradingNotice.setVisibility(VISIBLE);
        } else {
            binding.tradingNotice.setVisibility(GONE);
        }

        // 3 nut that (bg_pill_red/bg_pill_outline_red/bg_pill_sold_out dinh san mau that trong XML, khong can tu chon mau):
        // "THÊM VÀO GIỎ HÀNG" luon hien; "MUA HÀNG" hien khi con mua duoc, nguoc lai "HẾT HÀNG"/"NGỪNG GIAO DỊCH".
        binding.btnBuyNow.setVisibility(canBuy ? VISIBLE : GONE);
        binding.btnBlocked.setVisibility(canBuy ? GONE : VISIBLE);
        if (!canBuy) binding.btnBlocked.setText(soldOut ? "HẾT HÀNG" : "NGỪNG GIAO DỊCH");

        // Gioi han so luong: han muc (mac dinh 10) va ton kho. Het hang -> stepper khoa (max 0).
        this.limitQuantity = limitQuantity;
        int byLimit = DEFAULT_MAX_QUANTITY;
        if (limitQuantity != null && limitQuantity > 0 && product.quantity > 0) {
            byLimit = (int) Math.floor(limitQuantity / product.quantity + 1e-9);
        }
        this.limitByChi = limitQuantity != null ? byLimit : null;
        int byStock = product.remainingQuantity != null ? product.remainingQuantity : Integer.MAX_VALUE;
        maxQuantity = Math.max(0, Math.min(byLimit, byStock));
        quantity = Math.max(1, Math.min(quantity, Math.max(1, maxQuantity)));

        if (soldOut) {
            binding.tvMaxHint.setText("Sản phẩm hiện đã hết hàng");
        } else if (maxQuantity == 0) {
            binding.tvMaxHint.setText("Sản phẩm vượt hạn mức giao dịch của khách hàng");
        } else {
            binding.tvMaxHint.setText("Khách hàng được mua tối đa " + maxQuantity + " sản phẩm/giao dịch");
        }
        renderQuantity();
    }

    private void changeQuantity(int delta) {
        int next = quantity + delta;
        if (next < 1) return;
        if (next > maxQuantity) {
            // Truoc day im lang (nut da tu khoa alpha, nhung bam vao via vung van khong lam gi) - gio bao ro
            // ly do (yeu cau 15/10/2026). CHI hien popup han muc khi co han muc THAT tu server (limitQuantity
            // != null) - khong bia so "X chi vang" neu dang dung DEFAULT_MAX_QUANTITY gia dinh (khong phai
            // han muc that nen khong co gi de noi ve "chi vang" ca, luc do chi co the la do het ton).
            if (delta > 0 && product != null) {
                if (limitQuantity != null && limitByChi != null && quantity >= limitByChi) {
                    OrderPopups.showPurchaseLimit(getContext(), limitQuantity, product.name, limitByChi,
                            () -> { quantity = Math.min(limitByChi, maxQuantity); renderQuantity(); }, null);
                } else if (product.remainingQuantity != null) {
                    // So that/quyet dinh chan boi ton kho that (khong phai han muc, khong phai fallback gia dinh).
                    OrderPopups.showOutOfStock(getContext(), product.name, maxQuantity,
                            () -> { quantity = Math.max(1, maxQuantity); renderQuantity(); }, null);
                }
            }
            return;
        }
        quantity = next;
        renderQuantity();
    }

    /** Cap nhat so luong, do mo cua +/- va "Gia tri don hang" = don gia x so luong. */
    private void renderQuantity() {
        binding.etCount.setText(String.valueOf(quantity));
        binding.btnMinus.setAlpha(quantity > 1 ? 1f : 0.35f);
        binding.btnPlus.setAlpha(quantity < maxQuantity ? 1f : 0.35f);
        if (product == null) return;
        if (pricesHidden) {
            binding.tvPrice.setText("Đang cập nhật giá…");
            binding.tvPrice.setTextColor(Ui.TEXT_MUTED_LIGHT);
            binding.tvTotal.setText("Đang cập nhật giá…");
            binding.tvTotal.setTextColor(Ui.TEXT_MUTED_LIGHT);
        } else {
            binding.tvPrice.setText(Ui.number(product.price) + " VND");
            binding.tvPrice.setTextColor(Ui.BURGUNDY_PRIMARY);
            binding.tvTotal.setText(Ui.number(product.price * quantity) + " VND");
            binding.tvTotal.setTextColor(Ui.BURGUNDY_PRIMARY);
        }
    }
}
