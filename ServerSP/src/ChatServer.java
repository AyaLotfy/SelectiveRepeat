import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.Hashtable;
import java.util.Random;

public class ChatServer {

	static int timeOutDuration = 1000;

	static final String FileNotFoundMessage = "Error: File Not Found";
	static final String confingFileName = "server.in";
	static final String OKMessage = "OK";
	static final int chunkSize = 5; // 5oo byte every time

	// default configuration value
	static int PORT = 9999;
	static long seed = 0;
	static double prob = 1.0;
	static int cwnd = 1;
	static int MSS = 5;
	static int dupACKcount = 0;
	static int ssthreash = 5;

	static boolean ackPackets[];
	static int currentPackNo = 0; // next packet to be sent
	static Hashtable<Integer, Thread> hashTimers; // save active timers
	static byte[] fileContent;
	static int numberOfPackets;
	static Semaphore mutex;

	static Random rand;

	public static void main(String[] args) {

		setParameters();

		rand = new Random(seed);
		mutex = new Semaphore(1);

		byte[] buf = new byte[1000];
		DatagramPacket dgp;
		DatagramSocket sk;

		try {
			sk = new DatagramSocket(PORT);

			System.out.println("Server started");
			while (true) {
				// recieve a request
				System.out.println("Waiting for request");
				dgp = new DatagramPacket(buf, buf.length);
				sk.receive(dgp);

				// get file name requested
				String filename = new String(dgp.getData(), 0, dgp.getLength());
				String rcvd = filename + ", from address: " + dgp.getAddress() + ", port: " + dgp.getPort();
				System.out.println(rcvd);

				// search for file
				File file;

				try {
					file = new File(filename);
					fileContent = Files.readAllBytes(file.toPath());
				} catch (FileNotFoundException e) {
					sendStrToClient(FileNotFoundMessage, sk, dgp);
					continue;
				}

				// send file using stop and wait strategy
				sendFileToClient(fileContent, dgp, sk);

				System.out.println("file is sent");
			}
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private static void setParameters() {
		try {
			Scanner scan = new Scanner(new File(confingFileName));

			PORT = scan.nextInt();
			seed = scan.nextLong();

			MSS = scan.nextInt();

			double probSpec = scan.nextDouble();
			if (probSpec >= 0 && probSpec <= 1)
				prob = 1 - probSpec;
			else
				System.out.println("Probability is not correct");
			scan.close();
		} catch (FileNotFoundException e) {
			System.out.println("No config file found");
			e.printStackTrace();
		}

	}

	// send file using selective repeat approach
	public static void sendFileToClient(byte[] fileContent, DatagramPacket dgp, DatagramSocket sk) {

		System.out.println("file size " + fileContent.length);

		numberOfPackets = (int) Math.ceil(fileContent.length / chunkSize);
		ackPackets = new boolean[numberOfPackets];
		cwnd = 1;
		currentPackNo = 0;
		ssthreash = MSS;
		hashTimers = new Hashtable<Integer, Thread>();

		// send ok message with number of packets
		sendStrToClient(OKMessage + " " + numberOfPackets, sk, dgp);

		// send packet no. 1
		sendNewPackets(dgp, sk);

		while (true) {
			System.out.println("waiting for ack");
			AckPacket packet = getAck(sk);
			System.out.println("Ack recieved");

			// stoping condition
			if (packet.ackno == numberOfPackets - 1) {
				killTimers();
				return;
			}

			// ack is received
			try {
				mutex.acquire();
				// System.out.println("acqiure mutex: ");

				// check if duplicate
				if (isDuplicateAck(packet)) {
					dupACKcount++;
					System.out.println("duplicat ack: " + packet.ackno);
					if (dupACKcount == 3) {
						ssthreash = cwnd / 2;
						cwnd = ssthreash + 3;
						sendMissingPacket(dgp, sk, packet.ackno);
					} else if (dupACKcount > 3) {
						cwnd++;
						sendNewPackets(dgp, sk);
					}
				} else {
					System.out.println("new ack: " + packet.ackno);
					// new ack

					dupACKcount = 0;

					ackAllPacketsBefore(packet.ackno);

					if (dupACKcount >= 3) {
						cwnd = ssthreash;
					} else {
						cwnd++;
						sendNewPackets(dgp, sk);
					}
				}

				mutex.release();
				// System.out.println("release mutex: ");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static void ackAllPacketsBefore(int ackno) {
		int i = ackno;

		while ( i >= 0 && !ackPackets[i]) {
			ackPackets[i] = true;
			// kill its timer
			Thread t = hashTimers.remove(i);
			t.interrupt();
			i--;
		}
	}

	private static void sendMissingPacket(DatagramPacket dgp, DatagramSocket sk, int ackno) {
		hashTimers.remove(ackno + 1).interrupt();

		Timer timer = new Timer(ackno + 1, dgp, sk, timeOutDuration);
		hashTimers.put(ackno + 1, timer);
		sendPacket(dgp, sk, ackno + 1);
		timer.start();
	}

	private static void killTimers() {
		for (Integer x : hashTimers.keySet()) {
			hashTimers.get(x).interrupt();
		}
	}

	public static void handleTimeOut(int packetNo, DatagramPacket dgp, DatagramSocket sk) {

		try {
			System.out.println("handle time out: " + packetNo);
			mutex.acquire();

			// System.out.println("acquire mutex timeout packet: " + packetNo);
			// using sophomore
			ssthreash = cwnd / 2;
			cwnd = 1;
			dupACKcount = 0;

			sendPacket(dgp, sk, packetNo);

			mutex.release();
			// System.out.println("release mutex timeout packet: " + packetNo);

		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	// send packet with packetNo
	public static void sendPacket(DatagramPacket dgp, DatagramSocket sk, int packetNo) {
		int size;
		if (packetNo * chunkSize + chunkSize > fileContent.length)
			size = fileContent.length - packetNo * chunkSize;
		else
			size = chunkSize;

		System.out.printf("send %d from %d, size %d\n", packetNo, packetNo * chunkSize, size);

		byte[] part = new byte[size];
		System.arraycopy(fileContent, packetNo * chunkSize, part, 0, size);

		DataPacket packet = new DataPacket(part, size, packetNo);

		if (lossSim())
			sendObjectToClient(packet, dgp.getAddress(), dgp.getPort(), sk);
		else
			System.out.println("packet is not sent: " + packetNo);
	}

	// send cwnd packets where
	public static void sendNewPackets(DatagramPacket dgp, DatagramSocket sk) {

		for (int i = 0; i < cwnd; i++) {

			if (currentPackNo >= numberOfPackets)
				return;

			sendPacket(dgp, sk, currentPackNo);

			// create thread timeout and put in hashtable
			Timer timer = new Timer(currentPackNo, dgp, sk, timeOutDuration);
			hashTimers.put(currentPackNo, timer);
			timer.start();

			// increase currentPacketNo
			currentPackNo++;
		}
	}

	// simulate loss
	private static boolean lossSim() {

		int n = rand.nextInt(10); // n ==> 0...9

		// System.out.println("number generated: " + n);

		if (n < prob * 10)
			return true;

		return false;
	}

	// wait for ack
	// it is a blocking function
	public static AckPacket getAck(DatagramSocket dSock) {
		Object recievedObj = recvObjFrom(dSock);

		if (recievedObj != null) {
			try {
				AckPacket ack = (AckPacket) recievedObj;
				return ack;
			} catch (Exception e) {
			}
		}
		return null;
	}

	public static boolean isDuplicateAck(AckPacket ackPacket) {
		if (ackPackets[ackPacket.ackno])
			return true;
		return false;
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
		} catch (SocketTimeoutException e) {
			// timeout exception.
			System.out.println("Timeout reached!!! " + e);
		} catch (IOException e) {
			System.err.println("Exception:  " + e);
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return (null);
	}

	public static void sendStrToClient(String str, DatagramSocket sk, DatagramPacket dgp) {
		byte[] buf = new byte[1000];
		buf = (str).getBytes();
		DatagramPacket out = new DatagramPacket(buf, buf.length, dgp.getAddress(), dgp.getPort());
		try {
			sk.send(out);
		} catch (IOException e) {
			System.out.println("Cannot send response to client");
			e.printStackTrace();
		}
	}

	public static void scanAndSend() {
		byte[] buf = new byte[1000];
		DatagramPacket dgp = new DatagramPacket(buf, buf.length);
		DatagramSocket sk;

		try {
			sk = new DatagramSocket(PORT);
			System.out.println("Server started");
			while (true) {
				sk.receive(dgp);
				String rcvd = new String(dgp.getData(), 0, dgp.getLength()) + ", from address: " + dgp.getAddress()
						+ ", port: " + dgp.getPort();
				System.out.println(rcvd);

				BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
				String outMessage = stdin.readLine();
				buf = ("Server say: " + outMessage).getBytes();
				DatagramPacket out = new DatagramPacket(buf, buf.length, dgp.getAddress(), dgp.getPort());
				sk.send(out);
			}
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static void sendObjectToClient(Object o, InetAddress address, int desPort, DatagramSocket dSock) {
		try {
			// DatagramSocket dSock = new DatagramSocket(PORT);
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
}