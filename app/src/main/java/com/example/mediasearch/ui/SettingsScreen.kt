package com.example.mediasearch.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextAlign
import com.example.mediasearch.BuildConfig
import com.example.mediasearch.data.ConnectionMode
import com.example.mediasearch.ui.theme.Brand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val saved by vm.saved.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importKey(uri)
    }

    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            GradientHeader(
                title = "Settings",
                navigation = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Brand.OnHeader)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        // Include the on-screen keyboard so content scrolls above it instead of being covered.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        val form = vm.form

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard("🏠", "Home network", "Used inside the building", Color(0xFF0F766E)) {
                OutlinedTextField(
                    value = form.host,
                    onValueChange = { v -> vm.update { it.copy(host = v) } },
                    label = { Text("Local IP address") },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.portText,
                    onValueChange = vm::onPortChange,
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsCard("🌍", "Away", "Used from the internet, through your router", Color(0xFFB45309)) {
                OutlinedTextField(
                    value = form.extHost,
                    onValueChange = { v -> vm.update { it.copy(extHost = v) } },
                    label = { Text("Public IP or hostname") },
                    placeholder = { Text("myhouse.duckdns.org") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.extPortText,
                    onValueChange = vm::onExtPortChange,
                    label = { Text("Port") },
                    supportingText = { Text("The port your router forwards to the Pi's port 22") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SettingsCard("🔀", "Which address to use", null, Color(0xFF4F46E5)) {
                val modes = listOf(ConnectionMode.AUTO to "Auto", ConnectionMode.HOME to "Home", ConnectionMode.AWAY to "Away")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = form.connectionMode == mode,
                            onClick = { vm.update { it.copy(connectionMode = mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                        ) { Text(label) }
                    }
                }
                Text(
                    when (form.connectionMode) {
                        ConnectionMode.AUTO -> "Tries Home first. If it doesn't answer within a few seconds, uses Away."
                        ConnectionMode.HOME -> "Only connects to the Home address."
                        ConnectionMode.AWAY -> "Only connects to the Away address."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard("🔑", "Login", null, Color(0xFF7C3AED)) {
                OutlinedTextField(
                    value = form.username,
                    onValueChange = { v -> vm.update { it.copy(username = v) } },
                    label = { Text("Username") },
                    placeholder = { Text("pi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        saved.keyFileName?.let { "Key: $it" } ?: "No key imported yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (saved.keyFileName == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { keyPicker.launch(arrayOf("*/*")) }) {
                        Text(if (saved.keyFileName == null) "Import…" else "Replace…")
                    }
                }
                OutlinedTextField(
                    value = form.passphrase,
                    onValueChange = { v -> vm.update { it.copy(passphrase = v) } },
                    label = { Text("Key passphrase") },
                    supportingText = { Text("Leave blank if the key has no passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Saved server key", style = MaterialTheme.typography.labelLarge)
                Text(
                    saved.hostKeyFingerprint ?: "None yet. It's saved automatically the first time you connect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (saved.hostKeyFingerprint != null) {
                    TextButton(onClick = vm::forgetHostKey, contentPadding = ButtonDefaults.TextButtonContentPadding) {
                        Text("Forget saved server key")
                    }
                }
            }

            SettingsCard("📁", "Folders on the Pi", null, Color(0xFFDB2777)) {
                OutlinedTextField(
                    value = form.moviesPath,
                    onValueChange = { v -> vm.update { it.copy(moviesPath = v) } },
                    label = { Text("Movies folder") },
                    placeholder = { Text("/mnt/media/Movies") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.tvPath,
                    onValueChange = { v -> vm.update { it.copy(tvPath = v) } },
                    label = { Text("TV folder") },
                    placeholder = { Text("/mnt/media/TV") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = vm::testConnection, enabled = !vm.busy, modifier = Modifier.weight(1f)) {
                    if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("Test connection")
                }
                Button(
                    onClick = vm::save,
                    enabled = !vm.busy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            }
            Text(
                "Media Search v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Spacer(Modifier.size(16.dp))
        }
    }

    vm.testResult?.let { result ->
        AlertDialog(
            onDismissRequest = vm::dismissTestResult,
            confirmButton = { TextButton(onClick = vm::dismissTestResult) { Text("OK") } },
            title = { Text("Connection test") },
            text = { Text(result) },
        )
    }
}

@Composable
private fun SettingsCard(
    emoji: String,
    title: String,
    subtitle: String?,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Colored accent strip along the top of each card.
        Box(Modifier.fillMaxWidth().height(5.dp).background(accent))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            content()
        }
    }
}
