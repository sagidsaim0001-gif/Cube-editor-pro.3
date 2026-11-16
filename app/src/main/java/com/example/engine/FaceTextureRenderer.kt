package com.example.engine

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import com.example.data.FontRegistry
import com.example.model.CubeFace
import com.example.model.FaceTextItem

class FaceTextureCache(maxEntries: Int = 48) {
    private val cache = object : LruCache<String, Bitmap>(maxEntries) {
        override fun entryRemoved(evicted: Boolean, key: String?, oldValue: Bitmap?, newValue: Bitmap?) {
            if (evicted && oldValue != null && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    fun getOrRender(
        context: Context,
        face: CubeFace,
        sizePx: Int,
        activeWordId: String? = null
    ): Bitmap {
        val key = "${face.hashCode()}_${sizePx}_${activeWordId.orEmpty()}"
        val existing = cache.get(key)
        if (existing != null && !existing.isRecycled) {
            return existing
        }
        val bmp = FaceTextureRenderer.renderFaceBitmap(context, face, sizePx, activeWordId)
        cache.put(key, bmp)
        return bmp
    }

    fun clear() {
        cache.evictAll()
    }
}

object FaceTextureRenderer {

    fun parseColorSafe(hex: String, fallback: Int = Color.WHITE): Int {
        return try {
            Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }

    fun renderFaceBitmap(
        context: Context,
        face: CubeFace,
        sizePx: Int,
        activeWordId: String? = null
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (!face.transparent) {
            val bgColor = parseColorSafe(face.backgroundColorHex, Color.parseColor("#090910"))
            val gradHex = face.gradientColorHex
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            if (gradHex != null) {
                bgPaint.shader = LinearGradient(
                    0f, 0f, sizePx.toFloat(), sizePx.toFloat(),
                    bgColor, parseColorSafe(gradHex, bgColor),
                    Shader.TileMode.CLAMP
                )
            } else {
                bgPaint.color = bgColor
            }
            canvas.drawRect(0f, 0f, sizePx.toFloat(), sizePx.toFloat(), bgPaint)
        }

        if (face.borderWidth > 0.2f) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = face.borderWidth * (sizePx / 512f)
                color = parseColorSafe(face.borderColorHex, Color.parseColor("#2B2B3C"))
            }
            val half = borderPaint.strokeWidth * 0.5f
            canvas.drawRect(half, half, sizePx - half, sizePx - half, borderPaint)
        }

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        val measureWidth: (FaceTextItem, Float) -> Float = { item, fontSize ->
            val display = if (item.uppercase) item.text.uppercase() else item.text
            textPaint.typeface = FontRegistry.getTypeface(context, item.fontId, display)
            textPaint.textSize = fontSize
            textPaint.textScaleX = item.horizontalScaleX.coerceIn(0.45f, 1.8f)
            textPaint.letterSpacing = item.letterSpacing
            textPaint.measureText(display).coerceAtLeast(fontSize * 0.4f)
        }

        val placements = TextAutoFit.computePlacements(face, sizePx.toFloat(), measureWidth)
        placements.forEach { placement ->
            val item = placement.item
            val display = if (item.uppercase) item.text.uppercase() else item.text
            if (display.isBlank()) return@forEach

            textPaint.reset()
            textPaint.isAntiAlias = true
            textPaint.isSubpixelText = true
            textPaint.typeface = FontRegistry.getTypeface(context, item.fontId, display)
            textPaint.textSize = placement.fontSizePx
            textPaint.textScaleX = item.horizontalScaleX.coerceIn(0.45f, 1.8f)
            textPaint.letterSpacing = item.letterSpacing
            textPaint.textSkewX = item.skewX
            val baseColor = parseColorSafe(item.colorHex, Color.WHITE)
            val alphaInt = (item.opacity.coerceIn(0f, 1f) * 255).toInt()

            val layoutW = (placement.boxWidth * 1.15f).toInt().coerceAtLeast(16)

            canvas.save()
            canvas.translate(placement.centerX, placement.centerY)
            val rot = item.rotationDeg + item.freeRotationDeg
            if (rot != 0f) canvas.rotate(rot)

            if (item.shadowEnabled) {
                textPaint.setShadowLayer(
                    placement.fontSizePx * 0.08f,
                    0f,
                    placement.fontSizePx * 0.04f,
                    Color.argb(170, 0, 0, 0)
                )
            } else {
                textPaint.clearShadowLayer()
            }

            if (item.strokeWidth > 0.2f) {
                textPaint.style = Paint.Style.STROKE
                textPaint.strokeWidth = item.strokeWidth * (sizePx / 512f)
                textPaint.strokeJoin = Paint.Join.ROUND
                textPaint.color = parseColorSafe(item.strokeColorHex, Color.BLACK)
                textPaint.alpha = alphaInt
                val strokeLayout = StaticLayout.Builder.obtain(display, 0, display.length, textPaint, layoutW)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false)
                    .build()
                canvas.save()
                canvas.translate(-layoutW * 0.5f, -strokeLayout.height * 0.5f)
                strokeLayout.draw(canvas)
                canvas.restore()
            }

            textPaint.style = Paint.Style.FILL
            textPaint.color = baseColor
            textPaint.alpha = alphaInt
            val fillLayout = StaticLayout.Builder.obtain(display, 0, display.length, textPaint, layoutW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(-layoutW * 0.5f, -fillLayout.height * 0.5f)
            fillLayout.draw(canvas)
            canvas.restore()

            if (activeWordId != null && activeWordId == item.id) {
                val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 3f * (sizePx / 512f)
                    color = Color.parseColor("#00D2FF")
                    pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
                }
                canvas.drawRect(
                    -placement.boxWidth * 0.54f,
                    -placement.boxHeight * 0.54f,
                    placement.boxWidth * 0.54f,
                    placement.boxHeight * 0.54f,
                    selPaint
                )
            }

            canvas.restore()
        }

        return bmp
    }
}
