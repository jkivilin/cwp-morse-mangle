package fi_81.cwp_morse_mangle;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import fi_81.cwp_morse_mangle.cwp.CWStateChangeQueueFromMorseCode;

import android.util.Log;

public class CWPControlThread extends Thread {
	private static final String TAG = "CWPControlThread";

	/* Connection states */
	public static final int CONN_NO_CONFIGURATION = 0;
	public static final int CONN_RESOLVING_ADDRESS = 1;
	public static final int CONN_CREATE_CONNECTION = 2;
	public static final int CONN_CONNECTED = 3;

	/* Used to signal thread to end work */
	private AtomicBoolean isThreadKilled = new AtomicBoolean(false);

	/* Synchronized queue used for passing data to IO-thread */
	private Vector<CWPThreadValue> msgQueue = new Vector<CWPThreadValue>(16);

	/* Initial state does not have server-details, "no configuration" */
	private int connState = CONN_NO_CONFIGURATION;

	/* Connection configuration */
	private String hostName = "";
	private int hostPort = 0;
	private int morseSpeed = 0;

	/* Current connection setup */
	private InetAddress currAddr;

	/* Parent service */
	private CWPControlService cwpService;

	/** Service reference is needed for callbacks */
	public CWPControlThread(CWPControlService service) {
		cwpService = service;
	}

	/** Main loop of thread */
	private void run_loop() {
		try {
			switch (connState) {
			default:
			case CONN_NO_CONFIGURATION:
				/* Wait for configuration from UI-thread. */
				sleep(Long.MAX_VALUE);
				break;

			case CONN_RESOLVING_ADDRESS:
				handleResolvingAddress();

				break;

			case CONN_CREATE_CONNECTION:
				sleep(Long.MAX_VALUE);
				break;

			case CONN_CONNECTED:
				break;
			}
		} catch (InterruptedException ie) {
			/* woke from sleep */
		}

		/* Thread is being killed? */
		if (isThreadKilled.get())
			return;

		/*
		 * Each connection state handler might be sleeping and have sleep
		 * interrupted by messages from UI-thread.
		 */
		handleMessageQueue();
	}

	private void handleResolvingAddress() throws InterruptedException {
		/* Resolve IP-address for hostname */
		try {
			currAddr = InetAddress.getByName(hostName);

			/* TODO: notify UI-thread about successful connection */

			connState = CONN_CREATE_CONNECTION;
		} catch (UnknownHostException e) {
			/* Invalid hostname, cannot be resolved */

			/* Wait some time before retrying resolving address */
			sleep(5000);
		}
	}

	/** Handle messages from UI-thread */
	private void handleMessageQueue() {
		CWPThreadValue value;

		while ((value = queuePop(msgQueue)) != null) {
			switch (value.type) {
			case CWPThreadValue.TYPE_CONFIGURATION:
				handleNewConfiguration(value.getHostName(),
						value.getHostPort(), value.getMorseSpeed());
				break;
			case CWPThreadValue.TYPE_STATE_CHANGE:
				// handleNewSendingState(value.isStateUp());
				break;
			case CWPThreadValue.TYPE_FREQ_CHANGE:
				// handleNewFrequency(value.getFrequency());
				break;
			case CWPThreadValue.TYPE_MORSE_MESSAGE:
				// handleNewMorseMessage(value.getMorseMessage());
				break;
			}
		}

		/* Push unused value to memory pool */
		/*
		 * if (freeQueue.size() < 16) { queuePush(freeQueue, value.clear()); }
		 */
	}

	private void handleNewConfiguration(String hostName, int hostPort,
			int morseSpeed) {
		/* No configuration, fill in values and change connection state */
		if (connState == CONN_NO_CONFIGURATION) {
			this.hostName = hostName;
			this.hostPort = hostPort;
			this.morseSpeed = morseSpeed;

			CWStateChangeQueueFromMorseCode.setSignalWidth(morseSpeed);
			CWStateChangeQueueFromMorseCode.setSignalJitter(Integer.MAX_VALUE,
					0.0);

			connState = CONN_RESOLVING_ADDRESS;
			return;
		}

		if (this.morseSpeed != morseSpeed) {
			/* Set new morse speed */
			CWStateChangeQueueFromMorseCode.setSignalWidth(morseSpeed);

			this.morseSpeed = morseSpeed;
		}

		if (this.hostName.compareTo(hostName) != 0 || this.hostPort != hostPort) {
			/* Server setup changed, trigger reconnection */
			this.hostName = hostName;
			this.hostPort = hostPort;

			// if (isConnected)
			// disconnectFromServer();

			connState = CONN_RESOLVING_ADDRESS;
		}
	}

	@Override
	public void run() {
		while (!isThreadKilled.get())
			run_loop();

		/* Parent has killed CWP thread, do clean up */
		msgQueue.clear();
	}

	/** Signal and wait thread to quit work */
	public void endWorkAndJoin() {
		isThreadKilled.set(true);
		interrupt();

		try {
			join();
		} catch (InterruptedException ie) {
			/* UI-thread interrupted from wait */
			Log.w(TAG, String.format(
					"endWorkAndJoin(): joining ioThread failed [%s]",
					ie.toString()));
		}
	}

	/** Set up new configuration for thread */
	public void setNewConfiguration(String hostName, int hostPort,
			int morseSpeed) {
		/* Push new configuration as message to IO-thread */
		queuePush(msgQueue, CWPThreadValue.buildConfiguration(hostName,
				hostPort, morseSpeed));

		/* signal IO-thread of new message */
		interrupt();
	}

	private static void queuePush(Vector<CWPThreadValue> msgQueue,
			CWPThreadValue value) {
		msgQueue.add(value);
	}

	private static CWPThreadValue queuePop(Vector<CWPThreadValue> msgQueue) {
		try {
			return msgQueue.remove(0);
		} catch (IndexOutOfBoundsException IOOBE) {
			/* empty queue */
			return null;
		}
	}

	/** Class for passing messages from UI-thread to IO-thread */
	private static class CWPThreadValue {
		protected static final int TYPE_CONFIGURATION = 0;
		protected static final int TYPE_STATE_CHANGE = 1;
		protected static final int TYPE_FREQ_CHANGE = 2;
		protected static final int TYPE_MORSE_MESSAGE = 3;

		protected int type;
		protected long argLong0;
		protected int argInt0;
		protected String argString0;

		protected static CWPThreadValue buildConfiguration(String hostName,
				int hostPort, int morseSpeed) {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_CONFIGURATION;
			value.argString0 = hostName;
			value.argLong0 = hostPort;
			value.argInt0 = morseSpeed;

			return value;
		}

		protected static CWPThreadValue buildStateChange(boolean isStateUp) {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_STATE_CHANGE;
			value.argString0 = null;
			value.argLong0 = isStateUp ? 1 : 0;
			value.argInt0 = 0;

			return value;
		}

		protected static CWPThreadValue buildFreqChange(long freq) {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_FREQ_CHANGE;
			value.argString0 = null;
			value.argLong0 = freq;
			value.argInt0 = 0;

			return value;
		}

		protected static CWPThreadValue buildMorseMessage(String message) {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_MORSE_MESSAGE;
			value.argString0 = message;
			value.argLong0 = 0;
			value.argInt0 = 0;

			return value;
		}

		/*
		 * Values for TYPE_CONFIGURATION
		 */
		protected String getHostName() {
			return argString0;
		}

		protected int getHostPort() {
			return (int) argLong0;
		}

		protected int getMorseSpeed() {
			return argInt0;
		}

		/*
		 * Values for TYPE_STATE_CHANGE
		 */
		protected boolean isStateUp() {
			return argLong0 == 1;
		}

		/*
		 * Values for TYPE_FREQUENCY_CHANGE
		 */
		protected long getFrequency() {
			return argLong0;
		}

		/*
		 * Values for TYPE_MORSE_MESSAGE
		 */
		protected String getMorseMessage() {
			return argString0;
		}
	}
}
