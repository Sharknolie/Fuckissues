package com.jadeai.solvertracker.ui.stats

import android.annotation.SuppressLint
import android.util.Base64
import android.view.View
import android.webkit.WebView
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jadeai.solvertracker.ui.components.JellyBackground
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    JellyBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "\u7edf\u8ba1",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowBack,
                                contentDescription = "\u8fd4\u56de"
                            )
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
                    ClayStatsWebView(
                        html = buildStatsHtml(state.ranges),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ClayStatsWebView(
    html: String,
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
                loadStatsHtml(html)
            }
        },
        update = { webView -> webView.loadStatsHtml(html) }
    )
}

private fun WebView.loadStatsHtml(html: String) {
    val encodedHtml = Base64.encodeToString(html.toByteArray(Charsets.UTF_8), Base64.NO_PADDING)
    loadData(encodedHtml, "text/html; charset=utf-8", "base64")
}

private fun buildStatsHtml(ranges: List<StatsRangeUi>): String {
    val safeRanges = ranges.ifEmpty {
        listOf(
            StatsRangeUi("today", 0, 0, 0, 0.0, emptyList()),
            StatsRangeUi("week", 0, 0, 0, 0.0, emptyList()),
            StatsRangeUi("month", 0, 0, 0, 0.0, emptyList())
        )
    }
    val rangesJson = buildRangesJson(safeRanges)

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
              font-feature-settings: normal;
              font-variation-settings: normal;
              line-height: 24px;
              text-size-adjust: 100%;
              -webkit-font-smoothing: antialiased;
            }

            body {
              overflow-x: hidden;
            }

            .page {
              display: block;
              width: 100%;
              max-width: 1280px;
              min-height: 100vh;
              margin-left: auto;
              margin-right: auto;
              padding: 24px 20px 40px;
            }

            .stack {
              display: grid;
              grid-template-columns: 1fr;
              gap: 22px;
            }

            .range-wrap {
              display: flex;
              justify-content: center;
              margin-bottom: 2px;
            }

            .range-tabs {
              height: 52px;
              display: inline-flex;
              align-items: center;
              gap: 4px;
              padding: 8px;
              border-width: 0;
              border-style: solid;
              border-color: rgb(229, 231, 235);
              border-radius: 50px;
              background-color: rgb(224, 229, 236);
              color: rgb(74, 85, 104);
              box-shadow:
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(163, 177, 198, 0.7) 6px 6px 10px 0 inset,
                rgba(255, 255, 255, 0.8) -6px -6px 10px 0 inset;
            }

            .range-button {
              appearance: none;
              border: 0;
              height: 36px;
              display: inline-flex;
              align-items: center;
              justify-content: center;
              gap: 8px;
              padding: 8px 20px;
              border-radius: 50px;
              background: transparent;
              color: rgba(74, 85, 104, 0.52);
              font: inherit;
              font-size: 14px;
              line-height: 20px;
              font-weight: 800;
              transition: all 0.22s ease;
            }

            .range-button.active {
              background-color: rgb(255, 179, 217);
              color: rgb(138, 72, 96);
              box-shadow:
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(163, 177, 198, 0.6) 9px 9px 16px 0,
                rgba(255, 255, 255, 0.5) -9px -9px 16px 0,
                rgba(163, 177, 198, 0.2) 5px 5px 10px 0 inset,
                rgba(255, 255, 255, 0.5) -5px -5px 10px 0 inset;
            }

            .clay-card-interactive {
              display: block;
              width: 100%;
              min-height: 164px;
              padding: 24px;
              border-width: 0;
              border-style: solid;
              border-color: rgba(255, 255, 255, 0.24);
              border-radius: 35px;
              overflow: hidden;
              cursor: pointer;
              box-shadow:
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(163, 177, 198, 0.6) 9px 9px 16px 0,
                rgba(255, 255, 255, 0.5) -9px -9px 16px 0,
                rgba(163, 177, 198, 0.2) 5px 5px 10px 0 inset,
                rgba(255, 255, 255, 0.5) -5px -5px 10px 0 inset;
              transition-property: all;
              transition-duration: 0.3s;
              transition-timing-function: cubic-bezier(0.34, 1.56, 0.64, 1);
            }

            .clay-card-interactive:active {
              transform: scale(0.985);
            }

            .clay-blue {
              background-color: rgb(162, 210, 255);
              color: rgb(67, 101, 139);
            }

            .clay-pink {
              background-color: rgb(255, 179, 217);
              color: rgb(138, 72, 96);
            }

            .card-top {
              display: flex;
              align-items: flex-start;
              justify-content: space-between;
              gap: 12px;
              margin-bottom: 16px;
            }

            .label {
              font-size: 14px;
              line-height: 20px;
              font-weight: 700;
              opacity: 0.75;
            }

            .icon-bubble {
              width: 40px;
              height: 40px;
              border-radius: 9999px;
              display: flex;
              align-items: center;
              justify-content: center;
              background-color: rgba(255, 255, 255, 0.4);
              flex: 0 0 auto;
            }

            .icon-bubble svg {
              width: 20px;
              height: 20px;
              display: block;
            }

            .value {
              font-size: 30px;
              line-height: 36px;
              font-weight: 900;
              letter-spacing: -0.025em;
              word-break: break-all;
            }

            .subtitle {
              margin-top: 8px;
              font-size: 12px;
              line-height: 16px;
              font-weight: 600;
              opacity: 0.70;
            }

            .chart-panel {
              height: 264px;
              display: block;
              padding: 44px 16px 16px;
              border-width: 0;
              border-style: solid;
              border-color: rgb(229, 231, 235);
              border-radius: 28px;
              background-color: rgb(224, 229, 236);
              color: rgb(74, 85, 104);
              box-shadow:
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(0, 0, 0, 0) 0 0 0 0,
                rgba(163, 177, 198, 0.7) 6px 6px 10px 0 inset,
                rgba(255, 255, 255, 0.8) -6px -6px 10px 0 inset;
            }

            .bars {
              height: 204px;
              display: flex;
              align-items: end;
              gap: 6px;
            }

            .bar-item {
              min-width: 0;
              flex: 1 1 0;
              display: flex;
              flex-direction: column;
              align-items: center;
              justify-content: end;
              position: relative;
            }

            .bar {
              width: 100%;
              min-height: 3px;
              border-radius: 10px 10px 4px 4px;
              background-color: rgba(74, 85, 104, 0.10);
              transition: height 0.3s ease;
            }

            .bar.has-value {
              background: linear-gradient(180deg, rgb(255, 179, 217), rgb(162, 210, 255));
              box-shadow:
                rgba(255, 255, 255, 0.55) -3px -3px 7px 0 inset,
                rgba(138, 72, 96, 0.16) 4px 4px 8px 0 inset;
            }

            .bar-label {
              margin-top: 6px;
              color: rgba(74, 85, 104, 0.45);
              font-size: 9px;
              line-height: 1;
              font-weight: 700;
              text-align: center;
              white-space: nowrap;
            }

            @media (max-width: 380px) {
              .page { padding-left: 16px; padding-right: 16px; }
              .range-button { padding-left: 14px; padding-right: 14px; }
              .bars { gap: 3px; }
              .month .bar-label { font-size: 0; }
              .month .bar-item:nth-child(5n + 1) .bar-label { font-size: 8px; }
            }
          </style>
        </head>
        <body>
          <main class="page">
            <section class="stack">
              <div class="range-wrap">
                <div class="range-tabs" role="tablist" aria-label="date range">
                  <button type="button" class="range-button" data-range="today">&#20170;&#26085;</button>
                  <button type="button" class="range-button active" data-range="week">&#36817; 7 &#22825;</button>
                  <button type="button" class="range-button" data-range="month">&#36817; 30 &#22825;</button>
                </div>
              </div>

              <article class="clay-card-interactive clay-blue">
                <div class="card-top">
                  <span class="label">&#23436;&#25104;&#20219;&#21153;&#25968;</span>
                  <div class="icon-bubble">${checkCircleSvg()}</div>
                </div>
                <div class="value" id="completedValue">0 &#20010;&#20219;&#21153;</div>
                <div class="subtitle" id="completedSubtitle">&#36873;&#20013;&#26085;&#26399;&#33539;&#22260;&#20869;&#23436;&#25104;&#30340;&#35760;&#24405;</div>
              </article>

              <article class="clay-card-interactive clay-pink">
                <div class="card-top">
                  <span class="label">&#24179;&#22343;&#38382;&#39064;&#25968;</span>
                  <div class="icon-bubble">${trendSvg()}</div>
                </div>
                <div class="value" id="averageValue">0.0 &#20010; / &#20219;&#21153;</div>
                <div class="subtitle" id="averageSubtitle">0 &#20010;&#38382;&#39064; / 0 &#20010;&#20219;&#21153;</div>
              </article>

              <section class="chart-panel" aria-label="daily chart">
                <div class="bars" id="bars"></div>
              </section>
            </section>
          </main>

          <script>
            const ranges = $rangesJson;
            const labels = {
              today: '\u4eca\u65e5',
              week: '\u8fd1 7 \u5929',
              month: '\u8fd1 30 \u5929'
            };

            function formatAverage(value) {
              return Number(value || 0).toFixed(1);
            }

            function render(rangeKey) {
              const range = ranges[rangeKey] || ranges.week || ranges.today || ranges.month;
              if (!range) return;

              document.querySelectorAll('.range-button').forEach((button) => {
                button.classList.toggle('active', button.dataset.range === range.key);
              });

              document.getElementById('completedValue').textContent = range.completedTasks + ' 个任务';
              document.getElementById('completedSubtitle').textContent = labels[range.key] + '完成的记录';
              document.getElementById('averageValue').textContent = formatAverage(range.averageProblems) + ' 个 / 任务';
              document.getElementById('averageSubtitle').textContent = range.totalProblems + ' 个问题 / ' + range.tasks + ' 个任务';

              const bars = document.getElementById('bars');
              bars.className = 'bars ' + (range.key === 'month' ? 'month' : '');
              bars.innerHTML = '';

              const maxValue = Math.max(1, ...range.days.map((day) => Math.max(day.problems, day.tasks, day.completedTasks)));
              range.days.forEach((day) => {
                const value = Math.max(day.problems, day.tasks, day.completedTasks);
                const height = value <= 0 ? 3 : Math.max(10, Math.round((value / maxValue) * 176));
                const item = document.createElement('div');
                item.className = 'bar-item';
                item.innerHTML = '<div class="bar ' + (value > 0 ? 'has-value' : '') + '" style="height:' + height + 'px"></div><span class="bar-label">' + day.label + '</span>';
                bars.appendChild(item);
              });
            }

            document.querySelectorAll('.range-button').forEach((button) => {
              button.addEventListener('click', () => render(button.dataset.range));
            });

            render('week');
          </script>
        </body>
        </html>
    """.trimIndent()
}

private fun buildRangesJson(ranges: List<StatsRangeUi>): String {
    return ranges.joinToString(prefix = "{", postfix = "}") { range ->
        "\"${range.key}\":${range.toJson()}"
    }
}

private fun StatsRangeUi.toJson(): String {
    val daysJson = days.joinToString(prefix = "[", postfix = "]") { day ->
        "{" +
            "\"label\":\"${day.label}\"," +
            "\"tasks\":${day.tasks}," +
            "\"completedTasks\":${day.completedTasks}," +
            "\"problems\":${day.problems}" +
            "}"
    }
    return "{" +
        "\"key\":\"$key\"," +
        "\"completedTasks\":$completedTasks," +
        "\"tasks\":$tasks," +
        "\"totalProblems\":$totalProblems," +
        "\"averageProblems\":${String.format(Locale.US, "%.4f", averageProblems)}," +
        "\"days\":$daysJson" +
        "}"
}

private fun checkCircleSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
      <path d="m9 11 3 3L22 4"></path>
    </svg>
""".trimIndent()

private fun trendSvg(): String = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3 3v18h18"></path>
      <path d="m19 9-5 5-4-4-3 3"></path>
    </svg>
""".trimIndent()
