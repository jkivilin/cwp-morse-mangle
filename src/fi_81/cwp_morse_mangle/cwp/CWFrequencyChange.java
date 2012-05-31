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

package fi_81.cwp_morse_mangle.cwp;

public class CWFrequencyChange extends CWStateChange {
	public static final long MAX_FREQ_NEG = -Integer.MAX_VALUE;

	public void setValues(long newFreq) {
		/*
		 * Register with correct type and add timestamp of 0 (making sending
		 * logic to pass this forward immediately).
		 */
		setValues(CWStateChange.TYPE_FREQUENCY_CHANGE, (int) (-newFreq), 0);
	}

	public CWFrequencyChange(long newFreq) {
		/*
		 * Register with correct type and add timestamp of 0 (making sending
		 * logic to pass this forward immediately).
		 */
		super(CWStateChange.TYPE_FREQUENCY_CHANGE, (int) (-newFreq), 0);
	}

	public CWFrequencyChange() {
		super();
	}

	public long getFrequency() {
		/*
		 * Must cast to 'long' before negation, integer negation of
		 * Integer.MIN_VALUE results overflow back to Integer.MIN_VALUE.
		 */
		return -(long) value;
	}
}
