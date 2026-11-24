package com.nijatva.photo.editor.edit

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.min

/** Non-destructive render pipeline: geometry first, then colour, then markup. */
object Pipeline {

    /** Rotate / flip / straighten / crop — pure geometry, no colour work. */
    fun transform(src: Bitmap, state: EditState): Bitmap {
        var bmp = src
        val m = Matrix()
        if (state.rotation != 0) m.postRotate(state.rotation.toFloat())
        if (state.flipH || state.flipV) {
            m.postScale(if (state.flipH) -1f else 1f, if (state.flipV) -1f else 1f)
        }
        if (!m.isIdentity) {
            bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }

        if (state.straighten != 0f) {
            bmp = straighten(bmp, state.straighten)
        }

        val crop = state.crop
        if (crop != CropRect() && bmp.width > 2 && bmp.height > 2) {
            val w = bmp.width
            val h = bmp.height
            val left = (crop.l * w).toInt().coerceIn(0, w - 2)
            val top = (crop.t * h).toInt().coerceIn(0, h - 2)
            val right = (crop.r * w).toInt().coerceIn(left + 2, w)
            val bottom = (crop.b * h).toInt().coerceIn(top + 2, h)
            bmp = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
        }
        return bmp
    }

    /** Rotates by up to +-45 deg and scales up so the frame keeps no black edges. */
    private fun straighten(src: Bitmap, degrees: Float): Bitmap {
        val w = src.width
        val h = src.height
        val rad = Math.toRadians(degrees.toDouble())
        val cosA = abs(cos(rad)).toFloat()
        val sinA = abs(sin(rad)).toFloat()
        val scale = max(
            w / (w * cosA + h * sinA),
            h / (w * sinA + h * cosA)
        )
        val m = Matrix()
        m.postRotate(degrees, w / 2f, h / 2f)
        m.postScale(scale, scale, w / 2f, h / 2f)
        val rotated = Bitmap.createBitmap(src, 0, 0, w, h, m, true)
        val left = ((rotated.width - w) / 2).coerceAtLeast(0)
        val top = ((rotated.height - h) / 2).coerceAtLeast(0)
        val cw = min(w, rotated.width - left)
        val ch = min(h, rotated.height - top)
        return Bitmap.createBitmap(rotated, left, top, cw, ch)
    }

    fun render(src: Bitmap, state: EditState): Bitmap {
        val base = transform(src, state)
        var out = Bitmap.createBitmap(
            base.width.coerceAtLeast(1),
            base.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(state.colorMatrix())
        canvas.drawBitmap(base, 0f, 0f, paint)

        if (state.needsPixelWork) {
            out = AdvancedOps.pixelPass(out, state)
        }
        if (state.localAdjusts.isNotEmpty()) {
            out = AdvancedOps.applyLocal(out, state.localAdjusts)
        }
        if (state.layers.isNotEmpty()) {
            out = AdvancedOps.drawLayers(out, state.layers)
        }
        return out
    }
}

/** Decoding with EXIF orientation applied, downsampled for the preview. */
object ImageLoader {

    fun loadPreview(resolver: ContentResolver, uri: Uri, maxEdge: Int = 1600): Bitmap? =
        load(resolver, uri, maxEdge)

    fun loadFull(resolver: ContentResolver, uri: Uri, maxEdge: Int = 6000): Bitmap? =
        load(resolver, uri, maxEdge)

    private fun load(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        } catch (t: Throwable) {
            return null
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (t: Throwable) {
            null
        } ?: return null

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (t: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        }
        return if (m.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
    }
}

/** Writes the rendered bitmap into the gallery, with optional metadata scrubbing. */
object Exporter {

    data class Output(val displayPath: String, val file: File?)

    fun save(
        context: Context,
        bitmap: Bitmap,
        format: String,
        quality: Int,
        stripExif: Boolean,
        sourceUri: Uri?,
        scale: Float = 1f,
        maxEdge: Int = 0
    ): Output? {
        val compress = when (format) {
            "PNG" -> Bitmap.CompressFormat.PNG
            "WEBP" -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
            else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        val ext = when (format) {
            "PNG" -> "png"
            "WEBP" -> "webp"
            else -> "jpg"
        }
        val mime = when (format) {
            "PNG" -> "image/png"
            "WEBP" -> "image/webp"
            else -> "image/jpeg"
        }
        val name = "Nijatva_" + System.currentTimeMillis() + "." + ext

        var outScale = scale
        if (maxEdge > 0) {
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest > maxEdge) outScale = minOf(outScale, maxEdge.toFloat() / longest)
        }
        val target = if (outScale < 0.999f) {
            val w = (bitmap.width * outScale).toInt().coerceAtLeast(1)
            val h = (bitmap.height * outScale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } else {
            bitmap
        }

        val temp = File(context.cacheDir, name)
        try {
            FileOutputStream(temp).use { out ->
                target.compress(compress, quality.coerceIn(1, 100), out)
            }
        } catch (t: Throwable) {
            return null
        }

        if (!stripExif && sourceUri != null) {
            copyExif(context, sourceUri, temp)
        }

        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Nijatva")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val resolver = context.contentResolver
            val target = resolver.insert(collection, values) ?: return null
            try {
                resolver.openOutputStream(target)?.use { out ->
                    temp.inputStream().use { it.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(target, values, null, null)
                Output("Pictures/Nijatva/$name", null)
            } catch (t: Throwable) {
                resolver.delete(target, null, null)
                null
            } finally {
                temp.delete()
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "Nijatva"
            )
            if (!dir.exists() && !dir.mkdirs()) {
                temp.delete()
                return null
            }
            val dest = File(dir, name)
            try {
                temp.inputStream().use { input ->
                    FileOutputStream(dest).use { input.copyTo(it) }
                }
            } catch (t: Throwable) {
                temp.delete()
                return null
            }
            temp.delete()
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mime), null)
            Output("Pictures/Nijatva/$name", dest)
        }
    }

    /** Rough file-size estimate: compress a small sample, scale by pixel count. */
    fun estimateSize(src: Bitmap, format: String, quality: Int, scale: Float, maxEdge: Int = 0): String {
        val compress = when (format) {
            "PNG" -> Bitmap.CompressFormat.PNG
            "WEBP" -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
            else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        val sampleW = 320
        val sampleH = (src.height * sampleW / src.width).coerceAtLeast(1)
        val sample = Bitmap.createScaledBitmap(src, sampleW, sampleH, true)
        val bos = java.io.ByteArrayOutputStream()
        sample.compress(compress, quality.coerceIn(1, 100), bos)
        val samplePx = (sampleW * sampleH).toDouble()
        var eff = scale
        if (maxEdge > 0) {
            val longest = maxOf(src.width, src.height)
            if (longest > maxEdge) eff = minOf(eff, maxEdge.toFloat() / longest)
        }
        val targetPx = (src.width * eff) * (src.height * eff)
        val bytes = bos.size().toDouble() * targetPx / samplePx
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024 * 1024))
            bytes >= 1024 -> String.format("%.0f KB", bytes / 1024)
            else -> String.format("%.0f B", bytes)
        }
    }

    private fun copyExif(context: Context, source: Uri, dest: File) {
        try {
            val src = context.contentResolver.openInputStream(source)?.use { ExifInterface(it) } ?: return
            val out = ExifInterface(dest.absolutePath)
            val tags = listOf(
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_FOCAL_LENGTH,
                ExifInterface.TAG_ISO_SPEED_RATINGS
            )
            for (tag in tags) {
                src.getAttribute(tag)?.let { out.setAttribute(tag, it) }
            }
            out.saveAttributes()
        } catch (t: Throwable) {
            // Metadata copy is best effort; the image itself is already saved.
        }
    }
}

/** Key/value metadata for the EXIF sheet. */
object ExifSummary {

    fun read(resolver: ContentResolver, uri: Uri): List<Pair<String, String>> {
        val rows = mutableListOf<Pair<String, String>>()
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                fun add(label: String, tag: String) {
                    val v = exif.getAttribute(tag)
                    if (!v.isNullOrBlank()) rows.add(label to v)
                }
                add("Make", ExifInterface.TAG_MAKE)
                add("Model", ExifInterface.TAG_MODEL)
                add("Date", ExifInterface.TAG_DATETIME_ORIGINAL)
                add("Aperture", ExifInterface.TAG_F_NUMBER)
                add("Shutter", ExifInterface.TAG_EXPOSURE_TIME)
                add("ISO", ExifInterface.TAG_ISO_SPEED_RATINGS)
                add("Focal length", ExifInterface.TAG_FOCAL_LENGTH)
                add("Width", ExifInterface.TAG_IMAGE_WIDTH)
                add("Height", ExifInterface.TAG_IMAGE_LENGTH)
                val lat = exif.latLong
                if (lat != null) {
                    rows.add("GPS" to String.format("%.5f, %.5f", lat[0], lat[1]))
                }
            }
        } catch (t: Throwable) {
            // ignore: some formats carry no metadata
        }
        return rows
    }
}
