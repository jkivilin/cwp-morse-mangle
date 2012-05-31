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

import java.nio.ByteBuffer;

public class CWStateChange {
	public static final byte TYPE_DOWN_TO_UP = 1;
	public static final byte TYPE_UP_TO_DOWN = -1;
	public static final byte TYPE_FREQUENCY_CHANGE = 2;

	protected byte type;
	protected int value;

	/* time since creation of connection when to send this message */
	private long outTime;

	public void setValues(byte type, int timestampOrDuration, long outTime) {
		this.type = type;
		this.value = timestampOrDuration;
		this.outTime = outTime;
	}

	public CWStateChange(byte type, int timestampOrDuration, long outTime) {
		setValues(type, timestampOrDuration, outTime);
	}

	public void clear() {
		setValues((byte) 0, 0, 0);
	}

	public CWStateChange() {
		clear();
	}

	public byte getType() {
		return type;
	}

	public int getValue() {
		return value;
	}

	public long getOutTime() {
		return outTime;
	}

	public void addTimestamp(long timestamp) {
		if (type == TYPE_DOWN_TO_UP)
			value += timestamp;
		outTime += timestamp;
	}

	public boolean writeToBuffer(ByteBuffer outbuf) {
		if (type == TYPE_DOWN_TO_UP || type == TYPE_FREQUENCY_CHANGE) {
			/* check if state-change fits in to buffer */
			if (outbuf.remaining() < 4)
				return false;

			outbuf.putInt(value);
			return true;
		}

		/* TODO: handle long up-states */

		/* check if state-change fits in to buffer */
		if (outbuf.remaining() < 2)
			return false;

		outbuf.putShort((short) value);

		return true;
	}
}
