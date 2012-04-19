package fi_81.cwp_morse_mangle;

import java.util.concurrent.atomic.AtomicLong;

import android.util.Log;

/* Log wrapper with on/off switch and formatting input */
public class EventLog {
	private static final boolean logging = true;
	private static final AtomicLong recvSignalTime = new AtomicLong(0);

	/*
	 * Performance profiling for received signals.
	 * 
	 * Note: this is racy, works only when intervals of signals are ~50ms more
	 */
	public static void startProfRecv(long timeReceived) {
		if (!logging)
			return;

		recvSignalTime.set(timeReceived);

		Log.d("profiler", "received signal from server");
	}

	public static void endProgRecv(long timeProcessed, String info) {
		if (!logging)
			return;

		long duration = timeProcessed - recvSignalTime.get();

		Log.d("profiler", "duration from receiving signal to handling: "
				+ duration + " ms. (" + info + ')');
	}

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
