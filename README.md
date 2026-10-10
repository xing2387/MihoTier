# MihoTier

**MihoTier** 是一款面向 Android 平台的融合网络工具，将 **[Mihomo (Clash Meta)](https://github.com/MetaCubeX/mihomo)** 代理核心与 **[EasyTier](https://github.com/EasyTier/EasyTier)** P2P 虚拟组网核心整合在同一个 Android 应用中，并通过单一 `VpnService`（`UnifiedVpnService`）统一接管与分流系统网络流量。

###### **代码是AI写的**

---

## ✨ 核心特性

- **双核融合架构**：通过单个 Android `VpnService` 协同运行 Mihomo (Go) 与 EasyTier (Rust)，解决 Android 平台同时只能开启一个 VPN 服务的限制。
- **实时流量与日志监控**：
  - 首页深海蓝动态卡片实时展示上下行速率与双折线面积流量波形图；
  - 支持状态栏与系统通知栏实时显示上下行网速（字节自适应单位 `B/s`、`KB/s`、`MB/s`）；
  - 结构化实时日志流，支持 `DEBUG` / `INFO` / `WARN` / `ERROR` / `SILENT` 热切换、关键词高亮过滤与自动滚动追踪。
- **配置文件管理 (Profiles)**：
  - 支持远程订阅链接（URL）与本地配置文件（YAML）导入、切换与一键更新；
  - 内置全屏等宽字体 YAML 编辑器，并支持通过 `FileProvider` 双向读写跳转外部编辑器修改后自动同步。
- **精细化网络与分应用控制 (Network & Access Control)**：
  - 支持系统流量路由、绕过私有网段 (Bypass LAN)、DNS 劫持、IPv6 路由、HTTP 系统代理附加；
  - 支持 `System Stack` / `gVisor` / `Mixed` 协议栈切换；
  - 基于 Android SDK `PackageManager` 注入 UID 映射，无需读取受限的 `/data/system/packages.xml` 即可实现 Android 10+ 分应用代理（白名单 / 黑名单模式）。

---

## 📁 目录结构

```text
MihoTier/
├── app/                      # Android 主应用模块 (Jetpack Compose UI)
├── core/                     # 核心 VPN 服务与配置管理模块 (UnifiedVpnService)
├── easytier/                 # EasyTier Android Library 模块 (Rust JNI 绑定)
│   └── src/main/rust/        # 调用 vendor/easytier-src 的 Rust JNI 源码
├── mihomo-mobile/            # Mihomo Go-Mobile 绑定包装模块
│   └── lib.go                # 导出供 Android 调用的 Go 接口
├── scripts/
│   └── mihomo/
│       ├── build.sh          # 自动打补丁并编译 mihomo.aar 的构建脚本
│       └── patches/          # 针对 Android PackageManager 分应用路由的适配代码
└── vendor/                   # Git 子模块 (第三方核心源码)
    ├── easytier-src/         # EasyTier 官方源码子模块 (LGPL-3.0)
    └── mihomo-src/           # Mihomo 官方源码子模块 (GPL-3.0)
```

---

## 🚀 克隆与编译指南

### 1. 克隆仓库（含子模块）

```bash
git clone --recurse-submodules https://github.com/xing2387/MihoTier.git
cd MihoTier
```

如果已经克隆了主仓库但未拉取子模块，可执行：

```bash
git submodule update --init --recursive
```

### 2. 环境依赖

- **Android SDK & NDK**（推荐 NDK 28+，在根目录 `local.properties` 配置 `sdk.dir`）
- **JDK 17+**
- **Go 1.22+** 与 `gomobile`（用于编译 `mihomo.aar`）
- **Rust 工具链** 与 `cargo-ndk`（用于编译 `libeasytier.so`）：
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
  cargo install cargo-ndk
  ```

### 3. 编译 Mihomo 核心 (`mihomo.aar`)

运行内置构建脚本，脚本会自动将 `scripts/mihomo/patches/` 下的 Android 分应用路由桥接补丁同步到 `vendor/mihomo-src` 并生成 `core/libs/mihomo.aar`：

```bash
./scripts/mihomo/build.sh
```

### 4. 编译并安装 Android 应用

Gradle 构建会自动调用 `cargo-ndk` 编译 `:easytier` JNI 动态库并打包 APK：

```bash
./gradlew :app:assembleDebug
# 或直接安装到已连接的设备：
./gradlew installDebug
```

---

## 📄 开源协议与致谢 (License & Credits)

本项目基于 **[GNU General Public License v3.0 (GPLv3)](LICENSE)** 开源协议发布。

本项目依赖并致谢以下优秀的开源项目：
- **[MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo)** — Licensed under [GPL-3.0](https://github.com/MetaCubeX/mihomo/blob/Meta/LICENSE)
- **[EasyTier/EasyTier](https://github.com/EasyTier/EasyTier)** — Licensed under [LGPL-3.0](https://github.com/EasyTier/EasyTier/blob/main/LICENSE)