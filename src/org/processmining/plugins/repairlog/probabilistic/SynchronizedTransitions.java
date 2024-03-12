package org.processmining.plugins.repairlog.probabilistic;

import org.processmining.models.graphbased.directed.petrinet.elements.Transition;

/**
 * Helper class to collect information for a synchronous transition.
 * Can be used to get information about both the trace transition and the model transition that 
 * are synchronized by a transition in the product net.
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class SynchronizedTransitions {

	private final Transition traceTransition;
	private final Transition modelTransition;
	
	public SynchronizedTransitions(final Transition traceTransition, final Transition modelTransition){
		this.traceTransition = traceTransition;
		this.modelTransition = modelTransition;
	}

	public Transition getTraceTransition() {
		return traceTransition;
	}

	public Transition getModelTransition() {
		return modelTransition;
	}
}
