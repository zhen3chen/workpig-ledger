#!/usr/bin/env bash
# ============================================================
#  把工程推送到 GitHub（首次使用：先在网页上新建一个空仓库）
#  用法：
#     bash push-to-github.sh git@github.com:你的用户名/workpig-ledger.git
#     或
#     bash push-to-github.sh https://github.com/你的用户名/workpig-ledger.git
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

REMOTE="${1:-}"
if [ -z "$REMOTE" ]; then
  echo "用法：bash push-to-github.sh <仓库地址>"
  echo "示例：bash push-to-github.sh git@github.com:yourname/workpig-ledger.git"
  exit 1
fi

# 防护：确认没有任何签名材料会被推上去
for f in workpig-release.jks keystore.properties local.properties; do
  if [ -f "$f" ] && ! grep -q "^$f$" .gitignore 2>/dev/null; then
    echo "⚠ 检测到 $f 且未被 .gitignore 排除，已中止。"
    exit 1
  fi
done
if git ls-files | grep -qE "\.jks$|^keystore\.properties$"; then
  echo "⚠ 暂存区里出现了密钥文件，已中止。请先 git rm --cached 移除。"
  exit 1
fi

if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "$REMOTE"
else
  git remote add origin "$REMOTE"
fi

echo "▶ 推送到 $REMOTE"
git push -u origin main

echo ""
echo "✅ 推送完成。接下来："
echo "   1. 打开仓库 Settings → Secrets and variables → Actions"
echo "   2. 按 README 第九节配置 4 个 Secrets"
echo "   3. 打开 Actions → Build Release APK → Run workflow"
