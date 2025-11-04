package com.example.plexscreensaver.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * Activity for selecting which libraries to include in screensaver
 */
class LibrarySelectionActivity : ComponentActivity() {

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
                    LibrarySelectionScreen(
                        authManager = authManager,
                        onSaved = {
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LibrarySelectionScreen(
    authManager: PlexAuthManager,
    onSaved: () -> Unit
) {
    var libraries by remember { mutableStateOf<List<PlexApiClient.LibrarySection>>(emptyList()) }
    var selectedLibraryIds by remember { mutableStateOf(authManager.getSelectedLibraries()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Load libraries on launch
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val authToken = authManager.getAuthToken()
                val selectedServerId = authManager.getSelectedServerId()

                if (authToken == null || selectedServerId == null) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Please select a server first"
                        isLoading = false
                    }
                    return@launch
                }

                val apiClient = PlexApiClient(authToken)

                // Discover servers and find the selected one
                val serversResult = apiClient.discoverServers()
                if (serversResult.isFailure) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Failed to discover servers"
                        isLoading = false
                    }
                    return@launch
                }

                val server = serversResult.getOrNull()
                    ?.firstOrNull { it.clientIdentifier == selectedServerId }

                if (server == null) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Selected server not found"
                        isLoading = false
                    }
                    return@launch
                }

                // Get library sections
                val sectionsResult = apiClient.getLibrarySections(server)

                withContext(Dispatchers.Main) {
                    if (sectionsResult.isSuccess) {
                        libraries = sectionsResult.getOrNull() ?: emptyList()

                        // Auto-select all if none selected
                        if (selectedLibraryIds.isEmpty() && libraries.isNotEmpty()) {
                            selectedLibraryIds = libraries.map { it.id }.toSet()
                        }
                    } else {
                        errorMessage = "Failed to load libraries: ${sectionsResult.exceptionOrNull()?.message}"
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
            .padding(24.dp)
    ) {
        Text(
            text = "Select Libraries",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose which libraries to display in the screensaver",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Loading libraries...")
                    }
                }
            }

            errorMessage != null -> {
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            libraries.isEmpty() -> {
                Text(
                    text = "No libraries found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(libraries) { library ->
                        LibraryItem(
                            library = library,
                            isSelected = selectedLibraryIds.contains(library.id),
                            onToggle = {
                                selectedLibraryIds = if (selectedLibraryIds.contains(library.id)) {
                                    selectedLibraryIds - library.id
                                } else {
                                    selectedLibraryIds + library.id
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        authManager.saveSelectedLibraries(selectedLibraryIds)
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedLibraryIds.isNotEmpty()
                ) {
                    Text("Save Selection (${selectedLibraryIds.size} selected)")
                }
            }
        }
    }
}

@Composable
fun LibraryItem(
    library: PlexApiClient.LibrarySection,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = library.title,
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
                    text = library.type.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}


