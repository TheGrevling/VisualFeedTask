package com.example.visualfeedtask.data.thumbnail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.visualfeedtask.BuildConfig
import com.example.visualfeedtask.data.thumbnail.MediaPipeThumbnailGenerator.Companion.MAX_THUMBNAIL_BYTES
import com.example.visualfeedtask.data.thumbnail.MediaPipeThumbnailGenerator.Companion.THUMBNAIL_HEIGHT
import com.example.visualfeedtask.data.thumbnail.MediaPipeThumbnailGenerator.Companion.THUMBNAIL_WIDTH
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject

internal fun interface ThumbnailImageEngine {
    fun generate(modelDirectory: File, title: String, seed: Int): ByteArray
}

class MediaPipeImageEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : ThumbnailImageEngine {
    override fun generate(modelDirectory: File, title: String, seed: Int): ByteArray {
        val options = ImageGenerator.ImageGeneratorOptions.builder()
            .setImageGeneratorModelDirectory(modelDirectory.absolutePath)
            .build()
        val generator = ImageGenerator.createFromOptions(context, options)
        try {
            val prompt = "Flat minimalist editorial background, aesthetic color palette, " +
                "no text, no letters, inspired by: ${title.take(300)}"
            val result = generator.generate(prompt, BuildConfig.THUMBNAIL_INFERENCE_STEPS, seed)
                ?: throw IOException("MediaPipe returned no image")
            val image = result.generatedImage()
            try {
                val source = BitmapExtractor.extract(image)
                try {
                    return encodeThumbnail(source)
                } finally {
                    source.recycle()
                }
            } finally {
                image.close()
            }
        } finally {
            // Release model/GPU memory between requests rather than retaining gigabytes while idle.
            generator.close()
        }
    }

    internal fun encodeThumbnail(source: Bitmap): ByteArray {
        val thumbnail = Bitmap.createBitmap(
            THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, Bitmap.Config.ARGB_8888,
        )
        try {
            val cropWidth = minOf(source.width, source.height * THUMBNAIL_WIDTH / THUMBNAIL_HEIGHT)
            val cropHeight = minOf(source.height, source.width * THUMBNAIL_HEIGHT / THUMBNAIL_WIDTH)
            val left = (source.width - cropWidth) / 2
            val top = (source.height - cropHeight) / 2
            Canvas(thumbnail).drawBitmap(
                source,
                Rect(left, top, left + cropWidth, top + cropHeight),
                Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            for (quality in intArrayOf(80, 65, 50, 35, 20)) {
                val output = ByteArrayOutputStream()
                if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw IOException("Cannot encode generated thumbnail")
                }
                if (output.size() <= MAX_THUMBNAIL_BYTES) return output.toByteArray()
            }
            throw IOException("Cannot encode thumbnail within $MAX_THUMBNAIL_BYTES bytes")
        } finally {
            thumbnail.recycle()
        }
    }
}
