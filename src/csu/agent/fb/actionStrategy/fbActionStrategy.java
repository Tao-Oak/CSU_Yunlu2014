package csu.agent.fb.actionStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;

import csu.agent.Agent.ActionCommandException;
import csu.agent.fb.FireBrigadeAgent;
import csu.agent.fb.FireBrigadeWorld;
import csu.agent.fb.extinguishBehavior.ExtinguishBehaviorType;
import csu.agent.fb.extinguishBehavior.ExtinguishBehavior_Interface;
import csu.agent.fb.targetPart.FireBrigadeTargetSelector_Interface;
import csu.agent.fb.targetPart.TargetSelectorType;
import csu.agent.fb.tools.DirectionManager;
import csu.agent.fb.tools.FbUtilities;
import csu.common.TimeOutException;
import csu.common.clustering.FireCluster;
import csu.model.AgentConstants;
import csu.model.object.CSUBuilding;

public abstract class fbActionStrategy implements fbActionStrategy_Interface{

	protected FireBrigadeWorld world;
	protected DirectionManager directionManager;
	protected FbUtilities fbUtil;
	
	protected FireBrigadeAgent underlyingAgent;
	protected StandardEntity controlledEntity;
	protected EntityID agentId;
	
	protected CSUBuilding target;
	protected CSUBuilding lastTarget;
	protected FireCluster targetCluster;
	
	protected TargetSelectorType targetSelectorType = TargetSelectorType.DIRECTION_BASED;
	protected FireBrigadeTargetSelector_Interface targetSelector;
	
	protected ExtinguishBehaviorType extinguishBehaviorType = ExtinguishBehaviorType.CLUSTER_BASED;
	protected ExtinguishBehavior_Interface extinguishBehavior;
	
//	protected WaterPort waterPort;
	
	protected fbActionStrategy(FireBrigadeWorld world, DirectionManager directionManager, FbUtilities fbUtil) {
		this.world = world;
		this.directionManager = directionManager;
		this.fbUtil = fbUtil;
		
		this.underlyingAgent = (FireBrigadeAgent) world.getAgent();
		this.controlledEntity = world.getControlledEntity();
		this.agentId = underlyingAgent.getID();
		
		this.initializePartition();
	}
	
	private void initializePartition() {
		
	}
	
	@Override
	public abstract void execute() throws ActionCommandException, TimeOutException;
	
	@Override
	public void extinguishNearbyWhenStuck() throws ActionCommandException, TimeOutException{
		if (((FireBrigade) this.controlledEntity).getWater() == 0) {
			this.lastTarget = this.target;
			this.target = null;
			return;
		}
		
		Set<CSUBuilding> inRange = FbUtilities.getBuildingInExtinguishableRange(world, agentId);
		List<CSUBuilding> firedBuildings = new ArrayList<>();
		for (CSUBuilding next : inRange) {
			if (next.getEstimatedTemperature() >= 25)
				firedBuildings.add(next);
		}

		this.lastTarget = this.target;
		this.target = this.targetSelector.selectTargetWhenStuck(firedBuildings);
		
		if (AgentConstants.PRINT_TEST_DATA_FB) {
			System.out.println("time = " + world.getTime() + ", " + controlledEntity + " is stucked" +
					" and extinguished near by buildings. " +
					"----- class: fbActionStrategy, method: extinguishNearbyWhenStuck()");
		}
		
		this.extinguishBehavior.extinguishNearbyWhenStuck(this.target);
	}
}
