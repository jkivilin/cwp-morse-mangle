package fi_81.cwp_morse_mangle.cwp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import fi_81.cwp_morse_mangle.cwp.CWInputQueue;
import fi_81.cwp_morse_mangle.morse.BitString;

public class CWInput {
	/* Callbacks from input, frequency change, state changes and morse messages */
	public interface CWInputNotification {
		public abstract void frequencyChange(long frequency);

		public abstract void stateChange(byte newState, int value);

		public abstract void morseMessage(BitString morseBits);
	}

	public static class NotificationNone implements CWInputNotification {
		public void frequencyChange(long newFreq) {
		}

		public void stateChange(byte newState, int value) {
		}

		public void morseMessage(BitString morseBits) {
		}
	}

	private long currFreq = 1;
	private ByteBuffer inBuf;
	private CWInputQueue queue;
	private final CWaveQueueToMorseCode morseDecoder = new CWaveQueueToMorseCode();

	public CWInput(CWInputQueue queue, ByteBuffer bb) {
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

	public void flushStaleMorseBits(CWInputNotification notify, boolean force) {
		BitString morseBits;

		/* Force decoding */
		if (force) {
			do {
				morseBits = morseDecoder.tryDecode(queue, true);
				if (morseBits != null) {
					notify.morseMessage(morseBits);
					continue;
				}
			} while (morseBits != null);
		}

		/* Flush morse buffer, either by force or by timeout */
		morseBits = morseDecoder.flushStalled(force);
		if (morseBits != null)
			notify.morseMessage(morseBits);
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

			/*
			 * After receiving data, decode buffered wave-form to morse code.
			 * Decode after each received message, as this enforces same
			 * behavior in test-cases as in real world.
			 */
			BitString morseBits;
			do {
				morseBits = morseDecoder.tryDecode(queue, false);
				if (morseBits != null)
					notify.morseMessage(morseBits);
			} while (morseBits != null);
		}

		/*
		 * Let morseDecoder to flush too old stale morse bits
		 */
		flushStaleMorseBits(notify, false);

		inBuf.compact();
	}

	private void processInputDown(CWInputNotification notify) {
		int value = inBuf.getInt();

		/* At down state, so this is either state-change:up or freq-change */

		/* Negative value means frequency-change. */
		if (value < 0) {
			long newFreq = -(long) value;
			/*
			 * Frequency value is negated, so de-negate before passing
			 */
			notify.frequencyChange(newFreq);

			if (newFreq != currFreq) {
				/* Force flush morse buffer since channel changed */
				flushStaleMorseBits(notify, true);

				currFreq = newFreq;
			}
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

	public boolean hadPendingBits() {
		return morseDecoder.hadPendingBits();
	}
}
