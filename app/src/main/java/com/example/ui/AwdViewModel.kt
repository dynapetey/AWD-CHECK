package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AwdDatabase
import com.example.data.VinScan
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiInlineData
import com.example.data.api.GeminiPart
import com.example.data.api.GeminiRequest
import com.example.data.api.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

sealed interface ScanUiState {
    object Idle : ScanUiState
    object ExtractingVin : ScanUiState
    object DecodingVin : ScanUiState
    data class Success(val scan: VinScan) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class AwdViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AwdDatabase.getDatabase(application)
    private val dao = db.vinScanDao()

    val history: StateFlow<List<VinScan>> = dao.getAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _selectedScan = MutableStateFlow<VinScan?>(null)
    val selectedScan: StateFlow<VinScan?> = _selectedScan.asStateFlow()

    fun selectScan(scan: VinScan?) {
        _selectedScan.value = scan
    }

    fun deleteScan(scan: VinScan) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteScan(scan)
            if (_selectedScan.value?.id == scan.id) {
                _selectedScan.value = null
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearHistory()
            _selectedScan.value = null
        }
    }

    fun resetState() {
        _uiState.value = ScanUiState.Idle
    }

    // Decode a manually typed VIN
    fun decodeManualVin(vin: String) {
        val trimmedVin = vin.trim().uppercase()
        if (trimmedVin.isEmpty()) {
            _uiState.value = ScanUiState.Error("Please enter a valid VIN.")
            return
        }
        if (trimmedVin.length != 17) {
            _uiState.value = ScanUiState.Error("VIN must be exactly 17 characters (current length: ${trimmedVin.length}).")
            return
        }

        viewModelScope.launch {
            _uiState.value = ScanUiState.DecodingVin
            try {
                val scan = performNhtsaDecode(trimmedVin)
                _uiState.value = ScanUiState.Success(scan)
                _selectedScan.value = scan
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error(e.message ?: "Failed to decode VIN details.")
            }
        }
    }

    // Process a photo taken or uploaded, extract VIN via Gemini, then decode via NHTSA
    fun processImage(inputStream: InputStream, mimeType: String = "image/jpeg") {
        viewModelScope.launch {
            _uiState.value = ScanUiState.ExtractingVin
            try {
                // 1. Load and resize bitmap to make payload small and fast to upload
                val rawBitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeStream(inputStream)
                }

                if (rawBitmap == null) {
                    _uiState.value = ScanUiState.Error("Could not read image file.")
                    return@launch
                }

                val resizedBitmap = resizeBitmap(rawBitmap, 1024)
                val base64Image = withContext(Dispatchers.IO) {
                    bitmapToBase64(resizedBitmap)
                }

                // 2. Query Gemini to extract VIN
                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                    _uiState.value = ScanUiState.Error("Gemini API key is not configured. Please add it to your secrets inside AI Studio.")
                    return@launch
                }

                val prompt = "This is an image of a vehicle's dashboard VIN sticker, door pillar barcode decal, windshield printed tag, or official paperwork. " +
                        "Extract the 17-character VIN (Vehicle Identification Number) from this image. " +
                        "Look for alphanumeric sequence of 17 characters. " +
                        "Return ONLY the 17-character VIN code in plain text. " +
                        "No other text, preamble, explanations, punctuation, or spaces. If you cannot find any 17-character VIN, return 'NOT_FOUND'."

                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = prompt),
                                GeminiPart(inlineData = GeminiInlineData(mimeType = mimeType, data = base64Image))
                            )
                        )
                    )
                )

                val response = withContext(Dispatchers.IO) {
                    NetworkClient.geminiService.generateContent(apiKey, request)
                }

                val extractedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?.trim()?.uppercase()?.replace(Regex("[\\s-]"), "") ?: ""

                Log.d("AwdViewModel", "Extracted text: $extractedText")

                if (extractedText.isEmpty() || extractedText.contains("NOTFOUND") || extractedText.contains("NOT_FOUND")) {
                    _uiState.value = ScanUiState.Error("Could not detect a clear VIN number in this image. Please ensure the VIN is well-lit and clearly readable, or type it manually.")
                    return@launch
                }

                // Clean other words that might be returned
                val matchedVin = Regex("[A-Z0-9]{17}").find(extractedText)?.value

                if (matchedVin == null) {
                    _uiState.value = ScanUiState.Error("Extracted text did not contain a valid 17-character VIN block ($extractedText). Try aligning image more closely or manual typing.")
                    return@launch
                }

                // 3. Decode decoded VIN with NHTSA
                _uiState.value = ScanUiState.DecodingVin
                val scan = performNhtsaDecode(matchedVin)
                _uiState.value = ScanUiState.Success(scan)
                _selectedScan.value = scan

            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error("Error: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    private suspend fun queryParkingBrakeWithGemini(year: String, make: String, model: String, vin: String): String = withContext(Dispatchers.IO) {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Unknown (No API Key)"
        }

        val prompt = "Based on your technical knowledge of vehicles, determine if a $year $make $model (specifically associated with VIN: $vin if applicable) has an Electronic Parking Brake (EPB) or a mechanical handbrake / foot brake. " +
                "Respond with a brief, clear status. Try to answer in under 15 words. Examples of expected responses: " +
                "'Yes (Electronic Parking Brake)' or " +
                "'No (Mechanical Handbrake)' or " +
                "'No (Mechanical Foot Pedal)'. " +
                "Do not include markdown stars like * or extra details unless extremely concise."

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt)
                    )
                )
            )
        )

        try {
            val response = NetworkClient.geminiService.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                ?.replace("*", "")?.replace("`", "") ?: "Unknown"
        } catch (e: Exception) {
            Log.e("AwdViewModel", "Error fetching EPB status: ${e.message}")
            "Unknown"
        }
    }

    private suspend fun performNhtsaDecode(vin: String): VinScan = withContext(Dispatchers.IO) {
        val response = NetworkClient.nhtsaService.decodeVin(vin)
        val result = response.results?.firstOrNull()

        if (result == null) {
            throw Exception("NHTSA API returned empty decode results.")
        }

        // Verify if error text is clean (e.g. code is 0, or clean decode)
        val codeVal = result.errorCode ?: "0"
        val errorTextMsg = result.errorText ?: ""
        val isClean = codeVal == "0" || !errorTextMsg.lowercase().contains("error")

        val year = result.modelYear ?: "N/A"
        val make = result.make ?: "N/A"
        val model = result.model ?: "N/A"
        val rawDriveType = result.driveType ?: "N/A"

        // Format Drive Type for better readability
        val driveFormatted = formatDriveType(rawDriveType)

        // Query Gemini to get Electronic Parking Brake status
        val parkingBrakeInfo = if (year != "N/A" && make != "N/A") {
            queryParkingBrakeWithGemini(year, make, model, vin)
        } else {
            "Unknown"
        }

        val scan = VinScan(
            vin = vin,
            timestamp = System.currentTimeMillis(),
            year = year,
            make = make,
            model = model,
            driveType = driveFormatted,
            bodyClass = result.bodyClass ?: "N/A",
            vehicleType = result.vehicleType ?: "N/A",
            isClean = isClean,
            errorMsg = if (isClean) null else errorTextMsg,
            parkingBrake = parkingBrakeInfo
        )

        // Persistence
        dao.insertScan(scan)
        scan
    }

    private fun formatDriveType(raw: String): String {
        val l = raw.lowercase()
        return when {
            l.contains("all wheel") || l.contains("awd") -> "All-Wheel Drive (AWD)"
            l.contains("4x4") || l.contains("4wd") || l.contains("four wheel") || l.contains("four-wheel") -> "4x4 / Four-Wheel Drive"
            l.contains("rear") || l.contains("rwd") || l.contains("4x2") && (l.contains("rear") || l.contains("back")) -> "Rear-Wheel Drive (RWD)"
            l.contains("front") || l.contains("fwd") -> "Front-Wheel Drive (FWD)"
            else -> raw // fallback if something exotic
        }
    }

    // Helper to resize Bitmap
    private fun resizeBitmap(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        val ratio: Float = width.toFloat() / height.toFloat()

        var targetWidth = maxDimension
        var targetHeight = maxDimension

        if (width > height) {
            targetHeight = (maxDimension / ratio).toInt()
        } else {
            targetWidth = (maxDimension * ratio).toInt()
        }

        if (width <= maxDimension && height <= maxDimension) {
            return source
        }

        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    // Convert bitmap to Base64 jpeg string
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
