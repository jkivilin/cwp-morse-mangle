package fi_81.cwp_morse_mangle.cwp;

public class CWFrequencyChange extends CWStateChange {
	public CWFrequencyChange(int freq) {
		super(CWStateChange.TYPE_FREQUENCY_CHANGE, -freq, 0);
	}

	public int getFrequency() {
		return -value;
	}
}
