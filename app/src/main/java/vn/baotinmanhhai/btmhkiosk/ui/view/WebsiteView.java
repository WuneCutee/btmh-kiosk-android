package vn.baotinmanhhai.btmhkiosk.ui.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import vn.baotinmanhhai.btmhkiosk.databinding.ActivityWebViewBinding;
import vn.baotinmanhhai.btmhkiosk.databinding.FragmentHeaderBinding;

/**
 * Mo website tu quang cao (actionType = WEBSITE, spec II.2.2) NGAY TRONG App — khong bat trinh duyet ngoai de khach
 * khong thoat khoi kiosk. Inflate truc tiep activity_web_view.xml that (header dung chung qua fragment_header.xml,
 * thanh tien trinh do ruou mong, WebView, man loi web_load_error/web_go_back).
 */
public class WebsiteView extends FrameLayout {

    public interface Callbacks {
        void onClose();
    }

    private final ActivityWebViewBinding binding;
    private final WebView web;
    private String loadedUrl;

    @SuppressLint("SetJavaScriptEnabled")
    public WebsiteView(Context context, Callbacks callbacks) {
        super(context);
        binding = ActivityWebViewBinding.inflate(LayoutInflater.from(context), this, true);
        web = binding.webView;

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setGeolocationEnabled(false);
        web.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> { });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Chi cho https trong khung nay; moi scheme khac (intent:, tel:, http:...) bi chan.
                return !"https".equalsIgnoreCase(request.getUrl().getScheme());
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                binding.progress.setVisibility(VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                binding.progress.setVisibility(INVISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showError();
            }
        });
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                binding.progress.setProgressCompat(newProgress, true);
                binding.progress.setVisibility(newProgress < 100 ? VISIBLE : INVISIBLE);
            }
        });

        // fragment_header that: "header" trong activity_web_view.xml la FragmentContainerView tro toi HeaderFragment
        // (khong ton tai trong project nay, da thay bang FrameLayout rong khi copy layout) — inflate noi dung
        // fragment_header.xml that vao do, giong cach ShellView lam. Khong co tieu de (WebViewActivity khong goi
        // setTitle). Chevron trai tu xu ly lui trang WebView truoc (giong onBackPressedCallback that), het trang
        // moi dong han; Home luon dong han.
        FragmentHeaderBinding headerBinding = FragmentHeaderBinding.inflate(LayoutInflater.from(context), binding.header, true);
        headerBinding.tvTitle.setText("");
        headerBinding.ivBack.setOnClickListener(v -> {
            if (web.canGoBack()) web.goBack();
            else callbacks.onClose();
        });
        headerBinding.ivHome.setOnClickListener(v -> callbacks.onClose());

        binding.btnErrorBack.setOnClickListener(v -> callbacks.onClose());
    }

    private void showError() {
        binding.errorView.setVisibility(VISIBLE);
    }

    public void show(String url) {
        if (url == null || !url.startsWith("https://")) return;
        if (url.equals(loadedUrl)) return;
        loadedUrl = url;
        binding.errorView.setVisibility(GONE);
        web.loadUrl(url);
    }

    public void reset() {
        loadedUrl = null;
        binding.errorView.setVisibility(GONE);
        web.loadUrl("about:blank");
    }
}
