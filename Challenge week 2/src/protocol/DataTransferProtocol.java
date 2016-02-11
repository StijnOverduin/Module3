package protocol;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import client.Utils;

public class DataTransferProtocol extends IRDTProtocol {

	Set<Integer[]> packets = new HashSet<Integer[]>();
	private int packetNumber = 0;
	private Integer[] pkt;

	// change the following as you wish:
	static final int HEADERSIZE = 1; // number of header bytes in each packet
	static final int DATASIZE = 400; // max. number of user data bytes in each
										// packet

	@Override
	public void sender() {
		System.out.println("Sending...");
		// read from the input file
		Integer[] fileContents = Utils.getFileContents(getFileID());
		// keep track of where we are in the data
		int filePointer = 0;
		while (packetNumber < fileContents.length / DATASIZE + 1) {

			// create a new packet of appropriate size
			int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
			pkt = new Integer[HEADERSIZE + datalen];
			packets.add(pkt);
			// write something random into the header byte
			pkt[0] = packetNumber;
			// copy databytes from the input file into data part of the packet,
			// i.e., after the header
			System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);

			// send the packet to the network layer
			getNetworkLayer().sendPacket(pkt);
			System.out.println("Sent one packet with header=" + pkt[0]);

			// schedule a timer for 1000 ms into the future, just to show how
			// that works:
			client.Utils.Timeout.SetTimeout(4000, this, packetNumber);

			// and loop and sleep; you may use this loop to check for incoming
			// acks...

			boolean stop = false;
			while (!stop) {
				Integer[] ackPkt = getNetworkLayer().receivePacket();
				if (ackPkt != null && ackPkt[0] == packetNumber) {
					for (Integer[] entry : packets) {
						if (entry[0] == ackPkt[0]) {
							System.out.println("got ack");
							packets.remove(entry);
							filePointer = filePointer + DATASIZE;
							packetNumber++;
							stop = true;
						}
					}
				}

				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					stop = true;
				}
			}
		}
		Integer[] lastPkt = new Integer[HEADERSIZE];
		lastPkt[0] = 99;
		getNetworkLayer().sendPacket(lastPkt);
	}

	@Override
	public void TimeoutElapsed(Object tag) {
		int z = (Integer) tag;
		// handle expiration of the timeout:
		for (Integer[] entry : packets) {
			if (entry[0] == tag) {
				getNetworkLayer().sendPacket(pkt);
				client.Utils.Timeout.SetTimeout(6000, this, packetNumber);
			}
		}
		System.out.println("Timer expired with tag=" + z);
	}

	@Override
	public void receiver() {
		Set<Integer> receivedHeaders = new HashSet<Integer>();
		System.out.println("Receiving...");

		// create the array that will contain the file contents
		// note: we don't know yet how large the file will be, so the easiest
		// (but not most efficient)
		// is to reallocate the array every time we find out there's more data
		Integer[] fileContents = new Integer[0];

		// loop until we are done receiving the file
		boolean stop = false;
		while (!stop) {
			// try to receive a packet from the network layer
			Integer[] packet = getNetworkLayer().receivePacket();
			

			// if we indeed received a packet
			if (packet != null) {
					if (!receivedHeaders.contains(packet[0])) {
						receivedHeaders.add(packet[0]);
						Integer[] ackPkt = new Integer[HEADERSIZE];
						ackPkt[0] = packet[0];
						getNetworkLayer().sendPacket(ackPkt);
						// tell the user
						System.out.println("Received packet, length=" + packet.length + "  first byte=" + packet[0]);

						// append the packet's data part (excluding the header)
						// to the fileContents array, first making it larger
						int oldlength = fileContents.length;
						int datalen = packet.length - HEADERSIZE;
						fileContents = Arrays.copyOf(fileContents, oldlength + datalen);
						System.arraycopy(packet, HEADERSIZE, fileContents, oldlength, datalen);
						if (packet[0] == 99) {
							stop = true;
						}
					}
				// and let's just hope the file is now complete
			} else {
				// wait ~10ms (or however long the OS makes us wait) before
				// trying again
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					stop = true;
				}
			}
		}

		// write to the output file
		Utils.setFileContents(fileContents, getFileID());
	}
}
