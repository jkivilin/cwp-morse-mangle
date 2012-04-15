package fi_81.cwp_morse_mangle;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class CWPControlService extends Service {
	private static final String TAG = "CWPControlService";
	private final IBinder binder = new CWPControlBinder();

	/* Threading */
	private Thread ioThread;
	private boolean ioThreadStop = false;

	/* Callbacks to MainActivity */
	private CWPControlNotification notify = null;
	private Handler notifyHandler = null;

	/* Send and receive channel states */
	private boolean recvStateUp = false;
	private boolean sendStateUp = false;

	/* Received morse string */
	private String morseMessage = "";

	/* Sending morse state */
	private int sendingMorseCount = 0;

	/* Thread-safe setter & getter for morseMessage */
	private synchronized void setMorseMessage(String morse) {
		morseMessage = morse;
	}

	private synchronized String getMorseMessage() {
		return morseMessage;
	}

	/* Local process binder with getter of service object */
	public class CWPControlBinder extends Binder {
		private static final String TAG = "CWPControlBinder";

		public CWPControlService getService() {
			Log.d(TAG, "getService()");

			return CWPControlService.this;
		}
	}

	/* Callbacks for MainActivity */
	public interface CWPControlNotification {
		public static final int STATE_DOWN = 0;
		public static final int STATE_UP = 1;
		public static final int STATE_DOUBLE_UP = 2;

		/**
		 * Called when stateChanges, 0 = down, 1 = up, 2 = double-up (when
		 * received up when sending up)
		 */
		abstract public void stateChange(int state);

		/**
		 * Called when CWPService manages to decode received signals as morse
		 * code and morse message string has been updated as result.
		 */
		abstract public void morseUpdated(String latest);

		/**
		 * Called when CWPService completes sending morse message.
		 */
		abstract public void morseMessageComplete();
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Log.d(TAG, "onBind()");

		return binder;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate()");

		/* Simulate receiving of messages from IO thread */
		ioThread = new Thread(new Runnable() {
			public void run() {
				while (!ioThreadStop) {
					boolean up, sendingComplete = false;

					synchronized (CWPControlService.this) {
						up = recvStateUp;

						if (sendingMorseCount > 0)
							if (--sendingMorseCount == 0)
								sendingComplete = true;
					}

					try {
						if (up)
							Thread.sleep(100);
						else
							Thread.sleep(2000);
					} catch (InterruptedException e) {
						return;
					}

					synchronized (CWPControlService.this) {
						recvStateUp = !recvStateUp;
					}

					notifyStateChange();

					if (sendingComplete)
						notifyMorseMessageComplete();
				}
			}
		});

		ioThread.start();

		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "onStartCommand()");

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d(TAG, "onUnbind()");

		/* clear notifications to prevent calling unloaded activity */
		notify = null;

		return super.onUnbind(intent);
	}

	@Override
	public void onRebind(Intent intent) {
		Log.d(TAG, "onRebind()");

		super.onRebind(intent);
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy()");

		ioThreadStop = true;
		ioThread.interrupt();
		try {
			ioThread.join();
		} catch (InterruptedException e) {
			/* do nothing */
			Log.e(TAG,
					"onDestroy(): joining ioThread failed with " + e.toString());
		}

		super.onDestroy();
	}

	/** Registers notification callbacks */
	public synchronized void registerNotifications(
			CWPControlNotification notify, Handler handler) {
		Log.d(TAG, "registerNotifications()");

		this.notify = notify;
		notifyHandler = handler;

		/* Submit initial state to caller */
		notifyStateChange();
		notifyMorseUpdates();
	}

	/** Called when need to send stateChange notifications to activity */
	private void notifyStateChange() {
		int state = CWPControlNotification.STATE_DOWN;
		CWPControlNotification notify;
		Handler handler;

		synchronized (this) {
			if (recvStateUp && sendStateUp)
				state = CWPControlNotification.STATE_DOUBLE_UP;
			else if (recvStateUp || sendStateUp)
				state = CWPControlNotification.STATE_UP;

			notify = this.notify;
			handler = notifyHandler;
		}

		if (notify != null) {
			final int finalState = state;
			final CWPControlNotification notifyFinal = notify;

			/* Might be called from IO-thread, need to dispatch to UI thread */
			handler.post(new Runnable() {
				public void run() {
					notifyFinal.stateChange(finalState);
				}
			});
		}
	}

	/** Called when need to notification of updated morse message to activity */
	private void notifyMorseUpdates() {
		CWPControlNotification notify;
		Handler handler;
		String morse;

		synchronized (this) {
			notify = this.notify;
			handler = notifyHandler;
			morse = getMorseMessage();
		}

		if (notify != null) {
			final String morseFinal = morse;
			final CWPControlNotification notifyFinal = notify;

			/* Might be called from IO-thread, need to dispatch to UI thread */
			handler.post(new Runnable() {
				public void run() {
					notifyFinal.morseUpdated(morseFinal);
				}
			});
		}
	}

	/** Called when sending morse message completes */
	private void notifyMorseMessageComplete() {
		CWPControlNotification notify;
		Handler handler;

		synchronized (this) {
			notify = this.notify;
			handler = notifyHandler;
		}

		if (notify != null) {
			final CWPControlNotification notifyFinal = notify;

			/* Might be called from IO-thread, need to dispatch to UI thread */
			handler.post(new Runnable() {
				public void run() {
					notifyFinal.morseMessageComplete();
				}
			});
		}
	}

	/** Called by MainActivity when touching lamp-image */
	public void setSendingState(boolean setUpState) {
		boolean changed = false;

		synchronized (this) {
			if (sendStateUp != setUpState) {
				sendStateUp = setUpState;
				changed = true;
			}
		}

		/* Notify UI of state change */
		if (changed)
			notifyStateChange();
	}

	/** Returns list of allowed morse characters */
	public static String getAllowedMorseCharacters() {
		return "abcd0123";
	}

	/** Pushes morse message to CWP server */
	public synchronized void sendMorseMessage(String morse) {
		sendingMorseCount = morse.length();
	}
}
