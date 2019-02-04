import java.io.Serializable;

public class DataPacket implements Serializable {

	int ckSum, len, seqno;
	byte[] data;

	public DataPacket(byte[] data, int len, int seqno) {
		this.data = data;
		this.len = len;
		this.seqno = seqno;
	}

}

