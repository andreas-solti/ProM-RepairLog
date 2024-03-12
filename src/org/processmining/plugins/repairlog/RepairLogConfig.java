package org.processmining.plugins.repairlog;

import org.processmining.models.graphbased.directed.petrinet.elements.Transition;

public class RepairLogConfig {
	protected double missingChance = 0.1;
	
	/**
	 * TODO: make this transition dependent!
	 * @param transition
	 * @return
	 */
	public double getMissingChance(Transition transition) {
		return missingChance;
	}
	
	public double getAverageMissingChance(){
		return missingChance;
	}

	public void setMissingChance(double missingChance) {
		this.missingChance = missingChance;
	}

}
