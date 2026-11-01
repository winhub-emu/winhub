package com.winhub.hernandez.store

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import com.winhub.hernandez.ui.components.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.winhub.hernandez.core.AppOrientation
import com.winhub.hernandez.ui.theme.WinlatorTheme

/**
 * The in-app store web page — where "Get for free" and "View on GOG / Epic" land.
 *
 * GOG and Epic have no add-to-library API a third-party client may call: a free claim is a web
 * checkout (Epic's additionally carries a captcha), so the honest path is the store's own page in
 * a WebView that shares the app's cookie jar. The GOG and Epic sign-ins already run through
 * WebViews in this app, so the session cookies are usually present and the page opens signed in.
 *
 * Finishes with [RESULT_OK] so the host storefront re-syncs its library on return — a claim made
 * here shows up in the Library tab without a manual refresh.
 */
class StoreWebActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        /** Let the page start audio/video without a tap — the Media tab's YouTube embeds. */
        const val EXTRA_ALLOW_AUTOPLAY = "allow_autoplay"

        fun intent(ctx: Context, url: String, title: String, allowAutoplay: Boolean = false): Intent =
            Intent(ctx, StoreWebActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_ALLOW_AUTOPLAY, allowAutoplay)
    }

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppOrientation.apply(this)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val pageTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val allowAutoplay = intent.getBooleanExtra(EXTRA_ALLOW_AUTOPLAY, false)
        if (url.isBlank()) { finish(); return }
        setResult(RESULT_OK)

        setContent {
            WinlatorTheme {
                DeckStoreTheme {
                var progress by remember { mutableIntStateOf(0) }
                var title by remember { mutableStateOf(pageTitle) }
                var canGoBack by remember { mutableStateOf(false) }
                // A page's own full-screen element (the YouTube embed's fullscreen button).
                var customView by remember { mutableStateOf<View?>(null) }
                var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

                // Back leaves a full-screen element, then walks the page history, then closes.
                BackHandler(enabled = true) {
                    val wv = webView
                    if (customView != null) {
                        customViewCallback?.onCustomViewHidden()
                        customView = null; customViewCallback = null
                    } else if (wv != null && wv.canGoBack()) wv.goBack() else finish()
                }

                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            Text(
                                text = title.ifBlank { url },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.padding(2.dp))
                            IconButton(onClick = {
                                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webView?.url ?: url))) }
                            }) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = "Open in browser", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (progress in 1..99) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    WebView(context).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                        )
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.databaseEnabled = true
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        settings.setSupportZoom(true)
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        settings.mediaPlaybackRequiresUserGesture = !allowAutoplay
                                        CookieManager.getInstance().setAcceptCookie(true)
                                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView, url: String?) {
                                                canGoBack = view.canGoBack()
                                                view.title?.takeIf { it.isNotBlank() }?.let { title = it }
                                                CookieManager.getInstance().flush()
                                            }
                                        }
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView, newProgress: Int) {
                                                progress = newProgress
                                            }

                                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                                customView = view
                                                customViewCallback = callback
                                            }

                                            override fun onHideCustomView() {
                                                customView = null
                                                customViewCallback = null
                                            }
                                        }
                                        webView = this
                                        loadUrl(url)
                                    }
                                },
                            )
                        }
                    }
                    // Full-screen video element over everything (no system-bar inset: it is the video).
                    val cv = customView
                    if (cv != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black),
                            factory = { context ->
                                FrameLayout(context).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                }
                            },
                            update = { holder ->
                                if (cv.parent !== holder) {
                                    (cv.parent as? ViewGroup)?.removeView(cv)
                                    holder.removeAllViews()
                                    holder.addView(cv, ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    ))
                                }
                            },
                        )
                    }
                }
            
                }}
        }
    }

    override fun onDestroy() {
        runCatching { CookieManager.getInstance().flush() }
        webView?.let { wv ->
            runCatching {
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.stopLoading()
                wv.destroy()
            }
        }
        webView = null
        super.onDestroy()
    }
}
