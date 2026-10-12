package com.example.visualfeedtask.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.visualfeedtask.data.thumbnail.MediaPipeThumbnailGenerator.Companion.MAX_THUMBNAIL_BYTES
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaPipeThumbnailGeneratorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val directory by lazy { File(temporaryFolder.root, "thumbnails") }
    private val model by lazy {
        temporaryFolder.newFolder("image_generator").apply {
            File(this, "test-model.bin").writeText("fake model for injected engine")
        }
    }

    @Test
    fun `concurrent requests and recreated generator reuse story ID despite changed title`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var calls = 0
        val bytes = jpeg()
        val engine = ThumbnailImageEngine { _, _, _ -> calls++; bytes }
        val generator = MediaPipeThumbnailGenerator(directory, model, engine, dispatcher, dispatcher)

        val paths = List(5) { async { generator.generateThumbnail(7, "Title $it") } }.awaitAll()
        assertEquals(1, calls)
        assertEquals(1, paths.distinct().size)
        val recreated = MediaPipeThumbnailGenerator(
            directory, File(temporaryFolder.root, "missing-model"), engine, dispatcher, dispatcher,
        )
        assertEquals(paths.first(), recreated.generateThumbnail(7, "Changed title"))
        assertEquals(1, calls)
        assertTrue(File(paths.first()).readBytes().contentEquals(bytes))

        generator.generateThumbnail(8, "Another story")
        assertEquals(2, calls)
    }

    @Test
    fun `missing model fails explicitly without generating or writing`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val generator = MediaPipeThumbnailGenerator(
            directory, File(temporaryFolder.root, "missing"),
            ThumbnailImageEngine { _, _, _ -> error("Engine should not run") },
            dispatcher, dispatcher,
        )
        try {
            generator.generateThumbnail(7, "Story")
            error("Expected missing model failure")
        } catch (failure: IOException) {
            assertTrue(failure.message!!.contains("model is missing"))
        }
        assertFalse(directory.exists())
    }

    @Test
    fun `corrupt cached image is regenerated`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        directory.mkdirs()
        File(directory, "7.jpg").writeText("not an image")
        var calls = 0
        val generator = MediaPipeThumbnailGenerator(
            directory, model, ThumbnailImageEngine { _, _, _ -> calls++; jpeg() },
            dispatcher, dispatcher,
        )
        val path = generator.generateThumbnail(7, "Story")
        assertEquals(1, calls)
        assertEquals(320, BitmapFactory.decodeFile(path).width)
    }

    @Test
    fun `inference failure permits retry without caching a failed result`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var calls = 0
        val generator = MediaPipeThumbnailGenerator(
            directory, model,
            ThumbnailImageEngine { _, _, _ ->
                if (++calls == 1) throw IOException("Inference failed")
                jpeg()
            },
            dispatcher, dispatcher,
        )
        try {
            generator.generateThumbnail(7, "Story")
            error("Expected inference failure")
        } catch (failure: IOException) {
            assertEquals("Inference failed", failure.message)
        }
        assertFalse(File(directory, "7.jpg").exists())
        assertTrue(File(generator.generateThumbnail(7, "Story")).exists())
        assertEquals(2, calls)
    }

    @Test
    fun `invalid oversized and wrong dimension output never gets cached`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        for (bytes in listOf(byteArrayOf(1), ByteArray(MAX_THUMBNAIL_BYTES + 1), jpeg(512, 512))) {
            val generator = MediaPipeThumbnailGenerator(
                directory, model, ThumbnailImageEngine { _, _, _ -> bytes }, dispatcher, dispatcher,
            )
            try {
                generator.generateThumbnail(7, "Story")
                error("Expected invalid image failure")
            } catch (failure: IOException) {
                assertTrue(failure.message!!.contains("invalid or oversized"))
            }
            assertFalse(File(directory, "7.jpg").exists())
        }
    }

    @Test
    fun `cancellation is propagated without creating a cache entry`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val generator = MediaPipeThumbnailGenerator(
            directory, model,
            ThumbnailImageEngine { _, _, _ -> throw CancellationException("Cancelled") },
            dispatcher, dispatcher,
        )
        try {
            generator.generateThumbnail(7, "Story")
            error("Expected cancellation")
        } catch (failure: CancellationException) {
            assertEquals("Cancelled", failure.message)
        }
        assertFalse(directory.exists())
    }

    @Test
    fun `cancellation during synchronous inference discards the resulting image`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        lateinit var request: Job
        val bytes = jpeg()
        val generator = MediaPipeThumbnailGenerator(
            directory, model,
            ThumbnailImageEngine { _, _, _ ->
                request.cancel()
                bytes
            },
            dispatcher, dispatcher,
        )
        request = launch(start = CoroutineStart.LAZY) { generator.generateThumbnail(7, "Story") }
        request.start()
        request.join()
        assertTrue(request.isCancelled)
        assertFalse(directory.exists())
    }

    @Test
    fun `interrupted atomic write recovers the previously cached image`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        directory.mkdirs()
        val bytes = jpeg()
        File(directory, "7.jpg.bak").writeBytes(bytes)
        File(directory, "7.jpg").writeText("interrupted write")
        val generator = MediaPipeThumbnailGenerator(
            directory, File(temporaryFolder.root, "missing"),
            ThumbnailImageEngine { _, _, _ -> error("Cache should be recovered without inference") },
            dispatcher, dispatcher,
        )
        val path = generator.generateThumbnail(7, "Story")
        assertTrue(File(path).readBytes().contentEquals(bytes))
        assertFalse(File(directory, "7.jpg.bak").exists())
    }

    @Test
    fun `storage failure propagates without returning a successful path`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        directory.writeText("file obstructing directory creation")
        val bytes = jpeg()
        val generator = MediaPipeThumbnailGenerator(
            directory, model, ThumbnailImageEngine { _, _, _ -> bytes }, dispatcher, dispatcher,
        )
        try {
            generator.generateThumbnail(7, "Story")
            error("Expected storage failure")
        } catch (failure: IOException) {
            assertTrue(failure.message!!.contains("Cannot create thumbnail directory"))
        }
    }

    @Test
    fun `engine runs on the injected background dispatcher`() = runTest {
        Executors.newSingleThreadExecutor { Thread(it, "thumbnail-inference") }
            .asCoroutineDispatcher().use { background ->
                val bytes = jpeg()
                val generator = MediaPipeThumbnailGenerator(
                    directory, model,
                    ThumbnailImageEngine { _, _, _ ->
                        assertEquals("thumbnail-inference", Thread.currentThread().name)
                        bytes
                    },
                    background, StandardTestDispatcher(testScheduler),
                )
                generator.generateThumbnail(7, "Story")
            }
    }

    @Test
    fun `real encoder crops noisy input to 16 by 9 and enforces byte limit`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val source = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        try {
            val random = Random(7)
            val pixels = IntArray(512 * 512) { random.nextInt() or 0xff000000.toInt() }
            source.setPixels(pixels, 0, 512, 0, 0, 512, 512)
            val bytes = MediaPipeImageEngine(context).encodeThumbnail(source)
            assertTrue(bytes.size <= MAX_THUMBNAIL_BYTES)
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            try {
                assertEquals(320, decoded.width)
                assertEquals(180, decoded.height)
            } finally {
                decoded.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    @Test
    fun `invalid story ID fails before filesystem access`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val generator = MediaPipeThumbnailGenerator(
            directory, File(temporaryFolder.root, "missing"),
            ThumbnailImageEngine { _, _, _ -> error("Engine should not run") },
            dispatcher, dispatcher,
        )
        try {
            generator.generateThumbnail(0, "Story")
            error("Expected invalid ID failure")
        } catch (failure: IllegalArgumentException) {
            assertEquals("Story ID must be positive", failure.message)
        }
    }

    private fun jpeg(width: Int = 320, height: Int = 180): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output))
            return output.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
