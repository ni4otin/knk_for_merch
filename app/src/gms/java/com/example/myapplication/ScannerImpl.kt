package com.knk.scaner

import androidx.activity.ComponentActivity
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import android.widget.Toast

class ScannerImpl(private val activity: ComponentActivity) : ScannerWrapper {
    override fun startScan(onResult: (String?) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_QR_CODE
            )
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(activity, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                onResult(barcode.rawValue)
            }
            .addOnFailureListener { e ->
                Toast.makeText(activity, "Ошибка GMS: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
