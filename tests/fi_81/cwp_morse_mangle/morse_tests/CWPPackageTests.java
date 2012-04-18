package fi_81.cwp_morse_mangle.morse_tests;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Matcher;

import junit.framework.TestCase;

import org.junit.Test;

import android.util.Log;

import fi_81.cwp_morse_mangle.cwp.CWInput;
import fi_81.cwp_morse_mangle.cwp.CWInput.CWInputNotification;
import fi_81.cwp_morse_mangle.cwp.CWInputQueue;
import fi_81.cwp_morse_mangle.cwp.CWOutput;
import fi_81.cwp_morse_mangle.cwp.CWOutput.CWOutputNotification;
import fi_81.cwp_morse_mangle.cwp.CWStateChange;
import fi_81.cwp_morse_mangle.cwp.CWStateChangeQueueFromMorseCode;
import fi_81.cwp_morse_mangle.cwp.CWave;
import fi_81.cwp_morse_mangle.morse.BitString;
import fi_81.cwp_morse_mangle.morse.MorseCodec;

public class CWPPackageTests extends TestCase {
	private static final String TAG = "CWPPackageTests";

	@Test
	public void test1_CWStateChangeVectorFromMorseCode() {
		ArrayDeque<CWStateChange> queue = new ArrayDeque<CWStateChange>();
		ArrayList<CWStateChange> states;

		CWStateChangeQueueFromMorseCode.setSignalWidth(1);

		CWStateChangeQueueFromMorseCode.encode(queue, new BitString("10111"));
		states = new ArrayList<CWStateChange>(queue);
		queue.clear();
		assertTrue(states != null);
		assertEquals(4, states.size());
		assertEquals(CWStateChange.TYPE_DOWN_TO_UP, states.get(0).getType());
		assertEquals(CWStateChange.TYPE_UP_TO_DOWN, states.get(1).getType());
		assertEquals(CWStateChange.TYPE_DOWN_TO_UP, states.get(2).getType());
		assertEquals(CWStateChange.TYPE_UP_TO_DOWN, states.get(3).getType());
		assertEquals(0, states.get(0).getValue());
		assertEquals(1, states.get(1).getValue());
		assertEquals(2, states.get(2).getValue());
		assertEquals(3, states.get(3).getValue());

		CWStateChangeQueueFromMorseCode.setSignalWidth(2);

		CWStateChangeQueueFromMorseCode.encode(queue, new BitString("010"));
		states = new ArrayList<CWStateChange>(queue);
		queue.clear();
		assertTrue(states != null);
		assertEquals(2, states.size());
		assertEquals(CWStateChange.TYPE_DOWN_TO_UP, states.get(0).getType());
		assertEquals(CWStateChange.TYPE_UP_TO_DOWN, states.get(1).getType());
		assertEquals(2, states.get(0).getValue());
		assertEquals(2, states.get(1).getValue());

		CWStateChangeQueueFromMorseCode.encode(queue, new BitString(
				"11111000001"));
		states = new ArrayList<CWStateChange>(queue);
		queue.clear();
		assertTrue(states != null);
		assertEquals(4, states.size());
		assertEquals(CWStateChange.TYPE_DOWN_TO_UP, states.get(0).getType());
		assertEquals(CWStateChange.TYPE_UP_TO_DOWN, states.get(1).getType());
		assertEquals(CWStateChange.TYPE_DOWN_TO_UP, states.get(2).getType());
		assertEquals(CWStateChange.TYPE_UP_TO_DOWN, states.get(3).getType());
		assertEquals(0 * 2, states.get(0).getValue());
		assertEquals(5 * 2, states.get(1).getValue());
		assertEquals(10 * 2, states.get(2).getValue());
		assertEquals(1 * 2, states.get(3).getValue());
	}

	@Test
	public void test2_CWInputQueue() {
		CWInputQueue cwiq;
		ArrayList<CWave> arlist;

		/* test create */
		cwiq = new CWInputQueue();
		assertTrue(cwiq != null);
		assertEquals(CWave.TYPE_DOWN, cwiq.getCurrentState());
		assertEquals(cwiq.getCurrentStateTimestamp(), 0);
		assertEquals(cwiq.queueLength(), 0);
		assertFalse(cwiq.isQueueReadReady());

		/* push wrong down state */
		try {
			cwiq.pushStateDown(1);
			fail("Must fail here!");
		} catch (Exception e) {
			assertTrue(true);
		}

		/* push correct up state */
		cwiq.pushStateUp(10);
		assertEquals(CWave.TYPE_UP, cwiq.getCurrentState());
		assertEquals(cwiq.getCurrentStateTimestamp(), 10);
		assertEquals(cwiq.queueLength(), 1);
		assertFalse(cwiq.isQueueReadReady());
		assertEquals(0,
				cwiq.getQueue().peek()
						.compareTo(new CWave(CWave.TYPE_DOWN, 10)));

		/* push wrong up state */
		try {
			cwiq.pushStateUp(1);
			fail("Must fail here!");
		} catch (Exception e) {
			assertTrue(true);
		}

		/* push correct down state */
		cwiq.pushStateDown(1);
		arlist = new ArrayList<CWave>(cwiq.getQueue());
		assertEquals(CWave.TYPE_DOWN, cwiq.getCurrentState());
		assertEquals(cwiq.getCurrentStateTimestamp(), 11);
		assertEquals(cwiq.queueLength(), 2);
		assertTrue(cwiq.isQueueReadReady());
		assertEquals(0, arlist.get(0).compareTo(new CWave(CWave.TYPE_DOWN, 10)));
		assertEquals(0, arlist.get(1).compareTo(new CWave(CWave.TYPE_UP, 1)));

		/* pushing up-wave of length 0xffff */
		cwiq.pushStateUp(cwiq.getCurrentStateTimestamp() + 1);
		cwiq.pushStateDown(0xffff);
		arlist = new ArrayList<CWave>(cwiq.getQueue());
		assertEquals(cwiq.getCurrentStateTimestamp(), 12 + 0xffff);
		assertEquals(cwiq.queueLength(), 4);
		assertTrue(cwiq.isQueueReadReady());
		assertEquals(0, arlist.get(2).compareTo(new CWave(CWave.TYPE_DOWN, 1)));
		assertEquals(0,
				arlist.get(3).compareTo(new CWave(CWave.TYPE_UP, 0xffff)));

		/* handling of special case of Up-wave longer than 0xffff */
		cwiq = new CWInputQueue();
		cwiq.pushStateUp(1);
		cwiq.pushStateDown(0xffff);
		cwiq.pushStateUp(cwiq.getCurrentStateTimestamp());
		cwiq.pushStateDown(1);
		arlist = new ArrayList<CWave>(cwiq.getQueue());
		assertTrue(cwiq.isQueueReadReady());
		assertEquals(cwiq.getCurrentStateTimestamp(), 1 + 0xffff + 1);
		assertEquals(cwiq.queueLength(), 2);
		assertEquals(0, arlist.get(0).compareTo(new CWave(CWave.TYPE_DOWN, 1)));
		assertEquals(0,
				arlist.get(1).compareTo(new CWave(CWave.TYPE_UP, 0x10000)));
	}

	private static byte b(int i) {
		/*
		 * Byte is signed in java, need magic to convert unsigned byte in input
		 * integer to byte.
		 */
		ByteBuffer bb = ByteBuffer.allocate(4);

		bb.putInt(i);

		return bb.array()[3];
	}

	private static final byte test_array0[] = {
	/* state-change: state-up, timestamp = 1 */
	b(0x00), b(0x00), b(0x00), b(0x01),
	/* state-change: state-down, duration = 0x1f */
	b(0x00), b(0x1f),
	/* state-change: state-up, timestamp = 0x7f8f9faf */
	b(0x7f), b(0x8f), b(0x9f), b(0xaf),
	/* state-change: state-down, duration = 0xfffe */
	b(0xff), b(0xfe) };

	private static final byte test_array1[] = {
	/* freq-change, freq = 1 (-1 = 0xffffffff) */
	b(0xff), b(0xff), b(0xff), b(0xff),
	/* state-change: state-up, timestamp = 1 */
	b(0x00), b(0x00), b(0x00), b(0x01),
	/* state-change: state-down, duration = 0x1f */
	b(0x00), b(0x1f),
	/* freq-change, freq = 2 (-1 = 0xfffffffe) */
	b(0xff), b(0xff), b(0xff), b(0xfe),
	/* state-change: state-up, timestamp = 0x7f8f9faf */
	b(0x7f), b(0x8f), b(0x9f), b(0xaf),
	/* state-change: state-down, duration = 0xfffe */
	b(0xff), b(0xfe) };

	private static final byte test_array2[] = {
	/* state-change: state-up, timestamp = 0 */
	b(0x00), b(0x00), b(0x00), b(0x00),
	/* state-change: state-down, duration = 0x1f */
	b(0x00), b(0x1f) };

	private static final byte test_array3[] = {
	/* state-change: state-up, timestamp = 0x0 */
	b(0x00), b(0x00), b(0x00), b(0x00),
	/* state-change: state-down, duration = 0x0f */
	b(0x00), b(0x0f),
	/* state-change: state-up, timestamp = 0x0f */
	b(0x00), b(0x00), b(0x00), b(0x0f),
	/* state-change: state-down, duration = 0x01 */
	b(0x00), b(0x01) };

	@Test
	public void test3_CWInput() {
		CWInputNotification notify = new CWInput.NotificationNone();
		final LinkedList<Long> freqs = new LinkedList<Long>();

		CWInput cwi;
		CWInputQueue cwiq;

		/* test create */
		cwiq = new CWInputQueue();
		cwi = new CWInput(cwiq, ByteBuffer.wrap(test_array0));
		assertTrue(cwi != null);

		/* process input */
		try {
			cwi.processInput(notify);
			cwi.flushStaleMorseBits(notify, true);
		} catch (Exception e) {
			fail(e.toString());
		}

		/* check processed data */
		assertFalse(cwiq.isQueueReadReady());
		assertEquals(cwiq.queueLength(), 0);
		assertEquals(cwiq.getCurrentStateTimestamp(), 0x7f8f9faf + 0xfffe);

		/* test first zero */
		cwiq = new CWInputQueue();
		cwi = new CWInput(cwiq, ByteBuffer.wrap(test_array2));
		assertTrue(cwi != null);

		/* process input */
		cwi.processInput(notify);
		try {
			Thread.sleep(10 + 31);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		cwi.processInput(notify);
		cwi.flushStaleMorseBits(notify, true);

		/* check processed data */
		assertFalse(cwiq.isQueueReadReady());
		assertEquals(cwiq.queueLength(), 0);
		assertEquals(cwiq.getCurrentStateTimestamp(), 0x0 + 0x1f);

		/* test combined up-waves */
		cwiq = new CWInputQueue();
		cwi = new CWInput(cwiq, ByteBuffer.wrap(test_array3));
		assertTrue(cwi != null);

		/* process input */
		cwi.processInput(notify);
		try {
			Thread.sleep(10 + 31);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		cwi.processInput(notify);
		cwi.flushStaleMorseBits(notify, true);

		/* check processed data */
		assertFalse(cwiq.isQueueReadReady());
		assertEquals(cwiq.queueLength(), 0);
		assertEquals(cwiq.getCurrentStateTimestamp(), 0xf + 0x1);

		/* test freq-change */
		cwiq = new CWInputQueue();
		cwi = new CWInput(cwiq, ByteBuffer.wrap(test_array1));
		assertTrue(cwi != null);

		/* process input */
		final LinkedList<Integer> state_change_count = new LinkedList<Integer>();
		try {
			notify = new CWInputNotification() {
				@Override
				public void frequencyChange(long newFreq) {
					freqs.add(new Long(newFreq));
				}

				@Override
				public void stateChange(byte newState, int value) {
					if (state_change_count.size() == 0)
						state_change_count.add(1);
					else
						state_change_count.set(0, state_change_count.get(0)
								.intValue() + 1);
				}

				@Override
				public void morseMessage(BitString morseBits) {
					Log.d(TAG, MorseCodec.decodeMorseToMessage(morseBits));
				}
			};

			cwi.processInput(notify);
			cwi.flushStaleMorseBits(notify, true);
		} catch (Exception e) {
			fail(e.toString());
		}

		/* check freqs */
		assertEquals(2, freqs.size());
		assertEquals(1, freqs.get(0).intValue());
		assertEquals(2, freqs.get(1).intValue());

		/* check processed data */
		assertEquals(state_change_count.get(0).intValue(), 4);
		assertFalse(cwiq.isQueueReadReady());
		assertEquals(cwiq.queueLength(), 0);
		assertEquals(cwiq.getCurrentStateTimestamp(), 0x7f8f9faf + 0xfffe);
	}

	@Test
	public void test4_CWOutput() {
		ByteBuffer bb;
		CWOutput cwo;
		int first;

		CWStateChangeQueueFromMorseCode.setSignalWidth(1);

		/* test outputting shortest possible morse message */
		cwo = new CWOutput(ByteBuffer.allocate(8), System.currentTimeMillis());
		assertTrue(cwo != null);

		cwo.sendMorseCode(new BitString("1"));
		try {
			Thread.sleep(10 + 1);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		cwo.processOutput(new CWOutput.NotificationNone());
		bb = cwo.getOutputBuffer();

		assertTrue(bb.getInt() < 10);
		assertEquals(1, bb.getShort());

		/* test outputting slightly larger morse */
		cwo = new CWOutput(ByteBuffer.allocate(32), System.currentTimeMillis());
		assertTrue(cwo != null);

		cwo.sendMorseCode(new BitString("1110101111"));
		try {
			Thread.sleep(10 + 10);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		cwo.processOutput(new CWOutput.NotificationNone());
		bb = cwo.getOutputBuffer();

		first = bb.getInt();
		assertTrue(first < 10);
		assertEquals(3, bb.getShort());
		assertEquals(4, bb.getInt() - first);
		assertEquals(1, bb.getShort());
		assertEquals(6, bb.getInt() - first);
		assertEquals(4, bb.getShort());
	}

	private void privateTestCWBits(BitString bitsSend, int width,
			int delayStart, int checkType, String checkStr) {
		byte array[];
		ByteBuffer bb;
		CWOutput cwo;
		CWInput cwi;

		CWStateChangeQueueFromMorseCode.setSignalWidth(width);

		/* emulate delayed start by modifying connection start time to past */
		cwo = new CWOutput(System.currentTimeMillis() - delayStart);
		assertTrue(cwo != null);

		array = new byte[0];
		CWOutputNotification notifyOut = new CWOutput.NotificationNone();

		cwo.sendMorseCode(bitsSend);
		try {
			Thread.sleep(bitsSend.length() * width + 31);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		bb = cwo.getOutputBuffer();
		while (cwo.processOutput(notifyOut) || bb.position() > 0) {
			byte narray[] = new byte[array.length + bb.remaining()];
			System.arraycopy(array, 0, narray, 0, array.length);
			bb.get(narray, array.length, bb.remaining());
			array = narray;
		}

		cwi = new CWInput(ByteBuffer.wrap(array));
		assertTrue(cwi != null);

		final LinkedList<Integer> state_change_count = new LinkedList<Integer>();
		final LinkedList<BitString> morseCode = new LinkedList<BitString>();
		morseCode.add(new BitString());

		final CWInputNotification notifyIn = new CWInputNotification() {
			@Override
			public void frequencyChange(long newFreq) {
			}

			@Override
			public void stateChange(byte newState, int value) {
				if (state_change_count.size() == 0)
					state_change_count.add(1);
				else
					state_change_count.set(0, state_change_count.get(0)
							.intValue() + 1);
			}

			@Override
			public void morseMessage(BitString morseBits) {
				System.out.println(morseBits);
				System.out.println(MorseCodec.decodeMorseToMessage(morseBits));
				morseCode.set(0, morseCode.get(0).append(morseBits));

				if (morseCode.get(0).endWith(MorseCodec.endSequence))
					morseCode.set(0,
							morseCode.get(0).append(BitString.newZeros(3)));
			}
		};

		String messageReceived = "";
		int waitTime = (bitsSend.length() + 10) * width;

		for (int i = 0; i < waitTime + 20; i += 10) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			cwi.processInput(notifyIn);
		}

		cwi.flushStaleMorseBits(notifyIn, true);

		messageReceived = MorseCodec.decodeMorseToMessage(morseCode.get(0));

		Log.d(TAG, morseCode.get(0).toString());
		Log.d(TAG, MorseCodec.decodeMorseToMessage(morseCode.get(0)));

		messageReceived = messageReceived.replaceAll(
				Matcher.quoteReplacement(" ©"), "©").trim();
		messageReceived = messageReceived.replaceAll(
				Matcher.quoteReplacement("© "), "©").trim();
		messageReceived = messageReceived.replaceAll(
				Matcher.quoteReplacement("©"), "").trim();

		switch (checkType) {
		case 0: /* match string */
			assertEquals(messageReceived, checkStr);
			break;
		case 1: /* One signal 'e' or 't', or unknown 'Z' */
			assertTrue(messageReceived.length() > 0 || !cwi.hadPendingBits());
			for (char ch : messageReceived.toCharArray()) {
				assertTrue(ch == 'e' || ch == 'Z' || ch == 't');
			}
			break;
		default:
			assertTrue(false);
			break;
		}
	}

	@Test
	public void test5_CWOutputToCWInput_BitString() {
		BitString bits;

		/* Simple test should just work */
		bits = MorseCodec.encodeMessageToMorse("StestC");
		privateTestCWBits(bits, 5, 0, 0, "StestC");

		/* Decoding with leading zeros must work */
		bits = BitString.newZeros(64);
		bits = bits.append(MorseCodec.encodeMessageToMorse("StestC"));
		privateTestCWBits(bits, 4, 0, 0, "StestC");

		/*
		 * large/unknown bit sequence should output something (preferably "e"s
		 * or "Z"s) instead of buffering
		 */
		bits = new BitString(
				"101010101010101010101010101010101010101010101010101010101010101010101010101010101010101");
		privateTestCWBits(bits, 3, 0, 1, null);

		/* message after unknown bits */
		bits = new BitString(
				"10101010101010101010101010101010101010101010101010101010101010101010101010101010101010100000000000"
						+ "111111001100111111001100111111000000110011001100111111001100111111000000000000000"
						+ "10101011101110111010101");
		privateTestCWBits(bits, 2, 0, 0, "ZSCX");

		/* Slow messages */
		bits = new BitString("1");
		bits = bits.append(BitString.newZeros(600));
		bits = bits.append(MorseCodec.encodeMessageToMorse("SXC"));
		privateTestCWBits(bits, 1, 0, 0, "eSXC");
	}

	private void privateTestCWMorse(String[] messagesToSend, int widths[]) {
		byte array[];
		ByteBuffer bb;
		CWOutput cwo;
		BitString[] sentMorse = new BitString[messagesToSend.length];
		CWInput cwi;

		assert (messagesToSend.length == widths.length);

		cwo = new CWOutput(System.currentTimeMillis());
		assertTrue(cwo != null);

		CWOutputNotification notify = new CWOutput.NotificationNone();
		array = new byte[0];
		for (int i = 0, len = messagesToSend.length; i < len; i++) {
			CWStateChangeQueueFromMorseCode.setSignalWidth(widths[i]);

			sentMorse[i] = MorseCodec.encodeMessageToMorse(messagesToSend[i]);

			cwo.sendMorseCode(sentMorse[i]);
			try {
				Thread.sleep(sentMorse[i].length() * widths[i] + 31);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			bb = cwo.getOutputBuffer();
			while (cwo.processOutput(notify) || bb.position() > 0) {
				byte narray[] = new byte[array.length + bb.remaining()];
				System.arraycopy(array, 0, narray, 0, array.length);
				bb.get(narray, array.length, bb.remaining());
				array = narray;
			}

		}

		cwi = new CWInput(ByteBuffer.wrap(array));
		assertTrue(cwi != null);

		final LinkedList<Integer> state_change_count = new LinkedList<Integer>();
		final LinkedList<BitString> morseCode = new LinkedList<BitString>();
		morseCode.add(new BitString());

		final CWInputNotification notifyIn = new CWInputNotification() {
			@Override
			public void frequencyChange(long newFreq) {
			}

			@Override
			public void stateChange(byte newState, int value) {
				if (state_change_count.size() == 0)
					state_change_count.add(1);
				else
					state_change_count.set(0, state_change_count.get(0)
							.intValue() + 1);
			}

			@Override
			public void morseMessage(BitString morseBits) {
				System.out.println(morseBits);
				System.out.println(MorseCodec.decodeMorseToMessage(morseBits));
				morseCode.set(0, morseCode.get(0).append(morseBits));

				if (morseCode.get(0).endWith(MorseCodec.endSequence))
					morseCode.set(0,
							morseCode.get(0).append(BitString.newZeros(3)));
			}
		};

		int waitTime = 0;
		for (int i = 0, len = widths.length; i < len; i++)
			waitTime += sentMorse[i].length() * widths[i];

		for (int i = 0; i < waitTime + 20; i += 10) {
			cwi.processInput(notifyIn);

			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		cwi.flushStaleMorseBits(notifyIn, true);

		String messageSent = "";
		String messageSentSeparate = "";
		for (int i = 0, len = messagesToSend.length; i < len; i++) {
			messageSent = messageSent + messagesToSend[i];
			messageSentSeparate = messageSentSeparate + " " + messagesToSend[i];
		}

		String messageReceived = MorseCodec.decodeMorseToMessage(morseCode
				.get(0));

		Log.d(TAG, morseCode.get(0).toString());
		Log.d(TAG, MorseCodec.decodeMorseToMessage(morseCode.get(0)));

		messageReceived = messageReceived.replaceAll(
				Matcher.quoteReplacement(" ©"), "©").trim();
		messageReceived = messageReceived.replaceAll(
				Matcher.quoteReplacement("© "), "©").trim();
		messageReceived = messageReceived.replaceAll(
				Matcher.quoteReplacement("©"), "").trim();
		messageSent = messageSent.replaceAll(Matcher.quoteReplacement("©"), "")
				.trim();
		messageSent = messageSent.replaceAll(Matcher.quoteReplacement("  "),
				" ").trim();
		messageSentSeparate = messageSentSeparate.replaceAll(
				Matcher.quoteReplacement("©"), "").trim();
		messageSentSeparate = messageSentSeparate.replaceAll(
				Matcher.quoteReplacement("  "), " ").trim();

		if (messageSent.compareTo(messageReceived) == 0)
			return;

		if (messageSentSeparate.compareTo(messageReceived) == 0)
			return;

		Log.d(TAG, String.format("send[%s][%s], recv[%s]", messageSent,
				messageSentSeparate, messageReceived));
		assertEquals(messageSent, messageReceived);
	}

	private void privateTestCWMorse(String message, int width) {
		String[] messages = new String[1];
		int[] widths = new int[1];

		messages[0] = message;
		widths[0] = width;

		privateTestCWMorse(messages, widths);
	}

	private void privateTestCWMorse(String message1, int width1,
			String message2, int width2) {
		String[] messages = new String[2];
		int[] widths = new int[2];

		messages[0] = message1;
		widths[0] = width1;
		messages[1] = message2;
		widths[1] = width2;

		privateTestCWMorse(messages, widths);
	}

	@Test
	public void test6_CWOutputToCWInput() {
		/* test variating signal widths */
		CWStateChangeQueueFromMorseCode.setSignalJitter(100, 0.01);
		privateTestCWMorse("abc", 11, "cba", 22);

		for (int i = 1; i < 3; i++) {
			/* sos special character, ...---... */
			privateTestCWMorse("X", i);
			/* sos as separate characters, ..._---_... */
			privateTestCWMorse("sos", i);
			/* s, ... */
			privateTestCWMorse("s", i);
			/* abc..., .-_-..._-.-. -_._..._-_.._-._--. etc */
			privateTestCWMorse("abc testing 123 123", i);

			/* cba, -.-._-..._.- */
			privateTestCWMorse("cba", i);
		}

		/* multi message with variable signal widths */
		privateTestCWMorse("X", 1, "X", 2);
		privateTestCWMorse("X", 1, "X", 3);
		privateTestCWMorse("X", 1, "X", 7);
		privateTestCWMorse("sos", 1, "sos", 2);
		privateTestCWMorse("sos", 1, "sos", 3);
		privateTestCWMorse("sos", 1, "sos", 7);
		privateTestCWMorse("StotC", 1, "StotC", 2);
		privateTestCWMorse("StotC", 1, "StotC", 3);
		privateTestCWMorse("StotC", 1, "StotC", 7);

		privateTestCWMorse("X", 2, "X", 1);
		privateTestCWMorse("X", 3, "X", 1);
		privateTestCWMorse("SöC", 4, "S0C", 1);
		privateTestCWMorse("X", 7, "X", 1);
		privateTestCWMorse("sos", 2, "sos", 1);
		privateTestCWMorse("sos", 3, "sos", 1);
		privateTestCWMorse("sos", 7, "sos", 1);
		privateTestCWMorse("StotC", 2, "StotC", 1);
		privateTestCWMorse("StotC", 3, "StotC", 1);
		privateTestCWMorse("StotC", 7, "StotC", 1);

		privateTestCWMorse("abcdefghijklmopqrstuvwxyzåäö", 2,
				"0123456789,?\'!/()&:;=+-_\"$@", 1);
	}
}
