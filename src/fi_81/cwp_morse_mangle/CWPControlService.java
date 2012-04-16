package fi_81.cwp_morse_mangle;

import java.util.Arrays;

import fi_81.cwp_morse_mangle.morse.MorseCharList;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class CWPControlService extends Service {
	/** Callbacks for MainActivity */
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

		/**
		 * Called when CWPService received frequency change message.
		 */
		public abstract void frequencyChange(long freq);
	}

	private static final String TAG = "CWPControlService";
	private final IBinder binder = new CWPControlBinder();

	/* Threading */
	private CWPControlThread ioThread;

	/* Callbacks to MainActivity */
	private CWPControlNotification notify = null;
	private Handler notifyHandler = null;

	/* Send and receive channel states */
	private boolean recvStateUp = false;
	private boolean sendStateUp = false;
	private long frequency = 1;

	/* Received morse string */
	private String morseMessage = "";

	private String getMorseMessage() {
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

	@Override
	public IBinder onBind(Intent arg0) {
		Log.d(TAG, "onBind()");

		return binder;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate()");

		/* Start IO-thread */
		ioThread = new CWPControlThread(this);
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

		ioThread.endWorkAndJoin();
		ioThread = null;

		super.onDestroy();
	}

	/** New configuration for CWP service */
	public void setConfiguration(String hostName, int hostPort, int morseSpeed) {
		ioThread.setNewConfiguration(hostName, hostPort, morseSpeed);
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
		notifyFrequencyChange();
	}

	/** Returns notifier */
	private synchronized CWPControlNotification getClientNotifier() {
		return notify;
	}

	/** Called when need to send stateChange notifications to activity */
	private void notifyStateChange() {
		int state = CWPControlNotification.STATE_DOWN;
		Handler handler;

		/* In IO-thread context */
		synchronized (this) {
			if (recvStateUp && sendStateUp)
				state = CWPControlNotification.STATE_DOUBLE_UP;
			else if (recvStateUp || sendStateUp)
				state = CWPControlNotification.STATE_UP;

			handler = notifyHandler;
		}

		if (handler != null) {
			final int finalState = state;

			/* Might be called from IO-thread, need to dispatch to UI thread */
			handler.post(new Runnable() {
				public void run() {
					CWPControlNotification notify = getClientNotifier();

					if (notify != null)
						notify.stateChange(finalState);
				}
			});
		}
	}

	/** Called when need to notification of updated morse message to activity */
	private void notifyMorseUpdates() {
		Handler handler;
		String morse;

		/* In IO-thread context */
		synchronized (this) {
			handler = notifyHandler;
			morse = getMorseMessage();
		}

		if (handler != null) {
			final String morseFinal = morse;

			/* Might be called from IO-thread, need to dispatch to UI thread */
			handler.post(new Runnable() {
				public void run() {
					CWPControlNotification notify = getClientNotifier();

					if (notify != null)
						notify.morseUpdated(morseFinal);
				}
			});
		}
	}

	/** Called when sending morse message completes */
	private void notifyMorseMessageComplete() {
		Handler handler;

		/* In IO-thread context */
		synchronized (this) {
			handler = notifyHandler;
		}

		if (handler != null) {
			/* Might be called from IO-thread, need to dispatch to UI thread */
			handler.post(new Runnable() {
				public void run() {
					CWPControlNotification notify = getClientNotifier();

					if (notify != null)
						notify.morseMessageComplete();
				}
			});
		}
	}

	/** Called when received frequency change message */
	private void notifyFrequencyChange() {
		Handler handler;
		long freq;

		/* In IO-thread context */
		synchronized (this) {
			handler = notifyHandler;
			freq = frequency;
		}

		if (handler != null) {
			final long freqFinal = freq;

			/* Might be called from IO-thread, need to dispatch to UI thread */
			handler.post(new Runnable() {
				public void run() {
					CWPControlNotification notify = getClientNotifier();

					if (notify != null)
						notify.frequencyChange(freqFinal);
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

	/** Pushes morse message to CWP server */
	public void sendMorseMessage(String morse) {
	}

	/** Pushed frequency change to CWP server */
	public void setFrequency(long freq) {
		if (freq == frequency)
			return;

		frequency = freq;

		notifyFrequencyChange();
	}

	/** Checks if character is allowed for morse message */
	public static boolean isAllowedMorseCharacter(char ch) {
		char array[] = MorseCharList.getAllowedCharacters();

		return Arrays.binarySearch(array, ch) >= 0;
	}
}
