package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.util.ArrayList;
import java.util.List;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.data.Models;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Anh san pham dang trang tri (o anh nen kem, nam tren dinh the san pham): vuot ngang chuyen anh theo thu tu images[]
 * (priority tang dan, spec II.2.8); cham chi vi tri hien tai nam trong vien trang mo o day (cham dang chon keo dai,
 * mau do ruou). Chi 1 anh -> khong hien cham.
 */
public class ImagePagerView extends FrameLayout {

    private final RecyclerView recycler;
    private final LinearLayout dots;
    private final List<String> urls = new ArrayList<>();
    private final Adapter adapter = new Adapter();
    private String boundKey = "";

    public ImagePagerView(Context context) {
        super(context);
        setBackgroundColor(Ui.PAGE_BG_LIGHT);

        recycler = new RecyclerView(context);
        LinearLayoutManager lm = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        recycler.setLayoutManager(lm);
        recycler.setAdapter(adapter);
        recycler.setOverScrollMode(OVER_SCROLL_NEVER);
        new PagerSnapHelper().attachToRecyclerView(recycler);
        addView(recycler, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        dots = new LinearLayout(context);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        dots.setBackground(Ui.rect(Ui.withAlpha(Ui.SURFACE_WHITE, 0xE0), Ui.R_PILL, Ui.BORDER_LIGHT, 1));
        dots.setPadding(Ui.px(14), Ui.px(9), Ui.px(14), Ui.px(9));
        dots.setVisibility(GONE);
        LayoutParams dotsLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotsLp.bottomMargin = Ui.px(14);
        addView(dots, dotsLp);

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                highlight(lm.findFirstCompletelyVisibleItemPosition() >= 0
                        ? lm.findFirstCompletelyVisibleItemPosition()
                        : lm.findFirstVisibleItemPosition());
            }
        });
    }

    public void bind(Models.ProductDto product) {
        List<Models.ProductImageDto> sorted = product.sortedImages();
        StringBuilder key = new StringBuilder(product.id);
        for (Models.ProductImageDto i : sorted) key.append('|').append(i.url);
        if (key.toString().equals(boundKey)) return; // gia doi qua SSE khong lam anh nhay ve trang dau
        boundKey = key.toString();

        urls.clear();
        for (Models.ProductImageDto i : sorted) urls.add(i.url);
        if (urls.isEmpty()) urls.add(""); // images[] rong -> 1 trang anh mac dinh (II.2.6)
        adapter.notifyDataSetChanged();
        recycler.scrollToPosition(0);

        dots.removeAllViews();
        dots.setVisibility(urls.size() > 1 ? VISIBLE : GONE);
        if (urls.size() > 1) {
            for (int i = 0; i < urls.size(); i++) {
                View dot = new View(getContext());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(9), Ui.px(9));
                lp.setMargins(Ui.px(4), 0, Ui.px(4), 0);
                dots.addView(dot, lp);
            }
            highlight(0);
        }
    }

    private void highlight(int position) {
        for (int i = 0; i < dots.getChildCount(); i++) {
            boolean active = i == position;
            View dot = dots.getChildAt(i);
            dot.setBackground(Ui.rect(active ? Ui.BURGUNDY_PRIMARY : Ui.BORDER_LIGHT, Ui.R_PILL, 0, 0));
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            int w = active ? Ui.px(24) : Ui.px(9);
            if (lp.width != w) {
                lp.width = w;
                dot.setLayoutParams(lp);
            }
        }
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView image = new ImageView(parent.getContext());
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new Holder(image);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            String url = urls.get(position);
            if (url == null || url.isEmpty()) {
                Glide.with(holder.image).clear(holder.image);
                holder.image.setColorFilter(Ui.SIDEBAR_GOLD_BOT, PorterDuff.Mode.SRC_IN); // logo vang -> vang dong dam cho ro tren nen kem
                holder.image.setPadding(Ui.px(90), Ui.px(90), Ui.px(90), Ui.px(90));
                holder.image.setImageResource(R.drawable.logo);
                return;
            }
            holder.image.setPadding(Ui.px(16), Ui.px(12), Ui.px(16), Ui.px(24));
            holder.image.setColorFilter(Ui.PAGE_BG_LIGHT, PorterDuff.Mode.MULTIPLY); // nen trang cua anh hoa vao nen kem
            Glide.with(holder.image).load(url).fitCenter()
                    .transition(DrawableTransitionOptions.withCrossFade(200)).into(holder.image);
        }

        @Override
        public int getItemCount() {
            return urls.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final ImageView image;

            Holder(ImageView image) {
                super(image);
                this.image = image;
            }
        }
    }
}
