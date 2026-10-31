package com.workpig.ledger;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.OutputStream;

/**
 * 打工人小账本 · WebView 壳
 *
 * 设计要点：
 * 1. 页面从 assets/index.html 加载，完全离线，不申请网络权限。
 * 2. 拦截 window.prompt（页面「清空全部数据」的二次确认依赖它），改用原生输入框。
 * 3. 拦截 <a download> 触发的 blob 下载，改由原生写入「下载」目录，
 *    保证「导出 JSON / 导出 CSV」在 WebView 里可用。
 * 4. 拦截链接跳转：站内不动，站外交给系统浏览器，避免应用内被劫持。
 */
public class MainActivity extends AppCompatActivity {

    private static final String APP_URL = "file:///android_asset/index.html";
    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_CREATE_DOC = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private byte[] pendingBytes;
    private String pendingName;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);          // localStorage 必需
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);           // 只允许访问 assets，减少攻击面
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setTextZoom(100);                    // 不跟随系统字体缩放，保持版面
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setMediaPlaybackRequiresUserGesture(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                if (scheme.equals("file")) {
                    return false;
                }
                if (scheme.equals("http") || scheme.equals("https")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 让页面知道自己在壳里，后续可据此启用原生能力
                view.evaluateJavascript("window.__IS_ANDROID_SHELL__=true;", null);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                fileChooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择备份文件"), REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    fileChooserCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message,
                                     final android.webkit.JsResult result) {
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.app_name))
                        .setMessage(message)
                        .setPositiveButton("好", (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message,
                                       final android.webkit.JsResult result) {
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.app_name))
                        .setMessage(message)
                        .setPositiveButton("确定", (d, w) -> result.confirm())
                        .setNegativeButton("取消", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, final android.webkit.JsPromptResult result) {
                final android.widget.EditText input = new android.widget.EditText(MainActivity.this);
                input.setText(defaultValue == null ? "" : defaultValue);
                input.setSingleLine(true);
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.app_name))
                        .setMessage(message)
                        .setView(input, 48, 8, 48, 0)
                        .setPositiveButton("确定", (d, w) -> result.confirm(input.getText().toString()))
                        .setNegativeButton("取消", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            // 页面内导出走的是 blob: 链接，由下面的 Bridge.saveBlob 处理；
            // 这里兜底处理 http(s) 下载，避免静默失败。
            if (url != null && url.startsWith("blob:")) {
                return;
            }
            Toast.makeText(this, "请用「导出」按钮保存文件", Toast.LENGTH_SHORT).show();
        });

        if (savedInstanceState == null) {
            webView.loadUrl(APP_URL);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_CREATE_DOC) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                writeToUri(data.getData());
            } else {
                pendingBytes = null;
                Toast.makeText(this, "已取消导出", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == REQ_FILE_CHOOSER) {
            if (fileChooserCallback != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    result = new Uri[]{data.getData()};
                }
                fileChooserCallback.onReceiveValue(result);
                fileChooserCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /** 返回键：先尝试让页面处理（如关闭弹窗），再退出 */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null) {
            webView.evaluateJavascript(
                    "(function(){var m=document.getElementById('mask');" +
                    "if(m&&m.classList.contains('open')){document.getElementById('dlgCancel').click();return '1';}" +
                    "return '0';})()",
                    value -> {
                        if (value == null || !value.contains("1")) {
                            finish();
                        }
                    });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    /**
     * 暴露给页面的原生能力：把 base64 内容交给系统「另存为」选择器。
     * 页面通过 AndroidBridge.saveBlob(name, base64, mime) 调用。
     *
     * 使用 ACTION_CREATE_DOCUMENT（SAF）而非直接写 Download 目录：
     * 1. 用户自己决定保存位置，符合 HyperOS 的存储沙箱策略；
     * 2. 不需要任何存储读写权限；
     * 3. minSdk 24 到最新系统行为一致，不再有 Android 9 以下不可用的分支。
     */
    private class Bridge {
        @JavascriptInterface
        public void saveBlob(String fileName, String base64, String mime) {
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                pendingBytes = bytes;
                pendingName = fileName;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                intent.putExtra(Intent.EXTRA_TITLE, fileName);
                runOnUiThread(() -> {
                    try {
                        startActivityForResult(intent, REQ_CREATE_DOC);
                    } catch (Exception e) {
                        pendingBytes = null;
                        Toast.makeText(MainActivity.this,
                                "找不到可用的文件保存器，请用浏览器导出", Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "导出失败：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public String platform() {
            return "android";
        }
    }

    /** 把待保存的字节写入用户在 SAF 中选择的 Uri */
    private void writeToUri(Uri uri) {
        if (pendingBytes == null || uri == null) return;
        final byte[] data = pendingBytes;
        final String name = pendingName;
        pendingBytes = null;
        new Thread(() -> {
            try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                if (os == null) throw new IllegalStateException("无法写入");
                os.write(data);
                os.flush();
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "已保存：" + name, Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "保存失败：" + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }
}
