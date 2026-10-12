package top.xing2387.mihotier.easytier

/**
 * Interface to pass socket file descriptors back to the VpnService for protection.
 */
interface SocketProtectCallback {
    fun protect(fd: Int): Boolean
}

/**
 * JNI Wrapper for EasyTier Rust Core (`easytier-core`).
 */
object EasyTierNative {
    init {
        System.loadLibrary("easytier")
    }

    /**
     * Validates an EasyTier TOML configuration string via `easytier_core::config::toml::TomlConfig`.
     * @return empty string if valid, or error description if invalid.
     */
    external fun nativeValidateConfig(configToml: String): String

    /**
     * Starts EasyTier node with the given TOML configuration.
     * @param configToml The EasyTier TOML configuration string.
     * @return 0 for success, non-zero for error.
     */
    external fun nativeStartEasyTier(configToml: String): Int

    /**
     * Stops the EasyTier node.
     * @return 0 for success, non-zero for error.
     */
    external fun nativeStopEasyTier(): Int

    /**
     * Gets the last error message from native EasyTier operations.
     */
    external fun nativeGetLastError(): String

    /**
     * Gets the current status of the EasyTier node (JSON format).
     */
    external fun nativeGetNodeStatus(): String

    /**
     * Sets the callback to protect sockets created by EasyTier.
     */
    external fun nativeSetProtectSocketCallback(callback: SocketProtectCallback)
}
