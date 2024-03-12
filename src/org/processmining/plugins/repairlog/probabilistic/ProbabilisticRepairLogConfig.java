package org.processmining.plugins.repairlog.probabilistic;

import org.deckfour.uitopia.api.event.TaskListener.InteractionResult;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.plugins.repairlog.RepairLogConfig;
import org.processmining.plugins.repairlog.ui.ProbabilisticRepairLogPanel;

/**
 * Repairlog Config for computing probabilistic alignments.
 * 
 * @author Andreas Rogge-Solti
 *
 */
public class ProbabilisticRepairLogConfig extends RepairLogConfig {
	
	protected double missingChance = 0.1;
	
	protected double insertedChance = 0.02;
	
	protected boolean isUsingIlp = true;
	
	protected boolean verbose = false;
	
	public boolean letUserChooseValues(UIPluginContext context){
		ProbabilisticRepairLogPanel panel = new ProbabilisticRepairLogPanel(this);
		return letUserChooseValues(context, panel);
	}
	
	protected boolean letUserChooseValues(UIPluginContext context, ProbabilisticRepairLogPanel panel){
		InteractionResult result = context.showConfiguration("Select options for the probabilistic repair of the log", panel);
		if (result.equals(InteractionResult.CANCEL)){
			return false;
		} else {
			getValuesFromPanel(panel);
			return true;
		}
	}

	protected void getValuesFromPanel(ProbabilisticRepairLogPanel panel) {
		missingChance = panel.getMissingChance();
		isUsingIlp = panel.isUsingIlp();
		insertedChance = panel.getInsertedChance();
	}

	public boolean isUsingIlp() {
		return isUsingIlp;
	}
	
	public double getAverageMissingChance() {
		return missingChance;
	}

	public void setMissingChance(double missingChance) {
		this.missingChance = missingChance;
	}

	/**
	 * TODO: allow this to be depending on the events...
	 * @param eventId
	 * @return
	 */
	public double getInsertedChance(String eventId) {
		return this.insertedChance;
	}
	
	public double getAverageInsertedChance() {
		return insertedChance;
	}

	public void setInsertedChance(double insertedChance) {
		this.insertedChance = insertedChance;
	}
	
	public boolean isVerbose() {
		return verbose;
	}
	public void setVerbose(boolean verbose) {
		this.verbose = verbose;
	}
}
