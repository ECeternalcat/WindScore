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
    var isHit: Boolean = false, // 状态标记
    val fingering: String? = null // 指法提示 (Phase 3 新增)
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

4.4 传统看谱模式：认清现实，拥抱“无感原生滚动”

不要再执迷于用黑魔法去计算“这页到底能塞多高”，这违背了 OSMD 的底层设计逻辑。为了追求稳定、秒开的加载体验，我们将全面转向单页瀑布流滚动 (Endless Scroll) 架构。

A. 本地通信（Ktor 依然保留）

保持使用 Ktor 提供本地 HTTP 服务，让 WebView 通过 Fetch 独立拉取乐谱 XML 流，彻底规避跨端大字符串传输导致的假死问题。加载速度控制在 3 秒以内。

B. CSS：隐藏滚动条，保留滚动能力 (Hidden Scroll)

核心视角的转换：允许 Web 层内容自由生长超出屏幕高度，但我们要把那个“象征着传统网页”的滚动条干掉，让界面看起来依然像一个全屏应用。

<style>
  body, html {
    margin: 0; 
    padding: 0;
    width: 100vw; 
    height: 100vh;
    overflow-x: hidden; /* 严禁横向滚动 */
    overflow-y: auto;   /* 允许纵向自由滚动 */
  }

  /* 彻底隐藏各种内核浏览器的滚动条，实现“无感” */
  ::-webkit-scrollbar {
    width: 0px;
    background: transparent;
  }
  * {
    scrollbar-width: none; /* Firefox */
  }
</style>


C. 横向锁死与极速排版 (Width-Locked Zoom)

只需管好水平方向的缩放比例，让五线谱永远横向撑满屏幕。垂直方向不管多高，直接让 OSMD 一次性算完。

const BASE_WIDTH = 800.0;
const container = document.getElementById("container");

// 仅根据物理宽度锁定 Zoom，保证大字号阅读体验
let initialZoom = container.clientWidth / BASE_WIDTH;
initialZoom = Math.max(0.8, Math.min(initialZoom, 2.5));

const osmd = new opensheetmusicdisplay.OpenSheetMusicDisplay("container", {
    backend: "canvas", 
    drawingParameters: "compacttight",
    autoResize: false // 关闭自动响应，避免不必要的重绘开销
});
osmd.zoom = initialZoom;

// 一把梭全量加载并渲染
osmd.load(xmlData).then(() => {
    osmd.render(); 
    osmd.cursor.show();
});


D. 游标驱动自动追焦 (Auto-Tracking Cursor)

既然画面是瀑布流，谁来负责滚动？答案是 OSMD 自带的光标。
不需要手写任何定位代码。OSMD 内部的 cursor.next() 原生自带了 ScrollIntoView 的逻辑。

Kotlin 端：只需根据 MIDI 输入和状态机的判断，向 JS 发送最简单的指令：
evaluateJavascript("osmd.cursor.next()")

JS 端表现：每当游标跨越乐谱行 (System) 甚至即将掉出可视区域底边时，浏览器就会极其平滑地自动往上滚动一定的像素。

最终 UX：长辈看着平板，只要手不弹错，那根高亮的光标和当前在弹的小节，永远会稳稳地停留在视线的核心区域。没有任何计算错误的割裂感，也永远不会出现断掉的连音线。

5. 第二阶段开发路径 (Phase 2 Milestones)

Step 1: 发声系统构建 接入 Android 的 mididriver 和 Desktop 的内置 Synthesizer，测试程序主动发送 NoteOn 的发声延迟。

Step 2: 隔离与托管 修改状态机逻辑，实现轨道筛选、系统自动托管及 UI 透明度变化。

Step 3: 计分与本地化 IO 加入准度计分板。实现使用 Android SAF (Storage Access Framework) 和 Desktop FileDialog 读取本地外部 .xml 文件。

Step 4: 跨界看谱与极速渲染 摒弃强制分页的计算陷阱。实装文档 4.4 节的“无感滚动+游标追焦”架构，确保首屏加载维持在 3 秒内的可用状态。

6. 第三阶段：高阶功能与家庭互动 (Phase 3)

6.1 智能分段循环练习 (A/B Repeat & Looping)

长辈练琴最忌讳“从头弹到尾”，极易因为一处卡壳产生挫败感。

逻辑实现：在 UI 层增加“框选小节”功能。修改状态机的边界判定，允许设置 loopStartTick 和 loopEndTick。

自动回卷：当 currentTick 触发到边界时，系统自动将时间轴和 Canvas 视口回溯到起点。

6.2 提取并渲染指法标记 (Fingering Display)

不用看谱的长辈，最大的问题是不懂如何安排手指，经常“手忙脚乱”。

解析层扩展：解析 MusicXML 时，深挖 <notations><technical><fingering> 标签，提取 1-5 的数字。

渲染层扩展：在钢琴卷帘的下落方块上，用极大字号、高对比度的颜色直接把指法数字印上去。在“等待模式”中，提示他们用哪根手指按下高亮键。

6.3 去中心化远程推歌 (延后规划/远期展望)

考虑到开源项目的维护成本，早期坚决避免引入中心化服务器（如自建 HTTP 或 Firebase），但这不意味着放弃解决“长辈不懂如何导入乐谱”的痛点。

架构演进思考：放弃中心化账号体系，改为去中心化网络协议。

可行方案 (未来考虑)：

网盘/WebDAV 挂载：软件提供 WebDAV 接入能力（例如挂载坚果云或自建 NAS）。子女向特定网盘目录上传 MusicXML 文件，平板端每次启动时拉取增量更新即可。

局域网扫码直传 (P2P)：平板端直接拉起一个极简的本地 HTTP Server 并展示局域网 IP 二维码。子女用手机扫码后在浏览器内选择文件，直接 Push 进平板内。

6.4 动态节拍脉冲 (Visual Pulse)

既然取消了评分上的时间限制，就需要一套非惩罚性的“节奏引导”来帮他们建立乐感。

实现方案：在背景画布上渲染一条不刺眼的“心跳光带”或“涟漪”。当长辈卡住找键时，脉冲暂停或变弱；当长辈连续按对，且速度接近谱面 BPM 时，脉冲亮起并按 BPM 闪动，从视觉上暗示他们“现在的速度很完美”。

6.5 乐谱交互：点击跳转 (Click-to-Seek)

要实现类似 MuseScore 鼠标点击某处音符，光标和播放进度直接跳过去的体验，必须建立 JS 到 Kotlin 的逆向通信通道。

因为 OSMD 配置为了 Canvas 渲染（无 SVG DOM 元素），必须采用空间碰撞检测 (Spatial Hit Test) 来实现：

JS 层全局拦截与碰撞检测:
为 OSMD 所在的 Canvas 容器绑定原生的 onclick 事件。提取鼠标或触摸点击的 (x, y) 坐标。
利用 OSMD 内置的定位接口，根据坐标反查音符数据。

document.getElementById("container").addEventListener('click', function(event) {
    const rect = event.target.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;

    const nearestNote = osmd.GraphicSheet.GetNearestNote(
        new opensheetmusicdisplay.PointF2D(x, y), 
        new opensheetmusicdisplay.PointF2D(10, 10)
    );

    if (nearestNote) {
        const timestamp = nearestNote.sourceNote.getAbsoluteTimestamp();
        window.KotlinBridge.seekTo(timestamp.RealValue);
    }
});


Kotlin 状态机的“强制重置” (State Reset):
Kotlin 层收到 seekTo(realValue) 请求后，必须执行一次完整的状态机硬重置：

时间重算：根据回传的 RealValue (节拍分数值)，遍历内存中的 List<NoteEntity>，找到匹配或最接近的那个音符，将其 startTick 赋值给当前游标 currentTick。

池清理：清空 Active Key Pool（长按键池）。

卷帘对齐：立刻更新 Compose 侧的钢琴卷帘视口 Y 轴偏移量，让动画瞬间闪现。

闭环同步（Kotlin -> JS）:
Kotlin 状态更新完毕后，再反向调一次 JS Bridge：
evaluateJavascript("osmd.cursor.iterator = osmd.cursor.iterator.seek($realValue); osmd.cursor.update();")