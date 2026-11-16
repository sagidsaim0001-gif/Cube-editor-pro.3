package com.example.audio

import android.content.ContentUris
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class DeviceSong(
    val id: Long,
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dateAddedSec: Long
)

data class WaveformAndBeats(
    val peaks: List<Float>,
    val beatMarkersMs: List<Long>
)

object AudioLibrary {

    suspend fun queryDeviceSongs(context: Context): List<DeviceSong> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<DeviceSong>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val selection = "${MediaStore.Audio.Media.DURATION} > 1000"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    songs.add(
                        DeviceSong(
                            id = id,
                            uriString = uri.toString(),
                            title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Unknown Track",
                            artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown Artist",
                            album = cursor.getString(albumCol) ?: "",
                            durationMs = cursor.getLong(durCol).coerceAtLeast(1000L),
                            dateAddedSec = cursor.getLong(dateCol)
                        )
                    )
                }
            }
        }
        songs
    }

    suspend fun probeAudioMetadata(context: Context, uriString: String): Triple<String, String, Long>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(context, uri)
                val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: uri.lastPathSegment?.substringAfterLast('/')
                    ?: "Imported Audio"
                val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Local File"
                val dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 15_000L
                mmr.release()
                Triple(title, artist, dur.coerceAtLeast(1000L))
            }.getOrNull()
        }

    /**
     * Decodes audio chunks via MediaExtractor + MediaCodec in a background thread to compute
     * waveform peaks and energy-onset beat timestamps.
     */
    suspend fun extractWaveformAndBeats(
        context: Context,
        uriString: String,
        durationMs: Long,
        targetBars: Int = 140
    ): WaveformAndBeats = withContext(Dispatchers.Default) {
        val uri = Uri.parse(uriString)
        val barSums = FloatArray(targetBars)
        val barCounts = IntArray(targetBars)

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                val mime = tf.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = tf
                    break
                }
            }
            if (audioTrackIndex == -1 || format == null) {
                return@withContext fallbackWaveform(durationMs, targetBars)
            }
            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext fallbackWaveform(durationMs, targetBars)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sawInputEOS = false
            var sawOutputEOS = false
            val totalUs = (durationMs * 1000L).coerceAtLeast(1_000_000L)
            var decodedFrames = 0

            while (!sawOutputEOS && decodedFrames < 1800) {
                if (!sawInputEOS) {
                    val inIdx = codec.dequeueInputBuffer(5_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(buf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                            // Skip ahead slightly on long songs to stay fast (< 1.5s)
                            extractor.advance()
                            if (durationMs > 90_000L && decodedFrames % 3 == 0) {
                                extractor.advance()
                            }
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 5_000)
                if (outIdx >= 0) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true
                    }
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        outBuf.order(ByteOrder.LITTLE_ENDIAN)
                        val shortBuf = outBuf.asShortBuffer()
                        var sumSq = 0.0
                        val count = shortBuf.remaining()
                        var i = 0
                        while (i < count) {
                            val s = shortBuf.get(i) / 32768.0
                            sumSq += s * s
                            i += 4
                        }
                        val rms = sqrt(sumSq / max(1, count / 4)).toFloat()
                        val barIdx = ((info.presentationTimeUs.toDouble() / totalUs.toDouble()) * targetBars)
                            .toInt().coerceIn(0, targetBars - 1)
                        barSums[barIdx] += rms
                        barCounts[barIdx] += 1
                        decodedFrames++
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                }
            }
            codec.stop()
            codec.release()
            extractor.release()

            val rawPeaks = FloatArray(targetBars) { idx ->
                if (barCounts[idx] > 0) barSums[idx] / barCounts[idx] else 0.08f
            }
            val maxPeak = rawPeaks.maxOrNull()?.coerceAtLeast(0.05f) ?: 1f
            val normalized = rawPeaks.map { (it / maxPeak).coerceIn(0.08f, 1f) }
            val beats = detectBeatsFromPeaks(normalized, durationMs)
            WaveformAndBeats(normalized, beats)
        } catch (_: Exception) {
            runCatching { extractor.release() }
            fallbackWaveform(durationMs, targetBars)
        }
    }

    private fun detectBeatsFromPeaks(peaks: List<Float>, durationMs: Long): List<Long> {
        if (peaks.size < 4) return emptyList()
        val beats = mutableListOf<Long>()
        val minSpacingMs = 340L
        var lastBeat = -minSpacingMs
        for (i in 1 until peaks.lastIndex) {
            val prev = peaks[i - 1]
            val cur = peaks[i]
            val next = peaks[i + 1]
            val localAvg = (peaks.subList(max(0, i - 3), minOf(peaks.size, i + 4)).average()).toFloat()
            if (cur >= prev && cur >= next && cur > localAvg * 1.18f && cur > 0.36f) {
                val timeMs = ((i.toFloat() / peaks.size) * durationMs).toLong()
                if (timeMs - lastBeat >= minSpacingMs) {
                    beats.add(timeMs)
                    lastBeat = timeMs
                }
            }
        }
        if (beats.isEmpty()) {
            // Regular 120 BPM fallback markers
            var t = 500L
            while (t < durationMs) {
                beats.add(t)
                t += 500L
            }
        }
        return beats
    }

    private fun fallbackWaveform(durationMs: Long, bars: Int): WaveformAndBeats {
        val peaks = List(bars) { i ->
            (0.25f + 0.55f * abs(kotlin.math.sin(i * 0.37f))).coerceIn(0.1f, 1f)
        }
        val beats = mutableListOf<Long>()
        var t = 500L
        while (t < durationMs) {
            beats.add(t)
            t += 500L
        }
        return WaveformAndBeats(peaks, beats)
    }
}

class AudioClockPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false
    private var loadedUri: String? = null
    private var trimStartMs: Long = 0L
    private var currentSpeed: Float = 1.0f
    private var currentVolume: Float = 1.0f

    var onError: ((String) -> Unit)? = null

    fun prepare(uriString: String?, trimStart: Long, volume: Float): Boolean {
        trimStartMs = trimStart
        currentVolume = volume
        if (uriString.isNullOrBlank()) {
            release()
            return false
        }
        if (loadedUri == uriString && prepared && mediaPlayer != null) {
            setVolume(volume)
            return true
        }
        release()
        return try {
            val mp = MediaPlayer()
            mp.setDataSource(context, Uri.parse(uriString))
            mp.prepare()
            mp.setVolume(volume, volume)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && currentSpeed != 1.0f) {
                mp.playbackParams = PlaybackParams().setSpeed(currentSpeed)
            }
            mediaPlayer = mp
            loadedUri = uriString
            prepared = true
            true
        } catch (e: Exception) {
            prepared = false
            loadedUri = null
            onError?.invoke("Audio file could not be opened. Please relink it.")
            false
        }
    }

    fun play(timelineMs: Long, timelineOffsetMs: Long) {
        val mp = mediaPlayer ?: return
        if (!prepared) return
        val trackTarget = (timelineMs - timelineOffsetMs + trimStartMs).coerceIn(0L, mp.duration.toLong())
        runCatching {
            mp.seekTo(trackTarget.toInt())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mp.playbackParams = PlaybackParams().setSpeed(currentSpeed)
            }
            mp.start()
        }
    }

    fun pause() {
        runCatching {
            if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
        }
    }

    fun seekTo(timelineMs: Long, timelineOffsetMs: Long) {
        val mp = mediaPlayer ?: return
        if (!prepared) return
        val target = (timelineMs - timelineOffsetMs + trimStartMs).coerceIn(0L, mp.duration.toLong())
        runCatching { mp.seekTo(target.toInt()) }
    }

    fun setSpeed(speed: Float) {
        currentSpeed = speed
        val mp = mediaPlayer ?: return
        if (prepared && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val wasPlaying = mp.isPlaying
                mp.playbackParams = PlaybackParams().setSpeed(speed)
                if (!wasPlaying) mp.pause()
            }
        }
    }

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        runCatching { mediaPlayer?.setVolume(currentVolume, currentVolume) }
    }

    fun currentTimelinePositionMs(timelineOffsetMs: Long): Long? {
        val mp = mediaPlayer ?: return null
        if (!prepared || !mp.isPlaying) return null
        return runCatching {
            (mp.currentPosition.toLong() - trimStartMs + timelineOffsetMs).coerceAtLeast(0L)
        }.getOrNull()
    }

    fun isPlaying(): Boolean = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    fun release() {
        runCatching {
            mediaPlayer?.stop()
        }
        runCatching {
            mediaPlayer?.release()
        }
        mediaPlayer = null
        prepared = false
        loadedUri = null
    }
}
