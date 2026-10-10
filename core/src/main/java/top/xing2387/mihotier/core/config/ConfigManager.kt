package top.xing2387.mihotier.core.config

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ConfigManager {
    private const val CONFIG_DIR_NAME = "config"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private fun getConfigDir(context: Context): File {
        val dir = File(context.filesDir, CONFIG_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get the raw config yaml string for the active profile.
     */
    fun getConfig(context: Context): String {
        val activeProfile = ProfileRepository.getActiveProfile()
        if (activeProfile != null) {
            val file = File(getConfigDir(context), "${activeProfile.id}.yaml")
            if (file.exists()) {
                return file.readText()
            }
        }
        
        // Dummy fallback if no active profile
        return """
            mixed-port: 7890
            allow-lan: true
            mode: rule
            log-level: info
            dns:
              enable: true
              listen: 0.0.0.0:1053
              enhanced-mode: fake-ip
              fake-ip-range: 198.18.0.1/16
              nameserver:
                - 114.114.114.114
                - 8.8.8.8
            rules:
              - MATCH,DIRECT
        """.trimIndent()
    }

    /**
     * Download config from a subscription URL and save it for a specific profile ID.
     */
    suspend fun downloadAndSaveConfig(context: Context, urlString: String, profileId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "Mihotier/1.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val configText = response.body?.string() ?: ""
                        saveConfigText(context, configText, profileId)
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception("HTTP Error: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Copy a local file URI to the config directory for a specific profile ID.
     */
    suspend fun copyConfigFromUri(context: Context, uri: android.net.Uri, profileId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val configText = inputStream.bufferedReader().use { it.readText() }
                    saveConfigText(context, configText, profileId)
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Unable to open file URI"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun saveConfigText(context: Context, configText: String, profileId: String) {
        val file = File(getConfigDir(context), "${profileId}.yaml")
        file.writeText(configText)
    }
}
