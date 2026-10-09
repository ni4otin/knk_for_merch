package com.knk.scaner

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class ScannerImpl(private val activity: ComponentActivity) : ScannerWrapper {
    
    private var onScanResult: ((String?) -> Unit)? = null
    
    private val scanLauncher: ActivityResultLauncher<ScanOptions> = 
        activity.registerForActivityResult(ScanContract()) { result ->
            onScanResult?.invoke(result.contents)
        }

    override fun startScan(onResult: (String?) -> Unit) {
        this.onScanResult = onResult
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(
                ScanOptions.EAN_13,
                ScanOptions.EAN_8,
                ScanOptions.DATA_MATRIX,
                ScanOptions.QR_CODE
            )
            addExtra("TRY_HARDER", true)
            setPrompt("Наведите на штрихкод или QR-код")
            setBeepEnabled(true)
            setOrientationLocked(false)
            setCaptureActivity(VerticalCaptureActivity::class.java)
        }
        scanLauncher.launch(options)
    }
}

class VerticalCaptureActivity : com.journeyapps.barcodescanner.CaptureActivity()

