package top.xing2387.mihotier

import android.net.Uri
import androidx.core.content.FileProvider

/**
 * Custom FileProvider that provides accurate "text/plain" MIME type for
 * YAML, TOML, and config files so that system text viewers (such as HtmlViewer)
 * and third-party text editors correctly identify and display them.
 */
class AppFileProvider : FileProvider() {
    override fun getType(uri: Uri): String? {
        val path = uri.path?.lowercase() ?: ""
        return when {
            path.endsWith(".yaml") || path.endsWith(".yml") -> "text/plain"
            path.endsWith(".toml") -> "text/plain"
            path.endsWith(".json") -> "application/json"
            path.endsWith(".conf") || path.endsWith(".config") || path.endsWith(".ini") -> "text/plain"
            path.endsWith(".txt") || path.endsWith(".log") -> "text/plain"
            else -> {
                val superType = super.getType(uri)
                if (superType == "application/octet-stream") "text/plain" else superType
            }
        }
    }
}
