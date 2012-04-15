package fi_81.cwp_morse_mangle;

import java.math.BigInteger;

import fi_81.cwp_morse_mangle.CWPControlService.CWPControlBinder;
import fi_81.cwp_morse_mangle.CWPControlService.CWPControlNotification;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
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
	private ToneGenerator tone;

	/** Visualization of wave state changes */
	private void visualizeStateChange(int state) {
		Log.d(TAG, String.format("visualizeStateChange(%d)", state));

		/* Change appearance of the lamp */
		switch (state) {
		case CWPControlNotification.STATE_DOWN:
			/* Cache lamp drawable for better performance */
			if (lampImageGray == null)
				lampImageGray = getResources().getDrawable(
						R.drawable.gray_circle);

			/* Draw gray lamp */
			lampImage.setImageDrawable(lampImageGray);

			/* End tone and vibration when wntering down-state */
			if (vibrator != null)
				vibrator.cancel();
			if (tone != null)
				tone.stopTone();

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
				tone.startTone(ToneGenerator.TONE_CDMA_DIAL_TONE_LITE);

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
				tone.startTone(ToneGenerator.TONE_DTMF_1);

			break;
		}
	}

	/** To report touching state to service */
	private void setTouchingState(boolean touching) {
		Log.d(TAG, String.format("setTouchingState(new: %b, old: %b)",
				touching, touchingLamp));

		if (touchingLamp == touching)
			return;

		touchingLamp = touching;

		/* push state change to CWP service */
		if (serviceBound)
			cwpService.setSendingState(touchingLamp);
	}

	/** Called when sending morse message completes */
	private void sendingMorseMessageComplete() {
		Log.d(TAG, "sendingMorseMessageComplete()");

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

	/** Called when sending morse message to server */
	private void sendMorseMessage() {
		Log.d(TAG, "sendMorseMessage()");

		sendingMorseMessage = true;

		/* Disable touching */
		setTouchingState(false);

		/* Disable morse button and editor */
		morseButton.setEnabled(false);
		morseEdit.setEnabled(false);

		/* Disable changing channel while sending message */
		channelEdit.setEnabled(false);

		/* Make spinner visible */
		morseProgress.setVisibility(View.VISIBLE);

		/* Pass morse message to CWP Service for transfer */
		if (serviceBound) {
			cwpService.sendMorseMessage(morseEdit.getText().toString());
		} else {
			/* should not be here, but lets handle this anyway */
			sendingMorseMessageComplete();
		}
	}

	/** Called when CWP service changes frequency */
	private void receivedNewChannelSetting(long freq) {
		Log.d(TAG, String.format(
				"receivedNewChannelSetting(new-freq: %d, old-freq: %d)", freq,
				currentChannel));

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
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate()");

		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		/*
		 * Handle touching of lamp
		 */
		lampImage = (ImageView) findViewById(R.id.lamp);

		lampImage.setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				boolean up = false;

				/* Ignore lamp when sending morse message */
				if (sendingMorseMessage || !serviceBound)
					return false;

				switch (event.getActionMasked()) {
				case MotionEvent.ACTION_DOWN:
					/* touching */
					Log.d(TAG, "onTouch(ACTION_DOWN)");

					setTouchingState(true);
					return true;

				case MotionEvent.ACTION_UP:
					up = true;
				case MotionEvent.ACTION_CANCEL:
					/* end of touch */
					Log.d(TAG, up ? "onTouch(ACTION_UP)"
							: "onTouch(ACTION_CANCEL)");

					setTouchingState(false);
					return true;
				}

				return false;
			}
		});

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
				Log.d(TAG, "onKey()");

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
				sendMorseMessage();
			}
		});

		/* filter invalid morse characters */
		InputFilter filter = new InputFilter() {
			public CharSequence filter(CharSequence source, int start, int end,
					Spanned dest, int dstart, int dend) {
				for (int i = start; i < end; i++)
					if (!CWPControlService.isAllowedMorseCharacter(source
							.charAt(i)))
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

		/* Make sure that channel is in acceptable range */
		channelEdit.setOnEditorActionListener(new OnEditorActionListener() {
			private final BigInteger int32MinValue = new BigInteger(Integer
					.toString(Integer.MIN_VALUE));

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

				/* Limit values to range 1..2^32 */
				if (bigInput.compareTo(BigInteger.ONE) < 0) {
					bigInput = BigInteger.ONE;

					channelEdit.setText(bigInput.toString());
				} else if (bigInput.negate().compareTo(int32MinValue) < 0) {
					bigInput = int32MinValue.negate();

					channelEdit.setText(bigInput.toString());
				}

				/* Send change to CWP service */
				if (serviceBound)
					cwpService.setFrequency(bigInput.longValue());

				return false;
			}
		});

		/* Start CWP service */
		startService(new Intent(this, CWPControlService.class));
	}

	@Override
	public void onRestart() {
		Log.d(TAG, "onRestart()");

		super.onRestart();
	}

	@Override
	public void onStart() {
		Log.d(TAG, "onStart()");

		super.onStart();

		/* Bind to CWP service */
		bindService(new Intent(this, CWPControlService.class),
				cwpServiceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onResume() {
		Log.d(TAG, "onResume()");

		/* load saved settings */
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);

		if (DefaultSettings.getVibrator(settings)) {
			/* Vibrator for ultimate morse experience */
			vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		}

		if (DefaultSettings.getBeep(settings)) {
			/* ToneGenerator for audiable signals */
			tone = new ToneGenerator(AudioManager.STREAM_DTMF,
					ToneGenerator.MAX_VOLUME);
		}

		if (serviceBound) {
			/* Re-enable notifications when coming back to foreground */
			cwpService.registerNotifications(cwpNotifications, new Handler());

			/* Pass current settings to CWP service */
			cwpService.setConfiguration(DefaultSettings.getHostName(settings),
					DefaultSettings.getHostPortInt(settings),
					DefaultSettings.getMorseSpeedMillisec(settings));
		}

		super.onResume();
	}

	@Override
	public void onPause() {
		Log.d(TAG, "onPause()");

		super.onPause();

		/* Disable touching state */
		setTouchingState(false);

		/* Disable notifications when on background */
		if (serviceBound)
			cwpService.registerNotifications(null, null);

		/* Stopping sound and vibrator is absolute must when pausing activity */
		if (tone != null) {
			tone.stopTone();
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
	}

	@Override
	public void onStop() {
		Log.d(TAG, "onStop()");

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
		Log.d(TAG, "onDestroy()");

		/* Make sure there is no sound or vibration at exit */
		if (tone != null)
			tone.stopTone();
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

		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.d(TAG, "onSaveInstanceState()");

		outState.putLong("current_channel", currentChannel);

		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		Log.d(TAG, "onRestoreInstanceState()");

		currentChannel = savedInstanceState.getLong("current_channel");

		super.onRestoreInstanceState(savedInstanceState);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		Log.d(TAG, "onCreateOptionsMenu()");

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.d(TAG, "onOptionsItemSelected()");

		// Handle item selection
		switch (item.getItemId()) {
		case R.id.menu_settings:
			Intent i = new Intent(this, MainSettingsActivity.class);
			startActivity(i);
			return true;
		}

		return false;
	}

	/** Callbacks for CWP events */
	private final CWPControlNotification cwpNotifications = new CWPControlNotification() {
		private static final String TAG = "cwpNotifications";

		@Override
		public void stateChange(int state) {
			Log.d(TAG, String.format("stateChange(%d)", state));

			if (!serviceBound) {
				Log.w(TAG, "stateChange() callback while service not bound!");
				return;
			}

			visualizeStateChange(state);
		}

		@Override
		public void morseUpdated(String morse) {
			Log.d(TAG, String.format("morseUpdated(%s)", morse));

			if (!serviceBound) {
				Log.w(TAG, "morseUpdated() callback while service not bound!");
				return;
			}
		}

		@Override
		public void morseMessageComplete() {
			Log.d(TAG, "morseMessageComplete()");

			if (!serviceBound) {
				Log.w(TAG,
						"morseMessageComplete() callback while service not bound!");
				return;
			}

			sendingMorseMessageComplete();
		}

		@Override
		public void frequencyChange(long freq) {
			Log.d(TAG, String.format("frequencyChange(%d)", freq));

			if (!serviceBound) {
				Log.w(TAG,
						"frequencyChange() callback while service not bound!");
				return;
			}

			receivedNewChannelSetting(freq);
		}
	};

	/** Callbacks for service binding, passed to bindService() */
	private final ServiceConnection cwpServiceConnection = new ServiceConnection() {
		private static final String TAG = "cwpServiceConnection";

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.d(TAG, "onServiceConnected()");

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
					DefaultSettings.getMorseSpeedMillisec(settings));

			/*
			 * Enable GUI objects (morse and channel editors, keep send button
			 * disabled)
			 */
			morseEdit.setEnabled(true);
			channelEdit.setEnabled(true);

			/* Update touching state */
			cwpService.setSendingState(touchingLamp);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "onServiceDisconnected()");

			serviceBound = false;
			cwpService = null;
		}
	};
}