在 **Clash Meta for Android (CMFA)** 中，`Network` 设置页主要控制 Android 系统层的 **VpnService 构建参数** 以及 **Mihomo/Clash 内核的 TUN 虚拟网卡行为**。

以下是这些选项在 CMFA 中的具体作用与底层原理：

---

### 1. Route System Traffic（路由系统流量）
* **底层原理**：控制是否在 Android `VpnService` 中添加默认路由（`0.0.0.0/0` 和 `::/0`）。
* **实际作用**：
    * **开启（默认）**：全局 TUN 模式，手机上所有 App 的网络流量都会自动被捕获并送入 Clash 内核。
    * **关闭**：Clash 仅作为一个后台运行的本地 HTTP/SOCKS 代理服务（监听如 `7890` / `8800` 等端口），不建立全局接管。此时需要在其他 App 或 Wi-Fi 手动配置代理端口才能使用。

---

### 2. Bypass Private Network（绕过局域网地址）
* **底层原理**：在构建 VPN 路由表时，排除私有网段（`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` 以及组播/链路本地地址）。
* **实际作用**：
    * **开启（推荐）**：访问路由器管理后台、群晖/NAS、局域网打印机、投屏设备等，流量直接走物理网卡，不进入代理内核，保证局域网高速传输且不影响投屏与本地互联。
    * **关闭**：局域网流量也会进入 Clash TUN，若规则配置不当可能导致无法访问路由器或局域网设备。

---

### 3. DNS Hijacking（DNS 劫持）
* **底层原理**：强制将 TUN 虚拟网卡上所有发往 `53` 端口的 UDP/TCP DNS 请求重定向到 Clash 内置的 DNS 服务。
* **实际作用**：
    * **开启（核心项）**：防止运营商 DNS 污染，防止 DNS 泄露，使 Clash 能够接管系统的域名解析，配合 Fake-IP 或分流规则实现精确的域名规则匹配。
    * **关闭**：App 的 DNS 解析将直接走系统的物理网络 DNS，容易导致国外域名被污染阻断，或导致基于域名的代理规则失效。

---

### 4. Allow Bypass（允许应用绕过 VPN）
* **底层原理**：调用 Android VpnService 的 `builder.allowBypass()` API。
* **实际作用**：
    * **开启**：允许调用了 Android 专有网络绑定接口（如 `bindProcessToNetwork` 或 `bindSocket`）的 App 绕过当前 VPN 直连物理网卡（常见于部分运营商免流 App、热点网络、系统专属下载器等）。
    * **关闭**：严格强制所有应用的所有连接都必须经过 VPN，不允许任何应用通过底层 socket 绑定自行绕过。

---

### 5. Allow IPv6（允许 IPv6 流量）
* **底层原理**：控制是否在 VPN 接口上分配 IPv6 地址（如 `fdfe:dcba:9876::1/126`）并添加 IPv6 默认路由（`::/0`）。
* **实际作用**：
    * **开启**：代理环境支持 IPv6 时开启，允许 IPv6 数据包通过 TUN 并被 Clash 转发。
    * **关闭**：屏蔽或直接丢弃系统的 IPv6 出站请求，强制系统和 App 回退使用 IPv4。通常在机场节点不支持 IPv6 或为了避免 IPv6 地址泄露真实 IP 时关闭。

---

### 6. System Proxy（系统代理）
* **底层原理**：在建立 VpnService 时调用 `builder.setHttpProxy(...)`（Android 10+ 特性），向 Android 系统注册全局 HTTP 代理信息。
* **实际作用**：
    * 某些特殊的国内 App 或特定的系统组件（如 WebView、部分网银等）不会老老实实走 TUN 网卡，但会读取系统 HTTP 代理设置；开启后可双重保证这些应用也能正确走代理。

---

### 7. Stack Mode（协议栈模式）
* **底层原理**：TUN 虚拟网卡收到的是原始二层/三层 IP 数据包，需要一个**用户态 TCP/IP 协议栈**将其还原为 TCP/UDP 数据流交给代理内核。
* **可选模式**：
    1. **System Stack（系统/LWIP）**：轻量级协议栈，内存占用极低，适合低配设备，但在高并发或乱序包较多时性能不如 gVisor。
    2. **gVisor（推荐）**：Google 开源的用户态网络栈（Netstack），重传和乱序抗压能力强，吞吐量大、稳定性高，但内存占用略高。
    3. **Mixed（混合模式）**：TCP 走 gVisor/系统栈，UDP 特殊分流处理，兼顾低延迟和高稳定性。

---

### 8. Access Control Mode & Packages（应用分流控制）
* **底层原理**：利用 Android 系统的 `addAllowedApplication(pkg)` 和 `addDisallowedApplication(pkg)` 接口进行应用级过滤。
* **实际模式**：
    * **Allow all apps（全部应用）**：所有 App 都走代理。
    * **Disallow selected apps（黑名单 / 绕过模式）**：选中的应用（如微信、淘宝、国内银行、手游等）完全不走 VPN，直接走物理网络直连；其余应用走代理。
    * **Allow selected apps（白名单 / 仅代理模式）**：只有选中的 App（如浏览器、Twitter、Telegram 等）才会走 VPN，其余所有应用直接走本地网络。