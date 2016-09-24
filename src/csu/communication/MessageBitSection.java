package csu.communication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import rescuecore2.misc.Pair;

/**
 * This class handle the bit section of a meassage which involves priority,
 * headerNumber, bitLength and dataSizePair of message.
 * <p>
 * An RCR Agent communicates with kernel through a network using the RCRSS
 * protocol. A data unit for the RCRSS protocol is called a block which consists
 * of a header, body length, and body field, and a packet of the RCRSS protocol
 * consists of zero or more blocks and a HEADER_NULL as a terminator.
 * <p>
 * The smaller the number value of priority, the higher the priority. So in our
 * code, we set 1 as highest priority.
 * 
 * @author apppreciation-csu
 * 
 */
public class MessageBitSection implements Comparable<MessageBitSection>, Iterable<Pair<Integer, Integer>> {

	/** The priority of this message. */
	private int priority;
	/**
	 * Header numbers of this message. And we will set it to the index of its
	 * Port in Port list.
	 */
	private int headerNumber;
	/** The time this message should remain. */
	private int timeToLive = 0;

	/** The bit lenght of this message. */
	private int bitLength = 0;
	/** Stores the data and size pair of each blocks. */
	final ArrayList<Pair<Integer, Integer>> datasizepairs = new ArrayList<Pair<Integer, Integer>>();

	/**
	 * To construct the object of MessageBitSection with the given priority.
	 * 
	 * @param priority
	 *            the priority of this packet
	 */
	public MessageBitSection(final int priority) {
		this.priority = priority;
	}

	/**
	 * Add a new data to this message.
	 * 
	 * @param data
	 *            the new data will be added
	 * @param n
	 *            the bit length of this new data
	 */
	public void add(final int data, final int n) {
		bitLength += n;
		datasizepairs.add(new Pair<Integer, Integer>(data, n));
	}

	/**
	 * Set the priority of this message.
	 * 
	 * @param priority
	 *            the priority of this message
	 */
	public void setPriority(int priority) {
		this.priority = priority;
	}

	/**
	 * Compare the priority of this message to a specified message.
	 */
	@Override
	public int compareTo(MessageBitSection o) {
		return this.priority - o.priority;
	}

	/**
	 * Set the header number of this message.
	 * 
	 * @param headerNumber
	 *            the new value of header number
	 */
	public void setHeaderNumber(int headerNumber) {
		this.headerNumber = headerNumber;
	}

	/**
	 * Get the header number of this message.
	 * 
	 * @return the header number of this message
	 */
	public int getHeaderNumber() {
		return headerNumber;
	}
	
	public List<Pair<Integer, Integer>> getDataSizePair() {
		return Collections.unmodifiableList(datasizepairs);
	}

	@Override
	public Iterator<Pair<Integer, Integer>> iterator() {
		return datasizepairs.iterator();
	}

	/**
	 * Get the bit length of this message.
	 * 
	 * @return the bit length of this message
	 */
	public int getBitLength() {
		return bitLength;
	}

	/** Set the time this packet should live. */
	public void setTimeToLive(int time) {
		this.timeToLive = time;
	}

	/** Get the time this packet can live. */
	public int getTimeToLive() {
		return this.timeToLive;
	}

	/** Decrement the live time each cycle. */
	public void decrementTTL() {
		this.timeToLive--;
	}

	public int getPriority() {
		return this.priority;
	}

	@Override
	public String toString() {
		return "package with priority: " + priority + "";
	}
}
