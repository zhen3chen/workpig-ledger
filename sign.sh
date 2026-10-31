#!/usr/bin/env bash
# ============================================================
#  打工人小账本 · Release 签名一键脚本
#  用法：
#     1) bash init-key.sh        # 仅第一次：生成签名密钥
#     2) bash sign.sh            # 编译并签名，输出 dist/ 目录
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f keystore.properties ]; then
  echo "❌ 未找到 keystore.properties"
  echo "   请先执行：bash init-key.sh"
  exit 1
fi

echo "▶ 开始构建 Release 签名包..."
./gradlew clean assembleRelease --no-daemon

APK=$(find app/build/outputs/apk/release -name "*-release.apk" | head -1)
if [ -z "$APK" ]; then
  echo "❌ 未找到构建产物，请检查上方日志"
  exit 1
fi

mkdir -p dist
OUT="dist/打工人-v$(grep -oP "versionName \"\K[^\"]+" app/build.gradle | head -1).apk"
cp "$APK" "$OUT"

echo "▶ 校验签名..."
export PATH="$ANDROID_HOME/build-tools/35.0.0:$PATH"
if command -v apksigner >/dev/null 2>&1; then
  apksigner verify --verbose --print-certs "$OUT" | head -20
else
  echo "⚠ 未找到 apksigner，跳过签名校验（请确认已安装 build-tools）"
fi

echo ""
echo "✅ 完成：$OUT"
shasum -a 256 "$OUT" 2>/dev/null || sha256sum "$OUT"
