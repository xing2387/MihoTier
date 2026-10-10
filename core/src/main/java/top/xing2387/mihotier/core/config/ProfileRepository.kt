package top.xing2387.mihotier.core.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Profile(
    val id: String,
    val name: String,
    val url: String,
    val autoUpdate: String, // "Disabled", "Every Startup"
    val localFilePath: String? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isActive: Boolean = false
)

object ProfileRepository {
    private const val PROFILES_FILE = "profiles.json"
    private val _profiles = MutableStateFlow<List<Profile>>(emptyList())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    fun init(context: Context) {
        val file = File(context.filesDir, PROFILES_FILE)
        if (file.exists()) {
            try {
                val jsonText = file.readText()
                val jsonArray = JSONArray(jsonText)
                val list = mutableListOf<Profile>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        Profile(
                            id = obj.optString("id"),
                            name = obj.optString("name", "New Profile"),
                            url = obj.optString("url", ""),
                            autoUpdate = obj.optString("autoUpdate", "Disabled"),
                            localFilePath = if (obj.has("localFilePath")) obj.optString("localFilePath") else null,
                            lastUpdated = obj.optLong("lastUpdated", 0L),
                            isActive = obj.optBoolean("isActive", false)
                        )
                    )
                }
                _profiles.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            // Default active profile?
        }
    }

    fun saveProfile(context: Context, profile: Profile) {
        val currentList = _profiles.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            currentList[index] = profile
        } else {
            currentList.add(profile)
        }
        
        // If this one is active, deactivate others
        if (profile.isActive) {
            for (i in currentList.indices) {
                if (currentList[i].id != profile.id) {
                    currentList[i] = currentList[i].copy(isActive = false)
                }
            }
        }
        
        _profiles.value = currentList
        persist(context, currentList)
    }

    fun deleteProfile(context: Context, id: String) {
        val currentList = _profiles.value.toMutableList()
        currentList.removeAll { it.id == id }
        _profiles.value = currentList
        persist(context, currentList)
    }

    fun setActiveProfile(context: Context, id: String) {
        val currentList = _profiles.value.map {
            it.copy(isActive = it.id == id)
        }
        _profiles.value = currentList
        persist(context, currentList)
    }

    fun getActiveProfile(): Profile? {
        return _profiles.value.find { it.isActive }
    }

    fun getProfile(id: String): Profile? {
        return _profiles.value.find { it.id == id }
    }

    private fun persist(context: Context, list: List<Profile>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { p ->
                val obj = JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("url", p.url)
                obj.put("autoUpdate", p.autoUpdate)
                p.localFilePath?.let { obj.put("localFilePath", it) }
                obj.put("lastUpdated", p.lastUpdated)
                obj.put("isActive", p.isActive)
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, PROFILES_FILE)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
