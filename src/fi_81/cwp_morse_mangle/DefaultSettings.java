package fi_81.cwp_morse_mangle;

import android.content.SharedPreferences;

public class DefaultSettings {
	/* Default setup values */
	public static final String HOSTNAME_DEFAULT = "cwp.opimobi.com";
	public static final String HOSTPORT_DEFAULT = "20000";
	public static final String MORSE_SPEED_DEFAULT = "med";
	public static boolean BEEP_DEFAULT = true;
	public static boolean VIBRATOR_DEFAULT = true;

	public static String getHostName(SharedPreferences settings) {
		return settings.getString("hostname", HOSTNAME_DEFAULT);
	}

	public static String getHostPort(SharedPreferences settings) {
		return settings.getString("hostport", HOSTPORT_DEFAULT);
	}

	public static String getMorseSpeed(SharedPreferences settings) {
		return settings.getString("morse_speed", MORSE_SPEED_DEFAULT);
	}
	
	public static boolean getBeep(SharedPreferences settings) {
		return settings.getBoolean("allow_beep", BEEP_DEFAULT);
	}
	
	public static boolean getVibrator(SharedPreferences settings) {
		return settings.getBoolean("allow_vibrator", VIBRATOR_DEFAULT);
	}
}
