package fi_81.cwp_morse_mangle.cwp;

public class CWFrequencyChange extends CWStateChange {
	public CWFrequencyChange(long newFreq) {
		super(CWStateChange.TYPE_FREQUENCY_CHANGE, (int) (-newFreq), 0);
	}

	public long getFrequency() {
		return -value;
	}
}
