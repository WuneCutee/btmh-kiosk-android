package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.databinding.ItemCategoryTabBinding;

/** Java port 1:1 cua CategoryTabAdapter.kt that: dong chip danh muc, giang deu neu khong du lap day dai. */
public class CategoryTabAdapter extends RecyclerView.Adapter<CategoryTabAdapter.ViewHolder> {

    public static final class Tab {
        public final String id;
        public final String name;
        public final boolean selected;

        public Tab(String id, String name, boolean selected) {
            this.id = id;
            this.name = name;
            this.selected = selected;
        }
    }

    public interface OnSelect {
        void onSelect(String id);
    }

    private final OnSelect onSelect;
    private final List<Tab> items = new ArrayList<>();
    private int itemWidth = 0;

    public CategoryTabAdapter(OnSelect onSelect) {
        this.onSelect = onSelect;
    }

    public void submitList(List<Tab> newItems) {
        boolean sizeChanged = items.size() != newItems.size();
        items.clear();
        items.addAll(newItems);
        if (sizeChanged) {
            notifyDataSetChanged();
        } else {
            notifyItemRangeChanged(0, items.size());
        }
    }

    public void setItemWidth(int width) {
        if (width == itemWidth) return;
        itemWidth = width;
        notifyItemRangeChanged(0, getItemCount());
    }

    /** TabStrip.itemWidth that: it tab khong lap het dai thi gian deu, nhieu tab thi giu baseWidth va cuon. */
    public static int computeItemWidth(int stripWidth, int count, int baseWidth, int gap) {
        if (stripWidth <= 0 || count < 2) return baseWidth;
        int gaps = (count - 1) * gap;
        if (count * baseWidth + gaps >= stripWidth) return baseWidth;
        return (stripWidth - gaps) / count;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(ItemCategoryTabBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) onSelect.onSelect(items.get(position).id);
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Tab tab = items.get(position);
        View view = holder.binding.getRoot();
        ((android.widget.TextView) view).setText(tab.name != null ? tab.name
                : view.getContext().getString(R.string.list_tab_all));
        view.setSelected(tab.selected);

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        int width = itemWidth > 0 ? itemWidth : view.getResources().getDimensionPixelSize(R.dimen.list_tab_width);
        int gap = position == getItemCount() - 1 ? 0 : view.getResources().getDimensionPixelSize(R.dimen.list_tab_gap);
        if (params.width != width || params.getMarginEnd() != gap) {
            params.width = width;
            params.setMarginEnd(gap);
            view.setLayoutParams(params);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public int indexOfSelected() {
        for (int i = 0; i < items.size(); i++) if (items.get(i).selected) return i;
        return -1;
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ItemCategoryTabBinding binding;

        ViewHolder(ItemCategoryTabBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
