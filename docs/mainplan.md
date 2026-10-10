# Role & Project Goal
You are a Principal Android System Engineer & Network Protocol Expert (specializing in Android NDK, Go-Mobile, Rust FFI, and VPN architectures). 

Your objective is to design and implement a unified Android VPN & P2P Mesh client that combines **Sing-box / Mihomo (Clash Meta)** and **EasyTier** into a single application.

---

### Core Problem & Architectural Constraint
* **Android OS Constraint**: Android permits only one active `VpnService` at a time.
* **Solution**: 
  1. The application hosts a **single `VpnService`** managed by the Sing-box / Mihomo core (Go).
  2. The **EasyTier core (Rust)** is compiled into a shared library (`.so`) and executed strictly in **"No-TUN / SOCKS5 Local Server" mode** inside the Android process.
  3. EasyTier listens on a local loopback port (e.g., `127.0.0.1:10888` or Unix Domain Socket).
  4. The Sing-box / Mihomo routing engine intercepts all device traffic, injects an Outbound pointing to EasyTier's local SOCKS5 port, and routes custom P2P subnets (e.g., `10.144.0.0/16`) to EasyTier while routing all other traffic through standard proxies or direct connections.
  5. EasyTier's external sockets must be protected via Android `VpnService.protect(fd)` (or JNI bypass) to prevent routing loops.

---

### Key Technical Requirements & Deliverables

#### 1. Rust Core & JNI FFI Binding (EasyTier)
* Provide a Cargo NDK compilation guide and wrapper for `easytier-core`.
* Expose JNI C-FFI methods:
  * `nativeStartEasyTier(configJson: String): Int`
  * `nativeStopEasyTier(): Int`
  * `nativeGetNodeStatus(): String`
  * `nativeSetProtectSocketCallback(...)` (to prevent VPN routing loops)
* Configure EasyTier to disable TUN device creation and only bind a local SOCKS5 inbound proxy worker.

#### 2. Unified Android Service & Lifecycle Management
* Design a single `ForegroundService` (extending `VpnService`).
* Coordinate startup sequence:
  1. Initialize and start EasyTier background thread on local port `127.0.0.1:10888`.
  2. Protect EasyTier UDP/TCP socket file descriptors (`protect(fd)`).
  3. Generate runtime Mihomo/Sing-box configuration merging user proxy profiles with an auto-injected EasyTier Outbound & Routing Rule:
     * `IP-CIDR / Route Rule`: EasyTier Virtual CIDR -> Route to `127.0.0.1:10888` (SOCKS5 Outbound).
  4. Start Mihomo/Sing-box VPN tunnel and capture all device traffic.
* Handle graceful shutdown, network reconnections, and process crash recovery.

#### 3. Dynamic Configuration & Routing Injection
* Provide a Kotlin configuration builder that dynamically injects EasyTier proxy blocks and routing rules into standard Clash/Sing-box configuration schemas without overwriting user rules.

#### 4. UI & Settings Interface (Jetpack Compose or Kotlin XML)
* **EasyTier Settings**: Network Name, Network Secret, Peer Node URLs, Virtual IP / CIDR, SOCKS5 Port.
* **Mihomo/Sing-box Settings**: Subscription Management, Outbound Proxy Selection, Global Routing Modes (Rule / Global / Direct).
* **Unified Dashboard**: Single Connect/Disconnect toggle, live upload/download speed, EasyTier peer connectivity status list.

---

### Work Plan
Please proceed step-by-step:
1. First, outline the complete Android project directory structure and module division.
2. Provide the Rust JNI wrapper implementation (`easytier-android-ffi`) and `Android.mk` / `CMakeLists.txt` / `Cargo.toml`.
3. Provide the Kotlin `VpnService` lifecycle and socket protection logic.
4. Implement the dynamic JSON/YAML configuration merger and route injector.
5. Provide the main UI and bridge code.