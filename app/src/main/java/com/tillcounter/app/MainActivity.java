package com.tillcounter.app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;

/**
 * Sole Android host for Till Counter.
 * Owns Android lifecycle, safe-area layout and secure loading of the bundled offline web UI.
 * Business/counting rules remain in app.js. This host deliberately has no network, file-system,
 * account, update, camera or permission responsibilities.
 */
public final class MainActivity extends AppCompatActivity {
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.rgb(6, 23, 17));
        getWindow().setNavigationBarColor(Color.rgb(6, 23, 17));

        final FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(6, 23, 17));
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(6, 23, 17));
        root.addView(webView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            final androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) webView.getLayoutParams();
            lp.leftMargin = bars.left; lp.topMargin = bars.top; lp.rightMargin = bars.right; lp.bottomMargin = bars.bottom;
            webView.setLayoutParams(lp);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();

        final WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDatabaseEnabled(false);
        settings.setTextZoom(100);
        settings.setSupportMultipleWindows(false);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface public void ready(String marker) {
                if ("APP_READY".equals(marker)) Log.i("TillCounterProof", "APP_READY");
            }
        }, "TillCounterProof");

        webView.setLongClickable(false);
        webView.setOnLongClickListener(view -> true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }
        });
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            final ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) parent.removeView(webView);
            webView.stopLoading();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
