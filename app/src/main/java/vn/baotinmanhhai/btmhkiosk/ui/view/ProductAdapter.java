package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Luoi san pham co phan trang. 2 cot trong vung noi dung (CANVAS_W = 640 - rail): cardWidth = (CANVAS_W - SPACING) / 2 - SPACING
 * cao = cardWidth * 1.3. Dong cuoi (chiem het chieu ngang): "Dang tai them…" khi con trang,
 * "Ban da xem het danh sach san pham" khi het (II.2.6). Khi danh sach moi hien ra, cac the hien dan le tung
 * cai (stagger) — chi cho vi tri chua tung hien.
 */
public class ProductAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnProductClick {
        void onClick(Models.ProductDto product);
    }

    /** Stepper [-/+] ngay tren the luoi: them/bot 1 san pham khoi gio hang. */
    public interface OnStepperChange {
        void onIncrement(Models.ProductDto product);

        void onDecrement(Models.ProductDto product);
    }

    public static final int COLUMNS = 3;
    public static final int SPACING = 14;
    public static final int CARD_W = (int) ((Ui.CANVAS_W - SPACING) / COLUMNS - SPACING);
    public static final int CARD_H = (int) (CARD_W * 1.3);
    public static final int INFO_H = 100;
    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    private final List<Models.ProductDto> items = new ArrayList<>();
    private final OnProductClick onClick;
    private final OnStepperChange onStepperChange;
    private boolean hasMore;
    private boolean pricesHidden;
    private Map<String, Integer> cartQuantities = new HashMap<>();
    private Double limitQuantity; // han muc chung (chi) cho toan gio hang; null = khong gioi han
    private double totalCartUnits; // tong so chi TAT CA san pham dang co trong gio (tru rieng ra khi tinh tung the)
    /** Vi tri lon nhat da chay hieu ung hien dan (de cuon/tai them khong chay lai). */
    private int animatedUpTo = -1;
    private String firstId = null;

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
        String newFirst = newItems != null && !newItems.isEmpty() ? newItems.get(0).id : null;
        boolean sameList = newFirst != null && newFirst.equals(firstId) && newItems.size() >= items.size();
        if (!sameList) animatedUpTo = -1; // danh sach khac hoan toan (doi danh muc/tim kiem) -> hien dan lai tu dau
        firstId = newFirst;
        items.clear();
        if (newItems != null) items.addAll(newItems);
        this.hasMore = hasMore;
        this.pricesHidden = pricesHidden;
        notifyDataSetChanged();
    }

    public boolean isFooter(int position) {
        return position == items.size() && hasFooter();
    }

    private boolean hasFooter() {
        return !items.isEmpty();
    }

    @Override
    public int getItemViewType(int position) {
        return isFooter(position) ? TYPE_FOOTER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_FOOTER) {
            LinearLayout box = new LinearLayout(parent.getContext());
            box.setOrientation(LinearLayout.VERTICAL);
            box.setGravity(Gravity.CENTER_HORIZONTAL);
            box.setPadding(0, Ui.px(22), 0, Ui.px(40));
            box.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout ornament = new LinearLayout(parent.getContext());
            ornament.setGravity(Gravity.CENTER_VERTICAL);
            ornament.addView(line(parent), new LinearLayout.LayoutParams(Ui.px(46), Math.max(1, Ui.px(1))));
            View diamond = new View(parent.getContext());
            diamond.setBackground(Ui.rect(Ui.SIDEBAR_GOLD_BOT, 0, 0, 0));
            diamond.setRotation(45f);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.px(8), Ui.px(8));
            dlp.setMargins(Ui.px(12), 0, Ui.px(12), 0);
            ornament.addView(diamond, dlp);
            ornament.addView(line(parent), new LinearLayout.LayoutParams(Ui.px(46), Math.max(1, Ui.px(1))));
            box.addView(ornament, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView text = Ui.text(parent.getContext(), "", 9f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
            text.setGravity(Gravity.CENTER);
            text.setLetterSpacing(0.03f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Ui.px(14);
            box.addView(text, tlp);
            return new FooterHolder(box, text, ornament);
        }
        ProductCardView card = new ProductCardView(parent.getContext(), Ui.px(CARD_W), Ui.px(CARD_H), Ui.px(INFO_H));
        RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(Ui.px(CARD_W), Ui.px(CARD_H));
        int margin = Ui.px(SPACING / 2f);
        lp.setMargins(margin, margin, margin, margin);
        card.setLayoutParams(lp);
        return new Holder(card);
    }

    private static View line(ViewGroup parent) {
        View v = new View(parent.getContext());
        v.setBackgroundColor(Ui.SIDEBAR_GOLD_TOP);
        return v;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FooterHolder) {
            FooterHolder f = (FooterHolder) holder;
            f.text.setText(hasMore ? "Đang tải thêm…" : "Bạn đã xem hết danh sách sản phẩm");
            f.ornament.setVisibility(hasMore ? View.INVISIBLE : View.VISIBLE);
            return;
        }
        Models.ProductDto product = items.get(position);
        Holder h = (Holder) holder;
        h.card.bind(product, pricesHidden);
        h.card.setOnClickListener(v -> onClick.onClick(product));

        int inCart = cartQuantities.getOrDefault(product.id, 0);
        int maxQty = computeMaxQuantity(product, inCart);
        h.card.bindQuantity(inCart, maxQty, new ProductCardView.StepperListener() {
            @Override
            public void onIncrement() {
                onStepperChange.onIncrement(product);
            }

            @Override
            public void onDecrement() {
                onStepperChange.onDecrement(product);
            }
        });

        // Hien dan le tung the (toi da ~6 the dau/lo, tre 45ms moi the) — nhe cho may yeu.
        h.card.animate().cancel();
        if (position > animatedUpTo) {
            animatedUpTo = position;
            float rest = h.card.restAlpha();
            h.card.setAlpha(0f);
            h.card.setTranslationY(Ui.px(18));
            h.card.animate().alpha(rest).translationY(0f)
                    .setStartDelay((position % 6) * 45L).setDuration(300)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        } else {
            h.card.setTranslationY(0f);
            h.card.setAlpha(h.card.restAlpha());
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof Holder) {
            ((Holder) holder).card.animate().cancel();
            ((Holder) holder).card.setTranslationY(0f);
        }
    }

    @Override
    public int getItemCount() {
        return items.size() + (hasFooter() ? 1 : 0);
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

    static class FooterHolder extends RecyclerView.ViewHolder {
        final TextView text;
        final View ornament;

        FooterHolder(View root, TextView text, View ornament) {
            super(root);
            this.text = text;
            this.ornament = ornament;
        }
    }
}
