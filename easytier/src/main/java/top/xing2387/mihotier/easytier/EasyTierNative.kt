package top.xing2387.mihotier.easytier

/**
 * Interface to pass socket file descriptors back to the VpnService for protection.
 */
interface SocketProtectCallback {
    fun protect(fd: Int): Boolean
}

/**
 * JNI Wrapper for EasyTier Rust Core.
 */
object EasyTierNative {
    init {
        System.loadLibrary("easytier")
    }

    /**
     * Starts EasyTier node.
     * @param configJson The configuration JSON string (network name, secret, peers, etc).
     * @return 0 for success, non-zero for error.
     */
    external fun nativeStartEasyTier(configJson: String): Int

    /**
     * Stops the EasyTier node.
     * @return 0 for success, non-zero for error.
     */
    external fun nativeStopEasyTier(): Int

    /**
     * Gets the current status of the EasyTier node (JSON format).
     */
    external fun nativeGetNodeStatus(): String

    /**
     * Sets the callback to protect sockets created by EasyTier.
     */
    external fun nativeSetProtectSocketCallback(callback: SocketProtectCallback)
}
