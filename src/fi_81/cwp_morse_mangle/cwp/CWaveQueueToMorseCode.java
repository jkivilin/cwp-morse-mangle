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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Iterator;

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

	/* limit number of sample waves used for detection */
	private static final int MORSE_MAX_DETECTION_SAMPLE = 24;

	/* Current adaption to wave width (length in time) */
	private double adaptionWidth;

	/* Currently collected morse-bits */
	private final StringBuffer morseBits = new StringBuffer();

	private long lastPendingWaveTime;

	public CWaveQueueToMorseCode() {
		lastPendingWaveTime = 0;

		/* No adaption at start */
		adaptionWidth = 0.0;
	}

	public BitString tryDecode(CWInputQueue queue, boolean force) {
		boolean readapted = false;

		if (!queue.isQueueReadReady())
			return null;

		/* Attempt to detect short morse signal width */
		if (adaptionWidth <= 0.0) {
			do {
				CWave[] samples = queue.getQueue().toArray(emptyCWaveArray);
				adaptionWidth = detectSignalWidth(samples, samples.length,
						force);
				readapted = true;

				if (adaptionWidth <= 0.0) {
					if (!force)
						return null;

					adaptionWidth = 1.0;
				}

				/* purge and ignore too width signals */
				if (adaptionWidth > MORSE_MAX_ADAPTION_WIDTH)
					queue.completeWavesFromBegining(1);
			} while (adaptionWidth > MORSE_MAX_ADAPTION_WIDTH);
		}

		ArrayDeque<CWave> waves = queue.getQueue();
		int i, len;

		/*
		 * Iterate waves from queue, convert to BitString with help of
		 * adaptionWidth
		 */
		Iterator<CWave> waveIter = waves.iterator();
		for (i = 0, len = waves.size(); waveIter.hasNext() && i < len; i++) {
			CWave wave = waveIter.next();

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
				morseBits
						.append(wave.type == CWave.TYPE_DOWN ? MORSE_SHORT_ZERO_BITS
								: MORSE_SHORT_ONE_BITS);
				lastPendingWaveTime = System.currentTimeMillis();

				if (wave.type == CWave.TYPE_UP) {
					/* Check if last received code was end-of-contact */
					if (BitString.stringBufferEndWithBits(morseBits,
							MORSE_SPECIAL_END_OF_CONTACT_BITS)) {
						/* As this is end of message, reset width adaption */
						adaptionWidth = 0.0;

						morseBits.append(MORSE_LONG_ZERO_BITS);

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
					morseBits.append(MORSE_LONG_ZERO_BITS);

					/* Received full morse code, pass morse code to upper layer */
					return returnMorseCode(queue, i);
				} else {
					morseBits.append(MORSE_LONG_ONE_BITS);
					lastPendingWaveTime = System.currentTimeMillis();

					/* Check if last received code was end-of-contact */
					if (BitString.stringBufferEndWithBits(morseBits,
							MORSE_SPECIAL_END_OF_CONTACT_BITS)) {
						/* As this is end of message, reset width adaption */
						adaptionWidth = 0.0;

						morseBits.append(MORSE_LONG_ZERO_BITS);

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
					morseBits.append(MORSE_WORDBREAK_ZERO_BITS);

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
				morseBits.append(MORSE_LONG_ZERO_AND_SPECIAL_STOP_MESSAGE);

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

				return tryDecode(queue, force);
			}

			/*
			 * Add end of contant code at end of these morse messages, to
			 * indicate end of work/message.
			 */
			morseBits.append(MORSE_LONG_ZERO_AND_SPECIAL_STOP_MESSAGE);

			BitString morseCode = BitString.newBits(morseBits.toString());

			morseBits.setLength(0);
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
		BitString morseCode = BitString.newBits(morseBits.toString());

		/* clear handled waves */
		queue.completeWavesFromBegining(i + 1);

		/* reset bit buffer */
		morseBits.setLength(0);
		lastPendingWaveTime = 0;

		return morseCode;
	}

	public BitString flushStalled(boolean forceFlush) {
		if (morseBits.length() == 0)
			return null;

		if (!forceFlush) {
			if (lastPendingWaveTime == 0)
				return null;

			long currTime = System.currentTimeMillis();

			/* Check if currently stored bits are resent enough to keep */
			if (lastPendingWaveTime
					+ (long) (MORSE_WORDBREAK_WIDTH * adaptionWidth) >= currTime) {
				/* If received code is still short, return */
				if (morseBits.length() <= MorseCharList.longestMorseBits)
					return null;
			}
		}

		/* morseBits contain old data that needs to be flushed */
		morseBits.append(MORSE_LONG_ZERO_AND_SPECIAL_STOP_MESSAGE);

		BitString morseCode = BitString.newBits(morseBits.toString());

		morseBits.setLength(0);
		lastPendingWaveTime = 0;

		return morseCode;
	}

	public boolean hadPendingBits() {
		return (morseBits.length() > 0);
	}

	private static class WaveGroup {
		private int sum;
		private int count;
		private double average;

		private WaveGroup() {
			clear();
		}

		private void clear() {
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

	private final CWave[] detectSignalWaves = new CWave[MORSE_MAX_DETECTION_SAMPLE];
	private final WaveGroup detectSignalGroup[] = { new WaveGroup(),
			new WaveGroup(), new WaveGroup() };

	private double detectSignalWidth(final CWave[] samples, int sampleLimit,
			boolean force) {
		if (samples.length <= 0)
			return 0.0;

		if (sampleLimit == 0) {
			/* reached limit 0, return width 0 */
			return 0.0;
		}

		final WaveGroup group[] = detectSignalGroup;
		final CWave[] waves = detectSignalWaves;
		boolean waitForUp = true;
		boolean checkedDetectionOk = false;
		int oldGroup, currGroup = -1;

		for (int i = 0; i < group.length; i++)
			group[i].clear();

		/*
		 * sample limit to prevent mixing of morse messages of different signal
		 * width
		 */
		if (sampleLimit < 0)
			sampleLimit = samples.length;

		if (sampleLimit > MORSE_MAX_DETECTION_SAMPLE)
			sampleLimit = MORSE_MAX_DETECTION_SAMPLE;

		/* Copy waves to sample limit */
		System.arraycopy(samples, 0, waves, 0, sampleLimit);

		/* sort current waves by duration */
		Arrays.sort(waves, 0, sampleLimit);

		/*
		 * attempt to gather wave lengths from three different groups, starting
		 * from shortest
		 */
		for (int i = 0; i < sampleLimit; i++) {
			CWave wave = waves[i];

			/* skip zero length waves */
			if (wave.duration <= 0)
				continue;

			/* Ignore leading down-waves */
			if (waitForUp && wave.type == CWave.TYPE_DOWN)
				continue;

			waitForUp = false;
			checkedDetectionOk = false;
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
				if (!isDetectedWidthOk(WaveGroup.groupsAverage(group), samples,
						sampleLimit)) {
					Arrays.fill(waves, null);
					return detectSignalWidth(samples, sampleLimit / 2, force);
				}

				checkedDetectionOk = true;
			}

			if (currGroup == 3)
				break;
		}

		Arrays.fill(waves, null);

		/* Combine gathered group averages for detected signal width */
		double width = WaveGroup.groupsAverage(group);

		/*
		 * perform sanity checks against bad width detection (only when not in
		 * forced decoding mode)
		 */
		if (!force) {
			if (!checkedDetectionOk
					&& !isDetectedWidthOk(width, samples, sampleLimit))
				return 0.0;
		}

		if (width < 1.0)
			width = 1.0;

		return width;
	}

	private static boolean isDetectedWidthOk(double canditateAdaption,
			CWave[] samples, int sampleLimit) {
		boolean waitForUp = true;
		int shortOkCount = 0;
		int longOkCount = 0;
		int wordbreakOkCount = 0;

		int i = 0;
		for (CWave wave : samples) {
			if (i++ >= sampleLimit)
				break;

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

		return shortOkCount > 0 && totalOkCount > shortOkCount;
	}

	public long getFlushTimeout() {
		double width = adaptionWidth;
		if (width <= 0.0)
			width = 250.0;

		return (long) (width * (MORSE_WORDBREAK_WIDTH + 1));
	}

	/* Cached empty array for toArray(T[]) */
	private static final CWave[] emptyCWaveArray = new CWave[0];
}
