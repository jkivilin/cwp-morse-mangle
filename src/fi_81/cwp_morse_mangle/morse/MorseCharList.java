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

package fi_81.cwp_morse_mangle.morse;

import java.util.Arrays;

public class MorseCharList {
	public static final char SPECIAL_START_OF_MESSAGE = 'S';
	public static final char SPECIAL_END_OF_CONTACT = 'C';
	public static final char SPECIAL_END_OF_MESSAGE = 'E';
	public static final char SPECIAL_SOS = 'X';
	public static final char SPECIAL_STOP_MESSAGE = '©';

	private final static MorseChar characters[] = fillInMorseCharacters();
	private static char allowedMorseChars[];

	/*
	 * Longest length of morse code (excluding out internal
	 * SPECIAL_STOP_MESSAGE)
	 */
	public static int longestMorseBits;

	private static MorseChar[] fillInMorseCharacters() {
		final String morseData =
			/* Special, implementation spesific morse code, do not use for transmitting! */
			"1010101110101110" +
			"1010101110101110" +
			"101010111010111|" +		"©|" + /* triple "end of contant" to internally handle end of messages */
				
			/* Special morse codes, start/stop signals, etc */
			"1110101010111010111|" +	"B|" + /* break */
			"111010111010101110101|" +	"O|" + /* going off air */
			"111010111010111|" +		"S|" + /* start of transmission */
			"101010111010111|" +		"C|" + /* end of contact */
			"10101011101|" +			"U|" + /* understood */
			"10101011101110111010101|" +"X|" + /* S.O.S. */
			
			/* Special morse codes that overlap with writable codes, therefore disabled */
			//"10111010111|" +			"N|" + /* new line, same as 'ä' */
			//"111010111|" +			"K|" + /* go, invitation to reply, same as 'k' */
			//"10111010101|" +			"W|" + /* wait, same as '&' */
			//"1011101011101|" +		"E|" + /* end of message, same as '+' */
			//"111010111011101|" +		"L|" + /* reply invitation to named station, same as '(' */
			//"1110101010111|" +		"P|" + /* two new lines, new paragraph, same as '=' */
			
			/* Regular morse characters, lower case */
			"10111|" +					"a|" +
			"111010101|" +				"b|" +
			"11101011101|" +			"c|" +
			"1110101|" +				"d|" +
			"1|" +						"e|" +
			"101011101|" +				"f|" +
			"111011101|" +				"g|" +
			"1010101|" +				"h|" +
			"101|" +					"i|" +
			"1011101110111|" +			"j|" +
			"111010111|" +				"k|" +
			"101110101|" +				"l|" +
			"1110111|" +				"m|" +
			"11101|" +					"n|" +
			"11101110111|" +			"o|" +
			"10111011101|" +			"p|" +
			"1110111010111|" +			"q|" +
			"1011101|" +				"r|" +
			"10101|" +					"s|" +
			"111|" +					"t|" +
			"1010111|" +				"u|" +
			"101010111|" +				"v|" +
			"101110111|" +				"w|" +
			"11101010111|" +			"x|" +
			"1110101110111|" +			"y|" +
			"11101110101|" +			"z|" +
			"101110111010111|" +		"å|" +
			"10111010111|" +			"ä|" +
			"1110111011101|" +			"ö|" +
			"1110111011101110111|" +	"0|" +
			"10111011101110111|" +		"1|" +
			"101011101110111|" +		"2|" +
			"1010101110111|" +			"3|" +
			"10101010111|" +			"4|" +
			"101010101|" +				"5|" +
			"11101010101|" +			"6|" +
			"1110111010101|" +			"7|" +
			"111011101110101|" +		"8|" +
			"11101110111011101|" +		"9|" +
			"10111010111010111|" +		".|" +
			"1110111010101110111|" +	",|" +
			"101011101110101|" +		"?|" +
			"1011101110111011101|" +	"\'|" +
			"1110101110101110111|" +	"!|" +
			"1110101011101|" +			"/|" +
			"111010111011101|" +		"(|" +
			"1110101110111010111|" +	")|" +
			"10111010101|" +			"&|" +
			"11101110111010101|" +		":|" +
			"11101011101011101|" +		";|" +
			"1110101010111|" +			"=|" +
			"1011101011101|" +			"+|" +
			"111010101010111|" +		"-|" +
			"10101110111010111|" +		"_|" +
			"101110101011101|" +		"\"|" +
			"10101011101010111|" +		"$|" +
			"10111011101011101|" +		"@";

		/*
		 * Convert data string to data structures
		 */

		String dataStrings[] = morseData.split("\\|");

		StringBuffer gatheredCodes = new StringBuffer(morseData.length());
		StringBuffer allowed = new StringBuffer(dataStrings.length / 2);
		MorseChar chars[] = new MorseChar[dataStrings.length / 2];
		int[] codeOffsets = new int[dataStrings.length / 2];

		/*
		 * Gather all morse-code bits in to single large bitstring, to use
		 * single shared backing buffer to store all morse-code bits.
		 */
		for (int offset = 0, i = 0, len = dataStrings.length; i < len; i += 2) {
			gatheredCodes.append(dataStrings[i]);

			codeOffsets[i / 2] = offset;
			offset += dataStrings[i].length();
		}

		String gatheredCodesString = gatheredCodes.toString();

		longestMorseBits = 0;
		for (int i = 0, len = codeOffsets.length; i < len; i++) {
			int nextOffset = (i + 1 < len) ? codeOffsets[i + 1] : gatheredCodes
					.length();
			BitString bs = BitString.newBits(gatheredCodesString.substring(
					codeOffsets[i], nextOffset));
			char ch = dataStrings[i * 2 + 1].charAt(0);

			chars[i] = new MorseChar(bs, ch);

			/* Gather allowed characters, skip upper-case special characters */
			if (!Character.isUpperCase(ch) && ch != SPECIAL_STOP_MESSAGE)
				allowed.append(ch);

			/* Get length of longest message that we can receive */
			if (ch != SPECIAL_STOP_MESSAGE && bs.length() > longestMorseBits)
				longestMorseBits = bs.length();
		}

		allowedMorseChars = allowed.toString().toCharArray();

		/* Sort arrays for fast binary search */
		Arrays.sort(chars);
		Arrays.sort(allowedMorseChars);

		return chars;
	}

	public static char[] getAllowedCharacters() {
		return allowedMorseChars;
	}

	/*
	 * Search morse code from character list and return found character for the
	 * code. Return zero if not found.
	 */
	public static char morseBitsToCharacter(BitString bits) {
		/* Binary search from sorted list */
		int index = Arrays.binarySearch(characters,
				MorseChar.createSearchCode(bits));

		if (index < 0)
			return 'Z'; /* 'Z' reserved for "unknown" */
		else
			return characters[index].getCharacter();
	}

	/* Search character from the list and return morse signal for this character */
	public static BitString characterToMorseString(char ch) {
		for (MorseChar morse_char : characters)
			if (morse_char.getCharacter() == ch)
				return morse_char.getMorseString();

		return new BitString();
	}
}
