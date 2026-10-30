package com.pandyabhavik.blackicons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Turns any app icon into a white glyph on a black circle, matching the pack's icons. */
object GlyphMaker {
    const val SIZE = 192
    private const val WORK = 216

    fun make(icon: Drawable): Bitmap {
        val mask = monochromeMask(icon) ?: foregroundMask(icon) ?: fullMask(icon)
        return finish(mask)
    }

    private fun copy(d: Drawable): Drawable = d.constantState?.newDrawable()?.mutate() ?: d

    private fun render(d: Drawable): IntArray {
        val bitmap = Bitmap.createBitmap(WORK, WORK, Bitmap.Config.ARGB_8888)
        val drawable = copy(d)
        drawable.setBounds(0, 0, WORK, WORK)
        drawable.draw(Canvas(bitmap))
        val pixels = IntArray(WORK * WORK)
        bitmap.getPixels(pixels, 0, WORK, 0, 0, WORK, WORK)
        bitmap.recycle()
        return pixels
    }

    private fun alphaMask(pixels: IntArray) = FloatArray(pixels.size) { (pixels[it] ushr 24) / 255f }

    private fun coverage(mask: FloatArray) = mask.count { it > 0.5f }.toFloat() / mask.size

    private fun monochromeMask(icon: Drawable): FloatArray? {
        if (Build.VERSION.SDK_INT < 33 || icon !is AdaptiveIconDrawable) return null
        val mono = icon.monochrome ?: return null
        val mask = alphaMask(render(mono))
        return mask.takeIf { coverage(it) in 0.005f..0.9f }
    }

    private fun foregroundMask(icon: Drawable): FloatArray? {
        if (icon !is AdaptiveIconDrawable) return null
        val fg = icon.foreground ?: return null
        val mask = alphaMask(render(fg))
        return mask.takeIf { coverage(it) in 0.005f..0.6f }
    }

    private fun fullMask(icon: Drawable): FloatArray {
        val pixels = render(icon)
        val alpha = alphaMask(pixels)
        val opaque = pixels.indices.filter { (pixels[it] ushr 24) > 200 }
        // An icon without a solid plate is already its own silhouette.
        if (opaque.size < pixels.size * 0.3f) return alpha

        val buckets = HashMap<Int, IntArray>()
        for (i in opaque) {
            val p = pixels[i]
            val key = ((p shr 20) and 0xF shl 8) or ((p shr 12) and 0xF shl 4) or ((p shr 4) and 0xF)
            val b = buckets.getOrPut(key) { IntArray(4) }
            b[0]++; b[1] += Color.red(p); b[2] += Color.green(p); b[3] += Color.blue(p)
        }
        val top = buckets.values.maxBy { it[0] }
        val bgR = top[1] / top[0]; val bgG = top[2] / top[0]; val bgB = top[3] / top[0]

        val byColour = FloatArray(pixels.size) {
            val p = pixels[it]
            val dr = Color.red(p) - bgR; val dg = Color.green(p) - bgG; val db = Color.blue(p) - bgB
            alpha[it] * smoothstep(48f, 96f, sqrt((dr * dr + dg * dg + db * db).toFloat()))
        }
        if (coverage(byColour) in 0.015f..0.7f) return byColour

        val lum = FloatArray(pixels.size) {
            val p = pixels[it]
            (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p))
        }
        val mean = opaque.map { lum[it] }.average().toFloat()
        val darker = opaque.count { lum[it] < mean }
        val wantDark = darker < opaque.size - darker
        val byLight = FloatArray(pixels.size) {
            val minority = if (wantDark) lum[it] < mean - 12 else lum[it] > mean + 12
            if (minority) alpha[it] else 0f
        }
        if (coverage(byLight) in 0.015f..0.7f) return byLight

        return alpha
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    private fun finish(mask: FloatArray): Bitmap {
        var left = WORK; var top = WORK; var right = -1; var bottom = -1
        for (y in 0 until WORK) for (x in 0 until WORK) {
            if (mask[y * WORK + x] > 0.1f) {
                left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
            }
        }
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
        if (right < 0) return out

        val alphaBitmap = Bitmap.createBitmap(WORK, WORK, Bitmap.Config.ALPHA_8)
        alphaBitmap.setPixels(IntArray(mask.size) { (mask[it].coerceIn(0f, 1f) * 255).toInt() shl 24 }, 0, WORK, 0, 0, WORK, WORK)

        val w = right - left + 1f; val h = bottom - top + 1f
        val scale = SIZE * 0.5f / max(w, h)
        val matrix = Matrix().apply {
            postTranslate(-left - w / 2f, -top - h / 2f)
            postScale(scale, scale)
            postTranslate(SIZE / 2f, SIZE / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = Color.WHITE }
        canvas.drawBitmap(alphaBitmap, matrix, paint)
        alphaBitmap.recycle()
        return out
    }
}
