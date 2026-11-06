# 打工人小账本 · Android 工程（C 方案）

一个 **离线 WebView 壳工程**：把已经做好的单文件工作台 `app/src/main/assets/index.html` 打包成原生 APK。
页面本身零外链、零后端，壳只负责加载、保存文件和系统返回键。

> **重要前提**：本项目提供的是**可编译、可签名的工程**，不是预签名 APK。
> Release 签名必须用**你自己的密钥**在你自己的机器上执行——私钥不应经过任何第三方，这也是签名机制存在的意义。

---

## 一、环境要求

| 项目 | 版本 | 说明 |
|---|---|---|
| JDK | **17**（必须） | AGP 8.7 不支持 JDK 21+ 编译；Android Studio 自带的 JBR 17 即可 |
| Android SDK | Platform 35 + Build-Tools 35.0.0 | Android Studio SDK Manager 里勾选 |
| Gradle | 8.11.1（工程已内置 wrapper） | 首次构建自动下载 |
| AGP | 8.7.3 | 已在 `build.gradle` 固定 |

`minSdk 24`（Android 7.0）→ 小米 17 Pro（HyperOS，Android 15/16）完全覆盖。

---

## 二、最快路径：命令行三步

```bash
cd workpig-android

# 1) 生成你自己的签名密钥（只做一次）
bash init-key.sh
#    → 按提示填写组织/城市/密码
#    → 产出 workpig-release.jks + keystore.properties

# 2) 编译并签名
export ANDROID_HOME=$HOME/Library/Android/sdk     # macOS；Windows 用 %LOCALAPPDATA%\Android\Sdk
bash sign.sh

# 3) 产物
ls dist/
# 打工人小账本-v1.1.0.apk
```

**务必把 `workpig-release.jks` 和密码另外备份一份。** 密钥丢了，已安装的旧版就无法覆盖升级，只能卸载重装（数据随之丢失）。

---

## 三、Android Studio 路径

1. `File → Open` 选择 `workpig-android` 目录，等待 Gradle 同步完成
2. 在项目根目录放好 `keystore.properties`（格式见下），Studio 会自动识别 `signingConfigs.release`
3. 菜单 `Build → Generate Signed App Bundle / APK → APK → release`
4. 产物路径：`app/build/outputs/apk/release/app-release.apk`

也可以直接在 Studio 里配置签名：`Build → Generate Signed APK` 向导中选择已有 keystore，勾选 **V1 + V2** 签名。

`keystore.properties` 格式：

```properties
RELEASE_STORE_FILE=/绝对路径/workpig-release.jks
RELEASE_STORE_PASSWORD=你的密钥库密码
RELEASE_KEY_ALIAS=workpig
RELEASE_KEY_PASSWORD=你的密钥密码
```

> 该文件已在 `.gitignore` 中排除，**不要提交到任何仓库或网盘公开链接**。

---

## 四、手工签名（如果你的构建环境拿不到 keystore）

```bash
# 1. 构建未签名包
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release-unsigned.apk

# 2. 用 build-tools 里的 zipalign 对齐（4 字节）
$ANDROID_HOME/build-tools/35.0.0/zipalign -v -p 4 \
  app/build/outputs/apk/release/app-release-unsigned.apk aligned.apk

# 3. 用 apksigner 签名（同时写入 v1/v2/v3）
$ANDROID_HOME/build-tools/35.0.0/apksigner sign \
  --ks workpig-release.jks --ks-key-alias workpig \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out 打工人小账本.apk aligned.apk

# 4. 自检（必须输出 "Verifies"，并确认 v2/v3 为 true）
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose --print-certs 打工人小账本.apk
```

此外还可以用 `apksigner verify --print-certs` 比对证书指纹（SHA-256），后续每次发版指纹必须一致。

---

## 五、关于「默认被当成病毒」

这里必须说清楚，避免白折腾：

1. **签名 ≠ 免病毒提示。** 小米/HyperOS 的风险提示来自云端查杀（AVL 引擎）与「未知来源应用」策略，
   自签名证书只是证明「这个包确实由同一个开发者签发」，不具备任何白名单效力。
2. 正规自签名 APK 安装时通常出现的是两种提示：
   - 「未知来源」——需在 `设置 → 应用设置 → 授权管理 → 未知来源应用安装` 中允许你的文件管理器/浏览器
   - 「应用未经安全检测」——小米对非商店应用的一般性提示，可点「继续安装」
3. 若出现「疑似病毒 / 风险应用」红色拦截：
   - 大概率是**混淆或加壳**、**申请了不必要权限**、或**包名与签名证书在云端有风险记录**导致。
     本工程已刻意规避：**不申请 INTERNET 权限**、不申请存储读取权限、不申请通讯录/位置等敏感权限。
   - 可用 [VirusTotal](https://www.virustotal.com) 上传自检，正常自签名 WebView 壳一般报 0–2 个引擎误报。
4. 想彻底消除提示，唯一可靠方式是**上架应用商店**（小米应用商店 / 应用宝），或申请企业/开发者白名单。
5. 小米安装时如需临时降低拦截：
   `设置 → 应用设置 → 应用管理 → 找到安装器 → 关闭「安全检测」`（不同 HyperOS 版本路径略有差异）。

---

## 六、工程结构与实现要点

```
workpig-android/
├── settings.gradle / build.gradle / gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/
├── init-key.sh          # 一次性生成签名密钥
├── sign.sh              # 编译 + 签名 + 校验 + 输出 dist/
├── make-secrets.sh      # 生成 GitHub Secrets 所需的 base64 与密码
├── push-to-github.sh    # 带密钥泄漏防护的推送脚本
├── .github/workflows/build-release.yml  # 云端出包
└── app/
    ├── build.gradle     # 签名配置、minSdk 24、ABI 无关
    └── src/main/
        ├── AndroidManifest.xml     # 无 INTERNET 权限
        ├── assets/index.html       # ← 工作台本体（替换这个文件即可更新内容）
        ├── java/com/workpig/ledger/MainActivity.java
        └── res/
            ├── drawable/ic_launcher_foreground.xml  # 小猪矢量图标
            ├── mipmap-anydpi-v26/                   # 自适应图标
            ├── values/{strings,colors,themes}.xml
            └── xml/{backup_rules,data_extraction_rules}.xml
```

关键实现：

| 能力 | 做法 |
|---|---|
| 离线加载 | `file:///android_asset/index.html`，`setAllowFileAccess(false)`，只读 assets |
| 数据持久化 | `setDomStorageEnabled(true)` → 页面里的 localStorage 正常工作 |
| 导出 JSON/CSV | 页面检测 `window.AndroidBridge`，走 `saveBlob(name, base64, mime)` 写入系统「下载」目录（Android 10+，MediaStore） |
| 导入 JSON | `onShowFileChooser` + `ACTION_GET_CONTENT`，回填给页面的 `<input type=file>` |
| 本地备份（v1.1+） | 原生桥 `saveBackup / listBackups / readBackup / deleteBackup` 直写 `下载/打工人小账本`，每日自动备份、保留最近 10 份；无需权限、卸载重装不丢 |
| 二次确认 | 拦截 `window.prompt`（「清空全部」需要输入关键词），换成原生输入框；防止 WebView 默认不支持 prompt 而失效 |
| 返回键 | 先关页面弹窗，再退出 |
| 云备份 | `backup_rules.xml` / `data_extraction_rules.xml` 只备份 `app_webview/`，换机可恢复 localStorage |

---

## 七、更新页面内容

改完 HTML 后覆盖 `app/src/main/assets/index.html`，把 `app/build.gradle` 里的 `versionCode` +1、
`versionName` 改成新版本号，重新 `bash sign.sh` 即可。**签名不变即可覆盖安装，数据不丢。**

---

## 八、常见问题

**Q：构建报 `Unsupported class file major version 65` / 需要 JDK 17**
A：当前 JDK 太新或太旧。设置 `JAVA_HOME` 指向 JDK 17：Android Studio 自带路径一般是
`/Applications/Android Studio.app/Contents/jbr/Contents/Home`（macOS）或 `C:\Program Files\Android\Android Studio\jbr`（Windows）。

**Q：卡在 `Downloading https://services.gradle.org/distributions/gradle-8.11.1-bin.zip` 超时**
A：国内网络访问 Gradle 官方分发源经常超时。把 `gradle/wrapper/gradle-wrapper.properties` 里的
`distributionUrl` 换成腾讯云镜像即可：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.11.1-bin.zip
```

或者手工下载该 zip，放到 `~/.gradle/wrapper/dists/gradle-8.11.1-bin/<hash>/` 下（hash 目录会在首次失败时自动创建）。

**Q：`sdk.dir` 报错**
A：删掉 `local.properties`，让 Android Studio 重新生成；命令行则设置 `ANDROID_HOME` 环境变量。

**Q：安装时提示「应用未安装 / 签名冲突」**
A：设备上已有同包名但不同签名的旧版本。先卸载旧版（注意先导出 JSON 备份）再装。

**Q：导出文件没反应**
A：导出走系统「另存为」选择器（`ACTION_CREATE_DOCUMENT`），会弹出一个文件保存界面，选好位置点保存即可。
若设备上没有任何文件管理器 / 保存器，会提示「找不到可用的文件保存器」。本工程不申请任何存储读写权限，
这是 HyperOS 上最稳的做法（Android 14+ 的分区存储策略也完全兼容）。

**Q：Windows 下 Gradle 报找不到签名密钥**
A：`keystore.properties` 里的 `RELEASE_STORE_FILE` 必须写 Windows 路径
（如 `C:/Users/你的用户名/workpig-android/workpig-release.jks`）。
Git Bash 风格的 `/c/Users/...` 会被 Gradle 按相对路径解析，导致签名阶段失败。

**Q：为什么不做成 Flutter/原生重写？**
A：这个工作台是纯前端 + localStorage，套壳零收益差异；真要原生体验，应重做 UI 层，属于另一个项目规模。

---

## 九、用 GitHub Actions 云端自动出包

工程内已带 `.github/workflows/build-release.yml`，不需要本机装任何环境。
仓库已推送到 GitHub（`main` 分支；签名密钥、`keystore.properties`、`secrets.txt` 均已由 `.gitignore` 排除）。
打 `v*` tag 会自动创建 Release 并附上签名 APK 与 `.sha256`，可直接把下载链接发到新手机安装。

### 第 1 步：在 GitHub 上新建空仓库

打开 <https://github.com/new>，填仓库名（如 `workpig-ledger`），
**不要**勾选 README / .gitignore / License（本地已有，勾了会冲突）。

### 第 2 步：推送

```bash
cd workpig-android
bash push-to-github.sh git@github.com:你的用户名/workpig-ledger.git
```

脚本会先检查暂存区里有没有 `.jks` / `keystore.properties`，发现就中止。
如果本机还没配 SSH key，把地址换成 HTTPS 版本即可（会提示输入 token）。

### 第 3 步：生成 Secrets

```bash
bash make-secrets.sh
# 输入密钥库密码 → 自动校验密码和别名 → 生成 secrets.txt（权限 600）
```

打开 `secrets.txt`，到 `Settings → Secrets and variables → Actions → New repository secret`
逐条添加：

| Secret 名 | 内容 |
|---|---|
| `KEYSTORE_BASE64` | 文件里那一长串 base64 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 别名（默认 `workpig`） |
| `KEY_PASSWORD` | 密钥密码 |

配置完立刻删掉：`rm -f secrets.txt`。

### 第 4 步：触发构建

- 网页方式：`Actions → Build Release APK → Run workflow`
  产物在运行页底部的 **Artifacts** 里，文件名 `打工人-apk.zip`，解压得到 `打工人.apk`
- 打 tag 方式：`git tag v1.0.1 && git push origin v1.0.1`
  会自动创建 Release，APK 与 `.sha256` 直接挂在 Release 页面

每次构建都会跑 `apksigner verify --print-certs`，签名不通过则整个任务失败，不会产出半成品。
流程末尾（`if: always()`）强制删除 `keystore.properties` 和临时 jks，日志不会残留密码。

> CI 机器在境外，走官方 Gradle 源一般不会超时；若用国内自建 runner，把
> `gradle-wrapper.properties` 里的 `distributionUrl` 换成腾讯云镜像。

---

## 十、版本记录

| 版本 | 变更 |
|---|---|
| **v1.0.0** | 首版：离线 WebView 壳 + 时薪/记账/存钱罐工作台，JSON/CSV 导出与导入，GitHub Actions 云端出包 |
| **v1.1.0** | 额外收入独立页面并计入月度收入（联动结余、储蓄率、首页）；应用内每日自动快照（最多 8 份，可恢复/导出/删除）；原生本地备份直写「下载 / 打工人小账本」（每日自动、保留 10 份）；覆盖/合并两种导入、粘贴文本迁移、导入撤销；CSV 含支出与收入；手机端补上「收入」「备份」入口 |

> 签名密钥与 v1.0.0 完全一致，v1.1.0 可直接覆盖安装，`localStorage` 里的账本数据不丢。
> 换机：旧手机「备份 → 导出备份文件」→ 新手机安装新版 →「选择文件导入 / 粘贴文本」即可恢复。


