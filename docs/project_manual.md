# Mihotier 项目说明书

本项目（Mihotier）旨在将 **Mihomo (Clash Meta)** 的 Go 代理核心与 **EasyTier** 的 Rust P2P 组网核心整合到同一个 Android App 中，并通过单个 `VpnService` 统一接管和路由流量。

以下是项目的目录结构说明以及两大核心依赖的编译流程。

---

## 📁 核心目录结构

```text
Mihotier/
├── app/                  # 🟢 Android 主程序模块 (App)
│   ├── src/main/java/.../ui/ # Jetpack Compose 构建的用户界面层 (首页、设置等)
│   └── build.gradle.kts      # 依赖了 core 和 easytier 模块，最终生成 APK
│
├── core/                 # 🔵 核心服务模块 (Android Library)
│   ├── src/main/java/... # 包含 UnifiedVpnService，负责接管 Android 全局流量
│   ├── libs/             # 存放编译好的 mihomo.aar 文件
│   └── build.gradle.kts  # 包含了对 mihomo.aar 的依赖配置
│
├── easytier/             # 🟠 EasyTier 模块 (Android Library / JNI)
│   ├── src/main/rust/    # 我们手写的 Rust JNI 绑定代码 (lib.rs)
│   ├── build.gradle.kts  # 包含自动调用 cargo-ndk 编译 Rust 代码的 Gradle 任务
│   └── build/cargo-jniLibs/ # 编译过程中生成的对应架构的 libeasytier.so 文件
│
├── mihomo-mobile/        # 🟡 Mihomo Go-Mobile 包装模块
│   ├── lib.go            # 导出了给 Java 调用的 Start() 和 Stop() 接口
│   ├── tools.go          # 锁定 gomobile 依赖
│   └── mobile.aar        # 通过 gomobile 编译生成的产物 (编译后手动移入 core/libs)
│
└── vendor/               # 📦 第三方源码存放区
    ├── easytier-src/     # EasyTier 的官方 Rust 源码仓库
    └── mihomo-src/       # Mihomo (Meta分支) 的官方 Go 源码仓库
```

---

## ⚙️ Mihomo (Go Core) 编译流程

Mihomo 本身是一个纯 Go 项目，为了让 Android 能够调用，我们需要使用官方的 `gomobile` 工具将其编译成 Android 标准库格式（`.aar`）。

**编译步骤：**
1. **准备环境**：需要安装 Go 环境以及 `gomobile` 工具。
2. **包装接口**：在 `mihomo-mobile/lib.go` 中，我们编写了非常简单的 wrapper，引入了 `vendor/mihomo-src` 的核心库，并导出了 `Start(homeDir string, configStr string)` 和 `Stop()` 函数。通过 `go.mod` 的 `replace` 指令，让它直接读取 `vendor/mihomo-src` 里的本地源码。
3. **执行编译**：进入 `mihomo-mobile` 目录，执行以下命令：
   ```bash
   gomobile bind -target=android -androidapi 24 -v ./
   ```
   *这会将 Go 代码使用 NDK 交叉编译为四个架构 (arm64-v8a, armeabi-v7a, x86, x86_64) 的 C 动态库，并用 Java 包装一层 JNI 代码，最终打包成 `mobile.aar`。*
4. **集成**：将生成的 `mobile.aar` 移动到 `core/libs/mihomo.aar`，并在 `core/build.gradle.kts` 中使用 `compileOnly`（并在 `app` 模块中使用 `implementation`）将其接入 Android 工程。

---

## ⚙️ EasyTier (Rust Core) 编译流程

EasyTier 是使用 Rust 编写的。因为我们需要深度定制它的行为（以 No-TUN / SOCKS5 模式运行并接管 Android 的 `VpnService.protect()` 回调以防止流量环路），我们在 `easytier` 模块下编写了定制的 JNI 接口。

**编译步骤：**
1. **配置依赖**：在 `easytier/src/main/rust/Cargo.toml` 中，我们将 `easytier-core` 的依赖路径指向了本地的 `vendor/easytier-src/easytier-core`。这允许我们直接复用官方源码，同时保持 JNI 代码的分离。
2. **Gradle 任务自动化**：在 `easytier/build.gradle.kts` 中，我们注册了一个名为 `cargoBuild` 的 Gradle 任务。
3. **执行编译**：当你点击运行或在终端执行 `./gradlew assembleDebug` 时，Gradle 会自动触发 `cargoBuild` 任务，执行如下命令：
   ```bash
   cargo ndk -t armeabi-v7a -t arm64-v8a -t x86 -t x86_64 -o <.../cargo-jniLibs> build --release
   ```
   *`cargo-ndk` 会自动设置正确的交叉编译环境变量（如 `ANDROID_NDK_HOME`），将 Rust 代码编译成 `libeasytier.so`，并直接输出到 `cargo-jniLibs` 目录下。*
4. **集成**：在 `easytier/build.gradle.kts` 的 `sourceSets` 中，我们将 `cargo-jniLibs` 目录指定为了 `jniLibs.srcDir`。Android Gradle 插件（AGP）在打包时会自动去这个目录拉取 `.so` 文件，并将其原封不动地打包进最终的 APK 中。
