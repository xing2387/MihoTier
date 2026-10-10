#![allow(non_snake_case)]

use jni::JNIEnv;
use jni::objects::{JClass, JString, JObject, JValue};
use jni::sys::{jint, jstring};
use std::sync::Mutex;
use lazy_static::lazy_static;

// Mock Global state to hold tokio runtime or easytier instance if needed
lazy_static! {
    static ref PROTECT_CALLBACK: Mutex<Option<jni::objects::GlobalRef>> = Mutex::new(None);
    // static ref JVM: Mutex<Option<jni::JavaVM>> = Mutex::new(None);
}

// -----------------------------------------------------------------------------
// FFI Implementations
// -----------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeStartEasyTier<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    config_json: JString<'local>,
) -> jint {
    let config: String = env.get_string(&config_json)
        .expect("Couldn't get java string!")
        .into();

    println!("Starting EasyTier with config: {}", config);

    // TODO: In a real implementation:
    // 1. Parse config (Network name, secret, peers, etc.)
    // 2. Initialize easytier-core in No-TUN mode
    // 3. Start SOCKS5 inbound proxy worker on 127.0.0.1:10888
    // 4. Register custom socket creation hook in tokio/easytier to call `nativeProtectSocket`
    
    0 // Return 0 for success
}

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeStopEasyTier<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jint {
    println!("Stopping EasyTier");
    
    // TODO: Stop tokio tasks and clean up
    
    0
}

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeGetNodeStatus<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    let status = r#"{"status": "running", "peers": []}"#;
    let output = env.new_string(status)
        .expect("Couldn't create java string!");
    
    output.into_raw()
}

// -----------------------------------------------------------------------------
// Callbacks to Java
// -----------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_top_xing2387_mihotier_easytier_EasyTierNative_nativeSetProtectSocketCallback<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    callback_obj: JObject<'local>,
) {
    let global_ref = env.new_global_ref(callback_obj)
        .expect("Couldn't create global ref for callback");
    
    let mut cb = PROTECT_CALLBACK.lock().unwrap();
    *cb = Some(global_ref);
}

/// Helper function to call the Java VpnService.protect(fd) method from Rust
pub fn protect_socket(env: &mut JNIEnv, fd: i32) -> bool {
    let cb_guard = PROTECT_CALLBACK.lock().unwrap();
    if let Some(global_ref) = cb_guard.as_ref() {
        let obj = global_ref.as_obj();
        
        // Assuming the Java callback interface has a method: boolean protect(int fd)
        let result = env.call_method(obj, "protect", "(I)Z", &[JValue::Int(fd)]);
        
        match result {
            Ok(ret) => {
                if let Ok(b) = ret.z() {
                    return b;
                }
            }
            Err(e) => {
                println!("Error calling protect callback: {:?}", e);
            }
        }
    }
    false
}
