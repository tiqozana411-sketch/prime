#!/bin/bash

# PRIME 本地编译脚本
# 适用于Android设备上的Linux环境（proot/Termux）

set -e

echo "========================================="
echo "PRIME 本地编译脚本"
echo "========================================="

PROJECT_DIR="/storage/emulated/0/PRIME"
cd "$PROJECT_DIR"

# 检查环境
echo ""
echo "📋 检查编译环境..."

# 检查Java
if command -v java >/dev/null 2>&1; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    echo "✅ Java: $JAVA_VERSION"
else
    echo "❌ 未找到Java，正在安装..."
    apt-get update
    apt-get install -y openjdk-17-jdk
fi

# 检查Android SDK
if [ -z "$ANDROID_HOME" ]; then
    echo "⚠️  未设置ANDROID_HOME"
    echo "   尝试使用默认路径..."
    
    # 常见Android SDK路径
    POSSIBLE_PATHS=(
        "/data/data/com.termux/files/home/android-sdk"
        "$HOME/android-sdk"
        "/opt/android-sdk"
        "/usr/lib/android-sdk"
    )
    
    for path in "${POSSIBLE_PATHS[@]}"; do
        if [ -d "$path" ]; then
            export ANDROID_HOME="$path"
            echo "✅ 找到Android SDK: $ANDROID_HOME"
            break
        fi
    done
    
    if [ -z "$ANDROID_HOME" ]; then
        echo "❌ 未找到Android SDK"
        echo ""
        echo "请手动安装Android SDK："
        echo "1. 下载 commandlinetools: https://developer.android.com/studio#command-tools"
        echo "2. 解压到 $HOME/android-sdk"
        echo "3. 设置环境变量: export ANDROID_HOME=$HOME/android-sdk"
        echo "4. 安装必需组件:"
        echo "   \$ANDROID_HOME/cmdline-tools/bin/sdkmanager --sdk_root=\$ANDROID_HOME \"platform-tools\" \"platforms;android-34\" \"build-tools;34.0.0\""
        exit 1
    fi
else
    echo "✅ Android SDK: $ANDROID_HOME"
fi

# 设置环境变量
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# 检查Gradle Wrapper
if [ ! -f "gradlew" ]; then
    echo "⚠️  未找到gradlew，正在创建..."
    
    # 创建gradle wrapper目录
    mkdir -p gradle/wrapper
    
    # 下载gradle wrapper jar
    echo "📥 下载Gradle Wrapper..."
    GRADLE_VERSION="8.2"
    WRAPPER_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    
    # 创建gradle-wrapper.properties
    cat > gradle/wrapper/gradle-wrapper.properties << EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
    
    echo "✅ Gradle Wrapper配置完成"
fi

# 设置gradlew可执行权限
chmod +x gradlew

# 开始编译
echo ""
echo "========================================="
echo "🔨 开始编译PRIME..."
echo "========================================="
echo ""

# 清理旧的构建
echo "🧹 清理旧的构建..."
./gradlew clean

# 编译Debug版本
echo ""
echo "🔨 编译Debug APK..."
./gradlew assembleDebug

# 检查编译结果
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
    echo ""
    echo "========================================="
    echo "✅ 编译成功！"
    echo "========================================="
    echo ""
    echo "📦 APK位置: $APK_PATH"
    echo "📊 APK大小: $APK_SIZE"
    echo ""
    echo "安装命令:"
    echo "  adb install $APK_PATH"
    echo ""
else
    echo ""
    echo "========================================="
    echo "❌ 编译失败"
    echo "========================================="
    echo ""
    echo "请检查错误信息"
    exit 1
fi
