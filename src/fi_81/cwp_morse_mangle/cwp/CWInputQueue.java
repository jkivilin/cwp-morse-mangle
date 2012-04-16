package fi_81.cwp_morse_mangle.cwp;

import java.util.ArrayList;

import fi_81.cwp_morse_mangle.cwp.CWave;

public class CWInputQueue {
	private byte previousType;
	private int previousTimestamp;
	private boolean mergeLastUpWave;
	private ArrayList<CWave> waveQueue;

	public CWInputQueue() {
		/* New connection starts with state down and timestamp zero */
		previousType = CWave.TYPE_DOWN;
		previousTimestamp = 0;
		mergeLastUpWave = false;

		waveQueue = new ArrayList<CWave>();
	}

	public byte getCurrentState() {
		if (previousType == CWave.TYPE_DOWN && mergeLastUpWave)
			return CWave.TYPE_UP;

		return previousType;
	}

	public int getCurrentStateTimestamp() {
		return previousTimestamp;
	}

	public void pushStateUp(int timestamp) {
		int duration;

		if (mergeLastUpWave)
			throw new IndexOutOfBoundsException(); /* TODO: own exception */

		if (previousType == CWave.TYPE_UP)
			throw new IndexOutOfBoundsException(); /* TODO: own exception */

		duration = timestamp - previousTimestamp;
		if (duration < 0)
			throw new IndexOutOfBoundsException(); /* TODO: own exception */
		if (duration == 0 && !waveQueue.isEmpty()) {
			mergeLastUpWave = true;
			return;
		}

		pushWave(previousType, duration);

		previousTimestamp = timestamp;
		previousType = CWave.TYPE_UP;
	}

	public void pushStateDown(int duration) {
		/* Merging of long Up-wave segments into single piece. */
		if (mergeLastUpWave) {
			if (previousType == CWave.TYPE_UP)
				throw new IndexOutOfBoundsException(); /* TODO: own exception */

			mergeLastUpWave = false;

			CWave lastWave = waveQueue.get(waveQueue.size() - 1);

			lastWave.duration += duration;
			previousTimestamp += duration;

			return;
		}

		if (previousType == CWave.TYPE_DOWN)
			throw new IndexOutOfBoundsException(); /* TODO: own exception */

		pushWave(previousType, duration);

		previousTimestamp += duration;
		previousType = CWave.TYPE_DOWN;
	}

	public void pushWave(byte type, int duration) {
		waveQueue.add(new CWave(type, duration));
	}

	public int queueLength() {
		return waveQueue.size();
	}

	public ArrayList<CWave> getQueue() {
		return waveQueue;
	}

	public boolean isQueueReadReady() {
		/* In special merge wave mode */
		if (mergeLastUpWave)
			return false;

		/* Up state, wait for change */
		if (previousType == CWave.TYPE_UP)
			return false;

		/* Empty queue, not ready */
		if (waveQueue.isEmpty())
			return false;

		/*
		 * In down state, has data, not waiting for merging up-wave => queue
		 * ready
		 */
		return true;
	}

	public void completeWavesFromBegining(int beginingLen) {
		while (beginingLen-- > 0)
			waveQueue.remove(0);
	}

	public void completeAllWaves(int i) {
		waveQueue.clear();
	}
}
