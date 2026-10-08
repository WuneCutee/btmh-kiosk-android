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
 * Man "San pham tich luy" (spec II.1.7), nam trong khung {@link ShellView}.
 * Thanh tong hop day man duoc thiet ke noi (floating) tren nen trang:
 * - Card thong tin bo tron ben trai: So luong, So chi, Thanh tien.
 * - Nut "TIEP TUC" ben phai doc lap mau do ruou.
 */
public class ProductsView extends FrameLayout implements Ui.InsetAware {

    public interface Callbacks {
        void onCategoryClick(String categoryId);

        void onProductClick(Models.ProductDto product);

        /** Goi sau 300ms khong go them; VM tu bo qua chuoi < 2 ky tu. */
        void onSearch(String query);

        void onLoadMore();

        /** Stepper [-/+] tren the luoi: them/bot 1 san pham khoi gio hang. */
        void onIncrement(Models.ProductDto product);

        void onDecrement(Models.ProductDto product);

        /** Nut "TIEP TUC" tren thanh tong hop: mo man Xac nhan don hang. */
        void onContinue();
    }

    private static final int CHIP_HEIGHT = 60;
    private static final int CHIP_GAP = 10;
    private static final int BANNER_HEIGHT = 210;
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

    // Cum tong hop noi o day man
    private final LinearLayout summaryBar;
    private final TextView summaryQty;
    private final TextView summaryUnits;
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

        // ---- Luoi san pham ----
        recycler = new RecyclerView(context);
        int half = Ui.px(ProductAdapter.SPACING / 2f);
        recycler.setPadding(half, half, half, half);
        recycler.setClipToPadding(false);
        recycler.setOverScrollMode(OVER_SCROLL_NEVER);
        GridLayoutManager gm = new GridLayoutManager(context, ProductAdapter.COLUMNS);
        gm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.isFooter(position) ? ProductAdapter.COLUMNS : 1;
            }
        });
        recycler.setLayoutManager(gm);
        // The san pham: chon chieu cao sao cho dung 2 hang vua khit vung hien thi (khong tinh phan cuon)
        recycler.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitTwoRows());
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
                if (last >= adapter.getItemCount() - 4) callbacks.onLoadMore();
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

        // ---- Thanh tong hop day man (Floating container ben tren nen) ----
        summaryBar = new LinearLayout(context);
        summaryBar.setOrientation(LinearLayout.HORIZONTAL);
        summaryBar.setGravity(Gravity.CENTER_VERTICAL);
        // Nen panel lien tuc phia sau thanh tong hop, de khong bi cac the san pham de len
        summaryBar.setBackgroundColor(Ui.PAGE_BG_LIGHT);
        summaryBar.setPadding(Ui.px(14), Ui.px(10), Ui.px(14), Ui.px(10));
        summaryBar.setElevation(Ui.px(4));

        // 1. Card thong tin ben trai: nen trang bo tron, co bong
        LinearLayout summaryCard = new LinearLayout(context);
        summaryCard.setOrientation(LinearLayout.VERTICAL);
        summaryCard.setBackground(Ui.rect(Ui.SURFACE_WHITE, Ui.R_LG, 0, 0));
        summaryCard.setPadding(Ui.px(18), Ui.px(12), Ui.px(18), Ui.px(12));
        Ui.elevate(summaryCard, 4, 0x1A000000);

        // Hang 1 cua card: "Số lượng ... X"  va  "Số chỉ ... Y"
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);

        // Cột Số lượng
        LinearLayout colQty = new LinearLayout(context);
        colQty.setOrientation(LinearLayout.HORIZONTAL);
        colQty.setGravity(Gravity.CENTER_VERTICAL);
        TextView lblQty = Ui.text(context, "Số lượng", 10.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        colQty.addView(lblQty, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        summaryQty = Ui.text(context, "0", 11.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        colQty.addView(summaryQty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row1.addView(colQty, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        View gap = new View(context);
        row1.addView(gap, new LinearLayout.LayoutParams(Ui.px(20), 1));

        // Cột Số chỉ
        LinearLayout colUnits = new LinearLayout(context);
        colUnits.setOrientation(LinearLayout.HORIZONTAL);
        colUnits.setGravity(Gravity.CENTER_VERTICAL);
        TextView lblUnits = Ui.text(context, "Số chỉ", 10.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        colUnits.addView(lblUnits, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        summaryUnits = Ui.text(context, "0", 11.5f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
        colUnits.addView(summaryUnits, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        row1.addView(colUnits, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        summaryCard.addView(row1, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Hang 2 cua card: "Thành tiền" ... "XXX,XXX VND"
        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams r2Lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r2Lp.topMargin = Ui.px(6);
        row2.setLayoutParams(r2Lp);

        TextView lblTotal = Ui.text(context, "Thành tiền", 10.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        row2.addView(lblTotal, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        summaryValue = Ui.text(context, "0 VND", 13f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        row2.addView(summaryValue, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        summaryCard.addView(row2);
        summaryBar.addView(summaryCard, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // 2. Nut "TIẾP TỤC" pill rieng biet ben phai
        continueButton = Ui.text(context, "TIẾP TỤC", 11.5f, 0xFFFFFFFF, Ui.W_BOLD);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setOnClickListener(v -> {
            if (boundHasCart) callbacks.onContinue();
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(Ui.px(130), LinearLayout.LayoutParams.MATCH_PARENT);
        btnLp.leftMargin = Ui.px(12);
        summaryBar.addView(continueButton, btnLp);

        FrameLayout.LayoutParams sbLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        sbLp.setMargins(0, 0, 0, 0);
        addView(summaryBar, sbLp);

        // ---- Phan dau (header) ----
        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(Ui.PAGE_BG_LIGHT);
        header.setElevation(Ui.px(2));

        // O tim kiem
        searchBox = new LinearLayout(context);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(Ui.outline(Ui.R_PILL));
        Ui.softShadow(searchBox);

        searchInput = new EditText(context);
        searchInput.setHint("Nhập tên sản phẩm");
        searchInput.setHintTextColor(0xFF666666);
        searchInput.setTextColor(Ui.TEXT_TITLE_DARK);
        searchInput.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(10.5f));
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(250)});
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
                handler.postDelayed(searchRunnable, DEBOUNCE_MS);
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
        clear.setOnClickListener(v -> searchInput.setText(""));
        FrameLayout clearDisc = new FrameLayout(context);
        clearDisc.setBackground(Ui.oval(Ui.PAGE_BG_LIGHT));
        clearDisc.addView(new IconView(context, IconView.CLOSE, Ui.TEXT_BODY_DARK).strokeUnits(2.2f),
                new FrameLayout.LayoutParams(Ui.px(18), Ui.px(18), Gravity.CENTER));
        clear.addView(clearDisc, new FrameLayout.LayoutParams(Ui.px(36), Ui.px(36), Gravity.CENTER));
        Ui.pressEffect(clear);
        clearButton = clear;
        searchBox.addView(clear, new LinearLayout.LayoutParams(Ui.px(56), Ui.px(64)));
        IconView searchIcon = new IconView(context, IconView.SEARCH, Ui.INK_500).strokeUnits(1.9f);
        LinearLayout.LayoutParams siLp = new LinearLayout.LayoutParams(Ui.px(26), Ui.px(26));
        siLp.setMargins(0, 0, Ui.px(22), 0);
        searchBox.addView(searchIcon, siLp);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(64));
        searchLp.setMargins(Ui.px(14), Ui.px(10), Ui.px(14), Ui.px(10));
        header.addView(searchBox, searchLp);

        // Banner
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

        // Hang chip danh muc
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

    private static FrameLayout chipArrow(Context context, int icon) {
        FrameLayout f = new FrameLayout(context);
        f.addView(new IconView(context, icon, Ui.rgb(190, 166, 134)).strokeUnits(2.2f),
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

    /** Dat chieu cao the sao cho 2 hang (ke ca khoang cach) vua vung hien thi cua luoi. */
    private void fitTwoRows() {
        // Vung nhin thay that: tu day thanh dau den mep tren thanh tong hop (neu dang hien)
        int top = header.getBottom();
        int bottom = summaryBar.getVisibility() == VISIBLE ? summaryBar.getTop() : getHeight();
        int visible = bottom - top;
        if (visible <= 0) return;
        int margin = Ui.px(ProductAdapter.SPACING / 2f);
        int cardH = (visible - 4 * margin) / 2;
        if (cardH > 0) adapter.setCardHeightPx(cardH);
    }

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
        int bottomExtra = summaryVisible ? Ui.px(104) + insetBottom : insetBottom;
        recycler.setPadding(half, top, half, half + bottomExtra);
        skeleton.setPadding(half, top, half, 0);
        emptyHost.setPadding(0, headerHeight, 0, insetBottom);

        FrameLayout.LayoutParams sbLp = (FrameLayout.LayoutParams) summaryBar.getLayoutParams();
        if (sbLp != null) {
            sbLp.bottomMargin = insetBottom;
            summaryBar.setLayoutParams(sbLp);
        }
    }

    public void bind(KioskViewModel.UiState state) {
        if (state.banners != boundBanners) {
            boundBanners = state.banners;
            bannerIndex = 0;
            if (boundBanners == null || boundBanners.isEmpty()) {
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
                    : (state.errorMessage != null ? "Không tải được dữ liệu. Vui lòng thử lại sau." : "Không có sản phẩm"));
            emptyBox.setVisibility(VISIBLE);
        } else {
            emptyBox.setVisibility(GONE);
        }

        // Stepper tren the + thanh tong hop
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

        // Cap nhat card tong hop
        summaryQty.setText(String.valueOf(totalQty));
        summaryUnits.setText(Ui.number(totalUnits));
        summaryValue.setText(hasCart && state.pricesHidden ? "Đang cập nhật giá…" : Ui.price(totalValue));

        // Nut "TIẾP TỤC"
        if (hasCart) {
            continueButton.setTextColor(0xFFFFFFFF);
            continueButton.setBackground(Ui.pressable(
                    Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0),
                    Ui.rect(Ui.BURGUNDY_DARK, Ui.R_PILL, 0, 0)));
            Ui.elevate(continueButton, 4, Ui.BURGUNDY_PRIMARY);
        } else {
            continueButton.setTextColor(0xFF757981);
            continueButton.setBackground(Ui.rect(0xFFC5C5C5, Ui.R_PILL, 0, 0));
            continueButton.setElevation(0);
        }
    }

    public void pauseVideo() {
        bannerVideo.pause();
    }

    public void resumeVideo() {
        bannerVideo.resume();
    }

    public void clearSearchField() {
        suppressSearch = true;
        searchInput.setText("");
        suppressSearch = false;
    }

    private void showBanner() {
        if (boundBanners == null || boundBanners.isEmpty()) return;
        int count = boundBanners.size();
        int active = bannerIndex % count;
        String url = boundBanners.get(active).imagePath;
        Glide.with(bannerImage).load(url).fitCenter().into(bannerImage);

        if (bannerDots.getChildCount() != count) {
            bannerDots.removeAllViews();
            for (int i = 0; i < count; i++) {
                View dot = new View(getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(10), Ui.px(10));
                lp.setMargins(Ui.px(3), 0, Ui.px(3), 0);
                bannerDots.addView(dot, lp);
            }
        }
        bannerDots.setVisibility(count > 1 ? VISIBLE : GONE);
        for (int i = 0; i < bannerDots.getChildCount(); i++) {
            View dot = bannerDots.getChildAt(i);
            boolean on = i == active;
            dot.setBackground(Ui.oval(on ? Ui.rgb(234, 234, 234) : Ui.rgb(191, 191, 191)));
        }
    }

    private void buildTabs(List<Models.CategoryDto> categories, String selectedId) {
        List<Models.CategoryDto> items = new ArrayList<>();
        items.add(new Models.CategoryDto("ALL", "Tất cả"));
        if (categories != null) items.addAll(categories);

        chipRow.removeAllViews();
        View selectedChip = null;
        for (Models.CategoryDto category : items) {
            boolean active = category.id.equals(selectedId);
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