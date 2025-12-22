package com.willbeeching.flix.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willbeeching.flix.settings.ApiKeyManager
import com.willbeeching.flix.settings.SettingsServer
import com.willbeeching.flix.ui.theme.GoogleSansFontFamily
import com.willbeeching.flix.ui.theme.PlexScreensaverTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

class ApiSettingsActivity : ComponentActivity() {

    private var settingsServer: SettingsServer? = null
    private val TAG = "ApiSettingsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the settings server
        startServer()

        setContent {
            PlexScreensaverTheme {
                ApiSettingsScreen(
                    settingsServer = settingsServer,
                    onClose = { finish() }
                )
            }
        }
    }

    private fun startServer() {
        try {
            settingsServer = SettingsServer(this, 8888)
            settingsServer?.start()
            Log.d(TAG, "Settings server started on port 8888")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start settings server", e)
            Toast.makeText(this, "Failed to start settings server", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        settingsServer?.stop()
        Log.d(TAG, "Settings server stopped")
    }
}

@Composable
fun ApiSettingsScreen(
    settingsServer: SettingsServer?,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsUrl = settingsServer?.getSettingsUrl()
    val apiKeyManager = remember { ApiKeyManager(context) }

    // Track key status - refresh periodically to catch web UI changes
    var hasFanartKey by remember { mutableStateOf(apiKeyManager.hasFanartApiKey()) }
    var hasTmdbKey by remember { mutableStateOf(apiKeyManager.hasTmdbApiKey()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // Poll for status changes from web UI
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            hasFanartKey = apiKeyManager.hasFanartApiKey()
            hasTmdbKey = apiKeyManager.hasTmdbApiKey()
            refreshTrigger++
        }
    }

    // Apple TV / tvOS Dark Mode Palette
    val backgroundColor = androidx.compose.ui.graphics.Color(0xFF000000)
    val panelColor = androidx.compose.ui.graphics.Color(0xFF1C1C1E) // Standard tvOS card/button fill
    val dividerColor = androidx.compose.ui.graphics.Color(0xFF2C2C2E)
    val textColor = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val subtextColor = androidx.compose.ui.graphics.Color(0xFFEBEBF5).copy(alpha = 0.6f)
    val successColor = androidx.compose.ui.graphics.Color(0xFF32D74B) // tvOS Green
    val errorColor = androidx.compose.ui.graphics.Color(0xFFFF453A) // tvOS Red

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = backgroundColor
    ) {
        // Main container - no padding for true centering
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Left Column: QR Code
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isEmulator = settingsServer?.isEmulator() ?: false
                val port = settingsServer?.getPort() ?: 8888

                // Wrapper column to align text with QR code
                Column(
                    modifier = Modifier.width(300.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    if (isEmulator) {
                        Text(
                            text = "Get started.",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GoogleSansFontFamily,
                            color = textColor.copy(alpha = 0.4f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Use browser to configure.",
                            fontSize = 24.sp,
                            fontFamily = GoogleSansFontFamily,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "Get started.",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GoogleSansFontFamily,
                            color = textColor.copy(alpha = 0.4f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Scan the QR code.",
                            fontSize = 24.sp,
                            fontFamily = GoogleSansFontFamily,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (isEmulator) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("1. Terminal", color = subtextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSansFontFamily)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("adb forward tcp:$port tcp:$port", color = textColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 15.sp)

                            Spacer(modifier = Modifier.height(32.dp))

                            Text("2. Browser", color = subtextColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = GoogleSansFontFamily)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("http://localhost:$port", color = textColor, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 15.sp)
                        }
                    } else {
                        if (settingsUrl != null) {
                            val qrBitmap = remember(settingsUrl) { generateQRCode(settingsUrl) }
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(280.dp)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(androidx.compose.ui.graphics.Color.White)
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
                                    text = settingsUrl.replace("http://", ""),
                                    fontSize = 15.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = subtextColor,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.width(260.dp)
                                )
                            }
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

            // Right Column: Connected Services
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(vertical = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Wrapper column with fixed width
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .fillMaxHeight()
                ) {
                    // Top Section: Heading and integrations
                    Column {
                        Text(
                            text = "Artwork.",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GoogleSansFontFamily,
                            color = textColor.copy(alpha = 0.4f),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Connected APIs.",
                            fontSize = 24.sp,
                            fontFamily = GoogleSansFontFamily,
                            color = textColor,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Fanart.tv Row
                        ApiStatusRow(
                            name = "Fanart.tv",
                            description = "Backgrounds & Logos",
                            isConnected = hasFanartKey,
                            onDisconnect = {
                                apiKeyManager.clearFanartApiKey()
                                hasFanartKey = false
                            },
                            refreshTrigger = refreshTrigger,
                            textColor = textColor,
                            subtextColor = subtextColor,
                            successColor = successColor,
                            errorColor = errorColor,
                            buttonColor = panelColor
                        )

                        Spacer(modifier = Modifier.height(32.dp))
                        Divider(color = dividerColor, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(32.dp))

                        // TMDB Row
                        ApiStatusRow(
                            name = "TMDB",
                            description = "Metadata & Artwork",
                            isConnected = hasTmdbKey,
                            onDisconnect = {
                                apiKeyManager.clearTmdbApiKey()
                                hasTmdbKey = false
                            },
                            refreshTrigger = refreshTrigger,
                            textColor = textColor,
                            subtextColor = subtextColor,
                            successColor = successColor,
                            errorColor = errorColor,
                            buttonColor = panelColor
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Bottom Section: Info Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = textColor.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Scan the QR code on your phone to enter your API keys.",
                            fontSize = 14.sp,
                            fontFamily = GoogleSansFontFamily,
                            color = textColor.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Normal,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApiStatusRow(
    name: String,
    @Suppress("UNUSED_PARAMETER") description: String,
    isConnected: Boolean,
    onDisconnect: () -> Unit,
    @Suppress("UNUSED_PARAMETER") refreshTrigger: Int,
    textColor: androidx.compose.ui.graphics.Color,
    @Suppress("UNUSED_PARAMETER") subtextColor: androidx.compose.ui.graphics.Color,
    @Suppress("UNUSED_PARAMETER") successColor: androidx.compose.ui.graphics.Color,
    errorColor: androidx.compose.ui.graphics.Color,
    buttonColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GoogleSansFontFamily,
            color = textColor,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.width(24.dp))

        if (isConnected) {
            // tvOS Style Button: Filled dark grey with red text
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = errorColor
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    "Disconnect",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GoogleSansFontFamily
                )
            }
        } else {
            Text(
                text = "Not Configured",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = GoogleSansFontFamily,
                color = textColor.copy(alpha = 0.3f)
            )
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
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
