package fi_81.cwp_morse_mangle;

import java.util.concurrent.atomic.AtomicLong;

import android.os.Debug;
import android.util.Log;

/* Log wrapper with on/off switch and formatting input */
public class EventLog {
	private static final boolean logging = false;
	private static final boolean profiling = true;
	private static final boolean tracing = false;
	private static final AtomicLong recvSignalTime = new AtomicLong(0);
	private static final AtomicLong sendSignalTime = new AtomicLong(0);

	/* Tracing dumps to sd-card */
	public static void startTracing() {
		if (tracing)
			Debug.startMethodTracing("cwp_morse_mangle");

		if (profiling)
			Debug.startAllocCounting();
	}

	public static void endTracing() {
		if (tracing)
			Debug.stopMethodTracing();
	}

	/*
	 * Performance profiling for received signals.
	 * 
	 * Note: this is racy, works only when intervals of signals are ~50ms more
	 */
	public static void startProfRecv(long timeReceived) {
		if (profiling) {
			recvSignalTime.set(timeReceived);
			int allocs = Debug.getThreadAllocCount();
			int size = Debug.getThreadAllocSize();

			Log.d("profiler",
					"received signal from network. Memory (used/allocs): "
							+ size + "/" + allocs);
		}
	}

	public static void endProgRecv(long timeProcessed, String info) {
		if (profiling) {
			long recvTime = recvSignalTime.getAndSet(0);
			if (recvTime == 0)
				return;

			long duration = timeProcessed - recvTime;
			int allocs = Debug.getThreadAllocCount();
			int size = Debug.getThreadAllocSize();

			Log.d("profiler",
					"duration receiving signal from network to handling: "
							+ duration + " ms (" + info
							+ "). Memory (used/allocs): " + size + "/" + allocs);
		}
	}

	public static void startProfSend(long timeReceived, String info) {
		if (logging) {
			sendSignalTime.set(timeReceived);
			int allocs = Debug.getThreadAllocCount();
			int size = Debug.getThreadAllocSize();

			Log.d("profiler", "sending signal (" + info
					+ "). Memory (used/allocs): " + size + "/" + allocs);
		}
	}

	public static void endProgSend(long timeProcessed) {
		if (logging) {
			long sendTime = sendSignalTime.getAndSet(0);
			if (sendTime == 0)
				return;

			long duration = timeProcessed - sendTime;
			int allocs = Debug.getThreadAllocCount();
			int size = Debug.getThreadAllocSize();

			Log.d("profiler", "duration from sending signal to network: "
					+ duration + " ms. Memory (used/allocs): " + size + "/"
					+ allocs);
		}
	}

	/* Log.[deiw] wrappers */
	public static void d(String tag, String info) {
		if (logging)
			Log.d(tag, info);
	}

	public static void i(String tag, String info) {
		if (logging)
			Log.i(tag, info);
	}

	public static void w(String tag, String info) {
		if (logging)
			Log.w(tag, info);
	}

	public static void e(String tag, String info) {
		if (logging)
			Log.e(tag, info);
	}
}
