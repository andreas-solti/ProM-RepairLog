package org.processmining.plugins.repairlog.evaluation;

import java.util.List;
import java.util.Map;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.deckfour.uitopia.api.event.TaskListener.InteractionResult;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.plugins.repairlog.evaluation.LogRepairEvaluator.GeneralStats;
import org.processmining.plugins.repairlog.evaluation.LogRepairEvaluator.TraceStats;
import org.processmining.plugins.repairlog.evaluation.ui.RepairLogEvaluationResultsPanel;


public class RepairLogEvaluationResults {
	
	private List<TraceStats> statsCaseList = null;
	private GeneralStats generalStats = null;
	private Map<String, DescriptiveStatistics> errorTransitionsCase = null;
	
	public RepairLogEvaluationResults (List<TraceStats> statsCaseList, GeneralStats generalStats, Map<String, DescriptiveStatistics> errorTransitionsCase) {
		this.statsCaseList = statsCaseList;
		this.generalStats = generalStats;
		this.errorTransitionsCase = errorTransitionsCase;
	}
	
	public void presentResults(UIPluginContext context){
		RepairLogEvaluationResultsPanel panel = new RepairLogEvaluationResultsPanel(this, statsCaseList, generalStats, errorTransitionsCase);
		InteractionResult result = context.showConfiguration("Results of the Repair Log Evaluation Plug-in", panel);
	}

	public List<TraceStats> getStatsCaseList() {
		return statsCaseList;
	}

	public GeneralStats getGeneralStats() {
		return generalStats;
	}

	public Map<String, DescriptiveStatistics> getErrorTransitionsCase() {
		return errorTransitionsCase;
	}

}
