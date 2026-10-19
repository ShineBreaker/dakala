# 设计原则

> 打卡啦的视觉与交互设计准则。新增界面或调整既有界面时，先读本文件；本文件记录的是「为什么这么做」，怎么实现见代码。

## 1. 色彩：单一调色板来源

- **Material You 动态取色优先**：Android 12（API 31）及以上，应用与小部件共用同一套由壁纸推导的系统调色板（Compose 侧 `dynamicLightColorScheme` / `dynamicDarkColorScheme`）。用户换壁纸或切主题色，应用与小部件颜色一起变。
- **静态色为降级方案**：API 29/30 无动态取色，回退到 `Theme.kt` 中的品牌静态配色（`LightColorScheme` / `DarkColorScheme`）。动态与静态两套色在代码里走同一个 `ColorScheme` 接口，调用方不感知差异。
- **小部件与应用同色**：小部件的取色不走 `colors.xml` 里写死的另一份调色板，而是与应用同一来源（`WidgetPalette` 从 `ColorScheme` 取值）。任何颜色只定义一次。
- **语义色归位**：状态含义固定——未完成/错误用 `error` 系，完成用 `primary`/`success` 系，弱化信息用 `onSurfaceVariant`。小部件 RemoteViews 不能引用 Compose 主题，统一经 `WidgetPalette` 把 `ColorScheme` 拍平成具体色值。

## 2. 明暗模式：自动跟随系统

- 应用内由 Compose `isSystemInDarkTheme()` 驱动，天然跟随系统。
- **小部件按当前模式单次构建**：色值在构建时按 `WidgetPalette.current()` 烘焙进 RemoteViews 动作列表；系统配置变化广播（`ACTION_CONFIGURATION_CHANGED`，manifest 可声明的豁免广播）在明暗位变化时触发重绘，切深浅色约一秒内跟随。
- **不用 `RemoteViews(a, b)` 双视图构造器**：它的语义是 (landscape, portrait) 按尺寸选版，不是 (light, dark) 按主题选版——竖屏永远应用第二份，曾被误当明暗机制导致浅色模式下仍渲染深色版。（动态色值是运行时计算的，也无法走 layout-night/setColor 资源限定符路线。）

## 3. 小部件视觉

- **半透明底**：背景是独立的 `ImageView` 层（`widget_bg`），圆角由形状 drawable 提供，颜色经 `setColorFilter` 染色，透明度经 `setImageAlpha` 应用用户设置。内容层（文字、列表、图标）保持不透明，保证可读性。
- **半透明下保住对比度**：壁纸会从半透明底透出来吃掉文字对比，配色要为此补偿——背景色不用裸 `surface`，而是在其上向极端方向偏移（深色模式更黑、浅色模式更白）；次级文字不用 `onSurfaceVariant`（半透明底上太灰），用 `onSurface` 混入 30% `onSurfaceVariant` 的实体色 `secondary`；完成绿按明暗分档（深亮/浅深）；完成态标题用删除线标识而不是单纯降色。
- **圆角半径 16dp**，与 Material 3 卡片一致；内容内边距 12dp。
- **透明度可调**：范围 10%–100%，默认 90%。值存 `app_settings` 表（`widget_opacity`），改动后立即触发小部件重绘。低于 10% 不允许——内容悬浮在桌面上会丢失边界。默认值取高不取低：用户要更透可以手动降，但默认必须先保证可读。
- **Tab 仅文字着色**：选中 Tab 用 `accent` 文字色、未选中用 `secondary`，不做底框/胶囊——半透明底上再叠一层色块会削弱「浮于壁纸」的观感，文字染色已足够区分。RemoteViews 侧只用 `setTextColor`，也绕开了 `state_selected` 不可远程触发的限制。
- **默认仅未完成待办**：待办列表默认只显示未完成任务；设置页「小部件待办」开关打开后显示全部任务，未完成任务排最前（组内保持文件顺序）。默认收敛的理由：桌面小部件的核心场景是一眼看到要做的事，已完成项是少数场景的回顾需求，由用户显式打开。
- **RemoteViews 只许白名单反射调用**：`apply()` 时逐条执行反射 action，目标方法必须标注 `@RemotableViewMethod`，否则整条 apply 失败、宿主直接显示「载入窗口小部件时出现问题」占位视图且不一定有日志。常用陷阱：`setSelected` 不在白名单（`setEnabled`/`setBackgroundResource`/`setBackgroundTintList`/`setColorFilter`/`setImageAlpha` 在），drawable 的 `state_selected` 无法经 RemoteViews 触发，选中态要显式换资源。

## 4. 设置页

- **每个设置项必须显示当前值或默认值**：标题在 `headlineContent`，当前值在 `supportingContent`。不允许只写「设置 XX」而不展示现状——用户应该不看二级界面就知道当前配置。
- **按用途分区**：「打卡」「待办」「小部件」「权限」，用分区头分隔，不堆成一个长列表。
- **点击行即编辑**：整行可点，修改入口是对话框（时间选择、时长输入、透明度滑杆）、开关（如小部件标签页范围）或系统界面（文件选择、权限页），页内不做就地展开编辑。开关型设置同样遵守「显示当前值」规则——`supportingContent` 同步显示开/关对应的语义文案。
- **状态型条目可点跳外**：权限行显示「已授权/未授权」，未授权点击直达系统设置页。
- **入口即图标**：主界面右上角齿轮图标直达设置页，不再用三点菜单收纳零散入口。

## 5. Org 待办编辑

- **org 文件是唯一数据源**：应用内不维护副本，每次操作都重新读文件、按行号定位、最小化改写后整体写回。外部（Emacs/syncthing）修改在下次刷新即生效，行号失效时操作安全返回 false。
- **编辑只做最小 diff**：改标题/状态/优先级/tags 只重写 headline 行，层级与星号间距原样保留；SCHEDULED/DEADLINE 只重写 planning 行中对应条目，行上 CLOSED 与其他内容不受影响；状态跨过完成边界时自动补/删 `CLOSED` 时间戳，与 Org 的记账习惯一致。
- **删除范围到下一个标题为止**：标题行 + planning + 正文整块移除，子标题不删——它们是独立条目，用户删父条目时不应连带丢失。
- **写后双刷新**：应用内重读文件 + 通知小部件重绘（`reloadAfterWrite` 收口），任何写路径都不许只刷一边。
- **删除必须二次确认**：编辑对话框内嵌删除入口（错误色），点击后弹确认框。直接改 org 文件，没有回收站。
- **功能边界（相对 Orgzly 的轻量取舍）**：支持自定义状态工作流、优先级、tags、SCHEDULED/DEADLINE 日期、CLOSED 记账、编辑/删除/切换与小部件展示；不做搜索语法、repeater/警告期、笔记正文编辑、多笔记本与同步——这些是 Orgzly 的完整场景，本项目只覆盖「读列表 + 快速编辑」的核心路径。

## 6. 设置存储

- 全部用户设置存 Room `app_settings` 表（key-value），键名集中在 `AppSetting` companion 常量，默认值回退逻辑收敛在领域模型（如 `NotificationTime.parse`、`WidgetOpacity.parse`），读写两侧不各自 hardcode 默认值。
