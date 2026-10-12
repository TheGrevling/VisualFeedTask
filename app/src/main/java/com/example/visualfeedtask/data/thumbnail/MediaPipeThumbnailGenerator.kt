package com.example.visualfeedtask.data.thumbnail

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AtomicFile
import com.example.visualfeedtask.core.di.DefaultDispatcher
import com.example.visualfeedtask.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaPipeThumbnailGenerator internal constructor(
    private val thumbnailDirectory: File,
    private val modelDirectory: File,
    private val engine: ThumbnailImageEngine,
    private val defaultDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
) : ThumbnailGenerator {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        engine: MediaPipeImageEngine,
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(
        File(context.filesDir, "thumbnails"),
        File(context.noBackupFilesDir, "image_generator"),
        engine,
        defaultDispatcher,
        ioDispatcher,
    )

    private val generationMutex = Mutex()

    override suspend fun generateThumbnail(storyId: Long, title: String): String {
        require(storyId > 0) { "Story ID must be positive" }
        return generationMutex.withLock {
            val file = File(thumbnailDirectory, "$storyId.jpg")
            val cached = withContext(ioDispatcher) {
                val atomicFile = AtomicFile(file)
                if (!file.exists() && !File("${file.path}.bak").exists()) {
                    false
                } else {
                    atomicFile.openRead().use { input ->
                        input.channel.size() <= MAX_THUMBNAIL_BYTES && isThumbnail(input.readBytes())
                    }
                }
            }
            if (cached) return@withLock file.absolutePath

            require(title.isNotBlank()) { "Story title must not be blank" }
            withContext(ioDispatcher) {
                if (!modelDirectory.isDirectory ||
                    modelDirectory.listFiles()?.any { it.isFile && it.length() > 0 } != true
                ) {
                    throw IOException(
                        "MediaPipe model is missing. Install converted model files in $modelDirectory",
                    )
                }
            }
            val bytes = withContext(defaultDispatcher) {
                // No suspension inside the engine: GPU creation, use and close share one thread.
                engine.generate(modelDirectory, title, (storyId xor (storyId ushr 32)).toInt())
            }
            currentCoroutineContext().ensureActive()
            withContext(ioDispatcher) {
                if (!isThumbnail(bytes)) {
                    throw IOException("Image generation returned an invalid or oversized thumbnail")
                }
                if (!thumbnailDirectory.isDirectory && !thumbnailDirectory.mkdirs()) {
                    throw IOException("Cannot create thumbnail directory: $thumbnailDirectory")
                }
                val atomicFile = AtomicFile(file)
                val output = atomicFile.startWrite()
                try {
                    output.write(bytes)
                    currentCoroutineContext().ensureActive()
                    atomicFile.finishWrite(output)
                } catch (failure: Exception) {
                    atomicFile.failWrite(output)
                    throw failure
                }
            }
            file.absolutePath
        }
    }

    private fun isThumbnail(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > MAX_THUMBNAIL_BYTES) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth != THUMBNAIL_WIDTH || bounds.outHeight != THUMBNAIL_HEIGHT ||
            bounds.outMimeType != "image/jpeg"
        ) return false
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        bitmap.recycle()
        return true
    }

    internal companion object {
        const val THUMBNAIL_WIDTH = 320
        const val THUMBNAIL_HEIGHT = 180
        const val MAX_THUMBNAIL_BYTES = 64 * 1024
    }
}
