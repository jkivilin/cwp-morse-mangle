package fi_81.cwp_morse_mangle.cwp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;

import fi_81.cwp_morse_mangle.morse.BitString;

public class CWOutput {
	/* Callbacks from output, frequency change, state changes */
	public interface CWOutputNotification {
		public abstract void frequencyChange(long newFreq);

		public abstract void stateChange(byte newState, int value);
	}

	public class CWOutputNotificationNone implements CWOutputNotification {
		public void frequencyChange(long newFreq) {
		}

		public void stateChange(byte newState, int value) {
		}
	}

	private ByteBuffer outBuf;
	private List<CWStateChange> queue;
	private long startTime;

	private boolean inManualUp;
	private long manualUpStartTime;
	private long delayedFreq = -1;

	public CWOutput(long connectionStartTime) {
		this(null, connectionStartTime);
	}

	public CWOutput(ByteBuffer bb, long connectionStartTime) {
		queue = new LinkedList<CWStateChange>();
		startTime = connectionStartTime;
		inManualUp = false;
		manualUpStartTime = 0;

		if (bb == null) {
			/* Allocate IO buffer and set it to big-endian byteorder */
			outBuf = ByteBuffer.allocate(4);
			outBuf.order(ByteOrder.BIG_ENDIAN);
		} else {
			outBuf = bb;
		}

		outBuf.flip();
	}

	public ByteBuffer getOutputBuffer() {
		return outBuf;
	}

	public int timeToNext() {
		/* no next, return -1 */
		if (queue.isEmpty())
			return -1;

		long currentTime = System.currentTimeMillis();
		int timeSinceConnCreation = (int) (currentTime - startTime);
		int timeToNext = queue.get(0).getOutTime() - timeSinceConnCreation;

		if (timeToNext < 0)
			return -1;

		return timeToNext;
	}

	private boolean isTimeToSend() {
		if (queue.isEmpty())
			return false;

		/*
		 * Check if current time has reached send-time of first state-change in
		 * queue
		 */
		long currentTime = System.currentTimeMillis();
		int timeSinceConnCreation = (int) (currentTime - startTime);

		if (queue.get(0).getOutTime() <= timeSinceConnCreation)
			return true;

		return false;
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

		queue = CWStateChangeQueueFromMorseCode.encode(morseCode);

		/* adjust timestamps based on time since connection was created */
		long currentTime = System.currentTimeMillis();
		int timeSinceConnCreation = (int) (currentTime - startTime);
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
		if (newFreq < 1 || -newFreq < Integer.MIN_VALUE)
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
			int upStateDuration = (int) (currentTime - manualUpStartTime);
			int timestamp = (int) (currentTime - startTime);

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
			int timestamp = (int) (manualUpStartTime - startTime);

			queue.add(new CWStateChange(stateChange, timestamp, timestamp));

			inManualUp = true;
			return true;
		}

		return true;
	}

	public boolean sendDown() {
		return sendStateChange(CWStateChange.TYPE_UP_TO_DOWN);
	}

	public boolean sendUp() {
		return sendStateChange(CWStateChange.TYPE_DOWN_TO_UP);
	}

	public boolean processOutput(CWOutputNotification notify) {
		boolean addedToBuffer = false;

		outBuf.compact();

		while (isTimeToSend()) {
			/* Get first state-change from queue */
			CWStateChange stateToSend = queue.get(0);

			if (!stateToSend.writeToBuffer(outBuf)) {
				/* Could not fit state-change to outBuf */
				break;
			}

			/* state-change queued for transmit, remove from queue */
			queue.remove(0);

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
