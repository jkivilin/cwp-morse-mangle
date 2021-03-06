/*
 * Copyright (C) 2012 Jussi Kivilinna <jussi.kivilinna@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package fi_81.cwp_morse_mangle;

import android.content.SharedPreferences;

public class DefaultSettings {
	/* Default setup values */
	public static final String HOSTNAME_DEFAULT = "cwp.opimobi.com";
	public static final String HOSTPORT_DEFAULT = "20000";
	public static final String MORSE_SPEED_DEFAULT = "med";
	public static boolean BEEP_DEFAULT = true;
	public static boolean VIBRATOR_DEFAULT = true;
	public static boolean LATENCY_MANAGEMENT_DEFAULT = true;

	/* Milliseconds values for morse speed */
	public static int MORSE_SPEED_FAST = 50;
	public static int MORSE_SPEED_MED = 100;
	public static int MORSE_SPEED_SLOW = 200;

	public static String getHostName(SharedPreferences settings) {
		return settings.getString("hostname", HOSTNAME_DEFAULT);
	}

	public static String getHostPort(SharedPreferences settings) {
		return settings.getString("hostport", HOSTPORT_DEFAULT);
	}

	public static String getMorseSpeed(SharedPreferences settings) {
		String value = settings.getString("morse_speed", MORSE_SPEED_DEFAULT);

		/* validate old setting */
		if (value.compareTo("slow") == 0)
			return value;
		else if (value.compareTo("med") == 0)
			return value;
		else if (value.compareTo("fast") == 0)
			return value;

		return MORSE_SPEED_DEFAULT;
	}

	public static boolean getBeep(SharedPreferences settings) {
		return settings.getBoolean("allow_beep", BEEP_DEFAULT);
	}

	public static boolean getVibrator(SharedPreferences settings) {
		return settings.getBoolean("allow_vibrator", VIBRATOR_DEFAULT);
	}

	public static boolean getLatencyManagement(SharedPreferences settings) {
		return settings.getBoolean("latency_management",
				LATENCY_MANAGEMENT_DEFAULT);
	}

	public static int getHostPortInt(SharedPreferences settings) {
		try {
			return Integer.parseInt(getHostPort(settings));
		} catch (NumberFormatException NFE) {
			return Integer.parseInt(HOSTPORT_DEFAULT);
		}
	}

	public static int getMorseSpeedMillisec(SharedPreferences settings) {
		return getMorseSpeedMillisec(getMorseSpeed(settings));
	}

	public static int getMorseSpeedMillisec(String value) {
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
