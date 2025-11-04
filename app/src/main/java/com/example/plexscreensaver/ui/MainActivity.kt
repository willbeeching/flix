package com.example.plexscreensaver.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.plexscreensaver.plex.PlexAuthManager
import com.example.plexscreensaver.ui.theme.PlexScreensaverTheme

/**
 * Main activity - landing screen for the app
 */
class MainActivity : ComponentActivity() {

    private lateinit var authManager: PlexAuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager = PlexAuthManager(this)

        setContent {
            PlexScreensaverTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        isAuthenticated = authManager.isAuthenticated(),
                        selectedServerName = authManager.getSelectedServerName(),
                        selectedLibrariesCount = authManager.getSelectedLibraries().size,
                        onOpenSettings = { openSettings() },
                        onOpenScreensaverSettings = { openScreensaverSettings() },
                        onPreviewScreensaver = { previewScreensaver() },
                        onSelectServer = { openServerSelection() },
                        onSelectLibraries = { openLibrarySelection() }
                    )
                }
            }
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openScreensaverSettings() {
        try {
            // Open Android's screensaver settings
            startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
        } catch (e: Exception) {
            // Emulator may not have these settings, show a message instead
            android.widget.Toast.makeText(
                this,
                "Screensaver settings not available on this device. Use the Preview button instead!",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun previewScreensaver() {
        startActivity(Intent(this, PreviewActivity::class.java))
    }

    private fun openServerSelection() {
        startActivity(Intent(this, ServerSelectionActivity::class.java))
    }

    private fun openLibrarySelection() {
        startActivity(Intent(this, LibrarySelectionActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        // Refresh the UI when returning from settings
        setContent {
            PlexScreensaverTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        isAuthenticated = authManager.isAuthenticated(),
                        selectedServerName = authManager.getSelectedServerName(),
                        selectedLibrariesCount = authManager.getSelectedLibraries().size,
                        onOpenSettings = { openSettings() },
                        onOpenScreensaverSettings = { openScreensaverSettings() },
                        onPreviewScreensaver = { previewScreensaver() },
                        onSelectServer = { openServerSelection() },
                        onSelectLibraries = { openLibrarySelection() }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    isAuthenticated: Boolean,
    selectedServerName: String?,
    selectedLibrariesCount: Int,
    onOpenSettings: () -> Unit,
    onOpenScreensaverSettings: () -> Unit,
    onPreviewScreensaver: () -> Unit,
    onSelectServer: () -> Unit,
    onSelectLibraries: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "🎬",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Plex Screensaver",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Display beautiful artwork from your Plex library",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (isAuthenticated) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✓ Connected to Plex",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Setup Instructions:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "1. Tap 'Set as Screensaver' below",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "2. Select 'Plex Screensaver' from the list",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "3. Your Plex artwork will display when the screensaver activates",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onPreviewScreensaver,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("▶ Preview Screensaver")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenScreensaverSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("Set as Screensaver")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSelectServer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                colors = if (selectedServerName == null) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                }
            ) {
                Text(if (selectedServerName == null) "Select Server" else "Change Server")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSelectLibraries,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                enabled = selectedServerName != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                val text = if (selectedLibrariesCount > 0) {
                    "Libraries ($selectedLibrariesCount selected)"
                } else {
                    "Select Libraries"
                }
                Text(text)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("Plex Settings")
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Not Connected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Connect your Plex account to get started",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("Connect to Plex")
            }
        }
    }
}

