import java.io.Serializable;

public class AckPacket implements Serializable{
	int cksum, len, ackno;
	
	public AckPacket(int len, int ackno) {
		this.len = len;
		this.ackno = ackno;
	}
}
