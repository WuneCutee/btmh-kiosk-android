package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TabStopSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.ToLongFunction;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.databinding.ActivityCartBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.DialogOrderNoticeBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.ItemCartLineBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.ItemSelectOptionBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.SheetPriceChangedBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.SheetSelectBinding;
import vn.baotinmanhhai.btmhkiosk.ui.KioskViewModel;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man "Thong tin don hang" (S05). Doi tu ban ve tay Canvas sang inflate dung activity_cart.xml that qua
 * ViewBinding de khop pixel voi app chuan. Header (back/tieu de/home) van tu ve rieng nhu truoc (ShellView
 * an header dung chung cho man ORDER, giong het hanh vi cua app that - xem ShellView.setScreen()); FrameLayout
 * rong binding.header (von la cho cua FragmentContainerView/HeaderFragment khong ton tai trong du an nay) bi an.
 *
 * Picker Tinh/Thanh pho va Cua hang mo bang BottomSheetDialog rieng (sheet_select.xml + item_select_option.xml,
 * dung chung RecyclerView), giong dung co che OrderDialogs.select() cua app chuan - xem openSelectSheet().
 */
public class OrderConfirmView extends LinearLayout implements Ui.InsetAware {

    public interface Callbacks {
        void onBack();
        void onGoHome();
        void onSelectProvince(long provinceId);
        void onSelectStore(long storeId);
        void onChangeCartQuantity(String productId, int quantity);
        void onRemoveCartItem(String productId);
        void onPlaceOrder();
        void onVerifyIdentity();
        /** Icon but canh "Thong tin nhan hang": mo lai man Xac nhan thong tin khach hang de sua SDT/email. */
        void onEditReceiver();
        /** "DAT MUA": lam moi du lieu song (gia/ton kho) cho tung dong gio hang truoc khi tu kiem tra han
         * muc/gia/ton - xem KioskViewModel.refreshCartProductsForCheck(). */
        void onRequestCartRefresh(KioskViewModel.CartRefreshCallback callback);
    }

    private static final int MAX_UNBOUNDED = 99;
    private static final int FIELD_H = 56;

    private static final int COLOR_RED_BRAND = 0xFFAA1F23;      // brand_red
    private static final int COLOR_BORDER_BEIGE = 0xFFFBECD9;   // field_border
    private static final int COLOR_STEPPER_BG = 0xFFFFFBF4;     // detail_stepper_bg
    private static final int COLOR_STEPPER_BORDER = 0xFFF4E3CA; // detail_stepper_border
    private static final int COLOR_TEXT_LABEL = 0xFF535862;     // order_label
    private static final int COLOR_TEXT_MUTED = 0xFF757981;     // order_hint
    private static final int COLOR_INK_900 = 0xFF1A1A1A;        // order_value
    private static final int COLOR_CHEVRON = 0xFF444444;
    private static final String DASH = "—";

    private final Callbacks callbacks;
    private final ActivityCartBinding binding;

    private final LinearLayout topBar;
    private final CartLineAdapter adapter = new CartLineAdapter();

    private boolean pricesHidden;
    private boolean verified;
    private KioskViewModel.UiState lastState;

    private List<Models.ProvinceDto> boundProvinces;
    private Long boundProvinceId;
    private List<Models.StoreDto> boundStores;
    private Long boundStoreId;

    /** CartViewModel.entryPrices that: "gia cu" cua bottom sheet VIII = gia luc VAO man nay, chup 1 lan qua
     * captureEntryPrices() (goi tu KioskActivity khi chuyen vao man ORDER) - KHONG doi theo gia song trong
     * luc khach dang xem/dien thong tin, chi so sanh lai dung 1 lan khi bam "DAT MUA". */
    private final Map<String, Double> entryPrices = new HashMap<>();

    /** OrderDialogs.PriceChange that. */
    private static class PriceChange {
        final String productId;
        final String name;
        final double oldPrice;
        final double newPrice;

        PriceChange(String productId, String name, double oldPrice, double newPrice) {
            this.productId = productId;
            this.name = name;
            this.oldPrice = oldPrice;
            this.newPrice = newPrice;
        }
    }

    /** OrderDialogs.StockShortage that. */
    private static class StockShortage {
        final String productId;
        final String name;
        final int available;

        StockShortage(String productId, String name, int available) {
            this.productId = productId;
            this.name = name;
            this.available = available;
        }
    }

    public OrderConfirmView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        setOrientation(VERTICAL);
        // Ui.pageBackground() (gradient ve tay mo phong) tao duong rap noi voi phan noi dung ben
        // duoi (lay dung anh @drawable/bg that tu activity_cart.xml) - dung CHINH anh that nay,
        // giong fix da lam o ShellView, de topBar tu ve va noi dung cuon hien cung 1 nen lien mach.
        setBackground(context.getResources().getDrawable(R.drawable.bg, context.getTheme()));
        setClickable(true);

        // ================= HEADER / TOP BAR (tu ve, giong ShellView.header that nhung rieng cho man nay) =================
        topBar = new LinearLayout(context);
        topBar.setOrientation(HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Ui.px(20), Ui.px(56), Ui.px(32), Ui.px(8)); // Home: cach tren 56, phai 32 (Figma/fragment_header)

        FrameLayout backBtn = new FrameLayout(context);
        backBtn.setClickable(true);
        Ui.pressEffect(backBtn);
        backBtn.setOnClickListener(v -> {
            if (callbacks != null) callbacks.onBack();
        });
        IconView backIcon = new IconView(context, IconView.CHEVRON_LEFT, Color.BLACK).strokeUnits(2.2f);
        backBtn.addView(backIcon, new FrameLayout.LayoutParams(Ui.px(24), Ui.px(24), Gravity.CENTER));
        topBar.addView(backBtn, new LayoutParams(Ui.px(48), Ui.px(48)));

        TextView titleBar = Ui.text(context, "Thông tin đơn hàng", 24f, Color.BLACK, Ui.W_MEDIUM);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setIncludeFontPadding(false);
        titleBar.setLetterSpacing(-0.02f);
        LayoutParams titleLp = new LayoutParams(0, Ui.px(48), 1f);
        titleLp.setMarginStart(Ui.px(8));
        topBar.addView(titleBar, titleLp);

        FrameLayout homeBtn = new FrameLayout(context);
        homeBtn.setClickable(true);
        Ui.pressEffect(homeBtn);
        homeBtn.setOnClickListener(v -> {
            if (callbacks != null) callbacks.onGoHome();
        });
        android.view.View homeIcon = Ui.homeIcon(context);
        homeBtn.addView(homeIcon, new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        topBar.addView(homeBtn, new LayoutParams(Ui.px(66), Ui.px(66)));

        addView(topBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // ================= NOI DUNG THAT (activity_cart.xml) =================
        binding = ActivityCartBinding.inflate(LayoutInflater.from(context), null, false);
        binding.header.setVisibility(GONE); // thay the boi topBar tu ve o tren
        addView(binding.getRoot(), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        binding.rvLines.setLayoutManager(new LinearLayoutManager(context));
        binding.rvLines.setAdapter(adapter);

        // item_receiver_row.xml chi co tools:text cho tvLabel (preview-only, KHONG build vao app that) -
        // thieu dong set tvLabel.setText() nay la nguyen nhan 4 dong trong the "Thong tin nhan hang" hien
        // ra TRONG KHONG CO NHAN (chi thay gia tri) sau khi xac minh khuon mat xong. Dung dung tu vung nhan
        // da thiet lap san o CustomerAuthView (readonlyLabels) de dong bo toan app.
        binding.rowReceiver.tvLabel.setText("Người nhận");
        binding.rowIdNumber.tvLabel.setText("Số căn cước công dân/Hộ chiếu");
        binding.rowPhone.tvLabel.setText("Số điện thoại");
        binding.rowAddress.tvLabel.setText("Địa chỉ thường trú");

        // CartActivity.kt that: fieldProvince/fieldStore mo OrderDialogs.select() - 1 BottomSheetDialog rieng
        // (sheet_select.xml + item_select_option.xml, da co san trong du an), KHONG phai bung danh sach ngay
        // duoi truong nhu truoc (do la gian luoc cu, thi giac luc dong giong nhung luc mo list khac han ban that).
        binding.fieldProvince.setOnClickListener(v -> openProvinceSheet());
        binding.fieldStore.setOnClickListener(v -> openStoreSheet());

        // Sua thong tin nhan hang: chi hien khi da xac minh (luc do moi co the thong tin) - xem applyVerifiedStyle().
        binding.btnEditReceiver.setOnClickListener(v -> callbacks.onEditReceiver());
        binding.tradingNotice.setVisibility(GONE);   // chua co nguon du lieu gio mo ban rieng cho man don hang

        binding.btnVerify.setOnClickListener(v -> callbacks.onVerifyIdentity());

        binding.btnPlaceOrder.setOnClickListener(v -> {
            if (lastState != null && lastState.cart.isEmpty()) {
                KioskToast.error(getContext(), "Giỏ hàng trống");
                return;
            }
            if (!verified) {
                KioskToast.error(getContext(), "Vui lòng xác minh chính chủ trước khi đặt mua");
                return;
            }
            if (boundProvinceId == null) {
                KioskToast.error(getContext(), "Vui lòng chọn Tỉnh/Thành phố");
                return;
            }
            if (boundStoreId == null) {
                KioskToast.error(getContext(), "Vui lòng chọn cửa hàng nhận hàng");
                return;
            }
            proceedToPlaceOrder();
        });

        applyVerifiedStyle();
    }

    /** CartActivity.kt.openProvinceSheet(): sheet_province_title/search/count that. */
    private void openProvinceSheet() {
        openSelectSheet("Tỉnh/Thành Phố", "Nhập tên Tỉnh/Thành Phố…",
                n -> n + " Tỉnh/Thành Phố",
                boundProvinces, boundProvinceId,
                p -> p.id, p -> p.name, p -> null,
                (p, q) -> q.isEmpty() || fold(p.name).contains(q),
                id -> callbacks.onSelectProvince(id));
    }

    /** CartActivity.kt.openStoreSheet(): "if (state.province == null) return" - chua chon Tinh thi khong mo. */
    private void openStoreSheet() {
        if (boundProvinceId == null) return;
        openSelectSheet("Nhận hàng trực tiếp tại cửa hàng", "Nhập tên cửa hàng nhận hàng hoặc địa chỉ…",
                n -> n + " cửa hàng",
                boundStores, boundStoreId,
                st -> st.id, st -> st.name, st -> st.address,
                (st, q) -> q.isEmpty() || fold(st.name).contains(q) || fold(st.address).contains(q) || fold(st.wardName).contains(q),
                id -> callbacks.onSelectStore(id));
    }

    /**
     * OrderDialogs.select() that: BottomSheetDialog dung chung sheet_select.xml + item_select_option.xml (da co
     * san trong du an) cho ca man hinh chon Tinh/Thanh pho va Cua hang - thay cho kieu "bung danh sach ngay duoi
     * truong" truoc day (khac han luc mo so voi app chuan).
     */
    private <T> void openSelectSheet(String title, String searchHint, IntFunction<String> countText,
            List<T> items, Long selectedId, ToLongFunction<T> idFn, Function<T, String> nameFn,
            Function<T, String> subFn, BiPredicate<T, String> matches, LongConsumer onSelect) {
        Context context = getContext();
        SheetSelectBinding sb = SheetSelectBinding.inflate(LayoutInflater.from(context));
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.OrderSheet);
        dialog.setContentView(sb.getRoot());
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        sb.tvTitle.setText(title);
        sb.etSearch.setHint(searchHint);
        sb.btnBack.setOnClickListener(v -> dialog.dismiss());
        sb.btnClose.setOnClickListener(v -> dialog.dismiss());

        List<T> all = items == null ? new ArrayList<>() : items;
        SelectAdapter<T> adapter = new SelectAdapter<>(selectedId, idFn, nameFn, subFn, picked -> {
            dialog.dismiss();
            onSelect.accept(idFn.applyAsLong(picked));
        });
        sb.rvOptions.setLayoutManager(new LinearLayoutManager(context));
        sb.rvOptions.setAdapter(adapter);

        java.util.function.Consumer<String> applyFilter = query -> {
            String q = fold(query);
            List<T> shown = new ArrayList<>();
            for (T item : all) if (matches.test(item, q)) shown.add(item);
            adapter.submit(shown);
            sb.tvCount.setText(countText.apply(shown.size()));
        };
        sb.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applyFilter.accept(s.toString());
            }
        });
        applyFilter.accept("");

        OrderPopups.track(dialog);
        dialog.show();
        // showKiosk() that: man kiosk an thanh he thong, dialog co Window RIENG nen phai tu an lai.
        if (dialog.getWindow() != null) {
            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(dialog.getWindow(), dialog.getWindow().getDecorView());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    /** CartViewModel.load() that: chup "gia luc vao man" cho tung dong gio hang hien tai - goi dung 1 lan
     * moi khi KioskActivity chuyen man sang ORDER (xem KioskActivity.render(), previous != ORDER). */
    public void captureEntryPrices(List<Models.CartItem> cart) {
        entryPrices.clear();
        if (cart == null) return;
        for (Models.CartItem item : cart) {
            if (item.product != null) entryPrices.put(item.product.id, item.product.price);
        }
    }

    /**
     * CartViewModel.placeOrder() that: so sanh gia SONG hien tai (lastState.cart, da duoc cap nhat lien tuc
     * qua SSE/poll gia) voi "gia luc vao man" (entryPrices) - neu co dong nao lech, CHAN lai va bat khach xac
     * nhan lai o bottom sheet VIII truoc khi tiep tuc (openCustomerConfirm/openPayment o KioskActivity), dung
     * nhu that - khong de don hang am tham chot theo gia da doi ma khach chua tung thay.
     */
    private void proceedToPlaceOrder() {
        if (lastState == null) {
            callbacks.onPlaceOrder();
            return;
        }
        // BUG THAT da tim thay 15/10/2026: truoc day ham nay doc thang item.product tu lastState.cart - nhung
        // do la snapshot CHUP TU LUC THEM VAO GIO (addToCart/stepper tren luoi san pham), KHONG tung duoc lam
        // moi lai, nen moi lan kiem tra deu so sanh 1 object voi chinh no (luon "khong doi gi ca") du server
        // that su da doi gia/ton kho. Gio goi lam moi SONG qua API (II.2.8 tung dong, giong dung
        // CartViewModel.placeOrder() -> refreshProducts()) TRUOC, roi moi tu kiem tra tren du lieu moi nhat.
        setPlaceOrderBusy(true);
        callbacks.onRequestCartRefresh(freshCart -> {
            setPlaceOrderBusy(false);
            checkAndProceed(freshCart);
        });
    }

    /** Khoa nut "DAT MUA" trong luc dang cho ket qua lam moi gio hang song, tranh khach bam lien tiep. */
    private void setPlaceOrderBusy(boolean busy) {
        binding.btnPlaceOrder.setEnabled(!busy);
        binding.btnPlaceOrder.setAlpha(busy ? 0.6f : 1f);
    }

    /**
     * Chan cuoi: app nay KHONG co API tao don that (Phase 1, client-side-only - xem README) nen khong co
     * "server backstop" nhu CartViewModel that (server tra ma loi CODE_PRICE_CHANGED/EXCEED_LIMIT khi client
     * lot qua) - phai tu kiem tra het o day, tren du lieu VUA lam moi song (freshCart), khong dung du lieu cu.
     * Thu tu: han muc truoc, roi ton kho, roi gia (chot 28/09/2026 cho ton/gia - xem comment goc placeOrder()).
     */
    private void checkAndProceed(List<Models.CartItem> freshCart) {
        if (lastState != null && lastState.limitQuantity != null) {
            double totalChi = 0;
            for (Models.CartItem item : freshCart) totalChi += item.lineUnits();
            if (totalChi > lastState.limitQuantity) {
                Models.CartItem worst = null;
                for (Models.CartItem item : freshCart) {
                    if (item.product == null) continue;
                    if (worst == null || item.lineUnits() > worst.lineUnits()) worst = item;
                }
                if (worst != null) {
                    double otherChi = totalChi - worst.lineUnits();
                    Integer byLimit = computeByLimit(worst, lastState.limitQuantity, otherChi);
                    showPurchaseLimitDialog(lastState.limitQuantity, worst.product.id, worst.product.name,
                            byLimit == null ? 0 : byLimit);
                    return;
                }
            }
        }
        List<StockShortage> shortages = new ArrayList<>();
        for (Models.CartItem item : freshCart) {
            if (item.product == null) continue;
            Integer available = item.product.remainingQuantity;
            // remainingQuantity == null nghia la server khong tra (chua biet) -> KHONG coi la het hang (spec).
            if (available != null && available < item.quantity) {
                shortages.add(new StockShortage(item.product.id, item.product.name, available));
            }
        }
        if (!shortages.isEmpty()) {
            showOutOfStockDialog(shortages);
            return;
        }
        List<PriceChange> changes = new ArrayList<>();
        for (Models.CartItem item : freshCart) {
            if (item.product == null) continue;
            Double old = entryPrices.get(item.product.id);
            double current = item.product.price;
            if (old != null && old != current) {
                changes.add(new PriceChange(item.product.id, item.product.name, old, current));
            }
        }
        if (changes.isEmpty()) {
            callbacks.onPlaceOrder();
            return;
        }
        double newTotal = 0;
        for (Models.CartItem item : freshCart) newTotal += item.lineTotal();
        showPriceChangedSheet(changes, newTotal);
    }

    /**
     * OrderDialogs.notice() + outOfStockMessage() that: popup VII "SL mua > SL hien co". Dung chung layout
     * dialog_order_notice.xml (da co san trong du an, dung chung cho popup II/VI/VII cua app chuan).
     * "DIEU CHINH SO LUONG": ha so luong dong do ve dung ton hien co, O LAI man (khach bam "DAT MUA" lai tu dau).
     * "SAN PHAM KHAC": bo het cac dong vuong khoi gio, O LAI man Thong tin don hang (gian luoc - app chuan dieu
     * huong thang ve Danh sach san pham, nhung man nay chua co callback dieu huong rieng cho truong hop do).
     */
    private void showOutOfStockDialog(List<StockShortage> shortages) {
        Context context = getContext();
        DialogOrderNoticeBinding db = DialogOrderNoticeBinding.inflate(LayoutInflater.from(context));
        android.app.Dialog dialog = new android.app.Dialog(context, R.style.OrderDialog);
        dialog.setContentView(db.getRoot());

        db.tvTitle.setText(R.string.dialog_notice);

        SpannableStringBuilder msg = new SpannableStringBuilder();
        msg.append(context.getString(R.string.dialog_out_of_stock_head));
        for (StockShortage shortage : shortages) {
            msg.append('\n');
            String name = shortage.name == null ? "" : shortage.name;
            String line = context.getString(R.string.dialog_out_of_stock_line, shortage.available, name);
            int start = msg.length();
            msg.append(line);
            // Figma: dam so luong va ten san pham, "san pham" de thuong.
            String count = String.valueOf(shortage.available);
            int countAt = line.indexOf(count);
            if (countAt >= 0) msg.setSpan(new StyleSpan(Typeface.BOLD), start + countAt, start + countAt + count.length(), 0);
            if (!name.isEmpty()) {
                int nameAt = line.lastIndexOf(name);
                if (nameAt >= 0) msg.setSpan(new StyleSpan(Typeface.BOLD), start + nameAt, start + nameAt + name.length(), 0);
            }
        }
        msg.append('\n').append(context.getString(R.string.dialog_out_of_stock_tail));
        db.tvMessage.setText(msg);

        db.btnSecondary.setText(R.string.dialog_other_products);
        db.btnPrimary.setText(R.string.dialog_adjust_count);
        db.btnClose.setOnClickListener(v -> dialog.dismiss());
        db.btnSecondary.setOnClickListener(v -> {
            dialog.dismiss();
            for (StockShortage shortage : shortages) callbacks.onRemoveCartItem(shortage.productId);
        });
        db.btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            for (StockShortage shortage : shortages) callbacks.onChangeCartQuantity(shortage.productId, shortage.available);
        });

        OrderPopups.track(dialog);
        dialog.show();
        // showKiosk() that: man kiosk an thanh he thong, dialog co Window RIENG nen phai tu an lai.
        if (dialog.getWindow() != null) {
            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(dialog.getWindow(), dialog.getWindow().getDecorView());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    /**
     * OrderDialogs.notice() + purchaseLimitMessage() that: popup VI "Nhap qua so luong san pham co the mua"
     * (Figma 917:40180) - chi mo khi khach GO SO thang vao o nhap (rejectOverMaxOrApply()), KHONG mo khi bam (+).
     * "DIEU CHINH SO LUONG": dien dung [maxCount] theo han muc (da tinh san, khong vuot ca ton). "SAN PHAM KHAC":
     * bo dong nay khoi gio (gian luoc nhu showOutOfStockDialog() - o lai man Thong tin don hang).
     */
    private void showPurchaseLimitDialog(double limitChi, String productId, String name, int maxCount) {
        Context context = getContext();
        DialogOrderNoticeBinding db = DialogOrderNoticeBinding.inflate(LayoutInflater.from(context));
        android.app.Dialog dialog = new android.app.Dialog(context, R.style.OrderDialog);
        dialog.setContentView(db.getRoot());

        db.tvTitle.setText(R.string.dialog_notice);
        CharSequence message = TextUtils.expandTemplate(
                context.getString(R.string.dialog_purchase_limit),
                appendBold(new SpannableStringBuilder(), formatChi(limitChi)),
                appendBold(new SpannableStringBuilder(), name == null ? "" : name),
                appendBold(new SpannableStringBuilder(), String.valueOf(maxCount)));
        db.tvMessage.setText(message);

        db.btnSecondary.setText(R.string.dialog_other_products);
        db.btnPrimary.setText(R.string.dialog_adjust_count);
        db.btnClose.setOnClickListener(v -> dialog.dismiss());
        db.btnSecondary.setOnClickListener(v -> {
            dialog.dismiss();
            callbacks.onRemoveCartItem(productId);
        });
        db.btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            callbacks.onChangeCartQuantity(productId, maxCount);
        });

        OrderPopups.track(dialog);
        dialog.show();
        // showKiosk() that: man kiosk an thanh he thong, dialog co Window RIENG nen phai tu an lai.
        if (dialog.getWindow() != null) {
            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(dialog.getWindow(), dialog.getWindow().getDecorView());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    /** Formats.chi() that: 3.00 -> "3", 0.50 -> "0.5" (bo so 0 thua, khong dung dinh dang khoa hoc). */
    private static String formatChi(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /** OrderDialogs.priceChanged() that: bottom sheet VIII, moi san pham doi gia 1 khoi them vao llChanges. */
    private void showPriceChangedSheet(List<PriceChange> changes, double newTotal) {
        Context context = getContext();
        SheetPriceChangedBinding sb = SheetPriceChangedBinding.inflate(LayoutInflater.from(context));
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.OrderSheet);
        dialog.setContentView(sb.getRoot());
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        int red = COLOR_RED_BRAND;
        int grey = ContextCompat.getColor(context, R.color.text_hint);
        float textSize = context.getResources().getDimension(R.dimen.price_text);
        String labelOld = context.getString(R.string.price_changed_old);
        String labelNew = context.getString(R.string.price_changed_new);
        String labelDiff = context.getString(R.string.price_changed_diff);

        TextPaint paint = new TextPaint();
        paint.setTextSize(textSize);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        int tabStop = (int) (Math.max(Math.max(paint.measureText(labelOld), paint.measureText(labelNew)),
                paint.measureText(labelDiff)) + paint.measureText("  "));

        sb.llChanges.removeAllViews();
        for (PriceChange change : changes) {
            TextView block = new TextView(context);
            block.setTextColor(Color.BLACK);
            block.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textSize);
            TextViewCompat.setLineHeight(block, context.getResources().getDimensionPixelSize(R.dimen.price_line));
            block.setPadding(0, context.getResources().getDimensionPixelSize(R.dimen.price_block_gap), 0, 0);
            double diff = change.newPrice - change.oldPrice;
            String sign = diff > 0 ? "+" : "-";

            SpannableStringBuilder sbText = new SpannableStringBuilder();
            if (changes.size() > 1) {
                appendBold(sbText, change.name == null ? "" : change.name).append('\n');
            }
            appendRow(sbText, labelOld, formatMoneyVnd(change.oldPrice), grey, true).append('\n');
            appendRow(sbText, labelNew, formatMoneyVnd(change.newPrice), red, false).append('\n');
            appendRow(sbText, labelDiff, sign + formatMoneyVnd(Math.abs(diff)), red, false);
            sbText.setSpan(new TabStopSpan.Standard(tabStop), 0, sbText.length(), 0);
            block.setText(sbText);
            sb.llChanges.addView(block);
        }
        // Tu 3 san pham doi gia: chan chieu cao, cuon trong khung (giong dung OrderDialogs.priceChanged()).
        if (changes.size() > 2) {
            ViewGroup.LayoutParams lp = sb.scrollChanges.getLayoutParams();
            lp.height = context.getResources().getDimensionPixelSize(R.dimen.price_changes_max_height);
            sb.scrollChanges.setLayoutParams(lp);
        }

        SpannableStringBuilder totalText = new SpannableStringBuilder();
        appendBold(totalText, context.getString(R.string.price_changed_total) + " ");
        appendColored(totalText, formatMoneyVnd(newTotal), red, true);
        sb.tvTotal.setText(totalText);

        sb.btnClose.setOnClickListener(v -> dialog.dismiss());
        sb.btnAgree.setOnClickListener(v -> {
            for (PriceChange change : changes) entryPrices.put(change.productId, change.newPrice);
            dialog.dismiss();
            callbacks.onPlaceOrder();
        });

        OrderPopups.track(dialog);
        dialog.show();
        // showKiosk() that: man kiosk an thanh he thong, dialog co Window RIENG nen phai tu an lai.
        if (dialog.getWindow() != null) {
            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(dialog.getWindow(), dialog.getWindow().getDecorView());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    /** OrderDialogs.appendBold() that. */
    private static SpannableStringBuilder appendBold(SpannableStringBuilder sb, CharSequence text) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), 0);
        return sb;
    }

    /** OrderDialogs.appendColored() that. */
    private static SpannableStringBuilder appendColored(SpannableStringBuilder sb, CharSequence text, int color, boolean bold) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new ForegroundColorSpan(color), start, sb.length(), 0);
        if (bold) sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), 0);
        return sb;
    }

    /** OrderDialogs.appendRow() that: "Nhan:\tgia tri" - nhan dam den, gia tri dam theo mau, co the gach ngang. */
    private static SpannableStringBuilder appendRow(SpannableStringBuilder sb, String label, String value, int color, boolean strike) {
        appendBold(sb, label).append('\t');
        int start = sb.length();
        appendColored(sb, value, color, true);
        if (strike) sb.setSpan(new StrikethroughSpan(), start, sb.length(), 0);
        return sb;
    }

    /** item_select_option.xml that: ten + (dia chi, chi Cua hang) + dau check khi dang chon. */
    private static class SelectAdapter<T> extends RecyclerView.Adapter<SelectAdapter<T>.VH> {
        private final Long selectedId;
        private final ToLongFunction<T> idFn;
        private final Function<T, String> nameFn;
        private final Function<T, String> subFn;
        private final java.util.function.Consumer<T> onClick;
        private List<T> items = new ArrayList<>();

        SelectAdapter(Long selectedId, ToLongFunction<T> idFn, Function<T, String> nameFn,
                Function<T, String> subFn, java.util.function.Consumer<T> onClick) {
            this.selectedId = selectedId;
            this.idFn = idFn;
            this.nameFn = nameFn;
            this.subFn = subFn;
            this.onClick = onClick;
        }

        void submit(List<T> list) {
            items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemSelectOptionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            T item = items.get(position);
            boolean isSelected = selectedId != null && idFn.applyAsLong(item) == selectedId;
            holder.binding.tvName.setText(nameFn.apply(item));
            String sub = subFn.apply(item);
            holder.binding.tvSub.setText(sub);
            holder.binding.tvSub.setVisibility(sub != null && !sub.trim().isEmpty() ? VISIBLE : GONE);
            holder.binding.ivCheck.setVisibility(isSelected ? VISIBLE : INVISIBLE);
            holder.binding.getRoot().setBackgroundResource(isSelected ? R.drawable.bg_option_selected : 0);
            holder.binding.getRoot().setOnClickListener(v -> onClick.accept(item));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemSelectOptionBinding binding;
            VH(ItemSelectOptionBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    @Override
    public void onInsets(int l, int t, int r, int b) {
        setPadding(l, 0, r, 0);
        topBar.setPadding(Ui.px(20), Ui.px(56) + t, Ui.px(32), Ui.px(8));
        binding.scroll.setPadding(binding.scroll.getPaddingLeft(), binding.scroll.getPaddingTop(),
                binding.scroll.getPaddingRight(), binding.scroll.getPaddingBottom() + b);
    }

    /** SRS muc 2 / Figma (khung 298): hien toi da 2 san pham; tu san pham thu 3 cuon trong khung, co thanh cuon. */
    private static final int MAX_VISIBLE_LINES = 2;

    private void limitVisibleLines(int count) {
        android.content.res.Resources r = getResources();
        int itemH = r.getDimensionPixelSize(R.dimen.order_item_height);
        int gap = r.getDimensionPixelSize(R.dimen.order_item_gap); // marginBottom cua moi dong
        android.view.ViewGroup.LayoutParams lp = binding.rvLines.getLayoutParams();
        int want = count > MAX_VISIBLE_LINES
                ? MAX_VISIBLE_LINES * itemH + (MAX_VISIBLE_LINES - 1) * gap  // 2*144 + 10 = 298 (Figma)
                : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        if (lp.height != want) {
            lp.height = want;
            binding.rvLines.setLayoutParams(lp);
        }
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
        applyVerifiedStyle();
    }

    public boolean isVerified() {
        return verified;
    }

    private void applyVerifiedStyle() {
        binding.btnVerify.setVisibility(verified ? GONE : VISIBLE);
        binding.tvVerifyHint.setVisibility(verified ? GONE : VISIBLE);
        binding.receiverCard.setVisibility(verified ? VISIBLE : GONE);
        binding.btnEditReceiver.setVisibility(verified ? VISIBLE : GONE);

        if (canPlaceOrder()) {
            binding.btnPlaceOrder.setBackgroundResource(R.drawable.bg_pill_red);
            binding.btnPlaceOrder.setElevation(Ui.px(2));
        } else {
            binding.btnPlaceOrder.setBackgroundResource(R.drawable.bg_pill_disabled);
            binding.btnPlaceOrder.setElevation(0f);
        }
    }

    private boolean canPlaceOrder() {
        return verified && boundProvinceId != null && boundStoreId != null;
    }

    public void resetStorePicker() {
        verified = false;
        applyVerifiedStyle();
        boundProvinces = null;
        boundStores = null;
        boundStoreId = null;
        boundProvinceId = null;
    }

    public void setCustomerInfo(String name, String idNumber, String phone, String permanentAddress) {
        binding.rowReceiver.tvValue.setText(orDash(name));
        binding.rowIdNumber.tvValue.setText(orDash(idNumber));
        binding.rowPhone.tvValue.setText(orDash(phone));
        binding.rowAddress.tvValue.setText(orDash(permanentAddress));
    }

    /** CustomerConfirmView "HOAN THANH": ghi de so dien thoai bang so khach vua xac nhan (co the da sua). */
    public void setConfirmedContact(String phone) {
        binding.rowPhone.tvValue.setText(orDash(phone));
    }

    public String getRecipientName() { return binding.rowReceiver.tvValue.getText().toString(); }
    public String getRecipientId() { return binding.rowIdNumber.tvValue.getText().toString(); }
    public String getRecipientPhone() { return binding.rowPhone.tvValue.getText().toString(); }
    public String getRecipientAddress() { return binding.rowAddress.tvValue.getText().toString(); }

    private static String orDash(String s) {
        return s == null || s.trim().isEmpty() ? DASH : s;
    }

    public String[] getPaymentSummary() {
        String code = "DH" + new java.text.SimpleDateFormat("yyMMddHHmm", Locale.ROOT).format(new java.util.Date());
        String location = binding.fieldStore.getText().toString();
        return new String[]{getRecipientName(), code, binding.tvTotalPrice.getText().toString(), location};
    }

    public void bind(KioskViewModel.UiState s) {
        if (s.cart.isEmpty()) return;
        lastState = s;
        pricesHidden = s.pricesHidden;

        binding.progress.setVisibility(s.orderLoading ? VISIBLE : GONE);
        if (s.orderError != null && !s.orderError.isEmpty()) {
            KioskToast.error(getContext(), s.orderError);
        }

        renderItems(s);

        boolean hasProvinces = s.provinces != null && !s.provinces.isEmpty();
        boolean multi = s.provinces != null && s.provinces.size() > 1;
        binding.provinceGroup.setVisibility(hasProvinces ? VISIBLE : GONE);
        binding.fieldProvince.setClickable(multi);
        boundProvinces = s.provinces;
        boundProvinceId = s.selectedProvinceId;
        renderProvinceField(s, multi);

        boundStores = s.stores;
        boundStoreId = s.selectedStoreId;
        renderStoreField(s);
        // CartActivity.kt: fieldStore.isEnabled = state.province != null
        binding.fieldStore.setEnabled(boundProvinceId != null);
        renderReceiveInfo(s);
        applyVerifiedStyle();
    }

    private void renderItems(KioskViewModel.UiState s) {
        List<Models.CartItem> cart = s.cart;

        double totalUnits = 0;
        int totalQty = 0;
        double totalValueSum = 0;
        for (Models.CartItem it : cart) {
            totalQty += it.quantity;
            totalValueSum += it.lineTotal();
            totalUnits += it.lineUnits();
        }
        adapter.submit(cart, s.limitQuantity, totalUnits, pricesHidden);
        limitVisibleLines(cart.size());

        binding.tvTotalCount.setText(String.valueOf(totalQty));
        if (pricesHidden) {
            binding.tvTotalPrice.setText("Đang cập nhật giá…");
            binding.tvTotalPrice.setTextColor(COLOR_TEXT_MUTED);
        } else {
            binding.tvTotalPrice.setText(formatMoneyVnd(totalValueSum));
            binding.tvTotalPrice.setTextColor(COLOR_RED_BRAND);
        }
    }

    private int computeMaxQuantity(Models.CartItem item, Double limit, double otherUnitsInCart) {
        Integer byLimit = computeByLimit(item, limit, otherUnitsInCart);
        int byStock = computeByStock(item);
        return Math.max(0, Math.min(byLimit == null ? MAX_UNBOUNDED : byLimit, byStock));
    }

    /** PurchaseLimit.maxCountByLimit() that: null = khong gioi han theo han muc (khong co han muc hoac
     * san pham khong co ban vi). */
    private static Integer computeByLimit(Models.CartItem item, Double limitChi, double otherChiInCart) {
        if (limitChi == null || item.product == null || item.product.quantity <= 0) return null;
        double room = limitChi - otherChiInCart;
        if (room <= 0) return 0;
        return (int) Math.floor(room / item.product.quantity + 1e-9);
    }

    private static int computeByStock(Models.CartItem item) {
        return item.product != null && item.product.remainingQuantity != null
                ? Math.max(0, item.product.remainingQuantity) : MAX_UNBOUNDED;
    }

    // ================= ADAPTER DANH SACH SAN PHAM (item_cart_line.xml that) =================
    private class CartLineAdapter extends RecyclerView.Adapter<CartLineAdapter.VH> {
        private List<Models.CartItem> cart = new ArrayList<>();
        private Double limitQuantity;
        private double totalUnits;
        private boolean hidePrices;

        void submit(List<Models.CartItem> items, Double limitQuantity, double totalUnits, boolean hidePrices) {
            this.cart = items;
            this.limitQuantity = limitQuantity;
            this.totalUnits = totalUnits;
            this.hidePrices = hidePrices;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemCartLineBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Models.CartItem item = cart.get(position);
            Models.ProductDto p = item.product;
            ItemCartLineBinding b = holder.binding;

            String url = p != null ? p.getPrimaryImageUrl() : null;
            if (url == null || url.isEmpty()) {
                b.ivImage.setColorFilter(Ui.SIDEBAR_GOLD_BOT, PorterDuff.Mode.SRC_IN);
                b.ivImage.setPadding(Ui.px(10), Ui.px(10), Ui.px(10), Ui.px(10));
                b.ivImage.setImageResource(R.drawable.logo);
            } else {
                b.ivImage.clearColorFilter();
                b.ivImage.setPadding(0, 0, 0, 0);
                Glide.with(b.ivImage).load(url).fitCenter()
                        .transition(DrawableTransitionOptions.withCrossFade(200))
                        .into(b.ivImage);
            }

            b.tvName.setText(p != null ? p.name : "");
            if (p != null) {
                b.tvPrice.setText(hidePrices ? "Đang cập nhật giá…" : formatMoneyVnd(p.price));
            }

            double otherUnits = totalUnits - item.lineUnits();
            int maxQty = computeMaxQuantity(item, limitQuantity, otherUnits);

            b.etCount.setText(String.valueOf(item.quantity));
            b.btnMinus.setAlpha(item.quantity > 1 ? 1f : 0.3f);
            b.btnMinus.setOnClickListener(v -> {
                if (p != null && item.quantity > 1) callbacks.onChangeCartQuantity(p.id, item.quantity - 1);
            });
            b.btnPlus.setAlpha(item.quantity < maxQty ? 1f : 0.3f);
            Double limitForPlus = limitQuantity;
            double otherUnitsForPlus = otherUnits;
            b.btnPlus.setOnClickListener(v -> {
                if (p == null) return;
                if (item.quantity < maxQty) {
                    callbacks.onChangeCartQuantity(p.id, item.quantity + 1);
                    return;
                }
                // Da cham tran: truoc day im lang (nut da mo het, bam khong lam gi) - gio bao ro ly do
                // (popup han muc/het ton), dung yeu cau 15/10/2026 ap dung ca cho man nay.
                Integer byLimit = computeByLimit(item, limitForPlus, otherUnitsForPlus);
                int byStock = computeByStock(item);
                if (byLimit != null && item.quantity >= byLimit) {
                    showPurchaseLimitDialog(limitForPlus, p.id, p.name, byLimit);
                } else {
                    List<StockShortage> shortages = new ArrayList<>();
                    shortages.add(new StockShortage(p.id, p.name, byStock));
                    showOutOfStockDialog(shortages);
                }
            });
            b.btnRemove.setOnClickListener(v -> {
                if (p == null) return;
                String productId = p.id;
                OrderPopups.showRemoveConfirm(v.getContext(), () -> callbacks.onRemoveCartItem(productId));
            });

            Double limitForInput = limitQuantity;
            double otherUnitsForInput = otherUnits;
            b.etCount.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    b.etCount.clearFocus();
                    return true;
                }
                return false;
            });
            b.etCount.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus || p == null) return;
                int wanted;
                try {
                    wanted = Math.max(1, Integer.parseInt(b.etCount.getText().toString().trim()));
                } catch (NumberFormatException e) {
                    wanted = 1;
                }
                rejectOverMaxOrApply(item, p, wanted, limitForInput, otherUnitsForInput);
            });
        }

        /**
         * CartViewModel.inputCount()/rejectOverMax() that: khach go so THANG vao o - vuot han muc (VI) hay
         * vuot ton (VII) deu mo popup tuong ung va KHONG doi gio; "Han muc xet truoc: vuot ca hai thi bao
         * han muc, khong bao ton" (CountCap.check() that - dung dung thu tu uu tien nay).
         */
        private void rejectOverMaxOrApply(Models.CartItem item, Models.ProductDto p, int wanted, Double limitChi, double otherChiInCart) {
            Integer byLimit = computeByLimit(item, limitChi, otherChiInCart);
            int byStock = computeByStock(item);
            if (byLimit != null && wanted > byLimit) {
                revertEtCount(item); // ve lai etCount ve gia tri cu, huy nhap
                showPurchaseLimitDialog(limitChi, p.id, p.name, byLimit);
                return;
            }
            if (wanted > byStock) {
                revertEtCount(item);
                List<StockShortage> shortages = new ArrayList<>();
                shortages.add(new StockShortage(p.id, p.name, byStock));
                showOutOfStockDialog(shortages);
                return;
            }
            if (wanted != item.quantity) callbacks.onChangeCartQuantity(p.id, wanted);
        }

        private void revertEtCount(Models.CartItem item) {
            int idx = cart.indexOf(item);
            if (idx >= 0) notifyItemChanged(idx);
        }

        @Override
        public int getItemCount() {
            return cart.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ItemCartLineBinding binding;
            VH(ItemCartLineBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private void renderProvinceField(KioskViewModel.UiState s, boolean expandable) {
        String name = "Chọn Tỉnh/Thành phố";
        boolean chosen = false;
        if (s.provinces != null && s.selectedProvinceId != null) {
            for (Models.ProvinceDto p : s.provinces) {
                if (p.id == s.selectedProvinceId) {
                    name = p.name;
                    chosen = true;
                }
            }
        }
        binding.fieldProvince.setText(name);
        binding.fieldProvince.setTextColor(chosen ? COLOR_INK_900 : COLOR_TEXT_MUTED);
        binding.fieldProvince.setCompoundDrawablesWithIntrinsicBounds(0, 0,
                expandable ? R.drawable.ic_chevron_down : 0, 0);
    }

    private void renderStoreField(KioskViewModel.UiState s) {
        Models.StoreDto selected = selectedStore(s);
        if (selected == null) {
            binding.fieldStore.setText("Chọn cửa hàng");
            binding.fieldStore.setTextColor(COLOR_TEXT_MUTED);
        } else {
            binding.fieldStore.setText(selected.name);
            binding.fieldStore.setTextColor(COLOR_INK_900);
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
            binding.fieldDate.setText("10/08/2026");
            binding.tvPickupHours.setText("Khung giờ nhận: 09:00 - 20:30");
            return;
        }
        String date = formatDate(selected.receiveDate);
        binding.fieldDate.setText(date != null ? date : "10/08/2026");
        boolean hasHours = selected.pickupHours != null && !selected.pickupHours.isEmpty();
        binding.tvPickupHours.setText(hasHours ? "Khung giờ nhận: " + selected.pickupHours : "Khung giờ nhận: 09:00 - 20:30");
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

}
