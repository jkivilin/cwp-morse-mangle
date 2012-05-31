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

package fi_81.cwp_morse_mangle.cwp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;

import fi_81.cwp_morse_mangle.cwp.CWInputQueue;
import fi_81.cwp_morse_mangle.morse.BitString;
import fi_81.cwp_morse_mangle.morse.MorseCodec;

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

	private final int MAX_BUFFER_PAST = 100000; /* 100 sec */

	private long currFreq = 1;
	private ByteBuffer inBuf;
	private CWInputQueue morseQueue;
	private ArrayDeque<CWStateChange> bufferQueue;
	private final CWaveQueueToMorseCode morseDecoder = new CWaveQueueToMorseCode();
	private long lastReceivedWaveTime;

	private int lastStateUpValue;
	private long lastReceivedStateTime;
	private int maxBufferLength;
	private int bufferLength;
	private long connStartTime;

	public CWInput(CWInputQueue queue, ByteBuffer bb) {
		maxBufferLength = 0;
		bufferLength = 0;
		lastStateUpValue = 0;
		lastReceivedStateTime = 0;

		lastReceivedWaveTime = 0;
		morseQueue = queue;
		bufferQueue = new ArrayDeque<CWStateChange>();

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

	public CWInput(int maxBufferLen, long connectionStartTime) {
		this(new CWInputQueue(), null);

		connStartTime = connectionStartTime;
		maxBufferLength = maxBufferLen;
	}

	public ByteBuffer getInBuffer() {
		return inBuf;
	}

	public void processInput(final CWInputNotification notify) {
		inBuf.flip();

		while (inBuf.remaining() > 0) {
			boolean out = false;

			switch (morseQueue.getCurrentState()) {
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
			 * Process new buffered state changes before morse decoding, as
			 * morse decoding induces more latency and jitter.
			 */
			processBufferedStateChanges(notify);

			/*
			 * After receiving data, decode buffered wave-form to morse code.
			 * Decode after each received message, as this enforces same
			 * behavior in test-cases as in real world.
			 */
			BitString morseBits;
			do {
				morseBits = morseDecoder.tryDecode(morseQueue, false);
				if (morseBits != null)
					notify.morseMessage(morseBits);
			} while (morseBits != null);
		}

		inBuf.compact();

		/*
		 * Process buffered state changes (this is here in case there was no new
		 * input, to processing delayed state changes)
		 */
		processBufferedStateChanges(notify);

		/*
		 * Let morseDecoder to flush too old stale morse bits
		 */
		boolean forceFlush = false;

		if (morseQueue.getQueue().size() > MorseCodec.endSequence.length())
			forceFlush = true;
		else if (timeToNextWork() == 0)
			forceFlush = true;

		flushStaleMorseBits(notify, forceFlush);
		if (forceFlush)
			lastReceivedWaveTime = 0;
	}

	private void processInputDown(final CWInputNotification notify) {
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
			long currTime = System.currentTimeMillis();

			morseQueue.pushStateUp(value);

			/* Latency management for visualizing received state changes */
			if (maxBufferLength > 0) {
				adjustBufferLength(value, currTime);

				bufferQueue.add(new CWStateChange(
						CWStateChange.TYPE_DOWN_TO_UP, value, bufferLength
								+ value));

				lastStateUpValue = value;
			} else {
				/* No buffering if max buffer length set to zero */
				notify.stateChange(CWave.TYPE_UP, value);
			}

			lastReceivedStateTime = currTime;
		}
	}

	private void processInputUp(final CWInputNotification notify) {
		long currTime = System.currentTimeMillis();
		int value = inBuf.getShort();

		/*
		 * 16bit unsigned integer value get sign-extended, so mask out upper
		 * bits
		 */
		value = value & 0xffff;

		/* At up state, so this must be state-change:down */
		morseQueue.pushStateDown(value);

		/* Latency management for visualizing received state changes */
		if (maxBufferLength > 0) {
			adjustBufferLength(lastStateUpValue + value, currTime);

			bufferQueue.add(new CWStateChange(CWStateChange.TYPE_UP_TO_DOWN,
					value, bufferLength + lastStateUpValue + value));
		} else {
			/* No buffering if max buffer length set to zero */
			notify.stateChange(CWave.TYPE_DOWN, value);
		}

		lastReceivedWaveTime = currTime;
		lastReceivedStateTime = currTime;
	}

	private void processBufferedStateChanges(final CWInputNotification notify) {
		/*
		 * Process buffered data in-order, so that state changes in past will be
		 * processed as 0ms length waves.
		 */
		while (timeToNextQueueWork() == 0) {
			CWStateChange state = bufferQueue.remove();

			if (state.type == CWStateChange.TYPE_DOWN_TO_UP)
				notify.stateChange(CWave.TYPE_UP, state.value);
			else
				notify.stateChange(CWave.TYPE_DOWN, state.value);
		}
	}

	private void adjustBufferLength(int timeStamp, long currTime) {
		/* Adjust buffer length depending on current network latency */
		long currConnTime = currTime - connStartTime;
		int latency = (int) (currConnTime - timeStamp);
		int timeSinceLastState = (int) (currTime - lastReceivedStateTime);

		/*
		 * Adjust buffer length as specified in CWP Spec v1.1,
		 * "6 Latency Management"
		 */
		if (latency <= bufferLength) {
			bufferLength -= (timeSinceLastState * (bufferLength + latency))
					/ MAX_BUFFER_PAST;
		} else {
			bufferLength = latency;
		}

		if (bufferLength < 0)
			bufferLength = 0;
		else if (bufferLength > maxBufferLength)
			bufferLength = maxBufferLength;
	}

	public void flushStaleMorseBits(CWInputNotification notify, boolean force) {
		BitString morseBits;

		/* Force decoding */
		if (force) {
			do {
				morseBits = morseDecoder.tryDecode(morseQueue, true);
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

	public boolean hadPendingBits() {
		return morseDecoder.hadPendingBits();
	}

	/* Time to next latency management delayed work */
	private long timeToNextQueueWork() {
		if (maxBufferLength <= 0 || bufferQueue.isEmpty())
			return Long.MAX_VALUE;

		long currentTime = System.currentTimeMillis();
		long timeSinceConnCreation = currentTime - connStartTime;
		long timeToNext = bufferQueue.peek().getOutTime()
				- timeSinceConnCreation;

		if (timeToNext < 0)
			return 0;

		return timeToNext;
	}

	/* Time to next morse decoder delayed work */
	private long timeToNextMorseWork() {
		/* If no received waves, don't wait */
		if (lastReceivedWaveTime <= 0)
			return Long.MAX_VALUE;

		/* If less than zero, no data to be flushed */
		long flushTimeout = morseDecoder.getFlushTimeout();
		if (flushTimeout < 0)
			return Long.MAX_VALUE;

		/* Get time to next forced flush of morse-decoder */
		long timeToFlush = (lastReceivedWaveTime + flushTimeout)
				- System.currentTimeMillis();

		/* If timeout has passed return zero */
		if (timeToFlush <= 0)
			return 0;

		return timeToFlush;
	}

	public long timeToNextWork() {
		return Math.min(timeToNextQueueWork(), timeToNextMorseWork());
	}
}
