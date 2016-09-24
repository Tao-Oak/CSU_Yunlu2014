package csu.agent.fb;

import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import csu.agent.PlatoonAgent;
import csu.model.AdvancedWorldModel;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.messages.StandardMessageURN;
import rescuecore2.worldmodel.EntityID;

public abstract class AbstractFireBrigadeAgent extends PlatoonAgent<FireBrigade> {
//	protected static final int WITHIN_VIEW = 0;
//	protected static final int WITHIN_EXTINGUISHABLE = 1;
	
	public EntityID extinguishTarget = null;
	public int waterPower = 0;
	
	public void extinguish(EntityID target, int water) throws  ActionCommandException {
		this.extinguishTarget = target;
		this.waterPower = water;
		sendExtinguish(time, target, water);
		throw new ActionCommandException(StandardMessageURN.AK_EXTINGUISH);
	}

	public void extinguish(Building target, int water) throws  ActionCommandException {
		extinguish(target.getID(), water);
	}

	public void extinguish(EntityID target) throws  ActionCommandException {
		extinguish(target, world.getConfig().maxPower);
	}

	public void extinguish(Building target) throws  ActionCommandException {
		extinguish(target.getID());
	}

	public boolean isExtinguishable(FireBrigade fb, Building building) {
		Pair<Integer, Integer> position = fb.getLocation(world);
		final int r = world.getConfig().extinguishableDistance;
		java.awt.geom.Area range = new java.awt.geom.Area(new Ellipse2D.Double(
				position.first() - r, position.second() - r,
				r * 2, r * 2));
		range.intersect(new java.awt.geom.Area(building.getShape()));
		return !range.isEmpty();
	}
	
	public ArrayList<Building> extinguishableBuilding(AdvancedWorldModel world, List<Building> targets){
		ArrayList<Building> inRange = new ArrayList<Building>();
		for (Building building : targets){
			if (world.getDistance(building, me()) < world.getConfig().extinguishableDistance)
				if (building.isFierynessDefined() && building.isOnFire())
					inRange.add(building);
		}
		return inRange;
	}
	
	public ArrayList<Building> visibleFiredBuilding(AdvancedWorldModel world, List<Building> targets) {
		ArrayList<Building> inRange = new ArrayList<Building>();
		
		for (Building building : targets) {
			if (world.getDistance(building, me()) < world.getConfig().viewDistance){
				if (building.isFierynessDefined() && building.isOnFire()) {
					inRange.add(building);
				}
			}
		}
		return inRange;
	}
	
	/** Find a building with minimum extinguish difficulty from a list of building.*/
	public Building findMinDifficultyBuilding(List<Building> targets, boolean withinExtinguishable) {
		Building minDifficultyBuilding = null;
		double minDifficulty = Integer.MAX_VALUE;
		double minDifficultyAffect = 0.0;
		
		for (Building building : targets){
			if (withinExtinguishable) {
				if (!isExtinguishable(building)
						|| world.getTime() - world.getTimestamp().getLastChangedTime(building.getID()) >= 3) {
					continue;
				}
			}
			
			double area = (building.isTotalAreaDefined()) ? building.getTotalArea() : 1.0;
			double affected = world.getEnergyFlow().getIn(building);
			double difficulty = area * affected;
			
			if (difficulty < minDifficulty) {
				minDifficultyBuilding = building;
				minDifficulty = difficulty;
				minDifficultyAffect = world.getEnergyFlow().getOut(building);
			} else if (Math.abs(minDifficulty - difficulty) < 500.0) {
				double affect = world.getEnergyFlow().getOut(building);
				if (minDifficultyAffect < affect) {
					minDifficultyBuilding = building;
					minDifficulty = difficulty;
					minDifficultyAffect = world.getEnergyFlow().getOut(building);
				}
			}
		}
		
		if (minDifficultyBuilding != null)
			return minDifficultyBuilding;
		else 
			return null;
	}
	
	public Building findMinMoveDifficultyBuilding(ArrayList<Building> targets) {
		Building minValueBuilding = null;
		double minValue = Integer.MAX_VALUE;

		for (Building building : targets) {
			final double affect = world.getEnergyFlow().getOut(building);
			final double distance = world.getDistance(building, me());
			final double value = affect * distance;

			if (value < minValue) {
				minValueBuilding = building;
				minValue = value;
			}
		}
		return minValueBuilding;
	}
	
	public void moveToFires() throws ActionCommandException {
		if (world.getBurningBuildings().isEmpty()) {
			System.out.println("Agent: " + me()
					+ " has no burning building in his world model "
					+ "----- time: " + time
					+ ", class: FireBrigadeAgent, method: moveToFires()");
			return;
		}

		Building minValueBuilding = null;
		double minValue = Integer.MAX_VALUE;

		for (Building building : world.getBurningBuildings()) {
			final double affect = world.getEnergyFlow().getOut(building);
			final double distance = world.getDistance(building, me());
			final double value = affect * distance;

			if (value < minValue) {
				minValueBuilding = building;
				minValue = value;
			}
		}
		if (minValueBuilding != null) {
			System.out.println("Agent: " + me()
					+ " moving to a burning building in his world model "
					+ "----- time: " + time
					+ ", class: FireBrigadeAgent, method: moveToFires()");
			moveFront(minValueBuilding, router.getFbCostFunction(minValueBuilding));
		}
		System.out.println("In time: " + time + " Agent: " + me() + " can not find a burning building "
						+ "to move. ----- class: FireBrigadeAgent, method: moveToFires()");
	}
	
	/** 
	 * errorExtinguish can ensure Agents to extinguish fires near him when he was stucked.
	 * Otherwise, Agents just trying to go out and do nothing else. That is bad when there
	 * are fires around him.
	 */
	protected void errorExtinguish() throws ActionCommandException {
		final int extinguishableDistance = world.getConfig().extinguishableDistance;
		for (StandardEntity se : world.getObjectsInRange(me(), extinguishableDistance)) {
			if (se instanceof Building) {
				Building building = (Building) se;
				if (building.getFieryness() > 0 && building.getFieryness() < 4) {
					System.out.println("Agent: " + me() + " is error extinguishing in time: " 
						     + time + " ----- class:FireBrigadeAgent, method: errorExtinguish()");
					extinguish(building);
				}
			}
			System.out.println("In time: " + time + " Agent: " + me() + " can not error extinguish and leave. " 
				     + " ----- class:FireBrigadeAgent, method: errorExtinguish()");
			move(cannotLeaveBuildingEntrance);
		}
	}
	
	public boolean isExtinguishable(Building building) {
		return isExtinguishable(me(), building);
	}

	@Override
	protected EnumSet<StandardEntityURN> getRequestedEntityURNsEnum() {
		return EnumSet.of(StandardEntityURN.FIRE_BRIGADE);
	}
	
/* -------------------------------------------------------------------------------------------------------- */	

	/*@Override
	protected void initUnlookuped() {
		unlookupedBuildings = new HashSet<>(world.getCriticalArea().size());
		for (Area next : world.getCriticalArea().getAreas()) {
			unlookupedBuildings.add(next.getID());
		}
	}

	@Override
	protected void lookupSearchBuildings() throws ActionCommandException {
		if (unlookupedBuildings.isEmpty()) {
			initUnlookuped();
		}
		
		unlookupedBuildings.removeAll(someoneVisitedArea);
		unlookupedBuildings.remove(changed.getChangedEntities());
		
		if (unlookupedBuildings.isEmpty())
			return;
		
		Set<StandardEntity> dest = new HashSet<StandardEntity>(unlookupedBuildings.size());
		for (EntityID id : unlookupedBuildings) {
			dest.add(world.getEntity(id));
		}
		
		if (AgentConstants.PRINT_TEST_DATA) {
			System.out.println("In time: " + time + " Agent: " + me() + " is lookup search building " +
					"----- class: PlatoonAgent, method: lookupSearchBuilding()");
		}
		
		Point selfL = new Point(world.getSelfLocation().first(), world.getSelfLocation().second());
		
		List<EntityID> path = router.getMultiAStar(location(), dest, router.getNormalCostFunction(), selfL);
		
		while (path.size() == 1) {
			unlookupedBuildings.remove(path.get(0));
			dest.remove(world.getEntity(path.get(0)));
			if (dest.isEmpty())
				return;
			path = router.getMultiAStar(location(), dest, router.getNormalCostFunction(), selfL);
		}
		
		if (path != null)
			move(path);
	}*/
//	@Override
//	protected void initUnentered() {
//		unenteredBuildings = new HashSet<>(world.getCsuBuildings().size());
//		for (CSUBuilding next : world.getCsuBuildings()) {
//			if (next.isInflammable()) {
//				unenteredBuildings.add(next.getId());
//			}
//		}
//	}
//	
//	@Override
//	protected void enterSearchBuildings() throws ActionCommandException {
//		if (unenteredBuildings.isEmpty()) {
//			initUnentered();
//		}
//		
//		unenteredBuildings.removeAll(someoneVisitedArea);
//		
//		CSUBuilding building;
//		for (Iterator<EntityID> it = unenteredBuildings.iterator(); it.hasNext(); ) {
//			building = world.getCsuBuilding(it.next());
//			if (building.isEstimatedOnFire()) {
//				it.remove();
//			} else if (building.getEstimatedFieryness() == 8) {
//				it.remove();
//			} else if (!router.isSureReachable(building.getId())) {
//				it.remove();
//			}
//		}
//		
//		if (!unenteredBuildings.isEmpty()) {
//			Set<StandardEntity> dest = new HashSet<StandardEntity>(unenteredBuildings.size());
//			for (EntityID id : unenteredBuildings) {
//				dest.add(world.getEntity(id));
//			}
//			
//			if (AgentConstants.PRINT_TEST_DATA) {
//				System.out.println("Agent: " + me() + " is enter search building in time: " + time + 
//						" ----- class: FireBrigadeAgent, method: think()");
//			}
//			
//			move(dest);
//		}
//	}
}
