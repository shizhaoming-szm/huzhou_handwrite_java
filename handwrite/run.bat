@echo off
title 手写签名识别服务启动器
color 0A

echo ========================================
echo    手写签名识别服务启动器
echo ========================================
echo.

REM 检查Java环境
echo 正在检查Java环境...
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到Java环境
    echo 请安装Java 8或更高版本
    echo 下载地址: https://www.oracle.com/java/technologies/javase-downloads.html
    echo.
    pause
    exit /b 1
) else (
    echo ✅ Java环境检查通过
    java -version
    echo.
)

REM 检查Maven环境
echo 正在检查Maven环境...
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 错误: 未找到Maven环境
    echo 请安装Maven 3.6或更高版本
    echo 下载地址: https://maven.apache.org/download.cgi
    echo.
    echo 配置说明:
    echo 1. 设置环境变量 MAVEN_HOME 为Maven安装目录
    echo 2. 将 %%MAVEN_HOME%%\bin 添加到PATH环境变量
    echo.
    pause
    exit /b 1
) else (
    echo ✅ Maven环境检查通过
    mvn -version | findstr "Apache Maven"
    echo.
)

REM 编译项目
echo 正在编译项目...
echo ----------------------------------------
mvn clean compile
if %errorlevel% neq 0 (
    echo.
    echo ❌ 编译失败，请检查代码和依赖
    echo 可能的解决方案:
    echo 1. 检查网络连接（用于下载依赖）
    echo 2. 配置Maven镜像源加速下载
    echo 3. 清理Maven缓存: mvn clean
    echo.
    pause
    exit /b 1
) else (
    echo.
    echo ✅ 项目编译成功
    echo.
)

REM 运行应用
echo 正在启动应用...
echo ========================================
echo 服务即将在以下地址运行:
echo http://localhost:8080
echo ========================================
echo.
echo 按 Ctrl+C 可以停止服务
echo.

mvn spring-boot:run

echo.
echo 服务已停止
pause