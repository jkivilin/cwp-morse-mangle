package fi_81.cwp_morse_mangle;

import java.util.concurrent.atomic.AtomicBoolean;

import android.util.Log;

public class CWPControlThread extends Thread {
	private static final String TAG = "CWPControlThread";

	/* Connection states */
	public static final int CONN_NO_CONFIGURATION = 0;
	public static final int CONN_RESOLVING_ADDRESS = 1;
	public static final int CONN_CONNECTING = 2;
	public static final int CONN_CONNECTED = 3;

	/* Used to signal thread to end work */
	private AtomicBoolean isThreadKilled = new AtomicBoolean(false);

	/* Initial state does not have server-details, "no configuration" */
	private int connState = CONN_NO_CONFIGURATION;

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
				break;

			case CONN_CONNECTING:
				break;

			case CONN_CONNECTED:
				break;
			}
		} catch (InterruptedException ie) {
		}

		/*
		 * Each connection state handler might be sleeping and have sleep
		 * interrupted by messages from UI-thread. Therefore handle those
		 * messages here.
		 */

		if (isThreadKilled.get()) {
			/* Parent has killed CWP thread, do connection clean up */
			// TODO: clean up
			return;
		}
	}

	@Override
	public void run() {
		while (!isThreadKilled.get())
			run_loop();
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
}
