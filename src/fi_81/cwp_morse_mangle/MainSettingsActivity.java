package fi_81.cwp_morse_mangle;

import java.util.Arrays;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

public class MainSettingsActivity extends PreferenceActivity {
	private static final String TAG = "MainSettingsActivity";
	
	/* default setup values */
	public static final String HOSTNAME_DEFAULT = "cwp.opimobi.com";
	public static final String HOSTPORT_DEFAULT = "20000";
	public static final String MORSE_SPEED_DEFAULT = "med";

	/* cached references to preference objects */
	private EditTextPreference hostAddr;
	private EditTextPreference hostPort;
	private ListPreference morseSpeed;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "onCreate()");
		
		super.onCreate(savedInstanceState);

		/* load settings.xml for preferences layout */
		addPreferencesFromResource(R.xml.settings);

		hostAddr = (EditTextPreference) findPreference("hostname");
		hostPort = (EditTextPreference) findPreference("hostport");
		morseSpeed = (ListPreference) findPreference("morse_speed");

		/* load saved settings for display */
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

		hostAddr.setSummary(settings.getString("hostname", HOSTNAME_DEFAULT));
		hostPort.setSummary(settings.getString("hostport", HOSTPORT_DEFAULT));
		
		/* convert speed value to speed text */
		String speedValue = settings.getString("morse_speed", MORSE_SPEED_DEFAULT);
		String[] values = getResources().getStringArray(R.array.pref_morse_speed_entryvalues);
		String[] entries = getResources().getStringArray(R.array.pref_morse_speed_entries);
		
		int idx = Arrays.binarySearch(values, speedValue);
		if (idx < 0) {
			/*
			 * Invalid setting value, maybe from old version of Morse Mangle.
			 * Reset to "med".
			 */
			speedValue = "med";
			idx = Arrays.binarySearch(values, speedValue);
			
			morseSpeed.setValueIndex(idx);
		}
		
		morseSpeed.setSummary(entries[idx]);
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
}
