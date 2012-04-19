package fi_81.cwp_morse_mangle;

import java.util.concurrent.atomic.AtomicLong;

import android.os.Debug;
import android.util.Log;

/* Log wrapper with on/off switch and formatting input */
public class EventLog {
	private static final boolean logging = true;
	private static final boolean tracing = true;
	private static final AtomicLong recvSignalTime = new AtomicLong(0);

	/* Tracing dumps to sd-card */
	public static void startTracing() {
		if (!tracing)
			return;

		Debug.startMethodTracing("cwp_morse_mangle");
		Debug.startAllocCounting();
	}

	public static void endTracing() {
		if (!tracing)
			return;

		Debug.stopMethodTracing();
	}

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

	/* Log.[deiw] wrappers */
	public static void d(String tag, String info) {
		if (!logging)
			return;

		Log.d(tag, info);
	}

	public static void i(String tag, String info) {
		if (!logging)
			return;

		Log.i(tag, info);
	}

	public static void w(String tag, String info) {
		if (!logging)
			return;

		Log.w(tag, info);
	}

	public static void e(String tag, String info) {
		if (!logging)
			return;

		Log.e(tag, info);
	}
}
