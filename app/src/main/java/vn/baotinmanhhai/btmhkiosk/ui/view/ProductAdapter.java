package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vn.baotinmanhhai.btmhkiosk.data.Models;

/**
 * Luoi san pham co phan trang (cuon toi gan cuoi tu goi onLoadMore() — khong co dong "het danh sach" rieng,
 * giong het app chuan: strings.xml that khong co chuoi nay). The san pham gio inflate item_product_card.xml
 * that qua {@link ProductCardView} thay vi tu ve Canvas.
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.Holder> {

    public interface OnProductClick {
        void onClick(Models.ProductDto product);
    }

    /** Stepper [-/+] ngay tren the luoi: them/bot 1 san pham khoi gio hang. */
    public interface OnStepperChange {
        void onIncrement(Models.ProductDto product);

        void onDecrement(Models.ProductDto product);
    }

    public static final int COLUMNS = 3;

    private final List<Models.ProductDto> items = new ArrayList<>();
    private final OnProductClick onClick;
    private final OnStepperChange onStepperChange;
    private boolean pricesHidden;
    private Map<String, Integer> cartQuantities = new HashMap<>();
    private Double limitQuantity; // han muc chung (chi) cho toan gio hang; null = khong gioi han
    private double totalCartUnits; // tong so chi TAT CA san pham dang co trong gio (tru rieng ra khi tinh tung the)

    public ProductAdapter(OnProductClick onClick, OnStepperChange onStepperChange) {
        this.onClick = onClick;
        this.onStepperChange = onStepperChange;
    }

    /** Goi tu ProductsView.bind(): so luong dang co trong gio + han muc chung, de ve dung stepper tren moi the. */
    public void setCartState(Map<String, Integer> cartQuantities, Double limitQuantity, double totalCartUnits) {
        this.cartQuantities = cartQuantities != null ? cartQuantities : new HashMap<>();
        this.limitQuantity = limitQuantity;
        this.totalCartUnits = totalCartUnits;
        notifyDataSetChanged();
    }

    public void submitList(List<Models.ProductDto> newItems, boolean hasMore, boolean pricesHidden) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        this.pricesHidden = pricesHidden;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ProductCardView card = new ProductCardView(parent.getContext());
        card.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        return new Holder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Models.ProductDto product = items.get(position);
        holder.card.bind(product, pricesHidden);
        holder.card.setOnClickListener(v -> onClick.onClick(product));

        int inCart = cartQuantities.getOrDefault(product.id, 0);
        int maxQty = computeMaxQuantity(product, inCart);
        holder.card.bindQuantity(inCart, maxQty, new ProductCardView.StepperListener() {
            @Override
            public void onIncrement() {
                onStepperChange.onIncrement(product);
            }

            @Override
            public void onDecrement() {
                onStepperChange.onDecrement(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** X = floor((han muc - so chi cac san pham KHAC trong gio) / ban vi), gioi han them boi ton kho (giong OrderConfirmView). */
    private int computeMaxQuantity(Models.ProductDto product, int inCart) {
        int byStock = product.remainingQuantity != null ? product.remainingQuantity : Integer.MAX_VALUE;
        if (limitQuantity == null || product.quantity <= 0) return Math.max(0, byStock);
        double otherUnits = totalCartUnits - inCart * product.quantity;
        int byLimit = (int) Math.floor((limitQuantity - otherUnits) / product.quantity + 1e-9);
        return Math.max(0, Math.min(byLimit, byStock));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ProductCardView card;

        Holder(ProductCardView card) {
            super(card);
            this.card = card;
        }
    }
}
