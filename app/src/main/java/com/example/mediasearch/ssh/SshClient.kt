package com.example.mediasearch.ssh

import com.example.mediasearch.data.AppSettings
import com.example.mediasearch.data.SettingsRepository
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Talks to the Pi over SSH. Only folder names are transferred — a single `find`
 * per library — so a refresh takes a second or two even for large collections.
 */
class SshClient(private val settingsRepository: SettingsRepository) {

    /** movieFolders: "Movie Name (2010)"; tvEntries: "Show (2008)" and "Show (2008)/Season 1". */
    data class Listing(val movieFolders: List<String>, val tvEntries: List<String>)

    private data class ExecResult(val exitCode: Int, val output: String, val error: String)

    suspend fun fetchListing(s: AppSettings): Listing = withSession(s) { session ->
        Listing(
            movieFolders = listDirs(session, s.moviesPath, depth = 1),
            tvEntries = listDirs(session, s.tvPath, depth = 2),
        )
    }

    suspend fun testConnection(s: AppSettings): String = withSession(s) { session ->
        val lines = mutableListOf("Connected to ${s.host} as ${s.username}.")
        for ((label, path) in listOf("Movies" to s.moviesPath, "TV" to s.tvPath)) {
            if (path.isBlank()) {
                lines += "$label folder: not set"
                continue
            }
            val r = exec(session, "if [ -d ${quote(path)} ]; then echo yes; else echo no; fi")
            lines += "$label folder: " + if (r.output.trim() == "yes") "found" else "NOT found ($path)"
        }
        lines.joinToString("\n")
    }

    private suspend fun <T> withSession(s: AppSettings, block: (Session) -> T): T =
        withContext(Dispatchers.IO) {
            if (s.host.isBlank() || s.username.isBlank()) {
                throw IllegalStateException("Server address and username are required. Check Settings.")
            }
            val key = settingsRepository.readKey()
                ?: throw IllegalStateException("No private key imported yet. Import one in Settings.")

            val jsch = JSch()
            val session: Session
            try {
                jsch.addIdentity("mediasearch", key, null, s.passphrase.takeIf { it.isNotEmpty() }?.toByteArray())
                session = jsch.getSession(s.username, s.host, s.port)
            } catch (e: JSchException) {
                throw IOException(friendly(e), e)
            }
            // Home-network convenience: accept the Pi's host key without a known_hosts file.
            session.setConfig("StrictHostKeyChecking", "no")
            session.setConfig("PreferredAuthentications", "publickey")
            try {
                session.connect(15_000)
                block(session)
            } catch (e: JSchException) {
                throw IOException(friendly(e), e)
            } finally {
                session.disconnect()
            }
        }

    private fun listDirs(session: Session, path: String, depth: Int): List<String> {
        if (path.isBlank()) return emptyList()
        // -H follows the start path if it is a symlink; %P prints the path relative to it.
        val cmd = "find -H ${quote(path)} -mindepth 1 -maxdepth $depth -type d -printf '%P\\n'"
        val r = exec(session, cmd)
        if (r.exitCode != 0 && r.output.isBlank()) {
            throw IOException("Couldn't list $path: ${r.error.trim().ifBlank { "exit code ${r.exitCode}" }}")
        }
        return r.output.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            // skip hidden/system folders like .Trash or @eaDir
            .filterNot { p -> p.split('/').any { it.startsWith(".") || it.startsWith("@") } }
            .toList()
    }

    private fun exec(session: Session, command: String): ExecResult {
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand(command)
        val err = ByteArrayOutputStream()
        channel.setErrStream(err)
        val input = channel.inputStream
        channel.connect(15_000)
        val out = input.bufferedReader(Charsets.UTF_8).readText()
        while (!channel.isClosed) Thread.sleep(20)
        val code = channel.exitStatus
        channel.disconnect()
        return ExecResult(code, out, err.toString(Charsets.UTF_8.name()))
    }

    /** Shell-quotes a path; supports a leading ~ for the home directory. */
    private fun quote(raw: String): String {
        val p = raw.trim().trimEnd('/').ifEmpty { "/" }
        fun sq(s: String) = "'" + s.replace("'", "'\\''") + "'"
        return when {
            p == "~" -> "\"\$HOME\""
            p.startsWith("~/") -> "\"\$HOME\"/" + sq(p.removePrefix("~/"))
            else -> sq(p)
        }
    }

    private fun friendly(e: JSchException): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Auth fail", true) || msg.contains("Auth cancel", true) ->
                "Authentication failed. Check the username, and that this key's public half is in ~/.ssh/authorized_keys on the Pi."
            msg.contains("invalid privatekey", true) || msg.contains("decrypt", true) ->
                "Couldn't load the private key. Check the passphrase, or that the key format is supported."
            msg.contains("timeout", true) || msg.contains("timed out", true) ->
                "Connection timed out. Is the Pi on and are you on the same network?"
            e.cause is java.net.UnknownHostException -> "Unknown host. Check the IP address in Settings."
            e.cause is java.net.ConnectException -> "Connection refused. Check the IP address and port."
            else -> "SSH error: $msg"
        }
    }
}
