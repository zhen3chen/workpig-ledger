#!/usr/bin/env bash
# ============================================================
#  生成 GitHub Actions 需要的 4 个 Secrets
#  用法：bash make-secrets.sh
#  输出：secrets.txt（含 base64 与密码，用完请立即删除）
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f workpig-release.jks ]; then
  echo "❌ 没找到 workpig-release.jks"
  echo "   请先执行：bash init-key.sh"
  exit 1
fi

read -rp "密钥库密码: " -s KSPASS; echo
read -rp "密钥别名（默认 workpig）: " ALIAS; ALIAS=${ALIAS:-workpig}

# 校验密码是否正确，避免推到 CI 才发现签名失败
if ! keytool -list -keystore workpig-release.jks -alias "$ALIAS" -storepass "$KSPASS" >/dev/null 2>&1; then
  echo "❌ 密码或别名不正确，请重试"
  exit 1
fi

{
  echo "===== 复制下面每一行到 GitHub Secrets（Name 在等号左边）====="
  echo ""
  echo "KEYSTORE_BASE64="
  base64 -w0 workpig-release.jks 2>/dev/null || base64 -i workpig-release.jks | tr -d '\n'
  echo ""
  echo ""
  echo "KEYSTORE_PASSWORD=$KSPASS"
  echo "KEY_ALIAS=$ALIAS"
  echo "KEY_PASSWORD=$KSPASS"
} > secrets.txt

chmod 600 secrets.txt
echo ""
echo "✅ 已写入 secrets.txt（权限 600）"
echo "👉 打开 secrets.txt 复制内容，配置完后执行： rm -f secrets.txt"
