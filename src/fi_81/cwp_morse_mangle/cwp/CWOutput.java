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

import fi_81.cwp_morse_mangle.morse.BitString;

public class CWOutput {
	/* Callbacks from output, frequency change, state changes */
	public interface CWOutputNotification {
		public abstract void frequencyChange(long newFreq);

		public abstract void stateChange(byte newState, int value);
	}

	public static class NotificationNone implements CWOutputNotification {
		public void frequencyChange(long newFreq) {
		}

		public void stateChange(byte newState, int value) {
		}
	}

	private final ArrayDeque<CWStateChange> queue = new ArrayDeque<CWStateChange>();
	private final CWStateChangeQueueFromMorseCode stateChangeBuilder = new CWStateChangeQueueFromMorseCode();
	private ByteBuffer outBuf;
	private long startTime;

	private boolean inManualUp;
	private long manualUpStartTime;
	private long delayedFreq = -1;

	public CWOutput(long connectionStartTime) {
		this(null, connectionStartTime);
	}

	public CWOutput(ByteBuffer bb, long connectionStartTime) {
		startTime = connectionStartTime;
		inManualUp = false;
		manualUpStartTime = 0;

		if (bb == null) {
			/* Allocate IO buffer and set it to big-endian byteorder */
			outBuf = ByteBuffer.allocateDirect(128);
			outBuf.order(ByteOrder.BIG_ENDIAN);
		} else {
			outBuf = bb;
		}

		outBuf.flip();
	}

	public ByteBuffer getOutputBuffer() {
		return outBuf;
	}

	public long timeToNextQueueWork() {
		if (queue.isEmpty())
			return Long.MAX_VALUE;

		long currentTime = System.currentTimeMillis();
		long timeSinceConnCreation = currentTime - startTime;
		long timeToNext = queue.peek().getOutTime() - timeSinceConnCreation;

		if (timeToNext < 0)
			return 0;

		return timeToNext;
	}

	public long timeToNextContinuousUpWaveWork() {
		if (!inManualUp)
			return Long.MAX_VALUE;

		/*
		 * Split up-waves to 55 second chunks (max duration of up-wave is ~65
		 * sec [(2^16-1) msec]).
		 */
		long nextReupTime = manualUpStartTime + (55 * 1000);
		long currentTime = System.currentTimeMillis();
		long timeToNext = nextReupTime - currentTime;

		if (timeToNext < 0)
			return 0;

		return timeToNext;
	}

	public long timeToNextWork() {
		long nextQueueWork = timeToNextQueueWork();
		long nextContiniousUpWaveWork = timeToNextContinuousUpWaveWork();

		return Math.min(nextQueueWork, nextContiniousUpWaveWork);
	}

	private boolean isTimeToSend() {
		return timeToNextQueueWork() == 0;
	}

	public boolean isBusy() {
		return !queue.isEmpty() && inManualUp;
	}

	public boolean isBusyDown() {
		return !queue.isEmpty() && !inManualUp;
	}

	public boolean sendMorseCode(BitString morseCode) {
		if (!queue.isEmpty() || inManualUp)
			return false;

		stateChangeBuilder.encode(queue, morseCode);

		/* adjust timestamps based on time since connection was created */
		long currentTime = System.currentTimeMillis();
		long timeSinceConnCreation = currentTime - startTime;
		CWStateChange last = null;

		for (CWStateChange stateChange : queue) {
			stateChange.addTimestamp(timeSinceConnCreation);
			last = stateChange;
		}

		/* sanity check */
		if (last != null) {
			assert (last.getType() == CWStateChange.TYPE_UP_TO_DOWN);
		}

		return true;
	}

	public boolean sendFrequenceChange(long newFreq) {
		if (newFreq < 1 || -newFreq < CWFrequencyChange.MAX_FREQ_NEG)
			return true;

		/* Current send state is at 'down' always except when inManualUp. */
		if (inManualUp) {
			delayedFreq = newFreq;

			return true;
		}

		queue.add(new CWFrequencyChange(newFreq));
		delayedFreq = -1;

		return true;
	}

	private boolean sendStateChange(byte stateChange) {
		/*
		 * if inManualUp, then queue might have the up-value that we add there
		 * moment ago
		 */
		if (!queue.isEmpty() && !inManualUp)
			return false;

		/* already in manual up */
		if (inManualUp && stateChange == CWStateChange.TYPE_DOWN_TO_UP)
			return true;

		/* already in down state */
		if (!inManualUp && stateChange == CWStateChange.TYPE_UP_TO_DOWN)
			return true;

		/* from manual up to down, check elapsed time and add message to queue */
		if (inManualUp && stateChange == CWStateChange.TYPE_UP_TO_DOWN) {
			long currentTime = System.currentTimeMillis();
			long timestamp = currentTime - startTime;
			int upStateDuration = (int) (currentTime - manualUpStartTime);

			queue.add(new CWStateChange(stateChange, upStateDuration, timestamp));

			inManualUp = false;
			sendFrequenceChange(delayedFreq);

			return true;
		}

		/*
		 * from down to manual up, generate down-to-up state change with
		 * timestamp
		 */
		if (!inManualUp && stateChange == CWStateChange.TYPE_DOWN_TO_UP) {
			manualUpStartTime = System.currentTimeMillis();
			long timestamp = manualUpStartTime - startTime;

			queue.add(new CWStateChange(stateChange, (int) timestamp, timestamp));

			inManualUp = true;
			return true;
		}

		return true;
	}

	private void renewUpState() {
		if (!inManualUp)
			return;

		long currentTime = System.currentTimeMillis();
		long timestamp = currentTime - startTime;
		int upStateDuration = (int) (currentTime - manualUpStartTime);

		/* Send state change up-to-down ... */
		queue.add(new CWStateChange(CWStateChange.TYPE_UP_TO_DOWN,
				upStateDuration, timestamp));

		/* ... immediately followed by state change down-to-up. */
		queue.add(new CWStateChange(CWStateChange.TYPE_DOWN_TO_UP,
				(int) timestamp, timestamp));

		manualUpStartTime = currentTime;
	}

	public boolean sendDown() {
		return sendStateChange(CWStateChange.TYPE_UP_TO_DOWN);
	}

	public boolean sendUp() {
		return sendStateChange(CWStateChange.TYPE_DOWN_TO_UP);
	}

	public boolean processOutput(CWOutputNotification notify) {
		boolean addedToBuffer = false;

		/* Time to renew up-wave? */
		if (inManualUp && timeToNextContinuousUpWaveWork() == 0)
			renewUpState();

		outBuf.compact();

		while (isTimeToSend()) {
			/* Get first state-change from queue */
			CWStateChange stateToSend = queue.peek();

			if (!stateToSend.writeToBuffer(outBuf)) {
				/* Could not fit state-change to outBuf */
				break;
			}

			queue.remove();

			switch (stateToSend.getType()) {
			case CWStateChange.TYPE_DOWN_TO_UP:
				notify.stateChange(CWave.TYPE_UP, stateToSend.getValue());
				break;
			case CWStateChange.TYPE_UP_TO_DOWN:
				notify.stateChange(CWave.TYPE_DOWN, stateToSend.getValue());
				break;
			case CWStateChange.TYPE_FREQUENCY_CHANGE:
				notify.frequencyChange(((CWFrequencyChange) stateToSend)
						.getFrequency());
				break;
			}

			addedToBuffer = true;
		}

		outBuf.flip();

		return addedToBuffer;
	}

	public int queueSize() {
		if (queue != null)
			return queue.size();

		return 0;
	}
}
