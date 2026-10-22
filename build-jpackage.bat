@echo off
setlocal enabledelayedexpansion
chcp 936 >nul
cd /d "%~dp0"

REM ============================================================
REM  jpackage 打包：普通 JVM + 自带运行时，产出 dist\daliwan_tool\大力丸.exe
REM
REM  用法：
REM    build-jpackage.bat            完整打包并压缩
REM    build-jpackage.bat nopause    不暂停（脚本/CI 用）
REM
REM  需要：JDK 17+（带 jpackage，脚本会自动找）+ Maven
REM  不需要：GraalVM / VS Build Tools / WiX
REM  无 WiX 时 jpackage 只能出 app-image（目录形态），不能出安装器；
REM  目录形态照样是绿色版，整个文件夹拷走或压缩即可。
REM ============================================================

set "NO_PAUSE=0"
:parse_args
if "%~1"=="" goto args_done
if /i "%~1"=="nopause" set "NO_PAUSE=1"
shift
goto parse_args
:args_done

REM ---------- 定位 Maven ----------
set "MVN="
if exist "D:/soft/apache-maven-3.9.6/bin/mvn.cmd" set "MVN=D:/soft/apache-maven-3.9.6/bin/mvn.cmd"
if not defined MVN if exist "D:/soft/apache-maven-3.9.9/bin/mvn.cmd" set "MVN=D:/soft/apache-maven-3.9.9/bin/mvn.cmd"
if not defined MVN for %%i in (mvn.cmd) do if not "%%~$PATH:i"=="" set "MVN=%%~$PATH:i"
if not defined MVN (
    echo [错误] 未找到 Maven，请编辑脚本里的 MVN 变量
    goto fail
)

REM ---------- 定位带 jpackage 的 JDK ----------
set "JDK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JDK=%JAVA_HOME%"
if not defined JDK for /d %%d in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
    if exist "%%d\bin\jpackage.exe" set "JDK=%%d"
)
if not defined JDK for /d %%d in ("C:\Program Files\Java\jdk-*") do (
    if exist "%%d\bin\jpackage.exe" set "JDK=%%d"
)
if not defined JDK if exist "D:\soft\graalvm-jdk-25_windows-x64_bin\graalvm-jdk-25.0.4+7.1\bin\jpackage.exe" (
    set "JDK=D:\soft\graalvm-jdk-25_windows-x64_bin\graalvm-jdk-25.0.4+7.1"
)
if not defined JDK (
    echo [错误] 未找到带 jpackage 的 JDK（需要 JDK 17 及以上）
    goto fail
)

echo 使用 Maven: %MVN%
echo 使用 JDK  : %JDK%

REM ---------- 1/4 可执行 jar ----------
echo [1/4] 打可执行 jar（maven-shade，含全部依赖与 JavaFX 原生库）...
call "%MVN%" -B package -DskipTests
if errorlevel 1 goto fail
if not exist "target\daliwan_tool.jar" (
    echo [错误] 未生成 target\daliwan_tool.jar
    goto fail
)

REM ---------- 2/4 精简运行时 ----------
echo [2/4] jlink 生成精简运行时（选定模块 + 中文语言包 + 压缩）...

if exist "target\runtime" rmdir /s /q "target\runtime"

REM 模块集合：jdeps --print-module-deps 结果 + 实测 OcrSmoke/DocSmoke/PdfSmoke/UiCheck 验证过

REM   java.desktop       Aspose / PDFBox / JavaFX Prism 都要系统 AWT

REM   java.sql(+rowset)  Aspose.Cells 引用了 javax.sql.rowset

REM   jdk.charsets       GBK/GB18030 编码，缺了系统默认编码就回退成 ISO-8859-1

REM   jdk.unsupported    Unsafe（fesod 的 cglib、JavaFX 都用）

REM   jdk.localedata     配合 --include-locales=zh,en：全部语言包约 30MB，只留中英文约 2MB

REM 不用 java.se 聚合模块：它会连带拉进 java.rmi / java.net.http / java.instrument 等用不到的东西

"%JDK%\bin\jlink.exe" --add-modules java.base,java.desktop,java.logging,java.xml,java.datatransfer,java.prefs,java.management,java.sql,java.sql.rowset,java.naming,java.security.jgss,java.xml.crypto,jdk.charsets,jdk.unsupported,jdk.crypto.ec,jdk.localedata,jdk.jfr,jdk.zipfs ^
    --include-locales=zh,en --compress=zip-6 ^
    --output target\runtime --strip-debug --no-header-files --no-man-pages

if errorlevel 1 goto fail

REM ---------- 3/4 jpackage ----------
echo [3/4] jpackage 生成应用目录...
if exist "target\pkg" rmdir /s /q "target\pkg"
mkdir "target\pkg"
copy /y "target\daliwan_tool.jar" "target\pkg\" >nul
if exist "dist" rmdir /s /q "dist"
"%JDK%\bin\jpackage.exe" --type app-image ^
    --name daliwan_tool --app-version 1.0.0 --vendor daliwan_tool ^
    --description "大力丸：本地运行的 JavaFX 工具箱" ^
    --dest dist --input target\pkg ^
    --main-jar daliwan_tool.jar --main-class com.daliwan.tool.Launcher ^
    --runtime-image target\runtime ^
    --icon src\windows\assets\icon.ico ^
    --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 goto fail
if not exist "dist\daliwan_tool\daliwan_tool.exe" (
    echo [错误] dist\daliwan_tool\daliwan_tool.exe 未生成
    goto fail
)

REM exe 改成中文名「大力丸」：jpackage 没有单独的 exe 名参数，--name 同时决定目录名，
REM 而中文目录在个别工具里容易出编码问题，所以目录保持 ASCII，只把 exe（和它同名的
REM cfg）改掉 —— 启动器是按 exe 自己的文件名去找 app\<同名>.cfg 的，两个必须一起改。
ren "dist\daliwan_tool\daliwan_tool.exe" "大力丸.exe"
ren "dist\daliwan_tool\app\daliwan_tool.cfg" "大力丸.cfg"
if not exist "dist\daliwan_tool\大力丸.exe" (
    echo [错误] exe 改名为 大力丸.exe 失败
    goto fail
)

REM ---------- 4/4 压缩 ----------
echo [4/4] 压缩为 release\daliwan_tool-windows-x64.zip ...
where python >nul 2>nul
if errorlevel 1 (
    echo [提示] 未找到 python，跳过压缩；直接使用 dist\daliwan_tool 目录即可
) else (
    if not exist release mkdir release
    python tools\zip_app.py
)

echo.
echo 打包完成：dist\daliwan_tool\大力丸.exe
echo           （整个 dist\daliwan_tool 目录拷走即可运行，无需安装）
echo.
if "%NO_PAUSE%"=="1" exit /b 0
pause
exit /b 0

:fail
echo.
echo [失败] 打包中断
if "%NO_PAUSE%"=="1" exit /b 1
pause
exit /b 1
