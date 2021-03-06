package protocol;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import client.Utils;

public class DataTransferProtocol extends IRDTProtocol {

	Set<Integer[]> packets = new HashSet<Integer[]>();
	private int packetNumber = 0;
	private Integer[] pkt;
	private int packetAmount;

	// change the following as you wish:
	static final int HEADERSIZE = 1; // number of header bytes in each packet
	static final int DATASIZE = 512; // max. number of user data bytes in each
									// packet

	@Override
	public void sender() {
		System.out.println("Sending...");
		// read from the input file
		Integer[] fileContents = Utils.getFileContents(getFileID());
		// keep track of where we are in the data
		int filePointer = 0;
		this.packetAmount = fileContents.length / DATASIZE + 1;
		while (packetNumber < packetAmount) {
			int datalen = Math.min(DATASIZE, fileContents.length - filePointer);
			pkt = new Integer[HEADERSIZE + datalen];
			// write something random into the header byte
			pkt[0] = packetNumber;
			// copy databytes from the input file into data part of the packet,
			// i.e., after the header
			System.arraycopy(fileContents, filePointer, pkt, HEADERSIZE, datalen);
			packets.add(pkt);
			packetNumber++;
			filePointer = filePointer + DATASIZE;
		}
		sendPackets();
		client.Utils.Timeout.SetTimeout(15000, this, packets.size() - 1);

		// getNetworkLayer().sendPacket(pkt);
		// System.out.println("Sent one packet with header=" + pkt[0]);

		// schedule a timer for 1000 ms into the future, just to show how
		// that works:

		// and loop and sleep; you may use this loop to check for incoming
		// acks..

		boolean stop = false;
		while (!stop) {
			Integer[] ackPkt = getNetworkLayer().receivePacket();
			if (ackPkt != null) {
				Iterator<Integer[]> itje = packets.iterator();
				while (itje.hasNext()) {
					Integer[] entry = itje.next();
					if (entry[0] == ackPkt[0]) {
						System.out.println("got ack");
						itje.remove();
						if (ackPkt[0] == 255) {
							stop = true;
						}
						if (packets.size() == 0) {
							Integer[] lastPkt = new Integer[HEADERSIZE];
							lastPkt[0] = 255;
							getNetworkLayer().sendPacket(lastPkt);

						}
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

	public void sendPackets() {
		System.out.println("Packets send");
		for (Integer[] pkt : packets) {
			getNetworkLayer().sendPacket(pkt);
		}
	}

	@Override
	public void TimeoutElapsed(Object tag) {
		int z = (Integer) tag;
		// handle expiration of the timeout:
				sendPackets();
				client.Utils.Timeout.SetTimeout(15000, this, tag);
		System.out.println("Timer expired with tag=" + z);
	}

	@Override
	public void receiver() {
		Set<Integer> receivedHeaders = new HashSet<Integer>();
		Map<Integer, Integer[]> receivedPkts = new HashMap<Integer, Integer[]>();
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
				Integer[] ackPkt = new Integer[HEADERSIZE];
				ackPkt[0] = packet[0];
				getNetworkLayer().sendPacket(ackPkt);
				if (!receivedHeaders.contains(packet[0])) {
					// tell the user
					System.out.println("Received packet, length=" + packet.length + "  first byte=" + packet[0]);

					// append the packet's data part (excluding the header)
					// to the fileContents array, first making it larger
					
					if (packet[0] == 255) {
						fileContents = sortMap(receivedPkts);
						stop = true;
				} else {
					receivedPkts.put(packet[0], packet);
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

	public Integer[] sortMap(Map<Integer, Integer[]> unsortedMap) {
		int counter = 0;
		Integer[] fileContents = new Integer[0];
		for (int i = 0; i < unsortedMap.size(); i++) {
			for (Integer k : unsortedMap.keySet()) {
				if (k == counter) {
					int oldlength = fileContents.length;
					int datalen = unsortedMap.get(k).length - HEADERSIZE;
					fileContents = Arrays.copyOf(fileContents, oldlength + datalen);
					System.arraycopy(unsortedMap.get(k), HEADERSIZE, fileContents, oldlength, datalen);
					counter++;
				}
			}
		}
		return fileContents;
	}

}
