package id.contoh.absenmaju;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pembungkus WebView untuk aplikasi absensi (assets/index.html).
 * Halaman dimuat dari https://appassets.androidplatform.net sehingga kamera & GPS
 * (yang mensyaratkan "secure context") berfungsi tanpa perlu server sungguhan.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/index.html";
    private static final int REQ_PERMS = 100;

    private WebView web;
    private PermissionRequest pendingWebPermission;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage: tempat data absensi disimpan
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new AppClient());
        web.setWebChromeClient(new AppChrome());

        // Minta izin di awal agar pengguna tidak kaget saat pertama kali absen.
        List<String> need = new ArrayList<>();
        for (String p : new String[]{Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}) {
            if (!has(p)) need.add(p);
        }
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERMS);

        web.loadUrl(START_URL);
    }

    private boolean has(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocation() {
        return has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private static String mime(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json") || n.endsWith(".webmanifest")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private void grantVideoOnly(PermissionRequest request) {
        List<String> ok = new ArrayList<>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) ok.add(r);   // mikrofon tidak pernah diberikan
        }
        if (ok.isEmpty()) request.deny(); else request.grant(ok.toArray(new String[0]));
    }

    private class AppClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (HOST.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/assets/")) {
                String name = u.getPath().substring("/assets/".length());
                try {
                    if (name.contains("..")) throw new IOException("path tidak valid");
                    InputStream in = getAssets().open(name);
                    return new WebResourceResponse(mime(name), "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null,
                            new ByteArrayInputStream(new byte[0]));
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (HOST.equals(u.getHost())) return false;
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }  // mis. tautan peta
            return true;
        }
    }

    private class AppChrome extends WebChromeClient {
        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            runOnUiThread(() -> {
                if (!HOST.equals(request.getOrigin().getHost())) { request.deny(); return; }
                if (has(Manifest.permission.CAMERA)) {
                    grantVideoOnly(request);
                } else {
                    pendingWebPermission = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERMS);
                }
            });
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (hasLocation()) {
                callback.invoke(origin, true, false);
            } else {
                pendingGeoOrigin = origin;
                pendingGeoCallback = callback;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_PERMS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (pendingWebPermission != null) {
            if (has(Manifest.permission.CAMERA)) grantVideoOnly(pendingWebPermission); else pendingWebPermission.deny();
            pendingWebPermission = null;
        }
        if (pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, hasLocation(), false);
            pendingGeoCallback = null;
        }
    }

    /** Jembatan JS: menyimpan CSV ke folder Download (blob download tidak jalan di WebView). */
    private class Bridge {
        @JavascriptInterface
        public String saveFile(String filename, String mimeType, String content) {
            try {
                String safe = filename.replaceAll("[^A-Za-z0-9._-]", "_");
                ContentResolver cr = getContentResolver();
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                v.put(MediaStore.Downloads.MIME_TYPE, mimeType == null ? "text/csv" : mimeType);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) return "ERR:tidak bisa membuat file";
                try (OutputStream os = cr.openOutputStream(uri)) {
                    if (os == null) return "ERR:tidak bisa menulis file";
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                }
                return "OK:" + safe;
            } catch (Exception e) {
                return "ERR:" + e.getMessage();
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onResume() { super.onResume(); if (web != null) web.onResume(); }

    @Override
    protected void onPause() { if (web != null) web.onPause(); super.onPause(); }
}
