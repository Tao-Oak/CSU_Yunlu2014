package csu.agent.pf;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import rescuecore2.misc.Pair;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.PoliceForce;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.StandardMessageURN;
import rescuecore2.worldmodel.EntityID;
import csu.agent.pf.PFLastTaskType.PFClusterLastTaskEnum;
import csu.agent.pf.clearStrategy.AroundBasedStrategy;
import csu.agent.pf.clearStrategy.CenterAreaBasedStrategy;
import csu.agent.pf.clearStrategy.CenterLineBasedStrategy;
import csu.agent.pf.clearStrategy.POSBasedStrategy;
import csu.agent.pf.clearStrategy.TangentBasedStrategy;
import csu.agent.pf.cluster.Cluster;
import csu.agent.pf.cluster.Clustering;
import csu.common.TimeOutException;
import csu.model.AgentConstants;
import csu.model.BuriedHumans;
import csu.model.object.CSUBuilding;
import csu.model.object.CSUEdge;
import csu.model.object.CSURoad;
import csu.model.object.csuZoneEntity.CsuZone;
import csu.model.route.pov.CostFunction;
import csu.standard.Ruler;

public class PoliceForceAgent extends AbstractPoliceForceAgent {
	private Set<Cluster> cantWorkClusters;
	private Map<EntityID, Pair<EntityID, Integer>> pfMap;
	private Set<EntityID> buriedEntrances;
	
	@Override
	protected void initialize() {
		super.initialize();
		assignClearStrategy();
		
		this.needToExpandClusters = new ArrayList<>(clusters);
		this.expandedClusters = new ArrayList<>();

		currentCluster = clusters.get(currentClusterIndex);
		expandedClusters.add(currentCluster);
		needToExpandClusters.remove(currentCluster);

		taskTarget = new PFLastTaskTarget();

		traversalEntranceSet = new HashSet<Road>(currentCluster.getEntranceList());
		traversalCriticalAreas = new HashSet<>(currentCluster.getCriticalAreas());
		traversalRefugeSet = new HashSet<Refuge>(currentCluster.getRefugeList());

		clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
		
		nearBurningBuildings = new HashSet<Building>();
		hadSearchBuringBuildings = new HashSet<Building>();

		entrancesOfBurningZone = new HashSet<Road>();

		searchBurningFlag = true;

		buildingCount = 0;
		for (CsuZone zone : currentCluster.getZoneList()) {
			buildingCount += zone.size();
		}

		cantWorkClusters = new HashSet<Cluster>();
		pfMap = new HashMap<EntityID, Pair<EntityID, Integer>>();
		
		buriedEntrances = new HashSet<EntityID>();
		
		System.out.println(toString() + " was connected. [id=" + getID() + ", uniform=" + getUniform() + "]");
	}

	@Override
	protected void prepareForAct() throws TimeOutException {
		super.prepareForAct();
		world.getCriticalArea().update(router);
		this.updateTaskList();
		
		updatePfCannotToWork();
	}

	@Override
	protected void act() throws ActionCommandException, TimeOutException {
		this.cannotClear();
		this.careSelf();
		if (isBlocked()) {
			
			Blockade target = this.clearStrategy.blockedClear();	
			if (target != null)
				this.clearStrategy.doClear(null, null, target);
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("time = " + time + me() + " is stucked and ramdom walk");
			}
			randomWalk();
		}
		this.leaveBurningBuilding();
		this.stuckedClear();
		this.coincidentWork();

		this.clearStrategy.updateClearPath(lastCyclePath);
		this.clearStrategy.clear();
		
		this.continueLastTask();
		this.traversalRefuge();
		this.helpBuriedAgent();
		this.helpStuckAgent();
		this.searchingBurningBuilding();
		// this.traversalCritical();
		this.helpBuriedHumans();
		this.traversalEntrance();
		this.expandCluster();
		this.randomWalk();
	}
	
	private void stuckedClear() throws ActionCommandException {
		if (isStucked(me())) {
			Blockade blockade = isLocateInBlockade(me());
			if (blockade != null) {
				
				if (AgentConstants.PRINT_TEST_DATA_PF) {
					System.out.println("time = " + time + ", agent = " + me() + " is stucked clear, " +
							"target = " + blockade + " ----- PoliceForceAgent, stuckedClear()");
				}
				this.sendClear(time, blockade.getID());
				throw new ActionCommandException(StandardMessageURN.AK_CLEAR);
			}
		}
	}

	private void assignClearStrategy() {
		switch (clearStrategyType) {
		case AROUND_BASED_STRATEGY:
			clearStrategy = new AroundBasedStrategy(world);
			break;
		case CENTER_AREA_BASED_STRATEGY:
			clearStrategy = new CenterAreaBasedStrategy(world);
			break;
		case CENTER_LINE_BASED_STRATEGY:
			clearStrategy = new CenterLineBasedStrategy(world);
			break;
		case POS_BASED_STRATEGY:
			clearStrategy = new POSBasedStrategy(world);
			break;
		case TANGENT_BASED_STRATEGY:
			clearStrategy = new TangentBasedStrategy(world);
			break;
		}
	}

	private void updateTaskList() {
		if (location() instanceof Refuge) {
			visitedRefuges.add(location().getID());
			coincidentRefuge.remove((Refuge) location());
		}
		
		String string = null;
		FOR:for (Iterator<Human> itor = coincidentBuriedAgent.iterator(); itor.hasNext(); ) {
			Human human = itor.next();
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				if (string == null) {
					string = human.getID().getValue() + "";
				} else {
					string = string + ", " + human.getID().getValue();
				}
			}
			
			if (!human.isPositionDefined()) {
				if (AgentConstants.PRINT_TEST_DATA_PF) {
					System.out.println("time = " + time + ", " + me() + " human: " + human.getID() + 
							"'s position is not defined " +
							"----- class: PoliceForceAgent, method: updateTaskList()");
				}
				
				visitedBuriedAgent.add(human.getID());
				itor.remove();
				continue;
			}
			
			StandardEntity loca = human.getPosition(world);
			if (!(loca instanceof Building)) {
				if (AgentConstants.PRINT_TEST_DATA_PF) {
					System.out.println("time = " + time + ", " + me() + " human: " + human.getID() + 
							"'s position is not building " +
							"----- class: PoliceForceAgent, method: updateTaskList()");
				}
				
				visitedBuriedAgent.add(human.getID());
				itor.remove();
				continue;
			}
			
			Building building = (Building) loca;
			
			for (Road next : world.getEntrance().getEntrance(building)) {
				if (!isVisible(next.getID())) 
					continue;
				CSURoad road = world.getCsuRoad(next.getID());
				if (road.isNeedlessToClear()) {
					if (AgentConstants.PRINT_TEST_DATA_PF) {
						System.out.println("time = " + time + ", " + me() + " human: " + human.getID() + 
								"'s position's entrance is needless to clear " +
								"----- class: PoliceForceAgent, method: updateTaskList()");
					}
					
					visitedBuriedAgent.add(human.getID());
					itor.remove();
					continue FOR;
				}
			}
		}
		
		if (AgentConstants.PRINT_TEST_DATA_PF) {
			System.out.println("time = " + time + ", " + me() + " coincident buried agents = [" 
					+ string  + "] ----- class: PoliceForceAgent, method: updateTaskList()");
		}
		
		
		StandardEntity entity = null;
		for (EntityID changed : getChanged()) {
			entity = world.getEntity(changed);
			if (entity instanceof Road) {
				CSURoad road = world.getCsuRoad(changed);
				if (!road.isEntrance())
					continue;
				if (road.getSelfRoad().isBlockadesDefined() && road.getSelfRoad().getBlockades().size() > 0)
					continue;
				
				if (road.isNeedlessToClear())
					this.traversalEntranceSet.remove(road.getSelfRoad());
			}
		}
	}

	private void cannotClear() throws csu.agent.Agent.ActionCommandException {
		if (!me().isHPDefined() || me().getHP() <= 1000) {

			clusterLastTaskType = PFClusterLastTaskEnum.CANNOT_TO_CLEAR;
			Collection<StandardEntity> allReguge = world.getEntitiesOfType(StandardEntityURN.REFUGE);

			CostFunction costFunc = router.getNormalCostFunction();
			Point selfL = new Point(me().getX(), me().getY());
			lastCyclePath = router.getMultiAStar(location(), allReguge, costFunc, selfL);
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				
				String str = null;
				for (EntityID next : lastCyclePath) {
					if (str == null) {
						str = next.getValue() + "";
					} else {
						str = str + "," + next.getValue();
					}
				}
				
				System.out.println("time = " + time + ", " + me() + " can't to clear and move to refuge. " +
						"path = [" + str + "] ----- class: PoliceForceAgent, method: cantToClear()");
			}
			
			move(lastCyclePath);
		}
	}

	private void leaveBurningBuilding() throws ActionCommandException {
		/*StandardEntity entity;
		if (location() instanceof Building && !(location() instanceof Refuge)) {
			Building building = (Building) location();
			if ((building.isFierynessDefined() && building.isOnFire())
					|| (building.isFierynessDefined() && building.getFieryness() != 8
							&& building.isTemperatureDefined() && building.getTemperature() > 35)) {

				if (AgentConstants.PRINT_TEST_DATA_PF) {
					System.out.println("Agent "
									+ me()
									+ " in time: "
									+ time
									+ " was in a dangerous Building and trying to go out it."
									+ " ----- class: PoliceForceAgent, method: leaveBurningBuilding()");
				}

				for (EntityID next : building.getNeighbours()) {
					entity = world.getEntity(next);
					if (entity instanceof Building) {
						unenteredBuildings.remove(building);
					}
				}
				clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
				moveToRefuge();
			}
		}*/
	}
	
	private void coincidentWork() throws ActionCommandException {
		coincidentTaskUpdate();
		coincidentHelpStuckedAgent();
		coincidentHelpBuriedAgent();
		coincidentCheckRefuge();
	}
	
	private void coincidentTaskUpdate() {
		for (StandardEntity next : world.getObjectsInRange(me().getID(), 50000)) {
			if (next instanceof Refuge) {
				if (visitedRefuges.contains(next.getID())) {
					coincidentRefuge.remove((Refuge)next);
					continue;
				}
				coincidentRefuge.add((Refuge) next);
			} else if (next instanceof Human && !(next instanceof Civilian)){
				if (visitedBuriedAgent.contains(next.getID())) {
					coincidentBuriedAgent.remove((Human)next);
					continue;
				}
				
				Human human = (Human) next;
				if (human.isBuriednessDefined() && human.getBuriedness() > 0 && human.isPositionDefined()) {
					StandardEntity loca = human.getPosition(world);
					if (loca instanceof Building) {
						coincidentBuriedAgent.add(human);
					}
				}
			}
		}
	}
	
	private void coincidentCheckRefuge() throws ActionCommandException {
		if (coincidentRefuge.size() > 0) {
			CostFunction costFunc = router.getPfCostFunction();
			Point selfL = new Point(me().getX(), me().getY());
			lastCyclePath = router.getMultiDest(location(), coincidentRefuge, costFunc, selfL);
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				String str = null;
				for (EntityID next : lastCyclePath) {
					if (str == null)
						str = next.getValue() + "";
					else
						str = str + ", " + next.getValue();
				}
				
				EntityID destination = lastCyclePath.get(lastCyclePath.size() - 1);
				
				System.out.println("time = " + time + ", " + me() + " moving to clear coincident " +
						"refuge = " + destination.getValue() + ", path = [" + str + "] ----- " +
								"class: PoliceForceAgent, method: coincidentCheckRefuge()");
			}
			
			move(lastCyclePath);
		}
	}

	private void coincidentHelpBuriedAgent() throws ActionCommandException {
		if (coincidentBuriedAgent.size() > 0) {
			CostFunction costFunc = router.getPfCostFunction();
			Point selfL = new Point(me().getX(), me().getY());
			
			List<StandardEntity> dest = new ArrayList<>();
			
			for (Human next : coincidentBuriedAgent) {
				StandardEntity loca = next.getPosition(world);
				if (loca instanceof Building) {
					dest.addAll(world.getEntrance().getEntrance((Building) loca));
				}
			}
			
			lastCyclePath = router.getMultiDest(location(), dest, costFunc, selfL);
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				String str = null;
				for (EntityID next : lastCyclePath) {
					if (str == null)
						str = next.getValue() + "";
					else
						str = str + ", " + next.getValue();
				}
				
				EntityID destination = lastCyclePath.get(lastCyclePath.size() - 1);
				
				System.out.println("time = " + time + ", " + me() + " moving to clear coincident " +
						"buried agent = " + destination.getValue() + ", path = [" + str + 
						"] ----- class: PoliceForceAgent, method: coincidentHelpBuriedAgent()");
			}
			
			move(lastCyclePath);
		}
	}
	
	private void coincidentHelpStuckedAgent() throws ActionCommandException {
		List<EntityID> inChangeSetAT_PF = new ArrayList<>();
		List<EntityID> inChangeSetBlockades = new ArrayList<>();
		
		for (EntityID next : getChanged()) {
			StandardEntity entity = world.getEntity(next);
			if (entity instanceof AmbulanceTeam || entity instanceof FireBrigade) {
				inChangeSetAT_PF.add(next);
			} else if (entity instanceof Blockade) {
				inChangeSetBlockades.add(next);
			}
		}
		
		List<Blockade> needClearBlockades = new ArrayList<>();
		for (EntityID next : inChangeSetBlockades) {
			Blockade blockade = (Blockade) world.getEntity(next);
			for (EntityID at_pf : inChangeSetAT_PF) {
				Human agent = (Human)world.getEntity(at_pf);
				double dis = Ruler.getDistanceToBlock(blockade, new Point(agent.getX(), agent.getY()));
				if (dis < 2000) {
					needClearBlockades.add(blockade);
					break;
				}
			}
		}
		
		Blockade targetBlockade = null;
		double minDistance = Double.MAX_VALUE;
		for (Blockade next : needClearBlockades) {
			double dis = Ruler.getDistanceToBlock(next, me().getX(), me().getY());
			if (dis < minDistance) {
				minDistance = dis;
				targetBlockade = next;
			}
		}
		
		if (targetBlockade != null) {
			StandardEntity entity = world.getEntity(targetBlockade.getPosition());
			if (!(entity instanceof Area))
				return;
			Area blockadeLocation = (Area) entity;
			
			CostFunction costFunc = router.getPfCostFunction();
			Point selfL = new Point(me().getX(), me().getY());
			lastCyclePath = router.getAStar(location(), blockadeLocation, costFunc, selfL);
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				String str = null;
				for (EntityID next : lastCyclePath) {
					if (str == null)
						str = next.getValue() + "";
					else
						str = str + ", " + next.getValue();
				}
				
				System.out.println("time = " + time + ", " + me() + " moving to help agents, " +
						"target blockade = " + targetBlockade.getID() + ", path = [" + lastCyclePath 
						+ " ----- class: PoliceForceAgent, method: coincidentHelpStuckedAgent()");
			}
			move(lastCyclePath, targetBlockade.getX(), targetBlockade.getY());
		}
	}
	
	private void continueLastTask() throws ActionCommandException {

		EntityID taskEntityID;
		switch (clusterLastTaskType) {
		case CANNOT_TO_CLEAR:
			if (this.location() instanceof Refuge) {
				clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
				break;
			}
			moveToRefuge();
			clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
			break;
			
		case TRAVERSAL_REFUGE:
			taskEntityID = taskTarget.getTraversalRefuge().getID();
			if (changed.getChangedEntities().contains(taskEntityID)) {
				if (router.isSureReachable(taskEntityID)) {

					clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
					break;
				}
			}
			if (this.location() instanceof Building) {
				if (((Building) this.location()).equals(taskTarget.getTraversalRefuge())) {
					clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
					break;
				}
			}

			CostFunction costFunc_1 = router.getPfCostFunction();
			lastCyclePath = router.getAStar(me(), taskTarget.getTraversalRefuge(), costFunc_1);
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				
				String str = null;
				for (EntityID next : lastCyclePath) {
					if (str == null) {
						str = next.getValue() + "";
					} else {
						str = str + ", " + next.getValue();
					}
				}
				
				System.out.println("time = " + time + ", " + me() + " continue traversal refuge = " 
						+ taskEntityID.getValue() + ", the path = [" + str + "] " 
						+ "----- class: PoliceForceAgent, method: continueLastTask()");
			}

			move(lastCyclePath);
			
		/*case TRAVERSAL_CRITICAL_AREA:
			taskEntityID = taskTarget.getTraversalCriticalArea().getID();
			if (location().getID().getValue() == taskEntityID.getValue()) {
				lastTask = PFLastTaskEnum.NO_TAST;
				break;
			}
			CostFunction costFunc = router.getPfCostFunction();
			lastCyclePath = router.getAStar(me(), taskTarget.getTraversalCriticalArea(), costFunc);
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				String str = null;
				for (EntityID next : lastCyclePath) {
					if (str == null) {
						str = next.getValue() + "";
					} else {
						str = str + ", " + next.getValue();
					}
				}
				
				System.out.println("time = " + time + ", agent = " + me() + " continue traversal " +
						"critical: " + taskEntityID.getValue() + ", path = [" + str + "],"
						+ " ----- class: PoliceForceAgent, method: continueLastTask()");
			}
			move(lastCyclePath);
			break;*/
			
		case TRAVERSAL_ENTRANCE:
			taskEntityID = taskTarget.getTraversalEntrance().getID();

			if (changed.getChangedEntities().contains(taskEntityID)) {
				CSURoad road = world.getCsuRoad(taskEntityID);
				if (road.isNeedlessToClear()) {
					clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
					break;
				}
			}
			
			if (this.location() instanceof Road) {
				if (location().getID().getValue() == taskEntityID.getValue()) {
					boolean shouldBreak = nearTargetBehavior(taskEntityID, false);
					if (shouldBreak)
						break;
				}
			}
			
			if (location() instanceof Building) {
				for (Road next : world.getEntrance().getEntrance((Building) location())) {
					if (next.getID().getValue() == taskEntityID.getValue()) {
						boolean shouldBreak = nearTargetBehavior(taskEntityID, true);
						if (shouldBreak)
							break;
					}
				}
			}
			
			CostFunction costFunc_2 = router.getPfCostFunction();
			lastCyclePath = router.getAStar(me(), taskTarget.getTraversalEntrance(), costFunc_2);

			if (AgentConstants.PRINT_TEST_DATA_PF) {
				
				String str = null;
				for (EntityID next : lastCyclePath) {
					if (str == null) {
						str = next.getValue() + "";
					} else {
						str = str + ", " + next.getValue();
					}
				}
				
				System.out.println("time = " + time + ", " + me() + " continue traversal entrance = " 
						+ taskEntityID.getValue() + ", the path = [" + str + "] " 
						+ "----- class: PoliceForceAgent, method: continueLastTask()");
			}
			move(lastCyclePath);
			break;
			
		case HELP_STUCK_HUMAN:
			clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
			break;
		case NO_TAST:
			break;
		default:
			clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
			break;
		}
	}
	
	private boolean nearTargetBehavior(EntityID taskEntityID, boolean inBuilding) throws ActionCommandException {
		CSURoad road = world.getCsuRoad(taskEntityID);
		if (road.isNeedlessToClear()) {
			clusterLastTaskType = PFClusterLastTaskEnum.NO_TAST;
			return true;
		} else if (road.getCsuBlockades().isEmpty()) {
			for (EntityID neighbour : road.getSelfRoad().getNeighbours()) {
				StandardEntity entity = world.getEntity(neighbour);
				if (entity instanceof Building)
					continue;
				for (CSUEdge edge : road.getCsuEdgeTo(neighbour)) {
					if (!edge.getUnderlyingEdge().isPassable())
						continue;
					if (!edge.isNeedToClear())
						continue;
					List<EntityID> path = new ArrayList<>();
					if (inBuilding) {
						path.add(location().getID());
					} 
					path.add(edge.getNeighbours().second());
					path.add(edge.getNeighbours().first());
					
					lastCyclePath = path;
					move(lastCyclePath);
				}
			}
		}
		return false;
	}
	
	private void traversalRefuge() throws ActionCommandException {
		if (traversalRefugeSet.size() == 0) {
			return;
		}

		CostFunction costFunc = router.getPfCostFunction();
		Point selfL = new Point(me().getX(), me().getY());
		lastCyclePath = router.getMultiAStar(location(), traversalRefugeSet, costFunc, selfL);

		Building destination = (Building) world.getEntity(lastCyclePath.get(lastCyclePath.size() - 1));
		traversalRefugeSet.remove(destination);
		clusterLastTaskType = PFClusterLastTaskEnum.TRAVERSAL_REFUGE;
		taskTarget.setTraversalRefuge(destination);
		taskTarget.setEntlityList(lastCyclePath);

		if (AgentConstants.PRINT_TEST_DATA_PF) {
			
			String str = null;
			for (EntityID next : lastCyclePath) {
				if (str == null) {
					str = next.getValue() + "";
				} else {
					str = str + ", " + next.getValue();
				}
			}
			
			System.out.println("time = " + time + ", " + me() + " traversal refuge = " 
					+ destination.getID().getValue() + ", the path = [" + str + "] " 
					+ "----- class: PoliceForceAgent, method: traversalRefuge()");
		}

		move(lastCyclePath);
	}

	/*private void traversalCritical() throws ActionCommandException {
		if (traversalCriticalAreas.size() == 0)
			return;
		CostFunction costFunc = router.getPfCostFunction();
		Point selfL = new Point(me().getX(), me().getY());
		lastCyclePath = router.getMultiAStar(location(), traversalCriticalAreas, costFunc, selfL);
		
		Area destination = world.getEntity(lastCyclePath.get(lastCyclePath.size() - 1), Area.class);
		traversalCriticalAreas.remove(destination);
		
		lastTask = PFLastTaskEnum.TRAVERSAL_CRITICAL_AREA;
		taskTarget.setTraversalCriticalArea(destination);
		taskTarget.setEntlityList(lastCyclePath);
		
		if (AgentConstants.PRINT_TEST_DATA_PF) {
			String str = null;
			for (EntityID next : lastCyclePath) {
				if (str == null) {
					str = next.getValue() + "";
				} else {
					str = str + ", " + next.getValue();
				}
			}
			
			System.out.println("time = " + time + ", agent = " + me() + " traversal critical: " 
					+ destination.getID().getValue() + ", path = [" + str + "],"
					+ " ----- class: PoliceForceAgent, method: traversalCritical()");
		}
		
		move(lastCyclePath);
	}*/
	
	private void traversalEntrance() throws ActionCommandException {
		if (traversalEntranceSet.size() == 0) {
			return;
		}

		CostFunction costFunc = router.getPfCostFunction();
		Point selfL = new Point(me().getX(), me().getY());
		lastCyclePath = router.getMultiAStar(location(), traversalEntranceSet, costFunc, selfL);
		Road destination = (Road) world.getEntity(lastCyclePath.get(lastCyclePath.size() - 1));

		traversalEntranceSet.remove(destination);

		clusterLastTaskType = PFClusterLastTaskEnum.TRAVERSAL_ENTRANCE;
		taskTarget.setTraversalEntrance(destination);
		taskTarget.setEntlityList(lastCyclePath);

		if (AgentConstants.PRINT_TEST_DATA_PF) {
			
			String str = null;
			for (EntityID next : lastCyclePath) {
				if (str == null) {
					str = next.getValue() + "";
				} else {
					str = str + ", " + next.getValue();
				}
			}
			
			System.out.println("time = " + time + ", " + me() + " traversal Entrance = " 
					+ destination.getID().getValue() + ", the path = [" + str + "] " 
					+ "----- class: PoliceForceAgent, method: traversalEntrance()");
		}
		
		move(lastCyclePath);
	}
	
	private void traversalRoads(Set<Road> roads) throws ActionCommandException {

		if (roads.size() == 0) {
			return;
		}

		CostFunction costFunc = router.getPfCostFunction();
		Point selfL = new Point(me().getX(), me().getY());
		lastCyclePath = router.getMultiAStar(location(), roads, costFunc, selfL);
		Road destination = (Road) world.getEntity(lastCyclePath.get(lastCyclePath.size() - 1));

		roads.remove(destination);
		this.traversalEntranceSet.remove(destination);

		clusterLastTaskType = PFClusterLastTaskEnum.TRAVERSAL_ENTRANCE;
		taskTarget.setTraversalEntrance(destination);
		taskTarget.setEntlityList(lastCyclePath);

		if (AgentConstants.PRINT_TEST_DATA_PF) {
			
			String str = null;
			for (EntityID next : lastCyclePath) {
				if (str == null) {
					str = next.getValue() + "";
				} else {
					str = str + ", " + next.getValue();
				}
			}
			
			System.out.println("time = " + time + ", " + me() + " traversal Entrance = " 
					+ destination.getID().getValue() + ", the path = [" + str + "] " 
					+ "----- class: PoliceForceAgent, method: traversalRoads()");
		}
		
		move(lastCyclePath);
	}
	
	private void helpStuckAgent() throws ActionCommandException {
		/*Set<EntityID> changeSetStuckAgents = new HashSet<>();
		
		for (EntityID next : getChanged()) {
			StandardEntity entity = world.getEntity(next);
			
			if (entity instanceof AmbulanceTeam || entity instanceof FireBrigade) {
				if (isStucked((Human)entity))
					changeSetStuckAgents.add(next);
			}
		}
		
		if (changeSetStuckAgents.size() > 0) {
			
			EntityID target = null;
			double minDistance = Double.MAX_VALUE;
			
			for (EntityID next : changeSetStuckAgents) {
				double distance = world.getDistance(me().getID(), next);
				if (distance < minDistance) {
					minDistance = distance;
					target = next;
				}
			}
			
			if (target != null) {
				Human targetHuman = world.getEntity(target, Human.class);
				
				StandardEntity hu_posi_e = world.getEntity(targetHuman.getPosition());
				if (hu_posi_e instanceof Road) {
					CostFunction costFunc = router.getPfCostFunction();
					Point selfL = new Point(world.getSelfLocation().first(), world.getSelfLocation().second());
					Area stucHum_A = (Area) hu_posi_e;
					lastCyclePath = router.getAStar(location(), stucHum_A, costFunc, selfL);
					move(lastCyclePath, targetHuman.getX(), targetHuman.getY());
				}
			}
		}*/
		
		Collection<StandardEntity> PFEntity = world.getEntitiesOfType(StandardEntityURN.POLICE_FORCE);
		
		Set<Human> pfs = new HashSet<>();
		for (StandardEntity next : PFEntity) {
			pfs.add((Human)next);
		}

		List<EntityID> closetHuman = new ArrayList<EntityID>();
		for (EntityID next : world.getStuckedAgents()) {
			if (next.getValue() == me().getID().getValue())
				continue;
			
			if (world.getPoliceForceIdList().contains(next))
				continue;

			final Human stuckHuman = (Human)world.getEntity(next);
			
			SortedSet<Human> sort = new TreeSet<>(new Comparator<Human>() {
				@Override
				public int compare(Human o1, Human o2) {
					int dis_1 = world.getDistance(o1.getPosition(), stuckHuman.getPosition());
					int dis_2 = world.getDistance(o2.getPosition(), stuckHuman.getPosition());
					
					if (dis_1 > dis_2)
						return 1;
					if (dis_1 < dis_2)
						return -1;
					
					if (dis_1 == dis_2) {
						if (o1.getID().getValue() > o2.getID().getValue())
							return 1;
						if (o1.getID().getValue() < o2.getID().getValue())
							return -1;
					}
					return 0;
				}
			});
			
			sort.addAll(pfs);
			
			StandardEntity first = sort.first();
			sort.remove(first);
			StandardEntity second = sort.first();
			sort.remove(second);
			StandardEntity third = sort.first();
			
			if (first.equals(me()) || second.equals(me()) || third.equals(me())) {
				closetHuman.add(next);
			}
		}

		int minInt = Integer.MAX_VALUE;
		StandardEntity closetEntity = null;
		for (EntityID next : closetHuman) {
			StandardEntity stuckHuman = world.getEntity(next);
			int dis = world.getDistance(me(), stuckHuman);
			if (dis <= minInt) {
				minInt = dis;
				closetEntity = stuckHuman;
			}
		}
		if (closetEntity != null) {
			Human targetHuman = (Human) closetEntity;
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("In time: "
								+ time
								+ " Agent: "
								+ me()
								+ " helpStuckHuman."
								+ " ----- class: PoliceForceAgent, method: helpStuckHuman()");
			}
			 
			StandardEntity hu_posi_e = world.getEntity(targetHuman.getPosition());
			if (hu_posi_e instanceof Road) {
				Point selfL = new Point(world.getSelfLocation().first(), world.getSelfLocation().second());
				Area stucHum_A = (Area) hu_posi_e;
				lastCyclePath = router.getAStar(location(), stucHum_A, router.getPfCostFunction(), selfL);
				move(lastCyclePath, targetHuman.getX(), targetHuman.getY());
			}
		}
	}
	
	private void helpBuriedAgent() throws ActionCommandException {
		Collection<StandardEntity> PFEntity = world.getEntitiesOfType(StandardEntityURN.POLICE_FORCE);
		Set<Human> pfs = new HashSet<>();
		for (StandardEntity next : PFEntity) {
			pfs.add((Human)next);
		}
		
		BuriedHumans buriedHumanFactory = world.getBuriedHumans();
		Set<Human> buriedAgents = new HashSet<>();
		Set<Road> buriedAgentEntrance = new HashSet<>();
		
		for (EntityID next : buriedHumanFactory.getTotalBuriedHuman()) {
			StandardEntity entity = world.getEntity(next);
			if (!(entity instanceof Human))
				continue;
			if (entity instanceof Civilian)
				continue;
			
			final Human human = (Human) entity;
			SortedSet<Human> sort = new TreeSet<>(new Comparator<Human>() {
				@Override
				public int compare(Human o1, Human o2) {
					
					int dis_1 = world.getDistance(o1.getPosition(), human.getPosition());
					int dis_2 = world.getDistance(o2.getPosition(), human.getPosition());
					
					if (dis_1 > dis_2)
						return 1;
					if (dis_1 < dis_2)
						return -1;
					
					if (dis_1 == dis_2) {
						if (o1.getID().getValue() > o2.getID().getValue())
							return 1;
						if (o1.getID().getValue() < o2.getID().getValue())
							return -1;
					}
					return 0;
				}
			});
			sort.addAll(pfs);
			
			StandardEntity first = sort.first();
			sort.remove(first);
			StandardEntity second = sort.first();
			sort.remove(second);
			StandardEntity third = sort.first();
			
			if (first.equals(me()) || second.equals(me()) || third.equals(me())) {
				buriedAgents.add(human);
			}
		}
		
		FOR: for (Iterator<Human> itor = buriedAgents.iterator(); itor.hasNext(); ) {
			Human human = itor.next();
			if (visitedBuriedAgent.contains(human.getID())) {
				itor.remove();
				continue;
			}
			StandardEntity location = world.getEntity(human.getPosition());
			if (!(location instanceof Building))
				continue;
			Building loca_bu = (Building) location;
			
			for (Road road : world.getEntrance().getEntrance(loca_bu)) {
				if (!isVisible(road))
					continue;
				CSURoad csuRoad = world.getCsuRoad(road.getID());
				if (csuRoad.isNeedlessToClear()) {
					visitedBuriedAgent.add(human.getID());
					itor.remove();
					continue FOR;
				}
			}
			
			for (Road road : world.getEntrance().getEntrance(loca_bu)) {
				buriedAgentEntrance.add(road);
			}
		}
		
		if (buriedAgentEntrance.isEmpty())
			return;
		
		if (AgentConstants.PRINT_TEST_DATA_PF) {
			System.out.println("time = " + time + ", " + me() + " go to help buried agents" +
					" ----- class: PoliceForceAgent, method: helpBuriedAgent()");
		}
		
		traversalRoads(buriedAgentEntrance);
	}
	
	private void helpBuriedHumans() throws csu.agent.Agent.ActionCommandException {
		updateBuriedEntrances();
		if(buriedEntrances.size() == 0){
			return;
		}
		Set<Road> temp = new HashSet<Road>();
		for (EntityID entityID : buriedEntrances) {
			temp.add((Road)world.getEntity(entityID));
		}
		traversalRoads(temp);
	}
	
	private void updateBuriedEntrances() {
		BuriedHumans buriedHumanFactory = world.getBuriedHumans();
		Set<EntityID> buriedHumans = buriedHumanFactory.getTotalBuriedHuman();

		buriedEntrances.clear();
		for (EntityID entityID : buriedHumans) {
			StandardEntity SE = world.getEntity(entityID);
			if (!(SE instanceof Human))
				continue;
			Human human = (Human) SE;
			EntityID positionID = human.getPosition();
			StandardEntity positionSE = world.getEntity(positionID);
			
			if (!(positionSE instanceof Building))
				continue;
			Building building = (Building) positionSE;

			if (currentCluster.containtBuilding(entityID)) {
				for (Road road : world.getEntrance().getEntrance(building)) {
					if (traversalEntranceSet.contains(road))
						buriedEntrances.add(road.getID());
				}
			}
		}
	}

	/**
	 * If a pf finished its current cluster, then move to next one.
	 */
	private void expandCluster() {
		Cluster nextClusterPF = getNextCluster();
		if (nextClusterPF == null) {
			System.out.println("cant to find next cluster");
		} else {
			expandedClusters.add(nextClusterPF);
			needToExpandClusters.remove(nextClusterPF);
			currentCluster = nextClusterPF;
			currentClusterIndex = clusters.indexOf(currentCluster);

			traversalRefugeSet.addAll(nextClusterPF.getRefugeList());
			traversalEntranceSet.addAll(nextClusterPF.getEntranceList());

			for (CsuZone zone : nextClusterPF.getZoneList()) {
				buildingCount += zone.size();
			}

			searchBurningFlag = true;

			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("time = " + time + ", " + me() + " expand cluster");
			}
		}
	}

	private Cluster getNextCluster() {
		
		ArrayList<Cluster> canToWorkClusters = new ArrayList<Cluster>();
		canToWorkClusters.addAll(clusters);
		canToWorkClusters.removeAll(cantWorkClusters);
		// ClusterPF targetCluster;
		for (Cluster next : cantWorkClusters) {
			if (expandedClusters.contains(next)) {
				continue;
			}
			Cluster closestCluster = Clustering.getClosestCluster(canToWorkClusters, next);
			if (closestCluster.equals(currentCluster)) {
				return closestCluster;
			}
		}
		return Clustering.getClosestCluster(needToExpandClusters, currentCluster);
		
		/*this.cantWorkClusters.removeAll(expandedClusters);
		
		Cluster closestCluster = Clustering.getClosestCluster(cantWorkClusters, currentCluster);
		
		if (closestCluster != null) {
			return closestCluster;
		} else {
			return Clustering.getClosestCluster(needToExpandClusters, currentCluster);
		}*/
	}

	private void updatePfCannotToWork() {
		this.cantWorkClusters.clear();
		
		Set<EntityID> buriedID = world.getBuriedHumans().getTotalBuriedHuman();
		for (EntityID entityID : buriedID) {
			StandardEntity SE = world.getEntity(entityID);
			if (SE instanceof PoliceForce) {
				int indexOfCluster = Clustering.getClusterIndexAgentBelongTo(entityID, clusters);
				cantWorkClusters.add(clusters.get(indexOfCluster));
			}
		}

		Collection<StandardEntity> pfs = world.getEntitiesOfType(StandardEntityURN.POLICE_FORCE);

		for (StandardEntity next : pfs) {
			Human human = (Human) next;
			StandardEntity position = world.getEntity(human.getPosition());
			if (position instanceof Human) {
				int indexOfCluster = Clustering.getClusterIndexAgentBelongTo(next.getID(), clusters);
				cantWorkClusters.add(clusters.get(indexOfCluster));
			} else if (position instanceof Area) {
				Pair<EntityID, Integer> positionPair = pfMap.get(next);

				if (positionPair == null) {
					positionPair = new Pair<EntityID, Integer>(position.getID(), new Integer(1));
				} else {
					if (position.getID().getValue() == positionPair.first().getValue()) {
						positionPair = new Pair<EntityID, Integer>(
								positionPair.first(), positionPair.second() + 1);

						if (position instanceof Building) {// include refuge
							if (positionPair.second() >= 5) {
								int indexOfCluster = Clustering
										.getClusterIndexAgentBelongTo(next.getID(), clusters);
								cantWorkClusters.add(clusters.get(indexOfCluster));
							}
							
						}else if (positionPair.second() >= 35) {// 35 should be change
							int indexOfCluster = Clustering
									.getClusterIndexAgentBelongTo(next.getID(), clusters);
							cantWorkClusters.add(clusters.get(indexOfCluster));
						}
					} else {
						positionPair = new Pair<EntityID, Integer>(position.getID(), new Integer(1));
					}
				}
				pfMap.put(next.getID(), positionPair);
			}
		}
	}

	private void searchingBurningBuilding() throws ActionCommandException {
		if (!searchBurningFlag) {
			return;
		}
		
		if (entrancesOfBurningZone.size() == 0) {
			CsuZone searchZone = getBurningZone();
			if (searchZone == null) {
				return;
			}
			
			if (hadSearchBuringBuildings.size() >= buildingCount) {
				searchBurningFlag = false;
				return;
			}
			
			entrancesOfBurningZone = getEntranceOfZone(searchZone);

			for (CSUBuilding csuBuilding : searchZone) {
				hadSearchBuringBuildings.add(csuBuilding.getSelfBuilding());
				Building bu = csuBuilding.getSelfBuilding();
				if (!bu.isFierynessDefined())
					continue;
				if (bu.getFieryness() == 8 || bu.getFieryness() == 7 || bu.getFieryness() == 3) {
					entrancesOfBurningZone.removeAll(world.getEntrance().getEntrance(bu));
				}
			}
			traversalEntranceSet.removeAll(entrancesOfBurningZone);
		}

		if (entrancesOfBurningZone.size() == 0) {
			return;
		} else {
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("time = " + time + ", " + me() + " traversal burning zones" +
						" ----- class: PoliceForceAgent, method: traversalRoads()");
			}
			
			traversalRoads(entrancesOfBurningZone);
		}
	}

	private CsuZone getBurningZone() {
		if (hadSearchBuringBuildings.size() >= buildingCount) {
			// had done the burning building
			return null;
		}
		nearBurningBuildings.clear();
		nearBurningBuildings.addAll(world.getBurningBuildings());
		nearBurningBuildings.removeAll(hadSearchBuringBuildings);
		
		Cluster initiCluster = clusters.get(assignedClusterIndex);
		
		FOR: for (Iterator<Building> itor = nearBurningBuildings.iterator(); itor.hasNext(); ) {
			Building building = itor.next();
			
			if (initiCluster.containtBuilding(building.getID())) {
				continue FOR;
			}
			
			for (Integer next : initiCluster.getNeighbours()) {
				Cluster cluster = clusters.get(next.intValue());
				if (cluster.containtBuilding(building.getID())) {
					continue FOR;
				}
			}
			
			itor.remove();
		}

		if (nearBurningBuildings.isEmpty())
			return null;
		
		CostFunction costFunc = router.getPfCostFunction();
		Point selfL = new Point(me().getX(), me().getY());
		List<EntityID> pathList = router.getMultiAStar(location(), nearBurningBuildings, costFunc, selfL);

		EntityID destination = pathList.get(pathList.size() - 1);
		CsuZone searchZone = world.getZones().getBuildingZone(destination);
		
		return searchZone;
	}

	/**
	 * Get all entrance of a zone, excluding those entrances whose all
	 * beighbours are buildings.
	 * 
	 * @param zone
	 *            the target zone
	 * @return the entrance of target zone
	 */
	private Set<Road> getEntranceOfZone(CsuZone zone) {
		Set<Road> entrancesOfZone = zone.getAllEntranceRoad();
		List<EntityID> neighbours;
		for (Road road : entrancesOfZone) {
			neighbours = road.getNeighbours();
			int flag = 0;
			for (EntityID entityID : neighbours) {
				if (world.getEntity(entityID) instanceof Road) {
					flag = 1;
					break;
				}
			}
			if (flag == 0) {
				entrancesOfZone.remove(road);
			}
		}
		return entrancesOfZone;
	}
	
	public double distanceToArea(Area entrance) {
		if (me().isXDefined() && me().isYDefined() && entrance.isXDefined()
				&& entrance.isYDefined()) {
			Point2D firstPoint = new Point2D(me().getX(), me().getY());
			Point2D secondPoint = new Point2D(entrance.getX(), entrance.getY());
			double d = GeometryTools2D.getDistance(firstPoint, secondPoint);
			return d;
		}
		return -1;
	}
	
	@Override
	public void move(List<EntityID> path) throws ActionCommandException {
		if (path.size() > 2) {
			EntityID id_1 = path.get(0);
			EntityID id_2 = path.get(1);
			if (id_1.getValue() == id_2.getValue())
				path.remove(0);
		}
		this.clearStrategy.updateClearPath(path);
		this.clearStrategy.clear();
		sendMove(time, path);
		this.lastCyclePath = null;
		throw new ActionCommandException(StandardMessageURN.AK_MOVE);
	}
	
	@Override
	public void move(List<EntityID> path, int destX, int destY) throws ActionCommandException {
		if (path.size() > 2) {
			EntityID id_1 = path.get(0);
			EntityID id_2 = path.get(1);
			if (id_1.getValue() == id_2.getValue())
				path.remove(0);
		}
		
		this.clearStrategy.updateClearPath(path);
		this.clearStrategy.clear();
		sendMove(time, path, destX, destY);
		this.lastCyclePath = null;
		throw new ActionCommandException(StandardMessageURN.AK_MOVE);
	}

	@Override
	protected void cantMove() throws csu.agent.Agent.ActionCommandException {
		// TODO Auto-generated method stub
	}

	@Override
	public String toString() {
		return "CSU_YUNLU police force agent";
	}
}
