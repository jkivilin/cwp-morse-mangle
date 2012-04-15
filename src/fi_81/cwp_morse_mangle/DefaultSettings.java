package fi_81.cwp_morse_mangle;

import android.content.SharedPreferences;

public class DefaultSettings {
	/* Default setup values */
	public static final String HOSTNAME_DEFAULT = "cwp.opimobi.com";
	public static final String HOSTPORT_DEFAULT = "20000";
	public static final String MORSE_SPEED_DEFAULT = "med";
	public static boolean BEEP_DEFAULT = true;
	public static boolean VIBRATOR_DEFAULT = true;

	/* Milliseconds values for morse speed */
	public static int MORSE_SPEED_FAST = 10;
	public static int MORSE_SPEED_MED = 100;
	public static int MORSE_SPEED_SLOW = 200;

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

	public static int getHostPortInt(SharedPreferences settings) {
		try {
			return Integer.parseInt(getHostPort(settings));
		} catch (NumberFormatException NFE) {
			return Integer.parseInt(HOSTPORT_DEFAULT);
		}
	}

	public static int getMorseSpeedMillisec(SharedPreferences settings) {
		String value = getMorseSpeed(settings);
		int millisec = MORSE_SPEED_MED;

		if (value.compareTo("slow") == 0)
			millisec = MORSE_SPEED_SLOW;
		else if (value.compareTo("med") == 0)
			millisec = MORSE_SPEED_MED;
		else if (value.compareTo("fast") == 0)
			millisec = MORSE_SPEED_FAST;

		return millisec;
	}
}
