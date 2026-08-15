/*
 * Project:  NextGIS Mobile
 * Purpose:  Mobile GIS for Android.
 * Copyright (c) 2026 GeonicalSystem
 */
package com.nextgis.mobile.stakeout

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Short, damped wooden taps for all-day field use. */
internal class StakeoutAudioCue {
    private val ping = buildTrack(woodenTap(320.0, 68, 0.82))
    private val reached = buildTrack(
        woodenTap(280.0, 72, 0.74) + silence(85) + woodenTap(360.0, 76, 0.86)
    )

    @Synchronized
    fun playPing() {
        stopTrack(reached)
        replay(ping)
    }

    @Synchronized
    fun playReached() {
        stopTrack(ping)
        replay(reached)
    }

    @Synchronized
    fun stop() {
        stopTrack(ping)
        stopTrack(reached)
    }

    @Synchronized
    fun release() {
        stop()
        ping.release()
        reached.release()
    }

    private fun replay(track: AudioTrack) {
        stopTrack(track)
        track.setPlaybackHeadPosition(0)
        track.play()
    }

    private fun stopTrack(track: AudioTrack) {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
        }
        if (track.playState != AudioTrack.PLAYSTATE_STOPPED) {
            track.stop()
        }
    }

    private fun buildTrack(samples: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track.write(samples, 0, samples.size)
        track.setVolume(1.0f)
        return track
    }

    private fun woodenTap(
        startFrequency: Double,
        durationMillis: Int,
        amplitude: Double
    ): ShortArray {
        val sampleCount = SAMPLE_RATE * durationMillis / 1_000
        val attackSamples = (SAMPLE_RATE * 2 / 1_000).coerceAtLeast(1)
        return ShortArray(sampleCount) { index ->
            val seconds = index / SAMPLE_RATE.toDouble()
            val progress = index / sampleCount.toDouble()
            val attack = (index / attackSamples.toDouble()).coerceAtMost(1.0)
            val decay = exp(-7.5 * progress)
            val sweptPhase = startFrequency * seconds * (1.0 - 0.13 * progress)
            val body = sin(2.0 * PI * sweptPhase)
            val woodResonance = 0.32 * sin(2.0 * PI * sweptPhase * 1.47)
            val value = (body + woodResonance) / 1.32
            val boosted = (amplitude * MASTER_GAIN * attack * decay * value)
                .coerceIn(-1.0, 1.0)
            (Short.MAX_VALUE * boosted).toInt().toShort()
        }
    }

    private fun silence(durationMillis: Int): ShortArray =
        ShortArray(SAMPLE_RATE * durationMillis / 1_000)

    private operator fun ShortArray.plus(other: ShortArray): ShortArray {
        val combined = ShortArray(size + other.size)
        copyInto(combined, 0)
        other.copyInto(combined, size)
        return combined
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val MASTER_GAIN = 3.0
    }
}
