package com.jadeai.solvertracker.ui.detail

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadeai.solvertracker.data.local.entity.TaskStatus
import com.jadeai.solvertracker.domain.model.SolutionStep
import com.jadeai.solvertracker.ui.components.JellyBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bridge = remember(viewModel) { DetailBridge(viewModel) }

    JellyBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = state.task?.title.orEmpty(),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
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
                    DetailWebView(
                        html = buildDetailHtml(state),
                        bridge = bridge,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private class DetailBridge(
    private val viewModel: TaskDetailViewModel
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun addStep(problem: String, solution: String) {
        mainHandler.post { viewModel.addStep(problem, solution) }
    }

    @JavascriptInterface
    fun updateStep(stepId: String, problem: String, solution: String) {
        val id = stepId.toLongOrNull() ?: return
        mainHandler.post { viewModel.updateStep(id, problem, solution) }
    }

    @JavascriptInterface
    fun markCompleted() {
        mainHandler.post { viewModel.markCompleted() }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
private fun DetailWebView(
    html: String,
    bridge: DetailBridge,
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
                addJavascriptInterface(bridge, "DetailBridge")
                loadDetailHtml(html)
            }
        },
        update = { webView -> webView.loadDetailHtml(html) }
    )
}

private fun WebView.loadDetailHtml(html: String) {
    val encodedHtml = Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_PADDING)
    loadData(encodedHtml, "text/html; charset=utf-8", "base64")
}

private fun buildDetailHtml(state: TaskDetailUiState): String {
    val task = state.task
    val content = when {
        task == null -> clayCard("soft", """
            <div class="card-top"><span class="label">任务不存在</span><div class="icon-bubble">${alertSvg()}</div></div>
            <div class="body-text">这个任务不存在或已被删除。</div>
        """.trimIndent())

        else -> {
            val statusText = if (task.status == TaskStatus.COMPLETED) "已完成" else "进行中"
            buildString {
                append("<div class=\"detail-meta\">$statusText · ${task.stepCount} 个问题</div>")
                append(autoAnalysisStatusHtml(state))
                if (state.steps.isEmpty()) {
                    append(
                        clayCard("soft", """
                            <div class="card-top"><span class="label">还没有记录问题</span><div class="icon-bubble">${plusSvg()}</div></div>
                            <div class="body-text">点底部“添加问题”，先写下第一条。</div>
                        """.trimIndent())
                    )
                } else {
                    state.steps.forEachIndexed { index, step -> append(stepHtml(index, step)) }
                }
            }
        }
    }
    val canComplete = task != null && task.status == TaskStatus.IN_PROGRESS && state.steps.isNotEmpty()
    val isCompleted = task?.status == TaskStatus.COMPLETED

    return """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
          <style>${sharedClayCss()}</style>
        </head>
        <body>
          <main class="page detail-page">
            <section class="stack">$content</section>
          </main>
          ${if (task != null) bottomActionsHtml(canComplete, isCompleted) else ""}
          ${if (task != null) addSheetHtml() else ""}
          ${if (task != null) editSheetHtml() else ""}
          <script>
            const sheet = document.getElementById('sheet');
            const editSheet = document.getElementById('editSheet');
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
            function keepFieldVisible(field) {
              if (!field) return;
              document.body.classList.add('keyboard-open');
              syncViewport();
              setTimeout(() => {
                syncViewport();
                field.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'smooth' });
              }, 120);
            }
            function showSheet(target, focusId) {
              if (!target) return;
              syncViewport();
              document.body.classList.add('modal-open');
              target.classList.add('show');
              if (focusId) {
                setTimeout(() => {
                  const field = document.getElementById(focusId);
                  if (!field) return;
                  field.focus({ preventScroll: true });
                  keepFieldVisible(field);
                }, 180);
              }
            }
            function hideSheet(target) {
              if (!target) return;
              target.classList.remove('show');
              if (!document.querySelector('.sheet.show')) {
                document.body.classList.remove('modal-open');
                document.body.classList.remove('keyboard-open');
                syncViewport();
              }
            }
            function openSheet() { showSheet(sheet, 'problem'); }
            function closeSheet() { hideSheet(sheet); }
            function openEditSheet(id, problem, solution) {
              document.getElementById('editStepId').value = id;
              document.getElementById('editProblem').value = problem;
              document.getElementById('editSolution').value = solution;
              showSheet(editSheet, 'editProblem');
            }
            function closeEditSheet() { hideSheet(editSheet); }
            function submitStep() {
              const problem = document.getElementById('problem').value.trim();
              const solution = document.getElementById('solution').value.trim();
              if (!problem || !solution) return;
              DetailBridge.addStep(problem, solution);
              closeSheet();
            }
            function submitEditStep() {
              const stepId = document.getElementById('editStepId').value;
              const problem = document.getElementById('editProblem').value.trim();
              const solution = document.getElementById('editSolution').value.trim();
              if (!stepId || !problem || !solution) return;
              DetailBridge.updateStep(stepId, problem, solution);
              closeEditSheet();
            }
            syncViewport();
            if (window.visualViewport) {
              window.visualViewport.addEventListener('resize', syncViewport);
              window.visualViewport.addEventListener('scroll', syncViewport);
            }
            window.addEventListener('resize', syncViewport);
            window.addEventListener('orientationchange', syncViewport);
            document.addEventListener('focusin', event => {
              if (event.target && event.target.tagName === 'TEXTAREA') keepFieldVisible(event.target);
            });
            document.addEventListener('focusout', () => {
              setTimeout(() => {
                if (!document.activeElement || document.activeElement.tagName !== 'TEXTAREA') {
                  document.body.classList.remove('keyboard-open');
                  syncViewport();
                }
              }, 120);
            });
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun stepHtml(index: Int, step: SolutionStep): String {
    val problem = escapeHtml(step.problem)
    val solution = escapeHtml(step.solution)
    val problemJs = escapeJsSingleQuoted(step.problem)
    val solutionJs = escapeJsSingleQuoted(step.solution)
    return """
    <div class="timeline-item">
      <div class="timeline-rail"><span class="timeline-node">${index + 1}</span></div>
      <div class="pair">
        ${clayCard("pink compact clickable", """
          <div onclick="openEditSheet('${step.id}', '$problemJs', '$solutionJs')">
            <div class="card-top compact-top"><span class="label">问题 ${index + 1}</span></div>
            <div class="body-text strong">$problem</div>
          </div>
        """.trimIndent())}
        ${clayCard("green compact clickable", """
          <div onclick="openEditSheet('${step.id}', '$problemJs', '$solutionJs')">
            <div class="card-top compact-top"><span class="label">解决</span></div>
            <div class="body-text strong">$solution</div>
          </div>
        """.trimIndent())}
        </div>
    </div>
""".trimIndent()
}

private fun autoAnalysisStatusHtml(state: TaskDetailUiState): String {
    val message = state.autoAnalysisMessage.takeIf { it.isNotBlank() } ?: return ""
    val color = when (state.autoAnalysisState) {
        AutoAnalysisState.ANALYZING -> "soft analysis-dynamic-card"
        AutoAnalysisState.SAVED -> "green compact"
        AutoAnalysisState.SKIPPED -> "soft compact"
        AutoAnalysisState.ERROR -> "pink compact"
        AutoAnalysisState.IDLE -> return ""
    }
    val extra = if (state.autoAnalysisState == AutoAnalysisState.ANALYZING) {
        "<div class=\"inset dynamic-track\"><div class=\"loading-bar\"></div></div>"
    } else {
        ""
    }
    return clayCard(color, """
        <div class="card-top compact-top"><span class="label">AI 归因</span><div class="dot-row"><i></i><i></i><i></i></div></div>
        <div class="body-text">${escapeHtml(message)}</div>
        $extra
    """.trimIndent())
}

private fun bottomActionsHtml(canComplete: Boolean, isCompleted: Boolean): String = """
    <footer class="bottom-actions">
      <button class="pill blue-button" type="button" onclick="openSheet()">添加问题</button>
      <button class="pill pink-button" type="button" ${if (!canComplete || isCompleted) "disabled" else ""} onclick="DetailBridge.markCompleted()">${if (isCompleted) "已完成" else "标记完成"}</button>
    </footer>
""".trimIndent()

private fun addSheetHtml(): String = """
    <div class="sheet" id="sheet">
      <div class="sheet-card">
        <div class="card-top"><span class="label">添加问题</span><button class="close" type="button" onclick="closeSheet()">×</button></div>
        <div class="field-wrap"><textarea id="problem" placeholder="遇到的问题"></textarea></div>
        <div class="field-wrap"><textarea id="solution" placeholder="解决方法"></textarea></div>
        <button class="pill pink-button full" type="button" onclick="submitStep()">添加</button>
      </div>
    </div>
""".trimIndent()

private fun editSheetHtml(): String = """
    <div class="sheet" id="editSheet">
      <div class="sheet-card">
        <div class="card-top"><span class="label">编辑问题</span><button class="close" type="button" onclick="closeEditSheet()">×</button></div>
        <input id="editStepId" type="hidden" />
        <div class="field-wrap"><textarea id="editProblem" placeholder="遇到的问题"></textarea></div>
        <div class="field-wrap"><textarea id="editSolution" placeholder="解决方法"></textarea></div>
        <button class="pill pink-button full" type="button" onclick="submitEditStep()">保存修改</button>
      </div>
    </div>
""".trimIndent()

private fun clayCard(colorClass: String, content: String): String = "<article class=\"clay-card $colorClass\">$content</article>"

private fun sharedClayCss(): String = """
    * { box-sizing: border-box; -webkit-tap-highlight-color: rgba(0,0,0,0); }
    :root { --viewport-height: 100vh; --viewport-offset-top: 0px; --keyboard-height: 0px; }
    html, body { margin: 0; width: 100%; min-height: 100%; background: rgb(224,229,236); color: rgb(74,85,104); color-scheme: light; font-family: Nunito, Quicksand, "Rounded Mplus 1c", ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; line-height: 24px; text-size-adjust: 100%; -webkit-font-smoothing: antialiased; }
    body { overflow-x: hidden; }
    body.modal-open { overflow: hidden; }
    button, textarea { font: inherit; }
    .page { width: 100%; max-width: 1280px; min-height: 100vh; margin: 0 auto; padding: 24px 20px 132px; }
    .stack { display: grid; grid-template-columns: 1fr; gap: 20px; }
    .detail-meta { margin: 2px 4px -4px; font-size: 16px; line-height: 24px; font-weight: 900; color: rgb(74,85,104); opacity: .78; }
    .pair { display: grid; grid-template-columns: 1fr; gap: 12px; }
    .timeline-item { position: relative; display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 12px; align-items: stretch; }
    .timeline-rail { position: relative; display: flex; justify-content: center; align-items: flex-start; padding-top: 4px; }
    .timeline-rail::before { content: ""; position: absolute; top: 34px; bottom: -24px; left: 50%; width: 3px; transform: translateX(-50%); border-radius: 9999px; background: rgba(74,85,104,.18); box-shadow: rgba(255,255,255,.55) -1px -1px 2px 0 inset, rgba(163,177,198,.35) 1px 1px 3px 0 inset; }
    .timeline-item:last-child .timeline-rail::before { bottom: 18px; }
    .timeline-node { position: relative; z-index: 1; width: 28px; height: 28px; border-radius: 9999px; display: inline-flex; align-items: center; justify-content: center; background: rgb(224,229,236); color: rgb(74,85,104); font-size: 12px; line-height: 1; font-weight: 900; box-shadow: rgba(163,177,198,.55) 4px 4px 8px 0 inset, rgba(255,255,255,.8) -4px -4px 8px 0 inset; }
    .clay-card { display: block; width: 100%; min-height: 132px; padding: 24px; border-width: 0; border-style: solid; border-color: rgba(255,255,255,.24); border-radius: 35px; overflow: hidden; box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; transition: all .3s cubic-bezier(.34,1.56,.64,1); }
    .compact { min-height: 0; padding: 22px; }
    .clickable:active { transform: scale(.985); }
    .blue { background-color: rgb(162,210,255); color: rgb(67,101,139); }
    .pink { background-color: rgb(255,179,217); color: rgb(138,72,96); }
    .green { background-color: rgb(183,228,199); color: rgb(61,107,79); }
    .soft { background: rgba(255,255,255,.54); color: rgb(74,85,104); }
    .card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
    .label { font-size: 14px; line-height: 20px; font-weight: 800; opacity: .75; }
    .icon-bubble { width: 40px; height: 40px; border-radius: 9999px; display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,.4); flex: 0 0 auto; }
    svg { width: 20px; height: 20px; display: block; fill: none; stroke: currentColor; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
    .value { font-size: 30px; line-height: 36px; font-weight: 900; letter-spacing: -.025em; word-break: break-word; }
    .value.small { font-size: 22px; line-height: 30px; }
    .subtitle { margin-top: 8px; font-size: 12px; line-height: 16px; font-weight: 600; opacity: .70; }
    .body-text { font-size: 15px; line-height: 24px; font-weight: 700; opacity: .78; word-break: break-word; }
    .body-text.strong { margin-top: 8px; opacity: .88; }
    .compact-top { margin-bottom: 8px; align-items: center; }
    .analysis-dynamic-card { min-height: 0; padding: 20px; }
    .inset { height: 18px; padding: 4px; border-radius: 9999px; background: rgb(224,229,236); box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.7) 6px 6px 10px 0 inset, rgba(255,255,255,.8) -6px -6px 10px 0 inset; overflow: hidden; }
    .dynamic-track { margin-top: 14px; }
    .loading-bar { width: 48%; height: 100%; border-radius: 9999px; background: linear-gradient(90deg, rgb(255,179,217), rgb(162,210,255)); animation: pulse 1.2s ease-in-out infinite alternate; }
    .dot-row { display: inline-flex; align-items: center; gap: 5px; }
    .dot-row i { width: 7px; height: 7px; border-radius: 9999px; background: rgb(162,210,255); box-shadow: rgba(255,255,255,.6) -2px -2px 4px 0 inset, rgba(74,85,104,.12) 2px 2px 5px 0 inset; animation: dotPulse 1s ease-in-out infinite; }
    .dot-row i:nth-child(2) { animation-delay: .15s; background: rgb(255,179,217); }
    .dot-row i:nth-child(3) { animation-delay: .3s; background: rgb(183,228,199); }
    @keyframes pulse { from { transform: translateX(0); } to { transform: translateX(92%); } }
    @keyframes dotPulse { 0%, 100% { transform: translateY(0) scale(.86); opacity: .45; } 50% { transform: translateY(-2px) scale(1); opacity: 1; } }
    .bottom-actions { position: fixed; left: 0; right: 0; bottom: 0; display: flex; gap: 12px; padding: 16px 20px 34px; background: linear-gradient(180deg, rgba(224,229,236,0), rgb(224,229,236) 28%); }
    .pill { height: 58px; flex: 1; border: 0; border-radius: 50px; font-size: 16px; font-weight: 900; box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; }
    .pill:active { transform: scale(.97); }
    .pill:disabled { opacity: .55; }
    .blue-button { background: rgb(162,210,255); color: rgb(67,101,139); }
    .pink-button { background: rgb(255,179,217); color: rgb(138,72,96); }
    .full { width: 100%; margin-top: 14px; }
    .sheet { position: fixed; left: 0; right: 0; top: var(--viewport-offset-top); height: var(--viewport-height); display: none; align-items: center; padding: 20px 20px calc(max(20px, env(safe-area-inset-bottom)) + var(--keyboard-height)); background: rgba(74,85,104,.18); overflow-y: auto; overscroll-behavior: contain; }
    .sheet.show { display: flex; }
    .sheet-card { width: 100%; max-height: calc(var(--viewport-height) - var(--keyboard-height) - 40px); overflow-y: auto; padding: 22px; border-radius: 35px; background: rgb(224,229,236); box-shadow: rgba(163,177,198,.6) 9px 9px 16px 0, rgba(255,255,255,.5) -9px -9px 16px 0, rgba(163,177,198,.2) 5px 5px 10px 0 inset, rgba(255,255,255,.5) -5px -5px 10px 0 inset; scroll-padding-bottom: 128px; }
    body.keyboard-open .sheet { align-items: flex-start; padding-top: 14px; }
    body.keyboard-open .sheet-card { max-height: calc(var(--viewport-height) - var(--keyboard-height) - 28px); }
    .close { width: 40px; height: 40px; border: 0; border-radius: 9999px; background: rgba(255,255,255,.4); color: rgb(74,85,104); font-size: 26px; font-weight: 800; }
    .field-wrap { margin-top: 14px; border-radius: 28px; background: rgb(224,229,236); box-shadow: rgba(0,0,0,0) 0 0 0 0, rgba(0,0,0,0) 0 0 0 0, rgba(163,177,198,.7) 6px 6px 10px 0 inset, rgba(255,255,255,.8) -6px -6px 10px 0 inset; padding: 14px; }
    textarea { width: 100%; height: 118px; resize: none; border: 0; outline: 0; background: transparent; color: rgb(74,85,104); font-size: 16px; line-height: 24px; font-weight: 800; }
    textarea::placeholder { color: rgba(74,85,104,.42); }
""".trimIndent()

private fun taskSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M9 11l3 3L22 4"></path><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path></svg>
""".trimIndent()

private fun plusSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M12 5v14"></path><path d="M5 12h14"></path></svg>
""".trimIndent()

private fun alertSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z"></path><path d="M12 9v4"></path><path d="M12 17h.01"></path></svg>
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
