# 未解决问题：乐谱渲染区域右侧黑线

## 现象

- 乐谱渲染区域（ScoreSheetView）最右侧有一条黑色竖线
- 高度始终与乐谱渲染区域一致（在识谱模式下不延伸到下方钢琴键盘区）
- 突破圆角遮罩，贴附在组件最右边缘
- **仅在两个乐谱模式下出现**，钢琴卷帘（PianoRoll）模式无此问题
- 桌面端（JavaFX WebView）确认存在；Android 端未确认
- **系统缩放 100% 时黑线消失；150%（及其他高 DPI）时黑线出现**

## 根本原因

这是 **JavaFX WebView 在高 DPI 下的亚像素舍入误差**。

- 100% 缩放（96 DPI）：1 JavaFX 用户空间单位 = 1 物理像素，WebView 纹理与 JFXPanel 物理尺寸精确匹配，无缝隙
- 150% 缩放（144 DPI）：1 用户空间单位 = 1.5 物理像素，尺寸转换时产生 ±1px 舍入误差
- WebView 在用户空间渲染纹理 → GPU 缩放到物理像素 → 舍入产生 1px 缝隙
- 缝隙位于**硬件加速合成层**（Direct3D/OpenGL swap chain），在所有软件层之上
- 缝隙显示默认 framebuffer 颜色：**黑色**

关键：缝隙不在 HTML 内、不在 Swing Graphics2D 层、不在 JavaFX Scene Graph 层、不在 AWT 原生窗口层 —— 它在 GPU 纹理合成阶段产生，所有软件层面的背景色和裁剪都无法触及。

## 环境

- JavaFX 23（`javafx-web`），Direct3D/OpenGL 硬件加速渲染（已从 21.0.5 升级，黑线 bug 未修复）
- JavaFX WebView 内部 WebKit（版本未公开，远落后于主流 Chrome/Safari）
- Compose Multiplatform 1.11.1 的 `SwingPanel`（重量级组件，渲染在 Compose 层之上）
- OSMD 1.8.9（`opensheetmusicdisplay.min.js`，离线打包）
- Windows 10/11，系统缩放 150%

## 已尝试的修复及结果

### 第 1 轮：CSS 隐藏滚动条

**假设**：`#osmd` 的 `overflow-y:auto` 产生滚动条，滚动条轨道显示为黑线。

| 尝试 | 结果 |
|------|------|
| `#osmd::-webkit-scrollbar{width:0;}` | 无效 |
| `#osmd::-webkit-scrollbar{display:none;}` | 无效 |
| `#osmd{scrollbar-width:none;-ms-overflow-style:none;}` | 无效 |
| `#osmd{border:none;outline:none;}` | 无效 |
| `#osmd svg,#osmd div{border:0!important;outline:0!important;}` | 无效 |

**结论**：JavaFX WebView 的 WebKit 对 `::-webkit-scrollbar` 支持不完整。且后续诊断表明黑线并非滚动条。

### 第 2 轮：物理推出滚动条

**假设**：把滚动条推出可视区域，用 `body{overflow:hidden}` 裁剪。

| 尝试 | 结果 |
|------|------|
| `#osmd{width:calc(100%+20px);padding-right:20px;box-sizing:border-box;}` | 黑线仍在，位置不变 |

### 第 3 轮：缩小 OSMD 页面宽度

**假设**：黑线是 OSMD 渲染的末尾小节线。

| 尝试 | 结果 |
|------|------|
| `cW` 减 12px | 黑线仍在，位置不变 |
| `cW` 减 16px | 黑线仍在 |
| `cW` 减 20px | 黑线仍在 |

**结论**：黑线不是 OSMD 渲染内容，缩小页面宽度无法移动它。

### 第 4 轮：移除 body `border-radius` + `overflow:hidden`

**假设**：`body{border-radius:24px;overflow:hidden}` 在 WebKit 中产生子像素裁剪伪影。

| 尝试 | 结果 |
|------|------|
| 移除 `border-radius` 和 `overflow:hidden` | 黑线仍在 |

### 第 5 轮：JFXPanel `paint()` 遮罩

**假设**：用 Swing `Graphics2D` 在 `JFXPanel.paint()` 中画 blockLime 遮罩覆盖黑线。

| 尝试 | 结果 |
|------|------|
| 遮罩仅四角（`RoundRectangle2D(0,0,w,h,48,48)`） | 圆角生效，但黑线突破圆角 |
| 遮罩四角 + 四边 2px（`RoundRectangle2D(2,2,w-4,h-4,48,48)`） | 黑线仍在 |

**结论**：JFXPanel 可能使用硬件加速渲染（Direct3D/OpenGL），直接写入显存，覆盖了 `Graphics2D` 绘制的遮罩。`super.paint(g)` → `paintComponent()` 拷贝预渲染图像后，GPU 可能在后续帧中直接渲染到屏幕。

### 第 6 轮：移除 Compose `.clip()`

**假设**：ScoreSheetView modifier 上的 `.clip(RoundedCornerShape(lg))` 对 SwingPanel 产生裁剪伪影。

| 尝试 | 结果 |
|------|------|
| 移除 `.clip()`，SwingPanel 背景改 blockLime | 黑线仍在 |

**结论**：PianoRoll 同有 `.clip()` 但无黑线，Compose clip 本身不是原因。

### 第 7 轮：JavaFX Scene 透明 + HTML body `border-radius`

**假设**：让 JavaFX 场景透明，HTML body 自带圆角，透明角区露出 Compose 背景。

| 尝试 | 结果 |
|------|------|
| `panel.isOpaque=false` + `Scene(root, TRANSPARENT)` + `body{border-radius:24px}` | JavaFX WebView **不支持透明背景**，始终渲染不透明白色矩形 |

### 第 8 轮：JavaFX `Rectangle` clip（root 节点）

**假设**：在 JavaFX `StackPane` 根节点设 `Rectangle` clip 裁剪 WebView。

| 尝试 | 结果 |
|------|------|
| listener 方式 `root.widthProperty().addListener{...}` | 初始 0×0 不生效 |
| binding 方式 `clipRect.widthProperty().bind(root.widthProperty())` | 黑线仍在 |

### 第 9 轮：诊断 — body 背景改红色

将 `body{background:#ff0000}` 后观察：

- 黑线在红色区域**外面**
- 黑线距红色区域有"几十px"间距

**关键结论**：黑线不在 WebView HTML 内容内，而在 JFXPanel/WebView 组件本身的边缘。

### 第 10 轮：用户手动修改 `#scroll-wrapper`

用户自行添加 `#scroll-wrapper`（`width:calc(100vw+30px)` + `padding-right:30px`）推出滚动条，但遗漏 `::-webkit-scrollbar{display:none}`。

| 尝试 | 结果 |
|------|------|
| 补加 `::-webkit-scrollbar{display:none}` | 黑线仍在，且乐谱渲染变纯白（CSS 变动影响 OSMD） |

用户 git 回退。

### 第 11 轮：Compose 层 `.padding(end = 4.dp)`

**假设**：缩小 JFXPanel 实际宽度 4dp，让黑线内缩到 blockLime 区域。

| 尝试 | 结果 |
|------|------|
| `Modifier.padding(end = 4.dp)` | 完全无变化 |

**结论**：Compose padding 只影响布局尺寸，不影响 JFXPanel 原生窗口的物理渲染边界。

### 第 12 轮：Swing `JPanel` wrapper（null 布局 + JFXPanel 溢出）

**假设**：轻量级 Swing 父容器裁剪 JFXPanel 子组件。

| 尝试 | 结果 |
|------|------|
| `JPanel` + `layout=null` + `jfxPanel.setBounds(0,0,w+10,h)` | 圆角丢失 + 黑线右移 10px |

**结论**：JFXPanel 是**重量级组件**（拥有独立 HWND），轻量级 `JPanel` 无法裁剪它。JFXPanel 的 native 表面直接覆盖在 Swing 之上，`JPanel` 的 bounds 不产生任何阻挡。null 布局还破坏了 Compose 对 Swing 树的绘制代理，导致圆角失效。

### 第 13 轮：JavaFX `Node.clip`（直接作用于 WebView）

**假设**：在 JavaFX 渲染管线内部用 `webView.clip = Rectangle()` 裁剪 WebView 纹理。

| 尝试 | 结果 |
|------|------|
| `webView.clip = Rectangle(arcWidth=48, arcHeight=48, width=webView.width-3, height=webView.height)` + binding | 黑线仍在 |

**结论**：JavaFX clip 在用户空间操作。150% 缩放下 1 用户空间单位 = 1.5 物理像素，clip `width-1` 切掉 1.5 物理像素，但黑线是 1 物理像素 —— clip 位置因亚像素舍入无法与黑线的物理像素位置对齐。

### 第 14 轮：关键发现 — 100% 缩放无黑线

用户测试发现：系统缩放 100% 时黑线消失，150% 时出现。

**确认根因**：DPI 亚像素舍入误差。非 CSS、非 Swing、非 JavaFX clip 可控。

### 第 15 轮：双重防线

**防线 A**：4 层背景色同化（JFXPanel + Scene + StackPane + SwingPanel 全设 blockLime）。

**防线 B**：AWT `java.awt.Panel`（重量级容器）包裹 JFXPanel，JFXPanel 向右溢出 4px，OS HWND 裁剪。

**防线 C**（保留）：JavaFX `Node.clip` 圆角 + 右侧裁 1px。

| 尝试 | 结果 |
|------|------|
| 三重防线同时部署 | 黑线仍在 |

**结论**：
- 防线 A 无效：缝隙在硬件加速合成层，所有软件层背景被绕过
- 防线 B 无效：JFXPanel 的 Direct3D/OpenGL swap chain 绕过 HWND 裁剪区域，直接呈现到屏幕
- 防线 C 无效：用户空间 clip 在高 DPI 下无法精确对齐物理像素

## 各层失败原因汇总

| 层级 | 方法 | 失败原因 |
|------|------|----------|
| HTML/CSS | `::-webkit-scrollbar`、`border:none`、`border-radius` | JavaFX WebKit 不完全支持；黑线不在 HTML 内 |
| OSMD 内容 | 缩小页面宽度 | 黑线不是 OSMD 渲染内容 |
| Swing `Graphics2D` | `JFXPanel.paint()` 遮罩 | 硬件加速渲染覆盖遮罩 |
| Compose `.clip()` | `RoundedCornerShape` | 对重量级 SwingPanel 无效或产生伪影 |
| JavaFX Scene 透明 | `Scene(root, TRANSPARENT)` | WebView 不支持透明背景 |
| JavaFX `Node.clip` | `Rectangle` clip on WebView | 用户空间 clip 在高 DPI 下无法对齐物理像素 |
| Swing 轻量级容器 | `JPanel` wrapper + null 布局 | 轻量级无法裁剪重量级 JFXPanel；破坏 Compose 绘制 |
| Compose `.padding()` | 缩小组件宽度 | 不影响 JFXPanel 原生窗口物理渲染边界 |
| AWT 重量级容器 | `java.awt.Panel` + HWND 裁剪 | JFXPanel 的 D3D/OpenGL swap chain 绕过 HWND clip |
| 背景色同化 | 4 层全设 blockLime | 缝隙在硬件合成层，绕过所有软件背景 |
| 升级 JavaFX | 21.0.5 → 23 | 未修复高 DPI WebView 纹理舍入 bug |
| 禁用硬件加速 | `-Dprism.order=sw` + paint 遮罩 + blockLime 背景 | 黑线仍在；光标花屏（软件渲染 WebView 重绘异常） |

### 第 16 轮：升级 JavaFX 23

**假设**：JavaFX 23 修复了高 DPI 下 WebView 纹理亚像素舍入 bug。

| 尝试 | 结果 |
|------|------|
| JavaFX 21.0.5 → 23，清除所有 hack 回到最简 JFXPanel + WebView | 黑线仍在，圆角也丢失（因清除了 clip/遮罩） |

**结论**：JavaFX 23 未修复此高 DPI 渲染 bug。方向 B 排除。

### 第 17 轮：禁用 JavaFX 硬件加速（`-Dprism.order=sw`）

**假设**：强制软件渲染，渲染走 Swing `Graphics2D` 管线，`paint()` 遮罩和背景色不再被硬件层绕过。

同时恢复 `JFXPanel.paint()` blockLime 遮罩 + 多层 blockLime 背景。

| 尝试 | 结果 |
|------|------|
| `System.setProperty("prism.order", "sw")` + paint 遮罩 + blockLime 背景 | 黑线仍在；光标花屏（软件渲染下 WebView 重绘异常） |

**结论**：软件渲染下黑线**仍然存在** — 说明黑线不是硬件加速合成层独有的问题，而是 WebView/WebKit 内部渲染管线的固有 bug，在软件和硬件渲染模式下都产生 1px 未绘制边缘。方向 A 排除。同时软件渲染导致光标花屏，不可用。

## 尚未尝试的方向

### C. 替换 WebView 引擎
- **JCEF**（Chromium Embedded Framework）：现代 Chromium 引擎，DPI 支持完善。但依赖体积大（~100MB），集成复杂
- **SWT Browser + WebView2**：使用 Windows 系统 Edge/WebView2 引擎。引入 SWT 依赖，架构改动大

### D. 接受黑线，视觉吞没
放弃消除黑线，改为在 Compose 层用更宽的 blockLime 边框包裹乐谱区。但 SwingPanel 渲染在 Compose 之上，Compose 无法覆盖黑线。需要用另一个 SwingPanel（透明 JPanel）叠在上方画遮罩 —— 需验证两个 SwingPanel 的 z-order 可控性。

### E. 动态检测 DPI 缩放并调整
在运行时检测系统 DPI 缩放比例，据此动态调整 JavaFX clip 的裁切量（从用户空间换算到物理像素）。理论上可以精确裁掉黑线那一列物理像素，但实现复杂且脆弱（不同缩放比例需不同裁切量）。
