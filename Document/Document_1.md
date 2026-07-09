钢琴伴学软件 (Senior Piano Companion) - 核心技术设计文档

1. 架构选型与系统分层

本项目采用 Kotlin Multiplatform (KMP) + Compose Multiplatform 为核心技术栈。
系统分为三层，严格遵循单向数据流 (UDF)：

展示层 (UI Layer): 纯 Compose 实现。包含主界面、设置菜单以及核心的钢琴卷帘 (Piano Roll) 渲染。

核心业务逻辑 (Core State Machine): 纯 Kotlin Common 代码。负责乐谱解析、时间轴推演、判定逻辑、计分系统。

硬件抽象层 (Hardware I/O): 通过 KMP 的 expect/actual 机制实现跨平台 MIDI 通信与音频合成。

2. 核心模块技术路线 (第一阶段基础)

2.1 硬件抽象：MIDI 通信模块

避免引入 C/C++ 或 Rust 库，直接利用两端现成的系统 API。

Common 层定义:
定义 MidiManager 接口，暴露 Flow<MidiEvent> 供上层监听，包含 NoteOn(pitch, velocity) 和 NoteOff(pitch)。

Desktop (Windows) 实现 (actual):
直接调用 JVM 自带的 javax.sound.midi.MidiSystem。该库成熟且极其稳定，无需额外依赖即可获取外接 MIDI 键盘的输入。

Android 实现 (actual):
调用 android.media.midi.MidiManager。需要注意 Android 的 USB Host 权限申请逻辑需封装在这一层。

2.2 乐谱解析引擎：降维打击

绝对不要让 Compose 层直接接触 MusicXML 数据。必须在加载阶段进行“降维”。

解析器: 考虑到 MusicXML 是庞大的 XML 树，可使用第三方纯 Kotlin XML 库（如 core-ktx 或 xmlutil）。

内部数据结构 (Flat Note List):
抛弃原有的五线谱排版信息、连音线、休止符。解析后的唯一真理是一个按时间排序的一维数组/集合：

data class NoteEntity(
val id: Int,             // 唯一ID
val pitch: Int,          // MIDI 音高 (21-108)
val track: TrackType,    // LEFT_HAND, RIGHT_HAND (通过 MusicXML 的 staff 标签区分)
val startTick: Long,     // 起始绝对时间（以 MIDI Tick 或毫秒计）
val durationTick: Long,  // 持续时间
var isHit: Boolean = false // 状态标记
)


2.3 判定状态机：长辈友好的“等待模式”

放弃基于系统时钟的音游判定，改为事件驱动的步进系统。

游标 (Cursor): 系统维护一个 currentTick。

按键池 (Active Key Pool): 维护一个当前物理键盘处于 NoteOn 状态的音高集合 Set<Int>。

判定逻辑 (Tick Update):

找出当前 currentTick 需要按下的所有 NoteEntity（可能是单音，也可能是和弦）。

检查 Active Key Pool 是否包含所有需要的 pitch。

如果包含：触发“命中”动画，将这些 Notes 标记为 isHit = true。然后将 currentTick 瞬间推移到下一组音符的 startTick。

如果不包含，或者按了其他错音：无视，不给予任何负面反馈。时间轴停滞，卷帘停止滚动，高亮保持。

2.4 渲染引擎：Compose Canvas

钢琴卷帘属于高频动态界面，严禁使用 Column/Row 堆叠组件来实现，会导致重组风暴和卡顿。

使用 Compose 的 Canvas 原生绘制。

视口 (Viewport) 裁剪: 只绘制 startTick 落在当前屏幕可见时间范围内的 NoteEntity。

颜色编码: 左手轨道使用冷色系（如蓝色块），右手轨道使用暖色系（如橙色块）。高亮音符使用高饱和度，未命中音符使用低饱和度。

UI 线程隔离: 状态机的 Tick 更新频率极高，应在协程的 Dispatchers.Default 中运行，最终只将视口的偏移量和高亮状态通过 State 暴露给 Compose 所在的主线程。

3. 需要重点防范的“坑”

3.1 同音连线 (Tie) 问题

MusicXML 中，跨小节的长音会用 Tie 连接两个音符。如果解析器将其读取为两个独立的音符，长辈会被迫在中间重新按一次，这完全违背乐理。
解决方案：在降维解析阶段，遍历所有音符，如果遇到带有 <tie type="start"> 和 <tie type="stop"> 且音高相同的相邻音符，必须在内存中合并其 durationTick，融合成一个单独的 NoteEntity。

3.2 和弦判定的微小时间差 (Tolerance Window)

长辈在弹奏和弦时，不可能毫秒级同时按下，大概率会产生数十毫秒的先后差。
解决方案：只要 Active Key Pool 达到了当前所需音符的子集，就开始一个短延迟（例如 100ms）的宽容窗口。在窗口期内，只要补齐了剩余按键即算通过。因为错误不惩罚，只要最终把所有键都按在手里了，就算过关。

3.3 离谱的延音踏板 (Sustain Pedal)

MIDI 输入中会包含大量 Controller Change (CC64) 的踏板信号。这会导致状态机判定混乱。
解决方案：在第一阶段，直接硬过滤掉所有的 CC 控制信号，状态机只认物理按键的 NoteOn 和 NoteOff。

3.4 屏幕长宽比适配

安卓平板比例多为 16:10 或 4:3，Windows 双屏可能是 32:9。
解决方案：卷帘的 X 轴（音高轴）不要写死宽度，必须根据屏幕总宽度动态计算 88 键的单位宽度。

4. 第二阶段：产品化与体验进阶 (Phase 2)

4.1 软件合成器 (SoftSynth) 选型锁定

为支持本身不发声的低端 MIDI 键盘，系统必须内置软音源。基于“可用性优先、工程成本最低”的原则，舍弃发烧级音源，采用极简方案：

Common 层接口:

interface AudioSynth {
fun noteOn(pitch: Int, velocity: Int)
fun noteOff(pitch: Int)
}


Android 端 (轻量级流派): 强制采用 billthefarmer/mididriver 库。它通过 JNI 调用 Android 底层 Sonivox EAS 引擎。APK 体积仅增加数十 KB，且延迟极低，足以满足“提供按键成功反馈”的核心诉求。

Desktop 端: 直接复用第一阶段调通的 javax.sound.midi.MidiSystem.getSynthesizer()，打开系统默认波表即可。

4.2 单/双手分离模式 (Track Isolation)

降低学习门槛的核心功能。

逻辑实现: 状态机支持接受 PracticeMode 参数（LEFT_ONLY, RIGHT_ONLY, BOTH）。

自动托管 (Auto-play): 当选择 RIGHT_ONLY 时，状态机接管左手轨道（track = LEFT_HAND）。时间轴到达时，系统自动判定为 isHit = true，并主动调用 SoftSynth 发声，长辈只需专心按右手。

视觉弱化: 托管轨道在画布上的透明度应降至 0.3，避免视觉喧宾夺主。

4.3 计分系统设计：抛弃时间维度

彻底抛弃传统音游的“Miss/Bad/Good/Perfect”时机判定机制。

纯准度计分: 结算公式：Score = 弹对的音符总数 / 谱面总音符数 * 100。

UI 展现红线: 实时弹奏过程中，绝对不允许出现“打叉”、“飘红”或“扣分”动效。若需要指出问题，仅在一曲结束后的结算页面，用温和的“热力图”展示卡壳较多的小节。

4.4 传统看谱模式降维方案 (WebView + OSMD)

支持“关闭卷帘，显示五线谱并自动翻页”，但严禁使用 Compose 手写五线谱排版。

引入 KMP WebView 组件。

内部加载成熟的开源 Web 引擎 OSMD (Open Sheet Music Display)。

Kotlin 端解析 MusicXML，同时也将原始 XML 字符串灌给 WebView 中的 OSMD。

JS Bridge 通信: Kotlin 状态机中的 currentTick 每向前推移一步，就向 WebView 发送一个 osmd.cursor.next() 的 JS 执行指令。通过控制光标，白嫖 OSMD 完善的自动避让和翻页算法。

5. 第二阶段开发路径 (Phase 2 Milestones)

Step 1: 发声系统构建 接入 Android 的 mididriver 和 Desktop 的内置 Synthesizer，测试程序主动发送 NoteOn 的发声延迟。

Step 2: 隔离与托管 修改状态机逻辑，实现轨道筛选、系统自动托管及 UI 透明度变化。

Step 3: 计分与本地化 IO 加入准度计分板。实现使用 Android SAF (Storage Access Framework) 和 Desktop FileDialog 读取本地外部 .xml 文件。

Step 4: 跨界看谱 (独立分支尝试) 在一个独立的 Git 分支中引入 WebView 和 OSMD，打通 Kotlin 与 JS 的游标同步，验证五线谱自动步进的渲染性能。