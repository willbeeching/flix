package com.example.plexscreensaver.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.plexscreensaver.plex.PlexApiClient
import com.example.plexscreensaver.plex.PlexAuthManager
import com.example.plexscreensaver.ui.theme.PlexScreensaverTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for selecting a Plex server
 */
class ServerSelectionActivity : ComponentActivity() {

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
                    ServerSelectionScreen(
                        authManager = authManager,
                        onServerSelected = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ServerSelectionScreen(
    authManager: PlexAuthManager,
    onServerSelected: () -> Unit
) {
    var servers by remember { mutableStateOf<List<PlexApiClient.PlexServer>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedServerId by remember { mutableStateOf(authManager.getSelectedServerId()) }

    val scope = rememberCoroutineScope()

    // Load servers on launch
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val authToken = authManager.getAuthToken()
                if (authToken == null) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Not authenticated"
                        isLoading = false
                    }
                    return@launch
                }

                val apiClient = PlexApiClient(authToken)
                val serversResult = apiClient.discoverServers()

                withContext(Dispatchers.Main) {
                    if (serversResult.isSuccess) {
                        servers = serversResult.getOrNull() ?: emptyList()

                        // Auto-select if only one server
                        if (servers.size == 1 && selectedServerId == null) {
                            val server = servers.first()
                            authManager.saveSelectedServer(server.name, server.clientIdentifier)
                            selectedServerId = server.clientIdentifier
                        }
                    } else {
                        errorMessage = "Failed to load servers: ${serversResult.exceptionOrNull()?.message}"
                    }
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Error: ${e.message}"
                    isLoading = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Select Plex Server",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Choose which server to display artwork from",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        when {
            isLoading -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Loading servers...")
            }

            errorMessage != null -> {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            servers.isEmpty() -> {
                Text(
                    text = "No servers found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    servers.forEach { server ->
                        ServerItem(
                            server = server,
                            isSelected = server.clientIdentifier == selectedServerId,
                            onClick = {
                                authManager.saveSelectedServer(server.name, server.clientIdentifier)
                                selectedServerId = server.clientIdentifier
                                onServerSelected()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServerItem(
    server: PlexApiClient.PlexServer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = server.uri,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}


