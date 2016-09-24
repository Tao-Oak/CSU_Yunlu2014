package csu.agent.fb;

import java.util.Collection;
import csu.agent.fb.actionStrategy.CsuOldBasedActionStrategy;
import csu.agent.fb.actionStrategy.DefaultFbActionStrategy;
import csu.agent.fb.actionStrategy.StuckFbActionStrategy;
import csu.agent.fb.actionStrategy.ActionStrategyType;
import csu.agent.fb.actionStrategy.fbActionStrategy_Interface;
import csu.agent.fb.tools.DirectionManager;
import csu.agent.fb.tools.FbUtilities;
import csu.common.TimeOutException;
import csu.model.AgentConstants;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.Hydrant;
import rescuecore2.standard.entities.Refuge;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.EntityID;

public class FireBrigadeAgent extends AbstractFireBrigadeAgent {
	private fbActionStrategy_Interface actionStrategy;
	private ActionStrategyType actionType;
	
	private DirectionManager directionManager;
	private FbUtilities fbUtil;
	
	@Override
	protected void initialize() {
		super.initialize();
		this.directionManager = new DirectionManager(world);
		this.fbUtil = new FbUtilities(world);
	}
	
	@Override
	protected StandardWorldModel createWorldModel() {
		return new FireBrigadeWorld();
	}

	@Override
	protected void prepareForAct() throws TimeOutException {
		super.prepareForAct();
	}
	
	@Override
	protected void act() throws ActionCommandException, TimeOutException {
		this.chooseActionStrategy();
		this.leaveBurningBuilding();
		
		if (isBlocked()) {
			this.actionStrategy.extinguishNearbyWhenStuck();
			randomWalk();
		}
		super.act();
		this.careSelf();
		this.supplyWater();
		this.actionStrategy.execute();
		isThinkTimeOver("after excute");
		this.actionStrategy.moveToFires();
		
		if (world.getTime() < Math.min(80, world.getConfig().timestep / 3)
				|| world.getConfig().timestep - 45 < world.getTime()) {
			lookupSearchBuildings();
		}
		enterSearchBuildings();
		randomWalk();
	}

	@Override
	protected void cantMove() throws ActionCommandException, TimeOutException {
	}
	
	private void leaveBurningBuilding() throws ActionCommandException{
		StandardEntity entity;
		if (location() instanceof Building && !(location() instanceof Refuge)) {
			Building building = (Building) location();
			if ((building.isFierynessDefined() && building.isOnFire())
					|| ((building.isFierynessDefined() && building.getFieryness() != 8
							&& building.isTemperatureDefined() && building.getTemperature() > 35))) {
				
				if (AgentConstants.PRINT_TEST_DATA_FB) {
					System.out.println("Agent " + me() + " in time: " + time 
							+ " was in a dangerous Building and trying to go out it." 
							+ " ----- class: FireBrigadeAgent, method: leaveBurningBuilding()");
				}
				
				building.getNeighbours();
				for (EntityID next : building.getNeighbours()) {
					entity = world.getEntity(next);
					if (entity instanceof Building) {
						unlookupedBuildings.remove(entity);
						unenteredBuildings.remove(entity);
					}
				}
				moveToRefuge();
			}
		} else if (location() instanceof Refuge) {
			for (EntityID next : location().getNeighbours()) {
				StandardEntity neig = world.getEntity(next);
				if (neig instanceof Building) {
					Building n_bu = (Building) neig;
					if (n_bu.isFierynessDefined() && n_bu.isTemperatureDefined())
						if (n_bu.getFieryness() != 8 && n_bu.getTemperature() > 35) {
							extinguish(n_bu);
						}
				}
			}
		}
	}
	
	@Override
	protected void careSelf() throws ActionCommandException {
		if (me().getHP() - me().getDamage() * (timestep - time) < 0) {
			// setAgentState(AgentState.RESTING);
			
			if (AgentConstants.PRINT_TEST_DATA_FB) {
				System.out.println("In time: " + time + ", agent: " + me()
						+ "move to refuge ---- careSelf, FireBrigadeAgent.java");
			}

			moveToRefuge();
		}
	}
	
	private void supplyWater() throws ActionCommandException {
		if (!world.getEntitiesOfType(StandardEntityURN.REFUGE).isEmpty())
			supplyWaterInRefuge();
		else if (!world.getEntitiesOfType(StandardEntityURN.HYDRANT).isEmpty())
			supplyWaterInHydrant();
		else
			return;
	}
	
	private void supplyWaterInRefuge() throws ActionCommandException {
		final int maxPower = world.getConfig().maxPower;
		// final int remain = world.getConfig().timestep - time;
		final int maxTankCapacity = world.getConfig().maxTankCapacity;
		final int capacity = (maxTankCapacity > maxPower) ? maxTankCapacity : maxPower;
		final int refillRate = world.getConfig().tankRefillRate;
		final int currentWater = me().getWater();

		if (/*currentWater - remain * maxPower > 2 ||*/ me().getWater() >= capacity) {
			return;
		}
		
		if (location() instanceof Refuge) {
			if (currentWater + refillRate > capacity) {
				return;
			}
			rest();
		}

		if (currentWater <= maxPower / 2.0) {
			if (AgentConstants.PRINT_TEST_DATA_FB) {
				System.out.println("In time: " + time + ", agent: " + me() 
						+ " go to refill water ----- supplyWaterInRefuge, FireBrigadeAgent.java");
			}
			
			moveToRefuge();
		}
	}
	
	private void supplyWaterInHydrant() throws ActionCommandException {
		final int maxPower = world.getConfig().maxPower;
		// final int remain = world.getConfig().timestep - time;
		final int maxTankCapacity = world.getConfig().maxTankCapacity;
		final int capacity = maxTankCapacity > maxPower ? maxTankCapacity : maxPower;
		final int refillRate = world.getConfig().tankRefillRate;
		final int currentWater = me().getWater();
		
		if (/*currentWater - remain * maxPower > 2 ||*/ me().getWater() >= capacity) {
			return;
		}
		
		if (location() instanceof Hydrant) {
			if (currentWater + refillRate > capacity) {
				return;
			}
			rest();
		}
		
		if (currentWater <= 0) {
			if (AgentConstants.PRINT_TEST_DATA_FB) {
				System.out.println("In time: " + time + ", agent: " + me() 
						+ " go to refill water ----- supplyWaterInHydrant, FireBrigadeAgent.java");
			}
			
			moveToHydrant();
		}
	}
	
	private void moveToHydrant() throws ActionCommandException {
		Collection<StandardEntity> hydrants = world.getEntitiesOfType(StandardEntityURN.HYDRANT);
		for (FireBrigade fb : world.getFireBrigadeList()) {
			if (fb.getID().getValue() == me().getID().getValue())
				continue;
			if (!fb.isPositionDefined())
				continue;
			StandardEntity fbPosition = fb.getPosition(world);
			if (fbPosition instanceof Hydrant)
				hydrants.remove((Hydrant)fbPosition);
		}
		
		if (hydrants.isEmpty())
			return;
		move(hydrants);
	}
 	
	/*private void checkAggregator() throws csu.agent.Agent.ActionCommandException {
		int lastTime = -1;
		EntityID lastSeenID = null;
		for (EntityID id : getAggregators()) {
			int t = world.getTimestamp().getLastSeenTime(id);
			if (lastTime < t) {
				lastTime = t;
				lastSeenID = id;
			}
		}
		 if I have not see any aggerator, then I need to go to nearest aggerator
		if (lastTime == -1) {
			Set<Area> aggregatorsArea = new HashSet<Area>(getAggregators().size());
			for (EntityID id : getAggregators()) {
				aggregatorsArea.add((Area) world.getEntity(getAggregatorPosition(id)));
			}
			move(aggregatorsArea);
		}
		 if I leave my aggerator more than 10 cycle, I need go to my aggerator
		if (lastTime - time > 10) {
			move(lastSeenID);
		}
	}*/
	
	private void chooseActionStrategy() {
		this.actionType = ActionStrategyType.DEFAULT;
		switch (actionType) {
		case DEFAULT:
			if (this.shouldChangeActionType(ActionStrategyType.DEFAULT)) {
				actionStrategy = new DefaultFbActionStrategy((FireBrigadeWorld)world, directionManager, fbUtil);
			}
			break;
		case STUCK_SITUATION:
			if (this.shouldChangeActionType(ActionStrategyType.STUCK_SITUATION)) {
				actionStrategy = new StuckFbActionStrategy((FireBrigadeWorld)world, directionManager, fbUtil);
			}
			break;
		case CSU_OLD_BASED:
			if (this.shouldChangeActionType(ActionStrategyType.CSU_OLD_BASED)) {
				actionStrategy = new CsuOldBasedActionStrategy((FireBrigadeWorld)world, directionManager, fbUtil);
			}
		default:
			break;
		}
	}
	
	private boolean shouldChangeActionType(ActionStrategyType actionType) {
		return this.actionStrategy == null || !this.actionType.equals(actionType);
	}
	
	
///* -------------------------------------ports to receive centre's command ------------------------------------ */
//	private final int fbNumberBitSize = BitUtil.needBitSize(50);
//	protected Port createMoveTaskPort(final CommunicationUtil comUtil, final int timeToLive) {
//		return new Port() {
//			@Override
//			public void read(EntityID sender, int time, BitArrayInputStream stream) {
//				final int n = stream.readBit(fbNumberBitSize);
//				for (int i = 0; i < n; i++) {
//					final int fbUniform = stream.readBit(comUtil.AGENT_UNIFORM_BIT_SIZE);
//					EntityID fbId = world.getUniform().toID(StandardEntityURN.FIRE_BRIGADE, fbUniform);
//					final int mark = stream.readBit(1);
//					final int targetUniform;
//					EntityID targetId;
//					if (mark == 0) {
//						targetUniform = stream.readBit(comUtil.BUILDING_UNIFORM_BIT_SIZE);
//						targetId = world.getUniform().toID(StandardEntityURN.BUILDING, targetUniform);
//					} else {
//						targetUniform = stream.readBit(comUtil.ROAD_UNIFORM_BIT_SIZE);
//						targetId = world.getUniform().toID(StandardEntityURN.ROAD, targetUniform);
//					}
//					if (fbId.getValue() == FireBrigadeAgent.this.getID().getValue()) {
//						locationToGo = targetId;
//					}
//				}
//			}
//			
//			@Override
//			public MessageBitSection next() {
//				return null;
//			}
//			
//			@Override
//			public void init(ChangeSet changed) {
//				
//			}
//			
//			@Override
//			public boolean hasNext() {
//				return false;
//			}
//			
//			@Override
//			public MessageReportedType getMessageReportedType() {
//				return MessageReportedType.REPORTED_TO_FB;
//			}
//		};
//	}
//	
//	protected Port createExtinguishTaskPort(final CommunicationUtil comUtil, final int timeToLive) {
//		return new Port() {
//			
//			@Override
//			public void read(EntityID sender, int time, BitArrayInputStream stream) {
//				final int n = stream.readBit(fbNumberBitSize);
//				for (int i = 0; i < n; i++) {
//					final int fbUniform = stream.readBit(comUtil.AGENT_UNIFORM_BIT_SIZE);
//					EntityID fbId = world.getUniform().toID(StandardEntityURN.FIRE_BRIGADE, fbUniform);
//					final int targetUniform = stream.readBit(comUtil.BUILDING_UNIFORM_BIT_SIZE);
//					EntityID targetId = world.getUniform().toID(StandardEntityURN.BUILDING, targetUniform);
//					final int power = stream.readBit(comUtil.WATER_POWER_BIT_SIZE);
//					if (fbId.getValue() == FireBrigadeAgent.this.getID().getValue()) {
//						buildingToExtinguish = targetId;
//						waterPower = power;
//					}
//				}
//			}
//			
//			@Override
//			public MessageBitSection next() {
//				return null;
//			}
//			
//			@Override
//			public void init(ChangeSet changed) {
//				
//			}
//			
//			@Override
//			public boolean hasNext() {
//				return false;
//			}
//			
//			@Override
//			public MessageReportedType getMessageReportedType() {
//				return MessageReportedType.REPORTED_TO_FB;
//			}
//		};
//	}
}
