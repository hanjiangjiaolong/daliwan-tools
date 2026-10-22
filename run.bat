@echo off
setlocal

REM ============================================================
REM  开发者工具 启动脚本
REM  按顺序查找 Maven：本机已知路径 -> 系统 PATH
REM ============================================================

set "MVN="

if exist "D:\soft\apache-maven-3.9.6\bin\mvn.cmd" set "MVN=D:\soft\apache-maven-3.9.6\bin\mvn.cmd"
if exist "D:\soft\apache-maven-3.9.9\bin\mvn.cmd" set "MVN=D:\soft\apache-maven-3.9.9\bin\mvn.cmd"
if not defined MVN if exist "C:\Program Files\apache-maven-3.9.9\bin\mvn.cmd" set "MVN=C:\Program Files\apache-maven-3.9.9\bin\mvn.cmd"
if not defined MVN for %%i in (mvn.cmd) do if not "%%~$PATH:i"=="" set "MVN=%%~$PATH:i"

if not defined MVN (
    echo [错误] 未找到 Maven，请编辑本脚本，把 MVN 变量改为你自己的 mvn.cmd 路径。
    pause
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 java，请先安装 JDK 21 并加入 PATH。
    pause
    exit /b 1
)

echo 使用 Maven: %MVN%
echo 正在启动，首次运行需要下载依赖，请稍候...
echo.

call "%MVN%" -q javafx:run

if errorlevel 1 (
    echo.
    echo [提示] 启动失败，可在命令行执行 mvn clean javafx:run 查看详细报错。
    pause
)

endlocal
