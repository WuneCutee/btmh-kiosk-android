package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import vn.baotinmanhhai.btmhkiosk.network.GoldHistoryClient;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * "Bien dong gia vang theo ngay" (thay bang gia san pham cu o man Home): hang nut chon san pham -> dai tom tat
 * (Hom nay / 30 ngay / 6 thang / 1 nam / 5 nam) -> danh sach tung ngay, moi nhat truoc: ngay, mua vao, ban ra va
 * chenh lech gia ban so voi ngay lien truoc (▲/▼). Du lieu tu website BTMH (xem {@link GoldHistoryClient}).
 */
public class GoldHistoryView extends LinearLayout {

    public interface Callbacks {
        void onSelectProduct(String code);
    }

    /** Thu tu + tap san pham hien nut chon — theo cai dat cua trang Bang gia vang tren website. */
    private static final List<String> PRODUCT_ORDER = Arrays.asList(
            "KGBG", "KGB", "KHS", "BT-TKC", "BT24K", "9999", "999", "SJC9999", "NL9999", "NL999");

    private static final int PAD = 16;
    private static final int DATE_COL = 96;
    private static final int DELTA_COL = 168;

    private final Callbacks callbacks;
    private final LinearLayout chips;
    private final HorizontalScrollView chipScroll;
    private final LinearLayout summary;
    private final RecyclerView list;
    private final TextView emptyText;
    private final RowAdapter adapter = new RowAdapter();

    private GoldHistoryClient.History boundHistory;
    private String boundCode;

    public GoldHistoryView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        setOrientation(VERTICAL);
        setBackground(Ui.card(Ui.R_LG));
        Ui.softShadow(this);
        Ui.clipRounded(this, Ui.R_LG);

        // ---- Nut chon san pham ----
        chipScroll = new HorizontalScrollView(context);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        chips = new LinearLayout(context);
        chips.setOrientation(HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chips.setPadding(Ui.px(PAD - 4), Ui.px(12), Ui.px(PAD), Ui.px(10));
        chipScroll.addView(chips, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        addView(chipScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // ---- Dai tom tat bien dong ----
        summary = new LinearLayout(context);
        summary.setOrientation(HORIZONTAL);
        summary.setPadding(Ui.px(PAD), Ui.px(2), Ui.px(PAD), Ui.px(12));
        addView(summary, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // ---- Tieu de cot ----
        LinearLayout columns = new LinearLayout(context);
        columns.setOrientation(HORIZONTAL);
        columns.setBackgroundColor(Ui.PAGE_BG_LIGHT);
        columns.setPadding(Ui.px(PAD), Ui.px(12), Ui.px(PAD), Ui.px(12));
        columns.addView(columnTitle(context, "NGÀY", Gravity.START), new LayoutParams(Ui.px(DATE_COL), LayoutParams.WRAP_CONTENT));
        columns.addView(columnTitle(context, "MUA VÀO", Gravity.END), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        columns.addView(columnTitle(context, "BÁN RA", Gravity.END), new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        columns.addView(columnTitle(context, "BIẾN ĐỘNG", Gravity.END), new LayoutParams(Ui.px(DELTA_COL), LayoutParams.WRAP_CONTENT));
        addView(columns, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        View line = new View(context);
        line.setBackgroundColor(Ui.BORDER_LIGHT);
        addView(line, new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(1))));

        // ---- Danh sach ngay ----
        FrameLayout listArea = new FrameLayout(context);
        list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setItemAnimator(null);
        listArea.addView(list, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        emptyText = Ui.text(context, "Đang tải biến động giá…", 10.5f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(0, 1.3f);
        emptyText.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        listArea.addView(emptyText, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        addView(listArea, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private static TextView columnTitle(Context context, String text, int gravity) {
        TextView t = Ui.caps(context, text, 7.5f, Ui.BURGUNDY_PRIMARY, 0.08f);
        t.setGravity(gravity);
        return t;
    }

    /**
     * @param history du lieu hien co (co the la cache cu); null neu chua co gi
     * @param code    san pham dang chon
     * @param loading dang tai (hien "Dang tai..." khi chua co du lieu)
     * @param error   tai loi va khong co cache
     * @param hotline hien kem thong bao loi (neu co)
     */
    public void bind(GoldHistoryClient.History history, String code, boolean loading, boolean error, String hotline) {
        boolean hasData = history != null && history.dataPoints != null && !history.dataPoints.isEmpty();
        if (history != boundHistory) {
            boundHistory = history;
            if (hasData) {
                adapter.submit(GoldHistoryClient.rows(history));
                list.scrollToPosition(0);
                buildSummary(history);
                buildChips(history, code);
            }
        } else if (hasData && !code.equals(boundCode)) {
            buildChips(history, code);
        }
        boundCode = code;
        if (!hasData) {
            adapter.submit(new ArrayList<>());
            summary.removeAllViews();
            if (chips.getChildCount() == 0) buildChips(null, code);
        }

        if (hasData) {
            emptyText.setVisibility(GONE);
        } else {
            String hot = hotline != null && !hotline.isEmpty() ? "\nVui lòng liên hệ hotline " + hotline : "";
            emptyText.setText(error && !loading
                    ? "Chưa tải được biến động giá." + hot : "Đang tải biến động giá…");
            emptyText.setVisibility(VISIBLE);
        }
    }

    private void buildChips(GoldHistoryClient.History history, String selected) {
        chips.removeAllViews();
        List<GoldHistoryClient.Option> options = new ArrayList<>();
        if (history != null && history.productOptions != null) {
            for (String c : PRODUCT_ORDER) {
                for (GoldHistoryClient.Option o : history.productOptions) {
                    if (c.equals(o.value)) options.add(o);
                }
            }
            if (options.isEmpty()) options.addAll(history.productOptions);
        }
        if (options.isEmpty()) { // chua co danh sach tu website: it nhat hien san pham dang chon
            GoldHistoryClient.Option o = new GoldHistoryClient.Option();
            o.value = selected;
            o.label = selected;
            options.add(o);
        }
        for (GoldHistoryClient.Option o : options) {
            boolean on = o.value.equals(selected);
            TextView chip = Ui.text(getContext(), o.label, 9f, on ? Ui.SURFACE_WHITE : Ui.TEXT_BODY_DARK,
                    on ? Ui.W_BOLD : Ui.W_MEDIUM);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setMaxWidth(Ui.px(300));
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(on ? Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0) : Ui.outline(Ui.R_PILL));
            chip.setPadding(Ui.px(20), 0, Ui.px(20), 0);
            chip.setOnClickListener(v -> {
                if (!o.value.equals(boundCode)) callbacks.onSelectProduct(o.value);
            });
            Ui.pressEffect(chip);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.px(48));
            lp.setMarginEnd(Ui.px(8));
            chips.addView(chip, lp);
            if (on) {
                chip.post(() -> chipScroll.smoothScrollTo(Math.max(0, chip.getLeft() - Ui.px(60)), 0));
            }
        }
    }

    private void buildSummary(GoldHistoryClient.History history) {
        summary.removeAllViews();
        if (history.priceChanges == null) return;
        for (GoldHistoryClient.Change c : history.priceChanges) {
            boolean down = "down".equalsIgnoreCase(c.trend);
            boolean up = "up".equalsIgnoreCase(c.trend);
            int tone = up ? Ui.UP_GREEN : down ? Ui.DOWN_RED : Ui.TEXT_BODY_DARK;
            LinearLayout card = new LinearLayout(getContext());
            card.setOrientation(VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setBackground(Ui.rect(up ? Ui.UP_TINT : down ? Ui.DOWN_TINT : Ui.PAGE_BG_LIGHT, Ui.R_SM, 0, 0));
            card.setPadding(Ui.px(4), Ui.px(9), Ui.px(4), Ui.px(9));
            TextView label = Ui.text(getContext(), c.label, 7.5f, Ui.TEXT_BODY_DARK, Ui.W_MEDIUM);
            label.setSingleLine(true);
            card.addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            TextView value = Ui.text(getContext(), (up ? "▲ " : down ? "▼ " : "") + stripSign(c.value), 8.5f, tone, Ui.W_BOLD);
            value.setSingleLine(true);
            LayoutParams vlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            vlp.topMargin = Ui.px(5);
            card.addView(value, vlp);
            TextView pct = Ui.text(getContext(), stripSign(c.percentage), 7.5f, tone, Ui.W_REGULAR);
            LayoutParams plp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            plp.topMargin = Ui.px(3);
            card.addView(pct, plp);
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(Ui.px(6));
            summary.addView(card, lp);
        }
    }

    /** "-70.000" / "+1.340.000" -> bo dau (mui ten da the hien huong). */
    private static String stripSign(String s) {
        if (s == null) return "";
        return s.startsWith("+") || s.startsWith("-") ? s.substring(1) : s;
    }

    // ==================== DANH SACH ====================

    private static class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        private final List<GoldHistoryClient.Row> rows = new ArrayList<>();

        void submit(List<GoldHistoryClient.Row> newRows) {
            rows.clear();
            rows.addAll(newRows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(new RowView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            holder.row.bind(rows.get(position), position == 0, position % 2 == 1);
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final RowView row;

            Holder(RowView row) {
                super(row);
                this.row = row;
            }
        }
    }

    private static class RowView extends LinearLayout {
        private final TextView date;
        private final TextView today;
        private final TextView buy;
        private final TextView sell;
        private final TextView delta;

        RowView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout content = new LinearLayout(context);
            content.setOrientation(HORIZONTAL);
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.setPadding(Ui.px(PAD), Ui.px(16), Ui.px(PAD), Ui.px(16));
            addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            LinearLayout dateCol = new LinearLayout(context);
            dateCol.setOrientation(VERTICAL);
            date = Ui.text(context, "", 11f, Ui.TEXT_TITLE_DARK, Ui.W_BOLD);
            dateCol.addView(date, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            today = Ui.text(context, "MỚI NHẤT", 6.5f, Ui.SURFACE_WHITE, Ui.W_BOLD);
            today.setBackground(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0));
            today.setPadding(Ui.px(8), Ui.px(2), Ui.px(8), Ui.px(3));
            LayoutParams tlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Ui.px(5);
            dateCol.addView(today, tlp);
            content.addView(dateCol, new LayoutParams(Ui.px(DATE_COL), LayoutParams.WRAP_CONTENT));

            buy = Ui.text(context, "", 11f, Ui.TEXT_BODY_DARK, Ui.W_MEDIUM);
            buy.setGravity(Gravity.END);
            content.addView(buy, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            sell = Ui.text(context, "", 11.5f, Ui.PRICE_ORANGE, Ui.W_BOLD);
            sell.setGravity(Gravity.END);
            content.addView(sell, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            FrameLayout deltaHolder = new FrameLayout(context);
            delta = Ui.text(context, "", 9f, Ui.TEXT_MUTED_LIGHT, Ui.W_BOLD);
            delta.setGravity(Gravity.CENTER);
            delta.setSingleLine(true);
            delta.setPadding(Ui.px(10), Ui.px(6), Ui.px(10), Ui.px(7));
            deltaHolder.addView(delta, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));
            content.addView(deltaHolder, new LayoutParams(Ui.px(DELTA_COL), LayoutParams.WRAP_CONTENT));

            View sep = new View(context);
            sep.setBackgroundColor(Ui.BORDER_LIGHT);
            LayoutParams sepLp = new LayoutParams(LayoutParams.MATCH_PARENT, Math.max(1, Ui.px(0.8f)));
            sepLp.setMargins(Ui.px(PAD), 0, Ui.px(PAD), 0);
            addView(sep, sepLp);
        }

        void bind(GoldHistoryClient.Row row, boolean newest, boolean odd) {
            setBackgroundColor(odd ? Ui.withAlpha(Ui.PAGE_BG_LIGHT, 0x70) : 0x00000000);
            date.setText(row.date);
            today.setVisibility(newest ? VISIBLE : GONE);
            buy.setText(Ui.number(row.buy));
            sell.setText(Ui.number(row.sell));
            if (row.delta == null || row.delta == 0) {
                delta.setText(row.delta == null ? "—" : "Không đổi");
                delta.setTextColor(Ui.TEXT_MUTED_LIGHT);
                delta.setBackground(null);
            } else {
                boolean up = row.delta > 0;
                delta.setText((up ? "▲ " : "▼ ") + Ui.number(Math.abs(row.delta)));
                delta.setTextColor(up ? Ui.UP_GREEN : Ui.DOWN_RED);
                delta.setBackground(Ui.rect(up ? Ui.UP_TINT : Ui.DOWN_TINT, Ui.R_PILL, 0, 0));
            }
        }
    }
}
