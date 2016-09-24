package csu.agent;

import java.awt.Point;
import java.util.*;

import csu.agent.at.AmbulanceTeamAgent;
import csu.common.TimeOutException;
import csu.model.AgentConstants;
import csu.model.object.CSURoad;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.StandardEntityConstants.Fieryness;
import rescuecore2.worldmodel.EntityID;

public abstract class PlatoonAgent<E extends Human> extends HumanoidAgent<E> {

//	 /** All roads in my assigned region group.*/
//	 private Queue<Road> roads;
//
//	/**
//	 * Stands for all entrance of a building, and will be used in <b>goOut()</b>
//	 * method.
//	 */
//	private Iterator<Road> entranceIt = null;

	// private Road road;
	private Point oldPosition;
	private Point lastPosition;
	private Point currentPosition;

	StandardEntity oldLocation = null;

	/**
	 * Used in method <b>leaveBurningBuilding()</b>, it is the entrance Agent
	 * will move to if Agent in a fired Building.
	 * <p>
	 */
	protected Road fireBuildingEntrance = null;
	
	/**
	 * Used in method <b>cantMove()</b>, it is the entrance Agent will move to
	 * if Agent is stucked in a Building.
	 * <p>
	 */
	protected Road cannotLeaveBuildingEntrance = null;

	/** A set of areas that some other agent has been explored. */
	protected Set<EntityID> someoneVisitedArea = null;
	
	/**
	 * A set of buildings that has not been entered. So for a enteredBuilding,
	 * it must also be a lookupedBuilding.
	 */
	protected Set<EntityID> unenteredBuildings = null;
	
	/**
	 * A set of buildings that has not been looked up. A lookupedBuilding just
	 * represents those Building has been looked up out side it. So a
	 * lookupedBuilding must not a enteredBuilding
	 */
	protected Set<EntityID> unlookupedBuildings = null;
	
	
/* ----------------------------------------- initialize() ------------------------------------------------- */	
	
	/** This method was invoked in connection time.*/
	@Override
	protected void initialize() {
		super.initialize();
		
		initUnentered();
		initUnlookuped();
	}
	/** Initializing the unlookuped Buildings.*/
	protected void initUnlookuped() {
		Collection<StandardEntity> initedBuildings = world.getEntitiesOfType(StandardEntityURN.BUILDING);
		unlookupedBuildings = new HashSet<EntityID>(initedBuildings.size());
		for (StandardEntity se : initedBuildings) {
			unlookupedBuildings.add(se.getID());
		}
	}
	
	/** Initializing untered Buildings.*/
	protected void initUnentered() {
		StandardEntityURN[] initedURN;
		if (this instanceof AmbulanceTeamAgent) {
			initedURN = new StandardEntityURN[] {
					StandardEntityURN.BUILDING,
					StandardEntityURN.AMBULANCE_CENTRE,
					StandardEntityURN.FIRE_STATION,
					StandardEntityURN.POLICE_OFFICE,	
					StandardEntityURN.GAS_STATION
			};
		} else {
			initedURN = new StandardEntityURN[] {
					StandardEntityURN.BUILDING,
			};
		}
		Collection<StandardEntity> initedBuildings = world.getEntitiesOfType(initedURN);
		unenteredBuildings = new HashSet<EntityID>(initedBuildings.size());
		for (StandardEntity se : initedBuildings) {
			unenteredBuildings.add(se.getID());
		}
	}

	
/* ------------------------------------- prepareForAct() and act() ---------------------------------------- */	
	
	@Override
	protected void prepareForAct() throws TimeOutException{
		super.prepareForAct();
		
		someoneVisitedArea = world.getSearchedBuildings();
	}

	/**
	 * The necessary common behavior of all <code>PlatoonAgent</code>.
	 * <p>
	 * And this action is about how to go out of a Building for all <code>RCR Agent</code>
	 */
	@Override
	protected void act() throws ActionCommandException , TimeOutException{
		 
	}
	
	/** Handle the case Agent was stucked in a road.*/
	protected abstract void cantMove() throws ActionCommandException, TimeOutException;

	
/* --------------------------------- goOut() and serach building ------------------------------------------ */
	
//	/**
//	 * Find the entrance of a given Building, then go out of this Building.
//	 * <p>
//	 * When test, we found that sometimes, Agents can not go out a Building within a cycle.
//	 * Then he go back to the centre, and find next entrance to move out. This is a bad circle.
//	 * So we write another goOut() method following.
//	 * 
//	 * @param building  the Building you need to go out to 
//	 * @throws ActionCommandException
//	 */
//	protected Road goOut(Building bld) throws ActionCommandException {
//		// If you are away from from the centre of this Building, move to the centre.
//		if (1000 < world.getDistance(location(), me())) {
//			move(location(), location().getX(), location().getY());
//		}
//		if (entranceIt == null || !entranceIt.hasNext()) {
//			entranceIt = world.getEntrance().getEntrance(bld).iterator();
//		}
//		//To get an entrance of the building
//		Road entranceRoad = entranceIt.next();
//		move(entranceRoad);
//		
//		return entranceRoad;
//	}
//	
//	/**
//	 * Go out the target Building with the target road.
//	 * <pre>
//	 * 1.First of all, handle the entrance road iterator which stands for all
//	 * entrance of target Building.
//	 * 
//	 * 2.If target road is null, choose a unblocked road of target building
//	 * and move to it.
//	 * 
//	 * 3.If target road is not null and it was one of entrance of target
//	 * buillding, just move to it.
//	 * 
//	 * 4.If target road is not null, but it was not the entrance of target
//	 * building, find a unblocked road of target building and move to it.
//	 * <pre>
//	 * 
//	 * @param building
//	 *            target building
//	 * @param road
//	 *            target road
//	 * @param isLeaveFireBuilding
//	 *            flag to determines which method invoked this method
//	 * @return the target road
//	 * @throws ActionCommandException
//	 * 
//	 */
//	protected void goOut(Building building, Road road, boolean isLeaveFireBuilding) throws ActionCommandException {
//		Road entranceRoad = null;      // stands for target road
//		
//		if (entranceIt == null)
//			entranceIt = world.getEntrance().getEntrance(building).iterator();
//		if (entranceIt != world.getEntrance().getEntrance(building).iterator())
//			entranceIt = world.getEntrance().getEntrance(building).iterator();
//		if (!entranceIt.hasNext())
//			entranceIt = world.getEntrance().getEntrance(building).iterator();
//		
//		if (road == null) {
//			if (1000 < world.getDistance(location(), me())) {
//				move(location(), location().getX(), location().getY());
//			}
//			while (entranceIt.hasNext()) {
//				Road r = entranceIt.next();
//				if (RoadState.isSmoothRoad(r, world)) { // determines whether this road is smooth
//					entranceRoad = r;
//					break;
//				}
//			}
//		} else {
//			if (isEntranceOfBuilding(building, road)) {
//				entranceRoad = road;
//			} else {
//				while (entranceIt.hasNext()) {
//					Road r = entranceIt.next();
//					if (RoadState.isSmoothRoad(r, world)) {
//						entranceIt.remove();
//						entranceRoad = r;
//						break;
//					}
//				}
//			}
//		}
//		// all entrance of this building is blocked
//		if (entranceRoad == null) 
//			return;
//		
//		if (isLeaveFireBuilding)   // invoked by leaveBurnBuilding() which do not implement in this method
//			fireBuildingEntrance = entranceRoad;
//		else                      // invoked by canntMove() which do not implement in this method
//			cannotLeaveBuildingEntrance = entranceRoad;
//		
//		move(entranceRoad);
//	}
//	
//	/**
//	 * Determines whether the target Road was one of entrances of the target
//	 * Building.
//	 * 
//	 * @param building
//	 *            the target Building
//	 * @param road
//	 *            the target Road
//	 * @return true when the target Road is the entrance of the target Building,
//	 *         otherwise, false
//	 */
//	private boolean isEntranceOfBuilding(Building building, Road road) {
//		Set<Road> roads = world.getEntrance().getEntrance(building);
//		boolean flag = roads.contains(road);
//		return flag;
//	}

	/**
	 * Move to building and look up outside it to see if this building is on fire or not,
	 * whether has Human stucked in it and etc.
	 * <p>
	 * This method is mainly used to collecte informations, so there is no need to enter 
	 * those building. Beside, it will take a certain risk to enter a building because if
	 * a building is on fire and Agent enter it, Agent will get a certain damage.  
	 * 
	 * @throws ActionCommandException
	 */
	protected void lookupSearchBuildings() throws ActionCommandException {
//		setAgentState(AgentState.SEARCH);
		if (unlookupedBuildings.isEmpty()) {
			initUnlookuped();   // think all building in world are unlookuped
		}
		/* Remove all Buildings that someone has visited.*/
		unlookupedBuildings.removeAll(someoneVisitedArea);
		unlookupedBuildings.removeAll(changed.getChangedEntities());
		
		/* Remove all Buildings that is on fire or burnt out.*/
		for (Iterator<EntityID> it = unlookupedBuildings.iterator(); it.hasNext();) {
			EntityID id = it.next();
			StandardEntity se = world.getEntity(id);
			if (se instanceof Building) {
				Building building = (Building) se;
				if ((building.isFierynessDefined() && building.getFierynessEnum() == Fieryness.BURNT_OUT)
						|| building.isOnFire()) {
					it.remove();
				}
			}
		}
		
		// if a entrance is blocked, then unsearch all buildings related to this entrance
		for (EntityID next : getChanged()) {
			StandardEntity entity = world.getEntity(next);
			if (!(entity instanceof Road))
				continue;
			CSURoad road = world.getCsuRoad(next);
			
			if (road.isEntrance()) {
				for (Building build : world.getEntrance().getBuilding(road.getSelfRoad())) {
					unlookupedBuildings.remove(build.getID());
				}
			}
		}
		
		/* If the unlookupedBuildings is not empty, then I need go to one of them and do something.*/
		if (!unlookupedBuildings.isEmpty()) {
			Set<StandardEntity> dest = new HashSet<StandardEntity>(unlookupedBuildings.size());
			for (EntityID id : unlookupedBuildings) {
				dest.add(world.getEntity(id));
			}
			if (me() instanceof PoliceForce) {
				moveFront(dest, router.getPfCostFunction());
			}

			if (AgentConstants.PRINT_TEST_DATA || AgentConstants.PRINT_TEST_DATA_FB) {
				System.out.println("In time: " + time + " Agent: " + me() + " is lookup search building " +
						"----- class: PlatoonAgent, method: lookupSearchBuilding()");
			}
			
			moveFront(dest);
		}
	}
	
	/**
	 * Enter a Building and search. This method mainly used to find buried Human
	 * and reported to AT.
	 * <p>
	 * For the safety case, Agents will not enter fired buildings or buildings
	 * not on fire but with temperature greater than a certian value. And there
	 * is no need to enter burnt out building any more, so Agents will not enter
	 * burnt out building, too.
	 * 
	 * @throws ActionCommandException
	 */
	protected void enterSearchBuildings() throws ActionCommandException {
		if (unenteredBuildings.isEmpty()) {
			initUnentered();
		}
		unenteredBuildings.removeAll(someoneVisitedArea);
		for (Iterator<EntityID> it = unenteredBuildings.iterator(); it.hasNext();) {
			EntityID id = it.next();
			StandardEntity se = world.getEntity(id);
			if (se instanceof Building) {
				Building building = (Building) se;
				if (world.getCollapsedBuildings().contains(building) 
						|| world.getBurningBuildings().contains(building)) {
					it.remove();
				} else if (building.isTemperatureDefined() && building.getTemperature() > 25) {
					it.remove();
				}
			}
		}
		
		for (EntityID next : getChanged()) {
			StandardEntity entity = world.getEntity(next);
			if (!(entity instanceof Road))
				continue;
			CSURoad road = world.getCsuRoad(next);

			if (road.isEntrance()) {
				for (Building build : world.getEntrance().getBuilding(road.getSelfRoad())) {
					unenteredBuildings.remove(build.getID());
				}
			}
		}
		
		if (!unenteredBuildings.isEmpty()) {
			Set<StandardEntity> dest = new HashSet<StandardEntity>(unenteredBuildings.size());
			for (EntityID id : unenteredBuildings) {
				dest.add(world.getEntity(id));
			}
			
			dest.remove(location().getID());
			
			if (me() instanceof PoliceForce) {
				move(dest, router.getPfCostFunction());
			}
			
			if (AgentConstants.PRINT_TEST_DATA || AgentConstants.PRINT_TEST_DATA_FB) {
				System.out.println("Agent: " + me() + " is enter search building in time: " + time + 
						" ----- class: FireBrigadeAgent, method: think()");
			}
			
			move(dest);
		}
	}

	protected void randomWalk() throws ActionCommandException {
		Collection<StandardEntity> inRnage = world.getObjectsInRange(me().getID(), 50000);
		List<Area> target = new ArrayList<>();
		Collection<Area> blockadeDefinedArea = new ArrayList<>();
		
		for (StandardEntity entity : inRnage) {
			if (!(entity instanceof Area))
				continue;
			if (entity.getID().getValue() == location().getID().getValue())
				continue;
			Area nearArea = (Area) entity;
			/*if (nearArea.isBlockadesDefined() && nearArea.getBlockades().size() > 0) {
				blockadeDefinedArea.add(nearArea);
				continue;
			}*/
				
			target.add(nearArea);
		}
		
		if (target.isEmpty())
			target.addAll(blockadeDefinedArea);
		Area randomTarget = target.get(random.nextInt(target.size()));
		
		List<EntityID> path = router.getAStar(location(), 
				randomTarget, router.getNormalCostFunction(), new Point(me().getX(), me().getY()));
		
		if (AgentConstants.PRINT_TEST_DATA 
				|| AgentConstants.PRINT_TEST_DATA_PF || AgentConstants.PRINT_TEST_DATA_FB) {
			String str = null;
			for (EntityID next : path) {
				if (str == null) {
					str = next.getValue() + "";
				} else {
					str = str + ", " + next.getValue();
				}
			}
			System.out.println("time = " + time + " " + me() + " is stucked, ramdomWalk path = [" + str + "]");
		}
		
		move(path);
	}

	
/* ---------------------------------------- handle aggregator --------------------------------------------- */	
	
	/** Move to refuge.*/
	protected void moveToRefuge() throws ActionCommandException {
		Collection<StandardEntity> refuges = world.getEntitiesOfType(StandardEntityURN.REFUGE);
		
		if (refuges.isEmpty()) return;
		
		if (AgentConstants.PRINT_TEST_DATA 
				|| AgentConstants.PRINT_TEST_DATA_PF || AgentConstants.PRINT_TEST_DATA_FB) {
			System.out.println("In time: " + time + ", agent: " + me() 
					+ " move to refuges ----- moveToRefuge, PlatoonAgent.java");
		}
		
		move(refuges);
	}

	/*protected void aggregatorStay() throws ActionCommandException {
		EntityID agPos = getAggregatorPosition(getID());
		int dist = world.getDistance(me().getPosition(), agPos);
		
		 * if the distance to the aggragator position is shorter than the minimum of the voiceChannels' range,
		 * the agent is unnecessary to go to there.
		 * ====================================================================================
		 * But I think maybe while the agent random walking , the distane to the aggragator position was
		 * changed to be longer than the minimum
		 * ====================================================================================
		 
		if (dist < Collections.min(world.getConfig().voiceChannels.values(),
				new Comparator<ConfigConstants.VoiceChannel>() {
					@Override
					public int compare(VoiceChannel c1, VoiceChannel c2) {
						return c1.range - c2.range;
					}
				}).range) {
			randomWalk();
		}
		move(agPos);
	}
	
	protected void messengerLoop(int interval) throws ActionCommandException {
		int lastTime = -1;
		EntityID lastSeenID = null;
		for (EntityID id : getAggregators()) {
			int t = world.getTimestamp().getLastSeenTime(getAggregatorPosition(id));
			if (lastTime < t) {
				lastTime = t;
				lastSeenID = id;
			}
		}
		if (lastTime == -1) {
			Set<Area> aggregatorsArea = new HashSet<Area>(getAggregators().size());
			for (EntityID id : getAggregators()) {
				aggregatorsArea.add((Area) world.getEntity(getAggregatorPosition(id)));
			}
			move(aggregatorsArea);
		}
		if (!world.getBurningBuildings().isEmpty() || lastTime - time > interval) {
			move(lastSeenID);
		}
	}*/
	
	/**
	 * When the HP point of mine is less than a certain point, I need to go to refuge and rest.
	 * 
	 * @throws ActionCommandException
	 */
	protected void careSelf() throws ActionCommandException {
		if (me().getHP() - me().getDamage() * (timestep - time) < 16) {
			moveToRefuge();
		}
	}

	
/* ------------------------- The following method was added by Xingsheng Deng - csu ------------------------ */	
//	/* problem method*/
//	protected void RegionalRoadExplorationBehavior() throws ActionCommandException {
//		StandardEntity location;
//		setRoadsIfNotSet();
//
//		this.setCurrentPosition(Locator.getPosition(getID(), world));
//		location = location();
//		
//		if (location.equals(this.road)) {
//			this.road = null;
//		}
//		if (this.road == null) {
//			if (!this.roads.isEmpty()) {
//				this.road = this.roads.poll();
//				this.roads.add(this.road);
//			} else {
//				System.out.println("Agent: " + me() + "'s roads are empty. time: " + time + 
//						" ----- class: PlatoonAgent, method: RegionalRoadExplorationBehavior()");
//			}
//			return;
//		}
//		
//		if (this.road != null) { // search road has blockades only for policeforce -- dxs
//			if (location.equals(oldLocation)) {
//				return;
//			}
//			oldLocation = location();
//			this.road = this.roads.poll();
//			this.roads.add(this.road);
//			move(this.road, this.road.getX(), this.road.getY());
//			/*
//			 * if(me().getStandardURN() == StandardEntityURN.POLICE_FORCE){
//			 *     List<EntityID> blockades = road_.getBlockades(); 
//			 *     if(blockades != null){ 
//			 *         move(road_,road_.getX(),road_.getY()); 
//			 *     } 
//			 * } else{
//			 *     move(road_,road_.getX(),road_.getY()); 
//			 * }
//			 */
//		}
//	}
//
//	/* unused method, it also has some problem */
//	protected void pfRegionalRoadExplorationBehavior() throws ActionCommandException {
//		StandardEntity location;
//		setRoadsIfNotSet();
//
//		if (roads == null) {
//		}
//
//		setCurrentPosition(Locator.getPosition(getID(), world));
//
//		location = location();
//		if (location.equals(road)) {
//			road = null;
//		}
//		if (road == null) {
//			if (!roads.isEmpty()) {
//				// LOG.debug(agent_ + ": New road is set");
//				road = roads.poll();
//				roads.add(road);
//			} else {
//				// LOG.debug(agent_ + ": Roads are empty");
//			}
//			return;
//		}
//		if (road != null) { // search road has blockades only for policeforce  -- dxs
//			// System.out.println("me().getPosition()"+me().getPosition());
//			oldLocation = location();
//			road = roads.poll();
//			roads.add(road);
//			move(road, road.getX(), road.getY());
//			/*
//			 * if(me().getStandardURN() == StandardEntityURN.POLICE_FORCE){
//			 *     List<EntityID> blockades = road_.getBlockades(); 
//			 *     if(blockades != null){ 
//			 *         move(road_,road_.getX(),road_.getY()); 
//			 *     } 
//			 * } else{
//			 *     move(road_,road_.getX(),road_.getY()); 
//			 * }
//			 */
//		}
//	}

//	protected void setRoadsIfNotSet() {
//		List<Road> roads;
//		if (this.roads != null) {
//			return;
//		}
//		roads = getRegionRoads();
//		if (roads != null && !roads.isEmpty()) {
//			this.roads = new LinkedList<Road>();
//			this.roads.addAll(roads);
//		}
//	}

//	public List<Road> getRegionRoads() {
//		List<Road> entities = new ArrayList<Road>();
//		List<EntityRegion> regions = getAssignedRegions();
//		
//		if (regions == null) {
//			return null;
//		}
//		for (EntityRegion region : regions) {
//			entities.addAll(region.getRoads());
//		}
//		return entities;
//	}
	
	public void setCurrentPosition(Point currentPosition) {
		this.currentPosition = currentPosition;
	}
	
	public Point getOldPosition() {
		return oldPosition;
	}
	public Point getLastPosition() {
		return lastPosition;
	}
	public Point getCurrentPosition() {
		return currentPosition;
	}
	public Road getFireBuildingEntrance() {
		return fireBuildingEntrance;
	}
	public Road getCannotLeaveBuildingEntrance() {
		return cannotLeaveBuildingEntrance;
	}
}
