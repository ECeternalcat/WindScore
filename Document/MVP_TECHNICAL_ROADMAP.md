# WindScore MVP 技术实现路线

## 1. 文档目的

本文是 WindScore 从现有练习应用走向长期练习仪表盘的 MVP 技术路线。

它回答：

```text
现有代码已经具备什么
MVP 需要新增什么
数据如何从一次练习流向长期观察
每个阶段如何验收
```

本文不重新定义产品理念。产品目标、表达边界和不做什么，以 `Document_20260818.md` 为准。

本文也不要求一次完成最终的音乐分析系统。MVP 的目标是建立一条可信的最小闭环：

```text
记录一次练习
    ↓
保留原始 MIDI 和练习条件
    ↓
生成可追溯的基础观察
    ↓
在相近条件下聚合重复模式
    ↓
用中性语言展示变化
    ↓
用户选择是否处理这个模式
```

---

## 2. 当前实现基线

### 已有能力

现有代码已经具备以下基础设施：

- Kotlin Multiplatform / Compose Multiplatform，支持 Desktop 和 Android；
- MusicXML / MXL 解析；
- 降维后的 `Score` 和不可变 `NoteEntity`；
- 基于 tick 的音符时间线；
- 等待式 `JudgmentEngine`；
- 和弦分组、重复音重新按键、单手托管；
- A/B 循环、播放、定位、步进和速度控制；
- Desktop / Android MIDI 输入；
- `MidiInputController` 的事件路由；
- `ScoreLibraryController`、`PlaybackController`、`PracticeController`、`ScoringController`、`AppSettingsController`；
- CBOR 乐谱缓存、JSON 设置和评分历史；
- 本地原始 MusicXML 恢复和分析版本基础；
- 较完整的 commonTest / desktopTest 覆盖。

### 现有能力不能直接作为长期分析

当前 `JudgmentEngine` 的职责是让用户在等待模式下继续弹奏，并计算一次评分结果。它不能直接承担长期练习记录，原因包括：

- 时间轴会等待用户命中，不等于真实演奏时间；
- 错按只用于当前评分状态，不保留完整事件序列；
- `ScoreRecord` 只有一次评分的汇总结果；
- `MidiEvent` 当前只有音高和力度，没有事件时间戳；
- `NoteEntity` 只有 tick，没有小节、声部语义之外的稳定分析定位信息；
- Session 的运行状态和持久化 Attempt 尚未分离；
- 现有历史按评分记录组织，不支持相近条件筛选和观察聚合。

因此，长期分析应作为独立的记录和分析层接入现有练习流程，而不是修改现有评分结果的含义。

---

## 3. MVP 技术边界

### 支持

MVP 只支持以下范围：

```text
Solo 钢琴
固定乐谱版本
基础 MIDI Note On / Note Off
用户主动选择的短片段
固定或接近固定速度
单手或合手
练习模式与自由演奏模式的显式区分
本地持久化
```

MVP 的位置标识优先使用：

```text
scoreId + scoreVersion + startTick/endTick + noteId
```

小节显示可以继续使用已有的 OSMD 或 UI 映射，但在没有稳定小节映射之前，分析核心不得依赖小节文字。MVP 可以展示“片段位置”或“第 N 个时间区域”，不强行声称精确小节语义。

### 暂不支持

```text
复杂节奏质量模型
rubato 自动评价
力度与踏板趋势解释
左右手能力推断
跨乐谱版本自动对齐
自由改编自动识别
从 MIDI 自动判断用户意图
云端同步
LLM 直接读取 MIDI 并自行分析
```

---

## 4. 核心数据流

```text
Score
+ ScoreVersion
+ PracticeContext
        ↓
PracticeSession
        ↓
Attempt 开始
        ↓
Raw MIDI Event Recording
        ↓
Attempt 结束 / 异常结束
        ↓
AnalysisRun
        ↓
Fact / Observation
        ↓
Comparable History Set
        ↓
Candidate Trend
        ↓
User Meaning
```

重要约束：

- 原始 MIDI 事件只追加，不被用户反馈覆盖；
- 分析结果可以按新版本重新计算；
- 用户反馈是独立事件，不修改历史事实；
- 评分结果和长期分析结果可以引用同一次 Attempt，但含义不同；
- 没有足够证据时，分析层可以只保存事实，不生成观察或趋势。

---

## 5. MVP 数据模型

以下是逻辑模型，不要求第一版完全采用这些类名。

### 5.1 Score Identity

```kotlin
data class ScoreIdentity(
    val scoreId: String,
    val scoreVersion: Int,
    val sourceHash: String?,
)
```

现有 `Score.id` 和缓存版本可以作为第一版基础。后续不能只依赖文件路径、文件大小或修改时间判断两个乐谱是否相同；内容 hash 应作为正确性身份。

### 5.2 PracticeContext

```kotlin
data class PracticeContext(
    val scoreId: String,
    val scoreVersion: Int,
    val regionStartTick: Long,
    val regionEndTick: Long,
    val practiceMode: PracticeMode,
    val hintMode: HintMode,
    val requestedTempo: Int?,
    val observedTempo: Int?,
    val practiceModeType: PracticeModeType,
    val attemptType: AttemptType,
    val midiDeviceId: String?,
    val startedAt: Long,
)
```

MVP 至少需要保存：

```text
乐谱身份
乐谱版本
练习区域
手部模式
提示模式
请求速度
练习模式
Attempt 类型
MIDI 设备标识（可用时）
开始时间
```

### 5.3 RawMidiEvent

```kotlin
data class RawMidiEvent(
    val offsetMicros: Long,
    val type: RawMidiEventType,
    val pitch: Int,
    val velocity: Int?,
    val source: String?,
)
```

第一版时间戳应由应用在收到事件时使用单调时钟生成相对时间。墙钟时间只用于 Attempt 的开始和结束时间，不能用于事件间隔分析。

如果底层平台有可靠的 MIDI 时间戳，后续可以同时保存平台时间戳，但不能假设 Desktop 和 Android 的时间基准一致。

`MidiEvent` 可以先扩展为携带录制所需的时间信息，也可以在 `MidiInputController` dispatch 边界创建带时间戳的记录事件。无论采用哪种方式，原始记录必须发生在等待判定之前，不能只记录判定引擎收到的结果。

### 5.4 Attempt

```kotlin
data class PracticeAttempt(
    val attemptId: String,
    val sessionId: String,
    val context: PracticeContext,
    val events: List<RawMidiEvent>,
    val endedAt: Long?,
    val endReason: AttemptEndReason,
    val analysisVersion: Int?,
)
```

Attempt 的边界由练习流程显式产生，不由“是否已经弹到最后一个音”单独决定。

建议的结束原因：

```text
COMPLETED
USER_STOPPED
USER_RESTARTED
NAVIGATED_AWAY
MIDI_DISCONNECTED
APP_INTERRUPTED
FREE_PLAY_MARKED
```

至少要区分 `USER_STOPPED`、`MIDI_DISCONNECTED` 和 `FREE_PLAY_MARKED`，否则历史聚合会把不同含义的数据混在一起。

### 5.5 AnalysisRun

```kotlin
data class AnalysisRun(
    val analysisId: String,
    val attemptId: String,
    val analysisVersion: Int,
    val alignmentQuality: AlignmentQuality,
    val facts: List<AnalysisFact>,
)
```

所有派生观察都必须能回到：

```text
analysisId
attemptId
scoreId / scoreVersion
相关 noteId 或 tick 区间
```

### 5.6 UserMeaning

用户语义独立保存：

```kotlin
data class UserMeaning(
    val meaningId: String,
    val targetId: String,
    val kind: UserMeaningKind,
    val createdAt: Long,
    val includeInTrend: Boolean,
)
```

第一版支持：

```text
WORTH_ATTENTION
INTENTIONAL
PERSONAL_ADAPTATION
DATA_ANOMALY
FREE_PLAY
IGNORED
UNDECIDED
```

用户语义不删除 Attempt、RawMidiEvent 或 AnalysisFact。

---

## 6. Attempt 录制设计

### 6.1 录制位置

建议在 `MidiInputController` 的事件分发边界建立录制接口：

```text
MIDI manager
    ↓
MidiInputController
    ├─ RawAttemptRecorder
    ├─ AudioSynth
    └─ Practice / Scoring handler
```

这样可以保证：

- 录制到的事件与应用实际收到的事件一致；
- 等待式引擎是否推进不会影响原始记录；
- 练习和评分可以共享记录机制；
- MIDI 事件不会因为 UI 重组而重复记录。

### 6.2 Attempt 状态机

```text
IDLE
  ↓ startAttempt
RECORDING
  ├─ stop / restart → FINALIZING
  ├─ complete        → FINALIZING
  ├─ disconnect      → FINALIZING
  └─ navigate away   → FINALIZING
                         ↓
                      FINALIZED
```

Attempt 录制器必须具备：

- 明确的 start / stop；
- 重复 stop 幂等；
- 断连时可以正常收尾；
- 关闭页面时不会遗留活动录制器；
- 不把同一 NoteOn / NoteOff 记录两次；
- 录制失败时不影响现有练习和评分流程。

### 6.3 与等待模式的关系

等待式练习中，用户的实际时间和分数时间不同。Attempt 必须记录真实 MIDI 时间；不能使用 `ScoreSession.currentTick` 代替演奏时间。

可以同时保存：

```text
raw offset time
score tick at event reception
session current tick at event reception
```

但三者含义不同：

- `offset time` 用于真实停顿和连续性分析；
- `score tick` 用于把事件定位到乐谱区域；
- `session current tick` 用于解释等待式练习状态。

---

## 7. MVP 基础分析

### 7.1 分析目标

第一版只回答：

```text
这次 Attempt 记录到了什么
哪些位置出现了可重复的事件模式
```

第一版不回答：

```text
用户是否真的漏弹
用户是否掌握了曲子
这个停顿是否具有音乐性
用户的节奏能力如何
```

### 7.2 Fact

MVP 可以生成：

```text
检测到 NoteOn(pitch, time)
检测到 NoteOff(pitch, time)
某个预期音高在当前对齐窗口内未观察到
观察到当前区域之外的音高
两个 NoteOn 之间存在超过阈值的无事件区间
Attempt 到达 / 完成了指定区域
MIDI 在 Attempt 期间发生断连或异常
```

描述必须使用“未检测到”“观察到”“根据当前记录”等表达，不能直接使用“用户漏弹”“用户弹错”。

### 7.3 对齐策略边界

MVP 采用保守的局部匹配：

- 先限定在用户选择的 score tick 区域；
- 以局部时间窗口匹配预期音符；
- 和弦按同一 startTick 分组；
- 对重复音要求区分连续 NoteOn；
- 无法可靠匹配时生成 `UNDETERMINED`，不生成问题结论；
- 不尝试对自由演奏和大幅改编进行自动归一化。

现有 `JudgmentEngine` 的和弦与重复音规则可以复用为基础参考，但分析器不能直接依赖其等待状态推进。

### 7.4 区域化

MVP 的问题区域先使用固定时间线区域或用户选择的片段：

```text
regionStartTick
regionEndTick
```

区域聚合可以在相邻事件之间形成，但必须保存原始 noteId / tick 证据。动态合并和拆分问题区域留到 P2 以后。

### 7.5 中断 / 长停顿

第一版只记录中性事件：

```text
long_silence_interval
```

不要直接命名为 `stuck` 或 `hesitation`。

停顿是否值得展示，应至少考虑：

- 是否位于当前练习区域；
- 是否超过固定的最小时间阈值；
- 是否被 MIDI 断连或 Attempt 边界解释；
- 是否在多次相近条件 Attempt 中重复出现。

---

## 8. 相近条件历史聚合

### 8.1 比较键

MVP 的比较集合先采用明确的硬条件和有限的软条件。

硬条件：

```text
scoreId
scoreVersion
regionStartTick / regionEndTick
practiceMode
practiceModeType
attemptType
```

软条件：

```text
hintMode
requestedTempo 的容差范围
midiDeviceId
```

以下数据默认排除：

```text
FREE_PLAY
MIDI_DISCONNECTED
APP_INTERRUPTED
alignmentQuality 不足
用户标记为 DATA_ANOMALY
```

### 8.2 样本要求

MVP 不在样本不足时生成趋势。建议初始规则：

```text
少于 3 个可比较 Attempt：只展示 Fact
至少 3 个：可以展示重复 Observation
至少 5 个：才生成 Candidate Trend
```

具体数字需要通过真实数据验证，但必须先有显式规则，不由 LLM 自行决定。

### 8.3 长期模式的处理

当某个停顿或音高不匹配在长期历史中重复出现时，系统展示：

```text
最近一个月，其他区域大多能够连续完成，
但第 N 个片段附近持续出现相似停顿。
目前这是一个稳定出现的模式，原因尚未确定。
```

系统可以提供：

```text
[值得继续练]
[这是有意的]
[这是我的改编]
[可能是设备或识别问题]
[暂不判断]
```

在用户确认之前，该模式不能自动进入 `intentional`，也不能自动作为“练习失误”计入问题趋势。

### 8.4 趋势输出

MVP 的趋势输出必须带证据摘要：

```text
在 5 次相近条件的 Attempt 中，
第 N 个片段出现长停顿的次数为：4 → 3 → 1。
目前看起来，这个位置可能更容易连续完成。
```

不要输出：

```text
你的节奏能力提高了。
你已经掌握了这里。
```

---

## 9. 持久化与版本

MVP 使用本地存储，不引入云端服务。

建议目录：

```text
attempts/<scoreId>/<attemptId>.cbor
analysis/<attemptId>/<analysisVersion>.cbor
meanings/<targetId>.json
```

实际路径可以沿用现有 StorageProvider，但数据类型必须使用 `kotlinx.serialization`，并采用原子写入。

每种派生数据都需要：

```text
schemaVersion
analysisVersion
createdAt
sourceAttemptId
scoreId
scoreVersion
```

存储失败时：

- 不影响当前练习继续进行；
- 通过 `DiagnosticController` 告知用户；
- 不把未完整写入的 Attempt 当成完成记录；
- 下次启动可以识别并清理或恢复临时文件。

原始 Attempt 追加写入时，可以先使用内存缓冲，在 Attempt 结束时一次性保存。MVP 不要求录制过程中实时落盘，但必须明确处理应用被强制关闭的残留状态。

---

## 10. UI 接入顺序

### P0：隐藏式数据采集

不新增复杂 UI，只在现有练习流程中加入：

- Attempt 开始和结束；
- 练习上下文保存；
- Raw MIDI 事件录制；
- 录制状态和异常收尾；
- 本地保存。

验收：现有等待式练习和评分行为不变，且每次 Attempt 可以独立回放或读取。

### P1：单次事实

在练习结束或结果页展示少量中性事实：

```text
本次到达第 N 个位置
记录到若干次长停顿
有若干音高事件未能与当前片段可靠匹配
```

验收：每条展示都能跳回对应 Attempt 和 tick 区域；无法可靠判断时不显示强结论。

### P2：片段历史与重复观察

新增最小历史视图：

- Attempt 次数；
- 最近练习时间；
- 最近出现的片段；
- 完成范围变化；
- 中断 / 长停顿的重复次数；
- 音符匹配观察的重复次数；
- 比较条件摘要。

验收：用户可以看到一个片段在相近条件下的变化，且自由演奏、设备异常和不同条件不会被默认混合。

### P3：候选趋势与聚合反馈

新增：

- 只展示少量长期稳定模式；
- Candidate Trend 的保守措辞；
- 样本数和证据摘要；
- `暂不判断`；
- 用户语义标记；
- 排除模式后的趋势重算。

验收：用户不需要处理逐条事件；一次反馈可以作用于一个聚合模式；原始数据仍完整保留。

### P4：针对性行动

只复用已有操作：

```text
循环片段
从更早位置开始
降低速度
重复完整演奏
开启提示
关闭提示检查
```

行动由用户选择，不由系统强制。行动必须携带来源 `targetId`，以便后续 Attempt 与建议关联。

---

## 11. 需要新增或调整的代码边界

### 新增核心组件

```text
core/practice/AttemptRecorder
core/practice/PracticeAttempt
core/practice/PracticeContext
core/analysis/AttemptAnalyzer
core/analysis/ComparableAttemptSelector
core/analysis/TrendAggregator
core/analysis/UserMeaning
core/storage/AttemptStorage
```

命名可以按当前项目风格调整，但职责应保持独立。

### 现有组件调整

`MidiEvent`：

- 增加录制所需的时间信息，或在 dispatch 边界创建带时间的 RecordedEvent；
- 保持现有判定接口不被迫理解长期分析。

`MidiInputController`：

- 支持一个独立的 recorder 或 recording sink；
- recorder 不应通过 handler 消费 MIDI 事件；
- NoteOff 必须和 NoteOn 一样完整记录。

`PracticeController`：

- 负责 Attempt 生命周期与当前 PracticeContext 的协调；
- 不负责趋势聚合。

`ScoringController`：

- 可以复用 AttemptRecorder；
- `ScoreRecord` 继续表示一次评分结果；
- 不把 `ScoreRecord` 直接迁移成长期分析记录。

`ScoreSession / JudgmentEngine`：

- 保持等待式练习和一次性评分职责；
- 必要时提供事件到 score tick 的定位信息；
- 不直接保存原始 Attempt。

`ScoreLibraryController`：

- 后续可以提供某曲目的 Attempt 和分析历史；
- 不把历史加载逻辑塞回 `App.kt`。

---

## 12. 测试与验收

### 单元测试

必须覆盖：

```text
Attempt start / stop 幂等
NoteOn / NoteOff 顺序保留
相对时间单调不倒退
MIDI 断连正常结束
自由演奏排除
scoreId / scoreVersion 隔离
不同练习条件不会错误合并
样本不足不生成趋势
相同模式可以聚合
用户语义不修改原始事实
排除模式后趋势可重算
分析版本变化不覆盖旧分析
```

### 集成测试

至少验证：

```text
打开乐谱
开始片段练习
收到 MIDI
结束 Attempt
保存并重新打开应用
读取 Attempt 和分析结果
继续下一次练习
历史聚合只使用可比较样本
```

### 回归测试

新记录层不得改变：

- 等待模式的和弦判定；
- 重复音必须松键重弹；
- 单手托管节奏；
- A/B 循环边界；
- 播放和练习时间线同步；
- 评分历史结果；
- Desktop / Android MIDI 事件路由。

### 产品验收

MVP 不以“识别了多少错误”为主要成功标准。需要验证：

```text
用户是否认为系统标记的位置有帮助
用户是否愿意查看同一片段的历史
用户是否能理解“稳定模式但原因未知”
用户是否愿意偶尔确认聚合模式
用户是否执行针对性复练
用户是否认为历史变化比单次分数更有价值
```

---

## 13. 明确的技术非目标

MVP 阶段不做：

```text
重写 JudgmentEngine
把等待式 tick 当作真实演奏时间
把一次 ScoreRecord 改造成 Attempt
为每个事件生成 UI 卡片
让 LLM 自行决定阈值或比较样本
自动把重复停顿判定为有意表达
自动把音高不匹配判定为练习错误
为了分析引入完整 MusicXML AST
引入云端数据库或账号系统
```

---

## 14. 推荐第一开发批次

第一批只实现以下内容：

1. 定义 `PracticeContext`、`PracticeAttempt` 和 `RawMidiEvent` 的可序列化模型。
2. 为 MIDI 事件增加应用侧单调相对时间。
3. 在 `MidiInputController` 增加独立的 recorder 接口。
4. 在练习页面明确 Attempt 的开始、停止、重来和异常结束。
5. 使用临时目录为 Attempt 增加持久化和恢复测试。
6. 先不展示任何新趋势 UI，提供一个开发诊断页或测试读取接口。
7. 用真实练习录制 10-20 次 Attempt，检查数据是否足够支持后续分析。

第一批完成条件：

```text
一次练习可以被完整记录
记录可以和曲目、版本、区域、条件关联
NoteOn / NoteOff 和真实相对时间可重放
录制失败不会破坏现有练习
现有 93 个 Desktop 测试继续通过
Android 至少完成编译；真实设备测试单独执行
```

在没有完成这一批之前，不建议实现候选趋势、LLM 解释或复杂问题区域算法。
