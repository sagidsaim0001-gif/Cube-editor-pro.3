package com.example.engine

import com.example.model.CornerAnchor
import com.example.model.CubeFace
import com.example.model.FaceLayoutMode
import com.example.model.FaceTextItem
import kotlin.math.abs
import kotlin.math.max

data class FittedWordPlacement(
    val item: FaceTextItem,
    val centerX: Float,
    val centerY: Float,
    val fontSizePx: Float,
    val boxWidth: Float,
    val boxHeight: Float,
    val alignLeft: Boolean = false,
    val alignRight: Boolean = false
)

object TextAutoFit {

    fun computePlacements(
        face: CubeFace,
        faceSizePx: Float = 512f,
        measureTextWidth: ((FaceTextItem, Float) -> Float)? = null
    ): List<FittedWordPlacement> {
        val items = face.textItems
        if (items.isEmpty()) return emptyList()

        val measure: (FaceTextItem, Float) -> Float = measureTextWidth ?: { item, sizePx ->
            val display = if (item.uppercase) item.text.uppercase() else item.text
            val charFactor = 0.56f * item.horizontalScaleX.coerceIn(0.4f, 1.8f)
            (display.length.coerceAtLeast(1) * sizePx * charFactor)
        }

        return when (face.layoutMode) {
            FaceLayoutMode.CORNERS -> layoutCorners(items, faceSizePx, measure)
            FaceLayoutMode.FREE -> layoutFree(items, faceSizePx, measure)
            FaceLayoutMode.AUTO_STACKED -> layoutAutoStacked(items, faceSizePx, measure)
            FaceLayoutMode.AUTO_GRID -> layoutAutoGrid(items, faceSizePx, measure)
            FaceLayoutMode.AUTO_POSTER -> layoutAutoPoster(items, faceSizePx, measure)
        }
    }

    private fun layoutCorners(
        items: List<FaceTextItem>,
        faceSize: Float,
        measure: (FaceTextItem, Float) -> Float
    ): List<FittedWordPlacement> {
        val margin = faceSize * 0.06f
        val maxSpan = faceSize * 0.44f
        val grouped = items.groupBy { it.corner }
        val out = mutableListOf<FittedWordPlacement>()

        grouped.forEach { (corner, group) ->
            val ordered = group.sortedBy { it.orderInCorner }
            var cursorY = when (corner) {
                CornerAnchor.TOP_LEFT, CornerAnchor.TOP_RIGHT -> margin
                CornerAnchor.BOTTOM_LEFT, CornerAnchor.BOTTOM_RIGHT -> faceSize - margin
            }

            ordered.forEach { item ->
                val baseSize = (item.fontSizePx * (faceSize / 512f)).coerceIn(14f, faceSize * 0.34f)
                val rawW = measure(item, baseSize).coerceAtLeast(1f)
                val fitScale = if (rawW > maxSpan) (maxSpan / rawW) else 1f
                val finalSize = (baseSize * fitScale).coerceAtLeast(14f)
                val w = measure(item, finalSize)
                val h = finalSize * item.lineHeightMult

                val isLeft = corner == CornerAnchor.TOP_LEFT || corner == CornerAnchor.BOTTOM_LEFT
                val isTop = corner == CornerAnchor.TOP_LEFT || corner == CornerAnchor.TOP_RIGHT
                val cx = if (isLeft) margin + w * 0.5f else faceSize - margin - w * 0.5f
                val cy = if (isTop) {
                    val y = cursorY + h * 0.5f
                    cursorY += h + faceSize * 0.015f
                    y
                } else {
                    val y = cursorY - h * 0.5f
                    cursorY -= h + faceSize * 0.015f
                    y
                }
                out.add(
                    FittedWordPlacement(
                        item = item,
                        centerX = cx,
                        centerY = cy,
                        fontSizePx = finalSize,
                        boxWidth = w,
                        boxHeight = h,
                        alignLeft = isLeft,
                        alignRight = !isLeft
                    )
                )
            }
        }
        return out
    }

    private fun layoutFree(
        items: List<FaceTextItem>,
        faceSize: Float,
        measure: (FaceTextItem, Float) -> Float
    ): List<FittedWordPlacement> {
        return items.map { item ->
            val size = (item.fontSizePx * (faceSize / 512f) * item.freeScale).coerceIn(12f, faceSize * 0.6f)
            val w = measure(item, size)
            val h = size * item.lineHeightMult
            FittedWordPlacement(
                item = item,
                centerX = item.freeX.coerceIn(0.05f, 0.95f) * faceSize,
                centerY = item.freeY.coerceIn(0.05f, 0.95f) * faceSize,
                fontSizePx = size,
                boxWidth = w,
                boxHeight = h
            )
        }
    }

    private fun layoutAutoStacked(
        items: List<FaceTextItem>,
        faceSize: Float,
        measure: (FaceTextItem, Float) -> Float
    ): List<FittedWordPlacement> {
        val pad = faceSize * 0.06f
        val targetWidth = faceSize - pad * 2f
        val usableHeight = faceSize - pad * 2f
        val gap = faceSize * 0.02f

        val rawPlacements = items.map { item ->
            val refSize = 100f
            val wAtRef = measure(item, refSize).coerceAtLeast(10f)
            val widthFitSize = (refSize * (targetWidth / wAtRef)).coerceIn(22f, faceSize * 0.42f)
            val actualW = measure(item, widthFitSize)
            val actualH = widthFitSize * 0.92f
            Triple(item, widthFitSize, actualW to actualH)
        }

        val totalRawH = rawPlacements.sumOf { it.third.second.toDouble() }.toFloat() +
            gap * (items.size - 1).coerceAtLeast(0)
        val verticalScale = if (totalRawH > usableHeight) usableHeight / totalRawH else (usableHeight / max(totalRawH, 1f)).coerceAtMost(1.18f)

        val scaled = rawPlacements.map { (item, fSize, _) ->
            val sSize = (fSize * verticalScale).coerceAtLeast(14f)
            val w = measure(item, sSize)
            val h = sSize * 0.94f
            Triple(item, sSize, w to h)
        }

        val finalTotalH = scaled.sumOf { it.third.second.toDouble() }.toFloat() + gap * (items.size - 1).coerceAtLeast(0)
        var y = (faceSize - finalTotalH) * 0.5f

        return scaled.map { (item, fSize, dims) ->
            val h = dims.second
            val placement = FittedWordPlacement(
                item = item,
                centerX = faceSize * 0.5f,
                centerY = y + h * 0.5f,
                fontSizePx = fSize,
                boxWidth = dims.first,
                boxHeight = h
            )
            y += h + gap
            placement
        }
    }

    private fun layoutAutoGrid(
        items: List<FaceTextItem>,
        faceSize: Float,
        measure: (FaceTextItem, Float) -> Float
    ): List<FittedWordPlacement> {
        val pad = faceSize * 0.06f
        val cols = if (items.size <= 2) 1 else 2
        val rows = (items.size + cols - 1) / cols
        val cellW = (faceSize - pad * 2f - (cols - 1) * pad * 0.4f) / cols
        val cellH = (faceSize - pad * 2f - (rows - 1) * pad * 0.35f) / rows

        return items.mapIndexed { idx, item ->
            val col = idx % cols
            val row = idx / cols
            val refSize = 100f
            val wAtRef = measure(item, refSize).coerceAtLeast(10f)
            val fittedSize = minOf(
                refSize * (cellW / wAtRef),
                cellH * 0.78f
            ).coerceIn(14f, faceSize * 0.36f)
            val w = measure(item, fittedSize)
            val h = fittedSize * 0.95f
            val cx = pad + col * (cellW + pad * 0.4f) + cellW * 0.5f
            val cy = pad + row * (cellH + pad * 0.35f) + cellH * 0.5f
            FittedWordPlacement(
                item = item,
                centerX = cx,
                centerY = cy,
                fontSizePx = fittedSize,
                boxWidth = w,
                boxHeight = h
            )
        }
    }

    private fun layoutAutoPoster(
        items: List<FaceTextItem>,
        faceSize: Float,
        measure: (FaceTextItem, Float) -> Float
    ): List<FittedWordPlacement> {
        val pad = faceSize * 0.055f
        val targetW = faceSize - pad * 2f
        val rows = mutableListOf<List<FaceTextItem>>()
        var i = 0
        while (i < items.size) {
            val cur = items[i]
            val next = items.getOrNull(i + 1)
            val curLen = cur.text.trim().length
            val nextLen = next?.text?.trim()?.length ?: 99
            if (next != null && curLen <= 5 && nextLen <= 5 && (abs(cur.id.hashCode()) % 2 == 0)) {
                rows.add(listOf(cur, next))
                i += 2
            } else {
                rows.add(listOf(cur))
                i += 1
            }
        }

        val gap = faceSize * 0.02f
        val rowHeights = rows.map { row ->
            if (row.size == 1) {
                val item = row[0]
                val refW = measure(item, 100f).coerceAtLeast(10f)
                (100f * (targetW / refW)).coerceIn(22f, faceSize * 0.44f)
            } else {
                val halfW = (targetW - gap) * 0.5f
                val s0 = 100f * (halfW / measure(row[0], 100f).coerceAtLeast(10f))
                val s1 = 100f * (halfW / measure(row[1], 100f).coerceAtLeast(10f))
                minOf(s0, s1).coerceIn(18f, faceSize * 0.3f)
            }
        }

        val totalH = rowHeights.sum() + gap * (rows.size - 1).coerceAtLeast(0)
        val vScale = ((faceSize - pad * 2f) / max(totalH, 1f)).coerceAtMost(1.15f)
        val finalTotalH = rowHeights.sumOf { (it * vScale).toDouble() }.toFloat() + gap * (rows.size - 1).coerceAtLeast(0)
        var y = (faceSize - finalTotalH) * 0.5f

        val out = mutableListOf<FittedWordPlacement>()
        rows.forEachIndexed { rIdx, row ->
            val fSize = (rowHeights[rIdx] * vScale).coerceAtLeast(14f)
            val h = fSize * 0.94f
            if (row.size == 1) {
                val item = row[0]
                out.add(
                    FittedWordPlacement(
                        item = item,
                        centerX = faceSize * 0.5f,
                        centerY = y + h * 0.5f,
                        fontSizePx = fSize,
                        boxWidth = measure(item, fSize),
                        boxHeight = h
                    )
                )
            } else {
                val w0 = measure(row[0], fSize)
                val w1 = measure(row[1], fSize)
                out.add(
                    FittedWordPlacement(
                        item = row[0],
                        centerX = pad + w0 * 0.5f,
                        centerY = y + h * 0.5f,
                        fontSizePx = fSize,
                        boxWidth = w0,
                        boxHeight = h
                    )
                )
                out.add(
                    FittedWordPlacement(
                        item = row[1],
                        centerX = faceSize - pad - w1 * 0.5f,
                        centerY = y + h * 0.5f,
                        fontSizePx = fSize,
                        boxWidth = w1,
                        boxHeight = h
                    )
                )
            }
            y += h + gap
        }
        return out
    }
}
