package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
  }

  override fun onResume() {
    super.onResume()
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
    Thread {
      runCatching {
        MobileAds.initialize(activity) {
          mainHandler.post {
            loadRewardedAd()
            loadInterstitialAd()
          }
        }
      }.onFailure { err ->
        Log.w("AdMobController", "MobileAds init skipped: ${err.message}")
      }
    }.start()
  }

  fun isRewardedReady(): Boolean = rewardedAd != null

  fun isInterstitialReady(): Boolean = interstitialAd != null

  fun loadRewardedAd() {
    if (rewardedAd != null || isLoadingRewarded) return
    isLoadingRewarded = true
    val unitId = activity.getString(R.string.admob_rewarded_id)
    runCatching {
      RewardedAd.load(
        activity,
        unitId,
        AdRequest.Builder().build(),
        object : RewardedAdLoadCallback() {
          override fun onAdLoaded(ad: RewardedAd) {
            isLoadingRewarded = false
            rewardedAd = ad
            Log.d("AdMobController", "RewardedAd loaded")
          }

          override fun onAdFailedToLoad(error: LoadAdError) {
            isLoadingRewarded = false
            rewardedAd = null
            Log.w("AdMobController", "RewardedAd load failed: ${error.message}")
          }
        }
      )
    }.onFailure {
      isLoadingRewarded = false
    }
  }

  fun loadInterstitialAd() {
    if (interstitialAd != null || isLoadingInterstitial) return
    isLoadingInterstitial = true
    val unitId = activity.getString(R.string.admob_interstitial_id)
    runCatching {
      InterstitialAd.load(
        activity,
        unitId,
        AdRequest.Builder().build(),
        object : InterstitialAdLoadCallback() {
          override fun onAdLoaded(ad: InterstitialAd) {
            isLoadingInterstitial = false
            interstitialAd = ad
            Log.d("AdMobController", "InterstitialAd loaded")
          }

          override fun onAdFailedToLoad(error: LoadAdError) {
            isLoadingInterstitial = false
            interstitialAd = null
            Log.w("AdMobController", "InterstitialAd load failed: ${error.message}")
          }
        }
      )
    }.onFailure {
      isLoadingInterstitial = false
    }
  }

  fun showRewardedAd(
    webView: WebView,
    rawPlacement: String
  ) {
    val placement = rawPlacement.replace(Regex("[^a-zA-Z0-9_]"), "")
    mainHandler.post {
      val ad = rewardedAd
      if (ad == null) {
        loadRewardedAd()
        webView.evaluateJavascript(
          "window.__onRewardedAdResult && window.__onRewardedAdResult('$placement', false, 'loading')",
          null
        )
        return@post
      }

      var rewardEarned = false
      webView.evaluateJavascript("window.__onAdWillShow && window.__onAdWillShow()", null)

      ad.fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
          rewardedAd = null
          loadRewardedAd()
          webView.evaluateJavascript(
            "window.__onRewardedAdResult && window.__onRewardedAdResult('$placement', $rewardEarned, 'dismissed')",
            null
          )
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
          rewardedAd = null
          loadRewardedAd()
          webView.evaluateJavascript(
            "window.__onRewardedAdResult && window.__onRewardedAdResult('$placement', false, 'failed')",
            null
          )
        }
      }

      rewardedAd = null
      ad.show(activity) {
        rewardEarned = true
      }
    }
  }

  fun showInterstitialAd(
    webView: WebView,
    rawActionTag: String
  ) {
    val actionTag = rawActionTag.replace(Regex("[^a-zA-Z0-9_]"), "")
    mainHandler.post {
      val ad = interstitialAd
      if (ad == null) {
        loadInterstitialAd()
        webView.evaluateJavascript(
          "window.__onInterstitialAdClosed && window.__onInterstitialAdClosed('$actionTag')",
          null
        )
        return@post
      }

      webView.evaluateJavascript("window.__onAdWillShow && window.__onAdWillShow()", null)

      ad.fullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
          interstitialAd = null
          loadInterstitialAd()
          webView.evaluateJavascript(
            "window.__onInterstitialAdClosed && window.__onInterstitialAdClosed('$actionTag')",
            null
          )
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
          interstitialAd = null
          loadInterstitialAd()
          webView.evaluateJavascript(
            "window.__onInterstitialAdClosed && window.__onInterstitialAdClosed('$actionTag')",
            null
          )
        }
      }

      interstitialAd = null
      ad.show(activity)
    }
  }
  fun preloadAll() {
    mainHandler.post {
      loadRewardedAd()
      loadInterstitialAd()
    }
  }
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
