#![allow(non_snake_case)]

use easytier::instance::factory::{
    native_instance_manager_with_runtime, NativeInstanceManager,
};
use easytier_core::config::toml::{ConfigLoader, TomlConfig};
use easytier_core::instance::manager::ConfigFileControl;
use easytier_proto::common::NatType;
use jni::objects::{JClass, JObject, JString, JValue};
use jni::sys::{jint, jstring};
use jni::JNIEnv;
use lazy_static::lazy_static;
use once_cell::sync::Lazy;
use serde_json::json;
use std::collections::HashSet;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};
use tokio::runtime::{Builder, Runtime};

static LOGGER_INIT: Lazy<()> = Lazy::new(|| {
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(log::LevelFilter::Info)
            .with_tag("EasyTier-JNI"),
    );
});

fn init_logger() {
    Lazy::force(&LOGGER_INIT);
}

struct EngineContext {
    runtime: Runtime,
    manager: Arc<NativeInstanceManager>,
}

impl EngineContext {
    fn new() -> Self {
        let runtime = Builder::new_multi_thread()
            .worker_threads(4)
            .enable_all()
            .build()
            .expect("Failed to build tokio runtime for EasyTier");
        let manager = Arc::new(native_instance_manager_with_runtime(
            runtime.handle().clone(),
        ));
        Self { runtime, manager }
    }
}

static ENGINE_CTX: Lazy<EngineContext> = Lazy::new(EngineContext::new);

struct ActiveConfigMeta {
    config: TomlConfig,
    started_at_sec: u64,
}

lazy_static! {
    static ref PROTECT_CALLBACK: Mutex<Option<jni::objects::GlobalRef>> = Mutex::new(None);
    static ref ACTIVE_META: Mutex<Option<ActiveConfigMeta>> = Mutex::new(None);
    static ref LAST_ERROR: Mutex<String> = Mutex::new(String::new());
}

fn now_unix_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

fn format_bytes(bytes: u64) -> String {
    if bytes < 1024 {
        format!("{} B", bytes)
    } else if bytes < 1024 * 1024 {
        format!("{:.1} KB", bytes as f64 / 1024.0)
    } else if bytes < 1024 * 1024 * 1024 {
        format!("{:.2} MB", bytes as f64 / (1024.0 * 1024.0))
    } else {
        format!("{:.2} GB", bytes as f64 / (1024.0 * 1024.0 * 1024.0))
    }
}

fn format_proto_ipv4_inet(inet: Option<easytier_proto::common::Ipv4Inet>) -> String {
    let Some(inet) = inet else {
        return String::new();
    };
    let Some(addr) = inet.address else {
        return String::new();
    };
    let ip = std::net::Ipv4Addr::from(addr.addr);
    format!("{}/{}", ip, inet.network_length)
}

fn format_proto_ipv4_only(inet: Option<easytier_proto::common::Ipv4Inet>) -> String {
    let Some(inet) = inet else {
        return String::new();
    };
    let Some(addr) = inet.address else {
        return String::new();
    };
    std::net::Ipv4Addr::from(addr.addr).to_string()
}

// -----------------------------------------------------------------------------
// FFI Implementations
// -----------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeValidateConfig<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    config_toml: JString<'local>,
) -> jstring {
    init_logger();
    let toml_str: String = match env.get_string(&config_toml) {
        Ok(s) => s.into(),
        Err(e) => {
            return env
                .new_string(format!("Invalid JNI string: {}", e))
                .expect("Failed to create Java string")
                .into_raw();
        }
    };

    let validation_msg = match TomlConfig::new_from_str(&toml_str) {
        Ok(_) => String::new(),
        Err(err) => format!("{:#}", err),
    };

    env.new_string(validation_msg)
        .expect("Failed to create Java string")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeStartEasyTier<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    config_toml: JString<'local>,
) -> jint {
    init_logger();
    let toml_str: String = match env.get_string(&config_toml) {
        Ok(s) => s.into(),
        Err(e) => {
            let msg = format!("Failed to read config string: {}", e);
            log::error!("{}", msg);
            *LAST_ERROR.lock().unwrap() = msg;
            return -1;
        }
    };

    log::info!("Starting EasyTier instance with TOML:\n{}", toml_str);

    let parsed = match TomlConfig::new_from_str(&toml_str) {
        Ok(cfg) => {
            let mut flags = cfg.get_flags();
            flags.no_tun = false;
            cfg.set_flags(flags);
            cfg
        }
        Err(err) => {
            let msg = format!("{:#}", err);
            log::error!("TomlConfig parse error: {}", msg);
            *LAST_ERROR.lock().unwrap() = msg;
            return -2;
        }
    };

    let ctx = Lazy::force(&ENGINE_CTX);

    // Stop any previously running instances first
    if let Err(e) = ctx
        .runtime
        .block_on(ctx.manager.retain_network_instances(&[]))
    {
        log::warn!("Failed to clear old instances before start: {:#}", e);
    }

    match ctx
        .manager
        .run_network_instance(parsed.clone(), ConfigFileControl::STATIC_CONFIG)
    {
        Ok(instance_id) => {
            log::info!(
                "EasyTier network instance launched successfully! instance_id={}",
                instance_id
            );
            *ACTIVE_META.lock().unwrap() = Some(ActiveConfigMeta {
                config: parsed,
                started_at_sec: now_unix_secs(),
            });
            *LAST_ERROR.lock().unwrap() = String::new();
            0
        }
        Err(err) => {
            let msg = format!("{:#}", err);
            log::error!("Failed to run_network_instance: {}", msg);
            *LAST_ERROR.lock().unwrap() = msg;
            -3
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeSetTunFd<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    tun_fd: jint,
) -> jint {
    init_logger();
    let ctx = Lazy::force(&ENGINE_CTX);
    let ids = ctx.manager.instance_ids();
    let Some(instance_id) = ids.into_iter().next() else {
        log::warn!("nativeSetTunFd({}) called, but no running instance found", tun_fd);
        return -1;
    };
    match ctx.manager.attach_tun_fd(instance_id, tun_fd) {
        Ok(()) => {
            log::info!(
                "Attached mobile TUN fd={} to EasyTier instance {}",
                tun_fd,
                instance_id
            );
            0
        }
        Err(err) => {
            let msg = format!("{:#}", err);
            log::error!("Failed to attach TUN fd={} to EasyTier: {}", tun_fd, msg);
            *LAST_ERROR.lock().unwrap() = msg;
            -2
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeStopEasyTier<
    'local,
>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jint {
    init_logger();
    log::info!("Stopping all EasyTier network instances...");
    let ctx = Lazy::force(&ENGINE_CTX);
    if let Err(e) = ctx
        .runtime
        .block_on(ctx.manager.retain_network_instances(&[]))
    {
        log::error!("Error stopping EasyTier instances: {:#}", e);
    }
    *ACTIVE_META.lock().unwrap() = None;
    0
}

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeGetLastError<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let err = LAST_ERROR.lock().unwrap().clone();
    env.new_string(err)
        .expect("Couldn't create java string!")
        .into_raw()
}

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeGetNodeStatus<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    init_logger();
    let ctx = Lazy::force(&ENGINE_CTX);
    let collected = match ctx.manager.collect_network_infos_sync() {
        Ok(map) => map,
        Err(e) => {
            log::error!("collect_network_infos_sync error: {:#}", e);
            let fallback = json!({ "running": false, "error": format!("{:#}", e) }).to_string();
            return env
                .new_string(fallback)
                .expect("Couldn't create java string!")
                .into_raw();
        }
    };

    let Some((instance_id, info)) = collected.into_iter().next() else {
        let stopped_json = json!({ "running": false }).to_string();
        return env
            .new_string(stopped_json)
            .expect("Couldn't create java string!")
            .into_raw();
    };

    let meta_guard = ACTIVE_META.lock().unwrap();
    let (network_name, socks5_portal, dhcp, started_at_sec) = if let Some(meta) = meta_guard.as_ref()
    {
        (
            meta.config.get_network_identity().network_name,
            meta.config
                .get_socks5_portal()
                .map(|u| u.to_string())
                .unwrap_or_default(),
            meta.config.get_dhcp(),
            meta.started_at_sec,
        )
    } else {
        ("easytier".to_string(), String::new(), false, now_unix_secs())
    };
    drop(meta_guard);

    let uptime_sec = now_unix_secs().saturating_sub(started_at_sec);

    let my_node = info.my_node_info.clone().unwrap_or_default();
    let my_ipv4 = format_proto_ipv4_inet(my_node.virtual_ipv4);
    let my_hostname = my_node.hostname.clone();
    let my_peer_id = my_node.peer_id;
    let nat_type_str = my_node
        .stun_info
        .as_ref()
        .and_then(|s| NatType::try_from(s.udp_nat_type).ok())
        .map(|t| format!("{:?}", t))
        .unwrap_or_else(|| "Unknown".to_string());

    let mut seen_peer_ids = HashSet::new();
    let mut peer_list = Vec::new();
    let mut route_list = Vec::new();

    for pair in &info.peer_route_pairs {
        let Some(route) = &pair.route else {
            continue;
        };
        seen_peer_ids.insert(route.peer_id);

        let peer_ipv4 = format_proto_ipv4_only(route.ipv4_addr);
        let is_public_server = route
            .feature_flag
            .as_ref()
            .map(|f| f.is_public_server)
            .unwrap_or(false);
        let is_relayed = route.cost > 1 || pair.peer.is_none();

        let tunnel_proto = pair
            .get_conn_protos()
            .map(|protos| protos.join("/").to_uppercase())
            .unwrap_or_else(|| {
                if route.cost <= 1 {
                    "DIRECT".to_string()
                } else {
                    format!("RELAY ({}跳)", route.cost)
                }
            });

        let latency_ms = pair
            .get_latency_ms()
            .map(|ms| ms.round() as i64)
            .unwrap_or_else(|| (route.path_latency as i64).max(0));

        let loss_rate = pair
            .get_loss_rate()
            .map(|r| format!("{:.1}%", r * 100.0))
            .unwrap_or_else(|| "0.0%".to_string());

        let rx_bytes = format_bytes(pair.get_rx_bytes().unwrap_or(0));
        let tx_bytes = format_bytes(pair.get_tx_bytes().unwrap_or(0));
        let peer_nat = pair.get_udp_nat_type();

        peer_list.push(json!({
            "peer_id": route.peer_id.to_string(),
            "hostname": route.hostname,
            "ipv4": if peer_ipv4.is_empty() {
                if is_public_server { "公共服务器".to_string() } else { "未分配 IP".to_string() }
            } else {
                peer_ipv4.clone()
            },
            "tunnel_proto": tunnel_proto,
            "nat_type": peer_nat,
            "latency_ms": latency_ms,
            "loss_rate": loss_rate,
            "rx_bytes": rx_bytes,
            "tx_bytes": tx_bytes,
            "cost": route.cost,
            "is_relay": is_relayed || is_public_server,
            "version": route.version,
        }));

        route_list.push(json!({
            "peer_id": route.peer_id.to_string(),
            "hostname": route.hostname,
            "ipv4": peer_ipv4,
            "next_hop": route.next_hop_peer_id.to_string(),
            "cost": route.cost,
            "proxy_cidrs": route.proxy_cidrs,
        }));
    }

    // Also include any directly connected peers not yet in OSPF routes
    for peer in &info.peers {
        if seen_peer_ids.contains(&peer.peer_id) || peer.peer_id == my_peer_id {
            continue;
        }
        let mut protos = Vec::new();
        let mut min_lat_us = u64::MAX;
        let mut rx_total = 0u64;
        let mut tx_total = 0u64;
        let mut remote_url = String::new();

        for conn in &peer.conns {
            if let Some(t) = &conn.tunnel {
                let t_type = t.tunnel_type.to_uppercase();
                if !t_type.is_empty() && !protos.contains(&t_type) {
                    protos.push(t_type);
                }
                if remote_url.is_empty() {
                    if let Some(r) = &t.remote_addr {
                        remote_url = r.url.clone();
                    }
                }
            }
            if let Some(st) = &conn.stats {
                min_lat_us = min_lat_us.min(st.latency_us);
                rx_total += st.rx_bytes;
                tx_total += st.tx_bytes;
            }
        }

        let lat_ms = if min_lat_us == u64::MAX {
            0
        } else {
            (min_lat_us / 1000) as i64
        };

        peer_list.push(json!({
            "peer_id": peer.peer_id.to_string(),
            "hostname": if remote_url.is_empty() {
                format!("peer-{}", peer.peer_id)
            } else {
                format!("peer-{} ({})", peer.peer_id, remote_url)
            },
            "ipv4": "握手中...",
            "tunnel_proto": if protos.is_empty() { "DIRECT".to_string() } else { protos.join("/") },
            "nat_type": "Unknown",
            "latency_ms": lat_ms,
            "loss_rate": "0.0%",
            "rx_bytes": format_bytes(rx_total),
            "tx_bytes": format_bytes(tx_total),
            "cost": 1,
            "is_relay": false,
            "version": "",
        }));
    }

    let status_json = json!({
        "running": info.running,
        "instance_id": instance_id.to_string(),
        "my_peer_id": my_peer_id.to_string(),
        "hostname": my_hostname,
        "ipv4": my_ipv4,
        "dhcp": dhcp,
        "network_name": network_name,
        "socks5_portal": socks5_portal,
        "nat_type": nat_type_str,
        "uptime_sec": uptime_sec,
        "error_msg": info.error_msg.unwrap_or_default(),
        "peers": peer_list,
        "routes": route_list,
        "events": info.events,
    })
    .to_string();

    env.new_string(status_json)
        .expect("Couldn't create java string!")
        .into_raw()
}

// -----------------------------------------------------------------------------
// Callbacks to Java
// -----------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeSetProtectSocketCallback<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    callback_obj: JObject<'local>,
) {
    let global_ref = env
        .new_global_ref(callback_obj)
        .expect("Couldn't create global ref for callback");

    let mut cb = PROTECT_CALLBACK.lock().unwrap();
    *cb = Some(global_ref);
}

/// Helper function to call the Java VpnService.protect(fd) method from Rust
pub fn protect_socket(env: &mut JNIEnv, fd: i32) -> bool {
    let cb_guard = PROTECT_CALLBACK.lock().unwrap();
    if let Some(global_ref) = cb_guard.as_ref() {
        let obj = global_ref.as_obj();
        let result = env.call_method(obj, "protect", "(I)Z", &[JValue::Int(fd)]);

        match result {
            Ok(ret) => {
                if let Ok(b) = ret.z() {
                    return b;
                }
            }
            Err(e) => {
                log::error!("Error calling protect callback: {:?}", e);
            }
        }
    }
    false
}
