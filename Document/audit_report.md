# WindScore 代码审计报告

全面审阅了整个代码库的所有源文件后，按**严重程度**分级列出发现的问题。

---

## 🔴 严重（可能导致崩溃或数据损坏）

### 1. ScorePlayer 使用 `System.currentTimeMillis()` — commonMain 中不可用

**文件**: [ScorePlayer.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/engine/ScorePlayer.kt#L65)

```kotlin
var lastMs = System.currentTimeMillis()  // L65
```

`System.currentTimeMillis()` 是 JVM API，放在 `commonMain` 中。目前项目只有 JVM 目标（Desktop + Android）所以能编译通过，但这违反了 KMP 的 expect/actual 原则。如果未来添加 iOS/JS 目标将直接编译失败。

**同样的问题也出现在** [PianoRoll.kt L109](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/PianoRoll.kt#L109):
```kotlin
val phase = (System.currentTimeMillis() % beatMs.toLong()) / beatMs
```

---

### 2. NoteEntity 的 `isHit` 和 `durationTick` 是可变字段 — 共享状态被多处修改

**文件**: [NoteEntity.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/model/NoteEntity.kt)

```kotlin
data class NoteEntity(
    ...
    var durationTick: Long,   // Tie合并时修改
    var isHit: Boolean = false, // JudgmentEngine 多处修改
)
```

`NoteEntity` 是 `data class` 却有可变字段，且同一个实例被 `JudgmentEngine`、`ScoreSession`、`PianoRoll`（draw 阶段读取）同时访问。在协程并发场景下（`ScoreSession.scheduleHostedIfNeeded` 在后台协程推进），存在竞态条件。虽然目前可能不会出现明显问题（因为推进是顺序的），但这是一个架构隐患。

---

### 3. ScoreSession.init 中调用 `synth.start()` — 重复启动

**文件**: [ScoreSession.kt L94](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/engine/ScoreSession.kt#L94)

```kotlin
init {
    synth.start()  // ScoreSession 构造时启动
    ...
}
```

但 `App.kt` 在 `LaunchedEffect(running)` 中也调用 `synth.start()`（L111）。`DesktopSynth.start()` 有 `if (synth != null) return` 防御，所以不会崩溃，但 `setPracticeMode()` 会重新构建 `ScoreSession`（调用 `openScore()`），意味着在 MIDI 未运行时 synth 也会被悄悄启动，语义上有问题。

---

### 4. `closeScore()` 中 player.stop() 在 session=null 之后调用

**文件**: [App.kt L160-L164](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt#L160-L164)

```kotlin
fun closeScore() {
    session = null
    currentScore = null
    player?.stop()  // player 依赖 currentScore，但 currentScore 已被置空
}
```

`player` 是 `remember(currentScore)`，当 `currentScore` 变为 null 时会触发重组创建一个新的 `null` player。但在同一帧内，旧 player 的 `stop()` 调用可能与重组竞争。更安全的做法是先 stop 再清空。

---

## 🟠 中等（功能缺陷或体验问题）

### 5. MIDI 事件 collect 协程不会在 `running=false` 时取消

**文件**: [App.kt L109-L126](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt#L109-L126)

```kotlin
LaunchedEffect(running) {
    if (running) {
        synth.start()
        midiManager.start()
        midiManager.events.collect { ev -> ... }  // 永不结束的 collect
    }
}
```

当 `running` 从 `true` 变为 `false` 时，`LaunchedEffect` 会取消旧协程并重新启动。但新启动的协程因为 `running=false` 直接跳过 if 块什么都不做，而旧协程在取消前可能仍处理了若干事件。这不是严重 bug，但语义上 `midiManager.stop()` 和 `synth.stop()`（在 `onToggleMidi` 回调中）与协程取消之间有微小的时序窗口。

### 6. ScoreSession 的 `startPulseDecay` 永远不会停止

**文件**: [ScoreSession.kt L101-L110](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/engine/ScoreSession.kt#L101-L110)

```kotlin
private fun startPulseDecay() {
    scope.launch {
        while (true) {  // 永远循环
            delay(PULSE_DECAY_MS)  // 50ms
            ...
        }
    }
}
```

这个协程在 `ScoreSession` 构造时启动，但没有取消机制。当用户切换练习模式（`setPracticeMode` → `openScore` → 新的 `ScoreSession`）时，旧 Session 的脉冲衰减协程仍在 `scope`（`rememberCoroutineScope`）中运行，只有在离开 App Composable 时才会取消。每次切换模式都会**泄漏一个 50ms 定时器协程**。

### 7. `hostedJob` 未在 ScoreSession 销毁时取消

**文件**: [ScoreSession.kt L36](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/engine/ScoreSession.kt#L36)

```kotlin
private var hostedJob: Job? = null
```

与 #6 类似，`hostedJob` 在 `scope` 中启动但 `ScoreSession` 没有 dispose/close 方法。多次创建 Session 可能导致旧的托管推进协程短暂残留。

### 8. Android MIDI 只打开 `outputPort(0)` — 忽略多端口设备

**文件**: [AndroidMidiManager.kt L83](file:///c:/A1/Code/WindScore/composeApp/src/androidMain/kotlin/com/windscore/midi/AndroidMidiManager.kt#L83)

```kotlin
val port = device.openOutputPort(0)  // 硬编码 port 0
```

如果 MIDI 设备的输出端口不在 index 0（某些 USB MIDI 适配器），会导致无法接收事件。应该遍历 `device.info.portCount` 打开所有 output 端口。

### 9. `captureSnapshot` 中 `scrollWidth` 可能包含空白

**文件**: [OsmdHtml.kt L86](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/OsmdHtml.kt#L86)

```javascript
var w=osmdEl.scrollWidth||Math.ceil(svg.getBoundingClientRect().width);
```

两步渲染修复后，SVG 应该已经铺满容器，但如果存在微小的像素舍入误差，`scrollWidth` 仍可能比实际 SVG 内容宽 1-2px，导致截图右侧有极细的白线。更稳健的做法是直接使用 SVG 的 `getBoundingClientRect().width`。

### 10. 两步渲染改变了布局——zoom 变化可能导致换行位置变化

**文件**: [OsmdHtml.kt L337-L352](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/OsmdHtml.kt#L337-L352)

当前两步渲染不修改 `pageWidth`，只修改 `zoom`。OSMD 中 `updateGraphic()` 使用 `zoom` 影响系统换行决策。所以 zoom=1 时的布局（每行可能排 4 小节）和 fitZoom=2 时的布局（每行可能排 2 小节）可能不同，导致第二遍渲染后 SVG 宽度不再精确等于 `cW`。

> [!TIP]
> 可以考虑在第二遍渲染后再测量一次 SVG 宽度，如果误差超过 5%，用 CSS `transform: scale(cW/actualW)` 做微调。

---

## 🟡 轻微（代码质量、可维护性）

### 11. `App.kt` 过大 — 1165 行单文件

[App.kt](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt) 包含：WelcomeScreen、PlayerScreen、PlayerToolbar、PlayerControlBar、ControlSidebar、MainStage、PillToggle、CircularIconButton 等。建议拆分为独立文件/包。

### 12. 重复导入

**文件**: [App.kt L43 & L80](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt#L43)

```kotlin
import androidx.compose.ui.unit.dp  // L43
import androidx.compose.ui.unit.dp  // L80 — 重复
```

### 13. MusicXmlParser 中 `tempo` 解析使用 `toInt()` — 小数 BPM 被截断

**文件**: [MusicXmlParser.kt L138](file:///c:/A1/Code/WindScore/composeApp/src/jvmShared/kotlin/com/windscore/core/parser/MusicXmlParser.kt#L138)

```kotlin
if (t.isNotEmpty()) tempo = t.toInt()
```

某些 MusicXML 使用小数 tempo（如 `120.5`），`toInt()` 会抛 `NumberFormatException`。应使用 `toDoubleOrNull()?.roundToInt()`。

### 14. MusicXmlParser 中 `resolveRootFile` 未禁用外部 DTD

**文件**: [MusicXmlParser.kt L270-L274](file:///c:/A1/Code/WindScore/composeApp/src/jvmShared/kotlin/com/windscore/core/parser/MusicXmlParser.kt#L270-L274)

```kotlin
val factory = DocumentBuilderFactory.newInstance().apply {
    isValidating = false
    // 缺少 setFeature(...load-external-dtd, false) 等
}
```

主解析路径（`parseXml`）已正确禁用了外部 DTD/实体，但 `resolveRootFile` 解析 `container.xml` 时没有设置同样的安全特性。虽然 `container.xml` 通常很简单，但这是一个安全不一致。

### 15. ScorePlayer 中 `Dispatchers.Default` 可能在 Android 上不理想

**文件**: [ScorePlayer.kt L64](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/engine/ScorePlayer.kt#L64)

```kotlin
job = scope.launch(Dispatchers.Default) {
```

`Dispatchers.Default` 在 `commonMain` 中引用。虽然在 JVM 平台可用，但如果未来扩展到其他平台可能需要调整。

### 16. `JudgmentEngine.seekTo` 重置 *所有* 组的 isHit — O(n) 操作

**文件**: [JudgmentEngine.kt L130-L147](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/core/engine/JudgmentEngine.kt#L130-L147)

```kotlin
fun seekTo(targetTick: Long) {
    groups.flatten().forEach { it.isHit = false }  // 遍历所有音符
    ...
    for (i in 0 until targetIdx) {
        groups[i].forEach { it.isHit = true }      // 再标记前面的
    }
}
```

对于大型乐谱（数千音符），每次 seek 都做两次全遍历。如果 seek 频繁（拖拽操作），可能卡顿。

### 17. Desktop `ScoreFilePickerButton` 在 UI 线程调用 `JFileChooser`

**文件**: [ScoreFilePickerButton.kt (desktop) L20-L29](file:///c:/A1/Code/WindScore/composeApp/src/desktopMain/kotlin/com/windscore/ui/ScoreFilePickerButton.kt#L20-L29)

`JFileChooser.showOpenDialog()` 是阻塞调用，在 Compose 的 `onClick` lambda 中直接调用会阻塞 UI 线程。后续的 `file.readBytes()` 和 `MusicXmlParser.parse()` 也是 IO/CPU 密集操作。应该移到后台线程。

### 18. `recentScores` 列表没有持久化

[App.kt L97](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/App.kt#L97): `val recentScores = remember { mutableStateListOf<Score>() }` — 每次启动都是空的。且 `Score` 对象包含完整的 `rawXml` 和 `notes` 列表，存 10 个在内存中可能占用较多内存。

### 19. `NativeSheetView` 光标追焦在 draw 阶段修改 `scrollY`

**文件**: [NativeSheetView.kt L91-L98](file:///c:/A1/Code/WindScore/composeApp/src/commonMain/kotlin/com/windscore/ui/NativeSheetView.kt#L91-L98)

```kotlin
// 在 Canvas draw lambda 中：
if (cursorRawY < viewHInSrc * 0.15f || cursorRawY > viewHInSrc * 0.5f) {
    scrollY = (step.y - viewHInSrc * 0.2f)...  // 修改状态
}
```

在 draw 阶段修改 Compose State（`scrollY`）会触发**立即重绘**循环。虽然因为第二次 draw 时条件不再满足所以不会无限循环，但这不是推荐的做法，可能导致一帧内多次绘制。

---

## 📊 总结

| 级别 | 数量 | 关键问题 |
|------|------|---------|
| 🔴 严重 | 4 | JVM API 泄漏到 commonMain、可变共享状态竞态、重复 synth.start、closeScore 顺序 |
| 🟠 中等 | 6 | 协程泄漏、Android MIDI 端口、截图宽度、两步渲染精度 |
| 🟡 轻微 | 9 | 代码组织、tempo 解析、UI 线程阻塞、draw 阶段状态修改 |

> [!IMPORTANT]
> 最值得优先修复的是 **#6（脉冲衰减协程泄漏）** 和 **#13（tempo 小数 BPM 崩溃）**——前者会随着用户操作逐渐积累资源占用，后者会在特定 MusicXML 文件上直接崩溃。
