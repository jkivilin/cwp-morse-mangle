package fi_81.cwp_morse_mangle.morse_tests;

import java.nio.CharBuffer;

import org.junit.Test;

import android.util.Log;

import fi_81.cwp_morse_mangle.morse.BitString;
import fi_81.cwp_morse_mangle.morse.MorseChar;
import fi_81.cwp_morse_mangle.morse.MorseCharList;
import fi_81.cwp_morse_mangle.morse.MorseCodec;

import junit.framework.TestCase;

public class MorsePackageTests extends TestCase {
	private static final String TAG = "MorsePackageTests";

	@Test
	public void test1_MorseChar() {
		CharBuffer cb = CharBuffer.allocate(5);
		cb.put("11101");
		cb.flip();

		MorseChar mc0 = new MorseChar(new BitString("111"), 't');
		MorseChar mc1 = new MorseChar(new BitString("1"), 'e');
		MorseChar mc2 = new MorseChar(new BitString("101"), 'i');

		MorseChar mc3 = new MorseChar(new BitString("10111"), 'a');
		MorseChar mc4 = new MorseChar(new BitString(cb), 'n');

		assertTrue(mc0.getCharacter() == 't');
		assertTrue(mc0.getMorseLength() == 3);
		assertTrue(mc0.compareTo(new BitString("111")) == 0);

		assertTrue(mc1.getCharacter() == 'e');
		assertTrue(mc1.getMorseLength() == 1);
		assertTrue(mc1.compareTo(new BitString("1")) == 0);

		assertFalse(mc0.compareTo(mc1) == 0);
		assertFalse(mc1.compareTo(mc2) == 0);
		assertFalse(mc2.compareTo(mc0) == 0);
		assertTrue(mc1.compareTo(mc0) == -mc0.compareTo(mc1));
		assertTrue(mc1.compareTo(mc2) == -mc2.compareTo(mc1));
		assertTrue(mc2.compareTo(mc0) == -mc0.compareTo(mc2));

		assertFalse(mc3.compareTo(mc4) == 0);
		assertTrue(mc3.compareTo(mc4) == -mc4.compareTo(mc3));
		assertTrue(mc3.getMorseString().compareTo(new BitString("10111")) == 0);

		BitString z0 = new BitString();
		BitString z1 = BitString.newZeros(1);
		BitString z2 = BitString.newZeros(2);
		BitString z3 = BitString.newZeros(3);
		BitString o1z1 = new BitString("10");
		BitString o1z2 = new BitString("100");
		BitString o1z3 = new BitString("1000");

		assertEquals(z0.compareTo(z0), "".compareTo(""));
		assertEquals(z0.toString().compareTo(""), "".compareTo(""));
		assertEquals(z0.compareTo(z1), "".compareTo("0"));
		assertEquals(z1.compareTo(z2), "0".compareTo("00"));
		assertEquals(z1.compareTo(z3), "0".compareTo("000"));

		assertEquals(z0.compareTo(o1z1), "".compareTo("10"));
		assertEquals(o1z1.compareTo(o1z1), "10".compareTo("10"));
		assertEquals(o1z2.compareTo(o1z2), "100".compareTo("100"));
		assertEquals(o1z1.compareTo(o1z3), "10".compareTo("1000"));
	}

	@Test
	public void test2_MorseCharList() {
		assertEquals(MorseCharList.morseBitsToCharacter(new BitString("111")),
				't');
		assertEquals(
				MorseCharList.morseBitsToCharacter(new BitString("11101")), 'n');
		assertEquals(MorseCharList.morseBitsToCharacter(BitString.newZeros(3)),
				'Z');
		assertEquals(MorseCharList.morseBitsToCharacter(new BitString("1111")),
				'Z');

		Log.d(TAG, new String(MorseCharList.getAllowedCharacters()));
	}

	@Test
	public void test3_MorseCodec_encode() {
		String sos_in = "sos";
		BitString sos_out = new BitString("101010001110111011100010101");

		assertTrue(sos_out.compareTo(MorseCodec.encodeMessageToMorse(sos_in)) == 0);

		String helloworld_in = "hello world!";
		BitString helloworld_out = new BitString("1010101" + "000" + "1"
				+ "000" + "101110101" + "000" + "101110101" + "000"
				+ "11101110111" + "0000000" + "101110111" + "000"
				+ "11101110111" + "000" + "1011101" + "000" + "101110101"
				+ "000" + "1110101" + "000" + "1110101110101110111");

		assertTrue(helloworld_out.compareTo(MorseCodec
				.encodeMessageToMorse(helloworld_in)) == 0);

		/* upper-case 'I' not mapped to any signal */
		assertEquals(MorseCodec.encodeMessageToMorse("I"), new BitString(""));

		assertEquals(MorseCodec.encodeMessageToMorse(" e"), new BitString("1"));
		assertEquals(MorseCodec.encodeMessageToMorse(" e "), new BitString("1"));
		assertEquals(MorseCodec.encodeMessageToMorse("e"), new BitString("1"));
		assertEquals(MorseCodec.encodeMessageToMorse("  e "),
				new BitString("1"));
		assertEquals(MorseCodec.encodeMessageToMorse(" e  "),
				new BitString("1"));

		assertEquals(MorseCodec.encodeMessageToMorse(" Ie I "), new BitString(
				"1"));
		assertEquals(MorseCodec.encodeMessageToMorse("I e II "), new BitString(
				"1"));

		/* mixed unsupported/support/spaces */
		assertEquals(MorseCodec.encodeMessageToMorse("eI IeIee  e"),
				new BitString("1000000010001000100000001"));

		Log.d(TAG, MorseCodec.encodeMessageToMorse("sos").toString());
	}

	@Test
	public void test4_MorseCodec_decode() {
		assertEquals(MorseCodec.trimMorseString(new BitString("010")),
				new BitString("1"));
		assertEquals(MorseCodec.trimMorseString(new BitString("010000")),
				new BitString("1"));
		assertEquals(MorseCodec.trimMorseString(new BitString("0000010100")),
				new BitString("101"));

		assertEquals(MorseCodec.decodeMorseToMessage(new BitString(
				"101010001110111011100010101")), "sos");

		BitString helloworld_in = new BitString("1010101" + "000" + "1" + "000"
				+ "101110101" + "000" + "101110101" + "000" + "11101110111"
				+ "0000000" + "101110111" + "000" + "11101110111" + "000"
				+ "1011101" + "000" + "101110101" + "000" + "1110101" + "000"
				+ "1110101110101110111");
		String helloworld_out = "hello world!";

		assertEquals(MorseCodec.decodeMorseToMessage(helloworld_in),
				helloworld_out);

		/* nearly invalid message */
		assertEquals(MorseCodec.decodeMorseToMessage(new BitString(
				"00000000001010100001010100")), "ss");

		assertEquals(MorseCodec.decodeMorseToMessage(new BitString(
				"000010000000000000000001000")), "e e");
	}
}
