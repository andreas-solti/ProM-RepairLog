package org.processmining.plugins.repairlog.probabilistic;

import org.processmining.models.semantics.petrinet.impl.EfficientTimedMarking;
import org.processmining.plugins.repairlog.probabilistic.ProbabilisticRepair.TransitionType;


/**
 * Stores a state in the search space.
 * 
 * Stores also back-links to the previous marked state for 
 * conveniently tracing back the resulting optimal state.
 * 
 * 
 * @author Andreas Rogge-Solti
 * 
 */
public class MarkedState implements Comparable<MarkedState>{
	
	private double logProbability;
 
 	/** the marking with integers on the tokens that denote the passed time to relative 0 of the trace */
	private final EfficientTimedMarking timedMarking;
	
	/**
	 * Discretized version of the spent time in a transition
	 */
	private final int lastTransitionDuration;
	
	private final int totalTimeAtFiring;
	
	private final short lastTransitionId;
	
	/** back-link  */
	private MarkedState predecessor;

	private final TransitionType moveType;
	
	private final int depth;
	
	private final double optimisticRemainingEstimate;
	
	public MarkedState(final EfficientTimedMarking timedMarking, final double logProbability, final TransitionType moveType, final double optimisticEstimate) {
		this(timedMarking,logProbability,null,0,0,(short) -1, moveType, optimisticEstimate);
	}
	
	public MarkedState(final EfficientTimedMarking timedMarking, 
			final double logProbability, 
			MarkedState predecessor, 
			final int lastTransitionDuration,
			final int totalTimeAtFiring,
			final short lastTransitionId, 
			final TransitionType moveType, 
			final double optimisticEstimate) {
		this.timedMarking = timedMarking.clone();
		this.logProbability = logProbability;
		this.predecessor = predecessor;
		this.lastTransitionDuration = lastTransitionDuration;
		this.totalTimeAtFiring = totalTimeAtFiring;
		this.lastTransitionId = lastTransitionId;
		this.moveType = moveType;
		if (predecessor != null){
			this.depth = predecessor.depth+1;
		} else {
			this.depth = 0;
		}
		this.optimisticRemainingEstimate = optimisticEstimate;
	}
	
	public double getLogProbability() {
		return logProbability;
	}
	
	public void setLogProbability(double logProbability){
		this.logProbability = logProbability;
	}

	public EfficientTimedMarking getTimedMarking() {
		return timedMarking;
	}
	
	public MarkedState getPredecessor(){
		return predecessor;
	}

	public int compareTo(MarkedState o) {
//		int result = Integer.compare(depth, o.depth);
//		if (result == 0){
//			result = Double.compare(logProbability,o.logProbability);
//		} 
//		return result;
		
//		return compareProbabilities(o);
		
		return Double.compare(getOptimisticEstimate(), o.getOptimisticEstimate());
	}
	public int compareProbabilities(MarkedState o){
		return Double.compare(logProbability,o.logProbability);
	}

	public short getLastTransitionId() {
		return lastTransitionId;
	}
	
	public int getLastTransitionDuration() {
		return lastTransitionDuration;
	}
	public int getLastTransitionTotalTimeOfFiring(){
		return totalTimeAtFiring;
	}
	public TransitionType getMoveType(){
		return moveType;
	}
	public double getOptimisticEstimate(){
		return logProbability+optimisticRemainingEstimate;
	}
}
