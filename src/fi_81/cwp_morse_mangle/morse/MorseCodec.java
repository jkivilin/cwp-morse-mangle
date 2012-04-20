package fi_81.cwp_morse_mangle.morse;

import java.util.Arrays;

public class MorseCodec {
	/* Each morse character is separated with 000 and word by 0000000. */
	private final static BitString charStop = BitString.newZeros(3);
	private final static BitString wordStop = BitString.newZeros(7);

	/* Our morse messages are ended with special code */
	public static final BitString endMessage = MorseCharList
			.characterToMorseString(MorseCharList.SPECIAL_STOP_MESSAGE);
	public static final BitString endSequence = charStop.append(endMessage);

	/* Or by end-of-contact from sender */
	public static final BitString endContact = charStop.append(MorseCharList
			.characterToMorseString(MorseCharList.SPECIAL_END_OF_CONTACT)
			.append(charStop));

	/* Message string to morse code */
	public static BitString encodeMessageToMorse(CharSequence message) {
		StringBuffer output = new StringBuffer();
		boolean addCharStop = false;
		boolean addWordStop = false;
		int i, len = message.length();

		for (i = 0; i < len; i++) {
			char cur_char = message.charAt(i);

			/*
			 * If character is space, we replace character-stop signal with
			 * word-stop.
			 */
			if (cur_char == ' ') {
				/* multiple spaces are skipped */
				if (!addCharStop)
					continue;

				addCharStop = false;
				addWordStop = true;
				continue;
			}

			BitString morse = MorseCharList.characterToMorseString(cur_char);
			if (morse.length() == 0)
				continue;

			/*
			 * add character-stop or word-stop between previous and current
			 * character
			 */
			if (addCharStop) {
				output.append(charStop);
				addCharStop = false;
			}
			if (addWordStop) {
				output.append(wordStop);
				addWordStop = false;
			}

			output.append(morse);
			addCharStop = true;
		}

		return BitString.newBits(output.toString());
	}

	public static BitString trimMorseString(BitString morse) {
		int i, start = -1, end = -1, len = morse.length();

		/* Find first and last non-zero signal */
		for (i = 0; i < len; i++) {
			if (start == -1 && morse.charAt(i) == '1')
				start = i;

			if (end == -1 && morse.charAt(len - (i + 1)) == '1')
				end = len - (i + 1);

			/* stop if both ends have been found */
			if (end != -1 && start != -1)
				break;
		}

		/* empty morse message */
		if (end == -1 && start == -1)
			return new BitString();

		/* nothing to trim? */
		if (start == 0 && end == len - 1)
			return morse;

		return morse.substring(start, end + 1);
	}

	/* Decode morse to message */
	public static String decodeMorseToMessage(BitString morse) {
		StringBuffer output = new StringBuffer();
		boolean first_word = true;

		/* trim leading/trailing spaces */
		morse = trimMorseString(morse);

		/* Split morse message by "0000000"-word separators */
		BitString morse_words[] = morse.split(wordStop);

		for (BitString morse_word : morse_words) {
			/*
			 * Trim leading/trailing spaces. This is here to protect from
			 * invalid morse code from untested/buggy clients.
			 */
			morse_word = trimMorseString(morse_word);
			if (morse_word.length() == 0)
				continue;

			/* add space between words */
			if (!first_word)
				output.append(' ');

			first_word = false;

			/* Split morse word by "000"-word separators */
			BitString morse_chars[] = morse_word.split(charStop);

			for (BitString morse_char : morse_chars) {
				/*
				 * Trim leading/trailing spaces. This is here to protect from
				 * invalid morse code from untested/buggy clients.
				 */
				morse_char = trimMorseString(morse_char);

				output.append(MorseCharList.morseBitsToCharacter(morse_char));
			}
		}

		return output.toString();
	}

	/* Check if character allowed for _input_ */
	public static boolean isAllowedMorseCharacter(char ch) {
		/* Space is special, and not in morse-code list. */
		if (ch == ' ')
			return true;

		return Arrays.binarySearch(MorseCharList.getAllowedCharacters(), ch) >= 0;
	}
}
