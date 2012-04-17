package fi_81.cwp_morse_mangle.morse;

import java.util.ArrayList;
import java.util.BitSet;

/* Bit format string (containing only ones and zeros) by utilizing BitSet class as backing buffer. */
public class BitString implements Comparable<BitString>, CharSequence {
	private int stringLength;
	private BitSet bits;
	private int bitsOffset;

	/*
	 * Helpers for accessing bits with bitsOffset
	 */
	private BitSet refBits() {
		if (bitsOffset == 0)
			if (stringLength >= bits.length())
				return bits;

		return bits.get(bitsOffset, bitsOffset + stringLength);
	}

	private BitSet getBits(int start, int end) {
		if (end > stringLength)
			end = stringLength;

		return bits.get(bitsOffset + start, bitsOffset + end);
	}

	private boolean getBit(int idx) {
		return bits.get(bitsOffset + idx);
	}

	private int nextSetBit(int start) {
		if (start >= stringLength)
			return -1;

		int next = bits.nextSetBit(bitsOffset + start) - bitsOffset;
		if (next < 0)
			return -1;

		return next;
	}

	/*
	 * Helper constructors
	 */
	private static BitSet zeroBits = null;
	private static BitSet oneBits = null;

	public static BitString newZeros(int numZeros) {
		if (zeroBits == null)
			zeroBits = new BitSet(64);

		return new BitString(zeroBits, numZeros, 0);
	}

	public static BitString newOnes(int numOnes) {
		if (oneBits == null) {
			oneBits = new BitSet(64);
			oneBits.flip(0, 64);
		}

		return new BitString(oneBits, numOnes, 0);
	}

	/* Convert CharSequence consisting of ones and zeros to BitString. */
	public BitString(CharSequence chars) {
		/* Empty BitString, do not allocate BitSet. */
		stringLength = chars.length();
		if (stringLength == 0) {
			bits = null;
			return;
		}

		BitSet bits = new BitSet(stringLength);

		/* Special handling for String-class */
		if (chars.getClass() == String.class) {
			String string = (String) chars;

			int i = string.indexOf('1');
			while (i >= 0) {
				bits.set(i++);

				if (i == stringLength)
					break;

				i = string.indexOf('1', i);
			}
		} else {
			for (int i = 0; i < stringLength; i++) {
				char ch = chars.charAt(i);

				if (ch == '1')
					bits.set(i);
			}
		}

		this.bitsOffset = 0;
		this.bits = bits;
	}

	/* Empty BitString, do not allocate BitSet. */
	public BitString() {
		stringLength = 0;
		bitsOffset = 0;
		bits = null;
	}

	/* BitString from BitSet. */
	private BitString(BitSet bits, int stringLength, int bitOffset) {
		this.stringLength = stringLength;
		this.bitsOffset = bitOffset;
		this.bits = bits;
	}

	/* BitString from another BitString, share backing BitSet */
	public BitString(BitString o) {
		this(o.bits, o.stringLength, o.bitsOffset);
	}

	/* Convert bitwise data to String with ones and zeros. */
	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer(stringLength);
		int i;

		for (i = 0; i < stringLength; i++)
			buf.append(getBit(i) ? '1' : '0');

		return buf.toString();
	}

	/*
	 * Comparing of two BitStrings, returns same values as for corresponding
	 * Strings
	 */
	public int compareTo(BitString o) {
		/* First check part that is of same length */
		int minLen = o.stringLength > stringLength ? stringLength
				: o.stringLength;

		/* Other of the string is empty, no need to compare bitsets */
		if (minLen > 0) {
			BitSet xorbits;

			/* same length codes with same bits generate empty xor-bits */
			if (minLen == o.stringLength) {
				xorbits = getBits(0, minLen);
				xorbits.xor(o.refBits());
			} else {
				xorbits = o.getBits(0, minLen);
				xorbits.xor(refBits());
			}

			if (!xorbits.isEmpty()) {
				/*
				 * Same length with different bits, check first bit
				 */
				int idx = xorbits.nextSetBit(0);

				/*
				 * Since we know now that bits at index in both variables are
				 * not the same we can avoid following code and just check one
				 * of two bits...
				 * 
				 * int search_bit = search_code.morseCode.get(i) ? 1 : 0; int
				 * code_bit = morseCode.get(i) ? 1 : 0; return code_bit -
				 * search_bit;
				 */
				return o.getBit(idx) ? -1 : 1;
			}
		}

		/* Leading BitSets are the same, check by length */
		return stringLength - o.stringLength;
	}

	/* Check if object is same as this. */
	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;

		if (!(o instanceof BitString))
			return false;

		BitString obs = (BitString) o;

		if (bitsOffset == 0)
			return stringLength == obs.stringLength
					&& bitsOffset == obs.bitsOffset
					&& (bits == null ? obs.bits == null : bits.equals(obs.bits));

		return stringLength == obs.stringLength
				&& (bits == null ? obs.bits == null : compareTo(obs) == 0);
	}

	public int length() {
		return stringLength;
	}

	public char charAt(int index) {
		if (index >= stringLength)
			throw new IndexOutOfBoundsException();

		return getBit(index) ? '1' : '0';
	}

	public CharSequence subSequence(int start, int end) {
		BitString bs;

		/* sub-BitString, share backing BitSet */
		bs = new BitString(bits, end - start, bitsOffset + start);

		return (CharSequence) bs;
	}

	public BitString substring(int start, int end) {
		return (BitString) subSequence(start, end);
	}

	/* Append another BitString at end of this BitString. */
	public BitString append(BitString endBits) {
		/* Empty bits? Do nothing */
		if (endBits == null || endBits.stringLength == 0)
			return this;

		if (bits == null) {
			/* return new BitString with endBits.bits as backing buffer */
			return new BitString(endBits);
		}

		int newLen = endBits.stringLength + stringLength;
		BitSet newBits = new BitSet(newLen);

		/* copy over old bits */
		newBits.or(refBits());

		/*
		 * append new bits (bitset does not have shifting upwards, have to do
		 * this manual bit by bit )
		 */
		for (int i = endBits.nextSetBit(0); i >= 0; i = endBits
				.nextSetBit(i + 1)) {
			newBits.set(stringLength + i);
		}

		return new BitString(newBits, newLen, 0);
	}

	/* Get index of substring o. */
	private int indexOf(BitString o, int start) {
		if (start >= stringLength)
			return -1;

		for (; o.stringLength <= stringLength - start; start++) {
			BitSet cmp_bits = getBits(start, start + o.stringLength);
			cmp_bits.xor(o.refBits());

			if (cmp_bits.isEmpty())
				return start;
		}

		return -1;
	}

	public BitString[] split(BitString splitString) {
		/* Emulate String.split() as closely as possible */

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

		/* Iterate through all split strings and copy to array */
		while ((idx = indexOf(splitString, idx)) >= 0) {
			list.add(new BitString(bits, idx - prev, bitsOffset + prev));

			if (splitString.stringLength > 0)
				idx += splitString.stringLength;
			else
				idx++; /* Avoid endless loop */

			prev = idx;
		}

		/* Finally, copy the last split string */
		idx = stringLength;
		list.add(new BitString(bits, idx - prev, bitsOffset + prev));

		BitString splits[] = new BitString[list.size()];
		return list.toArray(splits);
	}

	public boolean endWith(BitString suffix) {
		if (suffix.stringLength > stringLength)
			return false;

		BitSet endBits;

		if (suffix.stringLength == stringLength)
			endBits = (BitSet) getBits(0, stringLength);
		else
			endBits = getBits(stringLength - suffix.stringLength, stringLength);

		endBits.xor(suffix.refBits());
		return endBits.isEmpty();
	}
}
