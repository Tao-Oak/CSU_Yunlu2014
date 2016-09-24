package csu.agent.fb.actionStrategy;

import java.awt.Point;
import java.util.List;

import rescuecore2.worldmodel.EntityID;

import csu.agent.Agent.ActionCommandException;
import csu.agent.fb.FireBrigadeWorld;
import csu.agent.fb.extinguishBehavior.CsuOldBasedExtinguishBehavior;
import csu.agent.fb.extinguishBehavior.DirectionBasedExtinguishBehavior;
import csu.agent.fb.extinguishBehavior.ExtinguishBehavior_Interface;
import csu.agent.fb.targetPart.DirectionBasedTargetSelector;
import csu.agent.fb.targetPart.FireBrigadeTarget;
import csu.agent.fb.tools.DirectionManager;
import csu.agent.fb.tools.FbUtilities;
import csu.common.TimeOutException;
import csu.model.AgentConstants;
import csu.model.object.CSUBuilding;
import csu.util.Util;

public class DefaultFbActionStrategy extends fbActionStrategy{
	
	public DefaultFbActionStrategy(FireBrigadeWorld world, 
			DirectionManager directionManager, FbUtilities fbUtil) {
		super(world, directionManager, fbUtil);
		this.setTargetSelector();
		this.setExtinguishBehavior();
	}

	@Override
	public void execute() throws ActionCommandException, TimeOutException {
		if (isTimeToRefreshEstimator())
			FbUtilities.refreshFireEstimator(world);
		
		FireBrigadeTarget fbTarget = this.targetSelector.selectTarget();
		if (fbTarget != null) {
			this.extinguishBehavior.extinguish(world, fbTarget);
		} else {
			if (AgentConstants.PRINT_TEST_DATA_FB) {
				System.out.println("time = " + world.getTime() + ", " + controlledEntity 
						+ " has no target and move to fires. " +
						"----- class: DefaultFbActionStrategy, method: execute()");
			}
		}
	}

	@Override
	public ActionStrategyType getFbActionStrategyType() {
		return ActionStrategyType.DEFAULT;
	}
	
	@Override
	public void moveToFires() throws ActionCommandException {
		CSUBuilding csuBuilding;
		csuBuilding = targetSelector.getOverallBestBuilding(Util.burnBuildingToCsuBuilding(world));
		if(csuBuilding != null) {
			List<EntityID> path;
			path = world.getRouter().getAStar(underlyingAgent.location(), csuBuilding.getSelfBuilding(), 
					new Point(world.getSelfLocation().first(), world.getSelfLocation().second()));

			if (AgentConstants.PRINT_TEST_DATA_FB) {
				System.out.println("In time: " + world.getTime() + ", agent: " + world.getControlledEntity() 
						+ " move to fired building: " + csuBuilding.getSelfBuilding() 
						+ " ----- class: DefaultFbActionStrategy, method: moveToFires()");
			}
			
			int pathSize = path.size();
			path.remove(pathSize - 1);
			
			underlyingAgent.moveOnPlan(path);
		}
	}
	
	private boolean isTimeToRefreshEstimator() {
		if (world.getTime() > 120 && world.getTime() % 30 == 0)
			return true;
		return false;
	}
	
	private void setTargetSelector() {
		switch (this.targetSelectorType) {
		case DIRECTION_BASED:
			targetSelector = new DirectionBasedTargetSelector(this.world);
			break;

		default:
			targetSelector = new DirectionBasedTargetSelector(this.world);
			break;
		}
	}
	
	private void setExtinguishBehavior() {
		switch (this.extinguishBehaviorType) {
		case CLUSTER_BASED:
			extinguishBehavior = new DirectionBasedExtinguishBehavior(world);
			break;
		case MUTUAL_LOCATION:
			extinguishBehavior = new DirectionBasedExtinguishBehavior(world);
			break;
		case CSU_OLD_BASED:
			extinguishBehavior = new CsuOldBasedExtinguishBehavior(world);
		default:
			extinguishBehavior = new DirectionBasedExtinguishBehavior(world);
			break;
		}
	}

	public ExtinguishBehavior_Interface getExtinguishBehavior() {
		return this.extinguishBehavior;
	}
}
