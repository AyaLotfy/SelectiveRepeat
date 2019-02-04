import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class Timer extends Thread {
	int packetNo;
	DatagramPacket dgp;
	DatagramSocket sk;
	int timeOutDuration;

	Timer(int packetNo, DatagramPacket dgp, DatagramSocket sk, int timeOutDuration) {
		this.packetNo = packetNo;
		this.dgp = dgp;
		this.sk = sk;
		this.timeOutDuration = timeOutDuration;
	}

	public void run() {

		try {
			while (true) {

				Thread.sleep(timeOutDuration);

				// call time out function
				ChatServer.handleTimeOut(packetNo, dgp, sk);
			}
		} catch (InterruptedException e) {
			// e.printStackTrace();
			System.out.println("kill timer of packet: " + packetNo);
			
		}
	}
}
