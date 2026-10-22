# 大力丸（JavaFX）

大力丸是本地运行的桌面工具箱，覆盖日常开发中最常临时查一下的几件事：JSON 处理、MyBatis 日志还原 SQL、摘要计算、UUID 与随机字符串生成、时间戳、编码转换、对称/非对称加密、文本比对、二维码、图片元数据、OCR，以及 Office 与 PDF 的相互转换。不联网、不上传任何内容、不依赖第三方服务。

界面配色默认跟随系统（Windows 深色/浅色模式），也可在标题栏一键切成固定深色或浅色；左侧图标导航按功能分组，右侧固定的标题栏显示当前功能与说明。窗口初始尺寸按屏幕可用区域的比例计算，1366×768 的笔记本和 2K/4K 显示器上都不会显得过挤或空旷。

---

## 功能概览

左侧导航共 18 个页面，按常用性从高到低排列：JSON、SQL（MyBatis 日志还原）、转换（时间戳/Base64/URL/Unicode）、生成（UUID · 随机串）、工具（二维码/比对/图片信息/OCR）、摘要、加密、文档七组。

### 核心功能

| 功能 | 说明 |
|------|------|
| **JSON** | 格式化/压缩、语法校验、树视图浏览、数组转表格；支持大文件虚拟滚动、行号与语法高亮 |
| **MyBatis SQL** | 将 DEBUG 日志里的 `Preparing` / `Parameters` 直接还原成可执行的 SQL，支持多语句、集合展开、类型还原 |
| **摘要** | MD5、SHA-1、SHA-256、SHA-384、SHA-512，支持文本与文件计算 |
| **加密** | 对称加密（AES/DES/3DES）与非对称加密（RSA），支持密钥生成、加解密、签名验签 |
| **编码** | Base64、URL 编解码、Unicode 转义 |
| **生成** | UUID v4 批量生成、自定义随机字符串 |
| **工具** | 时间戳转换、二维码生成/识别、文本比对、图片元数据、OCR 识别 |
| **文档** | Office ↔ PDF 转换、PDF 拆分合并、图片转 PDF |

---

## 环境要求

- JDK 25 或更高
- Maven 3.9+

## 运行

### 方式一：脚本
```bat
run.bat
```

### 方式二：命令行
```bash
mvn javafx:run
```

### 方式三：IntelliJ IDEA
1. `File → Open` 选择本目录，按 Maven 项目导入
2. 右侧 Maven 面板 → `Plugins` → `javafx` → 双击 `javafx:run`

> 不要直接右键运行 `DaliwanApplication.main()`。非模块化工程下直接运行 Application 子类会报 "JavaFX runtime components are missing"，因此 `pom.xml` 中配置的启动类是 `Launcher`，由它转调 `DaliwanApplication`。

---

## 测试

```bash
mvn test
```

共 **297 个用例**，覆盖所有核心功能模块。

---

## 打包为 exe

双击 `build-jpackage.bat` 即可产出绿色版：
- `dist/daliwan_tool/大力丸.exe` — 双击即运行
- `dist/daliwan_tool/runtime/` — 自带 JRE，目标机器不需要安装 Java
- `release/daliwan_tool-windows-x64.zip` — 分发包

---

## 项目结构

```
daliwan_tool/
├── pom.xml                    # Maven 配置
├── run.bat                    # 启动脚本
├── build-jpackage.bat         # 打包脚本
├── tools/                     # 工具脚本与图标资源
├── docs/                      # 界面截图
└── src/
    ├── main/java/com/daliwan/tool/
    │   ├── Launcher.java              # 启动入口
    │   ├── DaliwanApplication.java    # JavaFX Application
    │   ├── core/                      # 纯逻辑模块（可单独测试）
    │   │   ├── DigestService.java     # 摘要计算
    │   │   ├── JsonFormatter.java     # JSON 格式化
    │   │   ├── JsonTreeBuilder.java   # JSON 树构建
    │   │   ├── JsonDocument.java      # 可修改的 JSON 文档
    │   │   ├── JsonTable.java         # JSON 转表格
    │   │   ├── SymmetricCipher.java   # 对称加密
    │   │   ├── RsaService.java        # RSA 加密
    │   │   ├── QrCodeService.java     # 二维码
    │   │   ├── OcrService.java        # OCR 识别
    │   │   ├── ImageMetaService.java  # 图片元数据
    │   │   ├── ImageMetaStripper.java # 去除元信息
    │   │   ├── sql/                   # MyBatis SQL 处理
    │   │   ├── doc/                   # 文档转换
    │   │   ├── cli/                   # 命令行入口
    │   │   └── shell/                 # 系统集成（右键菜单）
    │   └── ui/                        # JavaFX 界面层
    └── test/java/                     # 单元测试
```

---

## 界面设置

点击右上角设置图标可打开设置页：
- **主题切换**：跟随系统 / 浅色 / 深色
- **导航隐藏**：可按需隐藏不常用的功能
- **系统集成**：Windows 右键菜单集成

配置文件位于 `~/.daliwan/settings.properties`。

---

## 许可证

本项目基于开源组件构建，详情见「关于」对话框。