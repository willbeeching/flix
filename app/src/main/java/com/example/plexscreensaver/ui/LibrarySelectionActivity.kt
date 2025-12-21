package com.example.plexscreensaver.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.plexscreensaver.R
import com.example.plexscreensaver.plex.PlexApiClient
import com.example.plexscreensaver.plex.PlexAuthManager
import com.example.plexscreensaver.ui.theme.GoogleSansFontFamily
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
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

    // Colors matching main screen
    val textColor = Color.White
    val subtextColor = Color.White.copy(alpha = 0.4f)
    val errorColor = Color(0xFFFF453A)
    val dividerColor = Color.White.copy(alpha = 0.1f)

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Left column - Logo
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.plexflix_logo),
                contentDescription = "Flix Logo",
                modifier = Modifier.size(width = 200.dp, height = 58.dp)
            )
        }

        // Center Vertical Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(dividerColor)
        )

        // Right column - Library list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 56.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Heading
            Text(
                text = "Library selection.",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GoogleSansFontFamily,
                color = subtextColor,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Pick your media libraries.",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GoogleSansFontFamily,
                color = textColor,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(color = textColor)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Loading libraries...",
                            fontSize = 16.sp,
                            fontFamily = GoogleSansFontFamily,
                            color = subtextColor
                        )
                    }
                }

                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        fontSize = 16.sp,
                        fontFamily = GoogleSansFontFamily,
                        color = errorColor
                    )
                }

                libraries.isEmpty() -> {
                    Text(
                        text = "No libraries found",
                        fontSize = 16.sp,
                        fontFamily = GoogleSansFontFamily,
                        color = subtextColor
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            authManager.saveSelectedLibraries(selectedLibraryIds)
                            onSaved()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = selectedLibraryIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.3f),
                            disabledContentColor = Color.Black.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Save Selection (${selectedLibraryIds.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GoogleSansFontFamily,
                            letterSpacing = 0.5.sp
                        )
                    }
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
    val backgroundColor = if (isSelected) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.White.copy(alpha = 0.05f)
    }
    val textColor = Color.White

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = library.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GoogleSansFontFamily,
                color = textColor,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "✓",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFontFamily,
                        color = Color(0xFF00E676)
                    )
                }
            }
        }
    }
}


