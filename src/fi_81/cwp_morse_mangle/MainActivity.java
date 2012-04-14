package fi_81.cwp_morse_mangle;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class MainActivity extends Activity {
	private static final String TAG = "MainActivity";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate()");

		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);
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
}