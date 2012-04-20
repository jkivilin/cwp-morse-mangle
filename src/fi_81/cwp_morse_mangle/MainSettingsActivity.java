package fi_81.cwp_morse_mangle;

import java.util.Arrays;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class MainSettingsActivity extends PreferenceActivity implements
		OnPreferenceChangeListener {
	private static final String TAG = "MainSettingsActivity";

	/* cached references to preference objects */
	private EditTextPreference hostAddr;
	private EditTextPreference hostPort;
	private ListPreference morseSpeed;
	private CheckBoxPreference allowBeep;
	private CheckBoxPreference allowVibrator;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		EventLog.d(TAG, "onCreate()");

		super.onCreate(savedInstanceState);

		/* load settings.xml for preferences layout */
		addPreferencesFromResource(R.xml.settings);

		hostAddr = (EditTextPreference) findPreference("hostname");
		hostPort = (EditTextPreference) findPreference("hostport");
		morseSpeed = (ListPreference) findPreference("morse_speed");
		allowBeep = (CheckBoxPreference) findPreference("allow_beep");
		allowVibrator = (CheckBoxPreference) findPreference("allow_vibrator");

		hostAddr.setOnPreferenceChangeListener(this);
		hostPort.setOnPreferenceChangeListener(this);
		morseSpeed.setOnPreferenceChangeListener(this);

		/* load saved settings for display */
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);

		/* Force default settings in */
		hostAddr.setText(DefaultSettings.getHostName(settings));
		hostPort.setText(DefaultSettings.getHostPort(settings));

		allowBeep.setChecked(DefaultSettings.getBeep(settings));
		allowVibrator.setChecked(DefaultSettings.getVibrator(settings));

		hostPort.setSummary(DefaultSettings.getHostPort(settings));
		hostAddr.setSummary(DefaultSettings.getHostName(settings));

		String speedValue = DefaultSettings.getMorseSpeed(settings);

		/* convert speed value to speed text */
		int idx = getMorseSpeedIndex(speedValue);
		String speedText = getMorseSpeedSummary(idx, speedValue);

		morseSpeed.setValueIndex(idx);
		morseSpeed.setSummary(speedText);
	}

	private String getMorseSpeedSummary(int idx, String value) {
		String[] entries = getResources().getStringArray(
				R.array.pref_morse_speed_entries);

		return entries[idx] + " ("
				+ DefaultSettings.getMorseSpeedMillisec(value) + " ms)";
	}

	private int getMorseSpeedIndex(String speedValue) {
		String[] values = getResources().getStringArray(
				R.array.pref_morse_speed_entryvalues);

		int idx = Arrays.binarySearch(values, speedValue);
		if (idx < 0) {
			/*
			 * Invalid setting value, maybe from old version of Morse Mangle.
			 * Reset to "med".
			 */
			idx = Arrays.binarySearch(values, speedValue);
		}

		return idx;
	}

	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if (preference == hostAddr) {
			String value = (String) newValue;

			/*
			 * Can't really say anything about the address at this stage, allow
			 * it
			 */
			hostAddr.setSummary(value);

			return true;
		} else if (preference == hostPort) {
			String value = (String) newValue;
			int port = -1;

			/* Verify that hostPort is valid number for IP-port */
			try {
				port = Integer.parseInt(value);
			} catch (NumberFormatException NFE) {
			}

			/* Do not allow invalid value outside range 0..0xffff */
			if (port < 1 || port > 0xffff)
				return false;

			/* allow this value */
			hostPort.setSummary(value);

			return true;
		} else if (preference == morseSpeed) {
			/*
			 * ListPreference should prevent invalid newValues, just update
			 * summary
			 */
			String value = (String) newValue;
			int idx = getMorseSpeedIndex(value);

			morseSpeed.setSummary(getMorseSpeedSummary(idx, value));

			return true;
		}

		return false;
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
	}

	@Override
	public void onResume() {
		EventLog.d(TAG, "onResume()");

		super.onResume();
	}

	@Override
	public void onPause() {
		EventLog.d(TAG, "onPause()");

		super.onPause();
	}

	@Override
	public void onStop() {
		EventLog.d(TAG, "onStop()");

		super.onStop();
	}

	@Override
	public void onDestroy() {
		EventLog.d(TAG, "onDestroy()");

		/* cleanup */
		hostAddr = null;
		hostPort = null;
		morseSpeed = null;
		allowBeep = null;
		allowVibrator = null;

		super.onDestroy();
	}
}
