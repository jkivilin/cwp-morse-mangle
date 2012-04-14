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

	/* Callbacks to MainActivity */
	private CWPControlNotification notify = null;
	private Handler notifyHandler = null;

	/* Send and receive channel states */
	private boolean recvStateUp = false;
	private boolean sendStateUp = false;

	/* Received morse string */
	private String morseMessage = "";

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
	}

	@Override
	public IBinder onBind(Intent arg0) {
		Log.d(TAG, "onBind()");

		return binder;
	}

	@Override
	public void onCreate() {
		Log.d(TAG, "onCreate()");

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

		super.onDestroy();
	}

	/** Registers notification callbacks */
	public void registerNotifications(CWPControlNotification notify,
			Handler handler) {
		Log.d(TAG, "registerNotifications()");

		this.notify = notify;
		notifyHandler = handler;

		/* Submit initial state to caller */
		notifyStateChange();
		notifyMorseUpdates();
	}

	/** Called when need to send stateChange notifications to activity */
	private void notifyStateChange() {
		if (notify != null) {
			int state;

			synchronized (this) {
				state = CWPControlNotification.STATE_DOWN;

				if (recvStateUp && sendStateUp)
					state = CWPControlNotification.STATE_DOUBLE_UP;
				else if (recvStateUp || sendStateUp)
					state = CWPControlNotification.STATE_UP;
			}

			/* Might be called from IO-thread, need to dispatch to UI thread */
			final int finalState = state;
			notifyHandler.post(new Runnable() {
				public void run() {
					notify.stateChange(finalState);
				}
			});
		}
	}

	/** Called when need to notification of updated morse message to activity */
	private void notifyMorseUpdates() {
		if (notify != null) {
			final String morse = getMorseMessage();

			/* Might be called from IO-thread, need to dispatch to UI thread */
			notifyHandler.post(new Runnable() {
				public void run() {
					notify.morseUpdated(morse);
				}
			});
		}
	}

	/** Called by MainActivity when touching lamp-image */
	public synchronized void setSendingState(boolean setUpState) {
		if (sendStateUp != setUpState) {
			sendStateUp = setUpState;
			
			/* Notify UI of state change */
			notifyStateChange();
		}
	}
}
