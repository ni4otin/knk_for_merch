package com.knk.scaner

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.knk.scaner.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

// ==================== MODELS ====================

data class DeferredProduct(
    val name: String,
    val unitPrice: String,
    val cardPrice: String,
    val unitProd: String,
    val stock: String,
    val ul: String,
    val startDate: String,
    val endDate: String,
    val attr5: String,
    val attr6: String,
    val attr7: String,
    val barcode: String
)

data class ProductListItem(
    val name: String,
    val price: String,
    val stock: String,
    val barcode: String
)

data class ProductStockItem(
    val shopName: String,
    val description: String,
    val stock: String,
    val price: String
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val description: String
)

// ==================== THEME ====================

private val AppGreen = Color(0xFF2E7D32)
private val AccentGreen = Color(0xFF4CAF50)
private val LightGreenBg = Color(0xFFF1F8E9)

@Composable
fun AppTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = AccentGreen,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF1B391C),
            onPrimaryContainer = Color(0xFFC8E6C9),
            secondary = Color(0xFF81C784),
            onSecondary = Color.White,
            background = Color(0xFF0F120F),
            surface = Color(0xFF1A1F1A),
            onBackground = Color(0xFFE2E3DE),
            onSurface = Color(0xFFE2E3DE),
            outline = Color(0xFF3F493F),
            surfaceVariant = Color(0xFF2A312A)
        )
    } else {
        lightColorScheme(
            primary = AppGreen,
            primaryContainer = LightGreenBg,
            background = Color(0xFFFAFBFA),
            surface = Color.White,
            outline = Color(0xFFDBE5DB)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// ==================== VIEWMODEL ====================

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs: SharedPreferences = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private var fetchJob: Job? = null

    // Shared OkHttpClient for efficiency
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // --- Product State ---
    var scannedBarcode by mutableStateOf("")
    var productName by mutableStateOf("")
    var unitPrice by mutableStateOf("")
    var cardPrice by mutableStateOf("")
    var attr5 by mutableStateOf("")
    var attr6 by mutableStateOf("")
    var attr7 by mutableStateOf("")
    var stock by mutableStateOf("")
    var unitProd by mutableStateOf("")
    var ul by mutableStateOf("")
    var startDate by mutableStateOf("")
    var endDate by mutableStateOf("")

    var showProductCard by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf("")
    var printStatus by mutableStateOf("")
    var scannedPrice by mutableStateOf<Double?>(null)

    // --- Settings State ---
    var lenProdName by mutableIntStateOf(prefs.getInt("lenProdName", 23))
    var shopId by mutableStateOf(prefs.getString("shopId", "15") ?: "15")
    var deviceId by mutableStateOf(prefs.getString("DeviceId", "") ?: "")
    var serverUrl by mutableStateOf(prefs.getString("serverUrl", "") ?: "")
    var selectedPrinterAddress by mutableStateOf(prefs.getString("selectedPrinterAddress", "") ?: "")
    var tsplTemplate by mutableStateOf(prefs.getString("tsplTemplate", getDefaultTemplate()) ?: getDefaultTemplate())
    var isDarkTheme by mutableStateOf(prefs.getBoolean("isDarkTheme", false))
    var isAuthEnabled by mutableStateOf(prefs.getBoolean("isAuthEnabled", false))
    var username by mutableStateOf(prefs.getString("username", "") ?: "")
    var password by mutableStateOf(prefs.getString("password", "") ?: "")
    var apiKey by mutableStateOf(decryptField(prefs.getString("apiKey", "") ?: ""))
    
    // --- UI Flow State ---
    var isSettingsOpen by mutableStateOf(false)
    var showSplash by mutableStateOf(true)
    var warehouses by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var showWarehouseDialog by mutableStateOf(false)
    var showSecretDialog by mutableStateOf(false)
    var knkSecret by mutableStateOf("")

    // --- Search State ---
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<ProductListItem>>(emptyList())
    var searchCurrentPage by mutableIntStateOf(1)
    var showSearchDialog by mutableStateOf(false)
    var isSearchLoading by mutableStateOf(false)

    // --- Stocks State ---
    var productStocks by mutableStateOf<List<ProductStockItem>>(emptyList())
    var showStocksDialog by mutableStateOf(false)
    var isStocksLoading by mutableStateOf(false)

    var showDeferredDialog by mutableStateOf(false)

    var deferredList = mutableStateListOf<DeferredProduct>()

    // --- Update State ---
    var showUpdateDialog by mutableStateOf(false)
    var updateInfo by mutableStateOf<UpdateInfo?>(null)

    init {
        loadDeferredList()
        loadCurrentProduct()
        if (deviceId.isBlank()) {
            deviceId = fetchDeviceId()
            saveSettings()
        }
    }

    fun saveDeferredList() {
        val arr = org.json.JSONArray()
        deferredList.forEach { p ->
            val obj = JSONObject().apply {
                put("name", p.name)
                put("unitPrice", p.unitPrice)
                put("cardPrice", p.cardPrice)
                put("unitProd", p.unitProd)
                put("stock", p.stock)
                put("ul", p.ul)
                put("startDate", p.startDate)
                put("endDate", p.endDate)
                put("attr5", p.attr5)
                put("attr6", p.attr6)
                put("attr7", p.attr7)
                put("barcode", p.barcode)
            }
            arr.put(obj)
        }
        prefs.edit { putString("deferred_list", arr.toString()) }
    }

    private fun loadDeferredList() {
        val data = prefs.getString("deferred_list", null) ?: return
        try {
            val arr = org.json.JSONArray(data)
            deferredList.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                deferredList.add(
                    DeferredProduct(
                        name = obj.getString("name"),
                        unitPrice = obj.getString("unitPrice"),
                        cardPrice = obj.getString("cardPrice"),
                        unitProd = obj.getString("unitProd"),
                        stock = obj.getString("stock"),
                        ul = obj.getString("ul"),
                        startDate = obj.getString("startDate"),
                        endDate = obj.getString("endDate"),
                        attr5 = obj.getString("attr5"),
                        attr6 = obj.getString("attr6"),
                        attr7 = obj.getString("attr7"),
                        barcode = obj.getString("barcode")
                    )
                )
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun saveCurrentProduct() {
        if (!showProductCard) {
            prefs.edit { remove("current_product") }
            return
        }
        val obj = JSONObject().apply {
            put("name", productName)
            put("unitPrice", unitPrice)
            put("cardPrice", cardPrice)
            put("unitProd", unitProd)
            put("stock", stock)
            put("ul", ul)
            put("startDate", startDate)
            put("endDate", endDate)
            put("attr5", attr5)
            put("attr6", attr6)
            put("attr7", attr7)
            put("barcode", scannedBarcode)
        }
        prefs.edit { putString("current_product", obj.toString()) }
    }

    private fun loadCurrentProduct() {
        val data = prefs.getString("current_product", null) ?: return
        try {
            val obj = JSONObject(data)
            productName = obj.optString("name", "")
            unitPrice = obj.optString("unitPrice", "")
            cardPrice = obj.optString("cardPrice", "")
            unitProd = obj.optString("unitProd", "")
            stock = obj.optString("stock", "")
            ul = obj.optString("ul", "")
            startDate = obj.optString("startDate", "")
            endDate = obj.optString("endDate", "")
            attr5 = obj.optString("attr5", "")
            attr6 = obj.optString("attr6", "")
            attr7 = obj.optString("attr7", "")
            scannedBarcode = obj.optString("barcode", "")
            showProductCard = productName.isNotEmpty()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun saveSettings() {
        prefs.edit {
            putString("shopId", shopId)
            putString("DeviceId", deviceId)
            putString("serverUrl", serverUrl)
            putString("selectedPrinterAddress", selectedPrinterAddress)
            putString("tsplTemplate", tsplTemplate)
            putBoolean("isDarkTheme", isDarkTheme)
            putBoolean("isAuthEnabled", isAuthEnabled)
            putString("username", username)
            putString("password", password)
            putString("apiKey", encryptField(apiKey))
            putInt("lenProdName", lenProdName)
        }
    }

    fun encryptField(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val key = SecretKeySpec("KNK_SCAN_SEC_KEY".toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(value.toByteArray())
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) { value }
    }

    fun decryptField(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val key = SecretKeySpec("KNK_SCAN_SEC_KEY".toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val decoded = Base64.decode(value, Base64.NO_WRAP)
            String(cipher.doFinal(decoded))
        } catch (e: Exception) { "" }
    }

    private fun getDefaultTemplate() = """
        REM TSPL-шаблон пример
        SIZE 56 mm,40 mm
        GAP 0 mm,0
        DIRECTION 1
        CLS
        CODEPAGE 1251
        TEXT 10,20,"3",0,1,1,"@Name1@"
        BARCODE 10,100,"128",60,1,0,2,2,"@Barcode@"
        TEXT 10,185,"4",0,1,1,"@Price@ руб"
        PRINT 1
    """.trimIndent()

    private fun fetchDeviceId(): String {
        return try {
            Settings.Secure.getString(getApplication<Application>().contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_id"
        } catch (e: Exception) { "unknown_id" }
    }

    private fun calculateEanChecksum(code: String): Int {
        var sum = 0
        val data = code.dropLast(1).reversed()
        for (i in data.indices) {
            val digit = data[i].toString().toIntOrNull() ?: 0
            sum += if (i % 2 == 0) digit * 3 else digit
        }
        return (10 - (sum % 10)) % 10
    }

    private fun isValidBarcode(barcode: String): Boolean {
        if (barcode.isBlank()) return false
        var clean = barcode.trim().replace("\u001d", "")
        if (clean.startsWith("]d2")) clean = clean.substring(3)

        // 1. PRS format
        if (clean.startsWith("PRS") && clean.length == 22) return true

        // 2. GS1 / Marking
        if (clean.startsWith("01") && clean.length >= 16) return true

        // 3. EAN-13
        if (clean.length == 13 && clean.all { it.isDigit() }) {
            val expected = clean.last().toString().toInt()
            return calculateEanChecksum(clean) == expected
        }

        // 4. EAN-8
        if (clean.length == 8 && clean.all { it.isDigit() }) {
            val expected = clean.last().toString().toInt()
            return calculateEanChecksum(clean) == expected
        }

        return false
    }

    fun onBarcodeScanned(barcode: String?, onInvalid: () -> Unit = {}) {
        if (barcode.isNullOrBlank()) return
        
        if (!isValidBarcode(barcode)) {
            Toast.makeText(getApplication(), "Неверный формат штрихкода", Toast.LENGTH_SHORT).show()
            onInvalid()
            return
        }

        scannedPrice = null
        
        var clean = barcode.trim().replace("\u001d", "")
        if (clean.startsWith("]d2")) clean = clean.substring(3)

        val cleanBarcode: String
        if ((clean.length == 22) && clean.startsWith("PRS")) {
            val priceKop = clean.substring(16, 22).toDoubleOrNull() ?: 0.0
            scannedPrice = priceKop / 100.0
            cleanBarcode = if (clean.startsWith("PRS00000")) clean.substring(8, 16) else clean.substring(3, 16)
        } else {
            cleanBarcode = parseMarkInternal(clean)
        }

        scannedBarcode = cleanBarcode
        errorMessage = ""
        printStatus = ""
        fetchProductData(cleanBarcode)
        saveCurrentProduct()
    }

    private fun parseMarkInternal(clean: String): String {
        if (clean.startsWith("01") && clean.length >= 16) {
            val gtin = clean.substring(2, 16)
            return if (gtin.startsWith("0")) gtin.substring(1) else gtin
        }
        return clean
    }

    private fun buildBaseRequest(url: String, json: JSONObject): Request {
        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val builder = Request.Builder().url(url).post(body)
        if (apiKey.isNotBlank()) builder.addHeader("X-API-KEY", apiKey)
        if (isAuthEnabled) builder.addHeader("Authorization", Credentials.basic(username, password))
        return builder.build()
    }

    fun fetchProductData(barcode: String) {
        if (serverUrl.isBlank()) {
            errorMessage = "Укажите адрес сервера в настройках"
            return
        }
        fetchJob?.cancel()
        showProductCard = false
        isLoading = true
        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("barcode", barcode)
                    put("shop", shopId.toIntOrNull() ?: 15)
                    put("mac", deviceId)
                }
                val request = buildBaseRequest(serverUrl, json)
                val response = httpClient.newCall(request).execute()
                val responseData = response.body.string() ?: ""

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        parseResponse(responseData)
                        saveCurrentProduct()
                    }
                    else errorMessage = "Ошибка сервера: ${response.code}\n$responseData"
                }
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                withContext(Dispatchers.Main) { errorMessage = "Ошибка сети: ${e.message}" }
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    private fun parseResponse(data: String) {
        try {
            val json = JSONObject(data)
            if (json.has("errorCode")) {
                Toast.makeText(getApplication(), json.optString("description", "Ошибка"), Toast.LENGTH_LONG).show()
                return
            }
            val product = if (json.has("product")) json.getJSONObject("product") else json
            productName = product.optString("title", "").takeIf { it != "null" } ?: ""
            unitPrice = product.optString("unitPrice", "").takeIf { it != "null" } ?: "0"
            cardPrice = product.optString("cardPrice", "").takeIf { it != "null" } ?: "0"
            unitProd = product.optString("measure", "").takeIf { it != "null" } ?: ""
            stock = product.optString("stock", "##").takeIf { it != "" } ?: "0"
            ul = product.optString("UL", "").takeIf { it != "null" } ?: ""
            startDate = product.optString("startDate", "").takeIf { it != "null" } ?: ""
            endDate = product.optString("endDate", "").takeIf { it != "null" } ?: ""

            val unitP = unitPrice.replace(",", ".").toDoubleOrNull() ?: 0.0
            val card = cardPrice.replace(",", ".").toDoubleOrNull() ?: 0.0
            
            if (card > 0 && card < unitP) {
                attr5 = "Старая цена - $unitPrice"
                attr6 = "Скидка"
                val percent = ((1.0 - card / unitP) * 100).roundToInt()
                attr7 = "$percent%"
            } else {
                attr5 = ""; attr6 = ""; attr7 = ""
            }
            showProductCard = productName.isNotEmpty()
        } catch (e: Exception) { errorMessage = "Ошибка данных" }
    }

    fun shareSettingsFile(context: Context) {
        try {
            val json = JSONObject().apply {
                put("shopId", shopId); put("serverUrl", serverUrl); put("isAuthEnabled", isAuthEnabled)
                put("username", username); put("password", password); put("apiKey", encryptField(apiKey))
                put("lenProdName", lenProdName); put("tsplTemplate", tsplTemplate)
            }
            val file = File(context.cacheDir, "knk_settings.json")
            file.writeText(json.toString())
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = "application/json"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться настройками"))
        } catch (e: Exception) { errorMessage = "Ошибка: ${e.message}" }
    }

    @SuppressLint("MissingPermission")
    fun printLabel() {
        printStatus = "⏳ Соединение..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bluetoothManager = getApplication<Application>().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                if (!bluetoothManager.adapter.isEnabled) {
                    withContext(Dispatchers.Main) { printStatus = "❌ Bluetooth выключен" }
                    return@launch
                }
                val paired = BluetoothPrintersConnections().list
                val conn = if (selectedPrinterAddress.isEmpty()) BluetoothPrintersConnections.selectFirstPaired()
                           else paired?.find { it.device.address == selectedPrinterAddress }
                
                if (conn == null) {
                    withContext(Dispatchers.Main) { printStatus = "❌ Принтер не найден" }
                    return@launch
                }
                conn.connect()
                delay(1000)

                val dateStr = SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date())
                val finalPrice = if (attr5.isNotEmpty()) cardPrice else unitPrice
                val splitNm = if (lenProdName == 0) productName.length else lenProdName
                
                val priceVal = finalPrice.replace(",", ".").toDoubleOrNull() ?: 0.0
                val priceStr = String.format(Locale.US, "%06d", (priceVal * 100).roundToInt())
                val barcodePart = if (scannedBarcode.length == 8) "00000$scannedBarcode" else scannedBarcode.padStart(13, '0').take(13)
                val attr8 = "PRS$barcodePart$priceStr"

                val cmd = tsplTemplate.ifBlank { getDefaultTemplate() }
                    .replace("@Name1@", productName.take(splitNm))
                    .replace("@Name2@", if (productName.length > splitNm) productName.substring(splitNm, minOf(productName.length, splitNm*2)) else "")
                    .replace("@Name3@", if (productName.length > splitNm*2) productName.substring(splitNm*2, minOf(productName.length, splitNm*3)) else "")
                    .replace("@Barcode@", scannedBarcode)
                    .replace("@Attr8@", attr8)
                    .replace("@Price@", finalPrice)
                    .replace("@Date@", dateStr)
                    .replace("@Attr5@", attr5)
                    .replace("@Attr6@", attr6)
                    .replace("@Attr7@", attr7)
                    .replace("@Attr9@", startDate.substringBefore(" "))
                    .replace("@Attr10@", endDate.substringBefore(" "))
                    .replace("@UL@", ul)
                    .replace("@Measure@", unitProd)

                val formatted = cmd.lines().filter { it.isNotBlank() }.joinToString("\r\n", postfix = "\r\n")
                conn.write(formatted.toByteArray(charset("windows-1251")))
                conn.send()
                withContext(Dispatchers.Main) { printStatus = "⏳ Отправка..." }
                delay(2000)
                conn.disconnect()
                withContext(Dispatchers.Main) { printStatus = "✅ Готово" }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { printStatus = "❌ Ошибка: ${e.message}" }
            }
        }
    }

    fun fetchWarehouses(secret: String? = null) {
        if (serverUrl.isBlank()) { errorMessage = "Укажите URL"; return }
        isLoading = true; errorMessage = ""
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = serverUrl.replace("get_product_by_barcode", "get_warehouses")
                val json = JSONObject().apply {
                    put("mac", deviceId)
                    secret?.let { put("knk_secret", it) }
                }
                val request = buildBaseRequest(url, json)
                val response = httpClient.newCall(request).execute()
                val data = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val list = mutableListOf<Pair<String, String>>()
                        if (data.trim().startsWith("[")) {
                            val arr = org.json.JSONArray(data)
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                list.add(obj.optString("name", "Unknown") to obj.optString("description", ""))
                            }
                        } else {
                            val obj = JSONObject(data)
                            val arr = obj.optJSONArray("warehouses") ?: obj.optJSONArray("data")
                            arr?.let {
                                for (i in 0 until it.length()) {
                                    val item = it.getJSONObject(i)
                                    list.add(item.optString("name", "Unknown") to item.optString("description", ""))
                                }
                            } ?: if (obj.has("name")) list.add(obj.getString("name") to obj.optString("description", "")) else Unit
                        }
                        warehouses = list; showWarehouseDialog = true
                    } else errorMessage = "Ошибка сервера: ${response.code}\n$data"
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { errorMessage = "Ошибка: ${e.message}" } }
            finally { withContext(Dispatchers.Main) { isLoading = false } }
        }
    }

    fun searchProductsByName(page: Int = 1) {
        if (serverUrl.isBlank() || searchQuery.length < 3) return
        searchCurrentPage = page; isSearchLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = serverUrl.replace("get_product_by_barcode", "search_product_by_name")
                val json = JSONObject().apply {
                    put("mac", deviceId); put("query", searchQuery); put("shop", shopId.toIntOrNull() ?: 15)
                    put("only_in_stock", true); put("limit", 30); put("page", searchCurrentPage)
                }
                val request = buildBaseRequest(url, json)
                val response = httpClient.newCall(request).execute()
                val data = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val root = JSONObject(data)
                        val list = mutableListOf<ProductListItem>()
                        val arr = root.optJSONArray("products") ?: org.json.JSONArray()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val rStock = obj.optString("stock", "0")
                            val fStock = rStock.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: rStock
                            list.add(ProductListItem(obj.optString("title", ""), obj.optString("price", "0"), fStock, obj.optString("barcode", "")))
                        }
                        searchResults = list
                    } else errorMessage = "Ошибка сервера: ${response.code}"
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { errorMessage = "Ошибка: ${e.message}" } }
            finally { withContext(Dispatchers.Main) { isSearchLoading = false } }
        }
    }

    fun fetchProductStocks() {
        if (serverUrl.isBlank() || scannedBarcode.isBlank()) return
        isStocksLoading = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = serverUrl.replace("get_product_by_barcode", "get_product_stocks_by_warehouses")
                val json = JSONObject().apply { put("mac", deviceId); put("barcode", scannedBarcode) }
                val request = buildBaseRequest(url, json)
                val response = httpClient.newCall(request).execute()
                val data = response.body?.string() ?: ""

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val root = JSONObject(data)
                        val list = mutableListOf<ProductStockItem>()
                        val arr = root.optJSONArray("data") ?: org.json.JSONArray()
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(ProductStockItem(obj.optString("shop_name", ""), obj.optString("shop_description", ""), obj.optString("stock", "0"), obj.optString("price", "0")))
                        }
                        productStocks = list; showStocksDialog = true
                    } else errorMessage = "Ошибка складов: ${response.code}"
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { errorMessage = "Ошибка: ${e.message}" } }
            finally { withContext(Dispatchers.Main) { isStocksLoading = false } }
        }
    }

    fun storeCurrentProduct() {
        if (productName.isEmpty()) return
        
        // Ограничение списка 30 позициями
        if (deferredList.size >= 30) {
            Toast.makeText(getApplication(), "Список отложенной печати переполнен (макс. 30)", Toast.LENGTH_SHORT).show()
            return
        }

        // Проверка на дубликат по штрихкоду
        if (deferredList.any { it.barcode == scannedBarcode }) {
            Toast.makeText(getApplication(), "Товар уже есть в списке отложенной печати", Toast.LENGTH_SHORT).show()
            return
        }

        deferredList.add(
            DeferredProduct(
                name = productName,
                unitPrice = unitPrice,
                cardPrice = cardPrice,
                unitProd = unitProd,
                stock = stock,
                ul = ul,
                startDate = startDate,
                endDate = endDate,
                attr5 = attr5,
                attr6 = attr6,
                attr7 = attr7,
                barcode = scannedBarcode
            )
        )
        saveDeferredList()
    }

    fun extractDeferredProduct() {
        if (deferredList.isEmpty()) return
        val product = deferredList.removeAt(deferredList.size - 1)
        applyDeferredProduct(product)
    }

    fun extractSpecificProduct(index: Int) {
        if (index !in deferredList.indices) return
        val product = deferredList.removeAt(index)
        applyDeferredProduct(product)
    }

    private fun applyDeferredProduct(product: DeferredProduct) {
        saveDeferredList()
        
        productName = product.name
        unitPrice = product.unitPrice
        cardPrice = product.cardPrice
        unitProd = product.unitProd
        stock = product.stock
        ul = product.ul
        startDate = product.startDate
        endDate = product.endDate
        attr5 = product.attr5
        attr6 = product.attr6
        attr7 = product.attr7
        scannedBarcode = product.barcode
        
        scannedPrice = null
        showProductCard = true
        saveCurrentProduct()
    }

    fun shareDeferredList(context: Context) {
        if (deferredList.isEmpty()) return
        val text = StringBuilder("Список товаров:\n")
        deferredList.forEach { p ->
            val price = if (p.attr5.isNotEmpty()) p.cardPrice else p.unitPrice
            text.append("${p.barcode} | ${p.name} | $price ₽\n")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text.toString())
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться списком"))
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothConnection> {
        return try {
            BluetoothPrintersConnections().list?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearAllData() {
        deferredList.clear()
        showProductCard = false
        productName = ""
        scannedBarcode = ""
        saveDeferredList()
        saveCurrentProduct()
    }

    fun checkAppUpdate() {
        if (serverUrl.isBlank()) return

        val context = getApplication<Application>()
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            return
        }
        val currentCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode.toInt() else packageInfo.versionCode
        
        val pkgName = context.packageName
        val flavor = when {
            pkgName.endsWith(".gms") -> "gms"
            pkgName.endsWith(".zxing") -> "zxing"
            else -> ""
        }
        val fileName = if (flavor.isNotEmpty()) "checkversion_$flavor.json" else "checkversion.json"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = android.net.Uri.parse(serverUrl)
                val scheme = uri.scheme ?: "http"
                val authority = uri.authority ?: ""
                val baseUrl = "$scheme://$authority/"
                val updateUrl = "${baseUrl}knk_app/$fileName"

                val request = Request.Builder().url(updateUrl).get().build()
                val response = httpClient.newCall(request).execute()
                val responseData = response.body.string() ?: ""

                if (response.isSuccessful) {
                    val root = JSONObject(responseData)
                    val latestCode = root.optInt("versionCode", 0)
                    if (latestCode > currentCode) {
                        withContext(Dispatchers.Main) {
                            updateInfo = UpdateInfo(
                                latestCode,
                                root.optString("versionName", "Новая версия"),
                                root.optString("downloadUrl", ""),
                                root.optString("description", "")
                            )
                            showUpdateDialog = true
                        }
                    }
                }
            } catch (e: Exception) {
                // Fail silently
            }
        }
    }
}

// ==================== UI COMPONENTS ====================

@Composable
fun AppContent(viewModel: MainViewModel, onScan: () -> Unit, onPrint: () -> Unit, onImportSettings: () -> Unit) {
    val context = LocalContext.current
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler(enabled = !viewModel.isSettingsOpen && !viewModel.showSplash) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            viewModel.clearAllData()
            (context as? Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, "Нажмите еще раз для выхода", Toast.LENGTH_SHORT).show()
        }
    }

    AppTheme(isDark = viewModel.isDarkTheme) {
        LaunchedEffect(Unit) {
            viewModel.checkAppUpdate()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AnimatedContent(targetState = viewModel.isSettingsOpen, label = "SettingsAnim") { isSettings ->
                    if (isSettings) SettingsScreen(viewModel, onImportSettings)
                    else MainScreen(viewModel, onScan, onPrint)
                }
            }
            if (viewModel.showSplash) SplashScreen(onFinished = { viewModel.showSplash = false })
        }
    }
}

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val configuration = LocalConfiguration.current
    val screenSize = maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp * 2
    val alpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(1f) }
    val textOffset = remember { Animatable(0f) }
    val bgScale = remember { Animatable(1.5f) }
    val rowTranslationX = remember { Animatable(115f) }

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(400)) }
        launch { logoScale.animateTo(1.2f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        delay(600)
        rowTranslationX.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        textOffset.animateTo(1f, tween(500, easing = LinearOutSlowInEasing))
        delay(800)
        launch { bgScale.animateTo(0f, tween(500)) }
        launch { alpha.animateTo(0f, tween(400)) }
        delay(500); onFinished()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(screenSize).scale(bgScale.value).clip(CircleShape).background(Color(0xFFE0E0E0)))
        Row(modifier = Modifier.offset(x = rowTranslationX.value.dp).graphicsLayer(alpha = alpha.value), verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(id = R.drawable.knkgreenlogo), contentDescription = null, modifier = Modifier.size(180.dp).scale(logoScale.value).zIndex(2f))
            Box(modifier = Modifier.height(180.dp).width(260.dp).offset(x = (-40).dp).clipToBounds()) {
                Image(painter = painterResource(id = R.drawable.knktextlogo), contentDescription = null, modifier = Modifier.align(Alignment.CenterStart).height(90.dp).graphicsLayer(translationX = (textOffset.value - 1f) * 260f, alpha = textOffset.value))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onScan: () -> Unit, onPrint: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Image(painter = painterResource(id = R.drawable.knklogo), contentDescription = "Logo", modifier = Modifier.height(48.dp)) },
                actions = {
                    IconButton(onClick = { viewModel.showSearchDialog = true }) { Icon(Icons.Default.Search, "Поиск", tint = MaterialTheme.colorScheme.primary) }
                    IconButton(onClick = { viewModel.isSettingsOpen = true }) { Icon(Icons.Default.Settings, "Настройки", tint = MaterialTheme.colorScheme.primary) }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 8.dp, shadowElevation = 12.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (viewModel.printStatus.isNotEmpty() && viewModel.showProductCard) {
                        Text(viewModel.printStatus, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp), fontSize = 14.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onScan,
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.CenterFocusStrong, null)
                            Spacer(Modifier.width(8.dp))
                            Text("СКАН", fontSize = 18.sp, fontWeight = FontWeight.Black)
                        }

                        if (viewModel.showProductCard && viewModel.selectedPrinterAddress.isNotBlank()) {
                            Button(
                                onClick = onPrint,
                                modifier = Modifier.weight(1f).height(64.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Icon(Icons.Default.Print, null)
                                Spacer(Modifier.width(8.dp))
                                Text("ПЕЧАТЬ", fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (viewModel.showSearchDialog) SearchDialog(viewModel)
        if (viewModel.showStocksDialog) StocksDialog(viewModel)
        if (viewModel.showDeferredDialog) DeferredListDialog(viewModel)
        if (viewModel.showUpdateDialog && viewModel.updateInfo != null) UpdateDialog(viewModel)
        
        Column(modifier = Modifier.padding(padding).padding(horizontal = 20.dp).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            if (viewModel.isLoading || viewModel.isStocksLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            AnimatedVisibility(visible = viewModel.errorMessage.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(viewModel.errorMessage, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                if (viewModel.showProductCard) {
                    ProductInfoCard(viewModel, onPrint)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 60.dp)) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(120.dp), tint = AppGreen.copy(alpha = 0.2f))
                        Text("Нажмите кнопку внизу для начала", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductInfoCard(viewModel: MainViewModel, onPrint: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(viewModel.productName, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                
                val finalPrice = if (viewModel.attr5.isNotEmpty()) viewModel.cardPrice else viewModel.unitPrice
                val serverPrice = finalPrice.replace(",", ".").toDoubleOrNull() ?: 0.0
                val isMismatch = viewModel.scannedPrice != null && kotlin.math.abs(viewModel.scannedPrice!! - serverPrice) > 0.01
                val priceColor = if (isMismatch) Color.Red else MaterialTheme.colorScheme.primary

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(finalPrice, fontSize = 48.sp, fontWeight = FontWeight.Black, color = priceColor, lineHeight = 48.sp)
                    Text(" ₽/${viewModel.unitProd.ifBlank { "ед." }}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = priceColor, modifier = Modifier.padding(bottom = 8.dp))
                }
                
                if (viewModel.attr5.isNotEmpty()) {
                    Text(viewModel.attr5, fontSize = 14.sp, color = Color.Gray)
                    Surface(color = AccentGreen, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Text("СКИДКА: ", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(viewModel.attr7, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                if (viewModel.stock != "##") {
                    Spacer(Modifier.height(8.dp))
                    Text("Остаток: ${viewModel.stock} ${viewModel.unitProd}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.fetchProductStocks() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary), shape = MaterialTheme.shapes.large) {
                    Icon(Icons.Default.Store, null); Spacer(Modifier.width(8.dp)); Text("ГДЕ КУПИТЬ", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (viewModel.selectedPrinterAddress.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.large)
                        .combinedClickable(
                            enabled = viewModel.deferredList.isNotEmpty(),
                            onClick = { viewModel.extractDeferredProduct() },
                            onLongClick = { viewModel.showDeferredDialog = true }
                        ),
                    color = if (viewModel.deferredList.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = if (viewModel.deferredList.isNotEmpty()) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Извлечь (${viewModel.deferredList.size})", fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
                Button(
                    onClick = { viewModel.storeCurrentProduct() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text("Запомнить", fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onImportSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (viewModel.showWarehouseDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showWarehouseDialog = false },
            title = { Text("Выберите склад") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    viewModel.warehouses.forEach { (name, description) ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { viewModel.shopId = name; viewModel.saveSettings(); viewModel.showWarehouseDialog = false }.padding(16.dp)) {
                            Text(text = name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            if (description.isNotEmpty()) Text(text = description, fontSize = 14.sp, color = Color.Gray)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.showWarehouseDialog = false }) { Text("Закрыть") } }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                    else listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = perms.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val content = stream.bufferedReader(charset("windows-1251")).readText()
                        withContext(Dispatchers.Main) { viewModel.tsplTemplate = content; viewModel.saveSettings(); Toast.makeText(context, "Загружено", Toast.LENGTH_SHORT).show() }
                    }
                } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() } }
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.saveSettings(); viewModel.isSettingsOpen = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {
            if (viewModel.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            AnimatedVisibility(visible = viewModel.errorMessage.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(viewModel.errorMessage, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
                }
            }

            SettingsCard("Инфо") {
                Text("ID Устройства: ${viewModel.deviceId}", fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Темная тема", modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.isDarkTheme, onCheckedChange = { viewModel.isDarkTheme = it; viewModel.saveSettings() })
                }
            }

            SettingsCard("Сервер") {
                OutlinedTextField(value = viewModel.serverUrl, onValueChange = { viewModel.serverUrl = it; viewModel.saveSettings() }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = viewModel.shopId, onValueChange = { viewModel.shopId = it; viewModel.saveSettings() }, label = { Text("Магазин") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.fetchWarehouses() }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("ВЫБРАТЬ", fontSize = 12.sp) }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.showSecretDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("ЗАПРОСИТЬ ДОСТУП") }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Авторизация", modifier = Modifier.weight(1f))
                    Switch(checked = viewModel.isAuthEnabled, onCheckedChange = { viewModel.isAuthEnabled = it; viewModel.saveSettings() })
                }
                if (viewModel.isAuthEnabled) {
                    OutlinedTextField(value = viewModel.username, onValueChange = { viewModel.username = it; viewModel.saveSettings() }, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = viewModel.password, onValueChange = { viewModel.password = it; viewModel.saveSettings() }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            }

            SettingsCard("Печать") {
                Text("ПРИНТЕР", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                viewModel.getPairedPrinters().forEach { printer ->
                    @SuppressLint("MissingPermission")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { viewModel.selectedPrinterAddress = printer.device.address; viewModel.saveSettings() }.padding(4.dp)) {
                        RadioButton(selected = viewModel.selectedPrinterAddress == printer.device.address, onClick = { viewModel.selectedPrinterAddress = printer.device.address; viewModel.saveSettings() })
                        Text(printer.device.name ?: "Unknown", modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { pickFileLauncher.launch("*/*") }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                    Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(8.dp)); Text("ЗАГРУЗИТЬ ШАБЛОН")
                }
                OutlinedTextField(value = viewModel.tsplTemplate, onValueChange = { viewModel.tsplTemplate = it }, label = { Text("TSPL код") }, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp), textStyle = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace))
                OutlinedTextField(value = viewModel.lenProdName.toString(), onValueChange = { viewModel.lenProdName = it.toIntOrNull() ?: 0; viewModel.saveSettings() }, label = { Text("Переносить по") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number))
            }

            SettingsCard("Действия") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.shareSettingsFile(context) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null); Text("SHARE", fontSize = 11.sp) }
                    Button(onClick = onImportSettings, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Icon(Icons.Default.FileDownload, null); Text("IMPORT", fontSize = 11.sp) }
                }
            }
            Button(onClick = { viewModel.saveSettings(); viewModel.isSettingsOpen = false }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp).height(64.dp)) { Text("СОХРАНИТЬ") }
        }
    }

    if (viewModel.showSecretDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showSecretDialog = false },
            title = { Text("Пароль доступа") },
            text = { OutlinedTextField(value = viewModel.knkSecret, onValueChange = { viewModel.knkSecret = it }, label = { Text("Пароль") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password)) },
            confirmButton = { Button(onClick = { viewModel.showSecretDialog = false; viewModel.fetchWarehouses(viewModel.knkSecret); viewModel.knkSecret = "" }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { viewModel.showSecretDialog = false }) { Text("Отмена") } }
        )
    }
}

@Composable
fun UpdateDialog(viewModel: MainViewModel) {
    val context = LocalContext.current
    val info = viewModel.updateInfo ?: return

    AlertDialog(
        onDismissRequest = { viewModel.showUpdateDialog = false },
        title = { Text("Доступно обновление") },
        text = {
            Column {
                Text("Версия: ${info.versionName}", fontWeight = FontWeight.Bold)
                if (info.description.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(info.description)
                }
                Spacer(Modifier.height(8.dp))
                Text("Хотите скачать обновление?")
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.showUpdateDialog = false
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(info.downloadUrl))
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("СКАЧАТЬ")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.showUpdateDialog = false }) {
                Text("ПОЗЖЕ")
            }
        }
    )
}

@Composable
fun DeferredListDialog(viewModel: MainViewModel) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = { viewModel.showDeferredDialog = false },
        title = { Text("Отложенная печать") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                if (viewModel.deferredList.isEmpty()) {
                    Text("Список пуст", modifier = Modifier.padding(vertical = 20.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(viewModel.deferredList) { index, item ->
                            Column(modifier = Modifier.fillMaxWidth().clickable {
                                viewModel.extractSpecificProduct(index)
                                viewModel.showDeferredDialog = false
                            }.padding(vertical = 8.dp)) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val price = if (item.attr5.isNotEmpty()) item.cardPrice else item.unitPrice
                                    Text("$price ₽", color = MaterialTheme.colorScheme.primary)
                                    Text(item.barcode, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.shareDeferredList(context) },
                        modifier = Modifier.weight(1f),
                        enabled = viewModel.deferredList.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Share, "Поделиться")
                    }
                    Button(
                        onClick = { 
                            viewModel.deferredList.clear()
                            viewModel.saveDeferredList()
                            viewModel.showDeferredDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = viewModel.deferredList.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Delete, "Очистить")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.showDeferredDialog = false }) { Text("ЗАКРЫТЬ") }
        }
    )
}

@Composable
fun StocksDialog(viewModel: MainViewModel) {
    AlertDialog(
        onDismissRequest = { viewModel.showStocksDialog = false },
        title = { Text("Наличие") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                items(viewModel.productStocks, key = { it.shopName + it.description }) { item ->
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text(item.description.ifBlank { "Склад ${item.shopName}" }, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.price} ₽", color = MaterialTheme.colorScheme.primary)
                            Text("Остаток: ${item.stock}", color = if ((item.stock.toDoubleOrNull() ?: 0.0) > 0) AppGreen else Color.Gray)
                        }
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.showStocksDialog = false }) { Text("ЗАКРЫТЬ") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchDialog(viewModel: MainViewModel) {
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(
        onDismissRequest = { viewModel.showSearchDialog = false },
        title = { Text("Поиск") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
                OutlinedTextField(
                    value = viewModel.searchQuery, onValueChange = { viewModel.searchQuery = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    trailingIcon = { IconButton(onClick = { keyboard?.hide(); viewModel.searchProductsByName(1) }, enabled = viewModel.searchQuery.length >= 3 && !viewModel.isSearchLoading) { Icon(Icons.Default.Search, null) } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (viewModel.searchQuery.length >= 3) { keyboard?.hide(); viewModel.searchProductsByName(1) } })
                )
                if (viewModel.isSearchLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                LazyColumn(modifier = Modifier.weight(1f).heightIn(max = 400.dp)) {
                    items(viewModel.searchResults, key = { it.barcode + it.name }) { item ->
                        Column(modifier = Modifier.fillMaxWidth().clickable { viewModel.showSearchDialog = false; viewModel.onBarcodeScanned(item.barcode) }.padding(12.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.price} ₽", color = MaterialTheme.colorScheme.primary)
                                Text("Остаток: ${item.stock}")
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (viewModel.searchResults.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.searchProductsByName(viewModel.searchCurrentPage - 1) }, enabled = viewModel.searchCurrentPage > 1 && !viewModel.isSearchLoading) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                        Text("Стр. ${viewModel.searchCurrentPage}", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { viewModel.searchProductsByName(viewModel.searchCurrentPage + 1) }, enabled = viewModel.searchResults.size >= 30 && !viewModel.isSearchLoading) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.showSearchDialog = false }) { Text("ЗАКРЫТЬ") } }
    )
}

@Composable
fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppGreen, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

// ==================== ACTIVITY ====================

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var scanner: ScannerWrapper

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val barcode = intent.getStringExtra("barcodeData")
                ?: intent.getStringExtra("scannerdata")
                ?: intent.getStringExtra("BARCODE")
                ?: intent.getStringExtra("barcode_string")
                ?: intent.getStringExtra("data")
            if (!barcode.isNullOrBlank()) {
                viewModel.onBarcodeScanned(barcode)
            }
        }
    }

    private val importSettingsLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            contentResolver.openInputStream(it)?.use { stream ->
                try {
                    val json = JSONObject(stream.bufferedReader().readText())
                    viewModel.shopId = json.optString("shopId", viewModel.shopId)
                    viewModel.serverUrl = json.optString("serverUrl", viewModel.serverUrl)
                    viewModel.isAuthEnabled = json.optBoolean("isAuthEnabled", viewModel.isAuthEnabled)
                    viewModel.username = json.optString("username", viewModel.username)
                    viewModel.password = json.optString("password", viewModel.password)
                    val rawApiKey = json.optString("apiKey", "")
                    viewModel.apiKey = if (rawApiKey.contains("=")) viewModel.decryptField(rawApiKey) else rawApiKey
                    viewModel.lenProdName = json.optInt("lenProdName", viewModel.lenProdName)
                    viewModel.tsplTemplate = json.optString("tsplTemplate", viewModel.tsplTemplate)
                    viewModel.saveSettings()
                    Toast.makeText(this, "Импортировано", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) viewModel.printLabel()
        else Toast.makeText(this, "Нужны разрешения Bluetooth", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanner = ScannerImpl(this)
        
        checkAndRequestBatteryOptimization()

        setContent {
            AppContent(
                viewModel = viewModel, 
                onScan = { startScanning() },
                onPrint = { checkPermissionsAndPrint() },
                onImportSettings = { importSettingsLauncher.launch("application/json") }
            ) 
        }
    }

    override fun onResume() {
        super.onResume()
        registerScanReceiver()
        enableTsdScanner(true)
        restoreBroadcastProfile()
    }

    override fun onPause() {
        super.onPause()
        unregisterScanReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScanReceiver()
    }

    private fun registerScanReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction("scan.rcv.message")
                addAction("com.android.server.scannerservice.broadcast")
                addAction("com.barcode.sendBroadcast")
                addAction("android.intent.ACTION_DECODE_DATA")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(scanReceiver, filter, RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(scanReceiver, filter)
            }
        } catch (_: Exception) {}
    }

    private fun unregisterScanReceiver() {
        try {
            unregisterReceiver(scanReceiver)
        } catch (_: Exception) {}
    }

    private fun enableTsdScanner(enable: Boolean) {
        try {
            // Команды включения/пробуждения сканера для Sci Moby One, Chainway, iData, Urovo, Sunmi, Zebra и др.
            sendBroadcast(Intent("com.rfid.SCAN_ENABLE").putExtra("enabled", enable))
            sendBroadcast(Intent("android.intent.ACTION_DECODE_ENABLE").putExtra("enable", enable))
            sendBroadcast(Intent("com.android.scanner.ENABLED").putExtra("enabled", enable))
            sendBroadcast(Intent("com.scan.service.action.ENABLE").putExtra("enabled", enable))
            sendBroadcast(Intent("com.symbol.datawedge.api.ACTION").putExtra("com.symbol.datawedge.api.ENABLE_PLUGIN", "BARCODE"))
        } catch (_: Exception) {}
    }

    private fun restoreBroadcastProfile() {
        try {
            // 1. Сброс и настройка службы сканера для Sci Moby One / MobyScan / Android Scanner Service
            sendBroadcast(Intent("com.android.scanner.service_settings").apply {
                putExtra("action_barcode_broadcast", "scan.rcv.message")
                putExtra("key_barcode_broadcast", "barcodeData")
                putExtra("end_char", 0) // Без автоматического \n
                putExtra("scan_mode", 0) // Mode 0 = Broadcast
            })
            sendBroadcast(Intent("com.scan.service.action.SETTING").apply {
                putExtra("action_barcode_broadcast", "scan.rcv.message")
                putExtra("key_barcode_broadcast", "barcodeData")
                putExtra("mode", 0)
            })

            // 2. Zebra DataWedge
            val profileName = "KNK_Scaner_Profile"
            val configBundle = Bundle().apply {
                putString("PROFILE_NAME", profileName)
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")

                val appConfig = Bundle().apply {
                    putString("PACKAGE_NAME", packageName)
                    putStringArray("ACTIVITY_LIST", arrayOf("*"))
                }
                putParcelableArray("APP_LIST", arrayOf(appConfig))

                val intentConfig = Bundle().apply {
                    putString("PLUGIN_NAME", "INTENT")
                    putString("RESET_CONFIG", "true")
                    val intentProps = Bundle().apply {
                        putString("intent_output_enabled", "true")
                        putString("intent_action", "scan.rcv.message")
                        putString("intent_category", "android.intent.category.DEFAULT")
                        putString("intent_delivery", "0") // 0 = Broadcast
                    }
                    putBundle("PARAM_LIST", intentProps)
                }
                putParcelableArrayList("PLUGIN_CONFIGURATOR", arrayListOf(intentConfig))
            }
            sendBroadcast(Intent("com.symbol.datawedge.api.ACTION").putExtra("WM_SET_CONFIG", configBundle))

            // 3. Sunmi / Honeywell
            sendBroadcast(Intent("com.sunmi.scanner.ACTION_SETTING").apply {
                putExtra("buildIn", true)
                putExtra("output_mode", 2)
            })
            sendBroadcast(Intent("com.honeywell.decode.intent.action.CLAIM_SCANNER"))
        } catch (_: Exception) {}
    }

    @SuppressLint("BatteryLife")
    private fun checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as? PowerManager
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun checkPermissionsAndPrint() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                    else listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            val bm = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            if (bm.adapter?.isEnabled == true) viewModel.printLabel()
            else Toast.makeText(this, "Включите Bluetooth!", Toast.LENGTH_SHORT).show()
        } else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startScanning() {
        scanner.startScan { result ->
            viewModel.onBarcodeScanned(result) {
                // Если штрихкод неверный, пробуем еще раз
                startScanning()
            }
        }
    }
}
