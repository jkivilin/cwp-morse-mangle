package fi_81.cwp_morse_mangle;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayDeque;

import fi_81.cwp_morse_mangle.cwp.*;
import fi_81.cwp_morse_mangle.cwp.CWInput.CWInputNotification;
import fi_81.cwp_morse_mangle.cwp.CWOutput.CWOutputNotification;
import fi_81.cwp_morse_mangle.morse.BitString;
import fi_81.cwp_morse_mangle.morse.MorseCharList;
import fi_81.cwp_morse_mangle.morse.MorseCodec;

public class CWPControlThread extends Thread {
	private static final String TAG = "CWPControlThread";

	/* Connection states */
	public static final int CONN_NO_CONFIGURATION = 0;
	public static final int CONN_RESOLVING_ADDRESS = 1;
	public static final int CONN_CREATE_CONNECTION = 2;
	public static final int CONN_CONNECTED = 3;

	/* Used to signal thread to end work */
	private AtomicBoolean isThreadKilled = new AtomicBoolean(false);

	/* ArrayDeque used for passing data to IO-thread */
	private final ArrayDeque<CWPThreadValue> msgQueue = new ArrayDeque<CWPThreadValue>();

	/* Selector for blocking on non-blocking sockets */
	private Selector selector;

	/* Initial state does not have server-details, "no configuration" */
	private int connState = CONN_NO_CONFIGURATION;

	/* Connection configuration */
	private String hostName = "";
	private int hostPort = 0;
	private int morseSpeed = 0;

	/* Current connection setup */
	private InetSocketAddress connSockAddr;
	private long connStartTime;
	private SocketChannel connChannel;
	private SelectionKey connSelKey;
	private CWInput cwpIn;
	private CWOutput cwpOut;
	private boolean busySendingMorseMessage = false;
	private String sendMorseMessageString;

	/* Send and receive channel states */
	private boolean recvStateUp = false;
	private boolean sendStateUp = false;
	private long currFrequency = 1;
	private BitString morseMessageBits;
	private final StringBuffer recvMorseMessage = new StringBuffer();
	private final StringBuffer sendMorseMessage = new StringBuffer();

	/* Parent service */
	private CWPControlService cwpService;

	/** Service reference is needed for callbacks */
	public CWPControlThread(CWPControlService service) {
		try {
			selector = Selector.open();
		} catch (IOException e) {
			EventLog.e(
					TAG,
					"CWPControlThread(): Selector.open() failed: "
							+ e.toString());
			System.exit(1);
		}

		cwpService = service;
	}

	/** Disconnect from server cleanly and setup to resolve hostname */
	private void resetServerConnection() {
		EventLog.d(TAG, "resetServerConnection()");

		/* flush pending morse */
		if (cwpIn != null)
			cwpIn.flushStaleMorseBits(inputNotify, true);

		/* cancel selection-key registration */
		if (connSelKey != null) {
			connSelKey.cancel();
			connSelKey = null;
		}

		if (connState == CONN_CONNECTED) {
			/* Should be non-null */
			if (connChannel != null) {
				try {
					connChannel.close();
				} catch (IOException e) {
					/* Do we have other option than just ignore this? */
				}
			}

			connChannel = null;
			connSockAddr = null;
			connStartTime = -1;

			connState = CONN_RESOLVING_ADDRESS;
		} else if (connState == CONN_CREATE_CONNECTION) {
			connChannel = null;
			connSockAddr = null;
			connStartTime = -1;

			connState = CONN_RESOLVING_ADDRESS;
		}

		/* Clear pending morse message */
		handleReceivedMorseMessageBuffer();

		/* Clear connection state */
		recvStateUp = false;
		sendStateUp = false;
		morseMessageBits = null;
		busySendingMorseMessage = false;
		sendMorseMessageString = null;
		sendMorseMessage.setLength(0);
		cwpIn = null;
		cwpOut = null;

		handleStateRequest();
	}

	/** Main loop of thread */
	private void run_loop() {
		try {
			/* EventLog.d(TAG, "run_loop(): %d", connState); */

			switch (connState) {
			default:
			case CONN_NO_CONFIGURATION:
				/* Wait for configuration from UI-thread. */
				sleep(Long.MAX_VALUE);
				break;

			case CONN_RESOLVING_ADDRESS:
				handleResolvingAddress();

				break;

			case CONN_CREATE_CONNECTION:
				handleCreateConnection();

				break;

			case CONN_CONNECTED:
				try {
					handleConnection();
				} catch (IOException e) {
					EventLog.w(TAG,
							"Server connection IOException: " + e.toString());

					/* IOException, connection trouble, reset connection */
					resetServerConnection();
				}

				break;
			}
		} catch (ClosedChannelException e) {
			/* this is does not happen as selector is closed after run_loop() */
		} catch (InterruptedException ie) {
			/* woke from sleep */
		}

		/* Thread is being killed? */
		if (isThreadKilled.get())
			return;

		/*
		 * Each connection state handler might be sleeping and have sleep
		 * interrupted by messages from UI-thread.
		 */
		handleMessageQueue();
	}

	private void handleResolvingAddress() throws InterruptedException {
		/* Resolve IP-address for hostname */
		try {
			connSockAddr = new InetSocketAddress(
					InetAddress.getByName(hostName), hostPort);

			/* TODO: notify UI-thread about successful connection */

			connState = CONN_CREATE_CONNECTION;
		} catch (UnknownHostException e) {
			/* Invalid hostname, cannot be resolved */

			/* Wait some time before retrying resolving address */
			sleep(5000);
		}
	}

	private void handleCreateConnection() throws InterruptedException,
			ClosedChannelException {
		connChannel = null;

		try {
			/* open socket and store connection time */
			connChannel = SocketChannel.open(connSockAddr);
			connStartTime = System.currentTimeMillis();

			/* make channel non-blocking */
			connChannel.configureBlocking(false);

			/* adjust socket for low-latency */
			connChannel.socket().setTcpNoDelay(true);

			/* in connected state now */
			connState = CONN_CONNECTED;
		} catch (SocketException se) {
			/* clean up */
			if (connChannel != null) {
				try {
					connChannel.close();
				} catch (IOException e) {
				}
			}

			/* Try resolve address first */
			connState = CONN_RESOLVING_ADDRESS;

			/* Short sleep to avoid busy loop */
			sleep(2000);
			return;
		} catch (ClosedByInterruptException cbie) {
			/*
			 * Interrupted in SocketChannel.open() blocking I/O. Channel has
			 * been closed, retry connection on next loop.
			 */
			connState = CONN_CREATE_CONNECTION;
			throw new InterruptedException();
		} catch (IOException ioe) {
			/* IO error, need to reset connection */
			if (connChannel != null) {
				try {
					connChannel.close();
				} catch (IOException e) {
				}
			}

			/*
			 * IOException is hard error, maybe internet connection was lost. Go
			 * back to resolving address.
			 */
			connState = CONN_RESOLVING_ADDRESS;

			/* Short sleep to avoid busy loop */
			sleep(200);
			return;
		}

		/* Register read-channel to selector */
		connSelKey = connChannel.register(selector, SelectionKey.OP_READ);

		/* Connection has been created, initialize other components */
		cwpIn = new CWInput();
		cwpOut = new CWOutput(connStartTime);

		/* set frequency if not default */
		if (currFrequency != 1)
			cwpOut.sendFrequenceChange(currFrequency);
	}

	/**
	 * Handle sending and receiving data from CWP server
	 * 
	 * @throws ClosedChannelException
	 */
	private void handleConnection() throws IOException {
		long timeToNextWork;
		int numReadyChannels, interestSet;

		/* Always interested in reading */
		interestSet = SelectionKey.OP_READ;

		/* CWP output handling */
		cwpOut.processOutput(outputNotify);

		/* Check if need to register write-channel to selector */
		if (cwpOut.getOutputBuffer().remaining() > 0) {
			/* Set interest on writing too */
			interestSet |= SelectionKey.OP_WRITE;
		}

		/* Update interest set for key */
		connSelKey.interestOps(interestSet);

		/* Get time to next CWOutput or CWInput work */
		timeToNextWork = Math.min(cwpOut.timeToNextWork(),
				cwpIn.timeToNextWork());

		/* Wait for input */
		if (timeToNextWork == 0)
			numReadyChannels = selector.selectNow();
		else {
			/*
			 * Workaround Java or Darvik bug, cannot handle Long.MAX_VALUE.
			 * Throws SocketException.
			 */
			if (timeToNextWork > Integer.MAX_VALUE)
				timeToNextWork = Integer.MAX_VALUE;

			numReadyChannels = selector.select(timeToNextWork);
		}

		/* Receive and send data */
		if (numReadyChannels > 0) {
			handleNonBlockingNetworkIO();

			if (busySendingMorseMessage && cwpOut.queueSize() == 0
					&& cwpOut.getOutputBuffer().remaining() == 0) {
				/* Sending morse message completed */
				busySendingMorseMessage = false;
				sendMorseMessageString = null;
				cwpService.notifyMorseMessageSendingState(true, null);
			}
		}

		/* CWP input handling */
		cwpIn.processInput(inputNotify);
	}

	private void handleNonBlockingNetworkIO() throws IOException {
		ByteBuffer inBuf = cwpIn.getInBuffer();
		ByteBuffer outBuf = cwpOut.getOutputBuffer();
		Iterator<SelectionKey> keyIter = selector.selectedKeys().iterator();
		int bytesCopied;

		/* Iterate active selection keys */
		while (keyIter.hasNext()) {
			SelectionKey key = keyIter.next();

			/* Input reader */
			if (key.isValid() && key.isReadable()) {
				bytesCopied = connChannel.read(inBuf);

				if (bytesCopied > 0)
					EventLog.startProfRecv(System.currentTimeMillis());

				EventLog.d(TAG, "handleNonBlockingNetworkIO(): read "
						+ bytesCopied + " bytes");
			}

			/* Output writer */
			if (key.isValid() && key.isWritable()) {
				bytesCopied = connChannel.write(outBuf);

				EventLog.d(TAG, "handleNonBlockingNetworkIO(): written "
						+ bytesCopied + " bytes");
			}

			keyIter.remove();
		}
	}

	/** Handle messages from UI-thread */
	private void handleMessageQueue() {
		CWPThreadValue value;

		while ((value = queuePop(msgQueue)) != null) {
			switch (value.type) {
			case CWPThreadValue.TYPE_CONFIGURATION:
				handleNewConfiguration(value.getHostName(),
						value.getHostPort(), value.getMorseSpeed());
				break;
			case CWPThreadValue.TYPE_STATE_CHANGE:
				handleNewSendingState(value.isStateUp());
				break;
			case CWPThreadValue.TYPE_FREQ_CHANGE:
				handleNewFrequency(value.getFrequency());
				break;
			case CWPThreadValue.TYPE_MORSE_MESSAGE:
				handleNewMorseMessage(value.getMorseMessage());
				break;
			case CWPThreadValue.TYPE_STATE_REQUEST:
				handleStateRequest();
				break;
			case CWPThreadValue.TYPE_CLEAR_MESSAGES:
				recvMorseMessage.setLength(0);
				cwpService.notifyMorseUpdates("");
				break;
			}
		}
	}

	private void handleStateRequest() {
		cwpService.notifyFrequencyChange(currFrequency);
		cwpService.notifyStateChange(recvStateUp, sendStateUp);
		cwpService.notifyMorseUpdates(recvMorseMessage.toString());
		cwpService.notifyMorseMessageSendingState(!busySendingMorseMessage,
				sendMorseMessageString);
	}

	private void handleNewMorseMessage(String morse) {
		if (connState == CONN_CONNECTED && cwpOut != null) {
			/* Caller should make sure this does not happen */
			if (busySendingMorseMessage)
				return;

			busySendingMorseMessage = true;
			sendMorseMessageString = morse;

			/* Convert message to morse-code BitString */
			sendMorseMessage.setLength(0);
			sendMorseMessage.append(MorseCharList.SPECIAL_START_OF_MESSAGE);
			sendMorseMessage.append(morse);
			sendMorseMessage.append(MorseCharList.SPECIAL_END_OF_CONTACT);

			BitString morseBits = MorseCodec
					.encodeMessageToMorse(sendMorseMessage);

			/* Fill in morse message */
			cwpOut.sendDown();
			cwpOut.sendMorseCode(morseBits);

			/* Report state to activity */
			cwpService.notifyMorseMessageSendingState(false,
					sendMorseMessageString);
		} else {
			/* Just complete morse message sending when not connected */
			sendMorseMessageString = null;
			cwpService.notifyMorseMessageSendingState(true, null);
		}
	}

	private void handleNewFrequency(long frequency) {
		if (currFrequency != frequency) {
			EventLog.d(TAG, "handleNewFrequency: " + frequency);

			currFrequency = frequency;

			if (connState == CONN_CONNECTED && cwpOut != null)
				cwpOut.sendFrequenceChange(currFrequency);
		}
	}

	private void handleNewSendingState(boolean stateUp) {
		if (busySendingMorseMessage)
			return;

		if (connState == CONN_CONNECTED && cwpOut != null) {
			EventLog.d(TAG, "handleNewSendingState: " + stateUp);

			if (stateUp)
				cwpOut.sendUp();
			else
				cwpOut.sendDown();
		}
	}

	private void handleNewConfiguration(String hostName, int hostPort,
			int morseSpeed) {
		/* Enforce valid range of port */
		if (hostPort < 0)
			hostPort = 0;
		else if (hostPort > 0xffff)
			hostPort = 0xffff;

		/* No configuration, fill in values and change connection state */
		if (connState == CONN_NO_CONFIGURATION) {
			this.hostName = hostName;
			this.hostPort = hostPort;
			this.morseSpeed = morseSpeed;

			CWStateChangeQueueFromMorseCode.setSignalWidth(morseSpeed);
			CWStateChangeQueueFromMorseCode.setSignalJitter(Integer.MAX_VALUE,
					0.0);

			connState = CONN_RESOLVING_ADDRESS;
			return;
		}

		if (this.morseSpeed != morseSpeed) {
			/* Set new morse speed */
			CWStateChangeQueueFromMorseCode.setSignalWidth(morseSpeed);

			this.morseSpeed = morseSpeed;
		}

		if (this.hostName.compareTo(hostName) != 0 || this.hostPort != hostPort) {
			/* Server setup changed, trigger reconnection */
			this.hostName = hostName;
			this.hostPort = hostPort;

			/* restart from resolving server address */
			resetServerConnection();
		}
	}

	/** Decode received morse bits to message string and pass to UI-thread */
	private void handleReceivedMorseMessageBuffer() {
		/* Nothing to process? */
		if (morseMessageBits == null || morseMessageBits.length() == 0)
			return;

		morseMessageBits = morseMessageBits.append(BitString.newZeros(3));
		String message = MorseCodec.decodeMorseToMessage(morseMessageBits);

		/* No message? */
		if (message == null || message.length() == 0)
			return;

		EventLog.d(TAG, "Received morse-message: '" + message + '\'');

		/* Fill to main message buffer */
		recvMorseMessage.append(' ');
		for (char ch : message.toCharArray()) {
			/* Handle SOS specially */
			if (ch == MorseCharList.SPECIAL_SOS) {
				recvMorseMessage.append("¡SOS!");
				continue;
			}

			/* Fill end-message control codes with space */
			if (ch == MorseCharList.SPECIAL_END_OF_CONTACT
					|| ch == MorseCharList.SPECIAL_END_OF_MESSAGE
					|| ch == MorseCharList.SPECIAL_STOP_MESSAGE)
				ch = ' ';

			/* Exclude other control codes */
			if (Character.isUpperCase(ch))
				continue;

			recvMorseMessage.append(ch);
		}

		/* Send updated morse-message string to UI */
		cwpService.notifyMorseUpdates(recvMorseMessage.toString());

		morseMessageBits = null;
	}

	/** Handle callbacks from CWInput */
	private final CWInputNotification inputNotify = new CWInputNotification() {
		private static final String TAG = "CWPControlThread:inputNotify";

		public void frequencyChange(long newFreq) {
			EventLog.d(TAG, "freq-change: " + newFreq);

			/* flush pending morse */
			cwpIn.flushStaleMorseBits(inputNotify, true);

			/* Clear pending morse message */
			handleReceivedMorseMessageBuffer();

			cwpService.notifyFrequencyChange(newFreq);
		}

		public void stateChange(byte newState, int value) {
			boolean isUpState = newState == CWave.TYPE_UP;

			EventLog.d(TAG, "state-change, state: " + newState + ", value: "
					+ value);

			/* Report state change */
			if (recvStateUp != isUpState) {
				recvStateUp = isUpState;

				cwpService.notifyStateChange(recvStateUp, sendStateUp);
			}
		}

		public void morseMessage(BitString morseBits) {
			EventLog.d(TAG, "morse-message: " + morseBits.toString());

			/* Gather all message bits */
			if (morseMessageBits != null)
				morseMessageBits = morseMessageBits.append(morseBits);
			else
				morseMessageBits = morseBits;

			/*
			 * Message is ended either with endSequence (our internal
			 * implementation specific code) or by endContact (by sender)
			 */
			if (morseMessageBits.endWith(MorseCodec.endSequence)
					|| morseMessageBits.endWith(MorseCodec.endContact)) {
				handleReceivedMorseMessageBuffer();
			}
		}
	};

	/** Handle callbacks from CWOutput */
	private final CWOutputNotification outputNotify = new CWOutputNotification() {
		private static final String TAG = "CWPControlThread:outputNotify";

		public void frequencyChange(long newFreq) {
			EventLog.d(TAG, "freq-change: " + newFreq);

			cwpService.notifyFrequencyChange(newFreq);
		}

		public void stateChange(byte newState, int value) {
			boolean isUpState = newState == CWave.TYPE_UP;

			EventLog.d(TAG, "state-change, state: " + newState + ", value: "
					+ value);

			/* Report state change */
			if (sendStateUp != isUpState) {
				sendStateUp = isUpState;

				cwpService.notifyStateChange(recvStateUp, sendStateUp);
			}
		}
	};

	@Override
	public void run() {
		while (!isThreadKilled.get())
			run_loop();

		/*
		 * Parent has killed CWP thread, do clean up
		 */

		cwpIn = null;
		cwpOut = null;

		resetServerConnection();

		msgQueue.clear();

		try {
			selector.close();
		} catch (IOException e) {
			EventLog.w(
					TAG,
					"run()/cleanup: selector.close() exception: "
							+ e.toString());
		}
	}

	/** Signal and wait thread to quit work */
	public void endWorkAndJoin() {
		isThreadKilled.set(true);
		interrupt();

		try {
			join();
		} catch (InterruptedException ie) {
			/* UI-thread interrupted from wait */
			EventLog.w(TAG,
					"endWorkAndJoin(): joining ioThread failed with exception: "
							+ ie.toString());
		}
	}

	/** Set up new configuration for server */
	public void setNewConfiguration(String hostName, int hostPort,
			int morseSpeed) {
		/* Push new configuration as message to IO-thread */
		queuePush(msgQueue, CWPThreadValue.buildConfiguration(hostName,
				hostPort, morseSpeed));

		/* signal IO-thread of new message */
		interrupt();
	}

	/** Set new frequency */
	public void setFrequency(long freq) {
		/* Push new frequency to IO-thread */
		queuePush(msgQueue, CWPThreadValue.buildFreqChange(freq));

		/* signal IO-thread of new message */
		interrupt();
	}

	/** Set sending state */
	public void setSendingState(boolean setUpState) {
		/* Push new frequency to IO-thread */
		queuePush(msgQueue, CWPThreadValue.buildStateChange(setUpState));

		/* signal IO-thread of new message */
		interrupt();
	}

	/** Set to send morse message */
	public void sendMorseMessage(String morse) {
		/* Push new frequency to IO-thread */
		queuePush(msgQueue, CWPThreadValue.buildMorseMessage(morse));

		/* signal IO-thread of new message */
		interrupt();
	}

	/** Request current state from IO-thread */
	public void requestCurrentState() {
		/* Push new frequency to IO-thread */
		queuePush(msgQueue, CWPThreadValue.buildStateRequest());

		/* signal IO-thread of new message */
		interrupt();
	}

	/** Request to clear received morse messages */
	public void requestClearMessages() {
		/* Push new frequency to IO-thread */
		queuePush(msgQueue, CWPThreadValue.buildClearMessages());

		/* signal IO-thread of new message */
		interrupt();
	}

	private static void queuePush(ArrayDeque<CWPThreadValue> queue,
			CWPThreadValue value) {
		synchronized (queue) {
			queue.push(value);
		}
	}

	private static CWPThreadValue queuePop(ArrayDeque<CWPThreadValue> queue) {
		try {
			synchronized (queue) {
				if (queue.size() == 0)
					return null;

				return queue.pop();
			}
		} catch (NoSuchElementException NSEE) {
			/* empty queue */
			return null;
		}
	}

	/** Class for passing messages from UI-thread to IO-thread */
	private static class CWPThreadValue {
		protected static final int TYPE_CONFIGURATION = 0;
		protected static final int TYPE_STATE_CHANGE = 1;
		protected static final int TYPE_FREQ_CHANGE = 2;
		protected static final int TYPE_MORSE_MESSAGE = 3;
		protected static final int TYPE_STATE_REQUEST = 4;
		protected static final int TYPE_CLEAR_MESSAGES = 5;

		protected int type;
		protected long argLong0;
		protected int argInt0;
		protected Object argObj0;

		protected static CWPThreadValue buildConfiguration(String hostName,
				int hostPort, int morseSpeed) {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_CONFIGURATION;
			value.argObj0 = hostName;
			value.argLong0 = hostPort;
			value.argInt0 = morseSpeed;

			return value;
		}

		protected static CWPThreadValue buildStateChange(boolean isStateUp) {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_STATE_CHANGE;
			value.argObj0 = null;
			value.argLong0 = isStateUp ? 1 : 0;
			value.argInt0 = 0;

			return value;
		}

		protected static CWPThreadValue buildFreqChange(long freq) {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_FREQ_CHANGE;
			value.argObj0 = null;
			value.argLong0 = freq;
			value.argInt0 = 0;

			return value;
		}

		protected static CWPThreadValue buildMorseMessage(String morse) {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_MORSE_MESSAGE;
			value.argObj0 = morse;
			value.argLong0 = 0;
			value.argInt0 = 0;

			return value;
		}

		protected static CWPThreadValue buildStateRequest() {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_STATE_REQUEST;
			value.argObj0 = null;
			value.argLong0 = 0;
			value.argInt0 = 0;

			return value;
		}

		protected static CWPThreadValue buildClearMessages() {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_CLEAR_MESSAGES;
			value.argObj0 = null;
			value.argLong0 = 0;
			value.argInt0 = 0;

			return value;
		}

		/*
		 * Values for TYPE_CONFIGURATION
		 */
		protected String getHostName() {
			return (String) argObj0;
		}

		protected int getHostPort() {
			return (int) argLong0;
		}

		protected int getMorseSpeed() {
			return argInt0;
		}

		/*
		 * Values for TYPE_STATE_CHANGE
		 */
		protected boolean isStateUp() {
			return argLong0 == 1;
		}

		/*
		 * Values for TYPE_FREQUENCY_CHANGE
		 */
		protected long getFrequency() {
			return argLong0;
		}

		/*
		 * Values for TYPE_MORSE_MESSAGE
		 */
		protected String getMorseMessage() {
			return (String) argObj0;
		}
	}
}
