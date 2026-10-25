package dev.localvispr.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ==============================================================================
// 16 kHz mono PCM microphone capture with live speech-level metering
// ==============================================================================
//
// Matches the capture configuration and dB/SNR level metering of the reference
// app (`VoiceRecorder.meterValue`): `audioLevel` stays at 0.0 during silence and
// rises towards 1.0 only when speech energy exceeds the noise floor.

class VoiceRecorder {

    private val recording = AtomicBoolean(false)
    private var worker: Thread? = null
    private val pcmBytes = ByteArrayOutputStream()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    val isRecording: Boolean
        get() = recording.get()

    // Permission is checked by the caller before starting the foreground service.
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!recording.compareAndSet(false, true)) return false
        synchronized(pcmBytes) { pcmBytes.reset() }
        _audioLevel.value = 0f

        val minBuf =
            AudioRecord.getMinBufferSize(
                PcmSamples.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBuf <= 0) {
            recording.set(false)
            return false
        }

        val bufferSize = maxOf(minBuf * 2, PcmSamples.SAMPLE_RATE_HZ * 2)
        val audioRecord =
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                PcmSamples.SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            recording.set(false)
            return false
        }

        worker =
            thread(name = "localvispr-mic") {
                // 512 samples = 32 ms per read -> ~31 level updates per second.
                val chunk = ShortArray(512)
                val byteChunk = ByteBuffer.allocate(chunk.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                try {
                    audioRecord.startRecording()
                    while (recording.get()) {
                        val read = audioRecord.read(chunk, 0, chunk.size)
                        if (read > 0) {
                            _audioLevel.value = PcmSamples.speechLevel(chunk, read)
                            byteChunk.clear()
                            for (i in 0 until read) byteChunk.putShort(chunk[i])
                            synchronized(pcmBytes) {
                                pcmBytes.write(byteChunk.array(), 0, read * 2)
                            }
                        }
                    }
                } finally {
                    _audioLevel.value = 0f
                    runCatching { audioRecord.stop() }
                    audioRecord.release()
                }
            }
        return true
    }

    fun snapshotSamples(): FloatArray {
        val raw = synchronized(pcmBytes) { pcmBytes.toByteArray() }
        val shortCount = raw.size / 2
        val shorts = ShortArray(shortCount)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return PcmSamples.shortsToFloats(shorts)
    }

    fun stopAndTakeSamples(): FloatArray {
        if (!recording.compareAndSet(true, false)) return FloatArray(0)
        worker?.join(1_500)
        worker = null
        _audioLevel.value = 0f

        val raw = synchronized(pcmBytes) { pcmBytes.toByteArray() }
        val shortCount = raw.size / 2
        val shorts = ShortArray(shortCount)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return PcmSamples.shortsToFloats(shorts)
    }

    fun cancel() {
        if (!recording.compareAndSet(true, false)) return
        worker?.join(1_500)
        worker = null
        _audioLevel.value = 0f
        synchronized(pcmBytes) { pcmBytes.reset() }
    }
}
