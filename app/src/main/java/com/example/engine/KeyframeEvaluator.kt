package com.example.engine

import com.example.model.*
import kotlin.math.*

data class CubePose(
    val posX: Float = 0f,
    val posY: Float = 0f,
    val posZ: Float = 0f,
    val rotXDeg: Float = 0f,
    val rotYDeg: Float = 0f,
    val rotZDeg: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
    val cameraZoom: Float = 1f,
    val cameraRollDeg: Float = 0f,
    val shutterAngleDeg: Float = 180f,
    val useQuatSlerp: Boolean = false
)

object KeyframeEvaluator {

    fun faceTargetRotation(face: FaceSide): Triple<Float, Float, Float> = when (face) {
        FaceSide.FRONT -> Triple(0f, 0f, 0f)
        FaceSide.RIGHT -> Triple(0f, -90f, 0f)
        FaceSide.BACK -> Triple(0f, 180f, 0f)
        FaceSide.LEFT -> Triple(0f, 90f, 0f)
        FaceSide.TOP -> Triple(90f, 0f, 0f)
        FaceSide.BOTTOM -> Triple(-90f, 0f, 0f)
    }

    fun evaluateEasing(type: EasingType, rawT: Float): Float {
        val t = rawT.coerceIn(0f, 1f)
        return when (type) {
            EasingType.LINEAR -> t
            EasingType.EASE_IN -> t * t * t
            EasingType.EASE_OUT -> 1f - (1f - t).pow(3)
            EasingType.EASE_IN_OUT -> if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
            EasingType.OVERSHOOT -> {
                val c1 = 1.70158f
                val c3 = c1 + 1f
                1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
            }
            EasingType.BOUNCE -> {
                val n1 = 7.5625f
                val d1 = 2.75f
                when {
                    t < 1f / d1 -> n1 * t * t
                    t < 2f / d1 -> {
                        val x = t - 1.5f / d1
                        n1 * x * x + 0.75f
                    }
                    t < 2.5f / d1 -> {
                        val x = t - 2.25f / d1
                        n1 * x * x + 0.9375f
                    }
                    else -> {
                        val x = t - 2.625f / d1
                        n1 * x * x + 0.984375f
                    }
                }
            }
        }
    }

    fun poseAt(
        keyframes: List<KeyframeTrackItem>,
        timeMs: Long,
        defaultPose: CubePose
    ): CubePose {
        if (keyframes.isEmpty()) return defaultPose
        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeMs <= sorted.first().timeMs) {
            return fromKeyframe(sorted.first())
        }
        if (timeMs >= sorted.last().timeMs) {
            return fromKeyframe(sorted.last())
        }

        var prevIndex = 0
        for (i in sorted.indices) {
            if (sorted[i].timeMs <= timeMs) prevIndex = i else break
        }
        val prev = sorted[prevIndex]
        val next = sorted.getOrNull(prevIndex + 1) ?: return fromKeyframe(prev)

        if (prev.hold || next.timeMs <= prev.timeMs) {
            return fromKeyframe(prev)
        }

        val linearT = ((timeMs - prev.timeMs).toFloat() / (next.timeMs - prev.timeMs).toFloat()).coerceIn(0f, 1f)
        val easedT = evaluateEasing(next.easing, linearT)

        val dX = abs(next.rotXDeg - prev.rotXDeg)
        val dY = abs(next.rotYDeg - prev.rotYDeg)
        val dZ = abs(next.rotZDeg - prev.rotZDeg)
        val isMultiTurn = dX > 181f || dY > 181f || dZ > 181f

        val rx: Float
        val ry: Float
        val rz: Float
        if (isMultiTurn) {
            rx = lerp(prev.rotXDeg, next.rotXDeg, easedT)
            ry = lerp(prev.rotYDeg, next.rotYDeg, easedT)
            rz = lerp(prev.rotZDeg, next.rotZDeg, easedT)
        } else {
            val qa = Quat.fromEulerDeg(prev.rotXDeg, prev.rotYDeg, prev.rotZDeg)
            val qb = Quat.fromEulerDeg(next.rotXDeg, next.rotYDeg, next.rotZDeg)
            val qs = Quat.slerp(qa, qb, easedT)
            val euler = quatToApproxEuler(qs)
            rx = euler.first
            ry = euler.second
            rz = euler.third
        }

        return CubePose(
            posX = lerp(prev.posX, next.posX, easedT),
            posY = lerp(prev.posY, next.posY, easedT),
            posZ = lerp(prev.posZ, next.posZ, easedT),
            rotXDeg = rx,
            rotYDeg = ry,
            rotZDeg = rz,
            scale = lerp(prev.scale, next.scale, easedT),
            opacity = lerp(prev.opacity, next.opacity, easedT).coerceIn(0f, 1f),
            cameraZoom = lerp(prev.cameraZoom, next.cameraZoom, easedT),
            cameraRollDeg = lerp(prev.cameraRollDeg, next.cameraRollDeg, easedT),
            shutterAngleDeg = lerp(prev.shutterAngleDeg, next.shutterAngleDeg, easedT),
            useQuatSlerp = !isMultiTurn
        )
    }

    private fun fromKeyframe(k: KeyframeTrackItem) = CubePose(
        posX = k.posX,
        posY = k.posY,
        posZ = k.posZ,
        rotXDeg = k.rotXDeg,
        rotYDeg = k.rotYDeg,
        rotZDeg = k.rotZDeg,
        scale = k.scale,
        opacity = k.opacity,
        cameraZoom = k.cameraZoom,
        cameraRollDeg = k.cameraRollDeg,
        shutterAngleDeg = k.shutterAngleDeg
    )

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun quatToApproxEuler(q: Quat): Triple<Float, Float, Float> {
        val m = Mat4.fromQuat(q).m
        val sinX = (-m[6]).coerceIn(-1f, 1f)
        val rx = asin(sinX)
        val cosX = cos(rx)
        val ry: Float
        val rz: Float
        if (abs(cosX) > 1e-4f) {
            ry = atan2(m[2], m[10])
            rz = atan2(m[4], m[5])
        } else {
            ry = atan2(-m[8], m[0])
            rz = 0f
        }
        return Triple(
            Math.toDegrees(rx.toDouble()).toFloat(),
            Math.toDegrees(ry.toDouble()).toFloat(),
            Math.toDegrees(rz.toDouble()).toFloat()
        )
    }

    fun compileEffectiveKeyframes(project: CubeProject): Map<String, List<KeyframeTrackItem>> {
        val result = mutableMapOf<String, MutableList<KeyframeTrackItem>>()
        project.cubes.forEach { result[it.id] = mutableListOf() }

        val sortedLyrics = project.lyrics.sortedBy { it.startMs }
        val cubeRunningRot = mutableMapOf<String, Triple<Float, Float, Float>>()
        project.cubes.forEach { cubeRunningRot[it.id] = Triple(it.rotXDeg, it.rotYDeg, it.rotZDeg) }

        for ((idx, line) in sortedLyrics.withIndex()) {
            val cube = project.cubes.firstOrNull { it.id == line.targetCubeId } ?: project.cubes.firstOrNull() ?: continue
            val list = result.getOrPut(cube.id) { mutableListOf() }
            val (curX, curY, curZ) = cubeRunningRot[cube.id] ?: Triple(0f, 0f, 0f)
            val (baseX, baseY, _) = faceTargetRotation(line.targetFace)

            val spin = line.extraSpins * 360f
            val targetX: Float
            val targetY: Float
            val targetZ: Float

            when (line.direction) {
                RotationDirection.RIGHT -> {
                    targetX = adjustAngleNear(baseX, curX)
                    targetY = stepForward(curY, baseY, positive = false) - spin
                    targetZ = 0f
                }
                RotationDirection.LEFT -> {
                    targetX = adjustAngleNear(baseX, curX)
                    targetY = stepForward(curY, baseY, positive = true) + spin
                    targetZ = 0f
                }
                RotationDirection.UP -> {
                    targetX = stepForward(curX, baseX, positive = true) + spin
                    targetY = adjustAngleNear(baseY, curY)
                    targetZ = 0f
                }
                RotationDirection.DOWN -> {
                    targetX = stepForward(curX, baseX, positive = false) - spin
                    targetY = adjustAngleNear(baseY, curY)
                    targetZ = 0f
                }
                RotationDirection.DIAGONAL -> {
                    val sign = if (idx % 2 == 0) 1f else -1f
                    targetX = stepForward(curX, baseX, positive = idx % 2 == 0) + sign * spin
                    targetY = stepForward(curY, baseY, positive = idx % 2 == 1) - sign * spin
                    targetZ = 0f
                }
                RotationDirection.RANDOM -> {
                    val hash = abs(line.id.hashCode())
                    targetX = adjustAngleNear(baseX, curX) + ((hash % 3) - 1) * 360f
                    targetY = stepForward(curY, baseY, positive = hash % 2 == 0) + (if (hash % 2 == 0) spin else -spin)
                    targetZ = 0f
                }
                RotationDirection.AUTO_SHORTEST -> {
                    targetX = adjustAngleNear(baseX, curX)
                    targetY = adjustAngleNear(baseY, curY) + spin
                    targetZ = 0f
                }
            }

            val transStart = (line.startMs + line.anticipationOffsetMs).coerceAtLeast(0L)
            val transEnd = (transStart + line.rotationDurationMs).coerceAtLeast(transStart + 80L)
            val midTime = (transStart + transEnd) / 2L

            list.add(
                KeyframeTrackItem(
                    cubeId = cube.id,
                    timeMs = transStart,
                    posX = cube.posX,
                    posY = cube.posY,
                    posZ = cube.posZ,
                    rotXDeg = curX,
                    rotYDeg = curY,
                    rotZDeg = curZ,
                    scale = cube.scale,
                    hold = false,
                    isGeneratedFromLyric = true
                )
            )
            if (line.zoomPeak != 1.0f || abs(line.zTiltDeg) > 0.5f) {
                val tiltSign = if (idx % 2 == 0) 1f else -1f
                list.add(
                    KeyframeTrackItem(
                        cubeId = cube.id,
                        timeMs = midTime,
                        posX = cube.posX,
                        posY = cube.posY,
                        posZ = cube.posZ,
                        rotXDeg = (curX + targetX) * 0.5f,
                        rotYDeg = (curY + targetY) * 0.5f,
                        rotZDeg = tiltSign * line.zTiltDeg,
                        scale = cube.scale * line.zoomPeak,
                        easing = line.easing,
                        isGeneratedFromLyric = true
                    )
                )
            }
            list.add(
                KeyframeTrackItem(
                    cubeId = cube.id,
                    timeMs = transEnd,
                    posX = cube.posX,
                    posY = cube.posY,
                    posZ = cube.posZ,
                    rotXDeg = targetX,
                    rotYDeg = targetY,
                    rotZDeg = targetZ,
                    scale = cube.scale,
                    easing = line.easing,
                    isGeneratedFromLyric = true
                )
            )
            cubeRunningRot[cube.id] = Triple(targetX, targetY, targetZ)
        }

        val manualByCube = project.keyframes.groupBy { it.cubeId }
        manualByCube.forEach { (cubeId, manuals) ->
            val list = result.getOrPut(cubeId) { mutableListOf() }
            manuals.forEach { mk ->
                list.removeAll { it.isGeneratedFromLyric && abs(it.timeMs - mk.timeMs) <= 80L }
                list.add(mk)
            }
        }

        result.keys.forEach { k ->
            result[k] = result[k]!!.sortedBy { it.timeMs }.toMutableList()
        }
        return result
    }

    private fun adjustAngleNear(targetMod: Float, current: Float): Float {
        var candidate = targetMod
        while (candidate - current > 180f) candidate -= 360f
        while (candidate - current < -180f) candidate += 360f
        return candidate
    }

    private fun stepForward(current: Float, targetMod: Float, positive: Boolean): Float {
        var candidate = adjustAngleNear(targetMod, current)
        if (positive && candidate <= current + 5f) candidate += 360f
        if (!positive && candidate >= current - 5f) candidate -= 360f
        return candidate
    }

    fun computeCubeWorldMatrices(
        cubes: List<SceneCube>,
        poses: Map<String, CubePose>
    ): Map<String, Mat4> {
        val byId = cubes.associateBy { it.id }
        val cache = mutableMapOf<String, Mat4>()
        val visiting = mutableSetOf<String>()

        fun resolve(cube: SceneCube): Mat4 {
            cache[cube.id]?.let { return it }
            if (!visiting.add(cube.id)) return Mat4.identity()

            val pose = poses[cube.id] ?: CubePose(
                posX = cube.posX, posY = cube.posY, posZ = cube.posZ,
                rotXDeg = cube.rotXDeg, rotYDeg = cube.rotYDeg, rotZDeg = cube.rotZDeg,
                scale = cube.scale, opacity = cube.opacity
            )

            val localTrans = Mat4.translation(pose.posX, pose.posY, pose.posZ)
            val localRot = Mat4.fromQuat(Quat.fromEulerDeg(pose.rotXDeg, pose.rotYDeg, pose.rotZDeg))
            val localScale = Mat4.scale(pose.scale, pose.scale, pose.scale)
            val localMat = localTrans * localRot * localScale

            val world = if (cube.parentId != null && byId.containsKey(cube.parentId)) {
                val parent = byId.getValue(cube.parentId)
                val parentWorld = resolve(parent)
                val faceNormal = when (cube.parentFace ?: FaceSide.RIGHT) {
                    FaceSide.FRONT -> Vec3(0f, 0f, 1f)
                    FaceSide.BACK -> Vec3(0f, 0f, -1f)
                    FaceSide.RIGHT -> Vec3(1f, 0f, 0f)
                    FaceSide.LEFT -> Vec3(-1f, 0f, 0f)
                    FaceSide.TOP -> Vec3(0f, 1f, 0f)
                    FaceSide.BOTTOM -> Vec3(0f, -1f, 0f)
                }
                val dist = (parent.size * 0.5f) + (cube.size * pose.scale * 0.5f)
                val attachOffset = Mat4.translation(
                    faceNormal.x * dist,
                    faceNormal.y * dist,
                    faceNormal.z * dist
                )
                if (cube.hingeMode) {
                    val hingePivot = Vec3(faceNormal.x * (parent.size * 0.5f), faceNormal.y * (parent.size * 0.5f), faceNormal.z * (parent.size * 0.5f))
                    val toEdge = Mat4.translation(hingePivot.x, hingePivot.y, hingePivot.z)
                    val childOffsetFromEdge = Mat4.translation(
                        faceNormal.x * (cube.size * pose.scale * 0.5f),
                        faceNormal.y * (cube.size * pose.scale * 0.5f),
                        faceNormal.z * (cube.size * pose.scale * 0.5f)
                    )
                    parentWorld * toEdge * localRot * childOffsetFromEdge * localScale
                } else {
                    parentWorld * attachOffset * localMat
                }
            } else {
                localMat
            }

            visiting.remove(cube.id)
            cache[cube.id] = world
            return world
        }

        cubes.forEach { resolve(it) }
        return cache
    }

    fun generateMotionPresetKeyframes(
        preset: MotionPresetType,
        cubeId: String,
        startMs: Long,
        durationMs: Long,
        intensity: Float,
        basePose: CubePose
    ): List<KeyframeTrackItem> {
        val d = durationMs.coerceAtLeast(200L)
        val mid = startMs + d / 2
        val end = startMs + d
        val kStart = KeyframeTrackItem(
            cubeId = cubeId, timeMs = startMs,
            posX = basePose.posX, posY = basePose.posY, posZ = basePose.posZ,
            rotXDeg = basePose.rotXDeg, rotYDeg = basePose.rotYDeg, rotZDeg = basePose.rotZDeg,
            scale = basePose.scale
        )
        return when (preset) {
            MotionPresetType.SPIN_RIGHT -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, rotYDeg = basePose.rotYDeg - 90f * intensity.coerceAtLeast(1f).roundToInt(), easing = EasingType.EASE_IN_OUT)
            )
            MotionPresetType.SPIN_LEFT -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, rotYDeg = basePose.rotYDeg + 90f * intensity.coerceAtLeast(1f).roundToInt(), easing = EasingType.EASE_IN_OUT)
            )
            MotionPresetType.FLIP_UP -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, rotXDeg = basePose.rotXDeg + 90f * intensity.coerceAtLeast(1f).roundToInt(), easing = EasingType.EASE_IN_OUT)
            )
            MotionPresetType.FLIP_DOWN -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, rotXDeg = basePose.rotXDeg - 90f * intensity.coerceAtLeast(1f).roundToInt(), easing = EasingType.EASE_IN_OUT)
            )
            MotionPresetType.DIAGONAL_TUMBLE -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = mid, rotXDeg = basePose.rotXDeg + 45f * intensity, rotYDeg = basePose.rotYDeg - 45f * intensity, rotZDeg = 14f * intensity, scale = basePose.scale * 1.15f),
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, rotXDeg = basePose.rotXDeg + 90f, rotYDeg = basePose.rotYDeg - 90f, rotZDeg = 0f, scale = basePose.scale)
            )
            MotionPresetType.BARREL_ROLL -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, rotZDeg = basePose.rotZDeg + 360f * intensity.coerceAtLeast(1f), easing = EasingType.EASE_IN_OUT)
            )
            MotionPresetType.BOUNCE_IN -> listOf(
                kStart.copy(scale = 0.2f, rotYDeg = basePose.rotYDeg - 180f),
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, scale = basePose.scale, easing = EasingType.BOUNCE)
            )
            MotionPresetType.PUNCH_ZOOM -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = startMs + d / 3, scale = basePose.scale * (1f + 0.35f * intensity), rotZDeg = basePose.rotZDeg - 8f * intensity, easing = EasingType.EASE_OUT),
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, scale = basePose.scale, rotZDeg = basePose.rotZDeg, easing = EasingType.EASE_IN_OUT)
            )
            MotionPresetType.SHAKE -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = startMs + d / 4, posX = basePose.posX - 0.14f * intensity, rotZDeg = -6f * intensity, easing = EasingType.LINEAR),
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = startMs + d * 2 / 4, posX = basePose.posX + 0.14f * intensity, rotZDeg = 6f * intensity, easing = EasingType.LINEAR),
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = startMs + d * 3 / 4, posX = basePose.posX - 0.08f * intensity, rotZDeg = -3f * intensity, easing = EasingType.LINEAR),
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, posX = basePose.posX, rotZDeg = basePose.rotZDeg, easing = EasingType.EASE_OUT)
            )
            MotionPresetType.WHIP_PAN -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = mid, rotYDeg = basePose.rotYDeg - 180f, scale = basePose.scale * 1.2f, shutterAngleDeg = 320f, easing = EasingType.EASE_IN),
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, rotYDeg = basePose.rotYDeg - 360f, scale = basePose.scale, shutterAngleDeg = 180f, easing = EasingType.EASE_OUT)
            )
            MotionPresetType.SLOW_DRIFT -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, rotXDeg = basePose.rotXDeg + 12f * intensity, rotYDeg = basePose.rotYDeg + 24f * intensity, easing = EasingType.LINEAR)
            )
            MotionPresetType.RANDOM_TUMBLE -> listOf(
                kStart,
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = mid, rotXDeg = basePose.rotXDeg + 120f, rotYDeg = basePose.rotYDeg - 90f, rotZDeg = 22f, easing = EasingType.EASE_IN_OUT),
                kStart.copy(id = java.util.UUID.randomUUID().toString(), timeMs = end, rotXDeg = basePose.rotXDeg + 180f, rotYDeg = basePose.rotYDeg - 180f, rotZDeg = 0f, easing = EasingType.EASE_OUT)
            )
        }
    }
}
