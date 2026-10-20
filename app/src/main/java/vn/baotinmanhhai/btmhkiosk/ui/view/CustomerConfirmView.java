package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.regex.Pattern;

import vn.baotinmanhhai.btmhkiosk.databinding.ActivityFaceAuthInfoBinding;
import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * "Xac nhan thong tin khach hang" (S07). Doi tu ban ve tay Canvas sang inflate dung activity_face_auth_info.xml
 * that qua ViewBinding de khop pixel voi app chuan. Header tu ve rieng (giong OrderConfirmView/ShellView an header
 * dung chung cho man nay); binding.header (FrameLayout rong, thay cho FragmentContainerView/HeaderFragment khong
 * ton tai trong du an nay) bi an.
 */
public class CustomerConfirmView extends LinearLayout implements Ui.InsetAware {

    public interface Callbacks {
        void onBack();
        void onGoHome();
        /** "HOAN THANH": sdt/email da hop le. */
        void onDone(String phone, String email);
    }

    // SRS "Xac nhan thong tin KH" muc 7/8 (Validators.kt that): sdt VN 10 so bat dau 0[3,5,7,8,9]; email tuy chon,
    // rong la hop le, neu nhap thi phai dung dinh dang chuan.
    private static final Pattern PHONE_PATTERN = Pattern.compile("^0[35789]\\d{8}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

    private final Callbacks callbacks;
    private final ActivityFaceAuthInfoBinding binding;
    private final LinearLayout topBar;
    private boolean suppressWatchers;

    public CustomerConfirmView(Context context, Callbacks callbacks) {
        super(context);
        this.callbacks = callbacks;
        setOrientation(VERTICAL);
        setBackground(Ui.pageBackground());
        setClickable(true);

        // ================= HEADER (tu ve, giong OrderConfirmView) =================
        topBar = new LinearLayout(context);
        topBar.setOrientation(HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Ui.px(20), Ui.px(32), Ui.px(32), Ui.px(8));

        FrameLayout backBtn = new FrameLayout(context);
        backBtn.setClickable(true);
        Ui.pressEffect(backBtn);
        backBtn.setOnClickListener(v -> {
            if (callbacks != null) callbacks.onBack();
        });
        IconView backIcon = new IconView(context, IconView.CHEVRON_LEFT, Color.BLACK).strokeUnits(2.2f);
        backBtn.addView(backIcon, new FrameLayout.LayoutParams(Ui.px(24), Ui.px(24), Gravity.CENTER));
        topBar.addView(backBtn, new LayoutParams(Ui.px(48), Ui.px(48)));

        TextView titleBar = Ui.text(context, "Xác nhận thông tin khách hàng", 20f, Color.BLACK, Ui.W_MEDIUM);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setIncludeFontPadding(false);
        titleBar.setLetterSpacing(-0.02f);
        titleBar.setMaxLines(2);
        LayoutParams titleLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMarginStart(Ui.px(8));
        topBar.addView(titleBar, titleLp);

        FrameLayout homeBtn = new FrameLayout(context);
        homeBtn.setClickable(true);
        Ui.pressEffect(homeBtn);
        homeBtn.setOnClickListener(v -> {
            if (callbacks != null) callbacks.onGoHome();
        });
        IconView homeIcon = new IconView(context, IconView.HOME_OUTLINE, Ui.BURGUNDY_PRIMARY).strokeUnits(2.0f);
        homeBtn.addView(homeIcon, new FrameLayout.LayoutParams(Ui.px(34), Ui.px(34), Gravity.CENTER));
        topBar.addView(homeBtn, new LayoutParams(Ui.px(66), Ui.px(66)));

        addView(topBar, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // ================= NOI DUNG THAT (activity_face_auth_info.xml) =================
        binding = ActivityFaceAuthInfoBinding.inflate(LayoutInflater.from(context), null, false);
        binding.header.setVisibility(GONE);
        addView(binding.getRoot(), new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        binding.etPhone.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        binding.etPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!suppressWatchers) binding.tvPhoneError.setVisibility(GONE);
            }
        });

        binding.etEmail.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        binding.btnClearEmail.setOnClickListener(v -> binding.etEmail.setText(""));
        binding.etEmail.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                binding.btnClearEmail.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                if (!suppressWatchers) binding.tvEmailError.setVisibility(GONE);
            }
        });

        binding.btnDone.setOnClickListener(v -> submit());
    }

    private void submit() {
        String phone = binding.etPhone.getText().toString().trim();
        String email = binding.etEmail.getText().toString().trim();
        boolean phoneOk = PHONE_PATTERN.matcher(phone).matches();
        boolean emailOk = email.isEmpty() || EMAIL_PATTERN.matcher(email).matches();
        binding.tvPhoneError.setVisibility(phoneOk ? GONE : VISIBLE);
        binding.tvEmailError.setVisibility(emailOk ? GONE : VISIBLE);
        if (!phoneOk || !emailOk) return;
        if (callbacks != null) callbacks.onDone(phone, email);
    }

    /** Dien thong tin ho so (chi doc) + so dien thoai/email dang co (de khach sua tiep neu can). */
    public void bind(String name, String idNumber, String dob, String address, String phone, String email) {
        binding.tvReceiver.setText(orDash(name));
        binding.tvIdNumber.setText(orDash(idNumber));
        binding.tvDob.setText(orDash(dob));
        binding.tvAddress.setText(orDash(address));
        suppressWatchers = true;
        binding.etPhone.setText(phone == null ? "" : phone);
        binding.etEmail.setText(email == null ? "" : email);
        suppressWatchers = false;
        binding.tvPhoneError.setVisibility(GONE);
        binding.tvEmailError.setVisibility(GONE);
        binding.btnClearEmail.setVisibility(binding.etEmail.getText().length() > 0 ? VISIBLE : GONE);
    }

    private static String orDash(String s) {
        return s == null || s.trim().isEmpty() ? "—" : s;
    }

    @Override
    public void onInsets(int l, int t, int r, int b) {
        setPadding(l, 0, r, 0);
        topBar.setPadding(Ui.px(20), Ui.px(32) + t, Ui.px(32), Ui.px(8));
        binding.scroll.setPadding(binding.scroll.getPaddingLeft(), binding.scroll.getPaddingTop(),
                binding.scroll.getPaddingRight(), binding.scroll.getPaddingBottom() + b);
    }
}
