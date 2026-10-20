package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.databinding.ActivityHomeBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.ItemPriceRowBinding;
import vn.baotinmanhhai.btmhkiosk.ui.KioskViewModel;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man Home = BANG GIA VANG. Doi tu ban ve tay Canvas sang inflate dung activity_home.xml that qua ViewBinding,
 * de ket qua hien thi KHOP PIXEL voi app chuan thay vi mo phong bang Ui.px(). ivLogo/llPills trong layout that
 * bi an (GONE): {@link ShellView} da ve rieng hang dau logo + chao/dang nhap + hotline cho man Home tu truoc,
 * trung chuc nang voi 2 view nay neu de hien ca hai.
 */
public class HomeView extends FrameLayout implements Ui.InsetAware {

    public interface Callbacks {
        void onOpenProducts();

        void onSelectGoldProduct(String code);
    }

    private static final String PRICE_CONTACT = "Giá liên hệ"; // strings.xml: price_contact
    private static final DateTimeFormatter UPDATED_AT_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final ActivityHomeBinding binding;
    private final PriceRowAdapter adapter = new PriceRowAdapter();
    // Figma 1085:11214 ("Bang gia vang" DA DANG NHAP): them dong "Nhan de xem san pham" + nut thu 2 "San pham
    // trang suc" phia tren btnSavingsProducts - KHONG co trong activity_home.xml that (layout goc chi 1 trang
    // thai "chua login", xem ghi chu tren class) nen dung ve them bang code, chi hien khi da dang nhap.
    private final android.widget.TextView hintText;
    private final android.widget.TextView btnJewelry;

    public HomeView(Context context, Callbacks callbacks) {
        super(context);
        binding = ActivityHomeBinding.inflate(LayoutInflater.from(context), this, true);

        // ShellView ve rieng logo + pill chao/dang nhap + hotline cho Home -> an 2 view nay trong layout that
        // de khong trung lap (cardPrices/btnSavingsProducts van giu nguyen rang buoc constraint cua chung).
        binding.ivLogo.setVisibility(GONE);
        binding.llPills.setVisibility(GONE);

        binding.rvPrices.setLayoutManager(new LinearLayoutManager(context));
        binding.rvPrices.setAdapter(adapter);

        binding.btnSavingsProducts.setOnClickListener(v -> callbacks.onOpenProducts());

        androidx.constraintlayout.widget.ConstraintLayout root =
                (androidx.constraintlayout.widget.ConstraintLayout) binding.getRoot();

        hintText = new android.widget.TextView(context);
        hintText.setId(View.generateViewId());
        hintText.setText("Nhấn để xem sản phẩm");
        hintText.setTextColor(0xFF8A8A8A);
        hintText.setGravity(android.view.Gravity.CENTER);
        hintText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(10f));
        hintText.setVisibility(GONE);
        root.addView(hintText, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Ui.outlineButton() (vien do ruou, nen trang - giong nut "ĐĂNG KÝ" phu o CustomerAuthView) thay vi
        // primaryButton: 2 nut do ruou dac giong het nhau xep chong len nhau (truoc day) nhin nhu bi lap/loi bo
        // cuc. "San pham tich luy" (nut that trong activity_home.xml) giu nguyen la nut chinh (dac), "San pham
        // trang suc" (nut them rieng cho trang thai da dang nhap, khong co trong XML goc) la nut phu (vien).
        btnJewelry = Ui.outlineButton(context, "Sản phẩm trang sức", 20f);
        btnJewelry.setId(View.generateViewId());
        btnJewelry.setVisibility(GONE);
        // home_button_height that = 56dp (truoc day nham 100dp lam nut cao bat thuong).
        root.addView(btnJewelry, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(56)));

        // Rang buoc: hintText ngay tren hang nut, btnJewelry ngay tren btnSavingsProducts (nut cu that), hai nut
        // cach nhau Ui.px(16); cardPrices doi diem tua duoi tu btnSavingsProducts sang hintText khi da dang nhap.
        int parentId = androidx.constraintlayout.widget.ConstraintSet.PARENT_ID;
        androidx.constraintlayout.widget.ConstraintSet cs = new androidx.constraintlayout.widget.ConstraintSet();
        cs.clone(root);
        cs.connect(btnJewelry.getId(), androidx.constraintlayout.widget.ConstraintSet.START, parentId, androidx.constraintlayout.widget.ConstraintSet.START, Ui.px(32));
        cs.connect(btnJewelry.getId(), androidx.constraintlayout.widget.ConstraintSet.END, parentId, androidx.constraintlayout.widget.ConstraintSet.END, Ui.px(32));
        cs.connect(btnJewelry.getId(), androidx.constraintlayout.widget.ConstraintSet.BOTTOM, binding.btnSavingsProducts.getId(), androidx.constraintlayout.widget.ConstraintSet.TOP, Ui.px(16));
        cs.connect(hintText.getId(), androidx.constraintlayout.widget.ConstraintSet.START, parentId, androidx.constraintlayout.widget.ConstraintSet.START);
        cs.connect(hintText.getId(), androidx.constraintlayout.widget.ConstraintSet.END, parentId, androidx.constraintlayout.widget.ConstraintSet.END);
        cs.connect(hintText.getId(), androidx.constraintlayout.widget.ConstraintSet.BOTTOM, btnJewelry.getId(), androidx.constraintlayout.widget.ConstraintSet.TOP, Ui.px(12));
        cs.applyTo(root);
    }

    /**
     * Figma 1085:11214: them nut "San pham trang suc" + dong "Nhan de xem san pham" khi DA dang nhap (an khi
     * chua dang nhap, khop dung activity_home.xml that chi co 1 nut "San pham tich luy").
     */
    public void setLoggedIn(boolean loggedIn) {
        hintText.setVisibility(loggedIn ? VISIBLE : GONE);
        btnJewelry.setVisibility(loggedIn ? VISIBLE : GONE);
        androidx.constraintlayout.widget.ConstraintLayout root =
                (androidx.constraintlayout.widget.ConstraintLayout) binding.getRoot();
        androidx.constraintlayout.widget.ConstraintSet cs = new androidx.constraintlayout.widget.ConstraintSet();
        cs.clone(root);
        int anchor = loggedIn ? hintText.getId() : binding.btnSavingsProducts.getId();
        cs.connect(R.id.cardPrices, androidx.constraintlayout.widget.ConstraintSet.BOTTOM, anchor, androidx.constraintlayout.widget.ConstraintSet.TOP);
        cs.applyTo(root);
    }

    /** ShellView khong con dong phu duoi tieu de o man Home (thiet ke that: logo + loi chao/dang nhap + hotline,
     *  khong co dong phu bien dong gia theo ngay nua) — giu lai ham static de KioskActivity.java khoi phai sua. */
    public static String subtitle(KioskViewModel.UiState state) {
        return null;
    }

    @Override
    public void onInsets(int l, int t, int r, int b) {
        // llPills/ivLogo da GONE (ShellView tu lo phan an toan phia tren); chi can dem day man cho vung home_footer_bottom
        // that + inset he thong duoi (thanh dieu huong/ban phim).
        binding.getRoot().setPadding(l, 0, r, 0);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) binding.btnSavingsProducts.getLayoutParams();
        if (lp instanceof androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
            ((androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) lp).bottomMargin =
                    Ui.px(122) + b; // home_footer_bottom (dimens that) + inset he thong
            binding.btnSavingsProducts.setLayoutParams(lp);
        }
    }

    public void bind(KioskViewModel.UiState state) {
        List<Models.SsePriceItemDto> board = state.board;
        binding.tvUpdatedAt.setVisibility(state.boardUpdatedAt != null ? VISIBLE : GONE);
        if (state.boardUpdatedAt != null) {
            binding.tvUpdatedAt.setText("Cập nhật lúc " + formatUpdatedAt(state.boardUpdatedAt));
        }

        boolean hasRows = board != null && !board.isEmpty();
        binding.tvTableMessage.setVisibility(hasRows ? GONE : VISIBLE);
        binding.rvPrices.setVisibility(hasRows ? VISIBLE : GONE);
        if (!hasRows) return;

        List<PriceRowUi> rows = new ArrayList<>();
        int index = 0;
        for (Models.SsePriceItemDto item : board) {
            rows.add(PriceRowUi.from(item, index % 2 == 0));
            index++;
        }
        adapter.submitList(rows);
    }

    /** "2026-10-01T09:16:08.230098+07:00" (ISO, tu server) -> "01/10/2026 09:16:08" nhu man that; chuoi khong
     *  doc duoc (API tra khac dang) thi hien nguyen van thay vi crash. */
    private static String formatUpdatedAt(String iso) {
        try {
            return OffsetDateTime.parse(iso).format(UPDATED_AT_FORMAT);
        } catch (RuntimeException e) {
            return iso;
        }
    }

    /** Video banner da bo khoi man Home (khong con trong thiet ke that) — giu stub de khong phai sua
     *  vong doi onResume/onPause trong KioskActivity.java. */
    public void pauseVideo() {
    }

    public void resumeVideo() {
    }

    /** Du lieu da chuan hoa cho 1 dong bang gia (immutable, dung cho DiffUtil). */
    private static final class PriceRowUi {
        final String productId;
        final String name;
        final String buyPrice;
        final String sellPrice;
        final String buyTrend;
        final String sellTrend;
        final boolean striped;

        private PriceRowUi(String productId, String name, String buyPrice, String sellPrice,
                            String buyTrend, String sellTrend, boolean striped) {
            this.productId = productId;
            this.name = name;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.buyTrend = buyTrend;
            this.sellTrend = sellTrend;
            this.striped = striped;
        }

        static PriceRowUi from(Models.SsePriceItemDto item, boolean striped) {
            return new PriceRowUi(item.productId, item.name,
                    item.buyPrice == null ? null : Ui.number(item.buyPrice),
                    Ui.number(item.price), item.buyPriceTrend, item.priceTrend, striped);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PriceRowUi)) return false;
            PriceRowUi that = (PriceRowUi) o;
            return striped == that.striped && Objects.equals(productId, that.productId)
                    && Objects.equals(name, that.name) && Objects.equals(buyPrice, that.buyPrice)
                    && Objects.equals(sellPrice, that.sellPrice) && Objects.equals(buyTrend, that.buyTrend)
                    && Objects.equals(sellTrend, that.sellTrend);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, name, buyPrice, sellPrice, buyTrend, sellTrend, striped);
        }
    }

    /** Port Java 1:1 cua PriceRowAdapter.kt that: dong xen mau home_row_stripe/home_row_alt, mui ten xu huong
     *  INVISIBLE (khong GONE) khi SAME/null de gia khong bi lech cot. */
    private static final class PriceRowAdapter extends ListAdapter<PriceRowUi, PriceRowAdapter.ViewHolder> {

        private static final DiffUtil.ItemCallback<PriceRowUi> DIFF = new DiffUtil.ItemCallback<PriceRowUi>() {
            @Override
            public boolean areItemsTheSame(@NonNull PriceRowUi oldItem, @NonNull PriceRowUi newItem) {
                return Objects.equals(oldItem.productId, newItem.productId);
            }

            @Override
            public boolean areContentsTheSame(@NonNull PriceRowUi oldItem, @NonNull PriceRowUi newItem) {
                return oldItem.equals(newItem);
            }
        };

        PriceRowAdapter() {
            super(DIFF);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ItemPriceRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PriceRowUi row = getItem(position);
            ItemPriceRowBinding b = holder.binding;
            b.getRoot().setBackgroundColor(row.striped ? 0xFFF8ECDC : 0xFFFFFBF4); // home_row_stripe / home_row_alt
            b.tvName.setText(row.name);
            b.tvBuyPrice.setText(row.buyPrice != null ? row.buyPrice : PRICE_CONTACT);
            b.tvSellPrice.setText(row.sellPrice);
            bindTrend(b.ivBuyTrend, row.buyTrend);
            bindTrend(b.ivSellTrend, row.sellTrend);
        }

        private static void bindTrend(View view, String trend) {
            boolean up = "UP".equals(trend);
            boolean down = "DOWN".equals(trend);
            if (view instanceof android.widget.ImageView) {
                android.widget.ImageView iv = (android.widget.ImageView) view;
                if (up) iv.setImageResource(vn.baotinmanhhai.btmhkiosk.R.drawable.ic_trend_up);
                else if (down) iv.setImageResource(vn.baotinmanhhai.btmhkiosk.R.drawable.ic_trend_down);
                else iv.setImageDrawable(null);
            }
            view.setVisibility(up || down ? VISIBLE : INVISIBLE);
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final ItemPriceRowBinding binding;

            ViewHolder(ItemPriceRowBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
