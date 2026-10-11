package com.example.mediasearch.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
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
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

private const val BACKUP_FORMAT = "mediasearch-backup"

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

    // ---- Backup / restore ----
    // A small JSON file the user saves wherever they like (e.g. Downloads) and restores after a reinstall.
    // The library itself isn't included: one sync rebuilds it.

    /** Writes the saved settings to [uri]. The SSH key and passphrase are included only if asked. */
    suspend fun exportBackup(uri: Uri, includeKey: Boolean) = withContext(Dispatchers.IO) {
        val s = settings.first()
        val json = JSONObject().apply {
            put("format", BACKUP_FORMAT)
            put("formatVersion", 1)
            put("host", s.host)
            put("port", s.port)
            put("extHost", s.extHost)
            put("extPort", s.extPort)
            put("connectionMode", s.connectionMode.name)
            put("username", s.username)
            put("moviesPath", s.moviesPath)
            put("tvPath", s.tvPath)
            pinnedHostKey()?.let {
                put("hostKey", it)
                put("hostKeyFingerprint", s.hostKeyFingerprint ?: "")
            }
            if (includeKey) {
                readKey()?.let {
                    put("privateKey", Base64.encodeToString(it, Base64.NO_WRAP))
                    put("keyFileName", s.keyFileName ?: "private key")
                }
                put("passphrase", s.passphrase)
            }
        }
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toString(2).toByteArray()) }
            ?: throw IOException("Couldn't write the backup file.")
    }

    /** Restores settings from a backup. Returns true if it also contained the SSH key. */
    suspend fun importBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IOException("Couldn't read that file.")
        val j = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IOException("That file isn't a Media Search backup.")
        }
        if (j.optString("format") != BACKUP_FORMAT) throw IOException("That file isn't a Media Search backup.")

        val current = settings.first()
        save(
            current.copy(
                host = j.optString("host", current.host),
                port = j.optInt("port", current.port),
                extHost = j.optString("extHost", current.extHost),
                extPort = j.optInt("extPort", current.extPort),
                connectionMode = runCatching { ConnectionMode.valueOf(j.optString("connectionMode")) }
                    .getOrDefault(current.connectionMode),
                username = j.optString("username", current.username),
                moviesPath = j.optString("moviesPath", current.moviesPath),
                tvPath = j.optString("tvPath", current.tvPath),
                passphrase = if (j.has("passphrase")) j.getString("passphrase") else current.passphrase,
            ),
        )

        val hostKey = j.optString("hostKey")
        if (hostKey.isNotEmpty()) pinHostKey(hostKey, j.optString("hostKeyFingerprint"))

        val privateKey = j.optString("privateKey")
        if (privateKey.isNotEmpty()) {
            keyFile.writeBytes(Base64.decode(privateKey, Base64.NO_WRAP))
            context.dataStore.edit { it[Keys.KEY_NAME] = j.optString("keyFileName", "private key") }
            true
        } else {
            false
        }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}
