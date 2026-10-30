package top.xing2387.mihotier.ui.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeUtils {

    /**
     * Generate a high-contrast QR code bitmap.
     *
     * @param content The text/URL content to encode.
     * @param sizePx Pixel dimension (width and height).
     * @param margin Margin around the QR code matrix (0-4).
     * @return Generated Bitmap, or null if encoding failed.
     */
    fun generateQrCodeBitmap(
        content: String,
        sizePx: Int = 800,
        margin: Int = 1
    ): Bitmap? {
        if (content.isBlank()) return null
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to margin,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
            )
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decode QR code string from a standard Bitmap (e.g. chosen from gallery).
     */
    fun decodeQrCodeFromBitmap(bitmap: Bitmap): String? {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(
            DecodeHintType.CHARACTER_SET to "UTF-8",
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )

        val reader = MultiFormatReader()
        reader.setHints(hints)

        // 1. Try HybridBinarizer (Best for general images)
        try {
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            return reader.decodeWithState(binaryBitmap).text
        } catch (_: Exception) {}

        // 2. Fallback to GlobalHistogramBinarizer (Best for low-contrast/flat photos)
        try {
            val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
            return reader.decodeWithState(binaryBitmap).text
        } catch (_: Exception) {}

        return null
    }
}
