package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import vn.baotinmanhhai.btmhkiosk.BuildConfig;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Man kich hoat thiet bi — giong SetupScreenControl (WinForms): the trang bo goc 28px rong 440px + bong nhe,
 * vien be 1px, can giua man hinh (nen kem). Trong the (tren xuong): logo tren vien thuoc do ruou -> tieu de "Kich hoat thiet bi" 17pt
 * -> mo ta 10pt -> nhan "CHO KICH HOAT" -> 2 o nhap (Consolas 12pt) -> thong bao loi -> nut vang -> hotline.
 * Ho tro dan nguyen chuoi JSON QR {"deviceCode":..,"activationCode":..} vao o Ma kich hoat (tu tach 2 truong).
 */
public class SetupView extends FrameLayout {

    public interface Callbacks {
        void onActivate(String deviceCode, String activationCode);

        /** Mo man quet QR (camera) — cach nhap chinh theo spec II.2.14. */
        void onScanQr();

        /** Ky thuat vien nhan giu logo de doi dia chi may chu (STAGING/DEV). */
        void onEditBaseUrl();

        /** CHI ban DEBUG: nhay thang vao man Thong tin don hang voi gio hang gia, de chinh giao dien nhanh. */
        void onDebugPreviewOrderConfirm();
    }

    private static final int CARD_WIDTH = 440;
    private static final int INNER_WIDTH = CARD_WIDTH - 64;

    private final Callbacks callbacks;
    private final EditText deviceCodeInput;
    private final EditText activationInput;
    private final TextView errorView;
    private final TextView activateButton;

    public SetupView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        setBackground(Ui.pageBackground());

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        FrameLayout centerHost = new FrameLayout(context);
        scroll.addView(centerHost, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.MATCH_PARENT));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(Ui.rect(Ui.SURFACE_WHITE, Ui.R_XL, Ui.BORDER_LIGHT, 1));
        card.setPadding(0, Ui.px(40), 0, Ui.px(30));
        Ui.elevate(card, 8, 0xFF6B4A2B);
        centerHost.addView(card, new FrameLayout.LayoutParams(
                Ui.px(CARD_WIDTH), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // Logo vang dat tren vien thuoc do ruou (vang tren nen kem khong doc duoc).
        FrameLayout logo = new FrameLayout(context);
        logo.setBackground(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0));
        logo.addView(new LogoView(context, false), new FrameLayout.LayoutParams(Ui.px(150), Ui.px(52), Gravity.CENTER));
        // II.2.14: khong co loi tat nao khac vao man Kich hoat ngoai quet QR -> doi may chu chi co o ban DEBUG.
        if (BuildConfig.DEBUG) logo.setOnLongClickListener(v -> {
            callbacks.onEditBaseUrl();
            return true;
        });
        add(card, logo, 210, 76, 22);

        TextView title = Ui.text(context, "Kích hoạt thiết bị", 17f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        title.setGravity(Gravity.CENTER);
        add(card, title, CARD_WIDTH - 40, 32, 8);

        TextView subtitle = Ui.label(context,
                BuildConfig.DEBUG ? "Nhập mã máy và mã kích hoạt do quản trị viên cấp trên WebAdmin"
                        : "Quét mã QR kích hoạt do quản trị viên cấp trên WebAdmin", 10f, Ui.TEXT_BODY_DARK, false);
        subtitle.setTypeface(Ui.typeface(Ui.W_REGULAR));
        subtitle.setLineSpacing(0, 1.2f);
        subtitle.setGravity(Gravity.CENTER);
        add(card, subtitle, CARD_WIDTH - 96, 44, 20);

        TextView pill = Ui.caps(context, "CHỜ KÍCH HOẠT", 8.5f, Ui.PRICE_ORANGE, 0.1f);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(Ui.rect(Ui.withAlpha(Ui.PRICE_ORANGE, 0x14), Ui.R_PILL, Ui.withAlpha(Ui.PRICE_ORANGE, 0x55), 1));
        pill.setPadding(Ui.px(24), 0, Ui.px(24), 0);
        card.addView(pill, margins(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.px(34), 24));

        LinearLayout scan = new LinearLayout(context);
        scan.setOrientation(LinearLayout.HORIZONTAL);
        scan.setGravity(Gravity.CENTER);
        scan.setBackground(Ui.pressable(Ui.rect(Ui.BURGUNDY_PRIMARY, Ui.R_PILL, 0, 0), Ui.rect(Ui.BURGUNDY_DARK, Ui.R_PILL, 0, 0)));
        Ui.elevate(scan, 4, Ui.BURGUNDY_PRIMARY);
        scan.addView(new IconView(context, IconView.QR, Ui.SURFACE_WHITE).strokeUnits(2.2f),
                new LinearLayout.LayoutParams(Ui.px(28), Ui.px(28)));
        TextView scanText = Ui.text(context, "QUÉT MÃ QR KÍCH HOẠT", 11f, Ui.SURFACE_WHITE, Ui.W_BOLD);
        scanText.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stLp.setMarginStart(Ui.px(12));
        scan.addView(scanText, stLp);
        scan.setOnClickListener(v -> callbacks.onScanQr());
        Ui.pressEffect(scan);
        add(card, scan, INNER_WIDTH, 64, 14);

        // II.2.14: ban RELEASE chi kich hoat bang quet QR. 2 o nhap + nut Kich hoat van duoc tao (QR dien vao do
        // roi tu bam) nhung KHONG dua len man hinh; chi ban DEBUG (emulator khong quet duoc QR) moi hien.
        final boolean manual = BuildConfig.DEBUG;
        if (manual) {
            TextView or = Ui.label(context, "hoặc nhập tay", 8.5f, Ui.TEXT_MUTED_LIGHT, false);
            or.setGravity(Gravity.CENTER);
            add(card, or, INNER_WIDTH, 20, 14);
            add(card, caption(context, "MÃ MÁY (do quản trị viên đặt)"), INNER_WIDTH, 18, 4);
        }
        deviceCodeInput = input(context, "Ví dụ: KIOSK-HN-01", true);
        if (manual) add(card, deviceCodeInput, INNER_WIDTH, 52, 16);

        if (manual) add(card, caption(context, "MÃ KÍCH HOẠT (hoặc scan QR)"), INNER_WIDTH, 18, 4);
        // KHONG ep in hoa luc go (khac o Ma may): neu dan nguyen JSON qrPayload ma ep hoa ten field
        // ("deviceCode" -> "DEVICECODE") thi khong con tach duoc. Chi ep hoa 1 lan luc bam Kich hoat.
        activationInput = input(context, "12 ký tự, ví dụ: N7A42QNR7YOZ", false);
        activationInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tryAutoFillFromQrPayload(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        if (manual) add(card, activationInput, INNER_WIDTH, 52, 8);

        // INVISIBLE (khong phai GONE) de giu nguyen vi tri cac phan tu duoi nhu ban WinForms.
        errorView = Ui.text(context, "", 8.5f, Ui.DOWN_RED, Ui.W_BOLD);
        errorView.setGravity(Gravity.CENTER);
        errorView.setVisibility(INVISIBLE);
        add(card, errorView, INNER_WIDTH, 34, 4);

        activateButton = Ui.primaryButton(context, "KÍCH HOẠT MÁY", 11f);
        activateButton.setOnClickListener(v -> submit());
        if (manual) add(card, activateButton, INNER_WIDTH, 60, 22);

        TextView footer = Ui.text(context, "Hotline hỗ trợ: 1900 xxxx", 8.5f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        footer.setGravity(Gravity.CENTER);
        add(card, footer, CARD_WIDTH - 40, 20, BuildConfig.DEBUG ? 14 : 0);

        // Loi tat rieng cho dev: nhay thang vao man "Thong tin don hang" voi gio hang gia, khong can
        // kich hoat/dang nhap/them san pham. Dung de chinh kich thuoc/vi tri roi build lai xem ngay.
        if (BuildConfig.DEBUG) {
            TextView previewLink = Ui.text(context, "DEBUG: Xem màn Thông tin đơn hàng", 8.5f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
            previewLink.setGravity(Gravity.CENTER);
            previewLink.setPadding(0, Ui.px(10), 0, 0);
            previewLink.setClickable(true);
            Ui.pressEffect(previewLink);
            previewLink.setOnClickListener(v -> callbacks.onDebugPreviewOrderConfirm());
            add(card, previewLink, CARD_WIDTH - 40, 20, 0);
        }
    }

    private static LinearLayout.LayoutParams margins(int width, int height, float bottomDesign) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
        lp.bottomMargin = Ui.px(bottomDesign);
        return lp;
    }

    private static void add(LinearLayout card, android.view.View view, float w, float h, float bottomDesign) {
        card.addView(view, margins(Ui.px(w), Ui.px(h), bottomDesign));
    }

    private static TextView caption(Context context, String text) {
        TextView view = Ui.caps(context, text, 7.5f, Ui.BURGUNDY_PRIMARY, 0.06f);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        return view;
    }

    private static EditText input(Context context, String hint, boolean upperCase) {
        EditText edit = new EditText(context);
        edit.setHint(hint);
        edit.setHintTextColor(Ui.TEXT_MUTED_LIGHT);
        edit.setTextColor(Ui.TEXT_TITLE_DARK);
        edit.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.textPx(12f));
        edit.setTypeface(Typeface.MONOSPACE);
        edit.setGravity(Gravity.CENTER);
        edit.setSingleLine(true);
        edit.setIncludeFontPadding(false);
        edit.setPadding(Ui.px(8), 0, Ui.px(8), 0);
        edit.setBackground(Ui.rect(Ui.SURFACE_WHITE, Ui.R_MD, Ui.BORDER_LIGHT, 1));
        edit.setOnFocusChangeListener((v, focused) -> v.setBackground(
                Ui.rect(Ui.SURFACE_WHITE, Ui.R_MD, focused ? Ui.BURGUNDY_PRIMARY : Ui.BORDER_LIGHT, focused ? 1.5f : 1)));
        int type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (upperCase) {
            type |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
            edit.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
        }
        edit.setInputType(type);
        return edit;
    }

    /** Ket qua quet QR: tach 2 truong roi tu bam Kich hoat (QR chua ma may + ma kich hoat). */
    public void onQrScanned(String payload) {
        tryAutoFillFromQrPayload(payload);
        if (!deviceCodeInput.getText().toString().trim().isEmpty()
                && !activationInput.getText().toString().trim().isEmpty()
                && !activationInput.getText().toString().trim().startsWith("{")) {
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
                deviceCodeInput.setText(obj.get("deviceCode").getAsString());
                String code = obj.get("activationCode").getAsString();
                // Gia tri moi khong con bat dau bang '{' nen TextWatcher tu chan de quy.
                activationInput.setText(code);
                activationInput.setSelection(code.length());
            }
        } catch (Exception ignored) {
            // Chua go xong JSON hoac khong hop le -> cho nguoi dung go tiep.
        }
    }

    private void submit() {
        String deviceCode = deviceCodeInput.getText().toString().trim();
        String activationCode = activationInput.getText().toString().trim().toUpperCase();
        if (deviceCode.isEmpty() || activationCode.isEmpty()) {
            showError("Vui lòng nhập đủ Mã máy và Mã kích hoạt.");
            return;
        }
        showError(null);
        callbacks.onActivate(deviceCode, activationCode);
    }

    private void showError(String message) {
        errorView.setText(message == null ? "" : message);
        errorView.setVisibility(message == null ? INVISIBLE : VISIBLE);
    }

    /** Dong bo trang thai tu ViewModel: dang kich hoat (khoa nut/o nhap) va thong bao loi tu server. */
    public void bind(boolean activating, String error) {
        activateButton.setEnabled(!activating);
        activateButton.setText(activating ? "ĐANG KÍCH HOẠT..." : "KÍCH HOẠT MÁY");
        deviceCodeInput.setEnabled(!activating);
        activationInput.setEnabled(!activating);
        if (error != null || !activating) {
            if (error != null || errorView.getVisibility() == VISIBLE) showError(error);
        }
    }
}
