package fi_81.cwp_morse_mangle.cwp;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NoSuchElementException;

import fi_81.cwp_morse_mangle.cwp.CWStateChange;
import fi_81.cwp_morse_mangle.morse.BitString;

public class CWStateChangeQueueFromMorseCode {
	private static int signalWidth = 1;
	private static int signalJitterThreshold = 0;
	private static double signalJitter = 0.0;

	public static void setSignalWidth(int signalWidth) {
		assert (signalWidth > 0);

		CWStateChangeQueueFromMorseCode.signalWidth = signalWidth;
	}

	public static void setSignalJitter(int threshold, double jitter) {
		signalJitterThreshold = threshold;
		signalJitter = jitter;
	}

	public static int getSignalWidth() {
		return signalWidth;
	}

	public void encode(Deque<CWStateChange> states, BitString bits) {
		int i, len = bits.length();
		boolean isUp = false;
		int timestamp = 0;
		int duration = 0;

		for (i = 0; i < len; i++) {
			if (bits.charAt(i) == '1') {
				if (!isUp) {
					/* State change, down to up. With timestamp. */
					states.add(newFromMemPoolCWStateChange(
							CWStateChange.TYPE_DOWN_TO_UP, timestamp, timestamp));
					isUp = true;
					duration = 0;
				}
			} else {
				if (isUp) {
					/* State change, up to down. With duration */
					states.add(newFromMemPoolCWStateChange(
							CWStateChange.TYPE_UP_TO_DOWN, duration, timestamp));
					isUp = false;
				}
			}

			int add = signalWidth;
			if (add >= signalJitterThreshold && signalJitter > 0.0
					&& signalJitter < 0.5) {
				double jitter = 2 * signalJitter * (Math.random() - 0.5) + 1.0;

				add = (int) Math.round((double) add * jitter);
			}

			timestamp += add;
			duration += add;
		}

		/* if left in up-state, append up-to-down state change */
		if (isUp)
			states.add(newFromMemPoolCWStateChange(
					CWStateChange.TYPE_UP_TO_DOWN, duration, timestamp));
	}

	/* Memory pool for CWStateChanges */
	private final int CWSTATE_CHANGE_MEMPOOL_MAX_SIZE = 32;
	private final ArrayDeque<CWStateChange> freeQueue = new ArrayDeque<CWStateChange>();

	/* Memory pool allocator for CWStateChanges */
	private CWStateChange popFromMemPool() {
		if (freeQueue.size() == 0)
			return new CWFrequencyChange();

		try {
			CWStateChange stateChange = freeQueue.pop();
			return stateChange;
		} catch (NoSuchElementException NSEE) {
			return new CWFrequencyChange();
		}
	}

	/* Pushes unused CWStateChange back to freeQueue */
	public void pushToMemPool(CWStateChange unusedStateChange) {
		if (freeQueue.size() >= CWSTATE_CHANGE_MEMPOOL_MAX_SIZE)
			return;

		freeQueue.push(unusedStateChange);
	}

	/* CWStateChange allocator from mem-pool */
	public CWStateChange newFromMemPoolCWStateChange(byte type,
			int timestampOrDuration, long outTime) {
		CWStateChange stateChange = popFromMemPool();

		stateChange.setValues(type, timestampOrDuration, outTime);

		return stateChange;
	}

	/* CWFrequencyChange allocator from mem-pool */
	public CWFrequencyChange newFromMemPoolCWFrequencyChange(long freq) {
		CWFrequencyChange freqChange = (CWFrequencyChange) popFromMemPool();

		freqChange.setValues(freq);

		return freqChange;
	}
}
