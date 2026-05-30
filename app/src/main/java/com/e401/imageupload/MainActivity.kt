package com.e401.imageupload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZiplineTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp(intent = intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun ZiplineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2563EB),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDBEAFE),
            onPrimaryContainer = Color(0xFF1E40AF),
            secondary = Color(0xFF64748B),
            onSecondary = Color.White,
            background = Color(0xFFF1F5F9),
            onBackground = Color(0xFF0F172A),
            surface = Color.White,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFF8FAFC),
            onSurfaceVariant = Color(0xFF475569),
            outline = Color(0xFFE2E8F0),
            error = Color(0xFFEF4444),
            onError = Color.White
        ),
        content = content
    )
}

enum class Screen(val label: String, val icon: @Composable () -> Unit) {
    Upload("Upload", { Icon(Icons.Default.CloudUpload, contentDescription = "Upload") }),
    History("History", { Icon(Icons.Default.History, contentDescription = "History") }),
    Settings("Settings", { Icon(Icons.Default.Settings, contentDescription = "Settings") })
}

@Composable
fun MainApp(intent: Intent? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zipline_prefs", Context.MODE_PRIVATE) }
    val hasConfig = remember {
        val token = prefs.getString("auth_token", "") ?: ""
        val url = prefs.getString("api_url", "") ?: ""
        token.isNotBlank() && url.isNotBlank()
    }
    var selectedScreen by remember { mutableStateOf(if (hasConfig) Screen.Upload else Screen.Settings) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { screen.icon() },
                        label = { Text(screen.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedScreen) {
                Screen.Upload -> UploadScreen(intent = intent)
                Screen.History -> HistoryScreen()
                Screen.Settings -> SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(intent: Intent? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("zipline_prefs", Context.MODE_PRIVATE) }

    val authToken = prefs.getString("auth_token", "") ?: ""
    val apiBaseUrl = prefs.getString("api_url", "") ?: ""
    val uploadPath = prefs.getString("upload_path", "/api/upload") ?: "/api/upload"

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var resultLink by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasCheckedShare by remember { mutableStateOf(false) }

    LaunchedEffect(intent, hasCheckedShare) {
        if (!hasCheckedShare && intent?.action == Intent.ACTION_SEND) {
            val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                imageUri = uri
                resultLink = null
                errorMessage = null
            }
            hasCheckedShare = true
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it
            resultLink = null
            errorMessage = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Zipline Upload",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Upload images",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Image",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (imageUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUri)
                                    .size(Size.ORIGINAL)
                                    .scale(Scale.FILL_BOUNDS)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Selected image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                                FilledTonalIconButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.Image, contentDescription = "Change image", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Tap to select an image", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("or share from another app", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (authToken.isBlank()) {
                        errorMessage = "Please set an auth token in Settings"
                        return@Button
                    }
                    if (apiBaseUrl.isBlank()) {
                        errorMessage = "Please set the server URL in Settings"
                        return@Button
                    }
                    if (imageUri == null) {
                        errorMessage = "Please select an image"
                        return@Button
                    }
                    isUploading = true
                    errorMessage = null
                    resultLink = null

                    scope.launch {
                        try {
                            val result = uploadImage(context, imageUri!!, authToken, apiBaseUrl, uploadPath)
                            resultLink = result
                            val historyJson = prefs.getString("history", "[]") ?: "[]"
                            val arr = JSONArray(historyJson)
                            arr.put(JSONObject().apply {
                                put("url", result)
                                put("time", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()))
                            })
                            prefs.edit().putString("history", arr.toString()).apply()
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Upload failed"
                        } finally {
                            isUploading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isUploading && imageUri != null && authToken.isNotBlank() && apiBaseUrl.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
                )
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Uploading...", fontSize = 15.sp)
                } else {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            errorMessage?.let { error ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f))) {
                    Text(text = error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth().padding(14.dp), fontSize = 13.sp)
                }
            }

            resultLink?.let { link ->
                ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFF0FDF4))) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF16A34A))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Upload complete", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF16A34A))
                        }
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7))) {
                            Text(text = link, color = Color(0xFF166534), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(12.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Image URL", link))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy", fontSize = 13.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, link)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share link"))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("zipline_prefs", Context.MODE_PRIVATE) }
    var history by remember {
        mutableStateOf(
            try {
                JSONArray(prefs.getString("history", "[]") ?: "[]")
            } catch (_: Exception) {
                JSONArray()
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            text = "History",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 48.dp, bottom = 20.dp)
        )

        if (history.length() == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No uploads yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                items(count = history.length()) { index ->
                    val i = history.length() - 1 - index
                    val entry = history.getJSONObject(i)
                    val url = entry.optString("url", "")
                    val time = entry.optString("time", "")

                    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(url, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (time.isNotBlank()) {
                                    Text(time, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(
                                Icons.Default.ContentCopy, contentDescription = "Copy",
                                modifier = Modifier.size(18.dp).clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Image URL", url))
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("zipline_prefs", Context.MODE_PRIVATE) }
    var authToken by remember { mutableStateOf(prefs.getString("auth_token", "") ?: "") }
    var showToken by remember { mutableStateOf(false) }
    var apiUrl by remember { mutableStateOf(prefs.getString("api_url", "") ?: "") }
    var uploadPath by remember { mutableStateOf(prefs.getString("upload_path", "/api/upload") ?: "/api/upload") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 48.dp, bottom = 24.dp)
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (authToken.isNotBlank()) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auth Token", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                }
                OutlinedTextField(
                    value = authToken,
                    onValueChange = { newValue ->
                        authToken = newValue
                        prefs.edit().putString("auth_token", newValue).apply()
                    },
                    label = { Text("Bearer Token") },
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showToken) "Hide" else "Show",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Server URL", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = apiUrl,
                    onValueChange = { newValue ->
                        apiUrl = newValue
                        prefs.edit().putString("api_url", newValue).apply()
                    },
                    label = { Text("Zipline URL") },
                    placeholder = { Text("https://your-zipline-instance.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Upload Endpoint", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = uploadPath,
                    onValueChange = { newValue ->
                        uploadPath = newValue
                        prefs.edit().putString("upload_path", newValue).apply()
                    },
                    label = { Text("Path") },
                    placeholder = { Text("/api/upload") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Full endpoint: ${apiUrl.trimEnd('/')}${uploadPath}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (apiUrl.isBlank() || authToken.isBlank()) {
                            testResult = "Set server URL and auth token first"
                            return@OutlinedButton
                        }
                        isTesting = true
                        testResult = null
                        scope.launch {
                            try {
                                val testUrl = apiUrl.trimEnd('/') + uploadPath.trimEnd('/')
                                val request = Request.Builder()
                                    .url(testUrl)
                                    .addHeader("Authorization", authToken)
                                    .head()
                                    .build()
                                val client = OkHttpClient.Builder()
                                    .connectTimeout(10, TimeUnit.SECONDS)
                                    .readTimeout(10, TimeUnit.SECONDS)
                                    .build()
                                val response = client.newCall(request).execute()
                                testResult = if (response.isSuccessful || response.code == 405) {
                                    "Connection successful (${response.code})"
                                } else {
                                    "Failed: ${response.code} ${response.message}"
                                }
                            } catch (e: Exception) {
                                testResult = "Error: ${e.message ?: "Connection failed"}"
                            } finally {
                                isTesting = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isTesting && apiUrl.isNotBlank() && authToken.isNotBlank()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing...", fontSize = 13.sp)
                    } else {
                        Text("Test Connection", fontSize = 13.sp)
                    }
                }
                testResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(result, fontSize = 12.sp, color = if (result.startsWith("Connection") || result.startsWith("Success")) Color(0xFF16A34A) else MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                prefs.edit().remove("history").apply()
                Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Clear Upload History")
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

suspend fun uploadImage(context: Context, uri: Uri, token: String, apiBaseUrl: String, uploadPath: String = "/api/upload"): String {
    return withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val extension = when {
            mimeType.contains("png") -> "png"
            mimeType.contains("gif") -> "gif"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }

        val tempFile = File(context.cacheDir, "upload_image.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        val uploadUrl = apiBaseUrl.trimEnd('/') + "/" + uploadPath.trimStart('/')

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", tempFile.name, tempFile.asRequestBody(mimeType.toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url(uploadUrl)
            .addHeader("Authorization", token)
            .post(requestBody)
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        tempFile.delete()

        if (!response.isSuccessful) {
            throw Exception("Upload failed: ${response.code} - $responseBody")
        }

        try {
            val json = JSONObject(responseBody)
            if (json.has("files")) {
                val files = json.getJSONArray("files")
                if (files.length() > 0) {
                    val file = files.getJSONObject(0)
                    file.optString("url").ifEmpty { null }
                        ?: file.optString("link").ifEmpty { null }
                        ?: throw Exception("No URL in files array: $responseBody")
                } else {
                    throw Exception("Empty files array: $responseBody")
                }
            } else {
                json.optString("url").ifEmpty { null }
                    ?: json.optString("link").ifEmpty { null }
                    ?: json.optString("data").ifEmpty { null }
                    ?: json.optString("result").ifEmpty { null }
                    ?: throw Exception("No URL in response: $responseBody")
            }
        } catch (e: org.json.JSONException) {
            responseBody
        }
    }
}
