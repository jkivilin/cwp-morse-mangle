package fi_81.cwp_morse_mangle.cwp;

public class CWave implements Comparable<CWave> {
	public static final byte TYPE_UP = 1;
	public static final byte TYPE_DOWN = -1;

	protected byte type;
	protected int duration;

	public CWave(byte type, int duration) {
		this.type = type;
		this.duration = duration;
	}

	public int compareTo(CWave o) {
		int diff = this.duration - o.duration;

		if (diff != 0)
			return diff;

		if (o.type == this.type)
			return 0;
		if (o.type == TYPE_UP)
			return 1;
		else
			return -1;
	}
}