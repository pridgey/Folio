package dev.pridgey.folio.data

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Where the words are on a cover: titles, logos, credits, volume numbers. The bookcase uses it to cut
 * spines from the art between them. Each cover is read once, on the device, with ML Kit's Japanese and
 * Korean recognisers (both also read Latin script), and the result is kept.
 */
class CoverText(private val context: Context, private val scope: CoroutineScope) {
    /** A cover's shape (width over height) and its text, each box as [left, top, right, bottom] in 0..1. */
    data class Scan(val aspect: Float, val boxes: List<FloatArray>)

    private val prefs = context.getSharedPreferences("cover_text", Context.MODE_PRIVATE)
    private val _scans = MutableStateFlow(load())
    val scans: StateFlow<Map<String, Scan>> = _scans.asStateFlow()

    private val pending = HashSet<String>()
    // One cover at a time: this is background work and shouldn't compete with scrolling.
    private val lock = Mutex()
    private val recognizers: List<TextRecognizer> by lazy {
        listOf(
            TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build()),
        )
    }

    /** Reads the cover [model] (anything Coil loads), filed under [key], unless it already has been. */
    fun request(key: String, model: Any) {
        if (key in _scans.value) return
        synchronized(pending) { if (!pending.add(key)) return }
        scope.launch(Dispatchers.Default) {
            try {
                val scan = lock.withLock { runCatching { read(model) }.getOrNull() } ?: return@launch
                prefs.edit().putString(key, encode(scan)).apply()
                _scans.update { it + (key to scan) }
            } catch (_: Throwable) {
                // No recogniser on this device, or the image wouldn't load: the spine just goes without.
            } finally {
                synchronized(pending) { pending.remove(key) }
            }
        }
    }

    private suspend fun read(model: Any): Scan? {
        val request = ImageRequest.Builder(context).data(model).size(Size(720, 1080)).allowHardware(false).build()
        val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap() ?: return null
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val image = InputImage.fromBitmap(bitmap, 0)
        val boxes = recognizers.flatMap { recognizer ->
            recognizer.process(image).await().textBlocks.flatMap { block -> block.lines.mapNotNull { it.boundingBox } }
        }.map { r -> floatArrayOf(r.left / w, r.top / h, r.right / w, r.bottom / h) }
        return Scan(w / h, boxes)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private fun encode(scan: Scan) = "${scan.aspect}|" + scan.boxes.joinToString(";") { b -> b.joinToString(",") }

    private fun load(): Map<String, Scan> = prefs.all.mapNotNull { (key, value) ->
        val text = value as? String ?: return@mapNotNull null
        val aspect = text.substringBefore('|').toFloatOrNull() ?: return@mapNotNull null
        val boxes = text.substringAfter('|', "").split(';').filter { it.isNotBlank() }.mapNotNull { box ->
            box.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 4 }?.toFloatArray()
        }
        key to Scan(aspect, boxes)
    }.toMap()
}
