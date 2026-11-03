package com.kerja.thio;

import android.Manifest;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.BackoffPolicy;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String WEBSITE_ORIGIN = "https://thio-cc-cd.pages.dev";
    private static final String WEBSITE_URL = WEBSITE_ORIGIN + "/kerja";
    private static final String PRIVACY_URL =
            "https://www.termsfeed.com/live/ca9fa754-e7cb-4d86-a39d-d4aa9c0354c4";

    private static final int REQUEST_GEOLOCATION = 2002;
    private static final int REQUEST_WEB_MEDIA = 2003;

    private WebView webView;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigation;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private View errorState;
    private TextView errorTitle;
    private TextView errorMessage;
    private Button retryButton;

    private ValueCallback<Uri[]> fileUploadCallback;
    private PermissionRequest pendingWebPermissionRequest;
    private String[] pendingWebPermissionResources = new String[0];
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private String fcmToken = "";

    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (fileUploadCallback == null) {
                            return;
                        }

                        Uri[] selectedFiles = null;
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            selectedFiles = WebChromeClient.FileChooserParams.parseResult(
                                    result.getResultCode(), result.getData());
                        }

                        fileUploadCallback.onReceiveValue(selectedFiles);
                        fileUploadCallback = null;
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        configureSystemBars();
        configureToolbar();
        configureNavigation();
        configureWebView();
        configureBackNavigation();
        scheduleBackgroundNotifications();
        initializeFirebase();

        if (savedInstanceState != null) {
            Bundle webViewState = savedInstanceState.getBundle("webview_state");
            if (webViewState != null && webView.restoreState(webViewState) != null) {
                updateNavigationSelection(webView.getUrl());
            } else {
                loadInitialPage();
            }
        } else {
            loadInitialPage();
        }
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);
        errorState = findViewById(R.id.errorState);
        errorTitle = findViewById(R.id.errorTitle);
        errorMessage = findViewById(R.id.errorMessage);
        retryButton = findViewById(R.id.retryButton);

        swipeRefresh.setColorSchemeResources(R.color.colorPrimary);
        swipeRefresh.setOnRefreshListener(() -> webView.reload());
        swipeRefresh.setOnChildScrollUpCallback((parent, child) ->
                webView != null && webView.canScrollVertically(-1));

        retryButton.setOnClickListener(v -> {
            hideErrorState();
            webView.reload();
        });
    }

    private void configureSystemBars() {
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.status_bar_color));
        getWindow().setNavigationBarColor(
                ContextCompat.getColor(this, R.color.navigation_bar_color));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setStatusBarContrastEnforced(false);
            getWindow().setNavigationBarContrastEnforced(false);
        }
    }

    private void configureToolbar() {
        toolbar.setTitle(R.string.app_name);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.action_refresh) {
                webView.reload();
                return true;
            }
            if (id == R.id.action_share) {
                shareCurrentPage();
                return true;
            }
            if (id == R.id.action_open_browser) {
                openCurrentPageInBrowser();
                return true;
            }
            if (id == R.id.action_privacy) {
                openExternal(Uri.parse(PRIVACY_URL));
                return true;
            }
            if (id == R.id.action_notifications) {
                openNotificationSettings();
                return true;
            }
            return false;
        });
    }

    private void configureNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            String destination;

            if (item.getItemId() == R.id.nav_home) {
                destination = WEBSITE_ORIGIN + "/";
            } else if (item.getItemId() == R.id.nav_blog) {
                destination = WEBSITE_ORIGIN + "/blog";
            } else if (item.getItemId() == R.id.nav_support) {
                destination = WEBSITE_ORIGIN + "/support";
            } else {
                destination = WEBSITE_URL;
            }

            if (webView != null && !destination.equals(webView.getUrl())) {
                webView.loadUrl(destination);
            }
            return true;
        });

        bottomNavigation.setSelectedItemId(R.id.nav_workspace);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setDefaultTextEncodingName("utf-8");
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setGeolocationEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, false);

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                hideErrorState();
                progressBar.setVisibility(View.VISIBLE);
                swipeRefresh.setRefreshing(false);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                hideErrorState();
                saveWebSession();
                updateNavigationSelection(url);
                injectFcmToken();
                requestNotificationPermissionIfNeeded();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl().toString());
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error) {
                if (request.isForMainFrame()) {
                    showErrorState(
                            R.string.connection_error_title,
                            R.string.connection_error_message);
                }
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(
                    WebView view, int errorCode, String description, String failingUrl) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    showErrorState(
                            R.string.connection_error_title,
                            R.string.connection_error_message);
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view, WebResourceRequest request,
                    android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame() && response.getStatusCode() >= 500) {
                    showErrorState(
                            R.string.server_error_title,
                            R.string.server_error_message);
                }
            }

            @Override
            public void onReceivedSslError(
                    WebView view,
                    android.webkit.SslErrorHandler handler,
                    android.net.http.SslError error) {
                handler.cancel();
                showErrorState(
                        R.string.secure_connection_error_title,
                        R.string.secure_connection_error_message);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(
                        newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                if (!isTrustedOrigin(origin)) {
                    callback.invoke(origin, false, false);
                    return;
                }

                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                    return;
                }

                pendingGeoOrigin = origin;
                pendingGeoCallback = callback;
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        REQUEST_GEOLOCATION);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> handleWebPermissionRequest(request));
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingWebPermissionRequest == request) {
                    pendingWebPermissionRequest = null;
                    pendingWebPermissionResources = new String[0];
                }
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (!isTrustedUrl(view.getUrl())) {
                    filePathCallback.onReceiveValue(null);
                    return false;
                }

                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                try {
                    fileChooserLauncher.launch(fileChooserParams.createIntent());
                } catch (Exception exception) {
                    fileUploadCallback.onReceiveValue(null);
                    fileUploadCallback = null;
                    Toast.makeText(
                            MainActivity.this,
                            R.string.file_picker_unavailable,
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                downloadFile(url, userAgent, contentDisposition, mimeType));
    }

    private void handleWebPermissionRequest(PermissionRequest request) {
        if (!isTrustedOrigin(request.getOrigin())) {
            request.deny();
            return;
        }

        if (pendingWebPermissionRequest != null) {
            pendingWebPermissionRequest.deny();
        }

        List<String> approvedResources = new ArrayList<>();
        List<String> permissionsToRequest = new ArrayList<>();

        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                if (hasPermission(Manifest.permission.CAMERA)) {
                    approvedResources.add(resource);
                } else {
                    permissionsToRequest.add(Manifest.permission.CAMERA);
                }
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    approvedResources.add(resource);
                } else {
                    permissionsToRequest.add(Manifest.permission.RECORD_AUDIO);
                }
            }
        }

        if (permissionsToRequest.isEmpty()) {
            if (approvedResources.isEmpty()) {
                request.deny();
            } else {
                request.grant(approvedResources.toArray(new String[0]));
            }
            return;
        }

        pendingWebPermissionRequest = request;
        pendingWebPermissionResources = request.getResources();
        ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toArray(new String[0]),
                REQUEST_WEB_MEDIA);
    }

    private boolean handleNavigation(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return true;
        }

        Uri uri = Uri.parse(rawUrl);
        String scheme = uri.getScheme() == null
                ? ""
                : uri.getScheme().toLowerCase();

        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (isTrustedUrl(rawUrl)) {
                return false;
            }
            openExternal(uri);
            return true;
        }

        if ("blob".equals(scheme) || "data".equals(scheme) || "about".equals(scheme)) {
            return false;
        }

        if ("intent".equals(scheme)) {
            try {
                Intent intent = Intent.parseUri(rawUrl, Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    String fallback = intent.getStringExtra("browser_fallback_url");
                    if (fallback != null && isTrustedUrl(fallback)) {
                        webView.loadUrl(fallback);
                    } else if (fallback != null) {
                        openExternal(Uri.parse(fallback));
                    }
                }
            } catch (Exception exception) {
                Toast.makeText(this, R.string.link_unavailable, Toast.LENGTH_SHORT).show();
            }
            return true;
        }

        if ("tel".equals(scheme)
                || "mailto".equals(scheme)
                || "sms".equals(scheme)
                || "smsto".equals(scheme)
                || "market".equals(scheme)
                || "geo".equals(scheme)
                || "whatsapp".equals(scheme)) {
            openExternal(uri);
            return true;
        }

        return true;
    }

    private boolean isTrustedUrl(String rawUrl) {
        if (rawUrl == null) {
            return false;
        }

        Uri uri = Uri.parse(rawUrl);
        return "https".equalsIgnoreCase(uri.getScheme())
                && isTrustedHost(uri.getHost());
    }

    private boolean isTrustedOrigin(String origin) {
        if (origin == null) {
            return false;
        }

        Uri uri = Uri.parse(origin);
        return "https".equalsIgnoreCase(uri.getScheme())
                && isTrustedHost(uri.getHost());
    }

    private boolean isTrustedHost(String host) {
        if (host == null) {
            return false;
        }

        String normalized = host.toLowerCase();
        return normalized.equals("thio-cc-cd.pages.dev")
                || normalized.equals("thio.cc.cd")
                || normalized.equals("www.thio.cc.cd")
                || normalized.endsWith(".thio.cc.cd");
    }

    private void loadInitialPage() {
        String deepLink = resolveDeepLink(getIntent());
        webView.loadUrl(deepLink == null ? WEBSITE_URL : deepLink);
    }

    private String resolveDeepLink(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return null;
        }

        Uri data = intent.getData();
        if ("thio".equalsIgnoreCase(data.getScheme())) {
            String host = data.getHost() == null ? "" : data.getHost();
            String path = host + data.getPath();
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            return WEBSITE_ORIGIN + (path.isEmpty() ? "/" : "/" + path);
        }

        String url = data.toString();
        return isTrustedUrl(url) ? url : null;
    }

    private void saveWebSession() {
        if (webView == null || !isTrustedUrl(webView.getUrl())) {
            return;
        }

        String cookies = CookieManager.getInstance().getCookie(WEBSITE_ORIGIN);
        if (cookies == null || cookies.isEmpty()) {
            return;
        }

        getSharedPreferences("bp_prefs", MODE_PRIVATE)
                .edit()
                .putString("session_cookies", cookies)
                .putString("website_url", WEBSITE_ORIGIN)
                .apply();
    }

    private void initializeFirebase() {
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    fcmToken = token;
                    TokenRegistrar.register(getApplicationContext(), token);
                    injectFcmToken();
                })
                .addOnFailureListener(error ->
                        android.util.Log.w("KerjaFCM", "Unable to obtain a messaging token", error));
    }

    private void injectFcmToken() {
        if (webView == null || fcmToken.isEmpty() || !isTrustedUrl(webView.getUrl())) {
            return;
        }

        String quotedToken = JSONObject.quote(fcmToken);
        String script = "window.FCM_TOKEN = " + quotedToken
                + "; window.dispatchEvent(new Event('fcm_token_ready'));";
        webView.evaluateJavascript(script, null);
    }

    private void scheduleBackgroundNotifications() {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                NotificationWorker.class, 15, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "notification_check",
                ExistingPeriodicWorkPolicy.KEEP,
                request);
    }

    private void downloadFile(
            String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);

            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) {
                request.addRequestHeader("Cookie", cookies);
            }
            if (userAgent != null) {
                request.addRequestHeader("User-Agent", userAgent);
            }

            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(filename);
            request.setDescription(getString(R.string.download_in_progress));
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, filename);

            DownloadManager manager =
                    (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                throw new IllegalStateException("Download service unavailable");
            }

            manager.enqueue(request);
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void updateNavigationSelection(String url) {
        if (url == null) {
            return;
        }

        Uri uri = Uri.parse(url);
        String path = uri.getPath() == null ? "/" : uri.getPath();

        int selectedItem = R.id.nav_workspace;
        if ("/".equals(path)) {
            selectedItem = R.id.nav_home;
        } else if (path.equals("/blog") || path.startsWith("/blog/")) {
            selectedItem = R.id.nav_blog;
        } else if (path.equals("/support") || path.startsWith("/support/")) {
            selectedItem = R.id.nav_support;
        }

        bottomNavigation.setOnItemSelectedListener(null);
        bottomNavigation.setSelectedItemId(selectedItem);
        configureNavigationListener();
    }

    private void configureNavigationListener() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            String destination;
            if (item.getItemId() == R.id.nav_home) {
                destination = WEBSITE_ORIGIN + "/";
            } else if (item.getItemId() == R.id.nav_blog) {
                destination = WEBSITE_ORIGIN + "/blog";
            } else if (item.getItemId() == R.id.nav_support) {
                destination = WEBSITE_ORIGIN + "/support";
            } else {
                destination = WEBSITE_URL;
            }

            if (webView != null && !destination.equals(webView.getUrl())) {
                webView.loadUrl(destination);
            }
            return true;
        });
    }

    private void showErrorState(int titleRes, int messageRes) {
        progressBar.setVisibility(View.GONE);
        swipeRefresh.setRefreshing(false);
        errorTitle.setText(titleRes);
        errorMessage.setText(messageRes);
        errorState.setVisibility(View.VISIBLE);
    }

    private void hideErrorState() {
        if (errorState != null) {
            errorState.setVisibility(View.GONE);
        }
    }

    private void shareCurrentPage() {
        String currentUrl = webView == null ? WEBSITE_URL : webView.getUrl();
        if (currentUrl == null || currentUrl.isEmpty()) {
            currentUrl = WEBSITE_URL;
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, currentUrl);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_page)));
    }

    private void openCurrentPageInBrowser() {
        String currentUrl = webView == null ? WEBSITE_URL : webView.getUrl();
        openExternal(Uri.parse(currentUrl == null ? WEBSITE_URL : currentUrl));
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception exception) {
            Toast.makeText(this, R.string.link_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());

        try {
            startActivity(intent);
        } catch (Exception exception) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallback.setData(Uri.parse("package:" + getPackageName()));
            startActivity(fallback);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            return;
        }

        android.content.SharedPreferences preferences =
                getSharedPreferences("app_prefs", MODE_PRIVATE);
        if (preferences.getBoolean("notification_prompt_shown", false)) {
            return;
        }
        preferences.edit().putBoolean("notification_prompt_shown", true).apply();

        new AlertDialog.Builder(this)
                .setTitle(R.string.notifications_title)
                .setMessage(R.string.notifications_message)
                .setPositiveButton(R.string.allow, (dialog, which) ->
                        ActivityCompat.requestPermissions(
                                this,
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                1001))
                .setNegativeButton(R.string.not_now, null)
                .show();
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private void configureBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_GEOLOCATION && pendingGeoCallback != null) {
            boolean granted = hasLocationPermission();
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
            return;
        }

        if (requestCode == REQUEST_WEB_MEDIA && pendingWebPermissionRequest != null) {
            List<String> approvedResources = new ArrayList<>();

            for (String resource : pendingWebPermissionResources) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)
                        && hasPermission(Manifest.permission.CAMERA)) {
                    approvedResources.add(resource);
                } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)
                        && hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    approvedResources.add(resource);
                }
            }

            if (approvedResources.isEmpty()) {
                pendingWebPermissionRequest.deny();
            } else {
                pendingWebPermissionRequest.grant(
                        approvedResources.toArray(new String[0]));
            }

            pendingWebPermissionRequest = null;
            pendingWebPermissionResources = new String[0];
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            Bundle webViewState = new Bundle();
            webView.saveState(webViewState);
            outState.putBundle("webview_state", webViewState);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String deepLink = resolveDeepLink(intent);
        if (deepLink != null && webView != null) {
            webView.loadUrl(deepLink);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (fileUploadCallback != null) {
            fileUploadCallback.onReceiveValue(null);
            fileUploadCallback = null;
        }
        if (pendingWebPermissionRequest != null) {
            pendingWebPermissionRequest.deny();
            pendingWebPermissionRequest = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
