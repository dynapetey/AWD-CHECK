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
import retrofit2.HttpException
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

    // API Key Source and Remote URL config
    private val sharedPrefs = application.getSharedPreferences("awd_prefs", Application.MODE_PRIVATE)

    private val _userGeminiApiKey = MutableStateFlow(sharedPrefs.getString("user_gemini_api_key", "") ?: "")
    val userGeminiApiKey: StateFlow<String> = _userGeminiApiKey.asStateFlow()

    private val _apiProvider = MutableStateFlow(sharedPrefs.getString("api_provider", "ai_studio") ?: "ai_studio")
    val apiProvider: StateFlow<String> = _apiProvider.asStateFlow()

    private val _apiKeySource = MutableStateFlow(sharedPrefs.getString("api_key_source", "local") ?: "local")
    val apiKeySource: StateFlow<String> = _apiKeySource.asStateFlow()

    private val _remoteApiKeyUrl = MutableStateFlow(
        sharedPrefs.getString("remote_api_key_url", "") ?: ""
    )
    val remoteApiKeyUrl: StateFlow<String> = _remoteApiKeyUrl.asStateFlow()

    private val _cachedRemoteApiKey = MutableStateFlow(sharedPrefs.getString("cached_remote_api_key", "") ?: "")
    val cachedRemoteApiKey: StateFlow<String> = _cachedRemoteApiKey.asStateFlow()

    private val _vertexProjectId = MutableStateFlow(sharedPrefs.getString("vertex_project_id", "") ?: "")
    val vertexProjectId: StateFlow<String> = _vertexProjectId.asStateFlow()

    private val _vertexRegion = MutableStateFlow(sharedPrefs.getString("vertex_region", "us-central1") ?: "us-central1")
    val vertexRegion: StateFlow<String> = _vertexRegion.asStateFlow()

    private val _vertexModelName = MutableStateFlow(sharedPrefs.getString("vertex_model_name", "gemini-1.5-flash") ?: "gemini-1.5-flash")
    val vertexModelName: StateFlow<String> = _vertexModelName.asStateFlow()

    private val _ocrSpaceApiKey = MutableStateFlow(sharedPrefs.getString("ocr_space_api_key", "K81505784088957") ?: "K81505784088957")
    val ocrSpaceApiKey: StateFlow<String> = _ocrSpaceApiKey.asStateFlow()

    fun saveUserGeminiApiKey(key: String) {
        val trimmed = key.trim()
        _userGeminiApiKey.value = trimmed
        _apiProvider.value = "ai_studio"
        sharedPrefs.edit()
            .putString("user_gemini_api_key", trimmed)
            .putString("api_provider", "ai_studio")
            .apply()
    }

    fun isApiKeyConfigured(): Boolean {
        val userKey = _userGeminiApiKey.value.trim()
        if (userKey.isNotEmpty()) return true
        val buildKey = com.example.BuildConfig.GEMINI_API_KEY
        return buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY"
    }

    suspend fun testGeminiApiKey(keyToTest: String): Result<String> = withContext(Dispatchers.IO) {
        val key = keyToTest.trim()
        if (key.isEmpty()) {
            return@withContext Result.failure(Exception("API Key cannot be empty."))
        }
        try {
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = "Hello Gemini"))
                    )
                )
            )
            val url = "v1beta/models/gemini-3.5-flash:generateContent"
            val response = NetworkClient.geminiService.generateContent(url, key, request)
            if (response.candidates?.isNotEmpty() == true) {
                Result.success("API key verified successfully!")
            } else {
                Result.success("Key accepted by Gemini API.")
            }
        } catch (e: Exception) {
            val msg = when {
                e is HttpException && e.code() == 401 -> "HTTP 401: Invalid or unauthorized Gemini API key."
                e is HttpException && e.code() == 403 -> "HTTP 403: Permission denied for this Gemini API key."
                else -> e.message ?: "Failed to verify key."
            }
            Result.failure(Exception(msg))
        }
    }

    fun saveApiConfig(
        provider: String,
        source: String,
        url: String,
        projectId: String,
        region: String,
        modelName: String,
        ocrSpaceApiKeyVal: String
    ) {
        _apiProvider.value = provider
        _apiKeySource.value = source
        _remoteApiKeyUrl.value = url
        _vertexProjectId.value = projectId
        _vertexRegion.value = region
        _vertexModelName.value = modelName
        _ocrSpaceApiKey.value = ocrSpaceApiKeyVal

        sharedPrefs.edit()
            .putString("api_provider", provider)
            .putString("api_key_source", source)
            .putString("remote_api_key_url", url)
            .putString("vertex_project_id", projectId)
            .putString("vertex_region", region)
            .putString("vertex_model_name", modelName)
            .putString("ocr_space_api_key", ocrSpaceApiKeyVal)
            .apply()
    }

    fun getGeminiRequestUrl(): String {
        val provider = _apiProvider.value
        if (provider == "vertex_ai") {
            val projectId = _vertexProjectId.value.trim()
            val region = _vertexRegion.value.trim().ifEmpty { "us-central1" }
            val model = _vertexModelName.value.trim().ifEmpty { "gemini-1.5-flash" }
            return "https://$region-aiplatform.googleapis.com/v1/projects/$projectId/locations/$region/publishers/google/models/$model:generateContent"
        } else {
            return "v1beta/models/gemini-3.5-flash:generateContent"
        }
    }

    suspend fun fetchRemoteApiKey(url: String): String = withContext(Dispatchers.IO) {
        if (url.trim().isEmpty()) throw Exception("Remote URL is empty")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = okhttp3.Request.Builder()
            .url(url.trim())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP Error ${response.code}: ${response.message}")
            val body = response.body?.string()?.trim() ?: throw Exception("Empty response body")
            if (body.isEmpty()) throw Exception("Fetched key is empty")
            body
        }
    }

    suspend fun testRemoteUrl(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val key = fetchRemoteApiKey(url)
            Result.success(key)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEffectiveApiKey(): String {
        val userKey = _userGeminiApiKey.value.trim()
        if (userKey.isNotEmpty()) {
            return userKey
        }
        val source = _apiKeySource.value
        if (source == "local") {
            return com.example.BuildConfig.GEMINI_API_KEY
        }
        val url = _remoteApiKeyUrl.value
        if (url.trim().isEmpty() || url == "MY_GEMINI_API_KEY_URL") {
            return com.example.BuildConfig.GEMINI_API_KEY
        }
        return try {
            val fetchedKey = fetchRemoteApiKey(url)
            _cachedRemoteApiKey.value = fetchedKey
            sharedPrefs.edit().putString("cached_remote_api_key", fetchedKey).apply()
            fetchedKey
        } catch (e: Exception) {
            Log.e("AwdViewModel", "Failed to fetch remote API key, using cached key: ${e.message}")
            val cached = sharedPrefs.getString("cached_remote_api_key", "") ?: ""
            if (cached.isNotEmpty()) cached else com.example.BuildConfig.GEMINI_API_KEY
        }
    }

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

                val resizedBitmap = resizeBitmap(rawBitmap, 2048)
                val base64Image = withContext(Dispatchers.IO) {
                    bitmapToBase64(resizedBitmap)
                }

                // 2. OCR Extraction
                val rawResponse: String?
                if (_apiProvider.value == "ocr_space") {
                    val ocrKey = _ocrSpaceApiKey.value.trim().ifEmpty { "helloworld" }
                    val dataUri = "data:$mimeType;base64,$base64Image"
                    val ocrResponse = withContext(Dispatchers.IO) {
                        NetworkClient.ocrSpaceService.parseImage(
                            apiKey = ocrKey,
                            base64Image = dataUri,
                            ocrEngine = "3",
                            scale = true
                        )
                    }
                    if (ocrResponse.isErroredOnProcessing == true) {
                        _uiState.value = ScanUiState.Error("OCR.space Error: ${ocrResponse.errorDetails ?: "Unknown processing error"}")
                        return@launch
                    }
                    val parsedText = ocrResponse.parsedResults?.firstOrNull()?.parsedText
                    if (parsedText == null) {
                        _uiState.value = ScanUiState.Error("OCR.space did not return any text. Please try with a clearer photo.")
                        return@launch
                    }
                    rawResponse = parsedText
                    Log.d("AwdViewModel", "Raw OCR.space response: $rawResponse")
                } else {
                    val apiKey = getEffectiveApiKey()
                    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                        _uiState.value = ScanUiState.Error("Gemini API key is not configured. Please configure it in Settings (the top-right key icon) or add it to AI Studio secrets.")
                        return@launch
                    }

                    if (_apiProvider.value == "vertex_ai" && _vertexProjectId.value.trim().isEmpty()) {
                        _uiState.value = ScanUiState.Error("Vertex AI Project ID is not configured. Please configure it in Settings (the top-right key icon).")
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

                    val requestUrl = getGeminiRequestUrl()
                    val response = withContext(Dispatchers.IO) {
                        NetworkClient.geminiService.generateContent(requestUrl, apiKey, request)
                    }

                    rawResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    Log.d("AwdViewModel", "Raw Gemini OCR response: $rawResponse")
                }

                val extractedVin = extractVin(rawResponse)

                if (extractedVin == null) {
                    _uiState.value = ScanUiState.Error("Could not detect a clear VIN number in this image. Please ensure the VIN is well-lit and clearly readable, or type it manually.")
                    return@launch
                }

                val sanitizedVin = sanitizeVin(extractedVin)
                Log.d("AwdViewModel", "Extracted VIN: $extractedVin, Sanitized VIN: $sanitizedVin")

                // 3. Decode decoded VIN with NHTSA
                _uiState.value = ScanUiState.DecodingVin
                val scan = performNhtsaDecode(sanitizedVin)
                _uiState.value = ScanUiState.Success(scan)
                _selectedScan.value = scan

            } catch (e: Exception) {
                val errorMsg = when {
                    e is HttpException && e.code() == 401 ->
                        "HTTP 401 Unauthorized: The API key is missing, deleted, or unauthorized. Please set a valid API key in Settings (the top-right key icon) or add GEMINI_API_KEY to AI Studio Secrets."
                    e is HttpException && e.code() == 403 ->
                        "HTTP 403 Forbidden: Permission denied for this API key. Please check your key permissions."
                    e.message?.contains("401") == true ->
                        "HTTP 401 Unauthorized: Invalid or missing API key. Please configure your key in Settings."
                    else ->
                        "Error: ${e.localizedMessage ?: e.message}"
                }
                _uiState.value = ScanUiState.Error(errorMsg)
            }
        }
    }

    private fun sanitizeVin(vin: String): String {
        return vin.uppercase()
            .replace('I', '1')
            .replace('O', '0')
            .replace('Q', '0')
    }

    private fun extractVin(rawResponse: String?): String? {
        if (rawResponse == null) return null
        
        // 1. Normalize: upper-case and trim
        val text = rawResponse.trim().uppercase()
        
        // If the response explicitly states not found
        if (text.contains("NOT_FOUND") || text.contains("NOTFOUND") || text.contains("COULD NOT FIND") || text.contains("NO VIN")) {
            return null
        }
        
        // 2. Try to find a sequence of exactly 17 alphanumeric characters [A-Z0-9] in the raw text (as a standalone word or with boundaries)
        val standaloneRegex = Regex("\\b[A-Z0-9]{17}\\b")
        val standaloneMatch = standaloneRegex.find(text)?.value
        if (standaloneMatch != null) {
            Log.d("AwdViewModel", "Found standalone 17-character VIN: $standaloneMatch")
            return standaloneMatch
        }
        
        // 3. Try to find any 17-character alphanumeric sequence [A-Z0-9] even if not bounded by word boundaries
        val sequenceRegex = Regex("[A-Z0-9]{17}")
        val sequenceMatch = sequenceRegex.find(text)?.value
        if (sequenceMatch != null) {
            Log.d("AwdViewModel", "Found 17-character alphanumeric sequence: $sequenceMatch")
            return sequenceMatch
        }

        // 4. What if the VIN contains hyphens, spaces, or dots? (e.g. "1C4-HJXN23-KW-123456" or "1C4 HJXN23 KW 123456")
        val words = text.split(Regex("[\\s\\n\\r]+"))
        for (word in words) {
            val cleanedWord = word.replace(Regex("[^A-Z0-9]"), "")
            if (cleanedWord.length == 17) {
                Log.d("AwdViewModel", "Found 17-character cleaned word: $cleanedWord")
                return cleanedWord
            }
        }

        // 5. If we still haven't found a 17-character sequence, let's strip common words and try
        val cleanedText = text
            .replace(Regex("VEHICLE\\s*IDENTIFICATION\\s*NUMBER:?"), "")
            .replace(Regex("VIN\\s*CODE:?"), "")
            .replace(Regex("VIN\\s*NUMBER:?"), "")
            .replace(Regex("VIN\\s*IS:?"), "")
            .replace(Regex("VIN:?"), "")
            .replace(Regex("HERE\\s*IS\\s*THE\\s*VIN:?"), "")
            .replace(Regex("THE\\s*VIN\\s*IS:?"), "")
            .replace(Regex("THIS\\s*IMAGE\\s*SHOWS:?"), "")
            .replace(Regex("[^A-Z0-9]"), "") // remove everything except letters and digits

        val fallbackMatch = Regex("[A-Z0-9]{17}").find(cleanedText)?.value
        if (fallbackMatch != null) {
            Log.d("AwdViewModel", "Found fallback 17-character VIN: $fallbackMatch")
            return fallbackMatch
        }

        return null
    }

    private suspend fun queryParkingBrakeWithGemini(year: String, make: String, model: String, vin: String): String = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Unknown (No API Key)"
        }

        if (_apiProvider.value == "vertex_ai" && _vertexProjectId.value.trim().isEmpty()) {
            return@withContext "Unknown (Vertex AI Project ID not configured)"
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

        val requestUrl = getGeminiRequestUrl()
        try {
            val response = NetworkClient.geminiService.generateContent(requestUrl, apiKey, request)
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
