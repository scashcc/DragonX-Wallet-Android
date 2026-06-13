# DragonX 安卓钱包（修复版）

这个仓库把 **DragonX 安卓钱包** 的全部源码放在一起，并配置了 **GitHub Actions 云端自动编译**。
每次代码更新，GitHub 会自动编译出可直接安装的 APK，无需自己装编译环境。

## 仓库结构

| 目录 | 说明 |
|---|---|
| `SilentDragonXAndroid/` | 钱包 App（界面层，Kotlin） |
| `dragonx-android-wallet-sdk/` | 钱包引擎 SDK（Kotlin + Rust JNI） |
| `librustzcash/` | 底层密码学库（上游 zcash/librustzcash + DragonX 参数补丁，已重建） |
| `.github/workflows/build.yml` | 云端自动编译流程 |

## 怎么拿到编译好的 APK

1. 点本仓库上方的 **Actions** 标签。
2. 点最新一次绿色对勾 ✅ 的运行记录。
3. 方式一：拉到页面最下面 **Artifacts**，下载 `dragonx-wallet-apks`（一个 zip，解压里面有 APK）。
   方式二：去本仓库的 **Releases**，直接下载 `DragonX-Wallet-v1.1.2-universal.apk`。
4. 把 `DragonX-Wallet-v1.1.2-universal.apk` 传到手机安装（首次安装需在手机设置里允许「安装未知来源应用」）。
   - `universal` = 通用包，任何手机都能装；其余按 CPU 架构拆分的包（arm64-v8a 等）体积更小，懂的可自选。

## 本次改动要点

- **转账 Bug**：根因是旧版底层库（Hush fork 的并行扫描魔改）把本地 Sapling 承诺树算坏，导致交易引用了非法 anchor、被节点拒绝。本仓库改用 **上游 zcash/librustzcash 标准实现 + DragonX 参数**，从根上修复。
- **DragonX 专属参数**（已打补丁）：币种路径 `coin_type=141`、Sapling 全视图密钥前缀 `zviews`、Sapling 从高度 1 激活、禁用 Sapling 之后的所有网络升级（保持共识分支为 Sapling `0x76b809bb`，与节点一致）。
- **新检查点**：内置检查点扩充到约 306 万高度，新建/恢复钱包可从接近链顶起步，几分钟同步完。

## 编译用到的工具链（云端自动准备，无需本地安装）

JDK 11 · Gradle 7.5.1 · Android NDK r22 (22.1.7171670) · Rust 1.65.0（4 个安卓目标）

> 这是一套刻意与依赖年代匹配的工具链（Cargo.lock 锁定到 2022-11 的依赖版本），不要随意升级，否则可能编译失败。
