#!/usr/bin/env bash
# ============================================================
#  仅第一次执行：生成 Release 签名密钥（keystore）
#  重要：密钥文件一旦丢失，已发布的应用就无法再升级。
#        请务必备份 workpig-release.jks 与密码！
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

if [ -f keystore.properties ]; then
  echo "⚠ keystore.properties 已存在，为避免覆盖已有密钥，脚本退出。"
  exit 0
fi

read -rp "组织名称（默认 WorkPig）: " ORG; ORG=${ORG:-WorkPig}
read -rp "所在城市（默认 Beijing）: " CITY; CITY=${CITY:-Beijing}
read -rp "省份（默认 Beijing）: " ST; ST=${ST:-Beijing}
read -rp "国家代码（默认 CN）: " CC; CC=${CC:-CN}
read -rp "密钥库密码（至少 6 位）: " -s STOREPASS; echo
read -rp "再次输入密码: " -s STOREPASS2; echo
if [ "$STOREPASS" != "$STOREPASS2" ]; then
  echo "❌ 两次密码不一致"; exit 1
fi

keytool -genkeypair \
  -v \
  -keystore workpig-release.jks \
  -alias workpig \
  -keyalg RSA \
  -keysize 4096 \
  -validity 36500 \
  -storetype PKCS12 \
  -storepass "$STOREPASS" \
  -keypass "$STOREPASS" \
  -dname "CN=$ORG, OU=Mobile, O=$ORG, L=$CITY, ST=$ST, C=$CC"

cat > keystore.properties <<PROPS
RELEASE_STORE_FILE=$(pwd)/workpig-release.jks
RELEASE_STORE_PASSWORD=$STOREPASS
RELEASE_KEY_ALIAS=workpig
RELEASE_KEY_PASSWORD=$STOREPASS
PROPS

chmod 600 keystore.properties workpig-release.jks
echo ""
echo "✅ 密钥已生成：workpig-release.jks"
echo "✅ 签名配置已写入：keystore.properties（已在 .gitignore 中排除）"
echo "👉 下一步：bash sign.sh"
