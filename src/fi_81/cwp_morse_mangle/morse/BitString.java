package fi_81.cwp_morse_mangle.morse;

import java.util.ArrayList;

/*
 * Bit format string, originally implemented with BitSet as backing buffer, but as
 * it appeared to be too slow (profiling results on Motorola Defy+), reverted to String.
 */
public class BitString implements Comparable<BitString>, CharSequence {
	private String bits;

	/* Format input to BitString */
	private static String makeBits(CharSequence chars) {
		int i, len;

		/* Check if can accept input as is */
		for (i = 0, len = chars.length(); i < len; i++) {
			char ch = chars.charAt(i);
			if (ch != '0' && ch != '1')
				break;
		}

		if (i == len) {
			/* Already in correct bit-format */
			return chars.toString();
		}

		StringBuffer sb = new StringBuffer(len);

		sb.append(chars, 0, i);

		for (; i < len; i++) {
			char ch = chars.charAt(i);
			sb.append(ch == '1' ? '1' : '0');
		}

		return sb.toString();
	}

	/* Internal constructor for buffer that already is in bit-format */
	private BitString(String str, boolean alreadyBits) {
		if (alreadyBits)
			bits = str;
		else
			this.bits = makeBits(str);
	}

	/* Public constructor */
	public BitString(CharSequence chars) {
		this.bits = makeBits(chars);
	}

	/* Empty constructor */
	public BitString() {
		this.bits = "";
	}

	/*
	 * Helper builders
	 */
	public static BitString newFilled(char oneOrZero, int numChars) {
		StringBuffer sb = new StringBuffer(numChars);

		for (int i = 0; i < numChars; i++)
			sb.append(oneOrZero);

		return new BitString(sb.toString(), true);
	}

	public static BitString newZeros(int numZeros) {
		return newFilled('0', numZeros);
	}

	public static BitString newOnes(int numOnes) {
		return newFilled('1', numOnes);
	}

	public static BitString newBits(String onesAndZeros) {
		return new BitString(onesAndZeros, true);
	}

	@Override
	public char charAt(int index) {
		return bits.charAt(index);
	}

	@Override
	public int length() {
		return bits.length();
	}

	@Override
	public CharSequence subSequence(int start, int end) {
		return bits.subSequence(start, end);
	}

	@Override
	public int compareTo(BitString another) {
		return bits.compareTo(another.bits);
	}

	@Override
	public String toString() {
		return bits;
	}

	public BitString substring(int start, int end) {
		return new BitString(bits.substring(start, end), true);
	}

	/* Check if object is same as this. */
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (!(o instanceof BitString))
			return false;

		BitString obs = (BitString) o;

		return (bits == null ? obs.bits == null : bits.equals(obs.bits));
	}

	/** Append another BitString at end of this and return resulting BitString */
	public BitString append(BitString endBits) {
		StringBuffer sb = new StringBuffer(bits.length() + endBits.bits.length());

		sb.append(bits);
		sb.append(endBits.bits);

		return new BitString(sb.toString(), true);
	}

	public BitString[] split(BitString splitString) {
		/* Emulate String.split() as closely as possible */
		int stringLength = bits.length();
		int splitStringLength = splitString.bits.length();

		/*
		 * Empty string gives array with one cell, and that cell is empty
		 * string.
		 */
		if (stringLength == 0) {
			BitString array[] = new BitString[1];
			array[0] = this;
			return array;
		}

		ArrayList<BitString> list = new ArrayList<BitString>();
		int idx = 0, prev = 0;

		list.clear();

		/* Iterate through all split strings and copy to array */
		while ((idx = bits.indexOf(splitString.bits, idx)) >= 0) {
			list.add(new BitString(bits.substring(prev, idx), true));

			if (splitStringLength > 0)
				idx += splitStringLength;
			else
				idx++; /* Avoid endless loop */

			prev = idx;
		}

		/* Finally, copy the last split string */
		idx = stringLength;
		list.add(new BitString(bits.substring(prev, idx), true));

		BitString splits[] = list.toArray(new BitString[0]);
		list.clear();

		return splits;
	}

	public boolean endWith(BitString suffix) {
		return bits.endsWith(suffix.bits);
	}
}
