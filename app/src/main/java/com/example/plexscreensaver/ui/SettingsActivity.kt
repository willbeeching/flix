package com.example.plexscreensaver.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.plexscreensaver.plex.PlexAuthManager
import com.example.plexscreensaver.ui.theme.PlexScreensaverTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings activity for Plex Link authentication
 */
class SettingsActivity : ComponentActivity() {

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
                    SettingsScreen(
                        authManager = authManager,
                        onSignOut = {
                            authManager.signOut()
                            finish()
                        },
                        onStartAuth = { onAuthRequested() }
                    )
                }
            }
        }
    }

    private fun onAuthRequested() {
        // This will be handled by the composable
    }
}

@Composable
fun SettingsScreen(
    authManager: PlexAuthManager,
    onSignOut: () -> Unit,
    onStartAuth: () -> Unit
) {
    var isAuthenticated by remember { mutableStateOf(authManager.isAuthenticated()) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var linkCode by remember { mutableStateOf<String?>(null) }
    var linkUrl by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Plex Screensaver Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isAuthenticated) {
            // Authenticated state
            Text(
                text = "✓ Connected to Plex",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onSignOut()
                    isAuthenticated = false
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Sign Out")
            }
        } else if (isAuthenticating) {
            // Authenticating state
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Link Your Plex Account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Visit the URL below and enter this code:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (linkUrl != null) {
                        Text(
                            text = linkUrl!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (linkCode != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = linkCode!!.uppercase(),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    CircularProgressIndicator()

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Waiting for authorization...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            isAuthenticating = false
                            linkCode = null
                            linkUrl = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Cancel")
                    }
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            // Not authenticated state
            Text(
                text = "Connect your Plex account to enable the screensaver",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    isAuthenticating = true
                    errorMessage = null

                    scope.launch {
                        try {
                            // Run network operations on IO dispatcher
                            val result = withContext(Dispatchers.IO) {
                                // Request PIN
                                val pinResult = authManager.requestPin()

                                if (pinResult.isFailure) {
                                    return@withContext Result.failure<String>(
                                        pinResult.exceptionOrNull() ?: Exception("Unknown error")
                                    )
                                }

                                val linkResult = pinResult.getOrNull()!!

                                // Update UI on main thread
                                withContext(Dispatchers.Main) {
                                    linkCode = linkResult.code
                                    linkUrl = linkResult.linkUrl
                                }

                                // Poll for authorization
                                authManager.pollForAuth(
                                    pinId = linkResult.pinId,
                                    timeoutSeconds = 300
                                )
                            }

                            if (result.isSuccess) {
                                isAuthenticated = true
                                isAuthenticating = false
                                linkCode = null
                                linkUrl = null
                            } else {
                                errorMessage = "Failed to authenticate: ${result.exceptionOrNull()?.message}"
                                isAuthenticating = false
                            }
                        } catch (e: Exception) {
                            errorMessage = "Error: ${e.message}"
                            isAuthenticating = false
                        }
                    }
                }
            ) {
                Text("Connect to Plex")
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

