package fi_81.cwp_morse_mangle;

import android.util.Log;

/* Log wrapper with on/off switch and formatting input */
public class EventLog {
	private static final boolean logging = true;

	private static String getFormattedString(String fmt, Object... args) {
		if (args.length == 0)
			return fmt;

		return String.format(fmt, args);
	}

	public static void d(String tag, String fmt, Object... args) {
		if (!logging)
			return;

		Log.d(tag, getFormattedString(fmt, args));
	}

	public static void i(String tag, String fmt, Object... args) {
		if (!logging)
			return;

		Log.i(tag, getFormattedString(fmt, args));
	}

	public static void w(String tag, String fmt, Object... args) {
		if (!logging)
			return;

		Log.w(tag, getFormattedString(fmt, args));
	}

	public static void e(String tag, String fmt, Object... args) {
		if (!logging)
			return;

		Log.e(tag, getFormattedString(fmt, args));
	}
}
