package com.example.mediasearch.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mediasearch.MediaSearchApp
import com.example.mediasearch.data.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app as MediaSearchApp
    private val settingsRepo = container.settingsRepository
    private val ssh = container.sshClient

    var form by mutableStateOf(AppSettings())
        private set
    var portText by mutableStateOf("22")
        private set
    var loaded by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var testResult by mutableStateOf<String?>(null)
        private set

    val keyName: StateFlow<String?> =
        settingsRepo.settings.map { it.keyFileName }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Reloads saved values each time the screen opens, discarding unsaved edits. */
    fun load() {
        viewModelScope.launch {
            val s = settingsRepo.settings.first()
            form = s
            portText = s.port.toString()
            loaded = true
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        form = transform(form)
    }

    fun onPortChange(text: String) {
        portText = text.filter(Char::isDigit).take(5)
    }

    private fun current(): AppSettings =
        form.copy(port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: 22)

    fun save() {
        viewModelScope.launch {
            settingsRepo.save(current())
            _messages.send("Settings saved")
        }
    }

    fun importKey(uri: Uri) {
        viewModelScope.launch {
            try {
                val name = settingsRepo.importKey(uri)
                _messages.send("Imported key: $name")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(e.message ?: "Couldn't import key")
            }
        }
    }

    /** Tests the values currently on screen (saved or not). */
    fun testConnection() {
        if (busy) return
        viewModelScope.launch {
            busy = true
            testResult = try {
                ssh.testConnection(current())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun dismissTestResult() {
        testResult = null
    }
}
