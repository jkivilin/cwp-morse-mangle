package fi_81.cwp_morse_mangle;

import java.util.ArrayDeque;
import java.util.NoSuchElementException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Debug;
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
		 * Called when CWPService morse message sending state changes.
		 */
		abstract public void morseMessageSendingState(boolean isSending,
				String messageBeingSend);

		/**
		 * Called when CWPService received frequency change message.
		 */
		public abstract void frequencyChange(long freq);
	}

	private static final String TAG = "CWPControlService";
	private final IBinder binder = new CWPControlBinder();

	/* Notifications */
	private NotificationManager notifyManager;
	private boolean showingNotification = false;

	/* Threading */
	private CWPControlThread ioThread;

	/* Callbacks to MainActivity */
	private CWPControlNotification notify = null;
	private Handler notifyHandler = null;

	/* Local process binder with getter of service object */
	public class CWPControlBinder extends Binder {
		private static final String TAG = "CWPControlBinder";

		public CWPControlService getService() {
			EventLog.d(TAG, "getService()");

			return CWPControlService.this;
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		EventLog.d(TAG, "onBind()");

		return binder;
	}

	@Override
	public void onCreate() {
		EventLog.d(TAG, "onCreate()");

		/*
		 * Have some initial value in profiler (CWPControlService sends initial
		 * signals even without connection)
		 */
		EventLog.startProfRecv(System.currentTimeMillis());

		/* Get notification manager */
		notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		/* Start IO-thread */
		ioThread = new CWPControlThread(this);
		ioThread.start();

		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		EventLog.d(TAG, "onStartCommand()");

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		EventLog.d(TAG, "onUnbind()");

		/* clear notifications to prevent calling unloaded activity */
		clearNotification();
		notify = null;

		return super.onUnbind(intent);
	}

	@Override
	public void onRebind(Intent intent) {
		EventLog.d(TAG, "onRebind()");

		super.onRebind(intent);
	}

	@Override
	public void onDestroy() {
		EventLog.d(TAG, "onDestroy()");

		/* Stop IO-thread */
		ioThread.endWorkAndJoin();
		ioThread = null;

		/* Clear notifications */
		notifyManager.cancel(R.string.app_name);
		notifyManager = null;

		/* Stop tracing */
		Debug.startMethodTracing();

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
		if (showingNotification)
			return;

		showingNotification = true;

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
		showingNotification = false;
	}

	/** Registers notification callbacks */
	public synchronized void registerNotifications(
			CWPControlNotification notify, Handler handler) {
		EventLog.d(TAG, "registerNotifications()");

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

	/** Runnable class for passing messages from IO-thread to UI-thread */
	private static class CWPRunnable implements Runnable {
		public static final int TYPE_STATE_CHANGE = 0;
		public static final int TYPE_FREQUENCY_CHANGE = 1;
		public static final int TYPE_MORSE_MESSAGE_UPDATE = 2;
		public static final int TYPE_MORSE_SENDING_STATE = 3;

		private int type;
		private boolean argBool0;
		private String argStr0;
		private long argLong0;
		private CWPControlService parent;

		public CWPRunnable(CWPControlService service) {
			parent = service;
			type = 0;
			argBool0 = false;
			argStr0 = null;
			argLong0 = 0;
		}

		public void run() {
			CWPControlNotification notify = parent.getClientNotifier();

			switch (type) {
			case TYPE_STATE_CHANGE:
				int state = (int) argLong0;
				boolean recvStateUp = argBool0;

				/*
				 * Push state change to MainActivity and if activity is not
				 * active, push notification if received wave state is up.
				 */
				if (notify != null)
					notify.stateChange(state);
				else if (recvStateUp)
					parent.sendNotification();

				break;

			case TYPE_FREQUENCY_CHANGE:
				long freq = argLong0;

				if (notify != null)
					notify.frequencyChange(freq);

				break;

			case TYPE_MORSE_MESSAGE_UPDATE:
				String morseMessage = argStr0;

				if (notify != null)
					notify.morseUpdated(morseMessage);

				break;

			case TYPE_MORSE_SENDING_STATE:
				boolean isComplete = argBool0;
				String sendMorse = argStr0;

				if (notify != null)
					notify.morseMessageSendingState(isComplete, sendMorse);

				break;
			}

			CWPControlService service = parent;

			/* Clear references before passing to memory pool */
			parent = null;
			argStr0 = null;

			/* Runnable is now free for reuse */
			service.pushToMemoryPool(this);
		}
	};

	/** Memory pool for CWPRunnable */
	private final int CWPRUNNABLE_MEMPOOL_MAX_SIZE = 16;
	private final ArrayDeque<CWPRunnable> freeQueue = new ArrayDeque<CWPRunnable>();

	/** Memory pool allocator for CWPRunnable */
	private CWPRunnable popFromMemoryPool() {
		synchronized (freeQueue) {
			if (freeQueue.size() == 0)
				return new CWPRunnable(this);

			try {
				CWPRunnable run = freeQueue.pop();
				return run;
			} catch (NoSuchElementException NSEE) {
				return new CWPRunnable(this);
			}
		}
	}

	/** Pushes unused CWPRunnable to freeQueue */
	private boolean pushToMemoryPool(CWPRunnable unusedRunnable) {
		synchronized (freeQueue) {
			if (freeQueue.size() >= CWPRUNNABLE_MEMPOOL_MAX_SIZE)
				return false;

			freeQueue.push(unusedRunnable);
			return true;
		}
	}

	/** CWPRunnable allocator from mem-pool */
	private CWPRunnable newCWPRunnableFromMemoryPool(boolean recvStateUp,
			int state) {
		CWPRunnable run = popFromMemoryPool();

		run.type = CWPRunnable.TYPE_STATE_CHANGE;
		run.parent = this;
		run.argBool0 = recvStateUp;
		run.argLong0 = state;
		run.argStr0 = null;

		return run;
	}

	/** CWPRunnable allocator from mem-pool */
	private CWPRunnable newCWPRunnableFromMemoryPool(boolean isComplete,
			String sendMorse) {
		CWPRunnable run = popFromMemoryPool();

		run.type = CWPRunnable.TYPE_MORSE_SENDING_STATE;
		run.parent = this;
		run.argBool0 = isComplete;
		run.argStr0 = sendMorse;
		run.argLong0 = 0;

		return run;
	}

	/** CWPRunnable allocator from mem-pool */
	private CWPRunnable newCWPRunnableFromMemoryPool(long freq) {
		CWPRunnable run = popFromMemoryPool();

		run.type = CWPRunnable.TYPE_FREQUENCY_CHANGE;
		run.parent = this;
		run.argBool0 = false;
		run.argStr0 = null;
		run.argLong0 = freq;

		return run;
	}

	/** CWPRunnable allocator from mem-pool */
	private CWPRunnable newCWPRunnableFromMemoryPool(String morseMessage) {
		CWPRunnable run = popFromMemoryPool();

		run.type = CWPRunnable.TYPE_MORSE_MESSAGE_UPDATE;
		run.parent = this;
		run.argBool0 = false;
		run.argStr0 = morseMessage;
		run.argLong0 = 0;

		return run;
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
			CWPRunnable run = newCWPRunnableFromMemoryPool(recvStateUp, state);

			/* Might be called from IO-thread, need to dispatch to UI thread */
			if (!handler.post(run))
				pushToMemoryPool(run);
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
			CWPRunnable run = newCWPRunnableFromMemoryPool(morse);

			/* Might be called from IO-thread, need to dispatch to UI thread */
			if (!handler.post(run))
				pushToMemoryPool(run);
		}
	}

	/** Called when sending morse message completes */
	public void notifyMorseMessageSendingState(boolean complete,
			String sendMorse) {
		Handler handler = getClientHandler();

		if (handler != null) {
			CWPRunnable run = newCWPRunnableFromMemoryPool(complete, sendMorse);

			/* Might be called from IO-thread, need to dispatch to UI thread */
			if (!handler.post(run))
				pushToMemoryPool(run);
		}
	}

	/** Called when received frequency change message */
	public void notifyFrequencyChange(long freq) {
		Handler handler = getClientHandler();

		if (handler != null) {
			CWPRunnable run = newCWPRunnableFromMemoryPool(freq);

			/* Might be called from IO-thread, need to dispatch to UI thread */
			if (!handler.post(run))
				pushToMemoryPool(run);
		}
	}

	/** New configuration for CWP service */
	public void setConfiguration(String hostName, int hostPort, int morseSpeed) {
		ioThread.setNewConfiguration(hostName, hostPort, morseSpeed);
	}

	/** Called by MainActivity when touching lamp-image */
	public void setSendingState(boolean setUpState) {
		EventLog.d(TAG, "setSendingState: %b", setUpState);
		ioThread.setSendingState(setUpState);
	}

	/** Pushes morse message to CWP server */
	public void sendMorseMessage(String message) {
		ioThread.sendMorseMessage(message);
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
