#!/usr/bin/env bash
# ============================================================
#  把工程推送到 GitHub
#  用法：
#     bash push-to-github.sh                          # 推到已配置的 origin
#     bash push-to-github.sh <仓库地址>                # 指定/更换仓库
# ============================================================
set -euo pipefail
cd "$(dirname "$0")"

REMOTE="${1:-}"
if [ -n "$REMOTE" ]; then
  if git remote get-url origin >/dev/null 2>&1; then
    git remote set-url origin "$REMOTE"
  else
    git remote add origin "$REMOTE"
  fi
fi

if ! git remote get-url origin >/dev/null 2>&1; then
  echo "用法：bash push-to-github.sh <仓库地址>"
  echo "示例：bash push-to-github.sh https://github.com/yourname/workpig-ledger.git"
  exit 1
fi

echo "▶ 仓库：$(git remote get-url origin)"
echo ""

# 防护 1：密钥文件不能进暂存区
if git ls-files | grep -qE '\.jks$|\.keystore$|^keystore\.properties$|^secrets\.txt$|^local\.properties$'; then
  echo "❌ 暂存区里出现了密钥/本地配置，已中止："
  git ls-files | grep -E '\.jks$|\.keystore$|^keystore\.properties$|^secrets\.txt$|^local\.properties$'
  echo "   处理：git rm --cached <文件>，并确认 .gitignore 已包含"
  exit 1
fi
echo "✓ 暂存区无密钥文件"

# 防护 2：工作区不能有未提交改动
if [ -n "$(git status --porcelain)" ]; then
  echo "⚠ 有未提交的改动，请先 commit 再推送"
  git status --short
  exit 1
fi
echo "✓ 工作区干净"

# 防护 3：提交历史里不能含真实私钥或真实签名密码
# 说明：不能用 `RELEASE_STORE_PASSWORD=.{6,}` 这种宽松正则——脚本自身、workflow 里的
# printf 模板和 README 的中文示例都会命中，造成误报；改为比对本机真实密码字面值。
if git log -p --all 2>/dev/null | grep -qE -- '-----BEGIN (RSA |EC |OPENSSH |)PRIVATE KEY-----'; then
  echo "❌ 提交历史里疑似含私钥，已中止"
  exit 1
fi
if [ -f keystore.properties ]; then
  while IFS= read -r REAL_PASS; do
    [ "${#REAL_PASS}" -ge 6 ] || continue
    if git log -p --all 2>/dev/null | grep -Fq -- "$REAL_PASS"; then
      echo "❌ 提交历史里疑似含真实签名密码，已中止"
      exit 1
    fi
  done < <(sed -n 's/^RELEASE_\(STORE\|KEY\)_PASSWORD=//p' keystore.properties | tr -d '\r' | sort -u)
fi
echo "✓ 提交历史无密钥痕迹"
echo ""

echo "▶ 开始推送..."
git push -u origin main

echo ""
echo "✅ 推送成功"
echo ""
echo "下一步："
echo "  1) 生成签名密钥（如还没有）：bash init-key.sh"
echo "  2) 生成 Secrets：            bash make-secrets.sh"
echo "  3) 到 GitHub → Settings → Secrets and variables → Actions 添加 4 条"
echo "  4) 到 Actions → Build Release APK → Run workflow"
