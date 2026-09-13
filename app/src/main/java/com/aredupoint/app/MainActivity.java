package com.aredupoint.app;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.messaging.FirebaseMessaging;

public class MainActivity extends AppCompatActivity {

    private static final String APP_URL =
            "https://aredupoint.github.io/AR-EDUPOINT/";

    private static final int FILE_CHOOSER_REQUEST_CODE = 1001;

    private WebView webView;

    // Holds callback supplied by HTML <input type="file">
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

        // ============================================================
        // JAVASCRIPT / STORAGE
        // ============================================================

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // ============================================================
        // PINCH ZOOM
        // ============================================================

        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setTextZoom(100);

        // ============================================================
        // WEB COMPATIBILITY
        // ============================================================

        settings.setMediaPlaybackRequiresUserGesture(false);

        // Required for file/content URIs
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(
                webView,
                true
        );

        webView.setOverScrollMode(
                View.OVER_SCROLL_IF_CONTENT_SCROLLS
        );


        // ============================================================
        // FILE CHOOSER
        // ============================================================
        //
        // Required for:
        //
        // <input type="file">
        //
        // This allows teacher to choose PDF from phone.
        // ============================================================

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                // Cancel previous unfinished callback
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback
                            .onReceiveValue(null);
                }

                MainActivity.this.filePathCallback =
                        filePathCallback;

                try {

                    Intent intent =
                            new Intent(Intent.ACTION_OPEN_DOCUMENT);

                    intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                    );

                    // Study Material accepts PDF
                    intent.setType("application/pdf");

                    intent.putExtra(
                            Intent.EXTRA_ALLOW_MULTIPLE,
                            false
                    );

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST_CODE
                    );

                    return true;

                } catch (ActivityNotFoundException e) {

                    // Fallback file picker
                    try {

                        Intent fallbackIntent =
                                new Intent(
                                        Intent.ACTION_GET_CONTENT
                                );

                        fallbackIntent.addCategory(
                                Intent.CATEGORY_OPENABLE
                        );

                        fallbackIntent.setType(
                                "application/pdf"
                        );

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

                        MainActivity.this.filePathCallback =
                                null;

                        return false;
                    }
                }
            }
        });


        // ============================================================
        // PDF / FILE DOWNLOAD SUPPORT
        // ============================================================
        //
        // Student side:
        //
        // Study Material PDF
        //        ↓
        // Android DownloadManager
        //        ↓
        // Downloads folder
        //
        // ============================================================

        webView.setDownloadListener(
                new DownloadListener() {

                    @Override
                    public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimeType,
                            long contentLength) {

                        try {

                            Uri downloadUri =
                                    Uri.parse(url);

                            DownloadManager.Request request =
                                    new DownloadManager.Request(
                                            downloadUri
                                    );

                            // PDF MIME type
                            if (mimeType != null &&
                                    !mimeType.isEmpty()) {

                                request.setMimeType(
                                        mimeType
                                );

                            } else {

                                request.setMimeType(
                                        "application/pdf"
                                );
                            }

                            // Keep User-Agent
                            if (userAgent != null &&
                                    !userAgent.isEmpty()) {

                                request.addRequestHeader(
                                        "User-Agent",
                                        userAgent
                                );
                            }

                            request.setTitle(
                                    "AR EDUPOINT Study Material"
                            );

                            request.setDescription(
                                    "Downloading PDF..."
                            );

                            request.setNotificationVisibility(
                                    DownloadManager.Request
                                            .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                            );

                            /*
                             * Save inside Android Downloads folder.
                             *
                             * A timestamp is used so that two PDFs
                             * don't overwrite each other.
                             */
                            String fileName =
                                    "AR_EDUPOINT_Study_Material_"
                                            + System.currentTimeMillis()
                                            + ".pdf";

                            request.setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    fileName
                            );

                            DownloadManager downloadManager =
                                    (DownloadManager)
                                            getSystemService(
                                                    DOWNLOAD_SERVICE
                                            );

                            if (downloadManager != null) {

                                downloadManager.enqueue(
                                        request
                                );

                                Toast.makeText(
                                        MainActivity.this,
                                        "PDF download started",
                                        Toast.LENGTH_SHORT
                                ).show();

                            } else {

                                Toast.makeText(
                                        MainActivity.this,
                                        "Download service unavailable",
                                        Toast.LENGTH_LONG
                                ).show();
                            }

                        } catch (Exception e) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "PDF download failed",
                                    Toast.LENGTH_LONG
                            ).show();

                            android.util.Log.e(
                                    "AR_EDUPOINT",
                                    "PDF download error",
                                    e
                            );
                        }
                    }
                }
        );


        // ============================================================
        // WEBVIEW URL HANDLING
        // ============================================================

        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageStarted(
                            WebView view,
                            String url,
                            Bitmap favicon) {

                        super.onPageStarted(
                                view,
                                url,
                                favicon
                        );
                    }


                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request) {

                        if (request == null ||
                                request.getUrl() == null) {

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

                        return handleUrl(
                                view,
                                url
                        );
                    }
                }
        );


        // ============================================================
        // LOAD AR EDUPOINT
        // ============================================================

        webView.loadUrl(APP_URL);


        // ============================================================
        // ANDROID BACK BUTTON
        // ============================================================

        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {

                    @Override
                    public void handleOnBackPressed() {

                        if (webView != null &&
                                webView.canGoBack()) {

                            webView.goBack();

                        } else {

                            finish();
                        }
                    }
                }
        );
    }


    // ================================================================
    // URL HANDLER
    // ================================================================

    private boolean handleUrl(
            WebView view,
            String url) {

        if (url == null ||
                url.trim().isEmpty()) {

            return false;
        }

        Uri uri = Uri.parse(url);

        String scheme = uri.getScheme();

        if (scheme == null) {
            return false;
        }

        scheme = scheme.toLowerCase();


        // ------------------------------------------------------------
        // NORMAL WEBSITE
        // ------------------------------------------------------------

        if (scheme.equals("http") ||
                scheme.equals("https")) {

            return false;
        }


        // ------------------------------------------------------------
        // MAILTO / NEED HELP
        // ------------------------------------------------------------

        if (scheme.equals("mailto")) {

            // First try Gmail
            try {

                Intent gmailIntent =
                        new Intent(
                                Intent.ACTION_SENDTO
                        );

                gmailIntent.setData(uri);

                gmailIntent.setPackage(
                        "com.google.android.gm"
                );

                startActivity(gmailIntent);

                return true;

            } catch (ActivityNotFoundException gmailNotAvailable) {

                // Try any email application
                try {

                    Intent emailIntent =
                            new Intent(
                                    Intent.ACTION_SENDTO
                            );

                    emailIntent.setData(uri);

                    startActivity(emailIntent);

                    return true;

                } catch (ActivityNotFoundException noEmailApp) {

                    /*
                     * Do NOT send mailto back to WebView.
                     * This prevents ERR_UNKNOWN_URL_SCHEME.
                     */

                    Toast.makeText(
                            MainActivity.this,
                            "No email app found",
                            Toast.LENGTH_SHORT
                    ).show();

                    return true;
                }
            }
        }


        // ------------------------------------------------------------
        // PHONE
        // ------------------------------------------------------------

        if (scheme.equals("tel")) {

            try {

                Intent intent =
                        new Intent(
                                Intent.ACTION_DIAL
                        );

                intent.setData(uri);

                startActivity(intent);

            } catch (ActivityNotFoundException ignored) {
            }

            return true;
        }


        // ------------------------------------------------------------
        // GEO / MAPS
        // ------------------------------------------------------------

        if (scheme.equals("geo")) {

            try {

                Intent intent =
                        new Intent(
                                Intent.ACTION_VIEW
                        );

                intent.setData(uri);

                startActivity(intent);

            } catch (ActivityNotFoundException ignored) {
            }

            return true;
        }


        // ------------------------------------------------------------
        // intent://
        // ------------------------------------------------------------

        if (scheme.equals("intent")) {

            try {

                Intent intent =
                        Intent.parseUri(
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

                        view.loadUrl(
                                fallbackUrl
                        );
                    }
                }

            } catch (Exception ignored) {
            }

            return true;
        }


        // ------------------------------------------------------------
        // PLAY STORE / MARKET
        // ------------------------------------------------------------

        if (scheme.equals("market")) {

            try {

                Intent intent =
                        new Intent(
                                Intent.ACTION_VIEW
                        );

                intent.setData(uri);

                startActivity(intent);

            } catch (ActivityNotFoundException ignored) {
            }

            return true;
        }


        // ------------------------------------------------------------
        // OTHER NON-HTTP SCHEMES
        // ------------------------------------------------------------

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW
                    );

            intent.setData(uri);

            startActivity(intent);

        } catch (ActivityNotFoundException ignored) {
        }

        /*
         * Never send unsupported schemes to WebView.
         * Prevents ERR_UNKNOWN_URL_SCHEME.
         */

        return true;
    }


    // ================================================================
    // FILE PICKER RESULT
    // ================================================================

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


        if (requestCode !=
                FILE_CHOOSER_REQUEST_CODE) {

            return;
        }


        if (filePathCallback == null) {

            return;
        }


        Uri[] results = null;


        // ------------------------------------------------------------
        // USER CANCELLED
        // ------------------------------------------------------------

        if (resultCode != RESULT_OK) {

            filePathCallback.onReceiveValue(
                    null
            );

            filePathCallback = null;

            return;
        }


        // ------------------------------------------------------------
        // SINGLE FILE
        // ------------------------------------------------------------

        if (data != null &&
                data.getData() != null) {

            results = new Uri[]{
                    data.getData()
            };
        }


        // ------------------------------------------------------------
        // MULTIPLE FILES
        // ------------------------------------------------------------

        else if (data != null &&
                data.getClipData() != null) {

            int count =
                    data.getClipData()
                            .getItemCount();

            results = new Uri[count];

            for (int i = 0;
                    i < count;
                    i++) {

                results[i] =
                        data.getClipData()
                                .getItemAt(i)
                                .getUri();
            }
        }


        filePathCallback.onReceiveValue(
                results
        );

        filePathCallback = null;
    }


    // ================================================================
    // FCM
    // ================================================================

    private static final String FCM_TOPIC =
            "ar_edupoint_all";


    private void setupPushNotifications() {

        // Android 13+ notification permission
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(
                        android.Manifest.permission
                                .POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(
                    new String[]{
                            android.Manifest.permission
                                    .POST_NOTIFICATIONS
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
                            "FCM token obtained: "
                                    + token
                    );


                    messaging.subscribeToTopic(
                                    FCM_TOPIC
                            )
                            .addOnSuccessListener(
                                    unused ->
                                            android.util.Log.d(
                                                    "AR_EDUPOINT",
                                                    "FCM topic subscribed: "
                                                            + FCM_TOPIC
                                            )
                            )
                            .addOnFailureListener(
                                    e ->
                                            android.util.Log.e(
                                                    "AR_EDUPOINT",
                                                    "FCM topic subscription failed",
                                                    e
                                            )
                            );
                })
                .addOnFailureListener(
                        e ->
                                android.util.Log.e(
                                        "AR_EDUPOINT",
                                        "FCM token retrieval failed",
                                        e
                                )
                );
    }


    // ================================================================
    // NOTIFICATION CHANNEL
    // ================================================================

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
                            MyFirebaseMessagingService
                                    .CHANNEL_ID,
                            "AR EDUPOINT Events",
                            NotificationManager
                                    .IMPORTANCE_HIGH
                    );


            channel.setDescription(
                    "Notifications for new AR EDUPOINT calendar events"
            );

            channel.enableVibration(true);

            manager.createNotificationChannel(
                    channel
            );
        }
    }


    // ================================================================
    // CLEANUP
    // ================================================================

    @Override
    protected void onDestroy() {

        if (filePathCallback != null) {

            filePathCallback.onReceiveValue(
                    null
            );

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
