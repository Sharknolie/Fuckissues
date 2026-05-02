package com.jadeai.solvertracker.ui.history

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadeai.solvertracker.data.local.entity.TaskStatus
import com.jadeai.solvertracker.domain.model.Task
import com.jadeai.solvertracker.ui.components.JellyBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onTaskClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bridge = remember(onTaskClick) { HistoryBridge(onTaskClick) }

    JellyBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(text = "\u5386\u53f2\u4efb\u52a1", fontWeight = FontWeight.SemiBold) },
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
                    HistoryWebView(
                        html = buildHistoryHtml(state),
                        bridge = bridge,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private class HistoryBridge(
    private val onTaskClick: (Long) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun openTask(taskId: String) {
        val id = taskId.toLongOrNull() ?: return
        mainHandler.post { onTaskClick(id) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun HistoryWebView(
    html: String,
    bridge: HistoryBridge,
    modifier: Modifier = Modifier
) {
    var lastLoadedHtml by remember { mutableStateOf<String?>(null) }

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
                addJavascriptInterface(bridge, "HistoryBridge")
                lastLoadedHtml = html
                loadHistoryHtml(html)
            }
        },
        update = { webView ->
            if (html != lastLoadedHtml) {
                lastLoadedHtml = html
                webView.loadHistoryHtml(html)
            }
        }
    )
}

private fun WebView.loadHistoryHtml(html: String) {
    loadDataWithBaseURL(
        "https://solvertracker.local/history/",
        html,
        "text/html; charset=utf-8",
        "UTF-8",
        null
    )
}

private fun buildHistoryHtml(state: HistoryUiState): String {
    val isEmpty = state.inProgress.isEmpty() && state.completed.isEmpty()
    val sections = buildString {
        if (isEmpty) {
            append(
                clayCard(
                    colorClass = "soft",
                    content = """
                        <div class="card-top"><span class="label">还没有任务</span><div class="icon-bubble">${taskSvg()}</div></div>
                        <div class="body-text">去首页写下第一条吧。</div>
                    """.trimIndent()
                )
            )
        } else {
            append(sectionHtml("进行中", state.inProgress, listOf("blue", "green", "pink")))
            append(sectionHtml("已完成", state.completed, listOf("green", "pink", "blue")))
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
          <main class="page">
            <section class="stack">$sections</section>
          </main>
        </body>
        </html>
    """.trimIndent()
}

private fun sectionHtml(title: String, tasks: List<Task>, colorClasses: List<String>): String {
    if (tasks.isEmpty()) return ""
    return buildString {
        append("<h2 class=\"section-title\">${escapeHtml(title)}</h2>")
        tasks.forEachIndexed { index, task ->
            val statusText = if (task.status == TaskStatus.COMPLETED) "已完成" else "进行中"
            append(
                clayCard(
                    colorClass = colorClasses[index % colorClasses.size],
                    extraClass = "clickable",
                    attrs = "onclick=\"HistoryBridge.openTask('${task.id}')\"",
                    content = """
                        <div class="card-top">
                          <span class="label">${escapeHtml(statusText)} · ${task.stepCount} 个问题</span>
                          <div class="icon-bubble">${taskSvg()}</div>
                        </div>
                        <div class="value small">${escapeHtml(task.title)}</div>
                        <div class="subtitle">点击查看解决过程</div>
                    """.trimIndent()
                )
            )
        }
    }
}

private fun clayCard(
    colorClass: String,
    content: String,
    extraClass: String = "",
    attrs: String = ""
): String = "<article class=\"clay-card $colorClass $extraClass\" $attrs>$content</article>"

private fun sharedClayCss(): String = """
    * { box-sizing: border-box; -webkit-tap-highlight-color: rgba(0,0,0,0); }
    html, body {
      margin: 0; width: 100%; min-height: 100%; background: rgb(224,229,236); color: rgb(74,85,104);
      color-scheme: light; font-family: Nunito, Quicksand, "Rounded Mplus 1c", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      line-height: 24px; text-size-adjust: 100%; -webkit-font-smoothing: antialiased;
    }
    body { overflow-x: hidden; }
    .page { width: 100%; max-width: 1280px; min-height: 100vh; margin: 0 auto; padding: 24px 20px 40px; }
    .stack { display: grid; grid-template-columns: 1fr; gap: 20px; }
    .section-title { margin: 6px 4px -6px; font-size: 18px; line-height: 28px; font-weight: 900; color: rgb(74,85,104); }
    .clay-card {
      display: block; width: 100%; min-height: 132px; padding: 24px; border-width: 0; border-style: solid; border-color: rgba(255,255,255,.24);
      border-radius: 35px; overflow: hidden; box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset;
      transition: all .3s cubic-bezier(.34,1.56,.64,1);
    }
    .clickable:active { transform: scale(.985); }
    .blue { background-color: rgb(162,210,255); color: rgb(67,101,139); }
    .pink { background-color: rgb(255,179,217); color: rgb(138,72,96); }
    .green { background-color: rgb(183,228,199); color: rgb(61,107,79); }
    .soft { background: rgba(255,255,255,.54); color: rgb(74,85,104); }
    .card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
    .label { font-size: 14px; line-height: 20px; font-weight: 700; opacity: .75; }
    .icon-bubble { width: 40px; height: 40px; border-radius: 9999px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.4); flex: 0 0 auto; }
    svg { width: 20px; height: 20px; display: block; fill: none; stroke: currentColor; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
    .value { font-size: 30px; line-height: 36px; font-weight: 900; letter-spacing: -.025em; word-break: break-word; }
    .value.small { font-size: 22px; line-height: 30px; }
    .subtitle { margin-top: 8px; font-size: 12px; line-height: 16px; font-weight: 600; opacity: .70; }
    .body-text { font-size: 15px; line-height: 24px; font-weight: 700; opacity: .72; }
""".trimIndent()

private fun taskSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M9 11l3 3L22 4"></path><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path></svg>
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
