package com.example.audio

import com.example.model.*
import kotlin.math.abs

object LyricsParser {

    private val lrcTimestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val enhancedWordTagRegex = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")
    private val srtTimeRegex = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")

    fun parseAuto(rawText: String, targetCubeId: String, totalDurationMs: Long = 15_000L): List<LyricLine> {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return emptyList()

        return when {
            srtTimeRegex.containsMatchIn(trimmed) -> parseSrt(trimmed, targetCubeId)
            lrcTimestampRegex.containsMatchIn(trimmed) -> parseLrc(trimmed, targetCubeId, totalDurationMs)
            else -> parsePlainLines(trimmed, targetCubeId, totalDurationMs)
        }
    }

    fun parseLrc(rawText: String, targetCubeId: String, totalDurationMs: Long = 15_000L): List<LyricLine> {
        val timed = mutableListOf<Pair<Long, String>>()
        rawText.lines().forEach { line ->
            val matches = lrcTimestampRegex.findAll(line).toList()
            if (matches.isEmpty()) return@forEach
            val cleanText = line.replace(lrcTimestampRegex, "")
                .replace(enhancedWordTagRegex, "")
                .trim()
            if (cleanText.isEmpty()) return@forEach

            matches.forEach { m ->
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = m.groupValues[3]
                val fracMs = when (fracStr.length) {
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    3 -> fracStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val ms = min * 60_000L + sec * 1000L + fracMs
                timed.add(ms to cleanText)
            }
        }
        val sorted = timed.sortedBy { it.first }
        if (sorted.isEmpty()) return emptyList()

        return sorted.mapIndexed { idx, (startMs, text) ->
            val nextStart = sorted.getOrNull(idx + 1)?.first
            val endMs = (nextStart ?: maxOf(startMs + 2500L, totalDurationMs)).coerceAtLeast(startMs + 300L)
            LyricLine(
                text = text,
                startMs = startMs,
                endMs = endMs,
                targetCubeId = targetCubeId,
                targetFace = FaceSide.FOUR_SIDES[idx % FaceSide.FOUR_SIDES.size]
            )
        }
    }

    fun parseSrt(rawText: String, targetCubeId: String): List<LyricLine> {
        val blocks = rawText.trim().split(Regex("""\r?\n\r?\n+"""))
        val out = mutableListOf<LyricLine>()
        blocks.forEach { block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val timeLineIdx = lines.indexOfFirst { srtTimeRegex.containsMatchIn(it) }
            if (timeLineIdx == -1) return@forEach
            val m = srtTimeRegex.find(lines[timeLineIdx]) ?: return@forEach
            val startMs = toMs(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
            val endMs = toMs(m.groupValues[5], m.groupValues[6], m.groupValues[7], m.groupValues[8])
                .coerceAtLeast(startMs + 300L)
            val body = lines.drop(timeLineIdx + 1).joinToString(" ").trim()
            if (body.isNotEmpty()) {
                out.add(
                    LyricLine(
                        text = body,
                        startMs = startMs,
                        endMs = endMs,
                        targetCubeId = targetCubeId,
                        targetFace = FaceSide.FOUR_SIDES[out.size % FaceSide.FOUR_SIDES.size]
                    )
                )
            }
        }
        return out
    }

    fun parsePlainLines(rawText: String, targetCubeId: String, totalDurationMs: Long): List<LyricLine> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()
        val span = (totalDurationMs.coerceAtLeast(lines.size * 1500L)) / lines.size
        return lines.mapIndexed { idx, text ->
            val start = idx * span
            val end = (start + span).coerceAtLeast(start + 500L)
            LyricLine(
                text = text,
                startMs = start,
                endMs = end,
                targetCubeId = targetCubeId,
                targetFace = FaceSide.FOUR_SIDES[idx % FaceSide.FOUR_SIDES.size]
            )
        }
    }

    fun snapTimeToBeat(timeMs: Long, beats: List<Long>, thresholdMs: Long = 140L): Long {
        if (beats.isEmpty()) return timeMs
        val nearest = beats.minByOrNull { abs(it - timeMs) } ?: return timeMs
        return if (abs(nearest - timeMs) <= thresholdMs) nearest else timeMs
    }

    private fun toMs(h: String, m: String, s: String, ms: String): Long {
        return (h.toLongOrNull() ?: 0L) * 3_600_000L +
            (m.toLongOrNull() ?: 0L) * 60_000L +
            (s.toLongOrNull() ?: 0L) * 1000L +
            (ms.toLongOrNull() ?: 0L)
    }
}
