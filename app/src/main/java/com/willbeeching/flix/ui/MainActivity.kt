package com.willbeeching.flix.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willbeeching.flix.R
import com.willbeeching.flix.plex.PlexAuthManager
import com.willbeeching.flix.ui.theme.GoogleSansFontFamily
import com.willbeeching.flix.ui.theme.PlexScreensaverTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
                ) {
                    MainScreen(
                        isAuthenticated = authManager.isAuthenticated(),
                        selectedServerName = authManager.getSelectedServerName(),
                        selectedLibrariesCount = authManager.getSelectedLibraries().size,
                        onOpenScreensaverSettings = { openScreensaverSettings() },
                        onPreviewScreensaver = { previewScreensaver() },
                        onSelectServer = { openServerSelection() },
                        onSelectLibraries = { openLibrarySelection() }
                    )
                }
            }
        }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
                ) {
                    MainScreen(
                        isAuthenticated = authManager.isAuthenticated(),
                        selectedServerName = authManager.getSelectedServerName(),
                        selectedLibrariesCount = authManager.getSelectedLibraries().size,
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
    onOpenScreensaverSettings: () -> Unit,
    onPreviewScreensaver: () -> Unit,
    onSelectServer: () -> Unit,
    onSelectLibraries: () -> Unit
) {
    var isAuthenticating by remember { mutableStateOf(false) }
    var linkCode by remember { mutableStateOf<String?>(null) }
    var linkUrl by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var authenticated by remember { mutableStateOf(isAuthenticated) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authManager = remember { PlexAuthManager(context) }

    // Colors matching API Settings screen
    val backgroundColor = Color(0xFF0A0A0A)
    val textColor = Color.White
    val subtextColor = Color.White.copy(alpha = 0.6f)
    val buttonColor = Color(0xFF1A1A1A)
    val errorColor = Color(0xFFFF453A)

    if (isAuthenticating) {
            // Authenticating state - Two column layout like API settings
            val dividerColor = Color.White.copy(alpha = 0.1f)

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Left Column: QR Code
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.width(300.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "Link Plex.",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GoogleSansFontFamily,
                            color = textColor,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Scan the QR code.",
                            fontSize = 24.sp,
                            fontFamily = GoogleSansFontFamily,
                            color = textColor.copy(alpha = 0.4f),
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(64.dp))

                        if (linkUrl != null) {
                            val qrBitmap = remember(linkUrl) { generateQRCode(linkUrl!!) }
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .padding(0.dp)
                                ) {
                                    Image(
                                        bitmap = qrBitmap.asImageBitmap(),
                                        contentDescription = "QR Code",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = linkUrl!!.replace("https://", "").replace("http://", ""),
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = subtextColor,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.width(280.dp)
                                )
                            }
                        }
                    }
                }

                // Center Vertical Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(dividerColor)
                )

                // Right Column: PIN Code and Status
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        modifier = Modifier.width(280.dp)
                    ) {
                        // Top Section
                        Column {
                            Text(
                                text = "Enter Code.",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GoogleSansFontFamily,
                                color = textColor,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "On your device.",
                                fontSize = 24.sp,
                                fontFamily = GoogleSansFontFamily,
                                color = textColor.copy(alpha = 0.4f),
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(64.dp))

                            if (linkCode != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = textColor.copy(alpha = 0.05f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = linkCode!!.uppercase(),
                                        fontSize = 48.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White,
                                        letterSpacing = 4.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Status
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = textColor,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Waiting for authorization...",
                                    fontSize = 16.sp,
                                    fontFamily = GoogleSansFontFamily,
                                    color = subtextColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        // Bottom Section: Cancel Button
                        Button(
                            onClick = {
                                isAuthenticating = false
                                linkCode = null
                                linkUrl = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                contentColor = errorColor
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text(
                                "Cancel",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GoogleSansFontFamily
                            )
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = errorMessage!!,
                                fontSize = 14.sp,
                                fontFamily = GoogleSansFontFamily,
                                color = errorColor
                            )
                        }
                    }
                }
            }
    } else {
        if (authenticated) {
            // Authenticated - Two column layout
            val dividerColor = Color.White.copy(alpha = 0.1f)

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Left Column: Logo
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.flix_logo),
                        contentDescription = "Flix Logo",
                        modifier = Modifier
                            .width(200.dp)
                            .height(64.dp)
                    )
                }

                // Center Vertical Divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(dividerColor)
                )

                // Right Column: Buttons
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Column(
                        modifier = Modifier.width(340.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                // Preview Screensaver Button (Primary)
                Button(
                    onClick = onPreviewScreensaver,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier
                        .width(340.dp)
                        .height(48.dp)
                ) {
                    Text(
                        "Preview Screensaver",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Set as Screensaver Button
                Button(
                    onClick = onOpenScreensaverSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = textColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier
                        .width(340.dp)
                        .height(48.dp)
                ) {
                    Text(
                        "Set as Screensaver",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Server Selection Button
                Button(
                    onClick = onSelectServer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = textColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier
                        .width(340.dp)
                        .height(48.dp)
                ) {
                    Text(
                        if (selectedServerName == null) "Select Server" else "Server: $selectedServerName",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Library Selection Button
                Button(
                    onClick = onSelectLibraries,
                    enabled = selectedServerName != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = textColor,
                        disabledContainerColor = buttonColor.copy(alpha = 0.5f),
                        disabledContentColor = textColor.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier
                        .width(340.dp)
                        .height(48.dp)
                ) {
                    val text = if (selectedLibrariesCount > 0) {
                        "Libraries ($selectedLibrariesCount)"
                    } else {
                        "Select Libraries"
                    }
                    Text(
                        text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // API Settings Button
                Button(
                    onClick = {
                        context.startActivity(Intent(context, ApiSettingsActivity::class.java))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = textColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier
                        .width(340.dp)
                        .height(48.dp)
                ) {
                    Text(
                        "API Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sign Out Button
                Button(
                    onClick = {
                        authManager.signOut()
                        authenticated = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = errorColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier
                        .width(340.dp)
                        .height(48.dp)
                ) {
                    Text(
                        "Sign Out",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansFontFamily
                    )
                }
                    }
                }
            }
        } else {
            // Not authenticated - Logo and buttons centered
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.flix_logo),
                    contentDescription = "Flix Logo",
                    modifier = Modifier
                        .width(400.dp)
                        .height(129.dp)
                )

                Spacer(modifier = Modifier.height(64.dp))

                // Not connected - show Connect to Plex and API Settings buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
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
                                    authenticated = true
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
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                    modifier = Modifier
                        .width(280.dp)
                        .height(56.dp)
                ) {
                    Text(
                        "Connect to Plex",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // API Settings Button
                Button(
                    onClick = {
                        context.startActivity(Intent(context, ApiSettingsActivity::class.java))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = textColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                    modifier = Modifier
                        .width(280.dp)
                        .height(56.dp)
                ) {
                    Text(
                        "API Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = GoogleSansFontFamily
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = errorMessage!!,
                        fontSize = 14.sp,
                        fontFamily = GoogleSansFontFamily,
                        color = errorColor
                    )
                }
            }
            }
        }
    }
}

/**
 * Generate a QR code bitmap for the given URL
 */
private fun generateQRCode(url: String, size: Int = 512): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(url, BarcodeFormat.QR_CODE, size, size)

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
