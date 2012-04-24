package fi_81.cwp_morse_mangle;

import java.util.concurrent.atomic.AtomicLong;

import android.os.Debug;
import android.util.Log;

/* Log wrapper with on/off switch and formatting input */
public class EventLog {
	private static final boolean logging = false;
	private static final boolean profiling = false;
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

			StringBuffer sb = localStringBuffer.get();
			int allocs = Debug.getThreadAllocCount();
			int size = Debug.getThreadAllocSize();

			sb.setLength(0);
			sb.append("received signal from network. Memory (used/allocs): ");
			sb.append(size);
			sb.append('/');
			sb.append(allocs);

			Log.d("profiler", sb.toString());
		}
	}

	public static void endProfRecv(long timeProcessed, String info,
			long infoValue) {
		if (profiling) {
			long recvTime = recvSignalTime.getAndSet(0);
			if (recvTime == 0)
				return;

			StringBuffer sb = localStringBuffer.get();
			long duration = timeProcessed - recvTime;
			int allocs = Debug.getThreadAllocCount();
			int size = Debug.getThreadAllocSize();

			sb.setLength(0);
			sb.append("duration receiving signal from network to handling: ");
			sb.append(duration);
			sb.append(" ms (");
			sb.append(info);
			sb.append(infoValue);
			sb.append("). Memory (used/allocs): ");
			sb.append(size);
			sb.append('/');
			sb.append(allocs);

			Log.d("profiler", sb.toString());
		}
	}

	public static void startProfSend(long timeReceived, String info) {
		if (profiling) {
			sendSignalTime.set(timeReceived);

			StringBuffer sb = localStringBuffer.get();
			int allocs = Debug.getThreadAllocCount();
			int size = Debug.getThreadAllocSize();

			sb.setLength(0);
			sb.append("sending signal (");
			sb.append(info);
			sb.append("). Memory (used/allocs): ");
			sb.append(size);
			sb.append('/');
			sb.append(allocs);

			Log.d("profiler", sb.toString());
		}
	}

	public static void endProfSend(long timeProcessed) {
		if (profiling) {
			long sendTime = sendSignalTime.getAndSet(0);
			if (sendTime == 0)
				return;

			StringBuffer sb = localStringBuffer.get();
			long duration = timeProcessed - sendTime;
			int allocs = Debug.getThreadAllocCount();
			int size = Debug.getThreadAllocSize();

			sb.setLength(0);
			sb.append("duration from sending signal to network: ");
			sb.append(duration);
			sb.append(" ms. Memory (used/allocs): ");
			sb.append(size);
			sb.append('/');
			sb.append(allocs);

			Log.d("profiler", sb.toString());
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

	/* Cached thread-local objects to reduce memory allocations */
	private final static ThreadLocal<StringBuffer> localStringBuffer = new ThreadLocal<StringBuffer>() {
		@Override
		protected StringBuffer initialValue() {
			return new StringBuffer();
		}
	};
}
