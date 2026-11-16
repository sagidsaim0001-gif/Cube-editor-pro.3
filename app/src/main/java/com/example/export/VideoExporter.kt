package com.example.export

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.media.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.engine.FaceTextureCache
import com.example.engine.SceneRenderer
import com.example.model.CubeProject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

data class ExportProgress(
    val progressFraction: Float = 0f,
    val currentFrame: Int = 0,
    val totalFrames: Int = 1,
    val etaSeconds: Long = 0L,
    val stageLabel: String = "Preparing…"
)

data class ExportResult(
    val cacheFile: File,
    val galleryUri: Uri?,
    val durationMs: Long,
    val width: Int,
    val height: Int
)

object VideoExporter {

    suspend fun exportProjectToMp4(
        context: Context,
        project: CubeProject,
        bgBitmap: Bitmap?,
        fastPreview: Boolean = false,
        rangeStartMs: Long = 0L,
        rangeEndMs: Long = project.computedDurationMs(),
        onProgress: (ExportProgress) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        val (width, height) = project.outputDimensions(fastPreview = fastPreview)
        val fps = if (fastPreview) 24 else project.exportConfig.fps.coerceIn(24, 60)
        val subFrames = if (fastPreview) 1 else project.exportConfig.motionBlurSubFrames.coerceIn(1, 8)
        val texSize = if (fastPreview) 256 else 512

        val durationMs = (rangeEndMs - rangeStartMs).coerceAtLeast(1000L)
        val totalFrames = ((durationMs / 1000.0) * fps).roundToInt().coerceAtLeast(1)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = project.name.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "CubeVideo" }
        val outFile = File(exportDir, "${safeName}_${System.currentTimeMillis()}.mp4")
        if (outFile.exists()) outFile.delete()

        val faceCache = FaceTextureCache(36)
        val startWallMs = System.currentTimeMillis()

        val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, if (fastPreview) 2_500_000 else if (width >= 1080) 8_000_000 else 5_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var muxerStarted = false

        // Prepare optional AAC audio track BEFORE starting muxer if needed, or encode video first then remux.
        // Safer across all Android OEMs: encode video-only temp MP4, then mux audio + video cleanly in a second fast pass!
        val videoOnlyFile = File(exportDir, "v_${System.currentTimeMillis()}.mp4")
        muxer.release()
        val videoMuxer = MediaMuxer(videoOnlyFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val rAcc = if (subFrames > 1) IntArray(width * height) else null
        val gAcc = if (subFrames > 1) IntArray(width * height) else null
        val bAcc = if (subFrames > 1) IntArray(width * height) else null
        val subPixels = if (subFrames > 1) IntArray(width * height) else null
        val subBmp = if (subFrames > 1) Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) else null
        val finalBmp = if (subFrames > 1) Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) else null
        val subCanvas = subBmp?.let { Canvas(it) }

        val bufferInfo = MediaCodec.BufferInfo()
        val shutterMs = ((project.effects.shutterAngleDeg / 360f) * (1000f / fps)).toLong().coerceAtLeast(1L)

        var emittedVideoFrames = 0L

        try {
            ExportForegroundService.updateProgress(context, 0, "Preparing 3D export…")
            for (frameIdx in 0 until totalFrames) {
                if (!isActive) throw CancellationException("Export cancelled")

                val frameTimeMs = rangeStartMs + ((frameIdx * 1000L) / fps)
                val canvas = inputSurface.lockCanvas(null)
                try {
                    if (subFrames > 1 && subCanvas != null && subBmp != null && finalBmp != null &&
                        rAcc != null && gAcc != null && bAcc != null && subPixels != null
                    ) {
                        rAcc.fill(0)
                        gAcc.fill(0)
                        bAcc.fill(0)
                        for (sf in 0 until subFrames) {
                            val offsetMs = ((sf.toFloat() / subFrames) - 0.5f) * shutterMs
                            val tSub = (frameTimeMs + offsetMs.toLong()).coerceAtLeast(0L)
                            SceneRenderer.renderFrame(
                                context = context,
                                canvas = subCanvas,
                                width = width,
                                height = height,
                                project = project,
                                timeMs = tSub,
                                faceTextureCache = faceCache,
                                bgBitmap = bgBitmap,
                                textureSizePx = texSize,
                                isExport = true
                            )
                            subBmp.getPixels(subPixels, 0, width, 0, 0, width, height)
                            for (p in subPixels.indices) {
                                val px = subPixels[p]
                                rAcc[p] += (px shr 16) and 0xFF
                                gAcc[p] += (px shr 8) and 0xFF
                                bAcc[p] += px and 0xFF
                            }
                        }
                        for (p in subPixels.indices) {
                            val r = rAcc[p] / subFrames
                            val g = gAcc[p] / subFrames
                            val b = bAcc[p] / subFrames
                            subPixels[p] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                        finalBmp.setPixels(subPixels, 0, width, 0, 0, width, height)
                        canvas.drawBitmap(finalBmp, 0f, 0f, null)
                    } else {
                        SceneRenderer.renderFrame(
                            context = context,
                            canvas = canvas,
                            width = width,
                            height = height,
                            project = project,
                            timeMs = frameTimeMs,
                            faceTextureCache = faceCache,
                            bgBitmap = bgBitmap,
                            textureSizePx = texSize,
                            isExport = true
                        )
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                val drained = drainEncoder(
                    encoder = encoder,
                    muxer = videoMuxer,
                    currentTrackIndex = videoTrackIndex,
                    bufferInfo = bufferInfo,
                    endOfStream = false,
                    fps = fps,
                    emittedCountStart = emittedVideoFrames
                ) { started ->
                    muxerStarted = started
                }
                videoTrackIndex = drained.first
                emittedVideoFrames = drained.second

                val elapsedSec = ((System.currentTimeMillis() - startWallMs) / 1000.0).coerceAtLeast(0.1)
                val frac = (frameIdx + 1).toFloat() / totalFrames.toFloat()
                val eta = if (frac > 0.02f) ((elapsedSec / frac) - elapsedSec).toLong().coerceAtLeast(0L) else 0L
                val stage = "Rendering 3D frames (${frameIdx + 1}/$totalFrames)"
                if (frameIdx % 6 == 0 || frameIdx == totalFrames - 1) {
                    ExportForegroundService.updateProgress(context, (frac * 85).toInt(), stage)
                }
                onProgress(
                    ExportProgress(
                        progressFraction = frac * 0.85f,
                        currentFrame = frameIdx + 1,
                        totalFrames = totalFrames,
                        etaSeconds = eta,
                        stageLabel = stage
                    )
                )
            }

            encoder.signalEndOfInputStream()
            val finalDrained = drainEncoder(
                encoder = encoder,
                muxer = videoMuxer,
                currentTrackIndex = videoTrackIndex,
                bufferInfo = bufferInfo,
                endOfStream = true,
                fps = fps,
                emittedCountStart = emittedVideoFrames
            ) { started ->
                muxerStarted = started
            }
            videoTrackIndex = finalDrained.first
            emittedVideoFrames = finalDrained.second
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { inputSurface.release() }
            if (muxerStarted) runCatching { videoMuxer.stop() }
            runCatching { videoMuxer.release() }
            subBmp?.recycle()
            finalBmp?.recycle()
            faceCache.clear()
        }

        // Mux audio if enabled & present
        val music = project.music
        if (project.exportConfig.includeAudio && music != null) {
            onProgress(ExportProgress(0.90f, totalFrames, totalFrames, 1L, "Muxing synced audio…"))
            ExportForegroundService.updateProgress(context, 90, "Muxing synced audio…")
            val muxedOk = runCatching {
                muxVideoWithAudio(
                    context = context,
                    videoOnlyFile = videoOnlyFile,
                    outFile = outFile,
                    audioUri = Uri.parse(music.uriString),
                    trimStartMs = music.trimStartMs,
                    durationMs = durationMs,
                    volume = music.volume,
                    fadeInMs = music.fadeInMs,
                    fadeOutMs = music.fadeOutMs,
                    bitrateKbps = project.exportConfig.audioBitrateKbps
                )
            }.getOrDefault(false)

            if (!muxedOk) {
                videoOnlyFile.copyTo(outFile, overwrite = true)
            }
            videoOnlyFile.delete()
        } else {
            videoOnlyFile.copyTo(outFile, overwrite = true)
            videoOnlyFile.delete()
        }

        onProgress(ExportProgress(0.97f, totalFrames, totalFrames, 0L, "Saving to Gallery…"))
        val galleryUri = saveMp4ToGallery(context, outFile, "${safeName}.mp4")
        ExportForegroundService.stop(context)

        onProgress(ExportProgress(1f, totalFrames, totalFrames, 0L, "Done!"))
        ExportResult(
            cacheFile = outFile,
            galleryUri = galleryUri,
            durationMs = durationMs,
            width = width,
            height = height
        )
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        currentTrackIndex: Int,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        fps: Int,
        emittedCountStart: Long,
        onMuxerStarted: (Boolean) -> Unit
    ): Pair<Int, Long> {
        var trackIndex = currentTrackIndex
        var emitted = emittedCountStart
        val outInfo = MediaCodec.BufferInfo()
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (trackIndex == -1) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    onMuxerStarted(true)
                }
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && trackIndex >= 0) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    val deterministicPtsUs = (emitted * 1_000_000L) / fps.coerceAtLeast(1)
                    outInfo.set(bufferInfo.offset, bufferInfo.size, deterministicPtsUs, bufferInfo.flags)
                    muxer.writeSampleData(trackIndex, encodedData, outInfo)
                    emitted++
                }
                encoder.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
        return trackIndex to emitted
    }

    /**
     * Copies H.264 video track from [videoOnlyFile] and transcodes/trims/fades the audio track
     * to AAC inside [outFile].
     */
    private fun muxVideoWithAudio(
        context: Context,
        videoOnlyFile: File,
        outFile: File,
        audioUri: Uri,
        trimStartMs: Long,
        durationMs: Long,
        volume: Float,
        fadeInMs: Long,
        fadeOutMs: Long,
        bitrateKbps: Int
    ): Boolean {
        val vExtractor = MediaExtractor()
        val aExtractor = MediaExtractor()
        vExtractor.setDataSource(videoOnlyFile.absolutePath)
        aExtractor.setDataSource(context, audioUri, null)

        var vTrack = -1
        for (i in 0 until vExtractor.trackCount) {
            if (vExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                vTrack = i
                break
            }
        }
        var aTrack = -1
        for (i in 0 until aExtractor.trackCount) {
            if (aExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                aTrack = i
                break
            }
        }
        if (vTrack == -1 || aTrack == -1) {
            vExtractor.release()
            aExtractor.release()
            return false
        }

        vExtractor.selectTrack(vTrack)
        aExtractor.selectTrack(aTrack)
        val vFormat = vExtractor.getTrackFormat(vTrack)
        val aInFormat = aExtractor.getTrackFormat(aTrack)
        val aMime = aInFormat.getString(MediaFormat.KEY_MIME) ?: return false

        // Fast direct AAC copy if already AAC/MP4 and default gain/fades
        if (aMime == MediaFormat.MIMETYPE_AUDIO_AAC && volume > 0.95f && fadeInMs == 0L && fadeOutMs == 0L) {
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val mVTrack = muxer.addTrack(vFormat)
            val mATrack = muxer.addTrack(aInFormat)
            muxer.start()

            val buf = ByteBuffer.allocate(512 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                val sz = vExtractor.readSampleData(buf, 0)
                if (sz < 0) break
                info.set(0, sz, vExtractor.sampleTime, vExtractor.sampleFlags)
                muxer.writeSampleData(mVTrack, buf, info)
                vExtractor.advance()
            }

            val trimStartUs = trimStartMs * 1000L
            val endUs = trimStartUs + durationMs * 1000L
            aExtractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (true) {
                val sz = aExtractor.readSampleData(buf, 0)
                if (sz < 0) break
                val pts = aExtractor.sampleTime
                if (pts > endUs) break
                if (pts >= trimStartUs) {
                    info.set(0, sz, pts - trimStartUs, aExtractor.sampleFlags)
                    muxer.writeSampleData(mATrack, buf, info)
                }
                aExtractor.advance()
            }
            muxer.stop()
            muxer.release()
            vExtractor.release()
            aExtractor.release()
            return true
        }

        // General path: decode any audio format (MP3/WAV/OGG/AAC) -> apply volume/fade -> encode AAC -> mux
        val sampleRate = aInFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = aInFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
        val aOutFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
        }

        val decoder = MediaCodec.createDecoderByType(aMime)
        decoder.configure(aInFormat, null, null, 0)
        decoder.start()

        val aEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        aEncoder.configure(aOutFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        aEncoder.start()

        val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val mVTrack = muxer.addTrack(vFormat)
        var mATrack = -1
        var muxerStarted = false

        val trimStartUs = trimStartMs * 1000L
        val maxDurUs = durationMs * 1000L
        aExtractor.seekTo(trimStartUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        val decInfo = MediaCodec.BufferInfo()
        val encInfo = MediaCodec.BufferInfo()
        var decInDone = false
        var decOutDone = false
        var encOutDone = false
        val pendingAacBuffers = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()

        while (!encOutDone) {
            if (!decInDone) {
                val inIdx = decoder.dequeueInputBuffer(5000)
                if (inIdx >= 0) {
                    val buf = decoder.getInputBuffer(inIdx)!!
                    val sz = aExtractor.readSampleData(buf, 0)
                    val pts = aExtractor.sampleTime
                    if (sz < 0 || pts > trimStartUs + maxDurUs + 200_000L) {
                        decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        decInDone = true
                    } else {
                        decoder.queueInputBuffer(inIdx, 0, sz, pts, 0)
                        aExtractor.advance()
                    }
                }
            }

            if (!decOutDone) {
                val outIdx = decoder.dequeueOutputBuffer(decInfo, 5000)
                if (outIdx >= 0) {
                    val pcmBuf = decoder.getOutputBuffer(outIdx)
                    val relPtsUs = decInfo.presentationTimeUs - trimStartUs
                    if (pcmBuf != null && decInfo.size > 0 && relPtsUs in 0L..maxDurUs) {
                        pcmBuf.position(decInfo.offset)
                        pcmBuf.limit(decInfo.offset + decInfo.size)
                        val bytes = ByteArray(decInfo.size)
                        pcmBuf.get(bytes)

                        // Apply volume + fade-in/fade-out
                        val tMs = relPtsUs / 1000L
                        val fadeInGain = if (fadeInMs > 0L && tMs < fadeInMs) tMs.toFloat() / fadeInMs else 1f
                        val fadeOutGain = if (fadeOutMs > 0L && tMs > durationMs - fadeOutMs) ((durationMs - tMs).toFloat() / fadeOutMs).coerceIn(0f, 1f) else 1f
                        val gain = (volume * fadeInGain * fadeOutGain).coerceIn(0f, 1.5f)
                        if (gain != 1f) {
                            val sb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            for (i in 0 until sb.remaining()) {
                                val v = (sb.get(i) * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                                sb.put(i, v.toShort())
                            }
                        }

                        val eInIdx = aEncoder.dequeueInputBuffer(10_000)
                        if (eInIdx >= 0) {
                            val eBuf = aEncoder.getInputBuffer(eInIdx)!!
                            eBuf.clear()
                            val putLen = min(eBuf.remaining(), bytes.size)
                            eBuf.put(bytes, 0, putLen)
                            aEncoder.queueInputBuffer(eInIdx, 0, putLen, relPtsUs, 0)
                        }
                    }
                    val isEos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    decoder.releaseOutputBuffer(outIdx, false)
                    if (isEos) {
                        decOutDone = true
                        val eInIdx = aEncoder.dequeueInputBuffer(10_000)
                        if (eInIdx >= 0) {
                            aEncoder.queueInputBuffer(eInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        }
                    }
                }
            }

            while (true) {
                val eOutIdx = aEncoder.dequeueOutputBuffer(encInfo, 2000)
                if (eOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (mATrack == -1) {
                        mATrack = muxer.addTrack(aEncoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (eOutIdx >= 0) {
                    val outBuf = aEncoder.getOutputBuffer(eOutIdx)
                    if (outBuf != null && (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && encInfo.size > 0) {
                        outBuf.position(encInfo.offset)
                        outBuf.limit(encInfo.offset + encInfo.size)
                        val arr = ByteArray(encInfo.size)
                        outBuf.get(arr)
                        val copyInfo = MediaCodec.BufferInfo().apply {
                            set(0, arr.size, encInfo.presentationTimeUs, encInfo.flags)
                        }
                        if (muxerStarted) {
                            muxer.writeSampleData(mATrack, ByteBuffer.wrap(arr), copyInfo)
                        } else {
                            pendingAacBuffers.add(arr to copyInfo)
                        }
                    }
                    val isEos = (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    aEncoder.releaseOutputBuffer(eOutIdx, false)
                    if (isEos) {
                        encOutDone = true
                        break
                    }
                } else {
                    break
                }
            }
        }

        if (!muxerStarted && mATrack >= 0) {
            muxer.start()
            muxerStarted = true
        }
        if (muxerStarted) {
            pendingAacBuffers.forEach { (arr, info) ->
                muxer.writeSampleData(mATrack, ByteBuffer.wrap(arr), info)
            }
            val vBuf = ByteBuffer.allocate(512 * 1024)
            val vInfo = MediaCodec.BufferInfo()
            while (true) {
                val sz = vExtractor.readSampleData(vBuf, 0)
                if (sz < 0) break
                vInfo.set(0, sz, vExtractor.sampleTime, vExtractor.sampleFlags)
                muxer.writeSampleData(mVTrack, vBuf, vInfo)
                vExtractor.advance()
            }
            muxer.stop()
        }

        muxer.release()
        decoder.stop()
        decoder.release()
        aEncoder.stop()
        aEncoder.release()
        vExtractor.release()
        aExtractor.release()
        return muxerStarted
    }

    private fun saveMp4ToGallery(context: Context, sourceFile: File, displayName: String): Uri? {
        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/CubeEditor")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }.getOrNull()
    }
}
