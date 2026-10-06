package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.Gravity;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import vn.baotinmanhhai.btmhkiosk.ui.Ui;

/**
 * Mo website tu quang cao (actionType = WEBSITE, spec II.2.2) NGAY TRONG App — khong bat trinh duyet ngoai
 * de khach khong thoat khoi kiosk. Chi cho phep https (WebAdmin cung chi cho nhap https://); nut "Dong" ve lai.
 */
public class WebsiteView extends FrameLayout {

    public interface Callbacks {
        void onClose();
    }

    private final WebView web;
    private final TextView hostText;
    private final ProgressBar progress;
    private String loadedUrl;

    @SuppressLint("SetJavaScriptEnabled")
    public WebsiteView(Context context, Callbacks callbacks) {
        super(context);
        setBackground(Ui.pageBackground());
        setClickable(true);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.SURFACE_WHITE);
        Ui.elevate(bar, 2, 0xFF6B4A2B);
        bar.setPadding(Ui.px(16), Ui.px(8), Ui.px(20), Ui.px(8));
        LinearLayout close = new LinearLayout(context);
        close.setOrientation(LinearLayout.HORIZONTAL);
        close.setGravity(Gravity.CENTER_VERTICAL);
        close.setBackground(Ui.pressable(Ui.outline(Ui.R_PILL), Ui.rect(Ui.PAGE_BG_LIGHT, Ui.R_PILL, Ui.BORDER_LIGHT, 1)));
        close.setPadding(Ui.px(20), 0, Ui.px(26), 0);
        close.addView(new IconView(context, IconView.CLOSE, Ui.BURGUNDY_PRIMARY).strokeUnits(2.4f),
                new LinearLayout.LayoutParams(Ui.px(24), Ui.px(24)));
        TextView closeText = Ui.text(context, "Đóng", 11f, Ui.BURGUNDY_PRIMARY, Ui.W_BOLD);
        LinearLayout.LayoutParams ctLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ctLp.setMarginStart(Ui.px(10));
        close.addView(closeText, ctLp);
        close.setClickable(true);
        close.setOnClickListener(v -> callbacks.onClose());
        Ui.pressEffect(close);
        bar.addView(close, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.px(64)));
        hostText = Ui.text(context, "", 9f, Ui.TEXT_MUTED_LIGHT, Ui.W_REGULAR);
        hostText.setGravity(Gravity.END);
        bar.addView(hostText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        column.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.BURGUNDY_PRIMARY));
        progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Ui.BORDER_LIGHT));
        column.addView(progress, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.px(4)));

        web = new WebView(context);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Chi dieu huong https trong khung nay; moi scheme khac (intent:, tel:, http:...) bi chan.
                return !"https".equalsIgnoreCase(request.getUrl().getScheme());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progress.setVisibility(VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(GONE);
            }
        });
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
            }
        });
        column.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void show(String url) {
        if (url == null || !url.startsWith("https://")) return;
        if (url.equals(loadedUrl)) return;
        loadedUrl = url;
        try {
            hostText.setText(android.net.Uri.parse(url).getHost());
        } catch (Exception ignored) {
        }
        web.loadUrl(url);
    }

    public void reset() {
        loadedUrl = null;
        web.loadUrl("about:blank");
    }
}
