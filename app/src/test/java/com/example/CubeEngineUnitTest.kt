package com.example

import com.example.audio.LyricsParser
import com.example.engine.*
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CubeEngineUnitTest {

    @Test
    fun `poseAt clamps before first and after last keyframe`() {
        val kfs = listOf(
            KeyframeTrackItem(cubeId = "c1", timeMs = 1000L, rotYDeg = -90f, scale = 1.2f),
            KeyframeTrackItem(cubeId = "c1", timeMs = 2000L, rotYDeg = -180f, scale = 0.8f, easing = EasingType.LINEAR)
        )
        val before = KeyframeEvaluator.poseAt(kfs, 200L, CubePose())
        assertEquals(-90f, before.rotYDeg, 0.01f)
        assertEquals(1.2f, before.scale, 0.01f)

        val after = KeyframeEvaluator.poseAt(kfs, 5000L, CubePose())
        assertEquals(0.8f, after.scale, 0.01f)
    }

    @Test
    fun `poseAt handles duplicate timestamps and multi-turn spin`() {
        val kfs = listOf(
            KeyframeTrackItem(cubeId = "c1", timeMs = 0L, rotYDeg = 0f),
            KeyframeTrackItem(cubeId = "c1", timeMs = 1000L, rotYDeg = 0f),
            KeyframeTrackItem(cubeId = "c1", timeMs = 2000L, rotYDeg = 450f, easing = EasingType.LINEAR)
        )
        val mid = KeyframeEvaluator.poseAt(kfs, 1500L, CubePose())
        assertEquals(225f, mid.rotYDeg, 1f)
    }

    @Test
    fun `quaternion slerp picks shortest path`() {
        val qA = Quat.fromEulerDeg(0f, 350f, 0f)
        val qB = Quat.fromEulerDeg(0f, 10f, 0f)
        val mid = Quat.slerp(qA, qB, 0.5f)
        // Midpoint between 350 and 10 deg around Y should be ~0 deg (w ~ 1, y ~ 0)
        assertTrue(abs(mid.w) > 0.99f)
        assertTrue(abs(mid.y) < 0.05f)
    }

    @Test
    fun `parent child matrix attaches flush to parent face`() {
        val parent = SceneCube(id = "p", size = 1.0f)
        val child = SceneCube(id = "c", size = 0.8f, parentId = "p", parentFace = FaceSide.RIGHT)
        val mats = KeyframeEvaluator.computeCubeWorldMatrices(
            listOf(parent, child),
            mapOf("p" to CubePose(), "c" to CubePose())
        )
        val childOrigin = mats.getValue("c").transformPoint(Vec3(0f, 0f, 0f))
        // Parent half (0.5) + child half (0.4) = 0.9 on +X
        assertEquals(0.9f, childOrigin.x, 0.001f)
        assertEquals(0f, childOrigin.y, 0.001f)
        assertEquals(0f, childOrigin.z, 0.001f)
    }

    @Test
    fun `compileEffectiveKeyframes rotates cube to expected face at each lyric`() {
        val c1 = SceneCube(id = "c1")
        val lyrics = listOf(
            LyricLine(text = "One", startMs = 0L, endMs = 2000L, targetCubeId = "c1", targetFace = FaceSide.FRONT),
            LyricLine(text = "Two", startMs = 2000L, endMs = 4000L, targetCubeId = "c1", targetFace = FaceSide.RIGHT, direction = RotationDirection.RIGHT),
            LyricLine(text = "Three", startMs = 4000L, endMs = 6000L, targetCubeId = "c1", targetFace = FaceSide.BACK, direction = RotationDirection.RIGHT)
        )
        val proj = CubeProject(cubes = listOf(c1), lyrics = lyrics)
        val compiled = KeyframeEvaluator.compileEffectiveKeyframes(proj)["c1"].orEmpty()
        assertTrue(compiled.isNotEmpty())
        val poseAtLyric2Hold = KeyframeEvaluator.poseAt(compiled, 2600L, CubePose())
        // Right face front corresponds to rotY ~ -90 mod 360
        val modY = ((poseAtLyric2Hold.rotYDeg % 360f) + 360f) % 360f
        assertEquals(270f, modY, 2f)
    }

    @Test
    fun `autoFit places words inside face bounds without overlap`() {
        val face = CubeFace(
            side = FaceSide.FRONT,
            layoutMode = FaceLayoutMode.AUTO_STACKED,
            textItems = listOf(
                FaceTextItem(text = "RAAT"),
                FaceTextItem(text = "BHAR"),
                FaceTextItem(text = "BEATS")
            )
        )
        val placements = TextAutoFit.computePlacements(face, faceSizePx = 512f)
        assertEquals(3, placements.size)
        assertTrue(placements[0].centerY < placements[1].centerY)
        assertTrue(placements[1].centerY < placements[2].centerY)
        placements.forEach { p ->
            assertTrue(p.centerY in 0f..512f)
            assertTrue(p.fontSizePx >= 14f)
        }
    }

    @Test
    fun `lyricsParser parses LRC including Devanagari and snaps to beats`() {
        val lrc = """
            [00:01.50]रात भर
            [00:04.00]Bass Bajega
        """.trimIndent()
        val parsed = LyricsParser.parseLrc(lrc, targetCubeId = "c1")
        assertEquals(2, parsed.size)
        assertEquals(1500L, parsed[0].startMs)
        assertEquals("रात भर", parsed[0].text)
        assertEquals(4000L, parsed[1].startMs)

        val snapped = LyricsParser.snapTimeToBeat(1540L, listOf(1000L, 1500L, 2000L))
        assertEquals(1500L, snapped)
    }

    @Test
    fun `project JSON roundtrip preserves cubes lyrics and schemaVersion`() {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(CubeProject::class.java)
        val original = CubeProject(name = "Test Cube")
        val json = adapter.toJson(original)
        val decoded = adapter.fromJson(json)
        assertNotNull(decoded)
        assertEquals(original.id, decoded!!.id)
        assertEquals(original.name, decoded.name)
        assertEquals(CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(original.cubes.size, decoded.cubes.size)
    }
}
