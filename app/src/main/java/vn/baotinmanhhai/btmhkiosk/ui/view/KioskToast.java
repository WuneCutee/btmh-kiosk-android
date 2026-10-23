package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import vn.baotinmanhhai.btmhkiosk.R;
import vn.baotinmanhhai.btmhkiosk.databinding.ViewKioskToastBinding;

/**
 * Thong bao dung chung cua ca app theo Figma "Alert" (the 343 x >=72 o goc tren-phai, nen gradient nhat + vien,
 * icon 24 + chu 16 #666666) - thay cho Toast mac dinh cua Android. Ve ngay trong cua so Activity (khong qua he
 * thong Toast: tu Android 11 Toast tuy bien bi han che), moi lan chi 1 the, the moi thay the cu.
 */
public final class KioskToast {

    public enum Type { SUCCESS, ERROR }

    private static final long SHORT_MS = 2500;
    private static final long LONG_MS = 4000;
    private static final long FADE_MS = 200;
    private static final Object TAG = new Object();

    private KioskToast() {
    }

    public static void success(Context context, CharSequence message) {
        show(context, message, Type.SUCCESS, false);
    }

    public static void error(Context context, CharSequence message) {
        show(context, message, Type.ERROR, message != null && message.length() > 60);
    }

    public static void success(Context context, int messageRes) {
        success(context, context.getString(messageRes));
    }

    public static void show(Context context, CharSequence message, Type type, boolean longDuration) {
        if (message == null || message.length() == 0) return;
        Activity activity = findActivity(context);
        if (activity == null || activity.isFinishing()) {
            Toast.makeText(context, message, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
            return;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        View old = content.findViewWithTag(TAG);
        if (old != null) content.removeView(old);

        ViewKioskToastBinding b = ViewKioskToastBinding.inflate(LayoutInflater.from(activity), content, false);
        boolean ok = type == Type.SUCCESS;
        b.getRoot().setBackgroundResource(ok ? R.drawable.bg_toast_success : R.drawable.bg_toast_error);
        b.ivIcon.setImageResource(ok ? R.drawable.ic_toast_success : R.drawable.ic_toast_error);
        b.tvMessage.setText(message);

        View card = b.getRoot();
        card.setTag(TAG);
        card.setElevation(activity.getResources().getDimension(R.dimen.toast_elevation));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                activity.getResources().getDimensionPixelSize(R.dimen.toast_min_width),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = activity.getResources().getDimensionPixelSize(R.dimen.toast_top);
        lp.setMarginEnd(activity.getResources().getDimensionPixelSize(R.dimen.toast_end));
        // Hien ngay (khong fade-in): fade-in tu alpha 0 bi ket o 0 tren may ve phan mem (the co trong cay view
        // nhung khong thay) - da gap that tren emulator swiftshader.
        content.addView(card, lp);
        card.bringToFront();
        card.setOnClickListener(v -> dismiss(content, card));
        card.postDelayed(() -> dismiss(content, card), longDuration ? LONG_MS : SHORT_MS);
    }

    private static void dismiss(ViewGroup content, View card) {
        if (card.getParent() != content) return;
        card.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> content.removeView(card)).start();
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
