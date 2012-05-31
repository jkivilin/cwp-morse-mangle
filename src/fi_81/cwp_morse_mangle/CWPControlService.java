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

package fi_81.cwp_morse_mangle;

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

		EventLog.startTracing();

		/*
		 * Have some initial value in profiler (CWPControlService sends initial
		 * signals even without connection)
		 */
		EventLog.startProfRecv(System.currentTimeMillis());

		/* Get notification manager */
		notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		/* Start IO-thread */
		ioThread = new CWPControlThread(this);
		ioThread.setName("CWPControlThread");
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
		EventLog.endTracing();

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
	private class CWPRunnable implements Runnable {
		public static final int TYPE_STATE_CHANGE = 0;
		public static final int TYPE_FREQUENCY_CHANGE = 1;
		public static final int TYPE_MORSE_MESSAGE_UPDATE = 2;
		public static final int TYPE_MORSE_SENDING_STATE = 3;

		private int type;
		private boolean argBool0;
		private String argStr0;
		private long argLong0;

		/*
		 * Constructors for different type. Type selected by input arguments as
		 * they are different for all types.
		 */
		private CWPRunnable(boolean recvStateUp, int state) {
			type = CWPRunnable.TYPE_STATE_CHANGE;
			argBool0 = recvStateUp;
			argLong0 = state;
			argStr0 = null;
		}

		private CWPRunnable(boolean isComplete, String sendMorse) {
			type = CWPRunnable.TYPE_MORSE_SENDING_STATE;
			argBool0 = isComplete;
			argStr0 = sendMorse;
			argLong0 = 0;
		}

		private CWPRunnable(long freq) {
			type = CWPRunnable.TYPE_FREQUENCY_CHANGE;
			argBool0 = false;
			argStr0 = null;
			argLong0 = freq;
		}

		private CWPRunnable(String morseMessage) {
			type = CWPRunnable.TYPE_MORSE_MESSAGE_UPDATE;
			argBool0 = false;
			argStr0 = morseMessage;
			argLong0 = 0;
		}

		public void run() {
			CWPControlNotification notify = CWPControlService.this
					.getClientNotifier();

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
					CWPControlService.this.sendNotification();

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
		}
	};

	/** Called when need to send stateChange notifications to activity */
	public void notifyStateChange(boolean recvStateUp, boolean sendStateUp) {
		Handler handler = getClientHandler();

		int state = CWPControlNotification.STATE_DOWN;
		if (recvStateUp && sendStateUp)
			state = CWPControlNotification.STATE_DOUBLE_UP;
		else if (recvStateUp || sendStateUp)
			state = CWPControlNotification.STATE_UP;

		if (handler != null) {
			CWPRunnable run = new CWPRunnable(recvStateUp, state);

			/*
			 * Might be called from IO-thread, need to dispatch to UI thread.
			 */
			handler.post(run);
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
			CWPRunnable run = new CWPRunnable(morse);

			/*
			 * Might be called from IO-thread, need to dispatch to UI thread.
			 */
			handler.post(run);
		}
	}

	/** Called when sending morse message completes */
	public void notifyMorseMessageSendingState(boolean complete,
			String sendMorse) {
		Handler handler = getClientHandler();

		if (handler != null) {
			CWPRunnable run = new CWPRunnable(complete, sendMorse);

			/*
			 * Might be called from IO-thread, need to dispatch to UI thread.
			 */
			handler.post(run);
		}
	}

	/** Called when received frequency change message */
	public void notifyFrequencyChange(long freq) {
		Handler handler = getClientHandler();

		if (handler != null) {
			CWPRunnable run = new CWPRunnable(freq);

			/*
			 * Might be called from IO-thread, need to dispatch to UI thread.
			 */
			handler.post(run);
		}
	}

	/** New configuration for CWP service */
	public void setConfiguration(String hostName, int hostPort, int morseSpeed,
			boolean useLatencyManagement) {
		ioThread.setNewConfiguration(hostName, hostPort, morseSpeed, useLatencyManagement);
	}

	/** Called by MainActivity when touching lamp-image */
	public void setSendingState(boolean setUpState) {
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
