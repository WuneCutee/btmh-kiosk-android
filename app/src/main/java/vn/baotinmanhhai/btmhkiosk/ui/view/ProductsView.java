package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.ui.KioskViewModel;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man "San pham tich luy" (spec II.1.7), nam trong khung {@link ShellView} (tieu de + Quay lai do thanh dau cua khung
 * lo). Phan dau (dinh) nam TREN luoi va hoi trong suot: banner anh (chi khi co, doi moi 5s) -> o tim kiem (>= 2 ky tu,
 * debounce 300ms) -> MOT hang chip danh muc cuon ngang. Luoi san pham (the trang) co phan trang chay phia duoi phan do
 * (phan duy nhat cuon duoc); luc tai hien skeleton mau be co vet sang.
 */
public class ProductsView extends FrameLayout implements Ui.InsetAware {

    public interface Callbacks {
        void onCategoryClick(String categoryId);

        void onProductClick(Models.ProductDto product);

        /** Goi sau 300ms khong go them; VM tu bo qua chuoi < 2 ky tu (tra ve danh sach thuong). */
        void onSearch(String query);

        void onLoadMore();

        /** Stepper [-/+] tren the luoi: them/bot 1 san pham khoi gio hang (khong qua man Chi tiet). */
        void onIncrement(Models.ProductDto product);

        void onDecrement(Models.ProductDto product);

        /** Nut "TIEP TUC" tren thanh tong hop day man (khi gio hang co san pham): mo man Xac nhan don hang. */
        void onContinue();
    }

    private static final int CHIP_HEIGHT = 60;
    private static final int CHIP_GAP = 10;
    private static final int BANNER_HEIGHT = 120;
    private static final long DEBOUNCE_MS = 300;

    private final Callbacks callbacks;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final LinearLayout header;
    private final CoverVideoView bannerVideo;
    private final ImageView bannerImage;
    private final LinearLayout bannerDots;
    private final EditText searchInput;
    private final View clearButton;
    private final LinearLayout searchBox;
    private final HorizontalScrollView chipScroll;
    private final LinearLayout chipRow;
    private final LinearLayout emptyBox;
    private final FrameLayout emptyHost;
    private final TextView emptyText;
    private final LinearLayout skeleton;
    private final RecyclerView recycler;
    private final ProductAdapter adapter;
    private final LinearLayout summaryBar;
    private final TextView summaryQty;
    private final TextView summaryValue;
    private final TextView continueButton;

    private List<Models.BannerDto> boundBanners;
    private List<Models.CategoryDto> boundCategories;
    private String boundSelectedCategory;
    private List<Models.ProductDto> boundProducts;
    private boolean boundHasMore;
    private boolean boundPricesHidden;
    private boolean boundHasCart;
    private int bannerIndex;
    private boolean suppressSearch = false;
    private int headerHeight;
    private int insetBottom;
    private View selectedChipView;
    private boolean pendingCenter;

    private final Runnable rotateBanner = new Runnable() {
        @Override
        public void run() {
            if (boundBanners != null && boundBanners.size() > 1 && isShown()) {
                bannerIndex = (bannerIndex + 1) % boundBanners.size();
                showBanner();
            }
            handler.postDelayed(this, 5000);
        }
    };

    public ProductsView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;

        // ---- Luoi san pham (day toan vung noi dung, padding tren = chieu cao phan dau) ----
        recycler = new RecyclerView(context);
        int half = Ui.px(ProductAdapter.SPACING / 2f);
        recycler.setPadding(half, half, half, half);
        recycler.setClipToPadding(false);
        recycler.setOverScrollMode(OVER_SCROLL_NEVER);
        GridLayoutManager gm = new GridLayoutManager(context, ProductAdapter.COLUMNS);
        gm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isFooter(position) ? ProductAdapter.COLUMNS : 1; // dong cuoi chiem het chieu ngang
            }
        });
        recycler.setLayoutManager(gm);
        adapter = new ProductAdapter(product -> callbacks.onProductClick(product), new ProductAdapter.OnStepperChange() {
            @Override
            public void onIncrement(Models.ProductDto product) {
                callbacks.onIncrement(product);
            }

            @Override
            public void onDecrement(Models.ProductDto product) {
                callbacks.onDecrement(product);
            }
        });
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                int last = gm.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 4) callbacks.onLoadMore(); // gan cuoi -> tai trang ke
            }
        });
        addView(recycler, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- Skeleton khi dang tai ----
        skeleton = new LinearLayout(context);
        skeleton.setOrientation(LinearLayout.VERTICAL);
        skeleton.setVisibility(GONE);
        skeleton.setClickable(false);
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < ProductAdapter.COLUMNS; c++) row.addView(skeletonCard(context));
            skeleton.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        addView(skeleton, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- Trang thai rong ----
        emptyBox = new LinearLayout(context);
        emptyBox.setOrientation(LinearLayout.VERTICAL);
        emptyBox.setGravity(Gravity.CENTER_HORIZONTAL);
        emptyBox.setVisibility(GONE);
        FrameLayout emptyIcon = new FrameLayout(context);
        emptyIcon.setBackground(Ui.ovalStroke(Ui.SURFACE_WHITE, Ui.BORDER_LIGHT, 1));
        emptyIcon.addView(new IconView(context, IconView.SEARCH, Ui.TEXT_MUTED_LIGHT).strokeUnits(1.6f),
                new FrameLayout.LayoutParams(Ui.px(46), Ui.px(46), Gravity.CENTER));
        emptyBox.addView(emptyIcon, new LinearLayout.LayoutParams(Ui.px(104), Ui.px(104)));
        emptyText = Ui.text(context, "", 11f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(0, 1.2f);
        emptyText.setPadding(Ui.px(40), 0, Ui.px(40), 0);
        LinearLayout.LayoutParams etLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etLp.topMargin = Ui.px(22);
        emptyBox.addView(emptyText, etLp);
        emptyHost = new FrameLayout(context);
        emptyHost.setClickable(false);
        emptyHost.addView(emptyBox, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        addView(emptyHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // ---- Thanh tong hop day man (theo mockup Figma "Man hinh Chon vat pham"): luon hien (ke ca gio hang
        // rong, hien 0), So luong/Thanh tien + nut "TIEP TUC" — xam #C5C5C5/#757981 khi gio rong, do ruou khi co hang ----
        summaryBar = new LinearLayout(context);
        summaryBar.setOrientation(LinearLayout.HORIZONTAL);
        summaryBar.setGravity(Gravity.CENTER_VERTICAL);
        summaryBar.setBackgroundColor(Ui.SURFACE_WHITE);
        summaryBar.setPadding(Ui.px(18), Ui.px(12), Ui.px(14), Ui.px(12));
        Ui.elevate(summaryBar, 6, 0xFF6B4A2B);
        LinearLayout summaryTexts = new LinearLayout(context);
        summaryTexts.setOrientation(LinearLayout.VERTICAL);
        // Dong 1: "So luong  N   So chi  N" — dong 2: "Thanh tien   X VND" (theo mockup Figma).
        summaryQty = Ui.text(context, "", 9.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM);
        summaryTexts.addView(summaryQty, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LinearLayout valueRow = new LinearLayout(context);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.addView(Ui.text(context, "Thành tiền", 9.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM),
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        summaryValue = Ui.text(context, "", 12.5f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD); // #AA1F23 dung CSS Figma (khong phai cam)
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        svLp.leftMargin = Ui.px(8);
        valueRow.addView(summaryValue, svLp);
        LinearLayout.LayoutParams valueRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueRowLp.topMargin = Ui.px(2);
        summaryTexts.addView(valueRow, valueRowLp);
        summaryBar.addView(summaryTexts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        continueButton = Ui.primaryButton(context, "TIẾP TỤC", 11f);
        continueButton.setOnClickListener(v -> {
            if (boundHasCart) callbacks.onContinue();
        });
        summaryBar.addView(continueButton, new LinearLayout.LayoutParams(Ui.px(160), Ui.px(60)));
        addView(summaryBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        // ---- Phan dau (dinh, kem sang) nam duoi thanh dau cua khung: o tim kiem, banner, hang chip ----
        // Thu tu theo mockup Figma moi nhat "Man hinh Chon vat pham": O TIM KIEM TRUOC, banner quang cao SAU
        // (truoc day banner dung truoc o tim kiem — DA DOI lai cho dung thu tu Figma).
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Ui.PAGE_BG_LIGHT);
        header.setElevation(Ui.px(2));

        // O tim kiem (theo mockup Figma "Man hinh Chon vat pham"): pill trang, icon kinh lup o BEN PHAI, nut xoa; vien do ruou khi dang go
        searchBox = new LinearLayout(context);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(Ui.outline(Ui.R_PILL));
        Ui.softShadow(searchBox);

        searchInput = new EditText(context);
        searchInput.setHint("Nhập tên sản phẩm");
        searchInput.setHintTextColor(0xFF666666); // dung CSS Figma (man "Chon vat pham"): color: #666666
        searchInput.setTextColor(Ui.TEXT_TITLE_DARK);
        searchInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(10.5f));
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(250)}); // II.2.7: name <= 250
        searchInput.setIncludeFontPadding(false);
        searchInput.setBackground(null);
        searchInput.setPadding(Ui.px(24), 0, Ui.px(10), 0);
        searchInput.setHighlightColor(Ui.withAlpha(Ui.BURGUNDY_PRIMARY, 0x40));
        searchInput.setOnFocusChangeListener((v, focused) -> searchBox.setBackground(
                Ui.rect(Ui.SURFACE_WHITE, Ui.R_PILL, focused ? Ui.BURGUNDY_PRIMARY : Ui.BORDER_LIGHT, focused ? 1.5f : 1)));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                clearButton.setVisibility(s.length() > 0 ? VISIBLE : INVISIBLE);
                if (suppressSearch) return;
                handler.removeCallbacks(searchRunnable);
                handler.postDelayed(searchRunnable, DEBOUNCE_MS); // debounce 300ms sau phim cuoi
            }
        });
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(searchRunnable);
                searchRunnable.run();
                return true;
            }
            return false;
        });
        searchBox.addView(searchInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        FrameLayout clear = new FrameLayout(context);
        clear.setVisibility(INVISIBLE);
        clear.setOnClickListener(v -> searchInput.setText("")); // TextWatcher se goi tim kiem (chuoi rong)
        FrameLayout clearDisc = new FrameLayout(context);
        clearDisc.setBackground(Ui.oval(Ui.PAGE_BG_LIGHT));
        clearDisc.addView(new IconView(context, IconView.CLOSE, Ui.TEXT_BODY_DARK).strokeUnits(2.2f),
                new FrameLayout.LayoutParams(Ui.px(18), Ui.px(18), Gravity.CENTER));
        clear.addView(clearDisc, new FrameLayout.LayoutParams(Ui.px(36), Ui.px(36), Gravity.CENTER));
        Ui.pressEffect(clear);
        clearButton = clear;
        searchBox.addView(clear, new LinearLayout.LayoutParams(Ui.px(56), Ui.px(64)));
        IconView searchIcon = new IconView(context, IconView.SEARCH, Ui.INK_500).strokeUnits(1.9f); // #504F4F dung CSS Figma
        LinearLayout.LayoutParams siLp = new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26));
        siLp.setMargins(0, 0, Ui.px(22), 0);
        searchBox.addView(searchIcon, siLp);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(64));
        sbLp.setMargins(Ui.px(14), Ui.px(10), Ui.px(14), Ui.px(10));
        header.addView(searchBox, sbLp);

        // Banner: video thuong hieu mac dinh (chay lien tuc, giong banner Home) khi server CHUA co banner anh nao;
        // co banner anh tu server thi uu tien hien anh (an video) — theo yeu cau "banner trong thi dung video thay the".
        FrameLayout bannerFrame = new FrameLayout(context);
        bannerVideo = new CoverVideoView(context, R.raw.banner);
        bannerFrame.addView(bannerVideo, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        bannerImage = new ImageView(context);
        bannerImage.setBackgroundColor(Ui.SURFACE_WHITE);
        bannerImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bannerImage.setVisibility(GONE);
        bannerFrame.addView(bannerImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        Ui.clipRounded(bannerFrame, Ui.R_LG);
        Ui.softShadow(bannerFrame);

        // Cham chi so banner (dung CSS Figma: "Silder" position:absolute, bottom:12px — NOI TRUC TIEP TREN
        // anh banner, khong phai hang rieng ben duoi). Tat ca cham deu tron deu, chi khac mau (khong gian ra khi active).
        bannerDots = new LinearLayout(context);
        bannerDots.setOrientation(LinearLayout.HORIZONTAL);
        bannerDots.setGravity(Gravity.CENTER);
        bannerDots.setVisibility(GONE);
        FrameLayout.LayoutParams dotsLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotsLp.bottomMargin = Ui.px(14);
        bannerFrame.addView(bannerDots, dotsLp);

        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(BANNER_HEIGHT));
        bannerLp.setMargins(Ui.px(14), Ui.px(4), Ui.px(14), Ui.px(4));
        header.addView(bannerFrame, bannerLp);

        // Hang chip danh muc: MOT hang cuon ngang, co mui ten 2 dau goi y con the cuon duoc (theo mockup Figma)
        LinearLayout chipArea = new LinearLayout(context);
        chipArea.setOrientation(LinearLayout.HORIZONTAL);
        chipArea.setGravity(Gravity.CENTER_VERTICAL);

        chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        chipScroll.setClipToPadding(false);
        chipRow = new LinearLayout(context);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setGravity(Gravity.CENTER_VERTICAL);
        chipRow.setPadding(Ui.px(4), Ui.px(2), Ui.px(4), Ui.px(2));
        chipScroll.addView(chipRow, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT));
        chipScroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> centerSelectedChip());

        FrameLayout chipLeft = chipArrow(context, IconView.BACK);
        chipLeft.setOnClickListener(v -> chipScroll.smoothScrollBy(-Ui.px(160), 0));
        chipArea.addView(chipLeft, new LinearLayout.LayoutParams(Ui.px(32), Ui.px(48)));

        chipArea.addView(chipScroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout chipRight = chipArrow(context, IconView.ARROW_RIGHT);
        chipRight.setOnClickListener(v -> chipScroll.smoothScrollBy(Ui.px(160), 0));
        chipArea.addView(chipRight, new LinearLayout.LayoutParams(Ui.px(32), Ui.px(48)));

        header.addView(chipArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View line = new View(context);
        line.setBackgroundColor(Ui.BORDER_LIGHT);
        LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1)));
        lineLp.topMargin = Ui.px(10);
        header.addView(line, lineLp);

        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP));
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            if (h != headerHeight) {
                headerHeight = h;
                applyGridPadding();
            }
        });
    }

    /** Mui ten nho goi y con the cuon danh muc (goc trai/phai hang chip), theo mockup Figma. */
    private static FrameLayout chipArrow(Context context, int icon) {
        FrameLayout f = new FrameLayout(context);
        f.addView(new IconView(context, icon, Ui.rgb(190, 166, 134)).strokeUnits(2.2f), // #BEA686 (Gold/600, dung Figma)
                new FrameLayout.LayoutParams(Ui.px(18), Ui.px(18), Gravity.CENTER));
        f.setClickable(true);
        Ui.pressEffect(f);
        return f;
    }

    private static View skeletonCard(Context context) {
        FrameLayout card = new FrameLayout(context);
        card.setBackground(Ui.card(Ui.R_LG));
        Ui.clipRounded(card, Ui.R_LG);
        Ui.softShadow(card);
        int m = Ui.px(ProductAdapter.SPACING / 2f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(ProductAdapter.CARD_W), Ui.px(ProductAdapter.CARD_H));
        lp.setMargins(m, m, m, m);
        card.setLayoutParams(lp);

        View tile = new View(context);
        tile.setBackground(Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_MD, 0, 0));
        FrameLayout.LayoutParams tileLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, Ui.px(ProductAdapter.CARD_H - ProductAdapter.INFO_H - 8));
        tileLp.setMargins(Ui.px(8), Ui.px(8), Ui.px(8), 0);
        card.addView(tile, tileLp);
        card.addView(bar(context, 0.85f, 14, ProductAdapter.CARD_H - ProductAdapter.INFO_H + 14));
        card.addView(bar(context, 0.5f, 14, ProductAdapter.CARD_H - ProductAdapter.INFO_H + 44));
        card.addView(bar(context, 0.42f, 20, ProductAdapter.CARD_H - 34));
        card.addView(new ShimmerView(context, Ui.R_LG), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return card;
    }

    private static View bar(Context context, float widthFraction, float heightDesign, float topDesign) {
        View v = new View(context);
        v.setBackground(Ui.rect(Ui.BORDER_LIGHT, Ui.R_PILL, 0, 0));
        int w = Math.round(Ui.px(ProductAdapter.CARD_W - 28) * widthFraction);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, Ui.px(heightDesign));
        lp.setMargins(Ui.px(14), Ui.px(topDesign), 0, 0);
        v.setLayoutParams(lp);
        return v;
    }

    private final Runnable searchRunnable = new Runnable() {
        @Override
        public void run() {
            callbacks.onSearch(searchInput.getText().toString());
        }
    };

    /** Vung an toan phia tren (cutout) do thanh dau cua khung lo; duoi (thanh dieu huong / ban phim) chua cho luoi. */
    @Override
    public void onInsets(int l, int t, int r, int b) {
        setPadding(l, 0, r, 0);
        insetBottom = b;
        applyGridPadding();
    }

    private void applyGridPadding() {
        int half = Ui.px(ProductAdapter.SPACING / 2f);
        int top = headerHeight + half + Ui.px(4);
        boolean summaryVisible = summaryBar.getVisibility() == VISIBLE;
        int bottomExtra = summaryVisible ? Ui.px(84) + insetBottom : insetBottom;
        recycler.setPadding(half, top, half, half + bottomExtra);
        skeleton.setPadding(half, top, half, 0);
        emptyHost.setPadding(0, headerHeight, 0, insetBottom); // trang thai rong nam giua vung luoi (khong duoi thanh dau/ban phim)
        summaryBar.setPadding(Ui.px(18), Ui.px(12), Ui.px(14), Ui.px(12) + insetBottom);
    }

    public void bind(KioskViewModel.UiState state) {
        if (state.banners != boundBanners) {
            boundBanners = state.banners;
            bannerIndex = 0;
            if (boundBanners == null || boundBanners.isEmpty()) {
                // Chua co banner tu server -> hien video thuong hieu mac dinh (giong banner Home) thay vi de trong.
                bannerImage.setVisibility(GONE);
                bannerDots.setVisibility(GONE);
                bannerVideo.setVisibility(VISIBLE);
            } else {
                bannerVideo.setVisibility(GONE);
                bannerImage.setVisibility(VISIBLE);
                showBanner();
            }
        }

        if (state.categories != boundCategories || !state.selectedCategoryId.equals(boundSelectedCategory)) {
            boolean categoryChanged = boundSelectedCategory != null && !state.selectedCategoryId.equals(boundSelectedCategory);
            boundCategories = state.categories;
            boundSelectedCategory = state.selectedCategoryId;
            buildTabs(state.categories, state.selectedCategoryId);
            // Doi tab -> xoa o tim kiem (VM da dat searchQuery = "").
            if (categoryChanged && state.searchQuery.isEmpty() && searchInput.length() > 0) {
                suppressSearch = true;
                searchInput.setText("");
                suppressSearch = false;
            }
        }

        if (state.products != boundProducts || state.productsHasMore != boundHasMore
                || state.pricesHidden != boundPricesHidden) {
            boundProducts = state.products;
            boundHasMore = state.productsHasMore;
            boundPricesHidden = state.pricesHidden;
            adapter.submitList(state.products, state.productsHasMore, state.pricesHidden);
        }

        boolean noProducts = state.products == null || state.products.isEmpty();
        skeleton.setVisibility(state.isLoading && noProducts ? VISIBLE : GONE);

        boolean empty = !state.isLoading && noProducts;
        if (empty) {
            boolean searching = state.searchQuery != null && state.searchQuery.length() >= 2;
            emptyText.setText(searching ? "Không tìm thấy sản phẩm"
                    // II.4: KHONG bao gio hien "message" cua server cho khach — chi cau chung.
                    : (state.errorMessage != null ? "Không tải được dữ liệu. Vui lòng thử lại sau." : "Không có sản phẩm"));
            emptyBox.setVisibility(VISIBLE);
        } else {
            emptyBox.setVisibility(GONE);
        }

        // ---- Stepper tren the + thanh tong hop (theo so luong dang co trong gio hang) ----
        Map<String, Integer> quantities = new HashMap<>();
        int totalQty = 0;
        double totalUnits = 0;
        double totalValue = 0;
        for (Models.CartItem it : state.cart) {
            quantities.put(it.product.id, it.quantity);
            totalQty += it.quantity;
            totalUnits += it.lineUnits();
            totalValue += it.lineTotal();
        }
        adapter.setCartState(quantities, state.limitQuantity, totalUnits);
        boolean hasCart = totalQty > 0;
        boundHasCart = hasCart;

        // "So luong  N   So chi  N" (N dong san pham khac nhau · tong so chi tat ca cac dong), theo mockup Figma.
        // Thanh nay LUON hien (ke ca gio hang rong, hien 0) — theo mockup Figma moi nhat "Man hinh Chon vat pham".
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        appendLabelValue(sb, "Số lượng  ", String.valueOf(state.cart.size()));
        sb.append("    ");
        appendLabelValue(sb, "Số chỉ  ", Ui.number(totalUnits));
        summaryQty.setText(sb);
        summaryValue.setText(hasCart && state.pricesHidden ? "Đang cập nhật giá…" : Ui.price(totalValue));

        // Nut "TIEP TUC": xam #C5C5C5/chu #757981 khi gio rong (khong bam duoc), do ruou khi co hang.
        if (hasCart) {
            continueButton.setTextColor(0xFFFFFBF4); // dung CSS Figma: color #FFFBF4 (khong phai trang thuan)
            continueButton.setBackground(Ui.pressable(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0), Ui.rect(Ui.BURGUNDY_DARK, Ui.R_PILL, 0, 0)));
            Ui.elevate(continueButton, 4, Ui.BURGUNDY_PRIMARY);
        } else {
            continueButton.setTextColor(0xFF757981);
            continueButton.setBackground(Ui.rect(0xFFC5C5C5, Ui.R_PILL, 0, 0));
            continueButton.setElevation(0);
        }
    }

    /** Noi 1 doan "nhan muted + gia tri dam" vao SpannableStringBuilder (dung cho dong "So luong N  So chi N"). */
    private static void appendLabelValue(android.text.SpannableStringBuilder sb, String label, String value) {
        int s1 = sb.length();
        sb.append(label);
        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.TEXT_MUTED_LIGHT), s1, sb.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int s2 = sb.length();
        sb.append(value);
        sb.setSpan(new android.text.style.ForegroundColorSpan(Ui.TEXT_TITLE_DARK), s2, sb.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s2, sb.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public void pauseVideo() {
        bannerVideo.pause();
    }

    public void resumeVideo() {
        bannerVideo.resume();
    }

    /** Tro ve trang thai ban dau khi roi man (xoa tu khoa cu). */
    public void clearSearchField() {
        suppressSearch = true;
        searchInput.setText("");
        suppressSearch = false;
    }

    private void showBanner() {
        if (boundBanners == null || boundBanners.isEmpty()) return;
        // Giu nguyen thu tu mang (server da sap san theo priority) — khong tu sap lai (spec II.2.4).
        int count = boundBanners.size();
        int active = bannerIndex % count;
        String url = boundBanners.get(active).imagePath;
        Glide.with(bannerImage).load(url).fitCenter().into(bannerImage);

        if (bannerDots.getChildCount() != count) {
            bannerDots.removeAllViews();
            for (int i = 0; i < count; i++) {
                View dot = new View(getContext());
                // Dung CSS Figma: cham tron 10x10 deu nhau, gap 6 — KHONG gian rong khi active, chi doi mau.
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(10), Ui.px(10));
                lp.setMargins(Ui.px(3), 0, Ui.px(3), 0);
                bannerDots.addView(dot, lp);
            }
        }
        bannerDots.setVisibility(count > 1 ? VISIBLE : GONE);
        for (int i = 0; i < bannerDots.getChildCount(); i++) {
            View dot = bannerDots.getChildAt(i);
            boolean on = i == active;
            // Mau cham dung dung CSS Figma: #EAEAEA (sang) / #BFBFBF (xam) — khong phai do thuong hieu.
            dot.setBackground(Ui.oval(on ? Ui.rgb(234, 234, 234) : Ui.rgb(191, 191, 191)));
        }
    }

    /**
     * Hang chip danh muc: MOT hang cuon ngang. Chip dang chon = pill do ruou (chu trang); chip con lai = nen trang
     * vien be (chu nau). Chip dang chon duoc cuon ra giua khi doi.
     */
    private void buildTabs(List<Models.CategoryDto> categories, String selectedId) {
        List<Models.CategoryDto> items = new ArrayList<>();
        items.add(new Models.CategoryDto("ALL", "Tất cả"));
        if (categories != null) items.addAll(categories);

        chipRow.removeAllViews();
        View selectedChip = null;
        for (Models.CategoryDto category : items) {
            boolean active = category.id.equals(selectedId);
            // Theo CSS dev-mode cua Figma (Man hinh Vang tich luy): CA 2 trang thai deu chu do #AA1F23, chi khac nen —
            // dang chon = gradient trang->vang (#FFFFFF -> #CBBCA9), chua chon = trang dac, bo goc 8 (KHONG phai pill).
            TextView chip = Ui.text(getContext(), category.name, 10.5f, Ui.BURGUNDY_PRIMARY, Ui.W_MEDIUM);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(Ui.px(28), 0, Ui.px(28), 0);
            chip.setBackground(active
                    ? Ui.gradient(Ui.SURFACE_WHITE, Ui.rgb(203, 188, 169), Ui.R_SM, 0, 0)
                    : Ui.rect(Ui.SURFACE_WHITE, Ui.R_SM, 0, 0));
            Ui.pressEffect(chip);
            chip.setOnClickListener(v -> {
                if (!category.id.equals(boundSelectedCategory)) callbacks.onCategoryClick(category.id);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Ui.px(CHIP_HEIGHT));
            lp.setMarginEnd(Ui.px(CHIP_GAP));
            chipRow.addView(chip, lp);
            if (active) selectedChip = chip;
        }
        selectedChipView = selectedChip;
        pendingCenter = true;
        chipScroll.post(this::centerSelectedChip);
    }

    /** Cuon chip dang chon ra giua hang (chay lai khi bo cuc xong, vi luc dung tab man hinh co the chua do kich thuoc). */
    private void centerSelectedChip() {
        View target = selectedChipView;
        if (!pendingCenter || target == null || chipScroll.getWidth() == 0 || target.getWidth() == 0) return;
        pendingCenter = false;
        int x = target.getLeft() - (chipScroll.getWidth() - target.getWidth()) / 2;
        chipScroll.smoothScrollTo(Math.max(0, x), 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.postDelayed(rotateBanner, 5000);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(rotateBanner);
        handler.removeCallbacks(searchRunnable);
        super.onDetachedFromWindow();
    }
}
