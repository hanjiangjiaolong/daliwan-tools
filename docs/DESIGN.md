# 设计与实现说明

> 这份文档放的是「为什么这么写」：目录结构、实现要点、已知限制。
> 日常上手看 [README](../README.md)，功能清单看 [FEATURES](FEATURES.md)。

---

## 目录结构

```
daliwan_tool/
├── pom.xml
├── run.bat                        源码方式启动
├── build-jpackage.bat             一键打包为绿色版 exe
├── README.md
├── tools/
│   ├── icon-source.png            图标源图（写实羊角锤产品图，白底）
│   ├── build_icons.py             图标打包流水线（抠除白底 + 合成蓝色圆角底 + 7 档 PNG + ICO）
│   ├── zip_app.py                 把 dist/daliwan_tool 压成分发包
│   ├── check_exe_icon.py          校验 exe 里嵌的图标与源图是否一致
│   └── *Smoke.java / *Check.java   文档转换、右键菜单、界面截图的命令行验证脚本（不参与打包）
├── docs/                          界面截图
└── src/
    ├── main/
    │   ├── java/com/daliwan/tool/
    │   │   ├── Launcher.java              启动入口（规避 classpath 下的组件缺失检查）
    │   │   ├── DaliwanApplication.java        JavaFX Application
    │   │   ├── core/                      纯逻辑，无 JavaFX 依赖，可单独测试
    │   │   │   ├── DigestService.java     文本 / 字节 / 文件流式摘要
    │   │   │   ├── HashAlgorithm.java     MD5 与 SHA 系列算法定义
    │   │   │   ├── HashResult.java        摘要结果与派生形式
    │   │   │   ├── JsonFormatter.java     JSON 格式化 / 压缩 / 校验
    │   │   │   ├── JsonTreeBuilder.java   JSON 解析为节点树
    │   │   │   ├── JsonDocument.java       可修改的 JSON 文档（删节点写回）
    │   │   │   ├── JsonTable.java          JSON 数组转表格、自动定位与候选枚举
    │   │   │   ├── XlsxExporter.java       表格写出 .xlsx（fesod，数字列自动转数值）
    │   │   │   ├── JsonTreeNode.java      树节点模型
    │   │   │   ├── JsonFormatException.java  带行列号的语法异常
    │   │   │   ├── IndentStyle.java       缩进风格
    │   │   │   ├── UrlCodec.java          表单与 RFC 3986 编码
    │   │   │   ├── SymmetricCipher.java   AES / DES / 3DES 加解密
    │   │   │   ├── CipherException.java   加解密异常
    │   │   │   ├── RsaService.java        RSA 密钥 / 加解密 / 签名
    │   │   │   ├── UnicodeCodec.java      Unicode 转义与还原
    │   │   │   ├── QrCodeService.java     二维码生成与识别
    │   │   │   ├── PngEncoder.java        极简 PNG 编码器（二维码保存用）
    │   │   │   ├── QrCodeException.java   二维码异常
    │   │   │   ├── RandomService.java     UUID 与随机字符串生成
    │   │   │   ├── TextDiff.java          Myers 行级差分 + 左右行对齐
    │   │   │   ├── ImageMetaService.java  图片元数据（EXIF / GPS / 尺寸）
    │   │   │   ├── ImageMetaStripper.java 去除元信息（JPEG/PNG/WebP/GIF 结构剪裁 + 重编码兜底）
    │   │   │   ├── OcrService.java        内置 RapidOCR（PP-OCRv4）识别
    │   │   │   ├── OcrException.java      OCR 异常
    │   │   │   ├── ToolSettings.java      界面偏好（导航隐藏项 / 主题 / 右键菜单）持久化
    │   │   │   ├── sql/                   MyBatis 日志还原 + SQL 美化
    │   │   │   │   ├── MybatisLogParser.java   解析 Preparing / Parameters（含集合展开）
    │   │   │   │   ├── SqlAssembler.java       按顺序替换 ?（跳过字符串与注释里的问号）
    │   │   │   │   ├── ParamValue.java         参数值 → SQL 字面量（类型决定引号）
    │   │   │   │   ├── SqlBeautifier.java      sql-formatter 封装 + 失败兜底
    │   │   │   │   ├── SqlHighlighter.java     语法着色分词（关键字 / 字符串 / 注释…）
    │   │   │   │   └── SqlDialect.java         方言枚举
    │   │   │   ├── doc/                   文档互转（Stirling topdf / core + PDFBox）
    │   │   │   ├── cli/                   命令行入口（右键菜单「转 PDF」「转 Word」…）
    │   │   │   │   ├── PdfCommand.java         解析 --to-pdf、批量转换并写日志
    │   │   │   │   ├── OfficeCommand.java      解析 --to-docx / --to-xlsx / --to-pptx / --to-txt
    │   │   │   │   └── PdfResultDialog.java    右下角结果卡片（Swing 自绘，不拉 JavaFX）
    │   │   │   ├── ipc/
    │   │   │   │   └── InstanceBridge.java     本地套接字：请求（开关 + 文件）并到已开实例
    │   │   │   └── shell/
    │   │   │       ├── AppLauncher.java        解析当前程序的启动命令（exe / java -jar）
    │   │   │       ├── ContextMenuIntegration.java  右键菜单的注册表读写（多菜单项）
    │   │   │       └── Win11Menu.java          Win11 经典右键菜单开关与资源管理器重启
    │   │   ├── ui/
    │   │   │   ├── MainView.java          侧边导航与页面调度
    │   │   │   ├── HashPane.java          摘要页
    │   │   │   ├── RandomPane.java        UUID / 随机字符串页
    │   │   │   ├── Base64Pane.java        Base64 页
    │   │   │   ├── ImageBase64Pane.java    Base64 页的图片模式面板
    │   │   │   ├── UrlCodecPane.java      URL 编解码页
    │   │   │   ├── UnicodePane.java       Unicode 页
    │   │   │   ├── SymmetricCipherPane.java  对称加密页
    │   │   │   ├── RsaPane.java           RSA 页
    │   │   │   ├── TimestampPane.java     时间戳页
    │   │   │   ├── JsonPane.java          JSON 工具页（文本 / 树视图 / 表格切换）
    │   │   │   ├── JsonCodeArea.java      带行号与高亮的代码框
    │   │   │   ├── QrCodePane.java        二维码页
    │   │   │   ├── ImgMetaPane.java       图片信息页
    │   │   │   ├── StripMetaDialog.java   去除元信息对话框（策略 + 批量结果）
    │   │   │   ├── SqlPane.java           MyBatis SQL 页（上粘日志 / 下出结果）
    │   │   │   ├── SqlCodeArea.java       只读 SQL 结果框（行号 + 语法着色）
    │   │   │   ├── TextDiffPane.java      文本比对页
    │   │   │   ├── DiffView.java          左右分屏差异视图
    │   │   │   ├── ResultPanel.java       摘要结果展示组件
    │   │   │   ├── SettingsPane.java      设置页（卡片 + 开关）
    │   │   │   ├── ToggleSwitch.java      自绘开关控件
    │   │   │   ├── ThemeManager.java      主题装配与切换
    │   │   │   ├── BatchConvertPane.java   批量转换页的公共骨架（列表 / 后台逐个转 / 统计）
    │   │   │   ├── Icons.java             线性图标路径集合
    │   │   │   └── UiKit.java             页面内复用构件
    │   │   └── util/
    │   │       ├── ByteSizes.java         字节数的人类可读表示
    │   │       ├── ClipboardUtil.java     剪贴板封装
    │   │       └── SystemTheme.java       系统明暗配色探测（结果缓存到 theme.cache）
    │   └── resources/
    │       ├── com/daliwan/tool/styles/
    │       │   ├── app.css                    结构样式（只引用语义颜色变量）
    │       │   ├── theme-dark.css             深色配色
    │       │   └── theme-light.css            浅色配色
    │       ├── icons/                         窗口/任务栏图标（build_icons.py 生成的多尺寸 PNG）
    │       └── （图标说明见下方「应用图标」）
    └── test/java/com/daliwan/tool/core/
        ├── DigestServiceTest.java
        ├── JsonFormatterTest.java
        ├── JsonTreeBuilderTest.java
        ├── JsonDocumentTest.java
        ├── JsonTableAutoTest.java
        ├── XlsxExporterTest.java
        ├── UnicodeCodecTest.java
        └── UrlCodecTest.java
```

`core` 包不引用任何 JavaFX 类型，因此全部逻辑都能用普通单元测试覆盖，界面层只负责取值、调用、展示。

**应用图标**：源图是 `tools/icon-source.png`（写实羊角锤产品图：铬钢锤头 + 木纹柄），`tools/build_icons.py` 把它合成为应用图标——泛洪抠除白底、高光滚降去贼光、加柔和投影、贴到液态蓝色圆角底上，输出 7 档 PNG 与多尺寸 ICO。产物同步到两处——`src/main/resources/icons/`（窗口图标，随 jar 打包）和 `src/windows/assets/icon.ico`（`jpackage --icon` 用，决定 exe 文件与任务栏图标）。改图标只需替换 `tools/icon-source.png` 再跑一遍 `python tools/build_icons.py build`，然后**重新打包**（应用内图标是从 resources 里读的）。找不到 `icon.ico` 时 jpackage 会退回默认图标而不是报错，所以换图后最好用 `python tools/check_exe_icon.py` 核一下。Windows 还有图标缓存，资源管理器仍显示旧图时重启 explorer 或把 exe 改个名即可。

---

## 实现要点

**1. 摘要为什么用 `MessageDigest` 而不是工具库**

JDK 内置实现跨平台一致，无需引入 Commons Codec 或 Hutool。文件计算采用 64KB 缓冲的 `digest.update()` 循环，避免整文件读入内存。

**2. 所谓「16 位 MD5」**

MD5 摘要固定 128 位（32 个十六进制字符），不存在 16 位算法。「16 位 MD5」是取 32 位结果的第 9 到第 24 个字符，属于展示惯例，安全性没有提升。SHA 系列不提供该形式。

**3. JSON 为什么不做「解析成对象树再序列化」**

`readTree` 后重新序列化会带来三个副作用：字段顺序可能变化、重复键被合并、数字经 double 往返后精度丢失。本项目改用 Jackson 流式 token API 逐个重排，只改动空白字符，语义与字面量全部保持原样。树视图同样基于流式递归，不依赖 databind。

**4. 行号与语法高亮为什么需要 RichTextFX**

JavaFX 原生 `TextArea` 是纯文本控件：既无法对局部文字着色，也没有行号栏的扩展点。本项目改用 RichTextFX 的 `CodeArea`——它按段落虚拟化渲染，支持用 `StyleSpans` 给不同区间挂样式类，并内置 `LineNumberFactory` 生成行号。

两个实现细节值得记录：

- RichTextFX 的着色落在 `Text` 节点上，CSS 必须用 `-fx-fill` 而非 `-fx-text-fill`；且选择器要带上容器类（`.json-code-area .json-key`），否则会被 `.text` 的基础色规则按权重覆盖。
- 高亮用正则逐 token 匹配并挂样式，键的正则必须排在普通字符串之前（用 `(?=\s*:)` 前瞻判定），否则带冒号的键会被当成字符串。

**5. 主题为什么拆成三个样式表**

JavaFX 没有 CSS 变量语法，但支持「被查找的颜色」：在 `.root` 上定义 `-c-text: #e7e9ef`，任何规则都能用 `-fx-background-color: -c-text` 引用。

据此把样式分成两层：`app.css` 只写结构与形态，颜色一律引用 `-c-*`；`theme-dark.css` / `theme-light.css` 各自给这批变量赋值。切换主题时 `Scene.getStylesheets()` 换一个文件即可，界面无需重建，页面代码里也不该出现写死的颜色。

两处踩过的坑：

- **自定义滚动条不生效**。Modena 默认规则是 `.scroll-bar:vertical > .thumb`，选择器权重高于只写 `.scroll-bar > .thumb`，必须补上方向伪类才能覆盖。
- **点过的按钮像仍被选中**。Modena 用 `-fx-focus-color` 给聚焦控件铺一层主色底，与「选中态」撞色。解决方式是把焦点色设为透明，聚焦反馈改由输入控件自己的 `:focused` 边框提供。

**6. 页面惰性构建**

17 个页面在首次选中时才创建并缓存，避免启动阶段同时初始化全部控件。

**7. 图标不引入图标字体**

导航与按钮图标是一组 16×16 网格上的 SVG 描边路径，集中在 `Icons.java` 里。颜色由 `-fx-stroke` 控制，因此能自动跟随主题、以及导航项的选中态；换成图标字体则要额外打包字体文件，且在深色主题下换色更麻烦。

**8. 系统配色怎么取**

优先走 JavaFX 25 的 `Platform.getPreferences()`：配色直接由工具包提供，系统切换明暗时 `colorSchemeProperty` 会实时回调，主题立即跟上，不再需要起任何外部进程。toolkit 没启动的场合（右键转 PDF 的独立短进程、单元测试）拿不到偏好，才退回带 1.5 秒超时的 `reg query` 读 `AppsUseLightTheme`（macOS 用 `defaults`）；任何环节失败都退回浅色，最坏结果只是没跟随系统。

启动时先用上次落盘的缓存配色渲染首帧，再让后台校准一次——「跟随系统」的探测现在零成本（不起进程），但校准仍放在后台线程，保持首帧不被任何事堵住。

**9. 窗口尺寸不写死**

`Screen.getPrimary().getVisualBounds()` 给出的是排除任务栏后的可用区域，且是逻辑像素（已含 DPI 缩放），可以直接当布局尺寸用。初始宽高取该区域的 62% × 78%，再限制到 880×600 ~ 1600×900 之间，并保证不超出可用区域；最小尺寸同样按屏幕收缩，否则小屏上用户没法把窗口再缩小。

**10. JSON 为什么合并成一页**

「JSON 格式化」与「JSON 树视图」两个入口功能高度重叠（都要贴一段 JSON、都要校验语法、都要拖文件），实际用起来还得在两项之间来回切，因此合并为一页、一块区域两种形态：

- 文本态：格式化 / 压缩直接原地替换框内文本，改坏了 `Ctrl + Z`；
- 树视图态：切过去即按当前文本解析，文本是唯一数据源，因此不存在两处内容不一致，也不用额外放一个「生成树」按钮。

树视图用 `ToggleGroup` 控制可见性，但切换会先尝试解析：内容为空或语法错误时停在文本视图并给出错误行列，不做「切过去看一个空树」这种无意义跳转。切换按钮还处理了 ToggleGroup 的一个坑——再次点击已选中的按钮会把选中态清空，需要在监听器里把旧选中项按回去。

**11. MyBatis 日志怎么还原成 SQL**

只按 `Preparing:` / `Parameters:` 两个标记定位，前面有什么都不影响解析——时间戳、级别、Spring Boot 的 `--- [nio-8080-exec-1]`、Logger 名都能正确跳过，其中 Logger 名还会留下来当「来源」显示。参数引号由日志里的类型决定：`01(String)` 必须是 `'01'`，若按数字处理会变成 `1`，语义就变了。`?` 的替换是逐字符扫描的，字符串字面量、反引号标识符与 `--` / `/* */` 注释里的问号一律不碰；`[11, 22](ArrayList)` 这类集合参数按元素展开，正好对上 `<foreach>` 生成的多个占位符。美化交给 sql-formatter 的 Java 移植版，它抛异常时退回未美化的拼接结果——宁可排版难看，也不能把 SQL 弄丢。着色则叠在美化之后：`SqlHighlighter` 逐字符扫一遍，把关键字、函数、字符串、数字、注释切成不重叠的区间，界面层按区间贴样式类——这一步不放在正则里，是因为 SQL 里的 `--` 可能出现在字符串中、引号也可能出现在注释里，正则一旦错位，后面整段颜色都会花。着色只影响显示，`复制 SQL` 拿到的仍是纯文本，粘到客户端里不带任何标记。

**12. 去除元信息为什么剪结构而不是重绘**

重绘（解码 → 重画 → 再编码）最省事，但 JPEG 重编码必然掉画质、体积还可能变大，对「只是不想让别人看到拍摄地点」这个诉求完全不成比例。JPEG / PNG / WebP / GIF 都是分块容器，元数据各自待在独立块里：JPEG 的 APP1（EXIF / XMP）、APP13（IPTC）、COM，PNG 的 `tEXt` / `zTXt` / `iTXt` / `eXIf` / `tIME`，WebP 的 `EXIF` / `XMP `，GIF 的注释扩展与 XMP 应用扩展。做法是逐块扫描、保留块原样搬运，只把带元数据的块摘掉——像素数据一个字节都不碰，PNG 的 CRC 也不用重算（每个块的 CRC 只校验自己）。

几处容易翻车的地方：

- **RIFF 的长度字段是小端**。WebP 删块后要重算 `RIFF` 头的总长度，同时清掉 `VP8X` 里「存在 EXIF / XMP」的两个标志位，否则解码器会按标志位去找已经不存在的块。
- **JPEG 的方向标记不能跟着一起删**。手机照片的像素是横着存的，靠 `Orientation` 转正，整段删掉照片就躺下了。所以默认重建一个 26 字节的最小 EXIF 段，只含这一个标签；不需要的可以选丢弃，或把旋转烘进像素（那就必须重编码）。
- **GIF 是链式结构**，必须顺着 `0x21` 扩展 / `0x2C` 图像 / `0x3B` 结束逐块走，不能按固定偏移跳——每帧都可能带自己的局部调色板。
- **ICC 与 EXIF 要区别对待**：ICC 只描述颜色如何解释，不含任何身份信息，删掉反而让广色域照片发灰，所以默认保留。

TIFF / BMP 的结构里没有独立的元数据块（TIFF 的元数据就是 IFD 本身），这时才退回重编码，并在结果里标记 `reencoded`、提示画质可能变化。输出统一先写同目录临时文件再整体替换，中途失败不会留下半截文件。

**13. Win11 为什么把菜单项藏进「显示更多选项」**

Win11 的新右键菜单换了一套扩展模型：只有实现了 `IExplorerCommand`、并在包清单里登记过 `desktop4:FileExplorerContextMenus` 的（带包标识的）程序，才能进第一层；传统 `shell\<verb>\command` 这种静态菜单项仍然被支持，但只出现在旧式完整菜单里。绿色版不打包 MSIX，也就没有包标识，于是只能落到「显示更多选项」。26H2 又加了一层：默认连这个入口都不显示，得先去「设置 → 个性化 → 右键菜单」把它打开，自定义项才找得回来。要在第一层显示，必须额外写一个 native 的 COM DLL 并做 sparse package（还要装证书），体积和风险都与这个工具的定位不相称。真正的零成本做法是让用户按 `Shift + 右键`——它会绕过新菜单直接弹旧菜单；想永久直接显示，就在设置里打开「Win11：换回经典右键菜单」。

那个开关改的是 `HKCU\Software\Classes\CLSID\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}\InprocServer32` 的默认值，把它写成空串：这个 CLSID 是外壳用来加载新菜单组件的，`InprocServer32` 为空就等于「别加载它」，外壳退回旧菜单。这是**绕过准入条件**，不是满足它——我们并没有变成新菜单认的那种扩展，只是把菜单换了个样式，所以代价得说清楚：一是它**改的是全局右键菜单外观**，所有文件、所有程序都会变成 Win10 样式，用户装的其他右键扩展也跟着换位置，因此默认关闭、勾选保存时再确认一次；二是外壳只在启动时读一次，所以必须重启 `explorer.exe`（连同已打开的文件夹窗口一起关掉）；三是这是社区做法而非微软公开设置，功能更新后可能失效，因此开关做成可逆的，删掉整棵键就回到 Win11 默认样式。启动时还会回读一次注册表来校准开关状态——配置里记的可能和实际不一致（比如用别的工具改过），界面不能撒谎。

**14. 表格导出为什么默认 xlsx、为什么用 fesod**

CSV 是文本格式，中文 Excel 打开会连踩三个坑：无 BOM 乱码（用 UTF-8 BOM 可以绕）、值里带逗号引号要按 RFC 4180 转义（多数工具转不干净）、以及**长数字变科学计数法**——`13812345678` 进去出来成了 `1.38123E+10`，订单号、身份证号全部报废。xlsx 里每个单元格自带类型，数字与文本各归各位，所以导出菜单把 Excel 放在第一位。写出用的是 Apache Fesod（原 FastExcel，`org.apache.fesod:fesod`，POI 社区原班人马孵化）：自带 SXSSF 式流式写出，5000 行导出不到一秒，也不必为一次导出背上 POI 全家桶。数字列的判定很保守——只把「纯数字、无前导 0、有效数字 15 位以内」的单元格转成数值，`007` 这类编号和超长数字一律留文本，宁可让它左对齐，也不能悄悄丢精度。表头不走 fesod 的默认样式（14 号加粗 + 25% 灰底，列一多就糊成一片），改成 11 号白底，靠加粗区分；列宽按表头和全部内容的最长者自动撑开（中文算两个字符宽），下限 8 个字符、上限 60 个字符——单元格里塞了整段 JSON 也不至于把表拉到几屏宽。列宽是在写出前一次性算好的，不用 `LongestMatchColumnWidthStyleStrategy` 那种「每个单元格回写一次列宽」的做法，实测 5 万行 × 15 列开着样式和裸写一样快（约 1.8 s，差异在噪声里）。

## 已知限制

- MD5 与 SHA-1 均已被证明存在碰撞攻击，仅适用于完整性校验、缓存键等非安全场景。**不要用于密码存储或数字签名**——密码场景应使用 BCrypt、Argon2 或 PBKDF2。
- Base64 与 URL 页面面向文本，不含二进制文件编解码。
- 对称加密的明文按 UTF-8 处理，解密非文本（二进制）数据时结果会显示为乱码。
- RSA 使用 PKCS#1 v1.5 填充，未实现 OAEP；新系统建议自行改用 OAEP 以获得更好的安全性。
- OCR 用**内置的 RapidOCR**（PP-OCRv4 + ONNX Runtime，模型与动态库随程序分发，用户不需要另装任何东西）。两个使用上的约束：一是首次识别要把约 28 MB 模型解压到系统临时目录，比后续慢几百毫秒（进页面时会后台预热，把这段等待挪到看图的时候）；二是 RapidOCR 一个 JVM 进程内只允许一种引擎与模型，首次调用后即固定，所以固定用 V4、不提供运行时切换。另外它的字典以中英文为主，日文 / 韩文等只能识别其中的汉字与拉丁部分。
- Office ⇄ PDF 走**内嵌的 Stirling Office Convert**（MIT）：`stirling-office-convert-topdf` 管 Office → PDF，同项目的 `stirling-office-convert`（core）管 PDF → Office（DOCX / XLSX / PPTX / TXT），两者共用 PDFBox + POI，无水印、无评估弹窗、无授权文件。它按 Java 25 编译，整个项目的 `maven.compiler.release` 也跟着提到 25，打包脚本会优先找 JDK 25 并喂给 Maven。联动注意：Stirling 0.2.2 要求 PDFBox 3.0.8（它调用了 `TrueTypeFont.setEnableGsub`，3.0.2 上直接 `NoSuchMethodError`），pom 里 PDFBox 版本必须跟随。引擎不支持 WPS 私有格式，`.wps` 已从可转换列表移除。PDF 拆分合并与图片转 PDF 不依赖外部程序。
- JSON 树视图节点上限 20 万个；超过 3000 个节点时「展开全部」只展开三层。
- 去除元信息对 JPEG / PNG / WebP / GIF 是无损剪裁，对 BMP / TIFF 退回重编码（画质与体积都可能变化）；HEIC / RAW 等需要专门解码器的格式暂不支持，会明确提示先转成 JPEG 或 PNG。结构异常、剪不动的文件同样走重编码兜底，不会把原图改坏。
- **拖放全程不碰 `getDragboard()`，只在放下那一刻读一次文件列表**：`hasFiles()` 看着像读个布尔值，实际要穿过 JNI 调 Windows 的 `IDataObject::EnumFormatEtc`——拖拽源程序（资源管理器、浏览器、聊天工具）得把数据对象上挂的每一种格式都准备好才能枚举完，拖图片时还会临时拉起缩略图 / 属性处理器这类 Shell 扩展，一次几百毫秒到几秒不等，且**同步阻塞在界面线程上**。`DRAG_OVER` 每秒几十次、`DRAG_ENTERED` 还会随指针穿过子节点反复触发，只要在其中任何一个里问一句 `hasFiles()`，界面就一卡一卡甚至彻底冻住——表现就是「文件刚进窗口就死了」。所有页面统一走 `UiKit.installFileDrop()`：悬停阶段只 `acceptTransferModes`（不跨进程），内容是不是文件等 `DRAG_DROPPED` 再看，不是文件就 `setDropCompleted(false)`。另外高亮状态用场景坐标判断是否真的离开投放区，避免在子节点之间移动时样式反复重算。每次拖拽结束会在 `~/.daliwan/dnd.log` 追加一行：**界面最大间隔**是相邻两次 `DRAG_OVER` 的最大间隔，直接反映界面线程被堵了多久（正常个位数毫秒），排障时先看它。
- **两个批量转换页共用一套骨架**：「Office 转 PDF」与「PDF 转 Office」的交互完全一样（拖入 / 选择文件 → 后台逐个转 → 行上回显状态 → 结束给统计），只有两点不同：什么样的文件能进列表、单个文件怎么转。这两点由子类的 `rejection(Path)` 与 `convert(Path)` 回答，其余（列表单元格、按钮禁用逻辑、后台任务、统计文案）都收在 `BatchConvertPane` 里——新加一个「批量转换」页面只需要回答这两个问题。每个文件转完只替换列表中那一项来刷新该行，不用 `list.refresh()`（后者会重建所有单元格）。
  输入触发的文本比对还带一个自增 token，输入继续变化时旧结果直接丢弃。
  文本比对另有规模上限（参与比较 6000 行、编辑距离 1500）：Myers 每轮都要留一份
  `int[2*max+1]` 的快照，6000 行差异悬殊的文本能吃到近 300MB，超出上限就退化为
  「整段替换」展示。二维码识别对图片限了 2000px 最长边，避免上百 MB 的照片完整解码。
- **输出文件命名统一走 `FileNames.uniqueTarget`**：各页面自己写时容易踩同一个坑——
  用 `toLowerCase().endsWith(".pdf")` 判断后缀（大小写不敏感）却用 `replace(".pdf", ...)`
  拼接（大小写敏感），用户输入「报告.PDF」时算出来的目标路径永远相同，`Files.exists`
  恒为真，界面线程直接死循环。
- **OCR 引擎单例 + 推理串行**：RapidOCR 的 native 推理上下文不是线程安全的，
  `OcrService` 里所有 `runOcr` 用同一把锁串行，引擎实例懒加载一次后复用；
  加载失败抛的是 `UnsatisfiedLinkError` 这类 `Throwable`，统一包成 `OcrException`
  并提示装 Visual C++ 运行库，界面上给的是原因而不是一串堆栈。
- 未做代码签名，Windows 首次运行如出现 SmartScreen 提示属正常现象。

---

### 打包产物怎么验证

直接双击 `dist\daliwan_tool\大力丸.exe` 走一遍常用功能即可；源码级验证跑 `mvn test`（core 层全部逻辑）。
