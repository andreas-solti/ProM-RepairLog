package org.processmining.plugins.repairlog.evaluation;

import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.plugins.repairlog.evaluation.LogRepairEvaluator.GeneralStats;
import org.processmining.plugins.repairlog.evaluation.LogRepairEvaluator.TraceStats;


/**
 * A original Log and a repaired log are given as input.
 * The original log does not contain noise, i.e., missing events, or duplicate events, whereas for the repaired log it is tried 
 * to repair noise that has been introduced for the original log
 * 
 *  the idea is to compare each original trace with the repaired trace and calculate the costs for aligning. That means that for the 
 *  original trace first a Petri net is made and that it is aligned with the repaired trace.
 *  Afterwards, for the missing events that have been successfully added in the repaired the error of the new timestamp with the 
 *  original timestamp is calculated.
 * 
 * @author Ronny Mans
 *
 */
public class RepairLogEvaluationPlugin {
	
	@Plugin(name = "Evaluate Repaired Log", 
			parameterLabels = { "Log (original)", "Log (repaired)", "Petri Net" }, 
			returnLabels = { "Log Repair Evaluation Results" }, 
			returnTypes = { RepairLogEvaluationResults.class }, 
			userAccessible = true,
			help = "Evaluates the results that are obtained with the Repair Log Plugin.")
	@UITopiaVariant(affiliation = "Eindhoven University of Technology", author = "R.S. Mans", email = "r.s.mans@tue.nl")
	public RepairLogEvaluationResults evaluate(UIPluginContext context, XLog logOriginal, XLog logRepaired, Petrinet net){
		return evaluateLog(context, logOriginal, logRepaired, net);
	}

	/**
	 * Evaluates the repaired log against the original log according to the model 
	 * (the model is used to preserve parallelism) 
	 * @param context {@link UIPluginContext}
	 * @param logOriginal {@link XLog} the original log fully fitting to the model
	 * @param logRepaired {@link XLog} the repaired log (this might not be fitting too well)
	 * @param net {@link Petrinet} the Petri net capturing parallelism in the logs
	 * @return
	 */
	public RepairLogEvaluationResults evaluateLog(UIPluginContext context, XLog logOriginal, XLog logRepaired,
			Petrinet net) {
		try {
			// first check whether the number of traces in the repaired log and the original log is equal
			if (logOriginal.size() == logRepaired.size()) {
				RepairLogEvaluationConfig config = new RepairLogEvaluationConfig();
				config.getInformationForReplay(context, net, logOriginal);

				LogRepairEvaluator evaluator = new LogRepairEvaluator();
				config.letUserChooseValues(context);
				
				context.getProgress().setIndeterminate(true);
				List<TraceStats> statsCaseList = evaluator.evaluate(context, logOriginal, logRepaired, net, config);
				GeneralStats generalStats = evaluator.calculateGeneralResults();
				Map<String, DescriptiveStatistics> errorTransitionsCase = evaluator.getErrorTransitionsCase();
				RepairLogEvaluationResults resultsGUI = new RepairLogEvaluationResults(statsCaseList, generalStats, errorTransitionsCase);
				return resultsGUI;
			}
			else {
				JOptionPane.showMessageDialog(null,"The number of traces in the original log and the repaired log is not the same");  

			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		context.getFutureResult(0).cancel(true);
		return null;
	}

}

