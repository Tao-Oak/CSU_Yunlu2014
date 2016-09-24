package csu.communication;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.PriorityQueue;
import csu.communication.MessageConstant.MessageReportedType;
import csu.communication.channel.ChannelManager;
import csu.communication.channel.DynamicAssignedChannelManager;
import csu.communication.channel.RandomChannelManager;
import csu.communication.channel.StaticAssignedChannelManager;
import csu.io.BitArrayInputStream;
import csu.io.BitArrayOutputStream;
import csu.model.AdvancedWorldModel;
import csu.model.AgentConstants;
import csu.model.ConfigConstants;
import csu.util.BitUtil;

import rescuecore2.messages.Command;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.messages.AKSpeak;
import rescuecore2.worldmodel.ChangeSet;

/**
 * In this class, first we provide method to get radio and voice channels that
 * you can used to send radio and voice message.
 * <p>
 * For {@link csu.model.ConfigConstants.RadioChannel}, we assigne it for FBs,
 * PFs and ATs respectively. So you can find method to get channels those three
 * kind of Agent can subscribe to.
 * <p>
 * For {@link csu.model.ConfigConstants.VoiceChannel}, when you check config
 * file, you can find that there is always one voice channel with id 0. So we
 * call it as zero-channel. And all Agent will subscribe to it.
 * <p>
 * Then we provide method to create radio and voice message. There are many kind
 * of messages, for example, fired buildings, burried humans and etc, and you
 * should handle them respectively. For a certain type of message, it can has
 * many message packet({@link MessageBitSection}) each cycle.
 * <p>
 * We have a {@link Port} interface, and each implementation of this interface
 * can be considered as a kind of message. It also privide method to write and
 * read this kind of message.
 * <p>
 * When sending messages, we gather all message packet together, sort them
 * according to priority, and then write them into channel. You you read
 * messages, the system gives a byte stream and you should translate those bytes
 * into useful messages. How to translate? Currently, we have read method in
 * {@link Port} to translate packet associated with this specified {@link Port}.
 * The remain problem is, how do you separate each packet out from the byte
 * stream, and how do you determines which {@link Port} you should use to
 * translate this packet into useful message? So you need a marker for each
 * packet, and we call this marker is message head.
 * <p>
 * For each Agents, they monitor all kind of messages. So they have the same
 * {@link Port} array. And the index of each {@link Port} in this {@link Port}
 * array can be used as message header. When read the byte stream, we first read
 * the message header and we can find a {@link Port} in {@link Port} array, then
 * we invoke the read method of this {@link Port} to translate the message of a
 * packet till the end of this packet. After finish a packet, we read a another
 * header and translate it. All of this can be done in a while loop, and the
 * implementation method is
 * {@link CommunicationManager#readMessage(AKSpeak, Port[], int) readMessage(AKSpeak, Port[], int)}
 * 
 * @see ConfigConstants.VoiceChannel
 * @see ConfigConstants.RadioChannel
 * @see ChannelManager
 * @see RandomChannelManager
 * @see StaticAssignedChannelManager
 * @see DynamicAssignedChannelManager
 * @see CommunicationManagerPortsBuilder
 * @see MessageBitSection
 * @see Port
 */
public class CommunicationManager {
	/** The world model. */
    final private AdvancedWorldModel world;
	/** Channel manager which do the channel assign task.*/
	final private ChannelManager chManager;
	
	/** radioEventListener */
	final private Port[] radioEventListener;
	private List<Port> radioEventListeners = null;
	/** The header is the index of its Port in radioEventListener.*/
	final private int radioHeaderBitSize;
	
	/** voiceEventListener */
	final private Port[] voiceEventListener;
	private List<Port> voiceEventListeners = null;
	/** The header is the index of its Port in the voiceEventListener.*/
	final private int voiceHeaderBitSize;
	
	/** civilianVoiceListener */
	final private CivilianVoiceListener[] civilianListener;
	
	/**
	 * This variable stores all possible message for agents this cycle. And it
	 * helps to handle the case that an Agent can subscribe more than one
	 * channels. First we initialize all possible message and stores them here.
	 * Then we create message for the first subscriable channel. All packet send
	 * in first channel will be removed from this queue. After the first
	 * channel, we handle the second channel, and the third and ect.
	 */
	final PriorityQueue<MessageBitSection> allMessage = new PriorityQueue<MessageBitSection>();
	
	/**
	 * When create mesage for one specified channel, we should poll all packet
	 * from {@code allMessage} and determines whether it can be put into this
	 * channel. And we should not put packet back to {@code allMessage} when it
	 * can not be put into this channel. Otherwise, it may form an infinite
	 * loop. So we should have another variable to stores those can't been put
	 * in packet. When the message creation of current channel finished,
	 * {@code allMessage} will be an empty queue, and we put all packets in this
	 * variable into {@code allMessage}. Then we can create message for next
	 * channel. Before each creation, we should clear {@code tempMessage}.
	 */
	final PriorityQueue<MessageBitSection> tempMessage = new PriorityQueue<MessageBitSection>();
	
	// consturctor
	public CommunicationManager(CommunicationManagerPortsBuilder parts) {
		parts.build();
		world = parts.getWorld();                               
		chManager = parts.getChannelManager();                  
		radioEventListener = parts.getRadioEventListener();     
		voiceEventListener = parts.getVoiceEventListener();     
		radioHeaderBitSize = BitUtil.needBitSize(radioEventListener.length);   
		voiceHeaderBitSize = BitUtil.needBitSize(voiceEventListener.length);   
		civilianListener = parts.getCivilianVoiceListener();    
	}

	/**
	 * When you assign channel dynamicly, you should update channel manager in
	 * each cycle before you send a message.
	 */
	public void update() {
		chManager.update();
	}
	
	/** Get the voice communication channel. */
	public int getVoiceChannel() {
		return chManager.getSubscribeVoiceChannel();
	}

	/**
	 * Get all channels a FB Agent can subscribe to.
	 * 
	 * @return all channels a FB Agent can subscribe to
	 */
	public int[] getFireChannel(){
		return chManager.getFireChannel();
	}
	/**
	 * Get all channels a AT Agent can subscribe to.
	 * 
	 * @return all channels a AT Agent can subscribe to
	 */
	public int[] getAmbulanceChannel(){
		return chManager.getAmbulanceChannel();
	}
	/**
	 * Get all channels a PF Agent can subscribe to.
	 * 
	 * @return all channels a PF Agent can subscribe to
	 */
	public int[] getPoliceChannel(){
		return chManager.getPoliceChannel();
	}
	
	/**
	 * Get all channels this Agent can subscribe to.
	 * 
	 * @return all channels this Agent can subscribe to
	 */
	public int[] getSubscribeChannels() {
		return chManager.getSubscribeChannels();
	}

	
	
/* --------------------------- create message when all agent share one channel ----------------------------- */	
	
	
	public byte[] createRadioMessage(final int channel, final ChangeSet changed) {  
		
		final int limit = world.getConfig().radioChannels.get(channel).bandwidth;
		return createMessage(radioEventListener, radioHeaderBitSize, limit, changed, channel);
	}
	
	public byte[] createVoiceMessage(final int channel, final ChangeSet changed) {
		
		final int limit = world.getConfig().voiceChannels.get(channel).size;
		return createMessage(voiceEventListener, voiceHeaderBitSize, limit, changed, channel);
	}
	
	private byte[] createMessage(final Port[] listener, final int headerSize, 
			final int limit, final ChangeSet changed, int channel) {
		
		final PriorityQueue<MessageBitSection> que = new PriorityQueue<MessageBitSection>();
		final BitArrayOutputStream stream = new BitArrayOutputStream();
		
		for (int i = 0; i < listener.length; i++) {
			listener[i].resetCounter();
			for (listener[i].init(changed); listener[i].hasNext();) {
				MessageBitSection sec = listener[i].next();
				sec.setHeaderNumber(i);
				que.add(sec);
			}
		}
		
		final ArrayList<String> ports = new ArrayList<String>();
		
		while (!que.isEmpty()) {
			final MessageBitSection sec = que.poll();
			if ((stream.getBitLength() + sec.getBitLength()) / 8 + 1 > limit) {
				continue;
			}
			
			// TODO print the write message
			if (AgentConstants.PRINT_COMMUNICATION)  {
				listener[sec.getHeaderNumber()].printWrite(sec, channel);
			}
			// TODO
			
			ports.add(listener[sec.getHeaderNumber()].getClass().getName());
			stream.writeBit(sec.getHeaderNumber(), headerSize);
			for (Pair<Integer, Integer> writePair : sec) {
				stream.writeBit(writePair.first(), writePair.second());
			}
		}
		
		return stream.getData();
	}
	
	
	
	
/* ------------------------ create message for each agent has no more than one channel --------------------- */
	
	
	public byte[] createRadioMessage(final int channel, 
			final ChangeSet changed, EnumSet<MessageReportedType> reportedTypes) {  
		
		final int limit = world.getConfig().radioChannels.get(channel).bandwidth;
		return createMessage(radioEventListener, radioHeaderBitSize, channel, limit, changed, reportedTypes);
	}
	
	public byte[] createVoiceMessage(final int channel, 
			final ChangeSet changed, EnumSet<MessageReportedType> reportedTypes) {
		
		final int limit = world.getConfig().voiceChannels.get(channel).size;
		return createMessage(voiceEventListener, voiceHeaderBitSize, channel, limit, changed, reportedTypes);
	}
	
	private byte[] createMessage(final Port[] listener, final int headerSize, int channel,
			final int limit, final ChangeSet changed, EnumSet<MessageReportedType> reportedType) {
		
		final PriorityQueue<MessageBitSection> que = new PriorityQueue<MessageBitSection>();
		final BitArrayOutputStream stream = new BitArrayOutputStream();
		
		for (int i = 0; i < listener.length; i++) {
			Port flag = null;
			for(MessageReportedType next : reportedType) {
				if (listener[i].getMessageReportedType() == next) {
					flag = listener[i];
					break;
				}
			}
			if (flag == null)
				continue;
			listener[i].resetCounter();
			for (listener[i].init(changed); listener[i].hasNext();) {
				MessageBitSection sec = listener[i].next();
				sec.setHeaderNumber(i);
				que.add(sec);
			}
		}
		
		final ArrayList<String> ports = new ArrayList<String>();
		while (!que.isEmpty()) {
			final MessageBitSection sec = que.poll();
			if ((stream.getBitLength() + sec.getBitLength()) / 8 + 1 > limit) {
				continue;
			}	
			
			// TODO print the write message
			if (AgentConstants.PRINT_COMMUNICATION) {
				listener[sec.getHeaderNumber()].printWrite(sec, channel);
			}
			// TODO
			
			ports.add(listener[sec.getHeaderNumber()].getClass().getName());
			stream.writeBit(sec.getHeaderNumber(), headerSize);
			for (Pair<Integer, Integer> writePair : sec) {
				stream.writeBit(writePair.first(), writePair.second());
			}
		}
		
		return stream.getData();
	}
	
	
	
	
/* ---------------------- create message when each agent has many channels -------------------------------- */
	
	
	public void initRadioMessage(final ChangeSet changed, final EnumSet<MessageReportedType> reportedTypes) {
		this.initMessage(radioEventListener, changed, reportedTypes);
	}
	
	public void initVoiceMessage(final ChangeSet changed, final EnumSet<MessageReportedType> reportedTypes) {
		this.initMessage(voiceEventListener, changed, reportedTypes);
	}
	
	private void initMessage(final Port[] listener, final ChangeSet changed, 
			final EnumSet<MessageReportedType> reportedTypes) {
		
		allMessage.clear();
		for (int i = 0; i < listener.length; i++) {
			Port flag = null;
			for(MessageReportedType next : reportedTypes) {
				if (listener[i].getMessageReportedType() == next) {
					flag = listener[i];
					break;
				}
			}
			if (flag == null)
				continue;
			listener[i].resetCounter();
			for (listener[i].init(changed); listener[i].hasNext();) {
				MessageBitSection sec = listener[i].next();
				sec.setHeaderNumber(i);
				allMessage.add(sec);
			}
		}
	}
	
	public byte[] createRadioMessage(final int channel) {
		final int limit = world.getConfig().radioChannels.get(channel).bandwidth;
		return createMessage(radioHeaderBitSize, limit, radioEventListener, channel);
	}
	
	public byte[] createVoiceMessage(final int channel) {
		final int limit = world.getConfig().voiceChannels.get(channel).size;
		return createMessage(voiceHeaderBitSize, limit, voiceEventListener, channel);
	}
	
	private byte[] createMessage(final int headerSize, final int limit, final Port[] listener, int channel) {
		tempMessage.clear();
		final BitArrayOutputStream stream = new BitArrayOutputStream();
		
		final ArrayList<String> ports = new ArrayList<String>();
		while (!allMessage.isEmpty()) {
			final MessageBitSection sec = allMessage.poll();
			if ((stream.getBitLength() + sec.getBitLength()) / 8 + 1 > limit) {
				tempMessage.add(sec);
				continue;
			}
			
			// TODO print the write message
			if (AgentConstants.PRINT_COMMUNICATION) {
				listener[sec.getHeaderNumber()].printWrite(sec, channel);
			}
			// TODO
			
			ports.add(listener[sec.getHeaderNumber()].getClass().getName());
			stream.writeBit(sec.getHeaderNumber(), headerSize);
			for (Pair<Integer, Integer> writePair : sec) {
				stream.writeBit(writePair.first(), writePair.second());
			}
		}
		
		allMessage.addAll(tempMessage);
		
		return stream.getData();
	}
	
	
	
/* ----------------------------------------- read message ----------------------------------------------- */
	
	/**
	 * Read message. Including voice and radio message. This method also handle messages
	 * send by civilian.
	 * 
	 * @param heard  the received messages
	 */
	public void read(final Collection<Command> heard) {
		for (Command command : heard) {
			if (command instanceof AKSpeak) {
				final AKSpeak message = (AKSpeak) command;
				final StandardEntity sender = world.getEntity(message.getAgentID());
				final int channel = message.getChannel();

				if (sender instanceof Civilian) { 
					for (CivilianVoiceListener l : civilianListener) { 
						l.hear(message);                
					}
				} else if (chManager.isVoiceChannel(channel)) { // read voice meaasge
					readMessage(message, voiceEventListener, voiceHeaderBitSize, channel);
				} else { // read radio message
					readMessage(message, radioEventListener, radioHeaderBitSize, channel);
				}
			}
		}
	}
	
	/** Read the message send by an Agent. It can be a voice or radio message.*/
	private void readMessage(final AKSpeak message, final Port[] listener, final int headerSize, int channel) {
		
		final BitArrayInputStream stream = new BitArrayInputStream(message.getContent());
		
		final ArrayList<String> ports = new ArrayList<String>();    
		
		while (stream.hasNext()) {
			final int header = stream.readBit(headerSize);
			
//			try {
				ports.add(listener[header].getClass().getName());
				listener[header].read(message.getAgentID(), message.getTime(), stream);
				
				// TODO print the readed message
				if (AgentConstants.PRINT_COMMUNICATION) {
					listener[header].printRead(channel);
				}
				// TODO

//			} catch (ArrayIndexOutOfBoundsException e) {
//				System.out.println("Time: " + world.getTime() + " agent: " + 
//						world.getControlledEntity() + " --- " + ports);
//				e.printStackTrace(System.out);
//				break;
//			} catch (Exception e) {
//				System.out.println(ports);
//				e.printStackTrace();
//			}
		}
	}
	
	public List<Port> getRadioEventListers() {
		if (radioEventListeners == null) {
			radioEventListeners = new ArrayList<>();
			for(Port next : radioEventListener) {
				radioEventListeners.add(next);
			}
		}
		
		return Collections.unmodifiableList(radioEventListeners);
	}
	
	public List<Port> getVoiceEventListeners() {
		if (voiceEventListeners == null) {
			voiceEventListeners = new ArrayList<>();
			for (Port next : voiceEventListener) {
				voiceEventListeners.add(next);
			}
		}
		return Collections.unmodifiableList(voiceEventListeners);
	}
	
	/*
	 * Because I have changed the channel selection model, there is no need of those method.
	 *                                                 ------------------ appreciation-csu
	 * 
	 * public int getRadioChannel() { 
	 *     return chManager.getRadioChannel(); 
	 * }
	 */
	
	/*
	 * I give a type to each message, and then I can use this type to determines which Agent(FB,PF,AT) 
	 * this message to send. But is was fail try.
	 *                                                                      ----------- appreciation-csu
	 * 
	 * ------ Create a radio message for the target radio channel using a specified kind of packet.
	 * public byte[] createRadioMessage(final int channel, final ChangeSet changed, final MessageType type) {
	 *     final int limit = world.getConfigConstants().radioChannels.get(channel).bandwidth;
	 *     return createMessage(radioEventListener, radioHeaderBitSize, limit, changed, type);
	 * }              
	 * ------ Create a voice message for the target voice channel using a specified kind of packet.
	 * public byte[] createVoiceMessage(final int channel, final ChangeSet changed, final MessageType type) {
	 *     final int limit = world.getConfigConstants().voiceChannels.get(channel).size;
	 *     return createMessage(voiceEventListener, voiceHeaderBitSize, limit, changed, type);
	 * }
	 * ----- This method used to create message(radio and voice) using a specified kind of packet.
	 * static private byte[] createMessage(final Port[] listener, final int headerSize, 
	 *                             final int limit, final ChangeSet changed, final State.MessageType type) {
	 *     final PriorityQueue<MessageBitSection> que = new PriorityQueue<MessageBitSection>();
	 *     final BitArrayOutputStream stream = new BitArrayOutputStream();
	 *     
	 *     for (int i = 0; i < listener.length; i++ ) {
	 *         if (listener[i].getMessageType() != type)
	 *             continue;
	 *         for (listener[i].init(changed); listener[i].hasNext();) {
	 *             MessageBitSection sec = listener[i].next();
	 *             sec.setHeaderNumber(i);
	 *             que.add(sec);
	 *         }
	 *     }
	 *     while (!que.isEmpty()) {
	 *         final MessageBitSection sec = que.poll();
	 *         if ((stream.getBitLength() + sec.getBitLength()) / 8 + 1 > limit)
	 *             continue;
	 *         stream.writeBit(sec.getHeaderNumber(), headerSize);
	 *         for (Pair<Integer, Integer> writePair : sec) {
	 *             stream.writeBit(writePair.first(), writePair.second());
	 *         }
	 *     }
	 *     return stream.getData();
	 * }
	 * 
	 * 
	 * ----- Create a radio message for the target radio channel using a set of packets with differnent types.
	 * public byte[] createRadioMessage(final int channel, 
	 * 					final ChangeSet changed, final EnumSet<MessageType> types) {
	 *     final int limit = world.getConfigConstants().radioChannels.get(channel).bandwidth;
	 *     return createMessage(radioEventListener, radioHeaderBitSize, limit, changed, types);
	 * }
	 * 
	 * ----- Create a voice message for the target voice channel using a set of packets with different types.
	 * public byte[] createVoiceMessage(final int channel, 
	 * 					final ChangeSet changed, final EnumSet<MessageType> types) {
	 *     final int limit = world.getConfigConstants().voiceChannels.get(channel).size;
	 *     return createMessage(voiceEventListener, voiceHeaderBitSize, limit, changed, types);
	 * }
	 * 
	 * ----- This method used to create message(radio and voice) using a set of packets with differnet types.
	 * static private byte[] createMessage(final Port[] listener, final int headerSize, final int limit, 
	 * 											final ChangeSet changed, final EnumSet<MessageType> types){
	 *     final PriorityQueue<MessageBitSection> que = new PriorityQueue<MessageBitSection>();
	 *     final BitArrayOutputStream stream = new BitArrayOutputStream();
	 *     for (int i = 0; i < listener.length; i++) {
	 *         Port port = null;
	 *         for (MessageType type : types) {
	 *             if (listener[i].getMessageType() != type)
	 *                 continue;
	 *             port = listener[i];
	 *         }
	 *         if (port == null)
	 *             continue;
	 *             
	 *         for (listener[i].init(changed); listener[i].hasNext();) {
	 *             MessageBitSection sec = listener[i].next();
	 *             sec.setHeaderNumber(i);
	 *             que.add(sec);
	 *         }
	 *     }
	 *     
	 *     while (!que.isEmpty()) {
	 *         final MessageBitSection sec = que.poll();
	 *         if ((stream.getBitLength() + sec.getBitLength()) / 8 + 1 > limit)
	 *             continue;
	 *         stream.writeBit(sec.getHeaderNumber(), headerSize);
	 *         for (Pair<Integer, Integer> writePair : sec) {
	 *             stream.writeBit(writePair.first(), writePair.second());
	 *         }
	 *     }
	 *     return stream.getData();
	 * }
	 */
}
