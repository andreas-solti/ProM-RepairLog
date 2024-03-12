package org.processmining.plugins.repairlog.probabilistic;

import java.util.Comparator;
import java.util.Map;

import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.plugins.repairlog.probabilistic.ProbabilisticRepair.TransitionType;

/**
 * Sorts transitions in a product net according to their transition types, see {@link TransitionType}.
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class ProductNetTransitionComparator implements Comparator<Transition>{
	
	private final Map<Transition, TransitionType> transitionTypes;
	
	public ProductNetTransitionComparator(Map<Transition, TransitionType> transitionTypes){
		this.transitionTypes = transitionTypes;
	}

	public int compare(Transition o1, Transition o2) {
		return transitionTypes.get(o1).compareTo(transitionTypes.get(o2));
	}
}
