package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.widget.LinearLayoutCompat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import vn.baotinmanhhai.btmhkiosk.BuildConfig;
import vn.baotinmanhhai.btmhkiosk.databinding.ActivityActivationBinding;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man kich hoat thiet bi — inflate truc tiep activity_activation.xml that (API Kiosk II.2.14, phuong an A2,
 * docs/kiosk/phuong-an-man-kich-hoat.html). Them 1 FrameLayout rong (+id/btnScanQr, KHONG co trong file that) lot
 * trong o "Ma kich hoat" de gan loi tat quet QR bang camera (ScanOptions/zxing) cho may/emulator khong co dau doc
 * QR vat ly — xem ghi chu trong layout.
 */
public class SetupView extends android.widget.FrameLayout {

    public interface Callbacks {
        void onActivate(String deviceCode, String activationCode);

        /** Mo man quet QR (camera) — khong co nut rieng tren man that (chi may quet vat ly), giu lam loi tat rieng
         * cho emulator/thiet bi khong co dau doc QR vat ly: gan vao nut btnScanQr them vao layout. */
        void onScanQr();

        /** Ky thuat vien nhan giu logo de doi dia chi may chu (STAGING/DEV) — chi ban DEBUG. */
        void onEditBaseUrl();

        /** CHI ban DEBUG: nhay thang vao man Thong tin don hang voi gio hang gia, de chinh giao dien nhanh. */
        void onDebugPreviewOrderConfirm();
    }

    /** Bang ky tu ma kich hoat that (II.2.14/ActivationInput.kt): khong co I, L, O, U — O->0, I/L->1 khi go nham. */
    private static final String CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private final Callbacks callbacks;
    private final ActivityActivationBinding binding;
    private boolean normalizing;

    public SetupView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        binding = ActivityActivationBinding.inflate(LayoutInflater.from(context), this, true);

        if (BuildConfig.DEBUG) {
            // NestedScrollView root -> LinearLayoutCompat (cot) -> [logo, card, Space, footer]; logo la child 0.
            LinearLayoutCompat column = (LinearLayoutCompat) binding.getRoot().getChildAt(0);
            android.view.View logo = column.getChildAt(0);
            logo.setClickable(true);
            logo.setOnLongClickListener(v -> {
                callbacks.onEditBaseUrl();
                return true;
            });

            TextView previewLink = Ui.text(context, "DEBUG: Xem màn Thông tin đơn hàng", 10f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
            previewLink.setGravity(Gravity.CENTER);
            previewLink.setClickable(true);
            Ui.pressEffect(previewLink);
            previewLink.setOnClickListener(v -> callbacks.onDebugPreviewOrderConfirm());
            LinearLayoutCompat.LayoutParams linkLp = new LinearLayoutCompat.LayoutParams(
                    LinearLayoutCompat.LayoutParams.MATCH_PARENT, LinearLayoutCompat.LayoutParams.WRAP_CONTENT);
            linkLp.setMargins(Ui.px(34), Ui.px(8), Ui.px(34), Ui.px(16));
            column.addView(previewLink, linkLp);
        }

        // Nut quet QR (them, khong co trong file that) lot trong o Ma kich hoat.
        binding.btnScanQr.setClickable(true);
        Ui.pressEffect(binding.btnScanQr);
        binding.btnScanQr.addView(new IconView(context, IconView.QR, Ui.BURGUNDY_PRIMARY).strokeUnits(1.8f),
                new android.widget.FrameLayout.LayoutParams(Ui.px(22), Ui.px(22), Gravity.CENTER));
        binding.btnScanQr.setOnClickListener(v -> callbacks.onScanQr());

        binding.btnActivate.setOnClickListener(v -> submit());

        binding.etDeviceCode.addTextChangedListener(normalizer(binding.etDeviceCode, SetupView::normalizeDeviceCode));
        binding.etActivationCode.addTextChangedListener(normalizer(binding.etActivationCode, SetupView::normalizeActivationCode));
        binding.etActivationCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                updateButtonState();
            }
        });
        binding.etDeviceCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                tryAutoFillFromQrPayload(s.toString());
                updateButtonState();
            }
        });

        binding.tvDeviceInfo.setText(footerText());
    }

    private String footerText() {
        return "Phiên bản " + BuildConfig.VERSION_NAME + " · " + Build.MODEL;
    }

    /** Loc tu dong giong ActivationInput.deviceCode() that: in hoa, chi giu chu/so/gach ngang/gach duoi/cham. */
    private static String normalizeDeviceCode(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toUpperCase().toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') sb.append(c);
        }
        return sb.toString();
    }

    /** Loc tu dong giong ActivationInput.activationCode() that: in hoa, O->0, I/L->1, chi giu bang ma 32 ky tu. */
    private static String normalizeActivationCode(String raw) {
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toUpperCase().toCharArray()) {
            char mapped = c;
            if (c == 'O') mapped = '0';
            else if (c == 'I' || c == 'L') mapped = '1';
            if (CODE_ALPHABET.indexOf(mapped) >= 0) sb.append(mapped);
        }
        return sb.toString();
    }

    private interface Normalize {
        String apply(String raw);
    }

    private TextWatcher normalizer(EditText edit, Normalize fn) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (normalizing) return;
                String normalized = fn.apply(s.toString());
                if (!normalized.equals(s.toString())) {
                    normalizing = true;
                    edit.setText(normalized);
                    edit.setSelection(normalized.length());
                    normalizing = false;
                }
            }
        };
    }

    private void updateButtonState() {
        boolean ready = !binding.etDeviceCode.getText().toString().trim().isEmpty()
                && !binding.etActivationCode.getText().toString().trim().isEmpty();
        binding.btnActivate.setEnabled(ready); // mau nut/chu doi theo list_next_bg.xml/list_next_text.xml (state_enabled)
    }

    /** Ket qua quet QR: tach 2 truong roi tu bam Kich hoat (QR chua ma may + ma kich hoat). */
    public void onQrScanned(String payload) {
        tryAutoFillFromQrPayload(payload);
        if (!binding.etDeviceCode.getText().toString().trim().isEmpty()
                && !binding.etActivationCode.getText().toString().trim().isEmpty()
                && !binding.etActivationCode.getText().toString().trim().startsWith("{")) {
            submit();
        } else {
            showError("Mã QR không hợp lệ. Vui lòng quét đúng mã do quản trị viên cấp.");
        }
    }

    private void tryAutoFillFromQrPayload(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) return;
        try {
            JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
            if (obj.has("deviceCode") && obj.has("activationCode")) {
                normalizing = true;
                binding.etDeviceCode.setText(normalizeDeviceCode(obj.get("deviceCode").getAsString()));
                normalizing = false;
                String code = normalizeActivationCode(obj.get("activationCode").getAsString());
                binding.etActivationCode.setText(code);
                binding.etActivationCode.setSelection(code.length());
            }
        } catch (Exception ignored) {
            // Chua go xong JSON hoac khong hop le -> cho nguoi dung go tiep.
        }
    }

    private void submit() {
        String deviceCode = binding.etDeviceCode.getText().toString().trim();
        String activationCode = binding.etActivationCode.getText().toString().trim();
        if (deviceCode.isEmpty() || activationCode.isEmpty()) return; // nut da khoa khi thieu truong
        showError(null);
        callbacks.onActivate(deviceCode, activationCode);
    }

    private void showError(String message) {
        binding.tvError.setText(message == null ? "" : message);
        binding.tvError.setVisibility(message == null ? GONE : VISIBLE);
    }

    /** Dong bo trang thai tu ViewModel: dang kich hoat (khoa nut/o nhap) va thong bao loi tu server. */
    public void bind(boolean activating, String error) {
        binding.btnActivate.setClickable(!activating);
        binding.etDeviceCode.setEnabled(!activating);
        binding.etActivationCode.setEnabled(!activating);
        if (error != null || !activating) {
            if (error != null || binding.tvError.getVisibility() == VISIBLE) showError(error);
        }
    }
}
