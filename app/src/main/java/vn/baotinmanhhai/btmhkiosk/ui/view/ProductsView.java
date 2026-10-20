package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.databinding.ActivityProductListBinding;
import vn.baotinmanhhai.btmhkiosk.ui.KioskViewModel;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man "San pham tich luy" (spec II.1.7) — doi tu ve tay Canvas sang inflate dung activity_product_list.xml
 * that qua ViewBinding. Banner giu co che ảnh/video don gian da co (khong port ViewPager2+ExoPlayer dung chung
 * giua trang cua ban that) vi ket qua hien thi tai moi thoi diem la nhu nhau — vpBanner that bi an (GONE), thay
 * bang 1 FrameLayout chen vao dung vi tri flBanner.
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

    private static final long DEBOUNCE_MS = 300;
    private static final int SPAN_COUNT = 3;

    private final Callbacks callbacks;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityProductListBinding binding;
    private final ProductAdapter adapter;
    private final CategoryTabAdapter tabAdapter;
    private final Runnable searchRunnable;

    // Banner: co che anh/video don gian co san (xem javadoc lop), chen vao flBanner that.
    private final CoverVideoView bannerVideo;
    private final ImageView bannerImage;
    private final android.widget.LinearLayout bannerDots;

    private List<Models.BannerDto> boundBanners;
    private List<Models.CategoryDto> boundCategories;
    private String boundSelectedCategory;
    private List<Models.ProductDto> boundProducts;
    private boolean boundPricesHidden;
    private boolean boundHasCart;
    private int bannerIndex;
    private boolean suppressSearch = false;
    private String scrolledTabId;

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
        binding = ActivityProductListBinding.inflate(LayoutInflater.from(context), this, true);
        searchRunnable = () -> callbacks.onSearch(binding.etSearch.getText().toString());

        // ---- Banner: vpBanner that an di, chen khung anh/video don gian vao cung vi tri flBanner ----
        binding.vpBanner.setVisibility(GONE);
        binding.shimmerBanner.setVisibility(GONE);
        FrameLayout bannerHost = new FrameLayout(context);
        bannerVideo = new CoverVideoView(context, R.raw.banner);
        bannerHost.addView(bannerVideo, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        bannerImage = new ImageView(context);
        // CENTER_CROP: anh phu kin khung -> goc bo theo flBanner (clipToOutline). FIT_CENTER cu de lo dai nen trang
        // tren/duoi khi ti le anh khac khung, nhin nhu anh khong bo goc.
        bannerImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bannerImage.setVisibility(GONE);
        bannerHost.addView(bannerImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        binding.flBanner.addView(bannerHost, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        bannerDots = binding.llBannerDots;
        binding.flBanner.setClipToOutline(true);

        // ---- O tim kiem ----
        binding.ivHome.setVisibility(GONE); // ShellView da co nut Home rieng tren rail, tranh trung lap.
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderSearchAction(s.length() > 0);
                if (suppressSearch) return;
                handler.removeCallbacks(searchRunnable);
                handler.postDelayed(searchRunnable, DEBOUNCE_MS);
            }
        });
        binding.etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(searchRunnable);
                searchRunnable.run();
                return true;
            }
            return false;
        });
        binding.ivSearchAction.setOnClickListener(v -> {
            if (binding.etSearch.getText().length() == 0) {
                binding.etSearch.requestFocus();
            } else {
                binding.etSearch.setText("");
            }
        });

        // ---- Hang chip danh muc ----
        tabAdapter = new CategoryTabAdapter(id -> {
            if (!id.equals(boundSelectedCategory)) callbacks.onCategoryClick(id);
        });
        binding.rvTabs.setAdapter(tabAdapter);
        binding.rvTabs.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                context, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false));
        binding.rvTabs.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) post(this::fitTabs);
        });
        int tabStep = getResources().getDimensionPixelSize(R.dimen.list_tab_width)
                + getResources().getDimensionPixelSize(R.dimen.list_tab_gap);
        binding.ivTabPrev.setOnClickListener(v -> binding.rvTabs.smoothScrollBy(-tabStep, 0));
        binding.ivTabNext.setOnClickListener(v -> binding.rvTabs.smoothScrollBy(tabStep, 0));

        // ---- Luoi san pham ----
        GridLayoutManager gm = new GridLayoutManager(context, SPAN_COUNT);
        binding.rvProducts.setLayoutManager(gm);
        int gapH = getResources().getDimensionPixelSize(R.dimen.list_grid_gap_h);
        int gapV = getResources().getDimensionPixelSize(R.dimen.list_grid_gap_v);
        binding.rvProducts.addItemDecoration(new GridSpacing(SPAN_COUNT, gapH, gapV));
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
        binding.rvProducts.setAdapter(adapter);
        if (binding.rvProducts.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) binding.rvProducts.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        binding.rvProducts.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                int last = gm.findLastVisibleItemPosition();
                if (last >= adapter.getItemCount() - 4) callbacks.onLoadMore();
            }
        });

        binding.btnNext.setOnClickListener(v -> callbacks.onContinue());
    }

    private void renderSearchAction(boolean hasText) {
        binding.ivSearchAction.setImageResource(hasText ? R.drawable.ic_list_search_clear : R.drawable.ic_list_search);
    }

    /** Ít tab thì giãn đều cho kín dải, nhiều tab thì giữ bề rộng gốc và cuộn (TabStrip.itemWidth that). */
    private void fitTabs() {
        tabAdapter.setItemWidth(CategoryTabAdapter.computeItemWidth(
                binding.rvTabs.getWidth(), tabAdapter.getItemCount(),
                getResources().getDimensionPixelSize(R.dimen.list_tab_width),
                getResources().getDimensionPixelSize(R.dimen.list_tab_gap)));
    }

    @Override
    public void onInsets(int l, int t, int r, int b) {
        binding.getRoot().setPadding(l, 0, r, 0);
        androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp =
                (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) binding.llBottom.getLayoutParams();
        lp.bottomMargin = getResources().getDimensionPixelSize(R.dimen.list_bottom_margin) + b;
        binding.llBottom.setLayoutParams(lp);
    }

    public void bind(KioskViewModel.UiState state) {
        if (state.banners != boundBanners) {
            boundBanners = state.banners;
            bannerIndex = 0;
            if (boundBanners == null || boundBanners.isEmpty()) {
                // activity_product_list.xml THAT: ghi chu ngay tren flBanner - "GONE khi rong thi thanh tab tu
                // cach o tim kiem 10" - tuc KHONG co anh banner thi AN HAN CA KHU VUC nay, khong fallback sang
                // video nao ca. Truoc day hien video nen toi (R.raw.banner, dung chung voi man cho) khi thieu du
                // lieu banner, tao mang mau sam lac long voi nen kem phia tren -> nhin nhu "2 khoi" tach biet.
                binding.flBanner.setVisibility(GONE);
                bannerVideo.setVisibility(GONE);
                bannerImage.setVisibility(GONE);
                bannerDots.setVisibility(GONE);
            } else {
                binding.flBanner.setVisibility(VISIBLE);
                bannerVideo.setVisibility(GONE);
                bannerImage.setVisibility(VISIBLE);
                showBanner();
            }
        }

        if (state.categories != boundCategories || !state.selectedCategoryId.equals(boundSelectedCategory)) {
            boundCategories = state.categories;
            boundSelectedCategory = state.selectedCategoryId;
            buildTabs(state.categories, state.selectedCategoryId);
        }

        if (state.products != boundProducts || state.pricesHidden != boundPricesHidden) {
            boundProducts = state.products;
            boundPricesHidden = state.pricesHidden;
            adapter.submitList(state.products, state.productsHasMore, state.pricesHidden);
        }

        boolean noProducts = state.products == null || state.products.isEmpty();
        boolean showSkeleton = state.isLoading && noProducts;
        binding.shimmerSkeleton.setVisibility(showSkeleton ? VISIBLE : GONE);
        if (showSkeleton) binding.shimmerSkeleton.startShimmer(); else binding.shimmerSkeleton.stopShimmer();
        binding.rvProducts.setVisibility(showSkeleton ? INVISIBLE : VISIBLE);

        boolean empty = !state.isLoading && noProducts;
        binding.tvEmpty.setVisibility(empty ? VISIBLE : GONE);
        if (empty) {
            boolean searching = state.searchQuery != null && state.searchQuery.length() >= 2;
            binding.tvEmpty.setText(searching ? getResources().getString(R.string.list_no_result)
                    : getResources().getString(R.string.list_empty));
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

        binding.tvCount.setText(String.valueOf(totalQty));
        binding.tvChi.setText(Ui.number(totalUnits));
        binding.tvTotal.setText((hasCart && state.pricesHidden ? "Đang cập nhật giá…" : Ui.price(totalValue)));
        binding.btnNext.setEnabled(hasCart);
    }

    public void pauseVideo() {
        bannerVideo.pause();
    }

    public void resumeVideo() {
        bannerVideo.resume();
    }

    public void clearSearchField() {
        suppressSearch = true;
        binding.etSearch.setText("");
        suppressSearch = false;
    }

    private void showBanner() {
        if (boundBanners == null || boundBanners.isEmpty()) return;
        int count = boundBanners.size();
        int active = bannerIndex % count;
        String url = boundBanners.get(active).imagePath;
        Glide.with(bannerImage).load(url).centerCrop().into(bannerImage);

        if (bannerDots.getChildCount() != count) {
            bannerDots.removeAllViews();
            for (int i = 0; i < count; i++) {
                View dot = new View(getContext());
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(Ui.px(10), Ui.px(10));
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
        List<CategoryTabAdapter.Tab> items = new ArrayList<>();
        items.add(new CategoryTabAdapter.Tab("ALL", "Tất cả", "ALL".equals(selectedId)));
        if (categories != null) {
            for (Models.CategoryDto c : categories) items.add(new CategoryTabAdapter.Tab(c.id, c.name, c.id.equals(selectedId)));
        }
        tabAdapter.submitList(items);
        fitTabs();
        if (!selectedId.equals(scrolledTabId)) {
            scrolledTabId = selectedId;
            int index = tabAdapter.indexOfSelected();
            if (index > 0) {
                ((androidx.recyclerview.widget.LinearLayoutManager) binding.rvTabs.getLayoutManager())
                        .scrollToPositionWithOffset(index, 0);
            }
        }
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

    /** Khoang cach luoi 3 cot deu nhau (tuong duong GridSpacing.kt that). */
    private static final class GridSpacing extends RecyclerView.ItemDecoration {
        private final int spanCount;
        private final int gapH;
        private final int gapV;

        GridSpacing(int spanCount, int gapH, int gapV) {
            this.spanCount = spanCount;
            this.gapH = gapH;
            this.gapV = gapV;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view);
            int column = position % spanCount;
            outRect.left = column * gapH / spanCount;
            outRect.right = gapH - (column + 1) * gapH / spanCount;
            if (position >= spanCount) outRect.top = gapV;
        }
    }
}
