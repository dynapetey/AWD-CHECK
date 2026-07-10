package com.example.ui

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.VinScan
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AwdScreen(viewModel: AwdViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()
    val selectedScan by viewModel.selectedScan.collectAsState()

    val context = LocalContext.current
    var manualVin by remember { mutableStateOf("") }
    var showCameraScanner by remember { mutableStateOf(false) }

    val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)

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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book, // Repository icon
                            contentDescription = null,
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "dynapetey",
                            fontWeight = FontWeight.Normal,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF8B949E)
                        )
                        Text(
                            text = " / ",
                            fontWeight = FontWeight.Normal,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF8B949E)
                        )
                        Text(
                            text = "Awd_Check",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Public",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF8B949E),
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Scanning and manual input card
                item {
                    ScannerInputCard(
                        manualVin = manualVin,
                        onManualVinChange = {
                            if (it.length <= 17) manualVin = it.uppercase()
                        },
                        onDecodeManual = {
                            viewModel.decodeManualVin(manualVin)
                            manualVin = ""
                        },
                        onScanCameraClick = {
                            if (cameraPermissionState.status.isGranted) {
                                showCameraScanner = true
                            } else {
                                cameraPermissionState.launchPermissionRequest()
                            }
                        },
                        onUploadPhotoClick = {
                            galleryLauncher.launch("image/*")
                        }
                    )
                }

                // 2. Active loading states or scan results
                item {
                    AnimatedVisibility(
                        visible = uiState !is ScanUiState.Idle,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        when (uiState) {
                            is ScanUiState.ExtractingVin -> {
                                LoadingCard("Extracting VIN...", "Analyzing image with Gemini AI")
                            }
                            is ScanUiState.DecodingVin -> {
                                LoadingCard("Decoding Technical Specifications...", "Retrieving vehicle drive configuration from NHTSA database")
                            }
                            is ScanUiState.Success -> {
                                // Handled in selected detail pane or card
                                val scan = (uiState as ScanUiState.Success).scan
                                MainResultDisplay(scan = scan, onDismiss = { viewModel.resetState() })
                            }
                            is ScanUiState.Error -> {
                                val errorMsg = (uiState as ScanUiState.Error).message
                                ErrorCard(message = errorMsg, onDismiss = { viewModel.resetState() })
                            }
                            else -> {}
                        }
                    }
                }

                // 3. Selection Details display (from history list clicks)
                if (selectedScan != null && uiState !is ScanUiState.Success) {
                    item {
                        MainResultDisplay(
                            scan = selectedScan!!,
                            titleText = "Selected Vehicle Spec",
                            onDismiss = { viewModel.selectScan(null) }
                        )
                    }
                }

                // 4. History log section header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Scan History",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (history.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearHistory() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all scans", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear All", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Empty state for History
                if (history.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No scan records found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Camera-scan or search vehicle VIN above to begin",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Render history items list
                items(history) { scan ->
                    HistoryItemRow(
                        scan = scan,
                        isSelected = scan.id == selectedScan?.id,
                        onClick = {
                            viewModel.selectScan(scan)
                            // Clean scanning phase so detail view doesn't conflict
                            viewModel.resetState()
                        },
                        onDelete = { viewModel.deleteScan(scan) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }
    }

    // Camera Scan Full View Mock / Engine Integration dialog
    if (showCameraScanner) {
        CameraScannerDialog(
            onDismiss = { showCameraScanner = false },
            onImageCaptured = { file ->
                showCameraScanner = false
                val stream = FileInputStream(file)
                viewModel.processImage(stream)
            }
        )
    }
}

@Composable
fun ScannerInputCard(
    manualVin: String,
    onManualVinChange: (String) -> Unit,
    onDecodeManual: () -> Unit,
    onScanCameraClick: () -> Unit,
    onUploadPhotoClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = "Vehicle VIN Scanner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Decode VIN and verify AWD/4x4 drivetrain configurations using official NHTSA data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8B949E)
                )
            }

            // Direct Scan buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Camera Button (GitHub Green Action style)
                Button(
                    onClick = onScanCameraClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("scan_camera_button"),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF238636), // GitHub Green
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "Scan with Camera", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan VIN", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                // File Upload Button (GitHub Secondary style)
                Button(
                    onClick = onUploadPhotoClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("upload_pic_button")
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant, // GitHub Gray Secondary
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(imageVector = Icons.Default.UploadFile, contentDescription = "Upload Paperwork", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Upload Photo", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Divider or text "OR ENTER MANUALLY"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
                Text(
                    text = "OR ENTER MANUALLY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B949E),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            }

            // Input TextField for manual 17-char VIN
            OutlinedTextField(
                value = manualVin,
                onValueChange = onManualVinChange,
                label = { Text("17-Character VIN", color = Color(0xFF8B949E)) },
                placeholder = { Text("e.g. 1FT8W2BM0...", color = Color(0xFF8B949E).copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_vin_input"),
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary, // GitHub Blue
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline, // GitHub Slate Border
                    focusedContainerColor = MaterialTheme.colorScheme.background, // GitHub Dark Canvas
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (manualVin.length == 17) {
                            onDecodeManual()
                            focusManager.clearFocus()
                        }
                    }
                ),
                trailingIcon = {
                    if (manualVin.isNotEmpty()) {
                        IconButton(onClick = { onManualVinChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear text", tint = Color(0xFF8B949E))
                        }
                    }
                },
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Limit: 17 alphanumeric letters", fontSize = 11.sp, color = Color(0xFF8B949E))
                        Text("${manualVin.length}/17", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (manualVin.length == 17) Color(0xFF3FB950) else Color(0xFF8B949E))
                    }
                }
            )

            // Submit Button (GitHub Blue Primary style)
            Button(
                onClick = {
                    onDecodeManual()
                    focusManager.clearFocus()
                },
                enabled = manualVin.length == 17,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_manual_vin")
                    .then(
                        if (manualVin.length != 17) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        } else Modifier
                    ),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (manualVin.length == 17) Color(0xFF1F6FEB) else Color(0xFF21262D).copy(alpha = 0.5f), // GitHub Blue vs Disabled Grey
                    contentColor = if (manualVin.length == 17) Color.White else Color(0xFF8B949E).copy(alpha = 0.5f)
                )
            ) {
                Text("Lookup Vehicle Details", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LoadingCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                strokeWidth = 4.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Scan Failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss error",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

private data class Quint<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

@Composable
fun MainResultDisplay(
    scan: VinScan,
    titleText: String = "Vehicle Search Result",
    onDismiss: () -> Unit
) {
    val isAwd = scan.driveType.lowercase().contains("all") || scan.driveType.lowercase().contains("awd")
    val is4x4 = scan.driveType.lowercase().contains("4x4") || scan.driveType.lowercase().contains("four-wheel") || scan.driveType.lowercase().contains("4wd")
    val isFwd = scan.driveType.lowercase().contains("front") || scan.driveType.lowercase().contains("fwd")
    val isRwd = scan.driveType.lowercase().contains("rear") || scan.driveType.lowercase().contains("rwd")

    val (badgeText, badgeBgColor, badgeBorderColor, badgeTextColor, icon) = when {
        isAwd -> {
            Quint(
                "AWD MATCH ACTIVE",
                Color(0xFF152219),
                Color(0xFF3FB950),
                Color(0xFF56D364),
                Icons.Rounded.CheckCircle
            )
        }
        is4x4 -> {
            Quint(
                "4X4 DRIVE ACTIVE",
                Color(0xFF152219),
                Color(0xFF3FB950),
                Color(0xFF56D364),
                Icons.Rounded.AllInclusive
            )
        }
        isFwd -> {
            Quint(
                "FWD DRIVETRAIN DETECTED",
                Color(0xFF111E2E),
                Color(0xFF58A6FF),
                Color(0xFF79C0FF),
                Icons.Rounded.ArrowCircleUp
            )
        }
        isRwd -> {
            Quint(
                "RWD DRIVETRAIN DETECTED",
                Color(0xFF1E152E),
                Color(0xFF8957E5),
                Color(0xFFD2A8FF),
                Icons.Rounded.ArrowCircleDown
            )
        }
        else -> {
            Quint(
                scan.driveType.uppercase().ifEmpty { "DRIVETRAIN SPEC UNKNOWN" },
                Color(0xFF1F242C),
                Color(0xFF30363D),
                Color(0xFF8B949E),
                Icons.Rounded.Help
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(
                    1.dp,
                    if (isAwd || is4x4) Color(0xFF3FB950) else MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isAwd || is4x4) Color(0xFF3FB950) else Color(0xFF8B949E))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B949E),
                        letterSpacing = 0.5.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close specifications card",
                        tint = Color(0xFF8B949E)
                    )
                }
            }

            // Big visual AWD Badge styled like GitHub status box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBgColor)
                    .border(BorderStroke(1.dp, badgeBorderColor), RoundedCornerShape(6.dp))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = badgeTextColor,
                        textAlign = TextAlign.Center,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Specifications detailed Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpecRow(label = "DRIVE SYSTEM", value = scan.driveType.ifEmpty { "N/A" }, highlighted = true, highlightColor = badgeTextColor)
                SpecRow(label = "PARKING BRAKE", value = scan.parkingBrake.ifEmpty { "Unknown" }, applyBold = true)
                SpecRow(label = "YEAR", value = scan.year.ifEmpty { "N/A" })
                SpecRow(label = "MAKE", value = scan.make.ifEmpty { "N/A" }, applyBold = true)
                SpecRow(label = "MODEL", value = scan.model.ifEmpty { "N/A" }, applyBold = true)
                SpecRow(label = "BODY CLASS", value = scan.bodyClass.ifEmpty { "N/A" })
                SpecRow(label = "VEHICLE TYPE", value = scan.vehicleType.ifEmpty { "N/A" })
                SpecRow(label = "VEHICLE VIN", value = scan.vin, italicValue = true)

                if (!scan.isClean && !scan.errorMsg.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Decoder Notice: ${scan.errorMsg}",
                        color = Color(0xFFF85149), // GitHub Red Text
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color(0xFF2D191E), // GitHub Dark Red background
                                RoundedCornerShape(6.dp)
                            )
                            .border(1.dp, Color(0xFFF85149).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun SpecRow(
    label: String,
    value: String,
    highlighted: Boolean = false,
    highlightColor: Color = Color.Unspecified,
    applyBold: Boolean = false,
    italicValue: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlighted) highlightColor else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (highlighted || applyBold) FontWeight.Black else FontWeight.Normal,
            fontStyle = if (italicValue) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.5f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
}

@Composable
fun HistoryItemRow(
    scan: VinScan,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isAwdOr4x4 = scan.driveType.lowercase().contains("all") || scan.driveType.lowercase().contains("awd") || scan.driveType.lowercase().contains("4x4")

    val systemIcon = when {
        scan.driveType.lowercase().contains("all") || scan.driveType.lowercase().contains("awd") -> Icons.Rounded.CheckCircle
        scan.driveType.lowercase().contains("4x4") || scan.driveType.lowercase().contains("4wd") || scan.driveType.lowercase().contains("four") -> Icons.Rounded.AllInclusive
        scan.driveType.lowercase().contains("front") || scan.driveType.lowercase().contains("fwd") -> Icons.Rounded.ArrowCircleUp
        scan.driveType.lowercase().contains("rear") || scan.driveType.lowercase().contains("rwd") -> Icons.Rounded.ArrowCircleDown
        else -> Icons.Rounded.Help
    }

    val iconColor = when {
        scan.driveType.lowercase().contains("all") || scan.driveType.lowercase().contains("awd") -> Color(0xFF3FB950) // GitHub Green
        scan.driveType.lowercase().contains("4x4") || scan.driveType.lowercase().contains("4wd") || scan.driveType.lowercase().contains("four") -> Color(0xFF3FB950)
        scan.driveType.lowercase().contains("front") || scan.driveType.lowercase().contains("fwd") -> Color(0xFF58A6FF) // GitHub Blue
        scan.driveType.lowercase().contains("rear") || scan.driveType.lowercase().contains("rwd") -> Color(0xFFBC8CFF) // GitHub Purple
        else -> Color(0xFF8B949E) // GitHub Muted Gray
    }

    val format = SimpleDateFormat("MMM d, yyyy - hh:mm a", Locale.getDefault())
    val formattedTime = format.format(Date(scan.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF58A6FF) else MaterialTheme.colorScheme.outline, // GitHub Blue vs Gray Border
                shape = RoundedCornerShape(6.dp)
            ),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1F242C) else MaterialTheme.colorScheme.surface // GitHub Tinted Gray vs Surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon representing AWD status (styled like a GitHub status check)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = systemIcon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text specs
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${scan.year} ${scan.make} ${scan.model}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Spec: ${if (scan.driveType.isNotEmpty()) scan.driveType else "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAwdOr4x4) Color(0xFF56D364) else Color(0xFF8B949E), // Pass Green vs Muted
                        fontWeight = if (isAwdOr4x4) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B949E)
                    )
                    Text(
                        text = "VIN: ${scan.vin}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "Checked on $formattedTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B949E).copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Delete button (styled neutrally)
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete entry from history",
                    tint = Color(0xFFF85149).copy(alpha = 0.8f), // GitHub Danger Red
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// CameraX View Dialog to provide a zero-mock, real hardware integration!
@Composable
fun CameraScannerDialog(
    onDismiss: () -> Unit,
    onImageCaptured: (File) -> Unit
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Camera Preview Frame
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Outer target overlay bounds
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = "ALIGN VEHICLE VIN",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                // Scanning viewfinder guide target frame
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

                Text(
                    text = "Place VIN sticker inside green box. Avoid shadows & angles.",
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 110.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )

                // Capture snapshot trigger
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .size(64.dp)
                        .testTag("camera_capture_trigger"),
                    shape = CircleShape,
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) {
                    Icon(imageVector = Icons.Default.Camera, contentDescription = "Capture Snap", modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}
