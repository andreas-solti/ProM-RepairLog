package org.processmining.plugins.repairlog.evaluation;

import gnu.trove.map.TIntObjectMap;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.processmining.models.graphbased.directed.petrinet.StochasticNet;
import org.processmining.plugins.manifestanalysis.visualization.performance.ReliablePerfCounter;

/**
 * This class behaves as the {@link ReliablePerfCounter} class,
 * but allows invisible transitions in the model and assumes that these do not take time.
 * This is in particular useful for Stochastic Petri nets ({@link StochasticNet}) with immediate transitions
 * controlling the routing that are not reflected in the log.   
 * 
 *
 * @author Andreas Rogge-Solti
 * Nov 21, 2012
 *
 */
public class ReliableInvisibleTransitionPerfCounter extends ReliablePerfCounter{
	
	private Long startTime;
	
	public ReliableInvisibleTransitionPerfCounter(Long startTimeOfTrace){
		this.startTime = startTimeOfTrace;
	}
	
	/**
	 * This firing is only used for move model only move on model means firing
	 * transitions as soon as they are enabled
	 * 
	 * @param timedPlaces
	 * @param marking
	 * @param encTrans
	 * @param time
	 */
	protected void updateMarkingMoveModel(final TIntObjectMap<List<Long>> timedPlaces,
			final short[] marking, int encTrans) {
		boolean isInVisibleTransition = this.idx2Trans[encTrans].isInvisible();

		final List<Long> oldDate = new ArrayList<Long>(1);
		oldDate.add(null);

		short[] pred = encodedTrans2Pred.get(encTrans);
		if (pred != null) {
			// decrease the value
			for (int place = 0; place < pred.length; place++) {
				if (pred[place] != 0) {
					int needed = 0;
					if (pred[place] > 0) {
						marking[place] -= pred[place];
						needed = pred[place];
					} else if (pred[place] < 0) {
						marking[place] = 0;
						needed = -pred[place] + 1;
					}
					// get predecessor with the latest timestamp (if possible)
					List<Long> listTime = timedPlaces.get(place);
					for (int i = 0; i < needed; i++) {
						Long removedDate = listTime.get(0);
						if (removedDate != null) {
							// there is a chance waiting time can be calculated
							Long comparison = oldDate.iterator().next();
							if (comparison != null) {
								if (removedDate > comparison) {
									oldDate.clear();
									oldDate.add(removedDate);
								}
							} else {
								// oldDate null, but not the removed date
								oldDate.clear();
								oldDate.add(removedDate);
							}
						}
					}
				}
			}

			/** ASSUMING THAT MOVE MODEL IMMEDIATELY MOVING TOKENS **/
			// in the second iteration, update waiting time for places that has token
			final Long maxSyncTime = oldDate.iterator().next();
			for (int place =0; place < pred.length; place++){
				int needed = 0;
				if (pred[place] > 0) {
					needed = pred[place];
				} else if (pred[place] < 0) {
					needed = -pred[place] + 1;
				}
				List<Long> listTime = timedPlaces.get(place);
				
				for (int i = 0; i < needed; i++) {
					Long tokenTime = listTime.remove(0);
					if (tokenTime != null) {
						// update waiting time, synchronization, and sojourn time
						updatePlaceTimeAll(place, tokenTime, maxSyncTime, maxSyncTime);
					} else {
						// 0 waiting time, synchronization, and sojourn time
						updatePlaceTimeAll(place, 0L, 0L, 0L);
					}
				}
			}
		}
		if(isInVisibleTransition){
			// produce tokens and timestamp
			produceTokens(timedPlaces, marking, encTrans, oldDate.iterator().next());
		} else {
			// penalize move model only on visible transitions 
			// produce tokens without timestamp
			produceTokens(timedPlaces, marking, encTrans, null);
		}
	}
	
	/**
	 * Initialize start tokens with trace start date. This way the first transition takes zero time.
	 */
	@Override
	protected void initTimedPlaces(final TIntObjectMap<List<Long>> timedPlaces, short[] marking) {
		// decrease the value
		for (int place=0; place < marking.length; place++){
			List<Long> list = new LinkedList<Long>();
			for (int i = 0; i < marking[place]; i++) {
				list.add(startTime);
			}
			timedPlaces.put(place, list);
		}
	}
}
