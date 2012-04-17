package fi_81.cwp_morse_mangle;

import fi_81.cwp_morse_mangle.morse.BitString;
import fi_81.cwp_morse_mangle.morse.MorseCharList;
import fi_81.cwp_morse_mangle.morse.MorseCodec;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

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
	private NotificationManager notifyManager;

	/* Threading */
	private CWPControlThread ioThread;

	/* Callbacks to MainActivity */
	private CWPControlNotification notify = null;
	private Handler notifyHandler = null;

	/* Local process binder with getter of service object */
	public class CWPControlBinder extends Binder {
		private static final String TAG = "CWPControlBinder";

		public CWPControlService getService() {
			LogF.d(TAG, "getService()");

			return CWPControlService.this;
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		LogF.d(TAG, "onBind()");

		return binder;
	}

	@Override
	public void onCreate() {
		LogF.d(TAG, "onCreate()");

		/* Get notification manager */
		notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		/* Start IO-thread */
		ioThread = new CWPControlThread(this);
		ioThread.start();

		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		LogF.d(TAG, "onStartCommand()");

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		LogF.d(TAG, "onUnbind()");

		/* clear notifications to prevent calling unloaded activity */
		notify = null;

		return super.onUnbind(intent);
	}

	@Override
	public void onRebind(Intent intent) {
		LogF.d(TAG, "onRebind()");

		super.onRebind(intent);
	}

	@Override
	public void onDestroy() {
		LogF.d(TAG, "onDestroy()");

		/* Stop IO-thread */
		ioThread.endWorkAndJoin();
		ioThread = null;

		/* Clear notifications */
		notifyManager.cancel(R.string.app_name);
		notifyManager = null;

		super.onDestroy();
	}

	/**
	 * Send notification to system when receiving event but MainActivity is not
	 * active
	 */
	private void sendNotification() {
		/*
		 * TODO: maybe make notification only activate on new decoded morse
		 * messages?
		 * 
		 * or better, make it so that user can change preference of
		 * notification: "Notifications: Off, On morse message, On signal."
		 */
		CharSequence notificationTitle = getText(R.string.notification_received_signal_title);

		Notification notification = new Notification(R.drawable.ic_mangle,
				notificationTitle, System.currentTimeMillis());

		PendingIntent intent = PendingIntent.getActivity(this, 0, new Intent(
				this, MainActivity.class), 0);

		notification.setLatestEventInfo(this, notificationTitle,
				getText(R.string.notification_received_signal_text_wave),
				intent);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;

		notifyManager.notify(R.string.app_name, notification);
	}
	
	/** Request from MainActivity to clear notification */
	public void clearNotification() {
		notifyManager.cancel(R.string.app_name);
	}

	/** Registers notification callbacks */
	public synchronized void registerNotifications(
			CWPControlNotification notify, Handler handler) {
		LogF.d(TAG, "registerNotifications()");

		this.notify = notify;
		notifyHandler = handler;

		/* Submit initial state to caller */
		ioThread.requestCurrentState();
	}

	/** Returns notifier */
	private synchronized CWPControlNotification getClientNotifier() {
		return notify;
	}

	/** Returns handler */
	private synchronized Handler getClientHandler() {
		return notifyHandler;
	}

	/** Called when need to send stateChange notifications to activity */
	public void notifyStateChange(boolean recvStateUp, boolean sendStateUp) {
		Handler handler = getClientHandler();

		int state = CWPControlNotification.STATE_DOWN;
		if (recvStateUp && sendStateUp)
			state = CWPControlNotification.STATE_DOUBLE_UP;
		else if (recvStateUp || sendStateUp)
			state = CWPControlNotification.STATE_UP;

		if (handler != null) {
			final int finalState = state;
			final boolean finalRecvUp = recvStateUp;

			/* Might be called from IO-thread, need to dispatch to UI thread */
			handler.post(new Runnable() {
				public void run() {
					CWPControlNotification notify = getClientNotifier();

					/*
					 * Push state change to MainActivity and if activity is not
					 * active, push notification if received wave state is up.
					 */
					if (notify != null)
						notify.stateChange(finalState);
					else if (finalRecvUp)
						sendNotification();
				}
			});
		} else if (recvStateUp) {
			/*
			 * MainActivity not available, push notification since state change
			 * was up.
			 */
			sendNotification();
		}
	}

	/** Called when need to notification of updated morse message to activity */
	public void notifyMorseUpdates(String morse) {
		Handler handler = getClientHandler();

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
	public void notifyMorseMessageComplete() {
		Handler handler = getClientHandler();

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
	public void notifyFrequencyChange(long freq) {
		Handler handler = getClientHandler();

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

	/** New configuration for CWP service */
	public void setConfiguration(String hostName, int hostPort, int morseSpeed) {
		ioThread.setNewConfiguration(hostName, hostPort, morseSpeed);
	}

	/** Called by MainActivity when touching lamp-image */
	public void setSendingState(boolean setUpState) {
		LogF.d(TAG, "setSendingState: %b", setUpState);
		ioThread.setSendingState(setUpState);
	}

	/** Pushes morse message to CWP server */
	public void sendMorseMessage(String message) {
		String morseMessage = MorseCharList.SPECIAL_START_OF_MESSAGE + message
				+ MorseCharList.SPECIAL_END_OF_CONTACT;
		BitString morseBits = MorseCodec.encodeMessageToMorse(morseMessage);

		ioThread.sendMorseMessage(morseBits);
	}

	/** Pushed frequency change to CWP server */
	public void setFrequency(long freq) {
		ioThread.setFrequency(freq);
	}

	/** Clear received morse messages */
	public void clearMorseMessages() {
		ioThread.requestClearMessages();
	}
}
