package csu.agent;

import java.awt.Shape;
import java.util.Collection;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import csu.Viewer.SelectedObject;
import csu.Viewer.layers.CSU_RoadLayer;
import csu.common.TimeOutException;
import csu.model.AdvancedWorldModel;
import csu.model.AgentConstants;
import csu.model.object.CSURoad;
import csu.standard.Ruler;
import csu.standard.simplePartition.GroupingType;
import rescuecore2.messages.Command;
import rescuecore2.misc.Pair;
import rescuecore2.standard.components.StandardAgent;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.standard.messages.StandardMessageURN;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;

/**
 * The simulation proceeds by repeating the following cycle. At the first cycle
 * of the simulation, steps 1 and 2 are skipped.
 * <p>
 * 1. The kernel sends individual vision information to each RCR agent.
 * <p>
 * 2. Each RCR agent submits an action command to the kernel individually.
 * <p>
 * 3. The kernel sends action commands of RCR agents to all sub-simulators.
 * <p>
 * 4. Sub-simulators submit updated states of the disaster space to the kernel.
 * <p>
 * 5. The kernel integrates the received states, and sends it to the viewer.
 * <p>
 * 6. The kernel advances the simulation clock of the disaster space.
 * 
 * @author appreciation - csu
 * 
 * @param <E>
 *            StandardWorldModel
 */
public abstract class Agent<E extends StandardEntity> extends StandardAgent<E> {
	/*Used to monitor the runing time of code, represent the time before think() do anything.*/
	protected long preTime;  
	/* Used to monitor the runing time of code, represent the time after a specified action.*/
	protected long afterTime;
	
	/** A RCR Agent is represent by a circle, and this is the radius of circle. */
	public static final double AGENT_RADIUS = 500;	
	/** An instance of WorldModel */
	protected AdvancedWorldModel world;
	/** Current time */
	protected int time;
	/**
	 * The set of currently visible entities for an agent is stored in a
	 * structure named <b>ChangeSet</b>; entities present in it are
	 * automatically updated in its <b>world model</b>; that is, if an agent
	 * perceives a blockade it did not know that was there before, this blockade
	 * is automatically added to its world model. The opposite, though does not
	 * happen: if the agent does not perceive a blockade any more, nothing in
	 * its world model changes, even if it knew that there was a blockade there
	 * before. In that case, the agent will still think that there is a blockade
	 * in that road, even though such blockade has already been cleared. Thus,
	 * it is up to the agent to figure this out and modify its world model
	 * accordingly.
	 */
	protected ChangeSet changed;
	/** 
	 * According to the simulation server, in each cycle, each <b>RCR Agent</b> can submit
	 * one or more action command at one circle, the kernel adopts only the last one.
	 * <p>
	 * And this Map <b>commandHistory</b> stores the send time and command paris this 
	 * <b>RCR Agent</b> send in the simulation process.  
	 * <p>
	 */
	protected Map<Integer, StandardMessageURN> commandHistory = new TreeMap<>();
	
	protected Map<Integer, Pair<Integer, Integer>> locationHistory= new TreeMap<>();
	
	protected Map<Integer, EntityID> positionHistory = new TreeMap<Integer, EntityID>();
	
	/** The total number of simulation cycles. */
	protected int timestep;
	/**
	 * In each cycle, each agent should send its command to kernel within a certain time,
	 * otherwise this command will ignored by kernel.<br>
	 * So during this time, he analysis dates he gained, by other word, he is thinking.
	 * And we call this time is <b>thinkTime</b>.
	 */
	protected long thinkTime;	
	/** Maximun distance a PF can clear in mm per timestep. */
	protected int distance;
	
//	/** 
//	 * This variable marks the state of platoon Agent. I define it here for the simplicy of communication.
//	 * I set this variable for the task assign of FB, so I only update FB's state. PFs and ATs are also 
//	 * need this state when implementing POs and ACs. Currently, we do not implement POs and ACs, so there
//	 * is no need to consider them.
//	 */
//	protected AgentState agentState = AgentState.SEARCH;
	
//	protected List<Building> burningBuildingsInChangeSet;
	
	
/* ------------------------ initialize(), postConnect() and createWorldModel() --------------------------- */
	/**
	 * Initialize. This method invoked in connection time.
	 * <p>
	 * <li>First, index all entity in this <b>RCR Agent</b>'s <b>WorldModel
	 * model</b> into several groups.</li>
	 * <p>
	 * <li>Second, initialize the <b>WorldModel</b>.</li>
	 * <p>
	 * <li>Third, get <b>timeStep</b> and <b>thinkTime</b> from config. Also,
	 * assign a new instance of <b>Random</b> to {@code random}.</li>
	 */
	protected void initialize() {
		// index all entities in this world model
		model.indexClass(StandardEntityURN.AMBULANCE_TEAM, 
				StandardEntityURN.FIRE_BRIGADE, 
				StandardEntityURN.POLICE_FORCE, 
				StandardEntityURN.BUILDING, 
				StandardEntityURN.AMBULANCE_CENTRE, 
				StandardEntityURN.FIRE_STATION, 
				StandardEntityURN.POLICE_OFFICE, 
				StandardEntityURN.ROAD, 
				StandardEntityURN.REFUGE, 
				StandardEntityURN.BLOCKADE, 
				StandardEntityURN.CIVILIAN,
				StandardEntityURN.GAS_STATION,
				StandardEntityURN.HYDRANT);
		
		world = (AdvancedWorldModel) model;	
		world.initialize(this, config, getGroupingType());
		
		timestep = world.getConfig().timestep;
		thinkTime = world.getConfig().thinkTime;
		random = new Random(getID().getValue());
		
		// why decrease 500? Added by Wang Shang --- Appreciation
		distance = world.getConfig().repairDistance - 500;
		// burningBuildingsInChangeSet = new ArrayList<Building>();
		// this.addTeamMembers();
		System.out.println(getID());
	}
	
	/**
	 * Perform any post-connection work required before acknowledgement of connection is made.
	 */
	@Override
	protected void postConnect() {
		super.postConnect();
		initialize();
	}
	
	/*
	 * This method is very important. If you don't override createWorldModel()
	 * method here, the createWorldModel() method defined in StandrdAgent class
	 * will be invoked, and you can only get an instance of StandardWorldModel.
	 * When you cast a StandardWorldModel to AdvancedWorldModel, an exception
	 * will be thrown, this means the class AdvancedWorldModel is useless, and
	 * then your code will be useless too. 
	 * 											---------- appreciation-csu
	 */
	@Override
	protected StandardWorldModel createWorldModel() {
		return new AdvancedWorldModel();
	}
	
	
/* ---------------------------------------------- Think Method -------------------------------------------- */
	/** Prepare for act.*/
	protected void prepareForAct() throws TimeOutException{}
	
	/** Act and affect the external world. */
	protected abstract void act() throws ActionCommandException, TimeOutException;
	
	/**
	 * This method mainly used to send message, including voice and radio message.
	 */
	 protected void afterAct() {}	 
	 
	/**
	 * This method handle all commands this Agent received.
	 * 
	 * @param heard
	 *            a collection of heard command
	 */
	protected abstract void hear(Collection<Command> heard);

	/**
	 * <b>think()</b> method determines what the agent will do.
	 * 
	 * @param time
	 *            current time
	 * @param changed
	 *            a set of changes observed this timestep
	 * @param heard
	 *            a set of communication messages this agent heard
	 */
	@Override
	protected void think(int time, ChangeSet changed, Collection<Command> heard) { 
		if (AgentConstants.LAUNCH_VIEWER) {
			SelectedObject.renderBuildingValueKey = false;
			
			if (world.getAgent().getID().getValue() == 485278126) {
				CSU_RoadLayer.selfL = null;
				CSU_RoadLayer.road = null;
				CSU_RoadLayer.dir = null;
				
				CSU_RoadLayer.repairDistance = world.getConfig().repairDistance;
			}
			
			CSU_RoadLayer.openParts.clear();
		}
		
		world.setThinkStartTime(System.currentTimeMillis());
		this.time = time;
		world.setTime(time);
		this.changed = changed;

		try {
			hear(heard);
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}

		try {
			try {
				prepareForAct();
			} catch (Exception e) {
				e.printStackTrace(System.out);
			}

			if (world.getConfig().ignoreUntil <= time) {
				act();
				isThinkTimeOver("after act");
				rest();
			}
		} catch (ActionCommandException ace) {
			commandHistory.put(time, ace.message);
		} catch (TimeOutException toe) {
			System.out.println("Time Out Exception message is: " + toe.getMessage());
			commandHistory.put(time, StandardMessageURN.AK_REST);
			world.setExceptionMessage(toe.getMessage());
		} catch (Exception e) {
			commandHistory.put(time, StandardMessageURN.AK_REST);
			e.printStackTrace(System.out);
			System.err.println("Exception : time = " + time + " id = " + me().getID() + " ----- think()");
		}

		try {
			afterAct();
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}
	}
	
	public boolean isThinkTimeOver(String str) throws TimeOutException {
		long thinkTimeThreshold;
		if (AgentConstants.IS_DEBUG) {
			thinkTimeThreshold = (long)1000000000;
		} else {
			thinkTimeThreshold = (long)(thinkTime * 0.9);
		}
		if (System.currentTimeMillis() - world.getThinkStartTime() > thinkTimeThreshold)
			throw new TimeOutException(str);
		return false;
	}
	
	
	
	
	
/* ------------------------------------------------------------------------------------------------------- */
	/**
	 * Return the entity controlled by this agent which is <b>Human</b>.
	 * <p>
	 * Here you can simple think that <b>Agent</b> is a people's thoughts and
	 * <b>Human</b> is the human body which is a kind of entity. So I can say
	 * <b>Agent</b> control <b>Entity</b>.
	 * 
	 * @return the entity controlled by this <b>Agent</b>
	 */
	public Human getMeAsHuman() {
		Human me = null;
        StandardEntity entity = world.getEntity(me().getID());
        if (entity instanceof Human)
        	me = (Human) entity;
        return me;
    }

	
	/**
	 * For a centre <b>Agent</b>, its represent a kind of building. So the
	 * location of centre <b>Agent</b> is the entity controlled by it.
	 * <p>
	 * For <b>Humanoid Agent</b>, it has a <b>position</b> property which is an
	 * object that the humanoid is on. For example, when the humanoid is loaded
	 * by an ambulance, this is set to the ambulance.
	 * 
	 * @return the entity controlled by centre Agent or the entity that Humanoid
	 *         Agent is on
	 */
	@Override
	public StandardEntity location() {
		E me = me();// AbstratEntity
		if (me instanceof Human) {
			return ((Human) me).getPosition(world);
		}
		return me;
	}
	
	 /**
     * Send a rest command to the kernel.
     * 
     * @throws ActionCommandException
     */
	protected void rest() throws ActionCommandException {
		sendRest(time);
		throw new ActionCommandException(StandardMessageURN.AK_REST);
	}
	
	/**
	 * Get the command this RCR Agent send is a specified time.
	 * 
	 * @param time
	 *            the time you send this message
	 * @return the meesage this RCR Agent has send.
	 */
	public StandardMessageURN getCommandHistory(int time) {
		return commandHistory.get(time);
	}
	
	/**
	 * Get the uniform number of this RCR Agent. The uniform number is used when
	 * coding this Agent into a message.
	 * 
	 * @return the uniform number of this RCR Agent
	 */
	protected int getUniform() {
		return world.getUniform().toUniform(getID());
	}

	/**
	 * Determines whether an Agent can see the specified entity.
	 * 
	 * @param entity
	 *            the <b>Entity</b> which needs to determination
	 * @return true when the entity is within the eyeside of this Agent.
	 *         Otherwise, false
	 */
	public boolean isVisible(StandardEntity entity) {
		return isVisible(entity.getID());
	}
	
	/**
	 * Determines whether an Agent can see the specified entity.
	 * 
	 * @param entity
	 *            the <b>Entity</b> which needs to determination
	 * @return true when the entity is within the eyeside of this Agent.
	 *         Otherwise, false
	 */
	public boolean isVisible(EntityID id){
		return changed.getChangedEntities().contains(id);
	}
	
	/**
	 * This method used to get all entities a RCR Agent could see currently.
	 * 
	 * @return a set entities a RCR Agent could see currently
	 */
	public Set<EntityID> getVisibleEntities() {
		if (changed != null)
			return changed.getChangedEntities();
		return null;
	}
	
	/**
	 * Get the world model of this RCR Agent.
	 */
	public AdvancedWorldModel getModel() {
		return world;
	}

	public GroupingType getGroupingType() {
		return GroupingType.getGroupingType(me().getStandardURN());
	}
	
	public Random getRandom() {
		return random;
	}
	
	public Set<EntityID> getChanged() {
		return this.changed.getChangedEntities();
	}
	
	/**
	 * The case when agent is totally buried by blockades. And sgent cannot move
	 * in this case.
	 * 
	 * @param human
	 *            the human who is stucked
	 * @return true when this agent is stucked
	 */
	public boolean isStucked(Human human) {
		Blockade blockade = isLocateInBlockade(human);
		if (blockade == null)
			return false;
		double minDistance = Ruler.getDistanceToBlock(blockade, human.getX(), human.getY());
		
		if (minDistance > 500){
			return true;
		}
		
		if (location() instanceof Building) {
			Building loc = (Building) location();
			
			Set<Road> entrances = world.getEntrance().getEntrance(loc);
			int size = entrances.size();
			int count = 0;
			for (Road next : world.getEntrance().getEntrance(loc)) {
				CSURoad road = world.getCsuRoad(next.getID());
				if (road.isNeedlessToClear())
					continue;
				count++;
			}
			
			if (count == size)
				return true;
		}
		
		return false;
	}
	
	
	/**
	 * True if the given human locate in blockade
	 * 
	 * @return
	 */
	protected Blockade isLocateInBlockade(Human human) {
		int x = human.getX();
		int y = human.getY();
		for (EntityID entityID : changed.getChangedEntities()){
			StandardEntity se = world.getEntity(entityID);
			if (se instanceof Blockade){
				Blockade blockade = (Blockade) se;
				Shape s = blockade.getShape();
				if (s != null && s.contains(x, y)) {
					return blockade;
				}
			}
		}
		return null;
	}
	
	/**
	 * The case when the path is blocked by blockades and agent cannot move
	 * front, but can move back.
	 * 
	 * @return ture when this agent is blocked
	 */
	public boolean isBlocked() {
		if (time < world.getConfig().ignoreUntil + 2)
			return false;
		
		if (commandHistory.get(time - 1).equals(StandardMessageURN.AK_MOVE) &&
				commandHistory.get(time - 2).equals(StandardMessageURN.AK_MOVE)) {
			
			Pair<Integer, Integer> location_1 = locationHistory.get(time - 2);
			Pair<Integer, Integer> location_2 = locationHistory.get(time - 1);
			Pair<Integer, Integer> location_3 = locationHistory.get(time);
			
			if (location_1 == null || location_2 == null || location_3 == null)
				return false;
			
			double distance_1 = Ruler.getDistance(location_1, location_2);
			double distance_2 = Ruler.getDistance(location_2, location_3);
			
			if (distance_1 < 8000 && distance_2 < 8000) {
				return true;
			}
		}
		
		return false;
	}
	
	@SuppressWarnings("serial")
	public static class ActionCommandException extends Exception {
		private StandardMessageURN message;

		public ActionCommandException(StandardMessageURN message) {
			super();
			this.message = message;
		}
	}
	
	/*public boolean failToMove() {
		if (time < world.getConfig().ignoreUntil + 5)
			return false;
		StandardMessageURN command_1 = commandHistory.get(time - 5);
		StandardMessageURN command_2 = commandHistory.get(time - 4);
		StandardMessageURN command_3 = commandHistory.get(time - 3);
		StandardMessageURN command_4 = commandHistory.get(time - 2);
		StandardMessageURN command_5 = commandHistory.get(time - 1);
		
		boolean flag_1 = command_1.equals(StandardMessageURN.AK_MOVE);
		boolean flag_2 = command_2.equals(StandardMessageURN.AK_MOVE);
		boolean flag_3 = command_3.equals(StandardMessageURN.AK_MOVE);
		boolean flag_4 = command_4.equals(StandardMessageURN.AK_MOVE);
		boolean flag_5 = command_5.equals(StandardMessageURN.AK_MOVE);
		
		EntityID location_1 = positionHistory.get(time - 5);
		EntityID location_2 = positionHistory.get(time - 4);
		EntityID location_3 = positionHistory.get(time - 3);
		EntityID location_4 = positionHistory.get(time - 2);
		EntityID location_5 = positionHistory.get(time - 1);
		
		boolean flag_6 = location_1.getValue() == location_2.getValue();
		boolean flag_7 = location_2.getValue() == location_3.getValue();
		boolean flag_8 = location_3.getValue() == location_4.getValue();
		boolean flag_9 = location_4.getValue() == location_5.getValue();
		
		if (flag_1 && flag_2 && flag_3 && flag_4 && flag_5)
			if (flag_6 && flag_7 && flag_8 && flag_9)
				return true;
		
		return false;
	}
	
	public AgentState getAgentState() {
		return this.agentState;
	}
	
	public void setAgentState(AgentState state) {
		world.getAgentStateMap().put(world.getTime(), state);
		this.agentState = state;
	}
	
	public List<Building> getBurningBuildingsFromChangeSet() {
		return getBurningBuildingsFromChangeSet(true, false);
	}

	public List<Building> getBurningBuildingsFromChangeSet(boolean sort, boolean noInfono) {
		List<Building> buildings;

		buildings = burningBuildingsInChangeSet;
		if (noInfono) {
			buildings = removeInfono(buildings);
		}
		if (sort) {
			Collections.sort(buildings, new DistanceComparator(location(), world));
		}
		return buildings;
	}

	public List<Building> removeInfono(List<Building> buildings) {
		List<Building> list;

		list = new ArrayList<Building>();
		for (Building building : buildings) {
			if (building.isFierynessDefined()) {
				list.add(building);
			} else {
				if (building.getFierynessEnum() != Fieryness.INFERNO) {
					list.add(building);
				}
			}
		}
		return list;
	}
	
	protected void processObserving(ChangeSet changed) {
		burningBuildingsInChangeSet.clear();
		for (EntityID next : changed.getChangedEntities()) {
			StandardEntity entity = world.getEntity(next);
			if (entity instanceof Building) {
				Building building = (Building) entity;
				if (building.isOnFire()) {
					burningBuildingsInChangeSet.add(building);
				}
			}
		}
	}
	public void assignRegions() {
		List<EntityID> team = world.getTeam();
		world.getRegionModel().assignRegionsToTeam();
		List<EntityRegion> regions = getAssignedRegions();

		for (EntityRegion region : regions) {
			getExploration().addEntitiesToExplore(region.getBuildings());
		}
	}

	private List<EntityRegion> getAssignedRegions() {
		if (world == null) {
			return null;
		} else {
			return getAssignedRegionGroup().getRegions();
		}
	}

	private RegionGroup getAssignedRegionGroup() {
		if (world == null) {
			return null;
		} else {
			return world.getRegionModel().getAssignedRegions(getID());
		}
	}

	public Exploration<Building> getExploration() {
		return world.getBuildingExploration();
	}

	public List<Road> getRegionRoads() {
		List<Road> entities;
		List<EntityRegion> regions;
		entities = new ArrayList<Road>();
		regions = getAssignedRegions();
		if (regions == null) {
			return null;
		}
		for (EntityRegion region : regions) {
			entities.addAll(region.getRoads());
		}
		return entities;
	}*/
}
