package dev.jelly.qr

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Thin wrapper around the Google Play Services Code Scanner. Triggered by the
 * settings sheet's "Scan QR" trailing-icon button so QA can pair with
 * jelly-local-sync by scanning the QR shown on the laptop instead of typing
 * the per-session URL.
 *
 * The scanner UI runs out-of-process inside Play Services, so:
 *   - The host app does NOT need a `CAMERA` permission in its manifest.
 *   - On devices without Play Services (e.g. some Huawei, FireOS), [scan]
 *     surfaces the failure to [onError] and the user falls back to typing.
 *   - The user dismissing the scanner counts as success-with-no-value;
 *     [onResult] simply isn't called.
 *
 * Restricted to QR format because the only thing we ever pair off is a
 * jelly-local-sync endpoint URL — narrowing the format avoids the scanner
 * spuriously triggering on linear barcodes in the camera frame.
 */
internal object QrScanner {
    fun scan(
        context: Context,
        onResult: (String) -> Unit,
        onError: (Throwable) -> Unit = {},
    ) {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.trim()?.takeIf { it.isNotBlank() }?.let(onResult)
                }
                .addOnFailureListener(onError)
        } catch (t: Throwable) {
            onError(t)
        }
    }
}
