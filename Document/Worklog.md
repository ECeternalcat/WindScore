<!-- 
[AI CLIENT DIRECTION]
- DEFAULT RESPONSE LANGUAGE: English only (for general analysis, code reviews, and log updates).
- EXCEPTION: You can output Chinese ONLY when explicitly commanded to perform localized translation tasks.
-->

# WindScore 工作日志 (Worklog)
。

## Phase 1 —— 基础设施 / 解析器 / 卷帘 / 状态机（已完成）

> 对应 `Document_1.md` 第 1–3 章 + 最初 4 个 Milestone。

### Step 1 — KMP + Compose Multiplatform 工程骨架与 MIDI 双端识别
- `composeApp` 单模块（commonMain / androidMain / desktopMain / jvmShared），版本目录，Kotlin DSL。
- 硬件抽象层 `expect/actual`：`MidiManager` 接口暴露 `Flow<MidiEvent>`。
  - Desktop：`javax.sound.midi.MidiSystem`。
  - Android：`android.media.midi.MidiManager`，自写状态字节解析。
  - **坑 3.3**：CC（踏板）信号硬过滤，只认 NoteOn/NoteOff。
- 版本栈（本机缓存、彼此兼容）：Kotlin 2.3.10 + Compose MP 1.11.1 + **AGP 8.13.2 + Gradle 8.14.4**
  （原 wrapper 为 Gradle 9.6.1，但 AGP 8.x 与 Gradle 9.6 不兼容；AGP 9 又破坏 KMP 集成，故降级）。

### Step 2 — MusicXML 降维解析器
- `MusicXmlParser`（jvmShared，JDK DOM + java.util.zip，无外部依赖）。
- staff 1→右手 / 2→左手；和弦不推进时间；backup/forward 正确推时间轴；休止符只消耗时间。
- **坑 3.1**：Tie 同音连线合并（真谱 341 个 Tie 全部正确合并）。
- 支持 ZIP(.mxl) 与未压缩 .xml（按 magic bytes 判断）；保留原始 XML 供看谱模式。

### Step 3 — Compose Canvas 钢琴卷帘
- `PianoRoll` 纯 `Canvas`（避免重组风暴）；视口裁剪；`currentTickProvider` draw 阶段读取。
- 左手冷色 / 右手暖色；当前待弹组白边高亮；底部键盘条。**坑 3.4**：keyWidth 按屏宽动态等分。

### Step 4 — 判定状态机（“等待模式”灵魂）
- `JudgmentEngine`（commonMain 纯 Kotlin）：按 startTick 分组，按对瞬间推移到下一组；错音无视。
- `freshKeys` 解决震音/重复音；和弦每次 NoteOn 重判天然容忍时间差（坑 3.2）。
- `ScoreSession`（Compose 包装）暴露 currentTick/progress/pendingPitches 等。

---

## Phase 2 —— 产品化与体验进阶（已完成）

> 对应 `Document_1.md` 第 4–5 章。

### Step 1 — 软件合成器（SoftSynth）
- `AudioSynth` 接口；Desktop 用 `javax Synthesizer`；Android 用 **billthefarmer/mididriver v1.29**（本地 AAR，flatDir 消费，4 ABI native lib）。
- 验证：Desktop 真实发声，noteOn API 延迟 ~225μs。

### Step 2 — 单/双手分离与自动托管
- `PracticeMode { BOTH, RIGHT_ONLY, LEFT_ONLY }`；托管手自动 isHit + SoftSynth 发声（按 tempo 延迟 noteOff）。
- `PianoRoll` 加 `hostedTrack`，托管轨道透明度 ×0.3。

### Step 3a — 准度计分 + 卡壳热力图
- 干净命中 / 练习轨道总数 × 100；过程绝不打叉/飘红；结算页 `ResultScreen` 温和热力图（冷绿→暖琥珀）。

### Step 3b — 本地 IO
- `expect @Composable ScoreFilePickerButton`：Desktop JFileChooser，Android SAF。

### Step 4 — OSMD 看谱模式（实验性）
- `expect @Composable ScoreSheetView`：Android WebView + OSMD；currentTick 推进驱动光标步进。

---

## 增强与修复轮次（最新）

### OSMD 看谱模式修复（Desktop 真正可用）
- **CDN 路径 bug 修复**：原 `build/minimal/opensheetmusicdisplay.min.js` 不存在，正确为 `/build/opensheetmusicdisplay.min.js`（固定 1.8.9）。
- HTML/JS 构建提到 commonMain `OsmdHtml`，双端共享；原始 XML 放 `type="text/xml"` script 免转义；暴露 `window.__advanceCursor(n)`。
- **Desktop 用 JavaFX WebView 真正渲染**（不再是占位）：Compose `SwingPanel` → `JFXPanel` → JavaFX `WebView`。
  - 依赖 `org.openjfx:javafx-{base,graphics,controls,swing,media,web}:21.0.5:win`。
  - 实跑发现 `WebEngine` 初始化需 `javafx-media`（缺则 `NoClassDefFound: jfxmedia...PlayerStateListener`），已补全。
  - JavaFX 不可用时优雅降级为提示，绝不崩溃。
- 光标同步：currentTick 每推移一步调用 `__advanceCursor(1)`（按组近似，足够自动翻页）。

### 按键可视化反馈（防盲弹）
- `ScoreSession.heldPitches` 暴露为 Compose State（按键变化可观察）。
- `PianoRoll` 加 `heldPitchesProvider`：当前按下音高的琴键高亮 + 判定线上方发光块，让长辈看到自己弹了什么。

### MIDI 多设备修复（Code 10 bug）
- 现象：Yamaha 在 Windows 设备管理器有两个条目，其一为 Code 10（设备无法启动）。旧实现 `firstOrNull` 只挑第一个能发送的设备，若 Code 10 排在前面就会打开它、收不到信号，导致“软件无反应”，必须禁用该设备；而 Web MIDI 网站监听所有设备故不受影响。
- 修复：`DesktopMidiManager.start()` 改为**打开所有可发送设备、合并到同一事件流**；`getMidiDevice` 抛异常或 `open()` 失败（Code 10）的设备 try/catch 静默跳过并打印诊断，继续接入真正工作的设备。Android 端同样改为打开所有设备。
- 验证：Code 10 设备启用状态下，应用正常连接 P-Series 并可弹奏。

---

## 验证状态
- `:composeApp:desktopTest` —— **17 测试全 PASSED**（synth 真实发声 / parser 含 Tie / engine 全分支含隔离+计分 / 真实乐谱端到端集成）。
- `:composeApp:assembleDebug` → `composeApp-debug.apk`（~19MB，含 mididriver native libs + WebView）。
- `:composeApp:desktopJar` 通过；`:composeApp:run` 实跑冒烟通过（JavaFX OSMD 渲染、MIDI 多设备、按键反馈）。

## 增强与修复轮次（最新）

### OSMD 同步残留清理
- 移除 `Score.cursorStepTicks` 与 `MusicXmlParser` 中对应的“原始音符起始 tick”列表；当前 OSMD 已使用 OSMD 自身时间戳自校准，该字段不再使用，避免留下“按组近似”的误导性残留。

### 纯托管段落实时推进（消除 burst）
- `JudgmentEngine.step()` 处理完一个纯托管组后主动返回，不再连续 burst 推进。
- 新增 `JudgmentEngine.advanceHosted()`：显式推进一个纯托管组，供上层按节奏调度；用户按键不再触发纯托管组推进。
- `ScoreSession` 增加托管段调度协程：记录上一个托管组起始 tick，按 `(currentTick - lastHostedTick) * msPerTick` 延迟后调用 `advanceHosted()`，使连续左手/右手托管段落按真实节奏发声，UI 也随之逐组推进。
- 新增单元测试 `consecutiveHostedGroupsDoNotBurst` 覆盖该行为；更新集成测试 `autoPlay` / `fumbledPlaythroughLowersScore` 以显式调用 `advanceHosted()`。

### JavaFX 跨平台分类器
- `composeApp/build.gradle.kts` 新增 `javafxClassifier`：根据构建宿主 OS 自动选择 `win` / `mac` / `mac-aarch64` / `linux`，不再硬编码 `:win`。

### OSMD 离线化
- 将 `opensheetmusicdisplay@1.8.9.min.js` (~1.17 MB) 打包到：
  - `composeApp/src/desktopMain/resources/opensheetmusicdisplay.min.js`
  - `composeApp/src/androidMain/assets/opensheetmusicdisplay.min.js`
- `OsmdHtml.build()` 改为接收 Base64 编码的 JS，通过 `data:application/javascript;base64,...` 注入，不再依赖 jsDelivr CDN。
- Desktop / Android `ScoreSheetView` 分别从 classpath / assets 读取 JS 并 Base64 编码后传入。

## 验证状态
- `:composeApp:desktopTest` —— **18 测试全 PASSED**（新增 `consecutiveHostedGroupsDoNotBurst`）。
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` 双端编译通过。

---

## Phase 3 —— 高阶功能与家庭互动（已完成）

> 对应 `Document_1.md` 第 6 章。

### 6.1 智能分段循环练习 (A/B Repeat)
- `JudgmentEngine` 新增 `setLoop(startTick, endTick)` / `clearLoop()`，设置后自动重置到循环起点。
- 越过 `loopEndTick` 时 `jumpToLoopStart()`：定位循环起点组、清空 activeKeys（要求重新按键）、重置循环段内 `isHit`、重新计算 `cleanHitCount` 防止分数虚高。
- `ScoreSession` 暴露 `isLoopActive`、`loopRange`、`setLoop()`、`clearLoop()`。
- `App.kt` 提供“循环起点 / 循环终点 / 清除循环”按钮：先点起点记录待设 tick，再点终点激活循环。
- 新增单元测试 `abLoopJumpsBackAtEnd`、`abLoopDoesNotInflateCleanHitCount`、`clearLoopRestoresFullPlaythrough`。

### 6.2 指法标记渲染 (Fingering Display)
- `NoteEntity` 已含 `fingering: String?`。
- `MusicXmlParser.readFingering()` 兼容 `<notations><fingering>` 与 `<notations><technical><fingering>`；Tie 合并保留起始音指法。
- `PianoRoll` 使用 `rememberTextMeasurer()` 在音符方块中央绘制白色粗体指法数字，字号 10sp~36sp 随 keyWidth 自适应。
- 新增单元测试 `fingeringIsExtractedFromTechnicalNotation`。

### 6.3 去中心化远程推歌
- 保持搁置（文档已明确为远期展望）。

### 6.4 动态节拍脉冲 (Visual Pulse)
- `ScoreSession` 维护 `pulseIntensity` Compose State：每次 progress 推进置 1.0，随后以 50ms 间隔按 0.97 衰减，下限 0.15。
- `PianoRoll` 通过 `pulseIntensityProvider` / `bpmProvider` 接收状态。
- `drawBackground()` 绘制水平淡蓝光带，alpha 由 `sin(phase * π) * pulseIntensity` 调制，按 BPM 闪烁；卡住时 intensity 衰减变暗。

## 验证状态
- `:composeApp:desktopTest` —— **21 测试全 PASSED**（新增 A/B 循环 3 个 + 指法 1 个）。
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` 双端编译通过。
- `:composeApp:assembleDebug` 成功。

---

## Beta UI  overhaul（基于 Document/DESIGN.md）

为进入 beta 测试，按 Figma 营销页设计系统对 UI 进行全面重构：

### 设计系统主题
- 新增 `com.windscore.ui.theme.WindScoreTheme` / `WindScoreColors` / `WindScoreSpacing` / `WindScoreRounded`。
- 颜色：黑白编辑基调（primary/canvas/ink）+ pastel 色块（block-lime/lilac/cream/pink/mint/coral/navy）。
- 字体：按 DESIGN.md 的 display/body/button/mono 尺寸、字重、字距定制 Material Typography；使用系统无衬线 + 等宽回退。
- 圆角：pill（50dp）用于所有按钮，lg（24dp）用于色块区段。

### 全局组件
- 新增 `PillButton`（PRIMARY / SECONDARY 两种表面），统一所有 CTA 为胶囊形。
- `ScoreFilePickerButton` 增加 `style: PillButtonStyle` 参数，Desktop / Android actual 均改用 `PillButton`。

### App.kt 重构
- 顶部栏：56dp 白底黑字，左侧品牌名 + 副标题，右侧 pill 按钮组（解析乐谱 / 打开文件 / 重置 / MIDI 开关）。
- 控制条：双手/右手/左手模式用 pill toggle；卷帘/看谱切换用 pill toggle；A/B 循环控件同一行紧凑排列。
- 主舞台：大块 `blockLime` 色块区段（rounded lg + 48dp padding）承载 `PianoRoll` 或 `ScoreSheetView`。
- 空状态：居中大字 Hero + 副标题 + 双 pill CTA。

### PianoRoll 配色迁移
- 背景由深色改为白/soft 渐变。
- 左手音符改为 `blockMint`，右手音符改为 `blockCoral`；命中为高饱和度，未命中为暗 pastel。
- 键盘区改为白键 + 黑键 + 发丝分隔线；按住反馈改为 `accentMagenta` 淡光。
- 节拍脉冲光带改为 `blockLilac`。

### ResultScreen 配色迁移
- 由深色半透明卡片改为 `blockCream` 大圆角色块卡片。
- 大分数使用 display 风格字重与负字距。
- 按钮改为 `PillButton` PRIMARY。

## 验证状态
- `:composeApp:desktopTest` —— **21 测试全 PASSED**。
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` 双端编译通过。

## 已知遗留
- 无当前已知遗留。

---

## 桌面/手机双端布局重构（Native App Layout）

按“Windows/Android 软件”重新设计整体导航与页面结构，不再照搬 Figma 营销页的长页布局：

### ScoreSession 崩溃修复
- 修复点击“解析乐谱/打开文件”时 `NullPointerException: Cannot invoke "State.getValue()"`。
- 根因：`ScoreSession` 中 `var xxx by mutableStateOf(...)` 的 `by` 委托在 `init { sync() }` 阶段偶发 delegate field 未就绪。
- 方案：将 `currentTick` / `progress` / `finished` / `heldPitches` / `pulseIntensity` 改为显式 `MutableState` + 自定义 getter/setter；`init` 中属性已初始化后再调用 `sync()`。

### 乐谱元数据
- `Score` 新增 `title: String` 字段；`MusicXmlParser` 解析 `<work-title>` / `<movement-title>` / `<credit-words>`，缺失时回退为“未命名乐谱”。
- 用于桌面欢迎页与 Android 乐谱库列表展示。

### 桌面端欢迎页（Photoshop 风格）
- `App(isCompact = false)` 进入欢迎页：
  - 顶部极简品牌栏（WindScore / 钢琴伴学）。
  - 左侧大卡片：`blockLime` 色块、中央白色方块 + `plus_thin` 图标、下方“添加乐谱”标题、再下方说明文字 + “打开文件” pill 按钮。
  - 右侧“最近打开的乐谱”面板：`surfaceSoft` 卡片，列表项使用 `music_notes_thin` 图标 + 标题/音符数/BPM。
- 点击示例/文件后进入演奏页；返回时回到欢迎页。

### Android 端乐谱库列表 + 强制横屏
- `MainActivity` 在 `setContent` 前设置 `requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE`。
- `App(isCompact = true)` 进入乐谱库列表：
  - 顶部“示例乐谱”/“打开文件” pill 按钮。
  - 下方 `surfaceSoft` 列表区，展示最近打开的乐谱；点击后进入演奏页（与桌面共用 `PlayerScreen`）。

### 演奏页统一结构
- 顶部工具栏：返回（`arrow_left_thin`）、乐谱标题、重置（`arrows_counter_clockwise_thin`）、MIDI 开始/停止 pill。
- 控制条：双手/右手/左手 pill toggle；看谱/卷帘圆形图标按钮（`piano_keys_thin` / `book_open_text_thin`）；A/B 循环起点/终点/清除圆形图标按钮（`repeat_thin` / `x_thin`）。
- 主舞台：大块 `blockLime` 色块区承载 `PianoRoll` 或 `ScoreSheetView`；看谱模式下有播放/暂停/停止圆形图标按钮（`play_thin` / `pause_thin` / `stop_thin`）。
- 已移除任何 marquee/跑马灯特效。

### 图标资源
- 新增 Compose Multiplatform Resources 依赖 `compose.components.resources`。
- 将 `icons/SVGs Flat/thin/` 中的 Phosphor 图标复制到 `composeApp/src/commonMain/composeResources/drawable/`（文件名下划线化）：
  - `plus_thin`, `clock_thin`, `music_notes_thin`, `play_thin`, `pause_thin`, `stop_thin`, `arrow_left_thin`, `arrows_counter_clockwise_thin`, `repeat_thin`, `piano_keys_thin`, `book_open_text_thin`, `x_thin`, `list_thin`。
- 在 commonMain 中通过 `windscore.composeapp.generated.resources.Res` 与 `painterResource(...)` 统一引用。

## 验证状态
- `:composeApp:desktopTest` —— **21 测试全 PASSED**。
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` 双端编译通过。
- `:composeApp:assembleDebug` 成功生成 APK。
- `:composeApp:run` 桌面实跑启动无崩溃。

## 已知遗留
- 无当前已知遗留。

---

## OSMD 看谱模式修正

### 光标不动修复
- 重写 `OsmdHtml.buildCursorTimes()`：不再用底层 iterator 的每一步映射光标 `next()`，而是直接调用 `osmd.cursor.next()` 并记录每个**实际光标位置**的 whole-note 时间戳。
- `__syncCursor(posWhole)` 只按这些真实位置推进，避免之前“迭代器步数 ≠ 光标步数”导致的卡住/跳越。
- `ScoreSheetView`（Desktop / Android）在 tick 回卷/循环/重置时调用 `__resetCursor()`，保证光标能回到起点。
- 看谱模式的光标现在同时跟随：
  - 播放按钮触发的 `ScorePlayer` 播放进度；
  - 练习模式下 `ScoreSession.currentTick` 的手动推进。

### 完整显示一页/两页
- `ScoreSheetView` 新增 `pages: Int` 参数（1 或 2）。
- OSMD 关闭 `autoResize`，采用 `pageFormat: 'A4_P'`；加载完成后按容器宽度计算 `zoom`：
  - `pages=1`：把 A4 页宽完整缩放进容器（一页宽度占满）。
  - `pages=2`：把两倍页宽缩放进容器（同时看到两页内容）。
- HUD 右上角显示当前缩放倍数与渲染耗时。

### 播放/停止按钮移出乐谱区
- `App.kt` 的 `PlayerControlBar` 新增播放/暂停/停止圆形图标按钮（`play_thin` / `pause_thin` / `stop_thin`）。
- `MainStage` 中移除原先嵌在乐谱显示区内的播放控制行，乐谱区域现在只显示乐谱。

## 验证状态
- `:composeApp:desktopTest` —— **21 测试全 PASSED**。
- `:composeApp:compileKotlinDesktop` / `:composeApp:compileDebugKotlinAndroid` 通过。
- `:composeApp:run` 桌面实跑，OSMD 诊断输出 `len=987` 条光标位置，渲染与光标初始化正常。

## 已知遗留
- 无当前已知遗留。
