package com.example.mediasearch.ssh

import com.example.mediasearch.data.AppSettings
import com.example.mediasearch.data.ConnectionMode
import com.example.mediasearch.data.SettingsRepository
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** Thrown when the server presents a different host key from the one saved earlier. */
class HostKeyChangedException(message: String) : IOException(message)

/** Thrown when a download is cancelled from the notification. */
class DownloadCancelledException : IOException("Cancelled")

/**
 * Talks to the Pi over SSH, using the Home address, the Away address, or both (Auto).
 * Only folder names are transferred — a single `find` per library.
 */
class SshClient(private val settingsRepository: SettingsRepository) {

    data class Endpoint(val label: String, val host: String, val port: Int) {
        override fun toString() = "$label ($host:$port)"
    }

    /** movieFolders: "Movie Name (2010)"; tvEntries: "Show (2008)" and "Show (2008)/Season 1". */
    data class Listing(val movieFolders: List<String>, val tvEntries: List<String>, val via: Endpoint)

    private data class ExecResult(val exitCode: Int, val output: String, val error: String)

    private fun home(s: AppSettings) = s.host.trim().takeIf { it.isNotEmpty() }?.let { Endpoint("Home", it, s.port) }
    private fun away(s: AppSettings) = s.extHost.trim().takeIf { it.isNotEmpty() }?.let { Endpoint("Away", it, s.extPort) }

    private fun endpointsFor(s: AppSettings): List<Endpoint> = when (s.connectionMode) {
        ConnectionMode.HOME -> listOfNotNull(home(s))
        ConnectionMode.AWAY -> listOfNotNull(away(s))
        ConnectionMode.AUTO -> listOfNotNull(home(s), away(s))
    }

    suspend fun fetchListing(s: AppSettings): Listing = withAnySession(s) { session, ep ->
        Listing(
            movieFolders = listDirs(session, s.moviesPath, depth = 1),
            tvEntries = listDirs(session, s.tvPath, depth = 2),
            via = ep,
        )
    }

    data class RemoteFile(val name: String, val size: Long)

    /** Files directly inside one movie's folder, with sizes. */
    suspend fun listMovieFiles(s: AppSettings, folder: String): List<RemoteFile> = withAnySession(s) { session, _ ->
        val dir = joinPath(s.moviesPath, folder)
        val r = exec(session, "find -H ${quote(dir)} -mindepth 1 -maxdepth 1 -type f -printf '%s\\t%f\\n'")
        if (r.exitCode != 0 && r.output.isBlank()) {
            throw IOException("Couldn't open the folder: ${r.error.trim().ifBlank { "exit code ${r.exitCode}" }}")
        }
        r.output.lineSequence()
            .map { it.trimEnd('\r') }
            .mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null else RemoteFile(line.substring(tab + 1), line.substring(0, tab).toLongOrNull() ?: 0L)
            }
            .filterNot { it.name.startsWith(".") }
            .toList()
    }

    /**
     * Streams one file from a movie folder into [out]. [onProgress] gets the bytes copied so far
     * and returns false to cancel. Uses `cat` over SSH so odd characters in names are handled by
     * normal shell quoting.
     */
    suspend fun downloadMovieFile(
        s: AppSettings,
        folder: String,
        name: String,
        out: OutputStream,
        onProgress: (Long) -> Boolean,
    ): Long = withAnySession(s) { session, _ ->
        val channel = session.openChannel("exec") as ChannelExec
        channel.setCommand("cat -- ${quote(joinPath(joinPath(s.moviesPath, folder), name))}")
        val err = ByteArrayOutputStream()
        channel.setErrStream(err)
        val input = channel.inputStream
        channel.connect(15_000)
        try {
            val buf = ByteArray(256 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                total += n
                if (!onProgress(total)) throw DownloadCancelledException()
            }
            while (!channel.isClosed) Thread.sleep(20)
            if (channel.exitStatus != 0) {
                throw IOException("Couldn't read $name: ${err.toString(Charsets.UTF_8.name()).trim().ifBlank { "exit code ${channel.exitStatus}" }}")
            }
            total
        } finally {
            channel.disconnect()
        }
    }

    /** Connects using the address(es) the mode allows (Auto: Home, then Away) and runs [block]. */
    private suspend fun <T> withAnySession(s: AppSettings, block: (Session, Endpoint) -> T): T =
        withContext(Dispatchers.IO) {
            val targets = endpointsFor(s)
            if (targets.isEmpty()) {
                throw IllegalStateException(
                    when (s.connectionMode) {
                        ConnectionMode.HOME -> "The Home address isn't set. Check Settings."
                        ConnectionMode.AWAY -> "The Away address isn't set. Check Settings."
                        ConnectionMode.AUTO -> "No server address is set. Check Settings."
                    },
                )
            }

            val failures = mutableListOf<String>()
            for ((i, ep) in targets.withIndex()) {
                // In Auto mode, give the Home address only a few seconds before falling back to Away.
                val quick = s.connectionMode == ConnectionMode.AUTO && i < targets.lastIndex
                val session = try {
                    connect(s, ep, timeoutMs = if (quick) 4_000 else 15_000)
                } catch (e: JSchException) {
                    if (isAuthFailure(e)) throw IOException(friendly(e), e) // same Pi either way; no point retrying
                    failures += "$ep: ${friendly(e)}"
                    continue
                }
                try {
                    return@withContext block(session, ep)
                } catch (e: JSchException) {
                    throw IOException(friendly(e), e)
                } finally {
                    session.disconnect()
                }
            }
            throw IOException("Couldn't reach the server.\n" + failures.joinToString("\n"))
        }

    private fun joinPath(base: String, child: String) = base.trim().trimEnd('/') + "/" + child

    /** Tries both addresses (whatever the mode) and checks the folders once. */
    suspend fun testConnection(s: AppSettings): String = withContext(Dispatchers.IO) {
        val all = listOfNotNull(home(s), away(s))
        if (all.isEmpty()) return@withContext "No server address is set."
        if (s.username.isBlank()) return@withContext "Username is required."

        val lines = mutableListOf<String>()
        var homeOk = false
        var awayFailed = false
        var foldersChecked = false
        for (ep in all) {
            val session = try {
                connect(s, ep, timeoutMs = 10_000)
            } catch (e: HostKeyChangedException) {
                lines += "✗ $ep: ${e.message}"
                continue
            } catch (e: JSchException) {
                lines += "✗ $ep: ${friendly(e)}"
                if (ep.label == "Away") awayFailed = true
                continue
            }
            if (ep.label == "Home") homeOk = true
            try {
                lines += "✓ $ep: connected"
                if (!foldersChecked) {
                    foldersChecked = true
                    for ((label, path) in listOf("Movies" to s.moviesPath, "TV" to s.tvPath)) {
                        lines += if (path.isBlank()) {
                            "• $label folder: not set"
                        } else {
                            val r = exec(session, "if [ -d ${quote(path)} ]; then echo yes; else echo no; fi")
                            "• $label folder: " + if (r.output.trim() == "yes") "found" else "NOT found ($path)"
                        }
                    }
                }
            } catch (e: Exception) {
                lines += "  Error checking folders: ${e.message}"
            } finally {
                session.disconnect()
            }
        }
        if (homeOk && awayFailed) {
            lines += ""
            lines += "Tip: many routers can't reach their own public address from inside the network. " +
                "Test Away from mobile data (Wi-Fi off) to be sure."
        }
        lines.joinToString("\n")
    }

    /** Connects and checks the server's identity against the saved key (saving it the first time). */
    private suspend fun connect(s: AppSettings, ep: Endpoint, timeoutMs: Int): Session {
        if (s.username.isBlank()) throw IllegalStateException("Username is required. Check Settings.")
        val key = settingsRepository.readKey()
            ?: throw IllegalStateException("No private key imported yet. Import one in Settings.")

        val jsch = JSch()
        jsch.addIdentity("mediasearch", key, null, s.passphrase.takeIf { it.isNotEmpty() }?.toByteArray())
        val session = jsch.getSession(s.username, ep.host, ep.port)
        // We check the host key ourselves below (trust on first use, then pinned).
        session.setConfig("StrictHostKeyChecking", "no")
        session.setConfig("PreferredAuthentications", "publickey")
        session.connect(timeoutMs)

        val hk = session.hostKey
        val presented = "${hk.type} ${hk.key}"
        val fingerprint = hk.getFingerPrint(jsch)
        val pinned = settingsRepository.pinnedHostKey()
        when {
            pinned == null -> settingsRepository.pinHostKey(presented, fingerprint)
            pinned != presented -> {
                session.disconnect()
                throw HostKeyChangedException(
                    "The server's identity has changed (now $fingerprint). If you rebuilt the Pi or changed its " +
                        "SSH keys, tap 'Forget saved server key' in Settings. Otherwise, don't connect: " +
                        "something may be intercepting the connection.",
                )
            }
        }
        return session
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

    private fun isAuthFailure(e: JSchException): Boolean {
        val msg = e.message.orEmpty()
        return msg.contains("Auth fail", true) || msg.contains("Auth cancel", true) ||
            msg.contains("invalid privatekey", true)
    }

    private fun friendly(e: JSchException): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("Auth fail", true) || msg.contains("Auth cancel", true) ->
                "Login refused. Check the username, and that this key's public half is in ~/.ssh/authorized_keys on the Pi."
            msg.contains("invalid privatekey", true) || msg.contains("decrypt", true) ->
                "Couldn't load the private key. Check the passphrase, or that the key format is supported."
            msg.contains("timeout", true) || msg.contains("timed out", true) -> "timed out"
            e.cause is java.net.UnknownHostException -> "unknown host name"
            e.cause is java.net.ConnectException -> "connection refused"
            e.cause is java.net.NoRouteToHostException -> "no route to host"
            else -> msg.ifBlank { "SSH error" }
        }
    }
}
