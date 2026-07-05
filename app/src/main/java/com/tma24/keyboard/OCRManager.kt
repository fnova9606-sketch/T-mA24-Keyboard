package com.tma24.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCRManager
 *
 * Wraps ML Kit's on-device text recognition.
 * All processing is local — zero network calls.
 *
 * Usage from the keyboard:
 *   1. User taps the OCR button in the toolbar
 *   2. An image picker intent is launched by TMA24InputMethodService
 *   3. The selected image URI is passed to recognizeFromUri()
 *   4. The recognised text is committed directly to the input field
 *
 * The recogniser is lazy-initialised and reused across calls
 * to avoid repeated startup cost.
 */
class OCRManager(private val context: Context) {

    // ML Kit Latin text recogniser — bundled on-device, no download needed
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // ── Public API ────────────────────────────────────────────────────

    /**
     * Recognises text from a content URI (e.g., from image picker).
     * Runs on IO dispatcher; safe to call from a coroutine.
     * Returns the full recognised text string, or throws on failure.
     */
    suspend fun recognizeFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val bitmap = uriToBitmap(uri)
        recognizeFromBitmap(bitmap)
    }

    /**
     * Recognises text directly from a Bitmap.
     * Useful if the caller already has a bitmap (e.g., from camera capture).
     */
    suspend fun recognizeFromBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Collect all recognised text blocks in reading order
                    val result = buildString {
                        visionText.textBlocks.forEachIndexed { blockIndex, block ->
                            block.lines.forEachIndexed { lineIndex, line ->
                                append(line.text)
                                // Add newline between lines within a block
                                if (lineIndex < block.lines.size - 1) {
                                    append("\n")
                                }
                            }
                            // Add blank line between blocks (paragraph separation)
                            if (blockIndex < visionText.textBlocks.size - 1) {
                                append("\n\n")
                            }
                        }
                    }
                    continuation.resume(result.trim())
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }

            // If coroutine is cancelled while ML Kit is working, close the task
            continuation.invokeOnCancellation {
                recognizer.close()
            }
        }

    /**
     * Converts a content URI to a Bitmap.
     * Handles both modern (API 29+) and legacy approaches.
     */
    private fun uriToBitmap(uri: Uri): Bitmap {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(
                context.contentResolver,
                uri
            )
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    }

    /**
     * Releases ML Kit resources when the IME service is destroyed.
     * Call from TMA24InputMethodService.onDestroy().
     */
    fun shutdown() {
        recognizer.close()
    }
}