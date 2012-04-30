package fi_81.cwp_morse_mangle;

import java.math.BigInteger;

import fi_81.cwp_morse_mangle.CWPControlService.CWPControlBinder;
import fi_81.cwp_morse_mangle.CWPControlService.CWPControlNotification;
import fi_81.cwp_morse_mangle.cwp.CWFrequencyChange;
import fi_81.cwp_morse_mangle.morse.MorseCharList;
import fi_81.cwp_morse_mangle.morse.MorseCodec;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TextView.OnEditorActionListener;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";

	/* Service state */
	private boolean serviceBound = false;
	private CWPControlService cwpService;

	/* Morse input */
	private EditText morseEdit;
	private Button morseButton;
	private ProgressBar morseProgress;
	private boolean sendingMorseMessage = false;

	/* Morse output */
	private TextView morseText;

	/* Channel input */
	private EditText channelEdit;
	private long currentChannel = 1;

	/* Visualization variables */
	private ImageView lampImage;
	private boolean touchingLamp = false;

	private Drawable lampImageRed;
	private Drawable lampImageGray;
	private Drawable lampImageGreen;

	private Vibrator vibrator;
	private SinAudioLoop tone;

	/** Visualization of wave state changes */
	private void visualizeStateChange(int state) {
		/* Change appearance of the lamp */
		switch (state) {
		case CWPControlNotification.STATE_DOWN:
			/* Cache lamp drawable for better performance */
			if (lampImageGray == null)
				lampImageGray = getResources().getDrawable(
						R.drawable.gray_circle);

			/* Draw gray lamp */
			lampImage.setImageDrawable(lampImageGray);

			/* End tone and vibration when entering down-state */
			if (vibrator != null)
				vibrator.cancel();
			if (tone != null)
				tone.stop();

			break;

		case CWPControlNotification.STATE_UP:
			/* Cache lamp drawable for better performance */
			if (lampImageGreen == null)
				lampImageGreen = getResources().getDrawable(
						R.drawable.green_circle);

			/* Draw green lamp */
			lampImage.setImageDrawable(lampImageGreen);

			/* Start 'gentle'-tone and vibrate shortly when entering up-state */
			if (vibrator != null)
				vibrator.vibrate(50);
			if (tone != null)
				tone.play();

			break;

		case CWPControlNotification.STATE_DOUBLE_UP:
			/* Cache lamp drawable for better performance */
			if (lampImageRed == null)
				lampImageRed = getResources()
						.getDrawable(R.drawable.red_circle);

			/* Draw red lamp on double-up/collision */
			lampImage.setImageDrawable(lampImageRed);

			/*
			 * Start 'annoying'-tone and vibrate shortly when entering
			 * double-up-state
			 */
			if (vibrator != null)
				vibrator.vibrate(50);
			if (tone != null)
				tone.play();

			break;
		}

		EventLog.endProfRecv(System.currentTimeMillis(), "state-change: ",
				state);
	}

	/** To report touching state to service */
	private void setTouchingState(boolean touching) {
		if (touchingLamp == touching)
			return;

		touchingLamp = touching;

		/* push state change to CWP service */
		if (serviceBound)
			cwpService.setSendingState(touchingLamp);
	}

	/** Called when sending morse message completes */
	private void sendingMorseMessageComplete() {
		if (!sendingMorseMessage)
			return;

		/* Clear morse edit */
		morseEdit.setText("");

		/* Re-enable morse editor */
		morseEdit.setEnabled(true);

		/* Keep button disable (editor is empty) */
		morseButton.setEnabled(false);

		/* Re-enable channel edit */
		channelEdit.setEnabled(true);

		/* Hide spinner */
		morseProgress.setVisibility(View.GONE);

		/* clear send state */
		sendingMorseMessage = false;
	}

	/**
	 * Called when sending morse message state is set on
	 * 
	 * @param messageBeingSend
	 */
	private void sendingMorseMessageBusy(String messageBeingSend) {
		if (sendingMorseMessage)
			return;

		sendingMorseMessage = true;

		/* set message text */
		if (messageBeingSend != null)
			morseEdit.setText(messageBeingSend);

		/* Disable touching */
		setTouchingState(false);

		/* Disable morse button and editor */
		morseButton.setEnabled(false);
		morseEdit.setEnabled(false);

		/* Disable changing channel while sending message */
		channelEdit.setEnabled(false);

		/* Make spinner visible */
		morseProgress.setVisibility(View.VISIBLE);
	}

	/** Called when sending morse message to server */
	private void sendMorseMessage(boolean sendSOS) {
		/* Disable input, visualize spinner */
		sendingMorseMessageBusy(null);

		/* Pass morse message to CWP Service for transfer */
		if (serviceBound) {
			/* Handle SOS specially */
			String message = sendSOS ? Character
					.toString(MorseCharList.SPECIAL_SOS) : morseEdit.getText()
					.toString();

			cwpService.sendMorseMessage(message);
		} else {
			/* should not be here, but lets handle this anyway */
			sendingMorseMessageComplete();
		}
	}

	/** Called when received morse message to server */
	private void updateMorseMessages(String morse) {
		morseText.setText(morse);
	}

	/** Called when CWP service changes frequency */
	private void receivedNewChannelSetting(long freq) {
		/* Tell user about new frequency */
		if (currentChannel != freq) {
			currentChannel = freq;

			/* Set frequency in channel editor */
			channelEdit.setText(Long.toString(freq));

			/* Make small notification of channel change */
			Toast.makeText(
					MainActivity.this,
					getResources().getText(R.string.toast_set_channel_to)
							+ ": " + freq, 2000).show();
		}

		EventLog.endProfRecv(System.currentTimeMillis(), "freq-change: ", freq);
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		EventLog.d(TAG, "onCreate()");

		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		/* Start CWP service */
		startService(new Intent(this, CWPControlService.class));

		/*
		 * Handle touching of lamp
		 */
		lampImage = (ImageView) findViewById(R.id.lamp);

		lampImage.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				/* Ignore lamp when sending morse message */
				if (sendingMorseMessage || !serviceBound)
					return false;

				switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					/* touching */
					EventLog.startProfSend(System.currentTimeMillis(),
							"up-wave");
					setTouchingState(true);
					return true;

				case MotionEvent.ACTION_UP:
				case MotionEvent.ACTION_CANCEL:
					/* end of touch */
					EventLog.startProfSend(System.currentTimeMillis(),
							"down-wave");
					setTouchingState(false);
					return true;
				}

				return false;
			}
		});

		/*
		 * Handle showing morse messages
		 */
		morseText = (TextView) findViewById(R.id.text_morse);
		morseText.setText("");

		/*
		 * Handle of editing morse message
		 */
		morseEdit = (EditText) findViewById(R.id.edit_morse);
		morseButton = (Button) findViewById(R.id.button_send_morse);
		morseProgress = (ProgressBar) findViewById(R.id.progress_morse);

		morseEdit.setEnabled(false);
		morseButton.setEnabled(false);

		/* enable send button when there is some input entered */
		morseEdit.setOnKeyListener(new OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				/* If already sending, do not re-enable button */
				if (sendingMorseMessage)
					return false;

				morseButton
						.setEnabled(morseEdit.getText().toString().length() > 0);
				return false;
			}
		});

		/* send message when button is pressed */
		morseButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				sendMorseMessage(false);
			}
		});

		/* filter invalid morse characters */
		InputFilter filter = new InputFilter() {
			public CharSequence filter(CharSequence source, int start, int end,
					Spanned dest, int dstart, int dend) {
				for (int i = start; i < end; i++)
					if (!MorseCodec.isAllowedMorseCharacter(source.charAt(i)))
						return "";

				return null;
			}
		};
		morseEdit.setFilters(new InputFilter[] { filter });

		/*
		 * Handle frequency/channel change
		 */
		channelEdit = (EditText) findViewById(R.id.edit_channel);
		channelEdit.setEnabled(false);

		if (savedInstanceState != null)
			currentChannel = savedInstanceState.getLong("current_channel");
		channelEdit.setText(Long.toString(currentChannel));

		/* Make sure that channel is in acceptable range */
		channelEdit.setOnEditorActionListener(new OnEditorActionListener() {
			private final BigInteger freqMaxValueNeg = new BigInteger(Long
					.toString(CWFrequencyChange.MAX_FREQ_NEG));

			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				BigInteger bigInput;

				/*
				 * Handle channel input as BigInteger, as -2^32 cannot be
				 * converted to positive signed 32bit integer
				 */
				try {
					bigInput = new BigInteger(channelEdit.getText().toString());
				} catch (NumberFormatException NFE) {
					/*
					 * Should not happen as out input-field in xml is defined to
					 * be positive integer, nether the less, reset to default
					 * channel in case of hickups.
					 */
					bigInput = BigInteger.ONE;
					channelEdit.setText(bigInput.toString());
				}

				/* Limit values to range 1..2^32-1 */
				if (bigInput.compareTo(BigInteger.ONE) < 0) {
					bigInput = BigInteger.ONE;

					channelEdit.setText(bigInput.toString());
				} else if (bigInput.negate().compareTo(freqMaxValueNeg) < 0) {
					bigInput = freqMaxValueNeg.negate();

					channelEdit.setText(bigInput.toString());
				}

				/* Send change to CWP service */
				if (serviceBound)
					cwpService.setFrequency(bigInput.longValue());

				return false;
			}
		});
	}

	@Override
	public void onRestart() {
		EventLog.d(TAG, "onRestart()");

		super.onRestart();
	}

	@Override
	public void onStart() {
		EventLog.d(TAG, "onStart()");

		super.onStart();

		/* Bind to CWP service */
		bindService(new Intent(this, CWPControlService.class),
				cwpServiceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onResume() {
		EventLog.d(TAG, "onResume()");

		/* load saved settings */
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);

		if (DefaultSettings.getVibrator(settings)) {
			/* Vibrator for ultimate morse experience */
			vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		}

		if (DefaultSettings.getBeep(settings)) {
			/* ToneGenerator for audiable signals */
			tone = new SinAudioLoop();
		}

		if (serviceBound) {
			/* Re-enable notifications when coming back to foreground */
			cwpService.registerNotifications(cwpNotifications, new Handler());

			/* Pass current settings to CWP service */
			cwpService.setConfiguration(DefaultSettings.getHostName(settings),
					DefaultSettings.getHostPortInt(settings),
					DefaultSettings.getMorseSpeedMillisec(settings),
					DefaultSettings.getLatencyManagement(settings));

			/* Clear current notification */
			cwpService.clearNotification();
		}

		super.onResume();
	}

	@Override
	public void onPause() {
		EventLog.d(TAG, "onPause()");

		super.onPause();

		/* Disable touching state */
		setTouchingState(false);

		/* Disable notifications when on background */
		if (serviceBound)
			cwpService.registerNotifications(null, null);

		/* Stopping sound and vibrator is absolute must when pausing activity */
		if (tone != null) {
			tone.stop();
			tone.release();
			tone = null;
		}
		if (vibrator != null) {
			vibrator.cancel();
			vibrator = null;
		}

		/* Clear cached images */
		lampImageRed = null;
		lampImageGray = null;
		lampImageGreen = null;

		System.gc();
	}

	@Override
	public void onStop() {
		EventLog.d(TAG, "onStop()");

		super.onStop();

		/* Unbind from the service */
		if (serviceBound) {
			unbindService(cwpServiceConnection);
			serviceBound = false;
			cwpService = null;
		}
	}

	@Override
	public void onDestroy() {
		EventLog.d(TAG, "onDestroy()");

		/* Make sure there is no sound or vibration at exit */
		if (tone != null) {
			tone.stop();
			tone.release();
		}
		if (vibrator != null)
			vibrator.cancel();

		/* Maybe not needed, but clear fields set at onCreate() anyway */
		vibrator = null;
		tone = null;
		lampImageRed = null;
		lampImageGray = null;
		lampImageGreen = null;
		lampImage = null;
		morseEdit = null;
		morseButton = null;
		morseProgress = null;
		channelEdit = null;
		morseText = null;

		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		EventLog.d(TAG, "onSaveInstanceState()");

		outState.putLong("current_channel", currentChannel);

		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		EventLog.d(TAG, "onRestoreInstanceState()");

		currentChannel = savedInstanceState.getLong("current_channel");

		super.onRestoreInstanceState(savedInstanceState);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		EventLog.d(TAG, "onCreateOptionsMenu()");

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		EventLog.d(TAG, "onOptionsItemSelected()");

		/* Handle item selection */
		switch (item.getItemId()) {
		case R.id.menu_settings:
			/* Start settings editor */
			Intent i = new Intent(this, MainSettingsActivity.class);
			startActivity(i);

			return true;

		case R.id.menu_clear_morse:
			/* Clear received messages from CWPService */
			if (serviceBound)
				cwpService.clearMorseMessages();

			return true;

		case R.id.menu_send_sos:
			/* Check if can send SOS now... */
			if (!sendingMorseMessage) {
				sendMorseMessage(true);
			} else {
				Toast.makeText(this, R.string.toast_cannot_send_sos, 2000)
						.show();
			}

			return true;

		case R.id.menu_reset_connection:
			if (serviceBound) {
				/* load saved settings */
				SharedPreferences settings = PreferenceManager
						.getDefaultSharedPreferences(MainActivity.this);

				/* Pass cleared settings to CWP service to close connection */
				cwpService.setConfiguration("", 12345,
						DefaultSettings.getMorseSpeedMillisec(settings),
						DefaultSettings.getLatencyManagement(settings));

				/* Pass current settings to CWP service restore connection */
				cwpService.setConfiguration(
						DefaultSettings.getHostName(settings),
						DefaultSettings.getHostPortInt(settings),
						DefaultSettings.getMorseSpeedMillisec(settings),
						DefaultSettings.getLatencyManagement(settings));
			}

			return true;
		}

		return false;
	}

	/** Callbacks for CWP events */
	private final CWPControlNotification cwpNotifications = new CWPControlNotification() {
		private static final String TAG = "cwpNotifications";

		public void stateChange(int state) {
			if (!serviceBound) {
				EventLog.w(TAG,
						"stateChange() callback while service not bound!");
				return;
			}

			visualizeStateChange(state);
		}

		public void morseUpdated(String morse) {
			if (!serviceBound) {
				EventLog.w(TAG,
						"morseUpdated() callback while service not bound!");
				return;
			}

			updateMorseMessages(morse);
		}

		public void morseMessageSendingState(boolean isComplete,
				String messageBeingSend) {
			if (!serviceBound) {
				EventLog.w(TAG,
						"morseMessageComplete() callback while service not bound!");
				return;
			}

			if (isComplete)
				sendingMorseMessageComplete();
			else
				sendingMorseMessageBusy(messageBeingSend);
		}

		public void frequencyChange(long freq) {
			if (!serviceBound) {
				EventLog.w(TAG,
						"frequencyChange() callback while service not bound!");
				return;
			}

			receivedNewChannelSetting(freq);
		}
	};

	/** Callbacks for service binding, passed to bindService() */
	private final ServiceConnection cwpServiceConnection = new ServiceConnection() {
		private static final String TAG = "cwpServiceConnection";

		public void onServiceConnected(ComponentName name, IBinder service) {
			EventLog.d(TAG, "onServiceConnected()");

			CWPControlBinder binder = (CWPControlBinder) service;

			cwpService = binder.getService();
			serviceBound = true;

			/* load saved settings */
			SharedPreferences settings = PreferenceManager
					.getDefaultSharedPreferences(MainActivity.this);

			/* Enable notifications */
			cwpService.registerNotifications(cwpNotifications, new Handler());

			/* Pass current settings to CWP service */
			cwpService.setConfiguration(DefaultSettings.getHostName(settings),
					DefaultSettings.getHostPortInt(settings),
					DefaultSettings.getMorseSpeedMillisec(settings),
					DefaultSettings.getLatencyManagement(settings));

			/* Clear current notification */
			cwpService.clearNotification();

			/*
			 * Enable GUI objects (morse and channel editors, keep send button
			 * disabled)
			 */
			morseEdit.setEnabled(true);
			channelEdit.setEnabled(true);

			/* Update touching state */
			cwpService.setSendingState(touchingLamp);
		}

		public void onServiceDisconnected(ComponentName name) {
			EventLog.d(TAG, "onServiceDisconnected()");

			serviceBound = false;
			cwpService = null;
		}
	};
}