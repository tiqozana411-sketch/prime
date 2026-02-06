#!/bin/bash

# PRIME 编译环境安装脚本
# 在Ubuntu 24.04 proot环境中安装Java和Android SDK

set -e

echo "========================================="
echo "PRIME 编译环境安装"
echo "========================================="

# 1. 更新包列表
echo ""
echo "📦 更新包列表..."
apt-get update

# 2. 安装Java 17
echo ""
echo "☕ 安装Java 17..."
apt-get install -y openjdk-17-jdk

# 验证Java安装
java -version
javac -version

# 3. 安装必需工具
echo ""
echo "🔧 安装必需工具..."
apt-get install -y wget unzip curl

# 4. 下载并安装Android SDK
echo ""
echo "📱 安装Android SDK..."

SDK_DIR="$HOME/android-sdk"
mkdir -p "$SDK_DIR"
cd "$SDK_DIR"

# 下载commandlinetools
CMDTOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip"
echo "📥 下载Android Command Line Tools..."
wget -q --show-progress "$CMDTOOLS_URL" -O cmdtools.zip

# 解压
echo "📦 解压..."
unzip -q cmdtools.zip
rm cmdtools.zip

# 移动到正确位置
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true

# 5. 设置环境变量
echo ""
echo "🔧 设置环境变量..."

cat >> ~/.bashrc << 'EOF'

# Android SDK
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH
EOF

# 立即生效
export ANDROID_HOME=$HOME/android-sdk
export PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# 6. 安装Android SDK组件
echo ""
echo "📦 安装Android SDK组件..."

yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses

sdkmanager --sdk_root=$ANDROID_HOME \
    "platform-tools" \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    "cmdline-tools;latest"

# 7. 验证安装
echo ""
echo "========================================="
echo "✅ 安装完成！"
echo "========================================="
echo ""
echo "Java版本:"
java -version
echo ""
echo "Android SDK位置:"
echo "  $ANDROID_HOME"
echo ""
echo "已安装的SDK组件:"
sdkmanager --sdk_root=$ANDROID_HOME --list_installed
echo ""
echo "========================================="
echo "现在可以编译PRIME了！"
echo "========================================="
echo ""
echo "运行编译命令:"
echo "  cd /storage/emulated/0/PRIME"
echo "  bash build_local.sh"
echo ""