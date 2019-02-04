import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Hashtable;

public class ChatClient {

	static final int PORT = 9999;
	static final String FileNotFoundMessage = "Error: File Not Found";
	static final String OKMessage = "OK";

	static Hashtable<Integer, DataPacket> hashPackets;

	public static void main(String[] args) {

		try {
			DatagramSocket s = new DatagramSocket();

			byte[] buf = new byte[1000];
			DatagramPacket dp = new DatagramPacket(buf, buf.length);

			InetAddress hostAddress = InetAddress.getByName("localhost");
			BufferedReader stdin;
			String filename;
			System.out.print("Please enter file name: ");
			stdin = new BufferedReader(new InputStreamReader(System.in));
			filename = stdin.readLine();

			String outString = filename;
			buf = outString.getBytes();

			// send file name
			DatagramPacket out = new DatagramPacket(buf, buf.length, hostAddress, PORT);
			s.send(out);

			// receive response
			s.receive(dp);
			String rcvd = new String(dp.getData(), 0, dp.getLength());

			if (rcvd.equals(FileNotFoundMessage)) {
				// file not found
				System.out.println("Server say: " + FileNotFoundMessage);
			} else {

				String[] parts = rcvd.split(" ");
				if (parts.length == 2 && parts[0].equals(OKMessage)) {
					int numOfChunks = Integer.valueOf(parts[1]);
					receiveFileFromSrever(filename, numOfChunks, s, out);
				} else {
					// message not recognized
					System.out.println("Server Message is not recognized: " + rcvd);
				}

			}

		} catch (SocketException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static void receiveFileFromSrever(String filename, int numOfChunks, DatagramSocket s, DatagramPacket dgp) {

		hashPackets = new Hashtable<Integer, DataPacket>();

		try {
			FileOutputStream fos = new FileOutputStream(filename);

			int currentPacket = 0; // packet number should be received next
			DataPacket packet;
			AckPacket ackPacket;

			while (currentPacket < numOfChunks) {

				if (hashPackets.containsKey(currentPacket)) {
					packet = hashPackets.remove(currentPacket++);
					fos.write(packet.data);
				} else {
					// send ack
					if (currentPacket != 0) {
						System.out.println("send ack: " + (currentPacket - 1));
						ackPacket = new AckPacket(0, currentPacket - 1);
						sendObjectToClient(ackPacket, dgp.getAddress(), dgp.getPort(), s);
					}

					packet = (DataPacket) recvObjFrom(s); // receive chunk
					System.out.println("recieved packet: " + packet.seqno);
					if (packet.seqno == (currentPacket)) {
						currentPacket++;
						fos.write(packet.data);
					} else {
						if (!hashPackets.containsKey(packet.seqno) && packet.seqno > currentPacket)
							hashPackets.put(packet.seqno, packet);
					}
				}
			}
			fos.close();
			// send ack of last packet
			System.out.println("send ack: " + (numOfChunks - 1));
			ackPacket = new AckPacket(0, numOfChunks - 1);
			sendObjectToClient(ackPacket, dgp.getAddress(), dgp.getPort(), s);

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static void sendObjectToClient(Object o, InetAddress address, int desPort, DatagramSocket dSock) {
		try {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream(5000);
			ObjectOutputStream os = new ObjectOutputStream(new BufferedOutputStream(byteStream));
			os.flush();
			os.writeObject(o);
			os.flush();
			// retrieves byte array
			byte[] sendBuf = byteStream.toByteArray();
			DatagramPacket packet = new DatagramPacket(sendBuf, sendBuf.length, address, desPort);
			int byteCount = packet.getLength();
			dSock.send(packet);
			os.close();
		} catch (UnknownHostException e) {
			System.err.println("Exception:  " + e);
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void recieveAndSend() {
		try {
			DatagramSocket s = new DatagramSocket();

			byte[] buf = new byte[1000];
			DatagramPacket dp = new DatagramPacket(buf, buf.length);

			InetAddress hostAddress = InetAddress.getByName("localhost");
			while (true) {
				BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
				String outMessage = stdin.readLine();

				if (outMessage.equals("bye"))
					break;
				String outString = "Client say: " + outMessage;
				buf = outString.getBytes();

				DatagramPacket out = new DatagramPacket(buf, buf.length, hostAddress, PORT);
				s.send(out);

				s.receive(dp);
				String rcvd = "rcvd from " + dp.getAddress() + ", " + dp.getPort() + ": "
						+ new String(dp.getData(), 0, dp.getLength());
				System.out.println(rcvd);
			}

		} catch (SocketException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static Object recvObjFrom(DatagramSocket dSock) {
		try {
			// DatagramSocket dSock = new DatagramSocket(PORT);
			byte[] recvBuf = new byte[5000];
			DatagramPacket packet = new DatagramPacket(recvBuf, recvBuf.length);
			dSock.receive(packet);
			int byteCount = packet.getLength();
			ByteArrayInputStream byteStream = new ByteArrayInputStream(recvBuf);
			ObjectInputStream is = new ObjectInputStream(new BufferedInputStream(byteStream));
			Object o = is.readObject();
			is.close();
			return (o);
		} catch (IOException e) {
			System.err.println("Exception:  " + e);
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return (null);
	}

}