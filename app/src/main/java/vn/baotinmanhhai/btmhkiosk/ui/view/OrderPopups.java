package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;

import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.databinding.DialogOrderNoticeBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.DialogRatingBinding;

/**
 * Ban rut gon cua OrderDialogs.notice()/purchaseLimitMessage()/outOfStockMessage() cua app chuan, dung chung
 * cho popup VI/VII khi chi co DUNG 1 san pham lien quan (vd bam (+) tren the san pham/man Chi tiet ngay luc
 * da cham tran). OrderConfirmView.java van giu ban rieng cho truong hop NHIEU dong gio hang cung luc (luc bam
 * "DAT MUA") vi can gop nhieu san pham vao 1 popup - khong dung lop nay o do de khoi doi kien truc da on dinh.
 */
public final class OrderPopups {
    private OrderPopups() {
    }

    /**
     * Theo doi dialog/bottom-sheet DANG MO gan nhat trong toan app (popup han muc/het ton/gia thay doi,
     * picker Tinh-Thanh/Cua hang...) - de KioskActivity tu dong dong lai khi may ve man hinh cho (idle),
     * khong de popup "treo" tren man hinh quang cao trong khi khong co khach nao con tuong tac (yeu cau
     * 15/10/2026). Moi noi mo Dialog/BottomSheetDialog trong app nen goi track(dialog) ngay truoc luc show().
     */
    private static Dialog activeDialog;

    /** Lop nen toi sau popup - dat muc nay bang MA chu KHONG chi dua vao android:backgroundDimAmount trong
     * style: da kiem qua dumpsys window thuc te tren emulator, Theme.Material3.DayNight.Dialog KHONG tu bat
     * co FLAG_DIM_BEHIND nhu ky vong du style co khai bao backgroundDimAmount - phai tu bat co nay + dat dim
     * truc tiep tren Window thi moi chac chan co hieu luc (yeu cau 15/10/2026, "nen van chua toi"). */
    private static final float DIM_AMOUNT = 0.82f;

    public static void track(Dialog dialog) {
        if (activeDialog != null && activeDialog.isShowing() && activeDialog != dialog) activeDialog.dismiss();
        activeDialog = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(DIM_AMOUNT);
        }
        dialog.setOnDismissListener(d -> {
            if (activeDialog == d) activeDialog = null;
        });
    }

    /** KioskActivity.showIdle() goi ham nay: dong het popup/sheet dang mo, khong de treo tren man hinh cho. */
    public static void dismissActive() {
        if (activeDialog != null && activeDialog.isShowing()) activeDialog.dismiss();
        activeDialog = null;
    }

    /**
     * Popup VI "Nhap qua so luong san pham co the mua" (Figma 917:40180) cho DUNG 1 san pham - dung khi bam
     * (+) ma da dung tran han muc (truoc day im lang khoa nut, gio bao ro ly do theo yeu cau 15/10/2026).
     */
    public static void showPurchaseLimit(Context context, double limitChi, String name, int maxCount,
                                          Runnable onAdjustToMax, Runnable onOther) {
        DialogOrderNoticeBinding db = DialogOrderNoticeBinding.inflate(LayoutInflater.from(context));
        Dialog dialog = new Dialog(context, R.style.OrderDialog);
        dialog.setContentView(db.getRoot());

        db.tvTitle.setText(R.string.dialog_notice);
        CharSequence message = TextUtils.expandTemplate(
                context.getString(R.string.dialog_purchase_limit),
                appendBold(new SpannableStringBuilder(), formatChi(limitChi)),
                appendBold(new SpannableStringBuilder(), name == null ? "" : name),
                appendBold(new SpannableStringBuilder(), String.valueOf(maxCount)));
        db.tvMessage.setText(message);

        db.btnSecondary.setText(R.string.dialog_other_products);
        db.btnPrimary.setText(R.string.dialog_adjust_count);
        db.btnClose.setOnClickListener(v -> dialog.dismiss());
        db.btnSecondary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onOther != null) onOther.run();
        });
        db.btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onAdjustToMax != null) onAdjustToMax.run();
        });
        showKiosk(dialog);
    }

    /** Popup VII "SL mua > SL hien co" cho DUNG 1 san pham - dung khi bam (+) ma da dung tran ton kho. */
    public static void showOutOfStock(Context context, String name, int available,
                                       Runnable onAdjustToStock, Runnable onOther) {
        DialogOrderNoticeBinding db = DialogOrderNoticeBinding.inflate(LayoutInflater.from(context));
        Dialog dialog = new Dialog(context, R.style.OrderDialog);
        dialog.setContentView(db.getRoot());

        db.tvTitle.setText(R.string.dialog_notice);
        SpannableStringBuilder msg = new SpannableStringBuilder();
        msg.append(context.getString(R.string.dialog_out_of_stock_head));
        msg.append('\n');
        String safeName = name == null ? "" : name;
        String line = context.getString(R.string.dialog_out_of_stock_line, available, safeName);
        int start = msg.length();
        msg.append(line);
        String count = String.valueOf(available);
        int countAt = line.indexOf(count);
        if (countAt >= 0) msg.setSpan(new StyleSpan(Typeface.BOLD), start + countAt, start + countAt + count.length(), 0);
        if (!safeName.isEmpty()) {
            int nameAt = line.lastIndexOf(safeName);
            if (nameAt >= 0) msg.setSpan(new StyleSpan(Typeface.BOLD), start + nameAt, start + nameAt + safeName.length(), 0);
        }
        msg.append('\n').append(context.getString(R.string.dialog_out_of_stock_tail));
        db.tvMessage.setText(msg);

        db.btnSecondary.setText(R.string.dialog_other_products);
        db.btnPrimary.setText(R.string.dialog_adjust_count);
        db.btnClose.setOnClickListener(v -> dialog.dismiss());
        db.btnSecondary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onOther != null) onOther.run();
        });
        db.btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onAdjustToStock != null) onAdjustToStock.run();
        });
        showKiosk(dialog);
    }

    /**
     * Popup II "Xoa san pham nay?" (Figma "Popup xac nhan bo chon hang") - bam X tren dong gio hang o man
     * Thong tin don hang. HUY/X/bam ra ngoai: giu nguyen gio; DONG Y: bo san pham khoi gio.
     */
    public static void showRemoveConfirm(Context context, Runnable onConfirm) {
        DialogOrderNoticeBinding db = DialogOrderNoticeBinding.inflate(LayoutInflater.from(context));
        Dialog dialog = new Dialog(context, R.style.OrderDialog);
        dialog.setContentView(db.getRoot());

        db.tvTitle.setText(R.string.dialog_remove_title);
        db.tvMessage.setText(R.string.dialog_remove_message);
        db.btnSecondary.setText(R.string.dialog_cancel);
        db.btnPrimary.setText(R.string.dialog_agree);
        db.btnClose.setOnClickListener(v -> dialog.dismiss());
        db.btnSecondary.setOnClickListener(v -> dialog.dismiss());
        db.btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.run();
        });
        showKiosk(dialog);
    }

    /** Gui danh gia: so sao (1-5) va gop y (co the rong). */
    public interface RatingListener {
        void onSubmit(int stars, String comment);
    }

    private static final int STAR_ON = 0xFFFFCC00;
    private static final int STAR_OFF = 0xFFF2F2F2;

    /**
     * Popup danh gia dich vu (Figma "Man hinh ket thuc thanh toan"): mo khi khach cham sao o man ket qua giao
     * dich, san so sao vua cham; khach doi duoc so sao, nhap gop y roi bam GUI. X / bam ra ngoai: dong, khong gui.
     */
    public static void showRating(Context context, int initialStars, RatingListener listener) {
        DialogRatingBinding db = DialogRatingBinding.inflate(LayoutInflater.from(context));
        Dialog dialog = new Dialog(context, R.style.OrderDialog);
        dialog.setContentView(db.getRoot());
        // Figma HEADER/T18-Semibold (Plus Jakarta Sans 600): font variable - thuoc tinh XML khong an, dat bang code.
        db.tvTitle.setFontVariationSettings("'wght' 600");
        // Khong tu bat ban phim khi mo popup: chi mo khi khach cham vao o gop y.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }

        android.widget.ImageView[] stars = {db.star1, db.star2, db.star3, db.star4, db.star5};
        int[] rating = {Math.max(1, Math.min(5, initialStars))};
        Runnable paint = () -> {
            for (int i = 0; i < stars.length; i++) {
                stars[i].setImageTintList(android.content.res.ColorStateList.valueOf(i < rating[0] ? STAR_ON : STAR_OFF));
            }
        };
        for (int i = 0; i < stars.length; i++) {
            final int value = i + 1;
            stars[i].setContentDescription(context.getString(R.string.result_rate_star, value));
            stars[i].setOnClickListener(v -> {
                rating[0] = value;
                paint.run();
            });
        }
        paint.run();

        db.btnClose.setOnClickListener(v -> dialog.dismiss());
        db.btnSubmit.setOnClickListener(v -> {
            String comment = db.etComment.getText() == null ? "" : db.etComment.getText().toString().trim();
            dialog.dismiss();
            if (listener != null) listener.onSubmit(rating[0], comment);
        });
        showKiosk(dialog);
        // Theme dialog tu ep cua so hep (~308dp) -> tieu de xuong dong, nut GUI bi cat: dat dung 470dp theo Figma.
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(context.getResources().getDimensionPixelSize(R.dimen.rating_dialog_width),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    /** showKiosk() that: man kiosk an thanh he thong, dialog co Window RIENG nen phai tu an lai. */
    private static void showKiosk(Dialog dialog) {
        track(dialog);
        dialog.show();
        if (dialog.getWindow() != null) {
            WindowInsetsControllerCompat controller =
                    new WindowInsetsControllerCompat(dialog.getWindow(), dialog.getWindow().getDecorView());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }
    }

    private static SpannableStringBuilder appendBold(SpannableStringBuilder sb, CharSequence text) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(new StyleSpan(Typeface.BOLD), start, sb.length(), 0);
        return sb;
    }

    /** Formats.chi() that: 3.00 -> "3", 0.50 -> "0.5". */
    private static String formatChi(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
