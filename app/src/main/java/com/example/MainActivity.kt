package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class MainActivity : ComponentActivity() {
  private var gameWebView: WebView? = null
  private lateinit var adController: AdMobController

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    applyImmersiveFullscreen()
    adController = AdMobController(this)
    adController.initialize()

    setContent {
      MyApplicationTheme {
        Scaffold(
          modifier = Modifier.fillMaxSize(),
          containerColor = androidx.compose.ui.graphics.Color(0xFF070D19),
          contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
          SkyCarGameView(
            adController = adController,
            onWebViewReady = { webView ->
              gameWebView = webView
            },
            modifier = Modifier.fillMaxSize()
          )
        }
      }
    }
    window.decorView.post { applyImmersiveFullscreen() }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      applyImmersiveFullscreen()
    }
  }

  @Suppress("DEPRECATION")
  private fun applyImmersiveFullscreen() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else {
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
      }
    }
    window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.systemBarsBehavior =
      WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    controller.hide(WindowInsetsCompat.Type.systemBars())
  }

  override fun onResume() {
    super.onResume()
    applyImmersiveFullscreen()
    gameWebView?.onResume()
    gameWebView?.resumeTimers()
  }

  override fun onPause() {
    gameWebView?.pauseTimers()
    gameWebView?.onPause()
    super.onPause()
  }

  override fun onDestroy() {
    gameWebView?.let { wv ->
      wv.stopLoading()
      (wv.parent as? ViewGroup)?.removeView(wv)
      wv.removeAllViews()
      wv.destroy()
    }
    gameWebView = null
    super.onDestroy()
  }
}

class AdMobController(private val activity: Activity) {
  private val mainHandler = Handler(Looper.getMainLooper())
  private var rewardedAd: RewardedAd? = null
  private var interstitialAd: InterstitialAd? = null
  private var isLoadingRewarded = false
  private var isLoadingInterstitial = false

  fun initialize() {
    // Test ads disabled until real ad integration is configured
  }

  fun isRewardedReady(): Boolean = false

  fun isInterstitialReady(): Boolean = false

  fun loadRewardedAd() {}

  fun loadInterstitialAd() {}

  fun showRewardedAd(
    webView: WebView,
    rawPlacement: String
  ) {
    val placement = rawPlacement.replace(Regex("[^a-zA-Z0-9_]"), "")
    mainHandler.post {
      webView.evaluateJavascript(
        "window.__onRewardedAdResult && window.__onRewardedAdResult('$placement', true, 'bypassed')",
        null
      )
    }
  }

  fun showInterstitialAd(
    webView: WebView,
    rawActionTag: String
  ) {
    val actionTag = rawActionTag.replace(Regex("[^a-zA-Z0-9_]"), "")
    mainHandler.post {
      webView.evaluateJavascript(
        "window.__onInterstitialAdClosed && window.__onInterstitialAdClosed('$actionTag')",
        null
      )
    }
  }

  fun preloadAll() {}
}

private class AndroidAdsBridge(
  private val adController: AdMobController,
  private val webViewProvider: () -> WebView?
) {
  @JavascriptInterface
  fun isRewardedAdReady(): Boolean = adController.isRewardedReady()

  @JavascriptInterface
  fun isInterstitialAdReady(): Boolean = adController.isInterstitialReady()

  @JavascriptInterface
  fun preloadAds() {
    adController.preloadAll()
  }

  @JavascriptInterface
  fun showRewardedAd(placementId: String) {
    val view = webViewProvider() ?: return
    adController.showRewardedAd(view, placementId)
  }

  @JavascriptInterface
  fun showInterstitialAd(actionTag: String) {
    val view = webViewProvider() ?: return
    adController.showInterstitialAd(view, actionTag)
  }
}

private class AndroidBackBridge(
  private val onBackStateChanged: (Boolean) -> Unit
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  @JavascriptInterface
  fun setBackInterceptEnabled(enabled: Boolean) {
    mainHandler.post { onBackStateChanged(enabled) }
  }
}

private class AndroidAppBridge(
  private val activity: Activity?
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  @JavascriptInterface
  fun quitApp() {
    mainHandler.post {
      activity?.finishAffinity()
    }
  }

  @Suppress("DEPRECATION")
  @JavascriptInterface
  fun vibrate(durationMs: Long, intensityPercent: Int) {
    val ctx = activity ?: return
    val clampedPct = intensityPercent.coerceIn(0, 100)
    if (clampedPct <= 0 || durationMs <= 0L) return
    val safeDuration = durationMs.coerceIn(10L, 400L)
    runCatching {
      val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val mgr = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        mgr?.defaultVibrator
      } else {
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
      if (vibrator == null || !vibrator.hasVibrator()) return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val amplitude = ((clampedPct / 100f) * 255f).toInt().coerceIn(1, 255)
        vibrator.vibrate(VibrationEffect.createOneShot(safeDuration, amplitude))
      } else {
        val scaledMs = ((safeDuration * clampedPct) / 100L).coerceAtLeast(10L)
        vibrator.vibrate(scaledMs)
      }
    }
  }

  @JavascriptInterface
  fun getAppVersion(): String = BuildConfig.VERSION_NAME

  @JavascriptInterface
  fun copyToClipboard(label: String, text: String) {
    val ctx = activity ?: return
    mainHandler.post {
      runCatching {
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label.ifBlank { "Thorn Rush" }, text))
      }
    }
  }

  @JavascriptInterface
  fun openSupportEmail(email: String, subject: String, body: String) {
    val ctx = activity ?: return
    val safeEmail = email.trim().ifBlank { "luqmanthair260@gmail.com" }
    mainHandler.post {
      runCatching {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
          data = Uri.parse("mailto:$safeEmail")
          putExtra(Intent.EXTRA_EMAIL, arrayOf(safeEmail))
          putExtra(Intent.EXTRA_SUBJECT, subject)
          putExtra(Intent.EXTRA_TEXT, body)
        }
        ctx.startActivity(Intent.createChooser(intent, "Contact Support"))
      }.onFailure {
        runCatching {
          val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
          clipboard?.setPrimaryClip(ClipData.newPlainText("Support Email", safeEmail))
        }
        Toast.makeText(
          ctx,
          "No email app found. Copied $safeEmail to clipboard.",
          Toast.LENGTH_LONG
        ).show()
      }
    }
  }

  @JavascriptInterface
  fun openExternalUrl(url: String) {
    val ctx = activity ?: return
    val trimmed = url.trim()
    if (!trimmed.startsWith("https://")) return
    mainHandler.post {
      runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
        ctx.startActivity(intent)
      }.onFailure {
        Toast.makeText(ctx, "Unable to open link on this device.", Toast.LENGTH_SHORT).show()
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun SkyCarGameView(
  adController: AdMobController,
  onWebViewReady: (WebView) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var canInterceptBack by remember { mutableStateOf(false) }

  val webView = remember {
    WebView(context).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      setBackgroundColor(Color.parseColor("#070D19"))
      setLayerType(View.LAYER_TYPE_HARDWARE, null)
      isVerticalScrollBarEnabled = false
      isHorizontalScrollBarEnabled = false
      overScrollMode = View.OVER_SCROLL_NEVER
      isFocusable = true
      isFocusableInTouchMode = true

      settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        allowFileAccessFromFileURLs = true
        allowUniversalAccessFromFileURLs = true
        useWideViewPort = true
        loadWithOverviewMode = true
        cacheMode = WebSettings.LOAD_DEFAULT
        mediaPlaybackRequiresUserGesture = false
      }

      addJavascriptInterface(
        AndroidBackBridge { enabled -> canInterceptBack = enabled },
        "AndroidBackBridge"
      )
      addJavascriptInterface(
        AndroidAdsBridge(adController) { this },
        "AndroidAdsBridge"
      )
      addJavascriptInterface(
        AndroidAppBridge(context as? Activity),
        "AndroidAppBridge"
      )

      webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
          val level = consoleMessage?.messageLevel()
          val msg = consoleMessage?.message() ?: ""
          val source = consoleMessage?.sourceId() ?: ""
          val line = consoleMessage?.lineNumber() ?: 0
          Log.d("SkyCarJS", "[$level] $msg (line: $line, src: $source)")
          return true
        }
      }

      webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
          super.onPageStarted(view, url, favicon)
          Log.d("SkyCarWebView", "Started loading: $url")
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          super.onPageFinished(view, url)
          Log.d("SkyCarWebView", "Page finished loading: $url")
        }

        override fun onReceivedError(
          view: WebView?,
          request: WebResourceRequest?,
          error: WebResourceError?
        ) {
          super.onReceivedError(view, request, error)
          Log.e(
            "SkyCarWebView",
            "Error loading url: ${request?.url}, code: ${error?.errorCode}, desc: ${error?.description}"
          )
        }
      }

      loadUrl("file:///android_asset/index.html")
    }
  }

  BackHandler(enabled = canInterceptBack) {
    webView.evaluateJavascript("window.__handleAndroidBack && window.__handleAndroidBack()", null)
  }

  DisposableEffect(webView) {
    onWebViewReady(webView)
    onDispose {
      // WebView lifecycle is bounded by the activity
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(androidx.compose.ui.graphics.Color(0xFF070D19))
  ) {
    AndroidView(
      factory = { webView },
      modifier = Modifier
        .fillMaxSize()
        .testTag("sky_car_canvas_view")
    )
  }
}
