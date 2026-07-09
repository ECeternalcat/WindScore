# WindScore 工作日志 (Worklog)

> 说明：本文件曾因异常变为全 NULL 字节而损坏，此处依据代码与提交记录完整重建。

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

## 已知遗留
- OSMD 光标按组近似（NoteEntity 数≠OSMD 渲染音符数，因 Tie 合并），非精确 1:1。
- JavaFX 依赖带 `:win` 分类器（macOS/Linux 需改对应分类器）。
- 纯托管段落（如 RIGHT_ONLY 连续左手）瞬时 burst 推进，未按真实节奏定时。
- OSMD 实跑需联网（jsDelivr CDN）。
