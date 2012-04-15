package fi_81.cwp_morse_mangle.morse;

public class MorseChar implements Comparable<MorseChar> {
	private BitString morseCode;
	private char morseCharacter;

	/*
	 * Input morse code as ones and zeroes and give mapping to corresponding
	 * character
	 */
	public MorseChar(BitString morse_bits, char character) {
		morseCode = morse_bits;
		morseCharacter = character;
	}

	public MorseChar(String string, char character) {
		morseCode = new BitString(string);
		morseCharacter = character;
	}

	public BitString getMorseString() {
		return morseCode;
	}

	public void setMorseString(BitString bitString) {
		morseCode = bitString;
	}

	/*
	 * Search code does not map morse-code to character but only contains the
	 * morse-string for searching
	 */
	public static MorseChar createSearchCode(BitString code) {
		return new MorseChar(code, '\0');
	}

	public char getCharacter() {
		return morseCharacter;
	}

	public int getMorseLength() {
		/* length == highest bit set */
		return morseCode.length();
	}

	/* For sorting list */
	public int compareTo(MorseChar o) {
		return morseCode.compareTo(o.morseCode);
	}

	/*
	 * Input morse-encoded character and compare with this morse-to-character
	 * mapping
	 */
	public int compareTo(BitString code) {
		return this.compareTo(MorseChar.createSearchCode(code));
	}

	/* More meaningful debug output */
	public String toString() {
		return Character.toString(morseCharacter) + " " + morseCode.toString();
	}
}
