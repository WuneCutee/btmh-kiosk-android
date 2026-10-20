package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.databinding.ItemProductCardBinding;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * The san pham — doi tu ve tay Canvas sang inflate dung item_product_card.xml that qua ViewBinding, de khop
 * pixel voi app chuan (truoc day mo phong bang Ui.px(), tung bi lech ti le/khoang cach nhieu lan).
 */
public class ProductCardView extends FrameLayout {

    /** Stepper dem so luong [ - n + ] duoi gia — cham thang vao gio hang, khong qua man Chi tiet. */
    public interface StepperListener {
        void onIncrement();

        void onDecrement();
    }

    private final ItemProductCardBinding binding;
    private float restAlpha = 1f;

    public ProductCardView(Context context) {
        super(context);
        binding = ItemProductCardBinding.inflate(LayoutInflater.from(context), this, true);
        binding.etCount.setFocusable(false); // Hien thi nhu nhan, khong go tay truc tiep (giu hanh vi cu, chi doi giao dien).
        binding.etCount.setClickable(false);
    }

    /** item_product_card.xml that: GOC inflate (binding.getRoot()) moi la view clickable/foreground that - dat
     * listener o day thay vi o chinh ProductCardView (outer wrapper), neu khong cham khong an duoc gi vi root
     * trong da nuot het su kien cham truoc khi noi len outer. */
    @Override
    public void setOnClickListener(OnClickListener l) {
        binding.getRoot().setOnClickListener(l);
    }

    /** So luong dang co trong gio hang cua san pham nay + gioi han toi da (0 = khong con mua duoc them). */
    public void bindQuantity(int quantity, int maxQuantity, StepperListener listener) {
        binding.etCount.setText(String.valueOf(quantity));
        binding.tvMinus.setAlpha(quantity > 0 ? 1f : 0.3f);
        binding.tvMinus.setOnClickListener(quantity > 0 ? v -> listener.onDecrement() : null);
        boolean canAdd = quantity < maxQuantity;
        binding.tvPlus.setAlpha(canAdd ? 1f : 0.3f);
        // Truoc day canAdd=false thi set listener=null (bam khong co phan hoi gi) - gio LUON giu listener de
        // tap van bao len onIncrement(), noi se tu kiem tra lai va BAO RO ly do (popup han muc/het ton) thay
        // vi im lang khoa nut (yeu cau 15/10/2026).
        binding.tvPlus.setOnClickListener(v -> listener.onIncrement());
    }

    /** Do mo "nghi" cua the (het hang mo hon) — adapter dung khi chay hieu ung hien dan. */
    public float restAlpha() {
        return restAlpha;
    }

    public void bind(Models.ProductDto product, boolean pricesHidden) {
        binding.tvName.setText(product.name);
        // Gia cu qua stalePriceSeconds -> an gia (spec II.3.4): "Dang cap nhat gia…".
        if (pricesHidden) {
            binding.tvPrice.setText("Đang cập nhật giá…");
            binding.tvPrice.setTextColor(Ui.TEXT_MUTED_LIGHT);
        } else {
            binding.tvPrice.setText(Ui.number(product.price) + " VND");
            binding.tvPrice.setTextColor(getResources().getColor(R.color.brand_red, getContext().getTheme()));
        }
        binding.tvSoldOut.setVisibility(product.isSoldOut() ? VISIBLE : GONE);
        restAlpha = product.isSoldOut() ? 0.65f : 1f;
        setAlpha(restAlpha);

        String url = product.getPrimaryImageUrl();
        View placeholder = binding.vImagePlaceholder;
        if (url == null || url.isEmpty()) {
            Glide.with(binding.ivImage).clear(binding.ivImage);
            binding.ivImage.setColorFilter(Ui.SIDEBAR_GOLD_BOT, PorterDuff.Mode.SRC_IN);
            int pad = Ui.px(20);
            binding.ivImage.setPadding(pad, pad, pad, pad);
            binding.ivImage.setImageResource(R.drawable.logo);
            placeholder.setVisibility(GONE);
        } else {
            binding.ivImage.clearColorFilter();
            binding.ivImage.setPadding(0, 0, 0, 0);
            placeholder.setVisibility(GONE);
            Glide.with(binding.ivImage).load(url).fitCenter()
                    .transition(DrawableTransitionOptions.withCrossFade(220))
                    .into(binding.ivImage);
        }
    }
}
