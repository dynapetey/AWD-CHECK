package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.VinScan
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AwdScreen(viewModel: AwdViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedScan by viewModel.selectedScan.collectAsState()
    val userApiKey by viewModel.userGeminiApiKey.collectAsState()

    var showCameraScanner by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var forceShowSetupScreen by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
    val context = LocalContext.current

    val isKeyConfigured = userApiKey.trim().isNotEmpty()

    // Gallery Picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    viewModel.processImage(inputStream, context.contentResolver.getType(it) ?: "image/jpeg")
                }
            } catch (e: Exception) {
                Log.e("AwdScreen", "Error opening gallery file stream", e)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            // 0. API Key Onboarding / Setup Screen (shown on first install or when explicitly opened)
            !isKeyConfigured || forceShowSetupScreen -> {
                GeminiApiKeySetupScreen(
                    viewModel = viewModel,
                    onKeySaved = {
                        forceShowSetupScreen = false
                    },
                    canSkip = isKeyConfigured,
                    onSkip = {
                        forceShowSetupScreen = false
                    }
                )
            }
            // 1. Loading State (corresponds to provider.isLoading in Flutter)
            uiState is ScanUiState.ExtractingVin || uiState is ScanUiState.DecodingVin -> {
                LoadingScreen()
            }
            // 2. Active scan or selected vehicle details (corresponds to provider.vehicleData != null)
            selectedScan != null -> {
                VehicleInfoScreen(
                    scan = selectedScan!!,
                    onBack = { viewModel.selectScan(null) }
                )
            }
            // 3. Main/Home dashboard (corresponds to _buildMainScreen in Flutter)
            else -> {
                val errorMsg = if (uiState is ScanUiState.Error) (uiState as ScanUiState.Error).message else null
                HomeScreenContent(
                    errorMessage = errorMsg,
                    onScanClick = {
                        if (cameraPermissionState.status.isGranted) {
                            showCameraScanner = true
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    },
                    onSettingsClick = {
                        showApiKeyDialog = true
                    },
                    onKeySetupClick = {
                        forceShowSetupScreen = true
                    }
                )
            }
        }

        // Camera Scanner full-screen view
        if (showCameraScanner) {
            CameraScannerScreen(
                onDismiss = { showCameraScanner = false },
                onImageCaptured = { file ->
                    showCameraScanner = false
                    val stream = FileInputStream(file)
                    viewModel.processImage(stream)
                },
                onManualVinEntered = { vin ->
                    showCameraScanner = false
                    viewModel.decodeManualVin(vin)
                },
                onGalleryClick = {
                    showCameraScanner = false
                    galleryLauncher.launch("image/*")
                }
            )
        }

        // API Configuration Settings popup
        if (showApiKeyDialog) {
            ApiKeySettingsDialog(
                viewModel = viewModel,
                onDismiss = { showApiKeyDialog = false },
                onOpenKeySetup = {
                    showApiKeyDialog = false
                    forceShowSetupScreen = true
                }
            )
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1A1A), Color(0xFF000000))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFFE50914),
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "SCANNING VIN...",
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 2.sp
                )
            )
        }
    }
}

@Composable
fun HomeScreenContent(
    errorMessage: String?,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onKeySetupClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1A1A), Color(0xFF000000))
                )
            )
    ) {
        // Top right actions
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onKeySetupClick,
                modifier = Modifier.testTag("key_setup_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = "Gemini Key Setup",
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Configure API",
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DirectionsCar,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "AWD CHECK",
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(40.dp)
                        .background(Color(0xFFE50914))
                )
            }

            // Error Display & Scan Button Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            style = TextStyle(
                                color = Color(0xFFE50914),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Large circular scanning button
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .border(
                                width = 2.dp,
                                color = Color(0xFFE50914).copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable(onClick = onScanClick)
                            .testTag("scan_camera_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Camera",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "SCAN VIN",
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                            )
                        }
                    }
                }
            }

            // Footer Section
            Text(
                text = "Scan vehicle VIN to check AWD status",
                style = TextStyle(
                    color = Color.Gray,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
fun VehicleInfoScreen(
    scan: VinScan,
    onBack: () -> Unit
) {
    val isAwdOr4x4 = scan.driveType.lowercase().contains("all") ||
            scan.driveType.lowercase().contains("awd") ||
            scan.driveType.lowercase().contains("4x4") ||
            scan.driveType.lowercase().contains("4wd") ||
            scan.driveType.lowercase().contains("four-wheel") ||
            scan.driveType.lowercase().contains("four wheel")

    val statusColor = if (isAwdOr4x4) Color(0xFF69F0AE) else Color(0xFFE50914)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1A1A1A), Color(0xFF000000))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header back button + status section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                // Back Arrow Button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(top = 40.dp)
                ) {
                    Icon(
                        imageVector = if (isAwdOr4x4) Icons.Rounded.CheckCircleOutline else Icons.Rounded.Cancel,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "AWD STATUS",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Equipped/Not Equipped Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .border(
                                width = 1.dp,
                                color = statusColor.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isAwdOr4x4) "EQUIPPED" else "NOT EQUIPPED",
                            style = TextStyle(
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                }
            }

            // Specs Detail Tables
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // VEHICLE DETAILS
                Column {
                    InfoSectionHeader(title = "VEHICLE DETAILS")
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoCard(
                        items = listOf(
                            InfoItem(Icons.Rounded.Fingerprint, "VIN", scan.vin),
                            InfoItem(Icons.Rounded.DirectionsCar, "Make", scan.make.ifEmpty { "N/A" }),
                            InfoItem(Icons.Rounded.Build, "Model", scan.model.ifEmpty { "N/A" }),
                            InfoItem(Icons.Rounded.Layers, "Trim", scan.bodyClass.ifEmpty { "N/A" }),
                            InfoItem(Icons.Rounded.CalendarToday, "Year", scan.year.ifEmpty { "N/A" })
                        )
                    )
                }

                // DRIVETRAIN SPECS
                Column {
                    InfoSectionHeader(title = "DRIVETRAIN SPECS")
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoCard(
                        items = listOf(
                            InfoItem(Icons.Rounded.SettingsInputComponent, "Drive Type", scan.driveType.ifEmpty { "N/A" }),
                            InfoItem(Icons.Rounded.AllInclusive, "AWD System", if (isAwdOr4x4) "Detected" else "Not Detected"),
                            InfoItem(Icons.Rounded.LocalParking, "Electric Parking Brake", scan.parkingBrake.ifEmpty { "N/A" })
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scan another VIN action button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE50914),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(min = 220.dp)
                            .testTag("scan_another_button"),
                        shape = RoundedCornerShape(30.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "SCAN ANOTHER VIN",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .background(Color(0xFFE50914))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = TextStyle(
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 14.sp,
                letterSpacing = 1.5.sp
            )
        )
    }
}

data class InfoItem(
    val icon: ImageVector,
    val label: String,
    val value: String
)

@Composable
fun InfoCard(items: List<InfoItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A1A)
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.forEachIndexed { index, item ->
                InfoRow(item = item)
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.05f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoRow(item: InfoItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Red Icon with circular background
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color(0xFFE50914),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.label.uppercase(),
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.value,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

@Composable
fun CameraScannerScreen(
    onDismiss: () -> Unit,
    onImageCaptured: (File) -> Unit,
    onManualVinEntered: (String) -> Unit,
    onGalleryClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Set up CameraX executors
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val previewView = remember { PreviewView(context) }
    var vinText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraScanner", "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // CameraX Preview View
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Target viewport frame guide
        Box(
            modifier = Modifier
                .size(width = 300.dp, height = 90.dp)
                .border(BorderStroke(2.dp, Color.Green), RoundedCornerShape(8.dp))
                .align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Green.copy(alpha = 0.05f))
            )
        }

        // Align guidelines text helper
        Text(
            text = "Place VIN sticker inside green box. Avoid shadows & angles.",
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 120.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        // Custom Top Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFFE50914)
                )
            }
            Text(
                text = "CAPTURE VIN",
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    letterSpacing = 2.sp
                ),
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Overlayed Text Input + Dual Floating Action Triggers at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // "Scan or type VIN" Input Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = Color(0xFFE50914),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = vinText,
                        onValueChange = {
                            if (it.length <= 17) vinText = it.uppercase()
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_vin_input"),
                        decorationBox = { innerTextField ->
                            if (vinText.isEmpty()) {
                                Text(
                                    text = "Scan or type VIN",
                                    style = TextStyle(color = Color.Gray, fontSize = 18.sp)
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (vinText.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                if (vinText.trim().length == 17) {
                                    onManualVinEntered(vinText.trim())
                                }
                            },
                            modifier = Modifier.testTag("submit_manual_vin")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Accept",
                                tint = Color.Green,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Dual Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Photo library picker
                FloatingActionButton(
                    onClick = onGalleryClick,
                    containerColor = Color(0xFF1A1A1A),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("upload_pic_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = "Upload from Gallery"
                    )
                }

                // Main shutter shutter capture
                FloatingActionButton(
                    onClick = {
                        val photoFile = File(
                            context.cacheDir,
                            "vin_capture_${System.currentTimeMillis()}.jpg"
                        )

                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    onImageCaptured(photoFile)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e("CameraScanner", "Photo capture failed: ${exception.message}", exception)
                                }
                            }
                        )
                    },
                    containerColor = Color(0xFFE50914),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(68.dp)
                        .testTag("camera_capture_trigger")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = "Capture Photo",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeySettingsDialog(
    viewModel: AwdViewModel,
    onDismiss: () -> Unit,
    onOpenKeySetup: () -> Unit = {}
) {
    val provider by viewModel.apiProvider.collectAsState()
    val source by viewModel.apiKeySource.collectAsState()
    val remoteUrl by viewModel.remoteApiKeyUrl.collectAsState()
    val cachedKey by viewModel.cachedRemoteApiKey.collectAsState()
    val vertexProjectIdState by viewModel.vertexProjectId.collectAsState()
    val vertexRegionState by viewModel.vertexRegion.collectAsState()
    val vertexModelNameState by viewModel.vertexModelName.collectAsState()
    val ocrSpaceApiKeyState by viewModel.ocrSpaceApiKey.collectAsState()

    var selectedProvider by remember { mutableStateOf(provider) }
    var selectedSource by remember { mutableStateOf(source) }
    var inputUrl by remember { mutableStateOf(remoteUrl) }
    var vertexProjectId by remember { mutableStateOf(vertexProjectIdState) }
    var vertexRegion by remember { mutableStateOf(vertexRegionState) }
    var vertexModelName by remember { mutableStateOf(vertexModelNameState) }
    var ocrSpaceApiKeyVal by remember { mutableStateOf(ocrSpaceApiKeyState) }

    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("API Configuration", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Quick shortcut button to key setup
                Button(
                    onClick = {
                        onOpenKeySetup()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_key_setup_dialog_button")
                ) {
                    Icon(imageVector = Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gemini API Key Setup & Instructions")
                }

                Text(
                    text = "Configure the API service provider and authentication setup for VIN scanner OCR and details extraction.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Service Provider Radio Group
                Text("API Provider / Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { selectedProvider = "ai_studio" },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedProvider == "ai_studio") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedProvider == "ai_studio") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProvider == "ai_studio",
                                onClick = { selectedProvider = "ai_studio" }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Google AI Studio", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("Standard Gemini API endpoint (default)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        onClick = { selectedProvider = "vertex_ai" },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedProvider == "vertex_ai") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedProvider == "vertex_ai") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProvider == "vertex_ai",
                                onClick = { selectedProvider = "vertex_ai" }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Google Cloud Vertex AI", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("Enterprise-grade Vertex AI endpoint", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        onClick = { selectedProvider = "ocr_space" },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedProvider == "ocr_space") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedProvider == "ocr_space") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProvider == "ocr_space",
                                onClick = { selectedProvider = "ocr_space" }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("OCR.space", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("High-performance OCR Space Engine 3", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (selectedProvider != "ocr_space") {
                    // API Key Source and Options
                    Text("API Key Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { selectedSource = "local" },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedSource == "local") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedSource == "local") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSource == "local",
                                onClick = { selectedSource = "local" }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Local API Key", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("Uses BuildConfig.GEMINI_API_KEY from Secrets panel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        onClick = { selectedSource = "remote" },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedSource == "remote") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selectedSource == "remote") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSource == "remote",
                                onClick = { selectedSource = "remote" }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Remote URL", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("Loads key dynamically from an online text URL", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                if (selectedSource == "remote") {
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Remote Plain-text Key URL") },
                        placeholder = { Text("https://example.com/api_key.txt") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("remote_url_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Test URL Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                testing = true
                                testResult = null
                                coroutineScope.launch {
                                    val result = viewModel.testRemoteUrl(inputUrl)
                                    testing = false
                                    if (result.isSuccess) {
                                        val key = result.getOrThrow()
                                        testResult = if (key.length >= 6) {
                                            "Success! Fetched key prefix: ${key.take(6)}..."
                                        } else {
                                            "Success! Fetched key length: ${key.length}"
                                        }
                                    } else {
                                        testResult = "Error: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            enabled = !testing && inputUrl.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("test_remote_url_button")
                        ) {
                            if (testing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing...")
                            } else {
                                Text("Test & Fetch")
                            }
                        }
                    }

                    testResult?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (msg.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (msg.startsWith("Success")) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                    RoundedCornerShape(8.dp)
                               )
                                .padding(8.dp)
                        )
                    }

                    if (cachedKey.isNotEmpty()) {
                        Text(
                            text = "Cached Key Prefix: ${if (cachedKey.length >= 6) cachedKey.take(6) + "..." else "Available"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
                }

                // Vertex AI options section
                if (selectedProvider == "vertex_ai") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Vertex AI Parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = vertexProjectId,
                        onValueChange = { vertexProjectId = it },
                        label = { Text("Google Cloud Project ID") },
                        placeholder = { Text("my-gcp-project-1234") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("vertex_project_id_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = vertexRegion,
                        onValueChange = { vertexRegion = it },
                        label = { Text("Location / Region") },
                        placeholder = { Text("us-central1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("vertex_region_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = vertexModelName,
                        onValueChange = { vertexModelName = it },
                        label = { Text("Model Name") },
                        placeholder = { Text("gemini-1.5-flash") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("vertex_model_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // OCR.space parameters section
                if (selectedProvider == "ocr_space") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("OCR.space Parameters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    
                    OutlinedTextField(
                        value = ocrSpaceApiKeyVal,
                        onValueChange = { ocrSpaceApiKeyVal = it },
                        label = { Text("OCR.space API Key") },
                        placeholder = { Text("helloworld") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ocr_space_api_key_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        text = "Set to 'helloworld' (default) or register your free/PRO key at ocr.space. Uses OCR Engine 3 for optimal performance.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveApiConfig(
                        provider = selectedProvider,
                        source = selectedSource,
                        url = inputUrl,
                        projectId = vertexProjectId,
                        region = vertexRegion,
                        modelName = vertexModelName,
                        ocrSpaceApiKeyVal = ocrSpaceApiKeyVal
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("save_api_key_config_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_api_key_config_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GeminiApiKeySetupScreen(
    viewModel: AwdViewModel,
    onKeySaved: () -> Unit,
    canSkip: Boolean = false,
    onSkip: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val userKey by viewModel.userGeminiApiKey.collectAsState()

    var apiKeyInput by remember { mutableStateOf(userKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var testResultMsg by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1F1F28), Color(0xFF0F0F14))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Header Icon & Title
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE50914).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = Color(0xFFE50914),
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Gemini API Key Setup",
                style = TextStyle(
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "To scan VIN numbers and fetch vehicle details using Google Gemini AI, please configure your personal API key.",
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF282834)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = null,
                            tint = Color(0xFFE50914),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "How to get a free Gemini API key:",
                            style = TextStyle(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    InstructionStepItem(
                        number = "1",
                        title = "Visit Google AI Studio",
                        desc = "Click the button below or go to aistudio.google.com in your browser."
                    )

                    InstructionStepItem(
                        number = "2",
                        title = "Sign in with Google",
                        desc = "Log in with any personal or workspace Google Account."
                    )

                    InstructionStepItem(
                        number = "3",
                        title = "Get API Key",
                        desc = "Click 'Get API key' or 'Create API key' in Google AI Studio."
                    )

                    InstructionStepItem(
                        number = "4",
                        title = "Copy & Paste",
                        desc = "Copy your key (starts with AIzaSy...) and paste it in the box below."
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Button to launch AI Studio website
                    Button(
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/app/apikey")
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE50914),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("get_api_key_browser_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Get Gemini API Key (aistudio.google.com)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Input Field Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF282834)
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Enter Gemini API Key",
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            testResultMsg = null
                        },
                        placeholder = { Text("AIzaSy...", color = Color.White.copy(alpha = 0.4f)) },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (apiKeyInput.isNotEmpty()) {
                                    IconButton(onClick = { apiKeyInput = "" }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Clear,
                                            contentDescription = "Clear",
                                            tint = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    val clipText = clipboardManager.getText()?.text
                                    if (!clipText.isNullOrBlank()) {
                                        apiKeyInput = clipText
                                        testResultMsg = null
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = Color(0xFFE50914)
                                    )
                                }
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Toggle Visibility",
                                        tint = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE50914),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFE50914)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input")
                    )

                    Text(
                        text = "Your key is saved locally on your device and used only for Gemini AI requests.",
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    )

                    testResultMsg?.let { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSuccess) Color(0xFF1B5E20).copy(alpha = 0.5f)
                                    else Color(0xFFB71C1C).copy(alpha = 0.5f)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = msg,
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Test Button
                        OutlinedButton(
                            onClick = {
                                isTesting = true
                                testResultMsg = null
                                coroutineScope.launch {
                                    val result = viewModel.testGeminiApiKey(apiKeyInput)
                                    isTesting = false
                                    if (result.isSuccess) {
                                        isSuccess = true
                                        testResultMsg = result.getOrNull() ?: "Key verified successfully!"
                                    } else {
                                        isSuccess = false
                                        testResultMsg = result.exceptionOrNull()?.message ?: "Verification failed."
                                    }
                                }
                            },
                            enabled = !isTesting && apiKeyInput.trim().isNotEmpty(),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_gemini_key_button")
                        ) {
                            if (isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Text("Test Key", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Save Button
                        Button(
                            onClick = {
                                if (apiKeyInput.trim().isEmpty()) {
                                    isSuccess = false
                                    testResultMsg = "Please enter an API key first."
                                    return@Button
                                }
                                viewModel.saveUserGeminiApiKey(apiKeyInput)
                                onKeySaved()
                            },
                            enabled = apiKeyInput.trim().isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE50914),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("save_gemini_key_button")
                        ) {
                            Text("Save & Continue", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (canSkip) {
                        TextButton(
                            onClick = onSkip,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("skip_key_setup_button")
                        ) {
                            Text(
                                text = "Cancel / Back to App",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun InstructionStepItem(
    number: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0xFFE50914)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            )
            Text(
                text = desc,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            )
        }
    }
}
