package com.jadeai.solvertracker.ui.home

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebViewClient
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadeai.solvertracker.ui.components.JellyBackground
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(
    onNavigateHistory: () -> Unit,
    onNavigateStats: () -> Unit,
    onNavigateCoffee: () -> Unit,
    onTaskCreated: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bridge = remember(viewModel, onNavigateHistory, onNavigateStats, onNavigateCoffee) {
        HomeBridge(
            viewModel = viewModel,
            onNavigateHistory = onNavigateHistory,
            onNavigateStats = onNavigateStats,
            onNavigateCoffee = onNavigateCoffee
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is HomeEvent.NavigateToTaskDetail -> onTaskCreated(event.taskId)
            }
        }
    }

    JellyBackground {
        ClayHomeWebView(
            state = state,
            bridge = bridge,
            modifier = Modifier.matchParentSize()
        )
    }
}

private class HomeBridge(
    private val viewModel: HomeViewModel,
    private val onNavigateHistory: () -> Unit,
    private val onNavigateStats: () -> Unit,
    private val onNavigateCoffee: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun updateTitle(value: String) {
        mainHandler.post { viewModel.updateTitle(value) }
    }

    @JavascriptInterface
    fun createTask() {
        mainHandler.post { viewModel.createTask() }
    }

    @JavascriptInterface
    fun navigateHistory() {
        mainHandler.post { onNavigateHistory() }
    }

    @JavascriptInterface
    fun navigateStats() {
        mainHandler.post { onNavigateStats() }
    }

    @JavascriptInterface
    fun navigateCoffee() {
        mainHandler.post { onNavigateCoffee() }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun ClayHomeWebView(
    state: HomeUiState,
    bridge: HomeBridge,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.defaultTextEncodingName = "UTF-8"
                addJavascriptInterface(bridge, "SolverHome")
                webViewClient = WebViewClient()
                loadHomeHtml(buildHomeHtml(state))
            }
        },
        update = { webView -> webView.evaluateJavascript("window.setHomeState && window.setHomeState(${state.toJson()})", null) }
    )
}

private fun WebView.loadHomeHtml(html: String) {
    val encodedHtml = Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_PADDING)
    loadData(encodedHtml, "text/html; charset=utf-8", "base64")
}

private fun buildHomeHtml(state: HomeUiState): String {
    val title = escapeJs(state.title)
    val error = state.error?.let(::escapeHtml).orEmpty()
    val hasError = state.error != null
    val isCreating = state.isCreating

    return """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <style>
            * {
              box-sizing: border-box;
              -webkit-tap-highlight-color: rgba(0, 0, 0, 0);
            }

            html,
            body {
              margin: 0;
              width: 100%;
              min-height: 100%;
              background: rgb(224, 229, 236);
              color: rgb(74, 85, 104);
              color-scheme: light;
              font-family: Nunito, Quicksand, "Rounded Mplus 1c", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
              line-height: 24px;
              text-size-adjust: 100%;
              -webkit-font-smoothing: antialiased;
            }

            body {
              overflow-x: hidden;
            }

            button,
            textarea {
              font: inherit;
            }

            .page {
              min-height: 100vh;
              display: flex;
              flex-direction: column;
              padding: 48px 20px 34px;
            }

            .top-row {
              display: flex;
              align-items: center;
              justify-content: space-between;
              gap: 16px;
            }

            .nav-icon,
            .nav-pill,
            .start-button,
            .input-card {
              border-width: 0;
              border-style: solid;
              border-color: rgba(255, 255, 255, 0.24);
              overflow: hidden;
              box-shadow:
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(163, 177, 198, 0.6) 9px 9px 16px 0,
                rgba(255, 255, 255, 0.5) -9px -9px 16px 0,
                rgba(163, 177, 198, 0.2) 5px 5px 10px 0 inset,
                rgba(255, 255, 255, 0.5) -5px -5px 10px 0 inset;
              transition: all 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
            }

            .nav-icon:active,
            .nav-pill:active,
            .start-button:active {
              transform: scale(0.96);
            }

            .nav-icon {
              width: 58px;
              height: 58px;
              display: inline-flex;
              align-items: center;
              justify-content: center;
              border-radius: 9999px;
              background-color: rgb(162, 210, 255);
              color: rgb(67, 101, 139);
            }

            .nav-icon.pink {
              background-color: rgb(255, 179, 217);
              color: rgb(138, 72, 96);
            }

            .nav-pill {
              height: 52px;
              min-width: 142px;
              display: inline-flex;
              align-items: center;
              justify-content: center;
              gap: 8px;
              padding: 0 22px;
              border-radius: 50px;
              background-color: rgb(255, 179, 217);
              color: rgb(138, 72, 96);
              font-weight: 800;
              font-size: 15px;
            }

            .center {
              flex: 1;
              display: flex;
              align-items: center;
              justify-content: center;
              padding: 38px 0;
            }

            .input-card {
              width: 100%;
              min-height: 268px;
              padding: 24px;
              border-radius: 35px;
              background-color: rgba(255, 255, 255, 0.54);
              color: rgb(74, 85, 104);
            }

            .title {
              margin: 0;
              font-size: 24px;
              line-height: 32px;
              font-weight: 900;
              letter-spacing: -0.02em;
            }

            .subtitle {
              margin: 4px 0 0;
              font-size: 14px;
              line-height: 22px;
              font-weight: 700;
              color: rgba(74, 85, 104, 0.62);
            }

            .field-wrap {
              margin-top: 16px;
              border-radius: 28px;
              background-color: rgb(224, 229, 236);
              box-shadow:
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(163, 177, 198, 0.7) 6px 6px 10px 0 inset,
                rgba(255, 255, 255, 0.8) -6px -6px 10px 0 inset;
              padding: 16px;
            }

            textarea {
              width: 100%;
              height: 92px;
              display: block;
              resize: none;
              border: 0;
              outline: 0;
              background: transparent;
              color: rgb(74, 85, 104);
              font-size: 18px;
              line-height: 28px;
              font-weight: 800;
            }

            textarea::placeholder {
              color: rgba(74, 85, 104, 0.42);
            }

            .error {
              display: ${if (hasError) "block" else "none"};
              margin: 10px 4px 0;
              color: rgb(138, 72, 96);
              font-size: 13px;
              font-weight: 800;
            }

            .start-button {
              width: 100%;
              height: 58px;
              margin-top: 16px;
              display: flex;
              align-items: center;
              justify-content: center;
              gap: 8px;
              border-radius: 50px;
              background-color: rgb(255, 179, 217);
              color: rgb(138, 72, 96);
              font-size: 16px;
              font-weight: 900;
            }

            .start-button:disabled {
              opacity: 0.72;
            }

            svg {
              width: 24px;
              height: 24px;
              display: block;
              fill: none;
              stroke: currentColor;
              stroke-width: 2.5;
              stroke-linecap: round;
              stroke-linejoin: round;
            }

            .spinner {
              width: 18px;
              height: 18px;
              border-radius: 9999px;
              border: 2px solid rgba(138, 72, 96, 0.28);
              border-top-color: rgb(138, 72, 96);
              animation: spin 0.8s linear infinite;
            }

            @keyframes spin {
              to { transform: rotate(360deg); }
            }
          </style>
        </head>
        <body>
          <main class="page">
            <nav class="top-row">
              <button class="nav-icon" type="button" onclick="SolverHome.navigateHistory()" aria-label="history">${historySvg()}</button>
              <button class="nav-pill" type="button" onclick="SolverHome.navigateStats()">${statsSvg()}<span>&#32479;&#35745;</span></button>
              <button class="nav-icon pink" type="button" onclick="SolverHome.navigateCoffee()" aria-label="coffee">${coffeeSvg()}</button>
            </nav>

            <section class="center">
              <article class="input-card">
                <h1 class="title">&#20889;&#19979;&#20320;&#35201;&#35299;&#20915;&#30340;&#20107;</h1>
                <p class="subtitle">&#31616;&#21333;&#19968;&#28857;&#20063;&#27809;&#20851;&#31995;&#12290;</p>
                <div class="field-wrap">
                  <textarea id="title" placeholder="&#20363;&#22914;&#65306;&#25226;&#30331;&#24405;&#23849;&#28291;&#20462;&#22909;">$title</textarea>
                </div>
                <div class="error">$error</div>
                <button class="start-button" type="button" onclick="SolverHome.createTask()" ${if (isCreating) "disabled" else ""}>
                  ${if (isCreating) "<span class=\"spinner\"></span>" else ""}
                  <span>&#24320;&#22987;&#35760;&#24405;</span>
                </button>
              </article>
            </section>
          </main>

          <script>
            const title = document.getElementById('title');
            title.addEventListener('input', () => SolverHome.updateTitle(title.value));

            window.setHomeState = function(state) {
              const title = document.getElementById('title');
              if (document.activeElement !== title || state.title === '') {
                title.value = state.title || '';
              }

              const error = document.querySelector('.error');
              error.textContent = state.error || '';
              error.style.display = state.error ? 'block' : 'none';

              const button = document.querySelector('.start-button');
              button.disabled = !!state.isCreating;
              button.innerHTML = (state.isCreating ? '<span class="spinner"></span>' : '') + '<span>开始记录</span>';
            };
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun HomeUiState.toJson(): String {
    return "{" +
        "\"title\":\"${escapeJson(title)}\"," +
        "\"isCreating\":$isCreating," +
        "\"error\":${error?.let { "\"${escapeJson(it)}\"" } ?: "null"}" +
        "}"
}

private fun historySvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M3 12a9 9 0 1 0 3-6.7"></path><path d="M3 3v6h6"></path><path d="M12 7v5l3 2"></path></svg>
""".trimIndent()

private fun statsSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M3 3v18h18"></path><path d="M18 17V9"></path><path d="M13 17V5"></path><path d="M8 17v-3"></path></svg>
""".trimIndent()

private fun coffeeSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M10 2v2"></path><path d="M14 2v2"></path><path d="M16 8a1 1 0 0 1 1 1v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h11Z"></path><path d="M17 8h1a4 4 0 0 1 0 8h-1"></path><path d="M6 22h12"></path></svg>
""".trimIndent()

private fun escapeHtml(value: String): String = buildString(value.length) {
    value.forEach { char ->
        append(
            when (char) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> char
            }
        )
    }
}

private fun escapeJs(value: String): String = escapeHtml(value)

private fun escapeJson(value: String): String = buildString(value.length) {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
