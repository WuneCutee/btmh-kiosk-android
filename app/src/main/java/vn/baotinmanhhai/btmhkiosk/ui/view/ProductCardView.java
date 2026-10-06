package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * The san pham — card trang (vien be mo + bong nhe, bo goc 22). Anh nam trong "o" nen kem
 * (anh nen trang duoc nhan MULTIPLY voi mau nen nen hoa vao lam mot), goc tren co chip kinh mo ban vi ("3 chi") va
 * huy hieu "HET HANG"; phia duoi: ten toi da 2 dong + gia lon (so to, "VNĐ" nho).
 */
public class ProductCardView extends LinearLayout {

    /** Stepper dem so luong [ - n + ] duoi gia — cham thang vao gio hang, khong qua man Chi tiet. */
    public interface StepperListener {
        void onIncrement();

        void onDecrement();
    }

    private static final int TILE_MARGIN = 8;

    private final ImageView picture;
    private final TextView nameView;
    private final TextView priceView;
    private final TextView quantityChip;
    private final TextView soldOutBadge;
    private final LinearLayout stepper;
    private final View stepMinus;
    private final TextView stepValue;
    private final View stepPlus;
    private float restAlpha = 1f;

    public ProductCardView(Context context, int cardWidthPx, int cardHeightPx, int infoHeightPx) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(Ui.card(Ui.R_LG));
        Ui.clipRounded(this, Ui.R_LG);
        Ui.softShadow(this);
        setClickable(true);
        setFocusable(true);
        Ui.pressEffect(this);
        int m = Ui.px(TILE_MARGIN);
        setPadding(m, m, m, 0);

        // ---- O anh + chip ----
        FrameLayout tile = new FrameLayout(context);
        tile.setBackgroundColor(Ui.PAGE_BG_LIGHT);
        Ui.clipRounded(tile, Ui.R_MD);
        picture = new ImageView(context);
        picture.setScaleType(ImageView.ScaleType.FIT_CENTER);
        picture.setPadding(Ui.px(6), Ui.px(6), Ui.px(6), Ui.px(6));
        tile.addView(picture, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Mau + bo goc dung CSS Figma (man "Chon vat pham"): nen kem #FFFAEB, vien #FEDF89, bo goc 6 (khong phai pill).
        quantityChip = Ui.text(context, "", 8f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        quantityChip.setBackground(Ui.rect(0xFFFFFAEB, 7f, 0xFFFEDF89, 1));
        quantityChip.setPadding(Ui.px(11), Ui.px(5), Ui.px(11), Ui.px(6));
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        chipLp.setMargins(0, Ui.px(10), Ui.px(10), 0);
        tile.addView(quantityChip, chipLp);

        // Mau dung CSS Figma (man "Chon vat pham"): nen kem #FFFAEB, vien #CF999F, chu cam chay #B54708 (khong phai do dac).
        soldOutBadge = Ui.text(context, "Hết hàng", 8f, 0xFFB54708, Ui.W_BOLD);
        soldOutBadge.setBackground(Ui.rect(0xFFFFFAEB, Ui.R_PILL, 0xFFCF999F, 1));
        soldOutBadge.setPadding(Ui.px(11), Ui.px(5), Ui.px(11), Ui.px(6));
        FrameLayout.LayoutParams soldLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        soldLp.setMargins(Ui.px(10), Ui.px(10), 0, 0);
        tile.addView(soldOutBadge, soldLp);
        addView(tile, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        // ---- Vung thong tin ----
        LinearLayout info = new LinearLayout(context);
        info.setOrientation(VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(Ui.px(6), Ui.px(8), Ui.px(6), Ui.px(8));

        nameView = Ui.text(context, "", 10f, Ui.TEXT_TITLE_DARK, Ui.W_MEDIUM);
        nameView.setMaxLines(2);
        nameView.setLines(2);
        nameView.setLineSpacing(0, 1.1f);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(nameView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        priceView = Ui.text(context, "", 13f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD); // #AA1F23 dung CSS Figma (khong phai cam)
        priceView.setSingleLine(true);
        priceView.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams priceLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        priceLp.topMargin = Ui.px(9);
        info.addView(priceView, priceLp);
        addView(info, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // ---- Stepper [ - n + ] them vao gio hang ngay tren luoi (theo mockup Figma "Man hinh Vang tich luy") ----
        stepper = new LinearLayout(context);
        stepper.setOrientation(HORIZONTAL);
        stepper.setGravity(Gravity.CENTER_VERTICAL);
        stepper.setBackground(Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.rgb(244, 227, 202), 1)); // #FFFBF4 + vien #F4E3CA (CSS Figma)
        int stepH = Ui.px(36);
        stepMinus = stepButton(context, IconView.MINUS);
        stepper.addView(stepMinus, new LayoutParams(stepH, stepH));
        stepValue = Ui.text(context, "0", 10f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        stepValue.setGravity(Gravity.CENTER);
        stepper.addView(stepValue, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        stepPlus = stepButton(context, IconView.PLUS);
        stepper.addView(stepPlus, new LayoutParams(stepH, stepH));
        LayoutParams stepLp = new LayoutParams(LayoutParams.MATCH_PARENT, stepH);
        stepLp.topMargin = Ui.px(8);
        stepLp.bottomMargin = Ui.px(2);
        info.addView(stepper, stepLp);

        setLayoutParams(new LayoutParams(cardWidthPx, cardHeightPx));
    }

    private static View stepButton(Context context, int icon) {
        FrameLayout f = new FrameLayout(context);
        f.addView(new IconView(context, icon, Ui.BURGUNDY_PRIMARY).strokeUnits(2.2f),
                new FrameLayout.LayoutParams(Ui.px(16), Ui.px(16), Gravity.CENTER));
        f.setClickable(true);
        Ui.pressEffect(f);
        return f;
    }

    /** So luong dang co trong gio hang cua san pham nay + gioi han toi da (0 = khong con mua duoc them). */
    public void bindQuantity(int quantity, int maxQuantity, StepperListener listener) {
        stepValue.setText(String.valueOf(quantity));
        stepMinus.setAlpha(quantity > 0 ? 1f : 0.3f);
        stepMinus.setOnClickListener(quantity > 0 ? v -> listener.onDecrement() : null);
        boolean canAdd = quantity < maxQuantity;
        stepPlus.setAlpha(canAdd ? 1f : 0.3f);
        stepPlus.setOnClickListener(canAdd ? v -> listener.onIncrement() : null);
        stepper.setVisibility(maxQuantity > 0 || quantity > 0 ? VISIBLE : GONE);
    }

    /** Do mo "nghi" cua the (het hang mo hon) — adapter dung khi chay hieu ung hien dan. */
    public float restAlpha() {
        return restAlpha;
    }

    public void bind(Models.ProductDto product, boolean pricesHidden) {
        nameView.setText(product.name);
        // Gia cu qua stalePriceSeconds -> an gia (spec II.3.4): "Dang cap nhat gia…".
        if (pricesHidden) {
            priceView.setText("Đang cập nhật giá…");
            priceView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(9.5f));
            priceView.setTextColor(Ui.TEXT_MUTED_LIGHT);
        } else {
            priceView.setText(Ui.bigPrice(product.price, 0.62f));
            priceView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(13f));
            priceView.setTextColor(Ui.BURGUNDY_PRIMARY);
        }
        quantityChip.setText(Ui.quantity(product.quantity));
        quantityChip.setVisibility(product.quantity > 0 ? VISIBLE : GONE);
        soldOutBadge.setVisibility(product.isSoldOut() ? VISIBLE : GONE);
        restAlpha = product.isSoldOut() ? 0.65f : 1f;
        setAlpha(restAlpha);

        String url = product.getPrimaryImageUrl();
        if (url == null || url.isEmpty()) {
            Glide.with(picture).clear(picture);
            picture.setColorFilter(Ui.SIDEBAR_GOLD_BOT, PorterDuff.Mode.SRC_IN); // logo vang -> vang dong dam cho ro tren nen kem
            picture.setPadding(Ui.px(40), Ui.px(40), Ui.px(40), Ui.px(40));
            picture.setImageResource(R.drawable.logo); // images[] rong -> anh mac dinh dong goi (II.2.6)
        } else {
            // Nen anh trang * nen o (trang am) = hoa vao mau o.
            picture.setColorFilter(Ui.PAGE_BG_LIGHT, PorterDuff.Mode.MULTIPLY);
            picture.setPadding(Ui.px(6), Ui.px(6), Ui.px(6), Ui.px(6));
            Glide.with(picture).load(url).fitCenter()
                    .transition(DrawableTransitionOptions.withCrossFade(220))
                    .into(picture);
        }
    }
}
