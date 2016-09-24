package csu.agent.pf.clearStrategy;

import java.awt.Polygon;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.geometry.Vector2D;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.messages.StandardMessageURN;
import rescuecore2.worldmodel.EntityID;
import csu.agent.Agent.ActionCommandException;
import csu.model.AdvancedWorldModel;
import csu.model.AgentConstants;
import csu.model.object.CSUEdge;
import csu.model.object.CSURoad;
import csu.standard.Ruler;
import csu.util.Util;

/**
 * Date: June 23, 2014  Time 9:23pm
 * 
 * @author appreciation-csu
 *
 */
public class POSBasedStrategy extends AbstractStrategy{
	
	public POSBasedStrategy(AdvancedWorldModel world) {
		super(world);
	}

	@Override
	public void clear() throws ActionCommandException {
		/*Area location = (Area)world.getSelfPosition();
		if (location instanceof Building) {
			clearf(false);
			return;
		}
			
		CSURoad road = world.getCsuRoad(location.getID());
		if (road.isEntrance()) {
			clearInEntrance(road);
		}
		
		if (road.isEntranceNeighbour()) {
		}
		
		if (road.isAllEdgePassable()) {
		}*/
		
		if (lastCyclePath == null)
			return;
		
		// clearCritical();

		mixingClear();
		// doClear((Road)location, targetEdge);
	}
	
	public void mixingClear() throws ActionCommandException{
		Point2D selfL = new Point2D(x, y);
		int pathLength = lastCyclePath.size();
		CSURoad road = world.getCsuRoad(world.getSelfPosition().getID());
		
		StandardEntity cur_entity = world.getEntity(world.getSelfPosition().getID());
		if (!(cur_entity instanceof Area))
			return;
		Area currentArea = (Area) cur_entity;
		
		double minDistance = Double.MAX_VALUE;
		Blockade nearestBlockade = null;
		
		int indexInPath = findIndexInPath(lastCyclePath, cur_entity.getID());
		
		if (AgentConstants.PRINT_TEST_DATA_PF) {
			String str = null;
			for(EntityID pa : lastCyclePath) {
				if (str == null)
					str = pa.getValue() + "";
				else
					str = str + "," + pa.getValue();
			}
			System.out.println("time = " + time + " Agent = " + world.getControlledEntity() 
					+ ", location = " + currentArea.getID().getValue() 
					+ ", index = " + indexInPath + " in path = [" + str + "]");
		}
		
		if (indexInPath == pathLength) {
			return;
		} else if (indexInPath == (pathLength - 1)) {
			if (currentArea instanceof Road && road.getSelfRoad().isBlockadesDefined()) {
				for (EntityID next : road.getSelfRoad().getBlockades()) {
					StandardEntity en = world.getEntity(next);
					if (!(en instanceof Blockade))
						continue;
					Blockade bloc = (Blockade) en;
					if (bloc.isApexesDefined() && bloc.getApexes().length < 6)
						continue;
					double dis = findDistanceTo(bloc, x, y);
					if (dis < minDistance) {
						minDistance = dis;
						nearestBlockade = bloc;
					}
				}
				
				if (nearestBlockade != null) {
					scaleClear(nearestBlockade, 2);
				} else {
					if (road.isEntrance()) {
						List<EntityID> neigh_bloc = new ArrayList<>();
						for (EntityID neigh : road.getSelfRoad().getNeighbours()) {
							StandardEntity entity = world.getEntity(neigh);
							if (!(entity instanceof Road))
								continue;
							Road neig_road = (Road) entity;
							if (!neig_road.isBlockadesDefined())
								continue;
							neigh_bloc.addAll(neig_road.getBlockades());
						}
						
						minDistance = Double.MAX_VALUE;
						nearestBlockade = null;
						
						for (EntityID next : neigh_bloc) {
							StandardEntity en = world.getEntity(next);
							if (!(en instanceof Blockade))
								continue;
							Blockade bloc = (Blockade) en;
							if (bloc.isApexesDefined() && bloc.getApexes().length < 6)
								continue;
							double dis = findDistanceTo(bloc, x, y);
							if (dis < minDistance) {
								minDistance = dis;
								nearestBlockade = bloc;
							}
						}
						
						if (nearestBlockade != null)
							scaleClear(nearestBlockade, 5);
					}
					return;
				}
			}
		}
		if (indexInPath + 1 >= pathLength)
			return;
		EntityID nextArea = lastCyclePath.get(indexInPath + 1);
		Area next_A = world.getEntity(nextArea, Area.class);
		
		Area last_A = null;
		if (indexInPath > 0) {
			last_A = world.getEntity(lastCyclePath.get(indexInPath - 1), Area.class);
		}
		
		Edge dirEdge = null;
		for (Edge nextEdge : currentArea.getEdges()) {
			if (!nextEdge.isPassable())
				continue;
			if (nextEdge.getNeighbour().getValue() == nextArea.getValue()) {
				dirEdge = nextEdge;
				break;
			}
		}
		if (dirEdge == null)
			return;
		Point2D dirPoint = new Point2D((dirEdge.getStart().getX() + dirEdge.getEnd().getX()) / 2.0, 
				(dirEdge.getStart().getY() + dirEdge.getEnd().getY()) / 2.0);
		
		Set<Blockade> c_a_Blockades = getBlockades(currentArea, next_A, selfL, dirPoint);
		// Set<Blockade> c_a_Blockades = getBlockades(selfL, dirPoint, road.getSelfRoad(), next_A);
		minDistance = Double.MAX_VALUE;
		nearestBlockade = null;
		for (Blockade bloc : c_a_Blockades) {
			double dis = findDistanceTo(bloc, x, y);
			if (dis < minDistance) {
				minDistance = dis;
				nearestBlockade = bloc;
			}
		}
		
		if (nearestBlockade != null) {
			directionClear(nearestBlockade, dirPoint, next_A, 1);
		} else {
			Vector2D vector = selfL.minus(dirPoint);
			vector = vector.normalised().scale(repairDistance - 500);
			Line2D line = new Line2D(selfL, vector);
			Point2D r_dirPoint = line.getEndPoint();
			Set<Blockade> c_a_r_Blockades = getBlockades(currentArea, last_A, selfL, r_dirPoint);
			
			minDistance = Double.MAX_VALUE;
			nearestBlockade = null;
			for (Blockade bloc : c_a_r_Blockades) {
				double dis = findDistanceTo(bloc, x, y);
				if (dis < minDistance) {
					minDistance = dis;
					nearestBlockade = bloc;
				}
			}
			
			if (nearestBlockade != null) {
				reverseClear(nearestBlockade, r_dirPoint, last_A, 1);
			}
		}
		
		for (int i = indexInPath + 1; i <= indexInPath + 5; i++) {
			if (pathLength > i + 1) {
				// Point2D startPoint = dirPoint;
				StandardEntity entity_1 = world.getEntity(lastCyclePath.get(i));
				StandardEntity entity_2 = world.getEntity(lastCyclePath.get(i + 1));
				
				if (entity_1 instanceof Area && entity_2 instanceof Area) {
					Area next_a_1 = (Area) entity_1;
					Area next_a_2 = (Area) entity_2;
					
					for (Edge edge : next_a_1.getEdges()) {
						if (!edge.isPassable())
							continue;
						if (edge.getNeighbour().getValue() == next_a_2.getID().getValue()) {
							dirPoint = new Point2D((edge.getStartX() + edge.getEndX()) / 2.0, 
									(edge.getStartY() + edge.getEndY()) / 2.0);
							break;
						}
					}
					
					Set<Blockade> n_a_blockades = getBlockades(next_a_1, next_a_2, selfL, dirPoint);
					
					String str = null;
					for (Blockade n_b : n_a_blockades) {
						if (str == null) {
							str = n_b.getID().getValue() + "";
						} else {
							str = str + ", " + n_b.getID().getValue();
						}
					}
					
					if (AgentConstants.PRINT_TEST_DATA_PF) {
						System.out.println("time = "+ time + " Agent = " + world.getControlledEntity()
								+ ", next_A_1 = " + next_a_1.getID().getValue()
								+ ", next_A_2 = " + next_a_2.getID().getValue()
								+ ", blockades = [" + str 
								+ "] ----- class: POSBasedStrategy, method: mixingClear()");
					}
					
					// Set<Blockade> n_a_blockades = getBlockades(startPoint, dirPoint, next_a_1, next_a_2);
					minDistance = Double.MAX_VALUE;
					nearestBlockade = null;
					for (Blockade bloc : n_a_blockades) {
						double dis = findDistanceTo(bloc, x, y);
						if (dis < minDistance) {
							minDistance = dis;
							nearestBlockade = bloc;
						}
					}
					
					if (nearestBlockade != null) {
						directionClear(nearestBlockade, dirPoint, next_a_2, 2);
					}
				}
			} else if (pathLength == i + 1){
				minDistance = Double.MAX_VALUE;
				nearestBlockade = null;
				StandardEntity entity = world.getEntity(lastCyclePath.get(i));
				if (!(entity instanceof Road))
					continue;
				Road destRoad = (Road)entity;
				if (!destRoad.isBlockadesDefined())
					continue;
				for (EntityID next : destRoad.getBlockades()) {
					StandardEntity en = world.getEntity(next);
					if (!(en instanceof Blockade))
						continue;
					Blockade bloc = (Blockade) world.getEntity(next);
					if (bloc.isApexesDefined() && bloc.getApexes().length < 6)
						continue;
					double dis = findDistanceTo(bloc, x, y);
					if (dis < minDistance) {
						minDistance = dis;
						nearestBlockade = bloc;
					}
				}
				
				if (nearestBlockade != null) {
					scaleClear(nearestBlockade, 3);
				} else {
					break;
				}
			}
		}
	}
	
	// 1920451, 355780
	private void scaleClear(Blockade target, int marker) throws ActionCommandException {
		double distance = findDistanceTo(target, x, y);
		if (distance < repairDistance && underlyingAgent.isVisible(target)) {
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("time = "+ time + " Agent = " + world.getControlledEntity()
						+ ", targetBlockade = " + target.getID().getValue() + " is clearing "
						+ "----- class: POSBasedStrategy, method: scaleClear(" + marker + ")");
			}

			underlyingAgent.sendClear(time, target.getID());
			throw new ActionCommandException(StandardMessageURN.AK_CLEAR);
		} else {
			int current_I = findIndexInPath(lastCyclePath, world.getSelfPosition().getID());
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				String str = null;
				for(EntityID pa : lastCyclePath) {
					if (str == null)
						str = pa.getValue() + "";
					else
						str = str + "," + pa.getValue();
				}
				System.out.println("time = " + time + " the index of " 
						+ world.getSelfPosition().getID() + " in path:[" + str + "] is " + current_I);
			}

			List<EntityID> path = getPathToBlockade(current_I, target);
			
			if (path.size() > 0) {
				
				if (AgentConstants.PRINT_TEST_DATA_PF) {
					String str = null;
					for(EntityID pa : lastCyclePath) {
						if (str == null)
							str = pa.getValue() + "";
						else
							str = str + "," + pa.getValue();
					}
					
					System.out.println("time = "+ time + " Agent = " + world.getControlledEntity()
							+ ", targetBlockade = " + target.getID().getValue() + ", path:[" + str 
							+ "]----- class: POSBasedStrategy, method: scaleClear(" + marker + ")");
				}

				underlyingAgent.sendMove(time, path, target.getX(), target.getY());
				lastClearDest_x = -1;
				lastClearDest_y = -1;
				throw new ActionCommandException(StandardMessageURN.AK_MOVE);
			}
		}
	}
	
	private void directionClear(Blockade target, Point2D dirPoint, Area next_A, int marker) throws ActionCommandException {
		
		if (!underlyingAgent.isVisible(target.getID())) {
			int current_I = findIndexInPath(lastCyclePath, world.getSelfPosition().getID());
			
			/*if (marker == 1) {
				Area currentArea = (Area)world.getEntity(lastCyclePath.get(current_I));
				Point2D selfL = new Point2D(x, y);
				Vector2D vector = selfL.minus(dirPoint);
				vector = vector.normalised().scale(repairDistance - 500);
				Line2D line = new Line2D(selfL, vector);
				Point2D r_dirPoint = line.getEndPoint();
				Set<Blockade> c_a_r_blockades = getBlockades(currentArea, null, selfL, r_dirPoint);
				
				double minDistance = Double.MAX_VALUE;
				Blockade nearestBlockades = null;
				for (Blockade bloc : c_a_r_blockades) {
					double dis = findDistanceTo(bloc, x, y);
					if (dis < minDistance) {
						minDistance = dis;
						nearestBlockades = bloc;
					}
				}
				
				if (nearestBlockades != null)
					reverseClear(nearestBlockades, r_dirPoint, null, 2);
			}*/
			
			List<EntityID> path = getPathToBlockade(current_I, target);
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				String str = null;
				for(EntityID pa : path) {
					if (str == null)
						str = pa.getValue() + "";
					else
						str = str + "," + pa.getValue();
				}
				
				System.out.println("time = "+ time + " Agent = " + world.getControlledEntity()
						+ ", targetBlockade_1 = " + target.getID().getValue()
						+ " is moving, path = [" + str 
						+ "] ----- class: POSBasedStrategy, method: directionClear(" + marker + ")");
			}
			
			Point2D movePoint = getMovePoint(target, dirPoint);
			if (movePoint != null) {
				underlyingAgent.sendMove(time, path, (int)movePoint.getX(), (int)movePoint.getY());
			} else {
				underlyingAgent.sendMove(time, path, target.getX(), target.getY());
			}
			lastClearDest_x = -1;
			lastClearDest_y = -1;
			
			throw new ActionCommandException(StandardMessageURN.AK_MOVE);
		}
		
		double dis_to_dir = Math.hypot(dirPoint.getX() - x, dirPoint.getY() - y);
		Vector2D v = new Vector2D(dirPoint.getX() - x, dirPoint.getY() - y);
		v = v.normalised().scale(Math.min(dis_to_dir, repairDistance - 500));
		Point2D t_dir_p = new Point2D(x + v.getX(), y + v.getY());
		
		Road road = (Road)world.getEntity(target.getPosition());
		
		Set<Blockade> t_bloc = getBlockades(road, next_A, new Point2D(x, y), t_dir_p);
		if (t_bloc.size() > 0) {
			if (dis_to_dir < repairDistance) {
				v = v.normalised().scale(repairDistance);
			} else {
				v = v.normalised().scale(dis_to_dir);
			}
			
			int destX = (int) (x + v.getX()), destY = (int) (y + v.getY());
			timeLock(destX, destY, target);
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("time = "+ time + " Agent = " + world.getControlledEntity()
						+ ", targetBlockade = " + target.getID().getValue() 
						+ ", destX = " + destX + ", destY = " + destY + " is clearing " 
						+ "----- class: POSBasedStrategy, method: directionClear(" + marker + ")");
			}
			
			underlyingAgent.sendClear(time, destX, destY);
			throw new ActionCommandException(StandardMessageURN.AK_CLEAR);
		} else {
			int current_I = findIndexInPath(lastCyclePath, world.getSelfPosition().getID());
			
			/*if (marker == 1) {
				Area currentArea = (Area)world.getEntity(lastCyclePath.get(current_I));
				Point2D selfL = new Point2D(x, y);
				Vector2D vector = selfL.minus(dirPoint);
				vector = vector.normalised().scale(repairDistance - 500);
				Line2D line = new Line2D(selfL, vector);
				Point2D r_dirPoint = line.getEndPoint();
				Set<Blockade> c_a_r_blockades = getBlockades(currentArea, null, selfL, r_dirPoint);
				
				double minDistance = Double.MAX_VALUE;
				Blockade nearestBlockades = null;
				for (Blockade bloc : c_a_r_blockades) {
					double dis = findDistanceTo(bloc, x, y);
					if (dis < minDistance) {
						minDistance = dis;
						nearestBlockades = bloc;
					}
				}
				
				if (nearestBlockades != null)
					reverseClear(nearestBlockades, r_dirPoint, null, 3);
			}*/
			
			List<EntityID> path = getPathToBlockade(current_I, target);
			
			String str = null;
			for(EntityID pa : path) {
				if (str == null)
					str = pa.getValue() + "";
				else
					str = str + "," + pa.getValue();
			}
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("time = " + time + " Agent = " + world.getControlledEntity()
						+ ", targetBlockade_2 = " + target.getID().getValue() + " is moving, path = [" 
						+ str + "] ----- class: POSBasedStrategy, method: directionClear(" + marker + ")");
			}
			
			
			Point2D movePoint = getMovePoint(target, dirPoint);
			if (movePoint != null) {
				underlyingAgent.sendMove(time, path, (int)movePoint.getX(), (int)movePoint.getY());
			} else {
				underlyingAgent.sendMove(time, path, (int)dirPoint.getX(), (int)dirPoint.getY());
			}
			lastClearDest_x = -1;
			lastClearDest_y = -1;
			
			throw new ActionCommandException(StandardMessageURN.AK_MOVE);
		}
	}
	
	private void reverseClear(Blockade target, Point2D dirPoint, Area last_A, int marker) throws ActionCommandException {
		if (!underlyingAgent.isVisible(target.getID()))
			return;
		
		double dis_to_dir = Math.hypot(dirPoint.getX() - x, dirPoint.getY() - y);
		Vector2D v = new Vector2D(dirPoint.getX() - x, dirPoint.getY() - y);
		v = v.normalised().scale(Math.min(dis_to_dir, repairDistance - 500));
		Point2D t_dir_p = new Point2D(x + v.getX(), y + v.getY());
		
		Road road = (Road)world.getEntity(target.getPosition());
		
		Set<Blockade> t_bloc = getBlockades(road, last_A, new Point2D(x, y), t_dir_p);
		if (t_bloc.size() > 0) {
			if (dis_to_dir < repairDistance) {
				v = v.normalised().scale(repairDistance);
			}
			
			int destX = (int) (x + v.getX()), destY = (int) (y + v.getY());
			
			timeLock(destX, destY, target);
			if (reverseTimeLock(destX, destY, target))
				return;
			
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("time = "+ time + " Agent = " + world.getControlledEntity()
						+ ", targetBlockade = " + target.getID().getValue() + " is clearing " 
						+ "----- class: POSBasedStrategy, method: reverseClear(" + marker + ")");
			}
			
			underlyingAgent.sendClear(time, destX, destY);
			throw new ActionCommandException(StandardMessageURN.AK_CLEAR);
		}
	}
	
	private List<EntityID> getPathToBlockade(int current_L_I, Blockade blockade) {
		List<EntityID> path = new ArrayList<>();
		if (!blockade.isPositionDefined()){
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("!blockade.isPositionDefined() == true");
			}
			
			path.add(world.getSelfPosition().getID());
			return path;
		}
			
		EntityID blo_A = blockade.getPosition();
		int b_index = findIndexInPath(lastCyclePath, blo_A);
		
		
		if (b_index < lastCyclePath.size()) {
			for (int i = current_L_I; i <= b_index; i++)
				path.add(lastCyclePath.get(i));
		} /*else {
			for (int i = current_L_I; i < lastCyclePath.size(); i++)
				path.add(lastCyclePath.get(i));
		}*/
		if (path.isEmpty()) {
			if (AgentConstants.PRINT_TEST_DATA_PF) {
				System.out.println("path is null. " + "current_L_I:" + current_L_I + "  b_index:" + b_index );
			}
			
			path.add(world.getSelfPosition().getID());
		}
		return path;
	}
	
	@Override
	public void doClear(Road road, CSUEdge dir, Blockade target) throws ActionCommandException {
		if (AgentConstants.PRINT_TEST_DATA_PF) {
			System.out.println("time = " + time + ", " + underlyingAgent + " is clear blockade = " 
					+ target.getID() + " ----- class: POSBasedStrategy, method: doClear()");
		}
		underlyingAgent.sendClear(time, target.getID());
		throw new ActionCommandException(StandardMessageURN.AK_CLEAR);
	}
	
	private int findIndexInPath(List<EntityID> path, EntityID location) {
		int index = 0;
		for (EntityID next : path) {
			if (location.getValue() == next.getValue())
				break;
			index++;
		}
		return index;
	}
	
	private void timeLock(int destX, int destY, Blockade target) throws ActionCommandException {
		if (lastClearDest_x == destX && lastClearDest_y == destY) {
			if (count >= lock) {
				int current_I = findIndexInPath(lastCyclePath, world.getSelfPosition().getID());
				List<EntityID> path = getPathToBlockade(current_I, target);
				
				if (AgentConstants.PRINT_TEST_DATA_PF) {
					String str = null;
					for(EntityID pa : path) {
						if (str == null)
							str = pa.getValue() + "";
						else
							str = str + "," + pa.getValue();
					}
					
					System.out.println("time = "+ time + " Agent = " + world.getControlledEntity()
							+ ", targetBlockade_1 = " + target.getID().getValue()
							+ " is moving, path = [" + str 
							+ "] ----- class: POSBasedStrategy, method: timeLock()");
				}
				
				underlyingAgent.sendMove(time, path, destX, destY);
				lastClearDest_x = -1;
				lastClearDest_y = -1;
				count = 0;
				throw new ActionCommandException(StandardMessageURN.AK_MOVE);
			} else {
				count++;
			}
		} else {
			count = 0;
			lastClearDest_x = destX;
			lastClearDest_y = destY;
		}
	}
	
	private boolean reverseTimeLock(int destX, int destY, Blockade target) {
		if (lastClearDest_x == destX && lastClearDest_y == destY) {
			if (count >= reverseLock) {
				destX = -1;
				destY = -1;
				return true;
			} else {
				count++;
				return false;
			}
		} else {
			count = 0;
			lastClearDest_x = destX;
			lastClearDest_y = destY;
			return false;
		}
	}
	
	private Set<Blockade> getBlockades(Area current_A, Area next_A, Point2D selfL, Point2D dirPoint) {
		if (current_A instanceof Building && next_A instanceof Building)
			return new HashSet<Blockade>();
		Set<EntityID> allBlockades = new HashSet<>();
		
		Road currentRoad = null, nextRoad = null;
		if (current_A instanceof Road) {
			currentRoad = (Road) current_A;
		}
		if (next_A instanceof Road) {
			nextRoad = (Road) next_A;
		}
			
		rescuecore2.misc.geometry.Line2D line = new rescuecore2.misc.geometry.Line2D(selfL, dirPoint);
		rescuecore2.misc.geometry.Line2D[] temp = getParallelLine(line, 500);
		
		Polygon po_1 = new Polygon();
		po_1.addPoint((int)temp[0].getOrigin().getX(), (int)temp[0].getOrigin().getY());
		po_1.addPoint((int)temp[0].getEndPoint().getX(), (int)temp[0].getEndPoint().getY());
		po_1.addPoint((int)temp[1].getEndPoint().getX(), (int)temp[1].getEndPoint().getY());
		po_1.addPoint((int)temp[1].getOrigin().getX(), (int)temp[1].getOrigin().getY());
		java.awt.geom.Area area = new java.awt.geom.Area(po_1);
		
		Set<Blockade> results = new HashSet<Blockade>();
		
		if (currentRoad != null && currentRoad.isBlockadesDefined()) {
			allBlockades.addAll(currentRoad.getBlockades());
		}
		if (nextRoad != null && nextRoad.isBlockadesDefined()) {
			allBlockades.addAll(nextRoad.getBlockades());
		}
		
		for (EntityID blockade : allBlockades) {
			StandardEntity entity = world.getEntity(blockade);
			if (entity == null)
				continue;
			if (!(entity instanceof Blockade))
				continue;
			Blockade blo = (Blockade) entity;
			
			if (!blo.isApexesDefined())
				continue;
			if (blo.getApexes().length < 6)
				continue;
			Polygon po = Util.getPolygon(blo.getApexes());
			java.awt.geom.Area b_Area = new java.awt.geom.Area(po);
			b_Area.intersect(area);
			if (!b_Area.getPathIterator(null).isDone() || blo.getShape().contains(selfL.getX(), selfL.getY()))
				results.add(blo);
		}
		
		return results;
	}
	
	/*@SuppressWarnings("unused")
	private Set<Blockade> getBlockades(Point2D selfL, Point2D dirP, Area cur_A, Area next_A) {
		if (cur_A instanceof Building && next_A instanceof Building)
			return new HashSet<Blockade>();
		Set<EntityID> allBlockades = new HashSet<>();
		Set<Blockade> results = new HashSet<Blockade>();
		
		Road currentRoad = null, nextRoad = null;
		if (cur_A instanceof Road) {
			currentRoad = (Road) cur_A;
		}
		if (next_A instanceof Road) {
			nextRoad = (Road) next_A;
		}
		
		if (currentRoad != null && currentRoad.isBlockadesDefined()) {
			allBlockades.addAll(currentRoad.getBlockades());
		}
		if (nextRoad != null && nextRoad.isBlockadesDefined()) {
			allBlockades.addAll(nextRoad.getBlockades());
		}
			
		rescuecore2.misc.geometry.Line2D line = new rescuecore2.misc.geometry.Line2D(selfL, dirP);
		rescuecore2.misc.geometry.Line2D[] temp = getParallelLine(line, 500);
		
		rescuecore2.misc.geometry.Line2D[] final_L = {line, temp[0], temp[1]};
		
		for (rescuecore2.misc.geometry.Line2D next : final_L) {
			for (EntityID blockade : allBlockades) {
				Blockade bloc = (Blockade) world.getEntity(blockade);
				
				Polygon po = createPolygon(bloc.getApexes());
				if (po.contains(selfL.getX(), selfL.getY())) {
					results.add(bloc);
					break;
				}
				
				List<rescuecore2.misc.geometry.Line2D> bloc_Ls = GeometryTools2D.pointsToLines(
						GeometryTools2D.vertexArrayToPoints(bloc.getApexes()), true);
				for (rescuecore2.misc.geometry.Line2D bloc_L : bloc_Ls) {
					double t1 = next.getIntersection(bloc_L);
					double t2 = bloc_L.getIntersection(next);
					if (Double.isNaN(t1) || Double.isNaN(t2) || t2 < 0 || t2 > 1) {
						continue;
					} else {
						results.add(bloc);
						break;
					}
				}
			}
		}
		
		return results;
	}*/
	
	/*private Polygon createPolygon(int[] apexes) {
		int vertexCount = apexes.length / 2;
		int[] xCoordinates = new int[vertexCount];
		int[] yCOordinates = new int[vertexCount];
		
		for (int i = 0; i < vertexCount; i++) {
			xCoordinates[i] = apexes[2 * i];
			yCOordinates[i] = apexes[2 * i + 1];
		}
		
		return new Polygon(xCoordinates, yCOordinates, vertexCount);
	}*/
	
	/**
	 * Get the parallel lines(both left and right sides) of the given line. The
	 * distance is specified by rad.
	 * 
	 * @param line
	 *            the given line
	 * @param rad
	 *            the distance
	 * @return the two parallel lines of the given line
	 */
	private Line2D[] getParallelLine(Line2D line, int rad) {
		float theta = (float)Math.atan2(line.getEndPoint().getY() - line.getOrigin().getY(), 
				line.getEndPoint().getX() - line.getOrigin().getX());
		theta = theta - (float)Math.PI / 2;
		while (theta > Math.PI || theta < -Math.PI) {
			if (theta > Math.PI)
				theta -= 2 * Math.PI;
			else
				theta += 2 * Math.PI;
		}
		int t_x = (int)(rad * Math.cos(theta)), t_y = (int)(rad * Math.sin(theta));
		
		Point2D line_1_s, line_1_e, line_2_s, line_2_e;
		line_1_s = new Point2D(line.getOrigin().getX() + t_x, line.getOrigin().getY() + t_y);
		line_1_e = new Point2D(line.getEndPoint().getX() + t_x, line.getEndPoint().getY() + t_y);
		
		line_2_s = new Point2D(line.getOrigin().getX() - t_x, line.getOrigin().getY() - t_y);
		line_2_e = new Point2D(line.getEndPoint().getX() - t_x, line.getEndPoint().getY() - t_y);
		
		Line2D[] result = {new Line2D(line_1_s, line_1_e), new Line2D(line_2_s, line_2_e)};
		
		return result;
	}
	
	private Point2D getMovePoint(Blockade target, Point2D dirPoint) {
		if (target == null || dirPoint == null)
			return null;
		if (!target.isPositionDefined())
			return null;
		EntityID b_location =  target.getPosition();
		
		StandardEntity entity = world.getEntity(b_location);
		if (!(entity instanceof Area))
			return null;
		Area b_area = (Area) entity;
		
		Point2D center_p = new Point2D(b_area.getX(), b_area.getY());
		
		Vector2D vector = center_p.minus(dirPoint);
		vector = vector.normalised().scale(100000);
		
		center_p = dirPoint.plus(vector);
		// dirPoint = dirPoint.plus(vector);
		
		Line2D line = new Line2D(dirPoint, center_p);
		Set<Point2D> intersections = Util.getIntersections(Util.getPolygon(target.getApexes()), line);
		
		Point2D farestPoint = null;
		double maxDistance = Double.MIN_VALUE;
		for (Point2D next : intersections) {
			double dis = Ruler.getDistance(dirPoint, next);
			if (dis > maxDistance) {
				maxDistance = dis;
				farestPoint = next;
			}
		}
		
		if (farestPoint != null) {
			Line2D line_2 = new Line2D(dirPoint, farestPoint);
			line_2 = Util.improveLine(line_2, 500);
			
			return line_2.getEndPoint();
		}
		
		return null;
	}
	
	/*private void clearInCurrentRoad(Blockade b) throws ActionCommandException {
	double dist = Ruler.getDistance(x, y, b.getX(), b.getY());
	
	for (int i = 2; i < b.getApexes().length; i += 2) {
		Point2D po = new Point2D(
				(b.getApexes()[i - 2] + b.getApexes()[i]) / 2,
				(b.getApexes()[i - 1] + b.getApexes()[i + 1]) / 2);
		double tmpDist = GeometryTools2D.getDistance(new Point2D(x, y), po);
		if (tmpDist < dist) {
			dist = tmpDist;
		}
	}
	
	Point2D po = new Point2D(
			(b.getApexes()[0] + b.getApexes()[b.getApexes().length - 2]) / 2,
			(b.getApexes()[1] + b.getApexes()[b.getApexes().length - 1]) / 2);
	double tmpDist = GeometryTools2D.getDistance(new Point2D(x, y), po);
	if (tmpDist < dist) {
		dist = tmpDist;
	}

	if (dist < repairDistance && underlyingAgent.isVisible(b.getID())) {
		underlyingAgent.sendClear(time, b.getID());
	} else {
		List<EntityID> path = new ArrayList<>();
		path.add(world.getSelfPosition().getID());
		underlyingAgent.sendMove(time, path, b.getX(), b.getY());
	}
}*/
	
	/*private void clearCritical() throws ActionCommandException {
		double minDistance = repairDistance;
		Blockade result = null;
		for (EntityID next : underlyingAgent.getChanged()) {
			StandardEntity entity = world.getEntity(next);
			if (!(entity instanceof Road))
				continue;
			
			CSURoad road = world.getCsuRoad(next);
			
			if (road.isEntrance() || road.isAllEdgePassable()) {
				if (!road.getSelfRoad().isBlockadesDefined())
					continue;
				for (EntityID blockade : ((Road)entity).getBlockades()) {
					Blockade blo = (Blockade)world.getEntity(blockade);
					double dis = Ruler.getDistance(blo.getX(), blo.getY(), x, y);
					if (dis < minDistance) {
						minDistance = (int) dis;
						result = blo;
					}
				}
			}
		}
		
		if (result != null) {
			System.out.println("time = "+ time + " Agent = " + world.getControlledEntity()
					+ ", targetBlockade = " + result.getID().getValue()
					+ " is moving ----- class: POSBasedStrategy, method: clearCritical()");
			
			underlyingAgent.sendClear(time, result.getID());
			throw new ActionCommandException(StandardMessageURN.AK_CLEAR);
		}
	}
	
	private void clearInEntrance(CSURoad entrance) throws ActionCommandException {
		double minDistance = Double.MAX_VALUE;
		Blockade nearestBlockade = null;
		
		Set<EntityID> blockades = new FastSet<>();
		
		if (entrance.getSelfRoad().isBlockadesDefined())
			blockades.addAll(entrance.getSelfRoad().getBlockades());
		
		for (EntityID next : entrance.getSelfRoad().getNeighbours()) {
			StandardEntity entity = world.getEntity(next);
			if (entity instanceof Road) {
				Road road = (Road) entity;
				if (road.isBlockadesDefined())
					blockades.addAll(road.getBlockades());
			}
		}
		
		for (EntityID next_bloc : blockades) {
			Blockade bloc = (Blockade) world.getEntity(next_bloc);
			double dis = findDistanceTo(bloc, x, y);
			if (dis < minDistance) {
				minDistance = dis;
				nearestBlockade = bloc;
			}
		}
		
		if (nearestBlockade != null) {
			scaleClear(nearestBlockade, 1);
		} else {
			return;
		}
	}*/
	
	/*private boolean stuckByBlockade(CSURoad road, int x, int y) {
		java.awt.geom.Area blo;
		int radius = 500;
		Shape s = new java.awt.geom.Ellipse2D.Double(x - radius, y - radius,
				2 * radius, 2 * radius);
		java.awt.geom.Area agent;

		for (CSUBlockade next : road.getCsuBlockades()) {
			blo = new java.awt.geom.Area(next.getPolygon());
			agent = new java.awt.geom.Area(s);
			agent.intersect(blo);
			if (!agent.getPathIterator(null).isDone())
				return true;
		}

		return false;
	}

	private boolean hasIntersection(Polygon po, rescuecore2.misc.geometry.Line2D line) {
		List<rescuecore2.misc.geometry.Line2D> polyLines = getLines(po);
		for (rescuecore2.misc.geometry.Line2D ln : polyLines) {

			if (Util.isIntersect(ln, line))
				return true;
		}
		return false;
	}

	private List<rescuecore2.misc.geometry.Line2D> getLines(Polygon polygon) {
		List<rescuecore2.misc.geometry.Line2D> lines = new ArrayList<>();
		int count = polygon.npoints;
		for (int i = 0; i < count; i++) {
			int j = (i + 1) % count;
			Point2D p1 = new Point2D(polygon.xpoints[i], polygon.ypoints[i]);
			Point2D p2 = new Point2D(polygon.xpoints[j], polygon.ypoints[j]);
			rescuecore2.misc.geometry.Line2D line = new rescuecore2.misc.geometry.Line2D(
					p1, p2);
			lines.add(line);
		}
		return lines;
	}*/
	
	public void doNothing() {
		
	}
}
