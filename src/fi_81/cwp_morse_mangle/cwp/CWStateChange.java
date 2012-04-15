package fi_81.cwp_morse_mangle.cwp;

import java.nio.ByteBuffer;

public class CWStateChange {
	public static final byte TYPE_DOWN_TO_UP = 1;
	public static final byte TYPE_UP_TO_DOWN = -1;
	public static final byte TYPE_FREQUENCY_CHANGE = 2;

	protected byte type;
	protected int value;

	/* time since creation of connection when to send this message */
	private int outTime;

	public CWStateChange(byte type, int timestampOrDuration, int outTime) {
		this.type = type;
		this.value = timestampOrDuration;
		this.outTime = outTime;
	}

	public byte getType() {
		return type;
	}

	public int getValue() {
		return value;
	}

	public int getOutTime() {
		return outTime;
	}

	public void addTimestamp(int timestamp) {
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
