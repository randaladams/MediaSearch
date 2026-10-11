package com.example.mediasearch.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

/** AUTO tries the Home address first, then Away. */
enum class ConnectionMode { AUTO, HOME, AWAY }

data class AppSettings(
    // Home = inside the building (same network)
    val host: String = "",
    val port: Int = 22,
    // Away = from the internet, through the router's port forward
    val extHost: String = "",
    val extPort: Int = 6425,
    val connectionMode: ConnectionMode = ConnectionMode.AUTO,
    val username: String = "",
    val moviesPath: String = "",
    val tvPath: String = "",
    val passphrase: String = "",
    val keyFileName: String? = null,
    val hostKeyFingerprint: String? = null,
    val lastRefresh: Long = 0L,
    val lastRefreshVia: String? = null,
)

/** Settings are stored locally with DataStore; the private key is copied into app-private storage. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val EXT_HOST = stringPreferencesKey("ext_host")
        val EXT_PORT = intPreferencesKey("ext_port")
        val MODE = stringPreferencesKey("connection_mode")
        val USER = stringPreferencesKey("username")
        val MOVIES = stringPreferencesKey("movies_path")
        val TV = stringPreferencesKey("tv_path")
        val PASSPHRASE = stringPreferencesKey("passphrase")
        val KEY_NAME = stringPreferencesKey("key_name")
        val HOST_KEY = stringPreferencesKey("host_key")
        val HOST_KEY_FP = stringPreferencesKey("host_key_fingerprint")
        val LAST_REFRESH = longPreferencesKey("last_refresh")
        val LAST_REFRESH_VIA = stringPreferencesKey("last_refresh_via")
    }

    private val keyFile: File get() = File(context.filesDir, "ssh_private_key")

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            host = p[Keys.HOST] ?: "",
            port = p[Keys.PORT] ?: 22,
            extHost = p[Keys.EXT_HOST] ?: "",
            extPort = p[Keys.EXT_PORT] ?: 6425,
            connectionMode = p[Keys.MODE]?.let { runCatching { ConnectionMode.valueOf(it) }.getOrNull() }
                ?: ConnectionMode.AUTO,
            username = p[Keys.USER] ?: "",
            moviesPath = p[Keys.MOVIES] ?: "",
            tvPath = p[Keys.TV] ?: "",
            passphrase = p[Keys.PASSPHRASE] ?: "",
            keyFileName = p[Keys.KEY_NAME],
            hostKeyFingerprint = p[Keys.HOST_KEY_FP],
            lastRefresh = p[Keys.LAST_REFRESH] ?: 0L,
            lastRefreshVia = p[Keys.LAST_REFRESH_VIA],
        )
    }

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { p ->
            p[Keys.HOST] = s.host.trim()
            p[Keys.PORT] = s.port
            p[Keys.EXT_HOST] = s.extHost.trim()
            p[Keys.EXT_PORT] = s.extPort
            p[Keys.MODE] = s.connectionMode.name
            p[Keys.USER] = s.username.trim()
            p[Keys.MOVIES] = s.moviesPath.trim()
            p[Keys.TV] = s.tvPath.trim()
            p[Keys.PASSPHRASE] = s.passphrase
        }
    }

    suspend fun setLastRefresh(time: Long, via: String) {
        context.dataStore.edit {
            it[Keys.LAST_REFRESH] = time
            it[Keys.LAST_REFRESH_VIA] = via
        }
    }

    // ---- Server identity (host key) pinning ----
    // Home and Away reach the same Pi, so one pinned key covers both.

    suspend fun pinnedHostKey(): String? = context.dataStore.data.first()[Keys.HOST_KEY]

    suspend fun pinHostKey(key: String, fingerprint: String) {
        context.dataStore.edit {
            it[Keys.HOST_KEY] = key
            it[Keys.HOST_KEY_FP] = fingerprint
        }
    }

    suspend fun forgetHostKey() {
        context.dataStore.edit {
            it.remove(Keys.HOST_KEY)
            it.remove(Keys.HOST_KEY_FP)
        }
    }

    // ---- Private key ----

    /** Copies the chosen key file into app-private storage. Returns its display name. */
    suspend fun importKey(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Couldn't read that file.")
        val text = bytes.toString(Charsets.UTF_8)
        require(text.contains("PRIVATE KEY") || text.startsWith("PuTTY-User-Key-File")) {
            "That doesn't look like a private key. Pick the file without the .pub extension " +
                "(it starts with '-----BEGIN ... PRIVATE KEY-----')."
        }
        keyFile.writeBytes(bytes)
        val name = displayName(uri) ?: "private key"
        context.dataStore.edit { it[Keys.KEY_NAME] = name }
        name
    }

    fun readKey(): ByteArray? = keyFile.takeIf { it.exists() }?.readBytes()

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
