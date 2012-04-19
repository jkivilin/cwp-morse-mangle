package fi_81.cwp_morse_mangle;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/* Based on example from http://stackoverflow.com/questions/2413426/playing-an-arbitrary-tone-with-android */
public class SinAudioLoop {
	private byte[] soundBuffer;
	private AudioTrack audioTrack;

	public SinAudioLoop() {
		final int sampleRate = 8000;
		final double freqOfTone = 400;
		final int loopLengthMs = (int)(sampleRate / freqOfTone);
		final double[] sample = new double[loopLengthMs * sampleRate / 1000];
		soundBuffer = new byte[2 * sample.length];

		/* Generate audio buffer */
		for (int i = 0; i < sample.length; i++)
			sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / freqOfTone));

		/* Convert to 16bit pcm sound array */
		for (int i = 0; i < sample.length; i++) {
			short val = (short) (sample[i] * Short.MAX_VALUE);

			soundBuffer[i * 2 + 0] = (byte) (val & 0xff);
			soundBuffer[i * 2 + 1] = (byte) ((val >> 8) & 0xff);
		}

		/* Setup audio track for looped sound-effect */
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
				AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT, sample.length,
				AudioTrack.MODE_STATIC);

		audioTrack.write(soundBuffer, 0, soundBuffer.length);
		audioTrack.setLoopPoints(0, soundBuffer.length / 4, -1);
	}

	public void play() {
		audioTrack.play();
	}

	public void stop() {
		audioTrack.pause();
	}

	public void release() {
		audioTrack.release();
	}
}
