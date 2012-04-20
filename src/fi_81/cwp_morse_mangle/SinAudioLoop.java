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
