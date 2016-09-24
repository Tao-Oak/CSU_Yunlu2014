package csu.communication;

import csu.communication.MessageConstant.MessageReportedType;
import csu.io.BitArrayInputStream;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

/**
 * An interface for message. Each implementation of this interface can be
 * regared as a kind of this message. And you should handle each kind of
 * messages respectively.
 * 
 * @see MessageBitSection
 * @see MessageConstant.MessageReportedType
 */
public interface Port {
	/**
	 * Initialize of this <code>Port</code>. When writing message, this method
	 * is invoked first and create a new packet this cycle should report. It
	 * will create nothing when there is no new message to reporte.
	 * 
	 * @param changed
	 *            the change set
	 */
	public void init(final ChangeSet changed);

	/**
	 * Determines whether this <code>Port</code> still has packet to write.
	 * Gernelly, each <code>Port</code> has one packet to write in each cycle.
	 */
	public boolean hasNext();

	/**
	 * This method return a packet of this kind of message.
	 * 
	 * @return a packet of this kind of message
	 */
	public MessageBitSection next();

	/**
	 * This method used to read the packet contents of this <code>Port</code>.
	 * 
	 * @param sender
	 *            the sender of this message
	 * @param time
	 *            the time this message was send
	 * @param stream
	 *            input stream
	 */
	public void read(final EntityID sender, final int time, final BitArrayInputStream stream);

	/**
	 * Get the reported type of this kind of message which determines what kind
	 * of Agent this message will send to.
	 * 
	 * @return the reported type of this kind of message
	 */
	public MessageReportedType getMessageReportedType();
	
	/**
	 * Print the message this Port write. Only used for test.
	 * 
	 * @param packet
	 *            the packet will write into channel
	 * @param channel
	 *            the channel this message will write to
	 */
	public void printWrite(MessageBitSection packet, int channel);
	
	/**
	 * Print the message this Port read. Only used for test.
	 * 
	 * @param channel
	 *            the channel read message from
	 */
	public void printRead(int channel);
	
	public void resetCounter();

}
