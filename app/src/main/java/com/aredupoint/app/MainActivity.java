package com.aredupoint.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.webkit.CookieManager;
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

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        createNotificationChannel();
        setupPushNotifications();

        WebSettings settings = webView.getSettings();

        // Full WebView support for the live AR EDUPOINT website.
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Enable pinch zoom / zoom in / zoom out.
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);

        // Keep normal text sizing while allowing page zoom.
        settings.setTextZoom(100);

        // Improve compatibility with the existing HTML app.
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        webView.loadUrl(APP_URL);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    private static final String FCM_TOPIC = "ar_edupoint_all";

    private void setupPushNotifications() {
        // Android 13+ requires runtime notification permission.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7001);
        }

        // Explicitly initialize FCM, obtain the registration token, then subscribe
        // to the broadcast topic. This also gives us a reliable diagnostic path.
        FirebaseMessaging messaging = FirebaseMessaging.getInstance();
        messaging.getToken()
                .addOnSuccessListener(token -> {
                    android.util.Log.d("AR_EDUPOINT", "FCM token obtained: " + token);
                    messaging.subscribeToTopic(FCM_TOPIC)
                            .addOnSuccessListener(unused ->
                                    android.util.Log.d("AR_EDUPOINT", "FCM topic subscribed: " + FCM_TOPIC))
                            .addOnFailureListener(e ->
                                    android.util.Log.e("AR_EDUPOINT", "FCM topic subscription failed", e));
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("AR_EDUPOINT", "FCM token retrieval failed", e));
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            NotificationChannel channel = new NotificationChannel(
                    MyFirebaseMessagingService.CHANNEL_ID,
                    "AR EDUPOINT Events",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for new AR EDUPOINT calendar events");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }
}
