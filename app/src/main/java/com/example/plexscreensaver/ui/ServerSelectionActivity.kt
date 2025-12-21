package com.example.plexscreensaver.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.plexscreensaver.R
import androidx.lifecycle.lifecycleScope
import com.example.plexscreensaver.plex.PlexApiClient
import com.example.plexscreensaver.plex.PlexAuthManager
import com.example.plexscreensaver.ui.theme.GoogleSansFontFamily
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
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

        // Right column - Server list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 56.dp, vertical = 56.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Heading
            Text(
                text = "Server selection.",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GoogleSansFontFamily,
                color = subtextColor,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Pick you media server.",
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
                            "Loading servers...",
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

                servers.isEmpty() -> {
                    Text(
                        text = "No servers found",
                        fontSize = 16.sp,
                        fontFamily = GoogleSansFontFamily,
                        color = subtextColor
                    )
                }

                else -> {
                    Column(
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
}

@Composable
fun ServerItem(
    server: PlexApiClient.PlexServer,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.White.copy(alpha = 0.05f)
    }
    val textColor = Color.White
    val subtextColor = Color.White.copy(alpha = 0.6f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = server.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GoogleSansFontFamily,
                color = textColor,
                letterSpacing = 0.5.sp
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "✓",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFontFamily,
                        color = Color(0xFF00E676)
                    )
                }
            }
        }
    }
}


