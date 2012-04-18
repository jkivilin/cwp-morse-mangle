package fi_81.cwp_morse_mangle.cwp;

import java.util.Deque;

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

	public static void encode(Deque<CWStateChange> states, BitString bits) {
		int i, len = bits.length();
		boolean isUp = false;
		int timestamp = 0;
		int duration = 0;

		for (i = 0; i < len; i++) {
			if (bits.charAt(i) == '1') {
				if (!isUp) {
					/* State change, down to up. With timestamp. */
					states.add(new CWStateChange(CWStateChange.TYPE_DOWN_TO_UP,
							timestamp, timestamp));
					isUp = true;
					duration = 0;
				}
			} else {
				if (isUp) {
					/* State change, up to down. With duration */
					states.add(new CWStateChange(CWStateChange.TYPE_UP_TO_DOWN,
							duration, timestamp));
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
			states.add(new CWStateChange(CWStateChange.TYPE_UP_TO_DOWN,
					duration, timestamp));
	}
}
