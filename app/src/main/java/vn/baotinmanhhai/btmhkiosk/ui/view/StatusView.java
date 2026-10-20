package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.widget.LinearLayoutCompat;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.databinding.ActivityRevokedBinding;
import vn.baotinmanhhai.btmhkiosk.ui.KioskViewModel;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man thong bao trang thai may phu len tren (spec II.2.1, II.2.12): "Can cap nhat", "Dang cho ket noi", "May tam
 * ngung phuc vu", loi dong ho, khoa khong duoc cong nhan. KEY_NOT_RECOGNIZED (ma loi 104) inflate truc tiep
 * activity_revoked.xml that (logo + the trang bo goc + ic_revoked_lock 56dp + StatusTitle 22sp + ActivationSubtitle
 * 16sp + dong "Ma may: xxx" an duoc) — LUU Y: khac voi device-revoked (ma 121) di qua SetupView voi thong bao loi
 * rieng, KHONG qua man nay (xem KioskViewModel dong ~608 va KioskActivity). Cac trang thai khac (NEEDS_UPDATE,
 * WAITING_CONNECTION, SERVICE_UNAVAILABLE, CLOCK_ERROR) KHONG co man rieng trong app that (chi suy ra tu SRS) nen
 * dung chung khung the nay, them spinner/hotline/nut "THU LAI" (KHONG co trong activity_revoked.xml that) truc
 * tiep vao card luc runtime cho cac trang thai do.
 */
public class StatusView extends FrameLayout {

    public interface Callbacks {
        void onRetry();
    }

    private final ActivityRevokedBinding binding;
    private final LinearLayoutCompat card;
    private final TextView title;
    private final TextView message;
    private final IconView icon;
    private final ProgressBar spinner;
    private final TextView hotlineLine;
    private final TextView retry;

    public StatusView(Context context, Callbacks callbacks) {
        super(context);
        setClickable(true); // chan cham xuyen xuong lop duoi
        binding = ActivityRevokedBinding.inflate(LayoutInflater.from(context), this, true);

        // root (LinearLayoutCompat) -> [0]=logo ImageView, [1]=MaterialCardView -> [0]=card noi dung that.
        card = (LinearLayoutCompat) ((android.view.ViewGroup) binding.getRoot().getChildAt(1)).getChildAt(0);
        // card -> [0]=icon ic_revoked_lock (ImageView tinh), [1]=title, [2]=message, [3]=groupDeviceCode.
        title = (TextView) card.getChildAt(1);
        message = (TextView) card.getChildAt(2);

        // Thay ImageView tinh bang IconView de doi duoc theo tung trang thai (UPDATE/WIFI_OFF/PAUSE/CLOCK/LOCK).
        View staticIcon = card.getChildAt(0);
        LinearLayoutCompat.LayoutParams iconLp = (LinearLayoutCompat.LayoutParams) staticIcon.getLayoutParams();
        card.removeViewAt(0);
        FrameLayout iconBg = new FrameLayout(context);
        iconBg.setBackground(Ui.oval(0xFFFBEAEA)); // status_icon_bg
        icon = new IconView(context, IconView.CLOCK, Ui.BURGUNDY_PRIMARY).strokeUnits(2.2f);
        iconBg.addView(icon, new FrameLayout.LayoutParams(Ui.px(28), Ui.px(28), Gravity.CENTER));
        card.addView(iconBg, 0, iconLp);

        // Them: spinner (WAITING_CONNECTION), dong hotline, nut "THU LAI" — khong co trong activity_revoked.xml
        // that (man that la trang thai cuoi, khong the thu lai); dat ngay sau groupDeviceCode trong cung card.
        spinner = new ProgressBar(context);
        spinner.getIndeterminateDrawable().setColorFilter(Ui.BURGUNDY_PRIMARY, PorterDuff.Mode.SRC_IN);
        LinearLayoutCompat.LayoutParams spinnerLp = new LinearLayoutCompat.LayoutParams(Ui.px(36), Ui.px(36));
        spinnerLp.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerLp.topMargin = Ui.px(16);
        card.addView(spinner, spinnerLp);

        hotlineLine = Ui.text(context, "", 14f, 0xFF4F4F4F, Ui.W_REGULAR); // text_secondary
        hotlineLine.setGravity(Gravity.CENTER);
        LinearLayoutCompat.LayoutParams hotlineLp = new LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT);
        hotlineLp.topMargin = Ui.px(16);
        card.addView(hotlineLine, hotlineLp);

        retry = Ui.text(context, "THỬ LẠI", 18f, 0xFFFFFBF4, Ui.W_MEDIUM);
        retry.setGravity(Gravity.CENTER);
        retry.setBackground(Ui.rect(Ui.BURGUNDY_PRIMARY, 28, 0, 0)); // home_button_radius
        retry.setOnClickListener(v -> callbacks.onRetry());
        LinearLayoutCompat.LayoutParams retryLp = new LinearLayoutCompat.LayoutParams(
                LinearLayoutCompat.LayoutParams.MATCH_PARENT, Ui.px(56)); // home_button_height
        retryLp.topMargin = Ui.px(24); // act_button_top
        card.addView(retry, retryLp);
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
                title.setText("Cần cập nhật ứng dụng");
                message.setText("Phiên bản ứng dụng trên máy đã cũ. Vui lòng liên hệ nhân viên để được cập nhật.");
                canRetry = false;
                break;
            case WAITING_CONNECTION:
                icon.setIcon(IconView.WIFI_OFF);
                title.setText("Đang chờ kết nối");
                message.setText("Máy đang kết nối tới máy chủ. Vui lòng đợi trong giây lát…");
                loading = true;
                break;
            case SERVICE_UNAVAILABLE:
                icon.setIcon(IconView.PAUSE);
                title.setText("Máy tạm ngưng phục vụ");
                message.setText("Xin lỗi quý khách vì sự bất tiện này. Vui lòng liên hệ nhân viên cửa hàng.");
                break;
            case CLOCK_ERROR:
                icon.setIcon(IconView.CLOCK);
                title.setText("Đồng hồ máy bị lệch");
                message.setText("Vui lòng chỉnh lại ngày giờ hệ thống và bật đồng bộ giờ tự động (NTP).");
                break;
            case KEY_NOT_RECOGNIZED:
                // revoked_title/revoked_message that (activity_revoked.xml) - dung nguyen chu that.
                icon.setIcon(IconView.LOCK);
                title.setText(R.string.revoked_title);
                message.setText(R.string.revoked_message);
                showDevice = true;
                canRetry = false;
                break;
            default:
                break;
        }
        binding.tvDeviceCode.setText(showDevice && state.deviceCode != null ? state.deviceCode : "");
        binding.groupDeviceCode.setVisibility(showDevice ? View.VISIBLE : View.GONE);
        spinner.setVisibility(loading ? View.VISIBLE : View.GONE);
        retry.setVisibility(canRetry && !loading ? View.VISIBLE : View.GONE);
    }
}
