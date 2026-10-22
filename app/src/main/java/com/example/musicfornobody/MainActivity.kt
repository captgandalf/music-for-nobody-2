package com.example.musicfornobody

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MusicViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val api = SonglinkApi.create()
        val prefs = PreferencesManager(applicationContext)
        viewModel = MusicViewModel(api, prefs)

        handleIncomingIntent(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicForNobodyApp(
                        viewModel = viewModel,
                        onCopy = { copyToClipboard(it) },
                        onPaste = { readFromClipboard() },
                        onOpen = { openInBrowserOrApp(it) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                viewModel.translateLink(
                    rawInput = sharedText,
                    onCopyUrl = { copyToClipboard(it) },
                    onOpenUrl = { openInBrowserOrApp(it) }
                )
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Translated Music Link", text))
        Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun readFromClipboard(): String {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
    }

    private fun openInBrowserOrApp(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicForNobodyApp(
    viewModel: MusicViewModel,
    onCopy: (String) -> Unit,
    onPaste: () -> String,
    onOpen: (String) -> Unit
) {
    val settings by viewModel.settingsFlow.collectAsState(initial = UserSettings())
    var showSettingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Music for Nobody", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Conversion Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AssistChip(
                onClick = { showSettingsSheet = true },
                label = {
                    val modeText = if (settings.preferredPlatform == MusicPlatform.ASK_EVERY_TIME) {
                        "Mode: Ask Every Time"
                    } else {
                        "Auto-convert to: ${settings.preferredPlatform.displayName}"
                    }
                    Text(modeText)
                },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
            )

            OutlinedTextField(
                value = viewModel.inputUrl,
                onValueChange = { viewModel.inputUrl = it },
                label = { Text("Paste Spotify, Tidal, YouTube Music link...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        val pasted = onPaste()
                        if (pasted.isNotBlank()) {
                            viewModel.translateLink(pasted, onCopy, onOpen)
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste from Clipboard")
                    }
                }
            )

            Button(
                onClick = { viewModel.translateLink(viewModel.inputUrl, onCopy, onOpen) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Translate Link")
            }

            when (val state = viewModel.uiState) {
                is UiState.Idle -> {
                    Text(
                        "Share a link from any music app to 'Music for Nobody' or paste a link above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = state.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is UiState.Success -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(state.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(state.artist, style = MaterialTheme.typography.bodyMedium)
                            state.autoTriggeredMessage?.let { msg ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = msg,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }

                    Text("Available Services:", style = MaterialTheme.typography.titleSmall)

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.availableLinks.toList()) { (platform, url) ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = platform.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Row {
                                        IconButton(onClick = { onCopy(url) }) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link")
                                        }
                                        IconButton(onClick = { onOpen(url) }) {
                                            Icon(Icons.Default.OpenInNew, contentDescription = "Open Link")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Default Conversion Behavior", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Choose whether to pick a service every time or automatically translate to a specific platform:")

                MusicPlatform.entries.forEach { platform ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = (settings.preferredPlatform == platform),
                            onClick = { viewModel.updatePreferredPlatform(platform) }
                        )
                        Text(text = platform.displayName)
                    }
                }

                if (currentSettingsCheck(settings)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("When Auto-Converting:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    AutoAction.entries.forEach { action ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = (settings.autoAction == action),
                                onClick = { viewModel.updateAutoAction(action) }
                            )
                            Text(text = action.label)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun currentSettingsCheck(settings: UserSettings): Boolean {
    return settings.preferredPlatform != MusicPlatform.ASK_EVERY_TIME
}
