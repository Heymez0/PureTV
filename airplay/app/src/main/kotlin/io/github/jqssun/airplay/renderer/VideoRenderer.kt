package io.github.jqssun.airplay.renderer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import io.github.jqssun.airplay.renderer.DecoderSelector.Companion.videoCaps

class VideoRenderer(ctx: Context) {

    private val lock = Object()
    private val pipeline = VideoPipeline()
    val selector = DecoderSelector(ctx)
    private var avcDecoder: MediaCodecInfo? = null
    private var hevcDecoder: MediaCodecInfo? = null
    private var maxFps = 0
    @Volatile private var codec: MediaCodec? = null
    private var displaySurface: Surface? = null
    // PureTV: where the decoder renders. The display surface itself when possible (no GL copy per frame; Amlogic and
    // similar TV chips then put the video on their video plane), otherwise the pipeline's surface, which repaints the
    // last frame on a returning display and keeps the decoder running while nothing is shown.
    @Volatile private var output: Surface? = null
    // a display that returned while the pipeline showed the last frame: the decoder moves to it with the next frame
    private var handover: Surface? = null
    // decoded frames go to the screen as soon as they are ready, not when the next input arrives
    private var drainer: Thread? = null
    private val outLock = Object()
    // the drainer hit a codec error: the next input resets the codec, as a feed error does
    @Volatile private var drainFailed = false
    private var currentH265 = false
    private var videoWidth = 0
    private var videoHeight = 0
    private var firstFrameQueued = false

    // stats
    @Volatile var fps = 0; private set
    @Volatile var bitrateBps = 0L; private set
    @Volatile var frameCount = 0L; private set
    @Volatile var codecName = ""; private set
    @Volatile var droppedFrames = 0L; private set
    @Volatile var framePacingJitterUs = 0L; private set

    var enforceSdr = true
    var keyAllowFrameDrop = true
    var scheduledOutputBufferRelease = true
    var benchmarkLog = false
    var benchmarkLogCallback: ((String) -> Unit)? = null
    private var _framesThisSec = 0
    private var _bytesThisSec = 0L
    private var _lastStatReset = 0L
    private val _frameIntervalsNs = LongArray(120)
    private var _frameIntervalIdx = 0
    private var _frameIntervalCount = 0
    private var _lastOutputFrameNs = 0L
    // anchors that map decoder PTS (us) to System.nanoTime() for scheduled rendering
    private var _ptsBaseUs = Long.MIN_VALUE
    private var _wallBaseNs = 0L

    fun setResolution(w: Int, h: Int) = synchronized(lock) {
        if (w == videoWidth && h == videoHeight) return@synchronized
        videoWidth = w
        videoHeight = h
        pipeline.setVideoSize(w, h)
        stopCodec()
    }

    // doesn't restart codec: the decoder is re-pointed (setOutputSurface) or keeps rendering into the pipeline
    fun setSurface(surface: Surface) = synchronized(lock) {
        displaySurface = surface
        val c = codec
        if (!_direct()) {
            pipeline.setDisplaySurface(surface)
            return@synchronized
        }
        if (c == null || output === surface) return@synchronized
        if (output === pipeline.inputSurface && pipeline.hasFrame) {
            // show the last frame now; the decoder takes over this surface with the next one
            synchronized(outLock) { handover = surface }
            pipeline.setDisplaySurface(surface)
        } else {
            _redirect(c, surface)
        }
    }

    fun clearSurface(surface: Surface) = synchronized(lock) {
        if (displaySurface !== surface) return@synchronized
        displaySurface = null
        synchronized(outLock) { if (handover === surface) handover = null }
        // the surface is going away once this returns: neither GL nor the decoder may still use it
        pipeline.setDisplaySurface(null, wait = true)
        val c = codec ?: return@synchronized
        if (output !== surface) return@synchronized
        // the last frame was drawn straight to the display: the pipeline's older one must not reappear later
        pipeline.forgetFrame()
        _pipelineSurface()?.let { _redirect(c, it) } ?: stopCodec()
    }

    private fun _direct() = !directFailed && android.os.Build.VERSION.SDK_INT >= 23

    private fun _pipelineSurface(): Surface? {
        pipeline.start()
        pipeline.setVideoSize(videoWidth, videoHeight)
        return pipeline.inputSurface
    }

    private fun _redirect(c: MediaCodec, surface: Surface) {
        val moved = synchronized(outLock) {
            try {
                c.setOutputSurface(surface)
                output = surface
                true
            } catch (e: Exception) {
                // never again in this process: the pipeline path works everywhere; video resumes at the next keyframe
                Log.w(TAG, "setOutputSurface failed; using the GL pipeline", e)
                directFailed = true
                handover = null
                false
            }
        }
        // outside outLock: stopCodec waits for the drainer, which takes outLock
        if (!moved) {
            stopCodec()
            displaySurface?.let { pipeline.setDisplaySurface(it) }
        }
    }

    fun selectDecoders(w: Int, h: Int, fps: Int, h265: Boolean?): Boolean = synchronized(lock) {
        avcDecoder = selector.avc()
        hevcDecoder = if (h265 != false) selector.hevc(avcDecoder, w, h, fps, force = h265 == true) else null
        maxFps = fps
        Log.i(TAG, "decoders: avc=${avcDecoder?.name} hevc=${hevcDecoder?.name}")
        hevcDecoder != null
    }

    // unknown limits default to 1080p
    fun maxResolution(): Pair<Int, Int> =
        listOfNotNull(avcDecoder?.let { it to DecoderSelector.AVC }, hevcDecoder?.let { it to DecoderSelector.HEVC })
            .map { (info, mime) ->
                runCatching { info.videoCaps(mime).let { it.supportedWidths.upper to it.supportedHeights.upper } }
                    .getOrDefault(1920 to 1080)
            }
            .reduceOrNull { (w1, h1), (w2, h2) -> minOf(w1, w2) to minOf(h1, h2) } ?: (1920 to 1080)

    // codec per mirror session; pipeline persists across sessions
    fun startSession() = synchronized(lock) { _resetStats() }

    fun stopSession() = synchronized(lock) { stopCodec() }

    private fun _resetStats() {
        fps = 0; bitrateBps = 0; frameCount = 0; codecName = ""
        droppedFrames = 0; framePacingJitterUs = 0
        _framesThisSec = 0; _bytesThisSec = 0
    }

    private fun _updateStats(size: Int) {
        val now = System.currentTimeMillis()
        if (now - _lastStatReset >= 1000) {
            fps = _framesThisSec
            bitrateBps = _bytesThisSec * 8
            framePacingJitterUs = _computeFramePacingJitterUs()
            _framesThisSec = 0
            _bytesThisSec = 0
            _lastStatReset = now
            if (benchmarkLog) _emitBenchmarkLine()
        }
        _framesThisSec++
        _bytesThisSec += size
        frameCount++
    }

    private fun _emitBenchmarkLine() {
        val msg = "fps=$fps bitrate=${bitrateBps / 1000}kbps " +
            "jitter=${framePacingJitterUs}us frames=$frameCount " +
            "dropped=$droppedFrames codec=$codecName " +
            "res=${videoWidth}x${videoHeight}"
        Log.i(BENCH_TAG, msg)
        benchmarkLogCallback?.invoke(msg)
    }

    fun feedFrame(data: ByteArray, ntpTimeNs: Long, isH265: Boolean) {
        synchronized(lock) {
            _updateStats(data.size)
            if (videoWidth == 0 || videoHeight == 0) return
            if (drainFailed) {
                drainFailed = false
                stopCodec()
            }

            if (codec == null || isH265 != currentH265) {
                // a stale reference frame decodes to corruption, so wait for a keyframe to (re)start
                if (!_isKeyframe(data, isH265)) {
                    if (codec != null) stopCodec()
                    return
                }
                stopCodec()
            }

            try {
                if (codec == null) startCodec(isH265)
                _feedToCodec(data, ntpTimeNs)
            } catch (e: Exception) {
                Log.w(TAG, "Codec error, resetting", e)
                stopCodec()
            }
        }
    }

    private fun _feedToCodec(data: ByteArray, ntpTimeNs: Long) {
        val c = codec ?: return
        // dropping a frame desyncs decoder until the next keyframe, but source would only send one on (re)connect
        val retries = if (firstFrameQueued) FEED_RETRIES else FIRST_FEED_RETRIES
        repeat(retries) {
            val idx = c.dequeueInputBuffer(FEED_WAIT_US)
            if (idx >= 0) {
                val buf = c.getInputBuffer(idx) ?: return
                buf.clear()
                buf.put(data)
                c.queueInputBuffer(idx, 0, data.size, ntpTimeNs / 1000, 0)
                firstFrameQueued = true
                return
            }
        }
        droppedFrames++
        Log.w(TAG, "Decoder input queue full; dropping frame. drops=$droppedFrames")
    }

    private fun _isKeyframe(data: ByteArray, isH265: Boolean): Boolean {
        if (data.size < 5) return false
        var i = 0
        while (i <= data.size - 5) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
                data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                val key = if (isH265) {
                    val type = (data[i + 4].toInt() shr 1) and 0x3F
                    type == 19 || type == 20 || type == 21 || type == 32 || type == 33
                } else {
                    val type = data[i + 4].toInt() and 0x1F
                    type == 5 || type == 7
                }
                if (key) return true
            }
            i++
        }
        return false
    }

    private fun startCodec(h265: Boolean) {
        val display = displaySurface?.takeIf { _direct() && it.isValid }
        if (display != null) pipeline.setDisplaySurface(null, wait = true)
        val s = display ?: _pipelineSurface() ?: return
        currentH265 = h265
        val mime = if (h265) DecoderSelector.HEVC else DecoderSelector.AVC
        val info = (if (h265) hevcDecoder else avcDecoder) ?: error("no decoder selected for $mime")

        firstFrameQueued = false
        try {
            _startWithLadder(info, mime, s, h265)
        } catch (e: Exception) {
            // strict hw decoders reject configs beyond their real limits
            val sw = selector.software(mime, videoWidth, videoHeight) ?: throw e
            Log.w(TAG, "Hardware decoder failed, trying software fallback", e)
            _startWithLadder(sw, mime, s, h265)
        }
        output = s
        val c = codec ?: return
        drainer = Thread({ _drain(c) }, "VideoDrain").also { it.start() }
        Log.i(TAG, "Video codec started: $mime ${videoWidth}x${videoHeight} ($codecName) " +
            if (s === display) "direct" else "via GL")
    }

    private fun _startWithLadder(info: MediaCodecInfo, mime: String, s: Surface, h265: Boolean) {
        var tryNum = 0
        while (true) {
            val format = _format(mime, info)
            val more = selector.lowLatencyOptions(format, info, mime, tryNum)
            try {
                _startDecoder(MediaCodec.createByCodecName(info.name), format, s, h265)
                return
            } catch (e: Exception) {
                if (!more) throw e
                Log.w(TAG, "configure try $tryNum failed: $format", e)
                tryNum++
            }
        }
    }

    private fun _format(mime: String, info: MediaCodecInfo) = MediaFormat.createVideoFormat(mime, videoWidth, videoHeight).apply {
        setInteger(MediaFormat.KEY_FRAME_RATE, maxFps)
        if (selector.adaptive(info, mime)) {
            setInteger(MediaFormat.KEY_MAX_WIDTH, videoWidth)
            setInteger(MediaFormat.KEY_MAX_HEIGHT, videoHeight)
        }
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxOf(videoWidth * videoHeight * 3 / 4, 1024 * 1024))
        if (enforceSdr) {
            setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            setInteger(MediaFormat.KEY_ALLOW_FRAME_DROP, if (keyAllowFrameDrop) 1 else 0)
        }
    }

    private fun _startDecoder(c: MediaCodec, format: MediaFormat, surface: Surface, h265: Boolean) {
        try {
            c.configure(format, surface, null, 0)
            c.start()
        } catch (e: Exception) {
            try { c.release() } catch (_: Exception) {}
            throw e
        }
        codec = c
        codecName = (if (h265) "H.265" else "H.264") + " (${c.name})"
    }

    private fun stopCodec() {
        val c = codec
        codec = null
        // the drainer never takes `lock`; it sees codec gone within one DRAIN_WAIT_US
        drainer?.let { if (it !== Thread.currentThread()) it.join() }
        drainer = null
        drainFailed = false
        synchronized(outLock) { handover = null }
        output = null
        _frameIntervalIdx = 0
        _frameIntervalCount = 0
        _lastOutputFrameNs = 0L
        _ptsBaseUs = Long.MIN_VALUE
        _wallBaseNs = 0L
        c?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {}
        }
    }

    private fun _drain(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (codec === c) {
            val idx = try { c.dequeueOutputBuffer(info, DRAIN_WAIT_US) } catch (e: IllegalStateException) {
                if (codec === c) { Log.w(TAG, "Codec error while draining", e); drainFailed = true }
                return
            }
            if (idx < 0) continue
            synchronized(outLock) {
                if (codec !== c) return
                handover?.let { _takeOver(c, it) }
                try { _release(c, idx, info) } catch (e: IllegalStateException) {
                    if (codec === c) drainFailed = true
                    return
                }
            }
        }
    }

    // the pipeline painted the last frame on the returning display; now the decoder draws there itself
    private fun _takeOver(c: MediaCodec, surface: Surface) {
        handover = null
        pipeline.setDisplaySurface(null, wait = true)
        try {
            c.setOutputSurface(surface)
            output = surface
        } catch (e: Exception) {
            Log.w(TAG, "setOutputSurface failed; staying on the GL pipeline", e)
            directFailed = true
            pipeline.setDisplaySurface(surface)
        }
    }

    private fun _release(c: MediaCodec, idx: Int, info: MediaCodec.BufferInfo) {
        _recordOutputFrameTime()
        if (!scheduledOutputBufferRelease) {
            c.releaseOutputBuffer(idx, true)
            return
        }
        // schedule frame at VSYNC matching its NTP presentation time
        val ptsUs = info.presentationTimeUs
        val now = System.nanoTime()
        var at = _wallBaseNs + (ptsUs - _ptsBaseUs) * 1000L
        // PureTV: re-anchor when clock drift or a stall puts the frame far off, instead of queueing ever later
        if (_ptsBaseUs == Long.MIN_VALUE || at > now + MAX_AHEAD_NS || at < now - MAX_BEHIND_NS) {
            _ptsBaseUs = ptsUs
            _wallBaseNs = now
            at = now
        }
        c.releaseOutputBuffer(idx, at)
    }

    fun release() = synchronized(lock) {
        stopCodec()
        pipeline.release()
        _resetStats()
    }

    private fun _recordOutputFrameTime() {
        val now = System.nanoTime()
        if (_lastOutputFrameNs > 0) {
            _frameIntervalsNs[_frameIntervalIdx % _frameIntervalsNs.size] = now - _lastOutputFrameNs
            _frameIntervalIdx++
            _frameIntervalCount++
        }
        _lastOutputFrameNs = now
    }

    private fun _computeFramePacingJitterUs(): Long {
        val count = _frameIntervalCount.coerceAtMost(_frameIntervalsNs.size)
        if (count < 2) return 0

        var sum = 0.0
        var sumSq = 0.0
        for (i in 0 until count) {
            val interval = _frameIntervalsNs[i].toDouble()
            sum += interval
            sumSq += interval * interval
        }
        val mean = sum / count
        val variance = (sumSq / count) - (mean * mean)
        return (kotlin.math.sqrt(variance.coerceAtLeast(0.0)) / 1000.0).toLong()
    }

    companion object {
        private const val TAG = "VideoRenderer"
        private const val BENCH_TAG = "BENCHMARK"
        private const val FEED_WAIT_US = 20_000L
        private const val FEED_RETRIES = 10
        private const val FIRST_FEED_RETRIES = 50
        private const val DRAIN_WAIT_US = 10_000L
        private const val MAX_AHEAD_NS = 100_000_000L
        private const val MAX_BEHIND_NS = 50_000_000L
        // PureTV: a decoder that rejected setOutputSurface keeps the GL pipeline for the rest of the process
        @Volatile private var directFailed = false
    }
}
