package com.aredupoint.app;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    private static final String APP_URL =
            "https://aredupoint.github.io/AR-EDUPOINT/";

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private WebView webView;

    // Holds the callback supplied by the HTML <input type="file">
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        createNotificationChannel();
        setupPushNotifications();

        WebSettings settings = webView.getSettings();

        // JavaScript / storage
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Pinch zoom
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);

        // Web compatibility
        settings.setMediaPlaybackRequiresUserGesture(false);

        /*
         * IMPORTANT:
         * File chooser / content URIs need WebView file/content access.
         */
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        /*
         * ============================================================
         * FILE CHOOSER
         * ============================================================
         *
         * This is required for:
         *
         * <input type="file" ...>
         *
         * in Android WebView.
         */
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                // Cancel any previous unfinished callback.
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                    intent.addCategory(Intent.CATEGORY_OPENABLE);

                    // Study Material accepts PDF files.
                    intent.setType("application/pdf");

                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST_CODE
                    );

                    return true;

                } catch (ActivityNotFoundException e) {

                    // Fallback for devices where ACTION_OPEN_DOCUMENT
                    // is not available.
                    try {
                        Intent fallbackIntent =
                                new Intent(Intent.ACTION_GET_CONTENT);

                        fallbackIntent.addCategory(
                                Intent.CATEGORY_OPENABLE
                        );

                        fallbackIntent.setType("application/pdf");

                        fallbackIntent.putExtra(
                                Intent.EXTRA_ALLOW_MULTIPLE,
                                false
                        );

                        startActivityForResult(
                                fallbackIntent,
                                FILE_CHOOSER_REQUEST_CODE
                        );

                        return true;

                    } catch (Exception fallbackException) {
                        MainActivity.this.filePathCallback = null;
                        return false;
                    }
                }
            }
        });

        /*
         * ============================================================
         * WEBVIEW URL HANDLING
         * ============================================================
         *
         * HTTP/HTTPS -> stay inside WebView.
         *
         * mailto: -> Gmail / email application.
         *
         * tel:, geo:, market:, etc. -> Android external application.
         *
         * This prevents ERR_UNKNOWN_URL_SCHEME.
         */
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    Bitmap favicon) {

                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                if (request == null || request.getUrl() == null) {
                    return false;
                }

                return handleUrl(
                        view,
                        request.getUrl().toString()
                );
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url) {

                return handleUrl(view, url);
            }
        });

        webView.loadUrl(APP_URL);

        /*
         * Android back button:
         * first go back inside WebView,
         * otherwise close the app.
         */
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (webView != null && webView.canGoBack()) {
                            webView.goBack();
                        } else {
                            finish();
                        }
                    }
                }
        );
    }

    /*
     * ================================================================
     * URL HANDLER
     * ================================================================
     */
    private boolean handleUrl(WebView view, String url) {

        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();

        if (scheme == null) {
            return false;
        }

        scheme = scheme.toLowerCase();

        /*
         * Normal website pages stay inside WebView.
         */
        if (scheme.equals("http") || scheme.equals("https")) {
            return false;
        }

        /*
         * ------------------------------------------------------------
         * MAILTO
         * ------------------------------------------------------------
         *
         * Need Help button:
         *
         * mailto:aredupointafz@gmail.com?subject=...
         *
         * First try Gmail directly.
         * If Gmail isn't installed, try another email app.
         */
        if (scheme.equals("mailto")) {

            // First: Gmail directly.
            try {
                Intent gmailIntent =
                        new Intent(Intent.ACTION_SENDTO);

                gmailIntent.setData(uri);

                gmailIntent.setPackage("com.google.android.gm");

                startActivity(gmailIntent);

                return true;

            } catch (ActivityNotFoundException gmailNotAvailable) {

                // Second: any installed email application.
                try {
                    Intent emailIntent =
                            new Intent(Intent.ACTION_SENDTO);

                    emailIntent.setData(uri);

                    startActivity(emailIntent);

                    return true;

                } catch (ActivityNotFoundException noEmailApp) {

                    /*
                     * No email application is installed.
                     * Do not send the mailto URL back to WebView,
                     * because that would produce ERR_UNKNOWN_URL_SCHEME.
                     */
                    return true;
                }
            }
        }

        /*
         * Phone calls.
         */
        if (scheme.equals("tel")) {

            try {
                Intent intent =
                        new Intent(Intent.ACTION_DIAL);

                intent.setData(uri);

                startActivity(intent);

            } catch (ActivityNotFoundException ignored) {
            }

            return true;
        }

        /*
         * Maps / location.
         */
        if (scheme.equals("geo")) {

            try {
                Intent intent =
                        new Intent(Intent.ACTION_VIEW);

                intent.setData(uri);

                startActivity(intent);

            } catch (ActivityNotFoundException ignored) {
            }

            return true;
        }

        /*
         * Android intent:// URLs.
         */
        if (scheme.equals("intent")) {

            try {
                Intent intent = Intent.parseUri(
                        url,
                        Intent.URI_INTENT_SCHEME
                );

                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {

                    String fallbackUrl =
                            intent.getStringExtra(
                                    "browser_fallback_url"
                            );

                    if (fallbackUrl != null &&
                            !fallbackUrl.isEmpty()) {

                        view.loadUrl(fallbackUrl);
                    }
                }

            } catch (Exception ignored) {
            }

            return true;
        }

        /*
         * Play Store / other Android schemes.
         */
        if (scheme.equals("market")) {

            try {
                Intent intent =
                        new Intent(Intent.ACTION_VIEW);

                intent.setData(uri);

                startActivity(intent);

            } catch (ActivityNotFoundException ignored) {
            }

            return true;
        }

        /*
         * For any other non-http scheme, try Android first.
         * Never send it to WebView, which avoids
         * ERR_UNKNOWN_URL_SCHEME.
         */
        try {

            Intent intent =
                    new Intent(Intent.ACTION_VIEW);

            intent.setData(uri);

            startActivity(intent);

        } catch (ActivityNotFoundException ignored) {
        }

        return true;
    }

    /*
     * ================================================================
     * FILE PICKER RESULT
     * ================================================================
     */
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode != FILE_CHOOSER_REQUEST_CODE) {
            return;
        }

        if (filePathCallback == null) {
            return;
        }

        Uri[] results = null;

        /*
         * User cancelled the picker.
         */
        if (resultCode != RESULT_OK) {

            filePathCallback.onReceiveValue(null);
            filePathCallback = null;

            return;
        }

        /*
         * Single selected file.
         */
        if (data != null && data.getData() != null) {

            results = new Uri[]{
                    data.getData()
            };
        }

        /*
         * Multiple-selection support, kept for compatibility.
         */
        else if (data != null &&
                data.getClipData() != null) {

            int count = data.getClipData().getItemCount();

            results = new Uri[count];

            for (int i = 0; i < count; i++) {

                results[i] =
                        data.getClipData()
                                .getItemAt(i)
                                .getUri();
            }
        }

        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    /*
     * ================================================================
     * FCM
     * ================================================================
     */
    private static final String FCM_TOPIC =
            "ar_edupoint_all";

    private void setupPushNotifications() {

        // Android 13+ notification permission.
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            android.Manifest.permission.POST_NOTIFICATIONS
                    },
                    7001
            );
        }

        FirebaseMessaging messaging =
                FirebaseMessaging.getInstance();

        messaging.getToken()
                .addOnSuccessListener(token -> {

                    android.util.Log.d(
                            "AR_EDUPOINT",
                            "FCM token obtained: " + token
                    );

                    messaging.subscribeToTopic(FCM_TOPIC)
                            .addOnSuccessListener(unused ->
                                    android.util.Log.d(
                                            "AR_EDUPOINT",
                                            "FCM topic subscribed: "
                                                    + FCM_TOPIC
                                    )
                            )
                            .addOnFailureListener(e ->
                                    android.util.Log.e(
                                            "AR_EDUPOINT",
                                            "FCM topic subscription failed",
                                            e
                                    )
                            );
                })
                .addOnFailureListener(e ->
                        android.util.Log.e(
                                "AR_EDUPOINT",
                                "FCM token retrieval failed",
                                e
                        )
                );
    }

    /*
     * ================================================================
     * NOTIFICATION CHANNEL
     * ================================================================
     */
    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O) {

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class
                    );

            if (manager == null) {
                return;
            }

            NotificationChannel channel =
                    new NotificationChannel(
                            MyFirebaseMessagingService.CHANNEL_ID,
                            "AR EDUPOINT Events",
                            NotificationManager.IMPORTANCE_HIGH
                    );

            channel.setDescription(
                    "Notifications for new AR EDUPOINT calendar events"
            );

            channel.enableVibration(true);

            manager.createNotificationChannel(channel);
        }
    }

    /*
     * ================================================================
     * CLEANUP
     * ================================================================
     */
    @Override
    protected void onDestroy() {

        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }

        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
