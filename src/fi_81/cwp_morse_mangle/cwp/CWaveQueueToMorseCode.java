package fi_81.cwp_morse_mangle.cwp;

import java.util.ArrayList;
import java.util.Arrays;

import fi_81.cwp_morse_mangle.cwp.CWave;
import fi_81.cwp_morse_mangle.morse.BitString;
import fi_81.cwp_morse_mangle.morse.MorseCharList;

public class CWaveQueueToMorseCode {
	private static final double MORSE_SHORT_WIDTH = 1.0;
	private static final double MORSE_LONG_WIDTH = MORSE_SHORT_WIDTH * 3;
	private static final double MORSE_WORDBREAK_WIDTH = MORSE_SHORT_WIDTH * 7;

	private static final double MORSE_ALLOWED_SHORT_JITTER = 0.5;
	private static final double MORSE_ALLOWED_LONG_JITTER = 0.3;
	private static final double MORSE_ALLOWED_WORDBREAK_JITTER = 0.2;

	private static final BitString MORSE_SHORT_ONE_BITS = BitString.newOnes(1);
	private static final BitString MORSE_SHORT_ZERO_BITS = BitString
			.newZeros(1);
	private static final BitString MORSE_LONG_ONE_BITS = BitString.newOnes(3);
	private static final BitString MORSE_LONG_ZERO_BITS = BitString.newZeros(3);
	private static final BitString MORSE_WORDBREAK_ZERO_BITS = BitString
			.newZeros(7);

	private static final BitString MORSE_SPECIAL_END_OF_CONTACT_BITS = MorseCharList
			.characterToMorseString(MorseCharList.SPECIAL_END_OF_CONTACT);
	private static final BitString MORSE_SPECIAL_STOP_MESSAGE = MorseCharList
			.characterToMorseString(MorseCharList.SPECIAL_STOP_MESSAGE);
	private static final BitString MORSE_LONG_ZERO_AND_SPECIAL_STOP_MESSAGE = MORSE_LONG_ZERO_BITS
			.append(MORSE_SPECIAL_STOP_MESSAGE);

	/* limit adaption width to 1000ms */
	private static final double MORSE_MAX_ADAPTION_WIDTH = 1000.0;

	/* Current adaption to wave width (length in time) */
	private double adaptionWidth;

	/* Currently collected morse-bits */
	private BitString morseBits;

	private long lastPendingWaveTime;

	public CWaveQueueToMorseCode() {
		morseBits = new BitString();
		lastPendingWaveTime = 0;

		/* No adaption at start */
		adaptionWidth = 0.0;
	}

	public BitString tryDecode(CWInputQueue queue) {
		boolean readapted = false;

		if (!queue.isQueueReadReady())
			return null;

		/* Attempt to detect short morse signal width */
		if (adaptionWidth <= 0.0) {
			do {
				adaptionWidth = detectSignalWidth(queue, -1);
				readapted = true;

				if (adaptionWidth <= 0.0)
					return null;

				/* purge and ignore too width signals */
				if (adaptionWidth > MORSE_MAX_ADAPTION_WIDTH)
					queue.completeWavesFromBegining(1);
			} while (adaptionWidth > MORSE_MAX_ADAPTION_WIDTH);
		}

		ArrayList<CWave> waves = queue.getQueue();
		int i, len;

		/*
		 * Iterate waves from queue, convert to BitString with help of
		 * adaptionWidth
		 */
		for (i = 0, len = waves.size(); i < len; i++) {
			CWave wave = waves.get(i);

			/* skip zero length waves */
			if (wave.duration <= 0)
				continue;

			double waveWidth = wave.duration / adaptionWidth;

			/* Ignore leading down-waves */
			if (morseBits.length() == 0 && wave.type == CWave.TYPE_DOWN)
				continue;

			/*
			 * Compare adapted wave width, with short morse signal width.
			 * 
			 * If is shorter than threshold, set adaptionWidth zero and return
			 * collected morse bits (if no bits, retry processing).
			 * 
			 * If within threshold, append 1 or 0, depending on wave type.
			 */
			if (waveWidth <= MORSE_SHORT_WIDTH
					* (1.0 - MORSE_ALLOWED_SHORT_JITTER))
				break;

			if (waveWidth <= MORSE_SHORT_WIDTH
					* (1.0 + MORSE_ALLOWED_SHORT_JITTER)) {
				morseBits = morseBits
						.append(wave.type == CWave.TYPE_DOWN ? MORSE_SHORT_ZERO_BITS
								: MORSE_SHORT_ONE_BITS);
				lastPendingWaveTime = System.currentTimeMillis();

				if (wave.type == CWave.TYPE_UP) {
					/* Check if last received code was end-of-contact */
					if (morseBits.endWith(MORSE_SPECIAL_END_OF_CONTACT_BITS)) {
						/* As this is end of message, reset width adaption */
						adaptionWidth = 0.0;

						morseBits = morseBits.append(MORSE_LONG_ZERO_BITS);

						return returnMorseCode(queue, i);
					}
				}

				continue;
			}

			/*
			 * Compare adapted wave width, with long morse signal width.
			 * 
			 * If is shorter than threshold, set adaptionWidth zero and return
			 * collected morse bits (if no bits, retry processing).
			 * 
			 * If within threshold, append 111 or 000, depending on wave type.
			 */
			if (waveWidth <= MORSE_LONG_WIDTH
					* (1.0 - MORSE_ALLOWED_LONG_JITTER))
				break;

			if (waveWidth <= MORSE_LONG_WIDTH
					* (1.0 + MORSE_ALLOWED_LONG_JITTER)) {
				if (wave.type == CWave.TYPE_DOWN) {
					morseBits = morseBits.append(MORSE_LONG_ZERO_BITS);

					/* Received full morse code, pass morse code to upper layer */
					return returnMorseCode(queue, i);
				} else {
					morseBits = morseBits.append(MORSE_LONG_ONE_BITS);
					lastPendingWaveTime = System.currentTimeMillis();

					/* Check if last received code was end-of-contact */
					if (morseBits.endWith(MORSE_SPECIAL_END_OF_CONTACT_BITS)) {
						/* As this is end of message, reset width adaption */
						adaptionWidth = 0.0;

						morseBits = morseBits.append(MORSE_LONG_ZERO_BITS);

						return returnMorseCode(queue, i);
					}

					continue;
				}
			}

			/*
			 * If down-wave, compare adapted wave width, with word-break morse
			 * signal width.
			 * 
			 * If not within threshold, set adaptionWidth zero and return
			 * collected morse bits (if no bits, retry processing).
			 * 
			 * If within threshold, append 0000000.
			 */
			if (wave.type == CWave.TYPE_DOWN) {
				if (waveWidth <= MORSE_WORDBREAK_WIDTH
						* (1.0 - MORSE_ALLOWED_WORDBREAK_JITTER))
					break;

				if (waveWidth <= MORSE_WORDBREAK_WIDTH
						* (1.0 + MORSE_ALLOWED_WORDBREAK_JITTER)) {
					morseBits = morseBits.append(MORSE_WORDBREAK_ZERO_BITS);

					/*
					 * Received full morse word/code, pass morse code to upper
					 * layer
					 */
					return returnMorseCode(queue, i);
				}

				/*
				 * If down-wave is longer, assume that current message has
				 * reached end.
				 */

				/*
				 * Add end of contant code at end of these morse messages, to
				 * indicate end of work/message.
				 */
				morseBits = morseBits
						.append(MORSE_LONG_ZERO_AND_SPECIAL_STOP_MESSAGE);

				/* As this is end of message, reset width adaption */
				adaptionWidth = 0.0;

				return returnMorseCode(queue, i);
			}

			/* No match, attempt re-adaption */
			break;
		}

		/* Didn't process full queue, adaption did not match input */
		if (i < len) {
			/* Take out elements before this point */
			queue.completeWavesFromBegining(i);

			adaptionWidth = 0.0;

			/* no morse message received yet, fast retry */
			if (morseBits.length() == 0) {
				/*
				 * force decoding forward, take at least one wave if already
				 * readapted
				 */
				if (readapted && i == 0)
					queue.completeWavesFromBegining(1);

				return tryDecode(queue);
			}

			/*
			 * Add end of contant code at end of these morse messages, to
			 * indicate end of work/message.
			 */
			morseBits = morseBits
					.append(MORSE_LONG_ZERO_AND_SPECIAL_STOP_MESSAGE);

			BitString morseCode = morseBits;

			morseBits = new BitString();
			lastPendingWaveTime = 0;

			return morseCode;
		} else {
			/* Clear queue */
			queue.completeAllWaves(i);
		}

		/*
		 * This morse code (partially stored in morseBits) is not fully received
		 * yet, do not pass forward
		 */
		return null;
	}

	private BitString returnMorseCode(CWInputQueue queue, int i) {
		BitString morseCode = morseBits;

		/* clear handled waves */
		queue.completeWavesFromBegining(i + 1);

		/* reset bit buffer */
		morseBits = new BitString();
		lastPendingWaveTime = 0;

		return morseCode;
	}

	public BitString flushStalled() {
		if (morseBits.length() == 0 || lastPendingWaveTime == 0)
			return null;

		long currTime = System.currentTimeMillis();

		/* Check if currently stored bits are resent enough to keep */
		if (lastPendingWaveTime
				+ (long) (MORSE_WORDBREAK_WIDTH * adaptionWidth) >= currTime) {
			/* If received code is still short, return */
			if (morseBits.length() <= MorseCharList.longestMorseBits)
				return null;
		}

		/* morseBits contain old data that needs to be flushed */
		morseBits = morseBits.append(MORSE_LONG_ZERO_AND_SPECIAL_STOP_MESSAGE);

		BitString morseCode = morseBits;

		morseBits = new BitString();
		lastPendingWaveTime = 0;

		return morseCode;
	}

	private static class WaveGroup {
		private int sum;
		private int count;
		private double average;

		private WaveGroup() {
			sum = 0;
			count = 0;
			average = 0;
		}

		private void add(int duration) {
			sum += duration;
			count++;

			average = (double) sum / count;
		}

		private boolean isWithInRange(double waveWidth, double allowedJitter) {
			return (waveWidth > average * (1.0 - allowedJitter) && waveWidth <= average
					* (1.0 + allowedJitter));
		}

		private static double groupsAverage(WaveGroup groups[]) {
			double width = 0.0;
			int count = 0;

			if (groups[0].count > 0) {
				width += groups[0].average / MORSE_SHORT_WIDTH
						* groups[0].count;
				count += groups[0].count;
			}
			if (groups[1].count > 0) {
				width += groups[1].average / MORSE_LONG_WIDTH * groups[1].count;
				count += groups[1].count;
			}
			if (groups[2].count > 0) {
				width += groups[2].average / MORSE_WORDBREAK_WIDTH
						* groups[2].count;
				count += groups[2].count;
			}

			if (count > 1)
				width /= count;

			return width;
		}
	}

	private double detectSignalWidth(CWInputQueue queue, int sampleLimit) {
		if (queue.queueLength() <= 0)
			return 0.0;

		if (sampleLimit == 0) {
			/* reached limit 0, return width 0 */
			return 0.0;
		}

		boolean waitForUp = true;
		Object[] waves;
		WaveGroup group[] = new WaveGroup[3];
		int i, oldGroup, currGroup = -1;

		/*
		 * sample limit to prevent mixing of morse messages of different signal
		 * width
		 */
		if (sampleLimit >= 1) {
			waves = queue.getQueue().subList(0, sampleLimit).toArray();
		} else {
			waves = queue.getQueue().toArray();
			sampleLimit = waves.length;
		}

		for (i = 0; i < group.length; i++)
			group[i] = new WaveGroup();

		/* sort current waves by duration */
		Arrays.sort(waves);

		/*
		 * attempt to gather wave lengths from three different groups, starting
		 * from shortest
		 */
		for (Object o : waves) {
			CWave wave = (CWave) o;

			/* skip zero length waves */
			if (wave.duration <= 0)
				continue;

			/* Ignore leading down-waves */
			if (waitForUp && wave.type == CWave.TYPE_DOWN)
				continue;
			else
				waitForUp = false;

			oldGroup = currGroup;
			switch (currGroup) {
			case -1:
				/*
				 * Assume that first value is good match for group[0].
				 * 
				 * This might be bad guess. If we receive two morse messages in
				 * close proximity with different signal widths and first
				 * message has longer width, this might go wrong. Therefore
				 * check adaption against
				 */
				group[0].add(wave.duration);
				currGroup = 0;
				break;

			case 0:
				if (group[0].isWithInRange(wave.duration,
						MORSE_ALLOWED_SHORT_JITTER)) {
					group[0].add(wave.duration);
					break;
				}

				if (group[0].isWithInRange(wave.duration / MORSE_LONG_WIDTH,
						MORSE_ALLOWED_LONG_JITTER)) {
					group[1].add(wave.duration);
					currGroup = 1;
					break;
				}

				if (group[0]
						.isWithInRange(wave.duration / MORSE_WORDBREAK_WIDTH,
								MORSE_ALLOWED_WORDBREAK_JITTER)) {
					group[2].add(wave.duration);
					currGroup = 2;
					break;
				}

				currGroup = 3;
				break;

			case 1:
				if (group[1].isWithInRange(wave.duration,
						MORSE_ALLOWED_LONG_JITTER)
						|| group[0].isWithInRange(wave.duration
								/ MORSE_LONG_WIDTH, MORSE_ALLOWED_LONG_JITTER)) {
					group[1].add(wave.duration);
					break;
				}

				if (group[0]
						.isWithInRange(wave.duration / MORSE_WORDBREAK_WIDTH,
								MORSE_ALLOWED_WORDBREAK_JITTER)
						|| group[1].isWithInRange(wave.duration
								* MORSE_LONG_WIDTH / MORSE_WORDBREAK_WIDTH,
								MORSE_ALLOWED_WORDBREAK_JITTER)) {
					group[2].add(wave.duration);
					currGroup = 2;
					break;
				}

				currGroup = 3;
				break;

			case 2:
				if (group[2].isWithInRange(wave.duration,
						MORSE_ALLOWED_WORDBREAK_JITTER)
						|| group[1].isWithInRange(wave.duration
								* MORSE_LONG_WIDTH / MORSE_WORDBREAK_WIDTH,
								MORSE_ALLOWED_WORDBREAK_JITTER)
						|| group[0].isWithInRange(wave.duration
								/ MORSE_WORDBREAK_WIDTH,
								MORSE_ALLOWED_WORDBREAK_JITTER)) {
					group[2].add(wave.duration);
					break;
				}

				currGroup = 3;
				break;
			}

			if (oldGroup != -1 && oldGroup != currGroup) {
				/* perform sanity checks against bad width detection */
				if (!isDetectedWidthOk(WaveGroup.groupsAverage(group), queue)) {
					waves = null;
					group = null;
					return detectSignalWidth(queue, sampleLimit / 2);
				}
			}

			if (currGroup == 3)
				break;
		}

		/* Combine gathered group averages for detected signal width */
		double width = WaveGroup.groupsAverage(group);

		if (width < 1.0)
			width = 1.0;

		return width;
	}

	private static boolean isDetectedWidthOk(double canditateAdaption,
			CWInputQueue queue) {
		boolean waitForUp = true;
		int shortOkCount = 0;
		int longOkCount = 0;
		int wordbreakOkCount = 0;

		for (CWave wave : queue.getQueue()) {
			/* skip zero length waves */
			if (wave.duration <= 0)
				continue;

			/* Ignore leading down-waves */
			if (waitForUp && wave.type == CWave.TYPE_DOWN)
				continue;
			else
				waitForUp = false;

			double waveWidth = wave.duration / canditateAdaption;

			if (waveWidth > MORSE_SHORT_WIDTH
					* (1.0 - MORSE_ALLOWED_SHORT_JITTER)
					&& waveWidth <= MORSE_SHORT_WIDTH
							* (1.0 + MORSE_ALLOWED_SHORT_JITTER)) {
				shortOkCount++;
				continue;
			}

			if (waveWidth > MORSE_LONG_WIDTH
					* (1.0 - MORSE_ALLOWED_LONG_JITTER)
					&& waveWidth <= MORSE_LONG_WIDTH
							* (1.0 + MORSE_ALLOWED_LONG_JITTER)) {
				longOkCount++;
				continue;
			}

			if (waveWidth > MORSE_WORDBREAK_WIDTH
					* (1.0 - MORSE_ALLOWED_WORDBREAK_JITTER)
					&& waveWidth <= MORSE_WORDBREAK_WIDTH
							* (1.0 + MORSE_ALLOWED_WORDBREAK_JITTER)) {
				wordbreakOkCount++;
				continue;
			}

			/* bad wave length detected */
			break;
		}

		/*
		 * We could do better heuristics here, but for now just go with the
		 * following.
		 */
		int totalOkCount = shortOkCount + longOkCount + wordbreakOkCount;

		return totalOkCount > 0 && shortOkCount > 0;
	}

	public boolean hadPendingBits() {
		if (morseBits == null)
			return false;

		return (morseBits.length() > 0);
	}
}
