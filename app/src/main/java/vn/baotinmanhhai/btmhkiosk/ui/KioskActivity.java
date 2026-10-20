package vn.baotinmanhhai.btmhkiosk.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.lifecycle.ViewModelProvider;

import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import vn.baotinmanhhai.btmhkiosk.BuildConfig;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.ui.view.CustomerAuthView;
import vn.baotinmanhhai.btmhkiosk.ui.view.CustomerConfirmView;
import vn.baotinmanhhai.btmhkiosk.ui.view.DetailView;
import vn.baotinmanhhai.btmhkiosk.ui.view.GoodsPickupView;
import vn.baotinmanhhai.btmhkiosk.ui.view.HomeView;
import vn.baotinmanhhai.btmhkiosk.ui.view.IdentityVerifyView;
import vn.baotinmanhhai.btmhkiosk.ui.view.IdlePlayerView;
import vn.baotinmanhhai.btmhkiosk.ui.view.OrderConfirmView;
import vn.baotinmanhhai.btmhkiosk.ui.view.OrderPopups;
import vn.baotinmanhhai.btmhkiosk.ui.view.PaymentView;
import vn.baotinmanhhai.btmhkiosk.ui.view.ProductsView;
import vn.baotinmanhhai.btmhkiosk.ui.view.SetupView;
import vn.baotinmanhhai.btmhkiosk.ui.view.ShellView;
import vn.baotinmanhhai.btmhkiosk.ui.view.StatusView;
import vn.baotinmanhhai.btmhkiosk.ui.view.WebsiteView;

/**
 * Activity DUY NHAT cua kiosk — tuong duong Form1 cua ban WinForms: mot khung chua chong cac "panel"
 * (Home bang gia, San pham, Chi tiet, Xac nhan don hang, Kich hoat), lop thong bao trang thai may, dai mat
 * ket noi, man website, va man cho quang cao phu len tren cung. Chi co 1 ViewModel duy nhat cho ca app.
 */
public class KioskActivity extends AppCompatActivity {

    private static final long FADE_MS = 260;
    private static final int OFFLINE_STRIP_H = 30; // px thiet ke (chua tinh vung an toan phia tren)

    private KioskViewModel viewModel;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ShellView shell;
    private HomeView home;
    private ProductsView products;
    private DetailView detail;
    private OrderConfirmView order;
    private SetupView setup;
    private StatusView status;
    private TextView offlineStrip;
    private WebsiteView website;
    private GoodsPickupView goodsPickup; // man Tra hang (UI tinh, chi mo duoc o ban DEBUG)
    private CustomerAuthView customerAuth; // man Xac thuc khach hang (UI tinh) — chen giua Chi tiet va Xac nhan don hang
    private PaymentView payment; // man Thanh toan QR + Ket qua giao dich (UI tinh, mo phong) — sau "DAT MUA" o Xac nhan don hang
    private IdentityVerifyView identityVerify; // popup + quet khuon mat "Xac minh chinh chu" (khach DA dang nhap), truoc DAT MUA
    private CustomerConfirmView customerConfirm; // "Xac nhan thong tin khach hang" (S07) - sua SDT/email truoc khi thanh toan
    private boolean receiverConfirmed; // da hoan thanh CustomerConfirmView trong phien Xac nhan don hang hien tai chua
    private String confirmedPhone;
    private String confirmedEmail;
    private boolean verifyingForOrder; // dang mo customerAuth tu nut "Xac minh chinh chu" (khach CHUA dang nhap) — khac voi dang nhap tu Home
    private int pendingQuantity = 1; // so luong da chon o Chi tiet, giu trong luc dang o man Xac thuc
    private Models.ProductDto pendingProduct; // san pham tuong ung, them vao gio hang sau khi xac thuc xong
    private boolean authForLoginOnly; // true = mo tu nut "Dang nhap" o Home (khong lien quan don hang nao)
    private IdlePlayerView idle;

    private ActivityResultLauncher<ScanOptions> qrScanner;

    // ---- Man cho / quang cao (Form1: _isIdleShowing, _lastInteractionUtc...) ----
    private final List<String> defaultAdUris = new ArrayList<>();
    private boolean idleShowing;
    private boolean bootIdlePending = true; // chua vao man cho lan dau sau khi khoi dong/kich hoat
    private long lastInteraction = SystemClock.elapsedRealtime();

    private KioskViewModel.UiState lastState;
    private KioskViewModel.AppScreen currentScreen = null;
    private int idleTimeoutSeconds = KioskViewModel.DEFAULT_IDLE_TIMEOUT_SECONDS;

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            long idleMs = SystemClock.elapsedRealtime() - lastInteraction;
            // Dang o man Thanh toan (cho quet QR / cho ket qua chuyen khoan): khong tu dong ve man cho,
            // de khach khong bi mat man QR/ket qua giua chung khi dang doi ngan hang xu ly.
            boolean onPayment = payment != null && payment.getVisibility() == View.VISIBLE;
            if (!idleShowing && !onPayment && currentScreen != null && currentScreen != KioskViewModel.AppScreen.SETUP
                    && idleMs >= idleTimeoutSeconds * 1000L) {
                showIdle();
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setupEdgeToEdge();

        viewModel = new ViewModelProvider(this).get(KioskViewModel.class);
        loadDefaultAds();

        qrScanner = registerForActivityResult(new ScanContract(), result -> {
            if (result != null && result.getContents() != null) setup.onQrScanned(result.getContents());
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.pageBackground());

        home = new HomeView(this, new HomeView.Callbacks() {
            @Override
            public void onOpenProducts() {
                viewModel.openProducts();
            }

            @Override
            public void onSelectGoldProduct(String code) {
                viewModel.selectGoldProduct(code);
            }
        });
        products = new ProductsView(this, new ProductsView.Callbacks() {
            @Override
            public void onCategoryClick(String categoryId) {
                viewModel.selectCategory(categoryId);
            }

            @Override
            public void onProductClick(Models.ProductDto product) {
                viewModel.openProduct(product);
            }

            @Override
            public void onSearch(String query) {
                viewModel.search(query);
            }

            @Override
            public void onLoadMore() {
                viewModel.loadMoreProducts();
            }

            @Override
            public void onIncrement(Models.ProductDto product) {
                // Truoc day bam (+) luc da dung tran chi im lang khong lam gi (nut da tu khoa o ProductCardView) -
                // theo yeu cau 15/10/2026, bam tiep luc do phai BAO RO ly do (han muc hay het ton) thay vi im lang.
                int current = 0;
                double otherUnits = 0;
                if (lastState != null) {
                    for (Models.CartItem it : lastState.cart) {
                        if (it.product == null) continue;
                        if (it.product.id.equals(product.id)) current = it.quantity;
                        else otherUnits += it.lineUnits();
                    }
                }
                Double limitQuantity = lastState != null ? lastState.limitQuantity : null;
                Integer byLimit = null;
                if (limitQuantity != null && product.quantity > 0) {
                    double room = limitQuantity - otherUnits;
                    byLimit = room <= 0 ? 0 : (int) Math.floor(room / product.quantity + 1e-9);
                }
                int byStock = product.remainingQuantity != null ? Math.max(0, product.remainingQuantity) : Integer.MAX_VALUE;
                int maxQty = Math.max(0, Math.min(byLimit == null ? Integer.MAX_VALUE : byLimit, byStock));
                if (current >= maxQty) {
                    if (byLimit != null && current >= byLimit) {
                        int cappedByLimit = byLimit;
                        OrderPopups.showPurchaseLimit(KioskActivity.this, limitQuantity, product.name, cappedByLimit,
                                () -> viewModel.updateCartQuantity(product.id, cappedByLimit), null);
                    } else {
                        int cappedByStock = byStock;
                        OrderPopups.showOutOfStock(KioskActivity.this, product.name, cappedByStock,
                                () -> viewModel.updateCartQuantity(product.id, cappedByStock), null);
                    }
                    return;
                }
                viewModel.addToCart(product, 1, false);
            }

            @Override
            public void onDecrement(Models.ProductDto product) {
                int current = 0;
                if (lastState != null) {
                    for (Models.CartItem it : lastState.cart) {
                        if (it.product.id.equals(product.id)) current = it.quantity;
                    }
                }
                if (current <= 1) viewModel.removeFromCart(product.id);
                else viewModel.updateCartQuantity(product.id, current - 1);
            }

            @Override
            public void onContinue() {
                viewModel.viewCart(); // vao thang man Xac nhan don hang — "Xac minh chinh chu" la nut rieng trong do
            }
        });
        detail = new DetailView(this, new DetailView.Callbacks() {
            @Override
            public void onBuyNow(Models.ProductDto product, int quantity, double totalAmount) {
                // "MUA HANG" -> them vao gio (gop neu da co) roi vao thang man Xac nhan don hang (spec R61); viec
                // "Xac minh chinh chu" la nut rieng BEN TRONG man do (truoc DAT MUA), khong con chan o day nua.
                viewModel.addToCart(product, quantity, false);
                viewModel.openOrderConfirm();
            }

            @Override
            public void onAddToCart(Models.ProductDto product, int quantity) {
                viewModel.addToCart(product, quantity); // notice "Thêm giỏ hàng thành công" tự hiện Toast (xem onStateChanged)
            }
        });
        order = new OrderConfirmView(this, new OrderConfirmView.Callbacks() {
            @Override
            public void onBack() {
                navigateBack();
            }

            @Override
            public void onGoHome() {
                viewModel.goHome();
            }

            @Override
            public void onSelectProvince(long provinceId) {
                viewModel.selectProvince(provinceId);
            }

            @Override
            public void onSelectStore(long storeId) {
                viewModel.selectStore(storeId);
            }

            @Override
            public void onChangeCartQuantity(String productId, int quantity) {
                viewModel.updateCartQuantity(productId, quantity);
            }

            @Override
            public void onRemoveCartItem(String productId) {
                viewModel.removeFromCart(productId);
            }

            @Override
            public void onPlaceOrder() {
                // SRS "Xac nhan thong tin KH" (CartViewModel.placeOrder() that): da xac minh nhung CHUA xac
                // nhan SDT/email trong phien nay -> mo CustomerConfirmView truoc, chua vao thang Thanh toan.
                if (!receiverConfirmed) {
                    openCustomerConfirm();
                    return;
                }
                openPayment();
            }

            @Override
            public void onRequestCartRefresh(KioskViewModel.CartRefreshCallback callback) {
                viewModel.refreshCartProductsForCheck(callback);
            }

            @Override
            public void onVerifyIdentity() {
                // Bam "XAC MINH CHINH CHU" luon mo thang popup quet khuon mat, du da dang nhap hay chua
                // (khong bat dang ky tai khoan truoc nua).
                identityVerify.reset();
                show(identityVerify, true);
            }
        });
        identityVerify = new IdentityVerifyView(this, new IdentityVerifyView.Listener() {
            @Override
            public void onCancel() {
                show(identityVerify, false);
            }

            @Override
            public void onVerified() {
                show(identityVerify, false);
                // Quet FaceID xong ma chua dang nhap (chua co ten tu lastState): dien thong tin demo de "Thong tin
                // nhan hang" co du du lieu hien thi, giong nhu luong dang nhap tu Home.
                boolean loggedIn = lastState != null && lastState.loggedInCustomerName != null;
                if (!loggedIn) {
                    order.setCustomerInfo("Nguyễn Văn A (demo)", "099999999999", "0987654321", "Hà Nội");
                }
                order.setVerified(true);
            }

            @Override
            public void onGoHome() {
                show(identityVerify, false);
                viewModel.goHome();
            }
        });
        customerConfirm = new CustomerConfirmView(this, new CustomerConfirmView.Callbacks() {
            @Override
            public void onBack() {
                show(customerConfirm, false);
            }

            @Override
            public void onGoHome() {
                show(customerConfirm, false);
                viewModel.goHome();
            }

            @Override
            public void onDone(String phone, String email) {
                receiverConfirmed = true;
                confirmedPhone = phone;
                confirmedEmail = email;
                order.setConfirmedContact(phone);
                show(customerConfirm, false);
                openPayment();
            }
        });
        customerConfirm.setVisibility(View.GONE); // mac dinh an - thieu dong nay la nguyen nhan man "Xac nhan thong
        // tin khach hang" che het toan bo app tu luc khoi dong (nuot moi cham, k the bam nut nao khac).
        payment = new PaymentView(this, new PaymentView.Listener() {
            @Override
            public void onCancel() {
                show(payment, false);
            }

            @Override
            public void onFinished() {
                // SRS VIII.10 (repo that): sau "Tiep tuc trai nghiem" ve lai DANH SACH SAN PHAM (khong phai
                // Home) - man do tu tai lai gia/ton.
                show(payment, false);
                viewModel.clearCart();
                viewModel.openProducts();
            }

            @Override
            public void onGoHome() {
                show(payment, false);
                viewModel.goHome();
            }
        });
        setup = new SetupView(this, new SetupView.Callbacks() {
            @Override
            public void onActivate(String deviceCode, String activationCode) {
                viewModel.activate(deviceCode, activationCode);
            }

            @Override
            public void onScanQr() {
                ScanOptions options = new ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("Đưa mã QR kích hoạt (do quản trị viên cấp) vào khung")
                        .setBeepEnabled(false)
                        .setOrientationLocked(true);
                qrScanner.launch(options);
            }

            @Override
            public void onEditBaseUrl() {
                if (BuildConfig.DEBUG) showBaseUrlDialog(); // II.2.14: release khong co loi tat nay
            }

            @Override
            public void onDebugPreviewOrderConfirm() {
                if (!BuildConfig.DEBUG) return;
                // May chua kich hoat nen render() vua danh dau bootIdlePending=true (II.1.1: vao man cho
                // truoc) — huy no va an man cho ngay, neu khong man cho se de len tren man Xac nhan gia lap.
                bootIdlePending = false;
                idleShowing = false;
                idle.animate().cancel();
                idle.setVisibility(View.GONE);
                viewModel.debugPreviewOrderConfirm();
            }
        });
        status = new StatusView(this, () -> viewModel.retryNow());
        website = new WebsiteView(this, () -> viewModel.closeWebsite());
        goodsPickup = new GoodsPickupView(this, this::closeGoodsPickup);
        customerAuth = new CustomerAuthView(this, new CustomerAuthView.Listener() {
            @Override
            public void onBack() {
                verifyingForOrder = false; // huy dang ky giua chung tu nut "Xac minh chinh chu": khong coi la da dang nhap
                closeCustomerAuth(); // ve Chi tiet, so luong van giu nguyen
            }

            @Override
            public void onContinue() {
                continueFromCustomerAuth();
            }
        });

        offlineStrip = Ui.text(this, "MẤT KẾT NỐI — ĐANG HIỂN THỊ DỮ LIỆU CŨ", 8f, Ui.SURFACE_WHITE, Ui.W_BOLD);
        offlineStrip.setGravity(Gravity.CENTER);
        offlineStrip.setLetterSpacing(0.04f);
        offlineStrip.setBackgroundColor(Ui.BURGUNDY_PRIMARY);
        offlineStrip.setVisibility(View.GONE);

        idle = new IdlePlayerView(this, new IdlePlayerView.Callbacks() {
            @Override
            public void onTap(Models.AdDto ad) {
                // Cham vao banner hoac nut "Mua ngay" -> chi thoat ve giao dien chinh, KHONG mo lien ket/dieu huong
                // theo hanh dong cua quang cao (San pham/Danh muc/Website) theo yeu cau cua khach hang.
                exitIdle();
            }

            @Override
            public void onPlaybackError(Models.AdDto ad, String message) {
                viewModel.reportPlaybackError(ad, message);
            }

            @Override
            public void onLoginLogout(boolean currentlyLoggedIn) {
                if (currentlyLoggedIn) {
                    viewModel.logout();
                } else {
                    // Thoat man cho ve Home TRUOC (dong bo, render() chay ngay) roi moi mo lop phu dang nhap, vi
                    // openLoginFromHome() chi mo khi currentScreen==HOME va khong con o man cho.
                    exitIdle();
                    openLoginFromHome();
                }
            }
        });
        idle.setVisibility(View.GONE);

        FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        shell = new ShellView(this, new ShellView.Callbacks() {
            @Override
            public void onNavHome() {
                viewModel.goHome();
            }

            @Override
            public void onNavProducts() {
                navigateToProducts();
            }

            @Override
            public void onNavComingSoon(int nav) {
                Toast.makeText(KioskActivity.this, "Sắp ra mắt", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBack() {
                navigateBack();
            }

            @Override
            public void onViewCart() {
                viewModel.viewCart();
            }

            @Override
            public void onLoginLogout(boolean currentlyLoggedIn) {
                if (currentlyLoggedIn) {
                    viewModel.logout();
                } else {
                    openLoginFromHome();
                }
            }
        });
        shell.container().addView(home, full);
        shell.container().addView(products, full);
        shell.container().addView(detail, full);
        shell.container().addView(order, full);
        root.addView(shell, full);
        root.addView(setup, full);
        root.addView(goodsPickup, full);
        root.addView(customerAuth, full);
        root.addView(identityVerify, full);
        root.addView(customerConfirm, full);
        root.addView(payment, full);
        root.addView(status, full);
        root.addView(website, full);
        root.addView(idle, full);
        // Dai mat ket noi nam TREN man cho (II.2.1): them sau idle va showIdle() dua no len lai tren cung.
        root.addView(offlineStrip, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.px(OFFLINE_STRIP_H), Gravity.TOP));
        for (View v : new View[]{shell, home, products, detail, order, setup, goodsPickup, customerAuth, identityVerify, payment, status, website}) v.setVisibility(View.GONE);
        // Chua co diem vao that cho man Tra hang: chi ban DEBUG, giu bieu tuong MH tren rail (o man Home).
        if (BuildConfig.DEBUG) shell.setEmblemLongPress(this::openGoodsPickup);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets i = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            applyInsets(i.left, i.top, i.right, i.bottom, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // Kiosk: nut Back chi de lui giua cac man, khong bao gio thoat app.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (idleShowing || currentScreen == null) return;
                if (goodsPickup.getVisibility() == View.VISIBLE) {
                    closeGoodsPickup();
                    return;
                }
                if (payment.getVisibility() == View.VISIBLE) {
                    if (payment.getState() == PaymentView.State.TRANSFER) show(payment, false);
                    return; // o man Ket qua giao dich: khong cho Back thoat ngang, phai bam "Tiep tuc trai nghiem"
                }
                if (identityVerify.getVisibility() == View.VISIBLE) {
                    show(identityVerify, false);
                    return;
                }
                if (customerAuth.getVisibility() == View.VISIBLE) {
                    closeCustomerAuth();
                    return;
                }
                if (lastState != null && lastState.websiteUrl != null) {
                    viewModel.closeWebsite();
                    return;
                }
                navigateBack();
            }
        });

        viewModel.setListener(this::render);
        handler.postDelayed(idleCheck, 1000);
    }

    private void openGoodsPickup() {
        if (currentScreen != KioskViewModel.AppScreen.HOME || idleShowing) return;
        goodsPickup.setState(GoodsPickupView.State.WAITING);
        show(goodsPickup, true);
    }

    private void closeGoodsPickup() {
        show(goodsPickup, false);
    }


    /** Nut "Dang nhap" o Home: mo cung lop phu Xac thuc khach hang nhung khong lien quan don hang nao. */
    private void openLoginFromHome() {
        if (currentScreen != KioskViewModel.AppScreen.HOME || idleShowing) return;
        authForLoginOnly = true;
        pendingProduct = null;
        customerAuth.reset();
        show(customerAuth, true);
    }

    /**
     * Dong lop phu (Back / mui ten / het gio / ve Kich hoat): man Chi tiet ben duoi khong bi dong vao nen so luong nguyen ven.
     * Khong reset {@link #authForLoginOnly} o day vi {@link #continueFromCustomerAuth()} goi ham nay TRUOC khi doc co —
     * co da duoc dat lai dung cho moi khi mo lai qua {@link #openCustomerAuth} / {@link #openLoginFromHome}.
     */
    private void closeCustomerAuth() {
        show(customerAuth, false);
    }

    /** "DAT MUA" nhung chua xac nhan SDT/email trong phien nay (CartViewModel.placeOrder() that): mo S07. */
    private void openCustomerConfirm() {
        String dob = "09/09/1999"; // Phase 1: chua co ho so e-KYC that, dien demo giong cac truong con lai
        customerConfirm.bind(order.getRecipientName(), order.getRecipientId(), dob, order.getRecipientAddress(),
                confirmedPhone != null ? confirmedPhone : order.getRecipientPhone(), confirmedEmail);
        show(customerConfirm, true);
    }

    /**
     * Mo man Thanh toan QR voi thong tin don hang hien tai. Chan luon tai day neu dang mat ket noi:
     * man nay chi hien QR chuyen khoan tinh (Phase 1, chua co cong thanh toan that), khach van co the
     * chuyen khoan thuc su nhung kiosk mat ket noi se KHONG ghi nhan/doi soat duoc don hang do o backend
     * (gia/ton kho cung co the da doi so voi luc hien gio hang) - rui ro mat tien that cho khach va BTMH.
     */
    private void openPayment() {
        if (lastState != null && lastState.offline) {
            Toast.makeText(this, "Mất kết nối — chưa thể thanh toán lúc này. Vui lòng thử lại sau.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] sum = order.getPaymentSummary();
        String hotline = lastState != null ? lastState.hotline : null;
        payment.bind(sum[0], sum[1], sum[2], sum[3], hotline);
        show(payment, true);
    }

    /**
     * "Tiep tuc" tu man Xac thuc. Hai truong hop:
     * - Tu nut "Dang nhap" o Home ({@link #authForLoginOnly}): chi danh dau da dang nhap roi dong lop phu, khong dong gi den don hang.
     * - Tu "DAT MUA" o man Chi tiet: dung y nhu truoc day — them san pham dang xem (cong don neu da co trong gio) roi mo
     *   man xac nhan don hang voi TOAN BO gio hang (spec R61 sheet "Chi tiet san pham").
     */
    private void continueFromCustomerAuth() {
        String[] recipient = customerAuth.getRecipientInfo(); // doc TRUOC khi dong, vi closeCustomerAuth an view (van con du lieu nhung doc som cho chac)
        closeCustomerAuth();
        if (authForLoginOnly) {
            viewModel.login(CustomerAuthView.DEMO_CUSTOMER_NAME);
            authForLoginOnly = false;
            return;
        }
        if (verifyingForOrder) {
            // Chua dang nhap, bam "Xac minh chinh chu" o man Xac nhan don hang -> dang ky xong thi coi nhu da dang
            // nhap LUON VA da xac minh chinh chu (khong bat quet khuon mat lai nua), roi o nguyen man Xac nhan don hang.
            viewModel.login(CustomerAuthView.DEMO_CUSTOMER_NAME);
            if (recipient != null) order.setCustomerInfo(recipient[0], recipient[1], recipient[2], recipient[3]);
            order.setVerified(true);
            verifyingForOrder = false;
            return;
        }
        if (pendingProduct != null) viewModel.addToCart(pendingProduct, pendingQuantity, false);
        pendingProduct = null;
        // "Thong tin nhan hang" o man Xac nhan don hang: dien tu ket qua xac thuc vua xong (demo/form, chua co API dinh danh that).
        if (recipient != null) order.setCustomerInfo(recipient[0], recipient[1], recipient[2], recipient[3]);
        viewModel.openOrderConfirm();
    }

    /** Lui 1 man (nut Back cung / nut "Quay lai" tren thanh dau khung): cung y nghia voi hanh dong truoc day cua tung man. */
    private void navigateBack() {
        if (currentScreen == null) return;
        switch (currentScreen) {
            case ORDER:
                viewModel.closeOrderConfirm();
                break;
            case DETAIL:
                viewModel.closeProductDetail();
                break;
            case PRODUCTS:
                viewModel.closeProducts();
                break;
            default:
                break;
        }
    }

    /** Muc "San pham" tren rail: tu bat ky man nao trong khung ve danh sach san pham (dung lai cac hanh dong co san). */
    private void navigateToProducts() {
        if (currentScreen == null) return;
        switch (currentScreen) {
            case HOME:
                viewModel.openProducts();
                break;
            case ORDER:
                viewModel.closeOrderConfirm();
                viewModel.closeProductDetail();
                break;
            case DETAIL:
                viewModel.closeProductDetail();
                break;
            default:
                break;
        }
    }

    private void render(KioskViewModel.UiState state) {
        KioskViewModel.AppScreen previous = currentScreen;
        lastState = state;
        currentScreen = state.screen;
        idleTimeoutSeconds = state.idleTimeoutSeconds;

        if (previous != state.screen) {
            if (state.screen != KioskViewModel.AppScreen.DETAIL) closeCustomerAuth(); // roi Chi tiet (ve Home/Kich hoat...): bo lop phu
            boolean inShell = state.screen == KioskViewModel.AppScreen.HOME || state.screen == KioskViewModel.AppScreen.PRODUCTS
                    || state.screen == KioskViewModel.AppScreen.DETAIL || state.screen == KioskViewModel.AppScreen.ORDER;
            show(shell, inShell);
            if (inShell) shell.setScreen(state.screen);
            show(home, state.screen == KioskViewModel.AppScreen.HOME);
            show(products, state.screen == KioskViewModel.AppScreen.PRODUCTS);
            show(detail, state.screen == KioskViewModel.AppScreen.DETAIL);
            show(order, state.screen == KioskViewModel.AppScreen.ORDER);
            show(setup, state.screen == KioskViewModel.AppScreen.SETUP);
            if (state.screen == KioskViewModel.AppScreen.ORDER && previous != KioskViewModel.AppScreen.ORDER) {
                order.resetStorePicker(); // vao man Xac nhan don hang moi: dat lai o tim kiem/tinh-thanh dang mo
                order.captureEntryPrices(state.cart); // "gia luc vao man" - moc so sanh cho bottom sheet VIII luc DAT MUA
                receiverConfirmed = false; // phai xac nhan lai SDT/email (CustomerConfirmView) moi phien Xac nhan don hang
                confirmedPhone = null;
                confirmedEmail = null;
                if (state.loggedInCustomerName != null) {
                    // Da dang nhap: coi nhu da xac minh chinh chu luon, hien the thong tin nhan hang, khong can
                    // bam "XAC MINH CHINH CHU" / quet FaceID nua (Phase 1: chua co ho so that, dien du lieu demo).
                    order.setCustomerInfo(state.loggedInCustomerName, "099999999999", "0987654321", "Hà Nội");
                    order.setVerified(true);
                }
            }
        }

        shell.setSubtitle(state.screen == KioskViewModel.AppScreen.HOME ? HomeView.subtitle(state) : null);
        shell.setCartCount(state.cartItemCount());
        shell.bindHome(state.loggedInCustomerName, state.hotline);
        home.setLoggedIn(state.loggedInCustomerName != null);
        home.bind(state);
        products.bind(state);
        setup.bind(state.isActivating, state.activationError);
        if (state.selectedProduct != null) {
            if (state.screen == KioskViewModel.AppScreen.DETAIL && previous != KioskViewModel.AppScreen.DETAIL
                    && previous != KioskViewModel.AppScreen.ORDER) {
                detail.resetQuantity(); // vao Chi tiet tu danh sach/Home: so luong ve 1
            }
            detail.bind(state.selectedProduct, state.pricesHidden, state.sellingOpen, state.limitQuantity,
                    state.openTime, state.closeTime);
            if (state.screen == KioskViewModel.AppScreen.DETAIL) shell.setDetailTitle(state.selectedProduct.name);
        }
        if (state.screen == KioskViewModel.AppScreen.ORDER) order.bind(state);

        // Thong bao trang thai may (chi khi da kich hoat) + dai mat ket noi khi van co du lieu cu.
        boolean showStatus = state.status != KioskViewModel.Status.NONE && state.screen != KioskViewModel.AppScreen.SETUP;
        if (showStatus) status.bind(state);
        show(status, showStatus);
        boolean strip = state.offline && !showStatus && state.screen != KioskViewModel.AppScreen.SETUP;
        offlineStrip.setVisibility(strip ? View.VISIBLE : View.GONE);

        // II.1.1: khoi dong (hoac vua kich hoat xong) -> vao man cho quang cao truoc, khach cham moi vao Home.
        if (state.screen == KioskViewModel.AppScreen.SETUP) {
            closeGoodsPickup();
            closeCustomerAuth();
            bootIdlePending = true;
        } else if (bootIdlePending) {
            bootIdlePending = false;
            if (!idleShowing) showIdle();
        }

        // Thong bao ky thuat ngan (canh bao lech gio khi kich hoat, SSE bi thay the...) — hien 1 lan.
        if (state.notice != null) {
            Toast.makeText(this, state.notice, Toast.LENGTH_LONG).show();
            viewModel.clearNotice();
        }

        if (state.websiteUrl != null) {
            website.show(state.websiteUrl);
            show(website, true);
        } else {
            show(website, false);
            website.reset();
        }
    }

    /** Hien/an mot man; man moi hien len mo dan de chuyen man mem hon. */
    private void show(View view, boolean visible) {
        if (!visible) {
            view.animate().cancel();
            view.setTranslationY(0f);
            view.setVisibility(View.GONE);
        } else if (view.getVisibility() != View.VISIBLE) {
            view.setAlpha(0f);
            view.setTranslationY(Ui.px(14));
            view.setVisibility(View.VISIBLE);
            view.animate().alpha(1f).translationY(0f).setDuration(FADE_MS)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f)).start();
        }
    }

    // ==================== TOAN MAN HINH + VUNG AN TOAN ====================

    /**
     * Ve tran man hinh (edge-to-edge): dai den o dau man hinh truoc day la vung display cutout (lo camera/tai tho)
     * bi he thong "letterbox" khi an thanh trang thai voi layoutInDisplayCutoutMode = DEFAULT. Dat SHORT_EDGES de
     * noi dung tran vao cutout, roi tu tru vung an toan bang {@link #applyInsets}.
     */
    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    }

    /** Chuyen vung an toan (cutout / thanh he thong neu hien / ban phim) cho tung man. */
    private void applyInsets(int l, int t, int r, int b, int barsBottom) {
        shell.onInsets(l, t, r, b, barsBottom); // rail + thanh dau + cac man trong khung (home/products/detail/order)
        View[] screens = {setup, goodsPickup, customerAuth, identityVerify, customerConfirm, payment, status, website, idle};
        for (View v : screens) {
            if (v instanceof Ui.InsetAware) ((Ui.InsetAware) v).onInsets(l, t, r, b);
            else v.setPadding(l, t, r, b);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) offlineStrip.getLayoutParams();
        lp.height = Ui.px(OFFLINE_STRIP_H) + t;
        offlineStrip.setLayoutParams(lp);
        offlineStrip.setPadding(0, t, 0, 0);
    }

    // ==================== HOP THOAI DIA CHI MAY CHU ====================

    /** Ky thuat vien nhan giu logo o man Kich hoat de doi may chu (vd STAGING/DEV). */
    private void showBaseUrlDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(viewModel.getBaseUrl());
        input.setSelection(input.length());
        new AlertDialog.Builder(this)
                .setTitle("Địa chỉ máy chủ (kỹ thuật viên)")
                .setView(input)
                .setNegativeButton("Huỷ", null)
                .setPositiveButton("Lưu", (d, w) -> {
                    String url = input.getText().toString().trim();
                    if (url.startsWith("https://") || url.startsWith("http://")) {
                        viewModel.setBaseUrl(url);
                        Toast.makeText(this, "Đã lưu: " + url, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Địa chỉ phải bắt đầu bằng https://", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    // ==================== MAN CHO / QUANG CAO ====================

    /** Anh mac dinh dong goi san (assets/ads) — phat khi chua co quang cao nao tai duoc tu server. */
    private void loadDefaultAds() {
        try {
            String[] names = getAssets().list("ads");
            if (names == null) return;
            Arrays.sort(names);
            for (String name : names) defaultAdUris.add("file:///android_asset/ads/" + name);
        } catch (IOException ignored) {
            // Khong co thu muc ads -> man cho khong co gi de phat.
        }
    }

    private void showIdle() {
        if (lastState == null) return;
        idleShowing = true;
        // Popup/sheet (gioi han, het ton, gia thay doi, chon Tinh/Cua hang...) deu la Dialog rieng, KHONG nam
        // trong cay View cua Activity nen cac show(..., false) ben duoi khong dong duoc - phai tu dong rieng
        // (yeu cau 15/10/2026: khong de popup treo tren man hinh cho khi khong ai con tuong tac).
        OrderPopups.dismissActive();
        closeGoodsPickup(); // het thoi gian cho: bo man Tra hang (neu dang mo) truoc khi vao man cho
        closeCustomerAuth(); // ... va man Xac thuc khach hang (neu dang mo)
        show(payment, false); // ... va man Thanh toan (neu dang mo)
        show(identityVerify, false); // ... va man Xac minh chinh chu (neu dang mo)
        idle.setAlpha(0f);
        idle.setVisibility(View.VISIBLE);
        idle.bringToFront();
        offlineStrip.bringToFront(); // dai "Mat ket noi" van hien tren man cho
        idle.animate().alpha(1f).setDuration(400).start();
        idle.bind(lastState.hotline, lastState.loggedInCustomerName);
        idle.start(lastState.ads, defaultAdUris);
        viewModel.onIdleChanged(true); // may ranh -> thoi diem an toan de xoay khoa neu duoc yeu cau
    }

    private void exitIdle() {
        if (!idleShowing) return;
        idleShowing = false;
        idle.animate().cancel();
        idle.stop();
        idle.setVisibility(View.GONE);
        lastInteraction = SystemClock.elapsedRealtime();
        viewModel.onIdleChanged(false);
        viewModel.goHome();
    }

    /**
     * Moi cham deu reset dong ho. Truoc day o man cho, touch bi chan het va chuyen tay cho mot GestureDetector
     * duy nhat (khi man cho chi co quang cao, khong co nut that nao) — gio man cho co nut that (goi hotline/dang
     * nhap/Mua ngay) nen phai de touch di qua duong dispatch binh thuong cua Android thi cac nut do moi bam duoc;
     * {@link IdlePlayerView} tu dat touch listener (vuot/cham banner) tren chinh no: theo co che dispatch chuan
     * cua Android, ViewGroup chi tu xu ly ACTION_DOWN khi KHONG co view con clickable nao nam dung vi tri cham,
     * nen cham vao nut that (goi hotline/dang nhap/Mua ngay) se duoc chinh nut do nhan, con cham vung trong/vuot
     * ngang moi roi xuong listener cua IdlePlayerView.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastInteraction = SystemClock.elapsedRealtime();
        return super.dispatchTouchEvent(ev);
    }

    // ==================== VONG DOI ====================

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    /** Toan man hinh (an thanh trang thai/dieu huong) — vuot mep de hien tam thoi. */
    private void hideSystemBars() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        home.resumeVideo();
        products.resumeVideo();
    }

    @Override
    protected void onStop() {
        home.pauseVideo();
        products.pauseVideo();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        idle.stop();
        viewModel.setListener(null);
        super.onDestroy();
    }
}
