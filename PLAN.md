# 问题解决轨迹追踪器 — 项目计划书

## 1. 项目概述

### 1.1 产品定义

一款 Android 应用，帮助用户记录「完成一件事」的完整过程——遇到什么问题、如何解决的。通过时间线 UI 直观展示每个任务的解决轨迹，并利用 AI 分析用户在过程中暴露的弱点和偏好的解决策略。

### 1.2 核心场景

小明想给 Claude Code 接入 DeepSeek。他打开 App，新建任务「给 Claude Code 接入 DeepSeek」。过程中：
- 找不到 settings 文件 → 在 Linux.do 搜索找到位置
- 不知道如何修改 → 搜索找到配置教程，按步骤修改
- 重启后不生效 → 发现需要重启终端，配置成功

任务完成后，点「AI 分析」按钮，AI 告诉他：「你的主要问题是**对配置文件的文件位置不熟悉**，你的主要解决策略是**通过搜索引擎查找教程**」。

### 1.3 核心价值

| 价值点 | 说明 |
|--------|------|
| **复盘学习** | 回顾做事过程，发现知识盲区 |
| **模式识别** | AI 分析问题类型和解决方法的模式 |
| **成就感** | 看到一串问题被逐个击破的时间线 |

---

## 2. 技术方案

### 2.1 技术栈

| 层面 | 选型 | 版本 |
|------|------|------|
| 语言 | Kotlin | 1.9.x |
| UI 框架 | Jetpack Compose | 1.5.x + Material 3 |
| 架构 | MVVM + Repository | — |
| 本地存储 | Room Database | 2.6.x |
| 依赖注入 | Hilt | 2.48 |
| 异步 | Coroutines + Flow | 1.7.x |
| 导航 | Compose Navigation | 2.7.x |
| AI SDK | retrofit2 直接调用 DeepSeek API | — |
| JSON | kotlinx.serialization | 1.6.x |
| 最小 SDK | API 26 (Android 8.0) | — |

### 2.2 选型理由

- **Room**：结构化存储 Task → SolutionStep 一对多关系，支持按问题类型查询统计，比 DataStore 更合适
- **Compose**：时间线 UI 本质是卡片列表+连线，Compose 比 XML 更灵活
- **Retrofit 直接调用**：不需要 SDK，DeepSeek 是 OpenAI 兼容接口，常规 REST 调用即可
- **无后端、无登录**：纯本地 App，降低复杂度

---

## 3. 架构设计

### 3.1 架构分层

```
┌──────────────────────────────┐
│        UI Layer (Compose)     │  ← 屏幕/页面，组合 ViewModel
├──────────────────────────────┤
│      ViewModel Layer          │  ← 状态管理、UI 事件处理
├──────────────────────────────┤
│      Repository Layer         │  ← 统一数据访问入口（DB + API）
├──────────────────────────────┤
│   DataSource Layer            │  ← Room DAO / Retrofit API Service
├──────────────────────────────┤
│   Entity Layer                │  ← 数据模型
└──────────────────────────────┘
```

### 3.2 数据流向

```
User Action → ViewModel (event) → Repository → DAO/API
                                           ↓
                                      Entity / DTO
                                           ↓
UI ← Compose (observe) ← ViewModel (StateFlow) ← Repository (Flow)
```

### 3.3 不可变性保证

所有数据层返回的对象均为 `data class`，ViewModel 通过 `StateFlow` 暴露不可变状态，UI 重组触发于状态变化。

---

## 4. 数据模型设计

### 4.1 ER 关系

```
Task (1) ──── (N) SolutionStep
```

**Task**（任务表）
```
┌─────────────────┐
│       Task       │
├─────────────────┤
│ id: Long (PK)    │  自增主键
│ title: String    │  任务名称，如"给CC接入DeepSeek"
│ createdAt: Long  │  创建时间戳
│ completedAt: Long│  完成时间戳（null = 进行中）
│ status: Enum     │  IN_PROGRESS / COMPLETED
└─────────────────┘
```

**SolutionStep**（解决步骤表 — 有序的问题-解决对）
```
┌──────────────────────┐
│    SolutionStep       │
├──────────────────────┤
│ id: Long (PK)         │  自增主键
│ taskId: Long (FK)     │  关联任务
│ order: Int            │  排序号（0,1,2...）
│ problem: String       │  遇到的问题
│ solution: String      │  如何解决的
│ createdAt: Long       │  创建时间戳
└──────────────────────┘
```

**AnalysisResult**（AI 分析结果 — 缓存）
```
┌──────────────────────┐
│   AnalysisResult      │
├──────────────────────┤
│ id: Long (PK)         │
│ taskId: Long (FK)     │  关联任务
│ problemTypes: String  │  JSON 数组
│ solutionMethods:String│  JSON 数组
│ summary: String       │  AI 总结文本
│ analyzedAt: Long      │  分析时间戳
└──────────────────────┘
```

### 4.2 实体关系约束

- 一个 Task 有 N 个 SolutionStep，按 `order` 字段排序
- order 从 0 开始，连续递增
- Task 删除时级联删除关联的 SolutionStep 和 AnalysisResult
- SolutionStep 的 `problem + solution` 一对一绑定，不可多对多

### 4.3 示例数据

```
Task: {
  id: 1,
  title: "给 Claude Code 接入 DeepSeek",
  status: COMPLETED
}

SolutionSteps: [
  { id: 1, taskId: 1, order: 0,
    problem: "找不到 settings 文件",
    solution: "在 Linux.do 搜索找到了 settings.json 的位置" },
  { id: 2, taskId: 1, order: 1,
    problem: "不知道如何修改配置",
    solution: "搜索到配置教程，添加了 deepseek 的 api_key 和 base_url" },
  { id: 3, taskId: 1, order: 2,
    problem: "重启后不生效",
    solution: "发现需要完全退出终端再重启，配置成功" }
]
```

---

## 5. 页面 & 导航设计

### 5.1 导航图

```
HomeScreen (任务列表)
    │
    ├──[点击任务卡片]──→ TaskDetailScreen (时间线)
    │                        │
    │                        ├──[FAB 添加步骤]──→ AddStepDialog
    │                        ├──[点击 AI 分析]──→ AnalysisScreen
    │                        └──[返回]
    │
    ├──[FAB 新建任务]──→ CreateTaskDialog
    │
    └──[设置按钮]──→ SettingsScreen
```

### 5.2 页面详细设计

#### 5.2.1 首页 — 任务列表

```
┌────────────────────────────┐
│  🔍 问题解决轨迹    ⚙️      │  ← TopAppBar
├────────────────────────────┤
│                             │
│  ┌─────────────────────┐   │
│  │ 🟢 给CC接入DeepSeek  │   │  ← 任务卡片（绿色=完成，蓝色=进行中）
│  │    3 个问题 · 已完成  │   │
│  └─────────────────────┘   │
│  ┌─────────────────────┐   │
│  │ 🔵 搭建个人博客      │   │
│  │    5 个问题 · 进行中  │   │
│  └─────────────────────┘   │
│                             │
│         ┌───┐               │
│         │ + │               │  ← FAB 创建新任务
│         └───┘               │
└────────────────────────────┘
```

- 空状态：插图 + 引导文字「记录你解决问题的过程」
- 卡片显示：标题、问题数量、状态标签
- 左滑删除、点击进入详情

#### 5.2.2 任务详情 — 时间线

```
┌────────────────────────────┐
│  ← 返回                    │
├────────────────────────────┤
│  🎯 给CC接入DeepSeek       │
│  ──────────────────────    │
│                             │
│  ○ 第一步                   │  ← 圆形节点
│  │ ❓ 找不到settings文件    │
│  │ ✅ 在Linux.do搜索       │
│  │                         │
│  ○ 第二步                   │
│  │ ❓ 不知道如何修改配置    │
│  │ ✅ 搜索教程，修改配置    │
│  │                         │
│  ○ 第三步                   │
│  │ ❓ 重启后不生效          │
│  │ ✅ 完全退出终端重启     │
│  │                         │
│  ───── 结束标志 ─────      │
│                             │
│  [🤖 AI 分析]  [✏️ 继续添加]│  ← 底部操作
└────────────────────────────┘
```

- 时间线用 Compose Canvas 画竖线 + Canvas 画圆节点
- 每个节点内部是卡片：上面问题（红色/橙色图标），下面解决方案（绿色图标）
- 可长按编辑/删除单步
- AI 分析按钮仅在任务标记完成后显示

#### 5.2.3 AI 分析页

```
┌────────────────────────────┐
│  ← 分析结果                 │
├────────────────────────────┤
│                             │
│  📊 问题类型分析            │
│  ┌───────────────────────┐ │
│  │ 🔧 配置文件不熟悉  2次 │ │
│  │ 🔌 环境配置问题    1次 │ │
│  └───────────────────────┘ │
│                             │
│  🛠️ 解决方法偏好            │
│  ┌───────────────────────┐ │
│  │ 🔍 搜索引擎搜索    2次 │ │
│  │ 📖 官方文档查阅    1次 │ │
│  └───────────────────────┘ │
│                             │
│  💡 AI 总结                 │
│  ┌───────────────────────┐ │
│  │ 你在这件事中主要遇到   │ │
│  │ 的是对CC工具配置文件   │ │
│  │ 的位置和结构不熟悉，   │ │
│  │ 建议以后先阅读官方文档 │ │
│  └───────────────────────┘ │
└────────────────────────────┘
```

- AI 返回 JSON → 解析后渲染图表+总结
- 本地缓存分析结果（Room 存储）
- 若已分析过，直接展示缓存，提供「重新分析」按钮

#### 5.2.4 设置页

```
┌────────────────────────────┐
│  ← 设置                     │
├────────────────────────────┤
│  API Key     [___________]  │
│  Base URL    [api.deepseek] │
│  Model       [deepseek-chat]│
│                             │
│  [测试连接]                 │
│  [保存]                     │
└────────────────────────────┘
```

- API Key 加密存储（Android Keystore + EncryptedSharedPreferences）
- 默认 Base URL: `https://api.deepseek.com`
- 默认 Model: `deepseek-chat`

---

## 6. AI 集成设计

### 6.1 API 规格

```
POST https://api.deepseek.com/chat/completions
Authorization: Bearer {api_key}

Body:
{
  "model": "deepseek-chat",
  "response_format": { "type": "json_object" },
  "messages": [
    {
      "role": "system",
      "content": "你是一个问题解决分析专家。分析用户的问题解决过程，返回JSON。"
    },
    {
      "role": "user",
      "content": "任务: {title}\n\n解决过程:\n1. 问题: {p1}\n   解决: {s1}\n2. ...\n\n请分析问题类型和解决方法偏好。"
    }
  ]
}
```

### 6.2 期望返回 JSON

```json
{
  "problemTypes": [
    { "type": "配置文件不熟悉", "count": 2, "steps": [0, 1] },
    { "type": "环境配置问题", "count": 1, "steps": [2] }
  ],
  "solutionMethods": [
    { "method": "搜索引擎搜索", "count": 2, "steps": [0, 1] },
    { "method": "官方文档查阅", "count": 1, "steps": [2] }
  ],
  "summary": "在这件事中，你遇到的主要问题是对工具配置文件的位置和结构不熟悉。你偏好通过搜索引擎（尤其是社区论坛）来解决问题，效率较高。建议在接触新工具时先阅读官方文档了解配置文件结构。"
}
```

### 6.3 Prompt 设计要点

- System prompt 约束输出为 JSON
- User prompt 将完整的「问题→解决」链格式化后传给 AI
- 让 AI 自由分类问题类型和方法，不做预设

### 6.4 错误处理

| 场景 | 处理 |
|------|------|
| API Key 为空 | 提示用户先在设置页填入 |
| API Key 无效 (401) | 提示 Key 非法，引导去设置修改 |
| 网络超时 | 提示检查网络并重试 |
| 返回格式不合法 | 提示 AI 返回异常，建议重试 |
| 余额不足 (429) | 提示去 DeepSeek 官网充值 |

---

## 7. UI/UX 设计规范

### 7.1 颜色系统 (Material 3)

| Token | 用途 |
|-------|------|
| Primary | 主色（蓝色系，按钮、选中态） |
| Error | 问题标记色（红色系） |
| Tertiary | 解决方案标记色（绿色系） |
| Surface | 卡片背景 |
| Outline | 时间线竖线 |

### 7.2 动效

- 时间线节点进入：从下往上 FadeIn + SlideIn，从小到大 stagger
- 卡片点击：轻微 ripple 效果
- AI 分析中：打字机动画或 shimmer 占位符
- 页面切换：标准共享轴过渡

### 7.3 空状态

- 首页无任务：居中插图 + 「记录你的第一个问题解决过程」+ 创建按钮
- 任务无步骤：任务详情页显示「还没有添加问题，点击下方按钮开始」
- 无分析结果：显示「点击下方 AI 分析按钮获取洞察」

---

## 8. 文件结构

```
ProblemSolverTracker/
├── app/
│   └── src/main/
│       ├── java/com/jadeai/solvertracker/
│       │   ├── SolverTrackerApp.kt          // Application（Hilt入口）
│       │   ├── MainActivity.kt               // 单Activity
│       │   │
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── AppDatabase.kt        // Room数据库
│       │   │   │   ├── dao/
│       │   │   │   │   ├── TaskDao.kt
│       │   │   │   │   ├── SolutionStepDao.kt
│       │   │   │   │   └── AnalysisDao.kt
│       │   │   │   └── entity/
│       │   │   │       ├── TaskEntity.kt
│       │   │   │       ├── SolutionStepEntity.kt
│       │   │   │       └── AnalysisResultEntity.kt
│       │   │   ├── remote/
│       │   │   │   ├── DeepSeekApiService.kt  // Retrofit接口
│       │   │   │   └── dto/
│       │   │   │       ├── AnalysisRequest.kt
│       │   │   │       └── AnalysisResponse.kt
│       │   │   └── repository/
│       │   │       ├── TaskRepository.kt
│       │   │       ├── AnalysisRepository.kt
│       │   │       └── SettingsRepository.kt
│       │   │
│       │   ├── domain/
│       │   │   └── model/
│       │   │       ├── Task.kt                // 领域模型
│       │   │       ├── SolutionStep.kt
│       │   │       └── AnalysisResult.kt
│       │   │
│       │   ├── ui/
│       │   │   ├── navigation/
│       │   │   │   └── NavGraph.kt
│       │   │   ├── theme/
│       │   │   │   ├── Theme.kt
│       │   │   │   ├── Color.kt
│       │   │   │   └── Type.kt
│       │   │   ├── home/
│       │   │   │   ├── HomeScreen.kt
│       │   │   │   └── HomeViewModel.kt
│       │   │   ├── detail/
│       │   │   │   ├── TaskDetailScreen.kt
│       │   │   │   └── TaskDetailViewModel.kt
│       │   │   ├── analysis/
│       │   │   │   ├── AnalysisScreen.kt
│       │   │   │   └── AnalysisViewModel.kt
│       │   │   ├── settings/
│       │   │   │   ├── SettingsScreen.kt
│       │   │   │   └── SettingsViewModel.kt
│       │   │   └── components/
│       │   │       ├── TimelineView.kt        // 时间线组件
│       │   │       ├── TaskCard.kt            // 任务卡片
│       │   │       └── EmptyState.kt          // 空状态组件
│       │   │
│       │   └── di/
│       │       └── AppModule.kt               // Hilt模块
│       │
│       └── res/
│           └── values/
│               ├── strings.xml
│               └── themes.xml
│
├── build.gradle.kts (project)
├── app/build.gradle.kts
└── gradle/
```

---

## 9. 开发阶段

### Phase 1：骨架搭建（1-2天）
- 创建 Compose + Hilt + Room 项目骨架
- 配置 Gradle 依赖
- 定义数据实体和 DAO
- 实现主题系统

### Phase 2：核心功能 — 任务管理（1-2天）
- 首页任务列表（Room CRUD）
- 创建/编辑/删除任务
- 空状态 UI

### Phase 3：核心功能 — 时间线（2-3天）
- 实现 SolutionStep DAO
- 时间线 UI 组件（Canvas 画线 + 节点卡片）
- 添加/编辑/删除步骤
- 拖拽排序（可选 Phase 3.5）

### Phase 4：AI 分析（1-2天）
- Settings 页（API Key 管理）
- Retrofit Service 定义
- AI 分析请求/响应处理
- 分析结果页面
- 本地缓存分析结果

### Phase 5：打磨（1天）
- 动效
- 错误处理完善
- 边缘 case 处理

---

## 10. 测试策略

| 层级 | 测试类型 | 工具 | 目标覆盖率 |
|------|---------|------|-----------|
| Repository | 单元测试 | JUnit + Mockito | 80%+ |
| ViewModel | 单元测试 | JUnit + Turbine(Flow) | 80%+ |
| DAO | 仪表测试 | Room in-memory | 关键查询覆盖 |
| UI | Compose测试 | Compose Testing API | 核心交互覆盖 |
| E2E | 手动验收 | — | 全流程走通 |

测试原则（TDD）：
1. 先写 DAO 测试 → Room 实现
2. 先写 Repository 测试 → Repository 实现
3. 先写 ViewModel 测试 → ViewModel 实现
4. UI 层手动验收 + Compose 预览

---

## 11. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| DeepSeek API 返回格式不稳定 | AI 分析失败 | 加 JSON Schema 约束 + 解析容错 |
| 时间线 UI 复杂度高 | 开发延期 | 先用 Column 简单排列，再迭代 Canvas |
| Room 迁移 | 升级数据丢失 | 使用 destructive migration 初期，稳定后加 migration |

---

## 12. 待确认项

- [x] API 来源：DeepSeek 官网
- [x] 登录：不需要
- [x] 最小版本：Android 8.0 (API 26)
- [x] 数据关系：问题-解决一对一
- [x] UI 形式：时间线
- [x] AI 触发：手动按钮
- [x] 分类方式：AI 自由生成
- [x] Key 管理：用户自填

---

> 创建时间：2026-05-01
> 项目路径：C:\Users\郭鸿瑜\JadeAI\problem-solver-tracker\
