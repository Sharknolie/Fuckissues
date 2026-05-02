package com.jadeai.solvertracker.ui.coffee

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadeai.solvertracker.data.remote.dto.CoffeeCauseCategoryDto
import com.jadeai.solvertracker.data.remote.dto.CoffeeCauseContent
import com.jadeai.solvertracker.data.remote.dto.CoffeeCauseItemDto
import com.jadeai.solvertracker.ui.components.JellyBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoffeeScreen(
    onNavigateBack: () -> Unit,
    onNavigateSettings: () -> Unit,
    viewModel: CoffeeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bridge = remember(viewModel, onNavigateSettings) { CoffeeBridge(viewModel, onNavigateSettings) }
    val webViewHolder = remember { arrayOfNulls<WebView>(1) }
    var lastLoadedHtml by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.chatStreamEvents.collectLatest { event ->
            when (event) {
                is CoffeeChatStreamEvent.Delta -> {
                    webViewHolder[0]?.post {
                        webViewHolder[0]?.evaluateJavascript(
                            "appendAssistantDelta(${jsStringLiteral(event.content)})",
                            null
                        )
                    }
                }
            }
        }
    }

    JellyBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(text = "\u5496\u5561\u6c89\u601d\u9986", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "\u8fd4\u56de")
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
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    CoffeeWebView(
                        html = buildCoffeeHtml(state),
                        bridge = bridge,
                        shouldLoad = { html ->
                            val changed = html != lastLoadedHtml
                            if (changed) lastLoadedHtml = html
                            changed
                        },
                        onWebView = { webViewHolder[0] = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private class CoffeeBridge(
    private val viewModel: CoffeeViewModel,
    private val onNavigateSettings: () -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun analyzeSelectedTask() {
        mainHandler.post { viewModel.analyzeSelectedTask() }
    }

    @JavascriptInterface
    fun classifyNow() {
        mainHandler.post { viewModel.analyzeSelectedTask() }
    }

    @JavascriptInterface
    fun selectTask(taskId: String) {
        val id = taskId.toLongOrNull() ?: return
        mainHandler.post { viewModel.selectTask(id) }
    }

    @JavascriptInterface
    fun selectCauseItem(stepIndex: String) {
        val index = stepIndex.toIntOrNull() ?: return
        mainHandler.post { viewModel.selectCauseItem(index) }
    }

    @JavascriptInterface
    fun clearCauseItem() {
        mainHandler.post { viewModel.clearCauseItem() }
    }

    @JavascriptInterface
    fun openChat() {
        mainHandler.post { viewModel.openChat() }
    }

    @JavascriptInterface
    fun closeChat() {
        mainHandler.post { viewModel.closeChat() }
    }

    @JavascriptInterface
    fun selectChatStep(stepIndex: String) {
        val index = stepIndex.toIntOrNull()
        mainHandler.post { viewModel.selectChatStep(index) }
    }

    @JavascriptInterface
    fun sendChatMessage(content: String) {
        mainHandler.post { viewModel.sendChatMessage(content) }
    }

    @JavascriptInterface
    fun openSettings() {
        mainHandler.post { onNavigateSettings() }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun CoffeeWebView(
    html: String,
    bridge: CoffeeBridge,
    shouldLoad: (String) -> Boolean,
    onWebView: (WebView) -> Unit,
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
                addJavascriptInterface(bridge, "CoffeeBridge")
                onWebView(this)
                if (shouldLoad(html)) loadCoffeeHtml(html)
            }
        },
        update = { webView ->
            onWebView(webView)
            if (shouldLoad(html)) webView.loadCoffeeHtml(html)
        }
    )
}

private fun WebView.loadCoffeeHtml(html: String) {
    val encodedHtml = Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_PADDING)
    loadData(encodedHtml, "text/html; charset=utf-8", "base64")
}

private fun buildCoffeeHtml(state: CoffeeUiState): String {
    val body = buildString {
        append(headerCard(state))
        append("<button class=\"pill settings\" type=\"button\" onclick=\"CoffeeBridge.openSettings()\">API 配置</button>")
        append(taskSelector(state))

        if (!state.hasApiKey) {
            append(clayCard("soft", """
                <div class="card-top"><span class="label">未配置 API Key</span><div class="icon-bubble">${alertSvg()}</div></div>
                <div class="body-text">请先进入 API 配置。配置后才能分析单个任务的外界/内在因素。</div>
            """.trimIndent()))
        }

        state.error?.takeIf { it.isNotBlank() }?.let { error ->
            append(clayCard("soft error", """
                <div class="card-top"><span class="label">分析失败</span><div class="icon-bubble">${alertSvg()}</div></div>
                <div class="body-text">${escapeHtml(error)}</div>
            """.trimIndent()))
        }

        if (state.tasks.isEmpty()) {
            append(clayCard("green", """
                <div class="card-top"><span class="label">还没有可分析的任务</span><div class="icon-bubble">${bellSvg()}</div></div>
                <div class="body-text">先在任务详情里记录至少一个问题和解决办法。</div>
            """.trimIndent()))
        } else {
            if (state.isAnalyzing) {
                append(analyzingIndicatorHtml())
            } else {
                val analyzeText = if (state.causeResult == null) "分析这个任务" else "重新分析这个任务"
                append("<button class=\"pill analyze\" type=\"button\" onclick=\"CoffeeBridge.analyzeSelectedTask()\">$analyzeText</button>")
            }

            state.causeResult?.let { result -> append(causeResultHtml(result, state.causeAnalyzedAt)) }
        }
    }

    return """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <style>${sharedClayCss()}</style>
        </head>
        <body>
          <main class="page"><section class="stack">$body</section></main>
          ${causeSheetHtml()}
          ${chatSheetHtml(state)}
          <script>
            function syncViewport() {
              const viewport = window.visualViewport;
              const layoutHeight = Math.max(document.documentElement.clientHeight || 0, window.innerHeight || 0);
              const height = viewport ? viewport.height : window.innerHeight;
              const offsetTop = viewport ? viewport.offsetTop : 0;
              const resizedByKeyboard = Math.max(0, layoutHeight - height - offsetTop) > 80;
              let keyboardHeight = 0;
              if (document.body.classList.contains('keyboard-open') && !resizedByKeyboard && height > 480) {
                keyboardHeight = Math.min(320, Math.max(220, Math.round(layoutHeight * 0.42)));
              }
              document.documentElement.style.setProperty('--viewport-height', height + 'px');
              document.documentElement.style.setProperty('--viewport-offset-top', offsetTop + 'px');
              document.documentElement.style.setProperty('--keyboard-height', keyboardHeight + 'px');
            }
            function toggleTaskMenu() {
              const menu = document.getElementById('taskMenu');
              if (menu) menu.classList.toggle('open');
            }
            function setText(id, value) {
              const target = document.getElementById(id);
              if (target) target.textContent = value || '';
            }
            function scrollChatToBottom() {
              const list = document.getElementById('chatMessages');
              if (list) list.scrollTop = list.scrollHeight;
            }
            function appendAssistantDelta(value) {
              if (!value) return;
              const list = document.getElementById('chatMessages');
              if (!list) return;
              let bubble = list.querySelector('.chat-message.assistant:last-child > div');
              if (!bubble) {
                const row = document.createElement('div');
                row.className = 'chat-message assistant';
                bubble = document.createElement('div');
                row.appendChild(bubble);
                list.appendChild(row);
              }
              bubble.textContent = (bubble.textContent || '') + value.replace(/null/g, '');
              scrollChatToBottom();
            }
            function openCauseSheet(source) {
              const sheet = document.getElementById('causeSheet');
              if (!sheet || !source) return;
              setText('causeSheetTitle', source.dataset.title);
              setText('causeSheetSubtitle', source.dataset.subtitle);
              setText('causeRawProblem', source.dataset.rawProblem);
              setText('causeNormalizedProblem', source.dataset.normalizedProblem);
              setText('causeImprovement', source.dataset.improvement);
              sheet.classList.add('show');
            }
            function closeCauseSheet() {
              const sheet = document.getElementById('causeSheet');
              if (sheet) sheet.classList.remove('show');
            }
            function submitChatMessage() {
              const input = document.getElementById('chatInput');
              if (!input) return;
              const message = input.value.trim();
              if (!message) return;
              input.value = '';
              CoffeeBridge.sendChatMessage(message);
            }
            function handleChatKey(event) {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                submitChatMessage();
              }
            }
            function keepChatInputVisible() {
              const input = document.getElementById('chatInput');
              if (!input) return;
              document.body.classList.add('keyboard-open');
              syncViewport();
              setTimeout(() => {
                syncViewport();
                input.scrollIntoView({ block: 'end', inline: 'nearest', behavior: 'smooth' });
              }, 120);
            }
            setTimeout(() => {
              scrollChatToBottom();
            }, 80);
            document.addEventListener('focusin', event => {
              if (event.target && event.target.id === 'chatInput') keepChatInputVisible();
            });
            document.addEventListener('focusout', () => {
              setTimeout(() => {
                if (!document.activeElement || document.activeElement.id !== 'chatInput') {
                  document.body.classList.remove('keyboard-open');
                  syncViewport();
                }
              }, 120);
            });
            syncViewport();
            if (window.visualViewport) {
              window.visualViewport.addEventListener('resize', syncViewport);
              window.visualViewport.addEventListener('scroll', syncViewport);
            }
            window.addEventListener('resize', syncViewport);
            window.addEventListener('orientationchange', syncViewport);
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun headerCard(state: CoffeeUiState): String = clayCard("pink", """
    <div class="card-top"><span class="label">单任务原因分析</span><button class="icon-bubble icon-button" type="button" onclick="CoffeeBridge.openChat()">${coffeeSvg()}</button></div>
    <div class="value">${state.totalProblems} 条</div>
    <div class="subtitle">选择一个任务，只分析这个任务下的问题和解决办法</div>
""".trimIndent())

private fun taskSelector(state: CoffeeUiState): String {
    if (state.tasks.isEmpty()) return ""
    val options = state.tasks.joinToString(separator = "") { task ->
        val activeClass = if (task.id == state.selectedTaskId) " active" else ""
        """
            <button class="task-option$activeClass" type="button" onclick="CoffeeBridge.selectTask('${task.id}')">
              <span>${escapeHtml(task.title)}</span>
            </button>
        """.trimIndent()
    }
    val selectedTitle = state.tasks.firstOrNull { it.id == state.selectedTaskId }?.let {
        escapeHtml(it.title)
    }.orEmpty()
    return clayCard("soft selector-card", """
        <label class="field-label">选择任务</label>
        <button class="select-trigger" type="button" onclick="toggleTaskMenu()">
          <span>$selectedTitle</span>
          <span class="chevron">⌄</span>
        </button>
        <div class="task-menu" id="taskMenu">$options</div>
    """.trimIndent())
}

private fun analyzingIndicatorHtml(): String = """
    <div class="analysis-dynamic" aria-live="polite">
      <div class="dynamic-head">
        <span>AI 正在整理归因</span>
        <span class="dot-row"><i></i><i></i><i></i></span>
      </div>
      <div class="inset dynamic-track"><div class="loading-bar"></div></div>
    </div>
""".trimIndent()

private fun causeResultHtml(result: CoffeeCauseContent, analyzedAt: Long?): String = buildString {
    val cacheNote = analyzedAt?.let { time ->
        "<div class=\"cache-note\">已保存上次分析 · ${formatAnalyzedAt(time)}</div>"
    }.orEmpty()
    append(clayCard("blue", """
        <div class="card-top"><span class="label">AI 归因总结</span><div class="icon-bubble">${chartSvg()}</div></div>
        $cacheNote
        <div class="body-text summary">${escapeHtml(result.summary)}</div>
    """.trimIndent()))
    append(ratioBalls(result))
    if (result.categories.isNotEmpty()) {
        append(clayCard("soft", """
            <div class="card-top"><span class="label">归因分类</span><div class="icon-bubble">${chartSvg()}</div></div>
            <div class="chart-list">${categoryBars(result.categories)}</div>
        """.trimIndent()))
    }
    if (result.items.isNotEmpty()) {
        append("<h2 class=\"section-title\">单条问题归因</h2>")
        result.items.forEach { append(causeItemCard(it)) }
    }
    if (result.advice.isNotEmpty()) {
        append(clayCard("green", """
            <div class="card-top"><span class="label">下次建议</span><div class="icon-bubble">${sparkSvg()}</div></div>
            <ul class="advice">${result.advice.joinToString("") { "<li>${escapeHtml(it)}</li>" }}</ul>
        """.trimIndent()))
    }
}

private fun ratioBalls(result: CoffeeCauseContent): String {
    val balls = listOf(
        ratioBall("外界因素", result.externalRatio, "external-ball", 0),
        ratioBall("内在因素", result.internalRatio, "internal-ball", 1),
        ratioBall("混合因素", result.mixedRatio, "mixed-ball", 2)
    ).filter { it.isNotBlank() }
    if (balls.isEmpty()) return ""
    return """
        <section class="ball-section">
          <div class="ball-section-title">因素占比</div>
          <div class="ratio-balls">${balls.joinToString("")}</div>
        </section>
    """.trimIndent()
}

private fun ratioBall(
    label: String,
    value: Int,
    className: String,
    index: Int
): String {
    val percent = value.coerceIn(0, 100)
    if (percent == 0) return ""
    val size = (80 + (percent / 100f) * 144).toInt().coerceIn(80, 224)
    return """
        <div class="ball-wrap" style="animation-delay:${index * 0.3}s">
          <div class="ratio-ball $className" style="width:${size}px;height:${size}px"><span>${percent}%</span></div>
          <div class="ball-label">$label</div>
        </div>
    """.trimIndent()
}

private fun categoryBars(categories: List<CoffeeCauseCategoryDto>): String {
    val max = categories.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    return categories.joinToString(separator = "") { category ->
        val percent = ((category.count.toFloat() / max.toFloat()) * 100f).toInt().coerceIn(4, 100)
        val fill = when (category.factorType.lowercase(Locale.US)) {
            "external" -> "blue-fill"
            "internal" -> "pink-fill"
            else -> "green-fill"
        }
        """
            <div class="category-row">
              <div class="category-head"><span>${escapeHtml(category.name)}</span><span>${category.count}</span></div>
              <div class="category-track"><div class="category-fill $fill" style="width:$percent%"></div></div>
            </div>
        """.trimIndent()
    }
}

private fun causeItemCard(item: CoffeeCauseItemDto): String {
    val type = factorTypeText(item.factorType)
    val color = when (item.factorType.lowercase(Locale.US)) {
        "external" -> "blue"
        "internal" -> "pink"
        else -> "green"
    }
    val normalizedProblem = item.normalizedProblem.ifBlank { item.rawProblem }
    return clayCard("$color clickable compact", """
        <div onclick="openCauseSheet(this)"
          data-title="${escapeHtml("问题 ${item.stepIndex} 归因详情")}"
          data-subtitle="${escapeHtml("$type · ${item.factorCategory}")}"
          data-raw-problem="${escapeHtml(item.rawProblem)}"
          data-normalized-problem="${escapeHtml(normalizedProblem.ifBlank { "这条结果来自旧缓存，重新分析后会生成简短问题描述。" })}"
          data-improvement="${escapeHtml(item.improvement)}">
          <div class="card-top"><span class="label">问题 ${item.stepIndex} · $type</span><div class="icon-bubble">${sparkSvg()}</div></div>
          <div class="body-text strong">${escapeHtml(normalizedProblem)}</div>
          <div class="subtitle">${escapeHtml(item.factorCategory)}</div>
        </div>
    """.trimIndent())
}

private fun causeSheetHtml(): String = """
    <div class="sheet" id="causeSheet">
      <div class="sheet-card">
        <div class="card-top"><span class="label" id="causeSheetTitle"></span><button class="close" type="button" onclick="closeCauseSheet()">×</button></div>
        <div class="subtitle" id="causeSheetSubtitle"></div>
        <h3>原始问题</h3><p id="causeRawProblem"></p>
        <h3>整理后问题</h3><p id="causeNormalizedProblem"></p>
        <h3>改进建议</h3><p id="causeImprovement"></p>
      </div>
    </div>
""".trimIndent()

private fun chatSheetHtml(state: CoffeeUiState): String {
    val openClass = if (state.isChatOpen) " show" else ""
    val items = state.causeResult?.items.orEmpty()
    val selectedIndex = state.selectedChatStepIndex
    val itemOptions = buildString {
        append("""<option value=""${if (selectedIndex == null) " selected" else ""}>&#25972;&#20010;&#20219;&#21153;</option>""")
        items.forEach { item ->
            val selected = if (item.stepIndex == selectedIndex) " selected" else ""
            append("""<option value="${item.stepIndex}"$selected>&#38382;&#39064; ${item.stepIndex} &middot; ${escapeHtml(item.factorCategory)}</option>""")
        }
    }
    val selectorHtml = if (items.isNotEmpty()) {
        """
            <div class="chat-select-wrap">
              <select class="chat-select" onchange="CoffeeBridge.selectChatStep(this.value)">$itemOptions</select>
            </div>
        """.trimIndent()
    } else {
        "<div class=\"chat-empty\">&#20808;&#23436;&#25104;&#19968;&#27425; AI &#24402;&#22240;&#20998;&#26512;&#65292;&#20877;&#24320;&#22987;&#35752;&#35770;&#12290;</div>"
    }
    val messages = state.chatMessages.joinToString(separator = "") { message ->
        val roleClass = if (message.role == "user") "user" else "assistant"
        """
            <div class="chat-message $roleClass">
              <div>${escapeHtml(message.content)}</div>
            </div>
        """.trimIndent()
    }
    val error = state.chatError?.takeIf { it.isNotBlank() }?.let {
        "<div class=\"chat-error\">${escapeHtml(it)}</div>"
    }.orEmpty()
    val disabled = if (state.isChatSending || !state.hasApiKey || state.causeResult == null) "disabled" else ""
    return """
        <div class="chat-sheet$openClass" id="chatSheet">
          <div class="chat-card">
            <div class="chat-topline">
              <div class="chat-task">${escapeHtml(state.selectedTaskTitle.ifBlank { "\u5f53\u524d\u4efb\u52a1" })}</div>
              <button class="chat-close" type="button" onclick="CoffeeBridge.closeChat()">&times;</button>
            </div>
            $selectorHtml
            <div class="chat-messages" id="chatMessages">$messages</div>
            $error
            <div class="chat-input-wrap">
              <textarea id="chatInput" rows="2" onkeydown="handleChatKey(event)"></textarea>
              <button class="chat-send" type="button" onclick="submitChatMessage()" $disabled>&#21457;&#36865;</button>
            </div>
          </div>
        </div>
    """.trimIndent()
}

private fun factorTypeText(value: String): String = when (value.lowercase(Locale.US)) {
    "external" -> "外界因素"
    "internal" -> "内在因素"
    else -> "混合因素"
}

private fun formatAnalyzedAt(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))

private fun clayCard(colorClass: String, content: String): String = "<article class=\"clay-card $colorClass\">$content</article>"

private fun sharedClayCss(): String = """
    * { box-sizing: border-box; -webkit-tap-highlight-color: rgba(0,0,0,0); }
    :root { --viewport-height: 100vh; --viewport-offset-top: 0px; --keyboard-height: 0px; }
    html, body { margin: 0; width: 100%; min-height: 100%; background: rgb(224,229,236); color: rgb(74,85,104); color-scheme: light; font-family: Nunito, Quicksand, "Rounded Mplus 1c", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 24px; text-size-adjust: 100%; -webkit-font-smoothing: antialiased; }
    body { overflow-x: hidden; }
    button, textarea { font: inherit; }
    .page { width: 100%; max-width: 1280px; min-height: 100vh; margin: 0 auto; padding: 24px 20px 40px; }
    .stack { display: grid; grid-template-columns: 1fr; gap: 20px; }
    .section-title { margin: 6px 4px -6px; font-size: 18px; line-height: 28px; font-weight: 900; color: rgb(74,85,104); }
    .clay-card { display: block; width: 100%; min-height: 132px; padding: 24px; border-width: 0; border-style: solid; border-color: rgba(255,255,255,.24); border-radius: 35px; overflow: hidden; box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; transition: all .3s cubic-bezier(.34,1.56,.64,1); }
    .compact { min-height: 0; padding: 22px; }
    .clickable:active { transform: scale(.985); }
    .blue { background-color: rgb(162,210,255); color: rgb(67,101,139); }
    .pink { background-color: rgb(255,179,217); color: rgb(138,72,96); }
    .green { background-color: rgb(183,228,199); color: rgb(61,107,79); }
    .soft { background: rgba(255,255,255,.54); color: rgb(74,85,104); }
    .error { color: rgb(138,72,96); }
    .card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
    .label { font-size: 14px; line-height: 20px; font-weight: 800; opacity: .75; }
    .icon-bubble { width: 40px; height: 40px; border-radius: 9999px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.4); flex: 0 0 auto; }
    .icon-button { border: 0; color: inherit; padding: 0; box-shadow: rgba(255,255,255,.55) -3px -3px 7px 0 inset, rgba(74,85,104,.12) 4px 4px 8px 0 inset; }
    .icon-button:active { transform: scale(.94); }
    svg { width: 20px; height: 20px; display: block; fill: none; stroke: currentColor; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
    .value { font-size: 30px; line-height: 36px; font-weight: 900; letter-spacing: -.025em; word-break: break-word; }
    .subtitle { margin-top: 8px; font-size: 12px; line-height: 16px; font-weight: 700; opacity: .70; }
    .body-text { font-size: 15px; line-height: 24px; font-weight: 700; opacity: .78; word-break: break-word; }
    .body-text.strong { opacity: .9; }
    .summary { margin-bottom: 16px; }
    .cache-note { display: inline-flex; align-items: center; min-height: 28px; margin: -4px 0 12px; padding: 5px 10px; border-radius: 9999px; background: rgba(255,255,255,.36); font-size: 12px; line-height: 18px; font-weight: 900; opacity: .72; }
    .pill { width: 100%; height: 58px; border: 0; border-radius: 50px; font-size: 16px; font-weight: 900; background: rgb(255,179,217); color: rgb(138,72,96); box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; }
    .pill.settings { background: rgb(162,210,255); color: rgb(67,101,139); }
    .pill.analyze { background: rgb(255,179,217); color: rgb(138,72,96); }
    .pill:active { transform: scale(.97); }
    .selector-card { min-height: 0; padding: 18px; }
    .field-label { display: block; margin: 0 4px 8px; font-size: 13px; line-height: 18px; font-weight: 900; opacity: .68; }
    .select-trigger { width: 100%; min-height: 52px; display: flex; align-items: center; justify-content: space-between; gap: 12px; border: 0; border-radius: 24px; background: rgb(224,229,236); color: rgb(74,85,104); box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.62) 5px 5px 9px 0 inset, rgba(255,255,255,.78) -5px -5px 9px 0 inset; padding: 8px 14px 8px 16px; font-size: 15px; font-weight: 900; text-align: left; }
    .chevron { width: 30px; height: 30px; display: inline-flex; align-items: center; justify-content: center; border-radius: 9999px; background: rgba(255,255,255,.42); flex: 0 0 auto; font-size: 15px; }
    .task-menu { display: none; grid-template-columns: 1fr; gap: 0; margin-top: 10px; border-radius: 24px; overflow: hidden; background: rgb(224,229,236); box-shadow: rgba(163,177,198,.52) 5px 5px 9px 0 inset, rgba(255,255,255,.72) -5px -5px 9px 0 inset; padding: 6px; }
    .task-menu.open { display: grid; }
    .task-option { width: 100%; min-height: 46px; display: flex; align-items: center; border: 0; border-radius: 18px; background: transparent; color: rgba(74,85,104,.72); padding: 8px 12px; font-size: 14px; line-height: 20px; font-weight: 900; text-align: left; }
    .task-option + .task-option { margin-top: 2px; }
    .task-option.active { background: rgb(255,179,217); color: rgb(138,72,96); box-shadow: rgba(163,177,198,.44) 6px 6px 10px 0, rgba(255,255,255,.45) -6px -6px 10px 0, rgba(163,177,198,.16) 4px 4px 8px 0 inset, rgba(255,255,255,.45) -4px -4px 8px 0 inset; }
    .analysis-dynamic { width: 100%; min-height: 0; padding: 16px 18px; border-radius: 28px; background: rgb(224,229,236); color: rgb(74,85,104); box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.7) 6px 6px 10px 0 inset, rgba(255,255,255,.8) -6px -6px 10px 0 inset; }
    .dynamic-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; font-size: 14px; line-height: 20px; font-weight: 900; opacity: .76; }
    .dynamic-track { height: 18px; margin-top: 0; }
    .dot-row { display: inline-flex; align-items: center; gap: 5px; }
    .dot-row i { width: 7px; height: 7px; border-radius: 9999px; background: rgb(162,210,255); box-shadow: rgba(255,255,255,.6) -2px -2px 4px 0 inset, rgba(74,85,104,.12) 2px 2px 5px 0 inset; animation: dotPulse 1s ease-in-out infinite; }
    .dot-row i:nth-child(2) { animation-delay: .15s; background: rgb(255,179,217); }
    .dot-row i:nth-child(3) { animation-delay: .3s; background: rgb(183,228,199); }
    .mini-list { display: grid; gap: 12px; }
    .mini-step { padding: 18px; border-radius: 28px; background: rgba(255,255,255,.54); box-shadow: rgba(163,177,198,.38) 6px 6px 12px 0, rgba(255,255,255,.5) -6px -6px 12px 0, rgba(163,177,198,.16) 4px 4px 8px 0 inset, rgba(255,255,255,.45) -4px -4px 8px 0 inset; }
    .ball-section { display: block; width: 100%; padding: 10px 0 6px; }
    .ball-section-title { margin: 0 4px 14px; font-size: 14px; line-height: 20px; font-weight: 900; color: rgb(74,85,104); opacity: .74; text-align: center; }
    .ratio-balls { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: center; gap: 16px; min-height: 180px; padding: 8px 0 4px; overflow: visible; }
    .ball-wrap { position: relative; flex: 0 1 auto; min-width: 0; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; animation: float 6s ease-in-out infinite; }
    .ball-wrap:nth-child(2) { top: 12px; }
    .ball-wrap:nth-child(3) { top: -8px; }
    .ratio-ball { display: flex; align-items: center; justify-content: center; border-top-left-radius: 45.56% 49.63%; border-top-right-radius: 54.44% 60%; border-bottom-left-radius: 59.26% 50.37%; border-bottom-right-radius: 40.74% 40%; box-shadow: rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; }
    .ratio-ball span { font-size: 24px; line-height: 30px; font-weight: 900; letter-spacing: -.03em; }
    .external-ball { background-color: rgb(162,210,255); color: rgb(67,101,139); }
    .internal-ball { background-color: rgb(255,179,217); color: rgb(138,72,96); }
    .mixed-ball { background-color: rgb(183,228,199); color: rgb(61,107,79); }
    .ball-label { margin-top: 10px; font-size: 12px; line-height: 16px; font-weight: 900; color: rgb(74,85,104); opacity: .76; text-align: center; white-space: nowrap; }
    .inset { height: 18px; padding: 4px; border-radius: 9999px; background: rgb(224,229,236); box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.7) 6px 6px 10px 0 inset, rgba(255,255,255,.8) -6px -6px 10px 0 inset; overflow: hidden; }
    .loading-bar { width: 48%; height: 100%; border-radius: 9999px; background: linear-gradient(90deg, rgb(255,179,217), rgb(162,210,255)); animation: pulse 1.2s ease-in-out infinite alternate; }
    @keyframes pulse { from { transform: translateX(0); } to { transform: translateX(92%); } }
    @keyframes dotPulse { 0%, 100% { transform: translateY(0) scale(.86); opacity: .45; } 50% { transform: translateY(-2px) scale(1); opacity: 1; } }
    @keyframes float { 0%, 100% { transform: translateY(0px) rotate(0deg); } 50% { transform: translateY(-12px) rotate(2deg); } }
    .category-row + .category-row { margin-top: 16px; }
    .category-head { display: flex; justify-content: space-between; gap: 12px; font-size: 15px; line-height: 22px; font-weight: 900; }
    .category-track { height: 16px; margin-top: 7px; padding: 4px; border-radius: 9999px; background: rgb(224,229,236); box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.7) 6px 6px 10px 0 inset, rgba(255,255,255,.8) -6px -6px 10px 0 inset; overflow: hidden; }
    .category-fill { height: 100%; border-radius: 9999px; box-shadow: rgba(255,255,255,.55) -3px -3px 7px 0 inset, rgba(74,85,104,.12) 4px 4px 8px 0 inset; }
    .blue-fill { background: rgb(162,210,255); }
    .pink-fill { background: rgb(255,179,217); }
    .green-fill { background: rgb(183,228,199); }
    .advice { margin: 0; padding-left: 18px; font-size: 14px; line-height: 23px; font-weight: 700; }
    .sheet { position: fixed; z-index: 9999; left: 0; right: 0; top: var(--viewport-offset-top); height: var(--viewport-height); display: none; align-items: stretch; justify-content: center; padding: 18px 18px max(18px, env(safe-area-inset-bottom)); background: rgba(74,85,104,.28); overflow-y: auto; overscroll-behavior: contain; }
    .sheet.show { display: flex; }
    .sheet-card { width: 100%; min-height: calc(var(--viewport-height) - 36px); max-height: calc(var(--viewport-height) - 36px); overflow-y: auto; padding: 26px 24px 30px; border-radius: 35px; background: rgb(224,229,236); color: rgb(74,85,104); box-shadow: rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; }
    .chat-sheet { position: fixed; z-index: 9998; left: 0; right: 0; top: var(--viewport-offset-top); height: var(--viewport-height); display: none; padding: 10px 12px calc(max(12px, env(safe-area-inset-bottom)) + var(--keyboard-height)); background: rgb(224,229,236); overflow: hidden; }
    .chat-sheet.show { display: block; }
    .chat-card { height: calc(var(--viewport-height) - var(--keyboard-height) - 22px); display: flex; flex-direction: column; gap: 8px; padding: 14px; border-radius: 30px; background: rgb(224,229,236); color: rgb(74,85,104); box-shadow: rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; }
    .chat-topline { display: grid; grid-template-columns: minmax(0, 1fr) 36px; gap: 8px; align-items: center; }
    .chat-task { min-height: 38px; display: flex; align-items: center; padding: 8px 12px; border-radius: 20px; background: rgba(255,255,255,.42); font-size: 13px; line-height: 18px; font-weight: 900; color: rgb(138,72,96); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .chat-close { width: 36px; height: 36px; border: 0; border-radius: 9999px; background: rgba(255,255,255,.45); color: rgb(74,85,104); font-size: 24px; line-height: 1; font-weight: 900; }
    .chat-select-wrap { padding: 4px; border-radius: 24px; background: rgb(224,229,236); box-shadow: rgba(163,177,198,.62) 5px 5px 9px 0 inset, rgba(255,255,255,.78) -5px -5px 9px 0 inset; }
    .chat-select { width: 100%; height: 42px; border: 0; outline: 0; border-radius: 20px; background: transparent; color: rgb(74,85,104); padding: 0 12px; font-size: 14px; font-weight: 900; }
    .chat-empty { padding: 10px 12px; border-radius: 20px; background: rgba(255,255,255,.42); font-size: 12px; line-height: 18px; font-weight: 800; opacity: .72; }
    .chat-messages { flex: 1; min-height: 0; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; padding: 14px; border-radius: 26px; background: rgb(224,229,236); box-shadow: rgba(163,177,198,.7) 6px 6px 10px 0 inset, rgba(255,255,255,.8) -6px -6px 10px 0 inset; }
    .chat-message { display: flex; }
    .chat-message > div { max-width: 88%; padding: 12px 14px; border-radius: 24px; font-size: 14px; line-height: 22px; font-weight: 800; white-space: pre-wrap; word-break: break-word; box-shadow: rgba(163,177,198,.38) 5px 5px 10px 0, rgba(255,255,255,.5) -5px -5px 10px 0, rgba(163,177,198,.14) 4px 4px 8px 0 inset, rgba(255,255,255,.45) -4px -4px 8px 0 inset; }
    .chat-message.assistant { justify-content: flex-start; }
    .chat-message.assistant > div { background: rgb(183,228,199); color: rgb(61,107,79); }
    .chat-message.user { justify-content: flex-end; }
    .chat-message.user > div { background: rgb(255,179,217); color: rgb(138,72,96); }
    .chat-message.typing span { display: inline-block; width: 7px; height: 7px; margin: 0 2px; border-radius: 9999px; background: currentColor; opacity: .55; animation: dotPulse 1s ease-in-out infinite; }
    .chat-message.typing span:nth-child(2) { animation-delay: .15s; }
    .chat-message.typing span:nth-child(3) { animation-delay: .3s; }
    .chat-error { padding: 8px 10px; border-radius: 18px; background: rgba(255,179,217,.56); color: rgb(138,72,96); font-size: 12px; line-height: 18px; font-weight: 900; }
    .chat-input-wrap { display: grid; grid-template-columns: 1fr 52px; gap: 8px; align-items: stretch; }
    .chat-input-wrap textarea { width: 100%; min-height: 72px; max-height: 118px; resize: none; border: 0; outline: 0; border-radius: 26px; background: rgb(224,229,236); color: rgb(74,85,104); padding: 14px 16px; font-size: 15px; line-height: 22px; font-weight: 800; box-shadow: rgba(163,177,198,.7) 6px 6px 10px 0 inset, rgba(255,255,255,.8) -6px -6px 10px 0 inset; }
    .chat-send { width: 52px; border: 0; border-radius: 24px; background: rgb(255,179,217); color: rgb(138,72,96); font-size: 13px; font-weight: 900; box-shadow: rgba(163,177,198,.48) 6px 6px 10px 0, rgba(255,255,255,.48) -6px -6px 10px 0, rgba(163,177,198,.16) 4px 4px 8px 0 inset, rgba(255,255,255,.45) -4px -4px 8px 0 inset; }
    .chat-send:disabled { opacity: .55; }
    .close { width: 40px; height: 40px; border: 0; border-radius: 9999px; background: rgba(255,255,255,.4); color: rgb(74,85,104); font-size: 26px; font-weight: 800; }
    h3 { margin: 16px 0 6px; font-size: 15px; line-height: 22px; font-weight: 900; }
    p, ul { margin: 0; font-size: 14px; line-height: 23px; font-weight: 700; opacity: .82; }
""".trimIndent()

private fun coffeeSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M10 2v2"></path><path d="M14 2v2"></path><path d="M16 8a1 1 0 0 1 1 1v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h11Z"></path><path d="M17 8h1a4 4 0 0 1 0 8h-1"></path><path d="M6 22h12"></path></svg>
""".trimIndent()

private fun alertSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z"></path><path d="M12 9v4"></path><path d="M12 17h.01"></path></svg>
""".trimIndent()

private fun sparkSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8L12 3Z"></path><path d="M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8L19 15Z"></path></svg>
""".trimIndent()

private fun chartSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M3 3v18h18"></path><path d="M18 17V9"></path><path d="M13 17V5"></path><path d="M8 17v-3"></path></svg>
""".trimIndent()

private fun bellSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M10.268 21a2 2 0 0 0 3.464 0"></path><path d="M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"></path></svg>
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

private fun jsStringLiteral(value: String): String = buildString(value.length + 2) {
    append('\'')
    value.forEach { char ->
        append(
            when (char) {
                '\\' -> "\\\\"
                '\'' -> "\\'"
                '\n' -> "\\n"
                '\r' -> "\\r"
                '\t' -> "\\t"
                else -> char
            }
        )
    }
    append('\'')
}
