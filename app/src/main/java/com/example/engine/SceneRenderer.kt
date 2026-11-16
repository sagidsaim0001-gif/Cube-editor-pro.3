package com.example.engine

import android.content.Context
import android.graphics.*
import com.example.data.FontRegistry
import com.example.model.*
import kotlin.math.*

private data class ProjectedFace(
    val cubeId: String,
    val faceSide: FaceSide,
    val face: CubeFace,
    val depthZ: Float,
    val dstPoints: FloatArray,
    val shadeAlpha: Int,
    val opacity: Float,
    val isSelectedCube: Boolean,
    val isSelectedFace: Boolean
)

object SceneRenderer {

    private fun faceLocalCorners(side: FaceSide, half: Float): Array<Vec3> = when (side) {
        FaceSide.FRONT -> arrayOf(
            Vec3(-half, half, half), Vec3(half, half, half),
            Vec3(half, -half, half), Vec3(-half, -half, half)
        )
        FaceSide.RIGHT -> arrayOf(
            Vec3(half, half, half), Vec3(half, half, -half),
            Vec3(half, -half, -half), Vec3(half, -half, half)
        )
        FaceSide.BACK -> arrayOf(
            Vec3(half, half, -half), Vec3(-half, half, -half),
            Vec3(-half, -half, -half), Vec3(half, -half, -half)
        )
        FaceSide.LEFT -> arrayOf(
            Vec3(-half, half, -half), Vec3(-half, half, half),
            Vec3(-half, -half, half), Vec3(-half, -half, -half)
        )
        FaceSide.TOP -> arrayOf(
            Vec3(-half, half, -half), Vec3(half, half, -half),
            Vec3(half, half, half), Vec3(-half, half, half)
        )
        FaceSide.BOTTOM -> arrayOf(
            Vec3(-half, -half, half), Vec3(half, -half, half),
            Vec3(half, -half, -half), Vec3(-half, -half, -half)
        )
    }

    private fun faceLocalNormal(side: FaceSide): Vec3 = when (side) {
        FaceSide.FRONT -> Vec3(0f, 0f, 1f)
        FaceSide.RIGHT -> Vec3(1f, 0f, 0f)
        FaceSide.BACK -> Vec3(0f, 0f, -1f)
        FaceSide.LEFT -> Vec3(-1f, 0f, 0f)
        FaceSide.TOP -> Vec3(0f, 1f, 0f)
        FaceSide.BOTTOM -> Vec3(0f, -1f, 0f)
    }

    fun renderFrame(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        project: CubeProject,
        timeMs: Long,
        faceTextureCache: FaceTextureCache,
        bgBitmap: Bitmap? = null,
        selectedCubeId: String? = null,
        selectedFace: FaceSide? = null,
        selectedWordId: String? = null,
        showSafeGuides: Boolean = false,
        textureSizePx: Int = 512,
        isExport: Boolean = false
    ) {
        val w = width.toFloat()
        val h = height.toFloat()

        drawBackground(canvas, w, h, project.background, bgBitmap)

        var beatBoost = 1f
        if (project.effects.beatPulseEnabled && project.music != null) {
            val beats = project.music.beatMarkersMs
            if (beats.isNotEmpty()) {
                val nearestDelta = beats.minOf { abs(timeMs - it) }
                if (nearestDelta < 180L) {
                    val decay = 1f - (nearestDelta / 180f)
                    beatBoost += project.effects.beatPulseStrength * decay * decay
                }
            }
        }

        val shakeX: Float
        val shakeY: Float
        if (project.effects.shakeStrength > 0.01f) {
            val phase = timeMs * 0.045f
            shakeX = sin(phase * 1.7f) * w * 0.015f * project.effects.shakeStrength
            shakeY = cos(phase * 2.3f) * h * 0.015f * project.effects.shakeStrength
        } else {
            shakeX = 0f
            shakeY = 0f
        }

        val compiledKeys = KeyframeEvaluator.compileEffectiveKeyframes(project)
        val poseMap = mutableMapOf<String, CubePose>()
        project.cubes.forEach { cube ->
            val defaultPose = CubePose(
                posX = cube.posX, posY = cube.posY, posZ = cube.posZ,
                rotXDeg = cube.rotXDeg, rotYDeg = cube.rotYDeg, rotZDeg = cube.rotZDeg,
                scale = cube.scale, opacity = cube.opacity,
                cameraZoom = project.cameraZoom,
                shutterAngleDeg = project.effects.shutterAngleDeg
            )
            poseMap[cube.id] = KeyframeEvaluator.poseAt(compiledKeys[cube.id].orEmpty(), timeMs, defaultPose)
        }
        val worldMatrices = KeyframeEvaluator.computeCubeWorldMatrices(project.cubes, poseMap)

        val primaryPose = poseMap[project.cubes.firstOrNull()?.id] ?: CubePose(cameraZoom = project.cameraZoom)
        val camDist = (project.cameraDistance / (primaryPose.cameraZoom * beatBoost).coerceIn(0.35f, 3.0f)).coerceAtLeast(1.8f)
        val fovRad = Math.toRadians(project.cameraFovDeg.toDouble()).toFloat()
        val focalPx = (min(w, h) * 0.58f) / tan(fovRad * 0.5f)
        val centerX = w * 0.5f + shakeX
        val centerY = h * 0.5f + shakeY

        val activeLyric = project.lyrics.firstOrNull { timeMs in it.startMs..it.endMs }
        val visibleFaces = mutableListOf<ProjectedFace>()
        val camPos = Vec3(0f, 0f, camDist)

        for (cube in project.cubes) {
            if (!cube.visible) continue
            val worldMat = worldMatrices[cube.id] ?: continue
            val half = cube.size * 0.5f
            val pose = poseMap[cube.id] ?: CubePose()

            for (side in FaceSide.entries) {
                var face = cube.faces[side] ?: CubeFace(side = side)
                if (activeLyric != null &&
                    activeLyric.displayMode == LyricDisplayMode.AUTO_FIT_FACE &&
                    activeLyric.targetCubeId == cube.id &&
                    activeLyric.targetFace == side &&
                    face.textItems.isEmpty()
                ) {
                    val words = activeLyric.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    val autoItems = words.mapIndexed { idx, word ->
                        FaceTextItem(
                            id = "lyric_${activeLyric.id}_$idx",
                            text = word,
                            colorHex = listOf("#FFFFFF", "#00D2FF", "#FFD600", "#FF4081", "#00E676")[idx % 5],
                            fontId = face.defaultFontId,
                            fontSizePx = 96f
                        )
                    }
                    face = face.copy(layoutMode = FaceLayoutMode.AUTO_STACKED, textItems = autoItems)
                }

                val localNorm = faceLocalNormal(side)
                val worldNorm = worldMat.transformDirection(localNorm).normalized()
                val localCenter = localNorm * half
                val worldCenter = worldMat.transformPoint(localCenter)
                val toCam = camPos - worldCenter
                val facingDot = worldNorm.dot(toCam)

                if (facingDot <= 0.001f && !face.transparent) continue

                val corners = faceLocalCorners(side, half)
                val dstPts = FloatArray(8)
                var valid = true
                for (cIdx in 0..3) {
                    val wp = worldMat.transformPoint(corners[cIdx])
                    val depth = camDist - wp.z
                    if (depth <= 0.15f) {
                        valid = false
                        break
                    }
                    val sx = centerX + (wp.x / depth) * focalPx
                    val sy = centerY - (wp.y / depth) * focalPx
                    dstPts[cIdx * 2] = sx
                    dstPts[cIdx * 2 + 1] = sy
                }
                if (!valid) continue

                val light = (worldNorm.dot(Vec3(0.25f, 0.4f, 0.9f).normalized())).coerceIn(0f, 1f)
                val shadeAlpha = ((1f - light) * 60f).toInt().coerceIn(0, 75)

                visibleFaces.add(
                    ProjectedFace(
                        cubeId = cube.id,
                        faceSide = side,
                        face = face,
                        depthZ = worldCenter.z,
                        dstPoints = dstPts,
                        shadeAlpha = shadeAlpha,
                        opacity = pose.opacity,
                        isSelectedCube = !isExport && cube.id == selectedCubeId,
                        isSelectedFace = !isExport && cube.id == selectedCubeId && side == selectedFace
                    )
                )
            }
        }

        visibleFaces.sortBy { it.depthZ }

        if (!isExport && project.effects.motionBlurPreview && project.effects.shutterAngleDeg > 20f) {
            val prevTime = (timeMs - 24L).coerceAtLeast(0L)
            val anyFast = project.cubes.any { cube ->
                val pNow = poseMap[cube.id] ?: return@any false
                val pPrev = KeyframeEvaluator.poseAt(compiledKeys[cube.id].orEmpty(), prevTime, pNow)
                abs(pNow.rotYDeg - pPrev.rotYDeg) + abs(pNow.rotXDeg - pPrev.rotXDeg) > 4.5f
            }
            if (anyFast) {
                drawMotionEcho(canvas, visibleFaces, faceTextureCache, context, textureSizePx)
            }
        }

        val polyMatrix = Matrix()
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, w * 0.004f)
        }
        val quadPath = Path()

        for (pf in visibleFaces) {
            val bmp = faceTextureCache.getOrRender(
                context = context,
                face = pf.face,
                sizePx = textureSizePx,
                activeWordId = if (pf.isSelectedFace) selectedWordId else null
            )
            val s = bmp.width.toFloat()
            val srcPts = floatArrayOf(0f, 0f, s, 0f, s, s, 0f, s)
            if (polyMatrix.setPolyToPoly(srcPts, 0, pf.dstPoints, 0, 4)) {
                bitmapPaint.alpha = (pf.opacity * 255).toInt().coerceIn(0, 255)
                if (project.effects.chromaticAberrationEnabled && beatBoost > 1.02f) {
                    val shift = w * 0.006f
                    canvas.save()
                    canvas.translate(-shift, 0f)
                    bitmapPaint.colorFilter = PorterDuffColorFilter(Color.argb(90, 255, 60, 120), PorterDuff.Mode.ADD)
                    canvas.drawBitmap(bmp, polyMatrix, bitmapPaint)
                    canvas.restore()
                    bitmapPaint.colorFilter = null
                }
                canvas.drawBitmap(bmp, polyMatrix, bitmapPaint)

                if (pf.shadeAlpha > 0 && !pf.face.transparent) {
                    quadPath.reset()
                    quadPath.moveTo(pf.dstPoints[0], pf.dstPoints[1])
                    quadPath.lineTo(pf.dstPoints[2], pf.dstPoints[3])
                    quadPath.lineTo(pf.dstPoints[4], pf.dstPoints[5])
                    quadPath.lineTo(pf.dstPoints[6], pf.dstPoints[7])
                    quadPath.close()
                    shadePaint.color = Color.argb(pf.shadeAlpha, 0, 0, 0)
                    canvas.drawPath(quadPath, shadePaint)
                }

                if (pf.isSelectedCube) {
                    quadPath.reset()
                    quadPath.moveTo(pf.dstPoints[0], pf.dstPoints[1])
                    quadPath.lineTo(pf.dstPoints[2], pf.dstPoints[3])
                    quadPath.lineTo(pf.dstPoints[4], pf.dstPoints[5])
                    quadPath.lineTo(pf.dstPoints[6], pf.dstPoints[7])
                    quadPath.close()
                    selPaint.color = if (pf.isSelectedFace) Color.parseColor("#00D2FF") else Color.parseColor("#6C5CE7")
                    selPaint.alpha = if (pf.isSelectedFace) 230 else 130
                    canvas.drawPath(quadPath, selPaint)
                }
            }
        }

        if (project.effects.glowEnabled) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val pulseAlpha = (48 + ((beatBoost - 1f) * 350f).toInt()).coerceIn(30, 110)
            glowPaint.shader = RadialGradient(
                centerX, centerY, min(w, h) * 0.56f,
                intArrayOf(Color.argb(pulseAlpha, 108, 92, 231), Color.argb(pulseAlpha / 2, 0, 210, 255), Color.TRANSPARENT),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, glowPaint)
        }

        if (project.effects.vignetteStrength > 0.01f) {
            val vigPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            vigPaint.shader = RadialGradient(
                w * 0.5f, h * 0.5f, max(w, h) * 0.72f,
                intArrayOf(Color.TRANSPARENT, Color.argb((project.effects.vignetteStrength * 210).toInt().coerceIn(0, 230), 0, 0, 0)),
                floatArrayOf(0.45f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, vigPaint)
        }

        if (project.effects.filmGrainStrength > 0.02f) {
            val grainPaint = Paint().apply {
                color = Color.WHITE
                alpha = (project.effects.filmGrainStrength * 50).toInt().coerceIn(4, 38)
                strokeWidth = max(1f, w * 0.002f)
            }
            val seed = (timeMs / 33L).toInt()
            var hVal = seed * 374761393
            val dotCount = (60 * project.effects.filmGrainStrength).toInt().coerceIn(12, 80)
            for (g in 0 until dotCount) {
                hVal = (hVal xor (hVal ushr 13)) * 1274126177
                val gx = ((hVal and 0xFFFF) / 65535f) * w
                val gy = (((hVal ushr 16) and 0xFFFF) / 65535f) * h
                canvas.drawPoint(gx, gy, grainPaint)
            }
        }

        if (project.karaoke.enabled && activeLyric != null) {
            drawKaraokeOverlay(context, canvas, w, h, activeLyric, timeMs, project.karaoke)
        }

        if (project.credit.enabled && project.credit.handle.isNotBlank()) {
            drawCreditOverlay(context, canvas, w, h, project, timeMs)
        }

        if (showSafeGuides && !isExport) {
            drawSafeAreaGuides(canvas, w, h)
        }
    }

    private fun drawMotionEcho(
        canvas: Canvas,
        faces: List<ProjectedFace>,
        cache: FaceTextureCache,
        context: Context,
        textureSizePx: Int
    ) {
        val m = Matrix()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 45 }
        for (pf in faces) {
            val bmp = cache.getOrRender(context, pf.face, textureSizePx)
            val s = bmp.width.toFloat()
            val srcPts = floatArrayOf(0f, 0f, s, 0f, s, s, 0f, s)
            val shifted = pf.dstPoints.copyOf()
            for (i in shifted.indices step 2) shifted[i] += 6f
            if (m.setPolyToPoly(srcPts, 0, shifted, 0, 4)) {
                canvas.drawBitmap(bmp, m, p)
            }
        }
    }

    private fun drawBackground(
        canvas: Canvas,
        w: Float,
        h: Float,
        bg: BackgroundConfig,
        bgBitmap: Bitmap?
    ) {
        when (bg.type) {
            BackgroundType.BLACK -> canvas.drawColor(Color.BLACK)
            BackgroundType.SOLID -> canvas.drawColor(FaceTextureRenderer.parseColorSafe(bg.primaryColorHex, Color.BLACK))
            BackgroundType.GRADIENT -> {
                val p = Paint()
                val rad = Math.toRadians(bg.gradientAngleDeg.toDouble())
                val dx = cos(rad).toFloat() * w * 0.5f
                val dy = sin(rad).toFloat() * h * 0.5f
                p.shader = LinearGradient(
                    w * 0.5f - dx, h * 0.5f - dy,
                    w * 0.5f + dx, h * 0.5f + dy,
                    FaceTextureRenderer.parseColorSafe(bg.primaryColorHex, Color.parseColor("#0B0B12")),
                    FaceTextureRenderer.parseColorSafe(bg.secondaryColorHex, Color.parseColor("#1D1138")),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, p)
            }
            BackgroundType.IMAGE -> {
                canvas.drawColor(Color.BLACK)
                if (bgBitmap != null && !bgBitmap.isRecycled) {
                    val scale = max(w / bgBitmap.width, h / bgBitmap.height)
                    val dw = bgBitmap.width * scale
                    val dh = bgBitmap.height * scale
                    val dst = RectF((w - dw) * 0.5f, (h - dh) * 0.5f, (w + dw) * 0.5f, (h + dh) * 0.5f)
                    canvas.drawBitmap(bgBitmap, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                    if (bg.dimAlpha > 0f) {
                        canvas.drawColor(Color.argb((bg.dimAlpha * 255).toInt().coerceIn(0, 240), 0, 0, 0))
                    }
                }
            }
        }
    }

    private fun drawKaraokeOverlay(
        context: Context,
        canvas: Canvas,
        w: Float,
        h: Float,
        lyric: LyricLine,
        timeMs: Long,
        cfg: KaraokeConfig
    ) {
        val words = lyric.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return
        val progress = ((timeMs - lyric.startMs).toFloat() / (lyric.endMs - lyric.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
        val activeWordIdx = (progress * words.size).toInt().coerceIn(0, words.lastIndex)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = FontRegistry.getTypeface(context, cfg.fontId, lyric.text)
            textSize = (cfg.fontSizeSp / 360f) * min(w, h)
        }
        val activeColor = FaceTextureRenderer.parseColorSafe(cfg.activeColorHex, Color.CYAN)
        val inactiveColor = FaceTextureRenderer.parseColorSafe(cfg.inactiveColorHex, Color.LTGRAY)

        val spaceW = paint.measureText(" ")
        val wordWidths = words.map { paint.measureText(it) }
        val totalW = wordWidths.sum() + spaceW * (words.size - 1)
        val padH = paint.textSize * 0.45f
        val padW = paint.textSize * 0.7f
        val centerY = h * cfg.bottomFraction
        val pillRect = RectF(
            (w - totalW) * 0.5f - padW,
            centerY - paint.textSize * 0.75f - padH * 0.5f,
            (w + totalW) * 0.5f + padW,
            centerY + paint.textSize * 0.35f + padH * 0.5f
        )
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FaceTextureRenderer.parseColorSafe(cfg.pillColorHex, Color.argb(170, 20, 20, 34))
        }
        canvas.drawRoundRect(pillRect, paint.textSize * 0.6f, paint.textSize * 0.6f, pillPaint)

        var x = (w - totalW) * 0.5f
        words.forEachIndexed { idx, word ->
            paint.color = if (idx <= activeWordIdx) activeColor else inactiveColor
            canvas.drawText(word, x, centerY, paint)
            x += wordWidths[idx] + spaceW
        }
    }

    private fun drawCreditOverlay(
        context: Context,
        canvas: Canvas,
        w: Float,
        h: Float,
        project: CubeProject,
        timeMs: Long
    ) {
        val credit = project.credit
        val totalDur = project.computedDurationMs()
        val outroStart = (totalDur - credit.outroDurationMs).coerceAtLeast(0L)
        val outroT = if (credit.outroCenterEnabled && timeMs >= outroStart && credit.outroDurationMs > 0L) {
            KeyframeEvaluator.evaluateEasing(
                EasingType.EASE_IN_OUT,
                ((timeMs - outroStart).toFloat() / credit.outroDurationMs.toFloat()).coerceIn(0f, 1f)
            )
        } else 0f

        val baseScale = credit.scale * (1f + outroT * 1.1f)
        val textSize = min(w, h) * 0.036f * baseScale
        val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = FontRegistry.getTypeface(context, "plus_jakarta_sans", credit.handle)
            this.textSize = textSize
            color = Color.WHITE
            alpha = (credit.opacity * 255).toInt().coerceIn(0, 255)
            setShadowLayer(4f, 0f, 2f, Color.argb(160, 0, 0, 0))
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = FontRegistry.getTypeface(context, "inter", credit.subtitle)
            this.textSize = textSize * 0.68f
            color = Color.parseColor("#B8B8D0")
            alpha = (credit.opacity * 230).toInt().coerceIn(0, 255)
        }

        val pad = min(w, h) * 0.055f
        val startX = when (credit.corner) {
            CreditCorner.TOP_LEFT, CreditCorner.BOTTOM_LEFT -> pad
            CreditCorner.TOP_RIGHT, CreditCorner.BOTTOM_RIGHT -> w - pad - handlePaint.measureText(credit.handle)
        }
        val startY = when (credit.corner) {
            CreditCorner.TOP_LEFT, CreditCorner.TOP_RIGHT -> pad + textSize
            CreditCorner.BOTTOM_LEFT, CreditCorner.BOTTOM_RIGHT -> h - pad - textSize * 0.9f
        }

        val targetX = (w - handlePaint.measureText(credit.handle)) * 0.5f
        val targetY = h * 0.88f * (1f - outroT) + h * 0.52f * outroT
        val drawX = startX + (targetX - startX) * outroT
        val drawY = startY + (targetY - startY) * outroT

        canvas.drawText(credit.handle, drawX, drawY, handlePaint)
        if (credit.subtitle.isNotBlank()) {
            canvas.drawText(credit.subtitle, drawX, drawY + textSize * 0.9f, subPaint)
        }
    }

    private fun drawSafeAreaGuides(canvas: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(95, 0, 210, 255)
            pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        }
        canvas.drawRect(w * 0.08f, h * 0.12f, w * 0.92f, h * 0.78f, p)
        p.color = Color.argb(55, 255, 255, 255)
        canvas.drawLine(w * 0.5f, 0f, w * 0.5f, h, p)
        canvas.drawLine(0f, h * 0.5f, w, h * 0.5f, p)
    }
}
