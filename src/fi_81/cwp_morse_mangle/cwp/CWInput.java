package fi_81.cwp_morse_mangle.cwp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import fi_81.cwp_morse_mangle.cwp.CWInputQueue;
import fi_81.cwp_morse_mangle.morse.BitString;

public class CWInput {
	/* Callbacks from input, frequency change, state changes and morse messages */
	public interface CWInputNotification {
		public abstract void frequencyChange(int newFreq);

		public abstract void stateChange(byte newState, int value);

		public abstract void morseMessage(BitString morseBits);
	}

	public class CWInputNotificationNone implements CWInputNotification {
		public void frequencyChange(int newFreq) {
		}

		public void stateChange(byte newState, int value) {
		}

		public void morseMessage(BitString morseBits) {
		}
	}

	private ByteBuffer inBuf;
	private CWInputQueue queue;
	private CWaveQueueToMorseCode morseDecoder;

	public CWInput(CWInputQueue queue, ByteBuffer bb) {
		this.morseDecoder = new CWaveQueueToMorseCode();
		this.queue = queue;

		if (bb == null) {
			/* Allocate IO buffer and set it to big-endian byteorder */
			inBuf = ByteBuffer.allocateDirect(128);
			inBuf.order(ByteOrder.BIG_ENDIAN);
		} else {
			inBuf = bb;
			inBuf.compact();
		}
	}

	public CWInput(ByteBuffer bb) {
		this(new CWInputQueue(), bb);
	}

	public CWInput() {
		this(new CWInputQueue(), null);
	}

	public ByteBuffer getInBuffer() {
		return inBuf;
	}

	public void processInput(CWInputNotification notify) {
		inBuf.flip();

		while (inBuf.remaining() > 0) {
			boolean out = false;

			switch (queue.getCurrentState()) {
			case CWave.TYPE_DOWN:
				/* In down state, expect 4 bytes integer input */
				if (inBuf.remaining() < 4) {
					out = true;
					break;
				}

				processInputDown(notify);
				break;
			case CWave.TYPE_UP:
				/* In up state, expect 2 bytes integer input */
				if (inBuf.remaining() < 2) {
					out = true;
					break;
				}

				processInputUp(notify);
				break;
			}

			if (out)
				break;
		}

		/*
		 * After receiving data, decode buffered wave-form to morse code.
		 */
		BitString morseBits;
		do {
			morseBits = morseDecoder.tryDecode(queue);
			if (morseBits != null)
				notify.morseMessage(morseBits);
		} while (morseBits != null);

		/*
		 * Let morseDecoder to flush too old stale morse bits
		 */
		morseBits = morseDecoder.flushStalled();
		if (morseBits != null)
			notify.morseMessage(morseBits);

		inBuf.compact();
	}

	private void processInputDown(CWInputNotification notify) {
		int value = inBuf.getInt();

		/* At down state, so this is either state-change:up or freq-change */

		/* Negative value means frequency-change. */
		if (value < 0) {
			/*
			 * Frequency value is negated, so de-negate before passing
			 */
			notify.frequencyChange(-value);
		} else {
			queue.pushStateUp(value);
			notify.stateChange(CWave.TYPE_UP, value);
		}
	}

	private void processInputUp(CWInputNotification notify) {
		int value = inBuf.getShort();

		/*
		 * 16bit unsigned integer value get sign-extended, so mask out upper
		 * bits
		 */
		value = value & 0xffff;

		/* At up state, so this must be state-change:down */
		queue.pushStateDown(value);
		notify.stateChange(CWave.TYPE_DOWN, value);
	}
}
