package com.example.mediasearch

import android.app.Application
import com.example.mediasearch.data.AppDatabase
import com.example.mediasearch.data.MediaRepository
import com.example.mediasearch.data.SettingsRepository
import com.example.mediasearch.ssh.SshClient
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/** Holds the app-wide singletons (a tiny manual DI container). */
class MediaSearchApp : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var sshClient: SshClient
        private set
    lateinit var mediaRepository: MediaRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Android ships a trimmed-down "BC" provider; replace it with the full one so
        // modern SSH keys (ed25519, ecdsa) and ciphers work.
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)

        settingsRepository = SettingsRepository(this)
        sshClient = SshClient(settingsRepository)
        mediaRepository = MediaRepository(
            dao = AppDatabase.get(this).mediaDao(),
            ssh = sshClient,
            settings = settingsRepository,
        )
    }
}
