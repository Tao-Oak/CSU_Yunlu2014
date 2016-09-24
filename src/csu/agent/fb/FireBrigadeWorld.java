package csu.agent.fb;

import java.awt.Point;
import java.awt.Shape;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javolution.util.FastSet;

import rescuecore2.config.Config;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import csu.LaunchAgents;
import csu.agent.Agent;
import csu.agent.fb.tools.ProcessFile;
import csu.agent.fb.tools.Simulator;
// import csu.common.ProcessAdvantageRatio;
import csu.common.TimeOutException;
import csu.common.clustering.ClusterManager;
import csu.common.clustering.FireCluster;
import csu.common.clustering.FireClusterManager;
import csu.model.AdvancedWorldModel;
import csu.model.AgentConstants;
import csu.model.object.CSUBuilding;
import csu.standard.simplePartition.GroupingType;

/**
 * A world model for FB only.
 * 
 * @author appreciation-csu
 *
 */
public class FireBrigadeWorld extends AdvancedWorldModel{
	
	private ClusterManager<FireCluster> fireClusterManager;
	private Simulator simulator;
	private float rayRate = 0.0025f;
	
	private Set<CSUBuilding> estimatedBurningBuildings = new FastSet<CSUBuilding>();
	
	// private ProcessAdvantageRatio processAdvantageRatio;
	
	public FireBrigadeWorld() {
		super();
	}
	
	@Override
	public void initialize(Agent<? extends StandardEntity> selfAgent, Config conf, GroupingType type) {
		super.initialize(selfAgent, conf, type);
		
		// this.processAdvantageRatio = new ProcessAdvantageRatio(this);
        // this.processAdvantageRatio.process();
		
		if (LaunchAgents.SHOULD_PRECOMPUTE) {
			initConnectionValues();
		} else {
			for (CSUBuilding next : getCsuBuildings()) {
				if (!next.isInflammable())
					continue;
				next.initWallValue(this);
			}
		}
		
		this.simulator = new Simulator(this);
		this.fireClusterManager = new FireClusterManager(this);
		
//		if (AgentConstants.LAUNCH_VIEWER) {
//			if (CSU_BuildingLayer.CSU_BUILDING_MAP.isEmpty())
//				CSU_BuildingLayer.CSU_BUILDING_MAP = Collections.synchronizedMap(getCsuBuildingMap());
//		}
	}
	
	@Override
	public void update(Human me, ChangeSet changed) throws TimeOutException{
		super.update(me, changed);
		this.updateFireCluster(me, changed);
		
		// TODO add in Mar 30, 2014
		if (AgentConstants.PRINT_BUILDING_INFO) {
			if (this.getTime() == 249) {
				System.out.println("***************************************");
				FireClusterManager manager = (FireClusterManager) fireClusterManager;

				ProcessFile processFile = new ProcessFile(getAgent().getID().getValue());

				processFile.setTimeEstimatedTemperature(manager.getTimeEstimatedTemperature());
				processFile.setTimeEstimatedFieryness(manager.getTimeEstimatedFieryness());

				Thread thread = new Thread(processFile);
				thread.start();
			}
		}
		// TODO add in Mar 30, 2014
	}
	
	private void updateFireCluster(Human me, ChangeSet changed) throws TimeOutException {
		this.simulator.update();
		fireClusterManager.updateClusters();
	}
	
	private void initConnectionValues() {
		String fileName = "precompute/connect.ray";
		
		try {
			readConnectedValues(fileName);
		} catch (Exception e) {
			try {
				writeConnectedValues(fileName);
			} catch (Exception e2) {
				e2.printStackTrace();
			}
		}
	}
	
	private void writeConnectedValues(String fileName) throws IOException {
		File f = new File(fileName);
		f.createNewFile();
		BufferedWriter bw = new BufferedWriter(new FileWriter(f));
		bw.write(rayRate + "\n");

		for (CSUBuilding csuBuilding : getCsuBuildings()) {

			csuBuilding.initWallValue(this);

			bw.write(csuBuilding.getSelfBuilding().getX() + "\n");
			bw.write(csuBuilding.getSelfBuilding().getY() + "\n");
			bw.write(csuBuilding.getConnectedBuildings().size() + "\n");

			for (int c = 0; c < csuBuilding.getConnectedBuildings().size(); c++) {
				CSUBuilding building = csuBuilding.getConnectedBuildings().get(c);
				Float val = csuBuilding.getConnectedValues().get(c);
				bw.write(building.getSelfBuilding().getX() + "\n");
				bw.write(building.getSelfBuilding().getY() + "\n");
				bw.write(val + "\n");
			}
		}
		bw.close();
	}

	private void readConnectedValues(String fileName) throws IOException {
		File f = new File(fileName);
		BufferedReader br = new BufferedReader(new FileReader(f));
		Float.parseFloat(br.readLine());
		String nl;
		while (null != (nl = br.readLine())) {
			int x = Integer.parseInt(nl);
			int y = Integer.parseInt(br.readLine());
			int quantity = Integer.parseInt(br.readLine());
			List<CSUBuilding> bl = new ArrayList<CSUBuilding>();
			List<EntityID> bIDs = new ArrayList<EntityID>();
			List<Float> weight = new ArrayList<Float>();
			for (int c = 0; c < quantity; c++) {
				int ox = Integer.parseInt(br.readLine());
				int oy = Integer.parseInt(br.readLine());
				Building building = getBuildingInPoint(ox, oy);
				if (building == null) {
					System.err.println("building not found: " + ox + "," + oy);
					br.readLine();
				} else {
					bl.add(getCsuBuilding(building.getID()));
					bIDs.add(building.getID());
					weight.add(Float.parseFloat(br.readLine()));
				}

			}
			Building b = getBuildingInPoint(x, y);
			getCsuBuilding(b.getID()).setConnectedBuildins(bl);
			getCsuBuilding(b.getID()).setConnectedValues(weight);
		}
		br.close();
	}
	
	public ClusterManager<FireCluster> getFireClusterManager() {
		return this.fireClusterManager;
	}
	
	public Set<EntityID> getAreaInShape(Shape shape) {
		Set<EntityID> result = new FastSet<>();
		for (StandardEntity next : this.getEntitiesOfType(AgentConstants.BUILDINGS)) {
			Area area = (Area) next;
			if (!(area.isXDefined() && area.isYDefined()))
				continue;
			Point p = new Point(area.getX(), area.getY());
			if (shape.contains(p))
				result.add(area.getID());
		}
		return result;
	}
	
	public Simulator getSimulator() {
		return this.simulator;
	}
	
//	public Map<EntityID, CSUBuilding> getCsuBuildingMap() {
//		return csuBuildingMap;
//	}
//	
//	public List<EntityID> getFreeFireBrigades() {
//		List<EntityID> freeFireBrigade = new ArrayList<>();
//		List<EntityID> atRefuge = new ArrayList<>();
//		FireBrigade fireBrigade;
//		
//		freeFireBrigade.addAll(getFireBrigadeIdList());
//		freeFireBrigade.removeAll(this.getStuckHandle().getStuckedAgent());
//		freeFireBrigade.removeAll(this.getBuriedHumans().getTotalBuriedHuman());
//		for (EntityID next : freeFireBrigade) {
//			fireBrigade = getEntity(next, FireBrigade.class);
//			if (!fireBrigade.isPositionDefined() || getEntity(fireBrigade.getPosition()) instanceof Refuge) {
//				atRefuge.add(next);
//			}
//		}
//		freeFireBrigade.removeAll(atRefuge);
//		return freeFireBrigade;
//	}
	
	public Set<CSUBuilding> getEstimatedBurningBuildings() {
		return this.estimatedBurningBuildings;
	}
	
//	public void setEstimatedBurningBuildings(Set<CSUBuilding> estimatedBurningBuildings) {
//		this.estimatedBurningBuildings = estimatedBurningBuildings;
//	}
}