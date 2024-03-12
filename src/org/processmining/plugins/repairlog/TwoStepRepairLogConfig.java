package org.processmining.plugins.repairlog;

import org.deckfour.uitopia.api.event.TaskListener.InteractionResult;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.models.graphbased.directed.petrinet.StochasticNet.TimeUnit;
import org.processmining.plugins.repairlog.bayes.BayesNetworkAlgorithm.Type;
import org.processmining.plugins.repairlog.ui.TwoStepRepairLogPanel;

/**
 * Configuration parameters for the repairing algorithm.
 * Determines the selection of different alignment proposals
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class TwoStepRepairLogConfig extends RepairLogConfig{
	
	protected double ratioOfStructureToInsertion = 0.5;
	
	/** parallel repair with this number of threads */ 
	protected int repairWorkerCount = 4;
	
	protected boolean simpleImmediateEventHandling = false;
	
	protected boolean randomMode = false;
	
	protected Type implementationType = Type.BNT;
	
	/** 
	 * The factor tells us how many milliseconds correspond to one unit in the stochastic model. 
	 * (TODO: infer proposal by comparing performance values in model and log for existing times) 
	 */
	protected TimeUnit timeUnit = TimeUnit.MILLISECONDS;
	
	public boolean letUserChooseValues(UIPluginContext context){
		TwoStepRepairLogPanel panel = new TwoStepRepairLogPanel(this);
		return letUserChooseValues(context, panel);
	}
	
	protected boolean letUserChooseValues(UIPluginContext context, TwoStepRepairLogPanel panel){
		InteractionResult result = context.showConfiguration("Select Options for the repair of the log", panel);
		if (result.equals(InteractionResult.CANCEL)){
			return false;
		} else {
			getValuesFromPanel(panel);
			return true;
		}
	}

	protected void getValuesFromPanel(TwoStepRepairLogPanel panel) {
		missingChance = panel.getMissingChance();
		ratioOfStructureToInsertion = panel.getRatioOfStructureToInsertion();
		repairWorkerCount = panel.getRepairWorkerCount();
		timeUnit = panel.getTimeUnitFactor();
		randomMode = panel.isRandomRepairMode();
		simpleImmediateEventHandling = panel.isSimpleImmediateEventHandling();
		implementationType = panel.getImplementationType();
	}

	public double getRatioOfStructureToInsertion() {
		return ratioOfStructureToInsertion;
	}

	public void setRatioOfStructureToInsertion(double ratioOfStructureToInsertion) {
		this.ratioOfStructureToInsertion = ratioOfStructureToInsertion;
	}

	public int getRepairWorkerCount() {
		return repairWorkerCount;
	}

	public void setRepairWorkerCount(int repairWorkerCount) {
		this.repairWorkerCount = repairWorkerCount;
	}

	public TimeUnit getTimeUnit() {
		return timeUnit;
	}

	public void setTimeUnit(TimeUnit timeUnit) {
		this.timeUnit = timeUnit;
	}

	public boolean isRandomMode() {
		return randomMode;
	}

	public void setRandomMode(boolean randomMode) {
		this.randomMode = randomMode;
	}

	public boolean getSimpleImmediateEventHandling() {
		return simpleImmediateEventHandling;
	}

	public Type getImplementationType() {
		return implementationType;
	}

	public void setImplementationType(Type implementationType) {
		this.implementationType = implementationType;
	}
	
}
