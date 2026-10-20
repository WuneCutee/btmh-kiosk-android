package vn.baotinmanhhai.btmhkiosk.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;

import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import vn.baotinmanhhai.btmhkiosk.auth.DeviceAuthService;
import vn.baotinmanhhai.btmhkiosk.data.DataCache;
import vn.baotinmanhhai.btmhkiosk.network.GoldHistoryClient;
import vn.baotinmanhhai.btmhkiosk.data.KioskClock;
import vn.baotinmanhhai.btmhkiosk.data.KioskConfig;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.data.VersionUtil;
import vn.baotinmanhhai.btmhkiosk.network.AdsManager;
import vn.baotinmanhhai.btmhkiosk.network.ErrorReporter;
import vn.baotinmanhhai.btmhkiosk.network.KioskApiService;
import vn.baotinmanhhai.btmhkiosk.network.KioskPriceStreamClient;
import vn.baotinmanhhai.btmhkiosk.network.NetworkModule;

/**
 * Trang thai + luong nghiep vu cua kiosk theo spec BTMH_API_Kiosk_Phase1 v1.0.1:
 * - Man hinh: SETUP / HOME (bang gia) / PRODUCTS / DETAIL / ORDER; lop trang thai loi (Status) phu len tren.
 * - Phien: lay token (II.2.1), gia han truoc khi het han 5 phut, xoay khoa khi may ranh (II.2.15), phan loai loi
 *   (thu hoi 121, khoa, dong ho, mat mang, may chu loi) va thu lai theo backoff.
 * - Du lieu: cache dia, cau hinh (II.2.12) + ETag moi 5 phut, quang cao (II.2.2), san pham co phan trang + tim kiem
 *   (II.2.6/7), man xac nhan don hang (II.2.9-II.2.11).
 * - Gia realtime: SSE (II.3) co version; mat SSE > 30s -> goi II.2.6 moi 60s + an mui ten; gia cu -> an gia.
 * Moi thay doi state deu di qua updateState() de chay tren main thread.
 */
public class KioskViewModel extends AndroidViewModel {

    private static final String TAG = "KioskViewModel";

    /** HOME = bang gia vang; PRODUCTS = "San pham tich luy"; DETAIL = chi tiet; ORDER = Xac nhan don hang. */
    public enum AppScreen { SETUP, HOME, PRODUCTS, DETAIL, ORDER }

    /** Man thong bao trang thai may phu len tren (spec II.2.1, II.2.12). */
    public enum Status {
        NONE,
        NEEDS_UPDATE,          // appVersion < minAppVersion: chi phat man cho
        WAITING_CONNECTION,    // chua tung tai duoc du lieu
        SERVICE_UNAVAILABLE,   // loi ngoai bang ma loi: "May tam ngung phuc vu"
        CLOCK_ERROR,           // dong ho lech > 5 phut ke ca sau khi bu lech
        KEY_NOT_RECOGNIZED     // 104: khoa khong duoc cong nhan
    }

    /** Nguong idle mac dinh (spec II.2.12: 60s) cho toi khi bootstrap tai xong; bootstrap ghi de. */
    public static final int DEFAULT_IDLE_TIMEOUT_SECONDS = 60;

    private static final int LIST_PAGE_SIZE = 20;
    private static final int BOARD_PAGE_SIZE = 100; // toi da cua II.2.6
    private static final int BOARD_MAX_PAGES = 5;
    private static final long PERIODIC_MS = 5 * 60 * 1000L;
    /** Bien dong gia theo ngay chi doi 1 lan/ngay + gia trong ngay: 10 phut/lan la du, tranh dap website ben ngoai. */
    private static final long GOLD_REFRESH_MS = 10 * 60 * 1000L;
    private static final long SSE_LOST_MS = 30_000L;
    private static final long FALLBACK_POLL_MS = 60_000L;
    private static final long BOOTSTRAP_MAX_AGE_MS = 24L * 60 * 60 * 1000;

    public static class UiState {
        public AppScreen screen = AppScreen.SETUP;
        public Status status = Status.NONE;
        public boolean isLoading = false;
        public boolean isActivating = false;
        public String activationError = null;
        /** Mat mang / kenh tam dung nhung van co du lieu cu de hien. */
        public boolean offline = false;

        public List<Models.BannerDto> banners = new ArrayList<>();
        public List<Models.CategoryDto> categories = new ArrayList<>();
        public String selectedCategoryId = "ALL";
        public List<Models.ProductDto> products = new ArrayList<>();
        public boolean productsHasMore = false;
        public boolean productsLoadingMore = false;
        public String searchQuery = "";
        public Models.ProductDto selectedProduct = null;

        public List<Models.SsePriceItemDto> board = new ArrayList<>();
        public String boardUpdatedAt = null;
        public boolean sseConnected = false;
        public boolean trendsHidden = false;
        public boolean pricesHidden = false;

        // ---- Bien dong gia vang theo ngay (website BTMH, man Home) ----
        public GoldHistoryClient.History goldHistory = null;
        public String goldCode = GoldHistoryClient.DEFAULT_CODE;
        public boolean goldLoading = false;
        public boolean goldError = false;

        public String deviceCode = "";
        public String storeName = null;
        public String hotline = null;
        public int idleTimeoutSeconds = DEFAULT_IDLE_TIMEOUT_SECONDS;
        public boolean sellingOpen = false;
        public String openTime = null;  // "HH:mm:ss" tu bootstrap - man Chi tiet hien "Thoi gian mo ban: tu X den Y" khi ngoai gio
        public String closeTime = null;

        public List<AdsManager.PlayableAd> ads = new ArrayList<>();
        public String websiteUrl = null;

        // ---- Man xac nhan don hang (II.2.9-II.2.11) ----
        public List<Models.ProvinceDto> provinces = new ArrayList<>();
        public Long selectedProvinceId = null;
        public List<Models.StoreDto> stores = new ArrayList<>();
        public Long selectedStoreId = null;
        public Double limitQuantity = null;
        public boolean orderLoading = false;
        public String orderError = null;

        /**
         * Khach da dang nhap o Home chua (spec sheet "Dang nhap Dang xuat"): null = chua dang nhap. CHUA co API dinh
         * danh khach hang that (xem CustomerAuthView) nen gia tri nay chi dien duoc qua man Xac thuc (debug: nut
         * "Da dinh danh"). Tu dong xoa khi khach roi phien ve man cho (BR_003), giong gio hang.
         */
        public String loggedInCustomerName = null;

        /**
         * Gio hang (spec "Danh sach san pham"/"Thong tin don hang"): nhieu san pham, moi san pham 1 so luong. Them qua
         * {@link #addToCart}, "DAT MUA" o man Chi tiet van di thang (khong qua gio hang) nhu truoc — gio hang chi anh
         * huong nut "THEM VAO GIO HANG". Mat khi dong app hoac khach roi man cho (chua luu dia/server).
         */
        public List<Models.CartItem> cart = new ArrayList<>();

        /** Tong so luong san pham (dong) trong gio — hien tren badge gio hang. */
        public int cartItemCount() {
            int n = 0;
            for (Models.CartItem it : cart) n += it.quantity;
            return n;
        }

        /** Tong gia tri gio hang (VND). */
        public double cartTotalValue() {
            double sum = 0;
            for (Models.CartItem it : cart) sum += it.lineTotal();
            return sum;
        }

        public String errorMessage = null;
        /** Thong bao ky thuat ngan (Toast) — Activity hien 1 lan roi goi clearNotice(). */
        public String notice = null;

        UiState copy() {
            UiState s = new UiState();
            s.screen = screen;
            s.status = status;
            s.isLoading = isLoading;
            s.isActivating = isActivating;
            s.activationError = activationError;
            s.offline = offline;
            s.banners = banners;
            s.categories = categories;
            s.selectedCategoryId = selectedCategoryId;
            s.products = products;
            s.productsHasMore = productsHasMore;
            s.productsLoadingMore = productsLoadingMore;
            s.searchQuery = searchQuery;
            s.selectedProduct = selectedProduct;
            s.board = board;
            s.boardUpdatedAt = boardUpdatedAt;
            s.sseConnected = sseConnected;
            s.trendsHidden = trendsHidden;
            s.pricesHidden = pricesHidden;
            s.goldHistory = goldHistory;
            s.goldCode = goldCode;
            s.goldLoading = goldLoading;
            s.goldError = goldError;
            s.deviceCode = deviceCode;
            s.storeName = storeName;
            s.hotline = hotline;
            s.idleTimeoutSeconds = idleTimeoutSeconds;
            s.sellingOpen = sellingOpen;
            s.openTime = openTime;
            s.closeTime = closeTime;
            s.ads = ads;
            s.websiteUrl = websiteUrl;
            s.provinces = provinces;
            s.selectedProvinceId = selectedProvinceId;
            s.stores = stores;
            s.selectedStoreId = selectedStoreId;
            s.limitQuantity = limitQuantity;
            s.orderLoading = orderLoading;
            s.orderError = orderError;
            s.cart = cart;
            s.loggedInCustomerName = loggedInCustomerName;
            s.errorMessage = errorMessage;
            s.notice = notice;
            return s;
        }
    }

    public interface Listener {
        void onStateChanged(UiState state);
    }

    private final KioskConfig config;
    private final DataCache cache;
    private final DeviceAuthService authService;
    private final KioskApiService apiService;
    private final KioskPriceStreamClient priceStream;
    private final ErrorReporter errorReporter;
    private final AdsManager adsManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newFixedThreadPool(3);

    private UiState state = new UiState();
    /** Man se quay ve khi dong man Xac nhan don hang (Chi tiet neu tu "DAT MUA", San pham neu tu icon gio hang tren thanh dau). */
    private AppScreen orderReturnScreen = AppScreen.DETAIL;
    private Listener listener;

    private volatile String accessToken;
    private Runnable tokenRefreshRunnable;
    private Runnable sessionRetryRunnable;
    private Runnable loadRetryRunnable;
    private int sessionAttempts = 0;
    private int loadAttempts = 0;

    /** Bo dem version chung cua snapshot/price (chi doc/ghi tren main thread). */
    private long priceVersion = -1;
    private long lastPriceAtElapsed = SystemClock.elapsedRealtime();
    private long sseLostAtElapsed = 0L;
    private long nextFallbackPollElapsed = 0L;
    private volatile boolean fallbackPolling = false;

    private volatile String currentCategoryId = "ALL";
    private volatile String currentQuery = "";
    private int listPage = 0;
    private long searchToken = 0;

    private String bootstrapEtag;
    private volatile String adsEtag;
    private Models.BootstrapConfigDto bootstrap;
    private long bootstrapAtMs = 0L;
    /** Thoi diem tai banner/nhom (II.2.4/II.2.5: cache 5 phut) va danh sach san pham (II.2.6: cu > 5 phut thi tai lai). */
    private volatile long bannersAtMs = 0L;
    private volatile long categoriesAtMs = 0L;
    private volatile long productsAtMs = 0L;
    private final GoldHistoryClient goldClient = new GoldHistoryClient();
    private volatile String goldCode = GoldHistoryClient.DEFAULT_CODE;
    private volatile long goldAtMs = 0L;
    private int stalePriceSeconds = 300;
    private volatile boolean rotationRequested = false;
    private volatile boolean idleShowing = false;
    private boolean sessionStarted = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            mainHandler.postDelayed(this, 5000);
        }
    };
    private final Runnable periodic = new Runnable() {
        @Override
        public void run() {
            if (System.currentTimeMillis() - goldAtMs >= GOLD_REFRESH_MS) fetchGoldHistory(goldCode, false);
            if (accessToken != null) {
                worker.execute(() -> {
                    try {
                        loadBootstrapSync();
                    } catch (Exception e) {
                        Log.w(TAG, "Bootstrap dinh ky loi: " + e);
                    }
                    refreshBannersAndCategories(true); // phien dai: banner/nhom lam moi toi da 5 phut/lan (II.2.4/II.2.5)
                });
                loadAds();
            }
            mainHandler.postDelayed(this, PERIODIC_MS);
        }
    };

    public KioskViewModel(Application application) {
        super(application);
        config = new KioskConfig(application);
        cache = new DataCache(application);
        errorReporter = new ErrorReporter(cache);
        authService = new DeviceAuthService(NetworkModule.sharedClient(), config);
        apiService = new KioskApiService(NetworkModule.sharedClient(), config);
        apiService.setErrorReporter(errorReporter);
        apiService.setTokenRefresher(() -> {
            Models.DeviceSessionData s = authService.fetchSession();
            accessToken = s.accessToken;
            return s.accessToken;
        });
        errorReporter.attach(apiService);
        adsManager = new AdsManager(application, cache, NetworkModule.sharedClient(), errorReporter);
        priceStream = new KioskPriceStreamClient(config);
        priceStream.setListener(new StreamListener());

        state.deviceCode = config.getDeviceCode();
        state.ads = adsManager.cachedPlayable();
        loadFromCache();
        state.goldHistory = cache.get("gold_" + goldCode, GoldHistoryClient.History.class);
        fetchGoldHistory(goldCode, true);

        mainHandler.postDelayed(ticker, 5000);
        mainHandler.postDelayed(periodic, PERIODIC_MS);

        // Man hinh dau phai dung NGAY tu dau — neu khong UI se thay SETUP 1 nhip roi nhay sai man.
        if (authService.hasLostKey()) {
            // Khong mo duoc khoa rieng (xoa du lieu/hong Keystore): man Kich hoat kem cau huong dan + ma may (II.2.1).
            state.screen = AppScreen.SETUP;
            state.activationError = "Máy cần kích hoạt lại, liên hệ quản trị viên. Mã máy: " + config.getDeviceCode();
        } else if (authService.isEnrolled()) {
            state.screen = AppScreen.HOME;
            startSession();
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) listener.onStateChanged(state.copy());
    }

    /** Activity da hien thong bao xong (goi tren main thread, khong can ve lai). */
    public void clearNotice() {
        state.notice = null;
    }

    // ==================== GIO HANG ====================

    /**
     * Them san pham vao gio hang (nut "THEM VAO GIO HANG" o man Chi tiet). Neu san pham da co trong gio thi cong don
     * so luong (spec: "Luu lai thong tin ... de khi back lai man hinh danh sach san pham thi cong don ngay"). Hien
     * thong bao "Them gio hang thanh cong" qua {@code notice}.
     */
    public void addToCart(Models.ProductDto product, int quantity) {
        addToCart(product, quantity, true);
    }

    /** showNotice=false: dung cho luong "DAT MUA" (vao thang gio hang, khong can bao "them gio hang thanh cong"). */
    public void addToCart(Models.ProductDto product, int quantity, boolean showNotice) {
        if (product == null || quantity <= 0) return;
        updateState(s -> {
            boolean merged = false;
            for (Models.CartItem it : s.cart) {
                if (it.product != null && it.product.id != null && it.product.id.equals(product.id)) {
                    it.quantity += quantity;
                    it.product = product; // cap nhat gia/ton moi nhat
                    merged = true;
                    break;
                }
            }
            if (!merged) s.cart.add(new Models.CartItem(product, quantity));
            if (showNotice) s.notice = "Thêm giỏ hàng thành công";
        });
    }

    /** Doi so luong 1 dong trong gio; so luong <= 0 thi xoa dong do. */
    public void updateCartQuantity(String productId, int quantity) {
        if (productId == null) return;
        updateState(s -> {
            for (int i = 0; i < s.cart.size(); i++) {
                Models.CartItem it = s.cart.get(i);
                if (it.product != null && productId.equals(it.product.id)) {
                    if (quantity <= 0) s.cart.remove(i);
                    else it.quantity = quantity;
                    break;
                }
            }
        });
    }

    public interface CartRefreshCallback {
        void onResult(List<Models.CartItem> freshCart);
    }

    /**
     * BUG THAT da tim thay 15/10/2026: OrderConfirmView.proceedToPlaceOrder() truoc day so sanh gia/ton kho
     * dua thang vao item.product cua CartItem dang co trong state.cart - nhung ProductDto do la snapshot
     * CHUP TU LUC THEM VAO GIO (addToCart/stepper tren luoi), KHONG tung duoc lam moi lai sau do (gia/ton kho
     * co the da doi rat nhieu luc khach con dang dien thong tin o man Thong tin don hang). Ket qua: popup "gia
     * thay doi" / "het ton kho" KHONG BAO GIO kich hoat duoc du server that su da doi, vi so sanh entryPrices
     * (chup tu chinh object cu nay) voi item.product.price (van la object cu do) luon bang nhau.
     *
     * CartViewModel.placeOrder() that goi refreshProducts() (II.2.8 tung dong) truoc khi tu kiem tra - ham nay
     * lam dung y vay: goi lai API chi tiet tung san pham trong gio NGAY LUC bam "DAT MUA", thay the toan bo
     * item.product bang ban moi nhat, roi moi tra ve qua callback de OrderConfirmView tu so sanh/kiem tra.
     */
    public void refreshCartProductsForCheck(CartRefreshCallback callback) {
        List<Models.CartItem> snapshot = new ArrayList<>(state.cart);
        worker.execute(() -> {
            List<Models.CartItem> fresh = new ArrayList<>();
            for (Models.CartItem item : snapshot) {
                Models.ProductDto latest = item.product;
                if (item.product != null && item.product.id != null) {
                    try {
                        Models.ProductDto d = apiService.productDetail(item.product.id);
                        if (d != null) latest = d;
                    } catch (Exception e) {
                        Log.w(TAG, "refreshCartProductsForCheck: khong lam moi duoc " + item.product.id + ": " + e);
                    }
                }
                fresh.add(new Models.CartItem(latest, item.quantity));
            }
            updateState(s -> s.cart = fresh);
            mainHandler.post(() -> callback.onResult(fresh));
        });
    }

    /** Bo 1 san pham khoi gio hang (nut "x" / xac nhan xoa trong popup). */
    public void removeFromCart(String productId) {
        if (productId == null) return;
        updateState(s -> {
            for (int i = 0; i < s.cart.size(); i++) {
                Models.CartItem it = s.cart.get(i);
                if (it.product != null && productId.equals(it.product.id)) {
                    s.cart.remove(i);
                    break;
                }
            }
        });
    }

    /** Don tat ca gio hang (vd sau khi "mo man xac nhan don hang" hoac khach roi man cho). */
    public void clearCart() {
        updateState(s -> s.cart = new ArrayList<>());
    }

    private void updateState(Consumer<UiState> mutator) {
        mainHandler.post(() -> {
            mutator.accept(state);
            if (listener != null) listener.onStateChanged(state.copy());
        });
    }

    // ==================== CACHE ====================

    private static <T> java.lang.reflect.Type listOf(Class<T> c) {
        return TypeToken.getParameterized(List.class, c).getType();
    }

    /** Nap du lieu lan truoc de hien ngay (mat mang van co gi de xem — "giu du lieu cu"). */
    private void loadFromCache() {
        List<Models.BannerDto> banners = cache.get("banners", listOf(Models.BannerDto.class));
        if (banners != null) state.banners = banners;
        Long bannersAt = cache.get("banners_at", Long.class);
        if (banners != null && bannersAt != null) bannersAtMs = bannersAt;
        List<Models.CategoryDto> cats = cache.get("categories", listOf(Models.CategoryDto.class));
        if (cats != null) state.categories = cats;
        Long categoriesAt = cache.get("categories_at", Long.class);
        if (cats != null && categoriesAt != null) categoriesAtMs = categoriesAt;
        List<Models.SsePriceItemDto> board = cache.get("board", listOf(Models.SsePriceItemDto.class));
        if (board != null) {
            state.board = board;
            state.boardUpdatedAt = latestUpdate(board);
            Long savedAt = cache.get("board_at", Long.class);
            if (savedAt != null) {
                lastPriceAtElapsed = SystemClock.elapsedRealtime() - Math.max(0, System.currentTimeMillis() - savedAt);
            }
        }
        Models.ProductPageDto page = cache.get("products_ALL", Models.ProductPageDto.class);
        if (page != null && page.content != null) {
            state.products = page.content;
            state.productsHasMore = !page.last;
        }
        Models.BootstrapConfigDto b = cache.get("bootstrap", Models.BootstrapConfigDto.class);
        Long bAt = cache.get("bootstrap_at", Long.class);
        if (b != null) {
            bootstrap = b;
            bootstrapAtMs = bAt != null ? bAt : 0L;
            applyBootstrapToState(state, b);
        }
    }

    private boolean hasCachedData() {
        return !state.board.isEmpty() || !state.products.isEmpty();
    }

    // ==================== KICH HOAT ====================

    public void activate(String deviceCode, String activationCode) {
        if (deviceCode == null || deviceCode.trim().isEmpty() || activationCode == null || activationCode.trim().isEmpty()) {
            updateState(s -> s.activationError = "Vui lòng nhập đủ Mã máy và Mã kích hoạt.");
            return;
        }
        updateState(s -> {
            s.isActivating = true;
            s.activationError = null;
        });
        authService.enroll(deviceCode, activationCode.toUpperCase(), new DeviceAuthService.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Dong ho may lech > 5 phut so voi server (da bu lech nhung ky thuat vien nen sua): canh bao.
                long skewMs = authService.getLastEnrollSkewMs();
                final String warn = Math.abs(skewMs) > 5 * 60_000L
                        ? "Cảnh báo: đồng hồ máy lệch " + (Math.abs(skewMs) / 60_000L)
                        + " phút so với máy chủ. Hãy chỉnh lại giờ hệ thống và bật đồng bộ giờ tự động (NTP)."
                        : null;
                updateState(s -> {
                    s.isActivating = false;
                    s.deviceCode = config.getDeviceCode();
                    s.status = Status.NONE;
                    if (warn != null) s.notice = warn;
                });
                startSession();
            }

            @Override
            public void onError(Exception error) {
                String message;
                if (error instanceof Models.EnrollmentFailedException) {
                    message = describeEnrollError(((Models.EnrollmentFailedException) error).errorCode, error.getMessage());
                } else if (error instanceof Models.ClockSkewException) {
                    message = describeEnrollError("102", null);
                } else if (error instanceof Models.KeyUnavailableException) {
                    message = error.getMessage();
                } else {
                    message = "Lỗi kết nối máy chủ: " + error.getMessage();
                }
                final String shown = message;
                updateState(s -> {
                    s.isActivating = false;
                    s.activationError = shown;
                });
            }
        });
    }

    /** Dien giai ma loi kich hoat (spec II.2.14/II.4). */
    private static String describeEnrollError(String errorCode, String fallbackMessage) {
        if (errorCode != null) {
            switch (errorCode) {
                case "101":
                    return "Chữ ký không hợp lệ — thử lại (có thể do đồng hồ máy sai giờ).";
                case "102":
                    return "Đồng hồ máy lệch quá 5 phút so với máy chủ — chỉnh lại giờ hệ thống, bật đồng bộ giờ tự động (NTP) rồi thử lại.";
                case "103":
                    return "Yêu cầu bị trùng (nonce đã dùng) — thử lại.";
                case "104":
                    return "Không tìm thấy khoá của máy trên máy chủ.";
                case "111":
                    return "Mã kích hoạt không đúng.";
                case "112":
                    return "Mã kích hoạt đã hết hạn — yêu cầu quản trị viên cấp mã mới.";
                case "113":
                    return "Mã kích hoạt không còn hiệu lực — yêu cầu quản trị viên cấp mã mới.";
                case "121":
                    return "Máy đã bị thu hồi — liên hệ quản trị viên để cấp mã kích hoạt mới.";
                case "122":
                case "124":
                    return "Máy không ở trạng thái chờ kích hoạt — liên hệ quản trị viên.";
                case "123":
                    return "Khoá gửi lên khác khoá đã đăng ký (nghi mã bị dùng trộm) — liên hệ quản trị viên.";
                default:
                    break;
            }
        }
        if (fallbackMessage != null && fallbackMessage.toLowerCase().contains("hết hạn")) {
            return "Mã kích hoạt đã hết hạn — yêu cầu quản trị viên cấp mã mới.";
        }
        return "Kích hoạt thất bại (" + fallbackMessage + ").";
    }

    /** Ky thuat vien doi dia chi may chu (STAGING/DEV) ngay tai man Kich hoat. */
    public void setBaseUrl(String url) {
        config.setBaseUrl(url);
    }

    public String getBaseUrl() {
        return config.getBaseUrl();
    }

    // ==================== PHIEN / TOKEN ====================

    private void startSession() {
        sessionStarted = true;
        updateState(s -> {
            s.screen = AppScreen.HOME;
            s.isLoading = true;
            s.errorMessage = null;
        });
        worker.execute(() -> {
            try {
                onSession(authService.fetchSession());
            } catch (Exception e) {
                handleSessionError(e);
            }
        });
    }

    /** Nut "Thu lai" o man thong bao trang thai. */
    public void retryNow() {
        cancel(sessionRetryRunnable);
        cancel(loadRetryRunnable);
        if (authService.isEnrolled()) startSession();
    }

    private void onSession(Models.DeviceSessionData session) {
        accessToken = session.accessToken;
        apiService.setToken(session.accessToken);
        sessionAttempts = 0;
        rotationRequested = session.keyRotationRequired;
        Log.i(TAG, "Phien moi: keyRotationRequired=" + session.keyRotationRequired + " minAppVersion=" + session.minAppVersion);

        boolean needsUpdate = VersionUtil.isLower(config.getAppVersion(), session.minAppVersion);
        updateState(s -> {
            if (session.storeName != null) s.storeName = session.storeName;
            s.deviceCode = config.getDeviceCode();
            s.status = needsUpdate ? Status.NEEDS_UPDATE : (isBlockingStatus(s.status) ? Status.NONE : s.status);
        });

        scheduleTokenRefresh(session.expiresIn);
        priceStream.start(() -> accessToken); // mot lan cho ca phien; token lay qua supplier moi lan ket noi
        loadHomeData();
        loadAds();
        maybeRotateKey();
    }

    private static boolean isBlockingStatus(Status s) {
        return s == Status.WAITING_CONNECTION || s == Status.SERVICE_UNAVAILABLE || s == Status.CLOCK_ERROR;
    }

    /** Phan loai loi lay token (spec II.2.1 + II.4) va len lich thu lai. */
    private void handleSessionError(Exception e) {
        if (e instanceof Models.DeviceRevokedException) {
            onDeviceRevoked();
        } else if (e instanceof Models.DeviceNotEnrolledException) {
            updateState(s -> s.screen = AppScreen.SETUP);
        } else if (e instanceof Models.KeyUnavailableException) {
            updateState(s -> {
                s.screen = AppScreen.SETUP;
                s.activationError = "Máy cần kích hoạt lại, liên hệ quản trị viên. Mã máy: " + config.getDeviceCode();
                s.isLoading = false;
            });
        } else if (e instanceof Models.KeyNotRecognizedException) {
            updateState(s -> {
                s.status = Status.KEY_NOT_RECOGNIZED;
                s.isLoading = false;
            });
        } else if (e instanceof Models.ClockSkewException) {
            updateState(s -> {
                s.status = Status.CLOCK_ERROR;
                s.isLoading = false;
            });
            scheduleSessionRetry(60_000L);
        } else if (e instanceof IOException || e instanceof Models.ChannelPausedException) {
            // Mat mang: van coi may da kich hoat (KHONG bao gio ve man Kich hoat), giu du lieu cu, thu lai.
            boolean noData = !hasCachedData();
            updateState(s -> {
                s.offline = true;
                s.isLoading = false;
                if (noData) s.status = Status.WAITING_CONNECTION;
            });
            scheduleSessionRetry(networkBackoff(sessionAttempts++));
        } else {
            long wait;
            if (e instanceof Models.ApiException && ((Models.ApiException) e).httpStatus == 429) {
                wait = Math.max(5, ((Models.ApiException) e).retryAfterSeconds) * 1000L;
            } else {
                // Loi ngoai bang ma loi: "May tam ngung phuc vu", thu lai 1 -> 10 phut, bao loi qua II.2.13.
                wait = Math.min(600_000L, 60_000L * (1L << Math.min(sessionAttempts++, 4)));
                errorReporter.report(ErrorReporter.OTHER, "Lay token that bai: " + e, null);
                updateState(s -> {
                    s.status = Status.SERVICE_UNAVAILABLE;
                    s.isLoading = false;
                });
            }
            scheduleSessionRetry(wait);
        }
    }

    private void scheduleSessionRetry(long delayMs) {
        cancel(sessionRetryRunnable);
        sessionRetryRunnable = () -> worker.execute(() -> {
            try {
                onSession(authService.fetchSession());
            } catch (Exception e) {
                handleSessionError(e);
            }
        });
        mainHandler.postDelayed(sessionRetryRunnable, delayMs);
    }

    /** 3s, 6s, 12s, 24s, toi da 30s; +-50% jitter (tranh nhieu may cung ket noi lai mot luc). */
    private static long networkBackoff(int attempt) {
        long base = Math.min(30_000L, 3_000L * (1L << Math.min(attempt, 4)));
        return (long) (base * (0.5 + ThreadLocalRandom.current().nextDouble()));
    }

    /** Gia han token TRUOC khi het han 5 phut (khong co API refresh). */
    private void scheduleTokenRefresh(int expiresInSeconds) {
        cancel(tokenRefreshRunnable);
        long lifetimeMs = expiresInSeconds > 0 ? expiresInSeconds * 1000L : 3_600_000L;
        long delay = Math.max(60_000L, lifetimeMs - 5 * 60_000L);
        tokenRefreshRunnable = () -> worker.execute(() -> {
            try {
                Models.DeviceSessionData s = authService.fetchSession();
                accessToken = s.accessToken;
                apiService.setToken(s.accessToken);
                rotationRequested = rotationRequested || s.keyRotationRequired;
                updateState(st -> {
                    if (st.status == Status.NEEDS_UPDATE || VersionUtil.isLower(config.getAppVersion(), s.minAppVersion)) {
                        st.status = VersionUtil.isLower(config.getAppVersion(), s.minAppVersion) ? Status.NEEDS_UPDATE : Status.NONE;
                    }
                });
                scheduleTokenRefresh(s.expiresIn);
                maybeRotateKey();
            } catch (Models.DeviceRevokedException e) {
                onDeviceRevoked();
            } catch (Exception e) {
                Log.w(TAG, "Gia han token loi: " + e);
                mainHandler.postDelayed(tokenRefreshRunnable, networkBackoff(1));
            }
        });
        mainHandler.postDelayed(tokenRefreshRunnable, delay);
    }

    // ==================== XOAY KHOA (II.2.15) ====================

    /** Activity bao man cho dang hien (may ranh, khong co phien khach) — thoi diem an toan de xoay khoa. */
    public void onIdleChanged(boolean idle) {
        idleShowing = idle;
        // Khach roi phien (man cho hien len) -> don gio hang + dang xuat, tranh khach sau nhin thay du lieu khach truoc (BR_003: tu dong ket thuc phien).
        if (idle && !state.cart.isEmpty()) clearCart();
        if (idle && state.loggedInCustomerName != null) logout();
        maybeRotateKey();
    }

    // ==================== DANG NHAP / DANG XUAT O HOME (sheet "Dang nhap Dang xuat") ====================

    /** Danh dau khach da dang nhap sau khi xac thuc xong o man Home (hien ten, doi nut "Dang nhap" -> "Dang xuat"). */
    public void login(String customerName) {
        updateState(s -> s.loggedInCustomerName = customerName == null || customerName.trim().isEmpty()
                ? "Khách hàng" : customerName.trim());
    }

    /** Nut "Dang xuat" o Home, hoac tu dong khi roi phien (xem onIdleChanged). */
    public void logout() {
        updateState(s -> s.loggedInCustomerName = null);
    }

    private void maybeRotateKey() {
        if (rotationRequested) Log.i(TAG, "maybeRotateKey: yeu cau xoay khoa, may ranh=" + idleShowing);
        if (!rotationRequested || !idleShowing || !authService.isEnrolled()) return;
        rotationRequested = false;
        authService.rotateKey(new DeviceAuthService.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.i(TAG, "Xoay khoa thanh cong");
            }

            @Override
            public void onError(Exception error) {
                Log.w(TAG, "Xoay khoa loi: " + error);
                if (error instanceof Models.DeviceRevokedException) {
                    onDeviceRevoked();
                } else if (error instanceof Models.ApiException) {
                    String code = ((Models.ApiException) error).errorCode;
                    // 131 (chua du 1 gio) / 5xx / mang: thu lai o lan token ke tiep; 124/132: dung xoay.
                    if (!"124".equals(code) && !"132".equals(code)) rotationRequested = true;
                } else {
                    rotationRequested = true;
                }
            }
        });
    }

    // ==================== TAI DU LIEU ====================

    private void loadHomeData() {
        worker.execute(() -> {
            try {
                loadBannersSync(false);
                loadCategoriesSync(false);

                loadBootstrapSync();

                Models.ProductPageDto page = apiService.products(currentCategoryId, 0, LIST_PAGE_SIZE);
                listPage = 0;
                productsAtMs = System.currentTimeMillis();
                if ("ALL".equals(currentCategoryId)) cache.put("products_ALL", page);
                updateState(s -> {
                    s.products = page.content;
                    s.productsHasMore = !page.last;
                });

                loadBoardFromRestSync();

                loadAttempts = 0;
                updateState(s -> {
                    s.isLoading = false;
                    s.offline = false;
                    s.errorMessage = null;
                    if (isBlockingStatus(s.status)) s.status = Status.NONE;
                });
            } catch (Exception e) {
                handleLoadError(e, "Lỗi tải dữ liệu");
            }
        });
    }

    /** II.2.4: banner cache 5 phut — chi goi lai khi cu hon 5 phut (hoac force tu dong ho dinh ky). */
    private void loadBannersSync(boolean force) throws Exception {
        if (!force && System.currentTimeMillis() - bannersAtMs < PERIODIC_MS) return;
        List<Models.BannerDto> banners = apiService.banners();
        bannersAtMs = System.currentTimeMillis();
        cache.put("banners", banners);
        cache.put("banners_at", bannersAtMs);
        updateState(s -> s.banners = banners);
    }

    /** II.2.5: nhom san pham cache 5 phut (nhu banner). */
    private void loadCategoriesSync(boolean force) throws Exception {
        if (!force && System.currentTimeMillis() - categoriesAtMs < PERIODIC_MS) return;
        List<Models.CategoryDto> categories = apiService.categories();
        categoriesAtMs = System.currentTimeMillis();
        cache.put("categories", categories);
        cache.put("categories_at", categoriesAtMs);
        updateState(s -> s.categories = categories);
    }

    /**
     * Bien dong gia vang theo ngay tu website BTMH (khong can token may). Loi mang/dinh dang doi -> giu du lieu cu
     * (cache), chi khi khong co gi moi bao loi; khong anh huong phan con lai cua App.
     */
    private void fetchGoldHistory(String code, boolean showLoading) {
        if (showLoading) updateState(s -> s.goldLoading = s.goldHistory == null);
        worker.execute(() -> {
            try {
                GoldHistoryClient.History h = goldClient.fetch(code, GoldHistoryClient.DEFAULT_DAYS);
                goldAtMs = System.currentTimeMillis();
                cache.put("gold_" + code, h);
                updateState(s -> {
                    if (code.equals(s.goldCode)) {
                        s.goldHistory = h;
                        s.goldLoading = false;
                        s.goldError = false;
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Tai bien dong gia vang (" + code + ") loi: " + e);
                GoldHistoryClient.History old = cache.get("gold_" + code, GoldHistoryClient.History.class);
                updateState(s -> {
                    if (!code.equals(s.goldCode)) return;
                    if (old != null && (s.goldHistory == null || !code.equals(s.goldHistory.code))) s.goldHistory = old;
                    s.goldLoading = false;
                    s.goldError = s.goldHistory == null || !code.equals(s.goldHistory.code);
                });
            }
        });
    }

    /** Khach chon san pham khac o the bien dong gia: hien ngay ban cache (neu co) roi tai moi. */
    public void selectGoldProduct(String code) {
        goldCode = code;
        GoldHistoryClient.History cached = cache.get("gold_" + code, GoldHistoryClient.History.class);
        updateState(s -> {
            s.goldCode = code;
            s.goldHistory = cached;
            s.goldLoading = cached == null;
            s.goldError = false;
        });
        fetchGoldHistory(code, false);
    }

    /** Dinh ky 5 phut: lam moi banner + nhom; loi mang chi ghi log (van hien ban cache). */
    private void refreshBannersAndCategories(boolean force) {
        try {
            loadBannersSync(force);
            loadCategoriesSync(force);
        } catch (Models.DeviceRevokedException e) {
            verifyRevocation();
        } catch (Exception e) {
            Log.w(TAG, "Lam moi banner/nhom dinh ky loi: " + e);
        }
    }

    /** Bang gia tam thoi tu II.2.6 (categoryId=ALL, 100/trang) — SSE snapshot se thay the khi ve. */
    private void loadBoardFromRestSync() throws Exception {
        List<Models.SsePriceItemDto> rows = new ArrayList<>();
        int order = 1;
        for (int page = 0; page < BOARD_MAX_PAGES; page++) {
            Models.ProductPageDto p = apiService.products("ALL", page, BOARD_PAGE_SIZE);
            for (Models.ProductDto product : p.content) rows.add(Models.SsePriceItemDto.fromProduct(product, order++));
            if (p.last || p.content.isEmpty()) break;
        }
        final List<Models.SsePriceItemDto> finalRows = rows;
        cache.put("board", finalRows);
        cache.put("board_at", System.currentTimeMillis());
        updateState(s -> {
            // Neu SSE dang song va da co snapshot thi khong ghi de bang du lieu REST cu hon.
            if (s.sseConnected && priceVersion >= 0) return;
            s.board = finalRows;
            s.boardUpdatedAt = latestUpdate(finalRows);
            lastPriceAtElapsed = SystemClock.elapsedRealtime();
        });
    }

    /** II.2.12 kem If-None-Match; 304 -> giu ban cu. */
    private void loadBootstrapSync() throws Exception {
        KioskApiService.Result<Models.BootstrapConfigDto> r = apiService.bootstrap(bootstrapEtag);
        if (r.notModified) {
            bootstrapAtMs = System.currentTimeMillis();
            cache.put("bootstrap_at", bootstrapAtMs);
            updateState(s -> s.sellingOpen = computeSellingOpen());
            return;
        }
        if (r.data == null) return;
        bootstrapEtag = r.etag;
        bootstrap = r.data;
        bootstrapAtMs = System.currentTimeMillis();
        cache.put("bootstrap", r.data);
        cache.put("bootstrap_at", bootstrapAtMs);
        KioskClock.sync(r.data.serverTime);
        final Models.BootstrapConfigDto b = r.data;
        updateState(s -> {
            applyBootstrapToState(s, b);
            if (VersionUtil.isLower(config.getAppVersion(), b.minAppVersion)) s.status = Status.NEEDS_UPDATE;
            else if (s.status == Status.NEEDS_UPDATE) s.status = Status.NONE;
        });
    }

    private void applyBootstrapToState(UiState s, Models.BootstrapConfigDto b) {
        s.hotline = b.hotline;
        s.openTime = b.openTime;
        s.closeTime = b.closeTime;
        if (b.storeName != null) s.storeName = b.storeName;
        if (b.idleTimeoutSeconds > 0) s.idleTimeoutSeconds = b.idleTimeoutSeconds;
        if (b.stalePriceSeconds > 0) stalePriceSeconds = b.stalePriceSeconds;
        s.sellingOpen = computeSellingOpen();
    }

    /** Trong khung gio mo ban (theo gio server); du lieu cu qua 24 gio hoac khong co gio ban -> ngoai gio ban. */
    private boolean computeSellingOpen() {
        Models.BootstrapConfigDto b = bootstrap;
        if (b == null) return false;
        if (System.currentTimeMillis() - bootstrapAtMs > BOOTSTRAP_MAX_AGE_MS) return false;
        return KioskClock.isWithinHours(b.openTime, b.closeTime);
    }

    private void handleLoadError(Exception e, String defaultMsg) {
        if (e instanceof Models.DeviceRevokedException) {
            verifyRevocation();
        } else if (e instanceof IOException || e instanceof Models.ChannelPausedException) {
            boolean noData = !hasCachedData();
            updateState(s -> {
                s.offline = true;
                s.isLoading = false;
                if (noData) s.status = Status.WAITING_CONNECTION;
            });
            scheduleLoadRetry(networkBackoff(loadAttempts++));
        } else if (e instanceof Models.ApiException && ((Models.ApiException) e).httpStatus == 429) {
            scheduleLoadRetry(Math.max(5, ((Models.ApiException) e).retryAfterSeconds) * 1000L);
        } else if (e instanceof Models.ApiException && isClientError(((Models.ApiException) e).httpStatus)) {
            // II.4: 400/404/405 la loi yeu cau/ma nguon — thu lai vo ich, KHONG backoff; chi bao loi chung.
            Log.w(TAG, defaultMsg + " (khong thu lai): " + e.getMessage());
            updateState(s -> {
                s.isLoading = false;
                s.productsLoadingMore = false;
                s.errorMessage = defaultMsg;
            });
        } else {
            // Khong bao gio dua "message" cua server ra cho khach (II.4) — chi cau chung.
            updateState(s -> {
                s.isLoading = false;
                s.errorMessage = defaultMsg;
            });
            scheduleLoadRetry(networkBackoff(loadAttempts++));
        }
    }

    private static boolean isClientError(int httpStatus) {
        return httpStatus == 400 || httpStatus == 404 || httpStatus == 405;
    }

    private void scheduleLoadRetry(long delayMs) {
        cancel(loadRetryRunnable);
        loadRetryRunnable = this::loadHomeData;
        mainHandler.postDelayed(loadRetryRunnable, delayMs);
    }

    // ==================== QUANG CAO MAN CHO (II.2.2) ====================

    private void loadAds() {
        worker.execute(() -> {
            try {
                KioskApiService.Result<List<Models.AdDto>> r = apiService.adsPlaylist(adsEtag);
                if (r.notModified || r.data == null) return;
                // Chi nho ETag khi TAT CA muc da tai xong; con muc loi -> bo ETag de luot lay ke tiep khong ra 304
                // ma tai lai muc loi (II.2.2).
                final String etag = r.etag;
                adsEtag = null;
                adsManager.sync(r.data, (ads, complete) -> {
                    if (complete) adsEtag = etag;
                    updateState(s -> s.ads = ads);
                });
            } catch (Models.DeviceRevokedException e) {
                verifyRevocation();
            } catch (Exception e) {
                Log.w(TAG, "Tai playlist quang cao loi: " + e); // mat mang van phat cac muc da tai
            }
        });
    }

    /** Khach cham vao quang cao co hanh dong: PRODUCT / CATEGORY / WEBSITE (NONE: chi thoat man cho). */
    public void handleAdAction(Models.AdDto ad) {
        String type = ad.actionType == null ? "NONE" : ad.actionType.toUpperCase();
        switch (type) {
            case "PRODUCT":
                if (ad.productId != null) openProductById(ad.productId);
                break;
            case "CATEGORY":
                openCategory(ad.categoryId);
                break;
            case "WEBSITE":
                if (ad.websiteUrl != null && ad.websiteUrl.startsWith("https://")) {
                    final String url = ad.websiteUrl;
                    updateState(s -> s.websiteUrl = url);
                }
                break;
            default:
                break;
        }
    }

    /** Loi phat video quang cao (spec II.2.13 type MEDIA_PLAYBACK). */
    public void reportPlaybackError(Models.AdDto ad, String message) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("adId", ad.id);
        ctx.put("url", ad.url);
        errorReporter.report(ErrorReporter.MEDIA_PLAYBACK, message, ctx);
    }

    public void closeWebsite() {
        updateState(s -> s.websiteUrl = null);
    }

    // ==================== DANH SACH SAN PHAM / TIM KIEM ====================

    public void selectCategory(String categoryId) {
        currentCategoryId = categoryId;
        currentQuery = "";
        searchToken++;
        listPage = 0;
        updateState(s -> {
            s.selectedCategoryId = categoryId;
            s.searchQuery = "";
            s.isLoading = true;
        });
        loadListPage(0, true);
    }

    /** Mo tab nhom san pham (tu quang cao CATEGORY); nhom khong con -> tab "Tat ca" + man danh sach. */
    public void openCategory(String categoryId) {
        boolean exists = false;
        if (categoryId != null) {
            for (Models.CategoryDto c : state.categories) if (c.id.equals(categoryId)) exists = true;
        }
        selectCategory(exists ? categoryId : "ALL");
        updateState(s -> s.screen = AppScreen.PRODUCTS);
        refreshOrderLimit(); // xem ghi chu o openProducts() - cung mot lo hong, cung mot cho sua.
    }

    /** Tim kiem (II.2.7): App goi khi du 2 ky tu va sau 300ms debounce; ket qua cu bi bo neu khach go tiep. */
    public void search(String query) {
        final String q = query == null ? "" : query.trim();
        if (q.length() < 2) {
            if (!currentQuery.isEmpty()) {
                currentQuery = "";
                searchToken++;
                updateState(s -> s.searchQuery = "");
                listPage = 0;
                loadListPage(0, true);
            }
            return;
        }
        currentQuery = q;
        final long token = ++searchToken;
        listPage = 0;
        updateState(s -> {
            s.searchQuery = q;
            s.isLoading = true;
        });
        worker.execute(() -> {
            try {
                Models.SearchProductRequest req = new Models.SearchProductRequest();
                req.name = q;
                req.categoryId = "ALL".equals(currentCategoryId) ? null : currentCategoryId;
                req.pageNumber = 0;
                req.pageSize = LIST_PAGE_SIZE;
                Models.ProductPageDto page = apiService.search(req);
                if (token != searchToken) return; // khach da go tiep -> bo ket qua cu
                updateState(s -> {
                    s.products = page.content;
                    s.productsHasMore = !page.last;
                    s.isLoading = false;
                });
            } catch (Exception e) {
                if (token == searchToken) handleLoadError(e, "Lỗi tìm kiếm");
            }
        });
    }

    /** Cuon toi cuoi danh sach: tai trang ke (theo che do danh muc hoac tim kiem). */
    public void loadMoreProducts() {
        if (!state.productsHasMore || state.productsLoadingMore || state.isLoading) return;
        updateState(s -> s.productsLoadingMore = true);
        loadListPage(listPage + 1, false);
    }

    private void loadListPage(int page, boolean reset) {
        final long token = searchToken;
        final String category = currentCategoryId;
        final String query = currentQuery;
        worker.execute(() -> {
            try {
                Models.ProductPageDto result;
                if (query.length() >= 2) {
                    Models.SearchProductRequest req = new Models.SearchProductRequest();
                    req.name = query;
                    req.categoryId = "ALL".equals(category) ? null : category;
                    req.pageNumber = page;
                    req.pageSize = LIST_PAGE_SIZE;
                    result = apiService.search(req);
                } else {
                    result = apiService.products(category, page, LIST_PAGE_SIZE);
                }
                if (token != searchToken) return;
                listPage = page;
                if (reset) productsAtMs = System.currentTimeMillis();
                final Models.ProductPageDto r = result;
                if (reset && "ALL".equals(category) && query.isEmpty()) cache.put("products_ALL", r);
                updateState(s -> {
                    if (reset) {
                        s.products = r.content;
                    } else {
                        List<Models.ProductDto> merged = new ArrayList<>(s.products);
                        merged.addAll(r.content);
                        s.products = merged;
                    }
                    s.productsHasMore = !r.last;
                    s.productsLoadingMore = false;
                    s.isLoading = false;
                    s.errorMessage = null;
                });
            } catch (Exception e) {
                updateState(s -> s.productsLoadingMore = false);
                if (token == searchToken) handleLoadError(e, "Lỗi tải sản phẩm");
            }
        });
    }

    // ==================== DIEU HUONG MAN HINH ====================

    /** Nut "San pham tich luy" o Home -> danh sach san pham. */
    public void openProducts() {
        updateState(s -> s.screen = AppScreen.PRODUCTS);
        // II.2.6: danh sach da tai cu hon 5 phut -> goi lai khi mo man (giu danh sach cu tren man cho toi khi co moi).
        if (accessToken != null && System.currentTimeMillis() - productsAtMs > PERIODIC_MS) reloadProductList();
        // BUG THAT da tim thay 15/10/2026: han muc 10 chi truoc day CHI duoc tai khi mo man Chi tiet
        // (openProductById -> refreshOrderLimit()) - neu khach vao thang man Danh sach san pham va bam (+)
        // tren luoi ma CHUA tung mo Chi tiet lan nao, limitQuantity con null -> ProductAdapter.computeMaxQuantity()
        // roi ve "khong gioi han theo han muc, chi gioi han boi ton kho" -> stepper tren luoi cho cong vuot han
        // muc that su (vd 20 cai). Goi luon o day de co du lieu han muc tu luc nay.
        refreshOrderLimit();
    }

    /** Tai lai trang dau theo nhom/tu khoa hien tai (khong bat co "dang tai" de danh sach cu khong bi nhay mat). */
    private void reloadProductList() {
        listPage = 0;
        loadListPage(0, true);
    }

    public void closeProducts() {
        updateState(s -> s.screen = AppScreen.HOME);
    }

    public void openProduct(Models.ProductDto product) {
        openProductById(product.id);
    }

    /**
     * Mo man chi tiet: goi II.2.8 lay ban DAY DU (co description) roi moi chuyen man. San pham khong con
     * ton tai/da tat cho kenh (404, code 202) -> ve man danh sach o tab "Tat ca".
     */
    public void openProductById(String productId) {
        worker.execute(() -> {
            try {
                Models.ProductDto detail = apiService.productDetail(productId);
                if (detail == null) throw new Models.ApiException(404, "202", "Khong co du lieu", 0, 0);
                updateState(s -> {
                    s.selectedProduct = detail;
                    s.screen = AppScreen.DETAIL;
                });
                refreshOrderLimit();
            } catch (Models.ApiException e) {
                if (e.httpStatus == 404 || "202".equals(e.errorCode)) {
                    selectCategory("ALL");
                    updateState(s -> s.screen = AppScreen.PRODUCTS);
                } else {
                    Log.w(TAG, "Mo chi tiet loi: " + e.getMessage());
                }
            } catch (Models.DeviceRevokedException e) {
                verifyRevocation();
            } catch (Exception e) {
                Log.w(TAG, "Mo chi tiet loi: " + e.getMessage());
            }
        });
    }

    /**
     * Lam moi man chi tiet DANG MO (ten/anh/mo ta vua doi tren web admin) ma khong doi man. San pham da bi tat
     * cho kenh (404/202) -> ve danh sach tab "Tat ca", giong openProductById.
     */
    private void refreshOpenDetail(String productId) {
        worker.execute(() -> {
            try {
                Models.ProductDto detail = apiService.productDetail(productId);
                if (detail == null) return;
                updateState(s -> {
                    if (s.screen == AppScreen.DETAIL && s.selectedProduct != null && productId.equals(s.selectedProduct.id)) {
                        s.selectedProduct = detail;
                    }
                });
            } catch (Models.ApiException e) {
                if (e.httpStatus == 404 || "202".equals(e.errorCode)) {
                    selectCategory("ALL");
                    updateState(s -> {
                        if (s.screen == AppScreen.DETAIL) {
                            s.selectedProduct = null;
                            s.screen = AppScreen.PRODUCTS;
                        }
                    });
                }
            } catch (Exception e) {
                Log.w(TAG, "Lam moi chi tiet loi: " + e.getMessage());
            }
        });
    }

    public void closeProductDetail() {
        updateState(s -> {
            s.selectedProduct = null;
            s.screen = AppScreen.PRODUCTS;
        });
    }

    /** Ve man Home tu bat ky man nao (khi thoat man cho quang cao) — giong Form1.ExitIdleAd -> ShowHome. */
    public void goHome() {
        updateState(s -> {
            s.selectedProduct = null;
            s.websiteUrl = null;
            if (s.screen != AppScreen.SETUP) s.screen = AppScreen.HOME;
        });
    }

    // ==================== XAC NHAN DON HANG (II.2.9 - II.2.11) ====================

    /**
     * Han muc giao dich cho man Chi tiet (ghi chu "toi da N san pham" + gioi han stepper). Chay nen, loi thi bo qua
     * (giu gia tri da co); han muc <= 0 = khong gioi han (null) — cung quy tac voi openOrderConfirm().
     */
    private void refreshOrderLimit() {
        worker.execute(() -> {
            try {
                Double limit = apiService.limit().limitQuantity;
                final Double finalLimit = (limit != null && limit > 0) ? limit : null;
                updateState(s -> s.limitQuantity = finalLimit);
            } catch (Models.DeviceRevokedException e) {
                verifyRevocation();
            } catch (Exception e) {
                Log.w(TAG, "Khong lay duoc han muc: " + e.getMessage());
            }
        });
    }

    /**
     * "DAT MUA" o man Chi tiet (sau khi da them san pham + so luong vao gio hang qua {@link #addToCart}) hoac "Tiep tuc"
     * tu man Thong tin don hang: mo man xac nhan voi TOAN BO gio hang hien tai (co the nhieu san pham — spec "Danh sach
     * san pham"/"Thong tin don hang" trong Kiosk.xlsx) va tai tinh/thanh, cua hang, han muc.
     */
    /**
     * CHI DUNG DE PHAT TRIEN GIAO DIEN: nhay thang tu man Kich hoat vao man Thong tin don hang voi
     * gio hang + tinh/cua hang gia, khong goi API, khong can kich hoat/them san pham truoc. Goi tu
     * {@link SetupView.Callbacks#onDebugPreviewOrderConfirm()} (chi hien o ban DEBUG). Sua xong
     * OrderConfirmView thi build lai + mo app la vao thang man nay de xem, khong phai bam qua cac man khac.
     */
    public void debugPreviewOrderConfirm() {
        updateState(s -> {
            Models.ProductDto p1 = new Models.ProductDto();
            p1.id = "debug-1";
            p1.name = "Dây chuyền vàng 10K PYD2V10KDH-511010-015 (demo)";
            p1.code = "PYD2V10KDH-511010-015";
            p1.price = 5_732_000;
            p1.quantity = 1;
            p1.remainingQuantity = 99;

            Models.ProductDto p2 = new Models.ProductDto();
            p2.id = "debug-2";
            p2.name = "Charm bạc BACHB92KD0-508005-1276 (demo)";
            p2.code = "BACHB92KD0-508005-1276";
            p2.price = 604_000;
            p2.quantity = 1;
            p2.remainingQuantity = 99;

            s.cart.clear();
            s.cart.add(new Models.CartItem(p1, 1));
            s.cart.add(new Models.CartItem(p2, 2));

            Models.ProvinceDto hn = new Models.ProvinceDto();
            hn.id = 1;
            hn.code = "HN";
            hn.name = "Hà Nội";
            hn.storeCount = 1;
            s.provinces = new ArrayList<>();
            s.provinces.add(hn);
            s.selectedProvinceId = 1L;
            s.stores = new ArrayList<>();
            s.selectedStoreId = null;
            s.limitQuantity = null;

            s.screen = AppScreen.ORDER;
            s.orderLoading = false;
            s.orderError = null;
        });
    }

    public void openOrderConfirm() {
        // BUG THAT da tim thay 15/10/2026: "stores=[]/selectedStoreId=null" truoc day nam trong KHOI BAT DONG BO
        // ben duoi (chay xong sau khi goi 2-3 API: provinces, limit, stores-theo-tinh) - neu khach nhanh tay
        // bam "Chon cua hang" va chon XONG truoc khi khoi do chay xong, luc no chay xong se GHI DE
        // selectedStoreId VE NULL, xoa mat lua chon vua roi cua khach (khach thay nhu "khong chon duoc cua
        // hang nao ca"). Chuyen 2 dong nay len khoi DONG BO (chay truoc ca man hinh kip hien ra) de khach
        // khong bao gio co co hoi chon truoc khi reset xong - khoi bat dong bo o duoi KHONG duoc dong vao
        // selectedStoreId nua.
        updateState(s -> {
            if (s.cart.isEmpty()) {
                s.notice = "Giỏ hàng trống";
                return;
            }
            orderReturnScreen = s.screen == AppScreen.PRODUCTS ? AppScreen.PRODUCTS : AppScreen.DETAIL;
            s.screen = AppScreen.ORDER;
            s.orderLoading = true;
            s.orderError = null;
            s.stores = new ArrayList<>();
            s.selectedStoreId = null;
        });
        worker.execute(() -> {
            try {
                List<Models.ProvinceDto> provinces = apiService.provinces();
                Long defaultProvince = null;
                // Phase 1 chua co dang nhap: luon chon mac dinh tinh co code "HN".
                for (Models.ProvinceDto p : provinces) if ("HN".equals(p.code)) defaultProvince = p.id;
                if (defaultProvince == null && !provinces.isEmpty()) defaultProvince = provinces.get(0).id;

                Double limit = null;
                try {
                    limit = apiService.limit().limitQuantity;
                } catch (Models.DeviceRevokedException e) {
                    throw e;
                } catch (Exception e) {
                    Log.w(TAG, "Khong lay duoc han muc: " + e.getMessage());
                }
                final Long provinceId = defaultProvince;
                // Spec II.2.11: khong co han muc -> null. Backend hien tra 0 (khong bao gio la han muc that) nen coi la khong gioi han.
                final Double finalLimit = (limit != null && limit > 0) ? limit : null;
                updateState(s -> {
                    s.provinces = provinces;
                    s.selectedProvinceId = provinceId;
                    s.limitQuantity = finalLimit;
                });
                if (provinceId != null) loadStoresSync(provinceId);
                updateState(s -> s.orderLoading = false);
            } catch (Models.DeviceRevokedException e) {
                verifyRevocation();
            } catch (Exception e) {
                updateState(s -> {
                    s.orderLoading = false;
                    s.orderError = "Không tải được thông tin nhận hàng. Vui lòng thử lại.";
                });
            }
        });
    }

    public void selectProvince(long provinceId) {
        updateState(s -> {
            s.selectedProvinceId = provinceId;
            s.stores = new ArrayList<>();
            s.selectedStoreId = null;
            s.orderLoading = true;
            s.orderError = null;
        });
        worker.execute(() -> {
            try {
                loadStoresSync(provinceId);
                updateState(s -> s.orderLoading = false);
            } catch (Exception e) {
                updateState(s -> {
                    s.orderLoading = false;
                    s.orderError = "Không tải được danh sách cửa hàng.";
                });
            }
        });
    }

    private void loadStoresSync(long provinceId) throws Exception {
        List<Models.StoreDto> stores = apiService.storesByProvince(provinceId);
        updateState(s -> {
            if (s.selectedProvinceId != null && s.selectedProvinceId == provinceId) s.stores = stores;
        });
    }

    public void selectStore(long storeId) {
        updateState(s -> s.selectedStoreId = storeId);
    }

    public void closeOrderConfirm() {
        updateState(s -> s.screen = orderReturnScreen);
    }

    /** Icon gio hang tren thanh dau (San pham/Chi tiet): mo thang man Xac nhan don hang voi gio hang hien tai, khong qua xac thuc. */
    public void viewCart() {
        openOrderConfirm();
    }

    // ==================== SSE: BANG GIA REALTIME ====================

    private class StreamListener implements KioskPriceStreamClient.Listener {
        @Override
        public void onConnectionChanged(boolean connected) {
            updateState(s -> s.sseConnected = connected);
        }

        @Override
        public void onSnapshot(Models.SsePriceSnapshotDto snapshot) {
            if (snapshot == null || snapshot.items == null) return;
            // snapshot LUON duoc ap dung va dat lai version (ke ca khi nho hon — vd server khoi dong lai).
            updateState(s -> {
                priceVersion = snapshot.version;
                lastPriceAtElapsed = SystemClock.elapsedRealtime();
                List<Models.SsePriceItemDto> rows = new ArrayList<>(snapshot.items);
                sortByOrder(rows);
                // Tap san pham doi (them/tat san pham) so voi bang cu hoac danh sach dang tai -> tai lai danh sach (II.3.1).
                // Server cung gui snapshot khi ten/anh/mo ta doi (sua tren web admin) - SSE khong mang anh/mo ta nen
                // danh sach dang hien phai tai lai qua II.2.6 thi moi thay; chi tai khi da co danh sach tren man.
                boolean setChanged = productSetChanged(s.board, s.products, rows);
                boolean listShown = !s.products.isEmpty();
                s.board = rows;
                s.boardUpdatedAt = latestUpdate(rows);
                applyItemsToProducts(s, rows);
                cache.put("board", rows);
                cache.put("board_at", System.currentTimeMillis());
                if ((setChanged || listShown) && accessToken != null) reloadProductList();
                if (s.screen == AppScreen.DETAIL && s.selectedProduct != null && accessToken != null) {
                    refreshOpenDetail(s.selectedProduct.id);
                }
            });
        }

        @Override
        public void onPrice(Models.SsePriceSnapshotDto update) {
            if (update == null || update.items == null) return;
            updateState(s -> {
                if (update.version <= priceVersion) return; // cu hon hoac bang version dang co -> bo qua
                priceVersion = update.version;
                lastPriceAtElapsed = SystemClock.elapsedRealtime();
                List<Models.SsePriceItemDto> rows = new ArrayList<>(s.board);
                for (Models.SsePriceItemDto item : update.items) {
                    boolean replaced = false;
                    for (int i = 0; i < rows.size(); i++) {
                        if (rows.get(i).productId != null && rows.get(i).productId.equals(item.productId)) {
                            rows.set(i, item);
                            replaced = true;
                            break;
                        }
                    }
                    if (!replaced) rows.add(item);
                }
                sortByOrder(rows);
                s.board = rows;
                s.boardUpdatedAt = latestUpdate(rows);
                applyItemsToProducts(s, update.items);
            });
        }

        @Override
        public void onConfigChanged() {
            // Server ban config-changed ca khi banner/nhom san pham doi (sua tren web admin) -> tai lai ngay,
            // khong doi chu ky 5 phut.
            worker.execute(() -> {
                try {
                    loadBootstrapSync();
                } catch (Exception e) {
                    Log.w(TAG, "Tai lai cau hinh loi: " + e);
                }
                if (accessToken != null) refreshBannersAndCategories(true);
            });
        }

        @Override
        public void onPlaylistChanged() {
            loadAds();
        }

        @Override
        public void onDeviceRevoked() {
            verifyRevocation();
        }

        @Override
        public void onReplaced() {
            // Spec II.3.3: KHONG tu ket noi lai; ghi log + bao loi de van hanh biet (thuong do app chay 2 tien trinh).
            errorReporter.report(ErrorReporter.SSE_CONNECT, "SSE bi thay the boi ket noi khac cua cung ma may", null);
            updateState(s -> s.notice = "Lỗi kỹ thuật: kết nối cập nhật giá bị ngắt. Vui lòng liên hệ quản trị viên.");
        }

        @Override
        public void onUnauthorized() {
            worker.execute(() -> {
                try {
                    Models.DeviceSessionData s = authService.fetchSession();
                    accessToken = s.accessToken;
                    apiService.setToken(s.accessToken);
                } catch (Models.DeviceRevokedException e) {
                    onDeviceRevoked();
                } catch (Exception ignored) {
                }
            });
        }

        @Override
        public void onConnectFailure(int consecutive, String message) {
            if (consecutive == 3 || (consecutive > 3 && consecutive % 10 == 0)) {
                Map<String, Object> ctx = new LinkedHashMap<>();
                ctx.put("consecutive", consecutive);
                errorReporter.report(ErrorReporter.SSE_CONNECT, "Mo SSE that bai lien tiep: " + message, ctx);
            }
        }
    }

    /** Dong ho 5 giay: mat SSE > 30s -> goi II.2.6 moi 60s + an mui ten; gia cu qua nguong -> an gia. */
    private void tick() {
        long now = SystemClock.elapsedRealtime();
        boolean connected = state.sseConnected;
        boolean trendsHidden;
        if (connected) {
            sseLostAtElapsed = 0L;
            nextFallbackPollElapsed = 0L;
            trendsHidden = false;
        } else {
            if (sseLostAtElapsed == 0L) sseLostAtElapsed = now;
            trendsHidden = now - sseLostAtElapsed > SSE_LOST_MS;
            if (trendsHidden && accessToken != null && !fallbackPolling && now >= nextFallbackPollElapsed) {
                nextFallbackPollElapsed = now + (long) (FALLBACK_POLL_MS * (0.9 + 0.2 * ThreadLocalRandom.current().nextDouble()));
                fallbackPolling = true;
                worker.execute(() -> {
                    try {
                        loadBoardFromRestSync();
                        updateState(s -> {
                            s.offline = false;
                            if (isBlockingStatus(s.status)) s.status = Status.NONE;
                        });
                    } catch (Models.DeviceRevokedException e) {
                        verifyRevocation();
                    } catch (Exception e) {
                        // Truoc day chi log, KHONG set s.offline=true: SSE da mat + REST du phong nay cung
                        // loi (server that su down) ma dai "MAT KET NOI" van khong hien - khach/van hanh
                        // tuong nham may van on. Dong bo voi handleSessionError/handleLoadError (cung IOException).
                        Log.w(TAG, "Poll gia du phong loi: " + e);
                        updateState(s -> s.offline = true);
                    } finally {
                        fallbackPolling = false;
                    }
                });
            }
        }
        boolean stale = !connected && (now - lastPriceAtElapsed) > stalePriceSeconds * 1000L;
        boolean selling = computeSellingOpen();
        if (trendsHidden != state.trendsHidden || stale != state.pricesHidden || selling != state.sellingOpen) {
            final boolean th = trendsHidden;
            updateState(s -> {
                s.trendsHidden = th;
                s.pricesHidden = stale;
                s.sellingOpen = selling;
            });
        }
    }

    private static void sortByOrder(List<Models.SsePriceItemDto> rows) {
        rows.sort(Comparator.comparingInt(r -> r.order != null ? r.order : Integer.MAX_VALUE));
    }

    /**
     * Snapshot co tap productId khac bang gia dang co (co san pham moi) HOAC thieu san pham dang hien trong danh sach
     * (da bi tat)? Bang gia rong (chua tai) thi khong tinh — tranh tai lai thua o snapshot dau tien.
     */
    private static boolean productSetChanged(List<Models.SsePriceItemDto> oldBoard, List<Models.ProductDto> listed,
                                             List<Models.SsePriceItemDto> fresh) {
        java.util.Set<String> freshIds = new java.util.HashSet<>();
        for (Models.SsePriceItemDto i : fresh) if (i.productId != null) freshIds.add(i.productId);
        if (!oldBoard.isEmpty()) {
            java.util.Set<String> oldIds = new java.util.HashSet<>();
            for (Models.SsePriceItemDto i : oldBoard) if (i.productId != null) oldIds.add(i.productId);
            if (!oldIds.equals(freshIds)) return true;
        }
        for (Models.ProductDto p : listed) if (p.id != null && !freshIds.contains(p.id)) return true;
        return false;
    }

    /** priceUpdatedAt moi nhat (cung offset +07:00 nen so sanh chuoi dung thu tu thoi gian). */
    private static String latestUpdate(List<Models.SsePriceItemDto> rows) {
        String latest = null;
        for (Models.SsePriceItemDto r : rows) {
            if (r.priceUpdatedAt != null && (latest == null || r.priceUpdatedAt.compareTo(latest) > 0)) {
                latest = r.priceUpdatedAt;
            }
        }
        return latest;
    }

    /** Dong bo gia/ton moi vao danh sach san pham va san pham dang xem — khong can goi lai REST. */
    private static void applyItemsToProducts(UiState s, List<Models.SsePriceItemDto> items) {
        Map<String, Models.SsePriceItemDto> byId = new HashMap<>();
        for (Models.SsePriceItemDto item : items) {
            if (item.productId != null) byId.put(item.productId, item);
        }
        List<Models.ProductDto> updated = new ArrayList<>();
        for (Models.ProductDto p : s.products) updated.add(withItem(p, byId.get(p.id)));
        s.products = updated;
        if (s.selectedProduct != null) s.selectedProduct = withItem(s.selectedProduct, byId.get(s.selectedProduct.id));
    }

    private static Models.ProductDto withItem(Models.ProductDto p, Models.SsePriceItemDto item) {
        if (item == null) return p;
        Models.ProductDto copy = p.copyWithPrice(item.price, item.buyPrice);
        if (item.priceUpdatedAt != null) copy.priceUpdatedAt = item.priceUpdatedAt;
        if (item.remainingQuantity != null) copy.remainingQuantity = item.remainingQuantity;
        return copy;
    }

    // ==================== THU HOI MAY ====================

    /**
     * Spec II.3.3 / II.4: nhan device-revoked (SSE) hoac 403 code 121 (REST) thi goi II.2.1 de XAC NHAN;
     * chi khi II.2.1 cung tra 121 moi xoa danh tinh tren may.
     */
    private void verifyRevocation() {
        worker.execute(() -> {
            try {
                Models.DeviceSessionData s = authService.fetchSession();
                accessToken = s.accessToken;
                apiService.setToken(s.accessToken);
            } catch (Models.DeviceRevokedException e) {
                onDeviceRevoked();
            } catch (Exception ignored) {
            }
        });
    }

    private void onDeviceRevoked() {
        priceStream.stop();
        cancel(tokenRefreshRunnable);
        cancel(sessionRetryRunnable);
        cancel(loadRetryRunnable);
        authService.clearEnrollment();
        cache.clear();
        accessToken = null;
        priceVersion = -1;
        sessionStarted = false;
        mainHandler.post(() -> {
            state = new UiState();
            state.screen = AppScreen.SETUP;
            state.activationError = "Máy đã bị thu hồi kích hoạt. Vui lòng kích hoạt lại.";
            if (listener != null) listener.onStateChanged(state.copy());
        });
    }

    private void cancel(Runnable r) {
        if (r != null) mainHandler.removeCallbacks(r);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        priceStream.stop();
        mainHandler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
    }
}
