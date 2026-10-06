package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import vn.baotinmanhhai.btmhkiosk.ui.KioskViewModel;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man thong bao trang thai may phu len tren (spec II.2.1, II.2.12): "Can cap nhat", "Dang cho ket noi",
 * "May tam ngung phuc vu", loi dong ho, khoa khong duoc cong nhan. Luon co hotline; loi can quan tri vien
 * xu ly hien kem ma may. Cac man tu phuc hoi duoc co nut "THU LAI". Bo cuc can giua: logo tren vien thuoc do ruou -> bieu tuong ve
 * bang Canvas trong vong sang -> tieu de -> noi dung -> (spinner) -> ma may/hotline -> nut.
 */
public class StatusView extends FrameLayout {

    public interface Callbacks {
        void onRetry();
    }

    private final IconView icon;
    private final TextView title;
    private final TextView message;
    private final TextView deviceLine;
    private final TextView hotlineLine;
    private final ProgressBar spinner;
    private final TextView retry;

    public StatusView(Context context, Callbacks callbacks) {
        super(context);
        setBackground(Ui.pageBackground());
        setClickable(true); // chan cham xuyen xuong lop duoi

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(Ui.px(40), 0, Ui.px(40), 0);
        addView(box, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

FrameLayout logo = new FrameLayout(context);        logo.setBackground(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0));        logo.addView(new LogoView(context, false), new FrameLayout.LayoutParams(Ui.px(140), Ui.px(48), Gravity.CENTER));        box.addView(logo, sized(196, 70, 30));

        // Bieu tuong trong vong sang (glow + vien vang mo + nen card)
        FrameLayout iconHost = new FrameLayout(context);
        View glow = new View(context);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(Ui.px(120));
        g.setColors(new int[]{Ui.withAlpha(Ui.SIDEBAR_GOLD_TOP, 0x60), Ui.withAlpha(Ui.SIDEBAR_GOLD_TOP, 0x00)});
        glow.setBackground(g);
        iconHost.addView(glow, new FrameLayout.LayoutParams(Ui.px(240), Ui.px(240), Gravity.CENTER));
        FrameLayout disc = new FrameLayout(context);
        GradientDrawable dd = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Ui.SURFACE_WHITE, Ui.SURFACE_WHITE});
        dd.setShape(GradientDrawable.OVAL);
        dd.setStroke(Math.max(1, Ui.px(1.5f)), Ui.BORDER_LIGHT);
        disc.setBackground(dd);
        Ui.softShadow(disc);
        icon = new IconView(context, IconView.CLOCK, Ui.BURGUNDY_PRIMARY).strokeUnits(1.6f);
        disc.addView(icon, new FrameLayout.LayoutParams(Ui.px(56), Ui.px(56), Gravity.CENTER));
        iconHost.addView(disc, new FrameLayout.LayoutParams(Ui.px(124), Ui.px(124), Gravity.CENTER));
        box.addView(iconHost, sized(240, 240, -30));

        title = Ui.caps(context, "", 16f, Ui.BURGUNDY_PRIMARY, 0.08f);
        title.setGravity(Gravity.CENTER);
        title.setLineSpacing(0, 1.15f);
        box.addView(title, wrap(16));

        message = Ui.text(context, "", 11f, Ui.TEXT_BODY_DARK, Ui.W_REGULAR);
        message.setGravity(Gravity.CENTER);
        message.setLineSpacing(0, 1.3f);
        box.addView(message, wrap(24));

        spinner = new ProgressBar(context);
        spinner.getIndeterminateDrawable().setColorFilter(Ui.BURGUNDY_PRIMARY, PorterDuff.Mode.SRC_IN);
        box.addView(spinner, sized(44, 44, 22));

        deviceLine = Ui.text(context, "", 9.5f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        deviceLine.setGravity(Gravity.CENTER);
        box.addView(deviceLine, wrap(10));

        hotlineLine = Ui.text(context, "", 10.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_MEDIUM);
        hotlineLine.setGravity(Gravity.CENTER);
        box.addView(hotlineLine, wrap(28));

        retry = Ui.primaryButton(context, "THỬ LẠI", 11f);
        retry.setOnClickListener(v -> callbacks.onRetry());
        box.addView(retry, sized(280, 64, 0));
    }

    private static LinearLayout.LayoutParams sized(float w, float h, float bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.px(w), Ui.px(h));
        lp.bottomMargin = Ui.px(bottom);
        return lp;
    }

    private static LinearLayout.LayoutParams wrap(float bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.px(bottom);
        return lp;
    }

    public void bind(KioskViewModel.UiState state) {
        String hotline = state.hotline != null && !state.hotline.isEmpty() ? "Hotline hỗ trợ: " + state.hotline : "";
        hotlineLine.setText(hotline);
        boolean showDevice = false;
        boolean canRetry = true;
        boolean loading = false;

        switch (state.status) {
            case NEEDS_UPDATE:
                icon.setIcon(IconView.UPDATE);
                title.setText("CẦN CẬP NHẬT ỨNG DỤNG");
                message.setText("Phiên bản ứng dụng trên máy đã cũ.\nVui lòng liên hệ nhân viên để được cập nhật.");
                canRetry = false;
                break;
            case WAITING_CONNECTION:
                icon.setIcon(IconView.WIFI_OFF);
                title.setText("ĐANG CHỜ KẾT NỐI");
                message.setText("Máy đang kết nối tới máy chủ.\nVui lòng đợi trong giây lát…");
                loading = true;
                break;
            case SERVICE_UNAVAILABLE:
                icon.setIcon(IconView.PAUSE);
                title.setText("MÁY TẠM NGƯNG PHỤC VỤ");
                message.setText("Xin lỗi quý khách vì sự bất tiện này.\nVui lòng liên hệ nhân viên cửa hàng.");
                break;
            case CLOCK_ERROR:
                icon.setIcon(IconView.CLOCK);
                title.setText("ĐỒNG HỒ MÁY BỊ LỆCH");
                message.setText("Vui lòng chỉnh lại ngày giờ hệ thống\nvà bật đồng bộ giờ tự động (NTP).");
                break;
            case KEY_NOT_RECOGNIZED:
                icon.setIcon(IconView.LOCK);
                title.setText("KHOÁ MÁY KHÔNG ĐƯỢC CÔNG NHẬN");
                message.setText("Máy cần được kiểm tra.\nVui lòng liên hệ quản trị viên.");
                showDevice = true;
                canRetry = false;
                break;
            default:
                break;
        }
        deviceLine.setText(showDevice && state.deviceCode != null ? "Mã máy: " + state.deviceCode : "");
        deviceLine.setVisibility(showDevice ? View.VISIBLE : View.GONE);
        spinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        retry.setVisibility(canRetry && !loading ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setPadding(int l, int t, int r, int b) {
        super.setPadding(l, t, r, b);
    }
}
