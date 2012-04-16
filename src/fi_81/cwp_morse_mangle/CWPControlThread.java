package fi_81.cwp_morse_mangle;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

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

	/* Synchronized queue used for passing data to IO-thread */
	private Vector<CWPThreadValue> msgQueue = new Vector<CWPThreadValue>(16);

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
	private CWInput cwpIn;
	private CWOutput cwpOut;
	private boolean busySendingMorseMessage = false;

	/* Send and receive channel states */
	private boolean recvStateUp = false;
	private boolean sendStateUp = false;
	private long currFrequency = 1;
	private BitString morseMessageBits;
	private final StringBuffer morseMessage = new StringBuffer();

	/* Parent service */
	private CWPControlService cwpService;

	/** Service reference is needed for callbacks */
	public CWPControlThread(CWPControlService service) {
		try {
			selector = Selector.open();
		} catch (IOException e) {
			LogF.e(TAG, "CWPControlThread(): Selector.open() failed: %s",
					e.toString());
			System.exit(1);
		}

		cwpService = service;
	}

	/** Disconnect from server cleanly and setup to resolve hostname */
	private void resetServerConnection() {
		LogF.d(TAG, "resetServerConnection()");

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

		recvStateUp = false;
		sendStateUp = false;
		currFrequency = 1;
		busySendingMorseMessage = false;
		morseMessageBits = null;
	}

	/** Main loop of thread */
	private void run_loop() {
		try {
			LogF.d(TAG, "run_loop(): %d", connState);

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
					LogF.w(TAG, "Server connection IOException: %s",
							e.toString());

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
			sleep(200);
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
		connChannel.register(selector, SelectionKey.OP_READ);

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
		int timeToNextOutWork, numReadyChannels;

		/* CWP output handling */
		cwpOut.processOutput(outputNotify);

		/* Check if need to register write-channel to selector */
		if (cwpOut.getOutputBuffer().remaining() > 0) {
			try {
				connChannel.register(selector, SelectionKey.OP_WRITE);
			} catch (CancelledKeyException cke) {
				/*
				 * Tried to register key that was cancelled in previous loop.
				 * Clear cancelled state with selectNow().
				 */
				selector.selectNow();
				connChannel.register(selector, SelectionKey.OP_WRITE);
			}
		}

		/* Get time to next CWOutput work */
		timeToNextOutWork = cwpOut.timeToNext();

		/* Wait for input */
		if (timeToNextOutWork == 0)
			numReadyChannels = selector.selectNow();
		else
			numReadyChannels = selector.select(timeToNextOutWork < 0 ? 0
					: timeToNextOutWork);

		/* Receive and send data */
		if (numReadyChannels > 0) {
			handleNonBlockingNetworkIO();

			if (busySendingMorseMessage && cwpOut.queueSize() == 0
					&& cwpOut.getOutputBuffer().remaining() == 0) {
				/* Sending morse message completed */
				busySendingMorseMessage = false;

				cwpService.notifyMorseMessageComplete();
			}
		}

		/* CWP input handling */
		cwpIn.processInput(inputNotify);
	}

	private void handleNonBlockingNetworkIO() throws IOException {
		Iterator<SelectionKey> keyIter = selector.selectedKeys().iterator();
		int bytesCopied;

		/* Iterate active selection keys */
		while (keyIter.hasNext()) {
			SelectionKey key = keyIter.next();

			/* Input reader */
			if (key.isValid() && key.isReadable()) {
				bytesCopied = connChannel.read(cwpIn.getInBuffer());

				LogF.d(TAG, "handleNonBlockingNetworkIO(): read %d bytes",
						bytesCopied);
			}

			/* Output writer */
			if (key.isValid() && key.isWritable()) {
				bytesCopied = connChannel.write(cwpOut.getOutputBuffer());

				LogF.d(TAG, "handleNonBlockingNetworkIO(): written %d bytes",
						bytesCopied);

				/* Output buffer emptied, stop writing */
				if (cwpOut.getOutputBuffer().remaining() == 0)
					key.cancel();
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
				cwpService.notifyFrequencyChange(currFrequency);
				cwpService.notifyStateChange(recvStateUp, sendStateUp);
				cwpService.notifyMorseUpdates(morseMessage.toString());
				break;
			case CWPThreadValue.TYPE_CLEAR_MESSAGES:
				morseMessage.setLength(0);
				cwpService.notifyMorseUpdates("");
				break;
			}
		}

		/* Push unused value to memory pool */
		/*
		 * if (freeQueue.size() < 16) { queuePush(freeQueue, value.clear()); }
		 */
	}

	private void handleNewMorseMessage(BitString morseMessage) {
		if (connState == CONN_CONNECTED && cwpOut != null) {
			/* Caller should make sure this does not happen */
			if (busySendingMorseMessage)
				return;

			busySendingMorseMessage = true;

			/* Fill in morse message */
			cwpOut.sendDown();
			cwpOut.sendMorseCode(morseMessage);
		} else {
			/* Just complete morse message sending when not connected */
			cwpService.notifyMorseMessageComplete();
		}
	}

	private void handleNewFrequency(long frequency) {
		if (currFrequency != frequency) {
			LogF.d(TAG, "handleNewFrequency: %d", frequency);

			currFrequency = frequency;

			if (connState == CONN_CONNECTED && cwpOut != null)
				cwpOut.sendFrequenceChange(currFrequency);
		}
	}

	private void handleNewSendingState(boolean stateUp) {
		if (busySendingMorseMessage)
			return;

		if (connState == CONN_CONNECTED && cwpOut != null) {
			LogF.d(TAG, "handleNewSendingState: %b", stateUp);

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

	/** Combined handling of */

	/** Handle callbacks from CWInput */
	private final CWInputNotification inputNotify = new CWInputNotification() {
		private static final String TAG = "CWPControlThread:inputNotify";

		public void frequencyChange(long newFreq) {
			LogF.d(TAG, "freq-change: %d", newFreq);

			cwpService.notifyFrequencyChange(newFreq);
		}

		public void stateChange(byte newState, int value) {
			boolean isUpState = newState == CWave.TYPE_UP;

			LogF.d(TAG, "state-change, state: %d, value: %d", newState, value);

			/* Report state change */
			if (recvStateUp != isUpState) {
				recvStateUp = isUpState;

				cwpService.notifyStateChange(recvStateUp, sendStateUp);
			}
		}

		public void morseMessage(BitString morseBits) {
			LogF.d(TAG, "morse-message: %s [%s]", morseBits.toString(),
					MorseCodec.decodeMorseToMessage(morseBits));

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
				morseMessageBits = morseMessageBits.append(BitString
						.newZeros(3));

				String message = MorseCodec
						.decodeMorseToMessage(morseMessageBits);

				LogF.d(TAG, "Received morse-message: '%s'", message);

				/* Fill to main message buffer */
				morseMessage.append(' ');
				for (char ch : message.toCharArray()) {
					/* Handle SOS specially */
					if (ch == MorseCharList.SPECIAL_SOS) {
						morseMessage.append("¡SOS!");
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

					morseMessage.append(ch);
				}

				/* Send updated morse-message string to UI */
				cwpService.notifyMorseUpdates(morseMessage.toString());

				morseMessageBits = null;
			}
		}
	};

	/** Handle callbacks from CWOutput */
	private final CWOutputNotification outputNotify = new CWOutputNotification() {
		private static final String TAG = "CWPControlThread:outputNotify";

		public void frequencyChange(long newFreq) {
			LogF.d(TAG, "freq-change: %d", newFreq);

			cwpService.notifyFrequencyChange(newFreq);
		}

		public void stateChange(byte newState, int value) {
			boolean isUpState = newState == CWave.TYPE_UP;

			LogF.d(TAG, "state-change, state: %d, value: %d", newState, value);

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
		msgQueue = null;

		try {
			selector.close();
		} catch (IOException e) {
			LogF.w(TAG, "run()/cleanup: selector.close() exception: %s",
					e.toString());
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
			LogF.w(TAG, "endWorkAndJoin(): joining ioThread failed [%s]",
					ie.toString());
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
	public void sendMorseMessage(BitString morseBits) {
		/* Push new frequency to IO-thread */
		queuePush(msgQueue, CWPThreadValue.buildMorseMessage(morseBits));

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

	private static void queuePush(Vector<CWPThreadValue> msgQueue,
			CWPThreadValue value) {
		msgQueue.add(value);
	}

	private static CWPThreadValue queuePop(Vector<CWPThreadValue> msgQueue) {
		try {
			return msgQueue.remove(0);
		} catch (IndexOutOfBoundsException IOOBE) {
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

		protected static CWPThreadValue buildMorseMessage(BitString morseBits) {
			CWPThreadValue value = new CWPThreadValue();

			value.type = TYPE_MORSE_MESSAGE;
			value.argObj0 = morseBits;
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
		protected BitString getMorseMessage() {
			return (BitString) argObj0;
		}
	}
}
