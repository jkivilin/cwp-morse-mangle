package fi_81.cwp_morse_mangle.cwp;

public class CWFrequencyChange extends CWStateChange {
	public CWFrequencyChange(long newFreq) {
		/*
		 * Register with correct type and add timestamp of 0 (making sending
		 * logic to pass this forward immediately.
		 */
		super(CWStateChange.TYPE_FREQUENCY_CHANGE, (int) (-newFreq), 0);
	}

	public long getFrequency() {
		/*
		 * Must cast to 'long' before negation, integer negation of
		 * Integer.MIN_VALUE results overflow back to Integer.MIN_VALUE.
		 */
		return -(long) value;
	}
}
