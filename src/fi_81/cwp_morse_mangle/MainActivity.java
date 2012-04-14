package fi_81.cwp_morse_mangle;

import fi_81.cwp_morse_mangle.CWPControlService.CWPControlBinder;
import fi_81.cwp_morse_mangle.CWPControlService.CWPControlNotification;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";

	/* Service state */
	private boolean serviceBound = false;
	private CWPControlService cwpService;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate()");

		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

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

		super.onResume();
	}

	@Override
	public void onPause() {
		Log.d(TAG, "onPause()");

		super.onPause();
	}

	@Override
	public void onStop() {
		Log.d(TAG, "onStop()");

		super.onStop();

		/* Unbind from the service */
		if (serviceBound) {
			unbindService(cwpServiceConnection);
			serviceBound = false;
		}
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy()");

		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.d(TAG, "onSaveInstanceState()");

		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		Log.d(TAG, "onRestoreInstanceState()");

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
			Log.d(TAG, "stateChange(" + state + ")");
		}

		@Override
		public void morseUpdated(String morse) {
			Log.d(TAG, "receivedMorseMessage(" + morse + ")");
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

			cwpService.registerNotifications(cwpNotifications, new Handler());
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.d(TAG, "onServiceDisconnected()");

			serviceBound = false;
		}
	};
}