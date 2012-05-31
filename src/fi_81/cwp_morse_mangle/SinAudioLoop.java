/*
 * Copyright (C) 2012 Jussi Kivilinna <jussi.kivilinna@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package fi_81.cwp_morse_mangle;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/* Based on example from http://stackoverflow.com/questions/2413426/playing-an-arbitrary-tone-with-android */
public class SinAudioLoop {
	private static final int sampleRate = 8000;

	private final byte[] soundBuffer = initSoundBuffer();
	private AudioTrack audioTrack;
	private float audioMaxVolume;
	private float audioMinVolume;

	private byte[] initSoundBuffer() {
		final double freqOfTone = 400;
		final int loopLengthMs = 1000;
		final int sampleLen = loopLengthMs * sampleRate / 1000;
		final int maxSampleHeight = 0x7fff;
		final byte[] soundBuffer = new byte[2 * sampleLen];

		/* Generate audio buffer as 16 bit pcm sound array */
		for (int i = 0; i < sampleLen; i++) {
			double sample = Math.sin(2 * Math.PI * i
					/ (sampleRate / freqOfTone));
			int val = (int) Math.round(sample * maxSampleHeight);

			if (val < -maxSampleHeight)
				val = -maxSampleHeight;
			else if (val > maxSampleHeight)
				val = maxSampleHeight;

			soundBuffer[i * 2 + 0] = (byte) (val & 0xff);
			soundBuffer[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
		}

		return soundBuffer;
	}

	public SinAudioLoop() {
		/* Setup audio track for looped sound-effect */
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
				AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT, soundBuffer.length / 2,
				AudioTrack.MODE_STATIC);

		audioTrack.write(soundBuffer, 0, soundBuffer.length);
		audioTrack.setLoopPoints(0, soundBuffer.length / 4, -1);

		audioMinVolume = AudioTrack.getMinVolume();
		audioMaxVolume = AudioTrack.getMaxVolume();

		audioTrack.setStereoVolume(audioMinVolume, audioMinVolume);
		audioTrack.play();
	}

	public void play() {
		audioTrack.setStereoVolume(audioMaxVolume, audioMaxVolume);
	}

	public void stop() {
		audioTrack.setStereoVolume(audioMinVolume, audioMinVolume);
	}

	public void release() {
		audioTrack.setStereoVolume(audioMinVolume, audioMinVolume);
		audioTrack.stop();
		audioTrack.release();
	}
}
