package com.jadeai.solvertracker.ui.settings

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadeai.solvertracker.ui.components.JellyBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bridge = remember(viewModel) { SettingsBridge(viewModel) }

    JellyBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(text = "API 配置", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color(0xFF4A5568),
                        navigationIconContentColor = Color(0xFF4A5568)
                    )
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                SettingsWebView(
                    html = buildSettingsHtml(state),
                    stateJson = state.toJson(),
                    bridge = bridge,
                    modifier = Modifier.fillMaxSize()
                )
                if (state.isTesting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

private class SettingsBridge(
    private val viewModel: SettingsViewModel
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun updateApiKey(value: String) {
        mainHandler.post { viewModel.updateApiKey(value) }
    }

    @JavascriptInterface
    fun updateBaseUrl(value: String) {
        mainHandler.post { viewModel.updateBaseUrl(value) }
    }

    @JavascriptInterface
    fun updateModel(value: String) {
        mainHandler.post { viewModel.updateModel(value) }
    }

    @JavascriptInterface
    fun loadModels() {
        mainHandler.post { viewModel.loadModels() }
    }

    @JavascriptInterface
    fun save() {
        mainHandler.post { viewModel.save() }
    }

    @JavascriptInterface
    fun testConnection() {
        mainHandler.post { viewModel.testConnection() }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun SettingsWebView(
    html: String,
    stateJson: String,
    bridge: SettingsBridge,
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
                webViewClient = WebViewClient()
                addJavascriptInterface(bridge, "SettingsBridge")
                loadSettingsHtml(html)
            }
        },
        update = { webView ->
            webView.evaluateJavascript("window.setSettingsState && window.setSettingsState($stateJson)", null)
        }
    )
}

private fun WebView.loadSettingsHtml(html: String) {
    val encodedHtml = Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_PADDING)
    loadData(encodedHtml, "text/html; charset=utf-8", "base64")
}

private fun buildSettingsHtml(state: SettingsUiState): String = """
    <!doctype html>
    <html lang="zh-CN">
    <head>
      <meta charset="UTF-8" />
      <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
      <style>${sharedClayCss()}</style>
    </head>
    <body>
      <main class="page">
        <section class="stack">
          <article class="clay-card blue">
            <div class="card-top"><span class="label">DeepSeek API</span><div class="icon-bubble">${keySvg()}</div></div>
            <div class="body-text">配置 Base URL、API Key 和模型。后续任务完成后会自动复盘分析。</div>
          </article>

          <article class="clay-card soft form-card">
            <label class="field-label">Base URL</label>
            <div class="field-wrap"><input id="baseUrl" value="${escapeHtml(state.baseUrl)}" placeholder="https://api.deepseek.com" /></div>

            <label class="field-label">API Key</label>
            <div class="field-wrap key-row"><input id="apiKey" type="password" value="${escapeHtml(state.apiKey)}" placeholder="sk-..." /><button class="mini" type="button" onclick="toggleKey()">显示</button></div>

            <label class="field-label">Model</label>
            <div class="field-wrap model-row">
              <input id="model" value="${escapeHtml(state.model)}" placeholder="deepseek-chat" />
              <button class="mini" id="loadModelsBtn" type="button" onclick="SettingsBridge.loadModels()">${if (state.isLoadingModels) "拉取中" else "拉取"}</button>
            </div>
            <div class="model-picker" id="modelPicker">${modelOptionsHtml(state)}</div>

            <div class="status ${if (state.error != null) "error" else ""}" id="status">${escapeHtml(state.error ?: state.testMessage ?: if (state.isSaved) "已保存" else "")}</div>

            <div class="actions">
              <button class="pill blue-button" type="button" onclick="SettingsBridge.testConnection()">${if (state.isTesting) "测试中..." else "测试连接"}</button>
              <button class="pill pink-button" type="button" onclick="SettingsBridge.save()">${if (state.isSaved) "已保存" else "保存"}</button>
            </div>
          </article>
        </section>
      </main>
      <script>
        const apiKey = document.getElementById('apiKey');
        const baseUrl = document.getElementById('baseUrl');
        const model = document.getElementById('model');
        const modelPicker = document.getElementById('modelPicker');
        const loadModelsBtn = document.getElementById('loadModelsBtn');
        apiKey.addEventListener('input', () => SettingsBridge.updateApiKey(apiKey.value));
        baseUrl.addEventListener('input', () => SettingsBridge.updateBaseUrl(baseUrl.value));
        model.addEventListener('input', () => SettingsBridge.updateModel(model.value));

        function toggleKey() {
          apiKey.type = apiKey.type === 'password' ? 'text' : 'password';
        }

        window.setSettingsState = function(state) {
          if (document.activeElement !== apiKey) apiKey.value = state.apiKey || '';
          if (document.activeElement !== baseUrl) baseUrl.value = state.baseUrl || '';
          if (document.activeElement !== model) model.value = state.model || '';
          renderModels(state.models || [], state.model || '');
          const status = document.getElementById('status');
          status.textContent = state.error || state.testMessage || (state.isSaved ? '已保存' : '');
          status.classList.toggle('error', !!state.error);
          loadModelsBtn.textContent = state.isLoadingModels ? '拉取中' : '拉取';
          document.querySelector('.blue-button').textContent = state.isTesting ? '测试中...' : '测试连接';
          document.querySelector('.pink-button').textContent = state.isSaved ? '已保存' : '保存';
        };

        function escapeText(value) {
          return String(value).replace(/[&<>"']/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
        }

        function selectModel(value) {
          model.value = value;
          SettingsBridge.updateModel(value);
        }

        function renderModels(models, selected) {
          if (!modelPicker) return;
          if (!models.length) {
            modelPicker.innerHTML = '<div class="model-empty">拉取后可在这里点选模型，也可以继续手动填写。</div>';
            return;
          }
          modelPicker.innerHTML = models.map((item) => {
            const active = item === selected ? ' active' : '';
            return '<button class="model-chip' + active + '" type="button" onclick="selectModel(\'' + item.replace(/\\/g, '\\\\').replace(/'/g, "\\'") + '\')">' + escapeText(item) + '</button>';
          }).join('');
        }
      </script>
    </body>
    </html>
""".trimIndent()

private fun SettingsUiState.toJson(): String = "{" +
    "\"apiKey\":\"${escapeJson(apiKey)}\"," +
    "\"baseUrl\":\"${escapeJson(baseUrl)}\"," +
    "\"model\":\"${escapeJson(model)}\"," +
    "\"models\":[${models.joinToString(",") { "\"${escapeJson(it)}\"" }}]," +
    "\"isSaved\":$isSaved," +
    "\"isTesting\":$isTesting," +
    "\"isLoadingModels\":$isLoadingModels," +
    "\"testMessage\":${testMessage?.let { "\"${escapeJson(it)}\"" } ?: "null"}," +
    "\"error\":${error?.let { "\"${escapeJson(it)}\"" } ?: "null"}" +
    "}"

private fun modelOptionsHtml(state: SettingsUiState): String {
    if (state.models.isEmpty()) {
        return "<div class=\"model-empty\">拉取后可在这里点选模型，也可以继续手动填写。</div>"
    }
    return state.models.joinToString(separator = "") { model ->
        val activeClass = if (model == state.model) " active" else ""
        "<button class=\"model-chip$activeClass\" type=\"button\" onclick=\"selectModel('${escapeJsSingleQuoted(model)}')\">${escapeHtml(model)}</button>"
    }
}

private fun sharedClayCss(): String = """
    * { box-sizing: border-box; -webkit-tap-highlight-color: rgba(0,0,0,0); }
    html, body { margin: 0; width: 100%; min-height: 100%; background: rgb(224,229,236); color: rgb(74,85,104); color-scheme: light; font-family: Nunito, Quicksand, "Rounded Mplus 1c", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 24px; text-size-adjust: 100%; -webkit-font-smoothing: antialiased; }
    body { overflow-x: hidden; }
    button, input { font: inherit; }
    .page { width: 100%; max-width: 1280px; min-height: 100vh; margin: 0 auto; padding: 24px 20px 40px; }
    .stack { display: grid; grid-template-columns: 1fr; gap: 20px; }
    .clay-card { display: block; width: 100%; padding: 24px; border-width: 0; border-style: solid; border-color: rgba(255,255,255,.24); border-radius: 35px; overflow: hidden; box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; }
    .blue { background-color: rgb(162,210,255); color: rgb(67,101,139); }
    .soft { background: rgba(255,255,255,.54); color: rgb(74,85,104); }
    .card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
    .label { font-size: 14px; line-height: 20px; font-weight: 800; opacity: .75; }
    .icon-bubble { width: 40px; height: 40px; border-radius: 9999px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.4); flex: 0 0 auto; }
    svg { width: 20px; height: 20px; display: block; fill: none; stroke: currentColor; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
    .body-text { font-size: 15px; line-height: 24px; font-weight: 700; opacity: .78; }
    .field-label { display: block; margin: 16px 4px 8px; font-size: 13px; line-height: 18px; font-weight: 900; opacity: .72; }
    .field-label:first-child { margin-top: 0; }
    .field-wrap { display: flex; align-items: center; gap: 10px; min-height: 58px; border-radius: 28px; background: rgb(224,229,236); box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.7) 6px 6px 10px 0 inset, rgba(255,255,255,.8) -6px -6px 10px 0 inset; padding: 8px 14px; }
    input { width: 100%; border: 0; outline: 0; background: transparent; color: rgb(74,85,104); font-size: 15px; line-height: 24px; font-weight: 800; min-width: 0; }
    input::placeholder { color: rgba(74,85,104,.42); }
    .mini { height: 38px; padding: 0 14px; border: 0; border-radius: 9999px; background: rgba(255,255,255,.42); color: rgb(74,85,104); font-size: 13px; font-weight: 900; flex: 0 0 auto; }
    .model-row { align-items: center; }
    .model-picker { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
    .model-chip { min-height: 38px; padding: 8px 12px; border: 0; border-radius: 9999px; background: rgba(255,255,255,.48); color: rgba(74,85,104,.76); font-size: 13px; line-height: 18px; font-weight: 900; box-shadow: rgba(163,177,198,.28) 4px 4px 8px 0, rgba(255,255,255,.52) -4px -4px 8px 0; }
    .model-chip.active { background: rgb(255,179,217); color: rgb(138,72,96); box-shadow: rgba(163,177,198,.44) 6px 6px 10px 0, rgba(255,255,255,.45) -6px -6px 10px 0, rgba(163,177,198,.16) 4px 4px 8px 0 inset, rgba(255,255,255,.45) -4px -4px 8px 0 inset; }
    .model-empty { width: 100%; padding: 10px 4px 0; color: rgba(74,85,104,.58); font-size: 12px; line-height: 18px; font-weight: 800; }
    .status { min-height: 22px; margin: 14px 4px 0; color: rgb(61,107,79); font-size: 13px; line-height: 20px; font-weight: 800; word-break: break-word; }
    .status.error { color: rgb(138,72,96); }
    .actions { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 18px; }
    .pill { height: 58px; border: 0; border-radius: 50px; font-size: 16px; font-weight: 900; box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; }
    .pill:active { transform: scale(.97); }
    .blue-button { background: rgb(162,210,255); color: rgb(67,101,139); }
    .pink-button { background: rgb(255,179,217); color: rgb(138,72,96); }
""".trimIndent()

private fun keySvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M15.5 7.5 19 4"></path><path d="m17 2 5 5"></path><path d="M9 14a5 5 0 1 1 3.54-8.54"></path><path d="M4 20l5-5"></path><path d="m7 17 2 2"></path></svg>
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

private fun escapeJsSingleQuoted(value: String): String = buildString(value.length) {
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '\'' -> append("\\'")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(char)
        }
    }
}

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
